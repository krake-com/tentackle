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
- **Tree rendering** — `getTreeRoot()`, `getTreeText(...)`, `getToolTipText(...)`,
  `createTreeItem()`, `getTreeCellFactory()`, and the navigation hooks
  `getTreeChildObjects(...)` / `getTreeParentObjects(...)` with their
  `providesTreeChildObjects()` / `getTreeExpandMaxDepth()` guards.
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
// open a modal edit dialog for a PDO
Rdc.displayCrudStage(invoice, /*editable*/ true, ownerWindow);

// open a search dialog for an entity
Rdc.displaySearchStage(on(Invoice.class), Modality.NONE, ownerWindow);

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

### Tables, trees and cells

RDC plugs PDOs into the
[table and tree infrastructure](../../../../tentackle-fx/src/main/docs/tables.md)
of `tentackle-fx`:

- [`PdoTableCell`](../java/org/tentackle/fx/rdc/PdoTableCell.java),
  [`PdoTreeCell`](../java/org/tentackle/fx/rdc/PdoTreeCell.java) and
  [`PdoTreeTableCell`](../java/org/tentackle/fx/rdc/PdoTreeTableCell.java) render a
  PDO using the text/graphic supplied by its `GuiProvider`.
- [`PdoTreeItem`](../java/org/tentackle/fx/rdc/PdoTreeItem.java) lazily expands a
  tree by asking the provider for child/parent objects, honouring
  `getTreeExpandMaxDepth()` and `stopTreeExpansion()`.
- The `table` package adds RDC-aware configuration
  ([`RdcTableConfiguration`](../java/org/tentackle/fx/rdc/table/RdcTableConfiguration.java),
  [`RdcTableColumnConfiguration`](../java/org/tentackle/fx/rdc/table/RdcTableColumnConfiguration.java)),
  a column-chooser/preferences popup
  ([`TablePopup`](../java/org/tentackle/fx/rdc/table/TablePopup.java)) and printing
  ([`TablePrinter`](../java/org/tentackle/fx/rdc/table/TablePrinter.java)).
- The `component` package contributes PDO-aware controls:
  [`RdcComboBox`](../java/org/tentackle/fx/rdc/component/RdcComboBox.java) and
  [`RdcChoiceBox`](../java/org/tentackle/fx/rdc/component/RdcChoiceBox.java),
  complete with the `delegate`/`build` companions that the
  [fx delegate pattern](../../../../tentackle-fx/src/main/docs/fx.md#the-delegate-pattern)
  prescribes.

### Value translators

The `translate` package supplies ready-made
[value translators](../../../../tentackle-fx/src/main/docs/fx.md#value-translators)
for PDOs, so a `PersistentDomainObject` can appear in any FX control without
boilerplate:

- [`PdoStringTranslator`](../java/org/tentackle/fx/rdc/translate/PdoStringTranslator.java) — PDO ⇄ display string.
- [`PdoObservableListTranslator`](../java/org/tentackle/fx/rdc/translate/PdoObservableListTranslator.java) — a PDO collection as an observable list.
- [`PdoTreeItemTranslator`](../java/org/tentackle/fx/rdc/translate/PdoTreeItemTranslator.java) /
  [`PdoCollectionTreeItemTranslator`](../java/org/tentackle/fx/rdc/translate/PdoCollectionTreeItemTranslator.java) — PDOs as tree items.
- [`PdoComponentAddon`](../java/org/tentackle/fx/rdc/translate/PdoComponentAddon.java) — wires the above into the component build process.

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
- The built-in items live in the `contextmenu` package:
  [`EditItem`](../java/org/tentackle/fx/rdc/contextmenu/EditItem.java),
  [`ViewItem`](../java/org/tentackle/fx/rdc/contextmenu/ViewItem.java),
  [`ExpandItem`](../java/org/tentackle/fx/rdc/contextmenu/ExpandItem.java) and
  [`CollapseItem`](../java/org/tentackle/fx/rdc/contextmenu/CollapseItem.java).

### Events

[`PdoEvent`](../java/org/tentackle/fx/rdc/PdoEvent.java), together with
[`EventListenerProxy`](../java/org/tentackle/fx/rdc/EventListenerProxy.java),
notifies the UI about persistence operations (save, delete, …) on a PDO so that
open views, tables and trees can react and refresh.

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
- The `admin` package contains operational views, e.g.
  [`SessionsView`](../java/org/tentackle/fx/rdc/admin/SessionsView.java) for
  monitoring live sessions on the server.

---

## Annotation processors (`apt`)

To keep service discovery working in both modular (JPMS) and classpath
deployments, the `apt` package provides validators to ensure the contracts.

- [`GuiProviderServiceAnnotationProcessor`](../java/org/tentackle/fx/rdc/apt/GuiProviderServiceAnnotationProcessor.java)
  — for `@GuiProviderService`.
- `PdoTableContextMenuItemServiceAnnotationProcessor` /
  `PdoTreeContextMenuItemServiceAnnotationProcessor` — for the context-menu item
  service annotations.

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
   on completion a [`PdoEvent`](../java/org/tentackle/fx/rdc/PdoEvent.java)
   refreshes any other open views. Lengthy work — a slow search, printing — is
   moved off the FX thread with [`Rdc.bg(...)`](background-execution.md).

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
| `org.tentackle.fx.rdc.table`                       | RDC table configuration, column popup, printing, table utilities.                                     |
| `org.tentackle.fx.rdc.component(.delegate/.build)` | PDO-aware combo/choice boxes with their delegates and builders.                                       |
| `org.tentackle.fx.rdc.translate`                   | Predefined PDO value translators.                                                                     |
| `org.tentackle.fx.rdc.contextmenu`                 | Built-in context-menu items (edit, view, expand, collapse).                                           |
| `org.tentackle.fx.rdc.security`                    | Security-rules editing UI.                                                                            |
| `org.tentackle.fx.rdc.admin`                       | Administrative views (e.g. live sessions).                                                            |
| `org.tentackle.fx.rdc.apt`                         | Annotation processors for service registration.                                                       |
| `org.tentackle.fx.rdc.service`                     | Module hook / runtime registration.                                                                   |

## See also

- [tentackle-fx — The Extended JavaFX Layer](../../../../tentackle-fx/src/main/docs/fx.md)
- [PDO — Persistent Domain Objects](../../../../tentackle-pdo/src/main/docs/pdo.md)
- [Binding](../../../../tentackle-core/src/main/docs/binding.md)
- [Validation](../../../../tentackle-core/src/main/docs/validation.md)
- [TRIP — Tentackle Remote Invocation Protocol](../../../../tentackle-core/src/main/docs/trip.md)
