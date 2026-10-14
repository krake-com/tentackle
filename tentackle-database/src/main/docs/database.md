# Tentackle Database — The Low-Level Persistence Engine

## Overview and Motivation

The `tentackle-database` module (Java package `org.tentackle.dbms`) is Tentackle's **low-level persistence
engine** — the runtime that actually moves data between Java objects and a relational database. It sits between
the dialect-aware [`tentackle-sql`](../../../../tentackle-sql/src/main/docs/sql.md) backend below it and the
PDO-aware [`tentackle-persistence`](../../../../tentackle-persistence/src/main/docs/persistence.md) layer above
it, and it provides the concrete implementation of the [`Session`](../../../../tentackle-session/src/main/docs/session.md)
abstraction.

Its two central classes are:

- [`Db`](../java/org/tentackle/dbms/Db.java) — *a persistence session*: the implementation of `Session` that owns
  transactions, connections, prepared statements, id-sources, and statistics.
- [`AbstractDbObject`](../java/org/tentackle/dbms/AbstractDbObject.java) — *a persistent low-level database
  object*: the base class every persistence implementation extends, providing generic insert/update/delete/select
  machinery driven by entity-specific code that the Wurbelizer generates from the model.

The defining feature of this layer is **2-tier / n-tier transparency**. A `Db` session is an abstraction over a
connection to a "server", where *server* need not be a database server at all:

- A **local** session talks to a database backend through a [`ConnectionManager`](../java/org/tentackle/dbms/ConnectionManager.java)
  that owns the physical JDBC connection. This is what client apps in 2-tier mode and application servers use.
- A **remote** session is connected to a Tentackle application server over
  [TRIP](../../../../tentackle-core/src/main/docs/trip.md). This is what n-tier (n ≥ 3) clients use.

Because both look identical to the code above, a Tentackle application switches between 2-tier and n-tier
deployment **by changing a config file, not the source code**.

### Design principles

- **Session over connection, not session = connection.** A `Db` does not own a JDBC connection directly; it asks a
  `ConnectionManager` to *attach* one for the duration of a task and *detach* it afterward. This decoupling is
  what enables connection multiplexing in servers.
- **Generated specialization, generic engine.** `AbstractDbObject`/`AbstractDbOperation` hold all the generic
  persistence logic; the per-entity SQL and column mapping are generated (by wurblets) into the application's
  subclasses. The OO "template method" approach keeps the framework code entity-agnostic.
- **Location transparency via TRIP delegates.** Every persistence capability has a remote-delegate twin in
  `org.tentackle.dbms.trip`, so the same calls run locally or against a remote server.
- **SQLExceptions never leak.** Statement/result wrappers and the session utilities translate vendor
  `SQLException`s into Tentackle's [`PersistenceException`](../../../../tentackle-session/src/main/docs/session.md)
  hierarchy, classified via the `Backend`, so upper layers never see raw JDBC errors.
- **Replaceable singletons via SPI.** Utilities, factories, and the modification tracker are `@Service`-registered
  so the PDO layer above can swap in PDO-aware versions transparently.

---

## Key Concepts

### Db — the session

[`Db`](../java/org/tentackle/dbms/Db.java) `implements Session` (from
[`tentackle-session`](../../../../tentackle-session/src/main/docs/session.md)) and is the workhorse of the module.
Each thread must use its own `Db`. Its responsibilities include:

| Group | Examples | Purpose |
|-------|----------|---------|
| **Connection brokering** | `getConnectionManager`, attach/detach (internal), `isRemote` | Acquire/release a managed connection for a unit of work. |
| **Transactions** | `begin`, `commit`, `rollback`, `createTransaction`, savepoints | Run (nestable) transactions; the voucher protocol from `Session` decides who really commits. |
| **Statements** | `getPreparedStatement`, `createStatement` | Obtain cached, wrapped statements bound to the session's connection. |
| **Id sources** | the per-session `IdSource` (`ObjectId` / `ObjectSequenceId`) | Allocate unique object IDs. |
| **Batching** | `executeBatch`, batch configuration | Collect prepared statements into JDBC batches for throughput. |
| **Modification tracking** | the session's `ModificationTracker` integration | Advance table serials on writes for caches/notifications. |
| **Diagnostics** | statement history, statement/transaction statistics, the statement tracer | Observe and profile database activity. |
| **Keep-alive / lifecycle** | `isAlive`, reconnection policy | Survive idle timeouts and dropped connections. |

A `Db` can be **cloned** and **pooled**, and exposes whether it is local or remote so the rest of the framework
can adapt where it genuinely matters (rarely).

### AbstractDbObject and AbstractDbOperation

[`AbstractDbObject<P>`](../java/org/tentackle/dbms/AbstractDbObject.java) is the base of all persistent objects.
It is associated with a `Session` (local or remote) and provides the generic CRUD engine; entity specifics
(table/column mapping, finders, relations) are generated by wurblets into subclasses according to the
[model](../../../../tentackle-model/src/main/docs/model-definition.md). It carries the bookkeeping every
persistent row needs — object id, serial (optimistic locking), table serial (cache expiry) — and is
`@TripSerializable` so instances can travel between JVMs.

[`AbstractDbOperation<P>`](../java/org/tentackle/dbms/AbstractDbOperation.java) is the counterpart for **operations**
— units of work that are not a single row but a (often complex, transactional) procedure bound to a `Db` session,
and are likewise remoting capable.

Per-class static metadata lives in [`DbObjectClassVariables`](../java/org/tentackle/dbms/DbObjectClassVariables.java)
and [`DbOperationClassVariables`](../java/org/tentackle/dbms/DbOperationClassVariables.java), created via the
[`DbClassVariablesFactory`](../java/org/tentackle/dbms/DbClassVariablesFactory.java) (which the PDO layer replaces
with a PDO-aware factory).

### Connection management

JDBC connections are **never used directly**. A session borrows one from a `ConnectionManager` via *attach*, uses
it for a transaction/one-shot, then *detaches* it — letting the manager broker N sessions over M connections.

- [`ConnectionManager`](../java/org/tentackle/dbms/ConnectionManager.java) — the broker interface
  (`login`/`logout`, `attach`/`detach`/`forceDetach`, `getMaxConnections`, …).
- [`DefaultConnectionManager`](../java/org/tentackle/dbms/DefaultConnectionManager.java) — strict 1:1 mapping
  (each session keeps its own physical connection).
- [`MpxConnectionManager`](../java/org/tentackle/dbms/MpxConnectionManager.java) — **multiplexing** manager that
  maps N sessions onto M < N connections, transparently to the application; the typical choice for application
  servers with many clients. (Note: this is *not* session pooling — multiplexing is invisible to the app.)
- `XaConnectionManager` and `XaMpxConnectionManager` (module `tentackle-jta`) — the default and the
  multiplexing manager with connections that can be enlisted in
  [global transactions](../../../../tentackle-jta/src/main/docs/jta.md).
- [`ManagedConnection`](../java/org/tentackle/dbms/ManagedConnection.java) — a JDBC connection wrapped with a
  prepared-statement cache and `SQLException` → `PersistenceException` translation; also exposes the backend's
  database version via `getBackendVersionInfo()`.
- [`ManagedConnectionMonitor`](../java/org/tentackle/dbms/ManagedConnectionMonitor.java) — watches managed
  connections; [`DefaultReconnectionPolicy`](../java/org/tentackle/dbms/DefaultReconnectionPolicy.java) decides how
  to reconnect after a failure.

Connection managers are **not** used by remote sessions (those go over TRIP instead). An application implementing
[`ConnectionManagerProvider`](../java/org/tentackle/dbms/ConnectionManagerProvider.java) (such as a server application
with a multiplexing manager) supplies the default connection manager for new sessions; otherwise, a
`DefaultConnectionManager` is used.

### Session creation and pooling

- [`DbFactory`](../java/org/tentackle/dbms/DbFactory.java) (`@Service(SessionFactory.class)`) — creates `Db`
  sessions; it is the local implementation of the session factory contract.
- [`DbPool`](../java/org/tentackle/dbms/DbPool.java) — a `SessionPool` of `Db`s for one `SessionInfo`. With the
  default connection manager each pooled `Db` is one physical connection; with the multiplexing manager pooled
  `Db`s are virtual connections attached only during operations — the preferred server configuration.
- [`MultiUserDbPool`](../java/org/tentackle/dbms/MultiUserDbPool.java) — a pool of `DbPool`s, one per
  `SessionInfo`, used by cascaded remote servers.
- [`DbPoolFactory`](../java/org/tentackle/dbms/DbPoolFactory.java) (`@Service(SessionPoolFactory.class)`) creates
  both kinds of pools; each pooled session lives in a [`DbPoolSlot`](../java/org/tentackle/dbms/DbPoolSlot.java),
  which removes closed sessions from the pool and keeps idle remote sessions alive.
- [`RemoteSessionFactory`](../java/org/tentackle/dbms/RemoteSessionFactory.java), implemented by
  [`DbRemoteSessionFactory`](../java/org/tentackle/dbms/DbRemoteSessionFactory.java)
  (`@Service(RemoteSessionFactory.class)`), and [`RemoteSessionAdapter`](../java/org/tentackle/dbms/RemoteSessionAdapter.java)
  provide the remote-session side.

### Backend identity

An application may be connected to **more than one database at the same time** — a replication or migration tool,
a server that reads from one database and writes to another, a test that compares two of them. Some state of
this layer is shared by all sessions of the JVM, but belongs to exactly one *physical* database. That state needs
a key telling the databases apart, and comparing a `BackendInfo` on every access would be too slow for it.

That key is the **backend identity**: an `int` returned by `Db#getBackendIdentity()` that denotes the physical
backend the session is connected to. It is resolved once, when the `Db` is created, by
[`DbUtilities#getBackendIdentity(BackendInfo)`](../java/org/tentackle/dbms/DbUtilities.java):

- By default, every distinct combination of **backend type and URL** gets the next free number, starting at 1 in
  the order of first use.
- Remote sessions have the identity `0`: there is no physical backend on their side of the wire.
- The identity is part of the session name (`Db7i2c41`: instance 7, backend identity 2, connection 41), so it
  shows up in logs and exceptions.

The identity is only meaningful **within the running JVM**. The numbers depend on the order in which the backends
are opened, so they must not be persisted or sent to another JVM.

Two things are keyed by it:

- [`ObjectSequenceId`](../java/org/tentackle/dbms/ObjectSequenceId.java) keeps the id range it took from the
  sequence per backend identity (see [Id sources](#id-sources)).
- [`ModificationLogFactory`](../java/org/tentackle/dbms/ModificationLogFactory.java) keeps one
  `ModificationLogSerializer` per backend identity (see [Modification logging](#modification-logging)).

#### Why it matters

The identity can be wrong in two directions, and they are not equally bad:

- **Two databases, one identity.** `ObjectSequenceId` would hand out ids in database B from a range it reserved
  in the sequence of database A. Sooner or later B's own sequence reaches that range and the ids collide.
  This must never happen.
- **One database, two identities.** The id ranges are merely kept twice, which wastes a few ids but is safe. The
  modification logs, however, break: a serializer such as `TripModificationLogSerializer` spans its dictionary
  over many modlogs of *one* backend. If the sessions of the second identity have no serializer, their modlogs
  carry no object data; if they have their own, two independent dictionaries are interleaved in the same `modlog`
  table and the receiver cannot decode them.

#### Setting it manually

The default cannot know that two different URLs lead to the same database. That is the case whenever the
application reaches one database in more than one way, for example:

- with different URL options (a read-only connection next to the regular one, another `ApplicationName`,
  different timeouts or SSL settings),
- via a host name in one configuration and an IP address or alias in the other,
- directly and through a connection pooler or proxy,
- via a JNDI `DataSource` and a JDBC URL, or two JNDI names.

In these cases assign the same identity to all of them, either with the `identity`
[backend property](../../../../tentackle-session/src/main/docs/backend-properties.md) or programmatically via
`BackendInfo#setBackendIdentity(int)`:

```properties
# backend.properties
url=jdbc:postgresql://db.example.com/erp
identity=1001

# readonly.properties: the same database
url=jdbc:postgresql://db.example.com/erp?readOnly=true
identity=1001
```

Rules:

- Use **high or negative** numbers. The generated identities count up from 1, so a low number may collide with
  one that was assigned automatically to a *different* database. `0` means "not set".
- Set it **before the first session** of that URL is created. The identity is cached per backend type and URL,
  so the first session decides and later changes of the `BackendInfo` are ignored.
- Give the same identity only to URLs that really are the same database. If in doubt, leave it unset: two
  identities for one database are the harmless mistake as long as no modlog serializer is involved.
- An explicit identity can *join* URLs, but it cannot *split* one: sessions with the same backend type and URL
  always share an identity, even if they log in as different database users. Where the user decides in which
  schema the unqualified sequence name is resolved (as in Oracle), such sessions need URLs that differ.

#### Why it cannot be detected automatically

It is tempting to let the framework find out by itself whether two connections end up in the same database. There
is no way to do that reliably, and a wrong "yes, the same" is exactly the dangerous direction described above:

- **A value stored in the database is copied along with the database.** A marker row with a UUID, the current
  value of the object sequence, the highest object id, the contents of some table, the catalog's object ids, even
  the vendor's own system identifier: all of it is data, and all of it survives a backup that is restored
  somewhere else, a file-system snapshot, a cloned cloud instance or a promoted standby. The original and its copy
  then report the same value, although they are two physical databases with independent sequences. This is not an
  exotic case: applications that connect to two databases at once are typically the very ones that deal with a
  database and its copy — production and the restored test system, the old and the new side of a migration,
  the source and the target of a replication.
- **What the server reports about itself depends on the path.** Host, address and port differ behind proxies,
  poolers, load balancers, NAT and after a failover, they are backend-specific, and one server hosts many
  databases anyway.
- **A probe lock is not safe either.** Taking a lock through one connection and testing it through the other
  only shows that *somebody* holds that lock right now. A second JVM running the same probe with the same
  well-known lock key against the copy yields a false positive. A read-only connection or a standby cannot take
  the lock at all, and a pooler in transaction mode does not keep session-level locks. The probe needs write
  privileges and backend-specific SQL, has to be repeated against every backend already known, and its lock
  waits and timeouts would make opening a session slow and flaky.
- **There may be no connection to ask.** The identity is needed when the `Db` is created. With a multiplexing
  connection manager, a connection is attached only later, and not necessarily the same one each time.

Which URLs denote the same database is a fact of the **deployment topology**, known to whoever configures the
application and to nobody else. Tentackle therefore derives the identity from the only thing that is
deterministic — the configuration — and lets the configuration override it.

### Id sources

[`IdSource`](../java/org/tentackle/dbms/IdSource.java) supplies unique object IDs. Two implementations ship:

- [`ObjectId`](../java/org/tentackle/dbms/ObjectId.java) — used when the backend has **no sequences**; updates a
  counter table, optimized to write only once at the end of transaction. The first id of a transaction locks the
  counter row until the transaction ends, so transactions allocating ids from the same table are serialized by
  the database. The counting state belongs to the transaction, so one instance can be shared by all sessions.
  A rollback to a savepoint may undo the increment and release the row lock; the next id
  or the final write then increments the counter again and continues after whatever other sessions allocated
  in the meantime.
- [`ObjectSequenceId`](../java/org/tentackle/dbms/ObjectSequenceId.java) — uses a **database sequence**, multiplied
  by a configurable factor with an in-range counter to cut round-trips. There is one instance per sequence name,
  shared by all sessions. The range taken from the sequence is kept per database, i.e., per
  [backend identity](#backend-identity), so applications connected to several databases never mix up their
  ranges.

[`AbstractIdSource`](../java/org/tentackle/dbms/AbstractIdSource.java) is the shared base; selection/configuration
goes through [`IdSourceConfigurator`](../java/org/tentackle/dbms/IdSourceConfigurator.java) /
[`DefaultIdSourceConfigurator`](../java/org/tentackle/dbms/DefaultIdSourceConfigurator.java)
(`@Service(IdSourceConfigurator.class)`). Failures are reported as
[`IdSourceException`](../java/org/tentackle/dbms/IdSourceException.java); its subclass
[`IdSourceEmptyException`](../java/org/tentackle/dbms/IdSourceEmptyException.java) is provided for
application-specific id sources with a limited range.

The default id source is chosen from the backend: `sequence:object_sequence_id` if it supports
sequences, `table:object_id` otherwise. The `idsource` [backend property](../../../../tentackle-session/src/main/docs/backend-properties.md)
selects another one, in the syntax of the `DefaultIdSourceConfigurator`:

| Value                                          | Id source                                                                                                  |
|------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `table:<name>`                                 | An `ObjectId` counting in table `<name>`.                                                                  |
| `sequence:<name>`                              | An `ObjectSequenceId` on sequence `<name>`, with the default multipliers `100` and `1`.                    |
| `sequence:<name>:<sequenceMult>:<globalMult>`  | The same with explicit multipliers. The id is `(sequenceValue * sequenceMult + counter) * globalMult`, where the counter runs from `0` to `sequenceMult - 1` before the sequence is read again. A `globalMult` greater than 1 leaves room for application-specific offsets. |

Different entities may use different id sources: if a resource named after the persistent class
(`com/example/persist/InvoicePersistenceImpl.properties`) exists and holds an `idsource` line, it
overrides the session's id source for that class. The class-specific id source is configured once per backend.
Otherwise, the class uses the default id source of the session that requests the id, so sessions on
different backends or with a different `idsource` property each get the id source that fits. All users of the same sequence must configure the
same multipliers, otherwise a `PersistenceException` is thrown.

### Statements, queries and result sets

- [`StatementWrapper`](../java/org/tentackle/dbms/StatementWrapper.java) /
  [`PreparedStatementWrapper`](../java/org/tentackle/dbms/PreparedStatementWrapper.java) — wrap JDBC statements,
  catch/translate `SQLException`s, and guard against accidental reuse.
- [`ResultSetWrapper`](../java/org/tentackle/dbms/ResultSetWrapper.java) — an `AutoCloseable` wrapper over a
  `ResultSet`; [`ResultSetSection`](../java/org/tentackle/dbms/ResultSetSection.java) /
  [`ResultSetSkipBlock`](../java/org/tentackle/dbms/ResultSetSkipBlock.java) support joins (due to inheritance or for load joins).
- [`Query`](../java/org/tentackle/dbms/Query.java) — builds the SQL string + parameter set for a one-off query
  (e.g., user-entered), adding the backend-specific `SELECT`/`LIMIT`/`OFFSET` automatically.
  All predefined [datatypes](../../../../tentackle-sql/src/main/docs/sql.md) are accepted as parameters, as well as
  application-specific types with a registered `DataType`. Notice that some datatypes span more than one column
  (`BMoney` or `ZonedDateTime`, for example) and thus require a matching number of question marks.
- The [`org.tentackle.dbms.dsl`](../java/org/tentackle/dbms/dsl/DSL.java) package builds such a `Query` from
  typed expressions instead of hand-written SQL strings, and counts the columns of multi-column datatypes for
  you — see [The Query DSL](./query-dsl.md).
- [`DbBatch`](../java/org/tentackle/dbms/DbBatch.java) / [`DbBatchStatement`](../java/org/tentackle/dbms/DbBatchStatement.java)
  — JDBC batching of standard prepared statements, grouped per root-entity type to preserve referential
  integrity, flushed at savepoints/commit. Prepared statements are cached per
  [`StatementKey`](../java/org/tentackle/dbms/StatementKey.java), i.e. the global
  [`StatementId`](../java/org/tentackle/dbms/StatementId.java) plus the using class. Enabled via the `batchsize` backend property; **off by default** and
  to be used with care because it complicates error diagnosis. See [SQL Statement Batching](./batching.md).

### Transactions

[`AbstractTransaction`](../java/org/tentackle/dbms/AbstractTransaction.java) is the serializable base;
[`DbTransaction`](../java/org/tentackle/dbms/DbTransaction.java) holds local transaction state and
[`RemoteTransaction`](../java/org/tentackle/dbms/RemoteTransaction.java) the remote variant.
[`DbTransactionFactory`](../java/org/tentackle/dbms/DbTransactionFactory.java) creates them and
[`DbTransactionHandle`](../java/org/tentackle/dbms/DbTransactionHandle.java) is the caller's handle.
[`TransactionStatistics`](../java/org/tentackle/dbms/TransactionStatistics.java) collects the metrics per
transaction name in [`TransactionStatisticsResult`](../java/org/tentackle/dbms/TransactionStatisticsResult.java)s:
duration, number of statements and rollbacks.

#### Global transactions

A local session can take part in global (distributed) transactions, configured by the `jta`
[backend property](../../../../tentackle-session/src/main/docs/backend-properties.md) or
`Db.setGlobalTransactionMode(…)`. The [`GlobalTransactionMode`](../java/org/tentackle/dbms/GlobalTransactionMode.java)
is `NONE` (default), `JOIN` (join a global transaction of the current thread, if any) or `REQUIRED` (the outermost
`begin()` starts a global transaction if none is running). The session talks to the transaction manager only through
the [`GlobalTransactionSupport`](../java/org/tentackle/dbms/GlobalTransactionSupport.java) SPI and completes its
part via a [`GlobalTransactionListener`](../java/org/tentackle/dbms/GlobalTransactionListener.java); the
implementation, together with the XA connection managers, is provided by the optional module `tentackle-jta`.
See [Global Transactions — JTA and XA](../../../../tentackle-jta/src/main/docs/jta.md).

#### Idle transaction supervisor

A transaction holds its connection — and the row locks it acquired — until it is committed or rolled
back. A transaction that is never finished, for example because the thread running it blocks on
something outside the database, would hold them forever. The `DbTransactionFactory` therefore starts
a daemon thread, the *Transaction Supervisor*, as soon as the first transaction of a local session is
created. Remote sessions never start it.

Each time a session performs I/O, its transaction is marked alive. The supervisor wakes up every
`txIdleInterval` milliseconds and clears the mark. A transaction that has not been alive for more than
`txIdleTimeout` consecutive intervals is considered stuck: the supervisor logs a warning with the
connection's diagnostic info (including its statement history), then rolls back and closes the JDBC
connection at the lowest level — in a separate thread, so a hanging driver cannot block the
supervisor. The thread stuck in the transaction gets an exception on its next database access.
The supervisor also logs a transaction that is no longer referenced but was never finished (as
`SEVERE`). When it is asked to stop — `terminateHelperThreads()` at application shutdown — it keeps
supervising the transactions still running and terminates only after they have finished or timed
out. The next transaction starts it again.

The defaults are an interval of `1000` ms and a timeout of `60` intervals, i.e. one minute.
Application servers configure both from the `txIdleInterval` and `txIdleTimeout`
[backend properties](../../../../tentackle-session/src/main/docs/backend-properties.md#server-keys);
other applications use `DbTransactionFactory.getInstance().setTxIdleInterval(…)` /
`setTxIdleTimeout(…)`. A timeout of `0` or less turns the idle check off. Raise the timeout rather
than turning it off if long-running batch transactions legitimately stay idle for a while, e.g. while
waiting for an external system.

### Modification tracking and table serials

This layer implements the change-tracking contracts from `tentackle-session`:

- [`DbModificationTracker`](../java/org/tentackle/dbms/DbModificationTracker.java) (`@Service(ModificationTracker.class)`)
  — the concrete tracker; advances serials when data changes and notifies listeners (e.g., caches).
- [`DbModification`](../java/org/tentackle/dbms/DbModification.java) (table `modification`) and
  [`ModificationTally`](../java/org/tentackle/dbms/ModificationTally.java) — the persisted per-table modification
  serial and its in-memory counter (local sessions only).
- [`TableSerialHistory`](../java/org/tentackle/dbms/TableSerialHistory.java) and
  [`TableSerialExpirationBacklog`](../java/org/tentackle/dbms/TableSerialExpirationBacklog.java) — track committed
  inserts/updates/deletes in memory so caches can be expired *precisely*, filling the gaps left by deletions and
  rolled-back changes and avoiding needless cache invalidation. Maintained only by the server connected to the
  database; intermediate servers and clients fetch their expiration sets remotely.
- [`DbModificationType`](../java/org/tentackle/dbms/DbModificationType.java) /
  [`ModificationType`](../java/org/tentackle/dbms/ModificationType.java) / [`ModificationTypeFactory`](../java/org/tentackle/dbms/ModificationTypeFactory.java)
  — enumerate INSERT/UPDATE/DELETE plus BEGIN/COMMIT for transaction logging and RESET for the serializer
  (see [Modification logging](#modification-logging)); applications may add their own
  types via a replaced `ModificationTypeFactory`.

### Modification logging

[`ModificationLog`](../java/org/tentackle/dbms/ModificationLog.java) (table `modlog`) records object modifications
for asynchronous database coupling, higher-level replication, audit, etc. Logs are produced through
[`ModificationLogFactory`](../java/org/tentackle/dbms/ModificationLogFactory.java) /
[`DefaultModificationLogFactory`](../java/org/tentackle/dbms/DefaultModificationLogFactory.java).

Optionally, the state of an inserted or updated object is stored in the modlog's `objdata` column as well, so
the receiver does not have to load the object from the database. Serialization is enabled per physical backend by
registering a [`ModificationLogSerializer`](../java/org/tentackle/dbms/ModificationLogSerializer.java) via
`ModificationLogFactory.setSerializer(backendIdentity, serializer)`, where `backendIdentity` is the session's
[backend identity](#backend-identity). The PDO-aware implementation is
[`TripModificationLogSerializer`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/trip/TripModificationLogSerializer.java)
in `tentackle-persistence`.

> **Note:** asynchronous coupling itself is out of scope. There are too many different scenarios — topology,
> transport, ordering, conflict resolution, error handling and recovery, to name a few — to cover them with a
> single generic solution. Tentackle therefore only provides the fundamental building blocks: the modlogs, their
> optional object data and the serializer SPI. Reading the modlogs, shipping them and applying them on the
> receiving side must be implemented at the application level.

### Persistence visitors

[`PersistenceVisitor`](../java/org/tentackle/dbms/PersistenceVisitor.java) is a reflective hook registered on a
`Db` (valid for one transaction) that is consulted before each INSERT/UPDATE/DELETE to decide whether the
operation is allowed or should be **silently skipped** — e.g.
[`IgnoreDuplicatesPersistenceVisitor`](../java/org/tentackle/dbms/IgnoreDuplicatesPersistenceVisitor.java).

### Diagnostics and statistics

- [`StatementTracer`](../java/org/tentackle/dbms/StatementTracer.java) — on-demand SQL tracing (off by default for
  performance), with [`StatementTraceException`](../java/org/tentackle/dbms/StatementTraceException.java).
- [`StatementStatistics`](../java/org/tentackle/dbms/StatementStatistics.java) /
  [`StatementStatisticsResult`](../java/org/tentackle/dbms/StatementStatisticsResult.java) and
  [`StatementHistory`](../java/org/tentackle/dbms/StatementHistory.java) — collect and report statement usage.
- [`DbDiagnosticUtilities`](../java/org/tentackle/dbms/DbDiagnosticUtilities.java)
  (`@Service(DiagnosticUtilities.class)`) — adds persistence info to thread stack dumps.

The diagnostics are switched on by static setters, typically at application startup. The
`statistics` [application property](../../../../tentackle-pdo/src/main/docs/application.md#well-known-properties)
enables a sensible subset on servers: statement, TRIP-invocation and transaction statistics, a
statistics log every 30 seconds, and logging nested selects at `INFO`.

| Setter                                                          | Default  | Effect                                                                                                             |
|-----------------------------------------------------------------|----------|--------------------------------------------------------------------------------------------------------------------|
| `ManagedConnection.setCollectingStatistics(boolean)`            | off      | Count the executions and durations of each SQL statement.                                                          |
| `ManagedConnection.setLogStatisticsMillis(long)`                | `0`      | Log and clear the statement statistics periodically.                                                               |
| `StatementStatistics.setWithParameters(boolean)`                | off      | Separate the statistics per parameter values, not only per statement.                                              |
| `ManagedConnection.setLogLevelForParallelOpenResultSets(Level)` | `null`   | Log *nested selects*: a select executed while a result set of the same connection is still open — typically a lazy relation loaded inside a loop over a result set. |
| `ManagedConnection.setTraceParallelOpenResultSets(boolean)`     | off      | Also add such statements to the `StatementTracer`.                                                                 |
| `ManagedConnection.setMaxStatementsPerTransaction(int)`         | `0`      | Fail a transaction that executes more statements than this — a guard against runaway loops exhausting memory or the backend. |
| `ManagedConnection.setLogMinAttachMillis(long)`                 | `0`      | Log sessions that keep a connection attached longer than this; `-1` logs every detach.                             |
| `ManagedConnection.setMdcFilter(Pattern)`                       | `null`   | Only collect statements for logging if the thread's mapped diagnostic context matches.                            |
| `ManagedConnection.setCheckingStatementResultSetParameters(boolean)` | off | Verify that a reused prepared statement has the requested result set type and concurrency.                       |
| `StatementHistory.setLogMinDurationMillis(long)`                | `0`      | Log statements running longer than this as a warning.                                                              |
| `DbTransactionFactory.setCollectingStatistics(boolean)`         | off      | Collect transaction statistics (count, duration) per transaction name.                                             |
| `RemoteDbDelegateInvocationHandler.setCollectingStatistics(boolean)` | off | Count and measure TRIP invocations per method on the server.                                                     |
| `RemoteDbDelegateInvocationHandler.setLoggingInvocations(boolean)` / `setLoggingInvocationDetails(boolean)` | off | Log each TRIP invocation, optionally with arguments and return value.         |
| `RemoteDbDelegateInvocationHandler.setLogMinDurationMillis(long)` / `setLogMinReturnedCollectionSize(int)` | `0` | Log only invocations that took at least this long, or returned at least this many elements. |
| `RemoteDbDelegateInvocationHandler.setMdcFilter(Pattern)`       | `null`   | Only log TRIP invocations for matching mapped diagnostic contexts.                                                 |

When a connection's diagnostic info is logged — for example by the idle transaction supervisor — it
includes the statement history. Code stuck in a loop can execute the same statements many thousand
times, so histories of 256 or more entries are compressed by the `StatementHistoryCompressor`: a block of
up to 64 statements that repeats immediately is printed once, followed by the number of repetitions.

### Utilities (SPI replacements)

- [`DbUtilities`](../java/org/tentackle/dbms/DbUtilities.java) (`@Service(DbUtilities.class)`) — persistence
  helpers; the clean seam between the low- and high-level layers (the PDO layer replaces it with
  `PersistenceUtilities`).
- [`DbSessionUtilities`](../java/org/tentackle/dbms/DbSessionUtilities.java) (`@Service(SessionUtilities.class)`) —
  extends the session utilities by the handling of `SQLException`s wrapped in a `PersistenceException`:
  driver-specific exceptions are replaced by plain `java.sql.SQLException`s that remote clients can
  deserialize, transient errors flag the exception as temporary, and the messages carry SQL state and error code.

---

## Database-backed Preferences

The `org.tentackle.dbms.prefs` package implements the standard `java.util.prefs.Preferences` API with the
**database as the backing store**, so application preferences are shared across all JVMs rather than living in a
local registry/file:

- [`DbPreferences`](../java/org/tentackle/dbms/prefs/DbPreferences.java) — the `Preferences` implementation;
  nodes persist via [`DbPreferencesNode`](../java/org/tentackle/dbms/prefs/DbPreferencesNode.java) and key/value
  pairs via [`DbPreferencesKey`](../java/org/tentackle/dbms/prefs/DbPreferencesKey.java); changes are flushed
  through [`DbPreferencesOperation`](../java/org/tentackle/dbms/prefs/DbPreferencesOperation.java) and node/key
  events propagate to *all* JVMs.
- [`DbPreferencesFactory`](../java/org/tentackle/dbms/prefs/DbPreferencesFactory.java)
  (`@Service(PersistedPreferencesFactory.class)`) — a drop-in replacement for the JRE preferences factory,
  normally reached via `PersistedPreferencesFactory.getInstance()`.
- `prefs/trip` — the remote delegate so preferences work over a remote connection.

---

## Remoting (TRIP)

The `org.tentackle.dbms.trip` package makes the whole layer **location transparent**. The connection/handshake
chain is:

- [`TripServer`](../java/org/tentackle/dbms/trip/TripServer.java) — the generic application server; parses the
  `service=<uri>` backend properties and creates the TRIP transports that accept client connections.
- [`RemoteDbConnection`](../java/org/tentackle/dbms/trip/RemoteDbConnection.java) — the first remote object handed
  to a connecting client; handles **login** to a session.
- [`RemoteDbSession`](../java/org/tentackle/dbms/trip/RemoteDbSession.java) — the server-side session that creates
  all other **delegates** for the client; cleaned up by
  [`RemoteDbSessionCleanupThread`](../java/org/tentackle/dbms/trip/RemoteDbSessionCleanupThread.java).

Each capability is exposed as a *remote delegate* (interface + `…Impl` executor):

| Delegate / impl | Mirrors |
|------------------|---------|
| [`DbRemoteDelegate`](../java/org/tentackle/dbms/trip/DbRemoteDelegate.java) / `…Impl` | `Db` (the session itself) |
| [`AbstractDbObjectRemoteDelegate`](../java/org/tentackle/dbms/trip/AbstractDbObjectRemoteDelegate.java) / `…Impl` | `AbstractDbObject` CRUD/queries |
| [`AbstractDbOperationRemoteDelegate`](../java/org/tentackle/dbms/trip/AbstractDbOperationRemoteDelegate.java) / `…Impl` | `AbstractDbOperation` |
| [`DbModificationTrackerRemoteDelegate`](../java/org/tentackle/dbms/trip/DbModificationTrackerRemoteDelegate.java) / `…Impl` | modification tracking / serials |
| [`ModificationLogRemoteDelegate`](../java/org/tentackle/dbms/trip/ModificationLogRemoteDelegate.java) / `…Impl` | modification logs |

[`RemoteDbDelegateLocator`](../java/org/tentackle/dbms/trip/RemoteDbDelegateLocator.java) /
[`DbRemoteDelegateLocator`](../java/org/tentackle/dbms/trip/DbRemoteDelegateLocator.java)
(`@Service(RemoteDbDelegateLocator.class)`) find the right delegate for a class; the invocation handlers
([`DbRemoteInvocationHandler`](../java/org/tentackle/dbms/trip/DbRemoteInvocationHandler.java),
[`RemoteDbDelegateInvocationHandler`](../java/org/tentackle/dbms/trip/RemoteDbDelegateInvocationHandler.java)) and
[`RemoteDbSessionInvocationFilter`](../java/org/tentackle/dbms/trip/RemoteDbSessionInvocationFilter.java) route and
guard the calls. [`DbTripFactory`](../java/org/tentackle/dbms/trip/DbTripFactory.java) (`@Service(TripFactory.class)`)
installs the client-side invocation handler, which translates a vanished server-side delegate into a
[`RemoteSessionClosedException`](../java/org/tentackle/dbms/trip/RemoteSessionClosedException.java).
[`DbObjectResult`](../java/org/tentackle/dbms/trip/DbObjectResult.java) carries the new id, serial and table serial
of a remote insert/update/delete back to the client. [`SQLExceptionComponentProvider`](../java/org/tentackle/dbms/trip/SQLExceptionComponentProvider.java)
/ [`SQLExceptionInstanceCreator`](../java/org/tentackle/dbms/trip/SQLExceptionInstanceCreator.java) ensure JDBC
exceptions serialize correctly across the wire, and [`DefaultMasterSerial`](../java/org/tentackle/dbms/trip/DefaultMasterSerial.java)
carries the master modification serial back to clients.

### Preloading remote delegates

Delegates are created lazily: the first time a class needs one, `Db.getRemoteDelegate(int)` asks the
server for it — **one network roundtrip per class**. For an application with several hundred entities
running on a link with a high latency, these roundtrips accumulate and dominate the perceived
startup time.

A class can be marked as a candidate for preloading with
[`@PreloadRemoteDelegate`](../java/org/tentackle/dbms/trip/PreloadRemoteDelegate.java):

```java
@PersistentObjectService(User.class)
@PreloadRemoteDelegate
public class UserPersistenceImpl extends AbstractPersistentObject<User, UserPersistenceImpl> { ... }
```

The annotation is meta-annotated with `@Service`, so the [build](../../../../tentackle-common/src/main/docs/services.md)
collects the annotated class names in `META-INF/services/org.tentackle.dbms.trip.PreloadRemoteDelegate`.
The client reads them as plain strings via `DbUtilities.getPreloadDelegateClassNames()` — the classes
themselves are never loaded — and fetches all of their delegates in a **single roundtrip** using
`RemoteDbSession.getRemoteDelegates(String[])`:

```java
Session session = Pdo.createSession(sessionInfo);
session.preloadRemoteDelegates();       // one roundtrip instead of one per class
```

Preloaded delegates are consumed transparently: `getRemoteDelegate(int)` takes them from the preload
map instead of going to the server. Preloading is remembered for the session, so it is repeated on
`reOpen()` and after an automatic reconnection. A **cloned** session is a session of its own and does
not inherit the request — it must invoke `preloadRemoteDelegates()` itself if it wants to.

Two rules apply:

- **The application decides, per session.** Nothing is preloaded unless `preloadRemoteDelegates()`
  is invoked. Not every session benefits — preloading costs one roundtrip plus one server-side
  delegate per candidate, which is wasted on short-lived or special-purpose sessions.
- **Annotate the classes used at startup**, not the whole entity model, and never a class whose
  delegate changes the state of the server session when created. `DbModificationTracker` is such an
  example: creating its delegate marks the session as the modification tracker's session, so it must
  not be a preload candidate.

Class names the server cannot resolve are answered with `null` and logged there — preloading never
breaks the session. Since `getRemoteDelegates` is part of the `RemoteDbSession` interface, client and
server must be built from the same Tentackle version, which the login handshake verifies anyway.

---

## Package Layout

| Package | Contents |
|---------|----------|
| `org.tentackle.dbms` | The engine: `Db`, `AbstractDbObject`/`AbstractDbOperation`, connection managers and `ManagedConnection`, pools and factories, id-sources, statement/query/result wrappers, transactions, batching, modification tracking + table serials, modification logging, persistence visitors, diagnostics/statistics and the SPI utility replacements. |
| `org.tentackle.dbms.dsl` | The [query DSL](./query-dsl.md): typed tables, columns, expressions and conditions rendered to the SQL of a `Query`. |
| `org.tentackle.dbms.prefs` (+ `prefs.trip`) | Database-backed `java.util.prefs.Preferences` and its remoting. |
| `org.tentackle.dbms.trip` | The TRIP server, remote connection/session and the remote delegates that make the layer location-transparent. |
| `org.tentackle.dbms.service` | The [`Hook`](../java/org/tentackle/dbms/service/Hook.java) (`ModuleHook`) for i18n bundle resolution. |

---

## How It Fits Together

A persistence operation flows through the layers as follows:

```text
   tentackle-persistence  (AbstractPersistentObject — PDO-aware)
        │  extends
        ▼
   AbstractDbObject ──── runs against ──── Db (Session)
                                            │
                          local ────────────┤──────────── remote
                            ▼               │                ▼
                     ConnectionManager      │        DbRemoteDelegate (TRIP)
                            │               │                ▼ (on server)
                     ManagedConnection      │        …RemoteDelegateImpl runs the
                            │               │        same AbstractDbObject logic
                     Backend (tentackle-sql)│        against a local Db
                            │               │                │
                          JDBC  ◄───────────┘                ▼
                                                       results via TRIP
```

For a **local** `Db`, `AbstractDbObject` builds SQL via the
[`Backend`](../../../../tentackle-sql/src/main/docs/sql.md), borrows a `ManagedConnection` from the
`ConnectionManager`, executes through the statement wrappers, and maps the `ResultSetWrapper` back to objects. For a
**remote** `Db`, the call is routed by the delegate locator to a `…RemoteDelegate`; on the server the matching
`…RemoteDelegateImpl` runs the very same `AbstractDbObject` logic against its own local `Db` and returns the
results over TRIP. Either way every write advances a table serial in the `DbModificationTracker`, which caches and
remote clients consult (via the expiration backlog) to invalidate exactly the stale entries.

The per-entity classes that extend `AbstractDbObject`/`AbstractDbOperation` are **generated** by wurblets from the
[model](../../../../tentackle-model/src/main/docs/model-definition.md); this module is the generic runtime they
plug into.

---

## Module Dependencies

`tentackle-database` sits in the middle of the persistence stack:

- It **requires transitive** [`tentackle-session`](../../../../tentackle-session/src/main/docs/session.md) (the
  `Session` contract it implements) and [`tentackle-sql`](../../../../tentackle-sql/src/main/docs/sql.md) (the
  dialect-aware `Backend` it executes).
- JDBC drivers are supplied by the application. The tests run against H2; the PostgreSQL driver is a test
  dependency only used with `-Ppostgres`.
- It **opens** `org.tentackle.dbms` and `org.tentackle.dbms.prefs` to `org.tentackle.core` for
  [TRIP](../../../../tentackle-core/src/main/docs/trip.md) serialization.
- It **provides** a `ModuleHook` service (`org.tentackle.dbms.service.Hook`) for i18n.
- Through `@Service`, it **replaces** several lower-layer singletons (`SessionFactory` → `DbFactory`,
  `SessionPoolFactory` → `DbPoolFactory`, `ModificationTracker` → `DbModificationTracker`,
  `SessionUtilities` → `DbSessionUtilities`, `DiagnosticUtilities` → `DbDiagnosticUtilities`,
  `PersistedPreferencesFactory` → `DbPreferencesFactory`, `TripFactory` → `DbTripFactory`) and provides its own
  replaceable services (`RemoteSessionFactory`, `IdSourceConfigurator`, `ModificationLogFactory`,
  `RemoteDbDelegateLocator`, and the self-defaulting `DbUtilities`, `DbClassVariablesFactory`,
  `DbTransactionFactory`, `ModificationTypeFactory`, `ManagedConnectionMonitor`, `StatementTracer`,
  `TableSerialHistory`); the PDO layer above in turn replaces `DbUtilities`/`DbClassVariablesFactory` with
  PDO-aware versions.

## Related Documentation

- [Session](../../../../tentackle-session/src/main/docs/session.md) — the `Session`/transaction/tracking contracts this module implements.
- [The Query DSL](./query-dsl.md) — building projection queries from typed expressions instead of SQL strings.
- [Tentackle SQL](../../../../tentackle-sql/src/main/docs/sql.md) — the dialect-aware backend that builds the executed SQL.
- [Persistence](../../../../tentackle-persistence/src/main/docs/persistence.md) — the PDO-aware layer that builds on `AbstractDbObject`/`Db`.
- [PDO](../../../../tentackle-pdo/src/main/docs/pdo.md) — the persistent domain object pattern at the top of the stack.
- [TRIP](../../../../tentackle-core/src/main/docs/trip.md) — the remoting protocol behind the `*.trip` delegates.
- [Model Definition](../../../../tentackle-model/src/main/docs/model-definition.md) — where the entities whose persistence code extends this module are declared.
- [Services / ServiceFinder](../../../../tentackle-common/src/main/docs/services.md) — the SPI mechanism behind the `@Service`-replaced singletons and delegate discovery.
