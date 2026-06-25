# Tentackle Logging SLF4J — The SLF4J Provider

## Overview and Motivation

`tentackle-log-slf4j` is one of the pluggable logging back-end providers for Tentackle. Tentackle code never logs
against a concrete logging library; it logs through the backend-agnostic facade in `org.tentackle.log` (part of
**`tentackle-core`**). This module binds that facade to **[SLF4J](https://www.slf4j.org/)**, and therefore to
whatever SLF4J itself is bound to at runtime (Logback, `slf4j-simple`, `log4j-slf4j-impl`, …).

> For the full picture of the facade — the `Logger` API, levels, MDC, `@Log`, method statistics and how a
> back-end is discovered and bound — see the **[logging deep-dive](../../../../tentackle-core/src/main/docs/logging.md)**.
> This page documents only the SLF4J-specific provider.

Selecting SLF4J is purely a matter of dependencies: put this module (plus an SLF4J binding) on the path and
Tentackle routes all of its logging through SLF4J. With no provider module present, Tentackle falls back to plain
`java.util.logging`.

```xml
<!-- route Tentackle logging through SLF4J -->
<dependency>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-log-slf4j</artifactId>
</dependency>
<!-- plus exactly one SLF4J binding, e.g. Logback -->
<dependency>
  <groupId>ch.qos.logback</groupId>
  <artifactId>logback-classic</artifactId>
</dependency>
```

The dependency on `tentackle-core` is declared `optional` in the POM so that adding this provider does not pull
`tentackle-core` transitively into a downstream project that already depends on it.

---

## How It Binds

The module ships a single `Logger` implementation,
[`SLF4JLogger`](../java/org/tentackle/log/slf4j/SLF4JLogger.java), annotated `@Service(Logger.class)`. The
`@Service` annotation processor generates `META-INF/services/org.tentackle.log.Logger`, so when this jar is on the
path `DefaultLoggerFactory` discovers it and binds the facade to SLF4J — no configuration required. (See
[*How a Backend Is Selected*](../../../../tentackle-core/src/main/docs/logging.md#how-a-backend-is-selected) in the
core doc.)

`SLF4JLogger` provides a static `getLogger(String)` factory that caches one instance per name in a `HashMap`, which
is the convention the factory prefers for per-name logger caching.

---

## Level Mapping

The five Tentackle levels map onto the SLF4J scale as follows:

| Tentackle | SLF4J |
|-----------|-------|
| `FINER`   | `TRACE` |
| `FINE`    | `DEBUG` |
| `INFO`    | `INFO`  |
| `WARNING` | `WARN`  |
| `SEVERE`  | `ERROR` |

The per-level guards (`isFineLoggable()` etc.) delegate straight to SLF4J's `isDebugEnabled()`,
`isTraceEnabled()`, … so disabled levels cost almost nothing and lazy `Supplier` messages are never evaluated.

---

## Location Awareness

A naïve facade reports *its own* class and line number as the origin of every log statement, which makes
pattern-layout `%class`/`%line` output useless. `SLF4JLogger` avoids this:

- On construction it checks whether the bound `org.slf4j.Logger` is a
  [`LocationAwareLogger`](https://www.slf4j.org/api/org/slf4j/spi/LocationAwareLogger.html) (Logback and most
  bindings are).
- If so, it logs through `LocationAwareLogger.log(...)`, passing its own classname as the FQCN to **exclude** from
  the stack, so the *caller's* class and line are reported.
- If the binding is not location-aware (e.g. `slf4j-simple`), it falls back to the ordinary
  `trace/debug/info/warn/error` methods.

---

## Message Formatting

SLF4J's native `{}` placeholder syntax is **not** used. Tentackle's facade uses `java.text.MessageFormat`
(`{0}`, `{1}`, …) uniformly across all back-ends, so `SLF4JLogger` formats the message with `MessageFormat` itself
and hands SLF4J a finished string. Parameter and message *suppliers* are resolved only after the level guard
passes, so unused arguments are never computed. Stacktraces are rendered through a
[`LoggerOutputStream`](../../../../tentackle-core/src/main/java/org/tentackle/log/LoggerOutputStream.java) at the
requested level (`SEVERE` by default).

---

## Mapped Diagnostic Context

[`SLF4JMappedDiagnosticContext`](../java/org/tentackle/log/slf4j/SLF4JMappedDiagnosticContext.java) extends
[`AbstractMappedDiagnosticContext`](../../../../tentackle-core/src/main/java/org/tentackle/log/AbstractMappedDiagnosticContext.java)
and delegates `put`/`get`/`remove`/`clear`/`getContext` directly to SLF4J's thread-local `org.slf4j.MDC`. It is a
singleton obtained via `ServiceFactory`, and is what `SLF4JLogger.getMappedDiagnosticContext()` returns — so MDC
keys set through the Tentackle facade show up in SLF4J's MDC and can be referenced from the back-end's layout
pattern.

---

## Packaging and Modularity

The module is fully modular:

```java
module org.tentackle.log.slf4j {
  exports org.tentackle.log.slf4j;
  requires transitive org.tentackle.core;
  requires org.slf4j;
  provides org.tentackle.common.ModuleHook with org.tentackle.log.slf4j.service.Hook;
}
```

It also publishes an `Automatic-Module-Name` of `org.tentackle.log.slf4j` for classpath use. The
[`Hook`](../java/org/tentackle/log/slf4j/service/Hook.java) `ModuleHook` provides resource-bundle access for the
module. For **OSGi**, a dedicated SLF4J activator bundle exists under
`tentackle-osgi/tentackle-osgi-activators/`.

---

## Source Map

| Type | Location |
|------|----------|
| `SLF4JLogger` (the `@Service(Logger.class)` provider) | `org.tentackle.log.slf4j` |
| `SLF4JMappedDiagnosticContext` | `org.tentackle.log.slf4j` |
| `Hook` (`ModuleHook` service) | `org.tentackle.log.slf4j.service` |

## See Also

- **[Tentackle Logging — the pluggable logging API](../../../../tentackle-core/src/main/docs/logging.md)** — the facade, `@Log`, MDC, statistics and back-end selection.
- The sibling [`tentackle-log-log4j2v` provider](../../../../tentackle-log-log4j2v/src/main/docs/tentackle-log-log4j2v.md) — the same pattern bound to Apache Log4J 2.
