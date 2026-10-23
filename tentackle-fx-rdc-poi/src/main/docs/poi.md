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
@ClasspathFirst(TableUtilities.class)   // this module is no JPMS module (POI requires a filename-based automatic module), but fx-rdc is
public class PoiTableUtilities extends TableUtilities {
  ...
}
```

- `@Service(TableUtilities.class)` makes `ServiceFinder` return `PoiTableUtilities` wherever the RDC
  asks for the singleton `TableUtilities.getInstance()` — including the *Export* /
  *Export selected rows* items wired up in
  [`TablePopup`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/table/TablePopup.java).
- `@ClasspathFirst(TableUtilities.class)` is required because this module cannot be a real JPMS
  module (see the caveat below). Without it, the service from the real module `tentackle-fx-rdc`
  would always win over the one from the classpath.

Two methods are overridden:

- `selectSpreadsheetFile(...)` — opens the file dialog with the `.xls` extension and remembers the
  last chosen file per table. Like every file selection of the RDC, it uses the JDK's
  `java.util.prefs.Preferences.userRoot()`, node `<application name>/lastXlsNames/<table name>`,
  key `path`. A file path only makes sense on the machine it was chosen on, so it is deliberately
  not kept in the database-backed [persisted preferences](../../../../tentackle-core/src/main/docs/preferences.md).
- `toSpreadsheet(columnConfigurations, file, items)` — builds the workbook from the table's visible
  [`TableColumnConfiguration`](../../../../tentackle-fx/src/main/java/org/tentackle/fx/table/TableColumnConfiguration.java)s.

A subclass can additionally override the protected `assertFitsIntoSheet(columnCount, rowCount)`,
which checks the [sheet limits](#sheet-limits).

## What the export produces

The exporter walks each visible column's binding and maps the model value to a typed cell:

| Model value                                         | Cell rendering                                                   |
|-----------------------------------------------------|------------------------------------------------------------------|
| `Boolean`                                           | boolean cell                                                     |
| `BMoney`                                            | numeric cell with a `#,##0[.0…]` format matching the money scale |
| any other `Number`                                  | numeric cell                                                     |
| `java.sql.Timestamp` (e.g. Tentackle's `Timestamp`) | date cell with the built-in `m/d/yy h:mm` format                 |
| `java.sql.Time` (e.g. Tentackle's `Time`)           | numeric cell holding the fraction of a day, format `h:mm:ss`     |
| any other `Date` / `Calendar` / `LocalDate`         | date cell with the built-in `m/d/yy` format                      |
| `LocalDateTime`                                     | date cell with the built-in `m/d/yy h:mm` format                 |
| `OffsetDateTime` / `ZonedDateTime`                  | as `LocalDateTime`: a spreadsheet has no notion of a time zone   |
| `Instant`                                           | as `LocalDateTime`, resolved in the JVM's default zone           |
| `LocalTime` / `OffsetTime`                          | numeric cell holding the fraction of a day, format `h:mm:ss`     |
| anything else (`!= null`)                           | text cell holding the text displayed in the table                |

Column headers are written as a bold, italic, centered first row. Number formats are cached and
reused per scale, so a large export does not create one cell style per row.

The `java.sql` types are checked first: they extend `java.util.Date`, but a date-only format would
hide the time of a timestamp and show a time as January 1, 1970. Null values and columns without a
binding leave the cell empty. The text cells show the same text as the table and the CSV export,
including case conversion and the formatting of the column's cell type. Note that a spreadsheet time
has no sub-second precision.

## Sheet limits

The `.xls` (BIFF8) format holds at most **65,536 rows** (the header included) and **256 columns**.
`toSpreadsheet(...)` checks both up front and throws an `RdcRuntimeException` naming the limit,
rather than letting POI fail with an `IllegalArgumentException` that names nothing but the offending
index. Invisible columns don't count. Exports that big need one of the OOXML formats
(`XSSFWorkbook`, or `SXSSFWorkbook` for streaming), which this module does not currently use.

## JPMS / POI caveat

Since version 5, Apache POI ships a `module-info` (`org.apache.poi.poi`), but it requires
`commons-math3`, which is still a filename-based automatic module. A module depending on POI
therefore must not be published as a real JPMS module, so this one deliberately has
**no `module-info.java`**. Its manifest declares the `Automatic-Module-Name`
`org.tentackle.fx.rdc.poi` instead, and the service relies on `@ClasspathFirst`.

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
- The displayed text of a column comes from its `TableCellType`, which needs no toolkit either.

`selectSpreadsheetFile(...)` would open a `FileChooser`, so the tests register a
`TestRdcUtilities` via `@Service(RdcUtilities.class)` that records the arguments instead
(hence the `test-analyze` goal in the module's `pom.xml`). The same mechanism verifies that
`TableUtilities.getInstance()` really resolves to `PoiTableUtilities`.

## See also

- [tentackle-fx-rdc — Rich Desktop Client](../../../../tentackle-fx-rdc/src/main/docs/rdc.md)
- [Service and Configuration API](../../../../tentackle-common/src/main/docs/services.md)
- [Preferences](../../../../tentackle-core/src/main/docs/preferences.md)
- [Jlink/Jpackage Maven Plugin](../../../../tentackle-jlink-maven-plugin/src/main/docs/jlinkpackage.md)
