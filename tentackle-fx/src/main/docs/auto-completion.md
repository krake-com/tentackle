# Auto-Completion — Suggesting Values While the User Types

## Overview and Motivation

Auto-completion turns a plain text field into a field that proposes matching values as the user
types. A popup below the component lists the candidates, the matching parts of each candidate are
highlighted, and picking one writes it into the field.

The feature is built around two ideas that shape the whole API:

- **A suggestion is a set of matched segments, not just a string.** The generator returns *where* it
  matched, not only *what* matched. That is what allows the popup to embolden exactly the characters
  the user typed, and it is what makes non-obvious matching strategies — camel case, separated
  numbers, regular expressions — presentable rather than mysterious.
- **Matching runs off the FX thread.** Choices may be large, and a matching strategy may be
  expensive. The controller runs the generator in the background and coalesces input, so the field
  keeps up with typing instead of stuttering or timing out.

Auto-completion is **off by default**. It is enabled explicitly per component by setting a function.
There is no global switch — but there is a way to reach every component of a given model type at once:
a [`ValueTranslator`](#enabling-it-for-a-whole-type-a-valuetranslator) can install the generator
itself, so that, say, every field bound to a `ProductNumber` completes without a single line of
per-field code.


## Key Concepts

### Enabling it: `setAutoCompletion`

Every [`FxTextComponent`](../java/org/tentackle/fx/FxTextComponent.java) carries the entry point:

```java
void setAutoCompletion(Function<String, List<List<SubString>>> autoCompletion);   // null to clear
Function<String, List<List<SubString>>> getAutoCompletion();                      // null = disabled
```

Setting a non-null function creates an
[`AutoCompletionController`](../java/org/tentackle/fx/component/auto/AutoCompletionController.java)
for the component; setting `null` closes it again, removing the listeners and hiding the popup. The
controller is created through `FxUtilities.createAutoCompletion(...)`, so applications that replace
`FxUtilities` can substitute their own controller.

These components support it:

| Component                              | Notes                                             |
|----------------------------------------|---------------------------------------------------|
| `FxTextField`, `FxTextArea`            | Directly on the control.                          |
| `FxComboBox`, `FxDatePicker`           | Attached to the control's *editor*.               |
| `FxCustomTextField`, `FxMaskTextField` | tentackle-fx-atlanta.                             |

Two components refuse it and throw `FxRuntimeException` if a non-null function is set:
`FxPasswordField` (auto-completing a password would disclose it) and `FxHTMLEditor` (unsupported).

### Enabling it for a whole type: a `ValueTranslator`

Setting the function on each field is fine for a one-off. But if a data type should *always* complete
— an article number, a country code, a `ProductNumber` — putting that call into every controller is
both tedious and easy to forget. The [`ValueTranslator`](fx.md#value-translators) is the place to do
it once.

A translator is created per component by `FxFactory.createValueTranslator(modelClass, viewClass,
component)` and receives that component in its constructor, which is all it takes to install the
generator there:

```java
@ValueTranslatorService(modelClass = ProductNumber.class, viewClass = String.class)
public class ProductNumberStringTranslator extends ValueStringTranslator<ProductNumber> {

  public ProductNumberStringTranslator(FxTextComponent component) {
    super(component);
    SeparatorSuggestionGenerator<ProductNumber> generator = new SeparatorSuggestionGenerator<>("-");
    generator.setChoices(ProductNumberCache.getInstance().getNumbers());
    component.setAutoCompletion(generator);
  }

  // toViewFunction() / toModelFunction() as usual
}
```

From then on every text component bound to a `ProductNumber` completes, whether it comes from FXML,
from the RDC's `GuiProvider`, or from a generated form — the binding picks the translator by model
type, and the translator brings the auto-completion with it. Because the translator is instantiated
per component, each component still gets its own generator instance, which is exactly what the
[threading rules](#threading-and-input-coalescing) require.

Model classes are mapped leniently, so a translator registered for a base type covers its subtypes as
well — that is how `PdoStringTranslator` serves all PDOs. A translator can therefore equip a whole
family of types in one go. Individual components can still opt out afterward with
`setAutoCompletion(null)`, or override the choice of generator by setting their own.

### The function type

The generator is a plain `Function<String, List<List<SubString>>>`:

- The **input** is the current text of the component.
- The **result** is one entry per suggestion.
- Each suggestion is a `List<SubString>` — the segments of that candidate that matched the input.

All `SubString`s of a suggestion refer to the same underlying string, so
`suggestion.getFirst().getString()` *is* the candidate's text; the segments only mark ranges within
it. That is the string written into the field when the user picks the suggestion.

[`SubString`](../../../../tentackle-core/src/main/java/org/tentackle/misc/SubString.java)
(`org.tentackle.misc`, tentackle-core) describes a substring by its begin and end index and builds
the segment lazily, so a generator can compute matches without allocating strings for candidates it
ends up rejecting.

An empty result list means "no suggestions" and hides the popup. You can implement the function from
scratch — a lambda hitting a database is perfectly legitimate — but the built-in generators cover the
usual strategies.

### Suggestion generators

[`AbstractSuggestionGenerator<T>`](../java/org/tentackle/fx/component/auto/AbstractSuggestionGenerator.java)
is the base class. It holds the **choices** (objects of any type `T`) and converts each to the string
compared against the input:

```java
SimpleSuggestionGenerator<Customer> generator = new SimpleSuggestionGenerator<>() {
  @Override
  protected String convert(Customer customer) {
    return customer.getName();     // defaults to toString()
  }
};
generator.setChoices(customerList);
nameField.setAutoCompletion(generator);
```

`setChoices` converts the list once, up front, into the *select list* — the strings actually matched
against. Subclasses may override `setSelectList` to bypass or filter that conversion.

The four built-in strategies:

| Generator                     | Matches                                                                  | Good for                                    |
|-------------------------------|--------------------------------------------------------------------------|---------------------------------------------|
| `SimpleSuggestionGenerator`   | The input as a plain substring, anywhere in the candidate.                | Ordinary text.                              |
| `CamelCaseSuggestionGenerator`| Input segments against the candidate's camel-case segments.               | Identifiers, `CustomerInvoiceReport`.       |
| `SeparatorSuggestionGenerator`| Input segments against segments split by a separator string.             | Structured codes like `XXX-YYY-Z-AAA`.      |
| `RegexSuggestionGenerator`    | Input segments against segments found by a regular expression.           | Anything with a lexical structure of its own.|

`SimpleSuggestionGenerator` and `SeparatorSuggestionGenerator` are case-insensitive by default
(`setCaseSensitive(true)` to change that).

### Segmented matching

The latter three extend
[`AbstractSegmentedSuggestionGenerator`](../java/org/tentackle/fx/component/auto/AbstractSegmentedSuggestionGenerator.java),
which implements one shared rule: split **both** the input and the candidate into segments, then walk
the input segments in order, consuming candidate segments as they match. A candidate is suggested
only if **every** input segment found a match — so the input's order is respected, but gaps are
allowed.

Subclasses supply just two things: `extractSegments(String)` (how to split) and
`match(inputSegment, itemSegment)` (when two segments match, and which part matched). Candidate
segments are cached per select list, so splitting happens once per candidate rather than once per
keystroke.

`setMaxSuggestions(int)` caps the result — matching stops as soon as the cap is reached. The default
`0` means unlimited, which is worth revisiting for large choice lists.

**Camel case** deserves a note, because it carries two refinements. Its input handling starts
case-insensitive and *switches to case-sensitive as soon as the user types an uppercase letter*:
`cir` matches `CustomerInvoiceReport`, and once you type `CIr` you have committed to that casing.
And because the natural alphabetical sort is useless here, it sorts by visual quality of the match —
earliest match position first, then most segments matched, then the remainder alphabetically.

### The popup

[`AutoCompletionPopup`](../java/org/tentackle/fx/component/AutoCompletionPopup.java) is a
`PopupControl` shown directly below the component, with its list width bound to the component's. The
first suggestion is preselected, `ENTER` or a primary mouse click accepts the selection, `ESCAPE`
hides the popup, and it auto-hides when the component loses focus.

[`AutoCompletionCellFactory`](../java/org/tentackle/fx/component/auto/AutoCompletionCellFactory.java)
renders each row as a `TextFlow`, drawing the matched segments in bold and the rest in the theme's
default font. Override `AutoCompletionPopup.createCellFactory()` to render rows differently.

Styling hooks — the framework itself ships no rules for these, they exist for themes and
applications:

| Hook                               | Kind        | Applies to                       |
|------------------------------------|-------------|----------------------------------|
| `.autocompletion`                  | style class | the popup                        |
| `.autocompletion-pane`             | style class | the popup's `StackPane`          |
| `.autocompletion-list`             | style class | the `ListView` inside            |
| `-tt-autocomplete-visible-row-count` | CSS property | rows shown before scrolling (default 16) |
| `-tt-autocomplete-fixed-cell-size` | CSS property | row height (default 0 = derive from the text component's height) |

### Threading and input coalescing

The controller never runs the generator on the FX application thread. It runs it in a JavaFX
`Service`, **one task at a time**: while a task is running, further keystrokes only overwrite the
pending input, and the newest input is picked up when the running task finishes. Intermediate inputs
are simply skipped. Typing therefore costs at most one queued match, no matter how slow the
generator, and there are no timeouts to tune.

Two consequences worth knowing:

- **A generator instance belongs to one component.** The built-ins are stateful (choices, the cached
  segments, and `RegexSuggestionGenerator`'s reused `Matcher`) and are called from a background
  thread. Give each component its own instance.
- **A custom generator must be safe to call off the FX thread.** It must not touch the scene graph.
  If it queries a database, that is fine — that is the point of running it in the background — but
  it should be a PDO/persistence call, not UI work.

If the generator throws, the controller logs a warning and shows nothing; the field stays usable.

The popup stays hidden in the cases where it would only be noise: no suggestions at all, or exactly
one suggestion that already equals the field's content.


## How It Fits Together

A controller offering completion over a list of customers:

1. The FXML declares an `FxTextField` (or an `FxComboBox`, or the RDC's `GuiProvider` creates one).
2. The controller creates a suggestion generator, overriding `convert()` to map each PDO to the
   string the user should see and match against.
3. `setChoices(...)` loads the candidates — eagerly here, or lazily, or not at all if the function
   queries on each call instead.
4. `field.setAutoCompletion(generator)` installs it. The delegate lazily creates the
   `AutoCompletionController`, which hooks the component's text and focus properties.
5. The user types. Each keystroke schedules a background match (coalescing anything still pending),
   and the popup appears with the matched segments in bold.
6. The user hits `ENTER`. The candidate's full text is written into the field, which fires the
   component's normal [binding](../../../../tentackle-core/src/main/docs/binding.md) and
   [validation](../../../../tentackle-core/src/main/docs/validation.md) machinery — auto-completion
   sets text, it does not bypass anything downstream.


## Package Layout

| Package                              | Content                                                                     | Module               |
|--------------------------------------|-----------------------------------------------------------------------------|----------------------|
| `org.tentackle.fx.component.auto`    | `AutoCompletionController`, the suggestion generators, `AutoCompletionCellFactory` | tentackle-fx   |
| `org.tentackle.fx.component`         | `AutoCompletionPopup`                                                       | tentackle-fx         |
| `org.tentackle.fx.component.skin`    | `AutoCompletionPopupSkin`                                                   | tentackle-fx         |
| `org.tentackle.misc`                 | `SubString`                                                                 | tentackle-core       |


## Related Documentation

- [tentackle-fx](fx.md) — the extended JavaFX layer this builds on, including the
  [prefix selection](fx.md#combo-and-choice-boxes) of non-editable combo and choice boxes, the
  keyboard counterpart for choosing among a fixed set of items.
- [Binding](../../../../tentackle-core/src/main/docs/binding.md) — what happens to the value once it lands in the field.
- [Validation](../../../../tentackle-core/src/main/docs/validation.md) — completed values are validated like any other input.
- [tentackle-fx-atlanta](../../../../tentackle-fx-atlanta/src/main/docs/atlanta.md) — `FxCustomTextField` and `FxMaskTextField`.
- [Tables and Trees](tables.md) — the other configuration-driven part of the FX layer.
