# Locking — Token Locks and the Optimistic `serial` Column

## Overview and Motivation

Two users open the same `Invoice`. Both edit it. Both save. In a naïve system the
second save silently overwrites the first — the *lost update* problem. Tentackle
addresses this on **two independent levels**, and it is important to understand that
they solve *different* problems and are *always both* in play for a writable entity:

| Mechanism              | Column(s)                                   | Scope                           | Answers                                                                 |
|------------------------|---------------------------------------------|---------------------------------|-------------------------------------------------------------------------|
| **Optimistic locking** | `serial`                                    | every persistent object, always | "Did the row change since I read it?" — detected *at write time*.       |
| **Token locking**      | `editedby` / `editedsince` / `editedexpiry` | opt-in (`tokenlock` option)     | "Is someone else *already* editing this?" — visible *before* you start. |

The `serial` column is the **safety net**: it cannot be turned off, and it guarantees
correctness even if nothing else is done — a stale write is *always* rejected. The
token lock is the **courtesy layer** on top: it lets users discover a conflict *early*
(before they waste effort editing) and is cooperative and persisted, so it is visible
across transactions and across clients.

This document explains each mechanism and then how they interlock.

---

## Optimistic Locking — the `serial` Column

Every PDO carries a `serial` (declared on
[`PersistentObject.getSerial()`](../../../../tentackle-pdo/src/main/java/org/tentackle/pdo/PersistentObject.java),
column [`CN_SERIAL = "serial"`](../../../../tentackle-common/src/main/java/org/tentackle/common/Constants.java),
Javadoc *"object version"*). Its contract is simple:

> Whenever the object is persisted, the `serial` is incremented by one. A freshly
> created, not-yet-persisted object has serial `0`.

This is a classic **version counter**, and it is the basis of *optimistic*
concurrency control: readers take no locks, and conflicts are detected only at the
moment of writing. The detection is done entirely by the `WHERE` clause of the
`UPDATE`/`DELETE` statement, which carries the serial the object had **when it was
loaded**:

```sql
UPDATE invoice SET ... , serial = serial + 1   WHERE id = ? AND serial = ?
DELETE FROM  invoice                            WHERE id = ? AND serial = ?
```

(See the generated update path
[`AbstractPersistentObject.updateImpl`/`deleteImpl`](../java/org/tentackle/persist/AbstractPersistentObject.java),
which bind `id` and `getSerial()`.)

If another transaction has modified the row in the meantime, its serial has already
advanced, so **zero rows match** and the statement updates nothing. Tentackle checks
the affected-row count in
[`assertThisRowAffected(int)`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/AbstractDbObject.java):

```java
if (count != 1) {
  if (count < 1) {
    // re-read the serial to distinguish "changed" from "deleted"
    long persistedSerial = selectSerial(getId());
    boolean temporary = persistedSerial > 0;   // still there -> optimistic-locking conflict
    NotFoundException nfx = new NotFoundException(this, message, persistedSerial);
    nfx.setTemporary(temporary);
    throw nfx;
  }
  ...
}
```

Two outcomes are distinguished by re-reading the current serial:

- **The row still exists** (`persistedSerial > 0`) → it was updated concurrently. The
  `NotFoundException` is flagged **temporary**, because retrying the *whole*
  transaction (re-reading the now-current row) may well succeed. This is the hook a
  `@Transaction`-annotated method uses to transparently retry — see
  [Transactions and Transparent Retry](../../../../tentackle-pdo/src/main/docs/transaction.md).
- **The row is gone** (`persistedSerial == 0`) → it was deleted concurrently; the
  failure is permanent.

Because the check is a side effect of the normal write, optimistic locking costs
nothing on the happy path and requires no held database locks — ideal for the typical
"read, think, write" interaction of a desktop client.

### Related counters

- `tableserial` (model option `tableserial`,
  [`CN_TABLESERIAL`](../../../../tentackle-common/src/main/java/org/tentackle/common/Constants.java)) is a
  *table-wide* version advanced on every write, used by the
  [`ModificationTracker`](persistence.md#modification-tracking) to drive **cache expiry** and
  cross-client change notification. It is a coherence/notification counter, not a
  per-row conflict guard — do not confuse it with `serial`.
- `serial` is **per object**; `tableserial` is **per table**.

---

## Token Locking

Optimistic locking is correct but *late*: a user only learns about the conflict when
they try to save, after all the editing work is done. For interactive editing that is
a poor experience. A **token lock** moves the discovery to the *start*.

A token lock is a **cooperative, persisted lock** expressing *"this object is currently
being edited by user X until time Y"*. Unlike a database row lock it **survives across
transactions** and is **visible to other users**. It is opt-in: an entity gets it by
declaring the `tokenlock` option in its model, which adds three columns
([model-definition → option flags](../../../../tentackle-model/src/main/docs/model-definition.md#entity-level-option-flags)):

| Attribute      | Column         | Meaning                                      |
|----------------|----------------|----------------------------------------------|
| `editedBy`     | `editedby`     | user id of the token holder, `0` = free      |
| `editedSince`  | `editedsince`  | when editing started (or when last released) |
| `editedExpiry` | `editedexpiry` | when the token automatically expires         |

`isTokenLockProvided()` reports whether an entity has these columns.

### Acquiring, releasing, checking

The PDO API
([`PersistentObject`](../../../../tentackle-pdo/src/main/java/org/tentackle/pdo/PersistentObject.java)):

```java
invoice.requestTokenLock();      // grab the token (LockException if held by another user)
invoice.releaseTokenLock();      // give it back without persisting
invoice.isTokenLocked();         // locked by anyone, and not expired?
invoice.isTokenLockedByMe();
invoice.isTokenLockableByMe();   // free, or already mine?
```

Convenience round-trips combine a load and a lock in **one** server call (important for
remote sessions): `selectTokenLocked(id)` (= `select` + `requestTokenLock`) and
`reloadTokenLocked()` (= `reload` + `requestTokenLock`). On the write side,
`persistTokenLocked()` saves *and renews* the token so the user can keep editing.

### How a token is granted — atomically, and serial-aware

`requestTokenLock()` / `releaseTokenLock()` both funnel into
[`updateTokenLock(...)`](../java/org/tentackle/persist/AbstractPersistentObject.java),
which performs a **single conditional `UPDATE`** — the grant decision and the write are
one atomic statement, so there is no check-then-act race:

```sql
UPDATE invoice
   SET editedby = ?, editedsince = ?, editedexpiry = ?
 WHERE id = ?
   AND serial = ?                              -- (1) the row hasn't changed under me
   AND ( editedby = ?                          -- (2a) I already hold it (renew)
      OR editedby = 0                          -- (2b) nobody holds it
      OR editedexpiry < ?                      -- (2c) the previous holder's token expired
      OR editedexpiry IS NULL )                -- (2d) never set
```

If the statement updates exactly one row, the token is ours; the in-memory
`editedBy`/`editedSince`/`editedExpiry` are set and the
[`LockManager`](../java/org/tentackle/persist/LockManager.java) is notified. If it
updates **zero** rows, the code re-selects the row to find out *why* and reports
precisely:

- the row is gone → `NotFoundException` ("removed in the meantime");
- the serial differs → `NotFoundException` ("updated in the meantime"); or
- someone else genuinely holds a live token → `LockException` carrying that holder's
  `editedBy`/`editedSince`/`editedExpiry` so the UI can say *who* and *since when*.

Note clause **(2c)**: tokens **auto-expire**. A token is a time-boxed reservation
(`getTokenLockTimeout()` ms), not a permanent lock, so a crashed or walked-away client
cannot block an object forever — once `editedexpiry` passes, the next requester simply
takes it over.

### The `LockManager` — surviving crashes

Token state lives in the columns, but a parallel
[`LockManager`](../java/org/tentackle/persist/LockManager.java) /
[`DefaultLockManager`](../java/org/tentackle/persist/lock/DefaultLockManager.java) tracks
*all* live tokens (persisted as
[`DbTokenLock`](../java/org/tentackle/persist/lock/DbTokenLock.java) rows) so they can be
known without scanning every table. Its job is cleanup:

- when a **client session crashes / logs out**, that user's tokens are dropped
  (`cleanupUserTokens`);
- when the **server restarts**, all tokens are cleared (`initialize`).

It is enabled **only on a non-remote server**; in 2-tier and remote-client setups it is
disabled (expiry alone then bounds a stale token's lifetime).

`transferTokenLock(userId)` is an administrative escape hatch to hand a token to another
user (or free it, `userId == 0`) without holding it — for corner cases like admin
tools, not normal flow.

---

## How the Two Mechanisms Relate

They are **complementary layers**, not alternatives, and the `serial` column is the
thread connecting them.

1. **The token notices `serial` too.** Look again at the token `UPDATE`: it includes
   `AND serial = ?`. So *requesting a lock on a stale object fails the same way a stale
   save does* — you cannot acquire an editing token on a version of the row that has
   already moved on. Token acquisition is therefore itself optimistic-locking-checked.

2. **A token is not required to write.** Even with no token at all, a stale `UPDATE`
   still fails on the serial mismatch. Optimistic locking is the **floor of
   correctness** that is never absent. The token only changes *when* you discover the
   conflict (early vs. at save), never *whether* you are protected from a lost update.

3. **The token does not replace the serial check at save.** A held token says "no one
   else is *editing*", but `save()`/`persist()` still write `WHERE id = ? AND serial = ?`.
   This matters because non-token paths (batch jobs, imports, admin tools, code that
   never asked for a token) can still legitimately change a row; the serial guard
   catches those regardless of tokens.

4. **Tokens are orthogonal to the serial counter.** Acquiring or releasing a token
   updates only the `editedby`/`editedsince`/`editedexpiry` columns; it deliberately
   **does not** bump `serial` and does not count as a modification. Editing metadata is
   not a content change, so it must not invalidate anyone else's optimistic read or trip
   cache expiry.

A typical interactive edit therefore uses **both**:

```java
Invoice inv = on(Invoice.class).selectTokenLocked(id);   // (a) token: claim it up-front,
                                                         //     fail fast if someone else has it
inv.setText("...");                                       // (b) user edits at leisure
inv = inv.persist();                                      // (c) serial: still verifies nobody
                                                         //     slipped a change past the token
```

- Step **(a)** gives a *friendly, early* signal: if another user holds the token, the
  current user is told immediately and need not start editing.
- Step **(c)** gives the *hard* guarantee: regardless of tokens, the write is rejected
  if the row's `serial` advanced — closing the small windows the cooperative token
  cannot (expired tokens, token-less writers, clock skew).

Use **optimistic locking alone** for back-end/batch writes and for entities where
collisions are rare and a retry is acceptable. Add a **token lock** for entities a human
sits and edits, where discovering the conflict *after* the work is unacceptable UX.

---

## Summary

|                                               | `serial` (optimistic)                                      | token lock                                       |
|-----------------------------------------------|------------------------------------------------------------|--------------------------------------------------|
| **Always present?**                           | Yes, on every persistent object                            | Opt-in via `tokenlock` model option              |
| **When is a conflict found?**                 | At write (save/persist/delete)                             | At lock request (before editing)                 |
| **Held resources**                            | None                                                       | A persisted, expiring reservation                |
| **Crosses transactions / visible to others?** | No (it's a version number)                                 | Yes                                              |
| **On conflict**                               | `NotFoundException` (temporary → `@Transaction` may retry) | `LockException` (names the holder)               |
| **Bumps `serial`?**                           | Yes, every persist                                         | No (metadata only)                               |
| **Guarantee**                                 | No lost update, unconditionally                            | Early, cooperative "already being edited" notice |

The serial column is the unconditional correctness guarantee; the token lock is the
cooperative, user-facing layer built on top of it — and because token acquisition is
itself serial-checked, the two reinforce rather than duplicate each other.

### See also

- [persistence.md → Token locking](persistence.md#token-locking)
- [pdo.md](../../../../tentackle-pdo/src/main/docs/pdo.md) — the token-lock family on the PDO API
- [model-definition.md → Entity-level option flags](../../../../tentackle-model/src/main/docs/model-definition.md#entity-level-option-flags) — the `tokenlock` and `tableserial` options
- [exceptions.md](../../../../src/main/docs/exceptions.md) — `LockException` and `NotFoundException` in the framework's exception model, and why both are *temporary*
