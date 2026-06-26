# Tentackle Script JSR — The JSR-223 Provider

## Overview and Motivation

`tentackle-script-jsr` is the pluggable scripting-language provider that bridges Tentackle's language-agnostic
scripting API in `org.tentackle.script` (part of **`tentackle-core`**) to **any `javax.script` (JSR-223) engine**.
Instead of binding one specific language, it adapts *whatever* script engines are registered with the JDK's
`ScriptEngineManager` — Groovy via `groovy-jsr223`, JavaScript via a GraalVM engine, the `jruby` engine, and so on.

> For the full picture of the scripting API — `ScriptFactory`, `Script`, `ScriptVariable`, `ScriptConverter`, the
> she-bang notation and how a language is discovered and bound — see the
> **[scripting deep-dive](../../../../tentackle-core/src/main/docs/scripting.md)**. This page documents only the
> JSR-223-specific provider.

What makes this module unusual among the providers is that it does not register a *language*; it **replaces the
factory**. With it on the path, a single application can mix JSR-223 engines and the native Groovy/Ruby providers.

```xml
<!-- bridge Tentackle scripting to javax.script engines -->
<dependency>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-script-jsr</artifactId>
</dependency>
<!-- plus at least one JSR-223 engine of your choosing, e.g., Groovy -->
<dependency>
  <groupId>org.apache.groovy</groupId>
  <artifactId>groovy-jsr223</artifactId>
</dependency>
```

> **No engine ships with this module.** Since Nashorn was removed from the JDK, `tentackle-script-jsr` bundles no
> engine of its own — its `groovy-jsr223` and `jruby` dependencies are **test-scoped only**. A production
> application must add the JSR-223 engine(s) it wants. Its dependencies on `tentackle-core` and `tentackle-common`
> are declared `optional` to avoid pulling them transitively into downstream projects.

---

## Replacing the Factory

The heart of the module is [`JSR223ScriptFactory`](../java/org/tentackle/script/jsr/JSR223ScriptFactory.java),
annotated `@Service(ScriptFactory.class)` and extending
[`DefaultScriptFactory`](../../../../tentackle-core/src/main/java/org/tentackle/script/DefaultScriptFactory.java).
Because it is itself a `ScriptFactory` service, `ScriptFactory.getInstance()` returns it in preference to the
default when this jar is present. Its overridden `loadLanguages()`:

1. enumerates **every** `ScriptEngineFactory` registered with a fresh `ScriptEngineManager`, wraps each in a
   `JSR223Language`, and registers it under each of the engine's names with `putIfAbsent`; then
2. calls `super.loadLanguages()` to add the annotation-based `@Service(ScriptingLanguage.class)` providers
   (the native Groovy/Ruby modules, if present) — **without** overriding abbreviations a JSR engine already
   claimed, since `putIfAbsent` lets the first registration win.

So the module composes rather than supplants: JSR engines and native providers coexist, and load order decides who
owns an ambiguous abbreviation (e.g. `groovy`).

---

## How a Language Is Adapted

[`JSR223Language`](../java/org/tentackle/script/jsr/JSR223Language.java) wraps one `ScriptEngineFactory`. Unlike
the native providers it is **not** a `@Service` — it is instantiated dynamically by the factory above, one per
discovered engine. It derives everything from the engine:

- **`getName()`** → `engineFactory.getLanguageName()`
- **`getAbbreviations()`** → `engineFactory.getNames()`
- It eagerly obtains one `ScriptEngine` from the factory and reuses it.

### Variable-name translation

Most engines reference a bound variable by its bare name, so `createLocalVariableReference` returns it unchanged.
The `jruby` engine is the exception: like the native Ruby provider it needs the `@name` instance-variable syntax,
so `JSR223Language` installs a translator that prepends `@` when the engine name contains `JRuby`.

### Thread-safety inference

`isEngineThreadSafe(engineFactory)` decides per engine whether parallel execution is safe:

- The **`jruby`** engine is treated as **not** thread-safe — it advertises `THREAD_ISOLATED` but does not honor it
  reliably, so parallel scripts fail intermittently.
- **Nashorn** (`Nashorn` in the engine name) is treated as thread-safe even though it reports no `THREADING`
  parameter, because each `eval` is passed fresh `Bindings`.
- Otherwise, the engine is thread-safe if it declares the `THREADING` parameter.

---

## Compilation, Caching, and Execution

[`JSR223Script`](../java/org/tentackle/script/jsr/JSR223Script.java) is the `AbstractScript` subclass returned by
the language. It follows the API's uniform *create → (lazily compile) → execute* lifecycle:

- **Lazy compilation.** The source is compiled on first `execute()` (or eagerly via `validate()`), through the
  engine's `javax.script.Compilable` interface, producing a `CompiledScript` wrapped in
  [`CompiledJSR223Script`](../java/org/tentackle/script/jsr/CompiledJSR223Script.java).
- **Caching.** With `cached = true`, identical source strings share one compiled unit via a static
  `ConcurrentHashMap<code, CompiledJSR223Script>` resolved with `computeIfAbsent`; `cached = false` keeps a private
  copy. The compiled unit is held in a `volatile` field guarded by double-checked locking.
- **Compile-time locking.** Compilation *itself* is synchronized on the engine when the script is `threadSafe`,
  because some engines (notably JRuby) throw `ConcurrentModificationException` sporadically while compiling in
  parallel.
- **Execution.** Each run builds a fresh `SimpleBindings` from the supplied
  [`ScriptVariable`](../../../../tentackle-core/src/main/java/org/tentackle/script/ScriptVariable.java)s (translated
  names included) and calls `CompiledScript.eval(bindings)`. If the engine is inherently thread-safe — or the
  caller did not request thread safety — `eval` runs unsynchronized; otherwise it synchronizes on the engine.
- Failures (`ScriptException` or runtime) surface as [`ScriptRuntimeException`](../../../../tentackle-core/src/main/java/org/tentackle/script/ScriptRuntimeException.java).

---

## Validation Message Conversion

Validator messages may use the i18n shortcut `@('key', args…)`, which the scripting API rewrites into a real
method call before compilation via a [`ScriptConverter`](../../../../tentackle-core/src/main/java/org/tentackle/script/ScriptConverter.java).
This module registers [`ECMAScriptValidationMessageConverter`](../java/org/tentackle/script/jsr/ECMAScriptValidationMessageConverter.java),
annotated `@MessageScriptConverter("ECMAScript")`, for the JavaScript case. Like the other providers' converters it
extends `AbstractScriptValidationMessageConverter` with no behavioral change — the base converter expresses the
validated object through the language's own `createLocalVariableReference`. (For Groovy and Ruby reached *through*
JSR-223, their respective native converters still apply by language name.)

> **Nashorn caveat.** Nashorn has been deprecated since Java 11 and removed from current JDKs; a GraalVM-based
> engine is the usual replacement. The message converter is registered under the name `ECMAScript`.

---

## Packaging and Modularity

Unlike the automatic-module Groovy and Ruby providers, this module is **fully modularized**:

```java
module org.tentackle.script.jsr {
  exports org.tentackle.script.jsr;
  requires transitive org.tentackle.core;
  requires transitive java.scripting;
  provides org.tentackle.common.ModuleHook with org.tentackle.script.jsr.service.Hook;
}
```

`requires transitive java.scripting` brings the `javax.script` API onto the module graph. The
[`Hook`](../java/org/tentackle/script/jsr/service/Hook.java) `ModuleHook` provides resource-bundle access for the
module. The `@Service(ScriptFactory.class)` annotation on `JSR223ScriptFactory` is turned into a
`META-INF/services/org.tentackle.script.ScriptFactory` descriptor by the `tentackle-maven-plugin` `analyze` goal,
so the factory replacement also works on the plain classpath.

---

## Source Map

| Type                                                                                 | Location                           |
|--------------------------------------------------------------------------------------|------------------------------------|
| `JSR223ScriptFactory` (the `@Service(ScriptFactory.class)` replacement)              | `org.tentackle.script.jsr`         |
| `JSR223Language` (per-engine adapter, thread-safety inference, variable translation) | `org.tentackle.script.jsr`         |
| `JSR223Script` (lazy compile, caching, synchronized compile/eval)                    | `org.tentackle.script.jsr`         |
| `CompiledJSR223Script` (code + `javax.script.CompiledScript`)                        | `org.tentackle.script.jsr`         |
| `ECMAScriptValidationMessageConverter` (`@MessageScriptConverter`)                   | `org.tentackle.script.jsr`         |
| `Hook` (`ModuleHook` service)                                                        | `org.tentackle.script.jsr.service` |

## See Also

- **[Tentackle Scripting — the pluggable scripting language API](../../../../tentackle-core/src/main/docs/scripting.md)** — the factory, `Script`/`ScriptVariable`, she-bang notation, converters and language discovery.
- **[Validation](../../../../tentackle-core/src/main/docs/validation.md)** — the largest consumer of scripting.
- The sibling native providers: [`tentackle-script-groovy`](../../../../tentackle-script-groovy/src/main/docs/groovy.md) (embedded `GroovyClassLoader`) and [`tentackle-script-ruby`](../../../../tentackle-script-ruby/src/main/docs/ruby.md) (native JRuby embed API). Note the two routes to JRuby — the dedicated native module versus this module's `jruby` JSR engine; prefer the native module, which this provider treats as the thread-safe one.
