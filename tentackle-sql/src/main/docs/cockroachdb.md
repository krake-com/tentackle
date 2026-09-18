# The CockroachDB Backend — Peculiarities

CockroachDB speaks PostgreSQL's wire protocol and most of its SQL, and applications connect with the PostgreSQL
JDBC driver. [`CockroachDb`](../java/org/tentackle/sql/backends/CockroachDb.java) therefore extends
[`Postgres`](../java/org/tentackle/sql/backends/Postgres.java) and overrides only where CockroachDB deviates. This
document collects those deviations and what they mean for an application.

---

## Selecting the Backend

The URL cannot tell CockroachDB from PostgreSQL, so `CockroachDb.isMatchingUrl()` never matches and a plain
`jdbc:postgresql:` URL keeps selecting `PostgreSQL`. Select the backend by name with the `|CockroachDB` suffix
(see [Backend Properties](../../../../tentackle-session/src/main/docs/backend-properties.md)):

```properties
url=jdbc:postgresql://localhost:26257/myapp|CockroachDB
```

For the [SQL Maven plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md), name
`CockroachDB` in `backendNames` — it is also part of `all`.

> ### ⚠️ Always use the suffix
>
> Without it, the PostgreSQL backend generates SQL that CockroachDB rejects — deferrable constraints, for
> example — and the failure shows up only when the migration is applied.

There is **no version check**: CockroachDB reports a PostgreSQL version to the driver, not its own.

---

## Deviations From PostgreSQL

- **No deferrable constraints.** `isSetConstraintsDeferredSupported()` returns `false`: deferrable unique indexes
  and foreign keys are created as plain ones, and `SET CONSTRAINTS` is never issued.
- **Index names are unique per table, not per schema**, and an index is addressed as `table@index`:
  `DROP INDEX md.customer@customer_udk`, `ALTER INDEX md.customer@customer_old RENAME TO customer_new` and
  `COMMENT ON INDEX md.customer@customer_udk IS ...`.
- **Sequences cannot be commented**, so `sqlCreateSequenceComment()` returns an empty string.
- **The backend id** is CockroachDB's session id (`SHOW session_id`).
- **Reserved schemas**: `crdb_internal` and `pg_extension`, in addition to PostgreSQL's.
- **No native `xml` type**, so `XML` is declared as `TEXT` and `isSqlxmlSupported()` is `false`.
  **`JSON` is not affected**: CockroachDB has a native `JSONB` and inherits PostgreSQL's mapping,
  its `Types.OTHER` handling and its parameter binding unchanged.

Everything else is inherited from PostgreSQL: the type mapping (native `UUID`, `TEXT` for unsized strings),
`LIMIT ? OFFSET ?`, filtered and expression indexes, `IF EXISTS` on drops, and the `ANY`/`ALL` array operators.

---

## Running on CockroachDB

Some properties of the database cannot be hidden by the backend:

- **Serializable isolation.** CockroachDB runs transactions as `SERIALIZABLE` by default and aborts one of two
  conflicting transactions with SQLState `40001` ("restart transaction"). The backend reports it as transient, so
  the `PersistenceException` is marked temporary and the transaction can be retried — see
  [Transactions and Transparent Retry](../../../../tentackle-pdo/src/main/docs/transaction.md). Under contention,
  expect such retries more often than on PostgreSQL.
- **Sequences are coordinated across the cluster**, which makes every `NEXTVAL` comparatively expensive. For a
  sequence under a high insert rate, consider adding a `CACHE` clause to the generated `CREATE SEQUENCE`.
- **Schema changes run online and are not transactional** as in PostgreSQL. Avoid mixing DDL and DML in one
  transaction when applying a migration script. Depending on the version, a column type change that rewrites the
  data must first be enabled with `SET enable_experimental_alter_column_type_general = true`.

---

## Related Documentation

- [Tentackle SQL](sql.md) — the backend abstraction this backend implements.
- [The PostgreSQL Backend](postgres.md) — the backend this one extends and mostly inherits, including the `JSON`/`JSONB` mapping.
- [Backend Properties](../../../../tentackle-session/src/main/docs/backend-properties.md) — the `|<BackendName>`
  URL suffix.
- [Transactions and Transparent Retry](../../../../tentackle-pdo/src/main/docs/transaction.md) — what happens with
  a transient exception.
- [Tentackle SQL Maven Plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) —
  generates the DDL and the migration scripts.
