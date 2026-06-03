## Tentackle Modules

Tentackle is a modular framework. It consists of 3 kinds of maven modules
as shown in the diagram below:

1. build-time modules and maven plugins
2. runtime modules
3. common modules for both build- and runtime

All artifacts are on [maven central](https://search.maven.org/search?q=tentackle).

There is also a maven bom (bill of materials), a maven archetype and two modules for TestNG support
not shown in the diagram.

![Tentackle Modules](./assets/tentackle-modules-80.png)

### Build- and Runtime

#### tentackle-common

Common classes and resources necessary for build- and runtime.
The most important are:

- essential annotations such as `@Analyze`
- additional datatypes
- bundle support
- module support (JPMS)
- service API

See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-common/apidocs/org.tentackle.common/module-summary.html)
for more details.

#### tentackle-sql

Low-level database backend abstraction layer.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-sql/apidocs/org.tentackle.sql/module-summary.html)
for more details.

### Runtime

#### tentackle-core

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

#### tentackle-session

Interfaces for the session handling and supporting classes.
The interfaces are implemented in `tentackle-database`.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-session/apidocs/org.tentackle.session/module-summary.html)
for more details.

#### tentackle-pdo

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

#### tentackle-database

Implements `tentackle-session`. Injected at runtime.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-database/apidocs/org.tentackle.database/module-summary.html)
for more details.

#### tentackle-persistence

Implements the persistence part of `tentackle-pdo`. Injected at runtime.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-persistence/apidocs/org.tentackle.persistence/module-summary.html)
for more details.

#### tentackle-domain

Implements the domain part of `tentackle-pdo`. Injected at runtime.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-domain/apidocs/org.tentackle.domain/module-summary.html)
for more details.

#### tentackle-update

Auto-update feature for Tentackle clients based on jlink images
created by the `tentackle-jlink-maven-plugin`.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-update/apidocs/org.tentackle.update/module-summary.html)
for more details.

#### tentackle-fx

JavaFX components, container, annotations, factories and binding.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-fx/apidocs/org.tentackle.fx/module-summary.html)
for more details.

#### tentackle-fx-rdc

Rich desktop client framework based on JavaFX using PDOs and operations.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-fx-rdc/apidocs/org.tentackle.fx.rdc/module-summary.html)
for more details.

#### tentackle-fx-rdc-poi

Pluggable Apache POI extension for `tentackle-fx-rdc`.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-fx-rdc-poi/apidocs/org/tentackle/fx/rdc/poi/package-summary.html)
for more details.

#### tentackle-fx-rdc-update

FX client UI for `tentackle-update`.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-fx-rdc-update/apidocs/org.tentackle.fx.rdc.update/module-summary.html)
for more details.

#### tentackle-log-log4j

Pluggable logging adapter for Log4J (major release 1).
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-log-log4j/apidocs/org/tentackle/log/log4j/package-summary.html)
for more details.

#### tentackle-log-log4j2v

Pluggable logging adapter for Log4J (major release 2).
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-log-log4j2v/apidocs/org/tentackle/log/log4j2v/package-summary.html)
for more details.

#### tentackle-log-slf4j

Pluggable logging adapter for SLF4J.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-log-slf4j/apidocs/org/tentackle/log/slf4j/package-summary.html)
for more details.

#### tentackle-script-groovy

Pluggable scripting language adapter for Groovy.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-script-groovy/apidocs/org/tentackle/script/groovy/package-summary.html)
for more details.

#### tentackle-script-ruby

Pluggable scripting language adapter for JRuby.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-script-ruby/apidocs/org/tentackle/script/ruby/package-summary.html)
for more details.

#### tentackle-script-jsr

Pluggable scripting language adapter for JSR223 (and Graalvm).
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-script-jsr/apidocs/org.tentackle.script.jsr/module-summary.html)
for more details.

#### tentackle-i18n

Pluggable I18N bundle support with database persistence.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-i18n/apidocs/org.tentackle.i18n/module-summary.html)
for more details.

### Build Time

#### tentackle-build-support

Basic support for the build process, independent of maven.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-build-support/apidocs/org/tentackle/buildsupport/package-summary.html)
for more details.

#### tentackle-maven-support

Maven support for the build process. Used by all Tentackle maven plugins.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-maven-support/apidocs/org/tentackle/maven/package-summary.html)
for more details.

#### tentackle-maven-plugin

Main tentackle plugin. Analyzes the source code for annotations directly or
indirectly annotated with `@Analyze` and invokes the corresponding 
AnalyzeHandlers. Fundamental for the ServiceFactory and its ServiceFinders.
See the [plugin docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-maven-plugin/plugin-info.html)
for more details and other goals.

#### tentackle-check-maven-plugin

Maven plugin to check tentackle resource bundles and validation annotations.
See the [plugin docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-check-maven-plugin/plugin-info.html)
for more details.

#### tentackle-sql-maven-plugin

Maven plugin to generate the DDL statements according to the model and to
generate SQL-code to migrate the database to the current model.
See the [plugin docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-sql-maven-plugin/plugin-info.html) 
for more details.

#### tentackle-i18n-maven-plugin

Maven pluging to synchronize the database persisted I18N bundles with
the project's resource bundles.
See the [plugin docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-i18n-maven-plugin/plugin-info.html) 
for more details.

#### tentackle-jlink-maven-plugin

Maven plugin to create self-contained applications via jlink or jpackage.
See the [plugin docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-jlink-maven-plugin/plugin-info.html)
for more details.

#### tentackle-wizard-maven-plugin

Maven plugin providing a interactive wizard to generate the source files
for PDOs and operations.
See the [plugin docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-wizard-maven-plugin/plugin-info.html)
for more details.

#### tentackle-model

API for the domain model of the application. Used by the wurblets,
the PDO wizard and the SQL plugin.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-model/apidocs/org.tentackle.model/module-summary.html)
for more details.

#### tentackle-wurblets

[Wurblets](https://wurbelizer.org/how-it-works/) 
to generate code for the PDO- and operation interfaces and some
other boilerplate code such as DTOs.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-wurblets/apidocs/org/tentackle/wurblet/package-summary.html)
for more details.

#### tentackle-persistence-wurblets

[Wurblets](https://wurbelizer.org/how-it-works/) 
to generate code for the PDO- and operation implementation.
See the [java docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-persistence-wurblets/apidocs/org/tentackle/persist/wurblet/package-summary.html)
for more details.
