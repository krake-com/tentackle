![Tentackle](./src/main/docs/assets/tentackle_Logo_V6.png)

**Tentackle** is a Java framework for building modular, multi-tier,
enterprise-grade applications — from the database up to a JavaFX desktop client —
with a single, location-transparent programming model. It is Open Source and
licensed under LGPL 2.1.

## Why Tentackle?

Five things set Tentackle apart from other Java frameworks:

1. **Persistent Domain Objects (PDOs), not an ORM.** A PDO unifies persistence
   and domain logic behind one interface via runtime-injected proxies — emulated
   multiple inheritance that keeps the two concerns fully independent. There are
   no lifecycle states and **no `LazyInitializationException`, ever**.
   See [PDO](tentackle-pdo/src/main/docs/pdo.md) and
   [PDOs vs. Traditional ORMs](tentackle-pdo/src/main/docs/pdo-vs-orm.md).

2. **Write the domain logic once, run it on any tier.** The same artifact works
   whether the database is local, behind a middle-tier server, or reached through
   a cascade of nested servers — transparently, over the
   [TRIP](tentackle-core/src/main/docs/trip.md) protocol. See the
   [Multi-Tier Cascade](src/main/docs/tiers.md).

3. **Correctness is the default, not an add-on.** Aggregates as first citizens
   enforcing DDD rules, optimistic (`serial`) locking always on, 
   validation is declarative and travels with the object,
   shared objects can be made immutable, and aggregates revert atomically via
   snapshots. See [Correctness First](src/main/docs/correctness-first.md).

4. **One model, generated everywhere — at build time.** The model lives in the
   PDO interface and drives generation of the persistence layer, DDL and database
   migrations through the [Wurbelizer](tentackle-wurblets/src/main/docs/wurblets.md).
   Service discovery is resolved at compile time by the
   [ServiceFinder SPI](tentackle-common/src/main/docs/services.md) — no runtime
   classpath scanning, fast startup, and it works in both modular and classpath mode.

5. **True JPMS, self-contained apps.** Every module is a real
   [JPMS module](src/main/docs/modules.md); applications ship as
   jlink/jpackage images with built-in client
   [auto-update](tentackle-update/src/main/docs/update.md) and rich
   [JavaFX](tentackle-fx-rdc/src/main/docs/rdc.md) desktop clients. TRIP replaces
   RMI/JOS with a compact, pluggable, SD-WAN-friendly transport.

For a sense of where Tentackle excels, read
[Why Tentackle Fits Technical and Scientific Applications](src/main/docs/technical-scientific.md).

## Where to go next

- 🚀 **[Quickstart](src/main/docs/quickstart.md)** — generate, build, and run a
  complete multi-tier application in minutes.
- 📚 **[Table of Contents](src/main/docs/toc.md)** — every document, grouped by topic.
- 🔎 **[Index](src/main/docs/index.md)** — look up a concept and jump straight to it.
- 🧾 **[Java site docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/)** — as a reference.
