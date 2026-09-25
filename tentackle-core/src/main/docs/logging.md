# Tentackle Logging — The Pluggable Logging API

## Overview and Motivation

Tentackle does **not** log against a concrete logging library. Instead, the whole framework — and any application
built on it — logs through a thin, backend-agnostic facade in `org.tentackle.log` (in **`tentackle-core`**). At
runtime a single provider is bound to that facade through the
[service API](../../../../tentackle-common/src/main/docs/services.md): plain `java.util.logging` by default, or
SLF4J or Log4J 2 by simply putting the matching jar on the path. No code changes, no per-library imports scattered
across the codebase.

### Why a facade?

Tentackle code uses thousands of log statements and is deployed in very different environments — desktop
(JavaFX), application servers, embedded, OSGi. A facade buys:

- **Backend independence.** Application and framework code call `Logger`; the operator chooses the backend at
  assembly time. Swapping SLF4J for Log4J 2 is a dependency change, not a code change.
- **A uniform model across backends.** A single five-value `Level` enum, a `MappedDiagnosticContext` that works
  even on backends that don't natively support MDC, and modern conveniences (`Supplier`-based lazy messages,
  `{0}`-style parameter formatting) regardless of what the backend offers.
- **Framework value-adds.** Declarative method-call logging (`@Log`), per-method timing statistics, and small
  helpers (`LoggingRunnable`, `LoggerOutputStream`) that build on the facade.
- **Modular & classpath parity.** Core declares `uses org.tentackle.log.Logger`, so providers resolve under JPMS
  as well as on the plain classpath.

> This page is the deep-dive for logging; the [core module map](core.md#logging) links here. Related: the
> [interceptors](interceptors.md) doc explains the proxy engine that `@Log` plugs into.

---

## Getting a Logger

The usual idiom is a `static final` field per class:

```java
private static final Logger LOGGER = Logger.get(MyClass.class);
// or, using the StackWalker to discover the caller automatically:
private static final Logger LOGGER = Logger.get();
```

[`Logger.get(...)`](../java/org/tentackle/log/Logger.java) delegates to
[`LoggerFactory.getInstance().getLogger(name, null)`](../java/org/tentackle/log/LoggerFactory.java). Loggers are
named (by class name) and cached per name, so repeated lookups return the same instance.

---

## The `Logger` API

[`Logger`](../java/org/tentackle/log/Logger.java) is the facade interface. Its surface is deliberately rich, so
call sites stay terse and efficient.

### Levels

A single enum, ordered from most to least verbose:

```
FINER  <  FINE  <  INFO  <  WARNING  <  SEVERE
```

These map onto each backend's native scale (see the implementations below). Per-level guards are provided both
generically and explicitly:

```java
if (LOGGER.isFineLoggable()) { ... }     // or isLoggable(Level.FINE)
```

### Three ways to pass a message

Every level has overloads covering the three common patterns, so you never build a string you won't emit:

```java
LOGGER.info("plain message");
LOGGER.info("user {0} logged in from {1}", userId, host);   // MessageFormat-style params
LOGGER.fine(() -> "expensive " + dump());                   // lazy Supplier, only evaluated if enabled
LOGGER.warning("partial failure", throwable);               // message + cause
LOGGER.severe(throwable, () -> "context: " + ctx);          // cause + lazy supplier
```

The same matrix exists on the generic `log(Level, …)` methods. Parameter *suppliers*
(`finer(String, Supplier<?>...)`) defer even the argument computation.

### Stacktraces and backend access

```java
LOGGER.logStacktrace(throwable);              // at SEVERE
LOGGER.logStacktrace(Level.FINE, throwable);
Object backend = LOGGER.getLoggerImpl();      // the underlying java.util / slf4j / log4j logger
MappedDiagnosticContext mdc = LOGGER.getMappedDiagnosticContext();
```

`getLoggerImpl()` is the escape hatch for the rare case you need a backend-specific feature.

---

## Mapped Diagnostic Context (MDC)

[`MappedDiagnosticContext`](../java/org/tentackle/log/MappedDiagnosticContext.java) provides **thread-local
key/value pairs** that enrich every log line on the current thread (user id, request id, session, …):

```java
MappedDiagnosticContext mdc = LOGGER.getMappedDiagnosticContext();
mdc.put("user", userId);
try {
  ... // all log lines on this thread can include {user}
}
finally {
  mdc.remove("user");   // or mdc.clear()
}
```

Crucially, the abstraction is **uniform even where the backend lacks MDC**. SLF4J and Log4J 2 delegate to their
native MDC; for `java.util.logging` (which has none),
[`DefaultMappedDiagnosticContext`](../java/org/tentackle/log/DefaultMappedDiagnosticContext.java) keeps its own
`InheritableThreadLocal` map — so application behavior does not depend on the chosen provider.
[`AbstractMappedDiagnosticContext`](../java/org/tentackle/log/AbstractMappedDiagnosticContext.java) supplies the
shared logic (key set, `toString()` rendering, pattern matching).

---

## Framework Value-Adds

These build on the facade and are reasons the abstraction exists beyond mere backend-swapping.

### `@Log` — declarative method-call logging

Annotate a method of an `Interceptable` (e.g., a PDO or any interceptable proxy) and every call is logged with its
elapsed time:

```java
@Log                       // defaults to INFO
@Log(Logger.Level.FINE)    // or a chosen level
public void recalculate() { ... }
```

[`@Log`](../java/org/tentackle/log/Log.java) is wired via `@Interception(implementedBy = LogInterceptor.class)`;
[`LogInterceptor`](../java/org/tentackle/log/LogInterceptor.java) times the invocation with a `TimeKeeper` (only
when the level is enabled) and logs `"<method> took <n>ms"`. This is a concrete example of the
[interception engine](interceptors.md) in action.

### Method statistics

[`MethodStatistics`](../java/org/tentackle/log/MethodStatistics.java) /
[`StatisticsResult`](../java/org/tentackle/log/StatisticsResult.java) collect per-method **invocation counts and
min/max/total durations** — used, for instance, to profile remote-delegate calls. Keyed by
[`MethodStatisticsKey`](../java/org/tentackle/log/MethodStatisticsKey.java) (method + serviced class).

### Helpers

- [`LoggingRunnable`](../java/org/tentackle/log/LoggingRunnable.java) — wraps a `Runnable` to log its start and
  duration at a chosen level.
- [`LoggerOutputStream`](../java/org/tentackle/log/LoggerOutputStream.java) — an `OutputStream` that funnels
  writes into a logger; handy to capture `e.printStackTrace(stream)` output.
- [`Loggable`](../java/org/tentackle/log/Loggable.java) — an interface objects (notably exceptions) implement to
  declare *how* they want to be logged (level, with/without stacktrace); honored e.g. by the remote-invocation
  handler. See [Exceptions and Error Handling](../../../../src/main/docs/exceptions.md#loggable-how-much-of-an-exception-to-log)
  for why a denied permission or a failed login should not fill a server log with stack traces.

---

## How a Backend Is Selected

[`LoggerFactory`](../java/org/tentackle/log/LoggerFactory.java) is a singleton obtained through `ServiceFactory`
(default [`DefaultLoggerFactory`](../java/org/tentackle/log/DefaultLoggerFactory.java)). At first use the factory
resolves the configured `Logger` *implementation class*:

1. `DefaultLoggerFactory` calls `ServiceFactory.createServiceClass(Logger.class)`. The `Logger` implementation a
   provider jar registers via `@Service(Logger.class)` (generated into
   `META-INF/services/org.tentackle.log.Logger`) becomes the bound class.
2. If **no** provider is found, it logs *"no logging backend configured → using default java.util.logging"* and
   falls back to [`DefaultLogger`](../java/org/tentackle/log/DefaultLogger.java). (`DefaultLogger` itself carries
   **no** `@Service` annotation, precisely so it never competes with a real provider.)
3. To instantiate a named logger, the factory prefers a static `getLogger(String)` factory method on the
   implementation (all providers offer one, enabling per-name caching), and falls back to a `Logger(String)`
   constructor. Reflection lookups are cached per class.

Because the binding is "the single `@Service(Logger.class)` on the path", choosing a backend is just a matter of
dependencies — **put exactly one provider module on the path**.

---

## The Implementations

| Module                          | Backend                             | Registered as            |
|---------------------------------|-------------------------------------|--------------------------|
| *(built into `tentackle-core`)* | `java.util.logging`                 | fallback — no `@Service` |
| `tentackle-log-slf4j`           | SLF4J (and whatever SLF4J binds to) | `@Service(Logger.class)` |
| `tentackle-log-log4j2v`         | Apache Log4J 2                      | `@Service(Logger.class)` |

All providers translate the Tentackle `Level` onto the backend scale identically:

| Tentackle | `java.util.logging` | SLF4J   | Log4J 2 |
|-----------|---------------------|---------|---------|
| `FINER`   | `FINER`/`FINE`      | `TRACE` | `TRACE` |
| `FINE`    | `FINE`              | `DEBUG` | `DEBUG` |
| `INFO`    | `INFO`              | `INFO`  | `INFO`  |
| `WARNING` | `WARNING`           | `WARN`  | `WARN`  |
| `SEVERE`  | `SEVERE`            | `ERROR` | `ERROR` |

### Default — `java.util.logging` (no extra module)

[`DefaultLogger`](../java/org/tentackle/log/DefaultLogger.java) wraps `java.util.logging.Logger`. It is the
zero-dependency fallback used when no provider jar is present, with the thread-local
`DefaultMappedDiagnosticContext` standing in for the MDC that JUL lacks.

### SLF4J — `tentackle-log-slf4j`

[`SLF4JLogger`](../../../../tentackle-log-slf4j/src/main/java/org/tentackle/log/slf4j/SLF4JLogger.java) wraps an
`org.slf4j.Logger`. It detects whether the bound logger is a `LocationAwareLogger` and, if so, logs through the
location-aware API so the *caller's* class/line is reported rather than the wrapper's (it excludes its own
classname from the stack). MDC delegates to SLF4J's `MDC` via
[`SLF4JMappedDiagnosticContext`](../../../../tentackle-log-slf4j/src/main/java/org/tentackle/log/slf4j/SLF4JMappedDiagnosticContext.java).
This module then binds to whatever SLF4J backend you add (Logback, slf4j-simple, …). See the
[**`tentackle-log-slf4j` module doc**](../../../../tentackle-log-slf4j/src/main/docs/tentackle-log-slf4j.md) for details.

### Log4J 2 — `tentackle-log-log4j2v`

[`Log4J2Logger`](../../../../tentackle-log-log4j2v/src/main/java/org/tentackle/log/log4j2v/Log4J2Logger.java) wraps
Log4J 2 through an `ExtendedLoggerWrapper`, using the wrapper FQCN so location information stays correct, and maps
messages onto Log4J's `Level`/`SimpleMessage`. MDC delegates to Log4J's `ThreadContext` via
[`Log4J2MappedDiagnosticContext`](../../../../tentackle-log-log4j2v/src/main/java/org/tentackle/log/log4j2v/Log4J2MappedDiagnosticContext.java).
("log4j2v" = Log4J **v**ersion 2, distinct from the long-dead Log4J 1.) See the
[**`tentackle-log-log4j2v` module doc**](../../../../tentackle-log-log4j2v/src/main/docs/tentackle-log-log4j2v.md) for details.

---

## Packaging and Modularity

`tentackle-core` exports `org.tentackle.log`, `requires java.logging`, and declares
`uses org.tentackle.log.Logger` so providers resolve under JPMS even when they appear as automatic modules.

Each provider registers its `Logger` (and a `ModuleHook` service for resource access) at build time via the
`@Service` annotation processor, which generates `META-INF/services/org.tentackle.log.Logger`. The provider
modules are themselves modular:

```
module org.tentackle.log.slf4j {           module org.tentackle.log.log4j2v {
  requires transitive org.tentackle.core;     requires transitive org.tentackle.core;
  requires org.slf4j;                          requires org.apache.logging.log4j;
  provides ...ModuleHook with ...;             provides ...ModuleHook with ...;
}                                            }
```

For **OSGi**, both providers are available as bundles (`tentackle.log.slf4j.osgi` and
`tentackle.log.log4j2v.osgi`), each with its own activator. See
[OSGi Bundles](../../../../tentackle-osgi/src/main/docs/osgi.md).

### Choosing a backend

Add **exactly one** provider module as a dependency (plus its own backend libraries — e.g. a Logback binding for
SLF4J, or the Log4J 2 core). With none present, Tentackle logs via `java.util.logging`.

```xml
<!-- e.g. route Tentackle logging through SLF4J -->
<dependency>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-log-slf4j</artifactId>
</dependency>
```

### Implementing a new provider

Implement [`Logger`](../java/org/tentackle/log/Logger.java) (extending nothing is required, but providing a static
`getLogger(String)` for caching is the convention) and a
[`MappedDiagnosticContext`](../java/org/tentackle/log/MappedDiagnosticContext.java), annotate the logger
`@Service(Logger.class)`, and put the jar on the path. `DefaultLoggerFactory` discovers and binds it.

---

## Source Map

| Type                                                                                                         | Location                                              |
|--------------------------------------------------------------------------------------------------------------|-------------------------------------------------------|
| `Logger`, `LoggerFactory`, `MappedDiagnosticContext`                                                         | `tentackle-core` · `org.tentackle.log`                |
| `DefaultLoggerFactory`, `DefaultLogger`, `DefaultMappedDiagnosticContext`, `AbstractMappedDiagnosticContext` | `tentackle-core` · `org.tentackle.log`                |
| `@Log`, `LogInterceptor`, `Loggable`, `LoggingRunnable`, `LoggerOutputStream`                                | `tentackle-core` · `org.tentackle.log`                |
| `MethodStatistics`, `MethodStatisticsKey`, `StatisticsResult`                                                | `tentackle-core` · `org.tentackle.log`                |
| SLF4J provider                                                                                               | `tentackle-log-slf4j` · `org.tentackle.log.slf4j`     |
| Log4J 2 provider                                                                                             | `tentackle-log-log4j2v` · `org.tentackle.log.log4j2v` |
| OSGi bundles and activators ([OSGi Bundles](../../../../tentackle-osgi/src/main/docs/osgi.md))               | `tentackle-osgi/` (separate build)                    |
