# PDO Caching

## Overview and Motivation

Most applications read the same **master data** over and over — currencies, units,
organizational units, number pools, account types. These tables are small, change
rarely, and are referenced by almost every transaction. Fetching them from the
database each time is wasteful, so Tentackle provides a first-class, model-driven
**PDO cache**.

A `PdoCache` is a per-entity-type, in-memory store of
[PDOs](pdo.md) keyed by their object ID and, optionally, by additional unique
domain keys. It transparently:

- serves PDOs by ID or unique key without a database round-trip,
- can hold the **complete table** and answer `selectAll()` from memory,
- keeps itself **coherent** as data changes — across transactions, across JVMs,
  and across a middle-tier server — using the table's *tableserial*,
- hands out **immutable, thread-shared** instances by default, so the same PDO can
  be used concurrently by many threads with no locking and no copying.

The whole subsystem lives in `tentackle-pdo`:

| Class                                                                                                                                                   | Role                                                    |
|---------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------|
| [`PdoCache`](../java/org/tentackle/pdo/PdoCache.java)                                                                                                   | The cache itself: indexes, lists, strategy, expiration. |
| [`PdoCacheIndex`](../java/org/tentackle/pdo/PdoCacheIndex.java)                                                                                         | One unique-key → PDO mapping (ID index is mandatory).   |
| [`PdoCacheStrategy`](../java/org/tentackle/pdo/PdoCacheStrategy.java)                                                                                   | The eviction mode: `PRELOAD`, `LRU`, `LFU`, `FORGET`.   |
| [`PdoCacheFactory`](../java/org/tentackle/pdo/PdoCacheFactory.java) / [`DefaultPdoCacheFactory`](../java/org/tentackle/pdo/DefaultPdoCacheFactory.java) | SPI factory; registry of all caches.                    |
| [`PdoCacheException`](../java/org/tentackle/pdo/PdoCacheException.java)                                                                                 | Signals a cache inconsistency (triggers invalidation).  |
| [`PdoCacheOperation`](../java/org/tentackle/pdo/PdoCacheOperation.java)                                                                                 | Remoting-capable operation that preloads the caches at startup. |
| [`PdoCachePreloadContent`](../java/org/tentackle/pdo/PdoCachePreloadContent.java) / [`PdoCachePreloadTableInfo`](../java/org/tentackle/pdo/PdoCachePreloadTableInfo.java) | The DTOs carrying a preloaded cache and its serial baseline over the wire. |

The [`@PreloadCache`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/PreloadCache.java)
annotation that drives the preloading lives in `tentackle-persistence`, since it marks a persistence
implementation class — see *Preloading Caches at Startup* below.

Application code rarely touches these directly. It calls the **generated
`selectCached…` methods** on the PDO; the model declares the cache and the
Wurbelizer wires everything up.

---

## Declaring a Cache

Two things turn an entity into a cached entity.

**1. The model `cached` option** marks a root entity as cacheable
([model-definition.md](../../../../tentackle-model/src/main/docs/model-definition.md)). All other entities containing a relation to it
automatically use the cache, if not declared otherwise in the model.

**2. The [`PdoCache`](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md)
wurblet** generates the cache holder, the indexes, and the `selectCached…`
accessors into the persistence implementation:

```java
// @wurblet cache PdoCache --udk --strategy=LRU --maxsize=5000 code[codeIfUser]
```

| Option                                 | Meaning                                                                                                                                                                                      |
|----------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `--mutable`                            | Generate a **non-shared, mutable** cache (default is a shared read-only cache).                                                                                                              |
| `--udk`                                | Add an index for the entity's unique domain key.                                                                                                                                             |
| `--strategy=PRELOAD\|LRU\|LFU\|FORGET` | Eviction strategy (default `PRELOAD`).                                                                                                                                                       |
| `--maxsize=<n>`                        | Maximum number of cached PDOs (`0` = unlimited).                                                                                                                                             |
| `--keepquota=<p>`                      | Percentage of entries kept when shrinking (default 50).                                                                                                                                      |
| `--configure=<method>`                 | Call an application method to further configure the cache.                                                                                                                                   |
| `[index …]`                            | Additional **unique** indexes by attribute; `attr[method]` extracts the key via `method` (allowing *filtered* indexes whose method returns `null` to exclude PDOs, forming a unique subset). |

The generated code (see
[`PdoCache.wrbl`](../../../../tentackle-persistence-wurblets/src/main/wurblets/org/tentackle/persist/wurblet/PdoCache.wrbl))
follows this shape — here trimmed from `StoredBundlePersistenceImpl`:

```java
private static class CacheHolder {
  private static final PdoCache<StoredBundle> CACHE = createCache();
  private static final PdoCacheIndex<StoredBundle, StoredBundleUDK> UDK_INDEX = createUdkIndex();

  private static PdoCache<StoredBundle> createCache() {
    PdoCache<StoredBundle> cache = Pdo.createCache(StoredBundle.class, true /* readOnly */);
    Pdo.listen(cache::expire, StoredBundle.class);   // subscribe to modification events
    return cache;
  }
  ...
}
```

The `CacheHolder` idiom gives a **lazily initialized singleton per PDO class**
(the cache is created on first use, in a thread-safe way courtesy of the JVM class
loader). `Pdo.listen(cache::expire, …)` subscribes the cache to the global
[`ModificationTracker`](../../../../tentackle-session/src/main/docs/session.md) so it
hears about updates/inserts/deletes to the table — see *Expiration* below.

> For an inheritance hierarchy, the **topmost super entity** normally provides the
> cache for the whole hierarchy; a subclass may keep its own cache (e.g., to add
> indexes the superclass doesn't have). For `MULTI`-table inheritance the cache
> listens to all leaf classes.

---

## Indexes and Keys

A cache is a collection of **unique indexes**, each a
`TreeMap<CacheKey, PDO>` inside a
[`PdoCacheIndex`](../java/org/tentackle/pdo/PdoCacheIndex.java):

- The **ID index** is mandatory, created automatically, and cannot be removed.
- Additional indexes (unique domain key, or any declared unique attribute) are
  added — possibly lazily, on first use ("deferred index assignment"). Adding an
  index to an already-populated cache backfills it by walking the ID index.
- Each index also keeps a **missing set**: keys that were looked up and found *not*
  to exist in the database, so repeated lookups of an absent key don't keep hitting
  storage.

The map key is a `CacheKey` combining **the unique key value *and* the
[`DomainContext`](pdo.md)** (plus the session instance number). This is essential:
the same logical row may be cached simultaneously under different contexts —
different tenants or different database sessions — and they must not collide. A
`null` key or context throws a `PdoCacheException`.

Each index is defined by two functions (supplied by the generated code): one to
**select** a PDO by key from storage, and one to **extract** the key from a PDO.
If `extract` returns `null` the PDO is simply not added to that index — the
mechanism behind *filtered* indexes.

---

## Read-Only vs. Mutable Caches

The `readOnly` flag chosen at creation time has far-reaching consequences.

**Read-only cache (the default).** PDOs are made
[**finally immutable**](../../../../tentackle-core/src/main/docs/immutable.md) and
their session is detached, so a single shared instance is safe for any number of
concurrent threads. On the way in, `PdoCacheIndex.processPdo()` does:

```java
pd.setDomainContextImmutable(false);
pd.setDomainContext(processedContext);   // a thread-local-session context
pd.setFinallyImmutable();
pd.setDomainContextImmutable(true);
pd.setSessionImmutable(true);
```

Because the session is thread-local, lazy-loading a relation off a cached PDO uses
*the current thread's* session, which keeps the shared object usable everywhere.

**Mutable cache (`--mutable`).** PDOs stay mutable and are therefore maintained
**per session**, which costs significantly more memory and shifts the burden of
avoiding multi-threading issues onto the application. Use it only when you really
need to edit cached instances. Closing a session removes its objects and lists
(`removeObjectsForSession`).

---

## Operating Modes (Strategies)

The cache works in one of four [`PdoCacheStrategy`](../java/org/tentackle/pdo/PdoCacheStrategy.java)
modes:

| Strategy              | Behaviour when `maxSize` is reached                                                        | Best for                                                                                                                                     |
|-----------------------|--------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| **PRELOAD** (default) | n/a — the first select loads the **whole table**, so subsequent selects never hit storage. | Small master-data tables; also **large or transactional tables** when a tableserial is configured (in-memory read model / CQRS — see below). |
| **LRU**               | Evicts the **least recently used** PDOs down to `keepQuota` %.                             | Large tables, keep hot subset.                                                                                                               |
| **LFU**               | Evicts the **least frequently used** PDOs.                                                 | Large tables with skewed access.                                                                                                             |
| **FORGET**            | **Clears the entire cache.**                                                               | Large tables, cheap to refill.                                                                                                               |

LRU/LFU ordering uses the per-PDO `cacheAccessTime` / `cacheAccessCount` that
`markCacheAccess()` stamps on every hit. Shrinking (`shrinkCache`) either removes
the surplus objects in place or, when more than half must go, invalidates and
re-adds the survivors — whichever touches fewer entries. `keepQuota = 0` behaves
like `FORGET`.

> **`selectAll` puts the cache into PRELOAD.** The first `selectAll()` loads the
> whole table and, unless `maxSizeHardLimit` is set, **auto-enlarges** `maxSize` to
> fit (size + 10 %). From then on the cache behaves exactly as if `PRELOAD` had been
> declared: eviction is off and the cache keeps the full list coherent instead.
> Crucially, with a tableserial configured this coherency is **additive** — new
> inserts are appended as they happen — so the cache **always holds every object**
> and stays **fully searchable** from memory (see *PRELOAD for large tables* below).


---

## Reading from the Cache

### By ID or unique key — `select`

The core `select(index, context, key, loadIfMissing)` flow
([`PdoCache`](../java/org/tentackle/pdo/PdoCache.java)):

1. If caching is globally or locally disabled → read straight from storage.
2. Run any **delayed expiration** for the session (and `refreshList` if preloading
   or a list exists for this context).
3. Look the key up in the index.
4. If the hit is **expired** (and not list-managed), drop it and treat as a miss.
5. On a miss, if `loadIfMissing` and the key isn't in the **missing set**, select
   from the database (or remote cache). A found PDO that `isCacheable()` is added
   to **all** indexes; a non-existent key is recorded in the missing set.
6. `markCacheAccess()` the returned PDO.

A `PdoCacheException` thrown mid-operation (a detected unique violation or key
change — usually because the application illegally mutated a cached object's key)
triggers `emergencyInvalidate()` and **one** automatic retry; a second failure
propagates, guarding against infinite loops.

### Whole table — `selectAll`

`selectAll(context)` returns a `CopyOnWriteList` wrapping the cached list, so
callers may sort or filter it without disturbing the cache. The list is kept in a
per-context `SelectAllList` that remembers which IDs need **refreshing** or
**removing** on the next access; `refreshList` reloads only the changed PDOs and
either patches the list in place or rebuilds it from the ID index, whichever is
cheaper (`isDeleteBetterThanRebuild`).

`selectAny(context, ids)` returns a specific subset, refreshing expired entries.

---

## Coherency: Expiration and Invalidation

This is the heart of the cache — keeping it consistent when data changes. The
strategy depends entirely on whether the table carries a **`tableserial`** column
(`isTableSerialProvided()` / the model `[tableserial]` option), a monotonically
increasing per-table modification counter.

### No tableserial → invalidate everything

Without a tableserial there is no way to know *which* rows changed, so any
modification **invalidates the whole cache**. That is perfectly fine for a small
preloading cache: reloading the entire table costs about the same as loading a
single row.

### With tableserial → surgical expiration

Modifying a PDO calls `expireCache(maxSerial)`, which the cache wurblet overrides
to call `cache.expire(session, tableName, maxSerial)`. Rather than reload
immediately, the cache **remembers an upper bound** and defers the actual check to
the next access (`expireDelayed` → `expireImpl` → `expireByExpirationInfo`). At
that point it asks the database (or the remote cache) for the *expired tableserials*
in the requested range and walks them:

- a **positive** serial means update/insert → an already-cached PDO is marked
  `expired` (reloaded on next access) or scheduled into list `refreshIds`; an
  **id not yet in the cache is a new insert** and is scheduled into `refreshIds`
  too, so `refreshList` **adds the new PDO** to the preloaded list;
- a **negative** serial means delete → the PDO is removed and scheduled into list
  `removeIds`;
- an **id of 0** means a rolled-back insert/update.

If a **gap** is detected in the serial sequence, the cache cannot trust its view
and **invalidates** — logging a warning in middle-tier mode (where it shouldn't
happen) or quietly in local-client mode (where it can).

How gaps arise depends on the **operation mode**:

- **Local client mode** — the app is directly connected to the database with few
  users ([`ModificationTracker.isLocalClientMode()`](../../../../tentackle-session/src/main/docs/session.md)).
  Modifications update the *modification table* inside the transaction (extra
  locks, limited throughput). Updates can be detected via tableserials, but
  **deletions cannot** — a gap appears and the cache invalidates.
- **Middle-tier mode** — the preferred mode, scaling to thousands of users.
  Modifications are recorded in an in-memory **tableserial history**
  ([`TableSerialHistory`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/TableSerialHistory.java))
  and flushed to the modification table after the transaction commits. Combining
  the database serials with the history leaves **no gaps**, so the cache knows
  exactly what to expire, remove, or append.

The local JVM also calls `expire` directly while modifying a PDO, so the modifying
JVM sees its own changes reflected immediately, without waiting for the
`ModificationTracker` round-trip. Other JVMs are notified through the modification
event delivered to `cache.expire(ModificationEvent)` (the `Pdo.listen`
subscription).

### PRELOAD for large tables — an in-memory read model (CQRS)

It is a common misconception that `PRELOAD` is only for small master-data tables.
The decisive enabler is the **in-memory tableserial history**: with a tableserial
configured *and* the application running in **middle-tier mode**, expiration is
surgical and gap-free, so a preloaded cache **never reloads the whole table to stay
coherent**. After the one-time initial load, every commit anywhere in the cluster
expires only the **rows that actually changed** — `selectAll`'s `refreshList`
reloads just the inserted/updated PDOs and drops the deleted ones
(`refreshIds` / `removeIds`), touching a handful of entries no matter how many rows
the table holds.

The key consequence is **completeness**. As soon as the cache is in `PRELOAD` —
whether declared with `--strategy=PRELOAD` or entered implicitly by the first
`selectAll` — it does not merely keep the rows it already holds up to date: **newly
inserted PDOs are added to the cache** as their tableserials arrive. The cache
therefore **mirrors the whole table** and is **fully searchable in memory**:
application code can iterate, filter, or index the complete object set instead of
issuing a query — no row silently absent because it was inserted after the preload.

**But search it as an *eventually consistent* view, not a transactional one.**
A cache is, by its nature, eventually consistent: changes committed by *other* JVMs
become visible only once their modification event has propagated through the
[`ModificationTracker`](../../../../tentackle-session/src/main/docs/session.md) and
the deferred expiration has run on the next access. There is an inherent — usually
small — propagation window during which a remote insert, update, or delete is not yet
reflected locally. (Your *own* committed changes are applied immediately, because the
modifying JVM calls `expire` directly.) So an in-memory search over a preloaded cache
can momentarily return a slightly stale set: a just-inserted row may be missing, a
just-deleted row may still appear, an updated row may carry an older value. This is
fine for the overwhelming majority of reads — reporting, lookups, populating choosers,
read models — but **whenever a decision must be made against the guaranteed-current
state** (uniqueness checks, balance/limit enforcement, anything used to gate a write),
do not rely on a cache scan: re-read or re-validate inside the transaction, where
optimistic locking and the database give you the authoritative answer.

That changes the economics completely: `PRELOAD` becomes viable — and attractive —
for **large tables, including transaction data**, not just small lookup tables. The
fully-populated, continuously-coherent cache is effectively a **read model in the
[CQRS](https://martinfowler.com/bliki/CQRS.html) sense**. The write side mutates
rows through the normal persistence path and stamps the tableserial; the read side
answers `selectAllCached()` / `selectCached…` **entirely from memory**, kept in sync
incrementally and automatically across all JVMs. Reads never touch the database and
never contend with writers.

The price is memory — the whole table lives on the heap — and the **middle-tier
requirement**: local-client mode cannot detect deletions without a serial gap (see
above) and therefore falls back to whole-cache invalidation, which is impractical
for a large table. Size the heap accordingly and keep the cache **read-only** so a
single shared, immutable instance set serves every thread. When the table is simply
too large to hold in RAM, fall back to `LRU` instead.

### Cache-pollution prevention inside a transaction

It is legal to modify another instance of a cached PDO inside a transaction, but **selecting it back
from the cache within that same uncommitted transaction** would poison the cache
if the transaction later rolls back. The cache tracks the tableserials expired per
running transaction (`SerialInfo.TxIdSet`) and, on such an attempt, throws a
`PdoCacheException` that **rolls the transaction back** rather than corrupting the
shared cache.

---

## Domain Context and Multi-Tenancy

Before any lookup the cache normalizes the context (`processContext`):

- a **root context** is replaced by its corresponding **non-root** context, so all
  callers share one cache slot regardless of which root they came from;
- a **read-only** cache replaces it with a **thread-local-session** context, which
  is what lets a single immutable PDO serve every thread.

Because the `CacheKey` includes the context, PDOs of different tenants/sessions
coexist without interfering — the cache is multi-tenant-safe by construction.

---

## Remoting

For remote clients or servers the cache always pulls updates **from the upstream
remote cache**, never the local database — the `selectForCache` /
`selectAllForCache` / `getExpiredTableSerials` calls transparently dispatch to the
remote delegate when the session is remote (see
[`AbstractPersistentObject`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/AbstractPersistentObject.java)
and [TRIP](../../../../tentackle-core/src/main/docs/trip.md)). This keeps a chain of
caches (client → middle-tier → database) coherent.

---

## Preloading Caches at Startup (`@PreloadCache`)

A `PRELOAD` cache is filled **lazily** — the first `selectCached…` that misses pulls the whole
table. That is unnoticeable next to a local database, but expensive on a slow link: each cached
entity costs a roundtrip of its own carrying a complete table, plus — whenever the
[`ModificationTracker`](../../../../tentackle-session/src/main/docs/modification-tracking.md) does not
already know the table — another one to establish its baseline serial. And the user pays all of it
interactively, at the moment of first use.

`@PreloadCache` moves that cost to startup and collapses it into **a single roundtrip** that carries
every preloading cache's content *and* the server's serial baseline. It is the content-level
counterpart to
[`@PreloadRemoteDelegate`](../../../../tentackle-database/src/main/docs/database.md#preloading-remote-delegates),
which solves the same problem for the delegate lookups. The two pair naturally: first the delegates,
then the data they serve.

### Declaring it

[`@PreloadCache`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/PreloadCache.java)
is placed on the **persistence implementation class** — not in the model and not on the wurblet line:

```java
@PersistentObjectService(Security.class)
@PreloadRemoteDelegate
@PreloadCache
public class SecurityPersistenceImpl extends AbstractPersistentObject<Security, SecurityPersistenceImpl> { ... }
```

The annotation is meta-annotated with `@Service`, so the
[build](../../../../tentackle-common/src/main/docs/services.md) collects the annotated class names in
`META-INF/services/org.tentackle.persist.PreloadCache`.
`PersistenceUtilities.getPreloadCacheClassNames()` reads them as plain strings — the classes
themselves are not loaded for the lookup — and `getPreloadingCaches()` instantiates and validates
them. Tentackle itself annotates `SecurityPersistenceImpl` and `StoredBundlePersistenceImpl`: the
[security rules](security.md) and the [database-backed resource bundles](../../../../tentackle-i18n/src/main/docs/i18n.md)
are read at login on every tier, so nobody ever benefits from loading them lazily.

### How it runs

[`AbstractApplication.finishStartup()`](../java/org/tentackle/app/AbstractApplication.java) calls the
overridable `preloadCaches()`, which creates a
[`PdoCacheOperation`](../java/org/tentackle/pdo/PdoCacheOperation.java) and invokes
`preloadPdoCaches()`. What that does depends on the tier:

| Session | Behavior |
|---------|----------|
| **Remote** — a client, or a server that is itself a client of another server | `preloadCachesFromRemote` in a background thread; **one** `getPreloadingCachesContent` call fetches every cache at once. |
| **Local + interactive** — a desktop client connected directly to the database | `preloadCachesFromDatabase` in a background thread, so the login window appears immediately. |
| **Local + non-interactive** — server, daemon or batch job | `preloadCachesFromDatabase` synchronously, on the main thread. |

The two background variants run on a **cloned session** (`clone("PL")`, grouped with the parent's
session id). Preloading may take a while and must not hold the main session long enough to risk a
timeout; grouping ties the clone's lifetime to its parent so it cannot be orphaned. They return a
`CountDownLatch` to wait on — the synchronous case returns `null`.

A **server** blocks on that latch before `finishStartup()` returns. Its own clients preload from it
and expect its caches to be initialized already; bringing the remote services up cold would simply
push the load time onto whichever client connects first.

> Preloading is skipped entirely when the `notracker` property is set — without the modification
> tracker all caches are [globally disabled](application.md) anyway.

### What travels — and why the serials travel with it

`PdoCache.getPreloadContent(context)` snapshots a cache into a
[`PdoCachePreloadContent`](../java/org/tentackle/pdo/PdoCachePreloadContent.java): the PDO list plus
one [`PdoCachePreloadTableInfo`](../java/org/tentackle/pdo/PdoCachePreloadTableInfo.java) per table,
holding the table name and the `IdSerialTuple` — the modification row id **and** serial — the
server's tracker had at that moment. (A cache for a `MULTI`-table inheritance hierarchy has more than
one table, hence the array.) Both records are `@TripDeduplicatable(NEVER)` / `@TripReferencable(NEVER)`:
records are deduplicated and back-referenced by default, but each of these occurs exactly once in the
transfer, so [TRIP](../../../../tentackle-core/src/main/docs/trip.md) would keep that bookkeeping for
nothing.

The serials are the point. `applyPreloadContent` builds the cache's `SerialInfo`s from the
**transmitted** `idSerial` rather than asking the local tracker — which would cost another roundtrip
per table and, worse, could pick up a serial newer than the data it is being paired with. The client
then feeds the same values into its own tracker through
`PdoModificationTracker.setIdSerialForName(...)`
(see [Modification Tracking](../../../../tentackle-session/src/main/docs/modification-tracking.md#seeding-serials-without-a-roundtrip)).
Cache and tracker therefore start from the exact baseline the server held when it took the snapshot:
no serial gap that would invalidate the freshly filled cache, and no modification silently skipped.
From there the ordinary machinery described under *Coherency* takes over.

### Guards

- Only caches that are **preloading**, **read-only** and **enabled** are preloaded. A `@PreloadCache`
  on a class with an `LRU`/`LFU`/`FORGET`, `--mutable` or absent cache is a mistake: it is skipped and
  logged as a warning naming the class and asking you to remove the annotation. A merely *disabled*
  cache is skipped quietly, at info level — that is a configuration choice, not an error.
- `applyPreloadContent` throws a `PdoCacheException` if the cache is already initialized
  (`isInitialized()`, i.e. it has serial infos) or if the content is for a different PDO class.
- If a cache happens to be initialized by regular use while the preload is in flight, its content is
  discarded with a warning rather than merged — the running cache is already coherent.

### When to preload

- **Yes — the handful of caches a client touches at startup or login, over a high-latency or
  low-bandwidth WAN link.** This is the case the feature exists for, and it is the same startup set
  you would weigh for `@PreloadRemoteDelegate`.
- **Yes — a middle-tier server whose [read model](#preload-for-large-tables-an-in-memory-read-model-cqrs)
  must be warm before the first client connects.** The server-side latch exists precisely for this.
- **No — the whole entity model.** Every annotated class costs server work, bandwidth and client heap
  on *every* startup, and converts a lazy miss that might never happen into a mandatory upfront
  transfer. Preloading a large table to every client is usually a net loss.
- **No — anything that is not a read-only `PRELOAD` cache.** Those are rejected by design.
- On a directly-connected, non-interactive tier this is **cache warming**, not latency avoidance: the
  gain is a predictable startup cost instead of a first-user penalty.

---

## Security

Cache persistence operations run on behalf of the **current thread's user**, so
restrictive `ReadPermission` rules on a cached entity would make the cache behave
differently per user — which is almost never intended. Prefer `ViewPermission`
rules for cached entities; see
[session.md](../../../../tentackle-session/src/main/docs/session.md).

---

## Generated Accessors

The `PdoCache` wurblet adds these methods to the persistence interface (`<Key>` is
each declared index / unique domain key):

| Method                         | Effect                                                                     |
|--------------------------------|----------------------------------------------------------------------------|
| `selectCached(id)`             | By ID via cache; load from storage if missing.                             |
| `selectCachedOnly(id)`         | By ID; **don't** load if missing (cache-only peek).                        |
| `selectCachedBy<Key>(key)`     | By unique key via cache; load if missing.                                  |
| `selectCachedOnlyBy<Key>(key)` | By unique key; cache-only.                                                 |
| `selectAllCached()`            | The whole table via cache.                                                 |
| `selectAnyCached(ids)`         | A subset by IDs via cache.                                                 |
| `select…ForCache(…)`           | Internal: load for the cache, dispatching to the remote cache when remote. |

When an entity is **not** cached, `AbstractPersistentObject` provides default
implementations of `selectCached…` that simply delegate to the non-cached
`select…`, so calling code can use the cached variants uniformly.

You can also reach the cache directly via
[`Pdo.getCache(Class)`](../java/org/tentackle/pdo/Pdo.java) for statistics,
`invalidate()`, strategy changes, or to add/remove indexes at runtime.
`PdoCache.setAllEnabled(false)` disables **all** caches globally (useful for
debugging or batch jobs).

---

## Rules of Thumb

- **Small table → default.** A read-only `PRELOAD` cache without a tableserial is
  ideal for the majority of master-data entities.
- **Large table that fits in RAM → tableserial + `PRELOAD` (CQRS read model).** In
  middle-tier mode the in-memory tableserial history keeps a fully preloaded cache
  coherent by reloading only the changed rows, so `PRELOAD` works even for large and
  **transactional** tables — an in-memory query side that never hits the database.
- **Large table that doesn't fit in RAM → tableserial + LRU.** Add the
  `[tableserial]` option and use a reasonably sized read-only `LRU` cache.
- **Run a middle tier for write-heavy / multi-user setups.** Deletion- and
  rollback-detection without whole-cache invalidation needs the tableserial
  history, which only middle-tier mode maintains.
- **Never mutate a cached key.** Changing the value behind an index on a shared
  (immutable) cached PDO is what triggers emergency invalidation — let the cache
  hand out immutable instances and copy if you must edit.
- **Don't select a just-modified PDO from the cache in the same transaction** — the
  cache deliberately rejects this to avoid pollution.
- **Treat cache searches as eventually consistent.** Other JVMs' changes appear only
  after a small propagation delay, so a cache scan may be momentarily stale. Read or
  re-validate inside the transaction when gating a write on the current state.
- **Over a slow link, `@PreloadCache` the startup set — and only that.** Filling the caches a client
  needs at login in one roundtrip removes the per-entity latency the user would otherwise feel;
  annotating the whole model just moves the cost to every startup.
- **Use `ViewPermission`, not `ReadPermission`, on cached entities.**

## See Also

- [pdo.md](pdo.md) — the PDO model and `DomainContext` the cache is keyed on.
- [immutable.md](../../../../tentackle-core/src/main/docs/immutable.md) — why and how cached PDOs are made immutable.
- [snapshot.md](snapshot.md) — `copy()` a cached PDO to get an editable instance.
- [session.md](../../../../tentackle-session/src/main/docs/session.md) — the `ModificationTracker`, tableserial and operation modes.
- [modification-tracking.md](../../../../tentackle-session/src/main/docs/modification-tracking.md) — the serials the cache expires on, and how a preload seeds them.
- [database.md](../../../../tentackle-database/src/main/docs/database.md#preloading-remote-delegates) — `@PreloadRemoteDelegate`, the companion of `@PreloadCache` for slow links.
- [application.md](application.md) — where `preloadCaches()` runs in the startup sequence.
- [model-definition.md](../../../../tentackle-model/src/main/docs/model-definition.md) — the `cached` / `tableserial` model options.
- [persistence-wurblets.md](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md) — the `PdoCache` wurblet that generates the cache.
