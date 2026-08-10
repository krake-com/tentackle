# Tentackle PDO Mocks — the `tentackle-pdo-mock` Module

## Overview

`tentackle-pdo-mock` is a tiny **test-support module** holding four classes: mock *domain* and *persistence*
delegates that let a unit test instantiate [PDOs](../../../../tentackle-pdo/src/main/docs/pdo.md) and
[operations](../../../../tentackle-pdo/src/main/docs/operation.md) **without a persistence backend** — no session,
no connection, no database.

Recall the [PDO pattern](../../../../tentackle-pdo/src/main/docs/pdo.md): a PDO is a dynamic proxy that emulates
multiple inheritance by combining a *domain* delegate (the business logic) with a *persistence* delegate (the
database logic). Each half can be tested in isolation by replacing the *other* half with a mock:

| To test …                | … pair the real …    | … with a mock              |
|--------------------------|----------------------|----------------------------|
| domain logic             | domain delegate      | `MockPersistentObject`     |
| persistence logic        | persistence delegate | `MockDomainObject`         |
| operation domain logic   | domain operation     | `MockPersistentOperation`  |
| operation persistence    | persistent operation | `MockDomainOperation`      |

> **Test scope only.** The artifact exists solely for tests and must never end up on an application's
> compile or runtime classpath.

### Why a module of its own

The mocks are used by the [domain](../../../../tentackle-domain/src/main/docs/domain.md) and
[persistence](../../../../tentackle-persistence/src/main/docs/persistence.md) layers for their *own* unit tests.
Putting them into [`tentackle-test-pdo`](../../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md) —
the natural home — would create a **dependency cycle**, because that module depends on the domain and persistence
layers. Hence `tentackle-pdo-mock` sits *below* both layers and depends on nothing but
[`tentackle-pdo`](../../../../tentackle-pdo/src/main/docs/pdo.md).

Downstream projects need not care: `tentackle-test-pdo` has a regular (non-optional) dependency on
`tentackle-pdo-mock`, so the classes arrive transitively under their unchanged package and class names,
`org.tentackle.pdo.mock.*`.

### Packaging

- The dependency on `tentackle-pdo` is declared `optional` — *"don't change the classpath order"*. The PDO runtime
  resolves the domain and persistence layers through the
  [ServiceFinder SPI](../../../../tentackle-common/src/main/docs/services.md), and the order in which those layers
  appear on the classpath matters; an optional dependency keeps this artifact from pushing `tentackle-pdo` into a
  downstream classpath at the wrong position.
- The module has **no `module-info.java`** and declares no `Automatic-Module-Name`. It is meant to live on the
  **test class path** — which is exactly where the domain and persistence modules run their tests
  (`useModulePath=false` in their surefire configuration).
- The `analyze` goal of the
  [tentackle-maven-plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md) runs over the
  module like everywhere else, but since the mocks carry no service annotations themselves, the jar contains nothing
  but the four classes. The `META-INF/mapped-services/*` entries that make *your* mock discoverable are produced by
  the `test-analyze` goal in *your* module.

---

## The Four Classes

All four live in `org.tentackle.pdo.mock`, are `Serializable`, and define a public constant

```java
public static final String UNSUPPORTED = "not implemented";
```

which is the message of the `UnsupportedOperationException` thrown by every method the mock does not implement.
That is the central design decision: **the mocks implement only what a database-less test legitimately touches**
and fail loudly — and unmistakably — the moment a test reaches for real persistence.

| Class                                                                                  | Stands in for                                          | Also implements                |
|----------------------------------------------------------------------------------------|--------------------------------------------------------|--------------------------------|
| [`MockPersistentObject<T,P>`](../java/org/tentackle/pdo/mock/MockPersistentObject.java) | the persistence delegate of a `PersistentDomainObject` | `PdoMethodCacheProvider<T>`       |
| [`MockDomainObject<T,D>`](../java/org/tentackle/pdo/mock/MockDomainObject.java)         | the domain delegate of a `PersistentDomainObject`      | —                              |
| [`MockPersistentOperation<T>`](../java/org/tentackle/pdo/mock/MockPersistentOperation.java) | the persistence delegate of an `Operation`         | `OperationMethodCacheProvider<T>` |
| [`MockDomainOperation<T,D>`](../java/org/tentackle/pdo/mock/MockDomainOperation.java)   | the domain delegate of an `Operation`                  | —                              |

The type parameters mirror the ones of the real base classes: `T` is the PDO resp. operation interface, `P`/`D` the
concrete mock implementation class (self-referential, as in `AbstractPersistentObject<T,P>`).

### Why the method-cache providers

`PdoInvocationHandler` casts the persistence delegate to `PdoMethodCacheProvider` to obtain the per-PDO-type method
cache — every persistence delegate must supply one, but `PersistentObject` deliberately doesn't extend the provider
interface because that is an implementation detail. The mocks therefore implement it, backed by a static
`ConcurrentHashMap` keyed by `getEffectiveClass()`. `MockPersistentOperation` does the same for
`OperationInvocationHandler`/`OperationMethodCache`.

---

## `MockPersistentObject` — what actually works

This is the mock you use most, because most tests are about *domain* logic. It keeps just enough state to make a
PDO a well-formed object:

| Works                                                                          | Behavior                                                                                                   |
|--------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `getPdo()` / `me()` / `setPdo()`                                               | the PDO proxy this is a delegate for                                                                       |
| `getDomainContext()` / `setDomainContext()`                                    | plain field                                                                                                |
| `getSession()` / `setSession()`                                                | plain field; honors `setSessionImmutable(true)` by throwing `PersistenceException` on a change             |
| `getId()` / `setId()`                                                          | plain field; `getId()` returns the **absolute value**, mirroring the real layer's negative reserved IDs     |
| `getSerial()`, `getTableSerial()`, `getRootId()`, `getRootClassId()` + setters | plain fields (the setters are added by the mock, they are not part of `PersistentObject`)                   |
| `setImmutable()`, `setFinallyImmutable()`, `isImmutable()`, `isFinallyImmutable()` | real [immutability](../../../../tentackle-core/src/main/docs/immutable.md) flags; `ImmutableException` when making a finally-immutable object mutable again |
| `isRootEntityOf(component)`                                                    | real implementation, evaluated against `isRootEntity()`, `getId()` and `getClassId()`                      |
| `getPdoMethodCache()`                                                          | shared static cache per effective PDO class                                                                |

Permissive or empty defaults, so that a test isn't blocked by machinery it doesn't care about:

- `isWriteAllowed()`, `isViewAllowed()`, `isEditAllowed()` → `true`; `validate()`, `requestTokenLock()`,
  `releaseTokenLock()` → no-ops.
- `isRootEntity()`, `isEmbedded()`, `isSnapshot()`, `isCopy()`, `isTableSerialProvided()`, `isTokenLockProvided()`,
  `isNormTextProvided()`, `isRootIdProvided()`, `isRootClassIdProvided()` → `false`
  (override `isRootEntity()` in your mock if the domain logic branches on it — the defaults of
  `AbstractDomainObject` differ for root entities and components).
- `getSnapshots()` → empty list, `loadComponents()` → empty `IdentifiableMap`, `getEmbeddingParent()` / `copy()` →
  `null`, `discardSnapshot(s)` → no-ops.
- `isValidated()` → `!isModified()`, `toIdString()` → `classId:id`.

The **cached select variants delegate to their uncached counterparts** — `selectCached(id)`, `selectForCache(id)`
and `selectCachedOnly(id)` route to `select(id)`; `selectAllCached()` / `selectAllForCache()` to `selectAll()`;
`selectAnyCached(ids)` / `selectAnyForCache(ids)` to `selectAny(ids)`. Overriding a single `select…` method in your
mock therefore covers the [cache](../../../../tentackle-pdo/src/main/docs/cache.md) paths as well.

Everything else — `save()`, `persist()`, `delete()`, all `select…`/`reload…` methods, the
[token lock](../../../../tentackle-persistence/src/main/docs/locking.md) state, the
[snapshot](../../../../tentackle-pdo/src/main/docs/snapshot.md) methods, `getTableName()`, `getClassId()`,
`isNew()`, `isModified()`, `getSecurityResult()` and the modification-tracking queries — throws
`UnsupportedOperationException(UNSUPPORTED)`. Override in your mock subclass exactly what the code under test needs.

### The other three

- **`MockDomainObject`** keeps the PDO reference and routes `getDomainContext()`/`getSession()` to the PDO;
  `toGenericString()` falls back to `Object.toString()` and `isUniqueDomainKeyProvided()` returns `false`. The
  [domain key](../../../../tentackle-pdo/src/main/docs/domain-key.md) methods, `getSingular()`/`getPlural()` and
  `getPersistenceDelegate()` throw.
- **`MockPersistentOperation`** is the operation counterpart of `MockPersistentObject`: operation reference,
  session (with the same immutability check), domain context and the operation method cache work; the context-ID
  and delegate lookups throw.
- **`MockDomainOperation`** keeps the operation reference and routes `getDomainContext()`/`getSession()` to it;
  `getPersistenceDelegate()` throws.

---

## Writing a Mock

A mock is a normal implementation class: extend the matching `Mock…` base, implement the **generated interface**
of the layer you are replacing, and register it with the SPI annotation of that layer.

| Replacing                       | Extend                       | Annotate with                | Discovered as       |
|---------------------------------|------------------------------|------------------------------|---------------------|
| persistence half of a PDO       | `MockPersistentObject<T,P>`  | `@PersistentObjectService`   | `PersistentObject`  |
| domain half of a PDO            | `MockDomainObject<T,D>`      | `@DomainObjectService`       | `DomainObject`      |
| persistence half of an operation| `MockPersistentOperation<T>` | `@PersistentOperationService`| `PersistentOperation` |
| domain half of an operation     | `MockDomainOperation<T,D>`   | `@DomainOperationService`    | `DomainOperation`   |

All four annotations are [`@MappedService`](../../../../tentackle-common/src/main/docs/services.md)s, so the
`test-analyze` goal of the
[tentackle-maven-plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md) writes the
`META-INF/mapped-services/…` entry into `target/test-classes`, and the ordinary factory picks the mock up.

> **The mock must implement its declaring interface directly.** The `Mixin` locates the declaring interface via
> `getInterfaces()` on the implementation class — inheriting it from a base class is not enough.

### Testing domain logic

```java
// the mock is registered like any other persistence implementation
@PersistentObjectService(NumberRange.class)
public class NumberRangePersistenceMock extends MockPersistentObject<NumberRange, NumberRangePersistenceMock>
       implements NumberRangePersistence {

  @Serial
  private static final long serialVersionUID = 1L;

  // back the attributes the domain logic reads and writes;
  // everything else inherits "not implemented"
  private long begin;
  private long end;

  @Override public long getBegin()          { return begin; }
  @Override public void setBegin(long begin){ this.begin = begin; }
  @Override public long getEnd()            { return end; }
  @Override public void setEnd(long end)    { this.end = end; }
}
```

```java
// Pdo.create() builds the real proxy: real domain delegate, mocked persistence delegate
NumberRange range = Pdo.create(NumberRange.class);
range.setBegin(10);
range.setEnd(20);
assertEquals(range.size(), 10);
```

`Pdo.create(Class)` without a [domain context](../../../../tentackle-pdo/src/main/docs/domain-context-provider.md)
or session is the database-less entry point. Operations use `Pdo.createOperation(Class)` accordingly.

The four constructors of every `Mock…` class mirror the real base classes — `(pdo, context)`, `(pdo, session)`,
`(pdo)` and the no-arg one — so the factory finds whichever it needs. Provide all four in your mock; the
`Declare`/`MethodsImpl` wurblets and the archetype template do the same.

### Testing persistence logic

The mirror image: the **real** persistence implementation is paired with a mock domain delegate.

```java
@SuppressWarnings("exports")
@DomainObjectService(DemoEntity.class)
public class DemoEntityDomainMock extends MockDomainObject<DemoEntity, DemoEntityDomainMock>
       implements DemoEntityDomain {

  @Serial
  private static final long serialVersionUID = 1L;

  public DemoEntityDomainMock(DemoEntity pdo) { super(pdo); }
  public DemoEntityDomainMock()               { super(); }
}
```

This is how `tentackle-persistence` tests the class variables, the generated SQL, the root columns, the normtext
and the snapshot wiring without a connection — see that module's `src/test/README.md`.

### Injecting a delegate without the SPI

Both factories also accept a delegate instance directly, which skips registration entirely:

```java
Message message = Pdo.create(Message.class, myPersistenceDelegate);          // mock the persistence half
Message message = Pdo.create(Message.class, context, myDomainDelegate);      // mock the domain half
```

The same pair exists as `Pdo.createOperation(Class, PersistentOperation)` and
`Pdo.createOperation(Class, DomainContext, DomainOperation)`.

---

## Generating Mocks with the Wurblets

Writing the attribute plumbing by hand gets tedious for wide entities. The persistence
[wurblets](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md) can generate a mock
persistence implementation from the [model](../../../../tentackle-model/src/main/docs/model-definition.md): pass
`--mock` to `Declare`, `MethodsImpl` and `PdoRelations`.

```java
@PersistentObjectService(Message.class)
public class MessagePersistenceMock extends MockPersistentObject<Message, MessagePersistenceMock>
       implements MessagePersistence {

  // @wurblet declare Declare --mock

  // … the four constructors …

  // @wurblet methods MethodsImpl --mock

  // @wurblet relations PdoRelations --mock

  // hand-written stubs for whatever the domain logic calls on the persistence half
  @Override
  public String nextMessageNumber() {
    return "1000";
  }
}
```

In `--mock` mode the wurblets emit the *plain* variant of everything and skip the database machinery:

- **`Declare --mock`** — plain fields; no persisted/modified shadow fields.
- **`MethodsImpl --mock`** — plain getters and setters (no `assertMutable()`, no property-change events, no
  modification tracking); `is<Attribute>Modified()` returns `false`; `get<Attribute>Persisted()` returns the current
  value; `isRootEntity()` returns `true` for a root entity. No snapshot methods.
- **`PdoRelations --mock`** — relation fields (list relations are pre-initialized to an empty list, a
  `TrackedArrayList` for tracked relations), plain
  relation getters/setters and `is<Relation>Loaded()`; none of `loadComponents`, `insertPlainWithComponents`,
  `deletePlainWithComponents`, the referencing-class registration, `createNormText`, `validate`, `setImmutable`,
  `isModified`, the snapshot handling or the `PdoSelect…`/`PdoDelete…` anchors.

A complete worked example ships with the
[project archetype](../../../../tentackle-project-archetype/src/main/docs/myapp-walkthrough.md#13-testing):
`MessagePersistenceMock` plus the `MessageTest` that exercises `toDiagnosticString()` against it.

---

## The Mockito Alternative

Nothing forces you to write a mock class at all. Because the `Mock…` classes are plain concrete classes, Mockito
can subclass them and you can stub the accessors instead:

```java
MessagePersistence po = (MessagePersistence) mock(MockPersistentObject.class, withSettings().
        defaultAnswer(CALLS_REAL_METHODS).extraInterfaces(MessagePersistence.class));
when(po.getMessageNumber()).thenReturn("1000");
when(po.getMessageType()).thenReturn(MessageType.LOGOUT);
when(po.getText()).thenReturn("blah");

Message message = Pdo.create(Message.class, po);
```

`CALLS_REAL_METHODS` keeps the working parts of `MockPersistentObject` (session, context, id, method cache) intact,
`extraInterfaces` adds the generated persistence interface.

Two Mockito pitfalls worth remembering:

- `mock(Message.class)` creates a **plain Mockito proxy, not a Tentackle proxy** — the PDO machinery is absent.
- To mock a *concrete PDO instance*, wrap a real one:
  `Message message = mock(Message.class, delegatesTo(Pdo.create(Message.class)));`

---

## Using It in a Downstream Project

Normally you get the mocks for free through the test-support module:

```xml
<dependency>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-test-pdo</artifactId>
  <version>${tentackle.version}</version>
  <scope>test</scope>
</dependency>
```

Declare `tentackle-pdo-mock` explicitly only if you want the mocks *without* the database-backed test base classes
(the [BOM](../../../../tentackle-bom/src/main/docs/bom.md) manages the version and the `test` scope):

```xml
<dependency>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-pdo-mock</artifactId>
  <scope>test</scope>
</dependency>
```

### When *not* to use the mocks

The mocks answer "does my domain logic compute the right thing?", not "does my entity persist correctly?".
Anything that needs a live session — selects, inserts, cursors, caches, remoting, the lock manager, the
modification tracker — belongs in a test based on
[`tentackle-test-pdo`](../../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md), which stands up an
in-memory H2 database from the generated DDL. A mock that starts growing a fake database is a sign the test belongs
there instead.

---

## Related Documentation

- [PDO — Persistent Domain Objects](../../../../tentackle-pdo/src/main/docs/pdo.md) — the proxy/delegate pattern the mocks plug into.
- [Operations](../../../../tentackle-pdo/src/main/docs/operation.md) — what the `Mock*Operation` delegates stand in for.
- [Domain Layer](../../../../tentackle-domain/src/main/docs/domain.md) — the half tested with a persistence mock.
- [Persistence Layer](../../../../tentackle-persistence/src/main/docs/persistence.md) — the half tested with a domain mock.
- [tentackle-test-pdo](../../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md) — database-backed test base classes; re-exposes this artifact.
- [Services / ServiceFinder](../../../../tentackle-common/src/main/docs/services.md) — how a mock is discovered.
- [Tentackle Maven Plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md) — the `test-analyze` goal that indexes it.
- [Persistence Wurblets](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md) — the `--mock` option of `Declare`, `MethodsImpl` and `PdoRelations`.
- [MyApp Walkthrough](../../../../tentackle-project-archetype/src/main/docs/myapp-walkthrough.md#13-testing) — the generated example project's mock-based domain test.
