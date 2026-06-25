# Why Tentackle Fits Technical and Scientific Applications

## The core difference: who decides the topology

In a typical **business application**, the deployment topology is a *design choice*. The architect decides how
many tiers there are, where caches live, which services are stateless, how to scale out, and where the database
sits. If those choices turn out wrong, they can be revised: move a service to the cloud, add a load balancer, split
a monolith, introduce a read replica. The constraints are *soft* — budget, SLAs, team structure — and the freedom
is large. Most enterprise frameworks are built on exactly this assumption: **you own the topology, so the framework
can bake topology decisions into the programming model** (one logical database, a web tier designed per
deployment, ORM lazy-loading that needs a live transaction, request/response services shaped for a known network).

In a **technical or scientific application**, the topology is *imposed from the outside* by hard constraints that
no amount of architecture can negotiate away:

- A JVM that controls a PLC, drives an instrument, or reads a detector **must run physically next to that
  hardware** — on the machine, in the rack, at the beamline. Compute placement is dictated by where the cabling
  ends, not by convenience.
- Data acquisition happens **at the edge**, where the sensors are; the central database may be a building, a site,
  or a continent away.
- The network links between a shop floor, a control room, a remote field station and a central server are **fixed
  in number, bandwidth, latency and reliability** — fieldbus segments, plant LANs, site-to-site WANs, cellular or
  satellite uplinks, sometimes air-gapped or split by a safety/security zone boundary.
- The layout changes when the *plant* changes — a new instrument, a relocated control room, a gateway inserted
  across a security zone — on the timescale of capital projects, not software sprints.

When the topology is non-negotiable, the frameworks that assume you control it become liabilities. **Tentackle is
built the other way around: application logic is completely decoupled from topology, and the topology is expressed
as pure configuration.** You map your code onto whatever physical layout the constraints dictate — and re-map it
when the constraints change — without rewriting a line. This document explains why that property is exactly what
technical and scientific projects need, and which Tentackle features deliver it.

> Tentackle runs on a standard JVM and is **not** a hard-real-time RTOS. Its sweet spot is the *soft-real-time
> application, acquisition and control-coordination layer* that sits above hard-real-time PLCs/firmware and below
> the enterprise — the layer whose placement is constrained by hardware but whose logic is ordinary Java.

---

## Topology as configuration, not code

Everything in [the multi-tier cascade](./tiers.md) follows from one property: the *same* application artifact runs
at *any* position in the JVM hierarchy, and where it sits is decided by a connection **URL**, never by the code.

- A [PDO](../../../tentackle-pdo/src/main/docs/pdo.md) or
  [`Operation`](../../../tentackle-pdo/src/main/docs/operation.md) method behaves identically whether the session
  is local (JDBC in this JVM) or remote (routed over [TRIP](../../../tentackle-core/src/main/docs/trip.md) to
  another node) — Tentackle's principle of **location transparency**
  ([session](../../../tentackle-session/src/main/docs/session.md)).
- A node becomes a database-connected server, a forwarding proxy/gateway, or a leaf client purely by what its
  `url` (and `service`) properties point at.

For a constrained project this is decisive: you do not design a network protocol per deployment and you do not
fork the codebase per site. You take the **one** application and instantiate it as many JVMs as the hardware
demands, wiring them with configuration to match the physical reality:

```
   instrument-attached node ── trip(c|e|q)://gateway ── zone gateway ── trips://core ── jdbc: ── DB
   (must run at the machine)     (lossy field link)     (safety zone)    (control room)     (data centre)
```

Re-layout the plant and you re-point a URL; the business logic, the control code and the data model are untouched.

---

## Hard constraints mapped to Tentackle features

| Constraint imposed by physics / hardware | What Tentackle provides |
|------------------------------------------|--------------------------|
| **Compute must sit where the device is.** Control/acquisition code has to run on the JVM physically wired to the PLC, instrument or detector. | Any JVM is a node in the cascade. An [`Operation`](../../../tentackle-pdo/src/main/docs/operation.md) — Tentackle's home for *verbs* like "dispatch a command to a PLC" or "read an instrument" — executes on the exact tier attached to the hardware, while its results travel anywhere as PDOs. The same control code runs unchanged on a developer laptop and at the machine. |
| **Heterogeneous, constrained, sometimes hostile network links.** Field uplinks are low-bandwidth, high-latency, lossy, NATed, or without a PKI. | [Pluggable TRIP transports](../../../tentackle-core/src/main/docs/trip.md): `trip` (TCP), `trips` (TLS), `tripc`/`tripcs` (deflate compression for thin WAN links), `tripe`/`tripce` (**pre-shared-key** encryption where no PKI exists or the link is air-gapped-adjacent), and `tripq`/`tripcq` (**QUIC/RFC-9000**, which survives packet loss and connection migration on cellular/satellite). TRIP's wire format is compact and **SD-WAN/NAT compatible** — it never leaks host/IP/port back through a gateway. Pick the transport per link by URL scheme; the application is oblivious. |
| **Latency and locality are correctness constraints, not just performance.** A node near a machine cannot round-trip to a central DB for every read. | Push a full middle-tier server next to the hardware. Its [per-tier cache](./tiers.md#caching-across-the-cascade) is a **local operational read model** that answers queries from RAM and keeps serving across brief uplink outages; only commands propagate centrally. Reads trickle up only on a miss; invalidations trickle down through the cascade. |
| **Domain objects must be processed far from the database.** A measurement aggregate, a sample with its full provenance/genealogy, a batch and its components must be usable on an analysis or control node with no live DB connection. | PDOs carry their session and can always reach a working one in *any* JVM, so there is **no `LazyInitializationException`, ever**. Aggregates are first-class: a root entity and its components know each other without a query, so a complete object graph ships to a compute node and is fully usable there. |
| **Topology changes are driven by physical re-layout.** Insert a gateway, relocate a control room, add an instrument. | Insert or remove a tier purely by configuration — point a server's `url` at the database or at another server's `service`. No code change, no per-site build; the cascade depth is limited only by the latency budget. |
| **Safety / regulatory zone isolation.** Process-control networks are separated from office/IT networks by mandated boundaries (e.g. the IEC 62443 / Purdue model). | A Tentackle **proxy server is naturally that zone gateway**: it terminates downstream sessions on one side and opens its own upstream session on the other, with encryption/compression chosen independently per link. The cascade maps one-to-one onto a zoned, defence-in-depth network. |
| **Long-lived, heterogeneous, version-skewed fleets.** Instruments and controllers outlive software cycles; nodes run mixed versions. | Built-in client/server **version checking** (`checkServerVersion` / `VersionIncompatibleException`) and a TRIP **type dictionary** tolerant of class evolution, with a single codebase deployed across the whole fleet. |
| **Mixed and embedded runtimes; reproducibility.** Code runs modular, non-modular, in OSGi, or embedded; scientific results must be reproducible. | Explicit [JPMS](./modules.md) `module-info` with strong encapsulation, the `ServiceFinder` SPI working in **both** modular and non-modular worlds, **deterministic** interceptor ordering, and **immutable, thread-shared** cached PDOs ([immutable](../../../tentackle-core/src/main/docs/immutable.md)) so parallel analysis threads share one consistent snapshot without locking. |

---

## Command/query separation falls out for free

Technical and scientific systems are naturally **write-up / read-local**: commands (a setpoint change, an
acquisition trigger, a recorded measurement) must reach the authoritative store, while queries (dashboards, trend
views, control-loop inputs) must be answered locally and fast. Tentackle's cascade already provides this split —
see [Using the cascade for CQRS on transaction data](./tiers.md#using-the-cascade-for-cqrs-on-transaction-data).
Commands climb to the database-connected tier inside a `READ_WRITE` transaction; queries are served from each
tier's cache as an incrementally-maintained read model, kept current by the `tableserial`-driven change feed that
ripples down the cascade. A continuously-updated projection of a high-churn measurement or event table is therefore
viable right at the edge, with the authoritative state staying strongly consistent at the core.

---

## A grounded picture

Tentackle's own examples come from this world rather than from generic CRUD. The
[binding documentation](../../../tentackle-core/src/main/docs/binding.md) walks through a *production* value type —
an **aluminium-coil product number** with a fixed composite layout (alloy, quality, thickness, width, length) — and
the [`Operation`](../../../tentackle-pdo/src/main/docs/operation.md) docs name **dispatching a command to a PLC**
and **integrating external systems** as primary use cases. These are the signatures of plant-floor and
laboratory software, where the hardware writes the rules and the software has to fit around it.

A representative deployment:

```
  ┌─────────────────────────┐    fieldbus / PLC link
  │ machine node (JVM)      │───────────────► PLC / instrument        ← compute pinned to the hardware
  │  Operations + local DB  │
  │  cache = live read model│
  └───────────┬─────────────┘
              │ tripcq://  (compressed QUIC over a lossy plant uplink)
  ┌───────────▼─────────────┐
  │ zone gateway (proxy)    │   ← IEC 62443 boundary: terminates one side, dials the other, re-encrypts
  └───────────┬─────────────┘
              │ trips://    (TLS across the site WAN)
  ┌───────────▼─────────────┐
  │ control-room server     │   ← DB-connected core; authoritative store; lock manager; master serial
  │  jdbc: → central DB     │
  └─────────────────────────┘
```

Every box runs the **same** application artifact (or a subset referring to the same domain model); 
only configuration distinguishes them. When the plant is
re-wired, the boxes and links are re-described in properties — not rewritten in Java.

---

## When a business framework would be the better fit

To be fair about the trade-off: if you *do* control your topology — a cloud-native business system free to add
tiers, replicas and queues as it pleases, talking to a single logical database over a fast, reliable, homogeneous
network — then a mainstream stack (Spring/JPA, REST, a service mesh) gives you a larger ecosystem and more hiring
familiarity, and Tentackle's location-transparency machinery solves a problem you don't have. Tentackle earns its
keep precisely when the **topology is dictated to you**: many JVMs, pinned to hardware, across constrained and
heterogeneous links, evolving with the physical plant. That is the technical-and-scientific regime, and it is what
the framework was designed for.

---

## Related Documentation

- [Multi-Tier Cascade](./tiers.md) — how JVMs form the hierarchy of nested servers and clients, and how caching and CQRS work across it.
- [TRIP](../../../tentackle-core/src/main/docs/trip.md) — the compact, transport-pluggable protocol behind the constrained-link support.
- [Tentackle Session](../../../tentackle-session/src/main/docs/session.md) — the location-transparent session every tier programs against.
- [PDO / Persistent Domain Objects](../../../tentackle-pdo/src/main/docs/pdo.md) and [Operations](../../../tentackle-pdo/src/main/docs/operation.md) — entities and the verb-like services that drive hardware and external systems.
- [Modules](./modules.md) — the JPMS module structure underpinning modular, non-modular, OSGi and embedded deployments.
