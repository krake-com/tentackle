# Number Sources — Unique Business Numbers

## Overview and Motivation

Applications need unique numbers that *users* see and refer to: invoice numbers,
order numbers, message numbers, customer numbers. These are not object ids. An
object id is a technical surrogate key, invisible to the user, handed out by an
[`IdSource`](../../../../tentackle-database/src/main/docs/database.md#id-sources)
and never reused. A business number is part of the domain: it may be reserved in
blocks, carved up between sites, handed back when a document is discarded, and
partitioned per tenant or per year.

A **number source** ([`NumberSource`](../java/org/tentackle/ns/NumberSource.java))
is Tentackle's answer for the second kind. It is a persistent, transactional pool
of numbers that supports both directions — you can *pop* numbers out of it and
*push* numbers back into it — and it is backed by ordinary
[PDOs](pdo.md), which means it is backend-agnostic, validated, secured, tracked,
and part of the model like any other entity.

The subsystem has two layers:

| Layer       | Package                | What it is                                                                                                                                                                           |
|-------------|------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| The API     | `org.tentackle.ns`     | [`NumberSource`](../java/org/tentackle/ns/NumberSource.java), [`NumberSourceFactory`](../java/org/tentackle/ns/NumberSourceFactory.java) and the default implementation.             |
| The storage | `org.tentackle.ns.pdo` | [`NumberPool`](../java/org/tentackle/ns/pdo/NumberPool.java) and [`NumberRange`](../java/org/tentackle/ns/pdo/NumberRange.java) — the PDOs the default implementation persists into. |

The two are deliberately separate: `NumberSource` is an interface with a
[service](../../../../tentackle-common/src/main/docs/services.md)-provided factory, so an
application that must draw its numbers from somewhere else entirely can replace the
implementation without the pool entities coming along.

---

## The `NumberSource` API

A source hands out `long`s and describes itself in terms of **ranges**. The
`Range` record is half-open — `begin` is the first number, `end` is the last number
**plus one**, exactly like `subList` or `IntStream.range`:

```java
record Range(long begin, long end) {
  long size() { return end - begin; }
}
```

| Method                              | Purpose                                                                                |
|-------------------------------------|----------------------------------------------------------------------------------------|
| `popNumber()`                       | Take the next single number. Throws `NumberSourceEmptyException` if the pool is empty. |
| `popNumbers(long count)`            | Take `count` numbers. Returns them as ranges, ascending — they need not be contiguous. |
| `popNumbers(long begin, long end)`  | Take a *specific* interval, if the pool holds it. Returns the parts actually consumed. |
| `pushNumber(long number)`           | Give one number back. A number already in the pool is ignored.                         |
| `pushNumbers(long begin, long end)` | Give an interval back. May overlap what the pool already holds.                        |
| `getRanges()`                       | The current ranges, consuming nothing. Unsorted, and may contain empty ranges.         |
| `getCount()`                        | How many numbers remain.                                                               |
| `isEmpty()` / `isOnline()`          | Whether the pool is exhausted / enabled.                                               |

All methods except `isOnline()` fail with a `NumberSourceException` if the pool is offline,
and all of them if the pool no longer exists.

Failures are unchecked:
[`NumberSourceException`](../java/org/tentackle/ns/NumberSourceException.java)
extends `TentackleRuntimeException`, and
[`NumberSourceEmptyException`](../java/org/tentackle/ns/NumberSourceEmptyException.java)
extends it in turn — so "empty" can be caught on its own or with everything else.

### Two subtleties worth knowing

**`popNumbers(count)` may return fewer than `count` numbers.** If the pool is empty
when the call starts, it throws. If it *runs dry part-way through*, it returns what
it managed to collect and stops. Always check the sizes of the returned ranges
rather than assuming they sum to `count`:

```java
long got = 0;
for (Range r : ns.popNumbers(500)) {
  got += r.size();
}
if (got < 500) { /* pool ran dry */ }
```

**`popNumbers(begin, end)` is a request, not a reservation.** It consumes only the
parts of the interval the pool actually holds, and returns exactly those parts. It
does not throw when the interval is absent — it returns an empty array.

---

## Getting a Number Source

Number sources are addressed by two strings, a **pool name** and an optional
**realm**, and reached through the factory singleton:

```java
NumberSource ns = NumberSourceFactory.getInstance().getNumberSource("MSG", "myapp");
long number = ns.popNumber();
```

The realm partitions one logical pool into independent number spaces. The generated
project uses the application name as the realm
(`Constants.POOL_REALM`), so several applications can share a database without
sharing message numbers; a multi-tenant application would use the tenant, a
year-scoped numbering scheme the year.

[`NumberSourceFactory`](../java/org/tentackle/ns/NumberSourceFactory.java) is an SPI
singleton resolved through `ServiceFactory.createService(...)`, defaulting to
[`DefaultNumberSourceFactory`](../java/org/tentackle/ns/DefaultNumberSourceFactory.java)
(annotated `@Service(NumberSourceFactory.class)`). The default factory caches one
source per `pool:realm` key, and throws `NumberSourceException` if no such pool
exists in the database — a missing pool is a configuration error, not an empty
result. The cache is never invalidated: a source whose pool has been deleted keeps
failing with "vanished", even if a pool of the same name is created again, until the
application is restarted. Notice further that the key is built by concatenation: avoid
colons in pool names, and use either `null` or the empty string for "no realm", not both.

---

## The Model — `NumberPool` and `NumberRange`

The default implementation stores a pool as an **aggregate**: a `NumberPool` root
entity owning a composite list of `NumberRange` components. The pool holds the
policy; the ranges hold the actual numbers.

[`NumberPool`](../java/org/tentackle/ns/pdo/NumberPool.java) — table `numpool`,
class id `6`, options `[root, remote, fulltracked]`:

| Attribute      | Column        | Type         | Purpose                                                        |
|----------------|---------------|--------------|----------------------------------------------------------------|
| `name`         | `name`        | `String(30)` | The pool name. Domain key, `@NotNull`.                         |
| `realm`        | `realm`       | `String(80)` | Optional partition. `[MAPNULL]`.                               |
| `description`  | `description` | `String`     | Free-text description.                                         |
| `online`       | `poolonline`  | `boolean`    | Whether the pool may be drawn from.                            |
| `lowWaterMark` | `lowmark`     | `long`       | Uplink refill threshold; `0` disables.                         |
| `requestSize`  | `reqsize`     | `long`       | Uplink request size; `0` disables.                             |
| `uplink`       | `uplink`      | `String`     | Uplink configuration; see [Uplinks](#uplinks-and-slave-pools). |

A unique index `udk` spans `name, realm`. Because `realm` is declared `[MAPNULL]`,
a null realm is stored as the backend's empty-string constant rather than SQL
`NULL` — which keeps the unique index meaningful for pools that have no realm, where
`NULL` values would otherwise not compare equal to each other.

[`NumberRange`](../java/org/tentackle/ns/pdo/NumberRange.java) — table `numrange`,
class id `7`, options `[remote, fulltracked]`:

| Attribute      | Column   | Type   | Purpose                    |
|----------------|----------|--------|----------------------------|
| `numberPoolId` | `poolid` | `long` | The owning pool.           |
| `begin`        | `rbegin` | `long` | First number in the range. |
| `end`          | `rend`   | `long` | Last number **+ 1**.       |

Both entities declare `integrity := none`: no foreign keys are emitted, because the aggregate
is managed programmatically, which allows performance optimizations and efficient caching
when handling the pool's ranges.
The relation is a composite list with `delete = cascade`: deleting a pool deletes its
ranges, which is necessary due to `integrity := none`.

Class ids `6` and `7` are framework-reserved; an application's own entities must not
claim them.

---

## Setting Up a Pool

A pool is created like any other aggregate — build the root, add components, save.
This is what the generated project's `FillDatabase` does:

```java
NumberPool pool = on(NumberPool.class);
pool.setRealm(Constants.POOL_REALM);
pool.setName(Message.POOL_NAME);
pool.setDescription("pool for message numbers");
pool.setOnline(true);

NumberRange range = on(NumberRange.class);
range.setBegin(1000000);
range.setEnd  (9999999);
pool.getNumberRangeList().add(range);

pool.save();
```

A pool may hold **several disjoint ranges**, and they need not be added in order —
numbers are handed out from the lowest non-empty range first, whatever order
the ranges were created in. (The current range is cached, though: a number pushed back
*below* it is handed out only after the current range is exhausted or the pool was
modified by another tier.) Setting `online` to `false` disables the pool: every
attempt to draw from or inspect it fails with `NumberSourceException` until it is
re-enabled — only `isOnline()` still answers. This is the intended way to take a pool
out of service without deleting it.

---

## How the Default Implementation Works

[`DefaultNumberSource`](../java/org/tentackle/ns/DefaultNumberSource.java) keeps the
pool and a cached *current range* — the non-empty range with the lowest `begin`,
determined by
[`NumberPoolDomainImpl.getCurrentRange()`](../../../../tentackle-domain/src/main/java/org/tentackle/domain/ns/NumberPoolDomainImpl.java),
which also prunes exhausted ranges as it scans.

Popping a single number is the hot path, and it is deliberately the cheapest one:
lock the pool, find the current range, take its `begin` and increment it, persist.

```java
return Session.getSession().transaction(() -> {
  lockPool();
  findCurrentRange();
  long number = currentRange.popNumber();
  pool = pool.persist();
  return number;
});
```

The other operations are variations on the same theme.
`popNumbers(count)` drains the current range, moves to the next, and repeats.
`popNumbers(begin, end)` walks every range looking for intersections and carves the
requested interval out of them — consuming a whole range, trimming its head, trimming
its tail, or, when the interval falls strictly inside a range, **splitting it into
two** by creating a fresh `NumberRange`.

The push operations merge a returned number or interval into the first adjacent or
overlapping range, or create a new range when it stands alone. Afterwards `fixRanges()`
walks the ranges sorted by `begin` and `end` and merges any that now overlap or touch,
deleting the ones merged into others. That last step is what makes uniqueness a
property of the pool rather than of the caller: however sloppily numbers are pushed
back, the pool never ends up holding the same number twice, so it can never hand it
out twice.

---

## Concurrency and Correctness

A number source is a contended, shared resource, and a duplicate number is exactly
the kind of quiet wrongness the framework
[refuses to produce](../../../../src/main/docs/correctness-first.md). Three mechanisms
combine:

**Every operation runs in a transaction.** Each public method wraps its work in
`Session.getSession().transaction(...)`, so a number is never handed out by a
transaction that later rolls back.

**The pool row is write-locked.** `lockPool()` calls `reloadForUpdate()`, which
reloads the pool under a database write lock for the remainder of the transaction.
Concurrent poppers on the same pool — in other processes, on other hosts — serialize
on that row. The reload doubles as a staleness check: if the reloaded pool's
[`serial`](../../../../tentackle-persistence/src/main/docs/locking.md) differs from
the cached one, the cached range is dropped. It also re-checks `online`, so disabling
a pool takes effect on the next operation.

**The cached range is revalidated.** `findCurrentRange()` reloads the cached range
and compares serials. If it vanished, changed underneath, or is now empty — another
tier drew from it, or a transaction rolled back — the cache is discarded and the
ranges are re-read from the database.

Additionally, the methods are `synchronized`, so threads within one JVM are excluded
before they ever reach the database.

One deliberate optimization deserves note.
[`NumberPoolPersistenceImpl`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/ns/NumberPoolPersistenceImpl.java)
overrides:

```java
@Override
public boolean isUpdatingSerialEvenIfNotModified() {
  return false;   // don't update the pool if only ranges changed
}
```

A root entity would normally have its serial bumped whenever its aggregate is
persisted. For a pool, popping a number changes only a range — bumping the pool's
serial on every single `popNumber()` would invalidate every other tier's cached copy
of the pool for a change that never touched it. The pool's own attributes are still
serial-protected; only range churn is exempted.

---

## Remoting — Why the Source Is Local by Design

`DefaultNumberSource` refuses a remote session outright:

```java
if (pool.getSession().isRemote()) {
  throw new PersistenceException(pool, "number sources cannot be remote");
}
```

This is not a limitation so much as a statement about where the work belongs. The
algorithm is a tight loop of locking, reading and updating rows; running it over a
remote session would turn each `popNumber()` into a series of round-trips while
holding a write lock on a hot row.

The supported pattern is to keep the source on the tier that owns the database and
expose a **remote method** that draws from it — which is what the generated project
does. `MessagePersistence.nextMessageNumber()` is declared on the persistence
interface and implemented as a `@RemoteMethod`:

```java
@RemoteMethod
@Override
public String nextMessageNumber() {
  return Long.toString(NumberSourceFactory.getInstance()
      .getNumberSource(Message.POOL_NAME, Constants.POOL_REALM).popNumber());
}
```

The domain layer then simply calls `me().nextMessageNumber()` without knowing or
caring which tier executed it. On a client, the call travels over
[TRIP](../../../../tentackle-core/src/main/docs/trip.md) and runs on the server; in a
single-tier or test setup, the very same code runs in-process. This is the
[multi-tier cascade](../../../../src/main/docs/tiers.md) doing its job: one round-trip
per number, and the locking stays next to the database.

The pool *entities* are a different matter — `NumberPool` and `NumberRange` are
declared `remote` and have TRIP delegates, so administering pools (creating them,
adding ranges, taking them offline, inspecting them) works perfectly well from a
remote client. It is only the number-drawing algorithm that insists on being local.

---

## Uplinks and Slave Pools

The model anticipates a **hierarchy** of pools: a central master pool, and slave
pools at branch sites that request blocks of numbers from it as they run low. Three
attributes describe this:

- `lowWaterMark` — refill when fewer than this many numbers remain;
- `requestSize` — how many to request from the uplink;
- `uplink` — how to reach the master.

[`NumberPoolDomain.isSlave()`](../java/org/tentackle/ns/pdo/NumberPoolDomain.java)
reports whether a pool is configured as one. The default implementation in
[`NumberPoolDomainImpl`](../../../../tentackle-domain/src/main/java/org/tentackle/domain/ns/NumberPoolDomainImpl.java)
reads:

```java
@Override
public boolean isSlave() {
  return me().getLowWaterMark() > 0 && me().getRequestSize() > 0;
}
```

and the model makes the uplink mandatory exactly when it is needed, via a
[conditional validation](../../../../tentackle-core/src/main/docs/validation.md#conditions-values-and-message-scripts):

```
uplink: @NotNull(condition="$slave")
```

**The default implementation does not implement uplinks.** `DefaultNumberSource`
never reads `lowWaterMark`, `requestSize` or `uplink`; a pool configured as a slave
behaves exactly like a standalone one and simply runs empty. As its Javadoc states,
it "does not implement any logic to access an uplink source, and thus needs to be
wrapped or replaced if an uplink is required."

The attributes, the `isSlave()` predicate and its validation are the **extension
point**, not a shipped feature. To implement refilling, provide a `NumberSource` whose
drawing methods top up from the uplink when `getCount()` falls below `lowWaterMark` —
for example, by delegating to a `DefaultNumberSource` (whose pool and ranges are private,
so subclassing gains nothing over delegation) — and publish a factory that returns it:

```java
@Service(NumberSourceFactory.class)
public class UplinkNumberSourceFactory implements NumberSourceFactory {
  @Override
  public NumberSource getNumberSource(String pool, String realm) { ... }
}
```

Because `NumberSourceFactory` is resolved through the SPI, application code calling
`getNumberSource(...).popNumber()` needs no change.

---

## Summary

| Concern          | How number sources address it                                                                            |
|------------------|----------------------------------------------------------------------------------------------------------|
| **Uniqueness**   | Ranges are merged on push (`fixRanges()`), so a pool cannot hold — or hand out — a number twice.         |
| **Concurrency**  | A database write lock on the pool row plus per-operation transactions serialize poppers across tiers.    |
| **Staleness**    | Serial comparison on both pool and range discards caches that another tier invalidated.                  |
| **Contention**   | Popping a number does not bump the pool's serial, so range churn does not invalidate other tiers' pools. |
| **Partitioning** | `realm` splits one pool name into independent number spaces per tenant, application, or year.            |
| **Placement**    | The algorithm stays on the database tier; clients reach it through a `@RemoteMethod`.                    |
| **Extension**    | `NumberSourceFactory` is an SPI singleton; uplink/slave pools are modeled but left to the application.   |

Number sources are for numbers the *domain* cares about. For technical object ids,
see [id sources](../../../../tentackle-database/src/main/docs/database.md#id-sources).

### See also

- [pdo.md → Aggregates and the root context](pdo.md#aggregates-and-the-root-context)
- [root-columns.md](root-columns.md)
- [locking.md](../../../../tentackle-persistence/src/main/docs/locking.md)
- [services.md](../../../../tentackle-common/src/main/docs/services.md)
- [validation.md → Conditions, values, and message scripts](../../../../tentackle-core/src/main/docs/validation.md#conditions-values-and-message-scripts)
- [tiers.md](../../../../src/main/docs/tiers.md)
- [database.md → Id sources](../../../../tentackle-database/src/main/docs/database.md#id-sources)
