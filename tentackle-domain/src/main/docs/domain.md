# Tentackle Domain — The Domain Logic Layer Base

## Overview and Motivation

The `tentackle-domain` module (Java package `org.tentackle.domain`) provides the **base classes for the domain
(business-logic) half of a Persistent Domain Object**. It is the deliberately thin companion to
[`tentackle-persistence`](../../../../tentackle-persistence/src/main/docs/persistence.md): where that module
implements the *persistence* half of a PDO, this one implements the *domain* half.

Recall the PDO pattern (see [pdo.md](../../../../tentackle-pdo/src/main/docs/pdo.md)). A persistent domain object
is a dynamic proxy that emulates multiple inheritance by combining **two injected delegates**:

- a **domain delegate** — the application's business logic, and
- a **persistence delegate** — the database logic.

This module supplies the abstract base every *domain delegate* extends:
[`AbstractDomainObject`](../java/org/tentackle/domain/AbstractDomainObject.java) for entities and
[`AbstractDomainOperation`](../java/org/tentackle/domain/AbstractDomainOperation.java) for operations. The
application's handwritten business classes extend these to add behavior, validation rules, domain keys and
derived values — the part of an application that expresses *what the business means*, kept rigorously separate
from *how it is stored*.

The cleanest way to see the symmetry is side by side:

| Concern              | Domain half (this module)                          | Persistence half                                           |
|----------------------|----------------------------------------------------|------------------------------------------------------------|
| Entity base          | `AbstractDomainObject`                             | `AbstractPersistentObject`                                 |
| Operation base       | `AbstractDomainOperation`                          | `AbstractPersistentOperation`                              |
| Discovery annotation | `@DomainObjectService` / `@DomainOperationService` | `@PersistentObjectService` / `@PersistentOperationService` |
| Module dependency    | only `tentackle-pdo`                               | `tentackle-pdo` **and** `tentackle-database`               |

The asymmetry in dependencies is the whole point: **the domain layer knows nothing about JDBC, SQL, or
remoting.** It depends only on [`tentackle-pdo`](../../../../tentackle-pdo/src/main/docs/pdo.md) (the contracts and
the `DomainContext`). The persistence layer is agnostic of the domain layer and vice versa; they meet only at the
PDO proxy. This is what lets the persistence layer be remoting-capable and dialect-aware without the business code
ever caring.

### Design principles

- **Thin base, rich subclasses.** The base classes carry almost no state — just a back-reference to the PDO
  proxy — and mostly *delegate* context/session access to the persistence delegate. All real business logic lives
  in the application's subclasses.
- **Strict layer separation.** The module depends solely on `tentackle-pdo`; it cannot reach the database. Domain
  code reaches persistence only through the PDO proxy and the `DomainContext`.
- **Delegates, not instances, travel.** Both base classes are `@TripReferencable(NEVER)` — only the PDO proxy is
  referenced across JVMs, never the domain implementation directly.
- **Convention-based discovery.** Domain implementations are bound to their PDO interface by annotation
  (`@DomainObjectService` / `@DomainOperationService`) and wired in at runtime through the SPI, never by `new`.

---

## Key Concepts

### AbstractDomainObject

[`AbstractDomainObject<T,D>`](../java/org/tentackle/domain/AbstractDomainObject.java) is the base domain
implementation of an entity PDO, where `T` is the PDO interface and `D` the concrete domain class. It implements
`DomainObject<T>` (the PDO domain contract) and `EffectiveClassProvider<T>`, and is `Serializable` but
`@TripReferencable(NEVER)`.

Its job is small and well-defined:

- **PDO back-reference.** It holds the `pdo` proxy it is a delegate for, exposed via `getPdo()` / `me()` (and set
  by the linker via `setPdo()`). `me()` is the idiomatic way for business code to call back through the proxy so
  that interceptors and the persistence delegate participate.
- **Context/session access.** `getDomainContext()` and `getSession()` simply forward to the persistence delegate —
  the domain object reaches the [session](../../../../tentackle-session/src/main/docs/session.md) *through* the
  PDO, never directly.
- **Effective class.** `getEffectiveClass()` / `getEffectiveSuperClasses()` forward to the proxy, supporting
  Tentackle's effective-class (entity-substitution) mechanism. For a PDO the effective class is the entity
  *interface* (e.g. `Customer`), not this delegate's class — so business code should `instanceof` the entity
  interface and key maps by `getEffectiveClass()`, never `getClass()`. See
  [Reflection → `getClass()`, `getEffectiveClass()` and `instanceof`](../../../../tentackle-core/src/main/docs/reflect.md#getclass-geteffectiveclass-and-instanceof).
- **Naming.** `getSingular()` / `getPlural()` resolve human-readable entity names via `PdoUtilities` (falling back
  to the simple class name and a default English pluralization rule).
- **Domain key.** The *unique domain key* — the business identity of a **root entity** (as opposed to the
  technical object id) — is exposed through `getUniqueDomainKey()` / `setUniqueDomainKey()` /
  `getUniqueDomainKeyType()` / `findByUniqueDomainKey()` and the flag `isUniqueDomainKeyProvided()`. The base
  implementations throw a `DomainException` (and assert root-entity), so an entity that has a domain key
  **overrides** them; `assertRootEntity()` enforces that these are only used on roots. The whole concept —
  declaration, generated key types, and framework usage — is covered in
  [Domain Keys](../../../../tentackle-pdo/src/main/docs/domain-key.md).
- **String representation.** `toString()` returns the domain key for a root entity, or the singular name for a
  component, and is carefully written never to throw (it falls back to `toGenericString()` — class plus
  id/serial — on error), because it is used for logging and debugging.

### AbstractDomainOperation

[`AbstractDomainOperation<T,D>`](../java/org/tentackle/domain/AbstractDomainOperation.java) is the analogous base
for **operations** — units of business logic that are not a single entity but a (often complex, transactional)
procedure (see [operation.md](../../../../tentackle-pdo/src/main/docs/operation.md)). It implements
`DomainOperation<T>` / `EffectiveClassProvider<T>`, holds the `operation` proxy back-reference (`me()` /
`getOperation()` / `setOperation()`), and likewise forwards `getDomainContext()` / `getSession()` to the
persistence delegate. It is the domain-side counterpart to `AbstractPersistentOperation`.

### Delegate linking

When a PDO proxy is created, its domain delegate must be connected back to the proxy.
[`DefaultDomainDelegateLinker`](../java/org/tentackle/domain/DefaultDomainDelegateLinker.java)
(`@Service(DomainDelegateLinker.class)`) is the SPI implementation that does this: `linkDomainObject(pdo, delegate)`
calls `setPdo()` on the `AbstractDomainObject`, and `linkDomainOperation(...)` calls `setOperation()` on the
`AbstractDomainOperation`. This is the runtime glue that turns a freshly instantiated domain implementation into a
working delegate of its proxy.

---

## Built-in Domain Implementations

Mirroring the built-in persistence implementations in `tentackle-persistence`, this module ships the **domain
half** of the framework's own infrastructure PDOs. Each is annotated `@DomainObjectService(...)` so it is
discovered as the domain delegate of the corresponding PDO interface:

- **Number sources** (`org.tentackle.domain.ns`) —
  [`NumberPoolDomainImpl`](../java/org/tentackle/domain/ns/NumberPoolDomainImpl.java) and
  [`NumberRangeDomainImpl`](../java/org/tentackle/domain/ns/NumberRangeDomainImpl.java) provide the business
  behavior for unique-number allocation; their persistence counterparts live in `tentackle-persistence`'s
  `ns` package.
- **Security rules** (`org.tentackle.domain.security`) —
  [`SecurityDomainImpl`](../java/org/tentackle/domain/security/SecurityDomainImpl.java) provides the domain
  behavior for access-control rules, paired with `SecurityPersistenceImpl`.

These are the canonical examples of how an application's own domain classes are structured: extend
`AbstractDomainObject<Pdo, Impl>`, implement the PDO's domain interface, and annotate with
`@DomainObjectService(Pdo.class)`.

---

## Package Layout

| Package                         | Contents                                                                                                |
|---------------------------------|---------------------------------------------------------------------------------------------------------|
| `org.tentackle.domain`          | The core: `AbstractDomainObject`, `AbstractDomainOperation` and the `DefaultDomainDelegateLinker`.      |
| `org.tentackle.domain.ns`       | Domain implementations for number sources (number pool / range).                                        |
| `org.tentackle.domain.security` | Domain implementation for security rules.                                                               |
| `org.tentackle.domain.service`  | The [`Hook`](../java/org/tentackle/domain/service/Hook.java) (`ModuleHook`) for i18n bundle resolution. |

---

## How It Fits Together

A PDO proxy combines a domain delegate (from this module's subclasses) and a persistence delegate (from
`tentackle-persistence`):

```text
                    PDO proxy  (e.g. Customer)
                   ┌───────────┴────────────┐
        domain delegate                persistence delegate
   AbstractDomainObject            AbstractPersistentObject
   (tentackle-domain)              (tentackle-persistence)
        │                                   │
   business logic,                    SQL / connection /
   domain key, validation,            transactions / remoting
   derived values                            │
        │  getSession()/getDomainContext()   ▼
        └──────────── forwarded ───────► Session / DomainContext
```

At runtime:

1. The PDO factory instantiates the domain implementation (located by `@DomainObjectService`) and the persistence
   implementation (located by `@PersistentObjectService`).
2. `DefaultDomainDelegateLinker` links the domain delegate back to the proxy (`setPdo`), and the persistence side
   does likewise.
3. Business code calls flow into `AbstractDomainObject` subclasses; when they need to persist, query, or learn the
   session/context, they call back through `me()` / `getPersistenceDelegate()`, which routes to the persistence
   delegate — local or remote, identically.

Because the domain object never holds a connection or SQL and reaches everything through the proxy, the *same*
business code runs unchanged in a 2-tier client, an n-tier client, or inside the application server.

The PDO interfaces and the per-entity domain method signatures are declared in the
[model](../../../../tentackle-model/src/main/docs/model-definition.md); the application fills in the behavior by
extending the base classes here.

---

## Module Dependencies

`tentackle-domain` is intentionally one of the lightest modules in the stack:

- It **requires transitive** only [`tentackle-pdo`](../../../../tentackle-pdo/src/main/docs/pdo.md) — the PDO
  contracts (`DomainObject`, `DomainOperation`, `DomainContext`, `DomainDelegateLinker`) it implements. It has
  **no** dependency on `tentackle-database`, `tentackle-sql` or any JDBC API.
- It **opens** `org.tentackle.domain`, `org.tentackle.domain.ns` and `org.tentackle.domain.security` to
  `org.tentackle.core` for TRIP serialization.
- It **provides** a `ModuleHook` service (`org.tentackle.domain.service.Hook`) for i18n.
- Through `@Service` it registers `DefaultDomainDelegateLinker` as the `DomainDelegateLinker`, and through
  `@DomainObjectService` it registers the built-in number-source and security domain implementations.

## Unit Testing Domain Logic

Domain logic is tested without a database by pairing the real domain delegate with a **mock persistence
delegate** from [`tentackle-pdo-mock`](../../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md#the-mock-delegates)
and letting the ordinary factory build the PDO. This module's own tests (`src/test/java`) follow that recipe
and double as worked examples:

```java
// the mock is registered like any other persistence implementation
@PersistentObjectService(NumberRange.class)
public class NumberRangePersistenceMock extends MockPersistentObject<NumberRange, NumberRangePersistenceMock>
       implements NumberRangePersistence {
  // back the attributes the domain logic reads and writes; everything else inherits "not implemented"
}

// Pdo.create() builds the real proxy: real domain delegate, mocked persistence delegate
NumberRange range = Pdo.create(NumberRange.class);
range.setBegin(10);
range.setEnd(20);
assertEquals(range.size(), 10);
```

Two things are worth knowing:

- Both delegates must **implement their declaring interface directly** (`NumberRangeDomain` resp.
  `NumberRangePersistence`). The `Mixin` locates the declaring interface via `getInterfaces()` on the
  implementation class, so inheriting it from a base class is not enough.
- The tests run on the **class path**, not patched into the module (`useModulePath=false` in the surefire
  configuration). A patched module resolves `META-INF/mapped-services/*` from the patch directory alone, which
  would hide this module's own domain implementations behind the ones registered by the tests.

Tests that need a live database belong in
[`tentackle-test-pdo`](../../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md) instead.

---

## Related Documentation

- [PDO](../../../../tentackle-pdo/src/main/docs/pdo.md) — the persistent domain object pattern and the domain/persistence delegate split this module implements.
- [tentackle-test-pdo](../../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md) — the mock delegates and the database-backed test base classes.
- [Operations](../../../../tentackle-pdo/src/main/docs/operation.md) — operations backed by `AbstractDomainOperation`.
- [Persistence](../../../../tentackle-persistence/src/main/docs/persistence.md) — the persistence half that pairs with this module's domain half.
- [Session](../../../../tentackle-session/src/main/docs/session.md) — the session/context the domain object reaches through the PDO.
- [Model Definition](../../../../tentackle-model/src/main/docs/model-definition.md) — where PDO interfaces and their domain method signatures are declared.
- [Services / ServiceFinder](../../../../tentackle-common/src/main/docs/services.md) — the SPI mechanism behind `@DomainObjectService` and the delegate linker.
