# Exceptions and Error Handling

## Overview and Motivation

[Correctness First](./correctness-first.md) states the rule: *fail loudly and early rather than
corrupt quietly*. An exception is how the framework says "loudly". Tentackle therefore throws more
exceptions than a mainstream Java stack — a stale write, an aggregate persisted through the wrong
door, a mutation of an immutable object, a component read outside its root context are all errors,
not silent no-ops.

That only works if exceptions carry enough information to act on. A `SQLException` that reaches a
client three tiers away is useless if the driver class is not on that client's class path, if the
message no longer says which object failed, or if the caller cannot tell "retry this" from "this
will never work". This document describes the machinery that solves those problems and the
conventions the framework follows.

### Design principles

- **Unchecked, with two exceptions.** Nearly every exception in the framework is a
  `RuntimeException`. Remote interfaces are plain Java interfaces with no `throws RemoteException`
  ([TRIP](../../../tentackle-core/src/main/docs/trip.md)), and a PDO method signature does not
  change when its implementation starts touching the database. The only two checked exceptions are
  build-time or veto concerns (see [below](#the-two-checked-exceptions)).
- **One root, one classifier.** Everything the framework throws at runtime derives from
  [`TentackleRuntimeException`](../../../tentackle-common/src/main/java/org/tentackle/common/TentackleRuntimeException.java),
  which carries a single, protocol-level classification: is the cause **temporary**?
- **An exception knows its context.** A
  [`PersistenceException`](../../../tentackle-session/src/main/java/org/tentackle/session/PersistenceException.java)
  remembers the [session](../../../tentackle-session/src/main/docs/session.md) and the object it
  belongs to, and folds them into its message.
- **An exception must survive the wire.** Causes are rewritten to types that exist everywhere,
  messages are evaluated before the transaction that could explain them is gone, and stack traces
  are stripped of proxy noise.
- **The chain is the API.** Callers rarely catch a single type; they *search* a chain with
  [`ExceptionHelper`](../../../tentackle-common/src/main/java/org/tentackle/common/ExceptionHelper.java).

---

## The Hierarchy

```
RuntimeException
└── TentackleRuntimeException                 org.tentackle.common      + isTemporary()
    ├── PersistenceException                  org.tentackle.session     + session, identifiable, lazy message
    │   ├── ConstraintException                                         database constraint violated
    │   ├── NotFoundException                                           expected row is not there (+ persistedSerial)
    │   ├── NotRemovableException                                       delete refused
    │   ├── SessionClosedException                                      session already closed
    │   │   └── RemoteSessionClosedException  org.tentackle.dbms.trip
    │   ├── LoginFailedException                                        (Loggable)
    │   │   ├── AlreadyLoggedInException
    │   │   ├── AuthenticationException       org.tentackle.session.auth
    │   │   │   └── CredentialsExpiredException
    │   │   └── VersionIncompatibleException                            + clientVersion, serverVersion
    │   ├── SecurityException                 org.tentackle.security    (Loggable)
    │   ├── IdSourceException                 org.tentackle.dbms
    │   │   └── IdSourceEmptyException
    │   ├── StatementTraceException           org.tentackle.dbms        not an error — an SQL trace marker
    │   ├── LockException                     org.tentackle.pdo         (Loggable), temporary by default
    │   └── PdoRuntimeException               org.tentackle.pdo         unspecified PDO problem
    │       └── PdoCacheException
    ├── DomainException                       org.tentackle.pdo         domain-logic failure
    ├── NotInvokedInBuddyDelegateException    org.tentackle.pdo
    ├── NumberSourceException                 org.tentackle.ns
    │   └── NumberSourceEmptyException
    ├── ImmutableException                    org.tentackle.misc        write to a read-only object
    ├── ReconnectedException                  org.tentackle.io          resource came back — retry
    ├── TaskException                         org.tentackle.task
    ├── TripRuntimeException                  org.tentackle.trip
    │   ├── TripInstantiationException
    │   ├── TripNoSuchDelegateException
    │   └── TripStreamClosedException
    └── FxRuntimeException                    org.tentackle.fx
        └── RdcRuntimeException               org.tentackle.fx.rdc
```

A few types deliberately stand *outside* the `TentackleRuntimeException` tree, because they are not
about a running application tier and must not be mistaken for one:

| Exception                    | Package                  | Why it is outside                                                                           |
|------------------------------|--------------------------|---------------------------------------------------------------------------------------------|
| `BackendException`           | `org.tentackle.sql`      | The [SQL backend layer](../../../tentackle-sql/src/main/docs/sql.md) is used at build time too. |
| `ScriptRunnerException`      | `org.tentackle.sql`      | Subclass of `BackendException`, thrown by the DDL/script runner.                             |
| `BindingException`           | `org.tentackle.bind`     | [Binding](../../../tentackle-core/src/main/docs/binding.md) is a standalone concern.         |
| `ScriptRuntimeException`     | `org.tentackle.script`   | [Scripting](../../../tentackle-core/src/main/docs/scripting.md) is a standalone concern.     |
| `ValidationRuntimeException` | `org.tentackle.validate` | A *validator* is illegal for the annotated element — a programming error, not a failed check. |
| `ValidationFailedException`  | `org.tentackle.validate` | Carries `ValidationResult`s; see [Validation](../../../tentackle-core/src/main/docs/validation.md). |
| `InterruptedRuntimeException`| `org.tentackle.common`   | A wrapped `InterruptedException`; see [Interrupts](#interrupts).                             |

> **`ValidationFailedException` is not the normal path.** Validation ordinarily *returns*
> `ValidationResult`s so a UI can show all of them at once. The exception exists to (a) hold the
> final results where a method cannot return them and (b) carry the partial results of a component
> when a `@Fail` validator aborted the run. See
> [Validation](../../../tentackle-core/src/main/docs/validation.md).

---

## `TentackleRuntimeException` — the Root

The root adds exactly one thing to `RuntimeException`: a boolean `temporary` flag with
`isTemporary()` / `setTemporary(boolean)`.

### The `temporary` flag

*Temporary* means: **the cause may disappear by itself, so repeating the work may succeed.** It is
the framework's single, transport-independent answer to "is this worth retrying?", and it is what
[transparent transaction retry](../../../tentackle-pdo/src/main/docs/transaction.md#what-is-retried--temporary-exceptions)
keys on. It is set:

- by the backend, for transient JDBC failures — deadlock victim, serialization failure — via
  `Backend.isTransientTransactionException(SQLException)` (see [below](#aligning-the-temporary-status));
- by [`LockException`](../../../tentackle-pdo/src/main/java/org/tentackle/pdo/LockException.java),
  which marks itself temporary by default: a [token lock](../../../tentackle-persistence/src/main/docs/locking.md)
  held by someone else expires on its own;
- by the pooling and connection-management code when a resource is momentarily unavailable
  (`AbstractPool`, `MultiUserDbPool`, `DefaultConnectionManager`, `MpxConnectionManager`);
- by application code that knows better: `setTemporary(true)` on any `TentackleRuntimeException`
  makes an enclosing `@Transaction(retry = true)` retry the unit of work.

The flag also drives **rollback logging**. `SessionUtilities.isSilentRollbackSufficient(Throwable)`
returns `true` when the chain's root `PersistenceException` is temporary (or any temporary exception
is in the chain), so an optimistic-lock conflict that is about to be retried rolls back silently,
while a constraint violation rolls back with the offending statements logged. See
[Transactions](../../../tentackle-pdo/src/main/docs/transaction.md#rollback-logging-and-rollbacksilently).

Because the flag is a plain field, it is *serialized*: a
[`TripComponentProvider`](../../../tentackle-core/src/main/java/org/tentackle/trip/provider/TentackleRuntimeExceptionComponentProvider.java)
transmits it explicitly (via `isTemporary`/`setTemporary`, since the field itself is private), so a
client three tiers from the database sees the same classification the database-connected server
made.

---

## `PersistenceException` — the Session-Aware Exception

Everything that can go wrong while talking to a backend is a `PersistenceException` or a subclass of
it. Beyond the `temporary` flag it carries two references:

| Property                  | Meaning                                                                                                    |
|---------------------------|------------------------------------------------------------------------------------------------------------|
| `getSession()`            | The [session](../../../tentackle-session/src/main/docs/session.md) the failure happened on. `transient` — it does not travel over the wire. |
| `getIdentifiable()`       | The object the failure belongs to, as an `Identifiable`. Usually the PDO or its persistence delegate.       |

The constructors take either of them; passing an `Identifiable` that is also a `SessionProvider`
sets the session too. `updateDbObject(Identifiable)` fills the object in later, for code that throws
where the PDO is not yet known.

### Lazy messages

`getMessage()` does not return the raw detail message. On first call it delegates to
`SessionUtilities.createLazyExceptionMessage(...)`, which appends the identifiable
(`toGenericString()` — never `toString()`, which may itself fail) and the session, and, in the
[database implementation](../../../tentackle-database/src/main/java/org/tentackle/dbms/DbSessionUtilities.java),
the SQL message, error code and SQL state of an underlying `SQLException`:

```
duplicate key
Object:      Customer[id=4711]
Session:     Db[42,pgsql@dbhost/erp]
SQL-Message: ERROR: duplicate key value violates unique constraint "customer_ukey"
SQL-Code:    0
SQL-State:   23505
```

The result is cached, which is the point of `evaluate()`:

```java
catch (PersistenceException pex) {
  throw pex.evaluate();   // build the message NOW, while the session still knows the answer
}
```

Call it whenever the exception will be logged *later* — after the transaction has been rolled back,
or on the far side of a remote call — because by then the session may no longer be able to describe
itself.

`PersistenceException.extractPersistenceException(Throwable)` walks a cause chain and returns the
first `PersistenceException` in it; `DomainException.extractDomainException(Throwable)` does the same
for the domain side.

### Driver-specific exceptions

A JDBC driver's own exception class (`org.postgresql.util.PSQLException`, `oracle.jdbc.OracleDatabaseException`, …)
is on the *server's* class path, not on a remote client's. Deserializing it there would fail — and the client would
see a class-loading error instead of the real problem.

TRIP therefore never sends a driver's class (see
[JDK exceptions](../../../tentackle-core/src/main/docs/trip.md#jdk-exceptions)). A driver-specific `SQLException` is
sent as its nearest JDK superclass, with the driver's class name appended to the message. It keeps its SQL state,
error code, next exception and stack trace. A driver-specific cause that is not an `SQLException`, such as
`OracleDatabaseException`, is replaced by a plain `Exception` carrying the original class name, message and stack
trace. Locally, the exception stays as thrown by the driver, so the backend can still classify it (see below).

### Aligning the temporary status

`SessionUtilities.alignTemporaryExceptionStatus(PersistenceException)` is the matching hook for the
retry classification. `DbSessionUtilities` asks the
[`Backend`](../../../tentackle-sql/src/main/docs/sql.md) whether the wrapped `SQLException` is a
transient transaction failure and sets the flag accordingly — the one place where backend-specific
SQL-state knowledge enters the generic layers.

### The specific persistence exceptions

| Exception                      | Thrown when                                                                                                       |
|--------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `ConstraintException`          | A database constraint was violated (unique, foreign key, check).                                                    |
| `NotFoundException`            | A row that must exist is not there. `getPersistedSerial()` returns the object's serial in the database, `-1` if the row is gone, `0` if unknown — this is what turns a zero-row `UPDATE` into a precise [optimistic-lock](../../../tentackle-persistence/src/main/docs/locking.md) diagnosis. |
| `NotRemovableException`        | A `delete()` was refused, e.g. because the object is still referenced.                                             |
| `SessionClosedException`       | The session is already closed. `RemoteSessionClosedException` is its remote variant.                               |
| `LoginFailedException`         | Authentication or connection setup failed — see [Authentication](../../../tentackle-session/src/main/docs/authentication.md). |
| `VersionIncompatibleException` | Client and server versions do not match; carries both versions, so the client can name them. Thrown at *connect* time, never mid-stream. |
| `SecurityException`            | A [permission](../../../tentackle-pdo/src/main/docs/security.md) was denied.                                        |
| `LockException`                | A [token lock](../../../tentackle-persistence/src/main/docs/locking.md) is held by someone else; carries the `TokenLockInfo` (`editedBy`, `editedSince`, `editedExpiry`) and the context name. Temporary by default. |
| `IdSourceException`            | The technical [id source](../../../tentackle-database/src/main/docs/database.md) failed; `IdSourceEmptyException` if it ran dry. |
| `PdoRuntimeException`          | An unspecified PDO-level problem — a missing session, an unexpected session on a component.                        |
| `PdoCacheException`            | The [PDO cache](../../../tentackle-pdo/src/main/docs/cache.md) was misused or is inconsistent.                     |

`StatementTraceException` is the odd one out: **it is not an error.** It is constructed purely to
capture a stack trace for SQL statement tracing, so a slow or unexpected statement can be traced back
to the code that issued it (see [Tentackle Database](../../../tentackle-database/src/main/docs/database.md)).

---

## `DomainException` — the Domain-Logic Failure

`DomainException` is the domain-side counterpart of `PersistenceException`: it extends
`TentackleRuntimeException` directly and carries no session semantics. The framework throws it when
domain-layer machinery is missing or misused — a
[domain key](../../../tentackle-pdo/src/main/docs/domain-key.md) method that was not generated, a
root-entity operation on a component. Application domain logic should use it (or a subclass) for
business-rule failures that are not validation results.

The distinction matters when catching: `PersistenceException` means *the backend said no*,
`DomainException` means *the domain said no*. Both may sit in the same chain.

---

## `ExceptionHelper` — Working With Chains

Because framework code wraps causes as they cross layers, catching one type is rarely enough.
[`ExceptionHelper`](../../../tentackle-common/src/main/java/org/tentackle/common/ExceptionHelper.java)
is the toolbox for the chain:

| Method                                                | Purpose                                                                                    |
|-------------------------------------------------------|----------------------------------------------------------------------------------------------|
| `extractException(Class<T>, boolean first, Throwable)` | The first or last exception of a type in the chain, `null` if none.                          |
| `extractException(boolean first, Throwable, Class…)`   | The same for several candidate types at once.                                                |
| `extractTemporaryException(boolean first, Throwable)`  | The first/last `TentackleRuntimeException` flagged [temporary](#the-temporary-flag).          |
| `getMessage(Throwable)`                                | The first non-empty message in the chain — skips wrappers that add nothing.                  |
| `concatenateMessages(Throwable)`                       | All messages of the chain, joined — what a user-facing dialog should show.                   |
| `handleException(boolean first, Throwable, Handler…)`  | Type-dispatch over a chain: the first matching `Handler<T>` consumes it. Returns `false` if nothing matched. |
| `getStackTraceAsString(Throwable)`                     | The trace as a printable string.                                                             |
| `filterStackTrace(StackTraceElement[])`                | The trace with the noise removed (see below).                                                |

`handleException` replaces the "catch, `instanceof`-cascade, rethrow" pattern:

```java
boolean handled = ExceptionHelper.handleException(true, e,
    new ExceptionHelper.Handler<>(LockException.class,     lx -> showLockOwner(lx.getTokenLockInfo())),
    new ExceptionHelper.Handler<>(SecurityException.class, sx -> showDenied(sx)),
    new ExceptionHelper.Handler<>(ConstraintException.class, cx -> showDuplicate(cx)));
if (!handled) {
  throw e;
}
```

### Filtered stack traces

A PDO call goes through a dynamic proxy, an invocation handler and an interceptor chain before it
reaches your code. Left alone, half of every stack trace is that machinery.
`isClassValuableForStackTrace(String)` classifies a frame as noise when it is a JDK frame
(`java*`, `sun.*`, `com.sun.*`, `jdk.*`) or a Tentackle frame whose class name contains
`InvocationHandler` or `Interceptable`; `filterStackTrace` drops those, but always keeps the
*leading* frames up to the first valuable one, so the throw site itself is never lost. Remote
invocation applies it to every exception it sends back to a client.

---

## `Loggable`: How Much of an Exception to Log

Some exceptions are meant *for the client* and are perfectly normal on the server: a failed login, a
denied permission, a lock held by another user. Logging them at `SEVERE` with a full stack trace
would drown the server log in noise that is not the server's problem.

[`Loggable`](../../../tentackle-core/src/main/java/org/tentackle/log/Loggable.java) lets an exception
say how it wants to be logged:

| Method             | Default | Meaning                                    |
|--------------------|---------|---------------------------------------------|
| `getLogLevel()`    | `INFO`  | The level; `null` means *do not log at all*. |
| `withStacktrace()` | `true`  | Whether the trace is worth printing.        |

`LockException`, `SecurityException` and `LoginFailedException` implement it. The remote invocation
handler
([`RemoteDbDelegateInvocationHandler`](../../../tentackle-database/src/main/java/org/tentackle/dbms/trip/RemoteDbDelegateInvocationHandler.java))
consults it before logging a failed delegate call server-side, falling back to `INFO` for anything
else — and raising to `WARNING` when the session actually crashed.

---

## Exceptions Across Tiers

An exception thrown on a database-connected server has to arrive intact at a client that may be
several [tiers](./tiers.md) away. [TRIP](../../../tentackle-core/src/main/docs/trip.md) handles
`Throwable` with a dedicated component provider
([`ThrowableComponentProvider`](../../../tentackle-core/src/main/java/org/tentackle/trip/provider/ThrowableComponentProvider.java)),
which transmits the message, the cause and the stack trace through their accessors rather than the
JDK's private fields, plus any non-`transient`, non-`@TripIgnore` field the subclass adds. The
`TentackleRuntimeException` subclass adds the [`temporary`](#the-temporary-flag) flag.

On the way out, the remote invocation handler:

1. unwraps the `InvocationTargetException`;
2. logs the failure server-side according to [`Loggable`](#loggable-how-much-of-an-exception-to-log);
3. replaces the stack trace with its [filtered](#filtered-stack-traces) form;
4. returns the cause unchanged if it is a `RuntimeException`, and wraps anything else in a
   `PersistenceException`.

The client therefore rethrows *the same exception type* the server threw — `LockException` stays a
`LockException`, complete with its `TokenLockInfo`. This is what makes
[transparent retry](../../../tentackle-pdo/src/main/docs/transaction.md) work identically at every
tier.

Three consequences are worth remembering:

- **The session does not travel.** `PersistenceException.getSession()` is `transient` and is `null`
  on the client. Call [`evaluate()`](#lazy-messages) on the server if the message must mention it.
- **Driver classes are not sent**, so a client never needs the JDBC driver on its class path
  (see [Driver-specific exceptions](#driver-specific-exceptions)).
- **Every exception type crossing the wire must be reachable by TRIP** — the same
  `opens`/registration rules as any other transmitted type apply.
- **The JDK's exceptions** travel via their getters and public constructors, since their packages
  are not open. Common ones arrive with all their values, any other with at least its message, cause
  and stack trace — see [JDK exceptions](../../../tentackle-core/src/main/docs/trip.md#jdk-exceptions).

---

## Interrupts

`InterruptedException` is checked, which makes it awkward inside interceptor chains and lambdas.
[`InterruptedRuntimeException`](../../../tentackle-common/src/main/java/org/tentackle/common/InterruptedRuntimeException.java)
wraps it — and, crucially, **re-sets the thread's interrupt flag in its constructor**, so wrapping
never swallows the interrupt. Catch it where you would have caught `InterruptedException`; the flag
is already restored, so returning from the task is enough.

It deliberately extends `RuntimeException` and not `TentackleRuntimeException`: an interrupt is a
shutdown signal, not a temporary backend failure, and must never be mistaken for something a
`@Transaction(retry = true)` should retry.

---

## The Two Checked Exceptions

| Exception              | Module                                                                 | Why it is checked                                                                                                                                |
|------------------------|------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `ModelException`       | [tentackle-model](../../../tentackle-model/src/main/docs/tentackle-model.md) | A malformed [model definition](../../../tentackle-model/src/main/docs/model-definition.md) is a *build* failure. The wurblets and the Maven plugins must handle it and turn it into a readable build error, so the compiler enforces that. |
| `BindingVetoException` | [tentackle-core](../../../tentackle-core/src/main/docs/binding.md)      | A binding listener vetoes a model↔view transfer. Vetoing is an ordinary outcome, not an error, and the binding code must deal with it at the point of transfer. |

Everything else is unchecked, by design.

---

## Where Exceptions End Up

| Layer                                                                            | What happens                                                                                                                                              |
|----------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`@Transaction`](../../../tentackle-pdo/src/main/docs/transaction.md)             | Rolls back; silently for [temporary](#the-temporary-flag) causes, with statement logging otherwise. With `retry = true` a temporary failure re-runs the whole unit of work, and only the last one is rethrown. |
| [Validation](../../../tentackle-core/src/main/docs/validation.md)                 | Returns `ValidationResult`s rather than throwing; `ValidationFailedException` carries them where a return value is impossible.                             |
| Remote server                                                                     | Logs per [`Loggable`](#loggable-how-much-of-an-exception-to-log), filters the trace, and sends the exception back — see [Exceptions Across Tiers](#exceptions-across-tiers). |
| `AbstractApplication`                                                             | Installs a default uncaught-exception handler that logs at `SEVERE`. See [Application Bootstrap](../../../tentackle-pdo/src/main/docs/application.md).      |
| [`DesktopApplication`](../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/app/DesktopApplication.java) | Overrides it for *all* threads and the FX thread: logs, then shows `Fx.error(...)`. If even that fails, the application terminates with exit code 99 rather than continuing in an unknown state. |
| [InteractiveError](../../../tentackle-fx/src/main/docs/interactive-error.md)      | Turns a `ValidationResult` — including one produced on a server — back into a marked control on screen.                                                    |
| [`Rdc.bg(...)`](../../../tentackle-fx-rdc/src/main/docs/background-execution.md)  | Captures the runner's `RuntimeException` and hands it to the `failedUI` consumer on the FX thread (logged as `SEVERE` if there is none). Non-`RuntimeException`s are wrapped in a `TentackleRuntimeException` — which is why background runners should throw runtime exceptions. |
| [Tasks and daemons](../../../tentackle-core/src/main/docs/tasks-daemon.md)        | `TaskException` for dispatcher problems; the [modification tracker](../../../tentackle-session/src/main/docs/modification-tracking.md) thread is non-daemon on purpose — if it dies, the application dies with it. |

---

## Writing Your Own — Checklist

1. **Extend the closest framework type.** Backend-related → `PersistenceException` (so it gets the
   session, the lazy message and the cause alignment). Domain-related → `DomainException`. UI →
   `FxRuntimeException`/`RdcRuntimeException`. Anything else in an application module →
   `TentackleRuntimeException`.
2. **Set `temporary` only if a retry can genuinely succeed.** Nothing hurts more than an
   infinitely retried permanent failure.
3. **Implement `Loggable`** if the exception is routine and meant for the caller — a denied
   permission, an expected conflict — so servers do not log it as a defect.
4. **Keep fields serializable and non-`transient`** if the client needs them; TRIP transmits
   subclass fields automatically. Mark what must not travel `transient` or `@TripIgnore`.
5. **Call `evaluate()`** before rethrowing a `PersistenceException` out of a transaction whose
   message you want to keep.
6. **Do not catch and discard.** If you catch to add context, keep the original as the cause — the
   chain is what `ExceptionHelper` searches.

---

## Related Documentation

- [Correctness First](./correctness-first.md) — why the framework prefers an exception to a quiet wrong answer.
- [Transactions and Transparent Retry](../../../tentackle-pdo/src/main/docs/transaction.md) — what the `temporary` flag actually triggers.
- [Locking](../../../tentackle-persistence/src/main/docs/locking.md) — `LockException`, `NotFoundException` and the optimistic `serial`.
- [Validation](../../../tentackle-core/src/main/docs/validation.md) — results, not exceptions, and when that rule bends.
- [TRIP](../../../tentackle-core/src/main/docs/trip.md) — how a `Throwable` is put on the wire.
- [Tentackle Session](../../../tentackle-session/src/main/docs/session.md) — the session a `PersistenceException` points at.
- [Tentackle Logging](../../../tentackle-core/src/main/docs/logging.md) — levels, the MDC, and where the logged exception goes.
- [InteractiveError](../../../tentackle-fx/src/main/docs/interactive-error.md) — turning failures into something the user can act on.
