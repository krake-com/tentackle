# tentackle-fx — The Extended JavaFX Layer

## Overview and Motivation

`tentackle-fx` is Tentackle's desktop UI foundation. It wraps the standard
JavaFX controls and containers in an *extended FX layer* that integrates
seamlessly with the rest of the framework: the [binding](../../../../tentackle-core/src/main/docs/binding.md)
API, [validation](../../../../tentackle-core/src/main/docs/validation.md),
[PDOs](../../../../tentackle-pdo/src/main/docs/pdo.md), internationalization,
theming, and SPI-based service discovery.

The module lives in package `org.tentackle.fx` (JPMS module `org.tentackle.fx`)
and `requires transitive org.tentackle.core` together with the JavaFX modules
(`javafx.fxml`, `javafx.web`, `javafx.controls`, …) and the Ikonli icon packs.

### Why an extended FX layer?

Plain JavaFX gives you controls, FXML, properties, and a controller hook. What it
does *not* give you is a way to connect those controls to a domain model without
hand-writing glue per field, nor a unified notion of "changeable", "mandatory",
"error" or "value of the model's type". Tentackle adds exactly that:

- **Every control is a first-class, bindable component.** `FxTextField`,
  `FxComboBox`, `FxTableView`, … each implement `FxComponent` and carry a
  *model type*, a *value translator*, mandatory/changeable/error state, and
  popup support — on top of the JavaFX control they extend.
- **Controllers are wired automatically.** An `@FxControllerService`-annotated
  controller is discovered, its FXML/CSS/resource bundle located by convention,
  its `@FXML` fields injected, and its components *bound by name* to the model —
  no `textProperty().bindBidirectional(...)` boilerplate.
- **Model values, not strings.** A component exposes `getViewValue()` /
  `setViewValue()` in the model's own type (`BigDecimal`, `LocalDate`,
  `BMoney`, an enum, a PDO …). A pluggable `ValueTranslator` converts to and
  from the on-screen representation.
- **Toolkit-agnostic model side.** The binding and validation contracts live in
  `tentackle-core` and know nothing about JavaFX. `tentackle-fx` is merely the
  reference implementation of those contracts for the JavaFX toolkit.
- **Modular and non-modular.** Components, translators, configurators,
  controllers, and table-cell types are all located through the `ServiceFinder`
  SPI, so the same code runs on the module path or the class path.

### Design principles

- **Wrap, don't replace.** Each Tentackle component *extends* its JavaFX
  counterpart, so anything you can do with a `TextField` you can still do with
  an `FxTextField`. The extras are additive.
- **Delegate over inheritance.** Because a component must already extend a
  concrete JavaFX class, the shared `FxComponent` behaviour cannot also be
  inherited from a common base class. Instead, each component holds a
  **delegate** that implements the behavior. This emulates the multiple
  inheritance that the PDO layer achieves with proxies.
- **Configure by convention, override by service.** Defaults come from naming
  conventions (FXML file next to the controller, bundle by class name) and
  from per-type *configurators*; applications override by contributing their
  own annotated services.
- **No state in shared singletons.** Factories, configurators, and cell-type
  factories are stateless singletons located once and reused across all
  instances.

---

## Key Concepts

### FxControl, FxComponent, and FxContainer

The control hierarchy mirrors JavaFX but adds the Tentackle contract:

- **`FxControl`** — the common parent of everything in the layer. It defines the
  *changeable* abstraction (`setChangeable`/`isChangeable`/`changeableProperty`),
  which is translated to `setEditable` or `setDisable` depending on the
  underlying control, plus the container-changeable propagation that lets a
  whole form be switched read-only at once.
- **`FxComponent extends FxControl`** — a leaf control that carries a value:
  text fields, combo boxes, check boxes, pickers, tables, etc. It adds the model
  *type* (`setType`/`getType`, plus a generic type), the *value translator*,
  the `getViewValue()`/`setViewValue()` value access in the model's type, and —
  via `ErrorPopupSupported` and `InfoPopupSupported` — error and info popups
  and the mandatory flag.
- **`FxContainer extends FxControl`** — a layout node holding components:
  `FxVBox`, `FxGridPane`, `FxTabPane`, … It knows its `FxController`, can walk
  the controller hierarchy (`getController(Class)`), and offers form-wide
  operations such as `clearErrors()`, `updateView()` and
  `updateViewNonFocused()`.

### The delegate pattern

Every component must extend a concrete JavaFX class (`FxTextField extends
TextField`), so the cross-cutting `FxComponent` behaviour cannot live in a
shared superclass. Tentackle therefore keeps that behavior in a **delegate**:

```java
public class FxTextField extends TextField implements FxTextComponent {
  private FxTextFieldDelegate delegate;

  protected FxTextFieldDelegate createDelegate() {
    return new FxTextFieldDelegate(this);
  }

  @Override
  public FxTextFieldDelegate getDelegate() {
    if (delegate == null) {
      setDelegate(createDelegate());
    }
    return delegate;
  }
  // ... thin forwarding methods generated by a wurblet ...
}
```

The component is a thin shell that extends the JavaFX class and forwards to its
delegate; the delegate (`FxTextFieldDelegate` → `AbstractTextFieldDelegate` →
`FxComponentDelegate` → `FxControlDelegate`) holds the real logic — the type,
the translator, the mandatory/error/info state, and the toolkit-specific
`getViewObject()`/`setViewObject()` that read and write the actual widget. The
forwarding methods are generated by a wurblet (`@wurblet delegate Include`), so
the boilerplate is never hand-maintained. `setDelegate(...)` lets an application
substitute custom behavior. The delegate base also defines the standard CSS
style classes `tt-mandatory`, `tt-error` and `tt-info`.

### Controllers

A controller drives a view. `FxController` is the contract; `AbstractFxController`
is the usual base class (and `AbstractValidateableFxController` adds
`Validateable` for controllers that validate themselves rather than their
bindables). A controller:

- holds its `view` (a `Parent`, usually also an `FxContainer`) and its top-level
  container;
- owns an `FxComponentBinder` (`getBinder()`) connecting components to the model;
- tracks its `@FXML`-annotated fields and methods, and `validateInjections()`
  verifies that every `fx:id` actually resolved to a non-null field (the FXML
  loader silently ignores mismatches);
- runs `configure()` as the final initialization step.

Controllers are *discovered*, not hand-instantiated, through the
`@FxControllerService` annotation (see below).

### @FxControllerService — declaring a controller

`@FxControllerService` marks a class as a Tentackle FX controller and is an
SPI `@Service(FxController.class)`, so `FxFactory` can enumerate and create all
controllers. By convention the loader resolves, next to the controller class:

| Attribute        | Default / convention                                                                   |
|------------------|----------------------------------------------------------------------------------------|
| `url()`          | `<ControllerName>.fxml` (set `FXML_NONE` to skip the FXML loader)                      |
| `resources()`    | `<ControllerName>.properties` (set `RESOURCES_NONE` for none)                          |
| `css()`          | `<ControllerName>.css` (missing is fine)                                               |
| `cssDarkAware()` | append `-dark` to the CSS file name in dark mode                                       |
| `binding()`      | `BINDING.YES`; also `NO`, `COMPONENT_INHERITED`, `BINDABLE_INHERITED`, `ALL_INHERITED` |
| `view()`         | a view class to use instead of FXML (see *no-FXML* below)                              |
| `test()`         | whether the controller is created and bound in unit tests                              |

> FXML, CSS, and `.properties` files are co-located with the controller in
> `src/main/java` (not `src/main/resources`) and copied as resources at build
> time, so SceneBuilder resolves them by relative path. See the FXML/FX
> conventions in `CLAUDE.md`.

### FxFactory — the central factory

`FxFactory.getInstance()` is the singleton entry point. It enumerates the
registered controller classes, supplies the `BuilderFactory` used by the FXML
loader, hands out per-class `Configurator`s, creates `ValueTranslator`s for a
given model/view type pair, creates configured `Stage`s, and creates controllers
(`createController(...)`). The default implementation is `DefaultFxFactory`.

### Fx — the convenience facade

`Fx` is a static helper covering the common operations so application code stays
terse:

- **Loading:** `Fx.load(url)`, `Fx.load(url, resources)`, `Fx.load(controllerClass)`.
- **Stages and scenes:** `createStage(...)`, `createScene(root)`, `show(stage)`,
  `getStage(node)`, `isModal(stage)`.
- **Creating components:** `Fx.create(SomeFxClass.class)` (delegating to the factory).
- **Dialogs:** `info`, `warning`, `error`, `question`, `yes`, `no` — each taking
  an owner, a message, and an optional callback.
- **Icons:** `createGraphic(realm, name)` / `createGraphic(name)` via the
  `GraphicProvider` SPI (Ikonli or image based).
- **Misc:** `copyToClipboard(text)`, `terminate()`.

### Value translators

A `ValueTranslator<M,V>` converts between the **model** type `M` and the **view**
type `V` (Tentackle says *translator*, not *converter*, to avoid confusion with
JavaFX `StringConverter`). It exposes a `toViewFunction()` and a
`toModelFunction()`, a `lenient` flag for parsing, and an *incomplete-mapping*
mechanism (`isMappingIncomplete()` / `saveModelValue()`) for types — like
`I18NText` — where the view shows only part of the model and the rest must be
remembered to detect changes.

Translators are located by model/view type through `FxFactory.createValueTranslator(...)`
and registered with `@ValueTranslatorService`. The `org.tentackle.fx.translate`
package ships a large built-in set: numeric (`IntegerStringTranslator`,
`LongStringTranslator`, `DoubleStringTranslator`, `BMoneyStringTranslator`,
`FractionNumberStringTranslator`, …), temporal (`LocalDateStringTranslator`,
`InstantStringTranslator`, `ZonedDateTimeStringTranslator`, …), `EnumStringTranslator`,
`I18NTextStringTranslator`, `ColorColorTranslator`, identity/collection helpers,
and the `AbstractValueTranslator` base.

### Tables

`FxTableView`, `FxTreeTableView` and `TotalsTableView` are the table components,
with `FxTableColumn` / `FxTreeTableColumn` and matching cells. Columns are
populated from a **table configuration** (`TableConfiguration` /
`TableColumnConfiguration`, default implementations and a
`TableConfigurationProvider` discovered via `@TableConfigurationProviderService`).
Per-value rendering is handled by **table cell types**: a `TableCellType` knows
how to display and edit one value class, produced by a `TableCellTypeFactory`
and registered with `@TableCellTypeService`. The `org.tentackle.fx.table.type`
package supplies cell types for strings, numbers, booleans, enums, money,
`I18NText` and the full range of date/time classes. Tables bind through the
dedicated `FxTableBinder` / `FxTableBinding` (see the binding doc).
For the full picture — including preferences-backed layouts, totals, and the
PDO-aware RDC layer — see [tables.md](tables.md).

### Showing validation results

Validation results describe problems in the *model* (`invoice.lineList[3].price`),
not on screen. An `InteractiveError` links a `ValidationResult` to the concrete
`FxControl` it belongs to, adding the message, an optional error code, and
whether it is an error or just a warning. `InteractiveErrorFactory` (a
replaceable `@Service`) resolves the control by mapping the validation path to a
binding path and looking up the `FxComponentBinding`.
`AbstractValidateableFxController.validateForm()` ties it together: it validates,
shows the collected messages in a dialog, and marks the offending components with
`setError` / `setInfo`. See [interactive-error.md](interactive-error.md).

### Graphics and icons

Icons never appear as file names or icon-pack names in framework code. A
`GraphicProvider` maps a **symbolic name** to a JavaFX `Node`
(`Fx.createGraphic("save")`), and providers are registered per **realm** with
`@GraphicProviderService` — the framework uses the default realm, applications
should use their own. `DefaultGraphicProvider` serves the default realm from the
Ikonli materialdesign2 pack; `IkonliGraphicProvider` and `ImageGraphicProvider`
are the base classes for font-icon and image-file providers. Size and color come
from CSS, so icons follow the theme. See
[graphic-provider.md](graphic-provider.md).

### Auto-completion

Any `FxTextComponent` can propose matching values while the user types:
`setAutoCompletion(...)` takes a `Function<String, List<List<SubString>>>` mapping
the current input to the matching candidates, each described by its matching text
segments. The `org.tentackle.fx.component.auto` package supplies the controller
and ready-made **suggestion generators** (simple substring, camel-case, separated
segments, regular expression), and the matched segments are rendered in bold in a
popup below the component. Matching runs in the background, so large choice lists
don't block the FX thread. For generators, styling and threading rules see
[auto-completion.md](auto-completion.md).

### Expandable one-line text areas

A text area displayed as a one-liner (`prefRowCount` of 1, e.g. from the
`LINES=1` binding option) is a compromise: it saves space in the form, but the
text does not fit. `FxTextArea` can therefore show an **expand button** at its
right edge. Clicking it pops up a multi-line editor that covers the text area,
aligned to its upper left corner, so that the first line stays where it is and
the editor grows downwards. It collapses again as soon as it loses the focus, or on `ESCAPE`, or
via the collapse button in its upper right corner.

The feature is **off by default** and enabled per component:

```java
textArea.setExpandable(true);       // opt in, no effect unless isOneLiner()
textArea.setExpandedRowCount(10);   // 0 = FxUtilities.getDefaultExpandedRowCount(), which is 6
textArea.expand();                  // programmatically, e.g. from a menu item
```

Expanding is only a different *view* on the same component. The editor's text is
written back to the text area on each keystroke, so binding, validation, and the
modification tracking behave exactly as if the user had typed into the text area
itself and the text area keeps the focus in its own scene while the popup is
showing — the model is updated by its regular focus handling. The editor also
gets a `TextFormatter` with the component's delegate as its filter, so valid
chars, case conversion and `maxColumns` apply to the expanded view as well.
`ENTER` inserts a newline there instead of navigating, since entering more than
one line is the very reason to expand; `TAB` collapses and navigates as usual.

The button lives in `FxTextAreaSkin`, which lays out the text in the space left
of it, and the editor in `ExpandedTextPopup`. Styling hooks:

| Hook                     | Kind        | Applies to                                    |
|--------------------------|-------------|-----------------------------------------------|
| `.tt-expand-button`      | style class | both the expand- and the collapse button      |
| `.tt-expanded-text`      | style class | the popup                                     |
| `.tt-expanded-text-pane` | style class | the popup's `StackPane`                       |

### Input alerts

Some input is rejected silently: text typed or pasted beyond `maxColumns` is cut
off by the delegate's `TextFormatter` filter, and `BACKSPACE` at the beginning of
the text has nothing to delete. `InputAlert` gives the user feedback in both
cases, acoustically and/or visually. `java.awt.Toolkit.beep()` is no option,
since it beeps the system speaker instead of the sound system, and JavaFX has no
API to access the system alerts.

- The **visual alert** briefly (150 ms) sets the CSS pseudo-class `:tt-alert` on
  the control. The stylesheets map it to the color variable `-tt-alert-color`,
  which overrides the mandatory, error and info colors for the duration of the flash.
  Since JavaFX weighs a style class higher than any number of pseudo-classes
  (unlike the W3C rules), the stylesheets combine each state class with the
  pseudo-class (`.tt-mandatory:tt-alert` etc.). Application stylesheets that
  color controls by their own style classes must do the same, e.g.
  `.my-state:tt-alert`, or the flash will not show on those controls.
- The **acoustic alert** plays a short, soft bump (the bundled
  `org/tentackle/fx/input-alert.wav`) via `javax.sound.sampled`, i.e. through the
  sound system. An alert is not played again while the previous
  one is still running, so holding down a key does not stack tones. If no audio
  device is available, a warning is logged once and the alert stays silent.

The alerts are triggered by all text components (`FxTextField`, `FxTextArea`,
`FxPasswordField`, the editors of `FxComboBox` and `FxDatePicker`, and the
expanded text popup). Truncation while the view is updated from the model does
not alert, since the user did not type anything.

By default, only the visual alert is enabled. Both can be changed at runtime,
e.g. from a settings dialog, since they are JavaFX properties:

```java
InputAlert alert = InputAlert.getInstance();
alert.setSoundEnabled(true);
alert.setVisualEnabled(false);
alert.setSound(MyApp.class.getResource("bump.wav"));   // wav, aiff or au; null = default sound
soundCheckBox.selectedProperty().bindBidirectional(alert.soundEnabledProperty());
```

A single control opts out with
`control.getProperties().put(InputAlert.DISABLED, Boolean.TRUE)`. To change the
behavior altogether, e.g. to alert on other occasions via `InputAlert.Reason`,
replace `DefaultInputAlert` with an implementation annotated
`@Service(InputAlert.class)`.

### Keyboard handling

The component configurators install a consistent keyboard model, geared to fast data entry:

| Key                          | Effect                                                                                                                         |
|------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `ENTER` / `Shift-ENTER`      | Move the focus to the next / previous component, like `TAB` / `Shift-TAB`. Not in table cell editors.                          |
| `Ctrl-ENTER` / `Ctrl-TAB`    | In a text area: insert a newline / tab. `ENTER` and `TAB` navigate there as well, unless `FxTextArea.setNavigateViaEnterEnabled(false)` restores the JavaFX behavior. |
| `Shift-BACKSPACE`            | Clear a text field.                                                                                                            |
| `Ctrl-Z`                     | Restore the saved value of the component — usually the value loaded from the database (see [View-modified tracking](#view-modified-tracking)). |
| `Ctrl-Y`                     | Bring back the value the user entered, i.e. undo the `Ctrl-Z`. The two keys toggle between the old and the new value.          |
| `DELETE` / `BACKSPACE`       | In a combo box: clear the selection, if allowed (see [Combo and choice boxes](#combo-and-choice-boxes)).                       |
| `F1`                         | Show the [online help](#online-help) of the component.                                                                         |
| `Ctrl-C` / `Shift-Ctrl-C`    | In a table: copy the selected cells to the clipboard, rows and columns swapped with `Shift` (see [Utilities](#utilities)).      |
| `Ctrl-Shift` + right click   | Log the component hierarchy under the mouse pointer and, if [BundleMonkey](../../../../tentackle-i18n/src/main/docs/bundlemonkey.md) is running, navigate to the component's bundle. |

With `tentackle-fx-rdc`, `F2` and `F3` edit and search the PDO shown in a component, see
[PDO-bound components](../../../../tentackle-fx-rdc/src/main/docs/rdc.md#pdo-bound-components).

When a component gains the focus, it updates its view from the model, shows its info or error note
(see [Notes and notifications](#notes-and-notifications)) and, if [`autoSelect`](#text-input-properties)
is set, selects its text. When it loses the focus, it updates the model — this is the moment the
binding transfers the value and validation runs. The notes are also shown while the mouse hovers
over the component.

The key handling is implemented by `FxUtilities.remapKeys`, `filterKeys` and `setupFocusHandling`,
invoked by the component [configurators](#configurators). An application changes it by overriding
these methods in its own `FxUtilities` service, or per type with a configurator.

### View-modified tracking

Every control remembers the value it showed after the last update from the model: `saveView()`
takes a copy, `invalidateSavedView()` drops it. As soon as the user types, `triggerViewModified()`
compares the current view with the saved one and sets the `viewModified` property. The flag bubbles up
the container hierarchy, so the top-level container of a form knows whether *anything* was changed.

```java
saveButton.disableProperty().bind(getContainer().viewModifiedProperty().not());
```

This is how the RDC's `PdoCrud` decides whether to ask "save, discard or cancel?" before closing, and
it is what `Ctrl-Z` restores. Changing a value programmatically via `updateView()` does not count
as a modification; call `saveView()` on the container after loading a new object to start over.

### Online help

Every control may carry a help URL (`setHelpUrl(...)`). `F1`, or `showHelp()`, opens the URL of the
control in the system browser — or, if the control has none, the URL of the nearest container that
has one, so a single URL on a form's top container covers all its fields. The URLs are relative to
a prefix set once for the application:

```java
FxUtilities.getInstance().setHelpURL("https://help.example.com/myapp/");
invoiceNoField.setHelpUrl("invoice.html#number");
```

### Text input properties

Text components (`FxTextField`, `FxTextArea`, `FxPasswordField` and the editors of combo boxes and date
pickers) implement `FxTextComponent`, which controls how the input is accepted and formatted:

| Property                        | Effect                                                                                                                   |
|---------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| `columns` / `maxColumns`        | The visible width in characters / the maximum length of the input (longer input is cut off with an [input alert](#input-alerts)). |
| `caseConversion`                | `UPPER_CASE` or `LOWER_CASE` while typing.                                                                               |
| `validChars` / `invalidChars`   | The characters accepted / rejected while typing.                                                                         |
| `filler` / `stripMode`          | A filler character (default blank) and which fillers are stripped from the input: `NONE`, `LEFT`, `RIGHT` or `BOTH` (default). |
| `textConverter`                 | A `Function<String,String>` applied to the typed text before it is shown.                                                |
| `pattern`                       | The format pattern of numbers and dates, overriding the defaults of the value translator.                               |
| `scale`                         | The number of fraction digits of a decimal number.                                                                       |
| `unsigned`                      | Reject negative numbers.                                                                                                 |
| `utc`                           | Interpret a timestamp as UTC.                                                                                            |
| `lenient`                       | Parse leniently, e.g. dates in abbreviated forms (see [SmartDateTimeParser](../../../../tentackle-core/src/main/docs/misc.md#smartdatetimeparser-typing-dates-the-way-people-think)). |
| `autoSelect`                    | Select the whole text when the component gains the focus, handy for numeric fields.                                      |
| `textAlignment`                 | The alignment of the text; numbers are right-aligned by default.                                                         |
| `autoCompletion`                | See [Auto-completion](#auto-completion).                                                                                 |

Usually, these properties are not set in code, but derived from the model: the binding applies the
[binding options](../../../../tentackle-model/src/main/docs/model-definition.md#binding-options) of the bound
attribute (the `@Bindable` options generated from the model) to the component.

| Binding option                                | Property                                |
|-----------------------------------------------|-----------------------------------------|
| `uc` / `lc`                                   | `caseConversion`                        |
| `maxcols=<n>` (and `size` for the model size) | `maxColumns`                            |
| `cols=<n>`                                    | `columns`                               |
| `lines=<n>`                                   | the row count of a text area            |
| `scale=<n>`                                   | `scale`                                 |
| `filler=<c>`                                  | `filler`                                |
| `strip`, `nostrip`, `lstrip`, `rstrip`        | `stripMode`                             |
| `digits`                                      | `validChars` = the digits               |
| `unsigned`                                    | `unsigned`                              |
| `utc`                                         | `utc`                                   |
| `autoselect`                                  | `autoSelect`                            |

In tables, the options `maxcols` and `scale` configure the column; the others are ignored there.
An application that wants money to be entered cash-register style overrides
`FxUtilities.isLenientMoneyInput(component)` to return `true` (default `false`): a decimal separator
missing in the input is then inserted according to the scale, so `1234` becomes `12.34`.

### Notes and notifications

Two kinds of popups give feedback without blocking the user:

- A [`Note`](../java/org/tentackle/fx/component/Note.java) is a small speech bubble linked to a
  component. Tentackle uses notes for the error message of a component (e.g. after a parse error)
  and for its info message, shown while the component has the focus or the mouse hovers over it. A
  note is placed to the right of its component by default, and elsewhere if there is not enough space
  on the screen. There are three types — `ERROR`, `INFO` and `STICKY` — and five positions (`RIGHT`,
  `LEFT`, `TOP`, `BOTTOM`, `CENTER`), which map to the style classes of the same name in lower case on
  the style class `note`. Applications use notes for hints of their own:

  ```java
  Note note = Fx.create(Note.class);
  note.setType(Note.Type.STICKY);
  note.setText("check the delivery address");
  note.show(addressField);
  ```

- A *notification* is a lightweight replacement for a JavaFX `Alert` dialog: a popup centered
  above its owner window, built by a [`NotificationBuilder`](../java/org/tentackle/fx/NotificationBuilder.java)
  from `FxFactory.createNotificationBuilder()`. The dialogs of the [`Fx`](#fx-the-convenience-facade) facade
  — `Fx.info`, `warning`, `error`, `question` — are notifications. Unlike `Alert.showAndWait()`, they do
  **not** block the event queue: the methods return immediately and deliver the answer to a callback.
  In a question, `0` answers *no* and `1` answers *yes*. A custom notification looks like this:

  ```java
  Popup popup = FxFactory.getInstance().createNotificationPopup();
  Parent notification = FxFactory.getInstance().createNotificationBuilder()
                          .type(NotificationBuilder.Type.WARNING)   // NONE, INFORMATION, WARNING, ERROR, CONFIRMATION, QUESTION
                          .title("Import")
                          .text("3 lines skipped")
                          .details(log)                             // or .content(node)
                          .duration(5000)                           // close automatically after 5 s
                          .hide(popup::hide)
                          .build();
  FxUtilities.getInstance().showNotification(owner, popup, notification, null);
  ```

  `button(...)` adds buttons with actions, `closeButton(...)` controls the close button, `fadeIn` and
  `fadeOut` animate it, and `css(url)` replaces the default stylesheet `org/tentackle/fx/notification.css`.
  The notification pane carries the style class `tt-notification`.

### Combo and choice boxes

`FxComboBox` and `FxChoiceBox` add a few conveniences to their JavaFX counterparts:

- **Prefix selection.** In a non-editable box, typing selects the first item starting with the typed
  characters; the prefix is reset after a pause of `FxUtilities.getPrefixSelectionTimeout()` (750 ms).
  The match is case-sensitive only if the first character is upper case. Setting the control property
  `PrefixSelectionFeature.ENABLED` to `PrefixSelectionFeature.CAMEL_CASE` matches camel-case prefixes
  instead, e.g. `CO` selects `CustomerOrder`; removing the property turns the feature off. For
  shortcuts that don't appear in the displayed text, a
  [`NavigationLetterStringConverter`](../java/org/tentackle/fx/NavigationLetterStringConverter.java)
  defines per item the letters to navigate by.
- **Deselection.** `DELETE` or `BACKSPACE` clears the selection of a combo box, unless its type is
  primitive or the component is mandatory. `setDeselectAllowed(Boolean)` overrides this; `null` restores
  the automatic decision.
- **Short and long text.** Items implementing `ShortLongText` (such as code tables) are shown in two
  columns in the popup list, the short text and the long text. `setShortLongTextEnabled(false)` shows
  the plain `toString()` instead. The cell factory is the `ShortLongTextCellFactory`; the RDC replaces it
  by one that respects the view permission of PDOs.

### Themes and dark mode

[`ThemeUtilities`](../java/org/tentackle/fx/ThemeUtilities.java) is the replaceable
`@Service` managing the look of the application: the theme, dark mode, the stylesheets applied to each
new scene, and the default font. The base implementation supports JavaFX's own `Modena` theme;
[tentackle-fx-atlanta](../../../../tentackle-fx-atlanta/src/main/docs/atlanta.md) replaces it by one
supporting the AtlantaFX themes.

```java
ThemeUtilities themes = ThemeUtilities.getInstance();
themes.setTheme(true, "Nord");                        // dark mode and theme at once
themes.darkModeProperty().addListener(...);           // react on changes
List<String> names = themes.getSupportedThemes();
```

Changing the theme or the mode re-applies the stylesheets to all scenes. Controllers declared with
`@FxControllerService(cssDarkAware = true)` load their `-dark` stylesheet when the view is created in
dark mode; for that reason, the RDC drops its cached dialogs when the mode changes. The user's choice is
persisted in the user preferences with `savePreferences(appClass, new ThemePreferences(darkMode, themeName))`
and restored with `loadPreferences(appClass)` and `applyThemePreferences(...)`; a `null` component of
`ThemePreferences` means "default" — for dark mode, the setting of the desktop. The RDC's
`DesktopApplication` does this at startup, also accepting `darkMode` and `themeName` as application
properties on the command line. The application generated by the
[archetype](../../../../tentackle-project-archetype/src/main/docs/tentackle-project-archetype.md) has a
dialog to change the theme.

### Utilities

A few more features of the components and of [`FxUtilities`](../java/org/tentackle/fx/FxUtilities.java),
the replaceable `@Service` behind many of them:

- **Copy to clipboard.** `FxTableView` and `FxTreeTableView` copy the selected cells with `Ctrl-C` —
  the texts as rendered, one CSV line per row, quoted only where necessary — and with rows and columns
  swapped with `Shift-Ctrl-C`; the copied text is shown in a note while `Ctrl` is held down. `setCopyToClipboardEnabled(...)` turns it off, or on for an `FxListView`.
- **Scrolling.** `scrollToCentered(row)` or `scrollToCentered(object)` of table, tree table and list
  views scroll so that the row is in the middle of the visible rows, not at the edge.
- **Stage placement.** `determinePreferredStageLocation(stage)` centers a new stage above its owner
  and keeps it on the screen; the RDC uses it for its CRUD and search dialogs.
- **Diagnostics.** `dumpComponentHierarchy(node)` describes the stage and the chain of containers down
  to a node, with their controllers and resource bundles — the text logged by `Ctrl-Shift` + right click,
  which tells a translator which bundle holds a label.

### Binding integration

`tentackle-fx` is the JavaFX implementation of the toolkit-neutral binding
contracts from `tentackle-core`. The package `org.tentackle.fx.bind` provides:

- `FxBindingFactory` / `DefaultFxBindingFactory` — the `@Service` factory;
- `FxComponentBinder` / `DefaultFxComponentBinder` — scans a controller and
  binds its `FxComponent`s to model members **by name**, optionally including
  inherited components or bindables;
- `FxComponentBinding`, `FxTableBinder`, `FxTableBinding` — the individual links.

Because the binding moves values in the model's type, the binder uses the
component's `ValueTranslator` to render and parse, and the component's mandatory
and validation state flows back into the view (the `tt-mandatory` / `tt-error`
styles and the error popups). For the full transfer/veto/validation mechanics
see [binding.md](../../../../tentackle-core/src/main/docs/binding.md).

#### Low-level listeners

Besides the binding, a control accepts plain listeners — a low-level alternative for views that
are not bound to a model, or for reacting to a change without a model property:

- a `ViewToModelListener` (`addViewToModelListener`) is invoked *after* the view was transferred to the
  model, i.e. when the user changed the value;
- a `ModelToViewListener` (`addModelToViewListener`) is invoked *before* the model is transferred to
  the view.

Registered on a container, the listener fires for the updates of any of its components. By default,
the listeners are invoked on every transfer, even if the value did not change;
`setListenerSuppressedIfModelUnchanged(true)` and `setListenerSuppressedIfViewUnchanged(true)` of a
component suppress these invocations.

```java
discountField.addViewToModelListener(this::updateTotals);
```

#### Changeable forms

`setChangeable(false)` on a container makes all its controls read-only at once — a form in view
mode. A control that must stay usable nevertheless, e.g. a button to open a detail view, is exempted
with `setContainerChangeableIgnored(true)`.

### Configurators

A `Configurator<T>` applies cross-cutting setup to every instance of an FX type
`T` (look-and-feel tweaks, default behavior). Configurators are *stateless*,
one per type, and their inheritance hierarchy mirrors the FX class hierarchy, so
configuring a subclass also runs the superclass configurator.
`FxFactory.getConfigurator(clazz)` resolves them; they are registered via
`@ConfiguratorService` and live in `org.tentackle.fx.component.config` and
`org.tentackle.fx.container.config`.

### Building views without FXML

Sometimes building a view programmatically is simpler than maintaining an FXML
file. Set `@FxControllerService(view = MyView.class)` and the framework creates
the view and controller **without** `FXMLLoader` at all. Fields in the view
annotated with `@NoFXML` are injected into the controller exactly as `@FXML`
would inject them; the `NoFXMLLinker` performs the wiring. The controller code
is unchanged, and switching back to FXML is just an annotation change. See
`org.tentackle.fx.nofxml`.

### Annotation processors

The `org.tentackle.fx.apt` package contains compile-time annotation processors
(`FxControllerServiceAnnotationProcessor`, `ValueTranslatorServiceAnnotationProcessor`,
`TableCellTypeServiceAnnotationProcessor`) that validate the service annotations
and emit the SPI registration metadata under `META-INF`, so discovery works on
both the module path and the class path.

---

## How It Fits Together

A typical screen comes to life like this:

1. An `@FxControllerService` controller class is discovered by `FxFactory`.
2. `Fx.load(MyController.class)` (or `FxFactory.createController(...)`) locates
   the FXML, resource bundle, and CSS by convention and loads the view — using
   the factory's `BuilderFactory`, which instantiates Tentackle components
   (`FxTextField`, `FxComboBox`, …) instead of plain JavaFX ones.
3. The controller's `@FXML` fields are injected; `validateInjections()` confirms
   none were silently missed.
4. Per-type `Configurator`s run against each component and container.
5. The controller's `FxComponentBinder` scans the components and binds them, by
   name, to the model's `@Bindable` members; each component gets the right
   `ValueTranslator` for its model type.
6. At runtime, binding transfers move values both ways in the model's type,
   running validation and reflecting mandatory/error state through CSS classes
   and popups.

---

## Package Reference

| Package                                                      | Role                                                                                                                                                                                                                                                                                            |
|--------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `org.tentackle.fx`                                           | Core contracts (`FxControl`, `FxComponent`, `FxContainer`, `FxController`), the `Fx` facade, `FxFactory`, `ValueTranslator`, `Configurator`, controller base classes, graphic/notification/interactive-error support                                                                            |
| `org.tentackle.fx.component`                                 | Extended components: `FxTextField`, `FxTextArea`, `FxComboBox`, `FxChoiceBox`, `FxCheckBox`, `FxRadioButton`, `FxToggleButton`, `FxButton`, `FxLabel`, `FxDatePicker`, `FxColorPicker`, `FxPasswordField`, `FxListView`, `FxTableView`, `FxTreeView`, `FxTreeTableView`, `FxHTMLEditor`, `Note`, `ExpandedTextPopup` |
| `org.tentackle.fx.component.delegate`                        | Component delegates holding the behaviour (`FxComponentDelegate`, `AbstractTextFieldDelegate`, per-component delegates)                                                                                                                                                                         |
| `org.tentackle.fx.component.config`                          | Per-component `Configurator`s                                                                                                                                                                                                                                                                   |
| `org.tentackle.fx.component.build`                           | Component builders used by the FXML builder factory                                                                                                                                                                                                                                             |
| `org.tentackle.fx.component.auto`                            | [Auto-completion](auto-completion.md) (popup, controller, suggestion generators: camel-case, regex, separator, …)                                                                                                                                                                               |
| `org.tentackle.fx.component.skin`                            | Custom control skins                                                                                                                                                                                                                                                                            |
| `org.tentackle.fx.container`                                 | Extended containers: `FxVBox`, `FxHBox`, `FxGridPane`, `FxBorderPane`, `FxAnchorPane`, `FxFlowPane`, `FxTilePane`, `FxStackPane`, `FxSplitPane`, `FxScrollPane`, `FxTabPane`, `FxTab`, `FxTitledPane`, `FxAccordion`, `FxToolBar`, `FxButtonBar`, `FxDialogPane`, `FxPane`, `FxTextFlow`        |
| `org.tentackle.fx.container.delegate` / `.config` / `.build` | Container delegates, configurators and builders                                                                                                                                                                                                                                                 |
| `org.tentackle.fx.bind`                                      | JavaFX binding implementation (`FxComponentBinder`, `FxComponentBinding`, `FxTableBinder`, `FxTableBinding`, `FxBindingFactory`)                                                                                                                                                                |
| `org.tentackle.fx.translate`                                 | Built-in `ValueTranslator`s for numeric, temporal, enum, money, color, `I18NText`, collection and string types                                                                                                                                                                                  |
| `org.tentackle.fx.table`                                     | Table configuration and cell-type infrastructure (`TableConfiguration`, `TableColumnConfiguration`, `TableConfigurationProvider`, `TableCellType`, `TableCellTypeFactory`, `FxTableColumn`/`FxTableCell` and tree variants, `TotalsTableView`)                                                  |
| `org.tentackle.fx.table.type`                                | Concrete `TableCellType`s per value class                                                                                                                                                                                                                                                       |
| `org.tentackle.fx.nofxml`                                    | Build a view/controller without `FXMLLoader` (`NoFXML`, `NoFXMLLinker`)                                                                                                                                                                                                                         |
| `org.tentackle.fx.apt`                                       | Annotation processors validating the contracts of controllers, translators and cell types at compile time (see [Annotation Processors](../../../../tentackle-core/src/main/docs/annotation-processors.md))                                                                                       |
| `org.tentackle.fx.service`                                   | Module hook (`Hook`) registered as a `ModuleHook`                                                                                                                                                                                                                                               |

See also: [binding.md](../../../../tentackle-core/src/main/docs/binding.md) for
the model↔view binding mechanics, [validation.md](../../../../tentackle-core/src/main/docs/validation.md)
for how mandatory/changeable and validation messages reach the view, and
[pdo.md](../../../../tentackle-pdo/src/main/docs/pdo.md) for the domain objects
most often displayed and edited.
