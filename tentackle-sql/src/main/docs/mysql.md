# The MySQL and MariaDB Backends — Peculiarities

MySQL and MariaDB share a dialect but not their feature sets, so Tentackle implements
[`MySql`](../java/org/tentackle/sql/backends/MySql.java) and derives
[`MariaDb`](../java/org/tentackle/sql/backends/MariaDb.java) from it. This document collects what deviates from
the other backends and what the model must take into account.

> Pick the backend by JDBC URL: `jdbc:mysql:...` selects `MySQL`, `jdbc:mariadb:...` selects `MariaDB`. Because
> the MariaDB driver connects to MySQL servers as well, append `|MySQL` to the URL when a MariaDB URL points at a
> MySQL server.

**Minimum versions.** `MySql.validateVersion()` requires **MySQL 8.0** (8.0.13 in fact — functional indexes and
defaults on `TEXT` columns; only the major version is available for the check).
`MariaDb.validateVersion()` requires **MariaDB 10.3**, the first version with sequences.

---

## Type Mapping

| `SqlType`   | MySQL/MariaDB type | Note                                                                       |
|-------------|--------------------|----------------------------------------------------------------------------|
| `VARCHAR(n)`| `VARCHAR(n)`       | `n` is capped at 4096 (`getMaxSize`)                                        |
| `VARCHAR`   | `LONGTEXT`         | an attribute **without a size** — see [Sizeless Strings](#sizeless-strings) |
| `CLOB`      | `LONGTEXT`         | there is no `CLOB`, and `TEXT` holds only 64 KB                            |
| `BLOB`      | `LONGBLOB`         | `BLOB` holds only 64 KB                                                    |
| `DECIMAL`   | `DECIMAL(19)`      | if the model gives no size — see [Decimals](#decimals)                     |
| `TIMESTAMP` | `DATETIME(6)`      | without the `(6)` MySQL truncates to whole seconds                         |
| `TIME`      | `TIME(6)`          | ditto                                                                       |
| `BIT`       | `TINYINT`          | so are `TINYINT`; `SMALLINT`/`INTEGER`/`BIGINT` become `SMALLINT`/`INT`/`BIGINT` |

### Sizeless Strings

An attribute declared without a size maps to `LONGTEXT`, not to `VARCHAR(4096)`. A declared `VARCHAR` length
counts twice against MySQL's limits — towards the **65 535 bytes per row** and towards the **3072 bytes per index
key** — and with `utf8mb4` a single `VARCHAR(4096)` already claims 16 KB of both budgets.

> ### ⚠️ A large string column cannot be indexed
>
> MySQL indexes a `TEXT` column only with an explicit key length (`col(255)`), which the model has no syntax for,
> and rejects a `VARCHAR` whose declared length exceeds 3072 bytes. **An indexed string attribute must therefore
> declare a size of at most 768 characters** (3072 bytes ÷ 4 for `utf8mb4`). This is a MySQL limitation, not a
> Tentackle one: PostgreSQL, Oracle, H2 and SQL Server index a large string column without complaining.

Because `TEXT` and `BLOB` columns accept a default only when it is written as an *expression*
(MySQL 8.0.13+, MariaDB 10.2.1+), `columnTypeNullDefaultToString()` emits `DEFAULT ('x')` rather than
`DEFAULT 'x'` for them.

### Decimals

MySQL reads a bare `DECIMAL` as `DECIMAL(10,0)` — it would silently round away every fraction digit. An
attribute without an explicit size therefore becomes `DECIMAL(19,<scale>)` (`getDefaultSize`). The scale is
capped at 30 (`getMaxScale`), which is MySQL's limit regardless of the precision.

This matters for `DMoney` and `BigDecimal` attributes: on PostgreSQL and Oracle an unsized decimal column keeps
whatever scale the value has, on MySQL the precision must be declared in the column.

---

## Indexes

- **No filtered indexes.** `isFilteredIndexSupported()` is `false` for both. A model index with a filter
  condition is commented out if it is optional, and rejected otherwise.
- **Functional indexes on MySQL only, and they need extra parentheses.** MySQL 8.0.13+ requires a functional key
  part to be enclosed in its own pair of parentheses, so `MySql.sqlCreateIndex()` emits
  `CREATE INDEX foo_idx ON foo ((LOWER(name)))`.
- **MariaDB has no functional indexes at all** — an expression can only be indexed through a generated column,
  which is outside the model's scope. `MariaDb.isFunctionBasedIndexSupported()` therefore returns `false`.
- Index names are scoped to their table, so `DROP INDEX` needs the table: `DROP INDEX idx ON tbl`.

---

## Migration

MySQL cannot change a single property of a column. `ALTER TABLE ... ALTER COLUMN` only sets or drops the
default; everything else — type, size, null-constraint, comment — requires the **whole declaration** to be
repeated with `MODIFY COLUMN`, or with `CHANGE` if the column is renamed at the same time. `getMigrationStrategy()`
therefore returns the single strategy `NAME_AND_TYPE` where other backends return a list of fine-grained steps.

The consequence for hand-editing generated migrations: dropping the `COMMENT` or the `DEFAULT` from a
`MODIFY COLUMN` statement does not leave them alone — it **removes** them.

---

## Other Peculiarities

- **No schemas.** `isSchemaSupported()` returns `false`: a schema *is* a database. A model using schemas must map
  them — see [Table Names](../../../../tentackle-session/src/main/docs/table-names.md), which applies to Oracle as
  well. `getMetaData()` still supports the unmapped case by opening one connection per database.
- **The locking clause comes last.** `SELECT ... LIMIT ? OFFSET ? FOR UPDATE`; unlike PostgreSQL, MySQL does not
  accept `FOR UPDATE` before the row limit. And since there is no `OFFSET` without a `LIMIT`, a query with only an
  offset is given a limit of `Integer.MAX_VALUE`.
- **Every derived table needs an alias** (`isAliasRequiredForSubSelect()`), which is why
  `Query.getRowCount()` wraps its count select in `... ) AS F_O_O`.
- **64-character identifiers** (`getMaxNameLength()`), and a long list of reserved words beyond SQL-92 —
  `LIMIT`, `INDEX`, `LOCK`, `RANK`, `REPLACE`, `LONG`, `DUAL` and many more are rejected at build time
  (`RESERVED_WORDS_MYSQL`).
- **Foreign keys are dropped with `DROP FOREIGN KEY`**, not `DROP CONSTRAINT` (which MySQL learned only in
  8.0.19). MariaDB additionally supports `IF EXISTS` on drops (`isDropIfExistsSupported()`).
- **Lock wait timeout is transient.** Besides the deadlock (error 1213 / SQLState 40001),
  `isTransientTransactionException()` also reports error **1205**, "lock wait timeout exceeded", whose SQLState is
  the useless `HY000`. Both make the transaction worth retrying.
- **Expressions must not refer to the table being updated**
  (`isExpressionReferringToTableBeingUpdatedSupported()` is `false`).
- **`UUID` is stored as `VARCHAR(36)`** on both. MariaDB has a native `UUID` type since 10.7, but
  `isUUIDSupported()` is a static capability with no access to the server version, so enabling it would break
  every older server. Storing the UUID as text costs space but works everywhere; a dedicated backend selected by
  `|<name>` (as `Oracle8` does for older Oracle servers) would be the way to opt in.
- **Sequences on MariaDB only** (10.3+), and `NEXTVAL` takes the sequence's *identifier*:
  `SELECT NEXTVAL(numpool_seq)` — unlike PostgreSQL, where it is a string literal. MySQL has no sequences at all,
  so number sources must use a table-based pool there.

---

## Related Documentation

- [Tentackle SQL](sql.md) — the backend abstraction these backends implement.
- [The Oracle Backend](oracle.md) — the peculiarities of the other schema-less backend.
- [Table Names](../../../../tentackle-session/src/main/docs/table-names.md) — schema mapping, required on MySQL
  and MariaDB because they have no schemas.
- [Model Definition](../../../../tentackle-model/src/main/docs/model-definition.md) — where sizes, scales and
  indexes are declared.
- [Tentackle SQL Maven Plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) —
  generates the DDL and the migration scripts discussed above.
