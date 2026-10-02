# GuiProvider — Per-Entity GUI Services

## Overview and Motivation

Almost everything the [Rich Desktop Client](rdc.md) does — searching, editing,
rendering tables and trees, drag & drop, context menus — is generic machinery
that works for *any* entity. What it cannot know is the entity-specific part:
which editor view to open for an `Invoice`, which icon a `User` shows in a tree,
which columns an `Article` table should display.

The [`GuiProvider<T>`](../java/org/tentackle/fx/rdc/GuiProvider.java) is the
single object that answers all of these questions for one PDO type. Exactly one
provider class exists per entity, and the framework consults it whenever a PDO
of that type meets the UI. It extends three smaller contracts:

- [`PdoProvider<T>`](../../../../tentackle-pdo/src/main/java/org/tentackle/pdo/PdoProvider.java)
  — the provider always operates on a concrete PDO instance (`getPdo()`).
- [`TableConfigurationProvider<T>`](../../../../tentackle-fx/src/main/java/org/tentackle/fx/table/TableConfigurationProvider.java)
  — it supplies the [table configuration](../../../../tentackle-fx/src/main/docs/tables.md)
  describing how PDOs of this type appear in tables and tree-tables.
- [`DomainContextProvider`](../../../../tentackle-pdo/src/main/docs/domain-context-provider.md)
  — it carries the domain context of its PDO.

Applications practically never implement the interface from scratch. They extend
[`DefaultGuiProvider`](../java/org/tentackle/fx/rdc/DefaultGuiProvider.java),
which implements the complete contract with sensible defaults, and override only
what differs — often just three methods. Together with the
[default editor](#the-default-editor) and [default finder](#the-default-finder),
even an entity whose provider overrides *nothing* is already searchable,
viewable and editable through generated views.

---

## Registration and Discovery

A provider is registered declaratively with the
[`@GuiProviderService`](../java/org/tentackle/fx/rdc/GuiProviderService.java)
annotation, which names the serviced PDO interface:

```java
@GuiProviderService(User.class)
public class UserGuiProvider extends DefaultGuiProvider<User> {

  public UserGuiProvider(User pdo) {
    super(pdo);
  }

  @Override
  public Node createGraphic() {
    return Fx.createGraphic(MyAppGraphicProvider.REALM, "user");
  }

  @Override
  public boolean isEditorAvailable() {
    return true;
  }

  @Override
  public UserEditor createEditor() {
    return Fx.load(UserEditor.class);    // an FXML controller
  }
}
```

`@GuiProviderService` is a [`@MappedService`](../../../../tentackle-common/src/main/docs/services.md),
so the *analyze* build step records the PDO-class → provider-class mapping under
`META-INF` — no runtime classpath scanning, working identically in modular
(JPMS) and classpath deployments. At runtime the
[`GuiProviderFactory`](../java/org/tentackle/fx/rdc/GuiProviderFactory.java)
(default: [`DefaultGuiProviderFactory`](../java/org/tentackle/fx/rdc/DefaultGuiProviderFactory.java))
resolves the mapping via a `ClassMapper`:

```java
GuiProvider<User> provider = GuiProviderFactory.getInstance().createGuiProvider(user);
```

The factory looks up the provider class registered for the PDO's *effective*
class, picks the public constructor taking the PDO as its single argument
(hence the mandatory `UserGuiProvider(User pdo)` constructor above), caches the
constructor and instantiates a fresh provider per call. A provider is therefore
cheap, short-lived and always bound to one PDO instance —
never hold state in it beyond the PDO itself.

Because different modules can register a provider for the same PDO type, the
usual [service override rules](../../../../tentackle-common/src/main/docs/services.md)
apply: an application module can replace the provider that a lower-level module
(such as the framework's own
[`SecurityGuiProvider`](../java/org/tentackle/fx/rdc/security/SecurityGuiProvider.java))
ships.

The annotation has two optional elements:

| Element    | Default | Meaning                                                                                                                             |
|------------|---------|-------------------------------------------------------------------------------------------------------------------------------------|
| `noBundle` | `false` | Declares that the provider ships **no resource bundle** (see [Internationalization](#internationalization)); avoids warnings in the i18n build goals. |
| `test`     | `true`  | Whether the provider participates in the generic [GuiProvider unit test](#testing).                                                 |

At compile time the
[`GuiProviderServiceAnnotationProcessor`](../java/org/tentackle/fx/rdc/apt/GuiProviderServiceAnnotationProcessor.java)
validates the contract (annotated type implements `GuiProvider`, etc.) before
the application even runs.

---

## The Contract at a Glance

The interface groups into seven responsibilities:

| Concern              | Methods                                                                                                                                                | Consumed by                                       |
|----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------|
| **Appearance**       | `createGraphic()`, `getBundle()`, `isBundleProvided()`                                                                                                 | tree/table cells, editors, i18n                     |
| **Editing**          | `isEditorAvailable()`, `isEditAllowed()`, `isViewAllowed()`, `createEditor()`                                                                          | `PdoCrud`, context-menu *edit*/*view* items         |
| **Searching**        | `isFinderAvailable()`, `createFinder()`                                                                                                                | `PdoSearch`                                         |
| **Tables**           | `createTableConfiguration()` (inherited), `createTableView()`                                                                                          | search-result tables, `Rdc` table helpers           |
| **Trees**            | `getTreeText(parent)`, `getToolTipText(parent)`, `createTreeItem()`, `getTreeCellFactory()`, `providesTreeChildObjects()`, `getTreeChildObjects(parent)` | `PdoTreeItem`, `PdoTreeCell`, search-result trees  |
| **Navigation hooks** | `getTreeRoot()`, `providesTreeParentObjects()`, `getTreeParentObjects(parent)`, `getTreeExpandMaxDepth()`, `stopTreeExpansion()` | application-specific navigable object trees only |
| **Drag & drop**      | `createDragboard(node)`, `isDragAccepted(event)`, `dropDragboard(dragboard)`                                                                           | tables, trees, PDO-aware components                 |

The tree methods take the *parent* PDO as an argument because the same entity
may render differently depending on where it hangs in a tree — an invoice
under a customer may show a different text than the same invoice under a
project.

The framework's own trees use only the methods in the *Trees* row. The *navigation hooks* are not
called by `PdoTreeItem` or any other framework class; they let an application build richer
navigable trees of its own (for example, with a button to show an object's parents) on top of the
same providers.

---

## DefaultGuiProvider — Out of the Box

[`DefaultGuiProvider`](../java/org/tentackle/fx/rdc/DefaultGuiProvider.java)
implements every method of the contract and is designed as an adapter: override
selectively, call `super` for the rest. What the defaults do:

| Method                                     | Default behavior                                                                                                                                         |
|--------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `createGraphic()`                          | The generic `"object"` icon via `Fx.createGraphic`.                                                                                                       |
| `isBundleProvided()` / `getBundle()`       | Loads the [provider bundle](#level-2-the-provider-bundle) named after the provider class via `BundleFactory`; auto-detects its presence (see below).      |
| `isEditorAvailable()`                      | `false` — an entity opts in to editing.                                                                                                                   |
| `isEditAllowed()` / `isViewAllowed()`      | `isEditorAvailable() && pdo.isEditAllowed()` (resp. `isViewAllowed()`) — combines UI capability with the PDO's **domain and security rules**.             |
| `createEditor()`                           | A generated [`DefaultPdoEditor`](#the-default-editor) (throws if `isEditorAvailable()` is `false`).                                                       |
| `isFinderAvailable()`                      | `false` — an entity opts in to searching.                                                                                                                 |
| `createFinder()`                           | The [`DefaultPdoFinder`](#the-default-finder) (throws if `isFinderAvailable()` is `false`).                                                               |
| `createTableConfiguration()`               | [Generated from the PDO model via reflection](#table-configuration-and-table-view).                                                                       |
| `createTableView()`                        | Creates, binds and configures an `FxTableView` from the table configuration.                                                                              |
| `createDragboard(node)`                    | Every PDO **is** a drag source: puts the PDO's `IdentifiableKey` on the dragboard via `RdcUtilities`.                                                     |
| `isDragAccepted(event)`                    | `false` — a PDO does not accept drops unless it says so.                                                                                                  |
| `dropDragboard(dragboard)`                 | Extracts the dropped PDOs from the dragboard and calls `dropPdo(pdo)` for each — override `dropPdo` to implement the drop.                                |
| `getTreeRoot()`                            | The PDO itself.                                                                                                                                            |
| `getTreeText(parent)`                      | `pdo.toString()`.                                                                                                                                          |
| `getToolTipText(parent)`                   | The PDO's long text if it implements `ShortLongText`, else the tree text.                                                                                 |
| `providesTreeChildObjects()` / `getTreeChildObjects(parent)` | `false` / empty — leaf node.                                                                                                              |
| `providesTreeParentObjects()` / `getTreeParentObjects(parent)` | `false` / empty.                                                                                                                        |
| `getTreeExpandMaxDepth()` / `stopTreeExpansion()` | `0` (no limit) / `false`.                                                                                                                          |
| `createTreeItem()` / `getTreeCellFactory()` | The standard lazily-expanding `PdoTreeItem` / `PdoTreeCell` via `Rdc`.                                                                                    |
| `dropPdo(pdo)`                             | Throws an `RdcRuntimeException` — override it together with `isDragAccepted(event)`.                                                                      |

Note the division of labor in the editing guards: `isEditorAvailable()` states
whether an editor *exists* for the entity, while `isEditAllowed()` and
`isViewAllowed()` additionally ask the PDO — and thus the domain logic and the
[security layer](../../../../tentackle-pdo/src/main/docs/security.md) — whether
the *current user* may edit or view *this instance*. The context menu grays out
its *edit*/*view* items accordingly.

### Table configuration and table view

The provider is the [`TableConfigurationProvider`](../../../../tentackle-fx/src/main/java/org/tentackle/fx/table/TableConfigurationProvider.java)
for its entity, so it decides how the entity appears in every
[table and tree-table](../../../../tentackle-fx/src/main/docs/tables.md) —
search results, related-PDO tables, tree-table columns.

**`createTableConfiguration()`** — the default builds the configuration from
the PDO model via reflection:

- Every model attribute whose getter is [`@Bindable`](../../../../tentackle-core/src/main/docs/binding.md)
  becomes one column; the column name is the attribute's **binding path**.
  The bookkeeping attributes inherited from `PersistentObject` (`id`, `serial`, …)
  and the object-id columns of relations are skipped.
- The **displayed column header** comes from the provider's resource bundle
  (key = attribute name) if one is provided, otherwise the capitalized attribute
  name (`unitPrice` → `UnitPrice`).
- If no bindable attribute exists at all, the configuration falls back to `id`
  and `serial` columns, so a table is never empty.
- If a bundle is provided, its base name is stored in the configuration
  (`setBaseBundleName`) — used by the BundleMonkey translation tool to navigate
  from a rendered table back to the bundle that labels it.

**`createTableView()`** — turns the configuration into a live `FxTableView`:
it binds the columns according to the configuration's binding type (`YES`,
`INHERITED` or `NO`), asserts that all columns are actually bound (catching
typos in binding paths at once), creates the view via `Fx.create(TableView.class)`
and applies the configuration. The table name (default: the basename of the
PDO's effective class) keys the [preferences](../../../../tentackle-core/src/main/docs/preferences.md)
under which column widths, order, visibility and sorting are stored per user.

Subclasses that want hand-picked columns override `createTableConfiguration()`
and start from the empty-configuration helpers:

```java
@Override
public TableConfiguration<Invoice> createTableConfiguration() {
  TableConfiguration<Invoice> config = createEmptyTableConfiguration();
  config.addColumn("invoiceNumber", getBundle().getString("invoiceNumber"));
  config.addColumn("customer.name", getBundle().getString("customer"));   // binding path across a relation
  config.addColumn("total", getBundle().getString("total"));
  return config;
}
```

`createEmptyTableConfiguration(String name)` creates a configuration with no
columns, applies the provider's bundle base name, and lets you pass a distinct
table *name* — useful when the same entity appears in differently laid-out
tables, since the name keys the table preferences.

### The default editor

If a subclass turns on `isEditorAvailable()` without overriding
`createEditor()`, the PDO is edited in a
[`DefaultPdoEditor`](../java/org/tentackle/fx/rdc/DefaultPdoEditor.java) — a
form **generated at runtime from the PDO model** via reflection. This is more
than a stopgap: for simple master-data entities it is often all you need, and
during early development every entity is instantly editable before any FXML
exists.

The generated form is a two-column `GridPane` (right-aligned label, control),
one row per bindable model attribute, where the control type is chosen from
the attribute's type:

| Attribute type                                             | Control                                          |
|------------------------------------------------------------|----------------------------------------------------|
| `Boolean` / `boolean`                                      | `CheckBox`                                        |
| enum                                                        | `ChoiceBox`                                       |
| PDO relation, related entity has a **preloading cache**     | `ComboBox` (populated from the cache)             |
| PDO relation, no preloading cache                           | `TextField`                                       |
| any other type, model option `LINES=n`                      | `TextArea`                                        |
| any other type                                              | `TextField`                                       |

Field widths honor the model: an explicit `COLS=n` option wins, otherwise text
fields size themselves to the attribute's maximum column count. Every control
is fully wired into the [binding layer](../../../../tentackle-core/src/main/docs/binding.md),
so editing, [validation](../../../../tentackle-core/src/main/docs/validation.md)
and save via the surrounding `PdoCrud` all work exactly as with a hand-written
editor. The initial focus goes to the first *changeable domain-key* attribute,
falling back to the first changeable control.

Labels come from the provider's bundle (key = attribute name — the same keys
that label the table columns) or, without a bundle, from the **attribute
comments in the PDO model** (see [Internationalization](#internationalization)).

For entities that may be inspected but not edited, wrap a plain view in a
[`PdoViewer`](../java/org/tentackle/fx/rdc/PdoViewer.java) and return `false`
from `isEditAllowed()` — see [rdc.md](rdc.md#read-only-views-with-pdoviewer).

### The default finder

Likewise, turning on `isFinderAvailable()` without overriding `createFinder()`
yields the [`DefaultPdoFinder`](../java/org/tentackle/fx/rdc/search/DefaultPdoFinder.java),
which covers the two most common search styles without any application code:

- If the entity provides a **normtext** (the normalized search text maintained
  by the persistence layer), the finder shows a single pattern field
  (input upper-cased automatically) and runs `selectByNormText(pattern)`.
  An empty pattern selects all.
- If the entity has **no normtext**, there are no criteria to enter: the finder
  hides itself and the search runs immediately — `selectAllCached()` when the
  entity has a preloading [PDO cache](../../../../tentackle-pdo/src/main/docs/cache.md),
  else `selectAll()`.

The default finder is also useful verbatim in application providers, e.g. for
small master-data entities where the result table *is* the search:

```java
@Override
public PdoFinder<User> createFinder() {
  @SuppressWarnings("unchecked")
  PdoFinder<User> finder = Fx.load(DefaultPdoFinder.class);
  finder.setSearchRunningImmediately(true);   // populate the result table right away
  return finder;
}
```

Note that a `DefaultPdoFinder` running its search immediately hides its pattern field as well, so the
user narrows the result with the search dialog's filter field instead.

Entities with richer criteria implement their own
[`PdoFinder`](../java/org/tentackle/fx/rdc/PdoFinder.java) controller and
return it from `createFinder()`; the surrounding
[`PdoSearch`](../java/org/tentackle/fx/rdc/search/PdoSearch.java) machinery
(criteria area, result table, background execution, CRUD on double-click)
stays the same.

---

## Internationalization

All texts a `GuiProvider` (and the views derived from it) presents are
localized through the framework's [i18n foundation](../../../../tentackle-common/src/main/docs/bundle.md).
The texts resolve through **four stacked levels** — each level refines the one
below it, and each is optional:

```
level 4   database-backed translations (tentackle-i18n)     runtime-editable overlay
level 3   locale-specific bundles  UserGuiProvider_de_AT →  ResourceBundle fall-back chain
                                   UserGuiProvider_de
level 2   the provider bundle      UserGuiProvider.properties
level 1   the PDO model            attribute names and comments
```

### Level 1: the PDO model

Without any bundle, all texts are derived from the
[PDO model](../../../../tentackle-model/src/main/docs/model-definition.md)
itself: table column headers show the capitalized attribute name, and the
default editor labels its fields with the **attribute comments** from the model
(`String name  name  the user's login name` → label *"the user's login name:"*).
The UI is fully functional but single-language — whatever language the model
is written in.

A provider that intentionally stays at this level declares
`@GuiProviderService(value = User.class, noBundle = true)` so the i18n build
goals do not warn about the missing bundle.

### Level 2: the provider bundle

The next level is a `.properties` file **co-located with the provider class**
(in `src/main/java`, per the framework's resource conventions) and named after
it: `UserGuiProvider.properties` next to `UserGuiProvider.java`. The keys are
the **attribute names** — one set of keys labels *both* the table columns and
the default editor's fields, so entity terminology stays consistent across
views:

```properties
# UserGuiProvider.properties
name=Name
comment=Description
loginAllowed=Login allowed?
```

`DefaultGuiProvider` detects the bundle automatically: `getBundle()` loads it
through the module-aware
[`BundleFactory`](../../../../tentackle-common/src/main/docs/bundle.md#the-bundle-factory)
(which is what makes co-located bundles work under JPMS, where a resource
bundle is normally invisible outside its own module), and `isBundleProvided()`
answers from the `@GuiProviderService` annotation if present — a first load
attempt otherwise. Because `@GuiProviderService` is itself a bundle-providing
annotation (analyzed by the `BundleAnalyzeHandler`), the bundle is recorded
under `META-INF/bundles` at build time and thus visible to the i18n tooling and
the runtime alike.

Custom editors and finders are `@FxControllerService` controllers with their
own co-located bundles, but the provider bundle remains available to them:
`createEditor()` for the default editor passes it along, and application
editors typically reuse the provider's keys for shared terminology.

### Level 3: locale-specific bundles

Translations are added per locale using the standard `ResourceBundle`
fall-back chain — `UserGuiProvider_de_AT` → `UserGuiProvider_de` →
`UserGuiProvider.properties` — so a more specific locale only overrides the
keys that actually differ:

```properties
# UserGuiProvider_de.properties
name=Name
comment=Beschreibung
loginAllowed=Anmeldung erlaubt?
```

*Which* locale applies is decided per thread by the
[`LocaleProvider`](../../../../tentackle-common/src/main/docs/bundle.md#the-locale-provider).
On the client that is simply the user's locale; in a multi-tier deployment the
server sets the thread-local locale per remote request from the calling
client's `SessionInfo` — one server serves every client in its own language,
including texts produced inside `GuiProvider`-related code paths that run
remotely.

At build time the
[check plugin](../../../../tentackle-check-maven-plugin/src/main/docs/tentackle-check-maven-plugin.md)
verifies that referenced keys resolve, and the
[i18n maven plugin](../../../../tentackle-i18n-maven-plugin/src/main/docs/tentackle-i18n-maven-plugin.md)
syncs the property files with the translation database.

### Level 4: database-backed translations

With the optional [`tentackle-i18n`](../../../../tentackle-i18n/src/main/docs/i18n.md)
module, stored translations **overlay** the property files at runtime — no
change to the provider or its bundles. A translator edits a text live (e.g.
with the BundleMonkey tool); the modification tracker invalidates the bundle
caches, and the new label appears on every client immediately. This is also
where the `baseBundleName` recorded in the table configuration pays off: the
tooling can navigate from a displayed table or editor straight to the bundle
and key that produced a label.

The framework's own texts (context menus, CRUD toolbar, dialogs — from bundles
such as `RdcFxRdcBundle`) ship already localized at levels 2–3, so an
application only translates what it adds: the entity terminology in its
provider bundles and its own views.

---

## Testing

[`tentackle-test-fx-rdc`](../../../../tentackle-test-fx-rdc/src/main/docs/tentackle-test-fx-rdc.md)
ships a generic `GuiProviderTest` base class (TestNG and JUnit 5 flavors) that
iterates over *all* registered `@GuiProviderService`s, instantiates each
provider against a fresh PDO and creates its editor and finder (if available),
graphic, table view and tree item — catching missing constructors, broken
bindings or unresolvable editors in the unit-test phase. A single subclass suffices:

```java
public class TestGuiProviders extends GuiProviderTest {
  TestGuiProviders() {
    super("com.example.myapp");    // restrict to the application's providers
  }
}
```

Providers annotated with `@GuiProviderService(..., test = false)` are skipped.

---

## See Also

- [tentackle-fx-rdc — Rich Desktop Client](rdc.md) — the module overview: CRUD machinery, trees, context menus, lifecycle.
- [Tables and Trees](../../../../tentackle-fx/src/main/docs/tables.md) — the table configuration infrastructure the provider feeds.
- [Resource Bundles](../../../../tentackle-common/src/main/docs/bundle.md) — bundle convention, `BundleFactory`, `LocaleProvider`.
- [Tentackle I18N — Database-backed Resource Bundles](../../../../tentackle-i18n/src/main/docs/i18n.md) — the live-editable translation overlay.
- [Services](../../../../tentackle-common/src/main/docs/services.md) — the `@MappedService` discovery behind `@GuiProviderService`.
- [Model Definition](../../../../tentackle-model/src/main/docs/model-definition.md) — attributes, comments and options the generated views build on.
- [tentackle-test-fx-rdc](../../../../tentackle-test-fx-rdc/src/main/docs/tentackle-test-fx-rdc.md) — testing GuiProviders.
