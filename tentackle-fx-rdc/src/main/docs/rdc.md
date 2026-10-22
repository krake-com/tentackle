# tentackle-fx-rdc — Rich Desktop Client

## Overview and Motivation

**RDC** stands for *Rich Desktop Client*. The `tentackle-fx-rdc` module is the
glue that turns the two lower layers it sits on — the
[PDO layer](../../../../tentackle-pdo/src/main/docs/pdo.md) and the
[extended JavaFX layer](../../../../tentackle-fx/src/main/docs/fx.md) — into a
ready-to-run desktop application.

Where `tentackle-fx` knows everything about JavaFX controls, controllers and
binding but nothing about persistence, and `tentackle-pdo` knows everything about
persistent domain objects but nothing about a UI, `tentackle-fx-rdc` is the only
module that knows about **both**. It answers the questions that arise as soon as a
PDO needs to be shown to and edited by a user:

- How is a PDO *searched for*, *displayed*, *edited* and *saved* through a UI?
- How does a PDO render itself in a table, a tree or a combo box?
- What happens when the user right-clicks a PDO — which context menu appears?
- How does the whole application *start up*, *log in* against a backend and
  *shut down* again?

The module is deliberately built around a small number of extension points so
that an application provides only the parts that are specific to its entities,
while the framework supplies the generic CRUD machinery, dialogs, background
execution and lifecycle.

### Design principles

- **One coordination object per entity.** Everything the UI needs to know about
  a PDO type is bundled behind a single interface — the
  [`GuiProvider`](../java/org/tentackle/fx/rdc/GuiProvider.java). Applications
  implement (or partially override) it per entity; the framework discovers the
  implementation via the SPI mechanism.
- **Generic CRUD, specific views.** The search-, edit- and tree machinery
  (`PdoSearch`, `PdoCrud`, `PdoController`, …) is entity-agnostic. The only
  entity-specific artifacts an application must supply are the *editor view*, the
  *finder view* and the *table/tree configuration*.
- **Never block the FX thread.** Persistence operations may go to a local
  database or across the network. RDC funnels them through a replaceable
  background executor and marshals the results back onto the FX application
  thread (see [Background execution](#background-execution)).
- **Location transparency, inherited.** Because PDOs are remoting-capable, the
  same UI code works whether the client talks to the database directly or through
  a middle-tier server — no `LazyInitializationException`, ever.
- **Convention with escape hatches.** Defaults (`DefaultGuiProvider`,
  `DefaultPdoFinder`, `DefaultPdoEditor`, `DefaultRdcFactory`) cover the common
  case; every one of them can be replaced or subclassed.

---

## Where it sits

```
tentackle-fx-rdc   ── knows PDOs *and* JavaFX ─────────────┐
        │ requires transitive                              │
        ├── org.tentackle.fx     (controls, controllers,   │  the only module
        │                         binding, tables)         │  that bridges the
        └── org.tentackle.pdo    (persistent domain        │  UI and the
                                  objects, sessions, DDD)   │  persistence world
                                                            ┘
```

The companion modules build on top of it:

| Module                    | Purpose                                                                                       |
|---------------------------|-----------------------------------------------------------------------------------------------|
| `tentackle-fx-rdc-poi`    | Excel (Apache POI) export of RDC tables.                                                      |
| `tentackle-fx-rdc-update` | Application self-update / deployment support.                                                 |
| `tentackle-test-fx-rdc`   | JUnit 5 / TestNG base classes for testing GuiProviders, controllers and table configurations. |

---

## Key Concepts

### GuiProvider — the per-entity hub

The [`GuiProvider<T>`](../java/org/tentackle/fx/rdc/GuiProvider.java) is the
central abstraction of the module — it has its own in-depth document,
[GuiProvider — Per-Entity GUI Services](gui-provider.md), covering the
`DefaultGuiProvider` defaults, the generated table configurations, the default
editor and finder, and the internationalization levels. In short: one
`GuiProvider` exists per PDO type and encapsulates *all* the UI behavior for
that entity. Its responsibilities fall into a few groups:

- **Identity / appearance** — `createGraphic()` for an icon, the resource bundle
  (`getBundle()`, `isBundleProvided()`).
- **Editing** — `isEditorAvailable()`, `isEditAllowed()`, `isViewAllowed()`,
  `createEditor()` returning a [`PdoEditor<T>`](../java/org/tentackle/fx/rdc/PdoEditor.java).
- **Searching** — `isFinderAvailable()`, `createFinder()` returning a
  [`PdoFinder<T>`](../java/org/tentackle/fx/rdc/PdoFinder.java).
- **Drag & drop** — `createDragboard(...)`, `isDragAccepted(...)`,
  `dropDragboard(...)`.
- **Tree rendering** — `getTreeText(...)`, `getToolTipText(...)`, `createTreeItem()`,
  `getTreeCellFactory()`, and the child objects `providesTreeChildObjects()` /
  `getTreeChildObjects(...)`. The remaining tree methods (`getTreeRoot()`,
  `getTreeParentObjects(...)`, `getTreeExpandMaxDepth()`, `stopTreeExpansion()`) are hooks
  for application-specific navigable trees; the framework's own trees don't call them.
- **Table rendering** — `createTableView()`.

Applications rarely implement the whole interface. They subclass
[`DefaultGuiProvider`](../java/org/tentackle/fx/rdc/DefaultGuiProvider.java) and
override only what differs. A provider is registered declaratively:

```java
@GuiProviderService(Invoice.class)
public class InvoiceGuiProvider extends DefaultGuiProvider<Invoice> {

  @Override
  public PdoEditor<Invoice> createEditor() {
    return Fx.load(InvoiceEditor.class);     // an FXML controller
  }

  @Override
  public PdoFinder<Invoice> createFinder() {
    return Fx.load(InvoiceFinder.class);
  }
}
```

The [`@GuiProviderService`](../java/org/tentackle/fx/rdc/GuiProviderService.java)
annotation is picked up at build time by the
[analyze phase](../../../../tentackle-common/src/main/docs/services.md), which generates the
`META-INF/services` entry, so the provider is found by
[`GuiProviderFactory`](../java/org/tentackle/fx/rdc/GuiProviderFactory.java) at
runtime — in both modular and non-modular deployments. Independently, an
[annotation processor](#annotation-processors-apt) validates at compile time that the
provider satisfies the contract.

#### Read-only views with PdoViewer

When an entity can be looked at but not edited, wrap a plain view controller in a
[`PdoViewer`](../java/org/tentackle/fx/rdc/PdoViewer.java) and disable editing:

```java
@Override
public PdoEditor<Incident> createEditor() {
  return new PdoViewer<Incident, IncidentView>(Fx.load(IncidentView.class));
}

@Override
public boolean isEditAllowed() {
  return false;     // grays out the "edit" context-menu item
}
```

### Rdc and RdcFactory — the entry points

[`Rdc`](../java/org/tentackle/fx/rdc/Rdc.java) is a static convenience facade
(mirroring the role `Fx` plays in `tentackle-fx`) that bundles the most common
operations so calling code reads naturally. The heavy lifting is delegated to the
SPI-resolved [`RdcFactory`](../java/org/tentackle/fx/rdc/RdcFactory.java)
(default: [`DefaultRdcFactory`](../java/org/tentackle/fx/rdc/DefaultRdcFactory.java)).

Typical one-liners:

```java
// open a non-modal edit dialog for a PDO
Rdc.displayCrudStage(invoice, /*editable*/ true, ownerWindow);

// open a non-modal search dialog for an entity, allowing to create new ones
Rdc.displaySearchStage(on(Invoice.class), ownerWindow, /*createPdoAllowed*/ true);

// render a PDO in a tree
Rdc.showTree(owner, rootPdo);

// build cells / tree items for a table or tree
TreeItem<Invoice> item = Rdc.createTreeItem(invoice);
```

`Rdc` also exposes the standard *save / discard / cancel* dialog
(`showSaveDiscardCancelDialog`), stage-hierarchy helpers
(`closeStageHierarchy`) and the background-execution helpers described below.

### CRUD controllers: PdoController, PdoCrud, PdoSearch

The generic editing machinery is layered:

| Type                                                                  | Role                                                                                                               |
|-----------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| [`PdoController<T>`](../java/org/tentackle/fx/rdc/PdoController.java) | Base FX controller bound to a single PDO (`setPdo`).                                                               |
| [`PdoEditor<T>`](../java/org/tentackle/fx/rdc/PdoEditor.java)         | A controller that edits a PDO; supplied by the `GuiProvider`.                                                      |
| [`PdoFinder<T>`](../java/org/tentackle/fx/rdc/PdoFinder.java)         | Holds the search-criteria view and runs the query.                                                                 |
| [`PdoCrud<T>`](../java/org/tentackle/fx/rdc/crud/PdoCrud.java)        | The full create/read/update/delete controller wrapping an editor with toolbar, navigation and persistence actions. |
| [`PdoSearch<T>`](../java/org/tentackle/fx/rdc/search/PdoSearch.java)  | The search controller wrapping a finder with a result table that opens CRUD on selection.                          |

The default finder,
[`DefaultPdoFinder`](../java/org/tentackle/fx/rdc/search/DefaultPdoFinder.java),
provides a *normtext* search field when the entity defines a normtext, and
otherwise selects all PDOs immediately — so even an entity with no custom finder
view is searchable out of the box.

#### Configuring CRUD and search dialogs

`Rdc.displayCrudStage(...)` and `Rdc.displaySearchStage(...)` accept a *configurator*, a
`Consumer<PdoCrud<T>>` resp. `Consumer<PdoSearch<T>>` invoked before the stage is shown. Together with
the hooks of the editor and finder, this is how a dialog is adapted to its use case:

| Where         | Method                                                   | Effect                                                                                                         |
|---------------|----------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| `PdoCrud`     | `setPdoList(list)`                                       | Walk through a list of PDOs with the *previous*/*next* buttons, e.g. the result of a search. Also hides *new*, *delete* and *find*, as a modal CRUD does. |
|               | `addPdoEventHandler(type, handler)` / `addPdoEventFilter(...)` | React on the [events](#events) of this dialog.                                                          |
|               | `releasePdo(released)`                                   | Ask the user to save, discard or cancel pending changes, and release the token lock.                           |
| `PdoSearch`   | `setSingleSelectMode(true)`                              | Select exactly one PDO, usually in a modal dialog.                                                             |
|               | `setCreatePdoAllowed(boolean)`                           | Show or hide the *new* button.                                                                                  |
|               | `setDoubleClickHandler(consumer)`                        | Replace the default action of a double-click in a non-modal dialog (opening the editor).                        |
|               | `setFinderVisible(boolean)`                              | Hide the search criteria, e.g. to display a given result with `setItems(list)`. Invoke it after `setPdo`.       |
|               | `setNoDataMessage(text)`                                 | The message shown if nothing was found.                                                                         |
|               | `showTree()` / `showTable()`                             | Toggle between the result table and a tree of the found PDOs.                                                   |
|               | `filterResult()`                                         | Narrow the displayed result by the text of the dialog's filter field, matched against the PDOs' normtext; a leading `!` or `-` inverts the filter. |
| `PdoFinder`   | `setSearchRunningImmediately(true)`                      | Search as soon as the dialog opens.                                                                             |
|               | `setSearchRunningInBackground(true)`                     | Run the query off the FX thread, for queries that may take a while.                                              |
|               | `setTreeShowingInitially(true)` / `setScrollingToEnd(true)` | Initial presentation of the result.                                                                          |
|               | `createTotals(items)` / `createTotalsTableView()`        | A totals row below the result table, by default summing up all numeric columns.                                |
| `PdoEditor`   | `isNewAllowed()` / `isRemoveAllowed()`                   | Enable the *new* and *delete* buttons; by default `isEditAllowed()`, for *delete* also the PDO's `isRemovable()`. |
|               | `validateForm()`                                         | Checks beyond the model's validation, invoked within the save transaction before the PDO is persisted.         |
|               | `getValidationMappers()` / `getValidationPath()`         | Map validation results from the server to the editor's controls.                                               |
|               | `requestInitialFocus()`                                  | Put the focus on the first field when the dialog opens.                                                         |

Opening an existing PDO in a `PdoCrud` for editing requests its [token lock](../../../../tentackle-persistence/src/main/docs/locking.md),
if the entity provides one. If another user holds it, the editor switches to view-only and says so. The
token lock is released when the PDO is saved, discarded or the dialog is closed. `save()` runs the form
validation, the duplicate check of the unique domain key and the `persist()` in one transaction, and
keeps the editor open with the pending changes if any of them fails.

The toolbars hold a few features that are easy to miss:

- **Shift + *new*** in the CRUD dialog creates a deep copy of the current PDO instead of an empty one.
- **Shift + *find*** in the search dialog adds the new results to the displayed ones instead of replacing them.
- The search dialog's *tree* button toggles between the result table and a tree of the found PDOs;
  the CRUD dialog's *tree* button shows the tree of the current PDO. A secondary click on the CRUD's
  *tree* button shows the PDO's object ID.
- The *security* button of the CRUD dialog edits the [security rules](#security-and-administration) of
  a root entity, if the user is allowed to.
- The search dialog shows a filter field for entities with a normtext, see `filterResult()` above.
- The PDOs the user is not allowed to view are left out of the search result.

CRUD and search controllers are expensive to create, since each loads its FXML view. `RdcUtilities`
therefore caches and reuses them per PDO class, as long as the dialog is not showing. Opening a PDO
that is already being edited brings that dialog to the front instead of opening a second one.
Caching is off on servers and if the `noRdcCache` [backend property](../../../../tentackle-session/src/main/docs/backend-properties.md#desktop-client-keys)
is set; switching between light and dark mode invalidates the caches, as does
`RdcUtilities.getInstance().invalidateCaches()`. Subclasses of `RdcUtilities` can decide per entity by
overriding `isCrudCachingEnabled(pdo)` and `isSearchCachingEnabled(pdo)`.

### Tables, trees and cells

RDC plugs PDOs into the
[table and tree infrastructure](../../../../tentackle-fx/src/main/docs/tables.md)
of `tentackle-fx`:

- [`PdoTreeCell`](../java/org/tentackle/fx/rdc/PdoTreeCell.java) renders a PDO in a tree
  with the text, tooltip and graphic supplied by its `GuiProvider`, and supports drag and drop.
  [`PdoTableCell`](../java/org/tentackle/fx/rdc/PdoTableCell.java) and
  [`PdoTreeTableCell`](../java/org/tentackle/fx/rdc/PdoTreeTableCell.java) are used for table
  columns holding a PDO and show its `toString()`. All three show the PDO's
  [context menu](#context-menus).
- [`PdoTreeItem`](../java/org/tentackle/fx/rdc/PdoTreeItem.java) lazily expands a
  tree by asking the provider for the child objects when they are needed for the first time.
  A recursive "expand all" stops at a PDO whose class already appears further up in the path
  (see [`RdcFxUtilities`](../java/org/tentackle/fx/rdc/RdcFxUtilities.java)), since expanding
  a deep object graph may take far too long.
- The `table` package adds RDC-aware configuration
  ([`RdcTableConfiguration`](../java/org/tentackle/fx/rdc/table/RdcTableConfiguration.java),
  [`RdcTableColumnConfiguration`](../java/org/tentackle/fx/rdc/table/RdcTableColumnConfiguration.java)),
  a column-chooser/preferences popup
  ([`TablePopup`](../java/org/tentackle/fx/rdc/table/TablePopup.java)) and printing
  ([`TablePrinter`](../java/org/tentackle/fx/rdc/table/TablePrinter.java)), see
  [Table popup, export and printing](#table-popup-export-and-printing) below.
- The `component` package contributes PDO-aware controls:
  [`RdcComboBox`](../java/org/tentackle/fx/rdc/component/RdcComboBox.java) and
  [`RdcChoiceBox`](../java/org/tentackle/fx/rdc/component/RdcChoiceBox.java),
  complete with the `delegate`/`build` companions that the
  [fx delegate pattern](../../../../tentackle-fx/src/main/docs/fx.md#the-delegate-pattern)
  prescribes. See [PDO-bound components](#pdo-bound-components) below.

### Table popup, export and printing

`Rdc.createTablePopup(table, preferencesSuffix, title)` installs a
[`TablePopup`](../java/org/tentackle/fx/rdc/table/TablePopup.java) as the context menu of a table
(or tree table) and all its columns. The search dialog's result table gets one automatically. It
offers:

- a column chooser to show or hide columns (`setColumnMenuEnabled(false)` turns it off),
- auto-adjusting the column widths to their content,
- printing,
- exporting all or only the selected rows to a spreadsheet or an XML file, which is opened in the
  associated application afterward,
- saving and loading the table [preferences](../../../../tentackle-core/src/main/docs/preferences.md)
  (column widths, order, visibility and sorting) in the user or system scope,
- for tree tables, expanding and collapsing the tree.

The exports and printing are implemented by
[`TableUtilities`](../java/org/tentackle/fx/rdc/table/TableUtilities.java), a replaceable
`@Service`. Only the visible columns are exported, with the same text as displayed in the table. The
default spreadsheet format is CSV (RFC 4180); with
[`tentackle-fx-rdc-poi`](../../../../tentackle-fx-rdc-poi/src/main/docs/poi.md) on the classpath,
it is an Excel file instead. The last file chosen is remembered per table in the JDK's user
preferences of the local machine.

[`TablePrinter`](../java/org/tentackle/fx/rdc/table/TablePrinter.java) prints the visible columns,
scaled down to the page width if necessary, with a title, the date and page numbers, and the totals
row of a table, if any. Because the rows per page depend on the actually rendered row heights, the pages
are prepared in a [background task](background-execution.md) that hops to the FX thread for each page.
A progress dialog allows the user to cancel the job.

### PDO-bound components

Any component [bound](../../../../tentackle-fx/src/main/docs/fx.md#binding-integration) to a PDO-valued
property — a text field showing a customer, a combo box selecting a currency — is enhanced by a
[`PdoComponentAddon`](../java/org/tentackle/fx/rdc/translate/PdoComponentAddon.java), which the PDO
[value translators](#value-translators) attach automatically:

- the style class `tt-pdo-style`, so PDO fields can be styled differently;
- **F2** (or *edit* in the context menu) opens the referenced PDO in a modal CRUD dialog — editable,
  unless the PDO is immutable — and shows the edited version afterwards;
- **F3** (or *search* in the context menu) opens a modal search dialog for the component's type and
  puts the selected PDO into the field, if the component is changeable;
- **drag and drop**: a PDO can be dragged out of the field, and a PDO of a matching type dropped
  into it, e.g. from a table or tree.

Removing the style class `tt-pdo-style` from a component turns these features off. They need the
`DomainContext` as a binding property of the binder, which the `PdoCrud` and `PdoSearch` controllers
set up; for the F3 search, the PDO class must have a `GuiProvider` with a finder.

With `tentackle-fx-rdc` present, every `ComboBox` and `ChoiceBox` created from FXML is an
`RdcComboBox` resp. `RdcChoiceBox` (their builders replace the ones of `tentackle-fx`). When bound
to a PDO type, they fill their item list themselves: with all PDOs of that type in the binding's
domain context. The list is reloaded when the entity's table
[modification count](../../../../tentackle-session/src/main/docs/modification-tracking.md) changes,
and a PDO edited via F2 replaces its outdated entry. By default, the PDOs are taken from the
[cache](../../../../tentackle-pdo/src/main/docs/cache.md) via `selectAllCached()` if the entity has a
preloading cache; otherwise `selectAll()` is used and a warning recommends caching. A different
selection — only the active entries, say — is installed with `setLoadAllPdosCallback(...)`, a callback
that receives a proxy PDO of the component's type and returns the items, e.g.
`box.setLoadAllPdosCallback(proxy -> proxy.selectActive())` with an application-defined finder
`selectActive()`.

The popup of such a combo box shows two columns, the PDO's short and long text, if it implements
`ShortLongText`; the
[`RdcShortLongTextCellFactory`](../java/org/tentackle/fx/rdc/RdcShortLongTextCellFactory.java) hides
the texts of PDOs the user is not allowed to view.

### Value translators

The `translate` package supplies ready-made
[value translators](../../../../tentackle-fx/src/main/docs/fx.md#value-translators)
for PDOs, so a `PersistentDomainObject` can appear in any FX control without
boilerplate:

- [`PdoStringTranslator`](../java/org/tentackle/fx/rdc/translate/PdoStringTranslator.java) — PDO ⇄ display string.
  A text field shows the PDO's unique domain key. Entering a key looks the PDO up via
  `findByUniqueDomainKey`; if there is none, a modal search dialog opens, preset with the input if
  the key class provides a static `lenientValueOf(String)`.
- [`PdoObservableListTranslator`](../java/org/tentackle/fx/rdc/translate/PdoObservableListTranslator.java) — a PDO list as an observable list,
  with the table configuration taken from the element type's `GuiProvider`.
- [`PdoTreeItemTranslator`](../java/org/tentackle/fx/rdc/translate/PdoTreeItemTranslator.java) /
  [`PdoCollectionTreeItemTranslator`](../java/org/tentackle/fx/rdc/translate/PdoCollectionTreeItemTranslator.java) — PDOs as tree items.
- [`PdoComponentAddon`](../java/org/tentackle/fx/rdc/translate/PdoComponentAddon.java) — adds the PDO
  style, context menu, F2/F3 keys and drag and drop to the components they translate for, see
  [PDO-bound components](#pdo-bound-components).

### Context menus

Right-clicking a PDO in a table or tree raises a context menu assembled by a
[`PdoContextMenuFactory`](../java/org/tentackle/fx/rdc/PdoContextMenuFactory.java)
(default
[`DefaultPdoContextMenuFactory`](../java/org/tentackle/fx/rdc/DefaultPdoContextMenuFactory.java)).
Menu items are themselves discoverable services, so an application can add its own
without touching the framework:

- Items implement `PdoTableContextMenuItem`, `PdoTreeContextMenuItem` or
  `PdoTreeTableContextMenuItem` and are declared with the matching
  `@PdoTableContextMenuItemService` /
  `@PdoTreeContextMenuItemService` /
  `@PdoTreeTableContextMenuItemService` annotation.
- An item class must extend `MenuItem` and provide a public constructor taking the cell
  (`PdoTableCell`, `PdoTreeCell` or `PdoTreeTableCell`). The annotation names the PDO class the item
  applies to — `PersistentDomainObject.class` for all PDOs —, an `ordinal` that sorts the items, and an
  optional `group`; groups are separated by a separator. Items applying to the PDO's own class win over
  items with the same ordinal registered for a superclass, which is how a built-in item is replaced
  for a particular entity. Invisible items are left out.
- The built-in items live in the `contextmenu` package:

  | Item                                                                      | Ordinal | Tree | Table | Action                                            |
  |---------------------------------------------------------------------------|---------|------|-------|---------------------------------------------------|
  | [`ExpandItem`](../java/org/tentackle/fx/rdc/contextmenu/ExpandItem.java)     | 100     | ✓    |       | Expand the node and all its children.             |
  | [`CollapseItem`](../java/org/tentackle/fx/rdc/contextmenu/CollapseItem.java) | 110     | ✓    |       | Collapse the node and all its children.           |
  | [`ViewItem`](../java/org/tentackle/fx/rdc/contextmenu/ViewItem.java)         | 140     | ✓    | ✓     | Open the PDO in a non-modal, read-only CRUD dialog. |
  | [`EditItem`](../java/org/tentackle/fx/rdc/contextmenu/EditItem.java)         | 150     | ✓    |       | Edit the PDO in a modal CRUD dialog.              |

  *View* and *edit* are disabled unless the `GuiProvider` allows viewing or editing. There are no
  built-in items for tree tables.

### Events

A `PdoCrud` fires a [`PdoEvent`](../java/org/tentackle/fx/rdc/PdoEvent.java) for every persistence
operation it performs: `CREATE` and `UPDATE` after a save, `DELETE` after a delete, and `READ` when the
*previous*/*next* buttons load another PDO of the list (`ANY` matches them all). The events are
delivered through an [`EventListenerProxy`](../java/org/tentackle/fx/rdc/EventListenerProxy.java) to
the handlers and filters registered on *that* dialog via `addPdoEventHandler` / `addPdoEventFilter`;
they are neither broadcast to other windows nor tied to the database. The `PdoSearch`, for instance,
registers a filter on the CRUD it opens, to update the edited row and follow the list navigation.
Other windows showing the same data learn about changes through
[modification tracking](../../../../tentackle-session/src/main/docs/modification-tracking.md) instead.

### Background execution

Time-consuming persistence calls must never run on the JavaFX application thread. RDC provides
the `bg(...)` helpers on [`Rdc`](../java/org/tentackle/fx/rdc/Rdc.java) to run
work off-thread and post the result back:

```java
Rdc.bg(node,
       () -> invoice.reload(),            // runs in the background
       reloaded -> refreshView(reloaded), // runs on the FX thread
       ex -> showError(ex));              // FX thread, on failure
```

This pattern keeps the UI responsive regardless of whether the PDO is served
locally or remotely.

By default the task runs on the
[`ModificationTracker`](../../../../tentackle-session/src/main/docs/modification-tracking.md)'s
thread and session — no extra threads or sessions, but tasks are serialized and a
long one delays modification tracking. Applications that need several background
tasks in parallel replace `RdcUtilities` with a subclass of
[`RdcUtilitiesWithBackgroundPool`](../java/org/tentackle/fx/rdc/RdcUtilitiesWithBackgroundPool.java),
which runs each task on its own pooled session. See
[Background Execution](background-execution.md) for both models, the trade-offs
and the parallel-runner API.

---

## Application lifecycle (`app`, `login`)

The `app` and `login` packages turn the building blocks above into a complete,
launchable application.

- [`FxApplication`](../java/org/tentackle/fx/rdc/app/FxApplication.java) — the
  base class for any Tentackle FX application.
- [`LoginApplication`](../java/org/tentackle/fx/rdc/app/LoginApplication.java) —
  usually the application that is launched *first*. It shows the login view and
  spawns the login handler; on success it hands over to the real application.
- [`DesktopApplication<C>`](../java/org/tentackle/fx/rdc/app/DesktopApplication.java)
  — the main desktop application, parameterized by its main controller type.
- [`LoginFailedHandler`](../java/org/tentackle/fx/rdc/app/LoginFailedHandler.java)
  and [`LogoutEventHandler`](../java/org/tentackle/fx/rdc/app/LogoutEventHandler.java)
  — pluggable handlers for the corresponding lifecycle events.

The `login` package implements the login flow:
[`Login`](../java/org/tentackle/fx/rdc/login/Login.java) (the controller),
[`LoginController`](../java/org/tentackle/fx/rdc/login/LoginController.java) (the
interface that lets an application substitute its own login view) and
[`Backends`](../java/org/tentackle/fx/rdc/login/Backends.java) (add / edit /
remove the server backends the user can connect to).

Which credentials the view collects is decided by the `authentication` key of
`backend.properties`. With the default `password` method the user fills in the username and password
fields. With an interactive (single sign-on) method those fields are hidden and a sign-in button runs
the application's `CredentialsProvider` on a background thread —
[`FxCredentialsPrompt`](../java/org/tentackle/fx/rdc/login/FxCredentialsPrompt.java) opens the system
browser and reports progress back into the view. See
[Authentication](../../../../tentackle-session/src/main/docs/authentication.md).

Which backend the user connects to is decided by the `backend` key of `backend.properties`: with
`@user` or `@system`, the login view lets the user pick one of the backends stored in the user resp.
system preferences and edit them with the [`Backends`](../java/org/tentackle/fx/rdc/login/Backends.java)
dialog (see [Redirecting the Backend](../../../../tentackle-session/src/main/docs/backend-properties.md#redirecting-the-backend-user-system-and-named-files)). A failed
login is handled by the [`LoginFailedHandler`](../java/org/tentackle/fx/rdc/app/LoginFailedHandler.java):
expired credentials are renewed once via the `CredentialsProvider`, a refused login may be retried twice
before the application terminates, and a client whose version does not match the server's is told so.
Applications replace the login view by overriding `LoginApplication.loadLoginController()`, and the
handler via `DesktopApplication.createLoginFailedHandler(...)`.

A minimal startup therefore looks like:

```text
launch LoginApplication
   └─ user picks a Backend and authenticates  (login package)
        └─ on success → DesktopApplication starts with the main controller
              └─ controllers use Rdc / GuiProvider to search, view and edit PDOs
```

---

## Security and administration

- The `security` package provides a UI for editing the PDO **security rules**
  enforced by the persistence layer:
  [`SecurityEditor`](../java/org/tentackle/fx/rdc/security/SecurityEditor.java),
  [`SecurityRulesView`](../java/org/tentackle/fx/rdc/security/SecurityRulesView.java),
  and a [`SecurityDialogFactory`](../java/org/tentackle/fx/rdc/security/SecurityDialogFactory.java)
  (default
  [`DefaultSecurityDialogFactory`](../java/org/tentackle/fx/rdc/security/DefaultSecurityDialogFactory.java)).
  Security thus participates in the same per-aggregate model described in the
  [PDO documentation](../../../../tentackle-pdo/src/main/docs/pdo.md#aggregates-prevent-whole-classes-of-bugs).
- The `admin` package contains operational views, see
  [Session administration](#session-administration) below.

### Session administration

A server knows every client session connected to it. The
[`AdminExtension`](../../../../tentackle-pdo/src/main/java/org/tentackle/pdo/AdminExtension.java), a
remote session extension implemented by the persistence layer's `AdminExtensionAdapter`, makes this
available to clients:

- `getSessions()` lists the sessions as `SessionData`: user, application, since when, client version,
  host, locale, time zone, JVM and OS info, session number and group.
- `logout(userId, sessionGroupId, applicationName, applicationId)` asks a client to log out: it
  sends a `LogoutEvent` through the user's
  [modification tracker](../../../../tentackle-session/src/main/docs/modification-tracking.md) session,
  which the client handles like a regular logout (see `LogoutEventHandler`).
- `kill(...)` closes the matching sessions on the server at once and returns their number.

`0` or `null` for the group, application name or application id match any. The client obtains the
extension with `((PdoRemoteSession) session.getRemoteSession()).getExtension(context,
AdminExtension.class)`, so it is only available in a three-tier setup, and the server checks the
execute permission on `AdminExtensionAdapter` for every call — grant it to administrators via the
[security rules](../../../../tentackle-pdo/src/main/docs/security.md).
[`SessionsView`](../java/org/tentackle/fx/rdc/admin/SessionsView.java) is a ready-made view on top of
it: a table of the live sessions with buttons to refresh, log out and kill the selected ones.
Logging out or killing a selected session applies to all sessions of the same session group and user
as well, e.g. the modification tracker's session of that client. The view's *security* button edits
the rules for `AdminExtension`. Override `isKillSessionAllowed()` to hide the kill button.

---

## Annotation processors (`apt`)

To keep service discovery working in both modular (JPMS) and classpath
deployments, the `apt` package provides validators to ensure the contracts.

- [`GuiProviderServiceAnnotationProcessor`](../java/org/tentackle/fx/rdc/apt/GuiProviderServiceAnnotationProcessor.java)
  — for `@GuiProviderService`: the class must implement `GuiProvider` and provide a constructor
  taking the PDO.
- `PdoTableContextMenuItemServiceAnnotationProcessor` /
  `PdoTreeContextMenuItemServiceAnnotationProcessor` — for the context-menu item
  service annotations: the class must implement the item interface and provide a constructor
  taking the cell. `@PdoTreeTableContextMenuItemService` is not checked at compile time.

---

## How It Fits Together

A typical "open an entity for editing" flow ties the pieces together:

1. The user triggers an action (menu, button, double-click in a table).
2. Calling code asks `Rdc` for the operation, e.g.
   `Rdc.displayCrudStage(pdo, true, owner)`.
3. `RdcFactory` resolves the [`GuiProvider`](../java/org/tentackle/fx/rdc/GuiProvider.java)
   for the PDO's type via `GuiProviderFactory`.
4. The provider creates a [`PdoEditor`](../java/org/tentackle/fx/rdc/PdoEditor.java)
   (an FX controller from `tentackle-fx`), which `PdoCrud` wraps with toolbar,
   navigation and persistence actions.
5. The editor's controls are [bound](../../../../tentackle-fx/src/main/docs/fx.md#binding-integration)
   to the PDO's attributes; edits flow through the
   [binding layer](../../../../tentackle-core/src/main/docs/binding.md).
6. On save, `PdoCrud` validates and persists the PDO within a single transaction;
   on completion it fires a [`PdoEvent`](../java/org/tentackle/fx/rdc/PdoEvent.java) to the handlers
   registered on it — e.g. the search dialog it was opened from, which updates its result row.
   Lengthy work — a slow search, printing — is moved off the FX thread with
   [`Rdc.bg(...)`](background-execution.md).

The application only had to supply an editor view (or even use a generated default)
and a `GuiProvider`; everything else came from the framework.

---

## Package Reference

| Package                                            | Contents                                                                                              |
|----------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| `org.tentackle.fx.rdc`                             | Core: `Rdc`, `RdcFactory`, `GuiProvider`, CRUD controllers, table/tree cells, events, utilities.      |
| `org.tentackle.fx.rdc.app`                         | Application lifecycle: `FxApplication`, `LoginApplication`, `DesktopApplication`, lifecycle handlers. |
| `org.tentackle.fx.rdc.login`                       | Login view, controller and backend management.                                                        |
| `org.tentackle.fx.rdc.crud`                        | The generic `PdoCrud` create/read/update/delete controller.                                           |
| `org.tentackle.fx.rdc.search`                      | `PdoSearch` and the default finder.                                                                   |
| `org.tentackle.fx.rdc.table`                       | RDC table configuration, table popup, export, printing, table utilities.                              |
| `org.tentackle.fx.rdc.component(.delegate/.build)` | PDO-aware combo/choice boxes with their delegates and builders.                                       |
| `org.tentackle.fx.rdc.translate`                   | Predefined PDO value translators.                                                                     |
| `org.tentackle.fx.rdc.contextmenu`                 | Built-in context-menu items (edit, view, expand, collapse).                                           |
| `org.tentackle.fx.rdc.security`                    | Security-rules editing UI.                                                                            |
| `org.tentackle.fx.rdc.admin`                       | Administrative views (e.g. live sessions).                                                            |
| `org.tentackle.fx.rdc.apt`                         | Annotation processors verifying the service annotations at compile time.                              |
| `org.tentackle.fx.rdc.service`                     | The module hook.                                                                                      |

## See also

- [tentackle-fx — The Extended JavaFX Layer](../../../../tentackle-fx/src/main/docs/fx.md)
- [PDO — Persistent Domain Objects](../../../../tentackle-pdo/src/main/docs/pdo.md)
- [Binding](../../../../tentackle-core/src/main/docs/binding.md)
- [Validation](../../../../tentackle-core/src/main/docs/validation.md)
- [TRIP — Tentackle Remote Invocation Protocol](../../../../tentackle-core/src/main/docs/trip.md)
