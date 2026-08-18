# tentackle-fx-rdc-poi — Spreadsheet (XLS) Export for RDC Tables

## Overview and Motivation

Every [rich desktop client](../../../../tentackle-fx-rdc/src/main/docs/rdc.md) table and tree-table
already offers a context-menu action to export its rows to a spreadsheet file. Out of the box
that action lives in [`TableUtilities`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/table/TableUtilities.java)
and writes **CSV** (RFC 4180) — a format every spreadsheet application can open, with no extra
dependencies.

`tentackle-fx-rdc-poi` upgrades that same action to write a real **Excel `.xls` workbook** using
[Apache POI](https://poi.apache.org/). It does so without any change to application code: the module
contributes a single service that *replaces* the default `TableUtilities`. Drop the module on the
path and the table popup's *Export to spreadsheet* command produces a styled `.xls` file instead of
CSV.

## How it plugs in

The module ships one public type,
[`PoiTableUtilities`](../java/org/tentackle/fx/rdc/poi/PoiTableUtilities.java), which extends
`TableUtilities` and is registered through the [service API](../../../../tentackle-common/src/main/docs/services.md):

```java
@Service(TableUtilities.class)
@ClasspathFirst(TableUtilities.class)   // POI isn't modularized yet, but fx-rdc is
public class PoiTableUtilities extends TableUtilities {
  ...
}
```

- `@Service(TableUtilities.class)` makes `ServiceFinder` return `PoiTableUtilities` wherever the RDC
  asks for the singleton `TableUtilities.getInstance()` — including the *Export* /
  *Export selected rows* items wired up in
  [`TablePopup`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/table/TablePopup.java).
- `@ClasspathFirst(TableUtilities.class)` is required because Apache POI is not yet a proper JPMS
  module (see the caveat below).

Only two methods are overridden:

- `selectSpreadsheetFile(...)` — opens the file dialog with the `.xls` extension and remembers the
  last directory per table, using [persisted preferences](../../../../tentackle-core/src/main/docs/preferences.md)
  (key prefix `lastXlsNames/`).
- `toSpreadsheet(columnConfigurations, file, items)` — builds the workbook from the table's visible
  [`TableColumnConfiguration`](../../../../tentackle-fx/src/main/java/org/tentackle/fx/table/TableColumnConfiguration.java)s.

## What the export produces

The exporter walks each visible column's binding and maps the model value to a typed cell:

| Model value                                        | Cell rendering                                                        |
|----------------------------------------------------|-----------------------------------------------------------------------|
| `Boolean`                                          | boolean cell                                                          |
| `BMoney`                                           | numeric cell with a `#,##0[.0…]` format matching the money scale       |
| any other `Number`                                 | numeric cell                                                          |
| `Date` / `Calendar` / `LocalDate`                  | date cell with the built-in `m/d/yy` format                           |
| `LocalDateTime`                                    | date cell with the built-in `m/d/yy h:mm` format                      |
| `OffsetDateTime` / `ZonedDateTime`                 | as `LocalDateTime`: a spreadsheet has no notion of a time zone         |
| `Instant`                                          | as `LocalDateTime`, resolved in the JVM's default zone                 |
| `LocalTime` / `OffsetTime`                         | numeric cell holding the fraction of a day, format `h:mm:ss`           |
| anything else (`!= null`)                          | rich-text string from `toString()`                                    |

Column headers are written as a bold, italic, centered first row. Number formats are cached and
reused per scale, so a large export does not create one cell style per row.

Note that a spreadsheet time has no sub-second precision, and that the value of an enum is written
as its `toString()` rather than the localized text the table shows.

## Sheet limits

The `.xls` (BIFF8) format holds at most **65,536 rows** (the header included) and **256 columns**.
`toSpreadsheet(...)` checks both up front and throws an `RdcRuntimeException` naming the limit,
rather than letting POI fail with an `IllegalArgumentException` that names nothing but the offending
index. Invisible columns don't count. Exports that big need one of the OOXML formats
(`XSSFWorkbook`, or `SXSSFWorkbook` for streaming), which this module does not currently use.

## JPMS / POI caveat

Apache POI 5.x still depends on filename-based automatic modules (e.g. `commons-math3`), so this
module deliberately has **no `module-info.java`** and relies on `@ClasspathFirst`.

If you build a distribution image with the
[jlink/jpackage plugin](../../../../tentackle-jlink-maven-plugin/src/main/docs/jlinkpackage.md), keep
the POI artifact on the **classpath** — moving it to the module path (even via `modulePathOnly`)
fails, because an automatic module referencing it uses the wrong class loader and throws
`ClassNotFoundException`:

```xml
<plugin>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-jlink-maven-plugin</artifactId>
  <configuration>
    <classpathDependencies>
      <dependency>
        <groupId>org.apache.poi</groupId>
        <artifactId>poi</artifactId>
      </dependency>
    </classpathDependencies>
  </configuration>
</plugin>
```

Once POI and all its transitive dependencies are fully modularized, the module can declare a normal
`module-info.java` and this special handling can be dropped.

## Unit testing the export

The module's own tests (`src/test/java`) run **without a JavaFX toolkit**. That is possible because
the export only touches the table *configuration*, never a `Control`:

- A `DefaultTableConfiguration` is filled with columns and bound to a plain row class with
  `@Bindable` getters (`config.getBinder().bind()`), which yields the very same `FxTableBinding`s the
  RDC uses at runtime.
- Column visibility is toggled through `getTableColumn()` / `getTreeTableColumn()` — the column
  configuration creates those lazily, and a `TableColumn` is not a `Control`.
- The workbook is written to a temporary file and read back with POI, so cell types, values, number
  formats and the reuse of cell styles are verified on the real `.xls` output.

`selectSpreadsheetFile(...)` would open a `FileChooser`, so the tests register a
`TestRdcUtilities` via `@Service(RdcUtilities.class)` that records the arguments instead
(hence the `test-analyze` goal in the module's `pom.xml`). The same mechanism verifies that
`TableUtilities.getInstance()` really resolves to `PoiTableUtilities`.

## See also

- [tentackle-fx-rdc — Rich Desktop Client](../../../../tentackle-fx-rdc/src/main/docs/rdc.md)
- [Service and Configuration API](../../../../tentackle-common/src/main/docs/services.md)
- [Preferences](../../../../tentackle-core/src/main/docs/preferences.md)
- [Jlink/Jpackage Maven Plugin](../../../../tentackle-jlink-maven-plugin/src/main/docs/jlinkpackage.md)
