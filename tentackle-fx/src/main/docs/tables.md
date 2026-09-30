# Tables and Trees — Configuration-Driven Tables, Tree-Tables, and Views

## Overview and Motivation

Tables are the workhorse of business applications: search results, pick lists, journal views,
editable detail grids. Plain JavaFX makes each of these a chore — every `TableColumn` needs a
cell-value factory, a cell factory, a formatter, an alignment, an editor, and none of it survives
a restart: column widths, ordering, visibility, and sorting are gone when the user closes the
window.

Tentackle replaces that per-column boilerplate with a **declarative table configuration**: a single
[`TableConfiguration`](../java/org/tentackle/fx/table/TableConfiguration.java) object describes the
table by [binding](../../../../tentackle-core/src/main/docs/binding.md) paths, and everything else —
cell rendering, editors, formats, preferences-backed layouts, totals, printing, spreadsheet export —
derives from it:

- **One configuration, two view types** — the same `TableConfiguration` configures both an
  [`FxTableView`](../java/org/tentackle/fx/component/FxTableView.java) and an
  [`FxTreeTableView`](../java/org/tentackle/fx/component/FxTreeTableView.java).
- **Columns are binding paths** — `config.addColumn("orgUnit", "Org Unit")` is all it takes; the
  column's type, format, alignment, and editor follow from the bound model member.
- **Rendering by value class** — a pluggable
  [`TableCellType`](../java/org/tentackle/fx/table/TableCellType.java) knows how to display and
  edit one Java type, application-wide.
- **Autofilter in the header** — an Excel-like filter popup per column, for tables and tree-tables
  alike (see [below](#column-filters-autofilter)).
- **Layouts that roam** — column widths, order, visibility, and sorting are stored in the
  [persisted preferences](../../../../tentackle-core/src/main/docs/preferences.md), per user or
  system-wide; with database-backed preferences the layout follows the user to any workstation.
- **PDO awareness on top** — the [rich desktop client](../../../../tentackle-fx-rdc/src/main/docs/rdc.md)
  derives default configurations from the PDO model, adds a column-chooser popup, printing, and
  spreadsheet/XML export ([`tentackle-fx-rdc-poi`](../../../../tentackle-fx-rdc-poi/src/main/docs/poi.md)
  upgrades the export to real Excel workbooks).

The foundation lives in `org.tentackle.fx.table` (+ `table.type`) in this module; the PDO layer in
`org.tentackle.fx.rdc.table` of [tentackle-fx-rdc](../../../../tentackle-fx-rdc/src/main/docs/rdc.md)
is covered [below](#the-rdc-layer-pdo-aware-tables).


## Key Concepts

### The view components

- [`FxTableView`](../java/org/tentackle/fx/component/FxTableView.java) and
  [`FxTreeTableView`](../java/org/tentackle/fx/component/FxTreeTableView.java) extend the JavaFX
  `TableView` / `TreeTableView` and implement `FxComponent`, so they participate in the
  [binding and delegate machinery](fx.md#fxcontrol-fxcomponent-and-fxcontainer) like any other
  Tentackle FX control. Each view holds its (optional but recommended) `TableConfiguration`.
- [`FxTableColumn`](../java/org/tentackle/fx/table/FxTableColumn.java) /
  [`FxTreeTableColumn`](../java/org/tentackle/fx/table/FxTreeTableColumn.java) and the matching
  [`FxTableCell`](../java/org/tentackle/fx/table/FxTableCell.java) /
  [`FxTreeTableCell`](../java/org/tentackle/fx/table/FxTreeTableCell.java) are created from the
  column configurations — application code rarely instantiates them directly.
- [`TotalsTableView`](../java/org/tentackle/fx/table/TotalsTableView.java) displays the totals of
  another table (see [below](#totals)).

### TableConfiguration — the single source of truth

A [`TableConfiguration<S>`](../java/org/tentackle/fx/table/TableConfiguration.java) describes a
table of row type `S`. Its **name** doubles as the key to the table's preferences. Columns are
added by binding path:

```java
TableConfiguration<Invoice> config = FxFactory.getInstance().createTableConfiguration(Invoice.class, null);
config.addColumn("invoiceNumber", bundle.getString("invoiceNumber"));
config.addColumn("total", bundle.getString("total"));
config.setEditMode(TableConfiguration.EDITMODE.ROW);
config.configure(tableView);      // creates columns, cells, editors, applies preferences
```

`configure(...)` accepts either an `FxTableView` or an `FxTreeTableView` — the configuration is
view-type agnostic (for tree-tables it also creates the `TreeItem`s via `createTreeItem`).

Two enums control the overall behavior:

- `BINDING` — whether the cells are bound to the row object's members: `YES` (the default,
  binds all `@Bindable` members), `INHERITED` (includes bindables inherited from superclasses)
  or `NO`.
- `EDITMODE` — `NO` (read-only, the default), `SIMPLE` (edit the current cell only), `ROW`
  (commit moves to the next/previous cell in the row) or `COLUMN` (commit moves to the cell
  below/above).

The configuration owns an [`FxTableBinder`](../java/org/tentackle/fx/bind/FxTableBinder.java) that
binds the *cells* to the row objects. Don't confuse it with the controller's binder, which binds a
data *list* to the table as a whole: binding properties needed by cell editors — the
`DomainContext`, for instance — must be applied to the **table** binder
(see [binding.md](../../../../tentackle-core/src/main/docs/binding.md)).

### TableColumnConfiguration — per-column tuning

Each column is described by a
[`TableColumnConfiguration<S,T>`](../java/org/tentackle/fx/table/TableColumnConfiguration.java),
whose name is the binding path (and the column's preferences key). Everything a business grid
needs is a property, with `null` meaning "use the default derived from the column type or editor":

| Aspect     | Properties                                                                          |
|------------|-------------------------------------------------------------------------------------|
| Display    | `displayedName`, `alignment`, `pattern` (yields a `DecimalFormat` or `DateTimeFormatter`), `blankZero` |
| Editing    | `editable`, `editor` (an `FxComponent` overriding the cell type's default), `validChars` / `invalidChars`, `unsigned`, `scale`, `maxColumns`, `caseConversion`, `autoSelect` |
| Behavior   | `summable` (defaults to true for numeric types — see [totals](#totals)), `cellType` (overrides the type-derived cell type), `binding` |

The configuration creates and caches the actual `TableColumn` or `TreeTableColumn` on demand
(`getTableColumn()` / `getTreeTableColumn()`).

### Table cell types — rendering per value class

A [`TableCellType<T>`](../java/org/tentackle/fx/table/TableCellType.java) is a singleton that knows
how to display (`updateItem`) and edit (`getEditor`) values of one Java class in table and
tree-table cells. Cell types are produced on demand by the
[`TableCellTypeFactory`](../java/org/tentackle/fx/table/TableCellTypeFactory.java) and registered
with [`@TableCellTypeService`](../java/org/tentackle/fx/table/TableCellTypeService.java), a
[mapped service](../../../../tentackle-common/src/main/docs/services.md) keyed by the value class —
so an application can add cell types for its own
[datatypes](../../../../src/main/docs/embedded-vs-datatype.md) or replace the provided ones.

The `org.tentackle.fx.table.type` package ships cell types for strings, numbers, booleans, enums,
[`BMoney`](../../../../tentackle-common/src/main/docs/common.md),
[`I18NText`](../../../../tentackle-common/src/main/docs/bundle.md), plain objects, and the full
date/time family (`Date`, `Time`, `Timestamp`, `LocalDate`, `LocalTime`, `LocalDateTime`,
`Instant`, `OffsetTime`, `OffsetDateTime`, `ZonedDateTime`), plus abstract bases
(`AbstractTableCellType`, `AbstractDateTimeTableCellType`) for custom types.

### Table configuration providers

A [`TableConfigurationProvider`](../java/org/tentackle/fx/table/TableConfigurationProvider.java)
creates the `TableConfiguration` for one row class. Providers are registered via
[`@TableConfigurationProviderService`](../java/org/tentackle/fx/table/TableConfigurationProviderService.java)
(again a `@MappedService`) and located through the
[`TableConfigurationProviderFactory`](../java/org/tentackle/fx/table/TableConfigurationProviderFactory.java),
which maps the row class leniently — a provider registered for a supertype or interface serves the
subclasses as well. This is how generic UI such as the RDC search dialog obtains the right columns
for any row type without knowing it in advance. In RDC applications the per-PDO
[`GuiProvider`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/GuiProvider.java)
*is* the table configuration provider (see [below](#the-rdc-layer-pdo-aware-tables)).

### Preferences-backed layouts

`TableConfiguration.savePreferences(...)` / `loadPreferences(...)` persist and restore the visual
state of a table via the pluggable
[persisted preferences](../../../../tentackle-core/src/main/docs/preferences.md):

- **What is stored**: per column the visibility, width, view position and sort type/order, plus the
  table's preferred width and height. Restoring the sorting is optional (`setSortingIncluded`,
  default off); restoring the view size can be turned off (`setViewSizeIncluded`, default on).
- **Where it is stored**: under a node named after the configuration's name, an optional suffix
  (to distinguish several tables of the same type, e.g. per controller), and the effective locale —
  column headers translate, so layouts are locale-specific. Each column gets a child node keyed by
  its binding path.
- **User vs. system scope**: layouts save to the user's preferences by default; loading falls back
  to the system preferences when the user hasn't stored a layout yet. Saving to the system scope
  turns a layout into the default for all users.

In a Tentackle application the preferences are usually
[database-backed](../../../../tentackle-database/src/main/docs/database.md#database-backed-preferences),
so a user's table layouts follow them to any workstation — and system-wide defaults are rolled out
by simply saving them once.

### Column filters (autofilter)

JavaFX can filter rows only programmatically — a `FilteredList` and a `Predicate` — and offers
nothing at all for `TreeTableView`. Tentackle adds the **autofilter** known from spreadsheet
applications: a filter button in each column header opening a popup with sort shortcuts, a search
field, a checkbox list of the column's distinct values, and a type-aware condition editor.

Filtering is **off by default** and is switched on per table configuration:

```java
config.setFilterEnabled(true);     // filter buttons in the column headers
config.setFilterIncluded(true);    // optional: the filter becomes part of the saved layout
config.getColumnConfiguration("serial").setFilterable(Boolean.FALSE);   // opt out per column
```

`configure(...)` then installs a
[`TableFilterSupport`](../java/org/tentackle/fx/table/filter/TableFilterSupport.java) on the view,
reachable through `FxTableView.getFilterSupport()` / `getFilter()` / `isFilterActive()` /
`clearFilter()` (and the same on `FxTreeTableView`).

Each column gets a [`TableColumnFilter`](../java/org/tentackle/fx/table/filter/TableColumnFilter.java)
combining two optional parts with a logical *and*:

- the **value set** — the displayed texts the user checked in the popup, plus the pseudo entry
  *(blank)* for empty values;
- the **condition** — a [`TableFilterOperator`](../java/org/tentackle/fx/table/filter/TableFilterOperator.java)
  with up to two operands: equals, contains, starts/ends with, less/greater, between, is empty, …
  Only the operators applicable to the column type are offered.

Values are matched by their **displayed text**, not by `equals`, since the values of a column often
provide no useful equality (PDOs, for example) and since the user picks what is displayed. Only the
ordering operators compare the values naturally, parsing the operand according to the column's
format. As in spreadsheet applications, the value list of a column shows the values of the rows
passing the filters of all *other* columns; it is capped at
`TableFilterSupport.setMaxDistinctValues(int)` (1000 by default).

**Table views** are filtered by inserting a `FilteredList` into the view's item chain, which then
reads `source → FilteredList → SortedList`. The chain always ends in a `SortedList` bound to the
table's comparator, because the table's sort policy cannot sort anything else. As a consequence,
`getItems()` returns an immutable transformation list as soon as filtering is enabled — modify the
root source instead:

```java
FxUtilities.getInstance().getRootSource(table.getItems()).add(pdo);
FxUtilities.getInstance().setSourceElement(table.getItems(), viewIndex, pdo);   // maps the index down
```

**Tree-table views** are filtered level by level by a
[`TreeItemFilter`](../java/org/tentackle/fx/table/filter/TreeItemFilter.java): an item is hidden when
its own row does not pass the filter, together with its whole subtree, and the children of an item
are evaluated when the item is **expanded** and not before. Lazily loading tree items — such as the
RDC's `PdoTreeItem`, which queries the database in `getChildren()` — are thus never forced to
materialize the whole tree, and node loops cannot lead to an endless recursion. Two consequences
worth knowing:

- an item all of whose children are filtered out still shows its disclosure arrow, since being a
  leaf is decided by the tree item itself;
- if the application replaces the children of an item while a filter is active, tell the support
  about it via `TableFilterSupport.invalidate(treeItem)`.

With `setFilterIncluded(true)` the active filter is stored per column in the same preferences node as
the width, visibility, and sorting, under the key `filter`. Unparsable or outdated entries clear the
column's filter instead of failing the load.

The popup renders each value with the text (and graphic) the table's cells would show. This comes
from `TableCellType.getText(...)` / `getGraphic(...)`, which render a value without a cell —
the same methods the spreadsheet and XML exports use, so a date column exports as `31.12.2025`
rather than `2025-12-31`.

The filter UI can be styled with these style classes:

| Style class               | Applies to                                                  |
|---------------------------|-------------------------------------------------------------|
| `tt-filter-column`        | every filterable column                                     |
| `tt-filter-button`        | the filter button in a column header                        |
| `tt-filter-button-active` | additionally, the filter button of a filtered column        |
| `tt-filter-popup`         | the filter popup                                            |
| `tt-filter-popup-pane`    | the popup's content pane                                    |
| `tt-filter-search`        | the search field                                            |
| `tt-filter-list`          | the checkbox list of the distinct values                    |
| `tt-filter-condition`     | the condition editor                                        |
| `tt-filter-hint`          | the hint shown if the value list was truncated              |

### Totals

[`TotalsTableView`](../java/org/tentackle/fx/table/TotalsTableView.java) is a small companion table
displayed below (or wherever the layout puts it) a regular `FxTableView`. Bound via
`setBoundTable`, it mirrors the bound table's columns — widths and visibility are bound, the header
and horizontal scrollbar are hidden via its own CSS — and renders one row per aggregate the
application supplies as items (one row for totals; three for totals, minimum and maximum; …).
Which columns show a total is controlled by the column configuration's `summable` property,
defaulting to true for numeric column types.


## The RDC Layer: PDO-Aware Tables

The [rich desktop client](../../../../tentackle-fx-rdc/src/main/docs/rdc.md) builds on this
foundation in `org.tentackle.fx.rdc.table` and makes tables of PDOs essentially free.

### GuiProviders supply the configuration

Every PDO's [`GuiProvider`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/GuiProvider.java)
extends `TableConfigurationProvider`. The
[`DefaultGuiProvider`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/DefaultGuiProvider.java)
derives a default configuration from the PDO model via reflection: each `@Persistent` attribute
with a `@Bindable` getter becomes a column, displayed names come from the provider's resource
bundle. `createTableView()` then creates the table, applies the binding mode, and asserts that all
columns are bound. A typical override picks and orders the columns explicitly, using the
generated attribute-name constants:

```java
@Override
public TableConfiguration<Message> createTableConfiguration() {
  TableConfiguration<Message> config = createEmptyTableConfiguration();
  config.addColumn(Message.AN_MESSAGENUMBER, getBundle().getString(Message.AN_MESSAGENUMBER));
  config.addColumn(Message.AN_WHEN, getBundle().getString(Message.AN_WHEN));
  config.addColumn(Message.RN_ORGUNIT, getBundle().getString(Message.RN_ORGUNIT));
  return config;
}
```

This is what the RDC [search dialog](../../../../tentackle-fx-rdc/src/main/docs/rdc.md#crud-controllers-pdocontroller-pdocrud-pdosearch)
uses for every result table: `guiProvider.createTableView()` plus a table popup — no per-entity
table code at all.

[`RdcTableConfiguration`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/table/RdcTableConfiguration.java)
and [`RdcTableColumnConfiguration`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/table/RdcTableColumnConfiguration.java)
are the PDO-aware configuration implementations, and
[`PdoTableCellType`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/table/PdoTableCellType.java)
registers a cell type for the `PersistentDomainObject` interface itself, so PDO-valued columns
(relations, for example) render out of the box.

### TablePopup — the user's table menu

[`TablePopup`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/table/TablePopup.java)
(created via `Rdc.createTablePopup(table, preferencesSuffix, title)`) attaches a context menu to
any table or tree-table:

- a **column chooser** with per-column visibility checkboxes and an *auto adjust* width action;
- **print** with a preview, via the
  [`TablePrinter`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/table/TablePrinter.java)
  controller;
- **export to spreadsheet** and **export to XML**, each for all or only the selected rows;
- **save / load preferences** — the user's own layout, plus loading the system-wide default;
  saving the *system* layout is offered when the application runs with system-only preferences
  (an administrative mode), and the save items disappear entirely when the preferences are
  read-only;
- **expand / collapse all** for tree-tables.

### TableUtilities — replaceable table services

[`TableUtilities`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/table/TableUtilities.java)
is the `@Service` singleton behind the popup's actions: creating default configurations from PDO
classes, printing, and exporting. The stock implementation writes RFC-4180 **CSV** for the
spreadsheet export — dependency-free. Adding the
[`tentackle-fx-rdc-poi`](../../../../tentackle-fx-rdc-poi/src/main/docs/poi.md) module to the
path replaces the service with one that writes styled, typed **Excel workbooks** via Apache POI,
without touching application code.


## How It Fits Together

A controller displaying a list of invoices in an editable table:

1. The FXML declares an `FxTableView` (or the code creates one via
   `guiProvider.createTableView()` in an RDC application).
2. The table configuration — from a `TableConfigurationProvider`, the PDO's `GuiProvider`, or
   built inline — is applied with `config.configure(tableView)`: columns, cell types, editors, and
   formats appear, and the saved layout is restored from the preferences.
3. The controller's [binder](../../../../tentackle-core/src/main/docs/binding.md) binds the data
   list to the table by name; the table's own binder moves values between cells and row objects,
   translating and validating through the same machinery as any form field.
4. `Rdc.createTablePopup(...)` adds the column chooser, printing, export, and layout save/load.
5. When the user rearranges columns and saves, the layout lands in the (database-backed)
   preferences and greets them on every workstation from then on.


## Package Layout

| Package                                        | Content                                                                | Module            |
|------------------------------------------------|------------------------------------------------------------------------|-------------------|
| `org.tentackle.fx.table`                       | Configurations, providers, cell/column classes, `TotalsTableView`      | tentackle-fx      |
| `org.tentackle.fx.table.filter`                | Column filters (autofilter): filter model, support, popup              | tentackle-fx      |
| `org.tentackle.fx.table.type`                  | Built-in `TableCellType`s (strings, numbers, money, date/time, …)      | tentackle-fx      |
| `org.tentackle.fx.rdc.table`                   | PDO-aware configuration, `TablePopup`, `TablePrinter`, `TableUtilities` | tentackle-fx-rdc  |
| `org.tentackle.fx.rdc.poi`                     | Excel export replacing the CSV default                                 | tentackle-fx-rdc-poi |


## Related Documentation

- [tentackle-fx](fx.md) — the extended JavaFX layer this builds on.
- [Binding](../../../../tentackle-core/src/main/docs/binding.md) — the binding machinery behind columns and cells.
- [tentackle-fx-rdc](../../../../tentackle-fx-rdc/src/main/docs/rdc.md) — the rich desktop client.
- [Spreadsheet (XLS) Export](../../../../tentackle-fx-rdc-poi/src/main/docs/poi.md) — Excel export via Apache POI.
- [Preferences](../../../../tentackle-core/src/main/docs/preferences.md) — the persisted preferences backing table layouts.
- [Embedded Entities vs. DataTypes](../../../../src/main/docs/embedded-vs-datatype.md) — custom value types that may need their own cell type.
