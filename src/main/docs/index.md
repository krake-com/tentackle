# Index

An alphabetical map of key concepts to the documents that cover them. For a
topic-grouped listing of every document, see the
[Table of Contents](./toc.md); to get started, see the
[Quickstart](./quickstart.md).

## A

- **Address validation (QUIC retry tokens)** — [TRIP over Cloudflare quiche](../../../tentackle-quiche/src/main/docs/quiche.md#server-side)
- **Aggregates** — [PDO](../../../tentackle-pdo/src/main/docs/pdo.md), [rootId / rootClassId Columns](../../../tentackle-pdo/src/main/docs/root-columns.md)
- **AI agents / coding agents (MCP, batch modes, skills)** — [AI Agent Tooling Support](./ai-agents.md), [MCP Maven Plugin](../../../tentackle-mcp-maven-plugin/src/main/docs/tentackle-mcp-maven-plugin.md), [Wizard Maven Plugin](../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md)
- **Application bootstrap / lifecycle** — [Application Bootstrap](../../../tentackle-pdo/src/main/docs/application.md), [Multi-Tier Cascade](./tiers.md)
- **Annotation analysis (`@Analyze`, META-INF generation)** — [Service and Configuration API](../../../tentackle-common/src/main/docs/services.md), [Build Support](../../../tentackle-build-support/src/main/docs/build-support.md), [Tentackle Maven Plugin](../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md)
- **Annotation processors (compile-time validation)** — [Annotation Processors](../../../tentackle-core/src/main/docs/annotation-processors.md)
- **Archetype (project scaffolding)** — [Tentackle Project Archetype](../../../tentackle-project-archetype/src/main/docs/tentackle-project-archetype.md), [Quickstart](./quickstart.md)
- **AtlantaFX theme** — [tentackle-fx-atlanta](../../../tentackle-fx-atlanta/src/main/docs/atlanta.md)
- **Authentication (login, credentials, providers)** — [Authentication](../../../tentackle-session/src/main/docs/authentication.md)
- **AuthenticationProvider / AuthenticationManager** — [Authentication](../../../tentackle-session/src/main/docs/authentication.md#the-provider-chain)
- **Auto-completion (text field suggestions)** — [Auto-Completion](../../../tentackle-fx/src/main/docs/auto-completion.md), [tentackle-fx](../../../tentackle-fx/src/main/docs/fx.md)
- **Auto-update** — [Tentackle Update](../../../tentackle-update/src/main/docs/update.md), [FX RDC Update](../../../tentackle-fx-rdc-update/src/main/docs/fx-rdc-update.md)
- **Autologin (desktop clients)** — [Backend Properties](../../../tentackle-session/src/main/docs/backend-properties.md#desktop-client-keys)

## B

- **Backend identity (`Db#getBackendIdentity`, `identity`, several databases in one JVM)** — [Tentackle Database](../../../tentackle-database/src/main/docs/database.md#backend-identity), [Backend Properties](../../../tentackle-session/src/main/docs/backend-properties.md#connection-keys)
- **Backend properties (`backend.properties`, connection/server/client keys)** — [Backend Properties](../../../tentackle-session/src/main/docs/backend-properties.md), [Tentackle Session](../../../tentackle-session/src/main/docs/session.md)
- **Background execution (`Rdc.bg`, session pool)** — [Background Execution](../../../tentackle-fx-rdc/src/main/docs/background-execution.md), [tentackle-fx-rdc](../../../tentackle-fx-rdc/src/main/docs/rdc.md)
- **Batch mode (headless wizard goals: `pdo`, `browse`, `script`)** — [AI Agent Tooling Support](./ai-agents.md), [Wizard Maven Plugin](../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md)
- **Batching (JDBC statement batching, `batchsize`)** — [SQL Statement Batching](../../../tentackle-database/src/main/docs/batching.md), [Tentackle Database](../../../tentackle-database/src/main/docs/database.md)
- **Binding (models to views)** — [Binding](../../../tentackle-core/src/main/docs/binding.md), [tentackle-fx](../../../tentackle-fx/src/main/docs/fx.md)
- **BOM (Bill of Materials)** — [Tentackle BOM](../../../tentackle-bom/src/main/docs/bom.md)
- **Bulk updates and deletes (`PdoUpdateBy`, `PdoDeleteBy`)** — [Persistence Wurblets](../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md#pdoupdateby-and-pdodeleteby--bulk-writes)
- **BundleMonkey (translation editor)** — [BundleMonkey](../../../tentackle-i18n/src/main/docs/bundlemonkey.md)
- **Bundles (resource)** — [Resource Bundles and Internationalization](../../../tentackle-common/src/main/docs/bundle.md), [Tentackle I18N](../../../tentackle-i18n/src/main/docs/i18n.md), [BundleMonkey](../../../tentackle-i18n/src/main/docs/bundlemonkey.md)

## C

- **Cache / caching** — [PDO Caching](../../../tentackle-pdo/src/main/docs/cache.md), [Multi-Tier Cascade](./tiers.md)
- **Cache preloading (`@PreloadCache`)** — [PDO Caching](../../../tentackle-pdo/src/main/docs/cache.md#preloading-caches-at-startup-preloadcache), [Application Bootstrap](../../../tentackle-pdo/src/main/docs/application.md)
- **Canonical construction (`@CanonicalConstructor`, builders)** — [Reflection](../../../tentackle-core/src/main/docs/reflect.md), [TRIP](../../../tentackle-core/src/main/docs/trip.md)
- **Class variables (per-class persistence metadata)** — [Tentackle Persistence](../../../tentackle-persistence/src/main/docs/persistence.md#class-variables), [Persistence Wurblets](../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md)
- **ClassMapper / class mapping** — [Reflection](../../../tentackle-core/src/main/docs/reflect.md), [Service and Configuration API](../../../tentackle-common/src/main/docs/services.md)
- **CockroachDB (backend peculiarities, selection by name)** — [The CockroachDB Backend](../../../tentackle-sql/src/main/docs/cockroachdb.md)
- **Collections (tracked, copy-on-write, immutable)** — [Miscellaneous](../../../tentackle-core/src/main/docs/misc.md), [Immutable](../../../tentackle-core/src/main/docs/immutable.md)
- **Column defaults (database)** — [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md), [The Oracle Backend](../../../tentackle-sql/src/main/docs/oracle.md) (defaults stored as source text)
- **Code generation** — [Wurblets](../../../tentackle-wurblets/src/main/docs/wurblets.md), [Persistence Wurblets](../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md), [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md), [The Wurblet API](../../../tentackle-wurblets/src/main/docs/wurblet-api.md), [The Persistence Wurblet API](../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblet-api.md)
- **Common module (foundation)** — [Tentackle Common](../../../tentackle-common/src/main/docs/common.md)
- **CompoundValue (constants, references, scripts in annotations)** — [CompoundValue](../../../tentackle-core/src/main/docs/compound-value.md), [Validation](../../../tentackle-core/src/main/docs/validation.md), [Tentackle Scripting](../../../tentackle-core/src/main/docs/scripting.md)
- **Connection migration (QUIC, stalled paths)** — [TRIP over Cloudflare quiche](../../../tentackle-quiche/src/main/docs/quiche.md#connection-migration), [TRIP over QUIC](../../../tentackle-quic/src/main/docs/quic.md)
- **Context menus (PDO tree and table items, `@PdoTreeContextMenuItemService`)** — [tentackle-fx-rdc](../../../tentackle-fx-rdc/src/main/docs/rdc.md#context-menus)
- **Core module (runtime foundation)** — [Tentackle Core](../../../tentackle-core/src/main/docs/core.md)
- **Correctness guarantees** — [Correctness First](./correctness-first.md)
- **Cryptor / encryption of secrets** — [Cryptor and EncryptedProperties](../../../tentackle-common/src/main/docs/cryptor.md)
- **Credentials (password, token, custom)** — [Authentication](../../../tentackle-session/src/main/docs/authentication.md#the-credential-model)
- **Cursors (streaming large result sets)** — [Cursors](../../../tentackle-persistence/src/main/docs/cursors.md), [PdoSelectList / PdoSelectUnique](../../../tentackle-persistence-wurblets/src/main/docs/pdo-select.md)
- **Combo / choice boxes (prefix selection, deselect, short/long text)** — [tentackle-fx](../../../tentackle-fx/src/main/docs/fx.md#combo-and-choice-boxes), [tentackle-fx-rdc](../../../tentackle-fx-rdc/src/main/docs/rdc.md#pdo-bound-components)
- **CRUD and search dialogs (`PdoCrud`, `PdoSearch`, `PdoEvent`, dialog caching)** — [tentackle-fx-rdc](../../../tentackle-fx-rdc/src/main/docs/rdc.md#configuring-crud-and-search-dialogs)

## D

- **Database backend** — [Tentackle SQL](../../../tentackle-sql/src/main/docs/sql.md), [Tentackle Database](../../../tentackle-database/src/main/docs/database.md), [The PostgreSQL Backend](../../../tentackle-sql/src/main/docs/postgres.md), [The Oracle Backend](../../../tentackle-sql/src/main/docs/oracle.md), [The MySQL and MariaDB Backends](../../../tentackle-sql/src/main/docs/mysql.md), [The SAP HANA Backend](../../../tentackle-sql/src/main/docs/hana.md), [The Firebird Backend](../../../tentackle-sql/src/main/docs/firebird.md), [The CockroachDB Backend](../../../tentackle-sql/src/main/docs/cockroachdb.md), [The H2 Backend](../../../tentackle-sql/src/main/docs/h2.md), [The DB2 Backend](../../../tentackle-sql/src/main/docs/db2.md)
- **Daemon / supervisor (background threads)** — [Tasks and Daemons](../../../tentackle-core/src/main/docs/tasks-daemon.md)
- **DataTypes (application-specific)** — [Embedded vs. DataType](./embedded-vs-datatype.md), [Binding](../../../tentackle-core/src/main/docs/binding.md), [Freezable](../../../tentackle-common/src/main/docs/freezable.md)
- **Date/time parsing (smart syntax, shortcuts)** — [Miscellaneous](../../../tentackle-core/src/main/docs/misc.md)
- **Date/time input (reference date `@`, sliding year window)** — [tentackle-fx](../../../tentackle-fx/src/main/docs/fx.md#date-and-time-input)
- **Deserialization hardening (allowlisting, untrusted data)** — [TRIP](../../../tentackle-core/src/main/docs/trip.md)
- **DB2 (backend peculiarities)** — [The DB2 Backend](../../../tentackle-sql/src/main/docs/db2.md)
- **DDL generation** — [SQL Maven Plugin](../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md), [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md)
- **Deduplication (wire format)** — [TRIP](../../../tentackle-core/src/main/docs/trip.md)
- **Deferred constraint checking (`deferred`, `deferrable`)** — [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md#deferred-constraint-checking), [Transactions](../../../tentackle-pdo/src/main/docs/transaction.md)
- **Dictionary (type compression)** — [TRIP](../../../tentackle-core/src/main/docs/trip.md)
- **Domain delegate (`AbstractDomainObject`, `AbstractDomainOperation`)** — [Tentackle Domain](../../../tentackle-domain/src/main/docs/domain.md), [PDO](../../../tentackle-pdo/src/main/docs/pdo.md)
- **Domain key (business identity, `findByUniqueDomainKey`)** — [Domain Keys](../../../tentackle-pdo/src/main/docs/domain-key.md)
- **Domain logic layer** — [Tentackle Domain](../../../tentackle-domain/src/main/docs/domain.md), [PDO](../../../tentackle-pdo/src/main/docs/pdo.md)
- **DomainDelegateLinker (linking a domain delegate to its proxy)** — [Tentackle Domain](../../../tentackle-domain/src/main/docs/domain.md#creating-and-linking-the-delegate)
- **DomainContext / DomainContextProvider** — [The DomainContextProvider](../../../tentackle-pdo/src/main/docs/domain-context-provider.md), [PDO](../../../tentackle-pdo/src/main/docs/pdo.md)
- **DSL (typed SQL projection queries)** — [The Query DSL](../../../tentackle-database/src/main/docs/query-dsl.md)
- **DTO (Data Transfer Object) generation** — [The DTO Wurblet and `@RecordDTO`](../../../tentackle-wurblets/src/main/docs/dto-wurblet.md), [Wurblets](../../../tentackle-wurblets/src/main/docs/wurblets.md)
- **Dark mode / themes (`ThemeUtilities`)** — [tentackle-fx](../../../tentackle-fx/src/main/docs/fx.md#themes-and-dark-mode), [tentackle-fx-atlanta](../../../tentackle-fx-atlanta/src/main/docs/atlanta.md)
- **Diagnostics (nested selects, statement and TRIP statistics, statement limits)** — [Tentackle Database](../../../tentackle-database/src/main/docs/database.md#diagnostics-and-statistics)

## E

- **Eager relations** — [Eager Relations](../../../tentackle-persistence-wurblets/src/main/docs/eager-relations.md), [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md)
- **Editable tables / edit handlers (`TableEditHandler`, navigation, adding rows)** — [Tables and Trees](../../../tentackle-fx/src/main/docs/tables.md#edit-handlers)
- **Effective class (`getClass()` vs `instanceof` on a PDO proxy)** — [Reflection](../../../tentackle-core/src/main/docs/reflect.md#getclass-geteffectiveclass-and-instanceof)
- **Embedded entities** — [Embedded vs. DataType](./embedded-vs-datatype.md), [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md)
- **Entity aliases (`entityAliases`)** — [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md#inheritance-mapping-strategies), [Tentackle Model](../../../tentackle-model/src/main/docs/tentackle-model.md#modeldefaults-and-entityaliases)
- **Error display (marking controls, error/info popups)** — [InteractiveError](../../../tentackle-fx/src/main/docs/interactive-error.md), [tentackle-fx](../../../tentackle-fx/src/main/docs/fx.md)
- **Error handling / exception model** — [Exceptions and Error Handling](./exceptions.md), [Correctness First](./correctness-first.md)
- **`ExceptionHelper` (searching and handling exception chains)** — [Exceptions and Error Handling](./exceptions.md#exceptionhelper--working-with-chains)
- **Exceptions (hierarchy, `TentackleRuntimeException`, `PersistenceException`)** — [Exceptions and Error Handling](./exceptions.md)
- **Exceptions across tiers (remoting a `Throwable`)** — [Exceptions and Error Handling](./exceptions.md#exceptions-across-tiers), [TRIP](../../../tentackle-core/src/main/docs/trip.md)
- **Expandable text areas (one-liner expanding to a multi-line editor)** — [tentackle-fx](../../../tentackle-fx/src/main/docs/fx.md#expandable-one-line-text-areas)
- **Environment variables (overriding properties)** — [Miscellaneous](../../../tentackle-core/src/main/docs/misc.md#properties)

## F

- **FFM API (native bindings)** — [TRIP over Cloudflare quiche](../../../tentackle-quiche/src/main/docs/quiche.md), [OS Credential Stores](../../../tentackle-keyring/src/main/docs/platform-keyring.md)
- **Fetch size / `FetchList` (fetching cursor rows in blocks)** — [Cursors](../../../tentackle-persistence/src/main/docs/cursors.md#fetching-in-blocks-fetch)
- **Filtering (column autofilter in table headers)** — [Tables and Trees](../../../tentackle-fx/src/main/docs/tables.md#column-filters-autofilter)
- **Firebird (backend peculiarities)** — [The Firebird Backend](../../../tentackle-sql/src/main/docs/firebird.md)
- **Formatting (localized patterns)** — [Miscellaneous](../../../tentackle-core/src/main/docs/misc.md), [Resource Bundles](../../../tentackle-common/src/main/docs/bundle.md)
- **Freezable value types** — [Freezable](../../../tentackle-common/src/main/docs/freezable.md)
- **FX / JavaFX** — [tentackle-fx](../../../tentackle-fx/src/main/docs/fx.md), [tentackle-fx-rdc](../../../tentackle-fx-rdc/src/main/docs/rdc.md), [tentackle-fx-atlanta](../../../tentackle-fx-atlanta/src/main/docs/atlanta.md)

## G

- **Global transactions (JTA/XA, two-phase commit)** — [Global Transactions — JTA and XA](../../../tentackle-jta/src/main/docs/jta.md), [Transactions and Transparent Retry](../../../tentackle-pdo/src/main/docs/transaction.md#global-transactions)
- **Google sign-in / OAuth client** — [Single Sign-On with Google](./google-sso.md)
- **GraphicProvider (icons/images by symbolic name, realms)** — [GraphicProvider](../../../tentackle-fx/src/main/docs/graphic-provider.md), [tentackle-fx](../../../tentackle-fx/src/main/docs/fx.md)
- **GuiProvider (per-entity GUI services, default editor/finder)** — [GuiProvider](../../../tentackle-fx-rdc/src/main/docs/gui-provider.md), [tentackle-fx-rdc](../../../tentackle-fx-rdc/src/main/docs/rdc.md)

## H

- **H2 (backend peculiarities, test database)** — [The H2 Backend](../../../tentackle-sql/src/main/docs/h2.md), [tentackle-test-pdo](../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md)
- **History (origins, milestones, the move to GitHub)** — [The History of Tentackle](./history.md)
- **Host name verification (`hostcheck`, TLS)** — [TRIP](../../../tentackle-core/src/main/docs/trip.md#ssltls-configuration), [TRIP over Cloudflare quiche](../../../tentackle-quiche/src/main/docs/quiche.md#client-side), [TRIP over QUIC](../../../tentackle-quic/src/main/docs/quic.md)

## I

- **I18NText (multilingual values, translation editor)** — [Resource Bundles and Internationalization](../../../tentackle-common/src/main/docs/bundle.md#the-i18ntext-value-type), [tentackle-fx](../../../tentackle-fx/src/main/docs/fx.md#multilingual-texts)
- **i18n / Internationalization** — [Resource Bundles and Internationalization](../../../tentackle-common/src/main/docs/bundle.md), [Tentackle I18N](../../../tentackle-i18n/src/main/docs/i18n.md), [I18N Maven Plugin](../../../tentackle-i18n-maven-plugin/src/main/docs/tentackle-i18n-maven-plugin.md), [BundleMonkey](../../../tentackle-i18n/src/main/docs/bundlemonkey.md)
- **Icons / images (symbolic names, icon packs, realms)** — [GraphicProvider](../../../tentackle-fx/src/main/docs/graphic-provider.md), [tentackle-fx-atlanta](../../../tentackle-fx-atlanta/src/main/docs/atlanta.md)
- **Id sources (technical object ids)** — [Tentackle Database](../../../tentackle-database/src/main/docs/database.md), [Number Sources](../../../tentackle-pdo/src/main/docs/ns.md) (for business numbers)
- **Immutability** — [Immutable](../../../tentackle-core/src/main/docs/immutable.md)
- **I/O streams (compression, encryption, packet framing)** — [I/O and Networking](../../../tentackle-core/src/main/docs/io.md)
- **Indexes (database)** — [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md), [The PostgreSQL Backend](../../../tentackle-sql/src/main/docs/postgres.md) (GIN indexes on JSONB, unmodeled index methods), [The Oracle Backend](../../../tentackle-sql/src/main/docs/oracle.md) (filtered indexes on Oracle), [The MySQL and MariaDB Backends](../../../tentackle-sql/src/main/docs/mysql.md) (index key limits), [The Firebird Backend](../../../tentackle-sql/src/main/docs/firebird.md) (sort order per index)
- **Inheritance mapping** — [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md)
- **Input alerts (sound / visual feedback on rejected input)** — [tentackle-fx](../../../tentackle-fx/src/main/docs/fx.md#input-alerts)
- **Integrity modes** — [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md)
- **InteractiveError (validation results → marked controls)** — [InteractiveError](../../../tentackle-fx/src/main/docs/interactive-error.md), [Validation](../../../tentackle-core/src/main/docs/validation.md)
- **Interceptors** — [Interceptors](../../../tentackle-core/src/main/docs/interceptors.md)
- **Interrupts (`InterruptedRuntimeException`)** — [Exceptions and Error Handling](./exceptions.md#interrupts)
- **Idle transactions (transaction supervisor, `txIdleTimeout`)** — [Tentackle Database](../../../tentackle-database/src/main/docs/database.md#idle-transaction-supervisor), [Backend Properties](../../../tentackle-session/src/main/docs/backend-properties.md#server-keys)

## J

- **JPMS / Jigsaw (modules, automatic modules)** — [Tentackle Modules](./modules.md#named-modules-and-automatic-modules), [Quickstart](./quickstart.md)
- **JPMS: exporting new packages (wizard profiles, first operation)** — [Quickstart](./quickstart.md#the-first-operation-jpms-does-not-come-for-free), [Wizard Maven Plugin](../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md#how-the-wizard-places-files-the-model-and-profiles)
- **Jlink / Jpackage images** — [Jlink/Jpackage Maven Plugin](../../../tentackle-jlink-maven-plugin/src/main/docs/jlinkpackage.md), [Quickstart](./quickstart.md)
- **Joins (`Join`, `JoinedSelect`, `JoinState`)** — [Tentackle Persistence](../../../tentackle-persistence/src/main/docs/persistence.md#joins), [Eager Relations](../../../tentackle-persistence-wurblets/src/main/docs/eager-relations.md), [The Persistence Wurblet API](../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblet-api.md)
- **JTA / XA (`jta` backend property, `XaConnectionManager`, `XaMpxConnectionManager`)** — [Global Transactions — JTA and XA](../../../tentackle-jta/src/main/docs/jta.md), [Tentackle Database](../../../tentackle-database/src/main/docs/database.md#global-transactions)
- **JWT / ID token verification** — [Single Sign-On with Google](./google-sso.md)
- **JWT / tokens on the client (storage)** — [Keyring](../../../tentackle-core/src/main/docs/keyring.md), [Single Sign-On with Google](./google-sso.md)
- **JSON / JSONB (model type ↔ database column type)** — [The PostgreSQL Backend](../../../tentackle-sql/src/main/docs/postgres.md#json-and-jsonb), [The MySQL and MariaDB Backends](../../../tentackle-sql/src/main/docs/mysql.md#json), [Tentackle SQL](../../../tentackle-sql/src/main/docs/sql.md)

## K

- **Keep-alive (sessions, `SessionKeepAliveDaemon`)** — [Tentackle Session](../../../tentackle-session/src/main/docs/session.md#session-task-dispatching), [Tentackle Session — Session utilities](../../../tentackle-session/src/main/docs/session.md#session-utilities)
- **Keychain (macOS), Credential Manager (Windows), Secret Service (Linux)** — [OS Credential Stores](../../../tentackle-keyring/src/main/docs/platform-keyring.md)
- **Keyring (secrets on the client)** — [Keyring](../../../tentackle-core/src/main/docs/keyring.md), [OS Credential Stores](../../../tentackle-keyring/src/main/docs/platform-keyring.md), [Single Sign-On with Google](./google-sso.md)
- **Keyboard handling (ENTER navigation, `Ctrl-Z`/`Ctrl-Y`, `F1`)** — [tentackle-fx](../../../tentackle-fx/src/main/docs/fx.md#keyboard-handling), [tentackle-fx-rdc](../../../tentackle-fx-rdc/src/main/docs/rdc.md#pdo-bound-components) (`F2`/`F3`)

## L

- **Location transparency (and its limits)** — [Location Transparency vs. REST, RMI and Spring Remoting](./remoting-comparison.md), [Multi-Tier Cascade](./tiers.md), [Tentackle Session](../../../tentackle-session/src/main/docs/session.md), [PDO](../../../tentackle-pdo/src/main/docs/pdo.md)
- **Locking (optimistic `serial`, token locks)** — [Locking](../../../tentackle-persistence/src/main/docs/locking.md)
- **LockManager (token lock cleanup on logout, crash and restart)** — [Locking](../../../tentackle-persistence/src/main/docs/locking.md#the-lockmanager-surviving-crashes), [Tentackle Persistence](../../../tentackle-persistence/src/main/docs/persistence.md#token-locking)
- **`Loggable` (how much of an exception to log)** — [Exceptions and Error Handling](./exceptions.md#loggable-how-much-of-an-exception-to-log), [Tentackle Logging](../../../tentackle-core/src/main/docs/logging.md)
- **Logging** — [Tentackle Logging](../../../tentackle-core/src/main/docs/logging.md), [SLF4J Provider](../../../tentackle-log-slf4j/src/main/docs/tentackle-log-slf4j.md), [Log4J 2 Provider](../../../tentackle-log-log4j2v/src/main/docs/tentackle-log-log4j2v.md)
- **Login view (backend selection, failed logins, custom login view)** — [tentackle-fx-rdc](../../../tentackle-fx-rdc/src/main/docs/rdc.md#application-lifecycle-app-login), [Authentication](../../../tentackle-session/src/main/docs/authentication.md)

## M

- **MasterSerialEvent (server-to-client events)** — [Modification Tracking](../../../tentackle-session/src/main/docs/modification-tracking.md), [Tentackle Session](../../../tentackle-session/src/main/docs/session.md)
- **Maven plugins** — [Tentackle Maven Plugin](../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md), [Maven Support](../../../tentackle-maven-support/src/main/docs/tentackle-maven-support.md), [Build Support](../../../tentackle-build-support/src/main/docs/build-support.md)
- **MCP server (coding agents, model introspection)** — [MCP Maven Plugin](../../../tentackle-mcp-maven-plugin/src/main/docs/tentackle-mcp-maven-plugin.md), [AI Agent Tooling Support](./ai-agents.md)
- **Migration (RMI → TRIP)** — [Migrate from RMI to TRIP](./migrate_rmi_trip.md)
- **Mixins (multiple-inheritance emulation)** — [Reflection](../../../tentackle-core/src/main/docs/reflect.md), [PDO](../../../tentackle-pdo/src/main/docs/pdo.md)
- **Mock delegates (database-less unit tests)** — [Tentackle PDO Mocks](../../../tentackle-pdo-mock/src/main/docs/pdo-mock.md), [tentackle-test-pdo](../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md)
- **Model defaults (`modelDefaults`, `nodefaults`)** — [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md#overriding-model-defaults), [Tentackle Model](../../../tentackle-model/src/main/docs/tentackle-model.md#modeldefaults-and-entityaliases)
- **Model definition (DSL)** — [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md), [Tentackle Model](../../../tentackle-model/src/main/docs/tentackle-model.md), [MCP Maven Plugin](../../../tentackle-mcp-maven-plugin/src/main/docs/tentackle-mcp-maven-plugin.md)
- **Model documentation (browsable HTML/Markdown pages, `tentackle-sql:document`)** — [SQL Maven Plugin](../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md#the-document-goal)
- **Modification log serialization (`TripModificationLogSerializer`, RESET, epochs)** — [Tentackle Database](../../../tentackle-database/src/main/docs/database.md#modification-logging), [Tentackle Persistence](../../../tentackle-persistence/src/main/docs/persistence.md)
- **Modification table generation (reinitialization detection)** — [Modification Tracking](../../../tentackle-session/src/main/docs/modification-tracking.md#the-generation)
- **Modification tracking** — [Modification Tracking](../../../tentackle-session/src/main/docs/modification-tracking.md), [Tentackle Session](../../../tentackle-session/src/main/docs/session.md)
- **ModuleHook** — [Service and Configuration API](../../../tentackle-common/src/main/docs/services.md#the-modulehook), [Resource Bundles](../../../tentackle-common/src/main/docs/bundle.md)
- **ModuleLayer (isolated dependency versions)** — [Isolated Module Layers](../../../tentackle-common/src/main/docs/layers.md), [jlinkpackage.md](../../../tentackle-jlink-maven-plugin/src/main/docs/jlinkpackage.md#isolated-module-layers)
- **Module ordering / service precedence (`@ModuleOrdinal`, `@ClasspathFirst`)** — [Service and Configuration API](../../../tentackle-common/src/main/docs/services.md#ordering-and-overriding)
- **Modules** — [Tentackle Modules](./modules.md)
- **Multi-tier deployment** — [Multi-Tier Cascade](./tiers.md)
- **MySQL / MariaDB (backend peculiarities)** — [The MySQL and MariaDB Backends](../../../tentackle-sql/src/main/docs/mysql.md)

## N

- **Naming rules (derived class names, `NamingRules`)** — [Naming Rules](../../../tentackle-common/src/main/docs/naming-rules.md)
- **NoBundle (bundle look-alikes, excluded from `tentackle-check:bundles`)** — [Resource Bundles and Internationalization](../../../tentackle-common/src/main/docs/bundle.md#the-bundle-convention), [Checker Maven Plugin](../../../tentackle-check-maven-plugin/src/main/docs/tentackle-check-maven-plugin.md#the-bundles-goal)
- **N:M relations** — [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md)
- **Normtext (phonetically normalized search, `NormText`)** — [Tentackle Persistence](../../../tentackle-persistence/src/main/docs/persistence.md#normtext), [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md)
- **Number pools / ranges** — [Number Sources](../../../tentackle-pdo/src/main/docs/ns.md), [Tentackle Persistence](../../../tentackle-persistence/src/main/docs/persistence.md#built-in-persistence-implementations)
- **Number sources (unique business numbers)** — [Number Sources](../../../tentackle-pdo/src/main/docs/ns.md)
- **Notes and notifications (non-blocking dialogs, `NotificationBuilder`)** — [tentackle-fx](../../../tentackle-fx/src/main/docs/fx.md#notes-and-notifications)

## O

- **Object identity (`equals`/`hashCode`/`compareTo`, id-based, transient trap, why relations are lists not sets)** — [PDO](../../../tentackle-pdo/src/main/docs/pdo.md#object-identity--equals-hashcode-and-why-relations-are-lists)
- **Operations (behavior without an entity)** — [Operation](../../../tentackle-pdo/src/main/docs/operation.md)
- **Optimistic locking (`serial` column, lost updates)** — [Locking](../../../tentackle-persistence/src/main/docs/locking.md#optimistic-locking-the-serial-column), [Correctness First](./correctness-first.md)
- **Optional query conditions (QBF-style finders)** — [Query Methods](../../../tentackle-persistence-wurblets/src/main/docs/pdo-select.md#optional-conditions-qbf-style-finders)
- **Oracle (backend peculiarities)** — [The Oracle Backend](../../../tentackle-sql/src/main/docs/oracle.md)
- **ORM comparison** — [PDOs vs. Traditional ORMs](../../../tentackle-pdo/src/main/docs/pdo-vs-orm.md)
- **OAuth2 / OIDC login** — [Authentication](../../../tentackle-session/src/main/docs/authentication.md), [Single Sign-On with Google](./google-sso.md)
- **OSGi bundles (building, activators, `RESOURCE-INDEX.LIST`)** — [OSGi Bundles](../../../tentackle-osgi/src/main/docs/osgi.md), [Service and Configuration API](../../../tentackle-common/src/main/docs/services.md#osgi-bundles)
- **Online help (`F1`, help URLs)** — [tentackle-fx](../../../tentackle-fx/src/main/docs/fx.md#online-help)

## P

- **PDO (Persistent Domain Objects)** — [PDO](../../../tentackle-pdo/src/main/docs/pdo.md), [PDOs vs. ORMs](../../../tentackle-pdo/src/main/docs/pdo-vs-orm.md)
- **PDO Browser (generating test-fixture code)** — [Wizard Maven Plugin](../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md#the-browse-goal), [tentackle-test-pdo](../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md#generating-fixture-code-with-the-pdo-browser), [AI Agent Tooling Support](./ai-agents.md#java-code-from-the-database-tentackle-wizardbrowse-in-batch-mode)
- **Parallel builds (`mvn -T`, thread-safe plugins and wurblets)** — [Tentackle Maven Support](../../../tentackle-maven-support/src/main/docs/tentackle-maven-support.md#parallel-builds), [Wurblets](../../../tentackle-wurblets/src/main/docs/wurblets.md#parallel-builds), [The Wurblet API](../../../tentackle-wurblets/src/main/docs/wurblet-api.md#thread-safety)
- **Password input (`char[]`, `FxPasswordField`)** — [tentackle-fx](../../../tentackle-fx/src/main/docs/fx.md#secure-password-input)
- **Passwords / credentials (storage, encryption)** — [Cryptor and EncryptedProperties](../../../tentackle-common/src/main/docs/cryptor.md), [Authentication](../../../tentackle-session/src/main/docs/authentication.md#the-credential-model), [Tentackle Session](../../../tentackle-session/src/main/docs/session.md)
- **PKCE (authorization code flow with proof key)** — [Single Sign-On with Google](./google-sso.md)
- **Permissions / grantees** — [Security](../../../tentackle-pdo/src/main/docs/security.md)
- **Persistence layer** — [Tentackle Persistence](../../../tentackle-persistence/src/main/docs/persistence.md)
- **Pooling (resources, sessions)** — [Miscellaneous](../../../tentackle-core/src/main/docs/misc.md), [Tentackle Session](../../../tentackle-session/src/main/docs/session.md)
- **PostgreSQL (backend peculiarities, `JSON`/`JSONB`)** — [The PostgreSQL Backend](../../../tentackle-sql/src/main/docs/postgres.md)
- **Printing (tables, tree tables)** — [tentackle-fx-rdc](../../../tentackle-fx-rdc/src/main/docs/rdc.md#table-popup-export-and-printing)
- **Projection queries (plain data instead of PDOs)** — [The Query DSL](../../../tentackle-database/src/main/docs/query-dsl.md), [Tentackle Database](../../../tentackle-database/src/main/docs/database.md)
- **Properties (loading, change events)** — [Miscellaneous](../../../tentackle-core/src/main/docs/misc.md)
- **Preferences (database-backed settings)** — [Preferences](../../../tentackle-core/src/main/docs/preferences.md)
- **Preferenzr (preferences editor)** — [Preferences](../../../tentackle-core/src/main/docs/preferences.md#preferenzr--browsing-and-editing-the-store)
- **Preloading PDO caches (`@PreloadCache`)** — [PDO Caching](../../../tentackle-pdo/src/main/docs/cache.md#preloading-caches-at-startup-preloadcache), [Application Bootstrap](../../../tentackle-pdo/src/main/docs/application.md)
- **Preloading remote delegates (`@PreloadRemoteDelegate`)** — [Tentackle Database](../../../tentackle-database/src/main/docs/database.md#preloading-remote-delegates), [TRIP](../../../tentackle-core/src/main/docs/trip.md#returning-several-remote-objects-at-once)
- **Pre-/post-commit methods (`@PreCommit`, `@PostCommit`)** — [Interceptors](../../../tentackle-core/src/main/docs/interceptors.md#precommit-and-postcommit--deferred-until-the-commit), [Transactions](../../../tentackle-pdo/src/main/docs/transaction.md)
- **PDO-bound components (`F2`/`F3`, drag and drop, self-loading combo boxes)** — [tentackle-fx-rdc](../../../tentackle-fx-rdc/src/main/docs/rdc.md#pdo-bound-components)
- **Placeholders in property values (`@{name?default}`, `${…}`)** — [Backend Properties](../../../tentackle-session/src/main/docs/backend-properties.md#overview-and-motivation), [Tentackle Common](../../../tentackle-common/src/main/docs/common.md#helper-utilities)

## Q

- **Query DSL (typed `SELECT`s, aggregates, joins)** — [The Query DSL](../../../tentackle-database/src/main/docs/query-dsl.md)
- **QUIC transport** — [TRIP over QUIC](../../../tentackle-quic/src/main/docs/quic.md), [TRIP over Cloudflare quiche](../../../tentackle-quiche/src/main/docs/quiche.md), [TRIP](../../../tentackle-core/src/main/docs/trip.md)
- **Quickstart** — [Quickstart](./quickstart.md)

## R

- **Referential integrity (`isReferenced`, `PdoIsReferencing`)** — [Persistence Wurblets](../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md#pdoisreferencing--the-referential-integrity-guard), [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md)
- **Reflection (proxies, class mapping, helpers)** — [Reflection](../../../tentackle-core/src/main/docs/reflect.md), [Interceptors](../../../tentackle-core/src/main/docs/interceptors.md)
- **Relations (object, list, reversed, N:M)** — [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md), [PdoSelectList / PdoSelectUnique](../../../tentackle-persistence-wurblets/src/main/docs/pdo-select.md), [Eager Relations](../../../tentackle-persistence-wurblets/src/main/docs/eager-relations.md)
- **Reconnection (dead resources, policies)** — [I/O and Networking](../../../tentackle-core/src/main/docs/io.md), [Tentackle Database](../../../tentackle-database/src/main/docs/database.md)
- **`@RecordDTO` (generating DTOs from records)** — [The DTO Wurblet and `@RecordDTO`](../../../tentackle-wurblets/src/main/docs/dto-wurblet.md)
- **Remote delegates (naming, lookup, preloading)** — [Naming Rules](../../../tentackle-common/src/main/docs/naming-rules.md), [Multi-Tier Cascade](./tiers.md), [Tentackle Database](../../../tentackle-database/src/main/docs/database.md#preloading-remote-delegates)
- **Remoting** — [TRIP](../../../tentackle-core/src/main/docs/trip.md), [QUIC](../../../tentackle-quic/src/main/docs/quic.md), [QUIC on quiche](../../../tentackle-quiche/src/main/docs/quiche.md), [vs. REST, RMI and Spring Remoting](./remoting-comparison.md)
- **REST (why not between your own tiers, and where it does belong)** — [Location Transparency vs. REST, RMI and Spring Remoting](./remoting-comparison.md), [Tentackle Web](../../../tentackle-web/src/main/docs/web.md)
- **RMI (comparison and migration)** — [Location Transparency vs. REST, RMI and Spring Remoting](./remoting-comparison.md#part-2--tentackle-vs-rmi-and-spring-remoting), [Migrate from RMI to TRIP](./migrate_rmi_trip.md), [TRIP](../../../tentackle-core/src/main/docs/trip.md)
- **Retry (transparent transaction retry, backoff policies)** — [Transactions and Transparent Retry](../../../tentackle-pdo/src/main/docs/transaction.md), [Locking](../../../tentackle-persistence/src/main/docs/locking.md)
- **Reverse engineering (existing database → model source, `reveng` goal)** — [SQL Maven Plugin](../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md#the-reveng-goal), [AI Agent Tooling Support](./ai-agents.md#bootstrapping-from-an-existing-database)
- **rootId / rootClassId** — [rootId / rootClassId Columns](../../../tentackle-pdo/src/main/docs/root-columns.md), [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md)

## S

- **SAP HANA (backend peculiarities)** — [The SAP HANA Backend](../../../tentackle-sql/src/main/docs/hana.md)
- **Scripting** — [Tentackle Scripting](../../../tentackle-core/src/main/docs/scripting.md), [CompoundValue](../../../tentackle-core/src/main/docs/compound-value.md), [Groovy](../../../tentackle-script-groovy/src/main/docs/groovy.md), [JRuby](../../../tentackle-script-ruby/src/main/docs/ruby.md), [JSR-223](../../../tentackle-script-jsr/src/main/docs/jsr.md)
- **Secrets in configuration (`~`-encrypted properties)** — [Cryptor and EncryptedProperties](../../../tentackle-common/src/main/docs/cryptor.md)
- **Secrets on the client (SSO tokens, passwords)** — [Keyring](../../../tentackle-core/src/main/docs/keyring.md)
- **Schema mapping (`mapSchema` / `mapSchemas`)** — [Table Names](../../../tentackle-session/src/main/docs/table-names.md), [SQL Maven Plugin](../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md), [The Oracle Backend](../../../tentackle-sql/src/main/docs/oracle.md), [The Firebird Backend](../../../tentackle-sql/src/main/docs/firebird.md)
- **Schema migration (`tentackle-sql:migrate`, migration hints)** — [SQL Maven Plugin](../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md#the-migrate-goal), [AI Agent Tooling Support](./ai-agents.md#following-the-model-tentackle-sqlmigrate)
- **Security model** — [Security](../../../tentackle-pdo/src/main/docs/security.md), [Tentackle Persistence](../../../tentackle-persistence/src/main/docs/persistence.md#built-in-persistence-implementations)
- **Security rules editor (`SecurityRulesView`, `SecurityDialogFactory`)** — [tentackle-fx-rdc](../../../tentackle-fx-rdc/src/main/docs/rdc.md#security-and-administration)
- **Server application (`ServerApplication`, `AbstractServerApplication`)** — [Tentackle Persistence](../../../tentackle-persistence/src/main/docs/persistence.md#server-application-lifecycle), [Application Bootstrap](../../../tentackle-pdo/src/main/docs/application.md), [Multi-Tier Cascade](./tiers.md)
- **Select methods (generated queries)** — [PdoSelectList / PdoSelectUnique](../../../tentackle-persistence-wurblets/src/main/docs/pdo-select.md)
- **Streaming results / batch processing** — [Cursors](../../../tentackle-persistence/src/main/docs/cursors.md), [SQL Statement Batching](../../../tentackle-database/src/main/docs/batching.md)
- **Serialization (wire format)** — [TRIP](../../../tentackle-core/src/main/docs/trip.md)
- **ServiceFinder / SPI** — [Service and Configuration API](../../../tentackle-common/src/main/docs/services.md)
- **Services (`@Service`, `@ServiceName`, `@MappedService`)** — [Service and Configuration API](../../../tentackle-common/src/main/docs/services.md#the-annotations), [Reflection](../../../tentackle-core/src/main/docs/reflect.md)
- **Session** — [Tentackle Session](../../../tentackle-session/src/main/docs/session.md)
- **Session pool** — [Tentackle Session](../../../tentackle-session/src/main/docs/session.md), [Background Execution](../../../tentackle-fx-rdc/src/main/docs/background-execution.md)
- **SessionPooledExecutor (parallel background work on pooled sessions)** — [Tentackle Session](../../../tentackle-session/src/main/docs/session.md#session-creation-factory-and-pooling)
- **SessionUtilities (class/table/class-id lookup, keep-alive daemon, exception hooks)** — [Tentackle Session](../../../tentackle-session/src/main/docs/session.md#session-utilities), [Exceptions and Error Handling](./exceptions.md)
- **Single sign-on (SSO)** — [Authentication](../../../tentackle-session/src/main/docs/authentication.md), [Single Sign-On with Google](./google-sso.md)
- **Singular / plural names (`@Singular`, `@Plural`, PDO bundle keys)** — [Wizard Maven Plugin](../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md#what-is-left-to-do), [MyApp Walkthrough](../../../tentackle-project-archetype/src/main/docs/myapp-walkthrough.md#16-how-to-extend-it)
- **SessionTaskDispatcher (session-aware background tasks)** — [Tasks and Daemons](../../../tentackle-core/src/main/docs/tasks-daemon.md#which-dispatcher-do-i-want), [Background Execution](../../../tentackle-fx-rdc/src/main/docs/background-execution.md)
- **Snapshots and copies** — [Snapshots and Copies](../../../tentackle-pdo/src/main/docs/snapshot.md)
- **Spreadsheet / XLS / Excel export** — [tentackle-fx-rdc-poi](../../../tentackle-fx-rdc-poi/src/main/docs/poi.md), [Tables and Trees](../../../tentackle-fx/src/main/docs/tables.md), [tentackle-fx-rdc](../../../tentackle-fx-rdc/src/main/docs/rdc.md#table-popup-export-and-printing)
- **Spring Remoting (comparison)** — [Location Transparency vs. REST, RMI and Spring Remoting](./remoting-comparison.md#part-2--tentackle-vs-rmi-and-spring-remoting)
- **SQL generation** — [SQL Maven Plugin](../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md), [Tentackle SQL](../../../tentackle-sql/src/main/docs/sql.md)
- **SQL script execution (`tentackle-sql:run`)** — [SQL Maven Plugin](../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md#the-run-goal), [AI Agent Tooling Support](./ai-agents.md#plain-sql-tentackle-sqlrun)
- **Startup / shutdown sequence** — [Application Bootstrap](../../../tentackle-pdo/src/main/docs/application.md)
- **Stateless domain logic (warning)** — [Annotation Processors](../../../tentackle-core/src/main/docs/annotation-processors.md#the-stateless-domain-logic-warning), [Tentackle Domain](../../../tentackle-domain/src/main/docs/domain.md)
- **Statement history limit (per transaction, `statementHistoryLimit`)** — [Tentackle Database](../../../tentackle-database/src/main/docs/database.md#diagnostics-and-statistics), [Transactions](../../../tentackle-pdo/src/main/docs/transaction.md#limiting-the-statement-history-with-statementhistorylimit)
- **Session administration (list, log out, kill client sessions)** — [tentackle-fx-rdc](../../../tentackle-fx-rdc/src/main/docs/rdc.md#session-administration), [Tentackle Persistence](../../../tentackle-persistence/src/main/docs/persistence.md#remoting-trip)

## T

- **Technical & scientific applications** — [Why Tentackle Fits Technical and Scientific Applications](./technical-scientific.md)
- **Temporary exceptions (the retry classifier)** — [Exceptions and Error Handling](./exceptions.md#the-temporary-flag), [Transactions and Transparent Retry](../../../tentackle-pdo/src/main/docs/transaction.md#what-is-retried--temporary-exceptions)
- **Tables / tree-tables (configuration, cell types, layouts)** — [Tables and Trees](../../../tentackle-fx/src/main/docs/tables.md), [tentackle-fx-rdc](../../../tentackle-fx-rdc/src/main/docs/rdc.md)
- **Table aliases (`alias`, automatic generation)** — [Tentackle Model](../../../tentackle-model/src/main/docs/tentackle-model.md#table-aliases), [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md)
- **Table names (`@TableName`, schemas, prefixes)** — [Table Names](../../../tentackle-session/src/main/docs/table-names.md), [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md)
- **Table popup (column chooser, CSV/XML export, table preferences)** — [tentackle-fx-rdc](../../../tentackle-fx-rdc/src/main/docs/rdc.md#table-popup-export-and-printing)
- **Tasks / task dispatcher (background execution)** — [Tasks and Daemons](../../../tentackle-core/src/main/docs/tasks-daemon.md)
- **Testing (TestNG support)** — [tentackle-test-pdo](../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md), [tentackle-test-fx-rdc](../../../tentackle-test-fx-rdc/src/main/docs/tentackle-test-fx-rdc.md), [Tentackle PDO Mocks](../../../tentackle-pdo-mock/src/main/docs/pdo-mock.md)
- **Tiers (nested servers/clients)** — [Multi-Tier Cascade](./tiers.md)
- **Token locks** — [Locking](../../../tentackle-persistence/src/main/docs/locking.md)
- **Tokens (bearer, refresh, expiry)** — [Authentication](../../../tentackle-session/src/main/docs/authentication.md#the-credential-model)
- **Transactional outbox (instead of XA)** — [Global Transactions — JTA and XA](../../../tentackle-jta/src/main/docs/jta.md#when-not-to-use-xa), [Modification Tracking](../../../tentackle-session/src/main/docs/modification-tracking.md)
- **Transactions (`@Transaction`, isolation, retry)** — [Transactions and Transparent Retry](../../../tentackle-pdo/src/main/docs/transaction.md), [Tentackle Session](../../../tentackle-session/src/main/docs/session.md), [PDOs vs. ORMs](../../../tentackle-pdo/src/main/docs/pdo-vs-orm.md), [Global Transactions — JTA and XA](../../../tentackle-jta/src/main/docs/jta.md)
- **Transports (pluggable)** — [TRIP](../../../tentackle-core/src/main/docs/trip.md), [QUIC](../../../tentackle-quic/src/main/docs/quic.md), [QUIC on quiche](../../../tentackle-quiche/src/main/docs/quiche.md)
- **Two-phase commit (XA)** — [Global Transactions — JTA and XA](../../../tentackle-jta/src/main/docs/jta.md)
- **TripFilter (skipping components, PDOs without components)** — [TRIP](../../../tentackle-core/src/main/docs/trip.md#filtering-components-with-tripfilter)
- **TRIP (remote invocation protocol)** — [TRIP](../../../tentackle-core/src/main/docs/trip.md), [QUIC](../../../tentackle-quic/src/main/docs/quic.md), [QUIC on quiche](../../../tentackle-quiche/src/main/docs/quiche.md), [Migrate from RMI to TRIP](./migrate_rmi_trip.md)
- **TCP transport parameters (connection pool, backlog, compression)** — [TRIP](../../../tentackle-core/src/main/docs/trip.md#transport)
- **Testing across JVMs (`ServerProcessWrapper`)** — [tentackle-test-pdo](../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md#client-server-tests-across-jvms-serverprocesswrapper)

## U

- **Unexpected (deviations from mainstream Java frameworks)** — [What a Seasoned Java Developer Would Not Expect](./unexpected.md)
- **Unique domain key (UDK)** — [Domain Keys](../../../tentackle-pdo/src/main/docs/domain-key.md)
- **Update (self-update service)** — [Tentackle Update](../../../tentackle-update/src/main/docs/update.md), [FX RDC Update](../../../tentackle-fx-rdc-update/src/main/docs/fx-rdc-update.md)
- **UserResolver (principal → user entity)** — [Authentication](../../../tentackle-session/src/main/docs/authentication.md#from-a-principal-to-a-user-the-userresolver), [Single Sign-On with Google](./google-sso.md)

## V

- **Validation** — [Validation](../../../tentackle-core/src/main/docs/validation.md), [CompoundValue](../../../tentackle-core/src/main/docs/compound-value.md), [Check Maven Plugin](../../../tentackle-check-maven-plugin/src/main/docs/tentackle-check-maven-plugin.md), [InteractiveError](../../../tentackle-fx/src/main/docs/interactive-error.md) (showing results in the UI)
- **Version negotiation (QUIC)** — [TRIP over Cloudflare quiche](../../../tentackle-quiche/src/main/docs/quiche.md#server-side)
- **Version numbering (Java LTS prefix, release lines)** — [The History of Tentackle](./history.md#the-version-scheme)
- **View-modified tracking (dirty forms, `Ctrl-Z`)** — [tentackle-fx](../../../tentackle-fx/src/main/docs/fx.md#view-modified-tracking)

## W

- **Walkthrough (generated application)** — [MyApp Walkthrough](../../../tentackle-project-archetype/src/main/docs/myapp-walkthrough.md)
- **When to use Tentackle (and when not to)** — [Location Transparency vs. REST, RMI and Spring Remoting](./remoting-comparison.md#choosing), [Technical & Scientific Applications](./technical-scientific.md), [What a Seasoned Java Developer Would Not Expect](./unexpected.md)
- **Web integration** — [Tentackle Web](../../../tentackle-web/src/main/docs/web.md)
- **Wizard (PDO/operation generator)** — [Wizard Maven Plugin](../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md), [AI Agent Tooling Support](./ai-agents.md) (headless batch modes)
- **Wizard: what is left to do after generating a PDO (bundles, GuiProvider)** — [Wizard Maven Plugin](../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md#what-is-left-to-do), [GuiProvider](../../../tentackle-fx-rdc/src/main/docs/gui-provider.md#level-1-the-pdo-model)
- **Wurblet API (`ModelWurblet`, `WurbletArgument`, `JoinPath`)** — [The Wurblet API](../../../tentackle-wurblets/src/main/docs/wurblet-api.md), [The Persistence Wurblet API](../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblet-api.md) (`DbModelWurblet`, the clause generators)
- **Wurblet arguments (the query mini-language)** — [The Wurblet API](../../../tentackle-wurblets/src/main/docs/wurblet-api.md#the-wurblet-argument-mini-language), [PdoSelectList / PdoSelectUnique](../../../tentackle-persistence-wurblets/src/main/docs/pdo-select.md)
- **Wurblets / Wurbelizer** — [Wurblets](../../../tentackle-wurblets/src/main/docs/wurblets.md), [Persistence Wurblets](../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md), [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md), [The Wurblet API](../../../tentackle-wurblets/src/main/docs/wurblet-api.md)
