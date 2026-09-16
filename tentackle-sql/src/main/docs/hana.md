# The SAP HANA Backend — Peculiarities

[`Hana`](../java/org/tentackle/sql/backends/Hana.java) supports SAP HANA 2.0 and SAP HANA Cloud. This document
collects what deviates from the other backends and what the model must take into account.

> The backend is selected by the JDBC URL of SAP's driver, `jdbc:sap://host:port/...`
> (`com.sap.db.jdbc.Driver`, which the application has to provide).

**Minimum version.** `Hana.validateVersion()` requires **SAP HANA 2.0**. SAP HANA Cloud reports the major
version 4 and passes as well.

---

## Type Mapping

| `SqlType`    | HANA type        | Note                                                                                  |
|--------------|------------------|---------------------------------------------------------------------------------------|
| `VARCHAR(n)` | `NVARCHAR(n)`    | `n` is capped at 5000 (`getMaxSize`); `VARCHAR` holds ASCII only on HANA 2.0          |
| `VARCHAR`    | `NVARCHAR(5000)` | an attribute without a size                                                           |
| `CHAR`       | `NCHAR(1)`       | for the same reason                                                                   |
| `CLOB`       | `NCLOB`          |                                                                                       |
| `SQLXML`     | `NCLOB`          | no native XML type; `isSqlxmlSupported()` is `false`                                 |
| `BLOB`       | `BLOB`           |                                                                                       |
| `BIT`        | `BOOLEAN`        | defaults are written as `TRUE`/`FALSE`, not as strings                                |
| `TINYINT`    | `SMALLINT`       | HANA's `TINYINT` is **unsigned** (0–255) and cannot hold a negative Java `byte`       |
| `FLOAT`      | `REAL`           | `DOUBLE` stays `DOUBLE`                                                               |
| `DECIMAL`    | `DECIMAL`        | without a size, a decimal floating-point number that keeps the scale of each value    |
| `UUID`       | —                | `isUUIDSupported()` is `false`, so a UUID is stored as a string                       |

The generated `CREATE TABLE` does not name a table type (`COLUMN` or `ROW`), so the database's configured default
applies. HANA Cloud knows column tables only.

---

## Migration

HANA alters a column with `ALTER TABLE ... ALTER (<column declaration>)`, and the new declaration **replaces**
the old one. There is no statement that changes only the default or only the null constraint, which shapes the
migration:

- `sqlAlterColumnType()` always emits the whole declaration, including the default and an explicit `NULL` or
  `NOT NULL`: `ALTER TABLE foo ALTER (name NVARCHAR(64) DEFAULT 'x' NOT NULL)`.
- `sqlAlterColumnNullConstraint()` emits the same statement, and `sqlAlterColumnDefault()` returns `null`.
- `getMigrationStrategy()` therefore never returns `DEFAULT`. A changed null constraint yields `NULL` — preceded
  by the `UPDATE` that replaces existing `NULL`s if the column becomes `NOT NULL` — which carries the type and
  the default along. Otherwise, a changed type, size, scale or default yields `TYPE`.
- Renaming a column and changing its comment remain separate steps: `RENAME COLUMN foo.name TO label` and
  `COMMENT ON COLUMN`.

As on [MySQL](mysql.md), dropping the `DEFAULT` from a generated `ALTER` statement when hand-editing a migration
does not leave the default alone — it **removes** it.

Tables and indexes are renamed with `RENAME TABLE` and `RENAME INDEX`. Columns are added and dropped with
`ALTER TABLE ... ADD (...)` and `ALTER TABLE ... DROP (...)`.

---

## Other Peculiarities

- **Paging needs a limit.** `SELECT ... LIMIT ? OFFSET ? FOR UPDATE` — the locking clause comes last, and since
  there is no `OFFSET` without a `LIMIT`, a query with only an offset is given a limit of `Integer.MAX_VALUE`.
- **The default comes before the null constraint** in a column declaration: `DEFAULT 0 NOT NULL`.
- **No filtered and no function-based indexes** (`isFilteredIndexSupported()`, `isFunctionBasedIndexSupported()`).
  A model index using either is commented out if it is optional, and rejected otherwise.
- **No deferrable constraints.** Deferrable unique indexes and foreign keys are created as plain ones.
- **Comments** are supported on tables and columns (`COMMENT ON`), but not on indexes and sequences.
- **Sequences** are read with `SELECT seq.NEXTVAL FROM DUMMY`, and the keep-alive select is `SELECT 1 FROM DUMMY`.
- **127-character identifiers** (`getMaxNameLength()`), and reserved words beyond SQL:2003 such as `LIMIT`, `TOP`,
  `MINUS`, `ROWID`, `NEXTVAL` and `CURRVAL` are rejected at build time (`RESERVED_WORDS_HANA`). The schemas `SYS`
  and `PUBLIC` are reserved.
- **Vendor error codes.** Besides the standard SQLStates, the backend checks HANA's error codes: **131** (lock wait
  timeout), **133** (deadlock) and **146** (resource busy with NOWAIT) are transient, **301** (unique constraint
  violated) is a constraint violation, and **-10709** and **-10807** (connection failed, connection down) mean the
  communication link is lost.
- **No `IF EXISTS`** on generated drops.

---

## Related Documentation

- [Tentackle SQL](sql.md) — the backend abstraction this backend implements.
- [The MySQL and MariaDB Backends](mysql.md) — the other backends migrating by full column declaration.
- [Model Definition](../../../../tentackle-model/src/main/docs/model-definition.md) — where sizes, scales and
  indexes are declared.
- [Tentackle SQL Maven Plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) —
  generates the DDL and the migration scripts discussed above.
