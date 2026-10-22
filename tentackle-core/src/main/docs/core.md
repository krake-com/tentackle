# Tentackle Core — The Runtime Foundation

## Overview and Motivation

The `tentackle-core` module is the **runtime foundation** of the framework. Sitting directly on top of
[`tentackle-common`](../../../../tentackle-common/src/main/docs/common.md) — its only Tentackle dependency — it
provides the cross-cutting runtime services that every higher layer (session, database, persistence, PDO, domain,
FX) builds upon: remoting, reflection-based proxying and interception, binding, validation, logging, scripting,
tasks, preferences, I/O and a large kit of general-purpose utilities.

Where `tentackle-common` is the *dependency-free* layer shared between build time and runtime, `tentackle-core` is
the first genuinely *runtime* layer. It is where the framework's defining mechanisms live:

- **TRIP** — the custom serialization/RPC protocol that replaces RMI and Java Object Serialization and makes PDOs
  travel transparently between JVMs.
- **Interceptable proxies** — the dynamic-proxy and interceptor machinery that lets a PDO combine its domain and
  persistence delegates and that implements `@Transaction`, `@Log` and friends.
- **Binding** and **Validation** — the model↔view binding engine and the annotation-driven, scope-aware
  validation framework.

Several of these subsystems are large enough to have their own dedicated documents; this page is the **map of the
whole module** and links to them where they exist.

> **Topic deep-dives in this folder:**
> [trip.md](trip.md) · [interceptors.md](interceptors.md) · [binding.md](binding.md) · [validation.md](validation.md)

### Design principles

- **Pluggable everything.** Almost every subsystem is exposed as an interface located through the
  [service API](../../../../tentackle-common/src/main/docs/services.md) (`Logger`, `ScriptingLanguage`,
  `ClassMapperFactory`, `PersistedPreferencesFactory`, the TRIP providers, …), so an application can replace any of
  them by registering its own `@Service`.
- **Annotation-driven behavior.** Cross-cutting concerns (interception, logging, validation, TRIP type handling)
  are attached declaratively via annotations and woven in through proxies and build-time annotation processors,
  not handwritten boilerplate.
- **Modular and classpath parity.** Like the rest of the framework, everything works identically under JPMS and on
  the classpath; the module even contains workarounds (the `uses` clauses for `Logger`/`ScriptingLanguage`) for
  non-modularized providers loaded from jimage files.
- **No higher-layer knowledge.** Core knows nothing about sessions, SQL or domain objects — those depend on *it*.

---

## Key Subsystems

### TRIP — remoting and serialization

Packages `org.tentackle.trip`, `trip.builder`, `trip.creators`, `trip.impl`, `trip.provider`, `trip.remote`,
`trip.transport`, `trip.types`.

[TRIP](trip.md) (Tentackle Remote Invocation Protocol) is the framework's own serialization and RPC stack — by far
the largest subsystem in the module (~200 files). It replaces RMI/JOS with a protocol designed for the PDO model:
objects and proxies are serialized via a pluggable type system, and methods can be invoked across JVMs (client↔
server and server↔server). The subdivision is:

- `trip` — the core API (`TripType`, `TripTypeBuilder`, the protocol entry points, `TripReferencable` and the
  serialization annotations).
- `trip.types` / `trip.builder` / `trip.creators` — the predefined `TripType`s and the builders/creators that
  produce them for concrete Java types.
- `trip.impl` / `trip.provider` — default implementations and component providers (including the default
  `ObjectSerializer` that `TBinary` uses).
- `trip.remote` — the remote-invocation protocol layer (transport-independent RPC).
- `trip.transport` — the concrete transports (TCP, SSL, PSK-encrypted, compressed, loopback) that carry the protocol.

See **[trip.md](trip.md)** for the full treatment.

### Reflection, proxies and interception

Package `org.tentackle.reflect` (plus the build-time `apt` processors).
See [reflect.md](reflect.md) for the full picture and [interceptors.md](interceptors.md) for the
interception model.

This package provides two intertwined capabilities:

1. **Class mapping** — [`ClassMapper`](../java/org/tentackle/reflect/ClassMapper.java) /
   [`ClassMapperFactory`](../java/org/tentackle/reflect/ClassMapperFactory.java) (and the `Default*` impls,
   [`ClassToServicesMapper`](../java/org/tentackle/reflect/ClassToServicesMapper.java),
   [`ClassMappedListener`](../java/org/tentackle/reflect/ClassMappedListener.java)) map names/interfaces to their
   implementation classes — the runtime side of the [service API](../../../../tentackle-common/src/main/docs/services.md)
   used to resolve PDO/domain/persistence implementations.
2. **Interceptable proxies** — [`Interceptable`](../java/org/tentackle/reflect/Interceptable.java),
   [`InterceptableFactory`](../java/org/tentackle/reflect/InterceptableFactory.java),
   [`InterceptableInvocationHandler`](../java/org/tentackle/reflect/InterceptableInvocationHandler.java),
   [`Interceptor`](../java/org/tentackle/reflect/Interceptor.java) and the
   [`@Interception`](../java/org/tentackle/reflect/Interception.java) meta-annotation form the dynamic-proxy engine
   that lets a PDO weave domain and persistence delegates together and apply cross-cutting interceptors around method
   calls. [`Mixin`](../java/org/tentackle/reflect/Mixin.java),
   [`PropertyMap`](../java/org/tentackle/reflect/PropertyMap.java),
   [`ReflectionHelper`](../java/org/tentackle/reflect/ReflectionHelper.java),
   [`ReflectiveVisitor`](../java/org/tentackle/reflect/ReflectiveVisitor.java) and the canonical-constructor
   annotations (`@CanonicalConstructor`, `@CanonicalField`, `@CanonicalGetter`, …) round it out.

See **[interceptors.md](interceptors.md)** for how interception is declared and applied.

### Binding

Package `org.tentackle.bind`.

The binding engine connects model properties to views (typically JavaFX), keeping them in sync without manual
listener wiring. It is the foundation the FX modules build their auto-binding on. See **[binding.md](binding.md)**.

### Validation

Packages `org.tentackle.validate`, `validate.scope`, `validate.validator`, `validate.severity`.

An annotation-driven, **scope-aware** validation framework: validators (`validate.validator` ships 19 predefined
annotations, such as `@NotNull`, `@Size`, `@Pattern` or `@Validate`) are attached to model fields/methods and run
within a [`ValidationScope`](../java/org/tentackle/validate/ValidationScope.java) (e.g., the default, mandatory,
changeable, interactive or persistence scope), producing results at graded severities. See **[validation.md](validation.md)**.

### Logging

Package `org.tentackle.log`.

A **pluggable logging facade**: [`Logger`](../java/org/tentackle/log/Logger.java) /
[`LoggerFactory`](../java/org/tentackle/log/LoggerFactory.java) abstract the backend, with
[`DefaultLogger`](../java/org/tentackle/log/DefaultLogger.java) on top of `java.util.logging` (the module `uses`
`Logger` so alternative providers can be supplied). Beyond plain logging it offers:

- [`MappedDiagnosticContext`](../java/org/tentackle/log/MappedDiagnosticContext.java) (MDC) for contextual logging.
- The [`@Log`](../java/org/tentackle/log/Log.java) annotation + [`LogInterceptor`](../java/org/tentackle/log/LogInterceptor.java)
  to log method calls declaratively (an example of the interception engine in use).
- [`MethodStatistics`](../java/org/tentackle/log/MethodStatistics.java) / [`StatisticsResult`](../java/org/tentackle/log/StatisticsResult.java)
  for collecting per-method execution counts and durations.

### Tasks and dispatchers

Package `org.tentackle.task`.

[`Task`](../java/org/tentackle/task/Task.java) and [`TaskDispatcher`](../java/org/tentackle/task/TaskDispatcher.java)
(with [`AbstractTask`](../java/org/tentackle/task/AbstractTask.java) /
[`DefaultTaskDispatcher`](../java/org/tentackle/task/DefaultTaskDispatcher.java)) provide **serialized** task
execution on a single worker thread. This is the base that `tentackle-session`'s `SessionTaskDispatcher`,
keep-alive daemon and modification tracker extend.

### Scripting

Package `org.tentackle.script`.

A **scripting abstraction layer**: [`ScriptingLanguage`](../java/org/tentackle/script/ScriptingLanguage.java) /
[`Script`](../java/org/tentackle/script/Script.java) / [`ScriptFactory`](../java/org/tentackle/script/ScriptFactory.java)
let the framework evaluate scripts (e.g., in validators or the model) independently of the concrete engine; the
module `uses Logger`/`ScriptingLanguage` so providers can be plugged in.

### I/O and networking

Package `org.tentackle.io`.

Building blocks used by the TRIP transports and elsewhere: compressing/decrypting streams
([`CompressedInputStream`](../java/org/tentackle/io/CompressedInputStream.java),
[`EncryptingOutputStream`](../java/org/tentackle/io/EncryptingOutputStream.java), …), packet-framed streams
([`PacketSizedInputStream`](../java/org/tentackle/io/PacketSizedInputStream.java)), notifying byte-array streams,
the [`SocketChannelDispatcher`](../java/org/tentackle/io/SocketChannelDispatcher.java) for accepting client
connections, and the reconnection machinery ([`Reconnector`](../java/org/tentackle/io/Reconnector.java),
[`ReconnectionPolicy`](../java/org/tentackle/io/ReconnectionPolicy.java)) that lets dropped connections recover.

See [io.md](io.md) for the details: the packet framing that makes compression and encryption work on a
communication link, and how reconnection policies are applied.

### Preferences

Package `org.tentackle.prefs`.

[`PersistedPreferences`](../java/org/tentackle/prefs/PersistedPreferences.java) /
[`PersistedPreferencesFactory`](../java/org/tentackle/prefs/PersistedPreferencesFactory.java) abstract the standard
`java.util.prefs` API so it can be backed by different stores (e.g., the database-backed implementation in
[`tentackle-database`](../../../../tentackle-database/src/main/docs/database.md)), with
[`CompositePreferences`](../java/org/tentackle/prefs/CompositePreferences.java) combining user and system nodes.

### Keyring

Package `org.tentackle.keyring`.

[`Keyring`](../java/org/tentackle/keyring/Keyring.java) keeps secrets on the local machine, such as the
tokens of a single sign-on. Core keeps them encrypted in the Java preferences, or only in memory. The opt-in
module [`tentackle-keyring`](../../../../tentackle-keyring/src/main/docs/platform-keyring.md) replaces
that with the credential store of the OS. See [keyring.md](keyring.md).

### Daemon supervision

Package `org.tentackle.daemon`.

[`DaemonSupervisor`](../java/org/tentackle/daemon/DaemonSupervisor.java) controls the life cycle of long-running
background threads ([`Supervisable`](../java/org/tentackle/daemon/Supervisable.java),
[`Killable`](../java/org/tentackle/daemon/Killable.java), [`Scavenger`](../java/org/tentackle/daemon/Scavenger.java),
[`Terminatable`](../java/org/tentackle/daemon/Terminatable.java)) — used for cleanup threads, keep-alive daemons
and the like.

### Miscellaneous utilities

Packages `org.tentackle.misc` and `org.tentackle.misc.time`. See [misc.md](misc.md) for the full
picture — including the contracts (`Modifiable`, `SerialNumbered`, `Snapshotable`, …) that
`PersistentObject` implements.

The framework's general-purpose toolkit (~70 files, including `misc.time`). Highlights:

- **Pooling** — [`AbstractPool`](../java/org/tentackle/misc/AbstractPool.java) / `Poolable` / `AbstractPoolSlot`,
  the base for the session pools.
- **Collections** — copy-on-write wrappers ([`CopyOnWriteList`](../java/org/tentackle/misc/CopyOnWriteList.java),
  `…Set`, `…Collection`), `TrackedList`, holders and tuples (`IdSerialTuple`, `IdSerialNameTuple`).
- **Functional helpers** — `Acceptor`/`Provider` (checked-exception-friendly `Consumer`/`Supplier`), `Holder`,
  `BlockingHolder`, `Convertible`.
- **Formatting & parsing** — [`FormatHelper`](../java/org/tentackle/misc/FormatHelper.java),
  [`CsvConverter`](../java/org/tentackle/misc/CsvConverter.java),
  [`CommandLine`](../java/org/tentackle/misc/CommandLine.java),
  [`DateTimeUtilities`](../java/org/tentackle/misc/DateTimeUtilities.java) and the **smart date/time parser** in
  `misc.time` ([`SmartDateTimeParser`](../java/org/tentackle/misc/time/SmartDateTimeParser.java) with its shortcuts
  and missing-field handlers for forgiving user input).
- **Concurrency** — [`ConcurrencyHelper`](../java/org/tentackle/misc/ConcurrencyHelper.java).
- **Diagnostics** — [`DiagnosticUtilities`](../java/org/tentackle/misc/DiagnosticUtilities.java) (extended by the
  database layer to enrich stack dumps), `Identifiable`, `Canonicalizer`.

### Annotation processors (build time)

Package `org.tentackle.apt` (and `apt.visitor`).

The build-time annotation processors that back core's annotations — chiefly the interception processors
([`InterceptionAnnotationProcessor`](../java/org/tentackle/apt/InterceptionAnnotationProcessor.java),
`AbstractInterceptorAnnotationProcessor`, …) and the service-annotation base
([`AbstractServiceAnnotationProcessor`](../java/org/tentackle/apt/AbstractServiceAnnotationProcessor.java)).
These run inside `javac` and **validate** annotated classes — that a service implementation is not
abstract, implements the required interface and provides the constructors the framework will invoke
reflectively — reporting errors and warnings. They generate nothing: the `META-INF` configuration is
emitted by the `@Analyze` handlers of the
[service & configuration API](../../../../tentackle-common/src/main/docs/services.md) instead, which is
also what registers the processors themselves.

See [Annotation Processors](annotation-processors.md) for the full picture, including the processors in
tentackle-pdo, tentackle-session, tentackle-fx and tentackle-fx-rdc.

---

## Package Layout

| Package(s)                                                                                         | Subsystem                                                            | Reference                          |
|----------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|------------------------------------|
| `org.tentackle.trip` (+ `builder`, `creators`, `impl`, `provider`, `remote`, `transport`, `types`) | TRIP remoting/serialization                                          | [trip.md](trip.md)                 |
| `org.tentackle.reflect`                                                                            | Class mapping, interceptable proxies, reflection helpers             | [reflect.md](reflect.md), [interceptors.md](interceptors.md) |
| `org.tentackle.bind`                                                                               | Model↔view binding                                                   | [binding.md](binding.md)           |
| `org.tentackle.validate` (+ `scope`, `validator`, `severity`)                                      | Validation framework                                                 | [validation.md](validation.md)     |
| `org.tentackle.log`                                                                                | Pluggable logging, MDC, `@Log`, method statistics                    | [logging.md](logging.md)           |
| `org.tentackle.task`                                                                               | Serialized task dispatchers                                          | [tasks-daemon.md](tasks-daemon.md) |
| `org.tentackle.script`                                                                             | Scripting abstraction                                                | [scripting.md](scripting.md)       |
| `org.tentackle.io`                                                                                 | Streams, sockets, reconnection (TRIP transport building blocks)      | [io.md](io.md)                     |
| `org.tentackle.prefs`                                                                              | Pluggable persisted preferences                                      | [preferences.md](preferences.md)   |
| `org.tentackle.keyring`                                                                            | Secrets on the client (tokens, passwords)                            | [keyring.md](keyring.md)           |
| `org.tentackle.daemon`                                                                             | Background-thread supervision                                        | [tasks-daemon.md](tasks-daemon.md) |
| `org.tentackle.misc` (+ `misc.time`)                                                               | General-purpose utilities, pooling, smart date/time parsing          | [misc.md](misc.md)                 |
| `org.tentackle.apt` (+ `apt.visitor`)                                                              | Build-time annotation processors                                     | [annotation-processors.md](annotation-processors.md) |
| `org.tentackle.service`                                                                            | The [`Hook`](../java/org/tentackle/service/Hook.java) (`ModuleHook`) | —                                  |

---

## How It Fits Together

`tentackle-core` is the layer where the framework's runtime mechanisms first appear, and almost every other module
consumes one or more of its subsystems:

```text
   FX / RDC ──── binding, validation, scripting, smart date/time parsing
   pdo / domain ─ interceptable proxies (Interceptable), TRIP, validation
   persistence ── TRIP remote delegates, tasks, diagnostics
   database ───── TRIP transport, tasks, persisted preferences, reconnection
   session ────── tasks (SessionTaskDispatcher), TRIP (remote sessions)
        │
        └──────────► tentackle-core (TRIP, reflect, bind, validate, log,
                      task, script, io, prefs, daemon, misc)
                          │
                          └──► tentackle-common (services, data types)
```

For example, a PDO is an `Interceptable` proxy (reflect) whose `@Transaction`/`@Log` methods are wrapped by
interceptors; when it travels to a server, it is serialized with TRIP; its fields are validated through the
validation framework and bound to FX controls through the binding engine; the server's background work runs on
`task` dispatchers supervised by the `daemon` package over `io` transports. All of this is configured through the
service API from `tentackle-common`.

---

## Module Dependencies

- It **requires transitive** only [`tentackle-common`](../../../../tentackle-common/src/main/docs/common.md) from
  Tentackle, plus `java.prefs` and `java.management` from the JDK. `java.logging` is required, but not transitively.
- It **uses** `org.tentackle.log.Logger` and `org.tentackle.script.ScriptingLanguage` so that non-modular
  providers remain visible even in jlink'd images.
- It declares no `opens`: modules whose classes TRIP serializes open their packages *to* `org.tentackle.core`
  (as `tentackle-sql` does). Core itself **provides** a `ModuleHook` (`org.tentackle.service.Hook`).
- Every Tentackle runtime module above this one — session, database, pdo, domain, persistence, the FX stack —
  depends on it. `tentackle-sql` sits beside it on top of `tentackle-common`.

## Related Documentation

- [TRIP](trip.md) — the remoting/serialization protocol.
- [Interceptors](interceptors.md) — annotation-driven method interception and the interceptable proxy engine.
- [Binding](binding.md) — connecting models to views.
- [Validation](validation.md) — annotation-driven, scope-aware validation.
- [Services / ServiceFinder](../../../../tentackle-common/src/main/docs/services.md) — the SPI that makes every core subsystem pluggable.
- [Session](../../../../tentackle-session/src/main/docs/session.md) — builds tasks, dispatchers and remoting on top of core.
