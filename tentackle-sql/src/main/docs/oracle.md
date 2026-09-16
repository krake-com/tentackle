# The Oracle Backend — Peculiarities

Oracle deviates from the SQL standard — and from every other backend Tentackle supports — in more ways than any
other product. Most of those deviations are handled silently by
[`Oracle8`](../java/org/tentackle/sql/backends/Oracle8.java) (the deprecated backend for versions 8 to 11) and
[`Oracle`](../java/org/tentackle/sql/backends/Oracle.java) (version 12 and newer), but a few of them leak into
what you may reasonably expect from your model. This document collects them.

> Pick the backend by JDBC URL (`jdbc:oracle:...` selects `Oracle`) and append `|Oracle8` to the URL to pin the
> legacy dialect. `Oracle` refuses to connect to a server older than 12 (`validateVersion`).

---

## Filtered Indexes Are Emulated by Function-Based Indexes

Oracle supports neither `CREATE INDEX ... WHERE <condition>` nor any other form of partial index. A model index
carrying a filter condition ([Model Definition §7](../../../../tentackle-model/src/main/docs/model-definition.md)),
such as

```
unique index code := code | type = 0
```

is therefore translated into a **function-based index** over a `CASE` expression:

```sql
CREATE UNIQUE INDEX foo_code ON foo (CASE WHEN (type = 0) THEN code END);
```

The trick works because a row that does not match the condition gets a `NULL` key, and **Oracle never stores
all-NULL keys in a B-tree index**. Such rows drop out of the index entirely — which is exactly the semantics of a
partial index, and it makes the *conditional uniqueness* rule enforceable.

Every index column is wrapped in the same `CASE`, so a multi-column filtered index also produces an all-NULL key
for non-matching rows:

```
unique index code := code, name | type = 0
```

```sql
CREATE UNIQUE INDEX foo_code ON foo (CASE WHEN (type = 0) THEN code END,
                                     CASE WHEN (type = 0) THEN name END);
```

### ⚠️ Such an index will not speed up your queries

This is the important caveat. A function-based index is only considered by the Oracle optimizer if the query
contains the **exact same expression** as the index definition — here, the literal
`CASE WHEN (type = 0) THEN code END`.

Tentackle's select wurblets (`PdoSelectList`, `PdoSelectUnique`, and everything derived from the model) generate
ordinary predicates:

```sql
SELECT ... FROM foo WHERE type = 0 AND code = ?
```

Oracle will **not** match that against the `CASE`-based index. Consequently:

- On Oracle, the value of a filtered index lies in the **constraint it enforces**, not in read performance. Use
  `unique index ... | <condition>` when you need conditional uniqueness — that keeps working exactly as on
  PostgreSQL or SQL Server.
- A **non-unique** filtered index is close to pointless on Oracle. It saves index space, but no generated query
  will ever use it. Either declare it `optional` (so it is simply skipped on backends that cannot do it well), or
  drop the filter and accept a full index.
- If you really need the index to be used for reads, the query must be hand-written with the identical `CASE`
  expression. Tentackle does not generate such SQL.

### Further consequences of the all-NULL rule

- Rows that *do* match the condition but whose indexed columns are all `NULL` are **also** absent from the index.
  A query with `IS NULL` can never be answered from it.
- Unlike PostgreSQL, this is not a property of the filter but of Oracle's B-tree indexes in general; the same is
  true for any plain multi-column index on Oracle.

---

## Filtered, Function-Based and Descending Indexes Are All Virtual Columns

Whenever an index is not a plain list of columns, Oracle materializes a hidden, system-generated virtual column
for it. This affects three cases:

| Model                         | What Oracle really creates                                            |
|-------------------------------|-----------------------------------------------------------------------|
| `index x := code \| type = 0` | index over `CASE WHEN ("TYPE"=0) THEN "CODE" END`                     |
| `index x := upper(name)`      | index over `UPPER("NAME")`                                            |
| `index x := -code`            | index over `"CODE"` (descending indexes are function-based in Oracle) |

`DatabaseMetaData.getIndexInfo()` then reports:

- `COLUMN_NAME` as `SYS_NC0000n$` instead of the real column, and
- `FILTER_CONDITION` as `null` — always, since JDBC has no notion of Oracle's emulation.

Taken at face value, the migrator would compare that against the model, find *both* the columns and the filter
different, and emit a `DROP INDEX` + `CREATE INDEX` pair on **every single run** of the SQL Maven plugin.

To prevent that, [`OracleIndexMetaData`](../java/org/tentackle/sql/metadata/OracleIndexMetaData.java) queries
Oracle's data dictionary for the real expressions:

```sql
SELECT COLUMN_POSITION, COLUMN_EXPRESSION FROM ALL_IND_EXPRESSIONS
 WHERE TABLE_NAME = ? AND INDEX_NAME = ? [AND INDEX_OWNER = ?]
```

and [`OracleIndexColumnMetaData`](../java/org/tentackle/sql/metadata/OracleIndexColumnMetaData.java) parses each
expression back into the model's view: a column name, an optional function name and — for the `CASE` form — the
filter condition.

Notes for operators:

- The connecting user needs `SELECT` on `ALL_IND_EXPRESSIONS`. That privilege is granted to `PUBLIC` by default,
  and the `ALL_` view only exposes objects the user may see anyway.
- `COLUMN_EXPRESSION` is a `LONG` column and must be the last one in the select list — hence the column order
  above.
- Table and index names are passed in **uppercase**: Tentackle generates unquoted identifiers, which Oracle folds
  to uppercase, while the metadata model keeps everything in lowercase.
- Oracle's double quotes around identifiers are stripped before the condition is compared with the model's, so
  `("TYPE"=0)` and `type = 0` are recognized as equal (the comparison in
  [`IndexMigrator`](../../../../tentackle-model/src/main/java/org/tentackle/model/migrate/IndexMigrator.java)
  ignores case, whitespace and matching parentheses).

If you ever see an index being dropped and re-created on every migration run, run the query above by hand and
compare its output with the model — that is where the round trip is decided.

---

## Column Defaults Are Stored as Source Text

Oracle does not store a column default as a value, but as the **verbatim source text** of the expression, in the
`LONG` column `ALL_TAB_COLUMNS.DATA_DEFAULT` — and that text includes the whitespace up to the token following
the default value. Since Oracle wants `DEFAULT` *before* `NOT NULL`, Tentackle generates:

```sql
CREATE TABLE td_kafkasndq (
    ...
    serial NUMBER(19) DEFAULT 1 NOT NULL -- object serial
);
```

which Oracle stores — and `DatabaseMetaData.getColumns()` reports in `COLUMN_DEF` — as `"1 "`, with a trailing
blank. Written as a separate statement, the same default ends at the statement terminator and is stored as `"1"`:

```sql
ALTER TABLE td_kafkasndq MODIFY serial DEFAULT 1;
```

Compared literally, `"1 "` differs from the model's `1`, so the migrator would report the default as changed and
emit exactly that `MODIFY` statement for a table it had just created itself. Executing it "fixes" the table only
because it rewrites the stored text without the blank.

[`OracleColumnMetaData`](../java/org/tentackle/sql/metadata/OracleColumnMetaData.java) therefore trims the
default before it is compared. Only the text *outside* the quotes is removed, so the single blank Oracle uses for
the empty string (see below) survives as `' '`.

Every column that is `NOT NULL` **and** has a default is affected — most notably `serial`, which every entity
carries.

### Defaults that are not written the way the model spells them

Two model values do not reach the DDL unchanged, because `valueToLiteral` rewrites them for Oracle:

| Model            | DDL           | Reason                                                                            |
|------------------|---------------|-----------------------------------------------------------------------------------|
| `[default ""]`   | `DEFAULT ' '` | Oracle cannot distinguish `''` from `NULL`, so the empty string is a single blank |
| `[default true]` | `DEFAULT 1`   | booleans are `NUMBER(1)`                                                          |

Comparing the model value with the stored text directly would therefore fail — `""` against `' '`, `TRUE` against
`1` — and unlike the trailing-blank case above, re-applying the `MODIFY` would *not* settle it: the mismatch
would be reported on every single migration run, forever. `Oracle8.isDefaultEqual()` applies the same mapping to
the model value before comparing.

---

## Other Oracle Peculiarities

These are older, and are implemented in `Oracle8` (and thus inherited by `Oracle`):

- **The empty string is `NULL`.** Oracle cannot distinguish `''` from `NULL`, so `getEmptyString()` returns a
  single blank. An attribute set to `""` is stored — and read back — as `" "`.
- **No schemas.** `isSchemaSupported()` returns `false`: in Oracle a "schema" is a user, so Tentackle does not
  generate schema-qualified DDL. A model using schemas must map them — see
  [Table Names](../../../../tentackle-session/src/main/docs/table-names.md), which applies to MySQL and
  MariaDB as well.
- **30-character identifiers.** `getMaxNameLength()` returns 30 for every kind of name. Long entity or index names
  in the model will be rejected at build time.
- **No leading underscore.** Identifiers must not start with `_`, so temporary objects created during a migration
  are prefixed `tmp_` instead of `_tmp` (`isTemporaryName`). Identifiers containing `$` are treated as reserved.
- **Booleans are `NUMBER(1)`.** So are `BIT` and `TINYINT`; `SMALLINT`/`INTEGER`/`BIGINT` become `NUMBER(5|10|19)`.
  `valueToLiteral` renders `true`/`false` as `1`/`0`.
- **`DEFAULT` comes before `NOT NULL`** in a column declaration (see
  [Column Defaults Are Stored as Source Text](#column-defaults-are-stored-as-source-text)), and there is no real
  `DROP DEFAULT` — Tentackle emits `DEFAULT NULL`, which is functionally but not structurally equivalent
  (see [`OracleColumnMetaData`](../java/org/tentackle/sql/metadata/OracleColumnMetaData.java)).
- **`ALTER TABLE` needs parentheses** — `ADD (...)` / `MODIFY (...)` instead of `ADD COLUMN` / `ALTER COLUMN`.
- **An extra commit is required** (`isExtraCommitRequired()`), and `SELECT` without a table needs `FROM DUAL`.
- **Paging** uses `OFFSET ... ROWS FETCH NEXT ... ROWS ONLY` in `Oracle`, but the nested
  `ROWNUM` construct in `Oracle8`.
- **SQLXML is supported**, but needs `xdb.jar` and `xmlparserv2.jar` on the classpath.

---

## Related Documentation

- [Tentackle SQL](sql.md) — the backend abstraction this backend implements.
- [Table Names](../../../../tentackle-session/src/main/docs/table-names.md) — schema mapping and table
  prefixes, required on Oracle because it has no usable schemas.
- [Model Definition](../../../../tentackle-model/src/main/docs/model-definition.md) — the index syntax, including
  filter conditions.
- [Tentackle SQL Maven Plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) —
  generates the DDL and the migration scripts discussed above.
