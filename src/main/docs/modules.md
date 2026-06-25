## Tentackle Modules

Tentackle is a modular framework. It consists of 3 kinds of maven modules
as shown in the diagram below:

1. build-time modules and maven plugins
2. runtime modules
3. common modules for both build- and runtime

All artifacts are on [maven central](https://search.maven.org/search?q=tentackle).

There is also a maven bom (bill of materials), a
[maven archetype](../../../tentackle-project-archetype/src/main/docs/tentackle-project-archetype.md) and two modules for TestNG support
([tentackle-test-pdo](../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md) and
[tentackle-test-fx-rdc](../../../tentackle-test-fx-rdc/src/main/docs/tentackle-test-fx-rdc.md))
not shown in the diagram.

![Tentackle Modules](./assets/tentackle-modules-80.png)

### Build- and Runtime

#### [tentackle-common](../../../tentackle-common/src/main/docs/common.md)

Common classes and resources necessary for build- and runtime.
The most important are:

- essential annotations such as `@Analyze`
- additional datatypes
- bundle support
- module support (JPMS)
- service API

See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-common/apidocs/org.tentackle.common/module-summary.html)
for more details.

#### [tentackle-sql](../../../tentackle-sql/src/main/docs/sql.md)

Low-level database backend abstraction layer.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-sql/apidocs/org.tentackle.sql/module-summary.html)
for more details.

### Runtime

#### [tentackle-core](../../../tentackle-core/src/main/docs/core.md)

The core module of the framework with the following packages:

- apt: annotation processors
- bind: binding API
- daemon: daemon and thread handling
- io: I/O support, mainly network related
- log: logging API (see pluggable logging backends below)
- misc: miscellaneous classes
- prefs: preferences API (database persisted preferences)
- reflect: reflection, dynamic proxy, interceptor, class mapping support
- script: scripting API (see pluggable scripting languages below)
- task: background tasks and dispatchers
- validate: validation API

See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-core/apidocs/org.tentackle.core/module-summary.html)
for more details.

#### [tentackle-session](../../../tentackle-session/src/main/docs/session.md)

Interfaces for the session handling and supporting classes.
The interfaces are implemented in `tentackle-database`.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-session/apidocs/org.tentackle.session/module-summary.html)
for more details.

#### [tentackle-pdo](../../../tentackle-pdo/src/main/docs/pdo.md)

Interfaces and supporting classes for the persistent domain object layer.
Contains the following packages:

- app: basic application lifecycle support
- apt: annotation processors for PDOs and operations
- ns: number source API
- pdo: interfaces and supporting classes for the PDO layer
- security: security interfaces and API for the PDO layer

The interfaces are implemented in `tentackle-persistence`.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-pdo/apidocs/org.tentackle.pdo/module-summary.html)
for more details.

#### [tentackle-database](../../../tentackle-database/src/main/docs/database.md)

Implements `tentackle-session`. Injected at runtime.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-database/apidocs/org.tentackle.database/module-summary.html)
for more details.

#### [tentackle-persistence](../../../tentackle-persistence/src/main/docs/persistence.md)

Implements the persistence part of `tentackle-pdo`. Injected at runtime.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-persistence/apidocs/org.tentackle.persistence/module-summary.html)
for more details.

#### [tentackle-domain](../../../tentackle-domain/src/main/docs/domain.md)

Implements the domain part of `tentackle-pdo`. Injected at runtime.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-domain/apidocs/org.tentackle.domain/module-summary.html)
for more details.

#### [tentackle-update](../../../tentackle-update/src/main/docs/update.md)

Auto-update feature for Tentackle clients based on jlink images
created by the `tentackle-jlink-maven-plugin`.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-update/apidocs/org.tentackle.update/module-summary.html)
for more details.

#### [tentackle-fx](../../../tentackle-fx/src/main/docs/fx.md)

JavaFX components, container, annotations, factories and binding.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-fx/apidocs/org.tentackle.fx/module-summary.html)
for more details.

#### [tentackle-fx-rdc](../../../tentackle-fx-rdc/src/main/docs/rdc.md)

Rich desktop client framework based on JavaFX using PDOs and operations.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-fx-rdc/apidocs/org.tentackle.fx.rdc/module-summary.html)
for more details.

#### tentackle-fx-rdc-poi

Pluggable Apache POI extension for `tentackle-fx-rdc`.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-fx-rdc-poi/apidocs/org/tentackle/fx/rdc/poi/package-summary.html)
for more details.

#### [tentackle-fx-rdc-update](../../../tentackle-fx-rdc-update/src/main/docs/fx-rdc-update.md)

FX client UI for `tentackle-update`.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-fx-rdc-update/apidocs/org.tentackle.fx.rdc.update/module-summary.html)
for more details.

#### tentackle-log-log4j

Pluggable logging adapter for Log4J (major release 1).
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-log-log4j/apidocs/org/tentackle/log/log4j/package-summary.html)
for more details.

#### [tentackle-log-log4j2v](../../../tentackle-log-log4j2v/src/main/docs/tentackle-log-log4j2v.md)

Pluggable logging adapter for Log4J (major release 2).
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-log-log4j2v/apidocs/org/tentackle/log/log4j2v/package-summary.html)
for more details.

#### [tentackle-log-slf4j](../../../tentackle-log-slf4j/src/main/docs/tentackle-log-slf4j.md)

Pluggable logging adapter for SLF4J.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-log-slf4j/apidocs/org/tentackle/log/slf4j/package-summary.html)
for more details.

#### [tentackle-script-groovy](../../../tentackle-script-groovy/src/main/docs/groovy.md)

Pluggable scripting language adapter for Groovy.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-script-groovy/apidocs/org/tentackle/script/groovy/package-summary.html)
for more details.

#### [tentackle-script-ruby](../../../tentackle-script-ruby/src/main/docs/ruby.md)

Pluggable scripting language adapter for JRuby.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-script-ruby/apidocs/org/tentackle/script/ruby/package-summary.html)
for more details.

#### [tentackle-script-jsr](../../../tentackle-script-jsr/src/main/docs/jsr.md)

Pluggable scripting language adapter for JSR223 (and Graalvm).
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-script-jsr/apidocs/org.tentackle.script.jsr/module-summary.html)
for more details.

#### [tentackle-i18n](../../../tentackle-i18n/src/main/docs/i18n.md)

Pluggable I18N bundle support with database persistence.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-i18n/apidocs/org.tentackle.i18n/module-summary.html)
for more details.

### Build Time

#### [tentackle-build-support](../../../tentackle-build-support/src/main/docs/build-support.md)

Basic support for the build process, independent of maven.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-build-support/apidocs/org/tentackle/buildsupport/package-summary.html)
for more details.

#### [tentackle-maven-support](../../../tentackle-maven-support/src/main/docs/tentackle-maven-support.md)

Maven support for the build process. Used by all Tentackle maven plugins.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-maven-support/apidocs/org/tentackle/maven/package-summary.html)
for more details.

#### [tentackle-maven-plugin](../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md)

Main tentackle plugin. Analyzes the source code for annotations directly or
indirectly annotated with `@Analyze` and invokes the corresponding 
AnalyzeHandlers. Fundamental for the ServiceFactory and its ServiceFinders.
See the [plugin docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-maven-plugin/plugin-info.html)
for more details and other goals.

#### [tentackle-check-maven-plugin](../../../tentackle-check-maven-plugin/src/main/docs/tentackle-check-maven-plugin.md)

Maven plugin to check tentackle resource bundles and validation annotations.
See the [plugin docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-check-maven-plugin/plugin-info.html)
for more details.

#### [tentackle-sql-maven-plugin](../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md)

Maven plugin to generate the DDL statements according to the model and to
generate SQL-code to migrate the database to the current model.
See the [plugin docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-sql-maven-plugin/plugin-info.html) 
for more details.

#### [tentackle-i18n-maven-plugin](../../../tentackle-i18n-maven-plugin/src/main/docs/tentackle-i18n-maven-plugin.md)

Maven pluging to synchronize the database persisted I18N bundles with
the project's resource bundles.
See the [plugin docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-i18n-maven-plugin/plugin-info.html) 
for more details.

#### [tentackle-jlink-maven-plugin](../../../tentackle-jlink-maven-plugin/src/main/docs/jlinkpackage.md)

Maven plugin to create self-contained applications via jlink or jpackage.
See the [plugin docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-jlink-maven-plugin/plugin-info.html)
for more details.

#### [tentackle-wizard-maven-plugin](../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md)

Maven plugin providing a interactive wizard to generate the source files
for PDOs and operations.
See the [plugin docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-wizard-maven-plugin/plugin-info.html)
for more details.

#### [tentackle-model](../../../tentackle-model/src/main/docs/tentackle-model.md)

API for the domain model of the application. Used by the wurblets,
the PDO wizard and the SQL plugin.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-model/apidocs/org.tentackle.model/module-summary.html)
for more details.

#### [tentackle-wurblets](../../../tentackle-wurblets/src/main/docs/wurblets.md)

[Wurblets](https://wurbelizer.org/how-it-works/) 
to generate code for the PDO- and operation interfaces and some
other boilerplate code such as DTOs.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-wurblets/apidocs/org/tentackle/wurblet/package-summary.html)
for more details.

#### [tentackle-persistence-wurblets](../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md)

[Wurblets](https://wurbelizer.org/how-it-works/) 
to generate code for the PDO- and operation implementation.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-persistence-wurblets/apidocs/org/tentackle/persist/wurblet/package-summary.html)
for more details.

### Further modules with documentation

These modules are not part of the layered diagram above but ship their own documentation:

- [tentackle-web](../../../tentackle-web/src/main/docs/web.md) — web tier support.
- [tentackle-quic](../../../tentackle-quic/src/main/docs/quic.md) — QUIC ([TRIP](../../../tentackle-core/src/main/docs/trip.md)) transport.
- [tentackle-fx-atlanta](../../../tentackle-fx-atlanta/src/main/docs/atlanta.md) — the Atlanta JavaFX theme.
- [tentackle-project-archetype](../../../tentackle-project-archetype/src/main/docs/tentackle-project-archetype.md) — the project-scaffolding archetype and its [walkthrough](../../../tentackle-project-archetype/src/main/docs/myapp-walkthrough.md).
- [tentackle-test-pdo](../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md) and [tentackle-test-fx-rdc](../../../tentackle-test-fx-rdc/src/main/docs/tentackle-test-fx-rdc.md) — TestNG support.

## Related Documentation

- [The Multi-Tier Cascade](./tiers.md) — how the same artifact runs at any tier across these modules.
- [Why Tentackle Fits Technical and Scientific Applications](./technical-scientific.md) — the design goals behind the module split.
- [Correctness First](./correctness-first.md) — the correctness guarantees the runtime modules provide together.
- [Service and Configuration API](../../../tentackle-common/src/main/docs/services.md) — the SPI that wires the runtime implementations into the interface modules.
- [PDO / Persistent Domain Objects](../../../tentackle-pdo/src/main/docs/pdo.md) — the central programming model spanning the application-layer modules.
