# Tentackle Script Groovy — The Groovy Provider

## Overview and Motivation

`tentackle-script-groovy` is one of the pluggable scripting-language providers for Tentackle. Tentackle code never
binds to a concrete scripting engine; it creates and runs scripts through the language-agnostic API in
`org.tentackle.script` (part of **`tentackle-core`**). This module binds that API to
**[Apache Groovy](https://groovy-lang.org/)**, compiling each expression with an embedded `GroovyClassLoader`.

> For the full picture of the scripting API — `ScriptFactory`, `Script`, `ScriptVariable`, `ScriptConverter`, the
> she-bang notation and how a language is discovered and bound — see the
> **[scripting deep-dive](../../../../tentackle-core/src/main/docs/scripting.md)**. This page documents only the
> Groovy-specific provider.

Selecting Groovy is purely a matter of dependencies: put this module on the path and the
[`ScriptFactory`](../../../../tentackle-core/src/main/java/org/tentackle/script/ScriptFactory.java) discovers it
automatically. Groovy is the usual choice for Tentackle's script-backed expressions, most notably the `condition`,
`value` and `message` slots of the [validation framework](../../../../tentackle-core/src/main/docs/validation.md).
There is no built-in default language, though: it is set explicitly (for example, from the application's
`scripting` property), or the application picks the only language available.

```xml
<!-- add Groovy scripting to an application -->
<dependency>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-script-groovy</artifactId>
</dependency>
```

```java
// optionally make Groovy the default language at startup
ScriptFactory.getInstance().setDefaultLanguage("gv");
```

The dependency on `tentackle-core` is declared `optional` in the POM so that adding this provider does not pull
`tentackle-core` transitively into a downstream project that already depends on it. The only other dependency is
`org.apache.groovy:groovy`.

---

## How It Binds

The module ships a single language implementation,
[`GroovyLanguage`](../java/org/tentackle/script/groovy/GroovyLanguage.java), annotated
`@Service(ScriptingLanguage.class)`. The `analyze` goal of the `tentackle-maven-plugin` turns it into
`META-INF/services/org.tentackle.script.ScriptingLanguage`, so when this jar is on the path
`DefaultScriptFactory` discovers it through `ServiceFinder` and registers it — no configuration required.

`GroovyLanguage` reports the canonical name **`Groovy`** and the abbreviations **`Groovy`** and **`gv`** (registered
lower-cased and matched case-insensitively, e.g. in a she-bang prefix). If [`tentackle-script-jsr`](../../../../tentackle-script-jsr/src/main/docs/jsr.md)
is on the path as well, its JSR-223 engines are registered first, and `groovy` then refers to the `groovy-jsr223`
engine (if present), while `gv` still selects this provider. It does not override `createLocalVariableReference`, so a plain variable
name is used verbatim — a Groovy script references the validated bean simply as `object`, e.g.

```
#!gv{ object.amount > 200 }      // explicit Groovy tag
#!{ object.amount > 200 }        // when Groovy is the default language
```

The language owns a **single** `GroovyClassLoader` shared by every script it creates. Its parent is the thread's
context class loader at the time the language is instantiated (i.e., when the factory loads its languages on first
use), or the module's own class loader if there is no context class loader. Hence, scripts can reference the
application classes visible from that class loader.

---

## Compilation and Caching

[`GroovyScript`](../java/org/tentackle/script/groovy/GroovyScript.java) is the `AbstractScript` subclass returned
by the language. It follows the API's uniform *create → (lazily compile) → execute* lifecycle:

- **Lazy compilation.** The source is parsed on first `execute()` (or eagerly when `validate()` is called to
  fail fast on syntax errors). The compiled unit is held in a `volatile` field guarded by double-checked locking.
- **Caching.** When the script was created with `cached = true`, identical source strings share a single compiled
  unit through a static `ConcurrentHashMap<code, CompiledGroovyScript>` resolved with `computeIfAbsent`, so the
  expensive parse happens once per distinct expression. With `cached = false` a private compiled copy is kept and
  not retained in the shared map — appropriate for one-shot scripts.
- Parsing goes through `GroovyClassLoader.parseClass(code)`, which yields a `groovy.lang.Script` subclass. Any
  failure is wrapped as a [`ScriptRuntimeException`](../../../../tentackle-core/src/main/java/org/tentackle/script/ScriptRuntimeException.java)
  whose message includes the offending script.
- The shared cache is never evicted: every distinct cached source string keeps its compiled class loaded for the
  lifetime of the JVM. Use `cached = true` for a bounded set of expressions (validators, model-defined scripts), not
  for generated or user-entered code.

---

## Execution and Thread-Safety

Groovy execution is **inherently thread-safe**, so the `threadSafe` flag needs no extra synchronization in this
provider. Each call to `execute(...)` builds a fresh `groovy.lang.Binding` from the supplied
[`ScriptVariable`](../../../../tentackle-core/src/main/java/org/tentackle/script/ScriptVariable.java)s, creates a
**new** script instance bound to that `Binding`, and runs it. Because no mutable state is shared between runs, the
same `Script` object can be executed from many threads in parallel (exercised by `GroovyThreadingTest`). The result
of the last expression is returned and cast to the caller's expected type. A runtime failure of the script (e.g. an
`ArithmeticException` or a `MissingPropertyException`) is wrapped in a `ScriptRuntimeException`, as required by the
`Script.execute` contract.

[`CompiledGroovyScript`](../java/org/tentackle/script/groovy/CompiledGroovyScript.java) wraps the parsed script
class and is responsible for instantiating it per execution. It scans the class's constructors and:

- prefers a `Script(Binding)` constructor (the normal case for a parsed expression), and
- otherwise falls back to the no-arg constructor plus `setBinding(...)`, logging a warning that asks for the
  missing `Binding` constructor.

This fallback exists so that a developer may supply a **custom `groovy.lang.Script` subclass** as the script body, not
just a bare expression. If neither constructor is present, instantiation fails with a `ScriptRuntimeException`.

---

## Validation Message Conversion

Validator messages may use the i18n shortcut `@('key', args…)`, which the scripting API rewrites into a real
method call before compilation via a [`ScriptConverter`](../../../../tentackle-core/src/main/java/org/tentackle/script/ScriptConverter.java).
The per-language rewrite is located as a service. For Groovy this is
[`GroovyValidationMessageConverter`](../java/org/tentackle/script/groovy/GroovyValidationMessageConverter.java),
annotated `@MessageScriptConverter(GroovyLanguage.NAME)` and registered as a mapped service under
`META-INF/mapped-services/`. The converter is selected by the language *name* (`Groovy`), not by an abbreviation.
It simply extends
`AbstractScriptValidationMessageConverter` with **no overrides** — because Groovy refers to the validated object as
plain `object` (the default variable reference), the generic rewrite is already syntactically correct and nothing
language-specific needs changing. (The Ruby converter is likewise empty: the base converter expresses the object
through each language's own `createLocalVariableReference`, which yields `object` for Groovy and `@object` for
Ruby automatically.) Since the `groovy-jsr223` engine reports the same language name `Groovy`, this
converter also serves Groovy reached through `tentackle-script-jsr`.

---

## Packaging and Modularity

The module is an **automatic module**: it ships without a `module-info.java` and publishes an
`Automatic-Module-Name` of `org.tentackle.script.groovy` via the `maven-jar-plugin`. Service discovery relies on
the `META-INF/services` descriptors generated by the `tentackle-maven-plugin` `analyze` goal, so the provider works
both on the module path and the plain classpath. `tentackle-core` declares `uses
org.tentackle.script.ScriptingLanguage`, which lets the factory resolve this provider under JPMS even as an
automatic module.

---

## Source Map

| Type                                                                | Location                      |
|---------------------------------------------------------------------|-------------------------------|
| `GroovyLanguage` (the `@Service(ScriptingLanguage.class)` provider) | `org.tentackle.script.groovy` |
| `GroovyScript` (lazy compile, caching, execution)                   | `org.tentackle.script.groovy` |
| `CompiledGroovyScript` (parsed class + per-run instantiation)       | `org.tentackle.script.groovy` |
| `GroovyValidationMessageConverter` (`@MessageScriptConverter`)      | `org.tentackle.script.groovy` |

## See Also

- **[Tentackle Scripting — the pluggable scripting language API](../../../../tentackle-core/src/main/docs/scripting.md)** — the factory, `Script`/`ScriptVariable`, she-bang notation, converters and language discovery.
- **[Validation](../../../../tentackle-core/src/main/docs/validation.md)** — the largest consumer of scripting, where Groovy is the default expression language.
- The sibling providers: [`tentackle-script-ruby`](../../../../tentackle-script-ruby/src/main/docs/ruby.md) (native JRuby embed API) and [`tentackle-script-jsr`](../../../../tentackle-script-jsr/src/main/docs/jsr.md) (any JSR-223 engine), both summarized in the [scripting deep-dive](../../../../tentackle-core/src/main/docs/scripting.md#the-implementations).
