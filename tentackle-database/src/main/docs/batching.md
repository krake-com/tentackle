# SQL Statement Batching

## Overview

Writing a row through JDBC costs one network round trip. Writing ten thousand rows one at a time costs
ten thousand round trips, and on a busy link the wire time dwarfs the time the database spends on the
work itself. JDBC's answer is *batching*: collect several parameter sets for the same prepared statement
and ship them to the backend in a single `executeBatch()` call.

Tentackle offers batching in two flavors:

- **Automatic batching** — the persistence layer transparently collects the generated insert, update and
  delete statements of a running transaction and executes them as JDBC batches. The application code does
  not change at all; a `saveObject()` loop stays a `saveObject()` loop.
- **Manual batching** — the application drives
  [`PreparedStatementWrapper`](../java/org/tentackle/dbms/PreparedStatementWrapper.java) directly
  (`addBatch()` / `executeBatch(...)`) for bulk loads that don't need the object-level machinery.

Automatic batching is **off by default**, and deliberately so — see [When Not to Batch](#when-not-to-batch).

> **Batching is a throughput optimization, not a semantic change.** Whether a statement is batched or not,
> Tentackle still verifies that each statement affected exactly the row it was supposed to affect, so
> [optimistic locking](../../../../tentackle-persistence/src/main/docs/locking.md) keeps working. Batching
> never trades correctness for speed — it only trades *diagnosability* for speed.

---

## Enabling Automatic Batching

Batching is a property of the [session](../../../../tentackle-session/src/main/docs/session.md). The usual
way to turn it on is the `batchsize` property of the session configuration
(see [`Db`](../java/org/tentackle/dbms/Db.java)):

```properties
# collect up to 100 parameter sets per statement before flushing
batchsize=100
```

A value **> 1** enables batching; anything else (or an absent property) disables it. The same value is
readable and settable programmatically at runtime:

```java
Db session = (Db) Pdo.getSession();
session.setBatchSize(100);      // <= 1 disables batching again
```

Because a batch belongs to a transaction, the setting takes effect for transactions started *after* the
change: [`DbTransaction`](../java/org/tentackle/dbms/DbTransaction.java) creates its batch at construction
time and keeps it for its whole lifetime:

```java
protected DbBatch createBatch() {
  int batchSize = session.getBatchSize();
  return batchSize > 1 ? new DbBatch(this, batchSize) : null;
}
```

A `null` batch means "no batching" — the code paths below all degrade to plain single-statement execution.

### Batching only for certain transactions

Since `createBatch()` is a protected method, an application that wants batching for its nightly import but
not for interactive transactions can provide its own
[`SessionFactory`](../../../../tentackle-session/src/main/docs/session.md) creating a subclass of `Db` that
overrides `Db#createTransaction(String, boolean)` to return a `DbTransaction` whose `createBatch()` decides per
transaction name, per user, or on whatever criterion fits:

```java
public class MyTransaction extends DbTransaction {

  public MyTransaction(Db session, long txVoucher, String txName, long txNumber) {
    super(session, txVoucher, txName, txNumber);
  }

  @Override
  protected DbBatch createBatch() {
    return "import".equals(getTxName()) ? super.createBatch() : null;
  }
}
```

---

## How Automatic Batching Works

### The players

| Class | Role |
|-------|------|
| [`DbBatch`](../java/org/tentackle/dbms/DbBatch.java) | The per-transaction batch. Owns the statements collected so far, decides when to flush and in which order. |
| [`DbBatchStatement`](../java/org/tentackle/dbms/DbBatchStatement.java) | One prepared statement plus the list of objects whose parameter sets are pending for it. |
| [`PreparedStatementWrapper`](../java/org/tentackle/dbms/PreparedStatementWrapper.java) | Knows whether it currently participates in a batch, buffers the parameter sets and performs the actual `addBatch()`/`executeBatch()`. |
| [`AbstractPersistentObject`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/AbstractPersistentObject.java) | Registers its insert/update/delete statements as batchable. |

### Registering a statement

The persistence layer does not call `getPreparedStatement(...)` for writes but
`getBatchablePreparedStatement(...)`, which additionally hands the statement, the modification type, the
object and its [`rootClassId`](../../../../tentackle-pdo/src/main/docs/root-columns.md) to the session:

```java
protected PreparedStatementWrapper getBatchablePreparedStatement(ModificationType modType,
                                                                 StatementId stmtId,
                                                                 SqlSupplier sqlSupplier) {
  PreparedStatementWrapper st = getPreparedStatement(stmtId, sqlSupplier);
  getSession().addToBatch(modType, st, this, getDomainContext().getRootClassId());
  return st;
}
```

`Db#addToBatch(...)` is a no-op if the running transaction has no batch, which is why the
`insertImpl`/`updateImpl`/`deleteImpl` methods of `AbstractPersistentObject` look exactly the same in both modes:

```java
@Override
protected void insertImpl(DbObjectClassVariables<P> classVariables, SqlSupplier sqlSupplier) {
  PreparedStatementWrapper st = getBatchablePreparedStatement(DbModificationType.INSERT,
                                                              classVariables.insertStatementId, sqlSupplier);
  setFields(st);
  assertThisRowAffected(st.executeUpdate());
}
```

Statements are grouped in `DbBatch` by their
[`StatementKey`](../java/org/tentackle/dbms/StatementKey.java) — statement ID plus using class — in a
`LinkedHashMap`, so the *order in which statement types were first seen is preserved*. One-shot statements
(such as those built by [`Query`](../java/org/tentackle/dbms/Query.java) from user input) have no key and
cannot be batched; passing one to `DbBatch#add` raises a `PersistenceException`.

**Only statements that affect a single row may be registered as batchable.** That is the contract of
`getBatchablePreparedStatement`, and it is what allows the row-count check described below.

### Deferring the execution

The interesting trick is that `executeUpdate()` on a batched statement does not execute anything:

```java
public int executeUpdate() {
  DbBatch batch = getBatch();
  if (batch != null) {
    batch.batch(this);
    return 1;                       // batched statements always affect a single row
  }
  getSession().executeBatch();      // not a batchable statement -> flush batch
  return super.executeUpdate(null);
}
```

It reports the row count the caller *would* have gotten (1) and defers the real work. The parameter sets
themselves are captured by `PreparedStatementWrapper#effectivePosition`: whenever the first parameter of a
batched statement is set again, the previous set is pushed onto the batch with `addBatch()`. The values
also go into a per-set parameter map, which is what makes batched statements show up in the statement
history with all their parameters.

`DbBatch#batch(...)` then only checks whether the session's batch size has been reached and, if so, runs
everything pending up to and including that statement:

```java
public void batch(PreparedStatementWrapper statement) {
  if (statement.getBatchCount() >= batchSize - 1) {   // -1 due to addBatch in DbBatchStatement#execute
    executePending(false, statement);
  }
}
```

### Flushing

`DbBatch#execute(flush)` walks the collected statements **in registration order** and executes each one.
A statement with more than one pending parameter set goes through `executeBatch()`; a statement with
exactly one set is executed as an ordinary `executeUpdate()` — a batch of one buys nothing.

A flush happens when:

| Trigger | Why |
|---------|-----|
| The batch size is reached for a statement | The point of the exercise. |
| A **different root entity type** is written (`rootClassId` changes) | Execution order across aggregates matters for referential integrity. |
| The **same object** is written again within the transaction | Its second statement must not overtake its first. |
| A **non-batchable** statement is executed (`Query`, plain `Statement`, any `executeUpdate(sql)`) | It might depend on, or be depended upon by, pending writes. |
| A **savepoint** is set (`Db#setSavepoint(...)`) | The savepoint must have the pending work behind it. |
| The transaction **commits** (`DbTransaction#commit()`) | Nothing may be left unwritten. |
| A **global transaction** is about to complete (before the two-phase commit) | Same as commit, see [JTA](../../../../tentackle-jta/src/main/docs/jta.md). |

The first two are handled in `DbBatch#add`:

```java
if (rootClassId != lastRootClassId || !objects.add(object)) {
  execute(true);    // order can change -> flush!
  lastRootClassId = rootClassId;
  objects.add(object);
}
```

Note that a **rollback does not flush** — pending statements are simply discarded along with the
transaction, which is correct: they were never executed.

### Keeping the row-count check

Correctness rests on `DbBatchStatement#execute`, which compares the update counts JDBC returns for the
batch against the objects that produced them:

```java
int[] counts = statement.executeBatch(finish);
if (modType != DbModificationType.INSERT) {  // inserts fail with an exception -> no need to check the count
  int ndx = 0;
  for (int count : counts) {
    if (count != 1) {
      objects.get(ndx).assertThisRowAffected(count);    // -> exception!
    }
    ndx++;
  }
}
```

Because the statements were registered one object at a time and in order, index *n* of the update-count
array belongs to object *n* of the batch. An update or delete that affected no row — the classic symptom of
a stale `serial`, i.e., a lost optimistic-locking race — therefore still raises the very same exception it
would have raised without batching, and it names the very same object. Inserts need no check: a failing
insert throws.

---

## Diagnostics

Batching moves the execution of a statement away from the line of code that logically issued it, which is
the price you pay. Tentackle mitigates it where it can:

- **Statement history** — [`StatementHistory`](../java/org/tentackle/dbms/StatementHistory.java) keeps the
  list of batched parameter maps, and prints each set with its index (`[0]`, `[1]`, …), so a failed batch
  shows every parameter set it carried, not just the last one.
- **Statement statistics** — [`StatementStatistics`](../java/org/tentackle/dbms/StatementStatistics.java)
  divides a batch's execution duration by the number of parameter sets, so per-statement averages remain
  comparable between batched and non-batched runs.
- **Logging** — `add batch`, `execute batch` and `clear batch` are logged at `FINER` on the statement
  wrappers.

---

## When Not to Batch

Batching is off by default because it is not a free win:

- **Error diagnosis gets harder.** The exception surfaces at the flush, not at the `saveObject()` that
  caused it. Stack traces point into the batch machinery; you need the statement history to see which
  parameter set was at fault.
- **The gain is highly backend-dependent.** It depends on the DBMS, the JDBC driver, the network latency,
  the indexes on the target table, and triggers. Some drivers need special connection parameters (for
  example, PostgreSQL's `reWriteBatchedInserts=true`) before batching pays off at all; others quietly
  execute the "batch" statement by statement.
- **Fewer, larger round trips also mean coarser locking.** Rows stay unwritten longer within the
  transaction, and are then written in a burst.

So: measure before you enable it, on the backend you actually deploy on, with realistic data volumes.
`BatchingTest` in [`tentackle-test-pdo`](../../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md)
is a template for such a measurement — it times the same number of inserts with and without batching, and
repeats both after database warmup.

**Use with great care in production.**

---

## Manual Batching

For pure bulk loads — imports, migrations, generated test data — the statement wrapper can be driven
directly. This bypasses the object-level bookkeeping, so the application is responsible for the parameter
sets and for checking the update counts:

```java
ModificationLog log = new ModificationLog(session, DbModificationType.INSERT);
log.setSerial(1);
PreparedStatementWrapper st = log.getPreparedStatement(log.getClassVariables().insertStatementId,
                                                       log::createInsertSql);
for (int i = 1; i <= COUNT; i++) {
  log.setId(i);
  log.setFields(st);
  st.addBatch();
  if (i % BATCHSIZE == 0) {
    int[] result = st.executeBatch(i >= COUNT);   // finish == true on the last invocation
    for (int r : result) {
      if (r != 1) {
        throw new PersistenceException(session, "insert failed");
      }
    }
  }
}
```

The `finish` flag of `executeBatch(boolean)` tells the wrapper whether this is the last invocation for the
transaction, so it can detach the session afterwards.

---

## Summary

| | Automatic | Manual |
|---|---|---|
| **Enabled by** | `batchsize` > 1 in the session config, or `Db#setBatchSize` | Nothing — just call `addBatch()` |
| **Applies to** | Generated single-row insert/update/delete statements | Any keyed prepared statement |
| **Application code** | Unchanged | Explicit batch loop |
| **Row-count check** | Automatic, per object, with the usual exception | Up to the application |
| **Ordering guarantees** | Preserved: flushed on root-type change, object reuse, savepoint, commit | Up to the application |

---

## See Also

- [Tentackle Database](./database.md) — the low-level persistence engine that owns sessions, statements and transactions.
- [Tentackle Persistence](../../../../tentackle-persistence/src/main/docs/persistence.md) — the PDO-aware layer that registers batchable statements.
- [Locking](../../../../tentackle-persistence/src/main/docs/locking.md) — why the row-count check must survive batching.
- [Cursors](../../../../tentackle-persistence/src/main/docs/cursors.md) — the read-side counterpart for large data volumes.
- [Tentackle Session](../../../../tentackle-session/src/main/docs/session.md) — session configuration properties.
