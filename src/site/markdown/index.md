# Tentackle — Project Site

This is the Maven-generated **project site** for Tentackle: API documentation
(Javadoc), cross-referenced sources (JXR), plugin documentation and project
reports for every module of the reactor.

Looking for the **user documentation** — quickstart, concepts, guides and the
architecture deep-dives? That lives at **[tentackle.org](https://tentackle.org/)**.

## What is Tentackle?

Tentackle is a comprehensive Java application framework for building modular,
enterprise-grade multi-tier applications with a single, location-transparent
programming model — from a JDBC/SQL database up to a JavaFX desktop client —
with full JPMS support. It is distributed under the LGPL 2.1 by
[Krake Softwaretechnik](https://krake.de/).

## Modules

Tentackle is a multi-module reactor. Modules fall into a few layers (see the
[modules guide](https://tentackle.org/) for the full picture).

### Common (build + runtime)

| Module | Description |
|--------|-------------|
| [tentackle-common](tentackle-common/) | Shared build- and runtime foundation: annotations, datatypes, bundles, JPMS support, the service API. |
| [tentackle-sql](tentackle-sql/) | Low-level database backend abstraction. |

### Core (runtime)

| Module | Description |
|--------|-------------|
| [tentackle-core](tentackle-core/) | Runtime foundation: TRIP (serialization/RPC), validation, binding, reflection, logging and scripting APIs, tasks, preferences. |
| [tentackle-i18n](tentackle-i18n/) | Internationalization support. |
| [tentackle-quic](tentackle-quic/) | QUIC (RFC-9000) pluggable transport for TRIP, on the pure-Java KWIK stack. |
| [tentackle-quiche](tentackle-quiche/) | The same QUIC transport on Cloudflare quiche, bound via the FFM API. |
| [tentackle-log-slf4j](tentackle-log-slf4j/) | SLF4J logging provider. |
| [tentackle-log-log4j2v](tentackle-log-log4j2v/) | Log4j2 logging provider. |
| [tentackle-script-jsr](tentackle-script-jsr/) | JSR-223 scripting provider. |
| [tentackle-script-groovy](tentackle-script-groovy/) | Groovy scripting provider. |
| [tentackle-script-ruby](tentackle-script-ruby/) | Ruby (JRuby) scripting provider. |

### Database (runtime)

| Module | Description |
|--------|-------------|
| [tentackle-session](tentackle-session/) | Session interfaces. |
| [tentackle-database](tentackle-database/) | Session implementation / database layer. |

### Application (runtime)

| Module | Description |
|--------|-------------|
| [tentackle-pdo](tentackle-pdo/) | Persistent Domain Object interfaces. |
| [tentackle-domain](tentackle-domain/) | Domain-logic layer base classes (PDO domain side). |
| [tentackle-persistence](tentackle-persistence/) | PDO-aware persistence layer (PDO persistence side). |
| [tentackle-update](tentackle-update/) | Application update support. |

### UI Layer (runtime)

| Module | Description |
|--------|-------------|
| [tentackle-fx](tentackle-fx/) | JavaFX binding and forms foundation. |
| [tentackle-fx-atlanta](tentackle-fx-atlanta/) | AtlantaFX theme integration. |
| [tentackle-fx-rdc](tentackle-fx-rdc/) | Rich Desktop Client framework (tables, trees, forms). |
| [tentackle-fx-rdc-poi](tentackle-fx-rdc-poi/) | RDC Apache POI (spreadsheet) integration. |
| [tentackle-fx-rdc-update](tentackle-fx-rdc-update/) | RDC application-update integration. |
| [tentackle-web](tentackle-web/) | Web-tier support. |

### Build tooling

| Module | Description |
|--------|-------------|
| [tentackle-model](tentackle-model/) | The model API describing entities and relationships. |
| [tentackle-wurblets](tentackle-wurblets/) | Wurblets generating PDO/operation interfaces and DTOs. |
| [tentackle-persistence-wurblets](tentackle-persistence-wurblets/) | Wurblets generating the persistence implementation and query methods. |
| [tentackle-build-support](tentackle-build-support/) | Shared build-time support. |
| [tentackle-maven-support](tentackle-maven-support/) | Shared support for the Maven plugins. |
| [tentackle-maven-plugin](tentackle-maven-plugin/) | Service/SPI metadata generation. |
| [tentackle-sql-maven-plugin](tentackle-sql-maven-plugin/) | SQL generation plugin. |
| [tentackle-check-maven-plugin](tentackle-check-maven-plugin/) | Build-time correctness checks. |
| [tentackle-i18n-maven-plugin](tentackle-i18n-maven-plugin/) | I18n resource generation plugin. |
| [tentackle-wizard-maven-plugin](tentackle-wizard-maven-plugin/) | PDO wizard plugin. |
| [tentackle-jlink-maven-plugin](tentackle-jlink-maven-plugin/) | jlink/jpackage distribution plugin. |

### Test support

| Module | Description |
|--------|-------------|
| [tentackle-test-pdo](tentackle-test-pdo/) | TestNG support for the PDO layer. |
| [tentackle-test-fx-rdc](tentackle-test-fx-rdc/) | TestNG support for the rich desktop client. |

### Aggregation & scaffolding

| Module | Description |
|--------|-------------|
| [tentackle-bom](tentackle-bom/) | Bill of Materials for downstream projects. |
| [tentackle-project-archetype](tentackle-project-archetype/) | Maven archetype for scaffolding new multi-tier projects. |

## Resources

- **Documentation:** <https://tentackle.org/>
- **Source:** <https://github.com/krake-com/tentackle>
- **Artifacts:** [Maven Central](https://central.sonatype.com/namespace/org.tentackle)
- **Published by:** [Krake Softwaretechnik](https://krake.de/)
