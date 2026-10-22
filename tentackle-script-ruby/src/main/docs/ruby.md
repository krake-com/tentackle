# Tentackle Script Ruby — The JRuby Provider

## Overview and Motivation

`tentackle-script-ruby` is one of the pluggable scripting-language providers for Tentackle. Tentackle code never
binds to a concrete scripting engine; it creates and runs scripts through the language-agnostic API in
`org.tentackle.script` (part of **`tentackle-core`**). This module binds that API to **[JRuby](https://www.jruby.org/)**
using JRuby's **native embed API** (`org.jruby.embed.ScriptingContainer`) rather than the generic `javax.script`
bridge.

> For the full picture of the scripting API — `ScriptFactory`, `Script`, `ScriptVariable`, `ScriptConverter`, the
> she-bang notation and how a language is discovered and bound — see the
> **[scripting deep-dive](../../../../tentackle-core/src/main/docs/scripting.md)**. This page documents only the
> Ruby-specific provider.

Selecting Ruby is purely a matter of dependencies: put this module on the path and the
[`ScriptFactory`](../../../../tentackle-core/src/main/java/org/tentackle/script/ScriptFactory.java) discovers it
automatically. It can be used wherever the scripting API is consumed — most notably the `condition`, `value` and
`message` slots of the [validation framework](../../../../tentackle-core/src/main/docs/validation.md).

```xml
<!-- add Ruby scripting to an application -->
<dependency>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-script-ruby</artifactId>
</dependency>
```

```java
// optionally make Ruby the default language at startup
ScriptFactory.getInstance().setDefaultLanguage("rb");
```

The dependency on `tentackle-core` is declared `optional` in the POM so that adding this provider does not pull
`tentackle-core` transitively into a downstream project that already depends on it. The only other dependency is
`org.jruby:jruby`.

> **Two routes to Ruby.** JRuby can also be reached through [`tentackle-script-jsr`](../../../../tentackle-script-jsr/src/main/docs/jsr.md)
> via its `jruby` JSR-223 engine. Prefer **this** dedicated module: it uses the native embed API directly, whereas
> the JSR path treats JRuby as *not* thread-safe (it advertises thread isolation it does not reliably honor) and
> serializes all executions on the engine. Note, however, that with both modules on the path the JSR engines are
> registered first and claim the abbreviations `ruby` and `jruby` (and thus `Ruby`, since abbreviations are
> case-insensitive). This provider then remains reachable only via **`rb`**.

---

## How It Binds

The module ships a single language implementation,
[`RubyLanguage`](../java/org/tentackle/script/ruby/RubyLanguage.java), annotated
`@Service(ScriptingLanguage.class)`. The `analyze` goal of the `tentackle-maven-plugin` turns it into
`META-INF/services/org.tentackle.script.ScriptingLanguage`, so when this jar is on the path
`DefaultScriptFactory` discovers it through `ServiceFinder` and registers it — no configuration required.

`RubyLanguage` reports the canonical name **`Ruby`** and the abbreviations **`Ruby`**, **`jruby`**, **`rb`** and
**`ruby`** (registered lower-cased and matched case-insensitively, e.g. in a she-bang prefix). It owns a **single**
JRuby `ScriptingContainer`, which is shared by every script it creates. The container is created with JRuby's
defaults: `LocalContextScope.SINGLETON` (one Ruby runtime per JVM, shared with any other singleton-scoped container)
and `LocalVariableBehavior.TRANSIENT` (Ruby *local* variables don't survive a run, instance variables do).

### The `@` variable syntax

Unlike Groovy, Ruby cannot reference a bound variable by a bare name. `RubyLanguage` therefore overrides
`createLocalVariableReference(name)` to return `"@" + name`, mapping each [`ScriptVariable`](../../../../tentackle-core/src/main/java/org/tentackle/script/ScriptVariable.java)
onto a Ruby **instance variable**. A script consequently refers to the validated bean as `@object`:

```
#!rb{ @object.amount > 200 }     // explicit Ruby tag
#!{ @object.amount > 200 }       // when Ruby is the default language
```

---

## Compilation and Caching

[`RubyScript`](../java/org/tentackle/script/ruby/RubyScript.java) is the `AbstractScript` subclass returned by the
language. It follows the API's uniform *create → (lazily compile) → execute* lifecycle:

- **Lazy compilation.** The source is parsed on first `execute()` (or eagerly when `validate()` is called to fail
  fast on syntax errors). The compiled unit is held in a `volatile` field guarded by double-checked locking.
- **Caching.** When the script was created with `cached = true`, identical source strings share a single compiled
  unit through a static `ConcurrentHashMap<code, CompiledRubyScript>` resolved with `computeIfAbsent`. With
  `cached = false` a private compiled copy is kept and not retained in the shared map. The shared map is never
  evicted, so caching is meant for a bounded set of expressions.
- Parsing goes through `ScriptingContainer.parse(code)`, which yields an `org.jruby.embed.EmbedEvalUnit` wrapped in
  [`CompiledRubyScript`](../java/org/tentackle/script/ruby/CompiledRubyScript.java) (together with the container and
  the source). Any failure is wrapped as a [`ScriptRuntimeException`](../../../../tentackle-core/src/main/java/org/tentackle/script/ScriptRuntimeException.java)
  whose message includes the offending script.

---

## Execution and Thread-Safety

Unlike Groovy, JRuby execution is **not inherently thread-safe**: the shared `ScriptingContainer` is reused for
every run, and bound variables live in the container's namespace. Therefore, when a script is created with
`threadSafe = true`, `RubyScript.execute(...)` **synchronizes on the container** for the whole bind-and-run
sequence; without the flag it runs unsynchronized (appropriate for single-threaded use, where it avoids the lock).

Since the container is shared by *all* Ruby scripts, the lock only protects scripts that take it: a script created
with `threadSafe = false` that runs concurrently with any other Ruby script interferes with it. As the
`ScriptFactory.createScript` contract demands, if scripts may run in parallel, create *all* of them thread-safe.

Each run binds the supplied variables into the container under their `@`-prefixed names (via the language's
`createLocalVariableReference`), calls `EmbedEvalUnit.run()`, and converts the resulting `IRubyObject` back to Java
with `toJava(...)`; a `null` Ruby result maps to a Java `null`. Any runtime failure is wrapped in a
`ScriptRuntimeException`. The threading behavior is exercised by `RubyThreadingTest`.

Afterward, even if the script failed, the bound variables are **reset to `nil`**, so a later script doesn't see a
stale value bound by an earlier one. The variables are not removed though: `ScriptingContainer.remove(...)` would
leave the instance variable defined in the runtime, and the next run would copy its old value back into the container.
Consequently, `defined?(@name)` still reports `instance-variable` for a variable bound in an earlier run, and the
previous value stays referenced by the runtime until the next run injects the `nil`.

> **Performance note.** The native embed path is correct and fully cached, but Ruby evaluation is markedly slower
> than Groovy (the module's own `RubyTest` runs two orders of magnitude fewer iterations than the Groovy
> equivalent). Choose Ruby for expressiveness, not for hot-path throughput.

---

## Validation Message Conversion

Validator messages may use the i18n shortcut `@('key', args…)`, which the scripting API rewrites into a real
method call before compilation via a [`ScriptConverter`](../../../../tentackle-core/src/main/java/org/tentackle/script/ScriptConverter.java).
The per-language rewrite is located as a service. For Ruby this is
[`RubyValidationMessageConverter`](../java/org/tentackle/script/ruby/RubyValidationMessageConverter.java),
annotated `@MessageScriptConverter(RubyLanguage.NAME)` and registered as a mapped service under
`META-INF/mapped-services/`. The converter is selected by the language *name* (`Ruby`), not by an abbreviation.
Like the Groovy converter, it extends
`AbstractScriptValidationMessageConverter` with **no overrides** — the base converter expresses the validated
object through the language's own `createLocalVariableReference`, so it automatically emits `@object` for Ruby and
the generic rewrite needs no further adaptation. The `jruby` JSR-223 engine reports the language name `ruby`
instead, hence `tentackle-script-jsr` ships its own converter for that route.

---

## Packaging and Modularity

The module is an **automatic module**: it ships without a `module-info.java` and publishes an
`Automatic-Module-Name` of `org.tentackle.script.ruby` via the `maven-jar-plugin`. Service discovery relies on the
`META-INF/services` descriptors generated by the `tentackle-maven-plugin` `analyze` goal, so the provider works
both on the module path and the plain classpath. `tentackle-core` declares `uses
org.tentackle.script.ScriptingLanguage`, which lets the factory resolve this provider under JPMS even as an
automatic module.

---

## Source Map

| Type                                                                                    | Location                    |
|-----------------------------------------------------------------------------------------|-----------------------------|
| `RubyLanguage` (the `@Service(ScriptingLanguage.class)` provider, `@`-variable mapping) | `org.tentackle.script.ruby` |
| `RubyScript` (lazy compile, caching, synchronized execution, variable reset)            | `org.tentackle.script.ruby` |
| `CompiledRubyScript` (container + parsed `EmbedEvalUnit`)                               | `org.tentackle.script.ruby` |
| `RubyValidationMessageConverter` (`@MessageScriptConverter`)                            | `org.tentackle.script.ruby` |

## See Also

- **[Tentackle Scripting — the pluggable scripting language API](../../../../tentackle-core/src/main/docs/scripting.md)** — the factory, `Script`/`ScriptVariable`, she-bang notation, converters and language discovery.
- **[Validation](../../../../tentackle-core/src/main/docs/validation.md)** — the largest consumer of scripting.
- The sibling providers: [`tentackle-script-groovy`](../../../../tentackle-script-groovy/src/main/docs/groovy.md) (embedded `GroovyClassLoader`) and [`tentackle-script-jsr`](../../../../tentackle-script-jsr/src/main/docs/jsr.md) (any JSR-223 engine, including the alternative `jruby` route), summarized in the [scripting deep-dive](../../../../tentackle-core/src/main/docs/scripting.md#the-implementations).
