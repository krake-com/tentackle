# Tentackle SQL — The Database Backend Layer

## Overview and Motivation

The `tentackle-sql` module is Tentackle's **database abstraction layer**. It sits directly above JDBC and below
the persistence layer, isolating the rest of the framework from the dialect, quirks, and capabilities of the
concrete relational database in use.

Its central abstraction is the [`Backend`](../java/org/tentackle/sql/Backend.java) — a single object that knows
*everything* dialect-specific about one kind of database: how to build a `SELECT` with a `LIMIT`/`OFFSET`, what an
empty string looks like, how to create a sequence, whether `UUID` is supported natively, how to render a
`CREATE TABLE` column, how to rename an index, which words are reserved, how to recognize a deadlock, and much
more. Everything that differs between PostgreSQL, CockroachDB, Oracle, DB2, SQL Server, SAP HANA, MySQL/MariaDB,
Firebird, Informix, and H2 is
funneled through this one interface, so the layers above it can stay completely database-agnostic.

The module serves two distinct audiences:

1. **The runtime persistence layer** ([`tentackle-persistence`](../../../../tentackle-persistence/src/main/docs/persistence.md)) uses a
   `Backend` to build and execute the SQL behind every PDO operation — selects, inserts, updates, deletes,
   sequences, write locks, paged queries, and array parameters.
2. **The build-time tooling** — the [SQL Maven plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md)
   and the Wurbelizer wurblets — use the *same* `Backend` and the `DataType` system to generate DDL scripts,
   schema migrations, and type-specific persistence code from the application
   [model](../../../../tentackle-model/src/main/docs/model-definition.md).

Because the build tools and the runtime share the same backend code, the SQL you generate at build time and the
SQL you execute at runtime are guaranteed to agree.

### Design principles

- **One backend instance per database type.** A `Backend` is a stateless singleton (loaded as a service). It must
  not hold per-connection state — the only state it conceptually represents is "the whole of all connections to
  this kind of database". This makes backends trivially shareable across threads and JVMs.
- **No ORM, no query DSL.** This module does not try to be JPA. It produces and consumes plain SQL strings and
  JDBC `PreparedStatement`/`ResultSet` objects. The intelligence lives in *how* those strings are assembled for
  each dialect, not in hiding SQL from the developer.
- **Pluggable via SPI.** Backends and data types are discovered through Tentackle's `ServiceFinder` mechanism, so
  applications can add their own database support or their own column types without modifying the framework.
- **Backend-agnostic upward, dialect-aware downward.** Code above this layer never branches on the database
  product; code inside a `Backend` never needs to know about domain objects or persistence semantics.

---

## Key Concepts

### Backend

[`Backend`](../java/org/tentackle/sql/Backend.java) is the heart of the module. It is a large interface — by
design — because it enumerates every point at which relational databases disagree. Its methods fall into a handful
of groups:

| Group                           | Examples                                                                                                                                                                     | Purpose                                                                                                               |
|---------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| **Identification & connection** | `isMatchingUrl`, `isMatchingName`, `getDriverClassName`, `createConnection`, `validateVersion`                                                                               | Recognize the database from a JDBC URL or name and open connections to it.                                            |
| **Query construction**          | `buildSelectSql`, `setLeadingSelectParameters`, `setTrailingSelectParameters`, `sqlJoin`, `sqlJoinSelects`, `sqlFunction`, `optimizeSql`                                     | Assemble `SELECT`s with dialect-correct paging, write-locking, joins and function calls.                              |
| **DDL generation**              | `sqlCreateTableIntro`, `sqlCreateColumn`, `sqlCreateIndex`, `sqlCreateForeignKey`, `sqlCreateSequence`, `sqlCreateSchema`, `sqlDropTable`                                    | Emit `CREATE`/`DROP` statements for the schema generator.                                                             |
| **Schema migration**            | `getMigrationStrategy`, `sqlRenameColumn`, `sqlAlterColumnType`, `sqlAlterColumnNullConstraint`, `sqlAlterColumnDefault`, `isDefaultEqual`                                   | Compute and emit the `ALTER` statements that morph an existing schema into the model's.                               |
| **Type mapping**                | `sqlTypeToString`, `columnTypeToString`, `columnTypeNullDefaultToString`, `jdbcTypeToSqlType`, `getMaxSize`, `getMaxScale`, `valueToLiteral`                                 | Translate Tentackle [`SqlType`](#sqltype)s to the database's own type declarations and back.                          |
| **Capability flags**            | `isSequenceSupported`, `isSchemaSupported`, `isClobSupported`, `isSqlxmlSupported`, `isJsonSupported`, `isUUIDSupported`, `isFunctionBasedIndexSupported`, `isFilteredIndexSupported`, `isReleaseSavepointSupported` | Let callers ask what the database can do instead of hard-coding product checks.                                       |
| **Naming & reserved words**     | `assertValidName`, `getMaxNameLength`, `getReservedWords`, `isReservedTableName`, `isTemporaryName`                                                                          | Validate identifiers against the dialect's rules.                                                                     |
| **Error classification**        | `isConstraintException`, `isCommunicationLinkException`, `isTransientTransactionException`                                                                                   | Interpret vendor-specific `SQLException`s so the upper layers can decide whether to retry a transaction or reconnect. |
| **Metadata**                    | `getMetaData`, `getModelMetaData`, `getTableMetaData`, `createColumnMetaData`, `createIndexMetaData`                                                                         | Read the live database schema for migration and verification.                                                         |

A long list of `SQL_*` string constants (`SQL_WHERE`, `SQL_SELECT`, `SQL_AND`, `SQL_EQUAL_PAR`, …) is also defined
on the interface. These are the building blocks the persistence layer concatenates into statements; sharing them
avoids scattering string literals throughout the framework and keeps generated and runtime SQL consistent.

> **Stateless contract:** because there is exactly one backend per database type, implementations *must not* cache
> per-connection or per-transaction data. The one mutable exception is `setDropIfExistsEnabled` /
> `isDropIfExistsEnabled`, a global toggle that controls whether generated `DROP` statements include `IF EXISTS`.

### BackendInfo

While a `Backend` describes *a kind of database*, a [`BackendInfo`](../java/org/tentackle/sql/BackendInfo.java)
describes *a concrete configured connection target*: the JDBC URL (or JNDI `DataSource`), username, password and
optional schemas. It is the object you actually use to open a `Connection`.

`BackendInfo` understands several ways to point at a database:

- **A JDBC URL** (`jdbc:...`) — the matching backend is found automatically via `Backend.isMatchingUrl`.
- **A JNDI name** (`jndi:...`) — the `DataSource` is looked up and the backend derived from its metadata.
- **A TRIP / remote URL** — there is no local backend at all (`isRemote()` returns true); the persistence layer
  routes calls to a remote server. This is what lets the very same code run on a directly-connected client and on
  a remoting client without change.

A backend type can be pinned explicitly by appending `|<BackendName>` to the URL. This is the escape hatch for
running an older dialect against a newer server, e.g.:

```
jdbc:oracle:thin:@//localhost:1521/xe|Oracle8
```

`BackendInfo` can be built directly from arguments, from an
[`EncryptedProperties`](../../../../tentackle-common/src/main/docs/common.md) configuration (honouring `Cryptor`-encrypted URLs and
passwords), or copied with a different user/password. It also carries the optional **keep-alive** settings
(`backendTimeout`, `backendKeepAliveEnabled`) used to survive cloud databases that silently drop idle
connections — either by sending a dummy `SELECT` periodically, or by testing the connection just before it is
reused.

Passwords are held in `char[]` and can be wiped from memory with `clearPassword()`.

### SqlType

[`SqlType`](../java/org/tentackle/sql/SqlType.java) is a small, type-safe enum that is Tentackle's curated subset
of `java.sql.Types`: `VARCHAR`, `CHAR`, `CLOB`, `SQLXML`, `JSON`, `BLOB`, `DECIMAL`, `BIT`, `TINYINT`,
`SMALLINT`, `INTEGER`, `BIGINT`, `FLOAT`, `DOUBLE`, `DATE`, `TIME`, `TIMESTAMP`, `UUID` and `JAVA_OBJECT`. Each
value knows its Java default value and how to parse a literal coming back from JDBC metadata.

`SqlType` is the **physical** view — one `SqlType` corresponds to one database column. It is what the backend
turns into an actual type declaration via `columnTypeToString` and friends.

### DataType

[`DataType<T>`](../java/org/tentackle/sql/DataType.java) is the **logical, model-level** view of a type. Where
`SqlType` describes a column, a `DataType` describes a *Java type as used in the model* and maps it to **one or
more** columns. For example [`BMoneyType`](../java/org/tentackle/sql/datatypes/BMoneyType.java) maps a single
`BMoney` value to two columns (amount + scale).

`DataType` is deliberately an interface, not an enum, so that **applications can define their own column types**.
The module ships ~38 predefined implementations under
[`org.tentackle.sql.datatypes`](../java/org/tentackle/sql/datatypes) covering the primitive wrappers and their
primitives, `String`/`Character`, the `java.time` types (`LocalDate`, `LocalDateTime`, `Instant`,
`ZonedDateTime`, `OffsetDateTime`, …), the legacy `java.sql` date/time types, `BigDecimal`, `UUID`, binary/large
types, and the Tentackle money types `BMoney`/`DMoney`.

Each `DataType` is a stateless singleton annotated with `@Service(DataType.class)` and located through the
[`DataTypeFactory`](../java/org/tentackle/sql/DataTypeFactory.java). It is keyed by the plain Java class name plus
an optional *variant* (e.g., the `"Large"` variant distinguishes a `CLOB`-backed `String` from a `VARCHAR` one,
and the `"Sqlxml"` variant bridges a `String` through the JDBC [`SQLXML`](../java/org/tentackle/sql/datatypes/SQLXMLType.java)
interface to a native XML column where the backend supports it — see [`Backend.isSqlxmlSupported()`](../java/org/tentackle/sql/Backend.java)).
The [`"Json"` variant](../java/org/tentackle/sql/datatypes/JsonType.java) does the same for a native JSON column,
but JDBC offers no binding to bridge it with: there is no `Types.JSON` and no `createJSON()`. The backends
therefore disagree on both ends — a `jsonb` parameter is set as `Types.OTHER`, H2 only accepts the UTF-8 bytes
(a plain string would be stored as a JSON *string value*), and a JSON column is reported back as `Types.OTHER`
with only the type name to tell it from a `uuid`. Both ends are hence the backend's business, via
`setJson`/`getJson` and the type-name-aware `jdbcTypeToSqlType` overload.

The interface serves three roles at once, which is why it is so broad:

1. **Model description** — `getColumnCount`, `getSize`, `getScale`, `createColumnNames`, `getSortableColumns`,
   `isNumeric`, `isDateOrTime`, `isMutable`, … describe how the type appears in the schema.
2. **Code generation** — `getColumnGetter`, `valueOfLiteralToCode`, `getDataTypeConstant`, `isPredefined`, … feed
   the wurblets that generate persistence code. *Predefined* types have handwritten persistence methods;
   application-defined types must return `isPredefined() == false` so generic accessor code is generated instead.
3. **Runtime persistence** — `set(...)`, `get(...)`, `getColumnValue`, `getMappedNullValue`, `toLiteral` read and
   write actual values to/from `PreparedStatement`s and `ResultSet`s (mainly for non-predefined types).

> **Where to put custom data types:** because a `DataType` references its Java type, define it in a *separate*
> module that the Java type's own module does not depend on — otherwise every application module would transitively
> depend on `tentackle-sql`, which is undesirable for the PDO modules. The custom type's module must then be
> registered as a `wurbletDependency` for the Wurbelizer plugin and as a plugin dependency of the SQL Maven plugin.

### ScriptRunner

[`ScriptRunner`](../java/org/tentackle/sql/ScriptRunner.java) executes multi-statement SQL scripts (e.g., generated
DDL and migration scripts) against a `Connection`. It splits a script into statements using the backend's
statement separators and comment syntax, then runs each one. It offers:

- control over **escape processing**, **max rows**, **per-statement timeout**, **read-only** mode, and whether to
  wrap the whole script in a **transaction**;
- `cancel()` / `isCanceled()` to abort a long-running script;
- listeners for each statement *submitted* (`addExecutionListener`), each *result* (`addResultListener`) and each
  *result set* (`addResultSetListener`).

A `Backend` creates an appropriate runner via `createScriptRunner(connection)`. The default is
[`DefaultScriptRunner`](../java/org/tentackle/sql/DefaultScriptRunner.java); PostgreSQL supplies a specialized
[`PostgresScriptRunner`](../java/org/tentackle/sql/backends/PostgresScriptRunner.java). Failures surface as
[`ScriptRunnerException`](../java/org/tentackle/sql/ScriptRunnerException.java) (a statement failed) or
[`BackendException`](../java/org/tentackle/sql/BackendException.java) (anything else).

### DynamicDriver

[`DynamicDriver`](../java/org/tentackle/sql/DynamicDriver.java) loads a JDBC driver from an arbitrary URL at
runtime. `DriverManager` refuses to use drivers not loaded by the system classloader, so `DynamicDriver` wraps the
URL-loaded driver in a system-classloader-visible delegate and registers it. The driver is specified as
`<className>:<url>`, e.g.:

```
org.postgresql.Driver:jar:file:/usr/share/java/postgresql.jar!/
```

This is triggered by the `backend.driver` configuration property and lets an application ship without bundling
every possible JDBC driver.

---

## Package Layout

| Package                       | Contents                                                                                                                                                                                                                                |
|-------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `org.tentackle.sql`           | The core API: `Backend`, `BackendInfo`, `BackendFactory`, `DataType`, `DataTypeFactory`, `SqlType`, `SqlNameType`, `JoinType`, `MigrationStrategy`, `ScriptRunner`, `DynamicDriver`, the `Default*` implementations and the exceptions. |
| `org.tentackle.sql.backends`  | The concrete `Backend` implementations and their abstract base classes (see below).                                                                                                                                                     |
| `org.tentackle.sql.datatypes` | The ~36 predefined `DataType` implementations and the abstract bases `AbstractDataType`, `AbstractNumberType`, `AbstractDateTimeType`.                                                                                                  |
| `org.tentackle.sql.metadata`  | Live-schema metadata model (`ModelMetaData`, `TableMetaData`, `ColumnMetaData`, `IndexMetaData`, `ForeignKeyMetaData` and backend-specific subclasses) used by the migrator and schema verifier.                                        |
| `org.tentackle.sql.service`   | The `ModuleHook` for resource-bundle/i18n integration.                                                                                                                                                                                  |

---

## Supported Backends

All backend implementations extend [`AbstractBackend`](../java/org/tentackle/sql/backends/AbstractBackend.java) and
are organized by SQL-standard level so that newer dialects inherit standard behavior and only override their
peculiarities:

```
AbstractBackend
└── AbstractSql92Backend
    ├── H2
    ├── Informix
    ├── Postgres
    │   └── CockroachDb
    ├── MySql
    │   └── MariaDb
    └── AbstractSql2003Backend
        ├── Db2
        ├── Hana
        ├── MsSql
        ├── Oracle8
        │   └── Oracle
        │       └── Oracle21
        └── AbstractSql2008Backend
            └── Firebird
```

| Backend       | Name          | Matches JDBC URL containing                       |
|---------------|---------------|---------------------------------------------------|
| `Postgres`    | `PostgreSQL`  | `:postgresql`                                     |
| `CockroachDb` | `CockroachDB` | never (PostgreSQL driver, selected by name)       |
| `H2`          | `H2`          | `:h2:` (in-memory when `:mem:`)                   |
| `MySql`       | `MySQL`       | `:mysql`                                          |
| `MariaDb`     | `MariaDB`     | `:mariadb`                                        |
| `Oracle`      | `Oracle`      | `:oracle:`                                        |
| `Oracle8`     | `Oracle8`     | `:oracle:` (legacy, selected by name)             |
| `Oracle21`    | `Oracle21`    | never (native JSON opt-in, selected by name)      |
| `Db2`         | `DB2`         | `:db2`                                            |
| `MsSql`       | `MsSQL`       | `:sqlserver`                                      |
| `Hana`        | `HANA`        | `:sap:`                                           |
| `Firebird`    | `Firebird`    | `:firebird` (`jdbc:firebirdsql:`, `jdbc:firebird:`) |
| `Informix`    | `Informix`    | `:informix`                                       |

When several backends could match the same URL (e.g. `Oracle` and the deprecated `Oracle8`), only one matches by
URL and the others are selected explicitly by name via the `|<BackendName>` URL suffix. `CockroachDB` is reached
through the PostgreSQL driver and cannot be told apart by its URL at all, so it never matches one. `Oracle21` is
the same idea used for an opt-in rather than a legacy dialect: it matches the very same URL as `Oracle`, but stores
JSON in a native `JSON` column (see [The Oracle Backend](oracle.md#other-oracle-peculiarities)), so it must be
requested explicitly with `|Oracle21`. Backends marked `isDeprecated()` are excluded from
`BackendFactory.getAllBackends()`.

Several products deviate from the others far enough to warrant their own document:

- [The Oracle Backend](oracle.md) — most notably how filtered indexes are emulated and what that means for the
  query optimizer.
- [The MySQL and MariaDB Backends](mysql.md) — the type mapping (`LONGTEXT`/`LONGBLOB`, decimal scale, fractional
  seconds), the index key limits, migration by full column declaration, and sequences on MariaDB.
- [The SAP HANA Backend](hana.md) — `NVARCHAR` strings, the unsigned `TINYINT`, and column alterations that
  replace the whole declaration.
- [The Firebird Backend](firebird.md) — the explicit row lock, the sort order per index, and tables that cannot
  be renamed.
- [The CockroachDB Backend](cockroachdb.md) — selecting it by name, index names per table, and serializable
  transactions.

The backends refuse to connect to a server older than their minimum version (`validateVersion`): Oracle 12
(21 for `Oracle21`), MySQL 8.0, MariaDB 10.3, SAP HANA 2.0, Firebird 4.0.

**H2 is the backend Tentackle's own tests run on** (see
[tentackle-test-pdo](../../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md)), so two of its
deviations from PostgreSQL are worth knowing when reading a generated script:

- **Paging and locking follow the SQL standard**, not PostgreSQL's order: `buildSelectSql` emits
  `OFFSET ? ROWS FETCH FIRST ? ROWS ONLY FOR UPDATE`. H2 rejects any other order as a syntax error, which is why
  the trailing parameters are bound offset-first.
- **An unsized `DECIMAL` needs a precision.** A bare `DECIMAL` is `DECIMAL(100000,0)` in H2 and would silently
  round away every fraction digit, so `getDefaultSize` supplies 19 — the same remedy the MySQL backend applies
  (see [Decimals](mysql.md#decimals)). As there, only PostgreSQL and Oracle keep the scale of a decimal column
  the model declares no size for.

---

## How It Fits Together

### Locating a backend at runtime

Backends are discovered as services and located through the
[`BackendFactory`](../java/org/tentackle/sql/BackendFactory.java) singleton:

```java
// by JDBC URL — the matching backend is found automatically
Backend backend = BackendFactory.getInstance()
                                .getBackendByUrl("jdbc:postgresql://localhost/mydb");

// by name — e.g., to pin a specific dialect
Backend oracle8 = BackendFactory.getInstance().getBackendByName("Oracle8");

// all non-deprecated backends — used by the SQL Maven plugin to generate DDL for every dialect
Collection<Backend> all = BackendFactory.getInstance().getAllBackends();
```

In practice, application code rarely calls `BackendFactory` directly. It configures a `BackendInfo` (usually from
properties) and obtains a connection:

```java
BackendInfo info = new BackendInfo(backendProperties);   // url, user, password, schemas, driver, timeout...
try (Connection con = info.connect()) {
    Backend backend = info.getBackend();
    String sql = backend.buildSelectSql("FROM customer WHERE name LIKE ?",
                                        false /*writeLock*/, 50 /*limit*/, 0 /*offset*/);
    // ... prepare, set parameters via backend.setLeadingSelectParameters(...), execute
}
```

### Generating and migrating schemas at build time

The [SQL Maven plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) walks
the application [model](../../../../tentackle-model/src/main/docs/model-definition.md), and for each configured
backend uses the `DataType`/`SqlType` mapping plus the backend's `sqlCreate*` methods to emit a complete DDL
script. For an existing database it reads the live schema into a `ModelMetaData`, diffs it against the model, and
asks the backend for a `MigrationStrategy[]` per changed column, emitting the corresponding `ALTER` statements.
The generated scripts are then either applied using a `ScriptRunner` (local development only) or checked into source control
to be applied later by migration tools such as Flyway or Liquibase.

Because the same `Backend` objects power both the build-time generation and the runtime execution, the schema the
tools create always matches the SQL the persistence layer issues.

---

## Extending the Module

### Adding a custom column type

1. Implement [`DataType<T>`](../java/org/tentackle/sql/DataType.java) (usually by extending
   [`AbstractDataType`](../java/org/tentackle/sql/datatypes/AbstractDataType.java)), annotate it with
   `@Service(DataType.class)`, and make `isPredefined()` return `false`.
2. Place it in a dedicated module that does **not** create a dependency from your PDO modules onto
   `tentackle-sql` (see the note in [DataType](#datatype)).
3. Register that module as a `wurbletDependency` of the Wurbelizer plugin and as a dependency of the SQL Maven
   plugin so both build-time and runtime can find the type.

### Adding support for another database

Subclass the closest `AbstractSqlNNNNBackend` matching the database's SQL-standard level, override the methods
where the dialect deviates (type strings, paging syntax, sequence support, reserved words, error classification,
…), and annotate the class with `@Service(Backend.class)`. The new backend is then picked up automatically by the
`BackendFactory`.

---

## Module Dependencies

`tentackle-sql` deliberately sits low in the stack:

- It **requires transitive** [`tentackle-common`](../../../../tentackle-common/src/main/docs/services.md) (service
  discovery, `EncryptedProperties`/`Cryptor`, `StringHelper`, money types, …) and `java.naming` (for JNDI
  `DataSource` lookups).
- It bundles the PostgreSQL JDBC driver as its reference/test database; other drivers are loaded by the
  application (optionally via [`DynamicDriver`](#dynamicdriver)).
- It **opens** `org.tentackle.sql` to `org.tentackle.core` for TRIP serialization, since `BackendInfo` and related
  types may travel between JVMs.
- It knows nothing about the domain or persistence layers — those depend on *it*, never the reverse.

## Related Documentation

- [The Oracle Backend](oracle.md) — the peculiarities of Oracle: emulated filtered indexes, virtual index columns, empty strings and more.
- [The MySQL and MariaDB Backends](mysql.md) — the peculiarities of MySQL and MariaDB: type mapping, index key limits, migration by full column declaration, sequences.
- [The SAP HANA Backend](hana.md) — the peculiarities of SAP HANA: type mapping, migration by full column declaration, vendor error codes.
- [The Firebird Backend](firebird.md) — the peculiarities of Firebird: row locks, index sort order, migration limits.
- [The CockroachDB Backend](cockroachdb.md) — the deviations of CockroachDB from PostgreSQL and how to select it.
- [Table Names](../../../../tentackle-session/src/main/docs/table-names.md) — schema mapping and table prefixes for the backends whose `isSchemaSupported()` returns `false`.
- [Model Definition](../../../../tentackle-model/src/main/docs/model-definition.md) — where attributes and their data types are declared.
- [Tentackle SQL Maven Plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) — generates DDL and migration scripts from the model using this module.
- [Services / ServiceFinder](../../../../tentackle-common/src/main/docs/services.md) — the SPI mechanism that discovers backends and data types.
- [Backend Properties](../../../../tentackle-session/src/main/docs/backend-properties.md) — the reference of all property keys `BackendInfo` and the session layer understand.
