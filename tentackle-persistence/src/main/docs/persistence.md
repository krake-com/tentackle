# Tentackle Persistence — The PDO-Aware Persistence Layer

## Overview and Motivation

The `tentackle-persistence` module is where the two halves of Tentackle's architecture finally meet: the
**Persistent Domain Object (PDO)** abstraction from [`tentackle-pdo`](../../../../tentackle-pdo/src/main/docs/pdo.md)
and the raw, dialect-aware database machinery from [`tentackle-database`](../../../../tentackle-database/src/main/docs/database.md). It
provides the concrete **persistence implementation** behind every PDO — the code that actually selects, inserts,
updates and deletes domain objects, manages their caches, locks, number ranges, security rules and the remoting
that lets all of this work transparently across JVMs.

In the PDO pattern (see [pdo.md](../../../../tentackle-pdo/src/main/docs/pdo.md)) a domain object is a dynamic
proxy that combines two injected implementations:

- a **domain** implementation (business logic), and
- a **persistence** implementation (database logic).

This module supplies the base classes for the *persistence* half:
[`AbstractPersistentObject`](../java/org/tentackle/persist/AbstractPersistentObject.java) for entities and
[`AbstractPersistentOperation`](../java/org/tentackle/persist/AbstractPersistentOperation.java) for operations.
The application's generated persistence classes extend these, the Wurbelizer fills in the entity-specific mapping
code (see [persistence-wurblets](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md)),
and at runtime the PDO proxy delegates all persistence calls here.

Where [`tentackle-database`](../../../../tentackle-database/src/main/docs/database.md) knows about tables, columns, `PreparedStatement`s, and
result sets (its `AbstractDbObject`/`AbstractDbOperation`), this module raises that to the level of *domain
objects*: it understands aggregates and their components, eager joins, the modification tracker, token locks and
the domain context. It is therefore the topmost runtime layer of the persistence stack — everything above it
(domain logic, UI) deals only in PDOs.

### Design principles

- **Bridge, not a new model.** The module does not invent its own entity model; it implements the `PersistentObject`
  / `PersistentOperation` contracts from `tentackle-pdo` on top of the `AbstractDbObject` / `AbstractDbOperation`
  primitives from `tentackle-database`.
- **Generated, not handwritten, mappings.** The per-entity SQL and column mapping live in generated code woven
  into the application's persistence classes by the Wurbelizer; the handwritten code here is the generic engine
  those classes plug into.
- **Aggregate-aware.** Roots and their components are first-class: operations like
  `insertPlainWithComponents` / `deletePlainWithComponents` and eager [`Join`](../java/org/tentackle/persist/Join.java)s
  understand composite relations so DDD invariants are preserved.
- **Location transparency via TRIP.** Every persistence capability has a *remote delegate* counterpart in the
  `*.trip` packages, so the identical PDO code runs whether the database is local or reached through a Tentackle
  server, which in turn may itself be a client of another Tentackle server, and so on.
- **SPI-replaced infrastructure.** Several singletons (`DbUtilities`, `ModificationTracker`,
  `DbClassVariablesFactory`, the remote-session factory and the remote-delegate locator) are *replaced* with
  PDO-aware implementations through Tentackle's `@Service`/`ServiceFinder` mechanism, so the lower layers
  transparently become PDO-aware.

---

## Key Concepts

### AbstractPersistentObject

[`AbstractPersistentObject<T,P>`](../java/org/tentackle/persist/AbstractPersistentObject.java) is the heart of the
module — the base persistence implementation of an entity PDO. It extends
`AbstractDbObject<P>` (database layer) and implements `PersistentObject<T>` (PDO contract), where `T` is the PDO
interface and `P` the concrete persistence class. It is `@TripReferencable(NEVER)` because only the PDO *proxy*
can be referenced when it travels across the wire, never the implementation.

Its (large) API covers the full persistence lifecycle of a domain object:

| Group                      | Examples                                                                                                                 | Purpose                                                                                                 |
|----------------------------|--------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| **Single-object CRUD**     | `select(id)`, `selectForUpdate`, `selectTokenLocked`, `persist`, `persistTokenLocked`, `save`, `delete`, `reload`        | Read and write one entity, optionally locking it.                                                       |
| **Bulk / aggregate**       | `insertPlainWithComponents`, `deletePlainWithComponents`, `selectAll`, `selectAny(ids)`, `save(Collection,…)`            | Operate on collections and on roots together with their components.                                     |
| **Querying**               | `selectByTemplate`, `selectByNormText`, `findDuplicate`, `selectLatest`, `selectAllAsCursor`, `selectByNormTextAsCursor` | Higher-level finders, including streaming via cursors.                                                  |
| **Caching**                | `selectCached`, `selectCachedOnly`, `selectAllCached`, `selectAnyCached`, `selectForCache`, `expireCache`                | The [PDO cache](../../../../tentackle-pdo/src/main/docs/cache.md), kept coherent via the table serials. |
| **Modification / serials** | `countModification`, `selectAllWithExpiredTableSerials`, `updateRootContext`                                             | Hook into change tracking and table serials.                                                            |
| **Token locking**          | the `editedBy` / `editedSince` / `editedExpiry` token-lock attributes plus the lock-aware `select*`/`reload*` variants   | Cooperative, persisted "currently edited by user X" locks (see [locking.md](locking.md)).               |
| **Normtext**               | `updateNormText`, `selectByNormText`                                                                                     | Phonetically normalized search text (see [NormText](#normtext)).                                        |

An entity optionally also carries a **normText** column (a phonetically normalized search string, model option
`normtext`) and the token-lock columns (model option `tokenlock`), which is why those concerns are baked into the
base class. The low-level `insertObject`/`updateObject`/`deleteObject` are not part of the PDO API and cannot be
invoked remotely; applications use `persist`, `save` and `delete` on root entities. Inserts, updates and deletes
take part in [JDBC batching](../../../../tentackle-database/src/main/docs/batching.md) if the transaction runs in
batched mode.

### AbstractPersistentOperation

[`AbstractPersistentOperation<T,P>`](../java/org/tentackle/persist/AbstractPersistentOperation.java) is the
persistence base for **operations** — units of work that are not a single entity but still need a persistence
context, typically complex multistep transactions (see
[operation.md](../../../../tentackle-pdo/src/main/docs/operation.md)). It extends `AbstractDbOperation<P>` and
implements `PersistentOperation<T>` / `PersistenceDelegate<T>`, is bound to a `DomainContext`, and is remoting
capable like entities.

### Class variables

Tentackle keeps per-class (static) metadata in *class-variable* objects.
[`PersistentObjectClassVariables`](../java/org/tentackle/persist/PersistentObjectClassVariables.java) and
[`PersistentOperationClassVariables`](../java/org/tentackle/persist/PersistentOperationClassVariables.java) extend
the database layer's `DbObjectClassVariables` / `DbOperationClassVariables` with PDO-level information. They are
produced by [`PersistentClassVariablesFactory`](../java/org/tentackle/persist/PersistentClassVariablesFactory.java),
which is registered as a `@Service(DbClassVariablesFactory.class)` so it *replaces and extends* the database
layer's factory — making all class variables PDO-aware. The `CLASSVARIABLES` of a persistence class, including
its eager joins, are generated by the `ClassVariables` wurblet (see
[persistence-wurblets](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md)).

### Modification tracking

[`PdoModificationTracker`](../java/org/tentackle/persist/PdoModificationTracker.java) is the persistence-layer
implementation of the `ModificationTracker` from [`tentackle-session`](../../../../tentackle-session/src/main/docs/session.md)
(registered via `@Service(ModificationTracker.class)`). It maintains the table serials that drive cache expiry
and cross-client change notification: when an entity is written, the modification serial of its table advances;
caches and remote clients compare serials to learn what to refresh. It differs from the database layer's tracker
only in mapping PDO interfaces to their table names. See
[modification-tracking.md](../../../../tentackle-session/src/main/docs/modification-tracking.md) for the
full picture.

### Token locking

A **token lock** is a cooperative, persisted lock expressing "this object is currently being edited by user X
until time Y". Unlike a database row lock, it survives across transactions and is visible to other users.

The token lock is opt-in: an entity gets the `editedBy`/`editedSince`/`editedExpiry` columns by declaring the
`tokenlock` option in its model.

- [`LockManager`](../java/org/tentackle/persist/LockManager.java) — tracks the state of *all* token locks in
  parallel to the PDO columns, so the full set of locks is known without scanning every table. It exists to clean
  up locks when a **client logs out or its session crashes** (drop that user's locks) or when the **server
  restarts** (drop all locks). It is only enabled on servers connected directly to the database; in 2-tier and
  remote-client setups it is disabled.
- [`TokenLock`](../java/org/tentackle/persist/TokenLock.java) — a lock as the `LockManager` knows it: the PDO's
  class ID and ID, the holder, and since/expiry.
- [`DefaultLockManager`](../java/org/tentackle/persist/lock/DefaultLockManager.java) (`@Service(LockManager.class)`)
  persists locks in a dedicated table of [`DbTokenLock`](../java/org/tentackle/persist/lock/DbTokenLock.java) rows.
  Applications can keep selected locks across a session cleanup or server restart by overriding
  `isTokenLockToKeep`.

See [locking.md](locking.md) for how token locks and the optimistic `serial` column work together.

### Joins

[`Join`](../java/org/tentackle/persist/Join.java) and [`JoinedSelect`](../java/org/tentackle/persist/JoinedSelect.java)
implement PDO-level joins — fetching two related PDOs in a single `SELECT`. They back both **eager joins**
(relations marked to load with their root) and **explicit domain-driven joins**, and work for composite
(aggregate component) and non-composite relations alike.

A `Join` is a *description* of how to join that is not modified once set up. Model-based eager joins are created
once in the static class variables of a persistence class, so the same `Join` instances are used concurrently by
every thread selecting that entity. The bookkeeping of which PDOs have already been joined therefore lives in
[`JoinState`](../java/org/tentackle/persist/JoinState.java), which is created per query and owned by the
`JoinedSelect` — never in the `Join` itself.

See [eager-relations.md](../../../../tentackle-persistence-wurblets/src/main/docs/eager-relations.md) for how
eager joins are declared in the model, and
[persistence-wurblet-api.md](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblet-api.md)
for using `JoinedSelect` in handwritten finders.

### Cursors

[`ResultSetCursor`](../java/org/tentackle/persist/ResultSetCursor.java) is a `ScrollableResource<T>` that turns a
`ResultSetWrapper` into a forward/scrollable stream of PDOs, so large result sets can be processed without
materializing them all in memory. Its remote counterpart is
[`RemoteResultSetCursor`](../java/org/tentackle/persist/trip/RemoteResultSetCursor.java) /
[`RemoteResultSetCursorImpl`](../java/org/tentackle/persist/trip/RemoteResultSetCursorImpl.java), so cursors work
over a remote connection too.

See [cursors.md](cursors.md) for the full picture: how one class serves both session types, block
fetching, the join constraint, and cursor lifetime.

### NormText

[`NormText`](../java/org/tentackle/persist/NormText.java) builds a *normtext* — a phonetically normalized string —
from user input, for searching PDO normtext columns with `LIKE`/`NOT LIKE`. Entities declaring the `normtext`
model option maintain such a column, built from the attributes and relations flagged for it, enabling tolerant,
accent/case-insensitive searches via `selectByNormText` and `selectByNormTextAsCursor`.

The search string is normalized the same way as the column and wrapped in `%…%`, so it matches anywhere.
A leading `!` or `-` inverts the search (`NOT LIKE`), and an empty string matches all.

### Utilities

[`PersistenceUtilities`](../java/org/tentackle/persist/PersistenceUtilities.java) is registered as
`@Service(DbUtilities.class)` and **replaces** the database layer's `DbUtilities` with a PDO-aware version — the
single most visible example of how this module quietly upgrades the layer beneath it. Among others, it creates
and selects PDOs where the database layer expects low-level objects, forwards commits and rollbacks to the
`LockManager`, releases the token locks of a closed client session, and collects the caches to preload.

---

## Remoting (TRIP)

Persistence is fully **location transparent**: the same PDO API runs against a local database or a remote
Tentackle server. This is achieved by pairing each persistence capability with a TRIP *remote delegate* — an
interface (the contract) plus an `…Impl` (the server-side executor):

| Delegate (interface / impl)                                                                                                                | Mirrors                                          |
|--------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------|
| [`AbstractPersistentObjectRemoteDelegate`](../java/org/tentackle/persist/trip/AbstractPersistentObjectRemoteDelegate.java) / `…Impl`       | `AbstractPersistentObject` (entity CRUD/queries) |
| [`AbstractPersistentOperationRemoteDelegate`](../java/org/tentackle/persist/trip/AbstractPersistentOperationRemoteDelegate.java) / `…Impl` | `AbstractPersistentOperation`                    |
| [`AdminExtensionAdapterRemoteDelegate`](../java/org/tentackle/persist/trip/AdminExtensionAdapterRemoteDelegate.java) / `…Impl`             | admin/session-management operations              |
| [`RemoteResultSetCursor`](../java/org/tentackle/persist/trip/RemoteResultSetCursor.java) / `…Impl`                                         | streaming cursors                                |
| [`PdoCacheOperationRemoteDelegate`](../java/org/tentackle/persist/trip/PdoCacheOperationRemoteDelegate.java) / `…Impl`                     | cache preloading (`PdoCacheOperation`)           |
| [`PdoModificationTrackerRemoteDelegate`](../java/org/tentackle/persist/trip/PdoModificationTrackerRemoteDelegate.java) / `…Impl`           | `PdoModificationTracker`                         |

The low-level methods inherited from the database layer (`insertObject`, `updatePlain`, `selectAllObjects`, …)
are disabled in `AbstractPersistentObjectRemoteDelegateImpl`, so a malicious client cannot bypass the PDO-level
security checks.

[`PdoComponentTripFilter`](../java/org/tentackle/persist/trip/PdoComponentTripFilter.java) is a `TripFilter` that
serializes PDOs without their related objects (except embedded ones), so they are loaded lazily on the receiving
side — see [Filtering components with TripFilter](../../../../tentackle-core/src/main/docs/trip.md#filtering-components-with-tripfilter).

[`PdoRemoteDelegateLocator`](../java/org/tentackle/persist/trip/PdoRemoteDelegateLocator.java)
(`@Service(RemoteDbDelegateLocator.class)`) locates the right delegate for a PDO. On the client, a PDO method call
is routed to the matching delegate; on the server, the `…Impl` runs the real `AbstractPersistentObject` logic
against a local database connection and ships the results back via TRIP.

Session-side remoting is provided by
[`PdoRemoteSessionAdapter`](../java/org/tentackle/persist/PdoRemoteSessionAdapter.java) (a PDO-aware
`RemoteSessionAdapter`, which also hands out session extensions after checking the execute permission) and
[`PdoRemoteSessionFactory`](../java/org/tentackle/persist/PdoRemoteSessionFactory.java)
(`@Service(RemoteSessionFactory.class)`), while
[`DefaultPersistenceDelegateLinker`](../java/org/tentackle/persist/DefaultPersistenceDelegateLinker.java)
(`@Service(PersistenceDelegateLinker.class)`) wires a PDO proxy to its persistence delegate, and
[`AdminExtensionAdapter`](../java/org/tentackle/persist/AdminExtensionAdapter.java)
(`@Service(AdminExtension.class)`) adds administrative session services: listing, logging out and
killing client sessions (see [Session administration](../../../../tentackle-fx-rdc/src/main/docs/rdc.md#session-administration)).

---

## Built-in Persistence Implementations

The module ships ready-made persistence implementations for the framework's own infrastructure PDOs. Each is
annotated `@PersistentObjectService(...)` so it is discovered as the persistence half of the corresponding PDO:

- **Number sources** (`org.tentackle.persist.ns`) — allocation of *business* numbers (invoice numbers,
  customer numbers, …) from number pools and their ranges, not of technical IDs:
  [`NumberPoolPersistenceImpl`](../java/org/tentackle/persist/ns/NumberPoolPersistenceImpl.java) and
  [`NumberRangePersistenceImpl`](../java/org/tentackle/persist/ns/NumberRangePersistenceImpl.java), with their
  remote delegates under `ns/trip`. See [ns.md](../../../../tentackle-pdo/src/main/docs/ns.md).
- **Security rules** (`org.tentackle.persist.security`) —
  [`SecurityPersistenceImpl`](../java/org/tentackle/persist/security/SecurityPersistenceImpl.java) persists the
  access-control rules, with its remote delegate under `security/trip`.
  See [security.md](../../../../tentackle-pdo/src/main/docs/security.md).

Besides, [`PdoCacheOperationPersistenceImpl`](../java/org/tentackle/persist/PdoCacheOperationPersistenceImpl.java)
implements the `PdoCacheOperation` that preloads the caches of all persistence classes annotated with
[`@PreloadCache`](../java/org/tentackle/persist/PreloadCache.java) — for remote clients in a single round trip.
See [Preloading Caches at Startup](../../../../tentackle-pdo/src/main/docs/cache.md#preloading-caches-at-startup-preloadcache).

---

## Server Application Lifecycle

The `org.tentackle.persist.app` package provides the entry points for the **middle-tier server**:

- [`AbstractServerApplication`](../java/org/tentackle/persist/app/AbstractServerApplication.java) — base class
  handling server startup/shutdown, configuration and the persistence environment. A server connected directly
  to the database serves its clients from a session pool with its own connection manager; a server that is
  itself a client of another server uses a pool of remote sessions instead. At startup, all validators are
  validated, so configuration errors show up immediately.
- [`ServerApplication`](../java/org/tentackle/persist/app/ServerApplication.java) — the abstract base of TRIP
  application servers clients connect to; it starts the `TripServer` hosting the remote delegates described above
  and, if connected directly to the database, initializes the `LockManager`.

See [application.md](../../../../tentackle-pdo/src/main/docs/application.md) for the application lifecycle and
[tiers.md](../../../../src/main/docs/tiers.md) for how servers cascade.

---

## Package Layout

| Package                                              | Contents                                                                                                                                                                                                                                                                                                                                                   |
|------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `org.tentackle.persist`                              | The core: `AbstractPersistentObject`, `AbstractPersistentOperation`, class-variables and their factory, the modification tracker, token-lock interfaces, joins (`Join`, `JoinedSelect`, `JoinState`), cursors, normtext, cache preloading (`PreloadCache`, `PdoCacheOperationPersistenceImpl`), the PDO-aware utilities and the session/delegate adapters. |
| `org.tentackle.persist.app`                          | Server-application lifecycle (`AbstractServerApplication`, `ServerApplication`).                                                                                                                                                                                                                                                                           |
| `org.tentackle.persist.lock`                         | The default `LockManager` and its persisted `DbTokenLock`.                                                                                                                                                                                                                                                                                                 |
| `org.tentackle.persist.ns` (+ `ns.trip`)             | Persistence for number sources (pools/ranges) and their remoting.                                                                                                                                                                                                                                                                                          |
| `org.tentackle.persist.security` (+ `security.trip`) | Persistence for security rules and their remoting.                                                                                                                                                                                                                                                                                                         |
| `org.tentackle.persist.trip`                         | The PDO-aware TRIP remote delegates (entity, operation, admin, cursor, cache preloading, modification tracker), the delegate locator and the `PdoComponentTripFilter`.                                                                                                                                                                                     |
| `org.tentackle.persist.service`                      | The [`Hook`](../java/org/tentackle/persist/service/Hook.java) (`ModuleHook`) for i18n bundle resolution.                                                                                                                                                                                                                                                   |

---

## How It Fits Together

A typical read of an entity through a PDO proxy flows like this:

```text
   PDO proxy (Customer)
        │  select(id)
        ▼
   persistence delegate  ── AbstractPersistentObject.select(id)
        │
   local ──────────────┐                    remote ─────────────┐
        ▼              │                          ▼             │
   AbstractDbObject    │   builds SQL via    PdoRemoteDelegateLocator
   + Backend (SQL)     │   the Backend       → …RemoteDelegate (TRIP)
        ▼              │                          ▼ (on server)
   JDBC ResultSet ─────┘                    …RemoteDelegateImpl runs the
        │                                   *same* AbstractPersistentObject
        ▼                                   logic against a local connection
   mapped PDO(s) returned
```

For a **local** session, `AbstractPersistentObject` assembles SQL via the
[`Backend`](../../../../tentackle-sql/src/main/docs/sql.md) and executes it through the
[`tentackle-database`](../../../../tentackle-database/src/main/docs/database.md) primitives. For a **remote** session, the call is shipped to
the server, where the corresponding `…RemoteDelegateImpl` runs the very same logic against its local connection and
returns the PDOs over TRIP. Caches stay coherent because every write advances the table serial tracked by the
`PdoModificationTracker`, which clients consult to expire stale entries.

The per-entity SQL, column mapping, finders, and relations are **generated** into the application's persistence
classes by the Wurbelizer using the model; this module is the generic runtime those generated classes extend. See
[persistence-wurblets](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md) for the
generation side and [model-definition](../../../../tentackle-model/src/main/docs/model-definition.md) for where
entities, relations, and attributes are declared.

---

## Module Dependencies

`tentackle-persistence` sits at the top of the persistence stack:

- It **requires transitive** [`tentackle-pdo`](../../../../tentackle-pdo/src/main/docs/pdo.md) (the PDO contracts
  it implements) and [`tentackle-database`](../../../../tentackle-database/src/main/docs/database.md) (the low-level DB engine it builds on),
  plus (non-transitively) `java.naming` to detect a container.
- It **opens** its `persist`, `persist.lock`, `persist.ns`, `persist.security` and `persist.trip` packages to
  `org.tentackle.core` for TRIP serialization.
- It **provides** a `ModuleHook` service (`org.tentackle.persist.service.Hook`) for i18n.
- Through `@Service`/`@MappedService` it **replaces** several lower-layer singletons with PDO-aware versions
  (`DbUtilities` → `PersistenceUtilities`, `ModificationTracker` → `PdoModificationTracker`,
  `DbClassVariablesFactory` → `PersistentClassVariablesFactory`, `RemoteSessionFactory` → `PdoRemoteSessionFactory`,
  `RemoteDbDelegateLocator` → `PdoRemoteDelegateLocator`), so code written against the database layer
  automatically becomes PDO-aware at runtime.

## Related Documentation

- [Locking](locking.md) — token locks and the optimistic `serial` column.
- [Cursors](cursors.md) — streaming large result sets, locally and remotely.
- [PDO](../../../../tentackle-pdo/src/main/docs/pdo.md) — the persistent domain object pattern this module implements.
- [Operations](../../../../tentackle-pdo/src/main/docs/operation.md) — operations backed by `AbstractPersistentOperation`.
- [Persistence Wurblets](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md) — generates the per-entity persistence code that extends this module.
- [Model Definition](../../../../tentackle-model/src/main/docs/model-definition.md) — where entities, relations, and attributes are declared.
- [Session](../../../../tentackle-session/src/main/docs/session.md) — the session/transaction/tracking context the persistence layer runs in.
- [Tentackle SQL](../../../../tentackle-sql/src/main/docs/sql.md) — the dialect-aware backend that builds the executed SQL.
- [Services / ServiceFinder](../../../../tentackle-common/src/main/docs/services.md) — the SPI mechanism behind the `@Service`-replaced singletons and delegate discovery.
