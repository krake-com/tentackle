# The `DomainContextProvider` — Creating PDOs and Operations in Context

## Overview and Motivation

Every piece of domain logic in a Tentackle application runs *somewhere*: in a
particular database session, on behalf of a particular tenant, possibly inside a
named task or aggregate. That "somewhere" is the
[`DomainContext`](../java/org/tentackle/pdo/DomainContext.java) — the object that
ties a [`PersistentDomainObject`](../java/org/tentackle/pdo/PersistentDomainObject.java)
(PDO) or [`Operation`](operation.md) to its session, its
[aggregate root context](pdo.md#aggregates-and-the-root-context), and its tenant.

The problem the **`DomainContextProvider`** solves is propagation. When domain code
on one entity needs to reach *another* entity — load related rows, create a new PDO,
invoke a service — that new object must end up in the **same** context: the same
session, the same tenant, the same transaction. Passing a `DomainContext` argument
through every call would be noise, and forgetting it would silently break tenant
scoping or transactional integrity.

[`DomainContextProvider`](../java/org/tentackle/pdo/DomainContextProvider.java) is the
small interface that makes the current context **ambient** for any object that has
one. It exposes that context and adds the convenience factory methods `on(...)` and
`op(...)` that create new PDOs and Operations *within it*, so domain logic stays
inside its context without ever naming it.

```java
public interface DomainContextProvider {
  DomainContext getDomainContext();

  default <T extends PersistentDomainObject<T>> T on(Class<T> clazz) { ... }   // create a PDO
  default <T extends Operation<T>>             T op(Class<T> clazz) { ... }    // create an Operation
  // + named-context overloads and isWithinContext(...) checks
}
```

---

## Who Provides a Context

`DomainContextProvider` is not something the application implements by hand — it is
inherited by the abstractions you already work with, so the methods are simply
*there*:

| Provider                                                                                                                   | Why it has a context                                                                                           |
|----------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| [`DomainObject`](../java/org/tentackle/pdo/DomainObject.java) (the domain half of every PDO)                               | `extends ... DomainContextProvider, SessionProvider` — so domain code on any entity can reach others.          |
| [`DomainOperation`](../java/org/tentackle/pdo/DomainOperation.java) (the domain half of every [`Operation`](operation.md)) | `extends ... DomainContextProvider, SessionProvider` — operations create PDOs in their own context too.        |
| [`Application`](../java/org/tentackle/app/Application.java)                                                                | `extends SessionProvider, DomainContextProvider` — the application's *entry* context, the root of propagation. |
| UI / RDC components (`PdoEditor`, `GuiProvider`, `PdoFinder`, …)                                                           | carry the context of what the user is currently editing.                                                       |

Because `getDomainContext()` is the single abstract method, every one of these gains
`on(...)`, `op(...)`, and `isWithinContext(...)` for free as `default` methods.

---

## Obtaining the First Context

Propagation has to start somewhere. The very first `DomainContext` is built from an
open [`Session`](../../../../tentackle-session/src/main/java/org/tentackle/session/Session.java)
— typically once, when the application logs in:

```java
DomainContext context = Pdo.createDomainContext(session);
```

[`Pdo.createDomainContext(...)`](../java/org/tentackle/pdo/Pdo.java) wraps the session
in a context created by the [`DomainContextFactory`](../java/org/tentackle/pdo/DomainContextFactory.java)
— a [`DefaultDomainContext`](../java/org/tentackle/pdo/DefaultDomainContext.java), unless the
application replaces the factory. From that initial context onward,
domain code never calls `createDomainContext` again — it uses `on(...)`/`op(...)`,
which clone or reuse the context it is already in. In a typical application the
[`Application`](../java/org/tentackle/app/Application.java) singleton holds this initial
context and hands it out via `getDomainContext()`. `AbstractApplication` creates it with an
immutable session (`Pdo.createDomainContext(session, true)`).

> Don't confuse this initial context with a [root context](pdo.md#aggregates-and-the-root-context):
> the latter is the context of an aggregate's root entity, shared by its components.

> A `DomainContext` *is a* [`SessionHolder`](../../../../tentackle-session/src/main/java/org/tentackle/session/SessionHolder.java):
> it carries the session in which its objects live, and therefore the transaction.
> Two objects sharing a context share a session; that is what makes "in the same
> context" mean "in the same transaction".

---

## Creating a PDO Within a Context

There are two equivalent spellings, and one is just sugar for the other.

### The explicit form — `Pdo.create`

```java
Invoice invoice = Pdo.create(Invoice.class, context);
List<Invoice> open = invoice.selectByCustomerId(customerId);
```

[`Pdo.create(Class, DomainContext)`](../java/org/tentackle/pdo/Pdo.java) delegates to
the [`PdoFactory`](../java/org/tentackle/pdo/PdoFactory.java) SPI, which assembles the
two-delegate proxy (persistence half + domain half) and attaches the given context.
Use this form at the boundary, where you hold a `DomainContext` directly but are not
yet *inside* a provider.

### The ambient form — `on(...)`

Inside any `DomainContextProvider` — that is, inside any domain implementation — write:

```java
Invoice invoice = on(Invoice.class);              // same context as 'this'
```

which is exactly:

```java
Invoice invoice = Pdo.create(Invoice.class, getDomainContext());
```

`on(...)` is "object new": it reads cleanly and, crucially, guarantees the new PDO
inherits **this object's** session, tenant, and context. This is how domain logic
reaches other entities while staying inside the current context. A worked example
from [pdo.md](pdo.md#me-and-on):

```java
@Override
public IngotCategory determineCategory() {
  for (IngotCategory category : on(IngotCategory.class).selectAllCached()) {
    if (category.isMatching(me())) {
      return category;
    }
  }
  return null;
}
```

Here `IngotDomainImpl` (the domain half of `Ingot`) creates an `IngotCategory` PDO in
its own context, queries with it, and compares against `me()` (the whole `Ingot`
proxy) — all without ever mentioning a session or a context object.

---

## Creating an Operation Within a Context

An [`Operation`](operation.md) is Tentackle's home for behavior that is *not* tied to
one entity — sending mail, running a batch, dispatching a command. It reuses the same
PDO machinery (proxy, context, SPI injection, TRIP remoting) and is created the same
way, through its own factory:

```java
// explicit
Pdo.createOperation(MailOperation.class, context)
   .send("plsbl", recipient, "test", "this is a test from PLSBL");

// ambient, inside a provider
op(MailOperation.class).send(...);
```

`op(...)` mirrors `on(...)`: it calls
[`OperationFactory.create(clazz, getDomainContext())`](../java/org/tentackle/pdo/OperationFactory.java),
so the operation runs in the caller's session, transaction, and tenant. Because an
operation carries a context just like a PDO, it can itself call `on(...)`/`op(...)`
to drive entities and further operations — all in the one context.

---

## Named Sub-Contexts

Both `on` and `op` have an overload that takes a **context name**:

```java
Ingot ingot = on(Ingot.class, "edit");        // a fresh named sub-context
MailOperation mail = op(MailOperation.class, "import");
```

These clone the current context into a **named sub-context**
(`getDomainContext().clone(contextName)`) before creating the object. A named context
describes a distinct part of the application — a module, task, or purpose — and serves
two roles in domain logic:

- **Method gating.** Methods annotated with
  [`@WithinContext`](../java/org/tentackle/pdo/WithinContext.java) /
  [`@NotWithinContext`](../java/org/tentackle/pdo/NotWithinContext.java) are allowed or rejected by an interceptor that checks the live
  context name, so certain operations are only callable inside (or outside) a given
  task context.
- **Lock isolation.** A PDO locked in one named context cannot be locked, persisted,
  or unlocked by the same user from a *different* named context — preventing a user
  from stepping on their own edit session in another module.

The provider also exposes the matching predicates —
`isWithinContext(String name)` and `isWithinContext(long contextId, int contextClassId)`
— which domain code can consult directly, and which the interceptors above are built
on.

---

## Why the Context Is Central to Domain Logic

The provider's small surface (`getDomainContext` + `on`/`op`) is the seam through which
the framework's core guarantees propagate automatically. Every object a piece of domain
logic creates with `on(...)`/`op(...)` inherits, *by construction*:

1. **The session and transaction.** A context is a `SessionHolder`; staying in-context
   keeps work in one transaction, so an aggregate and everything it touches commit or
   roll back together.

2. **The aggregate root context.** Because each root entity maintains its own
   [root context](pdo.md#aggregates-and-the-root-context), creating and persisting
   components in-context is what lets the framework enforce DDD aggregate boundaries —
   and what backs the [`rootId`/`rootClassId`](root-columns.md) reasoning used by
   security and immutability.

3. **Tenant / logical-data-space scoping.** An application-specific context (built via a
   [`DomainContextFactory`](../java/org/tentackle/pdo/DomainContextFactory.java)) can
   carry tenant ids; a `selectAll()` then returns only that tenant's rows
   ([logical data spaces](pdo.md#logical-data-spaces-and-multi-tenancy)). `on(...)`
   propagates that scoping silently, so cross-tenant leaks can't arise from forgetting
   to pass it.

4. **Authorization.** The [`SecurityManager`](../java/org/tentackle/security/SecurityManager.java)
   evaluates permissions *within a domain context* ([security.md](security.md)); objects
   created in-context are checked under the right grantee and scope, and
   `DomainContext.assertPermissions()` is invoked on (re)attachment.

The pattern, then, is: obtain one context at the application boundary, and from there
let domain logic create everything it needs with `on(...)` and `op(...)`. The context
is never threaded through signatures and never re-derived — it simply flows with the
work, which is exactly why every PDO and operation ends up in the right session, tenant,
aggregate, and permission scope.

---

## Summary

| Member                 | Purpose                                                                                               |
|------------------------|-------------------------------------------------------------------------------------------------------|
| `getDomainContext()`   | The single abstract method — the ambient context of this object.                                      |
| `on(Class<T>)`         | Create a PDO of another type in **this** context (sugar for `Pdo.create(clazz, getDomainContext())`). |
| `on(Class<T>, String)` | Same, but in a fresh **named** sub-context.                                                           |
| `op(Class<T>)`         | Create an [`Operation`](operation.md) in this context.                                                |
| `op(Class<T>, String)` | Same, but in a named sub-context.                                                                     |
| `isWithinContext(...)` | Test the current named/object context — the basis of `@WithinContext` gating.                         |

### See also

- [pdo.md → The DomainContext and DDD](pdo.md#the-domaincontext-and-ddd)
- [operation.md](operation.md) — behavior without an entity
- [root-columns.md](root-columns.md) — how the root context is recorded on component rows
- [security.md](security.md) — permission evaluation within a context
