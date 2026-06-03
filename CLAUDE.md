# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Tentackle** is a comprehensive Java application framework for building modular, enterprise-grade multi-tier applications with JavaFX desktop UI, JDBC persistence, and full JPMS (Java Platform Module System) support.

- Website: https://tentackle.org/
- License: LGPL 2.1
- Developer: Krake Softwaretechnik (https://krake.de/)

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

# Build specific module
mvn -pl :tentackle-core clean install

# Initialize test database
mvn -Pinitdb test
```

**Requirements:** Java JDK >= 25, Maven >= 3.9.0, PostgreSQL >= 9.

Tests run with locale forced to US English (`-Duser.language=en -Duser.region=US`).

## Module Architecture

This is a 37-module Maven project organized in layers:

```
UI Layer:          tentackle-fx, tentackle-fx-atlanta, tentackle-fx-rdc, tentackle-web
Application Layer: tentackle-pdo, tentackle-domain, tentackle-persistence
Database Layer:    tentackle-database, tentackle-sql, tentackle-session
Core:              tentackle-core, tentackle-common
Build Tooling:     tentackle-maven-plugin, tentackle-sql-maven-plugin, tentackle-i18n-maven-plugin,
                   tentackle-jlink-maven-plugin, tentackle-check-maven-plugin, tentackle-wizard-maven-plugin
```

Key modules:
- `tentackle-core` — TRIP (serialization/RPC), validation, reflection, binding
- `tentackle-pdo` — Persistent Domain Objects (entities, aggregates, components)
- `tentackle-domain` — Domain logic layer base classes
- `tentackle-persistence` — High-level persistence with code generation
- `tentackle-fx-rdc` — Rich Data Components (tables, trees, forms)
- `tentackle-bom` — Bill of Materials for downstream projects
- `tentackle-common` — common code used at build time by the various Tentackle maven plugins and by the application at runtime
- `tentackle-model` — the model API describing the application's entities and their relationships 
- `tentackle-project-archetype` — Maven archetype for scaffolding new projects

## Key Architectural Patterns

### PDO / Persistent Domain Object Pattern
- **PDO** (`PersistentDomainObject`) — interface extending the persistence- and domain interfaces (`PersistentObject` and `DomainObject`)
- **Domain Object** (`AbstractDomainObject<T,P>`) — base class implementing the domain logic. Extended by the application's domain objects implementing the concrete domain interfaces.
- **Persistent Object** (`AbstractPersistentObject<T,D>`) — base class implementing the persistence logic. Extended by the application's persistent objects implementing the concrete persistence interfaces.
- The domain- and persistence implementations are injected at runtime, creating the PDOs as dynamic proxies.
- This emulates multiple inheritance for classes in Java.
- Clear separation between persistence and business concerns. The persistence layer is agnostic of the domain layer and vice versa.
- Aggregates are first citizens, i.e., the persistence layer knows about root entities and their components, and each component knows its root entity w/o performing a database query. DDD rules are enforced.
- PDOs are defined in a model which is located in a special comment block in the source code of the PDO interface.
- The model is picked up by the code generation tools to generate the persistence layer interfaces and their implementations.
- Since the persistence layer is remoting capable, all methods of the PDOs can be invoked at any time and in any JVM, whether directly connected to a database or indirectly via a remote server (no LazyInitializationException ever).

### TRIP (Tentackle Remote Invocation Protocol)
Custom serialization/RPC framework in `tentackle-core` replacing RMI and JOS.
Used for client-server and server-server communication. Requires `opens` in `module-info.java` for reflection access.
PDOs travel between JVMs and are serialized using TRIP.

### Wurbelizer Code Generation
Wurblets are code generation templates processed at build time by `wurbelizer-maven-plugin`. They generate persistence mappings, SQL DDL, and entity classes. 
Generated code is woven into the source code within guarded code regions. Code analysis meta data lands in `target/wurbel/`. The `tentackle-persistence-wurblets` module defines the persistence generation templates.

### JPMS (Java Module System)
Every module has an explicit `module-info.java`. Modules use `exports` for public API and `opens` for reflection-based access.
However, Tentackle applications can run both in modular and non-modular environments. This is achieved by using the ServiceFinder SPI pattern based on extending META-INF/services and
other subdirectories of META-INF.

### SPI Pattern
Annotations enable dynamic service discovery via `ServiceFinder`.
`@Service` for singletons like logger providers, scripting language implementations, theme providers.
`@MappedService` for locating implementations of interfaces such as the persistence and domain layer implementations.

## FXML/FX Conventions

FXML, CSS, and `.properties` files are co-located with their Controller classes in `src/main/java` (not `src/main/resources`). They are copied as resources during the build. This enables SceneBuilder to locate them by relative path.

## Testing

- TestNG is the default test framework (JUnit 5 also supported)
- H2 in-memory database used for integration tests. Postgres for integration tests with multiple processes running in parallel.
- Test database credentials configured via filtered resources in test modules
