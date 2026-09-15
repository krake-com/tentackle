# Table of Contents

The complete Tentackle documentation, grouped by topic. New to the framework?
Start with the [Quickstart](./quickstart.md). Looking for a specific term? Use
the [Index](./index.md).

## Getting Started

- [Quickstart](./quickstart.md) — generate, build, and run a multi-tier application in minutes.
- [Tentackle Project Archetype](../../../tentackle-project-archetype/src/main/docs/tentackle-project-archetype.md) — what the archetype scaffolds and how the generated modules fit together.
- [MyApp Walkthrough](../../../tentackle-project-archetype/src/main/docs/myapp-walkthrough.md) — a guided tour through a generated application, tier by tier.
- [What a Seasoned Java Developer Would Not Expect](./unexpected.md) — the distilled list of deviations from mainstream Java frameworks, each with a pointer to the document that covers it.
- [Tentackle BOM](../../../tentackle-bom/src/main/docs/bom.md) — the Bill of Materials for pinning Tentackle module versions in downstream projects.

## Concepts & Architecture

- [Tentackle Modules](./modules.md) — the build-time, runtime and common modules, and how they layer.
- [Multi-Tier Cascade](./tiers.md) — nested servers and clients; how the same artifact runs at any tier.
- [Correctness First](./correctness-first.md) — the design philosophy: refuse to be wrong quietly.
- [Exceptions and Error Handling](./exceptions.md) — the exception hierarchy, the `temporary` flag behind transparent retry, session-aware messages, and how a `Throwable` survives a trip between tiers.
- [Why Tentackle Fits Technical and Scientific Applications](./technical-scientific.md) — where location transparency shines.
- [PDOs vs. Traditional ORMs](../../../tentackle-pdo/src/main/docs/pdo-vs-orm.md) — why a PDO is never in a degraded state.
- [Location Transparency vs. REST, RMI and Spring Remoting](./remoting-comparison.md) — what the tier boundary costs in each approach, the classic "location transparency is a lie" objection answered, and where each alternative wins.
- [Embedded Entities vs. Application-Specific DataTypes](./embedded-vs-datatype.md) — choosing the right modeling tool.

## Persistent Domain Objects (PDO)

- [PDO — Persistent Domain Objects](../../../tentackle-pdo/src/main/docs/pdo.md) — the central programming model.
- [The DomainContextProvider](../../../tentackle-pdo/src/main/docs/domain-context-provider.md) — creating PDOs and operations in context.
- [Operation — Behavior Without an Entity](../../../tentackle-pdo/src/main/docs/operation.md) — business logic that is not tied to one entity.
- [Transactions and Transparent Retry](../../../tentackle-pdo/src/main/docs/transaction.md) — declarative units of work: begin/commit, rollback, and automatic retry on temporary failures.
- [Domain Keys](../../../tentackle-pdo/src/main/docs/domain-key.md) — the business identity of a root entity: the `key` option, generated key types, and `findByUniqueDomainKey`.
- [PDO Caching](../../../tentackle-pdo/src/main/docs/cache.md) — the optional, coherent object cache.
- [Number Sources](../../../tentackle-pdo/src/main/docs/ns.md) — unique business numbers from persistent, transactional pools.
- [Snapshots and Copies](../../../tentackle-pdo/src/main/docs/snapshot.md) — exact, atomic aggregate undo.
- [Security — Permissions, Rules, and Grantees](../../../tentackle-pdo/src/main/docs/security.md) — the PDO security model.
- [The rootId and rootClassId Columns](../../../tentackle-pdo/src/main/docs/root-columns.md) — how components find their aggregate root.

## Model & Code Generation

- [Tentackle Model](../../../tentackle-model/src/main/docs/tentackle-model.md) — the model API module.
- [Model Definition Syntax](../../../tentackle-model/src/main/docs/model-definition.md) — the DSL embedded in a PDO interface.
- [Wurblets — Interface-Level Code Generation](../../../tentackle-wurblets/src/main/docs/wurblets.md) — generating the PDO/operation interfaces.
- [The DTO Wurblet and `@RecordDTO`](../../../tentackle-wurblets/src/main/docs/dto-wurblet.md) — generating data transfer objects from a property model or an annotated record.
- [The Wurblet API](../../../tentackle-wurblets/src/main/docs/wurblet-api.md) — the `org.tentackle.wurblet` classes: wurblet base classes, the argument mini-language, the join model.
- [Persistence Wurblets — Implementation-Level Code Generation](../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md) — generating the persistence implementation.
- [The Persistence Wurblet API](../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblet-api.md) — the `org.tentackle.persist.wurblet` classes: `DbModelWurblet`, the clause generators, the remoting helpers.
- [PdoSelectList and PdoSelectUnique](../../../tentackle-persistence-wurblets/src/main/docs/pdo-select.md) — generating query methods.
- [Eager Relations](../../../tentackle-persistence-wurblets/src/main/docs/eager-relations.md) — fetching related entities via outer joins.

## Persistence & Database

- [Tentackle Persistence](../../../tentackle-persistence/src/main/docs/persistence.md) — the PDO-aware persistence layer.
- [Locking — Token Locks and the Optimistic `serial` Column](../../../tentackle-persistence/src/main/docs/locking.md) — concurrency control.
- [Cursors](../../../tentackle-persistence/src/main/docs/cursors.md) — streaming large result sets, transparently over local and remote sessions.
- [Tentackle Domain](../../../tentackle-domain/src/main/docs/domain.md) — the domain-logic layer base.
- [Tentackle Session](../../../tentackle-session/src/main/docs/session.md) — the persistence and security context.
- [Authentication — Credentials, Providers, and Single Sign-On](../../../tentackle-session/src/main/docs/authentication.md) — the pluggable login: password, OAuth2/OIDC tokens, and your own.
- [Single Sign-On with Google](./google-sso.md) — a step-by-step walkthrough: the OAuth client, the browser flow with PKCE, ID token verification, and mapping the identity onto a user.
- [Table Names](../../../tentackle-session/src/main/docs/table-names.md) — schemas, schema mapping and table prefixes: `@TableName`, the wurblet properties and the maven parameters.
- [Modification Tracking](../../../tentackle-session/src/main/docs/modification-tracking.md) — the change-notification bus: serials, listeners, master serial events.
- [Backend Properties](../../../tentackle-session/src/main/docs/backend-properties.md) — the `backend.properties` reference: connection, server, and client configuration keys.
- [Tentackle Database](../../../tentackle-database/src/main/docs/database.md) — the low-level persistence engine.
- [The Query DSL](../../../tentackle-database/src/main/docs/query-dsl.md) — building projection queries from typed expressions instead of SQL strings.
- [SQL Statement Batching](../../../tentackle-database/src/main/docs/batching.md) — collecting inserts, updates and deletes into JDBC batches.
- [Tentackle SQL](../../../tentackle-sql/src/main/docs/sql.md) — the database backend abstraction layer.
- [The Oracle Backend](../../../tentackle-sql/src/main/docs/oracle.md) — Oracle's peculiarities: emulated filtered indexes, virtual index columns, empty strings.
- [The MySQL and MariaDB Backends](../../../tentackle-sql/src/main/docs/mysql.md) — type mapping, index key limits, migration by full column declaration, sequences.
- [The SAP HANA Backend](../../../tentackle-sql/src/main/docs/hana.md) — `NVARCHAR` strings, unsigned `TINYINT`, migration by full column declaration.
- [The Firebird Backend](../../../tentackle-sql/src/main/docs/firebird.md) — explicit row locks, sort order per index, tables that cannot be renamed.
- [The CockroachDB Backend](../../../tentackle-sql/src/main/docs/cockroachdb.md) — selecting it by name, index names per table, serializable transactions.

## Remoting

- [TRIP — Tentackle Remote Invocation Protocol](../../../tentackle-core/src/main/docs/trip.md) — binary serialization and RPC.
- [TRIP over QUIC](../../../tentackle-quic/src/main/docs/quic.md) — the QUIC (RFC-9000) transport, on KWIK.
- [TRIP over Cloudflare quiche](../../../tentackle-quiche/src/main/docs/quiche.md) — the same transport on Cloudflare quiche.
- [I/O and Networking](../../../tentackle-core/src/main/docs/io.md) — packet-framed compressing/encrypting streams and the reconnection machinery.

## Core Foundations

- [Tentackle Core](../../../tentackle-core/src/main/docs/core.md) — the runtime foundation.
- [Tentackle Common](../../../tentackle-common/src/main/docs/common.md) — the shared build- and runtime foundation.
- [Service and Configuration API — Locating Implementations Without Scanning](../../../tentackle-common/src/main/docs/services.md) — the `@Service`/`@MappedService` annotations, the ServiceFinder SPI, build-time registration, module ordering and the ModuleHook.
- [Cryptor and EncryptedProperties](../../../tentackle-common/src/main/docs/cryptor.md) — application-specific secret handling: encrypted passwords in configuration, memory, and on the wire.
- [Naming Rules](../../../tentackle-common/src/main/docs/naming-rules.md) — how the domain, persistence and remote-delegate class names are derived, and how to replace the conventions application-wide.
- [Annotation Processors](../../../tentackle-core/src/main/docs/annotation-processors.md) — compile-time validation of the framework's annotations.
- [Binding — Connecting Models to Views](../../../tentackle-core/src/main/docs/binding.md) — the binding API.
- [Validation — Annotation-Driven, Scope-Aware Validation](../../../tentackle-core/src/main/docs/validation.md) — declarative constraints.
- [Interceptors — Annotation-Driven Method Interception](../../../tentackle-core/src/main/docs/interceptors.md) — cross-cutting concerns.
- [Reflection — Class Mapping, Mixins, and Proxy Support](../../../tentackle-core/src/main/docs/reflect.md) — the `ClassMapper` behind `@MappedService`, the mixins behind PDO proxies, canonical construction.
- [Immutable — Switchable Read-Only Objects](../../../tentackle-core/src/main/docs/immutable.md) — controlled immutability.
- [Freezable — Making Old-School Mutable Value Types Safe](../../../tentackle-common/src/main/docs/freezable.md) — taming legacy mutable values.
- [Preferences — Persistable, Database-Backed Settings](../../../tentackle-core/src/main/docs/preferences.md) — settings that roam with the user.
- [Tasks and Daemons — Background Execution](../../../tentackle-core/src/main/docs/tasks-daemon.md) — the task dispatcher and daemon supervisor.
- [Miscellaneous — Contracts, Collections, Pooling, Utilities](../../../tentackle-core/src/main/docs/misc.md) — the low-level contracts the upper layers implement, plus `TrackedList`, pooling and smart date/time parsing.
- [Tentackle Logging](../../../tentackle-core/src/main/docs/logging.md) — the pluggable logging API.
- [Tentackle Scripting](../../../tentackle-core/src/main/docs/scripting.md) — the pluggable scripting-language API.
- [CompoundValue — Objects From Strings](../../../tentackle-core/src/main/docs/compound-value.md) — constants, references and scripts behind dynamic annotation parameters.

## Internationalization

- [Resource Bundles](../../../tentackle-common/src/main/docs/bundle.md) — the i18n overview: bundles, `I18NText` and locale handling.
- [Internationalization (i18n)](../../../tentackle-common/src/main/docs/internationalization.md) — companion overview of the i18n story.
- [Tentackle I18N — Database-backed Resource Bundles](../../../tentackle-i18n/src/main/docs/i18n.md) — managing translations in the database.
- [BundleMonkey](../../../tentackle-i18n/src/main/docs/bundlemonkey.md) — the standalone GUI tool for editing database-backed bundles at runtime.

## User Interface (JavaFX)

- [tentackle-fx — The Extended JavaFX Layer](../../../tentackle-fx/src/main/docs/fx.md) — JavaFX components, containers, and binding.
- [Tables and Trees](../../../tentackle-fx/src/main/docs/tables.md) — configuration-driven tables and tree-tables, cell types, preferences-backed layouts.
- [Auto-Completion](../../../tentackle-fx/src/main/docs/auto-completion.md) — suggesting values while the user types: suggestion generators, the popup, background matching.
- [GraphicProvider](../../../tentackle-fx/src/main/docs/graphic-provider.md) — icons and images by symbolic name, realms, replacing the icon pack.
- [InteractiveError](../../../tentackle-fx/src/main/docs/interactive-error.md) — turning validation results into marked controls on screen.
- [tentackle-fx-rdc — Rich Desktop Client](../../../tentackle-fx-rdc/src/main/docs/rdc.md) — the rich-client framework.
- [GuiProvider — Per-Entity GUI Services](../../../tentackle-fx-rdc/src/main/docs/gui-provider.md) — the per-entity UI hub: default editor/finder, generated table configurations, i18n levels.
- [Background Execution](../../../tentackle-fx-rdc/src/main/docs/background-execution.md) — `Rdc.bg(...)`, the ModificationTracker default and the session-pool alternative.
- [tentackle-fx-atlanta — The AtlantaFX Theme Layer](../../../tentackle-fx-atlanta/src/main/docs/atlanta.md) — theming and component styling.
- [tentackle-fx-rdc-poi — Spreadsheet (XLS) Export](../../../tentackle-fx-rdc-poi/src/main/docs/poi.md) — Excel export for RDC tables via Apache POI.
- [FX RDC Update — Auto-Update for the Rich Desktop Client](../../../tentackle-fx-rdc-update/src/main/docs/fx-rdc-update.md) — the client-side update UI.

## Logging Providers

- [SLF4J Provider](../../../tentackle-log-slf4j/src/main/docs/tentackle-log-slf4j.md) — pluggable SLF4J backend.
- [Log4J 2 Provider](../../../tentackle-log-log4j2v/src/main/docs/tentackle-log-log4j2v.md) — pluggable Log4J 2 backend.

## Scripting Providers

- [Groovy Provider](../../../tentackle-script-groovy/src/main/docs/groovy.md) — Groovy scripting backend.
- [JRuby Provider](../../../tentackle-script-ruby/src/main/docs/ruby.md) — JRuby scripting backend.
- [JSR-223 Provider](../../../tentackle-script-jsr/src/main/docs/jsr.md) — JSR-223 / GraalVM scripting backend.

## Application Lifecycle & Integration

- [Application Bootstrap](../../../tentackle-pdo/src/main/docs/application.md) — from `main()` to a running tier: the `Application` hierarchy and startup sequence.
- [Tentackle Update — The Application Self-Update Service](../../../tentackle-update/src/main/docs/update.md) — client auto-update.
- [Tentackle Web — Bridging Web Frameworks to a Tentackle Tier](../../../tentackle-web/src/main/docs/web.md) — exposing a Tentackle tier to web frameworks.

## Build Tooling (Maven Plugins)

- [Tentackle Maven Plugin](../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md) — the main plugin driving annotation analysis.
- [Tentackle Maven Support](../../../tentackle-maven-support/src/main/docs/tentackle-maven-support.md) — the shared Mojo foundation.
- [Tentackle Build Support](../../../tentackle-build-support/src/main/docs/build-support.md) — the annotation analyze phase, independent of Maven.
- [Check Maven Plugin](../../../tentackle-check-maven-plugin/src/main/docs/tentackle-check-maven-plugin.md) — validates bundles and validation annotations.
- [SQL Maven Plugin](../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) — generates DDL and database migrations.
- [I18N Maven Plugin](../../../tentackle-i18n-maven-plugin/src/main/docs/tentackle-i18n-maven-plugin.md) — syncs database-backed bundles with resource bundles.
- [Jlink/Jpackage Maven Plugin](../../../tentackle-jlink-maven-plugin/src/main/docs/jlinkpackage.md) — builds self-contained application images.
- [Wizard Maven Plugin](../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md) — interactive PDO/operation wizard.
- [MCP Maven Plugin](../../../tentackle-mcp-maven-plugin/src/main/docs/tentackle-mcp-maven-plugin.md) — serves the model to coding agents over MCP.

## AI Agent Support

- [AI Agent Tooling Support](./ai-agents.md) — the map: analyzing the model over MCP, the headless batch modes of the wizard goals that scaffold entities, generate fixture code and run application code, and the database side via `tentackle-sql:migrate` and `tentackle-sql:run`.

## Testing

- [tentackle-pdo-mock](../../../tentackle-pdo-mock/src/main/docs/pdo-mock.md) — mock delegates for database-less PDO and operation unit tests.
- [tentackle-test-pdo](../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md) — TestNG support for the PDO layer.
- [tentackle-test-fx-rdc](../../../tentackle-test-fx-rdc/src/main/docs/tentackle-test-fx-rdc.md) — TestNG support for the rich desktop client.

## Legacy
- [Migrate from RMI to TRIP](./migrate_rmi_trip.md) — upgrading an existing RMI-based application to the TRIP protocol.


---

See also the [Index](./index.md) for an alphabetical, concept-oriented entry point.
