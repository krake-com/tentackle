# The History of Tentackle

Tentackle has been in development for a quarter of a century. This page tells where it comes from,
why it was started at all, and how it got to where it is today. Knowing the history helps to
understand why some things look the way they do: the desktop focus, the emphasis on objects rather
than data, the version numbers, the JPMS-first design, the switch from RMI to TRIP — and why a
framework that has been on Maven Central since 2016 has only been documented since 2026.

## Built to get things done

Tentackle was never meant to be *a framework*. Its purpose was — and still is — to get things done:
it is the foundation the applications of [Krake Softwaretechnik](https://krake.de/) and its customers
are built on. Everything in it exists because a real application needed it, and it was published as
open source (LGPL 2.1) from the very beginning.

Those applications share a common profile:

- **In-house applications.** They run inside a company's network and are not accessible from the
  public internet. Their users are employees, not anonymous visitors.
- **Mostly desktop, not web.** An in-house application can rely on a rich desktop client talking
  to its own server tier, instead of being squeezed through a browser. This is why Tentackle cares
  about the desktop ([JavaFX](../../../tentackle-fx-rdc/src/main/docs/rdc.md)) while most of the Java
  world moved to the web.
- **Technical, scientific, and business.** Many of them are technical and scientific applications
  with complex domain logic (see
  [Why Tentackle Fits Technical and Scientific Applications](./technical-scientific.md)). But there are
  also business applications serving many hundreds of concurrent users — which is where the
  [multi-tier cascade](./tiers.md) and the always-on [locking](../../../tentackle-persistence/src/main/docs/locking.md)
  pay off.

## Objects, not data

Tentackle is **object-oriented, not data-oriented.** The Java mainstream went the other way: entities
became plain data holders (the
[anemic domain model](https://martinfowler.com/bliki/AnemicDomainModel.html)), the logic moved into a
service layer, and the network carries data — JSON, DTOs, rows. That works well for applications that
mostly send and receive data. Applications with complex domain logic, however, benefit from a rich
domain model, where objects carry state *and* behavior, inheritance and polymorphism just work, and
any method can be invoked at any time (see [PDOs vs. Traditional ORMs](../../../tentackle-pdo/src/main/docs/pdo-vs-orm.md)).

Two influences shaped this mindset:

- **[Smalltalk](https://en.wikipedia.org/wiki/Smalltalk)** — a world of live objects that are always
  usable. Java is not Smalltalk, of course, and Tentackle persists to relational databases rather than
  to an image. But with Java interfaces, the persistence implementation can be hidden from the domain
  logic almost entirely: in Tentackle, entities *are* just interfaces, and their domain and persistence
  implementations are injected at runtime (see [PDO](../../../tentackle-pdo/src/main/docs/pdo.md)).
- **[Domain-Driven Design](https://en.wikipedia.org/wiki/Domain-driven_design)** — aggregates, root
  entities, and components are first-class citizens. Components are persisted only via their root,
  and components accessed outside their root's domain context are made immutable to uncover
  programming errors as early as possible (see [Correctness First](./correctness-first.md)).

The object orientation reaches all the way down to the wire. Because Tentackle sends real objects
between JVMs and not just data, its remoting protocol [TRIP](../../../tentackle-core/src/main/docs/trip.md#design-principles)
is optimized for **directed cyclic graphs**: shared references and cycles survive the trip, so the
receiver gets the same object graph the sender had — not a flattened copy that has to be stitched
together again.

## Origins (2001–2015)

Tentackle was started in 2001 — the oldest copyright headers
in the code base still read `Copyright (C) 2001`. It grew inside customer projects rather than as an
academic exercise, which explains its pragmatic focus on correctness, multi-user concurrency, and
long-lived desktop clients.

The public git history starts in January 2016 with release **2.0.0**. At that point the framework
already contained the building blocks that still define it today:

- the PDO programming model with separate domain and persistence layers,
- the model DSL and the [wurblets](../../../tentackle-wurblets/src/main/docs/wurblets.md) generating
  code via the [Wurbelizer](https://wurbelizer.org/),
- the SQL backend abstraction and the [SQL maven plugin](../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md),
- remoting hidden from the domain layer,
- a rich desktop client framework in two flavors: Swing (`tentackle-swing-rdc`, with several
  look-and-feel modules) and JavaFX (`tentackle-fx-rdc`),
- scripting via Groovy and Ruby, and a web module.

## From library to full stack

Over the years, Tentackle grew from a persistence and remoting library with a desktop client into a
full-stack framework: from the JDBC driver up to the JavaFX client, plus the maven plugins that
generate, migrate, check, package, and update an application.

### 2016–2017: the 2.x line

- **2016-01** — 2.0.0 is released. Development switches to git-flow, and the artifacts are published
  to [Maven Central](https://search.maven.org/search?q=org.tentackle) via Sonatype OSSRH.
- **2016-04** — database-backed resource bundles move into the new
  [`tentackle-i18n`](../../../tentackle-i18n/src/main/docs/i18n.md) module, together with the
  [i18n maven plugin](../../../tentackle-i18n-maven-plugin/src/main/docs/tentackle-i18n-maven-plugin.md).
- **2016-09** — the [`tentackle-bom`](../../../tentackle-bom/src/main/docs/bom.md) is added.
- **2017-03** — transaction savepoints.
- **2017-04** — the [OSGi](../../../tentackle-osgi/src/main/docs/osgi.md) subproject wraps the
  runtime modules into bundles.
- **2017-07** — components loaded outside the domain context of their root entity become immutable.
- **2017-11** — the monolithic `tentackle-all` test module is split into `tentackle-test-pdo` and
  `tentackle-test-fx-rdc`; 2.1.0 is released.

### 2017–2018: Java 9, JPMS, and farewell to Swing

- **2017-12** — the migration to Java 9 begins.
- **2018-01/02** — the Swing modules move to a legacy subproject and are then removed from the main
  line. They live on in the `java8` branch; from now on, the desktop client is JavaFX only.
- **2018-02** — every module gets a `module-info.java` (see [Tentackle Modules](./modules.md));
  `finalize()` is replaced by the `Cleaner` API, logging uses the `StackWalker`, and a Log4j 2
  provider is added.
- **2018-11** — Nashorn is removed, so there is no default scripting language anymore.
- **2018-12** — 2.2.0, the last release of the 2.x line.

### 2018–2020: tooling

The framework grows a set of maven plugins that take care of the repetitive parts of a project:

- **2018-08** — the first working PDO and operation wizards
  ([`tentackle-wizard-maven-plugin`](../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md)).
- **2019-01** — the [jlink plugin](../../../tentackle-jlink-maven-plugin/src/main/docs/jlinkpackage.md)
  creates self-contained runtime images.
- **2019-06** — the [auto-update](../../../tentackle-update/src/main/docs/update.md) feature for
  desktop clients.
- **2019-07** — the [project archetype](../../../tentackle-project-archetype/src/main/docs/tentackle-project-archetype.md)
  scaffolds a complete multi-tier application.
- **2019-08** — [JSR-223 scripting](../../../tentackle-script-jsr/src/main/docs/jsr.md) in its own
  module, including GraalVM languages.
- **2020-07/08** — migration hints for renaming and dropping tables and columns in the SQL plugin.
- **2020-08** — `jpackage` support produces native installers.

### The version scheme

In January 2019, 8.2.2.0 and 11.2.3.0 were released on the same day. Since then, the first number of
a Tentackle version denotes the Java LTS release it is built for, followed by the feature, minor, and
patch numbers. The development line follows every new Java release as a snapshot (`14.0-SNAPSHOT`,
`16.0-SNAPSHOT`, …), but releases are cut for LTS versions only. Older LTS lines are maintained on
their own branches (`java8`, `java11`, `java17`, `java21`).

| Release line  | Java | First release |
|---------------|------|---------------|
| 2.0           | 8    | 2016-01       |
| 2.1           | 8    | 2017-11       |
| 2.2           | 8    | 2018-12       |
| 8.2, 8.3      | 8    | 2019-01       |
| 11.2 – 11.7   | 11   | 2019-01       |
| 17.8 – 17.14  | 17   | 2021-10       |
| 21.15, 21.16  | 21   | 2023-10       |
| 25.17         | 25   | 2026-01       |
| 25.18         | 25   | 2026-03       |
| 25.19         | 25   | 2026-08       |
| 25.20         | 25   | 2026-10       |

### 2021–2025: Java 17 and Java 21

- **2021-09** — switch to Java 17, followed by language migrations throughout the code base; the
  wurblets generate records instead of classes where appropriate. 17.8.0.0 is released in October.
- **2022-03** — `tentackle-wizard:browse`, a model browser.
- **2023-09** — switch to Java 21; 21.15.0.0 is released in October, 21.16.0.0 in April 2024.
- **2025-09** — switch to Java 25.

### 2026: the big renewal

The move to Java 25 started the most extensive overhaul in the project's history:

- **2026-01** — **TRIP**, the Tentackle Remote Invocation Protocol, replaces Java RMI and Java object
  serialization, including a QUIC transport based on KWIK (see [TRIP](../../../tentackle-core/src/main/docs/trip.md),
  [TRIP over QUIC](../../../tentackle-quic/src/main/docs/quic.md), and
  [Migrate from RMI to TRIP](./migrate_rmi_trip.md)). 25.17.0.0 is released.
- **2026-02/03** — [AtlantaFX](../../../tentackle-fx-atlanta/src/main/docs/atlanta.md) themes give
  the desktop client, the wizards, and the archetype a modern look.
- **2026-04 to 07** — batch modes for the wizard goals make them usable from the command line and by
  coding agents; the [`tentackle-pdo-mock`](../../../tentackle-pdo-mock/src/main/docs/pdo-mock.md)
  module enables database-less unit tests of the domain logic.
- **2026-06** — the documentation set and the website (see [below](#2026-documentation-with-the-help-of-ai)).
- **2026-08** — the [MCP server](../../../tentackle-mcp-maven-plugin/src/main/docs/tentackle-mcp-maven-plugin.md)
  serves the model to coding agents (see [AI Agent Tooling Support](./ai-agents.md)). The tests
  switch from PostgreSQL to H2, so the whole reactor builds without a database.
  [`tentackle-quiche`](../../../tentackle-quiche/src/main/docs/quiche.md) adds a second QUIC
  transport, binding Cloudflare quiche via the JDK's FFM API.
- **2026-09** — [isolated module layers](../../../tentackle-common/src/main/docs/layers.md),
  [JTA/XA](../../../tentackle-jta/src/main/docs/jta.md) support for global transactions, and a
  [keyring API](../../../tentackle-core/src/main/docs/keyring.md) with
  [platform-specific keyrings](../../../tentackle-keyring/src/main/docs/platform-keyring.md).

## A low profile

Tentackle has been on Maven Central since 2016, and its sources were publicly available on
Bitbucket. Yet we always kept a low profile. The reason was simple: there was virtually no
documentation beyond the Javadoc and a thin wiki.

For a mainstream framework, that would be a nuisance. For Tentackle, it was a showstopper. Tentackle
is radically different from the Java mainstream — it is *Java outside the box* (see
[What a Seasoned Java Developer Would Not Expect](./unexpected.md)). Its concepts cannot be guessed
from the experience with other frameworks, and nobody can be expected to reverse-engineer them from
the code. Writing that documentation, however, would have taken years alongside the actual project
work. So Tentackle stayed what it had always been: a tool for those who already knew it.

## 2026: documentation, with the help of AI

This changed in 2026. AI models had become capable of understanding a large existing code base —
not just individual classes, but the concepts behind them and how they depend on each other — and of
writing documentation for it. Without AI, we would not have been able to provide the documentation in
such a short timeframe.

The numbers speak for themselves: the reactor now holds about 120 markdown documents with roughly
300,000 words, all of them added between March and October 2026 — more than half of them in June
alone. Along the way, the old wiki was imported, the README became the public
overview ("Why Tentackle?"), the [Quickstart](./quickstart.md), the [Table of Contents](./toc.md) and
the [Index](./index.md) were added, and the website at [tentackle.org](https://tentackle.org/) is now
built from the same markdown files with MkDocs. In October 2026, the Java comments and markdown docs
were reviewed and streamlined module by module.

The collaboration went both ways: the same period gave Tentackle tooling made for AI agents — the
MCP model server and the headless batch modes of the wizard goals (see
[AI Agent Tooling Support](./ai-agents.md)).

## Moving to GitHub (October 2026)

Now that the documentation is available, there is no reason for a low profile anymore. In October
2026, the repository moved from Bitbucket to GitHub:
[github.com/krake-com/tentackle](https://github.com/krake-com/tentackle).

## What never changed

Technologies came and went — Swing, RMI, Nashorn, Java 8. The ideas behind Tentackle stayed the same:

- a **rich domain model**, where objects carry state and behavior,
- **one location-transparent programming model** from the database up to the desktop, at any tier,
- **correctness first**: optimistic locking, validation, and immutability are always on and fail fast,
- **code generation from the model** instead of runtime magic,
- and, above all, the purpose: **getting things done**.

## See also

- [Correctness First](./correctness-first.md) — the design philosophy.
- [What a Seasoned Java Developer Would Not Expect](./unexpected.md) — where Tentackle deviates from
  the mainstream.
- [Location Transparency vs. REST, RMI and Spring Remoting](./remoting-comparison.md) — why TRIP
  replaced RMI.
- [Tentackle Modules](./modules.md) — how the full stack is layered today.
