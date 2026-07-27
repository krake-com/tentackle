# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Tentackle** is a comprehensive Java application framework for building modular, enterprise-grade multi-tier applications with a single, location-transparent programming model — from a JDBC/SQL database up to a JavaFX desktop client — with full JPMS (Java Platform Module System) support.

- Website: https://tentackle.org/
- Artifacts: [Maven Central](https://search.maven.org/search?q=org.tentackle)
- License: LGPL 2.1
- Developer: Krake Softwaretechnik (https://krake.de/)

## Documentation

This repository ships an extensive prose documentation set. **Consult it before answering architecture questions or making non-trivial changes** — it is the authoritative description of the framework's concepts and conventions.

- Project-wide docs live in `src/main/docs/`.
- Each module documents itself in `<module>/src/main/docs/` (e.g. `tentackle-pdo/src/main/docs/pdo.md`).
- Each module also has a short `<module>/src/site/markdown/index.md` landing page for the generated Maven site (module blurb + links to Javadoc/JXR/reports). It is not a place for prose documentation.

Entry points (all under `src/main/docs/`):

- [`quickstart.md`](src/main/docs/quickstart.md) — generate, build and run a multi-tier app.
- [`toc.md`](src/main/docs/toc.md) — every document, grouped by topic.
- [`index.md`](src/main/docs/index.md) — alphabetical concept → document map.
- [`README.md`](README.md) — the public-facing overview ("Why Tentackle?").

Conceptual deep-dives worth knowing: [`correctness-first.md`](src/main/docs/correctness-first.md), [`tiers.md`](src/main/docs/tiers.md) (multi-tier cascade), [`modules.md`](src/main/docs/modules.md), and [`pdo-vs-orm.md`](tentackle-pdo/src/main/docs/pdo-vs-orm.md).

When adding or changing a concept, keep the relevant module doc, `toc.md`, and `index.md` in sync.

### Documentation website (MkDocs)

`src/mkdocs/` builds the public site at https://tentackle.org/ with MkDocs + Material. See [`src/mkdocs/README.md`](src/mkdocs/README.md).

- The page content is **generated**, never stored: `prepare.py` stages the repo `README.md` (as the site home page) plus every `src/main/docs/*.md` across the reactor into a flat `src/mkdocs/docs/` tree. Both `docs/` and the built `site/` are git-ignored — never edit them, they are overwritten on every build.
- Because the tree is flattened, **every doc basename must be unique across the whole reactor**. Check for a collision before adding a doc.
- `prepare.py` rewrites cross-document links to the flat layout, and rewrites links to Java sources (`../java/.../Foo.java`) to Bitbucket source URLs. Relative links between docs are therefore fine to write naturally.
- The `nav` in `src/mkdocs/mkdocs.yml` mirrors `src/main/docs/toc.md` — when adding a doc, update both.
- Build: `make serve` (live preview), `make build` (strict — fails on broken links), `make clean`. The first build needs network access (the privacy plugin localizes the Roboto font). The site is deliberately GDPR-compliant: no third-party requests, no tracking — don't introduce external assets, CDN links, or analytics.

Use American English spelling for all code comments and documentation (e.g. "behavior", "serialize", "color").

## Build Commands

```bash
# Standard build
mvn clean install

# Build with jlink/jpackage distribution images
mvn -Pjlink clean install

# Skip tests
mvn clean install -DskipTests

# Run tests only
mvn test

# Run a single test class
mvn -Dtest=TestClassName test

# Run a single test method
mvn -Dtest=TestClassName#methodName test

# Build a specific module (with its reactor dependencies)
mvn -pl :tentackle-core -am clean install

# Initialize/populate the test database
mvn -Pinitdb test
```

**Requirements:** Java JDK >= 25, Maven >= 3.9.0, PostgreSQL >= 9.
(Older LTS lines live on the `java21`, `java17`, `java11` and `java8` branches.)

Tests run with locale forced to US English (`-Duser.language=en -Duser.region=US`).

## Module Architecture

This is a 38-module Maven reactor. Per [`modules.md`](src/main/docs/modules.md), modules fall into three kinds, organized in layers:

```
UI Layer (runtime):     tentackle-fx, tentackle-fx-atlanta, tentackle-fx-rdc,
                        tentackle-fx-rdc-poi, tentackle-fx-rdc-update, tentackle-web
Application (runtime):  tentackle-pdo, tentackle-domain, tentackle-persistence, tentackle-update
Database (runtime):     tentackle-database, tentackle-session
Core (runtime):         tentackle-core, tentackle-i18n, tentackle-quic,
                        log/script providers (log-slf4j, log-log4j2v, script-groovy/ruby/jsr)
Common (build+runtime): tentackle-common, tentackle-sql
Build tooling:          tentackle-maven-plugin, tentackle-maven-support, tentackle-build-support,
                        tentackle-sql-maven-plugin, tentackle-i18n-maven-plugin,
                        tentackle-jlink-maven-plugin, tentackle-check-maven-plugin,
                        tentackle-wizard-maven-plugin, tentackle-model,
                        tentackle-wurblets, tentackle-persistence-wurblets
Aggregation/scaffold:   tentackle-bom, tentackle-project-archetype
Test support:           tentackle-pdo-mock, tentackle-test-pdo, tentackle-test-fx-rdc
```

Note the interface/implementation split: interface modules are implemented by separate runtime modules that are injected via the SPI (see below), so the API stays agnostic of the implementation.

- `tentackle-session` (interfaces) is implemented by `tentackle-database`.
- `tentackle-pdo` (interfaces) is implemented by `tentackle-persistence` (persistence side) and `tentackle-domain` (domain side).

Key modules:
- `tentackle-common` — shared build- and runtime foundation: essential annotations (`@Analyze`), datatypes, bundle support, JPMS module support, the service API.
- `tentackle-core` — runtime foundation: TRIP (serialization/RPC), validation, binding, reflection/dynamic-proxy/interceptors, logging API, scripting API, tasks/daemon, preferences.
- `tentackle-sql` — low-level database backend abstraction (build + runtime).
- `tentackle-pdo` — Persistent Domain Object interfaces (entities, aggregates, components, operations, security, number sources).
- `tentackle-domain` — domain-logic layer base classes (PDO domain side).
- `tentackle-persistence` — PDO-aware persistence layer (PDO persistence side).
- `tentackle-model` — the model API describing the application's entities and relationships; consumed by the wurblets, the PDO wizard and the SQL plugin.
- `tentackle-fx-rdc` — Rich Desktop Client framework (tables, trees, forms) built on PDOs and operations.
- `tentackle-bom` — Bill of Materials for downstream projects.
- `tentackle-project-archetype` — Maven archetype for scaffolding new multi-tier projects.

## Key Architectural Patterns

### PDO / Persistent Domain Object Pattern
See [`pdo.md`](tentackle-pdo/src/main/docs/pdo.md) and [`pdo-vs-orm.md`](tentackle-pdo/src/main/docs/pdo-vs-orm.md).

- **PDO** (`PersistentDomainObject`) — interface extending the persistence- and domain interfaces (`PersistentObject` and `DomainObject`).
- **Domain Object** (`AbstractDomainObject<T,P>`) — base class implementing the domain logic. Extended by the application's domain objects implementing the concrete domain interfaces.
- **Persistent Object** (`AbstractPersistentObject<T,D>`) — base class implementing the persistence logic. Extended by the application's persistent objects implementing the concrete persistence interfaces.
- The domain- and persistence implementations are injected at runtime, creating the PDOs as dynamic proxies. This emulates multiple inheritance for classes in Java.
- Clear separation between persistence and business concerns: the persistence layer is agnostic of the domain layer and vice versa.
- Aggregates are first-class citizens: the persistence layer knows about root entities and their components, and each component knows its root entity without a database query (see [`root-columns.md`](tentackle-pdo/src/main/docs/root-columns.md)). DDD rules are enforced.
- PDOs are defined by a model held in a special comment block in the PDO interface source. The model is picked up by the code generation tools to generate the persistence-layer interfaces and implementations.
- Because the persistence layer is remoting-capable, any PDO method can be invoked in any JVM — whether directly connected to a database or indirectly via a remote server (**no `LazyInitializationException`, ever**). PDOs are never in a degraded/detached state.
- **Operations** ([`operation.md`](tentackle-pdo/src/main/docs/operation.md)) carry behavior that is not tied to a single entity.

### Correctness-First Guarantees
See [`correctness-first.md`](src/main/docs/correctness-first.md). Optimistic (`serial`) and token locking are always on ([`locking.md`](tentackle-persistence/src/main/docs/locking.md)), validation is declarative and travels with the object ([`validation.md`](tentackle-core/src/main/docs/validation.md)), shared objects can be made immutable ([`immutable.md`](tentackle-core/src/main/docs/immutable.md)), and aggregates revert atomically via snapshots ([`snapshot.md`](tentackle-pdo/src/main/docs/snapshot.md)).

### TRIP (Tentackle Remote Invocation Protocol)
Custom binary serialization/RPC framework in `tentackle-core` replacing RMI and JOS — compact, pluggable and SD-WAN-friendly. Used for client-server and server-server communication; PDOs travel between JVMs serialized via TRIP. Requires `opens` in `module-info.java` for reflection access. QUIC (RFC-9000) is a pluggable transport (`tentackle-quic`). See [`trip.md`](tentackle-core/src/main/docs/trip.md) and [`tiers.md`](src/main/docs/tiers.md).

### Wurbelizer Code Generation
Wurblets are code generation templates processed at build time by the third-party `wurbelizer-maven-plugin` (see https://wurbelizer.org/). Generated code is woven into the source within guarded code-folding regions; analysis metadata lands in `target/wurbel/`. `tentackle-wurblets` generates the PDO/operation interfaces and DTOs; `tentackle-persistence-wurblets` generates the persistence implementation and query methods (`PdoSelectList`/`PdoSelectUnique`, eager relations). The model definition DSL is documented in [`model-definition.md`](tentackle-model/src/main/docs/model-definition.md).

### JPMS (Java Module System)
Every module has an explicit `module-info.java`, using `exports` for public API and `opens` for reflection-based access. Tentackle applications run in both modular and non-modular (classpath) environments via the ServiceFinder SPI, which is resolved at build time (no runtime classpath scanning, fast startup). See [`modules.md`](src/main/docs/modules.md).

### SPI / Service Pattern
Annotations enable service discovery via `ServiceFinder`, backed by entries under `META-INF/services` (and related `META-INF` subdirectories) produced at build time by the `tentackle-maven-plugin` (which scans for types annotated, directly or indirectly, with `@Analyze`). See [`services.md`](tentackle-common/src/main/docs/services.md).
- `@Service` — singletons such as logger providers, scripting-language implementations, theme providers.
- `@MappedService` — locating implementations of interfaces such as the persistence and domain layer implementations.

## FXML/FX Conventions

FXML, CSS, and `.properties` files are co-located with their Controller classes in `src/main/java` (not `src/main/resources`). They are copied as resources during the build. This enables SceneBuilder to locate them by relative path.

## Testing

- TestNG is the default test framework (JUnit 5 also supported).
- H2 in-memory database is used for integration tests; PostgreSQL is used for integration tests with multiple processes running in parallel.
- Test database credentials are configured via filtered resources in test modules.
- `tentackle-test-pdo` and `tentackle-test-fx-rdc` provide TestNG support for the PDO layer and the rich desktop client respectively.
