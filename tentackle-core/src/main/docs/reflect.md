# Reflection — Class Mapping, Mixins, and Proxy Support

## Overview and Motivation

`org.tentackle.reflect` is where the framework's dynamic behavior is implemented. Three of
Tentackle's most distinctive features are not language features — they are reflection, done
carefully and cached:

- **PDOs emulate multiple inheritance.** A `PersistentDomainObject` is a dynamic proxy whose calls
  are routed to *two* delegates, a persistence one and a domain one. The
  [`Mixin`](../java/org/tentackle/reflect/Mixin.java) is what makes that work.
- **Implementations are found, not wired.** `@MappedService` says "this class implements that
  interface"; the [`ClassMapper`](../java/org/tentackle/reflect/ClassMapper.java) is the runtime
  engine that resolves it — including up class hierarchies and across packages.
- **Immutable objects can be deserialized.** An object with no no-arg constructor and final fields
  cannot be reconstructed the usual way. The
  [`@CanonicalConstructor`](../java/org/tentackle/reflect/CanonicalConstructor.java) family tells
  [TRIP](trip.md) how to build it.

The package also holds the interception machinery. That half is documented in depth in
[interceptors.md](interceptors.md) — this document covers the rest and explains only how
interception *plugs into* the proxy support described here.

A word on the design: reflection is slow if you let it be. Nearly every type in this package exists
to do a reflective lookup **once** and cache it — `ConcurrentHashMap`s of resolved mappings,
constructors, method indexes, and property accessors are the norm. The expensive work happens at
class-load or first-use time; the steady state is a hash lookup.


## Class Mapping

### ClassMapper — the engine behind `@MappedService`

The [service API](../../../../tentackle-common/src/main/docs/services.md) has two flavors:
`@Service` locates a singleton, `@MappedService` maps *implementations to the interfaces they
implement*. The build-time tooling writes those relationships into `META-INF`; at runtime,
[`ClassMapper`](../java/org/tentackle/reflect/ClassMapper.java) resolves them.

A mapper is created per mapped service type and given a name used in logs and error messages:

```java
ClassMapper mapper = ClassMapper.create("FX table-config-provider", TableConfigurationProvider.class);
Class<?> providerClass = mapper.mapLenient(rowClass);
```

The interface is small — `map` and `mapLenient`, each taking a `Class` or a name, plus a default
class and listeners — but the **lenient** variant carries the weight:

| Method                | Lookup                                                                                          |
|-----------------------|-------------------------------------------------------------------------------------------------|
| `map(Class \| String)`      | The explicit mapping only. Throws `ClassNotFoundException` if there is none.               |
| `mapLenient(Class \| String)` | Walks **up the superclass chain** (all the way to `Object`), then tries the **interfaces**, recursively through super-interfaces. |

Lenient mapping is why a provider registered for a base class or an interface serves every subclass
without further registration — one `GuiProvider` for an entity hierarchy, one `TableCellType` for
`PersistentDomainObject` covering every PDO. Note the asymmetry, which the javadoc calls out: when
mapping a *class*, its interfaces are ignored until the superclass chain is exhausted.

`setDefaultClass(...)` supplies a fallback for unmapped classes instead of an exception, and
[`ClassMappedListener`](../java/org/tentackle/reflect/ClassMappedListener.java)s fire the first time
a class is mapped.

The base implementation [`AbstractClassMapper`](../java/org/tentackle/reflect/AbstractClassMapper.java)
provides the lenient and package/basename lookup and caches every resolved name in a
`ConcurrentHashMap`, so the hierarchy walk happens once per class. Resolution is logged at `FINE`
(configurable per mapper) — the log line `<mapper>: added <from> -> <to>` is the first place to look
when an implementation isn't found.

- [`DefaultClassMapper`](../java/org/tentackle/reflect/DefaultClassMapper.java) — initialized from a
  `Map` or a `Properties` object (a file, XML, or built at runtime).
- [`ClassToServicesMapper`](../java/org/tentackle/reflect/ClassToServicesMapper.java) — maps a class
  to **all** matching services rather than the first, for cases where every implementation on the
  path should contribute.
- [`ClassMapperFactory`](../java/org/tentackle/reflect/ClassMapperFactory.java) — the replaceable
  singleton behind `ClassMapper.create(...)`.

Consumers are everywhere: `PdoInvocationHandler` (persistence and domain implementations), the TRIP
factories, and the FX factories for `GuiProvider`s, table configurations, cell types and context
menus.


## Proxies and Mixins

### Mixin — emulating multiple inheritance

Java has single inheritance. A PDO needs two implementations — persistence and domain — behind one
interface. [`Mixin<T,M>`](../java/org/tentackle/reflect/Mixin.java) is the piece that closes the gap.

A mixin binds three things: the **declaring interface**, the **implementation class** found through
a `ClassMapper`, and the **delegate** instance it creates. Given a PDO interface, the constructor
maps leniently to the implementation, then identifies which of that implementation's interfaces
extends the expected super-interface — that becomes the declaring class, and it is how the invocation
handler later decides whether a method belongs to this mixin.

[`PdoInvocationHandler`](../../../../tentackle-pdo/src/main/java/org/tentackle/pdo/PdoInvocationHandler.java)
holds exactly two:

```java
private final Mixin<T,PersistentObject<T>> persistenceMixin;
private final Mixin<T,DomainObject<T>> domainMixin;
...
persistenceMixin = new Mixin<>(persistenceMapper, clazz, PersistentObject.class);
domainMixin      = new Mixin<>(domainMapper,      clazz, DomainObject.class);
```

Each call on the proxy is routed to the delegate whose declaring interface declares the method. The
result is an object that *is* both its persistence and its domain implementation — see
[pdo.md](../../../../tentackle-pdo/src/main/docs/pdo.md) for what that buys you and
[modules.md](../../../../src/main/docs/modules.md) for why the two implementations live in separate
modules that never see each other.

Mixins cache aggressively: constructors are looked up once per (implementation class, parameter
types) pair, and a static match cache remembers, per class pair, whether a class implements a given
declaring interface. `@TripReferencable(NEVER)` marks them as never referenced on the wire — the
proxy is what travels, not its mixins.

[`DummyDelegate`](../java/org/tentackle/reflect/DummyDelegate.java) is a marker letting a mixin with
a real delegate class be treated as a dummy — used where a proxy needs the shape but not the
behavior.

### EffectiveClassProvider — what `getClass()` can't tell you

Call `getClass()` on a dynamic proxy and you get the proxy's synthetic class, which is almost never
what you want. [`EffectiveClassProvider<T>`](../java/org/tentackle/reflect/EffectiveClassProvider.java)
is the interface an object implements to report its *effective* class — the one the application
thinks in terms of.

This matters more than it sounds: validation, scripting, the FX table configuration and the security
interceptors all key off the effective class, and all of them would silently misbehave against a
proxy class name. Implementors include `AbstractDomainObject`, `AbstractDomainOperation`,
`ValidationUtilities`, `ScriptVariable`, `DefaultTableConfiguration`, and the security interceptors.

#### `getClass()`, `getEffectiveClass()` and `instanceof`

For a [PDO](../../../../tentackle-pdo/src/main/docs/pdo.md) the effective class is the **entity
interface** — the type the application declared and thinks in, e.g. `Customer` — *not* the domain or
persistence implementation class behind the proxy. Three type queries therefore give three different
answers, and only two of them are useful:

| Expression                              | Result for a `Customer` PDO                                        |
|-----------------------------------------|-------------------------------------------------------------------|
| `pdo.getClass()`                        | the synthetic proxy class (`jdk.proxy…$ProxyN`) — never use it     |
| `pdo.getEffectiveClass()`               | `Customer.class` — the entity interface                           |
| `pdo instanceof Customer`               | **`true`** — the proxy implements the entity interface           |
| `pdo instanceof CustomerImpl` (or any domain/persistence impl class, `AbstractDomainObject`, …) | **`false`** — the impls are the *delegates* behind the proxy, not the proxy itself |

The practical rule that follows:

- **To test what kind of entity you hold, `instanceof` (or `switch`) against the entity
  *interface*.** That is what travels between tiers and what the proxy actually implements. Testing
  against an implementation class always fails — the impl is hidden behind the proxy — and testing
  against the proxy class is meaningless.
- **When you need the exact type as a `Class`** (to key a map, drive a `switch` on `Class`, look up a
  provider), call `getEffectiveClass()`; `getClass()` would hand you the proxy. Use the static
  `EffectiveClassProvider.getEffectiveClass(obj)` when `obj` might be either a proxy or a plain
  object — it returns the effective class for the former and `obj.getClass()` for the latter.

```java
// Yes — the entity interface is what the proxy implements
if (pdo instanceof Customer c) {
  c.getCreditLimit();
}

// No — CustomerImpl is the domain delegate, hidden behind the proxy: this is always false
if (pdo instanceof CustomerImpl) { … }         // dead branch

// Keying by type: getEffectiveClass(), never getClass()
Map<Class<?>, Handler> handlers = …;
Handler h = handlers.get(pdo.getEffectiveClass());   // Customer.class, not the proxy class
```

`getEffectiveSuperClasses()` completes the picture: it returns the entity's PDO super-interfaces, so
a caller that walks an inheritance hierarchy (a generic editor, an export) sees the same interface
lineage the application declared, not the proxy's.

### Interception

The interception types — [`Interceptable`](../java/org/tentackle/reflect/Interceptable.java),
[`@Interception`](../java/org/tentackle/reflect/Interception.java),
[`Interceptor`](../java/org/tentackle/reflect/Interceptor.java),
[`AbstractInterceptor`](../java/org/tentackle/reflect/AbstractInterceptor.java),
[`InterceptableMethod`](../java/org/tentackle/reflect/InterceptableMethod.java),
[`InterceptableMethodCache`](../java/org/tentackle/reflect/InterceptableMethodCache.java) and
[`InterceptableInvocationHandler`](../java/org/tentackle/reflect/InterceptableInvocationHandler.java)
— are covered by **[interceptors.md](interceptors.md)**: the annotation model, the `proceed`
contract, chaining and order, the built-in interceptors, and compile-time verification.

What belongs here is how they connect to the machinery above. Interception is layered onto the same
dynamic proxies the mixins feed:
[`InterceptableFactory`](../java/org/tentackle/reflect/InterceptableFactory.java) (with
[`DefaultInterceptableFactory`](../java/org/tentackle/reflect/DefaultInterceptableFactory.java))
creates interceptable objects;
[`@InterceptionService`](../java/org/tentackle/reflect/InterceptionService.java) is a
`@MappedService` for `Interceptable`, so implementations are located through a `ClassMapper` exactly
like any other mapped service;
[`InterceptionUtilities`](../java/org/tentackle/reflect/InterceptionUtilities.java) is the
replaceable `@Service` holding the helper logic; and
[`InterceptableMethodInvoker`](../java/org/tentackle/reflect/InterceptableMethodInvoker.java) — one
per interceptable type (PDO, operation, …) — performs the invocation and extends `MethodStatistics`,
which is where per-method invocation counts and timings come from (see [logging.md](logging.md)).


## Canonical Construction

An immutable object — final fields, no setters, no no-arg constructor — is a problem for any
deserializer: there is no way to create it empty and fill it in. Java records solve this with a
canonical constructor the compiler knows about. Tentackle generalizes the idea to ordinary classes
with a small annotation family, which is how [TRIP](trip.md) reconstructs immutable values without
requiring `-parameters` at compile time or resorting to `Unsafe`.

| Annotation                                                              | Applies to     | Meaning                                                                    |
|-------------------------------------------------------------------------|----------------|----------------------------------------------------------------------------|
| [`@CanonicalConstructor`](../java/org/tentackle/reflect/CanonicalConstructor.java) | constructor | *This* is the constructor to build the object with                     |
| [`@CanonicalName`](../java/org/tentackle/reflect/CanonicalName.java)     | parameter      | The parameter's name, since reflection can't recover it without `-parameters` |
| [`@CanonicalField`](../java/org/tentackle/reflect/CanonicalField.java)   | field          | This field supplies a constructor argument                                 |
| [`@CanonicalGetter`](../java/org/tentackle/reflect/CanonicalGetter.java) | method         | This public no-arg getter supplies a constructor argument                  |
| [`@CanonicalBuilder`](../java/org/tentackle/reflect/CanonicalBuilder.java) | type or constructor | The object is built through a builder instead                       |

The matching of constructor parameters to members is by **name**: either annotate the parameters
with `@CanonicalName`, or annotate the members with `@CanonicalField` / `@CanonicalGetter`. The
explicit-name requirement is a deliberate trade — Tentackle does not want to force every application
to compile with `javac -parameters`.

A `@CanonicalGetter` does not need a serializable field behind it: if the field is transient, or
declared in a JDK class TRIP must not reflect into, the getter becomes a component in its own right.
This is what lets [`ChainedProperties`](../java/org/tentackle/misc/ChainedProperties.java) transport
the `defaults` chain of a `java.util.Properties`, which lives in a `java.util` field —
see [JDK collections and maps](trip.md#jdk-collections-and-maps).

`@CanonicalBuilder` covers the builder pattern: the constructor takes a single builder argument; the
builder has a no-arg constructor, one method per field named after the field, taking the value and
returning the builder, plus a no-arg method returning the finished object. The
[DTO wurblet](../../../../tentackle-wurblets/src/main/docs/wurblets.md)'s `--canonical` option
generates exactly this shape.

[`ObjectTripType`](../java/org/tentackle/trip/types/ObjectTripType.java) reads all of it through
`ReflectionHelper.getCanonicalConstructor(...)` / `getCanonicalBuilderInfo(...)` and switches to
*delayed construction*: components are deserialized first, then the object is built in one shot.
Without a canonical constructor or a no-arg constructor, a class is simply not serializable by TRIP
— which is why [trip.md](trip.md) recommends adding one as the first remedy.

### Delayed construction and back-references

Delayed construction has one consequence worth knowing: TRIP can only register an object for
[back-references](trip.md#tripreferencable) once the object exists. How far a component may
reference back to the object it belongs to therefore depends on how that object is constructed:

| Construction            | When the object exists          | Components that may back-reference it |
|-------------------------|---------------------------------|---------------------------------------|
| no-arg constructor      | before any component is read    | all of them                           |
| `@CanonicalConstructor` | once all constructor args are read | every non-canonical component      |
| `@CanonicalBuilder`     | after *all* components are read | none                                  |
| record                  | after *all* components are read | none                                  |

A component that reaches back to its own object outside these bounds — directly or through another
object — fails deserialization with a `no such reference id` `TripRuntimeException`. This is rarely
hit in practice, since immutable values that need a canonical constructor or a builder are normally
leaves of the object graph rather than cycle participants. Where a genuine cycle is unavoidable, the
class must provide a no-arg constructor.

Maps and collections built through a `@CanonicalConstructor` send their components ahead of the
entries, so the container exists before the first entry is read: keys, values and elements may
back-reference the container itself without restriction.


## Caches, Visitors, and Helpers

### Method caches

- [`MethodCache`](../java/org/tentackle/reflect/MethodCache.java) — a thread-safe cache for method
  lookups that involve a *best-match* search, which is expensive to repeat. `ReflectiveVisitor` is
  the motivating case.
- [`IndexedMethodCache`](../java/org/tentackle/reflect/IndexedMethodCache.java) — one per class,
  mapping methods to small integer indexes and back. This is what lets [TRIP](trip.md) name a method
  on the wire with an `int` rather than a signature string: `DefaultRemoteDelegate` and
  `DefaultRegistry` assign an index on first use and both sides then speak in indexes.

### ReflectiveVisitor

[`ReflectiveVisitor`](../java/org/tentackle/reflect/ReflectiveVisitor.java) implements the visitor
pattern without the accept-method boilerplate: the visit method for a concrete class is found by
reflection, choosing the **most specific** match, so one method can serve a whole class hierarchy
and a more specific one overrides it for a subclass. It backs
[`PersistenceVisitor`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/PersistenceVisitor.java),
which is how applications add behavior across entity types without touching the entities — see
[database.md](../../../../tentackle-database/src/main/docs/database.md).

### PropertyMap

[`PropertyMap<T>`](../java/org/tentackle/reflect/PropertyMap.java) exposes a bean's properties as a
`Map<String,Object>`, with [`PropertyMapping<T>`](../java/org/tentackle/reflect/PropertyMapping.java)
describing each one — `get`/`set` or `is`/`set` pairs, or a read-only method. `AbstractBuilder` in
[tentackle-fx](../../../../tentackle-fx/src/main/docs/fx.md) uses it to configure components
generically by property name.

### ReflectionHelper

[`ReflectionHelper`](../java/org/tentackle/reflect/ReflectionHelper.java) is the static toolbox the
rest of the framework reaches for. Beyond the canonical-construction lookups already mentioned:

| Area                  | Methods                                                                                              |
|-----------------------|------------------------------------------------------------------------------------------------------|
| Names                 | `getClassBaseName`, `getPackageName`, `getPackagePathName`, `getOutermostClassName`, `getSerializableClassName`, `methodToString`, `makeDeclareString` |
| Loading               | `loadClass`                                                                                          |
| Primitives            | `primitiveToWrapperClass`, `wrapperToPrimitiveClass`                                                  |
| Type tests            | `isAssignableFrom`, `isAnnotationPresent` (both with all/any semantics), `isNonStaticInnerClass`, `isImmutableCollection` |
| Generics              | `extractGenericInnerTypeClass`                                                                        |
| Members               | `getAllFields`, `getAllMethods` (walking the hierarchy)                                               |
| Bean conventions      | `getGetterName`, `getFieldName`, `getGetter`, `getSetter`, `isGetter`, `isSetter`                     |

The small details matter here: it knows about synthetic `this$0` parameters of inner classes and
about `java.util.ImmutableCollections`, both of which trip up naive reflection over serialized
graphs.


## Package Layout

| Group                 | Types                                                                                                                     |
|-----------------------|---------------------------------------------------------------------------------------------------------------------------|
| Class mapping         | `ClassMapper`, `AbstractClassMapper`, `DefaultClassMapper`, `ClassMapperFactory`, `DefaultClassMapperFactory`, `ClassToServicesMapper`, `ClassMappedListener` |
| Proxies & mixins      | `Mixin`, `DummyDelegate`, `EffectiveClassProvider`                                                                        |
| Interception          | `Interceptable`, `Interception`, `Interceptor`, `AbstractInterceptor`, `InterceptableMethod`, `InterceptableMethodCache`, `InterceptableMethodInvoker`, `InterceptableInvocationHandler`, `InterceptableFactory`, `DefaultInterceptableFactory`, `InterceptionService`, `InterceptionUtilities` → [interceptors.md](interceptors.md) |
| Canonical construction | `CanonicalConstructor`, `CanonicalName`, `CanonicalField`, `CanonicalGetter`, `CanonicalBuilder`                          |
| Caches                | `MethodCache`, `IndexedMethodCache`                                                                                       |
| Helpers               | `ReflectionHelper`, `ReflectiveVisitor`, `PropertyMap`, `PropertyMapping`                                                 |


## Related Documentation

- [Tentackle Core](core.md) — the runtime foundation this package belongs to.
- [Interceptors](interceptors.md) — the interception model in depth.
- [PDO](../../../../tentackle-pdo/src/main/docs/pdo.md) — the dynamic proxy the mixins build.
- [Service and Configuration API](../../../../tentackle-common/src/main/docs/services.md) — `@Service` and `@MappedService`, which `ClassMapper` resolves.
- [TRIP](trip.md) — canonical construction and indexed method calls in practice.
- [Wurblets](../../../../tentackle-wurblets/src/main/docs/wurblets.md) — the DTO wurblet's `--canonical` option.
- [Miscellaneous](misc.md) — the sibling utility package.
