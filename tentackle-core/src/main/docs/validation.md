# Validation — Annotation-Driven, Scope-Aware Validation

## Overview and Motivation

The Tentackle validation API (`org.tentackle.validate`, module `tentackle-core`)
checks the *members* and the *state* of plain Java objects against a set of
**constraints declared as annotations**. A constraint such as
`@NotNull`, `@Greater("0")` or `@Pattern("[A-Z]{2}\\d+")` is attached to a field
or getter; at validation time the framework discovers the constraint, evaluates
it, and collects a list of `ValidationResult`s
describing what passed and what failed. The same API also drives **object
validators** that check an aggregate as a whole — an annotation such as
`@IngotValidator` placed on the [`Ingot`](../../../../tentackle-pdo/src/main/docs/pdo.md)
PDO interface validates the entity and its components against cross-member rules,
not just a single field.

The API is deliberately UI- and persistence-agnostic. It knows nothing about
JavaFX or JDBC — it works purely by reflection over annotated members and by
running optional [scripts](#conditions-values-and-message-scripts). Concrete
layers plug into it:

- the [binding layer](binding.md) discovers the same annotations to drive
  *mandatory* and *changeable* states of UI components and to surface validation
  messages next to the offending widget (a `@NotNull` field is obviously mandatory, for example);
- the persistence layer validates [PDOs](../../../../tentackle-pdo/src/main/docs/pdo.md) before they are saved;
- the results travel between JVMs over [TRIP](trip.md) — `ValidationResult` is
  `Serializable`, so a server can validate and the client can display the
  messages.

### Why a dedicated validation layer?

- **Declarative** — a constraint is a single annotation on the member it
  guards. There is no imperative `if (x == null) errors.add(...)` scattered
  across the code. The *what* lives next to the data.
- **Scope-aware** — the *same* object is validated differently depending on the
  situation. A field may be mandatory only when [interactively](#scopes) edited,
  another constraint may apply only before [persisting](#scopes). A
  `ValidationScope` filters which
  constraints run, so one annotated class serves every context.
- **Severity-aware** — a failed validation is not necessarily an error. The
  severity decides whether a result counts
  as a hard failure or merely an informational warning.
- **Conditional and dynamic** — a constraint can carry a `condition` script and
  a `value` script, so it applies only in certain object states and can compare
  against computed values rather than constants.
- **Remoting-ready** — results are serializable and carry a *validation path*
  (`invoice.lineList[3].price`) that survives the trip to a remote client and
  can be mapped back to the component that displays the value.
- **Cached and fast** — the relatively expensive reflection scan that discovers
  the validators of a class happens once and is cached for the lifetime of the
  JVM.

### Design principles

- **Validators are stateless singletons.** A `Validator` is created once per
  annotation-occurrence, cached, and reused for every object of the class. It
  *must not* hold per-validation state — all runtime state is passed in through
  a `ValidationContext`.
- **Annotation and implementation are separated.** The annotation
  (e.g. `@NotNull`) is pure metadata; the logic lives in a paired `Validator`
  implementation (`NotNullImpl`). The `@Validation` meta-annotation links them.
- **Constraints compose.** A member may carry several constraints; an annotation
  may be `@Repeatable`. Constraints run in a defined order (priority, then
  source order) and accumulate their results.
- **Two levels of validation.** *Field validators* check individual members;
  *object validators* check the object as a whole (cross-field rules). Fields
  run first, then the object.
- **Pluggable everywhere.** Scopes, severities, the context, the utilities, and
  the compound-value factory are all resolved as services and can be replaced or
  extended by an application.

---

## Key Concepts

### `@Validation` — marking an annotation as a constraint

A constraint annotation is itself annotated with `@Validation`, whose `value()`
names the `Validator` implementation:

```java
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Validation(NotNullImpl.class)            // links annotation -> validator
@Repeatable(NotNullContainer.class)       // may appear more than once
public @interface NotNull {
  String message() default "";
  String error() default "";
  int priority() default 0;
  Class<? extends ValidationScope>[] scope() default {DefaultScope.class};
  Class<? extends ValidationSeverity> severity() default DefaultSeverity.class;
  String condition() default "";
  String value() default "";
}
```

When the framework scans a class, it walks every annotation of every field, getter,
and the class itself. For each annotation it inspects *that annotation's* own
annotations: a `@Validation` means "create one validator of this class", a
[`@RepeatableValidation`](#repeatable-constraints-and-lists) means "unwrap the
container and create one validator per repeated element"
(`ValidationUtilities.extractValidators`).

### `Validator` — the constraint implementation

`Validator` is the interface every constraint implements. It exposes the
annotation's parameters (`getValue`, `getMessage`, `getCondition`, `getErrorCode`,
`getSeverity`, `getPriority`, `getScope`) and the two runtime methods:

| Method                  | Role                                                                                 |
|-------------------------|--------------------------------------------------------------------------------------|
| `isConditionValid(ctx)` | evaluates the optional `condition` script — does the constraint apply at all?        |
| `validate(ctx)`         | performs the check, returns an immutable list of `ValidationResult` (empty = passed) |

`AbstractValidator<T extends Annotation>` is the base class. It implements the
script-backed `condition`, `value` and `message` parameters as lazily compiled,
cached [`CompoundValue`](#conditions-values-and-message-scripts)s, the default
`isConditionValid` (true unless the condition evaluates to a `Boolean false`),
result-list construction, and the script-variable set. A concrete validator such
as `NotNullImpl` only adds the annotation accessors and the actual `validate`
logic.

> **Statelessness is mandatory.** Because validators are cached and shared across
> threads and objects, a `Validator` must keep *no* mutable per-validation state.
> Everything it needs at runtime arrives via the `ValidationContext`.

#### Field validators vs. object validators

- A **field validator** checks one member. Its `ValidationContext.getObject()` is
  the member's value, `getParentObject()` the object that declares it.
  `NotNullImpl`, `GreaterImpl`, `PatternImpl` … are field validators.
- An **object validator** checks the whole object (cross-field rules). It is
  annotated on the *type*, extends `AbstractObjectValidator` (which returns empty
  `value`/`condition`), and its `getObject()` *is* the object.

`ValidationUtilities.validate(object, path, scope)` runs the field validators
first (via `validateFields`) and then the object validators (`validateObject`).

### `ValidationContext` — the runtime state

Since validators are stateless, every call receives a `ValidationContext`
carrying all per-validation information:

| Accessor                      | Meaning                                                                                                  |
|-------------------------------|----------------------------------------------------------------------------------------------------------|
| `getObject()`                 | the element under test (a field's value, or the object for an object validator). May be lazily supplied. |
| `getParentObject()`           | the object that declares the element                                                                     |
| `getType()`                   | declared type of the element (null → derive from the value)                                              |
| `getValidationPath()`         | dotted path of the element, e.g. `invoice.customer.name`                                                 |
| `getEffectiveScope()`         | the scope this run was launched with                                                                     |
| `getValidationResults()`      | results collected for the current object so far (so `@Fail` can inspect them)                            |
| `put/get(key)` , `get(Class)` | scratch map to pass data between validators of the same field/object                                     |

`DefaultValidationContext` is the default implementation (replaceable through
`ValidationContextFactory`). It supports a **lazy object supplier**: the element's
value is fetched only when a validator asks for it, so a failing pre-condition
can spare an expensive getter (e.g., a lazily loaded list relation). This holds
for constant and `$reference` conditions; a *script* condition receives the element
as its `value` variable and therefore fetches it. The supplier is invoked on every
`getObject()`, i.e. the value is not cached by the context.

The context also names the variables exposed to scripts: `context`, `scope`,
`path`, `object` (= parent object), `value` (= the element) and `clazz`.

### `ValidationResult` — the outcome

`validate()` returns a list of `ValidationResult`. Each result reports:

- `hasFailed()` — must it be treated as a failure?
- `hasMessage()` / `getMessage()` — the (localized) diagnostic text;
- `getErrorCode()` — optional stable code identifying the error independent of
  the message text;
- `getValidationPath()` — where it occurred;
- `getConfiguredScopes()` — the scopes the producing constraint was configured
  for;
- `getValidator()` / `getValidationContext()` — the producers (both **transient**:
  `null` after the result was sent over the wire).

Results survive a [TRIP](trip.md) round-trip. They can
decode collection indices out of their path:
`getValidationCollectionIndexes()` turns
`invoice.lineList[3].refList[0].text` into the indices `3` and `0` together with
their path prefixes/suffixes, which lets a UI locate the exact row that failed.

`AbstractValidationResult` is the base implementation;
`FailedValidationResult` (`hasFailed()==true`, message required) and
`InfoValidationResult` (`hasFailed()==false`, a warning) are the two shipped
concrete results.

### `ValidationScope` — when a constraint applies

A scope answers the question *"is this constraint relevant right now?"*. Every
constraint declares one or more scope interfaces via its `scope()` parameter, and
validation is always launched *with* an effective scope. A constraint runs only
when its configured scopes intersect the effective scope
(`ValidationScope.appliesTo(scopes)`), evaluated in
`ValidationUtilities.validateFields`/`validateObject`:

```java
if (validator.isConditionValid(ctx) &&
    (effectiveScope == null || effectiveScope.appliesTo(validator.getConfiguredScopes(ctx)))) {
  results.addAll(validator.validate(ctx));
}
```

A scope is modeled as an **interface** extending `ValidationScope` (so scopes can
form hierarchies through interface inheritance) plus an implementation registered
as a service. `AbstractScope.appliesTo` matches by interface assignability and
treats `AllScope` as a wildcard that matches everything.

#### `DefaultScope` and `ScopeConfigurator`

Most annotations default to `scope = {DefaultScope.class}`. `DefaultScope` has no
implementation; it is a placeholder meaning *"ask the object"*. When a constraint
is configured with only the default scope and the parent object implements
[`ScopeConfigurator`](#defaultscope-and-scopeconfigurator), the object's `getDefaultScopes()`
supplies the effective scopes (`AbstractValidator.getConfiguredScopes`). This lets
an entity declare, once, the scopes its default-scoped constraints should use —
PDOs, for example, default to `PersistenceScope`, `MandatoryScope` and `ChangeableScope`.

For objects that don't implement `ScopeConfigurator`, the configured scope stays
`DefaultScope`. Since no scope implements it, such constraints run **only** when
validating with the `AllScope` — as `ValidationUtilities.validate(object)` does for
simple objects such as DTOs. Validating such an object with, say, the
`PersistenceScope` skips all its default-scoped constraints; give them an explicit
`scope` if they must run in other scopes as well.

### `ValidationSeverity` — how bad is it

A severity turns a failed check into a concrete `ValidationResult`. Each
constraint declares `severity()`; the validator hands it to
`ValidationUtilities.createValidationResult`, which resolves the
`ValidationSeverity` singleton (via `ValidationSeverityFactory`) and asks it to
build the result. The two built-in severities:

| Severity                                | Result produced          | `hasFailed()`          |
|-----------------------------------------|--------------------------|------------------------|
| `MajorSeverity` (and `DefaultSeverity`) | `FailedValidationResult` | `true`                 |
| `MinorSeverity`                         | `InfoValidationResult`   | `false` (warning only) |

`DefaultSeverity` extends `MajorSeverity`, so by default a failed constraint is a
hard failure. An application can define its own severities (see
[Extending](#extending-the-api)). Like scopes, a severity is an interface plus a
service-registered implementation, and must be a stateless singleton.

### Mandatory and changeable evaluation

Two constraints do double duty as **binding evaluators**, feeding the
[binding layer](binding.md) the *mandatory* and *changeable* state of a UI
component without an extra mechanism:

- a validator implementing `MandatoryBindingEvaluator` (`NotNullImpl`,
  `NotEmptyImpl`, `NotZeroImpl`, `MandatoryImpl`) answers `isMandatory(ctx)` —
  should the bound field be marked required?
- a validator implementing `ChangeableBindingEvaluator` (`ChangeableImpl`)
  answers `isChangeable(ctx)` — should the bound field be editable?

Each evaluator also reports `isMandatoryDynamic()` / `isChangeableDynamic()`:
`true` when the state depends on a `condition` (so the binder must recompute it as
the model changes), `false` when it is fixed for the binding's lifetime. This is
how `@NotNull` makes a text field show the *required* marker, and how
`@Changeable(condition = "$isDomainValid")` greys a field out until another field
is valid. The dedicated [`MandatoryScope`/`ChangeableScope`](#scopes) are always
included by the binder when it evaluates these states.

### Conditions, values, and message scripts

Three annotation parameters are not plain strings but
[`CompoundValue`](compound-value.md)s — they may be a **constant**, a **method
reference**, or a **script expression** (in the default scripting language, usually
Groovy; Ruby and JSR-223 languages are also supported, see [scripting](scripting.md)):

- **`condition`** — a boolean expression gating whether the constraint applies at
  all. `@NotNull(condition = "$isClassIdNecessary")` only requires the value when
  another property says so. A condition makes a mandatory/changeable evaluator
  *dynamic*.
- **`value`** — replaces the element under test with a computed value, or supplies
  the comparison operand for the comparison validators
  (`@Greater(value = "50")`, `@True(value = "$isNewEntity")`).
- **`message`** — the diagnostic text; if empty the validator builds its own
  default message.

Scripts see the context variables (`object`, `value`, `scope`, `path`, …). The
shortcut prefixed with `$` (e.g. `$isClassIdNecessary`) is a method/property
reference on the parent object. Inside a message script, the `@('key', args…)`
syntax is rewritten by `AbstractScriptValidationMessageConverter` into a call to
`ValidationUtilities.format(object, 'key', args…)`, which performs the localized
lookup. Compiled scripts are cached, so the compile cost is paid once.

The compound values themselves are built by `ValidatorCompoundValueFactory` and
created lazily per validator — see
[CompoundValue](compound-value.md#integration-with-the-validation-api) for the
full syntax, the script-variable table, and how to choose between a reference and
a script.

#### Localized messages — the `ValidationBundle` lookup

`ValidationUtilities.format(clazz, pattern, args…)` walks up from the validated
class's package looking for a class named `ValidationBundle` exposing a
`public static String getString(String)`. The first one found localizes the
pattern, then `MessageFormat` interpolates the arguments. So
`message = "{ @('{0}_is_wrong', value) }"` produces a per-package, locale-aware,
parameterized message with almost no boilerplate. The core's own validator
messages live in `ValidatorBundle` (e.g. `"{0} is missing"` for `@NotNull`).

---

## How Validation Runs

The entry point is `ValidationUtilities` (a singleton service, replaceable). The
typical call is `validate(object, validationPath, scope)`:

1. **Resolve the class** of the object (`EffectiveClassProvider` unwraps proxies
   such as PDOs).
2. **Field validators** — `ValidatorCache.getFieldValidators(clazz)` returns the
   cached, sorted validators for all annotated fields and getters. (If a member
   is annotated on *both* field and getter, the constraints of both apply — they
   share the validation context of that member.) `validateFields` builds one
   `ValidationContext` per field (shared by all its validators), then for each
   validator checks `condition` **and** `scope`, and runs it if both apply.
3. **Object validators** — `getObjectValidators(clazz)` returns the type-level
   validators; `validateObject` runs them against a single context for the whole
   object.
4. **Collect** — all results are concatenated into one list. Empty list ⇒ the
   object is valid (for the given scope).

Validators are **sorted** by descending `priority`, then by source order
(`validationIndex`); higher priority runs first (`sortValidators`).

### Aborting early with `@Fail`

`validate()` keeps going and accumulates *all* results by default. To stop at a
checkpoint, the `@Fail` validator throws a `ValidationFailedException` carrying
the results gathered so far. It can fail on the first hard failure
(`Results.FAILED`, the default), on any collected result (`Results.ANY`), or
unconditionally in combination with a `condition` (`Results.IGNORED`). Because
`@Fail` inspects `context.getValidationResults()`, placing it (via `priority`)
after a group of constraints turns that group into a gate.

`ValidationFailedException` is an unchecked exception that *holds* the results.
A composite `validate()` implementation catches it, prepends the results it had
already gathered (`vfx.reThrow(resultsSoFar)`), and rethrows — so no partial
results are ever lost:

```java
public List<ValidationResult> validate(...) {
  List<ValidationResult> results = new ArrayList<>();
  try {
    results.addAll(ValidationUtilities.getInstance().validate(...));
    // ... validate components ...
  }
  catch (ValidationFailedException vfx) {
    vfx.reThrow(results);     // prepend & rethrow
  }
  return results;
}
```

`ValidationRuntimeException` is the unchecked exception used for *configuration*
errors (a bad script, a non-comparable value, an unresolved severity, a malformed
validation path), as opposed to a logical validation failure.

### Validating collections and nested objects

`validateCollection(coll, path, scope)` validates each element, appending
`[index]` to the path (`null` elements are skipped and not counted). Elements implementing
`Validateable` are validated
through their own `validate` method (honoring any override); other elements go
through the annotation-driven `validate`. The `@Validate` constraint ties this
into the field/object flow: annotated on a member, it descends into a
`Validateable` value (through its own `validate` method) or a `Collection`, letting
an application-specific datatype or a PDO component be validated in place — and
letting `priority`/`condition` control *when* in the parent's validation that
descent happens. Any other non-null value is a `ValidationRuntimeException`.

---

## Built-in Validators

All live in `org.tentackle.validate.validator`. Each is `@Repeatable` (via a
`…Container`) and — unless noted — supports `message`, `error`, `priority`,
`scope`, `severity`, `condition` and `value`. `null` elements are generally
considered **valid** (use `@NotNull`/`@Mandatory` to require presence), except
where stated.

| Annotation                             | Checks                                                                                             |
|----------------------------------------|----------------------------------------------------------------------------------------------------|
| `@NotNull`                             | element is not `null` (also a *mandatory* evaluator)                                               |
| `@Null`                                | element is `null`                                                                                  |
| `@NotZero`                             | `Number` element ≠ 0 (scale-insensitive); implies mandatory                                        |
| `@Zero`                                | `Number` element == 0 (scale-insensitive)                                                          |
| `@NotEmpty`                            | `String`/`Collection`/`Map`/array is not empty; `null` is **invalid**; implies mandatory           |
| `@Size(min=, max=)`                    | length/size of `String`/`Collection`/`Map`/array within bounds                                     |
| `@Pattern(value, flag=, ignoreNulls=)` | `String` matches a regular expression                                                              |
| `@Greater` / `@GreaterOrEqual`         | `Comparable` element > / ≥ `value`                                                                 |
| `@Less` / `@LessOrEqual`               | `Comparable` element < / ≤ `value`                                                                 |
| `@Equal` / `@NotEqual`                 | element equals / differs from `value`                                                              |
| `@True` / `@False`                     | `boolean`/`Boolean` element is true / false; usable as an *object* validator with a `value` script |
| `@Mandatory`                           | marks a field mandatory for the binding (no actual check) — when `@NotNull`/`@NotZero` don't fit   |
| `@Changeable`                          | marks a field changeable for the binding (a *changeable* evaluator)                                |
| `@Validate`                            | descends into a `Validateable` component or `Collection`; reorders/guards component validation     |
| `@Fail`                                | aborts validation with a `ValidationFailedException` based on results so far                       |

Most validators carry an `ignoreNulls` flag (default `true`). Set to `false`, a `null`
element becomes invalid for `@Zero`, `@NotZero`, `@Pattern`, `@Size`, `@True` and
`@False`. For the comparison validators it controls how `null` operands compare:
`null` is then smaller than any other value (`@Greater`, `@Less`, …) and equals
only `null` (`@Equal`, `@NotEqual`). Example from a real model:

```java
@NotNull(message = "missing class ID", condition = "$isClassIdNecessary")
@GreaterOrEqual(value = "100", condition = "$isClassIdNecessary",
                message = "class ID must be >= 100")
Integer classId;

// object-level / scoped constraint with a script
@True(value = "$isNewEntity", scope = InteractiveScope.class, message = "entity already exists")
String entityName;

// dynamic changeable, driven by another property
@Changeable(condition = "$isDomainValid")
String domainInterface;
```

In a model definition (the `## validations` section, see
[model-definition.md](../../../../tentackle-model/src/main/docs/model-definition.md#6-attribute-option-lines-validation-section)),
the same constraints are written without the `@interface` import ceremony:

```
total:       @NotNull(scope=PersistenceScope.class),
             @Greater(value="0", scope=PersistenceScope.class, condition="{ object.printed != null }")
invoiceDate: @NotNull(scope=AllScope.class, message="{ @('please_enter_the_invoice-date') }",
                      condition="{ object?.printed == null }")
```

---

## Scopes

Predefined scopes live in `org.tentackle.validate.scope` (interface + `…Impl`
service). Each has a stable `NAME`.

| Scope                              | Purpose                                                                                   |
|------------------------------------|-------------------------------------------------------------------------------------------|
| `AllScope` (`ALL`)                 | matches every scope — a wildcard; the only scope applying `DefaultScope` constraints of objects without a `ScopeConfigurator` |
| `PersistenceScope` (`PERSISTENCE`) | constraints enforced before an object is persisted                                        |
| `InteractiveScope` (`INTERACTIVE`) | constraints relevant only during interactive editing                                      |
| `MandatoryScope` (`MANDATORY`)     | drives the *mandatory* state of bound components                                          |
| `ChangeableScope` (`CHANGEABLE`)   | drives the *changeable* state of bound components                                         |

`DefaultScope` (in the root package) is the placeholder default resolved per
object through `ScopeConfigurator`. Applications add their own scopes by declaring
an interface extending `ValidationScope` and an implementation annotated
`@ValidationScopeService(MyScope.class)`.

---

## Repeatable Constraints and Lists

Every built-in constraint is `@Repeatable`, backed by a container annotated
`@RepeatableValidation(TheConstraint.class)`:

```java
@RepeatableValidation(NotEmpty.class)
public @interface NotEmptyContainer {
  NotEmpty[] value();
}
```

When the scanner meets a container, it unwraps it via
`getAnnotationsByType` and creates one validator per repeated element, each with
its own `validationIndex`. This lets the same member carry, say, two `@Greater`
constraints with different scopes or conditions. The interfaces `ValidatorList`
and `RepeatableValidator` (with `AbstractRepeatableValidator`) describe validators
that wrap a list of sub-annotations; the scanner itself does not use them, but
unwraps the containers on its own.

---

## `Validateable` — objects that validate themselves

By default, any object is validated purely by its annotations through
`ValidationUtilities`. An object that needs *control* over its own validation
implements `Validateable`:

```java
public interface Validateable {
  default List<ValidationResult> validate(String validationPath, ValidationScope scope) {
    return ValidationUtilities.getInstance().validate(this, validationPath, scope);
  }
}
```

The default simply delegates, but an implementor may override it to add custom
steps, reorder, or short-circuit. Implementing the interface (even without
overriding) also signals intent and lets `validateCollection` and `@Validate`
route through the object's own method. The persistence layer's
`PersistentObject.validate()` is the canonical example: it presets virgin objects
and validates the components of the aggregate as well. Note that
`ValidationUtilities` itself does **not** check for `Validateable` (except inside
`validateCollection`) — such objects must be entered through their own `validate`
method.

---

## Caching and Statelessness

`ValidatorCache` (a singleton service) memoizes, per class:

- field validators (`getFieldValidators(clazz)`),
- field validators for a specific field/getter pair,
- object validators (`getObjectValidators(clazz)`).

Because validators are immutable after configuration, sharing them across all
instances and threads is safe — and the reflection scan plus script compilation
happens once. `invalidate()` clears the cache (e.g., for tooling that reloads
classes). The matching rule for caching state: **a validator must never store a
per-validation value in a field**; the `ValidationContext` is the only legitimate
carrier of runtime state.

### Test mode

`ValidationUtilities.configureTestMode(logLevel)` forces *every* validator to run
regardless of scope or condition (still evaluating those, and logging each
application at the given level). This is a build/CI aid to prove that all
validators wire up, all scripts compile and run, and no validator throws — even
results that would normally be filtered out by scope.

---

## Binding Integration: Validation Paths and the `ValidationMapper`

A `ValidationResult` carries a *validation path* describing the model location of
the offending value (`invoice.lineList[3].price`). A UI, however, addresses its
widgets by *binding path*, and these may differ — especially when nested
[binders](binding.md) compose a screen. `ValidationMapper` translates a validation
path to a binding path and, optionally, switches to a different (nested) binder.
`ValidationUtilities.mapValidationPath(mappers, binder, path)` walks an ordered
set of mappers, rewriting the path segment by segment and following binder
hand-offs (with loop detection), so a result produced deep in the model graph —
possibly on a remote server — lands on the right on-screen component.

---

## Extending the API

- **A new constraint** — declare an annotation `@Validation(MyImpl.class)`
  (optionally `@Repeatable(MyContainer.class)` with a
  `@RepeatableValidation`-annotated container), and implement `MyImpl` by
  extending `AbstractValidator<MyAnno>` (field) or `AbstractObjectValidator`
  (object). Keep it stateless; read runtime data from the `ValidationContext`.
  To drive UI mandatory/changeable state, also implement
  `MandatoryBindingEvaluator` / `ChangeableBindingEvaluator`.
- **A new scope** — an interface `extends ValidationScope` plus an implementation
  annotated `@ValidationScopeService(MyScope.class)`.
- **A new severity** — an interface `extends ValidationSeverity` plus an
  implementation annotated `@ValidationSeverityService(MySeverity.class)` that
  builds the desired `ValidationResult`.
- **Custom context / utilities** — replace `ValidationContextFactory`,
  `ValidationUtilities`, `ValidatorCache` or `ValidatorCompoundValueFactory` by
  registering an alternative `@Service`.
- **A scripting language** — provide a `@ValidationScriptConverter("lang")` (and
  a `@MessageScriptConverter`) so conditions/values/messages can be written in
  another language.

All of these are discovered through the
[`ServiceFinder` SPI](../../../../tentackle-common/src/main/docs/services.md), so
they work in both modular and non-modular deployments. The module exports
`org.tentackle.validate`, `…validate.scope`, `…validate.validator` and
`…validate.severity`.

---

## Package Reference

### `org.tentackle.validate` (tentackle-core)

| Type                                                       | Role                                                               |
|------------------------------------------------------------|--------------------------------------------------------------------|
| `Validation`                                               | meta-annotation linking a constraint annotation to its `Validator` |
| `RepeatableValidation`                                     | meta-annotation marking a `@Repeatable` container                  |
| `Validator` / `AbstractValidator`                          | the constraint contract and base implementation                    |
| `ValidatorList` / `RepeatableValidator`                    | validators wrapping a list of sub-annotations (not used by the scanner) |
| `ValidationContext` / `DefaultValidationContext`           | per-validation runtime state                                       |
| `ValidationResult`                                         | the (serializable) outcome of a check                              |
| `ValidationScope` / `DefaultScope`                         | when a constraint applies; the per-object default placeholder      |
| `ScopeConfigurator`                                        | object-supplied default scopes                                     |
| `ValidationSeverity`                                       | turns a failure into a concrete result                             |
| `Validateable`                                             | objects that drive their own validation                            |
| `ValidationUtilities`                                      | the engine: scan, filter by scope/condition, run, collect          |
| `ValidatorCache`                                           | per-class cache of discovered validators                           |
| `MandatoryBindingEvaluator` / `ChangeableBindingEvaluator` | feed mandatory/changeable state to the binding layer               |
| `ValidationMapper`                                         | maps validation paths to binding paths/binders                     |
| `ValidationFailedException`                                | unchecked, carries results, used by `@Fail` to abort               |
| `ValidationRuntimeException`                               | unchecked, signals configuration/runtime errors                    |
| `*Factory`, `*Service`                                     | service hooks for context, scope, severity, compound values        |

### `org.tentackle.validate.validator`

The built-in constraints and their `…Impl`/`…Container` classes
(see [the catalog](#built-in-validators)), plus `AbstractObjectValidator`,
`AbstractRepeatableValidator`, the script converters and `ValidatorBundle`.

### `org.tentackle.validate.scope`

`AllScope`, `PersistenceScope`, `InteractiveScope`, `MandatoryScope`,
`ChangeableScope` and their `…Impl` service implementations, plus `AbstractScope`.

### `org.tentackle.validate.severity`

`DefaultSeverity`, `MajorSeverity`, `MinorSeverity` and their `…Impl`s;
`AbstractValidationResult`, `FailedValidationResult`, `InfoValidationResult`.

See also: [binding.md](binding.md) for how mandatory/changeable state and
validation messages reach the UI, [pdo.md](../../../../tentackle-pdo/src/main/docs/pdo.md) for the domain objects most
often validated, and [trip.md](trip.md) for how validation results travel between
JVMs.
