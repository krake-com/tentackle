# CompoundValue — Objects From Strings

## Overview and Motivation

Java annotations may only carry compile-time constants. That is a hard limit whenever a declaration wants to say
something dynamic: *"this field is mandatory — but only when the invoice has been printed"*, or *"this amount must be
greater than the customer's credit limit"*. Neither the condition nor the operand is a constant, yet both belong in
the annotation, next to the thing they describe.

[`CompoundValue`](../java/org/tentackle/misc/CompoundValue.java) (`org.tentackle.misc`, **tentackle-core**) closes
that gap. It turns a single `String` into a value that is evaluated at runtime, and it decides *how* to evaluate it
from the string's own shape:

```java
new CompoundValue("100", Integer.class).getValue()              // → Integer 100
new CompoundValue("$sayHello").getValue(this)                   // → this.sayHello()
new CompoundValue("!$thisIsFalse").getValue(this)               // → !this.thisIsFalse()
new CompoundValue("$org.tentackle.common.DateHelper.now").getValue()   // → DateHelper.now()
new CompoundValue("#!{ 200 > 100 }").getValue()                 // → Boolean.TRUE (script)
```

The caller writes one string and gets one object back. Whether that string was a literal, a property path or a Groovy
expression is the `CompoundValue`'s business, not the caller's — which is exactly what makes a parameter like
`condition="..."` usable in an annotation.

Its largest consumer by far is the [validation framework](validation.md), where the `value`, `condition` and
`message` parameters of *every* validator annotation are `CompoundValue`s. The
[model definition DSL](../../../../tentackle-model/src/main/docs/model-definition.md) inherits the same syntax,
because model validation lines are compiled into those annotations.

---

## The Four Types

A `CompoundValue` is classified once, at construction, into one of four
[`Type`](../java/org/tentackle/misc/CompoundValue.java)s. The classification is purely syntactic — it is driven by
the leading characters of the text:

| `Type`             | Written as                     | Evaluates to                                          |
|--------------------|--------------------------------|-------------------------------------------------------|
| `CONSTANT`         | `100`, `\$literal`             | the text, converted to the requested type             |
| `REFERENCE`        | `$grandTotal`, `!$customer.valid` | a getter/field path on the parent object, optionally negated |
| `STATIC_REFERENCE` | `$org.tentackle.common.DateHelper.now` | a static method/field on a class, optionally negated  |
| `SCRIPT`           | `#!gv{ object.amount > 200 }`, `{ … }` | the result of a script expression                |

`getType()` reports the outcome, and `toString()` renders it — `[SCRIPT] #!gv{ object.amount > 200 }` — which makes
the classification easy to confirm in a debugger or a log line.

### How the text is classified

The constructor tests the text in this order, and the **first** match wins:

1. **Leading backslash** → `CONSTANT`. The backslash is stripped; the rest is the literal. This is the escape hatch
   for constants that would otherwise be mistaken for something else: `"\$string"` is the constant `$string`, not a
   reference.
2. **A `{` at index 0, or a `#!` prefix with a `{` after it — plus a trailing `}`** → `SCRIPT`.
3. **A leading `$`** → `REFERENCE` (later refined to `STATIC_REFERENCE`); **a leading `!`** → the same, negated, and
   the `!` *must* be followed by `$`.
4. **Anything else** → `CONSTANT`.

An empty or `null` text throws `IllegalArgumentException`, as does a marker with nothing behind it (`"$"`, `"!$"`,
`"\"`).

Because classification happens once, in the constructor, all the parsing and script-compilation cost is paid at
construction time; `getValue()` only evaluates.

---

## Constants

A constant is stored as text and converted to the target class on first `getValue()`, then **cached** for the
lifetime of the `CompoundValue` — so the conversion happens once, no matter how often the value is read.

The conversion is delegated to [`ObjectUtilities.convert(clazz, value)`](../java/org/tentackle/misc/ObjectUtilities.java),
which resolves in this order:

1. **Already assignable** → returned unchanged.
2. **A registered converter** → the built-in set covers `String`, all primitives and their wrappers, `BigDecimal`,
   `BigInteger`, Tentackle's `BMoney`/`DMoney`, and the whole date/time family (`java.sql.Timestamp`/`Time`/`Date`,
   `java.util.Date`, Tentackle's `Timestamp`/`Time`/`Date`, `LocalDate`, `LocalTime`, `LocalDateTime`,
   `OffsetTime`, `OffsetDateTime`, `ZonedDateTime`, `Instant`).
3. **An enum** → `Enum.valueOf`, with a convenience twist: a lowercase camel-case name is first translated to
   the usual constant spelling, so `oneTwo` finds `ONE_TWO`.
4. **`Class.class`** → `Class.forName` against the thread's context class loader.
5. **A public static `valueOf(…)`** on the target class, searched along the value's own class hierarchy.

Anything else raises `IllegalArgumentException` naming both types (`unsupported conversion: …`).

`ObjectUtilities` is itself a `@Service` singleton, so an application that wants `@Greater("2 weeks")` to work
against its own datatype simply extends it, registers a converter with `setConverter(…)`, and replaces the service —
see the [service API](../../../../tentackle-common/src/main/docs/services.md).

> **The `clazz` is mandatory for constants.** It is what the text gets converted *to*. The constructor enforces this
> only for the backslash-escaped form; for a plain constant such as `new CompoundValue("100")` the omission surfaces
> later, as a `NullPointerException` from `convert`, when the value is first read. Pass the class.

---

## References

A reference is a dotted path evaluated with
[`ReflectionHelper.getValueByPath(root, path)`](../java/org/tentackle/reflect/ReflectionHelper.java) against the
**parent object** handed to `getValue(parentObject)`. Passing no parent object to a `REFERENCE` is an
`IllegalArgumentException`.

```java
"$grandTotal"                  // parent.getGrandTotal()
"$customer.name"               // parent.getCustomer().getName()
"!$customer.customerNoValid"   // !parent.getCustomer().isCustomerNoValid()
```

**Each path element resolves in a fixed order**: a public no-arg method named exactly like the element, then `isXxx`,
then `getXxx`, and finally a field of that name. A trailing `()` is tolerated and stripped, so `$sayHello()` and
`$sayHello` mean the same thing. Non-public members are retried with `setAccessible(true)`, which under JPMS
requires the declaring package to be `opens` to `org.tentackle.core`.

**A `null` along the path yields `null`** rather than an NPE — the path simply stops. Append an asterisk to demand
the opposite: `"$customer.name*"` throws if `customer` is null. Use it when a silent `null` would mask a bug rather
than express "not applicable".

**Negation** (`!$`) applies to `REFERENCE` and `STATIC_REFERENCE` alike, and requires a `Boolean` — anything else is
an `IllegalArgumentException`. Note the asymmetry: `!` negates, but there is no `!` for the constant or script forms
(a script negates itself).

### Static references

The distinction between an instance and a static reference is **not** spelled out by the author; it is inferred from
capitalization. While tokenizing the dotted path, the first element whose first character is **uppercase** is taken
as the end of a class name: everything up to and including it becomes the class, the remainder becomes the path
evaluated against that class's static member.

```
"$org.tentackle.common.DateHelper.now"
     └──────── class ────────┘ └─┘ static member  → DateHelper.now()
```

Only the *first* element after the class name is resolved statically; the rest continues as an ordinary instance
path against whatever that returned. A static reference needs no parent object.

> **The uppercase rule has teeth.** Any uppercase path element triggers class interpretation — a property path such
> as `$customer.Name` will be read as the class `customer.Name`, not as a getter. Keep reference paths in the usual
> Java property spelling. Equally, the class name must be **fully qualified** (`Class.forName` gets exactly what was
> parsed), and it must be followed by a member: `"$com.acme.Foo"` on its own has nothing to evaluate and fails.

---

## Scripts

The third form embeds a [script](scripting.md) using a Unix-like *she-bang* prefix. `CompoundValue` is where that
notation is actually parsed:

```
#!groovy{ object.amount > 200 }    → language "groovy", code "object.amount > 200"
#!gv{ object.amount > 200 }        → the same, via Groovy's short abbreviation
#!ruby{ @object.amount > 200 }     → JRuby
#!{ object.amount > 200 }          → the default language
{ object.amount > 200 }            → the default language (shorthand)
```

The tag between `#!` and `{` is matched case-insensitively against the abbreviations of every registered
[`ScriptingLanguage`](../java/org/tentackle/script/ScriptingLanguage.java); an empty tag selects the default
language, which an application sets once at startup:

```java
ScriptFactory.getInstance().setDefaultLanguage("groovy");
```

The code is everything between the braces. `CompoundValue` hands it to `ScriptFactory.createScript(…)` **in the
constructor**, so an unknown language or a malformed script is reported immediately as an `IllegalArgumentException`
(`creating script '…' failed`) that names the offending text — the underlying `ScriptRuntimeException` is the cause.
Compilation itself is lazy; see [Fail-Fast](#fail-fast-compiling-scripts-before-production) for forcing it.

### The `object` variable

If a parent object is passed to `getValue(parentObject, …)`, the script sees it as the variable named `object`
(`ValidationContext.VAR_OBJECT`) — spelled `@object` in Ruby, because each language adapts variable syntax through
`createLocalVariableReference`. This is what makes the Groovy and Ruby forms above equivalent:

```java
new CompoundValue("#!gv{ 'the method says ' + object.sayHello() }").getValue(this);
new CompoundValue("#!rb{ 'the method says ' + @object.sayHello() }").getValue(this);
```

Additional variables are supplied as [`ScriptVariable`](../java/org/tentackle/script/ScriptVariable.java)s, either
as a `Set` or as varargs:

```java
cv.getValue(parent, new ScriptVariable("limit", limit), new ScriptVariable("today", LocalDate.now()));
cv.getValue(Set.of(new ScriptVariable("limit", limit)));    // no parent object
```

A `ScriptVariable`'s identity is its **name alone**, so duplicates are rejected: the varargs overloads throw
`IllegalArgumentException`, and a caller-supplied variable already named `object` keeps its own value — the parent
object does not overwrite it. The set you pass is not required to be mutable; an immutable one is copied when the
parent variable has to be added.

Variables are ignored by the non-script types. Passing them to a constant or a reference is harmless, which is what
lets a caller treat all four types uniformly.

### Type conversion of script results

If a `clazz` was given, the script's result is passed through the same `ObjectUtilities.convert` as a constant. This
is how a script can feed a typed slot without the author thinking about it:

```java
new CompoundValue("#!{ 123.0 + 101.0 }", String.class).getValue()   // → "224.0", not 224.0d
```

With `clazz == null` the script's natural return type survives untouched. Any failure during execution — including
a conversion failure — is wrapped as `IllegalArgumentException` (`evaluating script '…' failed`).

### Caching and thread-safety

The two script flags are passed straight through to the `ScriptFactory` and are meaningful only for `SCRIPT`:

- **`scriptCached`** — identical source shares one compiled unit, per language. Set it for anything evaluated more
  than once; leave it off for one-shot scripts so the cache does not retain them.
- **`scriptThreadSafe`** — the script may be executed from several threads in parallel. How that is achieved is the
  language's business (Groovy is inherently safe; JRuby synchronizes on its container).

Both default to `false` in the convenience constructors — and both are set to `true` for every validator (see
below), which is the configuration nearly all framework code runs with. Details are in
[Scripting](scripting.md#caching-and-thread-safety).

---

## The API

```java
// full control
CompoundValue(String text, Class<?> clazz, boolean scriptCached, boolean scriptThreadSafe,
              Function<String, ScriptConverter> scriptConverterProvider)

CompoundValue(String text, Class<?> clazz)   // uncached, not thread-safe, no converter
CompoundValue(String text)                   // ... and no target type
```

`getValue` comes in four shapes, all funnelling into the first:

| Overload                                              | Use when                                        |
|-------------------------------------------------------|-------------------------------------------------|
| `getValue(Object parentObject, Set<ScriptVariable>)`  | the general case                                |
| `getValue(Object parentObject, ScriptVariable...)`    | a handful of variables, written inline          |
| `getValue(Set<ScriptVariable>)` / `getValue(ScriptVariable...)` | no parent object (constants, static refs, self-contained scripts) |

Everything about the classification is introspectable after construction — `getText()`, `getType()`, `getClazz()`,
`getScript()`, `getReference()`, `getStaticClassName()`, `isNegate()`, `getConstantValue()`, `isScriptCached()`,
`isScriptThreadSafe()`, `getScriptConverterProvider()`. Callers use this to specialize: `PatternImpl`, for example,
asks `getType() == CONSTANT` to decide whether a regular expression can be pre-compiled once or must be rebuilt per
validation.

`validate()` compiles the script (and does nothing for the other types) — the fail-fast hook described below.

### Errors are uniform

Every failure mode — bad syntax, missing parent object, unresolvable path, unknown language, failed conversion,
script exception — surfaces as an **`IllegalArgumentException`**, with the original cause attached. Construction-time
problems (syntax, unknown language) are separated from evaluation-time problems (`null` parent, broken path) simply
by *when* they are thrown.

---

## Integration With the Validation API

The [validation framework](validation.md) is where `CompoundValue` earns its keep. Three parameters of every
validator annotation are compound values rather than plain strings:

```java
@NotNull(condition = "$slave")
@Greater(value = "0", condition = "{ object.printed != null }")
@True(condition = "$textValid", value = "$percentModulo10", message = "not divideable by 10")
@NotNull(condition = "{ object.objectClassId == 0 }", message = "{ @('missing object classname') }")
```

| Parameter   | Target type | Meaning                                                                             |
|-------------|-------------|-------------------------------------------------------------------------------------|
| `condition` | `Boolean`   | gates whether the constraint applies at all; a non-`Boolean` result is a `ValidationRuntimeException` |
| `value`     | the element's type | the comparison operand, or a computed replacement for the element under test  |
| `message`   | `String`    | the diagnostic text; empty means "use the validator's default message"              |

### `ValidatorCompoundValueFactory`

Validators never call `new CompoundValue(…)` directly. They go through
[`ValidatorCompoundValueFactory`](../java/org/tentackle/validate/ValidatorCompoundValueFactory.java), a `@Service`
singleton with one method per parameter:

```java
CompoundValue createValueParameter(String value, Class<?> clazz);
CompoundValue createConditionParameter(String condition);   // clazz = Boolean.class
CompoundValue createMessageParameter(String message);       // clazz = String.class
```

[`DefaultValidatorCompoundValueFactory`](../java/org/tentackle/validate/DefaultValidatorCompoundValueFactory.java)
creates all three **cached and thread-safe** — validators are shared singletons evaluated from many threads, so this
is not optional. The factory's real job, though, is to inject the right
[`ScriptConverter`](../java/org/tentackle/script/ScriptConverter.java) provider, and this is where `value`/`condition`
part ways with `message`:

- `value` and `condition` get the **validation** converters (`@ValidationScriptConverter`).
- `message` gets the **message** converters (`@MessageScriptConverter`).

Both maps are built at construction by asking `ServiceFinder` for every registered converter, keyed by language name.
Because the provider is a `Function<String, ScriptConverter>`, the converter is chosen by the language the script
actually declares — so a Groovy message and a Ruby message in the same class each get their own rewriter.

Being a service, the whole factory is replaceable: an application that wants different defaults, its own converters,
or a house dialect only replaces `ValidatorCompoundValueFactory`.

### Script variables in validation

For a validator's scripts, `AbstractValidator.createScriptVariables(ctx)` exposes the whole
[`ValidationContext`](../java/org/tentackle/validate/ValidationContext.java) as named variables:

| Variable  | Constant           | Bound to                                                       |
|-----------|--------------------|-----------------------------------------------------------------|
| `object`  | `VAR_OBJECT`       | the **parent** object — the one declaring the validated element |
| `value`   | `VAR_VALUE`        | the element under test (a field's value)                        |
| `clazz`   | `VAR_CLASS`        | the parent's effective class (proxies unwrapped)                |
| `context` | `VAR_CONTEXT`      | the `ValidationContext` itself                                  |
| `scope`   | `VAR_SCOPE`        | the effective `ValidationScope` of this run                     |
| `path`    | `VAR_PATH`         | the dotted validation path, e.g. `invoice.customer.name`        |

Note the pairing that catches newcomers out: **`object` is the parent, `value` is the field**. It is consistent
between the two worlds, though — `object` is bound to `getParentObject()` exactly as the `$reference` form resolves
against the parent object, so `condition = "$slave"` and `condition = "{ object.slave }"` mean the same thing. The
first is cheaper; the second can express anything.

### Localized messages: the `@(…)` shortcut

Inside a `message` script, `@('key', args…)` is not script syntax — it is rewritten *before compilation* by
[`AbstractScriptValidationMessageConverter`](../java/org/tentackle/validate/validator/AbstractScriptValidationMessageConverter.java)
into a real call:

```
message = "{ @('missing object classname') }"
   ↓ rewritten to
ValidationUtilities.getInstance().format(object, 'missing object classname')
```

The `object` in the generated call is emitted through the language's own `createLocalVariableReference`, so the
rewrite stays valid in Groovy, Ruby and JSR-223 alike. `ValidationUtilities.format` then walks up from the validated
class's package looking for a `ValidationBundle` and runs the result through `MessageFormat` — giving
`message = "{ @('{0}_is_wrong', value) }"` a per-package, locale-aware, parameterized message for almost no
boilerplate. See [Validation](validation.md#localized-messages--the-validationbundle-lookup).

`createMessageParameter` guards this path explicitly: if the message contains the i18n intro but no message
converter is registered for the script's language, it throws `ScriptRuntimeException` at construction, rather than
letting an unrewritten `@(…)` reach the compiler as a syntax error.

### Lazy creation and caching in `AbstractValidator`

[`AbstractValidator`](../java/org/tentackle/validate/validator/AbstractValidator.java) holds the three compound
values in `volatile` fields, created on first use under double-checked locking:

```java
protected CompoundValue getConditionParameter() {
  CompoundValue localPar = conditionParameter;
  if (localPar == null && !getCondition().isEmpty()) {
    synchronized (this) {
      localPar = conditionParameter;
      if (localPar == null) {
        localPar = conditionParameter = ValidatorCompoundValueFactory.getInstance().createConditionParameter(getCondition());
      }
    }
  }
  return localPar;
}
```

An annotation that omits the parameter never builds a `CompoundValue` at all — the empty string is the signal, and
`getConditionParameter()` returns `null`, which `isConditionValid` reads as "always applies". So the cost of the
machinery is only paid by the constraints that actually use it.

The `value` parameter carries one extra wrinkle. Its target type is the type of the element being validated, which
is known only from the `ValidationContext` — and a single validator instance could, in principle, be applied to
elements of different types. So `getValueParameter(clazz)` starts with a single cached `CompoundValue` and, if it
ever sees a different class, promotes itself to a `ConcurrentHashMap<Class<?>, CompoundValue>` keyed by type. The
common case stays a single field; the rare case stays correct.

> **Validators must stay stateless.** These caches are the deliberate exception: they are derived from the immutable
> annotation, not from any one validation. Since `CompoundValue` itself keeps no per-evaluation state — everything
> arrives through `getValue`'s parameters — sharing one instance across threads and objects is safe.

---

## Fail-Fast: Compiling Scripts Before Production

Scripts compile lazily, on first execution. For a validation script guarding a rare branch, "first execution" might
be months after deployment — a typo would then surface as a runtime failure in front of a user. `validate()` exists
to prevent that, and two mechanisms call it:

**At build time**, the [check plugin](../../../../tentackle-check-maven-plugin/src/main/docs/tentackle-check-maven-plugin.md)'s
`CheckValidationsMojo` walks every validator annotation in the project and builds each `value`, `condition` and
`message` compound value through the factory. Conditions and values are compiled via `script.validate()`; messages
go one step further and are actually **evaluated** (`cv.getValue(enclosingClass)`), which resolves the i18n keys too
— so a missing translation fails the build, reported as a plain "missing resource" line rather than a stack trace.

**At server startup**, `AbstractServerApplication.validateValidators()` loads the validators of every PDO class and
calls `Validator.validate()` on each, which forces all three compound values into existence and compiles their
scripts. A broken script fails the boot, not a request. It also warms the script cache, so the first real validation
pays no compile cost.

---

## Usage in the Model Definition DSL

Validation lines in a [model definition](../../../../tentackle-model/src/main/docs/model-definition.md) are compiled
into validator annotations, so the same syntax applies unchanged:

```
# conditional with method reference
uplink: @NotNull(condition="$slave")

# script condition, constant value
total: @NotNull(scope=PersistenceScope.class),
       @Greater(value="0", scope=PersistenceScope.class, condition="{ object.printed != null }")
```

Note `"{ … }"` rather than `"#!groovy{ … }"`: the brace shorthand for the default language keeps model lines
readable, and is the conventional spelling throughout the framework's own models.

---

## Choosing a Type

The four types are ordered by cost, and the cheapest one that expresses the rule is the right one:

| Prefer            | When                                                                                        |
|-------------------|---------------------------------------------------------------------------------------------|
| a **constant**    | the operand really is fixed — `@Greater("0")`. Converted once, then free.                   |
| a **reference**   | the rule is "ask this property" — `condition="$slave"`. Reflection only, no scripting engine on the path, nothing to compile, and it works with no scripting module on the classpath at all. |
| a **script**      | the rule needs logic a path cannot express — comparisons, arithmetic, null checks, `&&`.    |
| a **static reference** | the operand is a well-known global, typically a clock — `$org.tentackle.common.DateHelper.now`. |

A script pulls in a scripting provider and a compile step; a reference does not. `condition="{ object.slave }"` and
`condition="$slave"` produce identical results, and the reference is the better citizen of the two. Reach for a
script when the condition earns it.

---

## Pitfalls

- **A plain constant with no `clazz` fails at `getValue`, not at construction.** Only the backslash form checks
  eagerly.
- **An uppercase element makes a path static.** `$customer.Name` is read as the class `customer.Name`. Stick to
  Java property spelling.
- **A static class name must be fully qualified and followed by a member.** `Class.forName` receives literally what
  was parsed.
- **`!` only negates references**, must be written `!$`, and demands a `Boolean`.
- **A `null` in a reference path yields `null` silently.** Append `*` when that would hide a bug.
- **`object` is the parent, `value` is the element** — in validation scripts, the field under test is `value`.
- **A `{` at index 0 always means a script.** A constant that starts with a brace needs the backslash escape.
- **Non-public members need `opens`** under JPMS for `setAccessible` to succeed.

---

## Source Map

| Type                                                                                                          | Location                                        |
|---------------------------------------------------------------------------------------------------------------|-------------------------------------------------|
| [`CompoundValue`](../java/org/tentackle/misc/CompoundValue.java), [`ObjectUtilities`](../java/org/tentackle/misc/ObjectUtilities.java) | `tentackle-core` · `org.tentackle.misc`         |
| [`ReflectionHelper`](../java/org/tentackle/reflect/ReflectionHelper.java) (`getValueByPath`, `getStaticValueByPath`) | `tentackle-core` · `org.tentackle.reflect`  |
| [`ValidatorCompoundValueFactory`](../java/org/tentackle/validate/ValidatorCompoundValueFactory.java), [`DefaultValidatorCompoundValueFactory`](../java/org/tentackle/validate/DefaultValidatorCompoundValueFactory.java) | `tentackle-core` · `org.tentackle.validate` |
| [`AbstractValidator`](../java/org/tentackle/validate/validator/AbstractValidator.java), [`AbstractScriptValidationMessageConverter`](../java/org/tentackle/validate/validator/AbstractScriptValidationMessageConverter.java) | `tentackle-core` · `org.tentackle.validate.validator` |
| `ScriptFactory`, `Script`, `ScriptVariable`, `ScriptConverter`                                                 | `tentackle-core` · `org.tentackle.script`       |
| `CheckValidationsMojo` (build-time script check)                                                               | `tentackle-check-maven-plugin`                  |

## Related Documentation

- [Tentackle Scripting](scripting.md) — the pluggable scripting-language API behind the `SCRIPT` type.
- [Validation](validation.md) — the framework that consumes `condition`, `value` and `message`.
- [Miscellaneous](misc.md) — the `org.tentackle.misc` package `CompoundValue` belongs to.
- [Reflection](reflect.md) — `ReflectionHelper` and the path resolution used by references.
- [Service and Configuration API](../../../../tentackle-common/src/main/docs/services.md) — how `ObjectUtilities` and
  `ValidatorCompoundValueFactory` are replaced.
- [Model Definition](../../../../tentackle-model/src/main/docs/model-definition.md) — compound values in the model DSL.
- [Check Maven Plugin](../../../../tentackle-check-maven-plugin/src/main/docs/tentackle-check-maven-plugin.md) — build-time validation of compound values.
