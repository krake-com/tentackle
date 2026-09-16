# The Firebird Backend — Peculiarities

[`Firebird`](../java/org/tentackle/sql/backends/Firebird.java) supports Firebird 4.0 and newer. It is built on
[`AbstractSql2008Backend`](../java/org/tentackle/sql/backends/AbstractSql2008Backend.java), i.e. it pages with the
standard `OFFSET ? ROWS FETCH FIRST ? ROWS ONLY`. This document collects what deviates from the other backends and
what the model must take into account.

> The backend is selected by the JDBC URL of the Jaybird driver, `jdbc:firebirdsql://host/database` or
> `jdbc:firebird://host/database` (`org.firebirdsql.jdbc.FBDriver`, which the application has to provide).

**Minimum version.** `Firebird.validateVersion()` requires **Firebird 4.0**, the first version with identifiers of
up to 63 characters and decimals with a precision of up to 38 digits.

---

## Type Mapping

| `SqlType`          | Firebird type                | Note                                                                      |
|--------------------|------------------------------|---------------------------------------------------------------------------|
| `VARCHAR(n)`       | `VARCHAR(n)`                 | `n` is capped at 8191: 32 765 bytes with up to 4 bytes per UTF-8 character |
| `VARCHAR`          | `VARCHAR(8191)`              | an attribute without a size                                               |
| `CLOB`             | `BLOB SUB_TYPE TEXT`         |                                                                           |
| `SQLXML`           | `BLOB SUB_TYPE TEXT`         | no native XML type; `isSqlxmlSupported()` is `false`                     |
| `JSON`             | `BLOB SUB_TYPE TEXT`         | no native JSON type; `isJsonSupported()` is `false`                      |
| `BLOB`             | `BLOB SUB_TYPE BINARY`       |                                                                           |
| `BIT`              | `BOOLEAN`                    | defaults are written as `TRUE`/`FALSE`, not as strings                    |
| `TINYINT`          | `SMALLINT`                   | there is no `TINYINT`                                                     |
| `FLOAT` / `DOUBLE` | `FLOAT` / `DOUBLE PRECISION` |                                                                           |
| `DECIMAL`          | `DECIMAL(19)`                | if the model gives no size — see below                                    |
| `UUID`             | —                            | `isUUIDSupported()` is `false`, so a UUID is stored as a string           |

Firebird reads a bare `DECIMAL` as `DECIMAL(9,0)` and would silently round away every fraction digit, so an
attribute without an explicit size becomes `DECIMAL(19,<scale>)` (`getDefaultSize`) — the same remedy as on
[MySQL](mysql.md#decimals).

---

## Locking

Firebird's `FOR UPDATE` only declares an updatable cursor — it does **not lock** anything. A write-locking select
therefore ends with `FOR UPDATE WITH LOCK`, after the paging clauses:

```sql
SELECT * FROM foo WHERE id=? OFFSET ? ROWS FETCH FIRST ? ROWS ONLY FOR UPDATE WITH LOCK
```

`WITH LOCK` is only allowed for a select from a single table; the database rejects a write-locking select that
joins other tables.

---

## Indexes

- **The sort order belongs to the index, not to a column.** An index whose columns are all descending is
  generated as `CREATE DESCENDING INDEX foo_idx ON foo (a, b)`. An index mixing ascending and descending columns
  cannot be created at all: `sqlCreateIndex()` throws a `BackendException` while the DDL is generated.
- **No filtered and no function-based indexes** in the model's syntax (`isFilteredIndexSupported()`,
  `isFunctionBasedIndexSupported()`). A model index using either is commented out if it is optional, and rejected
  otherwise.
- **Indexes cannot be renamed** (`sqlRenameIndex()` returns `null`), so the migration drops and re-creates them.

---

## Migration

- **Tables cannot be renamed.** `sqlRenameTable()` throws a `BackendException`, so a migration renaming a table
  fails when it is generated rather than when it is applied. Create the new table, copy the data and drop the old
  one by hand.
- **Columns change one property at a time**: `ALTER TABLE foo ALTER COLUMN name TO label` renames,
  `ALTER COLUMN name TYPE VARCHAR(64)` retypes, and the null constraint and the default change separately
  (`SET`/`DROP NOT NULL`, `SET`/`DROP DEFAULT`). Firebird rejects type changes that could lose data, such as
  shrinking a column.
- **`ADD` and `DROP` take no `COLUMN` keyword**: `ALTER TABLE foo ADD extra INTEGER`, `ALTER TABLE foo DROP extra`.
- **Defaults are read back as source text.** Depending on the driver version, the default of a column is reported
  including the `DEFAULT` keyword. `FirebirdColumnMetaData` strips it, so the migration compares the value only.

---

## Other Peculiarities

- **No schemas.** `isSchemaSupported()` returns `false` — schemas arrive with Firebird 6. A model using schemas
  must map them, see [Table Names](../../../../tentackle-session/src/main/docs/table-names.md).
- **63-character identifiers, starting with a letter.** As on [Oracle](oracle.md), the migrator's temporary names
  start with `tmp_` instead of an underscore, and names starting with an underscore are rejected at build time.
- **Reserved names.** The system tables `RDB$...`, `MON$...` and `SEC$...` are reserved table names. Firebird's
  reserved words beyond SQL:2003, such as `INDEX`, `PLAN`, `OFFSET`, `ROW_COUNT` and `VARIABLE`, are rejected at
  build time (`RESERVED_WORDS_FIREBIRD`).
- **The default comes before the null constraint** in a column declaration: `DEFAULT 0 NOT NULL`.
- **Sequences** are created with `START WITH` before `INCREMENT BY` and read with
  `SELECT NEXT VALUE FOR seq FROM RDB$DATABASE`. The keep-alive select is `SELECT 1 FROM RDB$DATABASE`.
- **Comments** on tables, columns, indexes and sequences via `COMMENT ON`.
- **Vendor error codes.** Besides SQLState `40001`, the ISC error codes **335544336** (deadlock), **335544345**
  (lock conflict), **335544451** (update conflict) and **335544510** (lock timeout) are transient.
- **No deferrable constraints**, **no `IF EXISTS`** on generated drops, and every derived table gets an alias.

---

## Related Documentation

- [Tentackle SQL](sql.md) — the backend abstraction this backend implements.
- [Table Names](../../../../tentackle-session/src/main/docs/table-names.md) — schema mapping, required on Firebird
  because it has no schemas.
- [Model Definition](../../../../tentackle-model/src/main/docs/model-definition.md) — where sizes, scales and
  indexes are declared.
- [Tentackle SQL Maven Plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) —
  generates the DDL and the migration scripts discussed above.
