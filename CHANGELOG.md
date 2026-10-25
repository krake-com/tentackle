# Changelog

All notable changes to Tentackle are documented in this file. It covers every tagged release since
2.0.0, the first release in the public git history.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). The entries are
curated highlights, not a complete list of commits — refer to the git history for every detail.
For the story behind the releases, see [The History of Tentackle](src/main/docs/history.md).

## Versioning

Since 8.2.2.0, a version reads `<java>.<feature>.<minor>.<patch>`:

- **java** — the minimum JDK the release requires (8, 11, 17, 21, 25).
- **feature** — the feature level. Since the Java 11 line, each new Java line continues with the
  next feature level (11.7 → 17.8, 17.14 → 21.15, 21.16 → 25.17). The Java 8 line counts on its own.
- **minor**, **patch** — enhancements and fixes within a feature level.

Older Java lines are maintained on their own branches (`java8`, `java11`, `java17`, `java21`) and
still receive maintenance releases. The releases are therefore grouped by Java line below, newest
line first, and listed newest-first within each line. A maintenance release lists only what changed
on its branch.

Releases before 8.2.2.0 use plain `2.x.y` version numbers. They require Java 8, except 2.2.0, which
requires Java 11.

## [Unreleased]

### Changed

- Faster PDO and operation invocation handlers: `hashCode`, `equals`, `compareTo` and `toString` are dispatched
  without `Method.equals` chains, and `getEffectiveSuperClasses()` is computed once per class (and is now unmodifiable).
- `Mixin` is now an immutable descriptor shared by all proxies of the same type (`Mixin.get`, `Mixin.forImplementation`).
  It no longer holds the delegate: the invocation handlers hold their delegates directly, which saves two objects per
  PDO and one indirection per method call, and TRIP transfers the delegates instead of the mixins.
  `setDelegate`/`getDelegate` and the dummy constructor `Mixin(Class, Class)` were removed, and `createDelegate` now
  returns the delegate.
- TRIP no longer mutates final fields (JEP-500). Deserializing a final field that is not passed to a canonical
  constructor fails with a `TripRuntimeException`. The migration aid `-Dtentackle.trip.mutateFinalFields=true`
  (plus `--enable-final-field-mutation=org.tentackle.core`) restores the old behavior.
  Canonical members are now also matched against superclass fields.
- Final fields that were deserialized are now passed to canonical constructors (`CopyOnWriteCollection`,
  `CopyOnWriteList`, `CopyOnWriteSet`, `FailedValidationResult`, `InfoValidationResult`,
  `VersionIncompatibleException`, `RebuildEpochException`), transient (`ImmutableArrayList`'s hash) or no longer final (`DbPreferences`, `ParameterString`,
  `IgnoreDuplicatesPersistenceVisitor`, `ScriptRunnerException`). The protected no-arg constructors of the validation results were replaced
  by constructors taking the serialized values.
- Binding treats final fields without a setter as read-only, and the NoFXML linker refuses to inject into final
  fields.

### Fixed

- `tentackle-jlink-maven-plugin`: jlink of Java 27 refuses to link a resource directory found in more than one
  module, such as `OSGI-INF` in ikonli 12.4.0. The new parameter `excludeResources` (jlink's `--exclude-resources`)
  excludes `**/OSGI-INF/**` by default.

## Java 25 line (25.x)

### [25.20.0.0] – 2026-10-23

### Added

- `tentackle-jta`: JTA/XA support for global transactions.
- `tentackle-keyring`: keyring API in `tentackle-core` and an opt-in module for the platform keyrings
  of Linux, macOS and Windows. `Secret` moved to `tentackle-core`, `Secrets` was renamed to `SecretsHelper`.
- Authentication provider API (with an immutable provider chain), plus documentation for Google SSO.
- SQL backends for Oracle 21 (native JSON), Oracle 23 (native `BOOLEAN`, `IF EXISTS`), CockroachDB,
  SAP HANA and Firebird. Support for the `JSON` and `SQLXML` data types and `DataType#isComparable`.
- Model type `byte[]` (`ByteArrayType`): an empty array is now read back as an empty array, not as `null`.
- `tentackle-sql:document` goal: generates cross-linked HTML or Markdown pages from the model, with
  `NOT NULL`, indexes, defaults, aggregates and N:M composite relations.
- `tentackle-sql:reveng` goal for reverse engineering existing databases.
- Module layer support (`ModuleLayer`), also in the wizard plugin.
- Deferred transactions, `@Persistent#embedded` for embedded PDOs and an eager-join depth in the
  model and the persistence layer.
- `@Notinterceptable` with an annotation processor for methods sealed against interception.
- `@NoBundle` to exclude classes from bundle detection. `tentackle-check:bundles` also resolves
  string constants and simple constant expressions.
- `TripFilter` for `TripStream`, to skip components during serialization.
- `TripModificationLogSerializer`. Modification logging was refactored and `ModificationLoggable`
  was removed.
- `Session#setStatementHistoryLimit`. The statement history detects loops and compresses them in the log.
- Source port migration for stalled `tentackle-quiche` connections.
- Table editing via the new `TableEditHandler` API (package `table.edit`), which replaces `EDITMODE`.
- Visual feedback for rejected input in the FX controls.
- `ARCHITECTURE.md`, `CONTRIBUTING.md` and `history.md`.

### Changed

- The repository moved from Bitbucket to GitHub.
- All maven plugins and the wurblets are thread-safe, Maven 4 compliant (Maven 3 still supported)
  and compatible with reproducible builds. The minimum Maven version is 3.9.10.
- Plexus `@Component` annotations replaced by JSR 330 `@Inject`.
- `ObjectSequenceId` uses backend-specific sequences and allows concurrent fetches without a lock.
  `ObjectId` was refactored to avoid possible deadlocks.
- `DbTransactionFactory` shuts down gracefully and keeps running until all transactions have
  finished or timed out.
- Server certificate check is now on by default for `tentackle-quic` and `tentackle-quiche`. The TCP and
  QUIC(HE) transport configurations were aligned (hostname verification).
- The PDO and operation wizards add imports and validation imports, which better supports agentic
  coding in batch mode.
- Performance and memory improvements in `tentackle-common`, `tentackle-core`, `tentackle-database`,
  TRIP and `I18NText`.
- added URI parameter `nodelay` to turn off `TCP_NODELAY` which is on by default

### Fixed

- Many concurrency fixes in `tentackle-database`: races in `DbModificationTracker`, `DbModification`
  (concurrent ID collisions), `MultiUserDbPool`, `AbstractPool#monitor`, the connection managers
  (concurrent logins, dead connection cleanup, no login during shutdown) and `StatementWrapper`
  visibility.
- `tentackle-quiche`: the connection is closed when no spare connection IDs are left and a stall
  is detected. The connection is re-established proactively before the free CIDs run out.
- `tentackle-quic`: write retry for interrupted writes. Connect timeout added to `TcpConnectionPool`.
- The `Dictionary` is no longer garbage collected while a client session is still open.
- `ResultSetCursor#beforeFirst()` with joined selects, and table alias retrieval for multi-table
  inheritance with eager joins.
- TRIP: serialization of `DefaultAuthentication`, deserialization of `TripError` and
  `TripNoSuchDelegateException`, and a corner case of the sparse `byte[]` optimization.
- Windows: the 4K pipe problem and several Windows-specific test issues.

### Removed

- Usages of the obsolete `java.lang.SecurityException`.
- `MAPNULL` support for `SQLXMLType`.

### [25.19.0.0] – 2026-08-19

#### Added

- `tentackle-quiche`: second QUIC transport (`tripj`/`tripcj`) on Cloudflare quiche, bound via the
  FFM API and interoperable with `tentackle-quic`. It is also available as an OSGi bundle, like `tentackle-quic`.
- `tentackle-mcp-maven-plugin`: an MCP server that provides the model to coding agents. It is
  included in the archetype. Added documentation for the AI tooling.
- `tentackle-pdo-mock`: mock PDOs for unit tests without a database. The persistence wurblets
  gained better mocking support.
- Batch modes for `tentackle-wizard:browse` (generates Java code from the command line) and
  `tentackle-wizard:operation`.
- New `tentackle-wizard:script` goal with a script runner. Fragments can be saved and loaded in
  interactive mode.
- `@PreloadCache`, `@PreloadRemoteDelegate`, and a query DSL for projection SQL queries (`QueryDSL.wrbl`).
- `--oneshot` option for the `PdoSelectUnique`/`DbSelectUnique` and select-list wurblets.
  `WurbletArguments` can be optional.
- Expandable `FxTextArea` (IntelliJ-like UX), plus an autofilter for `FxTableView` and `FxTreeTableView`.
- The PDO browser can copy SELECTs that reference a PDO to the clipboard.
- TCP socket factories with enhanced SSL support, which replace the JDK's factories. `tentackle-quic`
  uses `SSLParameters`. Connect timeouts were added to QUIC, `tentackle-web` and `tentackle-update`,
  and QUIC timeout, keep-alive and stream-window parameters.
- TRIP: support for more JDK collections and maps (including `PriorityQueue` and `Properties`),
  `@Canonical` in maps and collections, and a version fallback for outdated clients.
- Derived service URI expansion (`NumericEvaluator`).
- Oracle: function-based indexes (to emulate filtered indexes).
- `tentackle-update`: downloads larger than 2 GB.
- Unit tests for most modules.

#### Changed

- The tests use H2 instead of PostgreSQL, so the whole reactor builds without a database. The
  `postgres` profile runs only the PostgreSQL-specific tests. The tests in `tentackle-test-fx-rdc` are run as well.
- Binding ordinals are generated by default, in the order of the model.
- The archetype uses PKCS12 instead of JKS.
- `DSL.wrbl` renamed to `QueryDSL.wrbl`.
- Script wizard: the import marker changed from `@` to `^`.

#### Fixed

- TRIP stream hardening: remote invocation is limited to remote interfaces, and component ordinals
  are bounds-checked. A missing canonical component now gets its default value instead of `null`.
- QUIC: hardened several timing and concurrency issues. KWIK streams got interrupt-agnostic wrappers.
- Concurrency fixes in `DefaultTaskDispatcher`, `DaemonSupervisor`, `DefaultLockManager` (possible
  deadlock), `PdoCache`, `DbModificationTracker`, `MultiUserDbPool`, `DbPool`, and in the
  connection managers (possible deadlocks).
- A prepared statement leak with `alwaysPrepare`, transaction writability, and `ScriptRunner`
  rolling back safely when canceled.
- `tentackle-i18n`: the property fallback and the parent chain resolution.
- `tentackle-model`: several fixes (cyclic inheritance check, NPEs, escape handling).
- PostgreSQL binary columns now use `setBinaryStream`. Fixes for MySQL and MariaDB, and for Oracle column defaults.
- `BMoney` scale alignment, `PdoStringTranslator` binding order of multi-member UDKs, and the
  rendering of the macOS system menu icons in dark and light mode.

### [25.18.2.1] – 2026-07-20

#### Added

- Checks for stale client transports.
- Extensive Markdown documentation (cursors, tables, services, auto-completion, number sources,
  modification tracking and more). Unit tests for many core packages.

#### Changed

- Switched to KWIK 0.11, which is JPMS compatible again.
- Only the required checksums are published to Maven Central.

#### Fixed

- `equals`/`hashCode` of `AbstractBinary`. Added missing TRIP types.

### [25.18.2.0] – 2026-07-09

#### Added

- Tabbed mode for the PDO browser.
- `BIND_FILLER` option and `StripMode`.
- `Session#clearProperties`, which is invoked when a session is closed or returned to the pool.
  `Session#setProperty` with a `null` value removes the property.
- `StringHelper#contains` and `#containsIgnoreCase`.
- The PDO wizard's batch mode accepts `!BIND` defaults and a `batchDir` property for CLI tooling.
- The new documentation site (MkDocs), with quickstart, table of contents, index, and documents
  for PDOs, FX, validation, binding, TRIP and more.

#### Changed

- The `Validateable` interface requirement was relaxed.
- The maven site plugin and its configuration were migrated to the latest version.

### [25.18.1.0] – 2026-05-20

#### Added

- Batch mode for the PDO wizard. The PDO wizard also got a cache flag.
- `tentackle-model`: compact print mode, and support for more than one model (for example, when
  migrating projects).
- macOS support in the archetype, and `MacUtilities`.
- `tentackle-osgi`: bundles and activators for `tentackle-fx-atlanta` and `tentackle-fx-rdc-poi`.
- DTO wurblet: `builderScope` option. Relation wurblets support arbitrary method invocations.
- Documentation for the model and for migrating an application from RMI to TRIP.

#### Changed

- AtlantaFX default theme: Cupertino on macOS, Primer on all other platforms. Dark theme colors adjusted.
- `TrackType.NONE` renamed to `UNTRACKED`.
- The ikonli modules are transitive dependencies of `tentackle-fx`.

#### Fixed

- `PdoDeleteBy` wurblet always sets the domain context.
- Recursion check in `EntityImpl#getUniqueDomainKeyImpl`.

### [25.18.0.0] – 2026-03-07

#### Added

- **TRIP (Tentackle Remote Invocation Protocol)**, the new remoting protocol that replaces RMI. It
  includes the compressed QUIC transport `tripcq`, `ObjectSerializer` for `TBinary`, error opcodes,
  `ClientTransportCloseRequest`, `RemoteRoot` and `RemoteDbSessionInvocationFilter`.
- AtlantaFX theme support (`ThemeUtilities`), used by the archetype and the wizards. AtlantaFX
  controls: mask and password text fields, `Calendar`, and the new `FxCard`, `FxTile` and `FxInputGroup`.
- `LoginController` interface, which makes it easier to replace the login view, and a button that
  shows the password as a temporary sticky note.
- `FxControllerService#cssDarkAware`.
- jlink plugin: `jlinkOptions`, `jpackageOptions` and `failOnAutomaticRequired`.
- `Logger.log(Logger.Level, String, Object...)`.

#### Changed

- `UpdateInfo` and `ClientInfo` are now records. The update service uses the `tripe` protocol scheme,
  so `UpdateInfo` no longer needs encrypted fields.
- `PersistentObject#transferTokenLock` no longer updates the serial or table serial, which prevents
  cache pollution.
- The login window is always centered on the screen.
- Binding to `0` binds to all interfaces, IPv4 and IPv6.

#### Removed

- Support for `PasswordTextField`, which is incompatible with Tentackle's binding.

### [25.17.0.0] – 2026-01-21

New Java baseline: **JDK 25**.

#### Added

- `ScriptRunner` enhancements: timeout, max rows, cancel, read-only and transactional mode,
  result set listeners and SQL types in `ScriptRunnerResult`.
- `Session#preCommit` and `Db#transactionProperties`. Post-commits may run in their own transaction.
- JDBC metadata and migration support for indexes with comments.
- DTO wurblet: builders and non-builders with withers and mutable attributes.
- `SessionsViewTimezoneCellType`, `LocaleProvider#getCurrency`, `Freezable#isFrozen` and
  `FxLabel#setContextMenuEnabled`.
- `component` as a synonym in the model.
- Profile for publishing to Maven Central via the new publishing portal.

#### Changed

- Network round trips are optimized for `@Transaction` and `@NoTransaction` in conjunction with `@RemoteMethod`.
- `selectTokenLocked` starts a transaction if none is running. `updateTokenLock` checks the serial,
  to detect updates in the meantime.
- The cache was refactored, and the configuration of PDO references is delayed until it is needed.
- `DefaultBundleFactory` supports non-modular bundles if controls are provided.

#### Fixed

- `selectForUpdate` for single-table inheritance.
- The pending count in `ModificationTally` after rolled-back transactions.
- Actions invoked twice. The title of `DefaultNotificationBuilder`.

#### Removed

- `CommitTxRunnable` (use `preCommit` instead) and `RollbackTxRunnable`.

## Java 21 line (21.x)

### [21.16.4.0] – 2026-06-10

Maintenance release that backports features of 25.17.0.0.

#### Added

- `Session#preCommit` and `Db#transactionProperties`. Post-commits may run in their own transaction.
- JDBC metadata and migration support for indexes with comments.
- DTO wurblet: builders and non-builders with withers and mutable attributes.
- `SessionsViewTimezoneCellType`, `LocaleProvider#getCurrency`, and `component` as a synonym in the model.
- jlink plugin: `failOnAutomaticRequired`.

#### Fixed

- `selectForUpdate` for single-table inheritance, and the read-only mode of transaction writability.
- The pending count in `ModificationTally` after rolled-back transactions.
- Actions invoked twice. The title of `DefaultNotificationBuilder`. `update.ftl` on macOS.

### [21.16.3.0] – 2025-06-16

#### Added

- Class mapper support for arrays. `FxLabel#setContextMenuEnabled`, and a copy menu for notification texts.

#### Fixed

- A race condition in `DbModificationTracker` in remote clients during startup. The tracker now
  runs with maximum priority.
- PDO wizard: composite object relations that are `null`.
- `DefaultSecurityFactory#getPermission` also accepts classes (with a warning).

### [21.16.2.0] – 2025-02-12

#### Added

- `StatementTracer`, which automatically traces parallel open result sets on a session.
- `PropertiesResource` and `PropertiesUtilities` for application-specific strategies to load properties.
- DTO wurblet: `--builderPrefix`, and options to annotate the builder class and its methods.
- PDO browser: selective refresh of a PDO. `PdoCrud` asks before deleting.

#### Changed

- JavaFX 23. `RdcRuntimeException` extends `FxRuntimeException`.
- Fewer round trips for `DbModificationTracker` in remote clients during startup.

#### Fixed

- `DbTokenLock`: applications can use different ID sources in parallel. An NPE in `DefaultLockManager` during session cleanup.
- `FxTreeTableView#copyToClipboard`.

### [21.16.1.0] – 2024-07-24

#### Added

- Partial (filtered) joins. The select wurblets have the options `--immutable`, `--finallyimmutable` and `--tokenlock`.
- `@PostCommit` annotation.
- Partial and optional indexes (optional indexes are useful for in-memory test databases).
- Transaction statistics count statements and rollbacks. `ManagedConnection#maxStatementsPerTransaction`.
- `MasterSerialListEvent` and `MasterSerialEvent#isOverridingOlderEvents` for better event delivery.
- `PdoFinder#validateForm`, `NamedValue`, and array support in `FxPropertyMapping`.
- DTO wurblet: optional access scope for getters, setters and withers.
- `Backend#getMaxNameLength` with enhanced checks for foreign key names.

#### Fixed

- Filters in chained joins. A possible race in `DefaultDomainContext`.
- `FxDatePickerDelegate`: changing the date and time at the same time no longer resets the time to midnight.
- `JSR223Script` compiles thread-safe if thread-safe execution is required (necessary for JRuby).

#### Deprecated

- `Pdo#createPdoCache` (for removal).

### [21.16.0.0] – 2024-04-26

#### Added

- `CsvConverter`. The table, tree and list exports were refactored.
- `PersistentObject#selectTokenLocked` and `#reloadTokenLocked`.
- `PdoCacheFactory#createCacheIndex`.
- The RMI files are generated from templates, and `NamingRules` were added.

#### Fixed

- `FxDatePickerDelegate` regressions caused by changes in JavaFX 18.

#### Removed

- Old synchronized formatting methods that were deprecated.

### [21.15.3.0] – 2024-03-28

#### Added

- **Master serial events**: application-defined events that the server pushes to remote clients along
  with the master serial they poll.
- Dark mode support based on the platform preferences. The scenes and the wizard follow changes to
  the desktop setting automatically. The archetype supports dark mode.
- `RdcUtilities#invalidateCaches` and an option to disable the CRUD and search caches.

#### Fixed

- `DefaultLockManager` turns a tainted lock untainted when it is reused.

### [21.15.2.0] – 2024-01-17

#### Added

- `ManagedConnectionMonitor` and `BackendInfo#backendKeepAliveEnabled`.
- PDO browser: load PDOs explicitly by their IDs, and the sorting is remembered.
- Default service classes as a fallback in case service lookup fails.
- `DefaultLockManager#isTokenLockToKeep` and `EncryptedProperties#getPropertyIgnoreCase`.

#### Changed

- The test artifacts are automatic modules.
- Logging for dead database connections is less noisy. Statements are not logged when a rollback
  is caused by a temporary (lock) exception.

#### Fixed

- Token locks that are missing in the database are handled better.
- Finished transactions are removed from idle supervision earlier.

#### Removed

- The shutdown hooks for statistics.

### [21.15.1.0] – 2023-11-27

#### Added

- Auto-completion for text fields.
- "NoFXML" feature: controllers without FXML files.
- `MpxConnectionManager#connectionInactivityTimeout`, for when the idle time cannot be deactivated
  (for example, with cloud services). `Backend#getDummySelect`.
- PDO browser: generate code from the details table, a context menu, non-composite relations in the details table.
- The SQL plugin migrates models loaded from the classpath.
- `FxUtilities#createNotificationPopup`. The update after a failed login can start automatically.

#### Fixed

- A regression in `ForeignReference#compareTo` since embedded entities were supported.
- Double quotes are escaped properly in Java string literals.
- Oracle-style exception chains in `DbSessionUtilities#alignExceptionCause`.

### [21.15.0.0] – 2023-10-02

New Java baseline: **JDK 21** (via Java 18, 19 and 20).

#### Added

- `BundleMonkeyHelper`. `FxUtilities#show(Stage)`. `JLinkResolver#filteredArtifacts`.

#### Changed

- Improved alias name generation in the model. `MigrationUtilities` was refactored to `ModelUtilities`.
- Code that was deprecated since Java 19 was updated.

## Java 17 line (17.x)

### [17.14.0.6] – 2026-06-18

#### Changed

- `Session#setProperty` with a `null` value removes the property.
- DTO wurblet updated to the capabilities of the current line. `--nofinal` was added again.
- jlink plugin: the zip creation moved from `AbstractJLinkMojo` to `DefaultArtifactCreator`.

### [17.14.0.5] – 2026-03-25

#### Changed

- `TrackType.NONE` renamed to `UNTRACKED`.

#### Fixed

- `update.ftl` on macOS.

### [17.14.0.4] – 2026-01-18

Includes the unreleased 17.14.0.3.

#### Added

- DTO wurblet: optional access scope for getters, setters and withers, and an option to annotate the builder class.
- Array support in `FxPropertyMapping`.
- Integrity checks consider method arguments.

#### Fixed

- A possible race in `DefaultDomainContext`. `DbModelWurblet` with `IN`/`NOT IN` arrays.
- `JSR223Script` compiles thread-safe if thread-safe execution is required (JRuby).
- `ServiceFactory` rethrows the exception if there is no default class.

### [17.14.0.2] – 2024-01-17

#### Added

- `EncryptedProperties` (backported from `develop`).

#### Fixed

- Finished transactions are removed from idle supervision earlier. Statements are not logged when
  a rollback is caused by a temporary (lock) exception.
- `JLinkResolver`: required automatic modules that are explicitly moved to the classpath.

#### Removed

- The shutdown hooks for statistics.

### [17.14.0.1] – 2023-11-27

#### Fixed

- A regression in `ForeignReference#compareTo` since embedded entities were supported.
- Double quotes are escaped properly in Java string literals. Oracle-style exception chains.
- Windows: `MODEL-INDEX.LIST` in the SQL plugin.

### [17.14.0.0] – 2023-09-17

#### Added

- Notification popups (`NotificationBuilder`) that replace the JavaFX `Alert` dialogs.
- A simple dark mode theme for JavaFX.
- `DbTransactionFactory` for transaction statistics and monitoring.
- PDO browser: generates SQL SELECT statements, row details table, shows hidden components, better N:M display.
- Drag and drop of multiple PDOs. `PdoCrud`: Shift+New creates a copy of the current PDO.
- Embedded relations: columns in indexes, sorting in joined selects, attribute paths in `link=`, and `EXISTS` clauses.
- Multi-column data types in indexes. Access scope for N:M methods.
- `MultiUserDbPool#maxTotalSize`, `BackendInfoFactory`, and a "cloned" column in the `SessionsView`.

#### Fixed

- Several race conditions when windows are hidden. A race in `SessionKeepAliveTask`. Missing synchronization in `DefaultNumberSource`.
- Transaction cleanup is more robust.

### [17.13.0.0] – 2023-05-22

#### Added

- **Embedded entities**, including nested embedded entities and `[NORMTEXT]`. The wizard templates support them.
- `CustomMigrationValidator`, `Backend#isDeprecated` and `BackendFactory#getBackends`.
- Model checks for relations and for attribute and relation names.
- `EncryptedProperties`. `I18NText` implements `CharSequence` and `Comparable`.
- Case-sensitive camel-case navigation in `PrefixSelectionFeature`.
- `AbstractPdoTest` enables statement logging automatically when a test fails.

#### Changed

- Building Tentackle requires Maven 3.9.0. Applications still work with Maven 3.8.5.
- `StringNormalizer` is a `@Service`. Migration results are records.
- JavaFX 20.

#### Removed

- `StringHelper#compare` and `#compareIgnoreCase`.

### [17.12.0.0] – 2022-12-12

#### Added

- `I18NText` data type, with JavaFX support.
- The auto-update passes the encrypted credentials to the restarted application.
  `DesktopApplication#isAutoLogin`.
- `MultiUserSessionPool#close` closes all sessions of a user.
- Data types can have a backend-specific number of columns.
- `LocaleProvider#getFallbackLocale`, `PooledDb#getUsedSince` and `RemoteSession#log()` with an explicit logger name.

#### Changed

- SLF4J 2.x (now modular). The archetype switched from SLF4J to Log4j 2.
- JavaFX 19. Requires the Maven API 3.8.5 or newer.

### [17.11.0.0] – 2022-09-11

#### Added

- `SessionPooledExecutor` and `RdcUtilitiesWithBackgroundPool` for running background tasks in pooled sessions.
- `Application` interface, extracted from the application classes. `Application#getUser` returns an `Identifiable`.
- `VolatileHolder`.

#### Changed

- JavaFX 18.

#### Removed

- Support for Ingres.

### [17.10.0.0] – 2022-05-24

#### Added

- `tentackle-wizard:browse`, the PDO browser, which also generates code (`JavaCodeFactory`).
- `ScriptRunner`, the `run` goal of the SQL plugin and the `createSchemas` parameter.
- In-memory database testing support (`AbstractPdoTest`). JUnit 5 support in the test modules.
- java.time data types `InstantType`, `OffsetTimeType`, `OffsetDateTimeType` and `ZonedDateTimeType`,
  `SmartDateTimeParser` with filters and shortcuts, and `TemporalStringTranslator`.
- `UUIDType` (with native PostgreSQL support) and `XUUID`. Application-specific data types may use backend-specific SQL types.
- Oracle 12+ backend, database version check, and creating sequences.
- Multi-table `PdoCache` with `maxSizeHardLimit`.
- Sub contexts as part of the framework, interceptor service discovery, and the `WithinContext` and `NotWithinContext` interceptors.
- `Session#postCommit`, and a `running` parameter for `@Transaction`.
- Load the model by URL, from a jar or from the class/module path.
- `GraphicProvider` (replaces `IconProvider`), using icons from the ikonli Material Design pack.
- Statement statistics with fetch duration and number of rows. `ResultSetWrapper#warnOpenMillis`.

#### Changed

- Formatting uses `java.time` instead of `java.util` formatting (faster).
- `Duration` renamed to `TimeKeeper`.
- `DefaultSecurityManager` refactored for better concurrency.
- Queries for single-table inheritance are optimized.

#### Fixed

- Deadlock detection and timeouts in `DefaultTaskDispatcher`.
- Log4j 2.17.0 (Log4Shell).

### [17.9.0.0] – 2021-11-19

#### Added

- `@Fail` and `@Validate` annotations. `ValidationContextFactory` (`ValidationContext` is now an interface).
- `Transaction#rollbackSilently`.
- Records in `DbUtilities#resultSetToList`. The domain methods and unique domain key wurblets generate records.
- Multi-release automatic modules in `JLinkResolver`.

#### Changed

- `LockException` is temporary by default, so it allows transaction retries.
- Java 17 language migrations. More lint checks and `@Serial`.

### [17.8.0.0] – 2021-10-06

New Java baseline: **JDK 17** (via Java 12–15 on `develop`).

#### Added

- Transaction isolation levels and retryable transactions (`DefaultTransactionRetryPolicy`).
  Optimistic locking errors are treated as temporary.
- Records support: `@RecordDTO` and the DTO wurblet generating records. Records can be bound.
- Bundle checks.

#### Changed

- `tentackle-log-log4j2v` is a JPMS module. `PersistenceException` no longer requires `java.sql` in `tentackle-session`.
- JavaFX 17, Apache POI 5.

#### Removed

- `tentackle-log-log4j`: support for Log4j 1.x (CVE-2019-17571, CVE-2020-9488).

## Java 11 line (11.x)

### [11.7.4.1] – 2022-05-24

#### Added

- Table binding supports abstract types and generics with wildcards. `PdoUtilities#getClassNames`.
- Support for encrypted fake URLs.

#### Changed

- Locking operations in `AbstractPersistentObjectRemoteDelegate` were optimized. `PdoCrud` shows the lock dialog after opening.
- jlink plugin skips `requires static` modules.
- Log4j 2.16.0 (Log4Shell), JavaFX 17.0.1.

#### Fixed

- Multithreading fixes in `PersistentObjectClassVariables`. `DaemonSupervisor` when the number of daemons decreases.
- `null` values of `BMoney`/`DMoney` in `ResultSetWrapper`. Copying to the clipboard with invisible columns.

### [11.7.4.0] – 2021-10-06

#### Added

- Transaction isolation levels and retryable transactions (`DefaultTransactionRetryPolicy`).
  Optimistic locking errors are treated as temporary.
- `FxUtilities#run`, `#browse` and `#edit`, and improved online help.
- DTO wurblet `--with` option. Withers are treated like from-methods in the binding.

#### Changed

- `tentackle-log-log4j2v` is a JPMS module. `PersistenceException` no longer requires `java.sql` in `tentackle-session`.
- Apache POI 5.0.0.

### [11.7.3.0] – 2021-07-27

#### Added

- JDBC batching and PDO-level auto batching.
- Multi-user session pooling, and remote proxy RMI servers.
- `Db#createTransaction`. The model is part of the `tentackle-test-pdo` artifact.
- Copy-to-clipboard separators for `FxListView` and `FxTableView`. `FxTextArea#navigateViaEnterEnabled`.

#### Changed

- `tentackle-jlink-maven-plugin` also handles `jpackage` (reflected in its name).
- The modification type changed from `char` to `ModificationType`.

### [11.7.2.1] – 2021-06-06

#### Added

- More bundle checks. Security validation moved to `SecurityValidator`.

#### Changed

- DTO wurblet: `createBuilder` renamed to `toBuilder`.

#### Removed

- The dependency on `org.reflections`.

### [11.7.2.0] – 2021-05-15

#### Added

- i18n maven plugin: `pull`, `verify` and `cleanup` goals.
- `TreeTableView` support with PDO translators for `TreeView` and `TreeTableView`.
- JDBC array operators `IN`, `NOT IN`, `ALL` and `ANY` in `WHERE` clauses.
- Persisted preferences wrapper for `java.util.prefs`.
- Ctrl+Y restores the last view value (toggles with Ctrl+Z).
- `AbstractValidateableFxController`. `InteractiveError` moved from `tentackle-fx-rdc` to `tentackle-fx`.
- DTO wurblet: `--validate`, `--names` and `--nott` (generates code without Tentackle dependencies).
- `PdoSearch` can add a search to an existing list (logical OR).
- Better support for two-tier RDC applications with `BackendConfiguration`.
- `UpdatableDesktopApplication#assertNotAlreadyRunning`.

#### Fixed

- Joins in sublevels ≥ 2 and nested join paths. Dead connections are handled better.
- The security editor and `SecurityRulesView`. Users can change their own password without edit permission.

### [11.7.1.0] – 2020-12-23

#### Added

- Generic conversion of result sets to DTOs, based on `DataType`.
- DTO wurblet: generates `equals`, `hashCode` and from-methods. The binding supports from-methods
  for properties of immutable DTOs.
- `PropertyMapping` and `PropertyMap`. `FxFactory#createScene` and `#createAlert` apply the Tentackle stylesheet.
- Localized default validator messages.
- Column migration uses the Levenshtein distance.

#### Changed

- No more `--add-exports` for JavaFX. `AbstractBuilder` no longer depends on non-public API.
- JavaFX 15.0.1.

### [11.7.0.0] – 2020-10-17

#### Added

- **Pluggable, application-specific data types.**
- Scripts compile in the background in `ServerApplication` and `DesktopApplication`.
- `ClassMappedListener`, `ShortLongTextCellFactory`, and splash screen support in `LoginApplication`.
- `ModificationTracker#transaction` and `#submit`. jlink plugin: `modulePathOnly`.
- `ViewPermission` checks in `PdoSearch` and combo boxes.

#### Changed

- The validator and scripting APIs were refactored.
- Caching on remote clients follows the same semantics as on the server.
- JavaFX 15.

### [11.6.2.0] – 2020-08-30

#### Added

- jlink plugin: `jpackage` goal (`JPackageMojo`), including updatable per-user installations and templates in the archetype.
- SQL plugin: migration hints to rename tables and columns, alien tables, and checks for reserved schema names.
- Variable substitution with system variables in `backend.properties`. `StringHelper#evaluate`
  supports nested variables and `@{...}`.
- An inactivity timeout closes orphaned login windows.
- `ObjectUtilities` with type conversions, extracted from `CompoundValue`.

#### Changed

- `ToolFinder` and `ToolRunner` moved to `tentackle-common`. Toolchain support in the maven plugins was improved.

#### Fixed

- Migration of a `NOT NULL` column with a default value.

### [11.6.1.0] – 2020-06-30

#### Added

- `TableConfigurationProvider`, and copying table cells to the clipboard.
- Drag and drop for `PdoComponentAddon`. Camel-case keyboard navigation in combo boxes.
- Hidden and setter-only annotations for attributes and relations in the model.
- A default uncaught exception handler in `AbstractApplication`.
- `ValueTranslator#bindingPropertiesUpdated`, a `failedUI` consumer for `Rdc.bg`, and `PdoCache`
  `selectForCache` methods for application-specific indexes.

#### Changed

- Session grouping was improved. `PdoCrud` deletes within a transaction.
- JavaFX 14.0.1.

### [11.6.0.0] – 2020-01-11

#### Added

- `tentackle:versions` and `tentackle:plugin-versions` goals.
- Better support for testing with Mockito (`--mock` option of the persistence wurblets). The archetype includes a domain test example.
- `SessionPoolFactory` and `Session#registerCloseHandler`. Session pools support remoting with session groups.
- `FxControllerService#FXML_NONE`, and `@BindableType` to create table configurations for DTOs.
- DTO wurblet: `required` option in builder mode.

### [11.5.0.0] – 2019-10-25

#### Added

- `tentackle-script-jsr`: JSR 223 scripting.
- Model: custom stereotypes, the `[CACHED]` option, the `blunt` relation option, and `CustomModelValidator`.
- Function-based indexes.
- `DatabaseVersionLoader` for the `migrate` goal.
- jlink plugin: `ArtifactCreator` service interface, `<variables>`, `noArtifact`, `variablesPrecedence`, and a jdeps cache.
- Validation mapper loop detection.

#### Changed

- `PersistentObject` no longer exposes modifying methods (better encapsulation). `PdoHolder` renamed to `PdoProvider`.
- `persist`, `reload` and `reloadForUpdate` preserve transient data.
- Table alias generation for large models was improved.
- JavaFX 13.

### [11.4.0.1] – 2019-07-12

#### Added

- `PdoEditor#getValidationPath`. `RemoteSecurable` in the archetype.

#### Fixed

- Passwords are kept in clearable arrays instead of strings. Remote sessions are unexported immediately when closed.

### [11.4.0.0] – 2019-07-05

#### Added

- `tentackle-project-archetype`: the Maven archetype for new multi-tier projects.
- **Auto-update** for desktop clients (`tentackle-update`, `tentackle-fx-rdc-update`), with an update template in the jlink plugin.
- `Cryptor`.
- Model: `[PROVIDED]` entities, the `[MUTE]` column option, `NameVerifier` and case sensitivity.
- SQL plugin: `validate` goal, `useDropIfExists`, `generateHeaderComment`. Indexes are migrated with `RENAME INDEX` where possible.
- `SYSTEM_...` properties in `backend.properties`. `Pdo.listen`/`unlisten` and `Rdc.bg`.

#### Changed

- A default scripting language is set if exactly one is provided.
- RMI configuration for split SSL and plain setups.
- JavaFX 12.0.1, Log4j 2.12.0.

#### Removed

- `PooledTaskDispatcher`.

### [11.3.0.0] – 2019-03-30

#### Added

- `tentackle-jlink-maven-plugin`: self-contained runtime images (with an experimental `jpackage` goal).
- `tentackle-maven-support`: common maven plugin code moved into a new module.
- `PdoViewer`, `normtext` model option, `DIGITS` bind option, `Immutable#setFinallyImmutable`.
- SQL plugin: server IDs from `settings.xml`, and FreeMarker templates for migrations.
- Remote invocations with arguments provided by methods of the PDO.
- `ClasspathFirst` and handling of meta services. Default backend configuration.

#### Changed

- FX and FX-RDC no longer use `showAndWait` where possible.
- Copied PDOs are always mutable.
- Automatic module names for non-JPMS modules. JavaFX 12.

#### Removed

- `Db.commitImmediately`. JNLP detection (replaced by `AbstractApplication#isInteractive`).

#### Fixed

- Race conditions in `LoginApplication` and `RemoteDbSessionImpl`.

### [11.2.3.0] – 2019-01-07

New Java baseline: **JDK 11**. Released together with 8.2.2.0, which introduced the new version scheme.

#### Added

- `LockManager` supports transactions in flight. `SessionInfo#lockLinger`.
- PDO support for `RdcChoiceBox`. Methods that simplify application-specific addons for FX components.
- `Backend#isFilteredIndexSupported`, with checks in the model.

#### Changed

- The default timeout for token locks is now 1 hour.
- The modification log stores the user ID instead of the user name.
- SQL reconnect was refactored.

#### Fixed

- Long multiline messages in JavaFX alerts are displayed completely.

## Java 8 line (8.x)

The Java 8 line keeps the Swing modules (`tentackle-swing`, `tentackle-swing-rdc` and the
look-and-feel modules) next to JavaFX. It is maintained on the `java8` branch.

### [8.3.0.5] – 2026-03-25

#### Changed

- `TrackType.NONE` renamed to `UNTRACKED`.

### [8.3.0.4] – 2024-01-23

#### Added

- `EncryptedProperties`.

### [8.3.0.3] – 2023-05-23

#### Removed

- `StringHelper#normalize` and `#undiacrit` (use the `StringNormalizer` service).

### [8.3.0.2] – 2023-03-07

#### Changed

- `StringNormalizer` is a `@Service`.

### [8.3.0.1] – 2022-04-22

#### Added

- `ResultSetWrapper#getArray`.

#### Changed

- Log4j 1.x replaced by reload4j.
- Locking operations in `AbstractPersistentObjectRemoteDelegate` were optimized.

#### Fixed

- Multithreading fixes in `PersistentObjectClassVariables`. `DaemonSupervisor` when the number of daemons decreases.
- `null` values of `BMoney`/`DMoney` in `ResultSetWrapper`.

### [8.3.0.0] – 2020-12-14

#### Added

- Varargs methods in `CompoundValue`.

#### Changed

- Better matching in `MigrationUtilities`. Improved use of `volatile`.

### [8.2.8.2] – 2020-08-14

#### Added

- SQL plugin: drop table hint.

### [8.2.8.1] – 2020-07-31

#### Changed

- SQL plugin: better checks for tables in wrong schemas, and warnings in generated migrations.

### [8.2.8.0] – 2020-07-28

#### Added

- SQL plugin: migration hints to rename tables and columns, alien tables, and checks for reserved schema names.
- `ObjectUtilities` with type conversions, extracted from `CompoundValue`.

#### Fixed

- Migration of a `NOT NULL` column with a default value.

### [8.2.7.4] – 2020-06-25

#### Added

- Backports: the `[MUTE]` option, removal of global DTO options via `-@` or `!@`, `ValidationContext` constructors.

#### Changed

- `PdoCrud` deletes within a transaction.

### [8.2.7.3] – 2020-03-09

#### Added

- DTO wurblet: `required` and `transient` flags. `ResultSetWrapper#getObject(Class<T>, ...)`. `CustomModelValidator`.

#### Fixed

- Windows: CRLF parsing in global options, and the case-insensitive file system in the analyze mojos.

### [8.2.7.2] – 2019-10-15

#### Added

- Function-based indexes (not on Oracle).

#### Fixed

- Oracle: the `DEFAULT NULL` problem.

### [8.2.7.1] – 2019-09-03

#### Fixed

- `SemanticVersionFileFilter` is loaded by default.

### [8.2.7.0] – 2019-08-26

#### Added

- JSR 223 scripting and GraalVM Truffle scripting (contributed by Bernd Eilers).
- `DatabaseVersionLoader` for the `migrate` goal.
- Validation mapper loop detection.

#### Changed

- Faster construction of mixin delegates.

### [8.2.6.0] – 2019-07-21

#### Added

- `Cryptor`, and backports from 11.x: `BlockingHolder`, `Holder` and `InterruptedRuntimeException`.
- `PdoEditor#getValidationPath`. SQL plugin: `generateHeaderComment`.

### [8.2.5.0] – 2019-06-08

#### Added

- `NameVerifier` and case sensitivity in the model. Quoted table and column names for provided entities.
- SQL plugin: `validate` goal. Indexes are migrated with `RENAME INDEX` where possible.
- `SYSTEM_` system properties.

#### Fixed

- Oracle: migration of column defaults. PostgreSQL: index filters for time zone types.

### [8.2.4.0] – 2019-05-14

#### Added

- `[PROVIDED]` entity option, `readonly` option of the `MethodsImpl` wurblet, and `useDropIfExists`.

#### Changed

- The modification tracker avoids polling too often.

### [8.2.3.0] – 2019-03-30

#### Added

- `PdoViewer`. Remote invocations with arguments provided by methods of the PDO.
- DTO wurblet: global annotations, and builders for abstract classes.

#### Removed

- `Db.commitImmediately`.

#### Fixed

- A race condition in `LoginApplication`. `DefaultDbPool#putSession` for remote sessions.

### [8.2.2.0] – 2019-01-07

First release of the Java 8 maintenance line with the new version scheme. It continues 2.1.6.

#### Added

- PDO support for `RdcChoiceBox`. `PdoSearch#noDataMessage`.

#### Changed

- SQL reconnect was refactored.

#### Fixed

- Long multiline messages in JavaFX alerts are displayed completely.

## 2.x line

### [2.2.0] – 2018-12-11

The last release of the 2.x line, and the first that is modular: Java 9 → 10 → **Java 11**.

#### Added

- JPMS: every module has a `module-info.java`. `ModuleSorter` determines the module hierarchy,
  and modules can be loaded from the file system and the local Maven repository.
- The first working **PDO and operation wizards** (`tentackle-wizard-maven-plugin`, FreeMarker templates).
- DTO wurblet, including builders and `@Bindable` support.
- `LockManager` with improved token locking. `PersistentObject#isTokenLockableByMe`.
- `Freezable` interface, `Snapshotable#discardSnapshot(s)`, and PDO `copy()`.
- Log4j 2 provider (`tentackle-log-log4j2v`) and `Logger.get()`.
- `DynamicDriver` loads JDBC drivers at runtime via URL. `BackendConfiguration`/`DriverConfiguration` with a UI.
- CLOB columns (`String(0)` in the model). The bind option `LINES=`.
- RMI files are generated automatically for remoting-capable operations.
- Transaction lambdas with checked exceptions.
- `MigrateSqlMojo` `split` option. `bundleDir` in the SQL mojo.

#### Changed

- The Swing modules moved to a legacy subproject and are no longer part of the main line (they
  continue in the `java8` branch). The desktop client is JavaFX only.
- `finalize()` replaced by the `Cleaner` API. `TransactionEnvelope` replaced by `Supplier`.
- JavaFX 11.0.1, Apache POI 4.0, ASM 7.0, Maven 3.6.0.

#### Removed

- Nashorn: there is no default scripting language anymore.
- Clone support for PDOs and operations (`clonePdo`, `ObjectHelper`), and `ApplicationException`.
- The jgitflow plugin.

### [2.1.6] – 2018-12-11

Java 8 maintenance release.

#### Added

- `PdoSearch#doubleClickHandler`, and a context menu and background image for text fields with `PdoStringTranslator`.
- `@Bindable` in the DTO wurblet. `ModelUtilities` for migrating from Tentackle 1 to 2.
- `VersionFileFilter` of the SQL plugin is pluggable via `@Service`. `createsequence.sql`.

#### Fixed

- `transferTokenLock`: the returned PDO still held the old user ID.
- NormText search, `Binary#equals`/`hashCode`, a negation bug in `CompoundValue`, and `SCALE=` not applied to components.

### [2.1.5] – 2018-06-29

Java 8 maintenance release with backports from 2.2.

#### Added

- DTO wurblet (with the `--builder` option). `Freezable`, `ProxyDelegate` and `me()`.
- `DynamicDriver` loads JDBC drivers at runtime via URL.
- `AttributeOptions#initialValue`, `AbstractDbObject#alignComponents`, `TablePopup` preferences.
- Token locks for dynamic root entities.

#### Changed

- `TransactionEnvelope` replaced by `Provider`. The default maximum number of connections of
  `MpxConnectionManager` was reduced from 100 to 40.

#### Deprecated

- `selectLocked` and `reloadLocked`.

#### Fixed

- Synchronization in `DbPreferencesFactory`, and remote sync and notification bugs in `DbPreferences`.

### [2.1.4] – 2018-02-05

#### Added

- `DbModificationTracker#localClientMode`.

#### Changed

- Index lines are parsed when the whole model is known, so automatically added columns such as `rootid`
  don't have to be specified. The persistence wurblets use `rootid` if it is provided.

### [2.1.3] – 2018-02-02

#### Added

- `@ValidationScriptConverter`.

#### Fixed

- Selection code and `Relations.wrbl` for object relations of an abstract type.

### [2.1.2] – 2018-01-28

#### Added

- Support for `java.time.LocalDate`, `LocalTime` and `LocalDateTime`.
- `PrefixSelectionFeature` for combo and choice boxes. Automatic positioning of FX stages.
- Lenient mode for `FxTextComponent`.

#### Changed

- `ValueTranslator` is an interface, with `AbstractValueTranslator` as its base class.
- Synchronization in the connection managers was improved.

#### Fixed

- `FxDatePicker` with `Date`, `Time` and `Timestamp`. A PDO in a table view was not updated after it was modified in the tree view.

### [2.1.1] – 2017-12-21

#### Added

- Java 9 support via multi-release jars (FX and FX-RDC).
- `TablePrinter` (replaces `SimpleTablePrinter`) with varying row heights and cancelable print jobs.
- An auto-login option for FX-RDC. `Fx.runAndWait`.

#### Fixed

- `Db.con` is volatile, which avoids deadlocks via `DaemonSupervisor.kill`.
- Lazy validation in combination with conditions.

### [2.1.0] – 2017-11-29

#### Added

- `tentackle-i18n`: database-backed resource bundles (`StoredBundle`, `BundleFactory`), with the
  `tentackle:checkBundles` goal.
- `tentackle-bom`.
- `tentackle-test-pdo` and `tentackle-test-fx-rdc` replace the monolithic test module.
- Transaction savepoints.
- Components loaded outside the domain context of their root entity are made immutable.
- `@Secured` and `SecuredInterceptor`. Predefined `SecurityResults`.
- Totals for tables (`TotalsTableView`), with clipboard support.
- OSGi support: explicit class loaders for services, and `DynamicImport-Package`.
- Blunt methods for serialized object relations. `PersistentObject#isRootEntityOf`.
- Application-specific session properties. `PropertySupport`/`PropertyEvent`/`PropertyListener`.
- Migration support for Tentackle 1 models.

#### Changed

- The persistence wurblets use the new `WurbletArgument` family. `JoinedSelect` and `Join` use
  fluent-style generation. `Relation` `args=` generates the complete code.
- `StringHelper` moved to `tentackle-common`. Excel support moved to FX-RDC.
- `DefaultSecurityManager` has better concurrency.

#### Removed

- `SessionManager`/`SessionProvider` interfaces (an implementation detail of the persistence layer),
  `I18NTranslation`, the XML import and export of `PersistedPreferences`, and `EmailAddress`.

### [2.0.7] – 2016-03-31

#### Added

- SSL RMI registries. The RMI socket factories moved to `tentackle-core`.
- `Session.rollbackSilently`, `PersistentObject.differsPersisted()`, `SecurityResult.isDefault`.
- Version filter for migration hints. OWASP dependency check plugin.
- ID popup in `PdoCrud`.

#### Changed

- `StringHelper.md5sum` became a generic hash with a given algorithm.

#### Fixed

- `revertToSnapshot` cleared the ID and serial (regression). `DefaultDomainContext#hashCode`.
- Security checks and `SecurityRulesView`. Immutable contexts in read-only `PdoCache`s.

### [2.0.6] – 2016-02-26

#### Added

- `AdminExtension` and `SessionsView`. `SessionInfo` carries the time zone and VM, OS and host info.
- `EventListenerProxy`. Context handling in the security dialog.

#### Changed

- `MethodsImpl` generates method references instead of lambdas.

#### Fixed

- Rdc cache pollution, an NPE when a client requests a remote delegate of a closed session, and
  several fixes in the login dialog.

#### Removed

- The JNLP system dependency.

### [2.0.5] – 2016-02-10

#### Changed

- Wurbelizer 2.0.4.

### [2.0.4] – 2016-02-04

#### Fixed

- Issue #7. The parent POM was updated for OSSRH.

### [2.0.3] – 2016-01-29

#### Fixed

- Servers deployed in EE containers. FX tree item separators are triggered by a group change.

### [2.0.2] – 2016-01-24

#### Fixed

- Javadocs for OSSRH (#6), the login progress bar (#3), `TableNameAnalyzeHandler` "internal logic error" (#1), diagnostics (#5).

#### Changed

- Swing templates moved to the attic (#2).

### [2.0.1] – 2016-01-19

#### Fixed

- Windows-specific fixes.

### [2.0.0] – 2016-01-09

The first release in the public git history.
For what it already contained, see [The History of Tentackle](src/main/docs/history.md#origins-20012015).

[Unreleased]: https://github.com/krake-com/tentackle/compare/25.20.0.0...develop
[25.20.0.0]: https://github.com/krake-com/tentackle/releases/tag/25.20.0.0
[25.19.0.0]: https://github.com/krake-com/tentackle/releases/tag/25.19.0.0
[25.18.2.1]: https://github.com/krake-com/tentackle/releases/tag/25.18.2.1
[25.18.2.0]: https://github.com/krake-com/tentackle/releases/tag/25.18.2.0
[25.18.1.0]: https://github.com/krake-com/tentackle/releases/tag/25.18.1.0
[25.18.0.0]: https://github.com/krake-com/tentackle/releases/tag/25.18.0.0
[25.17.0.0]: https://github.com/krake-com/tentackle/releases/tag/25.17.0.0
[21.16.4.0]: https://github.com/krake-com/tentackle/releases/tag/21.16.4.0
[21.16.3.0]: https://github.com/krake-com/tentackle/releases/tag/21.16.3.0
[21.16.2.0]: https://github.com/krake-com/tentackle/releases/tag/21.16.2.0
[21.16.1.0]: https://github.com/krake-com/tentackle/releases/tag/21.16.1.0
[21.16.0.0]: https://github.com/krake-com/tentackle/releases/tag/21.16.0.0
[21.15.3.0]: https://github.com/krake-com/tentackle/releases/tag/21.15.3.0
[21.15.2.0]: https://github.com/krake-com/tentackle/releases/tag/21.15.2.0
[21.15.1.0]: https://github.com/krake-com/tentackle/releases/tag/21.15.1.0
[21.15.0.0]: https://github.com/krake-com/tentackle/releases/tag/21.15.0.0
[17.14.0.6]: https://github.com/krake-com/tentackle/releases/tag/17.14.0.6
[17.14.0.5]: https://github.com/krake-com/tentackle/releases/tag/17.14.0.5
[17.14.0.4]: https://github.com/krake-com/tentackle/releases/tag/17.14.0.4
[17.14.0.2]: https://github.com/krake-com/tentackle/releases/tag/17.14.0.2
[17.14.0.1]: https://github.com/krake-com/tentackle/releases/tag/17.14.0.1
[17.14.0.0]: https://github.com/krake-com/tentackle/releases/tag/17.14.0.0
[17.13.0.0]: https://github.com/krake-com/tentackle/releases/tag/17.13.0.0
[17.12.0.0]: https://github.com/krake-com/tentackle/releases/tag/17.12.0.0
[17.11.0.0]: https://github.com/krake-com/tentackle/releases/tag/17.11.0.0
[17.10.0.0]: https://github.com/krake-com/tentackle/releases/tag/17.10.0.0
[17.9.0.0]: https://github.com/krake-com/tentackle/releases/tag/17.9.0.0
[17.8.0.0]: https://github.com/krake-com/tentackle/releases/tag/17.8.0.0
[11.7.4.1]: https://github.com/krake-com/tentackle/releases/tag/11.7.4.1
[11.7.4.0]: https://github.com/krake-com/tentackle/releases/tag/11.7.4.0
[11.7.3.0]: https://github.com/krake-com/tentackle/releases/tag/11.7.3.0
[11.7.2.1]: https://github.com/krake-com/tentackle/releases/tag/11.7.2.1
[11.7.2.0]: https://github.com/krake-com/tentackle/releases/tag/11.7.2.0
[11.7.1.0]: https://github.com/krake-com/tentackle/releases/tag/11.7.1.0
[11.7.0.0]: https://github.com/krake-com/tentackle/releases/tag/11.7.0.0
[11.6.2.0]: https://github.com/krake-com/tentackle/releases/tag/11.6.2.0
[11.6.1.0]: https://github.com/krake-com/tentackle/releases/tag/11.6.1.0
[11.6.0.0]: https://github.com/krake-com/tentackle/releases/tag/11.6.0.0
[11.5.0.0]: https://github.com/krake-com/tentackle/releases/tag/11.5.0.0
[11.4.0.1]: https://github.com/krake-com/tentackle/releases/tag/11.4.0.1
[11.4.0.0]: https://github.com/krake-com/tentackle/releases/tag/11.4.0.0
[11.3.0.0]: https://github.com/krake-com/tentackle/releases/tag/11.3.0.0
[11.2.3.0]: https://github.com/krake-com/tentackle/releases/tag/11.2.3.0
[8.3.0.5]: https://github.com/krake-com/tentackle/releases/tag/8.3.0.5
[8.3.0.4]: https://github.com/krake-com/tentackle/releases/tag/8.3.0.4
[8.3.0.3]: https://github.com/krake-com/tentackle/releases/tag/8.3.0.3
[8.3.0.2]: https://github.com/krake-com/tentackle/releases/tag/8.3.0.2
[8.3.0.1]: https://github.com/krake-com/tentackle/releases/tag/8.3.0.1
[8.3.0.0]: https://github.com/krake-com/tentackle/releases/tag/8.3.0.0
[8.2.8.2]: https://github.com/krake-com/tentackle/releases/tag/8.2.8.2
[8.2.8.1]: https://github.com/krake-com/tentackle/releases/tag/8.2.8.1
[8.2.8.0]: https://github.com/krake-com/tentackle/releases/tag/8.2.8.0
[8.2.7.4]: https://github.com/krake-com/tentackle/releases/tag/8.2.7.4
[8.2.7.3]: https://github.com/krake-com/tentackle/releases/tag/8.2.7.3
[8.2.7.2]: https://github.com/krake-com/tentackle/releases/tag/8.2.7.2
[8.2.7.1]: https://github.com/krake-com/tentackle/releases/tag/8.2.7.1
[8.2.7.0]: https://github.com/krake-com/tentackle/releases/tag/8.2.7.0
[8.2.6.0]: https://github.com/krake-com/tentackle/releases/tag/8.2.6.0
[8.2.5.0]: https://github.com/krake-com/tentackle/releases/tag/8.2.5.0
[8.2.4.0]: https://github.com/krake-com/tentackle/releases/tag/8.2.4.0
[8.2.3.0]: https://github.com/krake-com/tentackle/releases/tag/8.2.3.0
[8.2.2.0]: https://github.com/krake-com/tentackle/releases/tag/8.2.2.0
[2.2.0]: https://github.com/krake-com/tentackle/releases/tag/2.2.0
[2.1.6]: https://github.com/krake-com/tentackle/releases/tag/2.1.6
[2.1.5]: https://github.com/krake-com/tentackle/releases/tag/2.1.5
[2.1.4]: https://github.com/krake-com/tentackle/releases/tag/2.1.4
[2.1.3]: https://github.com/krake-com/tentackle/releases/tag/2.1.3
[2.1.2]: https://github.com/krake-com/tentackle/releases/tag/2.1.2
[2.1.1]: https://github.com/krake-com/tentackle/releases/tag/2.1.1
[2.1.0]: https://github.com/krake-com/tentackle/releases/tag/2.1.0
[2.0.7]: https://github.com/krake-com/tentackle/releases/tag/2.0.7
[2.0.6]: https://github.com/krake-com/tentackle/releases/tag/2.0.6
[2.0.5]: https://github.com/krake-com/tentackle/releases/tag/2.0.5
[2.0.4]: https://github.com/krake-com/tentackle/releases/tag/2.0.4
[2.0.3]: https://github.com/krake-com/tentackle/releases/tag/2.0.3
[2.0.2]: https://github.com/krake-com/tentackle/releases/tag/2.0.2
[2.0.1]: https://github.com/krake-com/tentackle/releases/tag/2.0.1
[2.0.0]: https://github.com/krake-com/tentackle/releases/tag/2.0.0
