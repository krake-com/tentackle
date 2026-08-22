# Multi-Tier Cascade — Nested Servers and Clients

## Overview and Motivation

A Tentackle application is not a fixed two- or three-tier deployment. Instead, every JVM is a *node* that
plays one (or two) of three roles, and nodes connect to one another to form a **cascade**: a chain — or a
tree — of servers and clients that ultimately terminates at a database. The same application code runs
unchanged regardless of how deep it sits in the cascade, because of Tentackle's core principle of
**location transparency** (see [session](../../../tentackle-session/src/main/docs/session.md)).

The key insight is that a *middle-tier server is itself a client*. When a server starts up, it opens its own
[`Session`](../../../tentackle-session/src/main/java/org/tentackle/session/Session.java) to a backend. That backend
may be a database — or it may be *another Tentackle server*. A server that connects upstream to another server
acts as a **proxy server** (sometimes called a relay or gateway tier), forwarding the work of its own downstream
clients further up the chain. There is no architectural limit to how many tiers can be stacked this way.

```
   ┌──────────┐   ┌──────────┐   ┌──────────┐        ┌──────────┐
   │  FX/Web  │   │  FX/Web  │   │  FX/Web  │   ...  │  FX/Web  │   ← leaf clients
   │  client  │   │  client  │   │  client  │        │  client  │
   └────┬─────┘   └────┬─────┘   └────┬─────┘        └────┬─────┘
        │ trip://      │              │                   │
        └──────────────┴──────┬───────┴───────────────────┘
                              ▼
                       ┌─────────────┐
                       │ edge server │  ← middle tier (server AND client)
                       │  (proxy)    │     accepts clients, forwards upstream
                       └──────┬──────┘
                              │ trips://
                              ▼
                       ┌─────────────┐
                       │ core server │  ← middle tier connected to the DB
                       │             │     accepts servers/clients, owns the pool
                       └──────┬──────┘
                              │ jdbc: / jndi:
                              ▼
                        ┌───────────┐
                        │  Database │   ← terminal node
                        └───────────┘
```

Each arrow is a Tentackle session. Whether it carries JDBC, JNDI, or [TRIP](../../../tentackle-core/src/main/docs/trip.md)
is decided entirely by the connection **URL**, never by the application code.

---

## What Decides Local vs. Remote: the URL

A node's role is determined by the URL of the session it opens to its backend
(see the class documentation of [`Db`](../../../tentackle-database/src/main/java/org/tentackle/dbms/Db.java)):

| URL form                    | Meaning                                          | Resulting node                        |
|-----------------------------|--------------------------------------------------|---------------------------------------|
| `jdbc:…` (driver)           | Direct JDBC connection                           | **local** — terminal, talks to the DB |
| `jndi:name[\|backend-type]` | Connection from a JNDI/container pool            | **local** — terminal, talks to the DB |
| `trip://host:port/service`  | Remote session over TRIP/TCP                     | **remote** — forwards upstream        |
| `trips://…`                 | TRIP + SSL                                       | remote                                |
| `tripcs://…`                | TRIP + compression + SSL                         | remote                                |
| `tripe://…` / `tripce://…`  | TRIP + pre-shared-key encryption (+ compression) | remote                                |
| `tripq://…` / `tripcq://…`  | TRIP over QUIC (RFC-9000) (+ compression)        | remote                                |
| `tripj://…` / `tripcj://…`  | TRIP over QUIC on Cloudflare quiche (+ compr.)   | remote                                |

[`Session.isRemote()`](../../../tentackle-session/src/main/java/org/tentackle/session/Session.java) reports the result.
A *local* session executes JDBC directly in this JVM; a *remote* session routes every `begin`/`commit`/query/PDO
call over TRIP to the node named in the URL, where a mirror session does the next hop of work. Because a PDO can
always reach a working session in *any* JVM, there is **no `LazyInitializationException`**, no matter how many
tiers separate the PDO from the physical database.

---

## The Three Node Roles

### 1. Leaf client

A desktop ([JavaFX](../../../tentackle-fx/src/main/docs/fx.md)) or [web](../../../tentackle-web/src/main/docs/web.md) application built on
[`AbstractClientApplication`](../../../tentackle-pdo/src/main/java/org/tentackle/app/AbstractClientApplication.java).
It opens a single remote session upstream and never accepts inbound connections. After login, it copies the
server-resolved user id back into its own `SessionInfo`
(`AbstractClientApplication.updateSessionInfoAfterLogin()` via `session.getRemoteSession().getClientSessionInfo()`).

### 2. Middle-tier server

Built on [`AbstractServerApplication`](../../../tentackle-persistence/src/main/java/org/tentackle/persist/app/AbstractServerApplication.java)
and, for the TRIP endpoint, [`ServerApplication`](../../../tentackle-persistence/src/main/java/org/tentackle/persist/app/ServerApplication.java).
This is the node that makes the cascade possible: it is **a server and a client at the same time**.

- As a **client**, it logs into its own backend during `login()`. If that backend URL is `trip…`, the server is a
  *proxy* that forwards upstream; if it is `jdbc`/`jndi`, the server is directly connected to the database.
- As a **server**, it runs a [`TripServer`](../../../tentackle-database/src/main/java/org/tentackle/dbms/trip/TripServer.java)
  that accepts inbound sessions from downstream clients (or downstream servers).

### 3. Two-tier / local client

A node that *is* a leaf client but connects with a `jdbc`/`jndi` URL — i.e., it talks to the database directly with
no middle tier. This is **local client mode**
([`ModificationTracker.isLocalClientMode()`](../../../tentackle-session/src/main/java/org/tentackle/session/ModificationTracker.java)),
intended for simple desktop deployments with one or very few concurrent users. Token locking is disabled in this
mode and in pure remote clients; the
[`LockManager`](../../../tentackle-persistence/src/main/java/org/tentackle/persist/LockManager.java) is only active on
a database-connected server.

---

## How a Server Is Both Sides at Once

The duality lives in `AbstractServerApplication.finishStartup()`:

```java
if (getSession().isRemote()) {
    remoteSessionPool = createRemoteSessionPool();   // proxy: pool of upstream sessions
}
else {
    connectionManager = createConnectionManager();   // DB-connected: own JDBC connections
    sessionPool       = createSessionPool();
}
```

- **DB-connected server.** It owns an [`MpxConnectionManager`](../../../tentackle-database/src/main/java/org/tentackle/dbms/MpxConnectionManager.java)
  multiplexing many logical sessions onto fewer physical JDBC connections, plus a
  [`DbPool`](../../../tentackle-database/src/main/java/org/tentackle/dbms/DbPool.java) `SessionPool` that hands a clean
  session (credentials wiped) to each logging-in client.
- **Proxy server.** It has no `ConnectionManager`. Instead, it builds a
  [`MultiUserSessionPool`](../../../tentackle-session/src/main/java/org/tentackle/session/MultiUserSessionPool.java)
  (`"remote-pool"`, default max 100) of *upstream* sessions — one logical session per downstream client, each
  itself a remote session to the next tier up. The pool is deliberately capped below the DB-connected server's pool
  size, since several proxies fan into one core server.

On top of either, `ServerApplication` starts the inbound endpoint:

```java
// ServerApplication.finishStartup()
if (!getSession().isRemote()) {
    LockManager.getInstance().initialize(getSession());   // locks only on the DB-owning tier
}
super.finishStartup();
tripServer = createRmiServer(connectionClass);            // build the inbound TRIP server
// ... later, in startup():
startTripServer();                                        // tripServer.start()
```

The inbound endpoint(s) come from the server's backend properties, parsed by `TripServer`:

```
service=trips://0.0.0.0:18000/myapp?idle=10&backlog=100      # single endpoint
service_plain=trip://...    service_ssl=trips://...           # multiple endpoints
```

So a single middle-tier process simultaneously **listens** on its `service` URI(s) for downstream nodes and
**dials out** on its `url` to its own backend.

---

## A Request's Path Up and Down the Cascade

Consider a leaf client three tiers below the database invoking a PDO method:

1. **Client → edge server.** The client holds a TRIP proxy to a
   [`RemoteDbConnection`](../../../tentackle-database/src/main/java/org/tentackle/dbms/trip/RemoteDbConnection.java) it
   looked up in the edge server's TRIP `Registry`. It called `login(SessionInfo)` once and received a
   [`RemoteDbSession`](../../../tentackle-database/src/main/java/org/tentackle/dbms/trip/RemoteDbSession.java); every
   PDO call travels through that session and its per-session *remote delegates*.
2. **Edge server.** `RemoteDbConnectionImpl.login()` created a server-side session bound to one slot of the edge
   server's `remote-pool`. That pooled session is **itself a remote session** pointing at the core server, so the
   edge server now replays the client's call upward — it is a client of the core server.
3. **Core server.** Receives the call on *its* TRIP endpoint, binds it to a pooled session backed by the
   `MpxConnectionManager`, and executes the actual JDBC against the database.
4. **Return.** Results (PDOs, with their `DomainContext` and serialized via TRIP) flow back down the same chain.
   TRIP is **SD-WAN compatible**: it never leaks a tier's host/IP/port back downstream, so NAT and relays work, and
   each new remote proxy costs no extra round-trip.

Server-side sessions are cleaned up automatically: each `TripServer` runs a cleanup thread
(`RemoteDbSessionImpl.startCleanupThread`) that closes sessions whose downstream client died (default timeout 30 s,
polled every 1 s), and on shutdown `ServerApplication.cleanup()` closes all open sessions and stops the endpoint.

---

## Session Grouping and Liveness Across Tiers

To keep the cascade consistent, the sessions a proxy opens upstream are **grouped** with the proxy's own main
session (named `"SRV"`):

```java
// AbstractServerApplication.createRemoteSessionPool()
if (getSession().getSessionGroupId() == 0) {
    getSession().groupWith(getSession().getSessionId());
}
```

All pooled upstream sessions join that group, so if the proxy's main session dies (e.g., its
[`ModificationTracker`](../../../tentackle-session/src/main/java/org/tentackle/session/ModificationTracker.java) loses
contact with the upstream server) the whole group is torn down rather than leaving orphaned half-open chains.

Remote sessions are kept from idling out by the
[`SessionKeepAliveDaemon`](../../../tentackle-session/src/main/java/org/tentackle/session/SessionKeepAliveDaemon.java),
which pings upstream on each session's `keepAliveInterval` — pinging asynchronously so a slow long-haul link in the
cascade cannot block the dispatcher.

---

## Caching Across the Cascade

Caching is what makes deep cascades pay off rather than just adding latency. The
[PDO cache](../../../tentackle-pdo/src/main/docs/cache.md) is not a single shared store; **every node in the
cascade owns its own independent [`PdoCache`](../../../tentackle-pdo/src/main/java/org/tentackle/pdo/PdoCache.java)
per cached entity**. Stacked along a chain, those caches form a multi-level cache hierarchy — client over edge
server over core server over database — and a row read once at the top is then held in memory at *every* tier it
passed through. The two halves of the story are: reads **trickle up** until they hit a cache, and invalidations
**trickle down** until they reach every leaf.

### Reads trickle up, results trickle down

A cache lookup never reaches straight for the database. When a tier's cache misses, the generated `selectCached…`
accessor calls `selectForCache` / `selectAllForCache` / `selectAnyForCache` on its PDO. Because the session is
remote, those calls dispatch over TRIP to the **upstream tier's remote delegate**
([`AbstractPersistentObject`](../../../tentackle-persistence/src/main/java/org/tentackle/persist/AbstractPersistentObject.java)):

```java
// AbstractPersistentObject — the cache fetch is location-transparent
public List<T> selectAllForCache() {
    if (getSession().isRemote()) {
        return getRemoteDelegate().selectAllForCache(context);   // ask the tier above
    }
    ...   // local: read from the database
}
```

Crucially, the upstream tier does **not** answer from the database directly. Its delegate resolves the request
**through its own cache**
([`AbstractPersistentObjectRemoteDelegateImpl`](../../../tentackle-persistence/src/main/java/org/tentackle/persist/trip/AbstractPersistentObjectRemoteDelegateImpl.java)):

```java
// server side: a cache fetch is served from THIS tier's cache, recursing further up on a miss
public List<T> selectAllForCache(DomainContext cb) {
    setDomainContext(cb);
    return dbObject.selectAllCached();      // selectCached(id) / selectAnyCached(ids) likewise
}
```

So a miss recurses upward one tier at a time, and the **first tier that has the PDO cached short-circuits the
walk** — the value never travels further than it must. Only when the recursion reaches the DB-connected tier and
*its* cache also misses does a physical `SELECT` happen. The loaded PDO (carrying its `DomainContext`, serialized
via [TRIP](../../../tentackle-core/src/main/docs/trip.md)) then flows back down, and **each tier on the return path
adds it to its own cache**. The next reader on any branch below finds it locally.

```
   read miss climbs ▲                          ▼ result descends, caching at each hop
   ┌──────────┐                          ┌──────────┐
   │  client  │  selectCached(id) miss   │  client  │  cache ← PDO   (next read: local hit)
   └────┬─────┘ ───────────────►         └────▲─────┘
        │ selectForCache (TRIP)               │
   ┌────▼─────┐                          ┌────┴─────┐
   │   edge   │  selectCached(id) miss   │   edge   │  cache ← PDO
   └────┬─────┘ ───────────────►         └────▲─────┘
        │ selectForCache (TRIP)               │
   ┌────▼─────┐                          ┌────┴─────┐
   │   core   │  selectCached(id) miss   │   core   │  cache ← PDO
   └────┬─────┘ ───────────────►         └────▲─────┘
        │ physical SELECT                     │
   ┌────▼─────┐                               │
   │ Database │ ──────────────────────────────┘
   └──────────┘
```

The practical effect: hot master-data (currencies, units, account types) is loaded from the database **once**,
then served from RAM at the core, every edge, and every client. A leaf miss is usually satisfied by the nearest
server's memory, not a database round-trip, so adding tiers spreads cache pressure instead of multiplying database
load. Because each `CacheKey` includes the `DomainContext`, this stays correct in multi-tenant deployments — every
tier caches per tenant/context independently.

### Invalidations trickle down

Caches at different tiers must not drift apart when data changes. Coherency rides the same
[modification-tracking](../../../tentackle-session/src/main/docs/session.md) channel as ordinary change
notification, propagating from the database tier outward:

- A **DB-connected server** records every modification in its in-memory `tableserial` history and advances a
  monotonic **master serial**.
- Each **remote node** (proxy or leaf) periodically polls the master serial of the tier *directly above it*. When
  it advances, the tier requests the changed table serials for the affected range —
  `selectAllWithExpiredTableSerials(context, oldSerial, maxSerial)` — which again dispatches up the chain, so each
  tier learns precisely which rows changed without scanning anything.
- The advancing serial is delivered as
  [`MasterSerialEvent`](../../../tentackle-session/src/main/java/org/tentackle/session/MasterSerialEvent.java)s; a
  proxy **re-publishes** them to *its* own downstream nodes, so the signal ripples tier by tier until it reaches
  every leaf.
- On each node, the resulting
  [`ModificationEvent`](../../../tentackle-session/src/main/java/org/tentackle/session/ModificationListener.java) is
  routed by name (typically the table name) to the cache's `expire` listener (subscribed via `Pdo.listen`). The
  cache then performs **surgical expiration**: positive serials mark PDOs expired (reloaded on next access),
  negative serials remove deleted PDOs, and `selectAll` lists are patched in place — no wholesale flush.

So data climbs on a miss, and invalidation descends on a change, each hop touching only one tier's cache. A row
edited by one client under one proxy is invalidated at the core, rippled down through every proxy, and re-fetched
lazily on next access by every other client — keeping the entire chain of caches coherent.

### Why role matters for coherency

The surgical, gap-free expiry above depends on the `tableserial` **history** that only middle-tier / DB-connected
nodes maintain ("connected to the database **or to another middle-tier server**"). A bare two-tier
local client has no upstream history to combine with, so it falls back to the coarser
scheme: modifications hit the modification table inside the transaction, updates are detected via tableserials, but
deletions show up only as a gap and force a **full cache invalidation**. This is the deliberate trade-off that
makes local-client mode simple for small deployments and middle-tier mode scale to thousands of users — and it is
yet another behavior selected automatically by a node's position in the cascade, with no application code change.

---

## Using the Cascade for CQRS on Transaction Data

The two flows described above — writes that climb to the authoritative tier, reads that are served from caches and
kept current by a change feed — are exactly the shape of **CQRS** (Command/Query Responsibility Segregation).
Tentackle does not ship a "CQRS framework", but the cascade gives you all the parts to build read/write separation
yourself, and it works for *transaction data*, not just slow-moving master data.

### Mapping CQRS onto the cascade

| CQRS concept                         | Tentackle building block                                                                                                                                                                                                                                                                                        |
|--------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Command** (state change)           | A `READ_WRITE` ([`TransactionWritability`](../../../tentackle-session/src/main/java/org/tentackle/session/TransactionWritability.java)) PDO `save`/`delete`, or an [`Operation`](../../../tentackle-pdo/src/main/docs/operation.md), routed up the cascade to the **DB-connected write tier**.                  |
| **Write model**                      | The authoritative entities at the write tier; it advances the `tableserial`/master serial on every commit.                                                                                                                                                                                                      |
| **Query**                            | A `READ_ONLY` read, served from a cache via `selectCached…` / `selectAllCached`.                                                                                                                                                                                                                                |
| **Read model / materialized view**   | The per-tier [`PdoCache`](../../../tentackle-pdo/src/main/docs/cache.md) and its `selectAll` lists — a denormalised, query-optimised projection held in RAM close to the reader.                                                                                                                                |
| **Change feed / projection updater** | The `tableserial`-driven [modification events](../../../tentackle-session/src/main/docs/session.md) and application-defined [`MasterSerialEvent`](../../../tentackle-session/src/main/java/org/tentackle/session/MasterSerialEvent.java)s that ripple down the cascade and patch each read model incrementally. |

```
        COMMANDS (climb, strongly consistent)        QUERIES (served locally, eventually consistent)
   ┌──────────┐                                     ┌──────────┐
   │  client  │  save()/Operation ─────────────►    │  client  │  selectAllCached()  ◄── read model (cache)
   └────┬─────┘                                     └────▲─────┘
        │ command up the cascade                         │ master-serial push (change feed) down the cascade
   ┌────▼─────┐                                     ┌────┴─────┐
   │ write    │  commit → tableserial++  ─────────► │ read     │  refreshIds / removeIds (incremental projection)
   │ tier(DB) │       master serial advances        │ tiers    │
   └──────────┘                                     └──────────┘
```

### Why it works for transaction data, not just master data

A CQRS read model is only useful if it can keep up with a **high-churn** table. The cache does this without ever
reloading the whole projection:

- **Incremental projection maintenance.** A materialised `selectAllCached` list is not flushed on every write.
  When the change feed reports new serials, `refreshList` reloads **only** the changed rows (`refreshIds`) and
  drops deleted ones (`removeIds`), patching the list in place — see
  [PDO Cache](../../../tentackle-pdo/src/main/docs/cache.md). A busy invoice or booking table stays current at a
  cost proportional to the *delta*, not the table size.
- **Bounded memory for large tables.** Where the full projection won't fit, an `LRU`/`LFU` cache keeps the hot
  working set as the read model while still answering key lookups; `PRELOAD` suits the bounded reference tables.
- **Gap-free deltas.** The middle-tier `tableserial` **history** guarantees the change feed has no gaps (see
  [above](#why-role-matters-for-coherency)), so a transactional read model never silently misses an update — the
  property that makes a continuously-updated projection trustworthy.
- **Push-based invalidation.** Because master serials are polled and `MasterSerialEvent`s pushed down the cascade,
  a command committed by one client invalidates the read models of every other client within one poll interval —
  no client has to ask "did anything change?" per query.

### Read/write split as a deployment choice

Command and query paths are just sessions, so you can place them on **different tiers or hosts** purely by
configuration:

- Run a **write tier** connected to the primary database (`url = jdbc:…`, accepting `READ_WRITE` commands).
- Run one or more **read tiers** as proxy servers (`url = trips://write-tier…` or pointed at a **database read
  replica**) that serve `READ_ONLY` queries from their caches. Clients on the query path never touch the write
  tier's connection pool; they scale horizontally as more read tiers are added.

Mark query transactions `READ_ONLY` and command transactions `READ_WRITE` so the backend (and any replica routing)
can optimize each accordingly.

### Honest constraints

This is a *pattern enabled by* the cascade, not a turnkey feature — know the trade-offs:

- **The read side is eventually consistent.** A read model lags the write tier by up to one master-serial poll
  interval. Use it for queries that tolerate that; for read-your-own-write inside a command, the modifying JVM
  already sees its change immediately (the local `expire` call), but *other* readers do not.
- **No event sourcing, one source of truth.** The "events" are `tableserial`-driven invalidations plus optional
  `MasterSerialEvent`s — the authoritative state is the live table, not an append-only log. The read model is a
  cache of that table, rebuilt on demand, not a separately persisted store.
- **Don't pollute the read model from a command.** Selecting a cached PDO you have modified within an uncommitted
  transaction is rejected with a `PdoCacheException` that rolls the transaction back, precisely so a command in
  flight cannot leak into the shared read model — keep command mutations off the cached, read-only instances.
- **Per-client projections are your code.** [`@MasterSerialEventService`](../../../tentackle-session/src/main/java/org/tentackle/session/MasterSerialEvent.java)
  handlers let each client react to the same write-tier event differently (the server stays oblivious), which is
  ideal for client-specific read models — but the projection logic itself is application code.

---

## Configuring a Cascade

Everything is configuration; no code changes are needed to add a tier.

**Core server** (terminal, owns the DB):
```properties
url     = jdbc:postgresql://db.internal:5432/erp
service = trips://0.0.0.0:28000/ErpCore
```

**Edge / proxy server** (forwards to the core server, serves clients):
```properties
url     = trips://core.internal:28000/ErpCore
service = trips://0.0.0.0:18000/ErpEdge
```

**Leaf client** (connects to the nearest server):
```properties
url = trips://edge.example.com:18000/ErpEdge
```

Point a server's `url` at a database to make it terminal, or at another server's `service` to insert it as another
tier. The cascade depth is limited only by latency budget, not by the framework.

---

## Related Documentation

- [Tentackle Session](../../../tentackle-session/src/main/docs/session.md) — the location-transparent session abstraction every tier programs against.
- [TRIP](../../../tentackle-core/src/main/docs/trip.md) — the wire protocol that carries remote sessions, delegates, and PDOs between JVMs.
- [PDO / Persistent Domain Objects](../../../tentackle-pdo/src/main/docs/pdo.md) — the objects that travel the cascade.
- [PDO Cache](../../../tentackle-pdo/src/main/docs/cache.md) — the cache internals (indexes, strategies, tableserial expiry) behind the stacked, per-tier caches described above.
- [Modules](./modules.md) — where the server, session, and database modules sit in the overall module graph.
- [Location Transparency vs. REST, RMI and Spring Remoting](./remoting-comparison.md) — what the tier boundary costs in each approach, and why a cascade is not a shape REST or RMI can take.
