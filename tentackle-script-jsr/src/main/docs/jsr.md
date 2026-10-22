# Tentackle Script JSR — The JSR-223 Provider

## Overview and Motivation

`tentackle-script-jsr` is the pluggable scripting-language provider that bridges Tentackle's language-agnostic
scripting API in `org.tentackle.script` (part of **`tentackle-core`**) to **any `javax.script` (JSR-223) engine**.
Instead of binding one specific language, it adapts *whatever* script engines are registered with the JDK's
`ScriptEngineManager` — Groovy via `groovy-jsr223`, JavaScript via GraalJS, the `jruby` engine, and so on.

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

> **No engine ships with this module.** Since Nashorn was removed from the JDK (Java 15), `tentackle-script-jsr`
> bundles no engine of its own — its `groovy-jsr223` and `jruby` dependencies are **test-scoped only**. A production
> application must add the JSR-223 engine(s) it wants. Its dependencies on `tentackle-core` and `tentackle-common`
> are declared `optional` to avoid pulling them transitively into downstream projects.

---

## Replacing the Factory

The heart of the module is [`JSR223ScriptFactory`](../java/org/tentackle/script/jsr/JSR223ScriptFactory.java),
annotated `@Service(ScriptFactory.class)` and extending
[`DefaultScriptFactory`](../../../../tentackle-core/src/main/java/org/tentackle/script/DefaultScriptFactory.java).
Because it is itself a `ScriptFactory` service, `ScriptFactory.getInstance()` returns it instead of the default
when this jar is present. Which of the two `@Service(ScriptFactory.class)` providers wins follows the
[service ordering rules](../../../../tentackle-common/src/main/docs/services.md#ordering-and-overriding):

- **On the module path**, this module's [`Hook`](../java/org/tentackle/script/jsr/service/Hook.java) puts it into the
  module hierarchy. Since it requires `org.tentackle.core`, it ranks above core, and its factory overrides
  `DefaultScriptFactory`.
- **On the classpath**, there is no module graph, and the first provider in classpath order wins. Hence, the
  `tentackle-script-jsr` jar must precede `tentackle-core` on the classpath.

Its overridden `loadLanguages()`:

1. enumerates **every** `ScriptEngineFactory` registered with a fresh `ScriptEngineManager`, wraps each in a
   `JSR223Language`, and registers it under each of the engine's names — lower-cased, like `DefaultScriptFactory`
   does, so that lookups are case-insensitive — with `putIfAbsent`; then
2. calls `super.loadLanguages()` to add the annotation-based `@Service(ScriptingLanguage.class)` providers
   (the native Groovy/Ruby modules, if present) — **without** overriding abbreviations a JSR engine already
   claimed, since `putIfAbsent` lets the first registration win.

So the module composes rather than supplants: JSR engines and native providers coexist, and load order decides who
owns an ambiguous abbreviation. Since the JSR engines are registered first, they always win. For example, with
`groovy-jsr223` and `jruby` on the path next to the native providers:

| Abbreviation            | Resolves to                                   |
|-------------------------|-----------------------------------------------|
| `groovy` (and `Groovy`) | the `groovy-jsr223` engine                    |
| `ruby`, `jruby`         | the `jruby` JSR-223 engine                    |
| `gv`                    | the native `tentackle-script-groovy` provider |
| `rb`                    | the native `tentackle-script-ruby` provider   |

Note that each engine is instantiated eagerly when the factory loads its languages on first use, whether or not it
is used later.

---

## How a Language Is Adapted

[`JSR223Language`](../java/org/tentackle/script/jsr/JSR223Language.java) wraps one `ScriptEngineFactory`. Unlike
the native providers it is **not** a `@Service` — it is instantiated dynamically by the factory above, one per
discovered engine. It derives everything from the engine:

- **`getName()`** → `engineFactory.getLanguageName()`
- **`getAbbreviations()`** → `engineFactory.getNames()`
- It eagerly obtains **one** `ScriptEngine` from the factory, which compiles and runs all scripts of this language.

### Variable-name translation

Most engines reference a bound variable by its bare name, so `createLocalVariableReference` returns it unchanged.
The `jruby` engine is the exception: like the native Ruby provider it needs the `@name` instance-variable syntax,
so `JSR223Language` installs a translator that prepends `@` when the engine name contains `JRuby`.

### Thread-safety inference

`isEngineThreadSafe(engineFactory)` decides per engine whether parallel execution is safe:

- The **`jruby`** engine is treated as **not** thread-safe — it advertises `THREAD_ISOLATED` but does not honor it
  reliably, so parallel scripts fail intermittently.
- **Nashorn** (`Nashorn` in the engine name, i.e. the standalone OpenJDK Nashorn) is treated as thread-safe even
  though it reports no `THREADING` parameter, because each `eval` is passed fresh `Bindings`.
- Otherwise, the engine is thread-safe if it declares the `THREADING` parameter (any non-`null` value, such as
  Groovy's `MULTITHREADED`). GraalJS, for example, declares none and is therefore synchronized.

---

## Compilation, Caching, and Execution

[`JSR223Script`](../java/org/tentackle/script/jsr/JSR223Script.java) is the `AbstractScript` subclass returned by
the language. It follows the API's uniform *create → (lazily compile) → execute* lifecycle:

- **Lazy compilation.** The source is compiled on first `execute()` (or eagerly via `validate()`), through the
  engine's `javax.script.Compilable` interface, producing a `CompiledScript` wrapped in
  [`CompiledJSR223Script`](../java/org/tentackle/script/jsr/CompiledJSR223Script.java). Hence, the engine **must
  implement `Compilable`** (Groovy, JRuby, Nashorn and GraalJS do); otherwise, compilation fails with a
  `ScriptRuntimeException`.
- **Caching.** With `cached = true`, identical source strings share one compiled unit via a static
  `ConcurrentHashMap<(engine, code), CompiledJSR223Script>` resolved with `computeIfAbsent`; `cached = false` keeps a private
  copy. The compiled unit is held in a `volatile` field guarded by double-checked locking. Since a `CompiledScript`
  is bound to the engine that compiled it, the cache is keyed by engine *and* source code.
- **Compile-time locking.** Compilation *itself* is synchronized on the engine when the script is `threadSafe`,
  because some engines (notably JRuby) throw `ConcurrentModificationException` sporadically while compiling in
  parallel.
- **Execution.** Each run builds a fresh `SimpleBindings` from the supplied
  [`ScriptVariable`](../../../../tentackle-core/src/main/java/org/tentackle/script/ScriptVariable.java)s (translated
  names included) and calls `CompiledScript.eval(bindings)`, which uses them as the engine scope for this run only.
  Hence, unlike the native Ruby provider, no variable survives a run. If the engine is inherently thread-safe — or
  the caller did not request thread safety — `eval` runs unsynchronized; otherwise it synchronizes on the engine.
- Failures (`ScriptException` or runtime) surface as [`ScriptRuntimeException`](../../../../tentackle-core/src/main/java/org/tentackle/script/ScriptRuntimeException.java).

---

## Validation Message Conversion

Validator messages may use the i18n shortcut `@('key', args…)`, which the scripting API rewrites into a real
method call before compilation via a [`ScriptConverter`](../../../../tentackle-core/src/main/java/org/tentackle/script/ScriptConverter.java).
The converters are selected by the language *name* as reported by the engine's `getLanguageName()`, which is
case-sensitive and not necessarily the name the native provider uses. Therefore, this module registers its own
converters for the common engines:

| Converter                                                                                                                | `@MessageScriptConverter` | Engines                                                  |
|--------------------------------------------------------------------------------------------------------------------------|---------------------------|----------------------------------------------------------|
| [`ECMAScriptValidationMessageConverter`](../java/org/tentackle/script/jsr/ECMAScriptValidationMessageConverter.java)     | `ECMAScript`              | JavaScript engines such as GraalJS or standalone Nashorn |
| [`GroovyJSR223ValidationMessageConverter`](../java/org/tentackle/script/jsr/GroovyJSR223ValidationMessageConverter.java) | `Groovy`                  | `groovy-jsr223`                                          |
| [`JRubyValidationMessageConverter`](../java/org/tentackle/script/jsr/JRubyValidationMessageConverter.java)               | `ruby`                    | the `jruby` engine                                       |

Like the native providers' converters, they extend `AbstractScriptValidationMessageConverter` with no behavioral
change — the base converter expresses the validated object through the language's own
`createLocalVariableReference`, which yields `@object` for JRuby. The `Groovy` converter duplicates the one of
`tentackle-script-groovy`, which reports the same name; if both modules are present, the first one found wins, with
the same result. The `jruby` engine, however, reports `ruby`, whereas the native Ruby provider's converter is
registered for `Ruby` and would not apply. For any other engine, the `@(…)` shortcut is left untouched unless a
converter for its language name is provided.

> **Nashorn caveat.** Nashorn was deprecated in Java 11 and removed from the JDK in Java 15. GraalJS or the
> standalone OpenJDK Nashorn are the usual replacements; both report the language name `ECMAScript`.

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
[`Hook`](../java/org/tentackle/script/jsr/service/Hook.java) `ModuleHook` makes the module part of the module
hierarchy, which is what lets its factory override the one of `tentackle-core` (see
[Replacing the Factory](#replacing-the-factory)). The `@Service(ScriptFactory.class)` annotation on
`JSR223ScriptFactory` is turned into a `META-INF/services/org.tentackle.script.ScriptFactory` descriptor, and the
`@MessageScriptConverter`s into `META-INF/mapped-services/` entries, by the `tentackle-maven-plugin` `analyze` goal,
so the factory replacement also works on the plain classpath (subject to the classpath order).

---

## Source Map

| Type                                                                                 | Location                           |
|--------------------------------------------------------------------------------------|------------------------------------|
| `JSR223ScriptFactory` (the `@Service(ScriptFactory.class)` replacement)              | `org.tentackle.script.jsr`         |
| `JSR223Language` (per-engine adapter, thread-safety inference, variable translation) | `org.tentackle.script.jsr`         |
| `JSR223Script` (lazy compile, caching, synchronized compile/eval)                    | `org.tentackle.script.jsr`         |
| `CompiledJSR223Script` (code + `javax.script.CompiledScript`)                        | `org.tentackle.script.jsr`         |
| `ECMAScriptValidationMessageConverter` (`@MessageScriptConverter`)                   | `org.tentackle.script.jsr`         |
| `GroovyJSR223ValidationMessageConverter` (`@MessageScriptConverter`)                 | `org.tentackle.script.jsr`         |
| `JRubyValidationMessageConverter` (`@MessageScriptConverter`)                        | `org.tentackle.script.jsr`         |
| `Hook` (`ModuleHook` service)                                                        | `org.tentackle.script.jsr.service` |

## See Also

- **[Tentackle Scripting — the pluggable scripting language API](../../../../tentackle-core/src/main/docs/scripting.md)** — the factory, `Script`/`ScriptVariable`, she-bang notation, converters and language discovery.
- **[Validation](../../../../tentackle-core/src/main/docs/validation.md)** — the largest consumer of scripting.
- The sibling native providers: [`tentackle-script-groovy`](../../../../tentackle-script-groovy/src/main/docs/groovy.md) (embedded `GroovyClassLoader`) and [`tentackle-script-ruby`](../../../../tentackle-script-ruby/src/main/docs/ruby.md) (native JRuby embed API). Note the two routes to JRuby — the dedicated native module versus this module's `jruby` JSR engine. Prefer the native module, since this provider serializes all JRuby executions on the engine; with both present, select the native one via `rb`.
