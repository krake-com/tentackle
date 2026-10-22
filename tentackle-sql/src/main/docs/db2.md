# The DB2 Backend — Peculiarities

[`Db2`](../java/org/tentackle/sql/backends/Db2.java) supports IBM DB2. It extends the SQL:2003 base class
[`AbstractSql2003Backend`](../java/org/tentackle/sql/backends/AbstractSql2003Backend.java). This document collects
what deviates from the other backends and what the model must take into account.

> The backend is selected by a JDBC URL containing `:db2` (`com.ibm.db2.jcc.DB2Driver`, which the application
> has to provide). There is no version check, and XA is not supported by this backend.

---

## Type Mapping

| `SqlType`                    | DB2 type       | Note                                                            |
|------------------------------|----------------|-----------------------------------------------------------------|
| `VARCHAR(n)`                 | `VARCHAR(n)`   | `n` is capped at 512 (`getMaxSize`)                             |
| `VARCHAR`                    | `VARCHAR(512)` | an attribute without a size gets the maximum size               |
| `CHAR`                       | `CHAR(1)`      |                                                                 |
| `CLOB`                       | `CLOB`         |                                                                 |
| `XML`                        | `XML`          | native; `isSqlxmlSupported()` is `true`                         |
| `JSON`                       | `CLOB`         | no native JSON column type                                      |
| `BLOB`                       | `BLOB`         |                                                                 |
| `BIT`, `TINYINT`, `SMALLINT` | `SMALLINT`     | booleans and bytes are stored as small integers                 |
| `FLOAT`                      | `REAL`         | `DOUBLE` stays `DOUBLE`                                         |
| `DECIMAL`                    | `DECIMAL`      | the maximum precision is 31, see below                          |
| `TIME`                       | `TIME`         |                                                                 |
| `UUID`                       | —              | `isUUIDSupported()` is `false`, so a UUID is stored as a string |

> ### ⚠️ Always give a decimal a size
>
> A bare `DECIMAL` is `DECIMAL(5,0)` in DB2, which silently rounds away every fraction digit and overflows beyond
> 99999. Unlike the H2 and MySQL backends, the DB2 backend supplies no default precision, so declare the size and
> scale of every `BigDecimal` attribute in the model.

---

## Queries

- **Paging** is emulated with the window function `ROW_NUMBER()`: the original select is wrapped into an enclosing
  select that filters by the row number. Such a select cannot be combined with a write lock, and a select
  starting with `WITH` cannot be paged at all.
- **Sequences** are supported (`NEXT VALUE FOR`).

---

## Schema and Migration

- **Names**: column names may be up to 30 characters long, all other names up to 128.
- **DDL scripts are line-oriented.** Each line of a `CREATE TABLE` statement ends with a backslash, the
  continuation character of the DB2 command line processor.
- **Comments** are created with `COMMENT ON` for tables, columns and indexes.
- **Columns are added** as `ALTER TABLE ... ADD (...)`.
- **Column alterations** change the type with `ALTER COLUMN ... SET DATA TYPE`. The null constraint and the
  default are separate alterations.
- **Indexes are renamed** with `RENAME INDEX`.
- **Function-based indexes** are supported, filtered indexes are not.
- **`DROP ... IF EXISTS`** is supported (see `setDropIfExistsEnabled`).

---

## Runtime

- **Transient errors.** Besides the standard SQLState `40001`, the SQLState `57014` (statement canceled) and the
  error code `-952` (interrupted) mark a transaction as worth retrying.
- **The backend id** is the DB2 application id (`application_id()`).

---

## Related Documentation

- [Tentackle SQL](sql.md) — the backend abstraction this backend implements.
- [Tentackle SQL Maven Plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) —
  generates the DDL and the migration scripts.
- [Model Definition](../../../../tentackle-model/src/main/docs/model-definition.md) — where the size and scale of
  an attribute are declared.
