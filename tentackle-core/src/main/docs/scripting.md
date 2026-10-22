# Tentackle Scripting — The Pluggable Scripting Language API

## Overview and Motivation

Tentackle embeds a small, language-agnostic **scripting abstraction** so that pieces of behavior can be expressed
as text — in source, in a model, in a database column or in an annotation — rather than as compiled Java. The most
visible consumer is the [validation framework](validation.md): a `@NotNull(condition = "...")`, a
`@Greater(value = "...")` or a validator `message` may all carry a script expression. The same machinery backs the
`condition`/`value`/`message` slots of any [`CompoundValue`](../java/org/tentackle/misc/CompoundValue.java).

The design goal is that *the code using a script never needs to know which language it is written in*. A caller
asks the [`ScriptFactory`](../java/org/tentackle/script/ScriptFactory.java) for a [`Script`](../java/org/tentackle/script/Script.java),
supplies named variables, and executes it. Whether the expression is Groovy, Ruby, or a JSR-223 engine such as
GraalJS is decided by a short language tag in the text, and by which implementation jars happen to be on the path.

The API lives in `org.tentackle.script` in **`tentackle-core`** (exported by the module). The concrete languages
ship as separate modules, so an application only pays for what it uses:

| Module                    | Language(s)                            | Engine                                        |
|---------------------------|----------------------------------------|-----------------------------------------------|
| `tentackle-script-groovy` | Groovy                                 | Embedded `GroovyClassLoader`                  |
| `tentackle-script-ruby`   | Ruby (JRuby)                           | JRuby `ScriptingContainer` (native embed API) |
| `tentackle-script-jsr`    | any JSR-223 engine (GraalJS, JRuby, …) | `javax.script`                                |

### Design principles

- **Pluggable via the [service API](../../../../tentackle-common/src/main/docs/services.md).** A language is a
  `@Service(ScriptingLanguage.class)`; the factory discovers all of them through `ServiceFinder` at first use.
  Drop a jar on the path, and its language becomes available — no configuration, no registration code.
- **Uniform lifecycle.** Every script follows the same *create → (lazily compile) → execute* path with the same
  caching and thread-safety contract, regardless of language.
- **Lazy, cached compilation.** Scripts compile on first use (or on demand via `validate()`); identical source can
  share one compiled unit through a per-language cache.
- **Pre-compilation rewriting.** An optional [`ScriptConverter`](../java/org/tentackle/script/ScriptConverter.java)
  can transform the source before it is compiled — used, for instance, to rewrite the validation message i18n
  shortcut into a real method call in the target language.
- **Modular and classpath parity.** Core declares `uses org.tentackle.script.ScriptingLanguage` so providers
  resolve under JPMS too, even when a provider jar is only an automatic module.

---

## The Core API

All types below are in `org.tentackle.script` (`tentackle-core`).

### `ScriptingLanguage` — the SPI

[`ScriptingLanguage`](../java/org/tentackle/script/ScriptingLanguage.java) is the interface every language
implements. It must be annotated `@Service(ScriptingLanguage.class)` so the factory can find it.

```java
public interface ScriptingLanguage {
  String   getName();                       // unique, e.g. "Groovy"
  String[] getAbbreviations();              // tags used in the she-bang, e.g. { "Groovy", "gv" }
  Script   createScript(String code, boolean cached, boolean threadSafe,
                        Function<String, ScriptConverter> converterProvider);
  String   createLocalVariableReference(String name);   // language-specific variable syntax
}
```

- **`getName()`** is the canonical, unique language name.
- **`getAbbreviations()`** are the tags that may appear in the *she-bang* prefix of a script (see below). All are
  matched case-insensitively. The first one is conventionally the canonical name.
- **`createLocalVariableReference(name)`** adapts a plain variable name to the language's syntax. Most languages
  return it unchanged (the default in `AbstractScriptingLanguage`); Ruby, for example, turns `object` into
  `@object`.

[`AbstractScriptingLanguage`](../java/org/tentackle/script/AbstractScriptingLanguage.java) provides the common
boilerplate: identity by name (`equals`/`hashCode`/`toString`), the no-op variable reference, and the
`getEffectiveCode(code, converterProvider)` helper that applies the optional converter before compilation.

### `ScriptFactory` — discovery and creation

[`ScriptFactory`](../java/org/tentackle/script/ScriptFactory.java) is a singleton obtained via
`ScriptFactory.getInstance()` (located through `ServiceFactory`, default
[`DefaultScriptFactory`](../java/org/tentackle/script/DefaultScriptFactory.java)). It maps language abbreviations
to languages and creates scripts:

```java
ScriptFactory factory = ScriptFactory.getInstance();

factory.setDefaultLanguage("gv");                 // or a ScriptingLanguage instance
ScriptingLanguage groovy = factory.getLanguage("gv");
boolean haveRuby = factory.isLanguageAvailable("ruby");
Set<ScriptingLanguage> all = factory.getLanguages();

Script s = factory.createScript("gv",             // abbreviation, null/"" → default language
                                "object.amount > 200",
                                true,             // cached
                                false,            // threadSafe
                                null);            // optional converter provider
```

`getLanguage(abbreviation)` and `createScript(...)` throw [`ScriptRuntimeException`](../java/org/tentackle/script/ScriptRuntimeException.java)
(an unchecked exception) when the language is unknown or the default is undefined; `isLanguageAvailable` is the
non-throwing probe.

**How languages are loaded** (`DefaultScriptFactory.loadLanguages`): the factory asks `ServiceFinder` for every
`ScriptingLanguage` provider, instantiates each one, and registers it under each of its (lower-cased)
abbreviations using `putIfAbsent`. Because the first registration wins, the meaning of an abbreviation is stable
and can be deliberately overridden by load order rather than accidentally clobbered.

There is **no built-in default language**. It is set explicitly via `setDefaultLanguage(...)`; a Tentackle
application does so from its `scripting` property, or picks the only language available if there is exactly one.
Without a default, the `{ … }` shorthand and the empty tag `#!{ … }` fail with a `ScriptRuntimeException`.

### `Script` — a compiled-on-demand expression

[`Script`](../java/org/tentackle/script/Script.java) is the unit of execution:

```java
public interface Script {
  ScriptingLanguage getLanguage();
  boolean isThreadSafe();
  boolean isCached();
  String  getCode();
  void    validate();                              // compile without executing
  <T> T   execute(Set<ScriptVariable> variables);  // null = no variables
  <T> T   execute(ScriptVariable... variables);
}
```

- The script is **compiled lazily on first execution**. Call `validate()` to force compilation (e.g., to fail fast
  on a syntax error) without running it.
- `execute(...)` binds the supplied [`ScriptVariable`](../java/org/tentackle/script/ScriptVariable.java)s into the
  language's namespace and returns the evaluated result, cast to the caller's expected type `T`. Any failure
  surfaces as `ScriptRuntimeException`.
- The varargs overload throws `IllegalArgumentException` on duplicate variable names.

[`AbstractScript`](../java/org/tentackle/script/AbstractScript.java) holds the language, code, `cached` and
`threadSafe` flags shared by all implementations and renders a readable `toString()` such as
`#!Groovy{ object.amount > 200 } (cached)`.

### `ScriptVariable` — named inputs

A [`ScriptVariable`](../java/org/tentackle/script/ScriptVariable.java) is a simple `(name, value)` pair. Its
identity is the **name only** (`equals`/`hashCode`), so a `Set<ScriptVariable>` can never hold two variables of the
same name, and the varargs `execute(ScriptVariable...)` rejects duplicates.

### `ScriptConverter` — pre-compilation rewriting

[`ScriptConverter`](../java/org/tentackle/script/ScriptConverter.java) is a functional interface,
`String convert(String code, ScriptingLanguage language)`. The factory and language accept a
`Function<String, ScriptConverter>` *converter provider*: given a language **name**, it returns the converter to
apply (or `null`). `AbstractScriptingLanguage.getEffectiveCode` runs it just before compilation, so the rewrite is
language-aware.

The canonical use is in validation. [`AbstractScriptValidationMessageConverter`](../java/org/tentackle/validate/validator/AbstractScriptValidationMessageConverter.java)
rewrites the message i18n shortcut `@('key', args…)` into
`ValidationUtilities.getInstance().format(object, 'key', args…)`, expressing `object` through the language's own
`createLocalVariableReference` so the generated call is syntactically correct in every language. The per-language
converters are themselves located as services: the Groovy, Ruby and JSR-223 modules register `@MessageScriptConverter` implementations
([`GroovyValidationMessageConverter`](../../../../tentackle-script-groovy/src/main/java/org/tentackle/script/groovy/GroovyValidationMessageConverter.java),
[`RubyValidationMessageConverter`](../../../../tentackle-script-ruby/src/main/java/org/tentackle/script/ruby/RubyValidationMessageConverter.java),
and [`ECMAScriptValidationMessageConverter`](../../../../tentackle-script-jsr/src/main/java/org/tentackle/script/jsr/ECMAScriptValidationMessageConverter.java)).

---

## The She-Bang Notation

Scripts embedded in text declare their language with a Unix-like *she-bang* prefix. The text is parsed by
[`CompoundValue`](../java/org/tentackle/misc/CompoundValue.java):

```
#!gv{ object.amount > 200 }       → language "gv" (Groovy), code "object.amount > 200"
#!ruby{ @object.amount > 200 }    → language "ruby"
#!{ object.amount > 200 }         → default language
{ object.amount > 200 }           → default language (shorthand)
```

The tag between `#!` and `{` is matched against every language's abbreviations (case-insensitive); an empty tag
means the default language. The code is everything between the braces. `CompoundValue` then calls
`ScriptFactory.createScript(language, code, …)` and wraps any `ScriptRuntimeException` as an
`IllegalArgumentException` describing the offending text. (A `CompoundValue` is more than a script — it can also be
a constant `\literal`, a property reference, or a negated reference `!$path` — but the `#!…{…}` / `{…}`
forms are the script case. See **[CompoundValue](compound-value.md)** for all four forms and how they are
classified.)

---

## Caching and Thread-Safety

Two boolean flags, threaded through every `createScript` call, govern how a script behaves at scale. Both are
documented on `ScriptFactory.createScript`:

- **`cached`** — when `true`, identical source code shares a single compiled unit. Each language implementation
  keeps a `ConcurrentHashMap<code, compiled>` and resolves it with `computeIfAbsent`, so the expensive
  compile/parse happens once per distinct script. Use it for scripts that run repeatedly (validators, mappings);
  leave it `false` for one-shot scripts so the cache does not retain them.
- **`threadSafe`** — when `true`, the script guarantees correct execution from multiple threads in parallel. How
  that is achieved is language-specific (see below). When in doubt, pass `true`.

The lazy-compile pattern is identical across implementations: a `volatile` reference to the compiled script with
double-checked locking, pulling from (or populating) the shared cache when `cached`, or compiling a private copy
otherwise.

---

## The Implementations

### Groovy — `tentackle-script-groovy`

[`GroovyLanguage`](../../../../tentackle-script-groovy/src/main/java/org/tentackle/script/groovy/GroovyLanguage.java)
(name `Groovy`, abbreviations `Groovy` and `gv`) owns a single
`GroovyClassLoader`, whose parent is the thread's context class loader (or the module's own class loader, if none). Compilation parses the source into a `groovy.lang.Script` subclass
([`CompiledGroovyScript`](../../../../tentackle-script-groovy/src/main/java/org/tentackle/script/groovy/CompiledGroovyScript.java)),
cached by source string.

Execution is **inherently thread-safe**: each run creates a fresh script instance with its own `Binding`, so the
`threadSafe` flag needs no extra synchronization. Variables become entries in the `Binding`. The compiled wrapper
scans the generated class's constructors and prefers a `Script(Binding)` constructor, falling back to the
no-arg constructor plus `setBinding(...)` (with a warning) — this lets developers supply a custom `groovy.lang.Script`
subclass as the script body. Runtime failures are wrapped in a `ScriptRuntimeException`. Groovy is the usual choice
for validation expressions.

See the **[Groovy provider deep-dive](../../../../tentackle-script-groovy/src/main/docs/groovy.md)** for the full
details of this module.

### Ruby (JRuby) — `tentackle-script-ruby`

[`RubyLanguage`](../../../../tentackle-script-ruby/src/main/java/org/tentackle/script/ruby/RubyLanguage.java)
(name `Ruby`, abbreviations `Ruby`, `jruby`, `rb`, `ruby`) uses the **native JRuby embed API** — a single
`ScriptingContainer`. Compilation produces an `EmbedEvalUnit`
([`CompiledRubyScript`](../../../../tentackle-script-ruby/src/main/java/org/tentackle/script/ruby/CompiledRubyScript.java)).

Because the shared container is not safe for parallel use, [`RubyScript`](../../../../tentackle-script-ruby/src/main/java/org/tentackle/script/ruby/RubyScript.java)
**synchronizes on the container** when `threadSafe` is set. Ruby variables use the `@name` instance-variable
syntax, so `createLocalVariableReference` prepends `@`; results are converted back to Java via
`IRubyObject.toJava(...)`. After each run, the bound variables are reset to `nil`, so later scripts don't see stale
values.

See the **[Ruby provider deep-dive](../../../../tentackle-script-ruby/src/main/docs/ruby.md)** for the full details
of this module.

### JSR-223 — `tentackle-script-jsr`

This module bridges the API to any `javax.script` engine. [`JSR223Language`](../../../../tentackle-script-jsr/src/main/java/org/tentackle/script/jsr/JSR223Language.java)
wraps a `ScriptEngineFactory`; its name and abbreviations come straight from the engine
(`getLanguageName()` / `getNames()`). Compilation goes through the engine's `Compilable` interface, which the
engine must therefore implement
([`CompiledJSR223Script`](../../../../tentackle-script-jsr/src/main/java/org/tentackle/script/jsr/CompiledJSR223Script.java)).

What makes this module different is that it replaces the factory itself:
[`JSR223ScriptFactory`](../../../../tentackle-script-jsr/src/main/java/org/tentackle/script/jsr/JSR223ScriptFactory.java)
is also a `@Service(ScriptFactory.class)` and extends `DefaultScriptFactory`. Its `loadLanguages()` enumerates
**every engine** registered with the `ScriptEngineManager`, then calls `super.loadLanguages()` to add the
annotation-based `@Service` languages (without overriding abbreviations already claimed by a JSR engine). So with
this module present, a single application can mix JSR engines and the native Groovy/Ruby providers — with the JSR
engines owning the shared abbreviations such as `groovy` and `ruby`, while `gv` and `rb` still select the native
providers.

Thread-safety is inferred per engine (`JSR223Language.isEngineThreadSafe`): an engine declaring the `THREADING`
parameter and the standalone Nashorn are treated as safe; **JRuby via
JSR-223 is treated as *not* safe** (it advertises thread isolation it does not reliably honor), so the JSR Ruby
path synchronizes on the engine for both compile and eval. Some engines need variable-name translation too — the
JRuby engine again gets the `@name` prefix. Note the two routes to Ruby: the dedicated `tentackle-script-ruby`
(native embed API) and this JSR path (the `jruby` JSR engine); prefer the dedicated module when you want JRuby.

Message converters are selected by the engine's language name, so the module ships its own for `ECMAScript`,
`Groovy` and `ruby` (the JRuby engine's name, which differs from the native provider's `Ruby`).

> **Nashorn caveat.** Nashorn was removed from the JDK in Java 15. GraalJS or the standalone OpenJDK Nashorn are the
> usual replacements; both report the language name `ECMAScript`.

See the **[JSR-223 provider deep-dive](../../../../tentackle-script-jsr/src/main/docs/jsr.md)** for the full details
of this module.

---

## Packaging and Modularity

`tentackle-core` exports `org.tentackle.script` and declares `uses org.tentackle.script.ScriptingLanguage` in its
`module-info`, so providers are resolvable under JPMS even when they are automatic modules.

The provider jars register their services through the `@Service` / `@MappedService` annotations, which the
`analyze` goal of the `tentackle-maven-plugin` turns into `META-INF/services/...` and `META-INF/mapped-services/...`
descriptors at build time (e.g.
`META-INF/services/org.tentackle.script.ScriptingLanguage` → `org.tentackle.script.groovy.GroovyLanguage`). This
keeps the providers usable both on the module path and the plain classpath:

- **`tentackle-script-groovy`** and **`tentackle-script-ruby`** ship without a `module-info` (automatic modules),
  relying on the generated `META-INF/services` descriptors discovered via `ServiceFinder`.
- **`tentackle-script-jsr`** is fully modularized (`module org.tentackle.script.jsr`, `requires transitive
  java.scripting`) and provides a `ModuleHook`, which places it above `tentackle-core` in the module hierarchy, so
  that its `ScriptFactory` overrides the default one.

To add scripting to an application, declare the desired implementation module(s) as dependencies and, unless
exactly one language is present, set the default language — via the application's `scripting` property or at
startup:

```java
ScriptFactory.getInstance().setDefaultLanguage("gv");   // Groovy as default
```

To implement a **new language**, extend `AbstractScriptingLanguage` (returning a name and abbreviations, and
creating an `AbstractScript` subclass that compiles and executes the code), annotate it
`@Service(ScriptingLanguage.class)`, and put the jar on the path — the factory does the rest.

---

## Where Scripting Is Used

- **[Validation](validation.md)** — the largest consumer: `condition`, `value` and `message` parameters of the
  validator annotations are script-backed `CompoundValue`s.
- **[`CompoundValue`](compound-value.md)** — the general "constant / reference / script" value abstraction used
  wherever a configurable expression is needed, and the parser of the she-bang notation.

## Source Map

| Type                                                                                            | Location                                                                                                                 |
|-------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| `ScriptingLanguage`, `ScriptFactory`, `Script`, `ScriptVariable`, `ScriptConverter`             | `tentackle-core` · `org.tentackle.script`                                                                                |
| `AbstractScript`, `AbstractScriptingLanguage`, `DefaultScriptFactory`, `ScriptRuntimeException` | `tentackle-core` · `org.tentackle.script`                                                                                |
| She-bang parsing                                                                                | [`CompoundValue`](compound-value.md) · `tentackle-core` · `org.tentackle.misc`                                           |
| Groovy implementation                                                                           | [`tentackle-script-groovy`](../../../../tentackle-script-groovy/src/main/docs/groovy.md) · `org.tentackle.script.groovy` |
| Ruby implementation                                                                             | [`tentackle-script-ruby`](../../../../tentackle-script-ruby/src/main/docs/ruby.md) · `org.tentackle.script.ruby`         |
| JSR-223 implementation                                                                          | [`tentackle-script-jsr`](../../../../tentackle-script-jsr/src/main/docs/jsr.md) · `org.tentackle.script.jsr`             |
