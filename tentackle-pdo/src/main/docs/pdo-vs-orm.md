# PDOs vs. Traditional ORMs (Hibernate / JPA)

## Overview

On the surface a [PDO](pdo.md) and a JPA `@Entity` look similar: both map a Java
object to a database row, both let you navigate relations as object references,
both validate and persist. The difference is **what kind of thing the object
actually is at runtime**.

A JPA entity is a *managed POJO*. It only behaves like a persistent object while
it is attached to an `EntityManager` and that `EntityManager`'s **persistence
context** is alive. Outside that window it is a plain — and dangerous — bag of
fields: half-initialised, throwing `LazyInitializationException` the moment you
touch a relation that was never fetched.

A **PDO is a real object**. It carries everything it needs to fulfil its contract
— persistence delegate, domain delegate, session, context — *inside itself*. No
external manager owns it, no lifecycle scope governs it. It is created, used,
passed around, and garbage-collected exactly like any other Java object. There is
no "attached" and "detached"; there is just the object, for as long as something
references it.

This document explains that difference and why it matters, concept by concept.

---

## The Core Difference: Who Owns the Object?

This is the single distinction from which everything else follows.

### JPA: the EntityManager owns the entity

In JPA the `EntityManager` maintains a **persistence context** — a first-level
cache, also called the *identity map*. While an entity is *managed*, the
persistence context **holds a strong reference to it**:

```
   EntityManager  ──owns──▶  PersistenceContext (identity map)
                                   │  strong references
                  ┌────────────────┼────────────────┐
                  ▼                 ▼                ▼
              Customer #1       Invoice #7      InvoiceLine #42
              (managed)         (managed)        (managed)
```

The entity is alive in the *persistence sense* only as long as that map references
it. The map gives JPA its powers — automatic dirty checking, write-behind
flushing, guaranteed reference equality within a context — but it also means:

- The entity **cannot be garbage-collected** while the persistence context that
  manages it is open, because the context strongly references it.
- Everything depends on the **lifecycle** of that context: it is opened at the
  start of a transaction or a web request (e.g. *Open Session In View*, a
  `@PersistenceContext` bound to a JTA transaction, a Spring
  `OpenEntityManagerInViewInterceptor`) and closed at the end. When it closes,
  every entity it managed is **detached**.

### Tentackle: nobody owns the PDO — it owns what it needs

A PDO points *outward* to its [`DomainContext`](pdo.md) (and through it to a
`Session`). The session does **not** point back. There is no identity map, no
managed-object set, no per-session registry of live PDOs:

```
   Customer  ──▶ DomainContext ──▶ Session ──▶ connection / TRIP channel
   Invoice   ──▶ DomainContext ──▶ Session
   InvoiceLine ▶ (root context of its Invoice)

   ▲ the Session holds NO reference back to any of these objects
```

You can confirm this in the source: `AbstractPersistentObject` keeps a reference
to its session holder (`getSessionHolder()` / `getDomainContext()`), and the only
back-references it ever stores are the `WeakReference`s used for
[snapshots](snapshot.md). The [`Session`](../../../../tentackle-session/src/main/java/org/tentackle/session/Session.java)
keeps no list of the PDOs loaded through it.

The consequence is the whole point of the pattern:

> A PDO is reachable only by the ordinary Java references your application holds to
> it. When you stop referencing it, it becomes eligible for garbage collection —
> just like a `String`, an `ArrayList`, or any POJO you wrote yourself. Its session
> staying open does **not** keep it alive.

---

## Garbage Collection: PDOs Behave Like Real Objects

Because no manager retains them, PDOs participate in normal JVM memory management.

### JPA: the persistence context is a memory liability

A long-running persistence context is a classic JPA footgun. Every entity you
`find()` or query is added to the identity map and pinned there until the context
closes. A batch that loads a million rows in one transaction will accumulate a
million managed entities and exhaust the heap, which is why JPA batch code must
periodically call `em.flush()` **and** `em.clear()` — manually evicting entities
from the identity map so the GC can reclaim them. The framework's bookkeeping has
become *your* problem.

```java
// JPA batch: you must manually defeat the identity map
for (int i = 0; i < total; i++) {
    Customer c = process(...);
    em.persist(c);
    if (i % 1000 == 0) {
        em.flush();
        em.clear();   // <-- detach everything so the heap doesn't blow up
    }
}
```

### Tentackle: nothing accumulates

A PDO is collected the moment your code drops it, transaction open or not. A
Tentackle batch loops over a cursor and lets each PDO fall out of scope; there is
no identity map filling up behind your back, so there is nothing to `clear()`:

```java
// Tentackle batch: PDOs are GC'd as soon as the loop moves on
try (ScrollableResource<Customer> cursor =
         Pdo.create(Customer.class, context).selectAllAsCursor()) {
  while (cursor.next()) {
    cursor.get().process();   // each PDO is unreferenced once the loop advances → GC-eligible
  }
}
```

The mental model is the one you already have for ordinary Java:

| Question | Plain Java object | JPA managed entity | Tentackle PDO |
|----------|-------------------|--------------------|---------------|
| What keeps it alive? | A reference to it | A reference **or** the open persistence context | A reference to it |
| When is it collected? | When unreferenced | When unreferenced **and** detached/context closed | When unreferenced |
| Can a framework pin it in memory? | No | Yes (identity map) | No |
| Do you ever "evict" it manually? | No | Yes (`em.clear()`) | No |

---

## Lifecycle: Entity States vs. "It's Just an Object"

### JPA has four entity states

A JPA entity is always in one of four states, and most JPA bugs are really
*state* bugs:

| State | Meaning | Touching a lazy relation… |
|-------|---------|---------------------------|
| **Transient** / new | Created with `new`, unknown to any context | works (nothing lazy yet) |
| **Managed** / persistent | Attached to an open persistence context | works — context fetches on demand |
| **Detached** | Was managed, but the context closed | **throws `LazyInitializationException`** |
| **Removed** | Scheduled for deletion on flush | undefined |

The same object reference means different things at different times. A `Customer`
returned from a service method may be managed or detached depending on *where the
transaction boundary was*, and the caller usually can't tell by looking. This is
the root cause of the most infamous JPA error.

### Tentackle has no entity states

A PDO is simply an object. There is no "managed" vs. "detached"; the question
never arises, because the PDO's persistence delegate always knows how to obtain
missing data — from a local JDBC connection or, transparently, from a middle-tier
server over [TRIP](../../../../tentackle-core/src/main/docs/trip.md). The PDO's
ability to load a relation does not depend on being inside some open scope.

```java
Customer c = service.loadCustomer(id);   // returned from anywhere, any tier
// ... arbitrary time later, no "transaction" or "session" ceremony in sight ...
List<Invoice> invoices = c.getInvoices();   // just works — fetched on demand
```

> **There is no `LazyInitializationException` in Tentackle. The exception type does
> not exist.** A relation can be navigated at any time, on any tier, because the
> object itself — not an ambient context — owns the means to fetch it. See
> [pdo.md → Write the Domain Logic Once, Run It Anywhere](pdo.md#write-the-domain-logic-once--run-it-anywhere).

---

## No Lifecycle Scope to Manage

Because the EntityManager's persistence context is the unit of work, a JPA
application is forced to **bind that lifecycle to something** and then defend the
boundary:

- **Web request** — *Open Session In View* keeps the context open for the whole
  HTTP request so the view layer can still touch lazy relations. It is widely
  considered an anti-pattern (it holds a DB connection for the entire request and
  hides N+1 queries) but is hard to avoid precisely *because* detached entities are
  unusable.
- **Transaction** — the cleanest scope, but then anything that escapes the
  transaction (returned to a controller, put on a queue, cached) is detached and
  must be re-attached (`merge`) or pre-initialised with fetch joins / DTOs.
- **Conversation / extended persistence context** — frameworks add stateful scopes
  (`@ConversationScoped`, extended `@PersistenceContext`) to stretch the context
  across multiple requests, with their own concurrency and memory hazards.

Every one of these exists to answer the question *"how long does the entity stay
usable?"* — a question that only exists because usability is tied to an external
context's lifecycle.

In Tentackle the question is meaningless. A PDO is usable for as long as you hold a
reference to it, the same as any object. There is no scope to open, bind, defend,
or remember to close. A [`Session`](../../../../tentackle-session/src/main/docs/session.md)
*is* a resource (it wraps a connection or a remote channel), but it is not a
persistence context: closing it does not "detach" your PDOs, and keeping it open
does not pin them in memory. A read-only [cached](cache.md) PDO can even have its
session swapped for a thread-local one and be shared, immutable, across every
thread in the JVM — something a stateful, context-bound JPA entity could never do.

---

## OOP: PDOs Restore Real Object-Orientation

The deepest difference is philosophical, and it is where the *Domain* in
Persistent **Domain** Object earns its name.

### JPA entities tend toward the Anemic Domain Model

A JPA entity is *data*. Behaviour lives elsewhere — in stateless `@Service` beans,
`@Repository` classes, and transaction scripts that fetch entities, mutate their
fields through setters, and flush them. Martin Fowler named this the
[Anemic Domain Model](https://martinfowler.com/bliki/AnemicDomainModel.html): an
object graph with getters and setters but no behaviour, which "is contrary to the
basic idea of object-oriented design, which is to combine data and process
together." The reasons are partly cultural but partly structural:

- An entity that is only safe to use while *managed* can't reliably offer rich
  behaviour, because its own methods might hit a lazy relation and throw when
  detached. So behaviour migrates out, to services that run inside a transaction.
- Java's **single inheritance** forces the persistence concern (the mapping, the
  `@Id`, the lifecycle callbacks) and the business concern into the *same* class
  hierarchy, so they fight over the one extends slot. Most teams resolve the fight
  by keeping the entity thin and pushing logic into services.

### PDOs put behaviour back on the object

A PDO is an object in the full OOP sense: it owns its data **and** its behaviour.
`ingot.move(fromYard, toYard, …)`, `invoice.approve()`, `customer.creditLimit()`
are methods *on the entity*, not procedures in a service that take the entity as an
argument. The domain logic reads as messages sent to objects, which is what
object-orientation is supposed to look like.

Two pieces of the architecture make this possible where JPA makes it hard:

1. **Emulated multiple inheritance.** Tentackle keeps the persistence concern and
   the domain concern in *two separate implementation classes* and weaves them into
   one object via a dynamic proxy (see
   [pdo.md → Anatomy of a PDO](pdo.md#anatomy-of-a-pdo)). The domain class is free
   to extend whatever domain base it likes; it never competes with the persistence
   base for the inheritance slot. Rich behaviour and clean mapping coexist instead
   of crowding each other out.

2. **The object is always usable.** Because a PDO never detaches, its methods can
   freely navigate relations and call persistence operations on `me()` — the whole
   PDO — from inside domain code. Behaviour can safely live *on* the entity because
   the entity is never in a degraded state.

### The "real object" test

A good litmus test for whether something is a real object: *can you treat it like
one without thinking about the framework?*

```java
// PDO: reads like ordinary OO code. No EntityManager, no transaction in sight.
if (ingot.isDeliverable()) {
  ingot.deliver(calloff);
}
for (PickupOrder po : ingot.getPickups()) {   // navigate the aggregate freely
  total += po.getWeight();
}
```

```java
// JPA equivalent must constantly account for the manager and the context:
Ingot ingot = em.find(Ingot.class, id);              // is the context open?
// ingot.getPickups() here may throw if detached, or trigger N+1 if managed,
// so logic moves into a @Transactional service and/or we fetch-join eagerly:
deliveryService.deliverIfPossible(em, ingot, calloff);
```

---

## No DTOs, No Merge, No Fetch Plans

A cluster of JPA practices exists purely to work around managed/detached entities;
none of them have a Tentackle counterpart:

| JPA mechanism | Why it exists in JPA | Tentackle equivalent |
|---------------|----------------------|----------------------|
| **DTOs** | Detached entities are unsafe to expose to the view/remote tier (lazy relations throw), so you copy data into transport objects | None. The PDO *is* the transport object — it travels between JVMs over [TRIP](../../../../tentackle-core/src/main/docs/trip.md) and stays fully functional on arrival |
| **`merge()` / re-attach** | A detached entity must be merged back into a context before it can be saved | None. A PDO is never detached; `persist()` always works |
| **Fetch joins / `@EntityGraph` / fetch plans** | You must predict, before the context closes, every relation the caller will touch | None. Any relation can be fetched on demand at any time, on any tier |
| **`OpenSessionInView`** | Keep the context open through rendering so the view can lazy-load | None. The view holds real objects |
| **`em.clear()` in batches** | Stop the identity map from exhausting the heap | None. PDOs are GC'd as they go out of scope |

Writing once and running anywhere — client, middle tier, or server — falls out of
the same property: the domain logic is on the object, the object works everywhere,
so there is nothing to split into "client DTO + server service."

---

## Transactions: Demarcation vs. Object Lifecycle

A subtle but important consequence: in JPA the transaction and the persistence
context are intertwined — the context typically *is* the transaction (or outlives
it as an extended context), and entity usability is tied to it. In Tentackle the
two are cleanly separated:

- A [`@Transaction`](../java/org/tentackle/pdo/Transaction.java) is pure
  **demarcation**: it opens a DB transaction if none is running, commits on normal
  return, rolls back on exception, and joins an enclosing transaction if one
  exists. It says nothing about object lifetime.
- A PDO's lifetime is governed by **references**, as shown throughout this
  document.

So a PDO can be created before a transaction, persisted inside one, and read after
it commits, all as the same live object — there is no "now it's detached" moment
when the transaction ends. The transaction controls *the database*, not *your
objects*.

---

## What You Give Up (An Honest Accounting)

The identity map that causes JPA's lifecycle pain also buys real conveniences. A
fair comparison names them:

- **Automatic dirty checking / write-behind.** JPA notices field changes on managed
  entities and flushes them without an explicit save. Tentackle tracks modification
  per attribute too (`isWeightModified()`), but you call `persist()`/`save()`
  explicitly. Many would call the explicit save a feature, not a loss — the write is
  where you put it.
- **Guaranteed reference identity within a context.** JPA guarantees that two
  `find()`s of the same id in one context return the *same* instance. Tentackle does
  not maintain a per-session identity map, so two independent `select()`s yield two
  instances of equal identity (`equals`/`compareTo`), not necessarily `==`. Where
  shared identity is wanted, the explicit [`PdoCache`](cache.md) provides it — as an
  opt-in, model-driven choice rather than an always-on global map.
- **A vast ecosystem.** JPA/Hibernate has enormous tooling, documentation, and
  hiring familiarity. Tentackle's model is more cohesive but less widely known.

The trade Tentackle makes is deliberate: give up the implicit, always-on identity
map — and the lifecycle ceremony that comes with it — in exchange for objects that
behave like objects.

---

## Summary

| Dimension | JPA / Hibernate entity | Tentackle PDO |
|-----------|------------------------|---------------|
| What is it at runtime? | A POJO managed by an `EntityManager` | A self-contained real object |
| Who keeps it alive? | A reference **or** the open persistence context (identity map) | Only your references to it |
| Garbage collection | Pinned by the context until detached/cleared | Collected when unreferenced, like any object |
| Lifecycle states | transient / managed / detached / removed | none — it just exists |
| Lazy relation after scope ends | `LazyInitializationException` | always works (no such exception exists) |
| Lifecycle scope to manage | transaction / web request / conversation | none |
| Behaviour location | services (anemic entity) | on the object (rich domain) |
| Inheritance | single slot shared by mapping + logic | multiple inheritance emulated via proxy |
| Cross-tier transfer | DTOs + merge + fetch plans | the PDO itself, fully functional |
| Transaction ↔ object | intertwined | cleanly separated |
| Identity map | always on, implicit | opt-in via [`PdoCache`](cache.md) |

The throughline: **a JPA entity is only fully itself while an `EntityManager` is
holding it; a PDO is fully itself for as long as any reference does — which is the
defining property of an ordinary Java object.**

## See Also

- [pdo.md](pdo.md) — the PDO pattern, the `DomainContext`, and emulated multiple
  inheritance in depth.
- [cache.md](cache.md) — the opt-in identity map / read model (`PdoCache`).
- [snapshot.md](snapshot.md) — editable copies of immutable/cached PDOs.
- [trip.md](../../../../tentackle-core/src/main/docs/trip.md) — the remoting that
  lets a PDO stay fully functional on any tier.
- [session.md](../../../../tentackle-session/src/main/docs/session.md) — what a
  `Session` is (a resource), and crucially what it is *not* (a persistence context).
