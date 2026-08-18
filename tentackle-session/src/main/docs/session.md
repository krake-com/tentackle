# Tentackle Session — The Persistence and Security Context Layer

## Overview and Motivation

The `tentackle-session` module defines the **session abstraction**: the runtime context that ties a user, a
connection target, and a transaction together. A [`Session`](../java/org/tentackle/session/Session.java) is the
single object every higher layer talks to in order to start transactions, run database work, share state, and be
notified about changes — regardless of whether the database is connected directly to the current JVM or reached
through a remote Tentackle server (or even a chain of middle-tiers).

The module sits between [`tentackle-core`](../../../../tentackle-core/src/main/docs/core.md) (its only dependency) and the persistence
stack ([`tentackle-database`](../../../../tentackle-database/src/main/docs/database.md), [`tentackle-persistence`](../../../../tentackle-persistence/src/main/docs/persistence.md),
[`tentackle-pdo`](../../../../tentackle-pdo/src/main/docs/pdo.md)). It deliberately contains **only interfaces, abstract bases,
annotations, events, and a handful of small helper classes** — the concrete `Session` implementation lives in
`tentackle-database` (local) and in the remoting modules (remote). This keeps the contract that the whole
framework programs against free of any JDBC, SQL-dialect, or wire-protocol detail.

A central design goal is **location transparency**. The same `Session` API is used whether persistence runs
locally or on a middle-tier server. A session knows whether it is *local* (the physical database connection lives
in this JVM) or *remote* (calls are routed via [TRIP](../../../../tentackle-core/src/main/docs/trip.md) to a server), and application code does not have to care.

### Design principles

- **Interface-only contract.** Everything that the framework above needs is expressed as an interface
  (`Session`, `SessionInfo`, `SessionPool`, `ModificationTracker`, …) located through a factory. Implementations
  are injected via Tentackle's `ServiceFinder`/`ServiceFactory` SPI, so the same calling code binds to a local or
  remote implementation at runtime.
- **No dependency on the database or SQL layer.** Notably, even the connection/driver *configuration* classes
  (`BackendConfiguration`, `DriverConfiguration`) live here and explicitly avoid depending on `tentackle-sql` or
  `tentackle-database`, so UI and configuration code can manipulate connection settings without dragging in the
  backend machinery.
- **Location transparency.** `isRemote()`/`getRemoteSession()` expose locality when needed, but the transaction,
  property, and change-notification APIs behave identically for local and remote sessions.
- **Thread affinity via thread-locals.** The "current" session is held in a `ThreadLocal`, so domain and
  persistence code can reach it without threading it through every method signature.

---

## Key Concepts

### Session

[`Session`](../java/org/tentackle/session/Session.java) is the heart of the module — a *persistence and security
context*. Its responsibilities group into:

| Group                          | Methods                                                                                                                        | Purpose                                                                                                    |
|--------------------------------|--------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| **Current-session management** | `getCurrentSession`, `setCurrentSession`, `getSession`, `makeCurrent`, `isCurrent`, `clearCurrent`                             | Maintain and reach the thread-local "current" session.                                                     |
| **Lifecycle**                  | `close`, `reOpen`, `clone`, `registerCloseHandler`, `isPooled`                                                                 | Open/close/clone sessions and react to closing.                                                            |
| **Transactions**               | `begin`, `commit`, `rollback`, `rollbackSilently`, `transaction(...)`, `isTxRunning`, `getTxName`, `getTxNumber`, `getTxLevel` | Start and finish (possibly nested) transactions.                                                           |
| **Savepoints**                 | `setSavepoint`, `rollback(SavepointHandle)`, `releaseSavepoint`                                                                | Fine-grained rollback within a transaction.                                                                |
| **Pre/post-commit hooks**      | `preCommit`, `postCommit`                                                                                                      | Run logic immediately before, or right after, the physical commit (e.g. firing events post-commit).        |
| **Grouping**                   | `groupWith`, `getSessionId`, `getSessionGroupId`                                                                               | Tie several sessions of the same URL into a group that shares e.g. "same user".                            |
| **Locality**                   | `isRemote`, `getRemoteSession`, `getUrl`                                                                                       | Discover whether the backend is local or reached via a server.                                             |
| **Keep-alive**                 | `setAlive`, `isAlive`, `setKeepAliveInterval`, `getKeepAliveInterval`                                                          | Prevent idle remote sessions from being timed out by the server.                                           |
| **State sharing**              | `setProperty`, `getProperty`, `clearProperties`                                                                                | Application state that, for remote sessions, is mirrored to the server-side session.                       |
| **Misc**                       | `getDispatcher`, `setOwnerThread`, `getInstanceNumber`, `applyTo(...)`                                                         | Async task dispatch, accidental-cross-thread detection, propagating the session onto `SessionDependable`s. |

The static `getCurrentSession()`/`setCurrentSession()` pair backs the thread-local model; `getSession()` asserts
one is present and throws a [`PersistenceException`](../java/org/tentackle/session/PersistenceException.java)
otherwise.

#### Transactions

`begin()` returns a **transaction voucher** (a `long`). Only the call that actually started the transaction
receives a non-zero voucher; nested `begin()`s return 0 and do nothing. The matching `commit(voucher)` /
`rollback(voucher)` only commit or roll back if the voucher is the one that opened the transaction — this makes
correct nesting trivial: every method may `begin()`/`commit()` defensively, and only the outermost actually
commits. The `transaction(...)` convenience methods wrap a lambda in this protocol and are the recommended
alternative to the `@Transaction` interceptor when the holder is not an `Interceptable`.

[`TransactionIsolation`](../java/org/tentackle/session/TransactionIsolation.java) and
[`TransactionWritability`](../java/org/tentackle/session/TransactionWritability.java) are type-safe enums over the
`java.sql.Connection` isolation constants and read-only/read-write mode, used when a transaction needs something
other than the default.

### SessionInfo

[`SessionInfo`](../java/org/tentackle/session/SessionInfo.java) is the **immutable, serializable descriptor** of
*who* and *what* a session represents: user id/name/class-id, password (held in `char[]`, wipeable via
`clearPassword()`), application name/id, locale, timezone, client version, host/OS/VM info, and the connection
`properties`/`propertiesName`. It travels to the server via TRIP when a remote session logs
in, and the server may write values back (e.g., the resolved user-id) for the client to read.

`SessionInfo`s are created by the [`SessionInfoFactory`](../java/org/tentackle/session/SessionInfoFactory.java)
singleton (default implementation
[`DefaultSessionInfoFactory`](../java/org/tentackle/session/DefaultSessionInfoFactory.java) producing
[`DefaultSessionInfo`](../java/org/tentackle/session/DefaultSessionInfo.java)), which can build one from a
username/password, from a properties name, or from an
[`EncryptedProperties`](../../../../tentackle-common/src/main/docs/cryptor.md) object — honouring
`Cryptor`-encrypted credentials. It also
carries flags such as `lockLingerEnabled` (whether token-locks survive a session close) and `checkServerVersion`
for client/server version validation (`VersionIncompatibleException`).

### Session creation, factory, and pooling

- [`SessionFactory`](../java/org/tentackle/session/SessionFactory.java) — the singleton that creates open
  sessions from a `SessionInfo` (`create()`, `create(info)`), plus a pool-internal `create(pool, info)`. It also
  manages **global close handlers** applied to every session.
- [`SessionPool`](../java/org/tentackle/session/SessionPool.java) — a pool of sessions for *one* `SessionInfo`,
  letting a single user run several sessions in parallel. Built via
  [`SessionPoolFactory`](../java/org/tentackle/session/SessionPoolFactory.java).
- [`MultiUserSessionPool`](../java/org/tentackle/session/MultiUserSessionPool.java) — a pool spanning *multiple*
  `SessionInfo`s, so different users can each have parallel sessions (typical for a server).
- [`SessionPoolProvider`](../java/org/tentackle/session/SessionPoolProvider.java) — abstracts whether a pool is
  local or remote.
- [`SessionPooledExecutor`](../java/org/tentackle/session/SessionPooledExecutor.java) — runs `Runnable`s in
  parallel, each on its own pooled session, for background work.

### Modification tracking

The change-notification subsystem lets any JVM learn that data changed, even when the change happened on another
client through the server. This section is the overview — see
[Modification Tracking](modification-tracking.md) for the full story: the modification table, counting and
rollback correction, operation modes, the poll loop, delayed listeners and master serial events.

- [`ModificationTracker`](../java/org/tentackle/session/ModificationTracker.java) — the singleton that tracks
  global PDO changes. It maintains its own session (and is both a `SessionTaskDispatcher` and an
  `ExclusiveSessionProvider`). Code calls `countModification(session, name)` when it modifies data, and
  `addDeletion(...)` for deletes; listeners are registered via `addModificationListener(...)`. It exposes the
  current `getIdSerialForClass(...)` / `getIdSerialForName(...)`, can `invalidate()` itself, and reports
  `isLocalClientMode()` (clients directly on the DB, no middle tier). Convenience statics `submit(...)` / `transaction(...)` schedule work on the
  tracker's own session.
- [`ModificationListener`](../java/org/tentackle/session/ModificationListener.java) — listens for changes by
  *name* (usually a table name, but any application name is allowed; `null` = listen to everything). Beyond
  `dataChanged(event)`, it controls ordering (`getPriority`) and batching (`getTimeFrame`, `getTimeDelay`) so
  bursts of events can be coalesced and fired at a randomized time to spread server load.
  [`ModificationListenerAdapter`](../java/org/tentackle/session/ModificationListenerAdapter.java) is a convenience
  base.
- [`ModificationEvent`](../java/org/tentackle/session/ModificationEvent.java) /
  [`ModificationEventDetail`](../java/org/tentackle/session/ModificationEventDetail.java) — events come in three
  flavors: a **master** event (any change, no detail), a **single** named event, or a **multi** named event
  carrying one detail per name.

### Master serial events

On top of the bare modification *serial*, the module supports pushing richer, application-specific events from
server to client:

- [`MasterSerial`](../java/org/tentackle/session/MasterSerial.java) wraps the `long serial` the tracker of a
  remote client periodically polls from the server.
- [`MasterSerialEvent`](../java/org/tentackle/session/MasterSerialEvent.java) is an application-defined event
  (extending the serial with arbitrary payload). Each event class is paired with a `Consumer` handler annotated
  with [`@MasterSerialEventService`](../java/org/tentackle/session/MasterSerialEventService.java) — a
  `MappedService` that maps an event type to its handler. This means the **server need not know the handlers at
  all**, and different clients can react differently to the same server-side event.
- [`MasterSerialListEvent`](../java/org/tentackle/session/MasterSerialListEvent.java) bundles several pending
  events for a client (always add via `add(...)` so the override-older-events rule is honored).
- [`MasterSerialEventHandlerFactory`](../java/org/tentackle/session/MasterSerialEventHandlerFactory.java) (default
  [`DefaultMasterSerialEventHandlerFactory`](../java/org/tentackle/session/DefaultMasterSerialEventHandlerFactory.java))
  resolves handlers. The annotation is processed at build time by
  [`MasterSerialEventServiceAnnotationProcessor`](../java/org/tentackle/session/apt/MasterSerialEventServiceAnnotationProcessor.java).

### Session task dispatching

- [`SessionTaskDispatcher`](../java/org/tentackle/session/SessionTaskDispatcher.java) — a `TaskDispatcher` bound to
  a `Session`, executing submitted tasks **serially on one exclusive connection** (default impl
  [`DefaultSessionTaskDispatcher`](../java/org/tentackle/session/DefaultSessionTaskDispatcher.java), with
  [`SessionTaskDispatcherLock`](../java/org/tentackle/session/SessionTaskDispatcherLock.java)). It can keep its
  session alive and optionally close it on termination.
- [`AbstractSessionTask`](../java/org/tentackle/session/AbstractSessionTask.java) — base for tasks that need the
  dispatcher's session (it is a `SessionDependable`).
- [`SessionKeepAliveDaemon`](../java/org/tentackle/session/SessionKeepAliveDaemon.java) /
  [`SessionKeepAliveTask`](../java/org/tentackle/session/SessionKeepAliveTask.java) — periodically `setAlive()` a
  session (pinging asynchronously so a slow remote line cannot block the dispatcher), honoring each session's
  `keepAliveInterval`.

### Session providers and dependables

- [`SessionProvider`](../java/org/tentackle/session/SessionProvider.java) — anything that can hand out a session.
- [`ExclusiveSessionProvider`](../java/org/tentackle/session/ExclusiveSessionProvider.java) — provides
  *exclusive*, reentrant access to a session: once acquired, no other thread gets it until released the matching
  number of times.
- [`SessionDependable`](../java/org/tentackle/session/SessionDependable.java) — an object carrying a session;
  `Session.applyTo(...)` propagates a session onto such objects (and collections of them, including a
  `TrackedList`'s removed objects), guarding against cycles.
- [`SessionHolder`](../java/org/tentackle/session/SessionHolder.java) /
  [`ThreadLocalSessionHolder`](../java/org/tentackle/session/ThreadLocalSessionHolder.java) — a serializable
  holder of a session; the thread-local variant is handy where no domain context is available.

### Connection configuration

These two classes describe *how to connect* and are persisted through the standard `java.util.prefs.Preferences`
API. Importantly, they depend on **neither** `tentackle-sql` nor `tentackle-database`, so configuration UIs (e.g.,
in `tentackle-fx-rdc`) can edit them without the backend on the classpath.

- [`AbstractSessionConfiguration`](../java/org/tentackle/session/AbstractSessionConfiguration.java) — shared base;
  `Validateable` and a `ScopeConfigurator`, with a symbolic `name` and a `persisted` flag.
- [`BackendConfiguration`](../java/org/tentackle/session/BackendConfiguration.java) — the connection target: URL,
  options, user, password (stored encrypted if a `Cryptor` is configured) and a reference to a
  `DriverConfiguration`. Provides static lookup of all configurations / the default for an application from user
  or system preferences.
- [`DriverConfiguration`](../java/org/tentackle/session/DriverConfiguration.java) — how to load the JDBC driver
  (driver class name + URL to load it from).

Database-centric 2-tier applications, such as BundleMonkey, use this to configure the database connections.
It is rarely used in a client-server application.

### Annotations describing the persistent model

These annotations let the persistence layer examine the generated model by reflection:

- [`@Persistent`](../java/org/tentackle/session/Persistent.java) — marks persistent fields, methods or types,
  carrying `comment`, `component`, `parent` and an `ordinal`. The ordinal encodes the field/method order from the
  model definition.
- [`@ClassId`](../java/org/tentackle/session/ClassId.java) — the numeric class id of a persistent object
  (a `MappedService`).
- [`@TableName`](../java/org/tentackle/session/TableName.java) — the database table name for a PDO, with
  build-time options `mapSchema` (replace dots by underscores) and `prefix` (configurable via the Wurbelizer
  plugin's `wurbletProperties`, e.g. `tablePrefix`), to retarget schemas/tables without touching the model.
  See [Table Names](table-names.md) for the full picture, including the corresponding maven parameters.

[`SessionValidationUtilities`](../java/org/tentackle/session/SessionValidationUtilities.java) (a `@Service`
overriding the core `ValidationUtilities`) uses the `@Persistent` ordinal so validators fire in priority plus
source-code order.

### Exceptions

A family of (mostly persistence-related) exceptions is defined here so the upper layers share one vocabulary:
[`PersistenceException`](../java/org/tentackle/session/PersistenceException.java) (the root),
[`SessionClosedException`](../java/org/tentackle/session/SessionClosedException.java),
[`LoginFailedException`](../java/org/tentackle/session/LoginFailedException.java),
[`AlreadyLoggedInException`](../java/org/tentackle/session/AlreadyLoggedInException.java),
[`VersionIncompatibleException`](../java/org/tentackle/session/VersionIncompatibleException.java),
[`NotFoundException`](../java/org/tentackle/session/NotFoundException.java),
[`NotRemovableException`](../java/org/tentackle/session/NotRemovableException.java) and
[`ConstraintException`](../java/org/tentackle/session/ConstraintException.java).

---

## Package Layout

| Package                         | Contents                                                                                                                                                                                                                                                           |
|---------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `org.tentackle.session`         | The whole public API: `Session`, `SessionInfo`, the factories and pools, the modification-tracking and master-serial types, the task-dispatcher types, the configuration classes, the model annotations, the `Default*` helper implementations and the exceptions. |
| `org.tentackle.session.service` | The [`Hook`](../java/org/tentackle/session/service/Hook.java) (`ModuleHook`) providing resource-bundle/i18n access for the module.                                                                                                                                 |
| `org.tentackle.session.apt`     | The annotation processor [`MasterSerialEventServiceAnnotationProcessor`](../java/org/tentackle/session/apt/MasterSerialEventServiceAnnotationProcessor.java) for `@MasterSerialEventService`.                                                                      |

---

## How It Fits Together

### Obtaining and using a session

Application code rarely constructs sessions directly; it usually receives one from a pool or the current domain
context. At the lowest level, however, the flow is:

```java
// describe who/what
SessionInfo info = SessionInfoFactory.getInstance().create("scott", password, "myapp");

// create an open session (local or remote, decided by the configuration/URL)
Session session = SessionFactory.getInstance().create(info);
session.makeCurrent();                 // becomes the thread-local current session

// transactional work — only the outermost begin() commits
long tx = session.begin("doStuff");
try {
    // ... persistence operations run against Session.getSession() ...
    session.commit(tx);
}
catch (RuntimeException ex) {
    session.rollback(tx);
    throw ex;
}

// or, equivalently, with the lambda form:
session.transaction("doStuff", () -> {
    // ... work ...
    return null;
});
```

### Local vs. remote

The very same code runs unchanged whether `session.isRemote()` is true or false. For a remote session, the
`SessionFactory` binds a remoting implementation that routes `begin`/`commit`/query calls over TRIP to the server,
where a mirror session does the actual JDBC work; `getRemoteSession()` exposes server-side helpers (e.g.,
server-side logging). For a local session, the implementation in `tentackle-database` talks to JDBC directly.

### Reacting to changes across clients

A client registers a `ModificationListener` with the `ModificationTracker`. The tracker (in remote mode)
periodically polls the server's master serial; when it advances, the server returns the pending
`MasterSerialEvent`s, the matching `@MasterSerialEventService` handlers run, and registered listeners receive
`ModificationEvent`s for the names they care about — so a change committed by one user can refresh the screens of
another.

---

## Extending the Module

- **Custom server-to-client events:** implement `MasterSerialEvent` and a `Consumer` handler annotated with
  `@MasterSerialEventService(YourEvent.class)`. The server stays oblivious to handlers; clients bind their own.
- **Custom session info:** provide a `SessionInfo`/`SessionInfoFactory` implementation via the SPI to carry extra
  per-session attributes between client and server.
- **Custom authentication:** register an `AuthenticationProvider` to verify a new kind of credentials on the server,
  and a `CredentialsProvider` to obtain them on the client — see [Authentication](authentication.md).
- **Reacting to session lifecycle:** register a `SessionCloseHandler` (per session, or globally via the
  `SessionFactory`) — but keep it non-blocking, as it runs during close.

---

## Module Dependencies

`tentackle-session` sits low in the stack and is intentionally thin:

- It **requires transitive** [`tentackle-core`](../../../../tentackle-core/src/main/docs/core.md) (and through it `tentackle-common`):
  TRIP serialization, tasks/dispatchers, validation, binding, reflection and service discovery.
- It **opens** `org.tentackle.session` to `org.tentackle.core` for TRIP, because `SessionInfo`, modification and
  master-serial events travel between JVMs.
- It **provides** a `ModuleHook` service (`org.tentackle.session.service.Hook`) for i18n bundle resolution.
- It depends on **neither** `tentackle-sql` nor `tentackle-database`; those (and the remoting modules) depend on
  *it* and supply the concrete local/remote `Session` implementations.

## Related Documentation

- [Backend Properties](backend-properties.md) — the reference of all session configuration keys (`url`, credentials, timeouts, server endpoints, …).
- [Authentication](authentication.md) — the credentials a `SessionInfo` carries and the provider chain verifying them at login.
- [Modification Tracking](modification-tracking.md) — the `ModificationTracker`, its listeners and the master serial events, in depth.
- [Tentackle SQL](../../../../tentackle-sql/src/main/docs/sql.md) — the database backend layer beneath the local session implementation.
- [PDO / Operations](../../../../tentackle-pdo/src/main/docs/pdo.md) — the persistent domain objects that run on top of sessions.
- [Services / ServiceFinder](../../../../tentackle-common/src/main/docs/services.md) — the SPI mechanism behind the session, info, and tracker factories.
- [TRIP](../../../../tentackle-core/src/main/docs/trip.md) — the remoting protocol that carries a remote session's calls and its `SessionInfo` between JVMs.
- [Locking](../../../../tentackle-persistence/src/main/docs/locking.md) — token locks, whose `lockLingerEnabled` flag is carried on the `SessionInfo`.
- [Database](../../../../tentackle-database/src/main/docs/database.md) — the module providing the concrete local `Session` implementation.
- [Exceptions and Error Handling](../../../../src/main/docs/exceptions.md) — `PersistenceException` and the session it points at, lazy messages, and the `SessionUtilities` alignment hooks.
