# Tentackle Logging Log4J 2 — The Log4J 2 Provider

## Overview and Motivation

`tentackle-log-log4j2v` is one of the pluggable logging backend providers for Tentackle. Tentackle code never logs
against a concrete logging library; it logs through the backend-agnostic facade in `org.tentackle.log` (part of
**`tentackle-core`**). This module binds that facade to **[Apache Log4J 2](https://logging.apache.org/log4j/2.x/)**.

The artifact name reads *"log4j2v"* — **Log4J version 2**, deliberately distinct from the long-dead Log4J 1.x.

> For the full picture of the facade — the `Logger` API, levels, MDC, `@Log`, method statistics and how a
> backend is discovered and bound — see the **[logging deep-dive](../../../../tentackle-core/src/main/docs/logging.md)**.
> This page documents only the Log4J 2-specific provider.

Selecting Log4J 2 is purely a matter of dependencies: put this module on the path, and Tentackle routes all of its
logging through Log4J 2. With no provider module present, Tentackle falls back to plain `java.util.logging`.

```xml
<!-- route Tentackle logging through Log4J 2 -->
<dependency>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-log-log4j2v</artifactId>
</dependency>
```

The module depends on `log4j-api` and `log4j-core` (the implementation), so no further Log4J dependency is
needed. The dependency on `tentackle-core` is declared `optional` in the POM: the application declares
`tentackle-core` itself, and the provider deliberately doesn't drag it in.

Only `log4j-api` is `requires`d in `module-info.java`, since the main code talks to the API exclusively.
`log4j-core` is a runtime-only dependency that Log4J locates via its own service lookup. (Both jars are modular;
the module's tests, which do access `log4j-core`, add the missing read edge via
`--add-reads org.tentackle.log.log4j2v=org.apache.logging.log4j.core` in the surefire `argLine`.)

---

## How It Binds

The module ships a single `Logger` implementation,
[`Log4J2Logger`](../java/org/tentackle/log/log4j2v/Log4J2Logger.java), annotated `@Service(Logger.class)`. The
`@Service` annotation processor generates `META-INF/services/org.tentackle.log.Logger`, so when this jar is on the
path `DefaultLoggerFactory` discovers it and binds the facade to Log4J 2 — no configuration required. (See
[*How a Backend Is Selected*](../../../../tentackle-core/src/main/docs/logging.md#how-a-backend-is-selected) in the
core doc.)

`Log4J2Logger` provides a static `getLogger(String)` factory, which `DefaultLoggerFactory` prefers over the public
`Log4J2Logger(String)` constructor. It caches one instance per name in a synchronized map; the instances are never
evicted.

---

## Level Mapping

The five Tentackle levels map onto Log4J's `Level` as follows:

| Tentackle | Log4J 2 |
|-----------|---------|
| `FINER`   | `TRACE` |
| `FINE`    | `DEBUG` |
| `INFO`    | `INFO`  |
| `WARNING` | `WARN`  |
| `SEVERE`  | `ERROR` |

Level guards (`isFineLoggable()` etc.) all funnel through `logger.isEnabled(translateLevel(level))`. The logging
methods themselves are guarded the same way (see below), so disabled levels cost almost nothing and lazy `Supplier`
messages are never evaluated.

---

## Location Awareness

A naïve facade reports *its own* class and line number as the origin of every log statement, which makes
pattern-layout `%C`/`%L`/`%M` output useless. `Log4J2Logger` avoids this by wrapping the Log4J logger in an
[`ExtendedLoggerWrapper`](https://logging.apache.org/log4j/2.x/javadoc/log4j-api/org/apache/logging/log4j/spi/ExtendedLoggerWrapper.html)
and logging through `logIfEnabled(FQCN, …)`, passing its own classname as the **fully-qualified class name** to
exclude from the stack walk. Log4J then attributes each line to the *caller* rather than to the wrapper.

Consequently, `getLoggerImpl()` returns that `ExtendedLoggerWrapper`, not the `org.apache.logging.log4j.Logger`
obtained from the `LogManager`. Both share the same name and configuration.

---

## Message Formatting

Log4J's native `{}` placeholder syntax is **not** used. Tentackle's facade uses `java.text.MessageFormat`
(`{0}`, `{1}`, …) uniformly across all backends, so `Log4J2Logger` formats the message with `MessageFormat` itself
and hands Log4J a finished `SimpleMessage`. The message (and any parameter *suppliers*) are wrapped in a
`Supplier<Message>` passed to `logIfEnabled`, so they are resolved only when the level is actually enabled and
unused arguments are never computed.

Whether `MessageFormat` is applied at all depends on the overload:

| Call                                           | Formatting                                      |
|------------------------------------------------|-------------------------------------------------|
| `info(String)`, `info(String, Throwable)`      | none — the message is logged verbatim           |
| `info(String, Object...)` with parameters      | `MessageFormat`                                 |
| `info(String, Object...)` without parameters   | none — the message is logged verbatim           |
| `info(String, Supplier<?>...)`                 | always `MessageFormat`                          |
| `info(Supplier<String>)` and variants          | none — the supplied string is logged verbatim   |

So `MessageFormat`'s quoting rules (a single quote must be doubled: `''`) apply only to formatted messages: a
verbatim `"it''s"` is logged with both quotes. A Log4J-style `{}` in a verbatim message is logged as is (a
`SimpleMessage` is never parameterized), but in a formatted message it is not a valid `MessageFormat` element:
`info("value {}", x)` throws an `IllegalArgumentException` in the caller's thread (if the level is enabled).

`logStacktrace(…)` prints the stacktrace through a
[`LoggerOutputStream`](../../../../tentackle-core/src/main/java/org/tentackle/log/LoggerOutputStream.java), which
logs it line by line at the requested level (`SEVERE` by default).

---

## Mapped Diagnostic Context

[`Log4J2MappedDiagnosticContext`](../java/org/tentackle/log/log4j2v/Log4J2MappedDiagnosticContext.java) extends
[`AbstractMappedDiagnosticContext`](../../../../tentackle-core/src/main/java/org/tentackle/log/AbstractMappedDiagnosticContext.java)
and delegates `put`/`get`/`remove`/`clear`/`getContext` directly to Log4J's thread-local
`org.apache.logging.log4j.ThreadContext`. It is a singleton obtained via `ServiceFactory` (and can therefore be
replaced by an application `@Service`), and is what `Log4J2Logger.getMappedDiagnosticContext()` returns — so MDC
keys set through the Tentackle facade show up in Log4J's `ThreadContext` and can be referenced from a layout pattern
(`%X`). `getContext()` returns a copy of the current thread's context: modifying it does not change the MDC.

---

## Packaging and Modularity

The module is fully modular:

```java
module org.tentackle.log.log4j2v {
  exports org.tentackle.log.log4j2v;
  requires transitive org.tentackle.core;
  requires org.apache.logging.log4j;          // log4j-api only; log4j-core is a runtime-only dependency
  provides org.tentackle.common.ModuleHook with org.tentackle.log.log4j2v.service.Hook;
}
```

The [`Hook`](../java/org/tentackle/log/log4j2v/service/Hook.java) `ModuleHook` provides resource-bundle access for
the module. For **OSGi**, the module is wrapped into the `tentackle.log.log4j2v.osgi` bundle with its own activator
(`tentackle-osgi/tentackle-osgi-activators/tentackle-log-log4j2v-osgi-activator`).

Log4J itself is configured the usual way (e.g., a `log4j2.xml` on the classpath); Tentackle adds nothing to that.

---

## Source Map

| Type                                                   | Location                            |
|--------------------------------------------------------|-------------------------------------|
| `Log4J2Logger` (the `@Service(Logger.class)` provider) | `org.tentackle.log.log4j2v`         |
| `Log4J2MappedDiagnosticContext`                        | `org.tentackle.log.log4j2v`         |
| `Hook` (`ModuleHook` service)                          | `org.tentackle.log.log4j2v.service` |

## See Also

- **[Tentackle Logging — the pluggable logging API](../../../../tentackle-core/src/main/docs/logging.md)** — the facade, `@Log`, MDC, statistics and backend selection.
- The sibling [`tentackle-log-slf4j` provider](../../../../tentackle-log-slf4j/src/main/docs/tentackle-log-slf4j.md) — the same pattern bound to SLF4J.
