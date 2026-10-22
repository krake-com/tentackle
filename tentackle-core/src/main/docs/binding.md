# Binding — Connecting Models to Views

## Overview and Motivation

The Tentackle binding API connects the *members* of plain Java objects (the
**model**) to the *components* of a user interface (the **view**), keeping the
two synchronized in both directions. A binding moves a value from the model into
the view (*to-view*) and writes an edited value back from the view into the
model (*to-model*), running validation, mandatory/changeable evaluation, and
veto-capable listeners along the way.

The model side is deliberately UI-agnostic. The base API in
`org.tentackle.bind` (module `tentackle-core`) knows nothing about JavaFX, Swing,
or any concrete widget toolkit — it works purely by reflection over annotated
members. A concrete toolkit binds to it by providing implementations of a
handful of interfaces. The reference implementation for JavaFX lives in
`org.tentackle.fx.bind` (module `tentackle-fx`).

Typical models are [PDOs](../../../../tentackle-pdo/src/main/docs/pdo.md) (Persistent Domain Objects) or controllers,
but any object will do — a bean, a record, an immutable value object, or a
controller field holding a `char[]`. The binding works *by declaration*, never
by keeping a reference to the bound object instance: the same binder can serve
many model instances over its lifetime.

### Why a dedicated binding layer?

- **Separation of concerns** — domain interfaces declare *what* is bindable with
  a single annotation; they never import a UI class. The view declares *which*
  widget displays a value through a naming convention. Neither side depends on
  the other.
- **No boilerplate glue code** — there is no `field.textProperty().bind(...)`
  per widget. A binder *scans* the controller, matches components to model
  members by name, and creates all bindings automatically.
- **Aggregate- and graph-aware** — bindings follow nested member paths
  (`customer.address.zip`), walking the object graph at transfer time. A `null`
  anywhere along the path simply clears the view instead of throwing.
- **Validation built in** — field- and getter-level validation annotations are
  discovered together with the binding, so mandatory/changeable state and
  validation messages flow to the view without extra wiring.
- **Immutable members supported** — members of immutable objects are written
  through a *from-method* that returns a new object; the binder propagates the
  replacement up the parent chain.

### Design principles

- The binding holds **no reference** to the bound model instance. The root
  object is resolved on each transfer (in the FX case, from the controller).
- Only the **leaves** of a binding hierarchy are actually bound. Annotated
  objects may contain further annotated members; the binder recurses and binds
  the terminal values.
- A binder is a **scanner and registry**, not a data holder. It discovers
  eligible components and bindable members and pairs them up.
- The base layer is **abstract**; concrete behavior (how to read/write a widget,
  what the root object is) is supplied by toolkit subclasses.

---

## Key Concepts

### Bindable — marking model members

`@Bindable` (`org.tentackle.bind.Bindable`) marks a field or method of any class
as eligible for binding. It targets `FIELD` and `METHOD` and is retained at
runtime.

```java
public interface UserPersistence {

  @Bindable
  boolean isLoginAllowed();

  @Bindable
  void setLoginAllowed(boolean loginAllowed);

  @Bindable
  TrackedList<UserGroup> getUserGroups();
}
```

It carries two attributes:

| Attribute | Meaning                                                                                                                                                                    |
|-----------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `options` | UI binding options as a comma-separated string, e.g. `@Bindable(options = "UC,MAXCOLS=5")`. Interpreted by the concrete binding (see [Binding options](#binding-options)). |
| `ordinal` | Sort key. The binder processes members ordered by `ordinal` then name. Useful when configurations (e.g. table column order) are generated from the binding.                |

An annotated *object* member may itself contain annotated members; only the leaf
values are bound. The class need not be a bean — getters, setters, fields, and
from-methods are all discovered.

### BindableElement — the reflective descriptor

A `BindableElement` is the cached, reflection-level description of one annotated
member: its camel-case name, its `ordinal` and `options`, and the
`Field` / getter `Method` / setter `Method` / *from*-`Method` that read and write
it. `DefaultBindableElement` implements the actual access logic:

- **Reading** (`getObject`): getter takes precedence over field.
- **Writing** (`setObject`): setter → from-method → field, in that order.
  Primitive targets receive a safe default when the incoming value is `null`.
- **Read-only** when there is no field, no setter, and no from-method.
- **Type** is derived lazily from the getter return type or field type.

`BindableCache` caches the sorted `BindableElement`s per class (declared-only or
including inherited), so the relatively expensive reflection scan happens once
per class. Annotated getters, setters, from-methods and fields of the same name
are merged into one element. For records, the from- or with-method of an annotated
component is picked up automatically, since the DTO wurblet does not annotate it.

### From- or with-method — binding immutable members

When a member belongs to an immutable object, there is no setter. Instead, the
member may expose a *from-method* or a *with-method* that takes the new value and returns a **new**
immutable instance. On a to-model transfer the binding calls that method,
gets the replacement object, and then walks **up** the parent chain writing the
replacement into each parent until it finds a parent with a setter or field
(`AbstractBinding.setModelValue`). If the chain cannot absorb the replacement, a
`BindingException` is thrown.
This feature is essential for application-specific DataTypes, for example, a
Position3D that is immutable but can be constructed from a 3-tuple of doubles.

### BindingMember and the binding path

A `BindingMember` is one node in a *declaration chain*. Each binding records the
chain of parent members (`getParents()`) leading to the bound leaf member
(`getMember()`). The dotted concatenation of member names is the **binding
path**, e.g. `customer.address.zip`. `DefaultBindingMember` wraps a
`BindableElement` together with its position in the chain.

At transfer time the binding resolves the actual parent object by starting at the
*root object* and calling `getObject` along the chain
(`AbstractBinding.getParentObject`). Any `null` encountered stops the walk and
results in the view being cleared rather than an exception.

### Binding — one model member ↔ one view component

`Binding` is the link between a single bound member and a single view component.
It exposes the four fundamental transfer operations:

| Method                  | Direction                             |
|-------------------------|---------------------------------------|
| `getModelValue()`       | reads the value out of the model      |
| `setViewValue(Object)`  | pushes a value into the view          |
| `getViewValue()`        | reads the current value from the view |
| `setModelValue(Object)` | writes a value back into the model    |

`AbstractBinding` implements the model-side logic (path walking, listener firing,
veto handling, validation) and leaves the toolkit-specific parts abstract:
`updateView(value)`, `getViewValue()`, `getBoundRootObject()`, `getViewType()`,
`viewComponentToString()`, `isValidationRequired()`. A binding also manages its
own listener lists, its validators, and the mandatory/changeable state of the
bound element.

### Binder — the scanner and registry

`Binder` owns a set of bindings for one view. Its central job is to **scan** a
view, find eligible components and annotated model members, and pair them up by
binding path. Key responsibilities:

- **Binding modes** — `bind()` (declared members and components only),
  `bindAllInherited()`, and (FX) `bindWithInheritedBindables()` /
  `bindWithInheritedComponents()`. The declared-only path is the fast path; the
  inherited variants are more expensive and only run when components remain
  unbound.
- **Registry** — `getBinding(path)`, `addBinding`, `removeBinding`, `unbind`,
  `getBindings`.
- **Completeness check** — `assertAllBound()` throws a `BindingException` listing
  every component that was left unbound.
- **Binding properties** — a `key → value` map (`putBindingProperty` /
  `getBindingProperty`) for extra context a component may need but the model does
  not provide, most commonly the `DomainContext`:

  ```java
  getBinder().putBindingProperty(DomainContext.class, pdo.getDomainContext());
  // ...later, inside a component or translator:
  DomainContext ctx = getBinder().getBindingProperty(DomainContext.class);
  ```

- **Listeners and validation scope** — view/model/validation listeners and the
  active `ValidationScope` (the `InteractiveScope` by default) are held at the
  binder level and shared by its bindings.

`AbstractBinder` provides the bookkeeping (unique instance number for ordering,
property map, listener lists, scope) and leaves the actual scan,
`doBind(...)`, to the concrete subclass.

### BindingFactory — pluggable construction

`BindingFactory` creates `BindingMember`s and `BindableElement`s and owns the
`BindableCache`. It also maintains a *model class → element class* mapping so an
application can register a custom `BindableElement` subclass for specific model
types. `AbstractBindingFactory` implements the generic logic, including
superclass-walking lookup of the element class, with results memoized in a
concurrent map.

The concrete factory adds the toolkit pieces and is discovered as a service.

---

## How a Binding Runs

### To-view (model → view)

1. The binder/binding resolves the parent object by walking the member path from
   the root object.
2. `getModelValue()` reads the leaf value (getter or field).
3. `ToViewListener`s registered on the binding, then those on the binder, fire; any
   may throw a `BindingVetoException` to suppress the update (optionally requesting
   a resync).
4. Fixed mandatory/changeable validators are evaluated and pushed to the
   component (it may become required or read-only).
5. `updateView(value)` sets the value into the widget.

### To-model (view → model)

1. `getViewValue()` reads the current widget value.
2. `ToModelListener`s on the binding, then those on the binder, fire *before* the
   model is written; a veto suppresses the write.
3. `member.setObject(parent, value)` writes the value (setter → from-method →
   field). If a from-method returned a new object, the binding propagates the
   replacement up the parent chain.
4. The binder flags that mandatory/changeable state may have changed and need a
   refresh.

### Vetoes and resync

A listener throws `BindingVetoException` to stop a transfer. The exception
carries two flags:

- `resyncRequested` — re-push the *other* side's current value so model and view
  stay consistent: on a rejected to-model write, the view is refreshed from the
  model; on a rejected to-view update, the model is updated from the view's
  current value. The resync itself bypasses the resync logic, so it cannot loop.
- `rethrowEnabled` — after processing, rethrow as an unchecked `BindingException`
  rather than swallowing silently.

`BindingException` is the unchecked runtime exception used throughout the API
(unbound components, inaccessible members, failed from-method propagation, etc.).

### Validation, mandatory and changeable

Validators are discovered from the bound member's field/getter annotations
(`AbstractBinding.determineValidators`) and filtered by a binding scope that
always includes the mandatory and changeable scopes plus the binder's active
scope. `Binding.validate()` runs them and fires `ValidationListener`s.

Mandatory and changeable state can be **fixed** (constant for the binding) or
**dynamic** (recomputed as the model changes — e.g., a field becomes mandatory
only when another field has a certain value). Fixed evaluators are applied by the
binding on each to-view transfer; dynamic evaluators are collected by the binder
and refreshed via `requestMandatoryUpdate()` / `requestChangeableUpdate()` after
model changes.

---

## The JavaFX Implementation

The `tentackle-fx` module implements the API for JavaFX in
`org.tentackle.fx.bind`. The concrete factory `DefaultFxBindingFactory` is
annotated `@Service(FxBindingFactory.class)` and obtained as a singleton via
`FxBindingFactory.getInstance()` (resolved through `ServiceFactory`, so it works
both modular and non-modular).

| Base interface   | FX interface          | FX default implementation   |
|------------------|-----------------------|-----------------------------|
| `BindingFactory` | `FxBindingFactory`    | `DefaultFxBindingFactory`   |
| `Binder`         | `FxComponentBinder`   | `DefaultFxComponentBinder`  |
| `Binding`        | `FxComponentBinding`  | `DefaultFxComponentBinding` |
| `Binder`         | `FxTableBinder<S>`    | `DefaultFxTableBinder`      |
| `Binding`        | `FxTableBinding<S,T>` | `DefaultFxTableBinding`     |

### Controllers, components, and matching by name

A `FxController` owns its binder lazily (`AbstractFxController.getBinder()` →
`FxBindingFactory.createComponentBinder(this)`). The controller *is* the root
object: `DefaultFxComponentBinding.getBoundRootObject()` returns the controller,
and bound model members are reached as fields of the controller annotated
`@Bindable`.

The binder scans the controller for `FxComponent` fields (typically `@FXML`
widgets) and the `@Bindable` members reachable from the controller, then matches
components to binding paths **by name**. For each component it derives one or more
candidate binding paths from the field name:

- the field name itself (`userNameField`);
- the field name with a trailing component-type name removed
  (`userNameField` → `userName`, since the widget is an `FxTextField`);
- the field name with a well-known suffix removed. The suffix list is
  `DefaultFxComponentBinder.bindingPathSuffixes` =
  `{ "Field", "Node", "Comp", "Component", "Control" }` and is application-modifiable.

So a controller field `FxTextField userNameField` binds to the model path
`user.userName` when the controller has a `@Bindable User user`. An application
may instead set an explicit path with `FxComponent.setBindingPath(...)`.

The binder recurses through nested annotated members (with loop detection bounded
by the longest component path) so deep paths such as
`customer.address.zip` resolve automatically. `assertAllBound()` reports any
`FxComponent` left without a binding.

### A complete example

From the archetype-generated `myapp` project, `ChangePasswordView` binds three
`char[]` controller fields directly to password widgets purely by name:

```java
@FxControllerService
public class ChangePasswordView extends AbstractFxController {

  @Bindable private char[] oldPassword;
  @Bindable private char[] newPassword;
  @Bindable private char[] confirmedNewPassword;

  @FXML private FxPasswordField oldPasswordField;          // -> oldPassword
  @FXML private FxPasswordField newPasswordField;          // -> newPassword
  @FXML private FxPasswordField confirmedNewPasswordField; // -> confirmedNewPassword
  // ...

  private void setUser(User user, boolean admin) {
    // ...
    getContainer().updateView();   // push model values into the widgets
  }
}
```

Here the model lives directly on the controller. `UserEditor` instead binds a
whole PDO and supplies its domain context as a binding property:

```java
@FxControllerService
public class UserEditor extends PdoEditor<User> {

  @Bindable private User user;

  @FXML private FxTextField userNameField;            // -> user.userName
  @FXML private FxCheckBox  userLoginAllowedField;    // -> user.loginAllowed
  @FXML private FxTableView<UserGroup> userUserGroupsNode; // -> user.userGroups

  @Override
  public void setPdo(User pdo) {
    user = pdo;
    getBinder().putBindingProperty(DomainContext.class, pdo.getDomainContext());
  }
}
```

The binding is triggered by the framework after the controller is loaded; the
mode (declared-only vs. inherited) is selected via the controller's binding
configuration (`DefaultFxFactory` maps it to `bind()`,
`bindWithInheritedComponents()`, `bindWithInheritedBindables()` or
`bindAllInherited()`).

### Value translation

A widget rarely speaks the model's type directly: a text field shows a `String`,
but the bound member may be a `BigDecimal`, a `LocalDate`, an `enum`, a `char[]`,
or an application-specific money type. The bridge is the `ValueTranslator<M,V>`
(`org.tentackle.fx.ValueTranslator`), where `M` is the model type and `V` the
view type. It exposes two functions — `toViewFunction()` (M → V) and
`toModelFunction()` (V → M), with the convenience methods `toView(M)` and
`toModel(V)`. Tentackle deliberately calls them *translators* rather than
*converters* so they are not confused with JavaFX `StringConverter`s.

**Why this matters: the view and the controller carry no type-specific code.**
Because the translator encapsulates *all* parsing, formatting, and conversion, the
view and the controller never deal with model data types:

- The **view** (FXML, CSS, widget choice) describes only the *look* — which
  component, its layout, and styling. It is completely independent of the model's
  data types. An `FxTextField` is just a text field; it does not know or care
  whether it ultimately edits a number, a date or a string.
- The **controller** only declares `@Bindable` model members and `@FXML`
  components. There is no `Integer.parseInt(...)`, no `DateTimeFormatter`, no
  per-field `StringConverter`, no validation of raw text — none of the
  conversion plumbing that normally clutters UI code.

A direct consequence: changing a model field's type (say `int` → `BigDecimal`)
requires **no change to the view or the controller**. The binding simply selects
a different translator for the new type. The look stays; only the model evolves.

**How a translator is chosen.** When a binding is created it applies the member's
type to the component (`DefaultFxComponentBinding.applyType()` →
`FxComponent.setType(member.getType())`). For text components,
`FxTextComponentDelegate.setType` then lazily creates a translator via
`FxFactory.createValueTranslator(type, String.class, component)` — *unless* the
application has already set one with `setValueTranslator(...)`. The factory
(`DefaultFxFactory`) builds a registry at startup by scanning every
`@ValueTranslatorService(modelClass = ..., viewClass = ...)` provider through the
[`ServiceFinder` SPI](../../../../tentackle-common/src/main/docs/services.md),
keyed by `(modelClass, viewClass)`. Lookup
walks up the model's superclasses (`DefaultClassMapper.mapLenient`), so one
`Number`- or `Temporal`-based translator covers all its subclasses.

Out of the box (`org.tentackle.fx.translate`) the framework ships translators for
`String` ↔ the numeric types (`Integer`, `Long`, `Short`, `Byte`, `Double`,
`Float`, `BigDecimal`/money, fraction numbers), `String` ↔ the temporal types
(`LocalDate`, `LocalTime`, `LocalDateTime`, `Instant`, offset/zoned variants,
legacy `Date`/`Timestamp`), enums, colors, `char[]` and `I18NText`, an
`IdentityTranslator` for the pass-through case, plus list- and tree-oriented
translators for tables and trees. An application adds support for a new type
simply by publishing its own `@ValueTranslatorService` provider — no change to
the binding layer.

**At transfer time** the component delegates conversion to its translator:
`setViewValue(value)` runs `translator.toView(value)` before pushing the result
into the widget, and `getViewValue()` runs `translator.toModel(...)` on the raw
widget value. The binding itself stays type-agnostic.

**The same translators serve table and tree-table cells.** Value translation is
not limited to single-component (`FxComponentBinding`) bindings. When a
`ProductNumber` (or any other type) appears as a *column* of a table or
tree-table, the cell obtains a translator from the very same factory and
registry: `FxTableCell` and `FxTreeTableCell` call
`FxFactory.createValueTranslator(editor.getType(), cellType.getEditorType(),
editor)`, then use `translator.toView(...)` to render the cell and
`translator.toModel(...)` to commit an edit (see
[Table binding](#table-binding)). A type therefore needs **one** translator to be
displayed and edited everywhere — in form fields, in table columns, and in
tree-table columns alike — with no per-cell formatting code and no duplicate
registration.

**Advanced behavior** the translator interface also supports:

- `isLenient()` — parse partial/loose input to the model without throwing.
- `isMappingIncomplete()` with `saveModelValue` / `isModelModified` — for types
  like `I18NText` where the view may look unchanged while the model has actually
  changed; lets the binding still detect a modification.
- `needsToModelTwice()` — forces a second to-model round-trip when formatting can
  alter the value (e.g., a `Double` shown with scale 2 must re-read `1.21` after
  the view reformats `1.211111`).
- `bindingPropertiesUpdated()` — invoked by the binder's
  `bindingPropertiesUpdated()` for every bound component's translator whenever a
  binding property changes. A translator that depends on, say, the
  `DomainContext` set via `getBinder().putBindingProperty(DomainContext.class, ...)`
  (see the `UserEditor` example above) re-initializes itself here.

#### A worked example: a domain value type with live input assistance

The built-in translators cover the common scalar types, but the real strength of
the mechanism shows when an application teaches the framework one of its own
domain types. The following translator, taken from a production project, binds an
industrial *product number* — an aluminum-coil identifier with a fixed composite
layout `AAAA-QQQ-TTT-WWWW-LLLL-XXX` (alloy, quality, thickness, width, length and
an optional test segment, each of a fixed width defined as a constant on the
model class). The model type `ProductNumber` is a domain value object; the view
is an ordinary text field.

```java
@ValueTranslatorService(modelClass = ProductNumber.class, viewClass = String.class)
public class ProductNumberTranslator extends ValueStringTranslator<ProductNumber>
       implements UnaryOperator<TextFormatter.Change> {

  private boolean relaxDashHandling;

  public ProductNumberTranslator(FxTextComponent component) {
    super(component);
    if (component instanceof TextInputControl) {
      // install ourselves as the field's text formatter for live input assistance
      ((TextInputControl) component).setTextFormatter(new TextFormatter<>(this));
    }
  }

  @Override
  public Function<ProductNumber, String> toViewFunction() {
    relaxDashHandling = false;
    return m -> m == null ? null : m.toString();          // canonical dashed form
  }

  @Override
  public Function<String, ProductNumber> toModelFunction() {
    return s -> {
      if (s == null) {
        return null;
      }
      // normalize each dash-separated segment to its fixed width,
      // left-padding alloy with spaces and the numeric segments with zeros,
      // and reject anything that violates the segment-length rules...
      // (see malformedProductNumber below)
      return new ProductNumber(buf.toString(), isLenient());
    };
  }

  private void malformedProductNumber(int errorOffset) {
    getComponent().setError(PlsblGuiBundle.getString("malformed product number"));
    getComponent().setErrorOffset(errorOffset);           // mark the exact position
    getComponent().setErrorTemporary(true);
    throw new FxRuntimeException("malformed product number at " + errorOffset);
  }

  @Override
  public TextFormatter.Change apply(TextFormatter.Change t) {
    t.setText(t.getText().toUpperCase());                 // force upper case
    // ...auto-insert a dash once the current segment is complete and the
    // text typed so far is well-formed, so the user need not type dashes...
    return t;
  }
}
```

What this single, self-contained class accomplishes — and where that code would
otherwise have to live:

- **It registers itself.** The `@ValueTranslatorService(modelClass =
  ProductNumber.class, viewClass = String.class)` annotation publishes the class
  through the `ServiceFinder` SPI. From then on, *any* text field bound to *any*
  `ProductNumber` member, in any controller, automatically uses this translator —
  there is no registration call, no controller wiring, no FXML attribute. Dropping
  the class into the GUI module is the entire integration.
- **It owns both conversions.** `toViewFunction()` renders the model as its
  canonical dashed string; `toModelFunction()` parses free-form user input,
  normalizing each segment to its fixed width and validating the layout. The view
  never sees a `ProductNumber`, and the controller never parses or formats one.
- **It owns the input ergonomics.** By also implementing
  `UnaryOperator<TextFormatter.Change>` and installing itself as the field's
  `TextFormatter`, the translator turns the otherwise tedious fixed-width format
  into a fast, guided entry. It upper-cases input and — the key ergonomic feature
  — **autocompletes the segment separators**: as soon as a segment reaches its
  defined width and everything typed so far is well-formed, the next dash is
  inserted automatically and the caret advanced. The user types only the
  significant characters (`AA12010…`) and the field fills in the canonical
  `AAAA-QQQ-TTT-WWWW-LLLL-XXX` skeleton (`AA12-010-…`) as they go, and the 
  matching product numbers automatically pop up as well. Because this behavior is part of how the *type* is
  edited, it lives with the type — not duplicated in every view that shows a
  product number, and it works identically in a form field or a table cell.
- **It owns error reporting.** On malformed input it sets a localized, positioned
  error directly on the component (`setError` / `setErrorOffset` /
  `setErrorTemporary`) and aborts the parse with an `FxRuntimeException`. The
  binding surfaces the marker; the controller writes no validation code.
- **It honors lenient mode.** Both parsing (`new ProductNumber(text,
  isLenient())`) and the separator autocompletion consult the inherited
  `isLenient()` flag, so the same translator serves strict entry forms and relaxed
  search fields without change.
- please note that it's recommended to use the FX base classes in the FXML file,
  such as `TextField`, as they will be upgraded to the FX-specific classes
  by the `FxBuilderFactory`.
- it is possible to use the binding without FXML completely by providing a
  manually written view and annotating its bindable components with `@NoFXML`.
  So you can switch between FXML and non-FXML without changing the controller.
- manually writing traditional binding code is still possible, since each
  control can opt out via `setBindable(false)` from the framework's automatic
  binding.


The payoff restated: the FXML for a product-number field is just an
`TextField`, and the controller just declares `@Bindable ProductNumber product`
(or binds a PDO whose member is a `ProductNumber`). All knowledge of the type —
its format, parsing, validation, error positioning, and separator autocompletion —
is concentrated in one place and reused everywhere the type appears, whether the
product number is shown in a form field, a table column, or a tree-table column.

### Binding options

For text components, `@Bindable(options = "...")` (or options set on the
component) tune input behavior. 
See [model-definition.md](../../../../tentackle-model/src/main/docs/model-definition.md#binding-options)
for details, and [tentackle-fx](../../../../tentackle-fx/src/main/docs/fx.md#text-input-properties) for the
component properties they are mapped to.

For views without a model to bind to, the JavaFX controls also accept
[low-level listeners](../../../../tentackle-fx/src/main/docs/fx.md#low-level-listeners) invoked on every
transfer between view and model.

### Table binding

`FxTableBinder<S>` / `FxTableBinding<S,T>` bind the columns of a table to the
members of the row type `S`. Instead of a single root object resolved from a
controller, each binding's root is the **row object**, set per row via
`FxTableBinding.setBoundRootObject(S)`. The table binder works from a
`TableConfiguration<S>`, pairs `@Bindable` members of `S` with column
configurations, and (like the component binder) reports bound and unbound
columns. This drives both how cell values are read/written and how column
configurations (order, etc.) are generated from the model.

---

## Extending the API

- **Custom binding for a component type** — register a `FxComponentBinding`
  subclass with `FxBindingFactory.setComponentBindingClass(componentClass,
  bindingClass)`; the factory instantiates it (via a constructor taking
  `FxComponentBinder, BindingMember[], BindingMember, FxComponent, String`) when
  binding components of that class or a subclass.
- **Custom bindable element** — register a `BindableElement` subclass per model
  class with `BindingFactory.putBindableElementClass(modelClass, elementClass)`.
- **Custom suffixes** — replace `DefaultFxComponentBinder.bindingPathSuffixes` to
  change how component field names are reduced to binding paths.
- **A new toolkit** — implement `Binder`, `Binding` and `BindingFactory` (the
  abstract base classes carry most of the model-side logic) and publish the
  factory as a `@Service`.

---

## Package Reference

### `org.tentackle.bind` (tentackle-core)

| Type                                         | Role                                                                       |
|----------------------------------------------|----------------------------------------------------------------------------|
| `Bindable`                                   | annotation marking a bindable member (`options`, `ordinal`)                |
| `BindableElement` / `DefaultBindableElement` | reflective descriptor and access logic for a member                        |
| `BindableCache` / `DefaultBindableCache`     | per-class cache of sorted bindable elements                                |
| `BindingMember` / `DefaultBindingMember`     | one node in the declaration chain (the binding path)                       |
| `Binding` / `AbstractBinding`                | one model-member ↔ view-component link; transfer, veto, validation         |
| `Binder` / `AbstractBinder`                  | scans a view, registers and manages bindings                               |
| `BindingFactory` / `AbstractBindingFactory`  | creates members/elements, owns the cache and class maps                    |
| `BindingEvent`                               | event carrying type, parent and value across a transfer                    |
| `ToViewListener` / `ToModelListener`         | veto-capable transfer listeners                                            |
| `ValidationListener` / `ValidationEvent`     | validation result notification                                             |
| `BindingVetoException`                       | thrown by listeners to suppress (and optionally resync/rethrow) a transfer |
| `BindingException`                           | unchecked runtime exception for binding failures                           |

### `org.tentackle.fx.bind` (tentackle-fx)

| Type                                               | Role                                        |
|----------------------------------------------------|---------------------------------------------|
| `FxBindingFactory` / `DefaultFxBindingFactory`     | `@Service` singleton factory for FX         |
| `FxComponentBinder` / `DefaultFxComponentBinder`   | binds a controller's `FxComponent`s by name |
| `FxComponentBinding` / `DefaultFxComponentBinding` | binds one member to one `FxComponent`       |
| `FxTableBinder` / `DefaultFxTableBinder`           | binds table columns to row-type members     |
| `FxTableBinding` / `DefaultFxTableBinding`         | binds one column to a row-type member       |

See also: [pdo.md](../../../../tentackle-pdo/src/main/docs/pdo.md) for the model objects most often bound, and
[trip.md](trip.md) for how those objects travel between JVMs.
