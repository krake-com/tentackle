![Tentackle](./src/main/docs/assets/tentackle_Logo_V6.png)

**Tentackle** is a Java framework for building modular, multi-tier,
enterprise-grade applications — from the database up to a JavaFX desktop client —
with a single, location-transparent programming model. It is Open Source (LGPL 2.1),
available from [Maven Central](https://search.maven.org/search?q=org.tentackle),
and requires Java 25 or later (older LTS lines are maintained on the `java21`,
`java17`, `java11` and `java8` branches).

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
   a cascade of nested servers — transparently, over
   [TRIP](tentackle-core/src/main/docs/trip.md), a compact binary protocol that
   replaces RMI/JOS with pluggable, SD-WAN-friendly transports (TLS, compression,
   pre-shared-key encryption, QUIC). The topology is pure configuration: a
   connection URL decides where each node sits. See the
   [Multi-Tier Cascade](src/main/docs/tiers.md).

3. **Correctness is the default, not an add-on.** Optimistic (`serial`) locking
   is always on, validation is declarative and travels with the object,
   aggregates are first-class citizens enforcing DDD boundaries and reverting
   atomically via snapshots, and shared objects can be made immutable. Whatever
   goes wrong fails loudly and early instead of corrupting data quietly. See
   [Correctness First](src/main/docs/correctness-first.md).

4. **One model — generated everywhere, and queryable by tools.** The model lives
   in the PDO interface and drives generation of the persistence layer, DDL and
   database migrations through the
   [Wurbelizer](tentackle-wurblets/src/main/docs/wurblets.md). Service discovery
   is resolved at compile time by the
   [ServiceFinder SPI](tentackle-common/src/main/docs/services.md) — no runtime
   classpath scanning, fast startup, and it works in both modular and classpath
   mode. That model is not locked away in a comment block, though:
   `mvn tentackle-mcp:serve` serves it *relation-resolved* — as the code generators
   see it — to coding agents over MCP, validating a candidate definition against the
   whole model in under a second, and the JavaFX wizards all run headless too. See
   [AI Agent Tooling Support](src/main/docs/ai-agents.md).

5. **True JPMS, self-contained apps.** Every module is a real
   [JPMS module](src/main/docs/modules.md); applications ship as self-contained
   jlink/jpackage images with built-in client
   [auto-update](tentackle-update/src/main/docs/update.md) and rich
   [JavaFX](tentackle-fx-rdc/src/main/docs/rdc.md) desktop clients.

For a sense of where Tentackle excels, read
[Why Tentackle Fits Technical and Scientific Applications](src/main/docs/technical-scientific.md).

## Where to go next

- 🚀 **[Quickstart](src/main/docs/quickstart.md)** — generate, build, and run a
  complete multi-tier application in minutes.
- 📚 **[Table of Contents](src/main/docs/toc.md)** — every document, grouped by topic.
- 🔎 **[Index](src/main/docs/index.md)** — look up a concept and jump straight to it.
- 🤖 **[AI Agent Tooling](src/main/docs/ai-agents.md)** — the MCP model server, the
  headless wizard batch modes, and the bundled agent skills.
- 🧾 **[Java site docs](https://tentackle.org/tentackle/)** — as a reference.

and for the impatient Java developer:

- 🔔 **[Concise Summary](src/main/docs/unexpected.md)** — main facts you might not expect
