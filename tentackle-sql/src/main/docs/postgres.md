# The PostgreSQL Backend — Peculiarities

[`Postgres`](../java/org/tentackle/sql/backends/Postgres.java) is the reference backend several others build on —
[`CockroachDb`](../java/org/tentackle/sql/backends/CockroachDb.java) extends it directly and speaks the same wire
protocol and mostly the same SQL. This document collects PostgreSQL's own peculiarities, in particular its `JSON`
vs. `JSONB` choice, which the model cannot influence today.

---

## Type Mapping

| `SqlType`    | PostgreSQL type | Note                                                                |
|--------------|-----------------|---------------------------------------------------------------------|
| `VARCHAR`    | `TEXT`          | an attribute **without a size** (`getDefaultSize` returns 0)        |
| `VARCHAR(n)` | `VARCHAR(n)`    | an attribute with an explicit size                                  |
| `CLOB`       | `TEXT`          | `isClobSupported()` is `false` — `TEXT` is used instead             |
| `BLOB`       | `BYTEA`         |                                                                     |
| `XML`        | `XML`           | native; pgjdbc registers it as `Types.SQLXML`                       |
| `JSON`       | `JSONB`         | **always binary-optimized — see [JSON and JSONB](#json-and-jsonb)** |
| `UUID`       | `UUID`          | native (`isUUIDSupported()`)                                        |

---

## JSON and JSONB

PostgreSQL provides two distinct native types for the model's `JSON` attribute type: plain `json`, which stores
the exact text that was written, and `jsonb`, a binary-optimized representation that is indexable, validates on
write, and normalizes the document (insignificant whitespace is dropped, object keys are reordered and
de-duplicated). `Postgres.sqlTypeToString()` always chooses `jsonb`
(`tentackle-sql/src/main/java/org/tentackle/sql/backends/Postgres.java:340`):

```java
case JSON -> TYPE_JSONB;   // jsonb is indexable and validates on write, unlike json
```

**There is currently no way to request plain `json` from the model** — the choice is hard-coded, not a per-backend
capability the model can select. On read-back, both `json` and `jsonb` columns are recognized as the model's
`JSON` type (`Postgres.jdbcTypeToSqlType()`, matching the JDBC type name case-insensitively), so an existing plain
`json` column (created by hand, or inherited from another tool) is accepted by the migrator without triggering an
`ALTER COLUMN TYPE`.

### JSON/JSONB columns cannot be indexed through the model

`JsonType.isComparable()` returns `false` — PostgreSQL provides no general-purpose comparator for a JSON document,
so `EntityImpl` and `IndexImpl` reject any attempt to put a `JSON`-typed attribute into a model index
(`tentackle-model/src/main/java/org/tentackle/model/impl/EntityImpl.java:1440`,
`tentackle-model/src/main/java/org/tentackle/model/impl/IndexImpl.java:557`).

PostgreSQL, however, supports indexing a `jsonb` column with a **GIN index** (`CREATE INDEX ... USING gin (col)`),
which is exactly the reason `jsonb` was chosen as the default mapping in the first place. Tentackle has no concept
of an index method (`USING gin`/`btree`/...) in the model, so a GIN index cannot be declared there.

**Workaround:** create the GIN index by hand, outside the model, and give it a name starting with an underscore.
`Backend.isTemporaryName()` treats any such name as temporary
(`tentackle-sql/src/main/java/org/tentackle/sql/Backend.java:346-350`,
default implementation `AbstractBackend.isTemporaryName()`: `name.startsWith("_")`), and
`TableMetaData.setupFromMetaData()` skips temporary index names entirely when it reads the live schema
(`tentackle-sql/src/main/java/org/tentackle/sql/metadata/TableMetaData.java:147`). Such an index is therefore
invisible to the migrator — it is never seen, never reported as an unmanaged extra index to drop, and never
conflicts with the model:

```sql
CREATE INDEX _payload_doc_gin ON payload USING gin (doc);
```

Run this once (e.g. as a one-off statement outside the generated migration, or hand-added to it) after the table
exists. The index survives every subsequent migration run untouched.

---

## Other Peculiarities

- **Reserved words beyond SQL-92**: `TEXT` and `UUID` (`RESERVED_WORDS_PG`).
- **Reserved schemas**: `information_schema`, `pg_catalog`, `pg_temp_1`, `pg_toast`, `pg_toast_temp_1`, in addition
  to the default schema `public` (which, unlike other reserved names, remains a legal schema name).
- **63-character identifiers** (`getMaxNameLength()`).
- **Sequences** via `NEXTVAL('name')` as a string literal (unlike MariaDB, where `NEXTVAL` takes the sequence's
  identifier).
- **Deferrable constraints** are supported (`isSetConstraintsDeferredSupported()`), unlike CockroachDB — see
  [The CockroachDB Backend](cockroachdb.md).
- **Filtered and function-based indexes** are both supported.
- **The locking clause comes before the row limit**: `SELECT ... FOR UPDATE LIMIT ? OFFSET ?` — the opposite order
  from MySQL/MariaDB.
- **`ANY`/`ALL` array operators** are supported (`isArrayOperatorSupported()`).
- **Deadlock and lock-timeout SQLStates** `40P01` (deadlock) and `55P03` (lock not available) are additionally
  classified as transient, on top of the standard `40001`.
- **The backend id** is the server-side PID (`SELECT pg_backend_pid()`).

---

## Related Documentation

- [Tentackle SQL](sql.md) — the backend abstraction this backend implements.
- [The CockroachDB Backend](cockroachdb.md) — a backend built on Postgres's wire protocol, and where it
  deliberately deviates (no native `xml` type, no deferrable constraints, index names per table).
- [Model Definition](../../../../tentackle-model/src/main/docs/model-definition.md) — where the `JSON`/`XML`
  attribute types and indexes are declared.
- [Tentackle SQL Maven Plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) —
  generates the DDL and the migration scripts discussed above.
