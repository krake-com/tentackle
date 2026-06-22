# tentackle-fx — The Extended JavaFX Layer

## Overview and Motivation

`tentackle-fx` is Tentackle's desktop UI foundation. It wraps the standard
JavaFX controls and containers in an *extended FX layer* that integrates
seamlessly with the rest of the framework: the [binding](../../../../tentackle-core/src/main/docs/binding.md)
API, [validation](../../../../tentackle-core/src/main/docs/validation.md),
[PDOs](../../../../tentackle-core/src/main/docs/pdo.md), internationalization,
theming and SPI-based service discovery.

The module lives in package `org.tentackle.fx` (JPMS module `org.tentackle.fx`)
and `requires transitive org.tentackle.core` together with the JavaFX modules
(`javafx.fxml`, `javafx.web`, `javafx.controls`, …) and the Ikonli icon packs.

### Why an extended FX layer?

Plain JavaFX gives you controls, FXML, properties and a controller hook. What it
does *not* give you is a way to connect those controls to a domain model without
hand-writing glue per field, nor a unified notion of "changeable", "mandatory",
"error" or "value of the model's type". Tentackle adds exactly that:

- **Every control is a first-class, bindable component.** `FxTextField`,
  `FxComboBox`, `FxTableView`, … each implement `FxComponent` and carry a
  *model type*, a *value translator*, mandatory/changeable/error state and
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
  controllers and table-cell types are all located through the `ServiceFinder`
  SPI, so the same code runs on the module path or the class path.

### Design principles

- **Wrap, don't replace.** Each Tentackle component *extends* its JavaFX
  counterpart, so anything you can do with a `TextField` you can still do with
  an `FxTextField`. The extras are additive.
- **Delegate over inheritance.** Because a component must already extend a
  concrete JavaFX class, the shared `FxComponent` behaviour cannot also be
  inherited from a common base class. Instead each component holds a
  **delegate** that implements the behaviour. This emulates the multiple
  inheritance that the PDO layer achieves with proxies.
- **Configure by convention, override by service.** Defaults come from naming
  conventions (FXML file next to the controller, bundle by class name) and
  from per-type *configurators*; applications override by contributing their
  own annotated services.
- **No state in shared singletons.** Factories, configurators and cell-type
  factories are stateless singletons located once and reused across all
  instances.

---

## Key Concepts

### FxControl, FxComponent and FxContainer

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
shared superclass. Tentackle therefore keeps that behaviour in a **delegate**:

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
substitute custom behaviour. The delegate base also defines the standard CSS
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

| Attribute | Default / convention |
|-----------|----------------------|
| `url()` | `<ControllerName>.fxml` (set `FXML_NONE` to skip the FXML loader) |
| `resources()` | `<ControllerName>.properties` (set `RESOURCES_NONE` for none) |
| `css()` | `<ControllerName>.css` (missing is fine) |
| `cssDarkAware()` | append `-dark` to the CSS file name in dark mode |
| `binding()` | `BINDING.YES`; also `NO`, `COMPONENT_INHERITED`, `BINDABLE_INHERITED`, `ALL_INHERITED` |
| `view()` | a view class to use instead of FXML (see *no-FXML* below) |
| `test()` | whether the controller is created and bound in unit tests |

> FXML, CSS and `.properties` files are co-located with the controller in
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

### Configurators

A `Configurator<T>` applies cross-cutting setup to every instance of an FX type
`T` (look-and-feel tweaks, default behaviour). Configurators are *stateless*,
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
   the FXML, resource bundle and CSS by convention and loads the view — using
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

| Package | Role |
|---------|------|
| `org.tentackle.fx` | Core contracts (`FxControl`, `FxComponent`, `FxContainer`, `FxController`), the `Fx` facade, `FxFactory`, `ValueTranslator`, `Configurator`, controller base classes, graphic/notification/interactive-error support |
| `org.tentackle.fx.component` | Extended components: `FxTextField`, `FxTextArea`, `FxComboBox`, `FxChoiceBox`, `FxCheckBox`, `FxRadioButton`, `FxToggleButton`, `FxButton`, `FxLabel`, `FxDatePicker`, `FxColorPicker`, `FxPasswordField`, `FxListView`, `FxTableView`, `FxTreeView`, `FxTreeTableView`, `FxHTMLEditor`, `Note` |
| `org.tentackle.fx.component.delegate` | Component delegates holding the behaviour (`FxComponentDelegate`, `AbstractTextFieldDelegate`, per-component delegates) |
| `org.tentackle.fx.component.config` | Per-component `Configurator`s |
| `org.tentackle.fx.component.build` | Component builders used by the FXML builder factory |
| `org.tentackle.fx.component.auto` | Auto-completion (popup, controller, suggestion generators: camel-case, regex, separator, …) |
| `org.tentackle.fx.component.skin` | Custom control skins |
| `org.tentackle.fx.container` | Extended containers: `FxVBox`, `FxHBox`, `FxGridPane`, `FxBorderPane`, `FxAnchorPane`, `FxFlowPane`, `FxTilePane`, `FxStackPane`, `FxSplitPane`, `FxScrollPane`, `FxTabPane`, `FxTab`, `FxTitledPane`, `FxAccordion`, `FxToolBar`, `FxButtonBar`, `FxDialogPane`, `FxPane`, `FxTextFlow` |
| `org.tentackle.fx.container.delegate` / `.config` / `.build` | Container delegates, configurators and builders |
| `org.tentackle.fx.bind` | JavaFX binding implementation (`FxComponentBinder`, `FxComponentBinding`, `FxTableBinder`, `FxTableBinding`, `FxBindingFactory`) |
| `org.tentackle.fx.translate` | Built-in `ValueTranslator`s for numeric, temporal, enum, money, color, `I18NText`, collection and string types |
| `org.tentackle.fx.table` | Table configuration and cell-type infrastructure (`TableConfiguration`, `TableColumnConfiguration`, `TableConfigurationProvider`, `TableCellType`, `TableCellTypeFactory`, `FxTableColumn`/`FxTableCell` and tree variants, `TotalsTableView`) |
| `org.tentackle.fx.table.type` | Concrete `TableCellType`s per value class |
| `org.tentackle.fx.nofxml` | Build a view/controller without `FXMLLoader` (`NoFXML`, `NoFXMLLinker`) |
| `org.tentackle.fx.apt` | Annotation processors emitting SPI metadata for controllers, translators and cell types |
| `org.tentackle.fx.service` | Module hook (`Hook`) registered as a `ModuleHook` |

See also: [binding.md](../../../../tentackle-core/src/main/docs/binding.md) for
the model↔view binding mechanics, [validation.md](../../../../tentackle-core/src/main/docs/validation.md)
for how mandatory/changeable and validation messages reach the view, and
[pdo.md](../../../../tentackle-core/src/main/docs/pdo.md) for the domain objects
most often displayed and edited.
