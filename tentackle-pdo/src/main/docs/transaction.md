# Transactions — `@Transaction`, Rollback, and Transparent Retry

## Overview and Motivation

Every unit of work that modifies the database must be atomic: it either commits as a whole or
leaves no trace. Tentackle expresses this declaratively — a domain or persistence method is
annotated with [`@Transaction`](../java/org/tentackle/pdo/Transaction.java), and the
[interceptor](../../../../tentackle-core/src/main/docs/interceptors.md) behind it wraps the
invocation in begin/commit, rolls back on any exception, and can transparently **retry** the whole
unit when the failure was temporary — an optimistic-lock conflict, a deadlock, a serialization
failure.

```java
@Transaction
@Override
public void invoiceShipment(Shipment shipment) {
  ...   // everything in here commits or rolls back as one unit
}
```

Because `@Transaction` is a PUBLIC [interception](../../../../tentackle-core/src/main/docs/interceptors.md),
it sits on the **declared interface method** — the transactional contract is part of the API, not
an implementation detail. And because the interceptor is *remote-aware*, the same annotated method
runs correctly in every [tier](../../../../src/main/docs/tiers.md): with a remote session the
transaction executes on the server (see [below](#remote-sessions)).

## Basic Behavior

For a method annotated with plain `@Transaction`, the interceptor:

1. **begins** a transaction on the session — unless one is already running (see
   [nesting](#nesting-inner-methods-join-the-outer-transaction));
2. **invokes** the method;
3. **commits** on normal return, or **rolls back** on any exception, which is then rethrown.

The transaction gets a **name** and is visible in logs.
By default, it is derived from the method: `DeclaringClass#methodName`. Set it explicitly via the
`value` attribute when several methods form one logical unit or when a
[retry policy](#custom-retry-policies) should treat certain transactions specially:

```java
@Transaction("invoice shipment")
```

### Nesting: inner methods join the outer transaction

`Session.begin(...)` does nothing when a transaction is already running — it hands out a
*voucher* of `0` instead of a real one. An inner `@Transaction` method therefore simply **joins**
the surrounding transaction: it neither commits nor rolls back by itself, and never
[retries](#transparent-retry). Only the outermost method — the holder of the real voucher — ends
the transaction. Composing transactional methods into larger transactional methods is thus safe
and requires no ceremony.

Two consistency rules apply at the join point:

- If the inner annotation requests an `isolation` or `writability` that differs from what the
  running transaction was started with, a `PersistenceException` is thrown — the inner method's
  requirements cannot be silently ignored.
- An inner annotation with `running = true` additionally asserts its expectation (see
  [below](#asserting-an-open-transaction-with-running)).

## The Attributes

| Attribute          | Default            | Meaning                                                                                                     |
|--------------------|--------------------|-------------------------------------------------------------------------------------------------------------|
| `value`            | derived            | The transaction name; defaults to `DeclaringClass#methodName`.                                              |
| `running`          | `false`            | Assert that a transaction is *already* running instead of beginning one.                                    |
| `rollbackSilently` | `false`            | Never log rollback details for this method.                                                                 |
| `isolation`        | `DEFAULT`          | Transaction isolation: `READ_UNCOMMITTED`, `READ_COMMITTED`, `REPEATABLE_READ`, `SERIALIZABLE`, `NONE`.     |
| `writability`      | `DEFAULT`          | `READ_WRITE` or `READ_ONLY` (a hint the database may use to optimize).                                      |
| `deferred`         | `false`            | Defer deferrable constraint checks to commit time (see [below](#deferring-constraint-checks-with-deferred)).|
| `retry`            | `false`            | Retry the transaction after a *temporary* failure.                                                          |
| `retryPolicy`      | `""` (the default) | Name of the [`TransactionRetryPolicy`](../java/org/tentackle/pdo/TransactionRetryPolicy.java) to use.       |
| `retryLogLevel`    | `WARNING`          | Level at which each retry is logged.                                                                        |

`DEFAULT` for `isolation` and `writability` means: whatever the session (and ultimately the
database backend) is configured with — most applications never need to override either.
`retryPolicy` is ignored unless `retry = true`; an unknown policy name fails with a
`PersistenceException` before the transaction even begins.

### Asserting an open transaction with `running`

Some methods are only meaningful *as part of* a larger unit — a step in a workflow that must never
commit on its own. Annotating them with

```java
@Transaction(running = true)
```

turns the interceptor into an assertion: a `PersistenceException` is thrown if **no** transaction
is running. If both the annotation and the running transaction are named, the names must match —
guarding against the method being invoked from the wrong workflow.

### Rollback logging and `rollbackSilently`

Not every rollback is worth a stack trace. The interceptor distinguishes:

- **Silent rollback** — for *temporary* exceptions (an optimistic-lock conflict about to be
  retried) and application-level domain exceptions: the rollback happens without detailed logging,
  because the exception is either handled upstream or part of normal business flow.
- **Logged rollback** — for non-temporary persistence errors (e.g. a constraint violation): the
  details go to the log.

`rollbackSilently = true` forces the silent variant for methods where rollbacks are expected
behavior and would only clutter the log.

### Deferring constraint checks with `deferred`

Some databases (Postgres, Oracle) can postpone checking `DEFERRABLE` unique- and foreign-key
constraints to commit time via `SET CONSTRAINTS ... DEFERRED`, instead of after every statement.
This is useful for operations that temporarily violate a constraint mid-transaction — swapping two
rows' unique values, or reordering a self-referencing hierarchy — without having to restructure
the transaction around the violation.

```java
@Transaction(deferred = true)
```

calls `Session.setConstraintsDeferred(true)` right after the transaction actually starts (not when
joining an already-running one), for the rest of that transaction. It only has an effect on
constraints declared `deferred` or `deferrable` in the model — see
[Index Lines](../../../../tentackle-model/src/main/docs/model-definition.md#7-index-lines).

### Remote sessions

When the session is remote and the implementation is a
[`@RemoteMethod`](../../../../tentackle-common/src/main/docs/common.md), the interceptor does
**not** begin a transaction locally: it forwards the invocation, and the server's own
`TransactionInterceptor` runs begin, method and commit *on the server* — saving the two network
round trips for begin and commit, and keeping the transaction (including any
[retry](#transparent-retry)) close to the database, where it belongs.

### Global transactions

With the optional module `tentackle-jta`, a session can take part in a global (JTA/XA) transaction
that spans several databases or other XA resources. `@Transaction` works unchanged: a session
configured to *join* enlists in the global transaction of the current thread and behaves like a
nested transaction, while a session configured as *required* starts the global transaction in its
outermost `@Transaction` method and completes it via two-phase commit — including the retry.
Isolation and writability cannot be changed within a global transaction. See
[Global Transactions — JTA and XA](../../../../tentackle-jta/src/main/docs/jta.md).

### Idle transactions

A transaction must not stay open while the code waits for something outside the database: it holds
a connection and its locks. The database layer watches for this. A local transaction that performs
no database I/O for a minute (by default) is rolled back and its connection closed by the
[idle transaction supervisor](../../../../tentackle-database/src/main/docs/database.md#idle-transaction-supervisor),
configurable via the `txIdleTimeout` and `txIdleInterval` backend properties.

## Transparent Retry

### Why retry belongs to the framework

Tentackle's [optimistic locking](../../../../tentackle-persistence/src/main/docs/locking.md) turns
a lost update into an exception at `UPDATE` time, and databases running at higher isolation levels
report deadlocks and serialization failures the same way: the transaction fails, but **retrying it
from the start would very likely succeed**. Scattering hand-written retry loops across the domain
logic is error-prone — so the `@Transaction` interceptor offers the loop as a one-word feature:

```java
@Transaction(retry = true)
@Override
public void transferStock(StorageBin from, StorageBin to, int amount) {
  ...
}
```

### What is retried — temporary exceptions

A failed transaction is re-attempted only when the exception chain contains a
`TentackleRuntimeException` flagged **temporary** — the framework's marker for "the cause may
disappear if you try again". The flag is set by:

- the **optimistic `serial` check**: the row was changed under the method by a concurrent,
  committed transaction (see [locking](../../../../tentackle-persistence/src/main/docs/locking.md));
- the **backend's error classification**: `SQLException`s the
  [backend](../../../../tentackle-sql/src/main/docs/sql.md) recognizes as transient — deadlock
  victim, serialization failure — are wrapped in a temporary `PersistenceException`;
- the **token lock**: a [`LockException`](../java/org/tentackle/pdo/LockException.java) because
  another user (or another named context) holds the token lock is temporary as well;
- any application code that throws a `TentackleRuntimeException` with `setTemporary(true)`.

Everything else — constraint violations, domain exceptions, programming errors — fails
immediately, exactly as without `retry`.

Only the **outermost** transaction retries. An inner `@Transaction(retry = true)` method that
joined a surrounding transaction rethrows instead: the unit of work that must be re-executed is
the whole transaction, and only its owner can do that.

### The retry loop and the default policy

On a temporary failure the interceptor rolls back (silently), asks the
[`TransactionRetryPolicy`](../java/org/tentackle/pdo/TransactionRetryPolicy.java) how long to
wait, sleeps, and re-invokes the method — beginning a fresh transaction. The policy's contract is
a single method:

```java
long waitMillis(String txName, int count);   // count starts at 1; return 0 to give up
```

Each retry is logged at `retryLogLevel` (default `WARNING`), so contention becomes visible in the
logs without failing the operation.

The [`DefaultTransactionRetryPolicy`](../java/org/tentackle/pdo/DefaultTransactionRetryPolicy.java)
implements randomized exponential backoff: up to **7 retries**, waiting a random 250–750 ms
doubled on each attempt — roughly 0.5 s, 1 s, 2 s, 4 s, 8 s, 16 s and 32 s. The randomization
de-synchronizes competing transactions that failed on the same row; the growing intervals give
longer outages (a failing-over database) a chance to clear. After the 7th unsuccessful retry the
temporary exception is rethrown.

### Custom retry policies

A policy is a [mapped service](../../../../tentackle-common/src/main/docs/services.md): implement
the interface and annotate it with
[`@TransactionRetryPolicyService`](../java/org/tentackle/pdo/TransactionRetryPolicyService.java):

```java
@TransactionRetryPolicyService("aggressive")
public class AggressiveRetryPolicy implements TransactionRetryPolicy {
  @Override
  public long waitMillis(String txName, int count) {
    return count > 20 ? 0 : 100;    // 20 fast retries, then give up
  }
}
```

Select it per method via the annotation:

```java
@Transaction(retry = true, retryPolicy = "aggressive")
```

Registering a policy under the **empty name** (`@TransactionRetryPolicyService("")`) replaces the
default policy application-wide. Referencing a name that no policy is registered under fails with
a `PersistenceException` on first use — a misspelled policy name cannot silently disable backoff.

### Writing retryable methods

Retry re-executes the **method body**, not just the database statements. That is safe as long as
everything the method does is transactional — reads, modifications, and PDO state changes all roll
back with the transaction. Two things to watch:

- **No external side effects.** A method that sends an e-mail, writes a file, or mutates shared
  in-memory state before the commit will repeat those effects on every retry. Move such work after
  the transaction — for example into a [`@PostCommit`](../java/org/tentackle/pdo/PostCommit.java)
  method, which runs only once, after the commit actually succeeded.
- **Re-read what you check.** The retried invocation must reload the PDOs it operates on (or
  receive them fresh), so the second attempt sees the concurrent modification instead of colliding
  with it again. Methods that select their working set inside the transaction get this for free.

## Related Documentation

- [Global Transactions — JTA and XA](../../../../tentackle-jta/src/main/docs/jta.md) — transactions
  spanning several databases or other XA resources.
- [Locking](../../../../tentackle-persistence/src/main/docs/locking.md) — the optimistic `serial`
  check whose temporary `NotFoundException` powers the retry, and token locks for long
  interactions.
- [Exceptions and Error Handling](../../../../src/main/docs/exceptions.md) — where the `temporary`
  flag comes from, how it survives a remote call, and what a rollback actually logs.
- [Interceptors](../../../../tentackle-core/src/main/docs/interceptors.md) — the interception
  framework `@Transaction` is built on, and the other built-in interceptors
  (`@PreCommit`/`@PostCommit`, `@NoTransaction`, …).
- [Correctness First](../../../../src/main/docs/correctness-first.md) — why a detected conflict
  plus an automatic retry beats silent overwriting.
- [Session](../../../../tentackle-session/src/main/docs/session.md) — the persistence context that
  owns the physical transaction.
- [Modification Tracking](../../../../tentackle-session/src/main/docs/modification-tracking.md) —
  how committed transactions become change notifications.
