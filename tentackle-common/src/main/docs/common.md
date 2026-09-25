# Tentackle Common — The Foundation Module

## Overview and Motivation

The `tentackle-common` module is the **root of the entire Tentackle stack**. Its package-info sums it up in one
line — *"Common classes for build- and runtime"* — and that dual role is the key to understanding it. Unlike every
other module, `tentackle-common` is used in **two completely different contexts**:

1. **At runtime**, by every Tentackle application, as the lowest-level utility and SPI layer.
2. **At build time**, by the Tentackle Maven plugins and the Wurbelizer, which need the same annotations, naming
   rules, money/date types, and the service-discovery primitives while generating code and analyzing annotations.

Because it must work everywhere, `tentackle-common` is deliberately **dependency-free**: it requires only
`java.sql` and `java.compiler` from the JDK and nothing else from Tentackle. Everything else in the framework
depends, directly or transitively, on this module.

Its single most important responsibility is the **Service and Configuration API** — Tentackle's replacement for
`java.util.ServiceLoader` that powers dependency injection, component replacement, and build-time annotation
analysis throughout the framework. That subsystem is large enough to have its own document; this page gives the
whole-module picture and points to it.

> **See also:** [services.md](services.md) — the in-depth guide to the `@Service` / `@MappedService` annotations,
> the `ServiceFinder`/`ServiceFactory`, build-time `@Analyze` processing, the `ModuleHook` mechanism, and OSGi.

### Design principles

- **Zero framework dependencies.** Nothing in Tentackle sits below this module, so it cannot depend on anything in
  Tentackle. It leans only on the JDK.
- **Build-time *and* runtime.** Types here are shared by the running application and by the Maven plugins/wurblets
  that build it, which guarantees that, e.g., a `BMoney` or a `@ClassId` means the same thing in both worlds.
- **Annotation-driven, not reflection-at-startup.** Component discovery is based on annotations analyzed at build
  time and recorded under `META-INF`, avoiding classpath scanning and minimizing startup cost.
- **Classpath *and* module-path.** All mechanisms work identically in traditional classpath mode and in JPMS
  modular (including jlink'd) mode, via the `ModuleHook` indirection.

---

## Key Concepts

### Service and Configuration API

This is the heart of the module and is documented fully in [services.md](services.md). In brief:

- [`Service`](../java/org/tentackle/common/Service.java) / [`ServiceName`](../java/org/tentackle/common/ServiceName.java)
  / [`MappedService`](../java/org/tentackle/common/MappedService.java) — the annotations that register an
  implementation (a *simple* service in `META-INF/services`, or a *mapped* service in `META-INF/mapped-services`
  that relates a property → class → value).
- [`ServiceFinder`](../java/org/tentackle/common/ServiceFinder.java) /
  [`DefaultServiceFinder`](../java/org/tentackle/common/DefaultServiceFinder.java) /
  [`ServiceFinderKey`](../java/org/tentackle/common/ServiceFinderKey.java) — locate service configurations,
  preserving module-dependency order (the first URL belongs to the topmost module; the classpath comes last).
- [`ServiceFactory`](../java/org/tentackle/common/ServiceFactory.java) — creates services and finders; a richer
  replacement for `ServiceLoader` (any configuration type, per-service classloaders, modular plus non-modular). It is
  itself loadable via `ServiceLoader` and therefore replaceable.
- [`Analyze`](../java/org/tentackle/common/Analyze.java) / [`AnnotationProcessor`](../java/org/tentackle/common/AnnotationProcessor.java)
  — the *meta-annotation* that ties an annotation to a build-time `AnalyzeHandler` (named by string, so the
  application never depends on the handler at runtime).
- [`ModuleHook`](../java/org/tentackle/common/ModuleHook.java) / [`ModuleInfo`](../java/org/tentackle/common/ModuleInfo.java)
  / [`ModuleOrdinal`](../java/org/tentackle/common/ModuleOrdinal.java) / [`ModuleSorter`](../java/org/tentackle/common/ModuleSorter.java)
  — determine and topologically sort the module hierarchy (needed for resource bundles and for jlink'd images
  where module dependencies are otherwise not discoverable).
- [`ClasspathFirst`](../java/org/tentackle/common/ClasspathFirst.java) — override a module-path service from the
  classpath.
- [`RemoteMethod`](../java/org/tentackle/common/RemoteMethod.java) / [`RecordDTO`](../java/org/tentackle/common/RecordDTO.java)
  — annotations consumed by wurblets/analysis to make handwritten methods remoting-capable and to mark records
  for code generation. `@RecordDTO` drives the
  [DTO wurblet](../../../../tentackle-wurblets/src/main/docs/dto-wurblet.md#what-recorddto-actually-does).

### Database-aware data types

Tentackle ships its own value types whose defining feature is **stable database/serialization semantics across
timezones** and optional immutability. Most are usable directly as model attribute types (mapped by
[`tentackle-sql`](../../../../tentackle-sql/src/main/docs/sql.md) data types).

- [`BMoney`](../java/org/tentackle/common/BMoney.java) — a money value derived from `BigDecimal` with a fixed,
  unchangeable scale; stored as a `DOUBLE` value + `INTEGER` scale.
- [`DMoney`](../java/org/tentackle/common/DMoney.java) — like `BMoney`, but stored as a `DECIMAL` (scale 0) +
  `INTEGER` scale for exact decimal storage.
- [`Date`](../java/org/tentackle/common/Date.java) — a date with *database* semantics: serialized as `YYYYMMDD`
  so it stays the same calendar date in every timezone (unlike `java.sql.Date`, which serializes epochal time).
- [`Time`](../java/org/tentackle/common/Time.java) — a time with database semantics.
- [`Timestamp`](../java/org/tentackle/common/Timestamp.java) — a timestamp with an optional **UTC** flag (the
  model's `[UTC]` option) that keeps it fixed to a timezone by convention rather than converting on
  serialization.
- [`Binary`](../java/org/tentackle/common/Binary.java) — stores a serializable object as a blob-like byte array
  using Java Object Serialization, but via binary-stream methods so it stays valid *outside* a transaction
  (unlike real blobs).
- [`TBinary`](../java/org/tentackle/common/TBinary.java) — same as `Binary` but uses the pluggable
  [`ObjectSerializer`](../java/org/tentackle/common/ObjectSerializer.java) (by default TRIP's serializer), so the
  object need not implement `Serializable`. [`AbstractBinary`](../java/org/tentackle/common/AbstractBinary.java) is
  the shared base.
- [`I18NText`](../java/org/tentackle/common/I18NText.java) — a multilingual string usable
  as a model attribute (`org.tentackle.sql.datatypes.I18NTextType`); implements `CharSequence`/`Comparable` for
  the current locale.
- [`Freezable`](../java/org/tentackle/common/Freezable.java) — the interface behind the "frozen" (made-immutable)
  capability of the date/time and binary types. See [freezable.md](freezable.md) for why it exists and how it
  makes old-school mutable types like `java.util.Date` safe as entity attributes.

### Object serialization

[`ObjectSerializer`](../java/org/tentackle/common/ObjectSerializer.java) abstracts generic object
serialization; [`DefaultObjectSerializer`](../java/org/tentackle/common/DefaultObjectSerializer.java) is the
JOS-based default. This is the seam that lets `TBinary` (and other framework code) serialize via TRIP instead of
plain Java serialization when `tentackle-core` is present.

### Security and configuration

See [cryptor.md](cryptor.md) for the full picture, including the build-time encryption of secrets
into filtered resources and an honest account of what this protects against.

- [`Cryptor`](../java/org/tentackle/common/Cryptor.java) — a simple symmetric en/decryptor. An application
  provides its own `@Service(Cryptor.class)` subclass with a confidential salt/passphrase; it is used to encrypt
  passwords in memory, during client/server login, and in `backend.properties`.
- [`EncryptedProperties`](../java/org/tentackle/common/EncryptedProperties.java) — a `Properties` variant where
  values starting with `~` are decrypted via the `Cryptor` (with `\` escaping and optional case-insensitive keys).
  This is the configuration format used for backend connection settings.
- [`Settings`](../java/org/tentackle/common/Settings.java) — global framework settings.
- [`Constants`](../java/org/tentackle/common/Constants.java) — shared build- and runtime constants.
- [`Version`](../java/org/tentackle/common/Version.java) — Tentackle version information.

### Internationalization and resource bundles

Resource-bundle handling differs between classpath and modular mode, so the framework routes it through its own
factory:

- [`Bundle`](../java/org/tentackle/common/Bundle.java) — annotation marking a class that corresponds to a resource
  bundle.
- [`BundleFactory`](../java/org/tentackle/common/BundleFactory.java) /
  [`DefaultBundleFactory`](../java/org/tentackle/common/DefaultBundleFactory.java) /
  [`BundleSupport`](../java/org/tentackle/common/BundleSupport.java) — locate and create bundles via the module
  hooks.
- [`LocaleProvider`](../java/org/tentackle/common/LocaleProvider.java) — supplies the current `Locale`.
- [`I18NText`](#database-aware-data-types) — the multilingual text value type (see above).
- [`CommonCommonBundle`](../java/org/tentackle/common/CommonCommonBundle.java) — the module's own message bundle.

### Helper utilities

A set of `final` static-method helpers and small utilities used throughout build and runtime:

- [`StringHelper`](../java/org/tentackle/common/StringHelper.java) — string manipulation helpers.
- [`StringNormalizer`](../java/org/tentackle/common/StringNormalizer.java) /
  [`DefaultStringNormalizer`](../java/org/tentackle/common/DefaultStringNormalizer.java) — reduce strings to a
  restricted character set (e.g., for normtext search); works for most Western languages by default.
- [`DateHelper`](../java/org/tentackle/common/DateHelper.java) — date/time helpers around `java.util.Date`.
- [`FileHelper`](../java/org/tentackle/common/FileHelper.java) — file-handling helpers.
- [`ExceptionHelper`](../java/org/tentackle/common/ExceptionHelper.java) — exception helpers.
- [`Compare`](../java/org/tentackle/common/Compare.java) — null-safe `Comparable` comparisons.
- [`ParameterString`](../java/org/tentackle/common/ParameterString.java) — parses/builds `name='value', …`
  parameter strings (used in model options and elsewhere).
- [`NamingRules`](../java/org/tentackle/common/NamingRules.java) — the replaceable singleton behind
  all derived class names (domain/persistence/remote delegate types); see
  [naming-rules.md](naming-rules.md).
- [`StripMode`](../java/org/tentackle/common/StripMode.java) — enum for string-stripping behavior.
- [`Path`](../java/org/tentackle/common/Path.java) / [`PathFactory`](../java/org/tentackle/common/PathFactory.java)
  — a generic path abstraction and its factory.

### External tools

- [`ToolFinder`](../java/org/tentackle/common/ToolFinder.java) — locates external executables.
- [`ToolRunner`](../java/org/tentackle/common/ToolRunner.java) — runs external tools (used by build tooling, e.g.,
  jlink/jpackage style invocations).

### Exceptions

- [`TentackleRuntimeException`](../java/org/tentackle/common/TentackleRuntimeException.java) — the framework's base
  unchecked exception.
- [`InterruptedRuntimeException`](../java/org/tentackle/common/InterruptedRuntimeException.java) — wraps a checked
  `InterruptedException` and re-sets the thread's interrupted flag.

---

## Package Layout

| Package                        | Contents                                                                                                                                                                                                                                      |
|--------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `org.tentackle.common`         | Everything: the service/configuration API, the database-aware data types, object serialization, the `Cryptor`/`EncryptedProperties` security layer, i18n/bundle support, the helper utilities, external-tool support and the base exceptions. |
| `org.tentackle.common.service` | The [`Hook`](../java/org/tentackle/common/service/Hook.java) (`ModuleHook`) for this module.                                                                                                                                                  |

---

## How It Fits Together

`tentackle-common` underpins the whole framework in two directions:

```text
   Build time                              Runtime
   ──────────                              ───────
   tentackle-maven-plugin   ┐         ┌  every Tentackle module
   tentackle-sql-maven-…    ├─ use ──►│  (core, session, sql, database,
   Wurbelizer wurblets      ┘         │   pdo, domain, persistence, fx, …)
        │                             │
        └──────► tentackle-common ◄───┘
                 (annotations, ServiceFinder, money/date types,
                  naming rules, Cryptor, helpers — no deps)
```

- At **build time**, the Maven plugins read the `@Analyze`-meta-annotated annotations (`@Service`, `@ClassId`,
  `@TableName`, …) from source, run their `AnalyzeHandler`s, and emit configuration files under `META-INF`. The
  same money/date/naming types are available, so the generated code is consistent.
- At **runtime**, application code obtains implementations through `ServiceFactory.createService(...)` /
  `ServiceFactory.getServiceFinder()`, which read those `META-INF` files in module-dependency order — working in
  both classpath and modular mode thanks to the `ModuleHook`s every module provides.

A minimal application only needs a dependency on `tentackle-common` plus the `tentackle-maven-plugin` to use the
service API; higher-level utilities (e.g., the reflection-based class mappers) additionally require
`tentackle-core`.

---

## Module Dependencies

`tentackle-common` is the bottom of the stack:

- It **requires transitive** only `java.sql` and `java.compiler` from the JDK — and nothing from Tentackle.
- It **uses** `org.tentackle.common.ModuleHook` (to discover other modules' `META-INF` contributions) and
  `org.tentackle.common.ServiceFactory` (so an application can replace the factory itself).
- It **opens** `org.tentackle.common` to `org.tentackle.core` for TRIP serialization.
- It **provides** its own `ModuleHook` (`org.tentackle.common.service.Hook`).
- Every other Tentackle module depends on it, directly or transitively.

## Related Documentation

- [Services / ServiceFinder](services.md) — the in-depth guide to the service and configuration API summarized above.
- [Cryptor and EncryptedProperties](cryptor.md) — the in-depth guide to the secret-handling layer summarized above.
- [Freezable](freezable.md) — why the date/time and binary value types are freezable, and how that protects entity attributes from aliasing mutation.
- [Tentackle SQL](../../../../tentackle-sql/src/main/docs/sql.md) — maps the data types here (`BMoney`, `DMoney`, `Date`, `Timestamp`, `I18NText`, …) to database columns.
- [Session](../../../../tentackle-session/src/main/docs/session.md) — uses `EncryptedProperties`/`Cryptor` for connection configuration and credentials.
- [PDO](../../../../tentackle-pdo/src/main/docs/pdo.md) — the PDO pattern wired together by the service API.
