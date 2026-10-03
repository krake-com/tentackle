# Tentackle Logging SLF4J — The SLF4J Provider

## Overview and Motivation

`tentackle-log-slf4j` is one of the pluggable logging backend providers for Tentackle. Tentackle code never logs
against a concrete logging library; it logs through the backend-agnostic facade in `org.tentackle.log` (part of
**`tentackle-core`**). This module binds that facade to **[SLF4J](https://www.slf4j.org/)**, and therefore to
whatever SLF4J itself is bound to at runtime (Logback, `slf4j-simple`, `log4j-slf4j2-impl`, …).

> For the full picture of the facade — the `Logger` API, levels, MDC, `@Log`, method statistics and how a
> backend is discovered and bound — see the **[logging deep-dive](../../../../tentackle-core/src/main/docs/logging.md)**.
> This page documents only the SLF4J-specific provider.

Selecting SLF4J is purely a matter of dependencies: put this module (plus an SLF4J provider, which SLF4J 1.x called a
*binding*) on the path, and Tentackle routes all of its logging through SLF4J. With no Tentackle provider module
present, Tentackle falls back to plain `java.util.logging`.

```xml
<!-- route Tentackle logging through SLF4J -->
<dependency>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-log-slf4j</artifactId>
</dependency>
<!-- plus exactly one SLF4J provider, e.g. Logback -->
<dependency>
  <groupId>ch.qos.logback</groupId>
  <artifactId>logback-classic</artifactId>
</dependency>
```

The module itself depends on `slf4j-api` only. Don't forget the SLF4J provider: without one, SLF4J 2 prints a
warning at startup and falls back to its no-operation logger, so all Tentackle logging is **silently discarded** —
the `java.util.logging` fallback applies only if this module is missing, not if SLF4J has nothing to bind to.
Likewise, with more than one provider on the path, SLF4J picks one of them and warns about the others.

The dependency on `tentackle-core` is declared `optional` in the POM: the application declares `tentackle-core`
itself, and the provider deliberately doesn't drag it in.

SLF4J's backend is configured the usual way (e.g., a `logback.xml` on the classpath); Tentackle adds nothing to that.

---

## How It Binds

The module ships a single `Logger` implementation,
[`SLF4JLogger`](../java/org/tentackle/log/slf4j/SLF4JLogger.java), annotated `@Service(Logger.class)`. The
`@Service` annotation processor generates `META-INF/services/org.tentackle.log.Logger`, so when this jar is on the
path `DefaultLoggerFactory` discovers it and binds the facade to SLF4J — no configuration required. (See
[*How a Backend Is Selected*](../../../../tentackle-core/src/main/docs/logging.md#how-a-backend-is-selected) in the
core doc.)

`SLF4JLogger` provides a static `getLogger(String)` factory, which `DefaultLoggerFactory` prefers over the public
`SLF4JLogger(String)` constructor. It caches one instance per name in a synchronized map; the instances are never
evicted.

---

## Level Mapping

The five Tentackle levels map onto the SLF4J scale as follows:

| Tentackle | SLF4J   |
|-----------|---------|
| `FINER`   | `TRACE` |
| `FINE`    | `DEBUG` |
| `INFO`    | `INFO`  |
| `WARNING` | `WARN`  |
| `SEVERE`  | `ERROR` |

The level guards (`isFinerLoggable()`, `isFineLoggable()` etc.) delegate straight to SLF4J's `isTraceEnabled()`,
`isDebugEnabled()`, … The logging methods themselves check the level before building the message (see below), so
disabled levels cost almost nothing and lazy `Supplier` messages are never evaluated.

---

## Location Awareness

A naïve facade reports *its own* class and line number as the origin of every log statement, which makes
pattern-layout `%class`/`%line` output useless. `SLF4JLogger` avoids this:

- On construction it checks whether the bound `org.slf4j.Logger` is a
  [`LocationAwareLogger`](https://www.slf4j.org/api/org/slf4j/spi/LocationAwareLogger.html) (Logback and most
  providers are).
- If so, it logs through `LocationAwareLogger.log(...)`, passing its own classname as the FQCN to **exclude** from
  the stack, so the *caller's* class and line are reported.
- If the provider is not location-aware (e.g. `slf4j-simple`), it falls back to the ordinary
  `trace/debug/info/warn/error` methods, and the backend reports the wrapper as the origin.

`getLoggerImpl()` returns the `org.slf4j.Logger` obtained from SLF4J's `LoggerFactory`, whether location-aware or
not.

---

## Message Formatting

SLF4J's native `{}` placeholder syntax is **not** used. Tentackle's facade uses `java.text.MessageFormat`
(`{0}`, `{1}`, …) uniformly across all backends, so `SLF4JLogger` formats the message with `MessageFormat` itself
and hands SLF4J a finished string without arguments — a literal `{}` in it is therefore logged as is. Message and
parameter *suppliers* are resolved only after the level guard passes, so unused arguments are never computed.

Whether `MessageFormat` is applied at all depends on the overload:

| Call                                           | Formatting                                      |
|------------------------------------------------|-------------------------------------------------|
| `info(String)`, `info(String, Throwable)`      | none — the message is logged verbatim           |
| `info(String, Object...)` with parameters      | `MessageFormat`                                 |
| `info(String, Object...)` without parameters   | none — the message is logged verbatim           |
| `info(String, Supplier<?>...)`                 | always `MessageFormat`                          |
| `info(Supplier<String>)` and variants          | none — the supplied string is logged verbatim   |

So `MessageFormat`'s quoting rules (a single quote must be doubled: `''`) apply only to formatted messages: a
verbatim `"it''s"` is logged with both quotes. An SLF4J-style `{}` in a formatted message is not a valid
`MessageFormat` element: `info("value {}", x)` throws an `IllegalArgumentException` in the caller's thread (if the
level is enabled).

`logStacktrace(…)` prints the stacktrace through a
[`LoggerOutputStream`](../../../../tentackle-core/src/main/java/org/tentackle/log/LoggerOutputStream.java), which
logs it line by line at the requested level (`SEVERE` by default).

---

## Mapped Diagnostic Context

[`SLF4JMappedDiagnosticContext`](../java/org/tentackle/log/slf4j/SLF4JMappedDiagnosticContext.java) extends
[`AbstractMappedDiagnosticContext`](../../../../tentackle-core/src/main/java/org/tentackle/log/AbstractMappedDiagnosticContext.java)
and delegates `put`/`get`/`remove`/`clear`/`getContext` directly to SLF4J's thread-local `org.slf4j.MDC`. It is a
singleton obtained via `ServiceFactory` (and can therefore be replaced by an application `@Service`), and is what
`SLF4JLogger.getMappedDiagnosticContext()` returns — so MDC keys set through the Tentackle facade show up in SLF4J's
MDC and can be referenced from the backend's layout pattern (e.g. `%X` in Logback). `getContext()` returns a copy of
the current thread's context (an empty map if there is none): modifying it does not change the MDC.

---

## Packaging and Modularity

The module is fully modular:

```java
module org.tentackle.log.slf4j {
  exports org.tentackle.log.slf4j;
  requires transitive org.tentackle.core;
  requires org.slf4j;                         // slf4j-api only; the SLF4J provider is a runtime-only dependency
  provides org.tentackle.common.ModuleHook with org.tentackle.log.slf4j.service.Hook;
}
```

Only `slf4j-api` is `requires`d, since the main code talks to the API exclusively. SLF4J locates its provider via
the `ServiceLoader`, so neither Logback nor any other provider is named in `module-info.java`. (The module's tests,
which do access Logback to capture the events, add the missing read edges via
`--add-reads org.tentackle.log.slf4j=ch.qos.logback.classic` and `…=ch.qos.logback.core` in the surefire `argLine`,
and exclude the `slf4j-simple` test dependency inherited from the parent POM, so that Logback is the only provider.)

The [`Hook`](../java/org/tentackle/log/slf4j/service/Hook.java) `ModuleHook` provides resource-bundle access for the
module. For **OSGi**, the module is wrapped into the `tentackle.log.slf4j.osgi` bundle with its own activator
(`tentackle-osgi/tentackle-osgi-activators/tentackle-log-slf4j-osgi-activator`).

---

## Source Map

| Type                                                  | Location                          |
|-------------------------------------------------------|-----------------------------------|
| `SLF4JLogger` (the `@Service(Logger.class)` provider) | `org.tentackle.log.slf4j`         |
| `SLF4JMappedDiagnosticContext`                        | `org.tentackle.log.slf4j`         |
| `Hook` (`ModuleHook` service)                         | `org.tentackle.log.slf4j.service` |

## See Also

- **[Tentackle Logging — the pluggable logging API](../../../../tentackle-core/src/main/docs/logging.md)** — the facade, `@Log`, MDC, statistics and backend selection.
- The sibling [`tentackle-log-log4j2v` provider](../../../../tentackle-log-log4j2v/src/main/docs/tentackle-log-log4j2v.md) — the same pattern bound to Apache Log4J 2.
