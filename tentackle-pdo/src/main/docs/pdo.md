# PDO — Persistent Domain Objects

## Overview and Motivation

The **Persistent Domain Object** (PDO) is the central abstraction of a Tentackle
application. A PDO is an entity — a `Customer`, an `Invoice`, an `Ingot` — that
unites two concerns that the framework deliberately keeps apart:

- **Persistence** — how the object is mapped to and from the database, selected,
  saved, locked, and transported between JVMs.
- **Domain logic** — the business rules, computations, and operations that give
  the entity meaning in the application.

In a classic Java design these two concerns would have to be merged into a single
class, because Java has only single inheritance. Tentackle instead keeps them in
**two separate implementation classes** and weaves them together at runtime into a
single object accessed through a dynamic proxy. The PDO therefore *behaves* as if
it inherited from both a persistence base class and a domain base class — Tentackle
**emulates multiple inheritance** without forcing either layer to know about the
other.

### Why split persistence and domain?

- **The persistence layer is agnostic of the domain layer, and vice versa.**
  The generated persistence code never depends on handwritten business rules, and
  business code never depends on JDBC, SQL, or wire formats. Each can evolve
  independently.
- **The implementations are injected at runtime.** The PDO interface does not know
  its concrete implementation classes; they are discovered via the SPI mechanism
  (`@PersistentObjectService` / `@DomainObjectService`) and instantiated by a
  factory. A persistence implementation can be replaced — e.g., a JDBC one swapped
  for an in-memory or remote one — without touching a single line of application
  code.
- **Aggregates are first-class citizens.** The persistence layer knows which
  entities are *root entities* and which are their *components*. Every component
  knows its root entity without issuing a database query, so Domain-Driven Design
  (DDD) aggregate rules can be enforced structurally rather than by convention.
  This turns a whole class of subtle bugs into immediate, fail-fast exceptions —
  see [Aggregates prevent whole classes of bugs](#aggregates-prevent-whole-classes-of-bugs).
- **Location transparency.** Because the persistence layer is remoting-capable
  (see [trip.md](../../../../tentackle-core/src/main/docs/trip.md)), every method of
  a PDO can be invoked **at any time, in any JVM** — whether that JVM is directly
  connected to a database or only indirectly connected through a middle-tier
  server. There is **no `LazyInitializationException`, ever**.

### Design principles

- A PDO is defined by **interfaces**. Application code depends only on interfaces;
  the implementations are an internal detail.
- The **model** of each PDO — its table, columns, relations, and validation rules —
  lives in a structured comment block in the source of the PDO interface and is
  picked up by the code generator (the *Wurbelizer*) to produce the persistence
  interfaces and implementations.
- **Domain logic is written exactly once.** It runs on whichever JVM currently
  holds the PDO, with no duplication between client and server tiers.

---

## Anatomy of a PDO

A single entity is described by **three interfaces** and backed by **two
implementation classes**, tied together by a runtime proxy. Using the aluminum
`Ingot` entity from a sample application
(modules `plsbl-pdo`, `plsbl-domain`, `plsbl-persistence`):

| Layer                 | Artifact               | Module              | Role                                                         |
|-----------------------|------------------------|---------------------|--------------------------------------------------------------|
| PDO interface         | `Ingot`                | `plsbl-pdo`         | The public entity type; what application code uses.          |
| Persistence interface | `IngotPersistence`     | `plsbl-pdo`         | Attributes, relations, finders, `persist`/`select`/`delete`. |
| Domain interface      | `IngotDomain`          | `plsbl-pdo`         | Business methods (`move`, `deliver`, `isDeliverable`, …).    |
| Persistence impl      | `IngotPersistenceImpl` | `plsbl-persistence` | Generated + hand-written SQL mapping.                        |
| Domain impl           | `IngotDomainImpl`      | `plsbl-domain`      | Hand-written business logic.                                 |

### The PDO interface

The PDO interface is thin. It simply extends the persistence- and domain
interfaces and is adorned with the metadata needed by persistence
(`@TableName`, `@ClassId`), the UI (`@Singular`, `@Plural`), and validation
(`@IngotValidator`):

```java
@TableName("td.ingot")
@ClassId(2002)
@Singular("Ingot")
@Plural("Ingots")
@IngotValidator
public interface Ingot extends TransactionData<Ingot>, IngotPersistence, IngotDomain {
  // ...mostly generated code regions...
}
```

`Ingot` extends `TransactionData<Ingot>`, an application-specific super-interface
that ultimately extends
[`PersistentDomainObject<T>`](../java/org/tentackle/pdo/PersistentDomainObject.java):

```java
public interface PersistentDomainObject<T extends PersistentDomainObject<T>>
       extends PersistentObject<T>, DomainObject<T>, EffectiveClassProvider<T>, Comparable<T> {

  DomainObject<T>     getDomainDelegate();
  PersistentObject<T> getPersistenceDelegate();
}
```

The self-referential type parameter (`Ingot extends ...<Ingot>`) lets methods such
as `persist()` or `reload()` return the precise type (`Ingot`) rather than a raw
`PersistentDomainObject`.

### The persistence interface

`IngotPersistence extends PersistentObject<Ingot>`. Almost all of it is
**generated** from the model block (see below) into guarded code regions:

- typed getters/setters for every column (`getWeight()`, `setStockyard(...)`);
- modification tracking (`isWeightModified()`, `getWeightPersisted()`);
- relation accessors (`getPickups()`, `getStockyard()`, `getProduct()`);
- attribute-name and column-length constants;
- finders (`selectByStockyardId(...)`).

Handwritten persistence-flavored methods can be added too. In `IngotPersistence`,
the generic CRUD methods are overridden only to attach interceptors:

```java
@Override @Transaction @UmpRelevant Ingot persist();
@Override @Transaction @UmpRelevant void   delete();
```

The base interface
[`PersistentObject<T>`](../java/org/tentackle/pdo/PersistentObject.java)
provides the persistence vocabulary shared by all PDOs: `persist()`, `save()`,
`delete()`, `select(id)`, `selectAll()`, `reload()`, `isNew()`, `isModified()`,
the token-lock family (`requestTokenLock()`, `selectTokenLocked(id)`, …),
snapshots, and the cache accessors (`selectCached(id)`, `selectAllCached()`).

### The domain interface

`IngotDomain extends DomainObject<Ingot>` and declares the **business API** —
methods that have nothing to do with how the ingot is stored:

```java
public interface IngotDomain extends DomainObject<Ingot>, SapNotifiable {

  IngotCategory determineCategory();
  boolean       isDeliverable();
  boolean       isOnTop();

  @Transaction Ingot move(Stockyard fromYard, Stockyard toYard,
                          boolean rotate, boolean updateStatistics, boolean sendPdaMessage);
  @Transaction void  deliver(Calloff calloff);
}
```

[`DomainObject<T>`](../java/org/tentackle/pdo/DomainObject.java) supplies
the cross-cutting domain vocabulary: the unique domain key (`getUniqueDomainKey()`,
`findByUniqueDomainKey(...)` — see [Domain Keys](domain-key.md)) and presentation
helpers (`getSingular()`, `getPlural()`, `toGenericString()`).

### The implementations

The two implementations never reference each other. They are bound to the PDO
interface by SPI annotations:

```java
@PersistentObjectService(Ingot.class)
public class IngotPersistenceImpl
       extends AbstractPersistentTransactionData<Ingot, IngotPersistenceImpl>
       implements IngotPersistence { ... }

@DomainObjectService(Ingot.class)
public class IngotDomainImpl
       extends AbstractDomainObject<Ingot, IngotDomainImpl>
       implements IngotDomain { ... }
```

At runtime, `PdoFactory` discovers both, instantiates them, and wraps them in a
single dynamic proxy implementing the `Ingot` interface. The
`PdoInvocationHandler` routes each method call to the correct delegate:
calls declared by the persistence interface go to `IngotPersistenceImpl`, calls
declared by the domain interface go to `IngotDomainImpl`. To application code it is
just an `Ingot`.

```
                         ┌──────────────────────────┐
   application code ───▶ │   Ingot  (dynamic proxy) │
                         └────────────┬─────────────┘
                          PdoInvocationHandler routes by interface
                ┌──────────────────────┴───────────────────────┐
                ▼                                                ▼
   ┌──────────────────────────┐                  ┌──────────────────────────┐
   │   IngotPersistenceImpl   │  (no mutual      │     IngotDomainImpl      │
   │  extends Abstract        │   dependency)    │  extends Abstract        │
   │  PersistentObject        │ ◀──────────────▶ │  DomainObject            │
   └──────────────────────────┘  buddy delegates └──────────────────────────┘
```

### `me()` and `on(...)`

Inside a domain implementation, two helpers make the code read naturally:

- **`me()`** returns the *whole PDO* (the proxy), so business code can call any
  persistence or domain method on itself. `IngotDomainImpl` is only the domain
  *half*; `me()` is the complete `Ingot`. Hence `me().getWeight()` (a persistence
  getter) and `me().persist()` work from inside domain code.
- **`on(SomeEntity.class)`** creates a new PDO of another type *in the same domain
  context* — it is the readable shorthand for
  `Pdo.create(SomeEntity.class, getDomainContext())`. This is how domain logic
  reaches other entities while staying inside the current context (and therefore
  the current session and tenant).

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

---

## Object identity — `equals`, `hashCode`, and why relations are lists

A PDO's identity is its **class plus its object id**, not its field values. The
implementation lives in [`AbstractDbObject`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/AbstractDbObject.java)
and is deliberately `final` — you cannot override it:

- **`equals`** — two PDOs are equal when they have the **same effective class and the same id**.
- **`hashCode`** — is the id (`(int) getId()`).
- **`compareTo`** — orders by id, then by class name. The natural ordering is therefore *technical*
  (insertion/id order), never a business field. To sort by name, price, or date, pass an explicit
  `Comparator`.

The proxy forwards `equals`/`hashCode`/`compareTo` to the persistence delegate (see
[The implementations](#the-implementations)), so this is exactly what application code observes when
it compares two `Ingot`s.

### The transient-object trap

There is one crucial subtlety. A freshly created, **not-yet-persisted** PDO has an id of `0`. With no
id there is no domain identity, so `equals`/`hashCode` **fall back to the object's address**
(`Object.equals`/`Object.hashCode`). The moment the object is persisted and receives its id, both
change:

```java
Ingot i = Pdo.create(Ingot.class, ctx);   // id == 0  → identity hash (its address)
Set<Ingot> seen = new HashSet<>();
seen.add(i);
i.persist();                              // id assigned → hashCode is now (int) id
seen.contains(i);                         // ⇒ FALSE — it now hashes to a different bucket
```

Two consequences follow directly:

- **Never put un-persisted PDOs into a `HashSet`/`HashMap`.** Their hash changes on the first
  `persist()`, and they become unreachable in the collection. This is the classic Hibernate
  "don't build `equals`/`hashCode` on the generated id" hazard — except here it is intentional and
  `final`, because a PDO's identity genuinely *is* its persistent id.
- **Two distinct un-persisted PDOs are never equal**, even if every field matches — each is only
  equal to itself until it has an id.

### Why relations are lists, not sets

This is the reason the [model](../../../../tentackle-model/src/main/docs/model-definition.md) offers
`list` relations (backed by [`TrackedList`](../../../../tentackle-core/src/main/docs/misc.md#trackedlist--knowing-what-changed))
but has **no `set` relation** — a hash- or tree-based `Set` of PDOs would be unsound for two
independent reasons:

1. **Identity mutates on save.** A `Set` decides membership by `hashCode`/`equals` at insertion time.
   Add a new component to an aggregate, then save the aggregate: every component's id — and therefore
   its hash — changes, and the `Set`'s internal bucketing is silently corrupted. `contains`, `remove`
   and iteration stop being reliable. A `List` stores elements by position and never hashes them, so
   it is immune.

2. **Cyclic object graphs during deserialization.** PDO graphs are cyclic by design — a component
   points back to its root, related entities reference each other — and
   [TRIP](../../../../tentackle-core/src/main/docs/trip.md) reconstructs them by **registering a
   placeholder for each object *before* its fields are read**, so a back-reference encountered later
   in the same subgraph can resolve to the object under construction. That means an element can be
   handed to its containing collection while it is still half-built: its id (and other fields) not yet
   deserialized. Adding such an element to a `Set` would hash it in its incomplete state, and
   completing it later would move it to a different bucket — the same corruption as above, now
   unavoidable because the cycle *forces* the element to be referenced before it is finished. A
   `List` appends by position regardless of the element's state, so partially built elements are
   never a problem.

`TrackedList` additionally gives the OR-mapper the added/removed history it needs to translate an
in-memory collection back into inserts, updates and deletes — a set would not carry that either. See
[TrackedList](../../../../tentackle-core/src/main/docs/misc.md#trackedlist--knowing-what-changed) for
the collection contract and [TRIP](../../../../tentackle-core/src/main/docs/trip.md) for the
placeholder/back-reference protocol.

---

## The Model and Code Generation

A PDO interface carries its **model** in a special comment block. For `Ingot` the
header defines the table and class id, and a `$mapping` block describes the
attributes, indexes, relations, and validations:

```
/*
 * @{
 * tablename = td.ingot
 * classid   = 2002
 * mapping   = $model/$tablename.map
 * @}
 */

/*
 * @> $mapping
 * name := $classname
 * table := $tablename
 * id := $classid
 *
 * ## attributes
 * [tokenlock, tableserial, fulltracked]
 *
 * IngotNumber<String>($ingot_no) ingotNumber  ingot_no   the ingot number [normtext]
 * int                            weight        weight     net weight in kg [unsigned]
 * Long                           stockyardId   stockyard_id ID of the stockyard, null if not in stock
 * ...
 *
 * ## relations
 * Stockyard:
 * PickupOrder:
 *    relation = component list,
 *    select   = lazy | received,
 *    count    = pickupCount,
 *    name     = Pickups
 * ...
 *
 * ## validations
 * weight: @NotZero(message="{ @('please enter the weight') }")
 * ...
 * @<
 */
```

At build time the **Wurbelizer** (`wurbelizer-maven-plugin`, with the templates in
`tentackle-persistence-wurblets`) reads this model and generates:

- the contents of the guarded regions in `IngotPersistence` (getters/setters,
  relation accessors, constants, finders);
- the SQL/JDBC mapping in `IngotPersistenceImpl`;
- DDL and other persistence artifacts.

The generated code is woven into the existing source between
`//GEN-BEGIN`/`//GEN-END` markers, so handwritten and generated code coexist in
the same file. The relations declared here are what make `getPickups()`,
`getMovements()`, etc. return live, context-aware collections — and what tells the
persistence layer that an `Ingot` is the **root entity** of an aggregate whose
components are its pickups, movements, transports, and loadings.

---

## The DomainContext and DDD

Every PDO is bound to a
[`DomainContext`](../java/org/tentackle/pdo/DomainContext.java). The
domain context is the single most important concept for understanding how PDOs
relate to Domain-Driven Design. It answers four questions for an object at all
times:

1. **In which session does it live?** A `DomainContext` is a `SessionHolder`, so
   it owns (or refers to) the database/remote session through which the object is
   loaded and saved.
2. **Which aggregate does it belong to?** Through the *root context* it links every
   component to its root entity.
3. **Which logical data space (tenant) does it belong to?** Through optional
   application-specific extensions it scopes queries to the current tenant.
4. **Which subdomain does it belong to?** A `DomainContext` can be **extended**
   to hold additional information or cloned to create a named sub-context.

### Aggregates and the root context

Since every PDO is either a root entity or a direct/indirect **component** of a
root entity, each root entity maintains its own copy of the domain context — the
**root context**. The root entity and all of its components refer to that same
root context and therefore "know" their root entity **without performing a database
query**.

For `Ingot`, the generated model comment states it plainly:

```
 * Ingot is a root entity
 *
 * Ingot is a composite with the components:
 *    + PickupOrder    via po.ingotId as pickups   [1:N]
 *    + IngotMovement  via im.ingotId as movements [1:N]
 *    + TransportOrder via t.ingotId  as transports[1:N]
 *    + LoadingOrder   via lo.ingotId as loadings  [1:N]
```

This is the DDD **aggregate** made explicit and enforced by the framework: the
`Ingot` is the aggregate root; `PickupOrder`, `IngotMovement`, `TransportOrder`,
and `LoadingOrder` are components reachable only through it. When `Ingot.move(...)`
adds an `IngotMovement` to `getMovements()` and then calls `me().persist()`, the
whole aggregate is persisted as one consistent unit — the root and its components
together. The root context is what the
[`SecurityManager`](../java/org/tentackle/security/SecurityManager.java)
and the optional `rootId` column rely on to reason about the aggregate as a whole.
The persistence layer takes care of the rest and updates the database where necessary.

### Aggregates prevent whole classes of bugs

Making aggregates first-class is not just conceptual tidiness — it lets the
framework catch, at runtime and fail-fast, mistakes that in other persistence
frameworks silently corrupt data. The guiding rule is the DDD invariant that
**an aggregate is responsible for its own consistency, and nothing else may write
to it behind its back**. Three concrete safeguards follow from it.

#### 1. A component cannot be persisted on its own

A component may only be persisted **through its root entity**. Invoking `persist()`,
`save()`, or `delete()` on a component throws an exception — in any context, even in
the root context of its own aggregate. Components are written exclusively by their
root, which inserts, updates, and deletes them along with itself.

The reason is the aggregate invariant: only the root may decide when its components
are valid, because only the root sees them all together. Consider an
`Invoice` / `InvoiceLine` aggregate with the rule *"the sum of all line amounts
must be positive"* (a validation owned by the `Invoice`, see
[validation.md](../../../../tentackle-core/src/main/docs/validation.md)):

```java
// WRONG — the line is persisted on its own, bypassing its Invoice
InvoiceLine line = Pdo.create(InvoiceLine.class, someOtherContext);
line.setInvoiceId(125889L);
line.setAmount(-500);
line.persist();          // throws: a component is not a root entity
```

Inserting that line straight into the database, without going through the
`Invoice` aggregate root, could leave the invoice's total negative — a globally
invalid state that *no single row* looks wrong on its own. Because the persistence
layer knows the line is a component, it refuses the orphaned write. The correct
form attaches the line to its root and lets the root persist the aggregate as a
unit, so the invoice-level validation runs:

```java
InvoiceLine line = invoice.on(InvoiceLine.class);   // same context as the invoice
line.setAmount(-500);
invoice.getLines().add(line);
invoice.persist();       // validates the whole aggregate; rejects the negative total
```

#### 2. Security rules are applied per aggregate, even when reading components

Because each component knows its root entity **without a database query**, the
[`SecurityManager`](../java/org/tentackle/security/SecurityManager.java) can apply
the *aggregate's* access rules the moment a component is read from the database — it
does not need to issue an extra query to discover which root the row belongs to.
A permission expressed on the `Invoice` ("user X may not see invoices of tenant Y")
therefore automatically governs reads of its `InvoiceLine`s too. The rule lives in
one place (on the root) and is enforced consistently across the whole aggregate,
including its components.

#### 3. Components read outside their root context become immutable

Sometimes a component is loaded directly, in a context that is *not* the root
context of its aggregate — for example, a finder that selects `InvoiceLine`s across
many invoices. In DDD terms this is a **deep reference** (reaching into another
aggregate's internals), which is a design smell. Tentackle does not forbid it, but
it makes such components **automatically immutable**: any attempt to change their
state throws an exception (fail-fast) rather than letting you mutate a component
whose aggregate root is not present to validate the change.

```java
// deep reference: lines fetched without their owning Invoice as root
List<InvoiceLine> lines = on(InvoiceLine.class).findByProduct(productId);
InvoiceLine line = lines.getFirst();
line.setAmount(0);       // throws: component is immutable outside its root context
```

This guarantees that the only way to *modify* a component is through its aggregate
root, in the root's context — exactly where the invariants can be checked — while
still allowing convenient read-only, cross-aggregate queries for reporting and
lookups.

### Logical data spaces and multi-tenancy

Because every PDO carries a reference to its context, the context can scope what
the object sees. A `DomainContext` can be **extended** to hold the id(s) of the
object(s) that span a tenant or other logical partition. A `selectAll()` then
returns only the rows belonging to that space. The model option `CONTEXT` makes the
generator automatically `AND` the tenant column into the `WHERE` clause of finders,
using the value from the domain context.

Contexts form a **hierarchy** that mirrors the domain. The Javadoc gives the
financial-accounting example:

```
TenantContext -> FiscalYearContext -> BookContext
TenantDependable -> FiscalYearDependable -> BookDependable
Tenant -> FiscalYear -> Book
```

It is recommended to provide an application-specific context via a
`DomainContextFactory`.

### Named (sub) contexts

By default, contexts are unnamed. Creating a **named clone** (`context.clone("edit")`,
or `on(Ingot.class, "edit")`) opens a sub-context that describes a distinct part of
the application — a module, task, or purpose. Named contexts can, for example,
inhibit or allow certain methods, and they guarantee that a PDO locked in one named
context cannot be persisted or unlocked by the same user in a *different* named
context. All sub-contexts of the same root are still considered *logically equal*
from a multi-tenancy perspective (the names are not part of `equals`/`hashCode`/
`compareTo`).

### Sessions, immutability, and remote safety

When a PDO travels between JVMs, its context travels with it. On arrival the
session is re-attached, and `DomainContext.assertPermissions()` is invoked so a
middle-tier server can verify the (possibly multi-tenant) context is allowed for
the current user. This is the hook that keeps a shared server from leaking one
tenant's data into another's context.

---

## Write the Domain Logic Once — Run It Anywhere

This is the property that ties PDOs to [TRIP](../../../../tentackle-core/src/main/docs/trip.md)
and is the single biggest practical payoff of the pattern.

A PDO's `getPersistenceDelegate()` is either:

- a **local** persistence implementation talking straight to a JDBC connection, or
- a **remote** delegate — a TRIP proxy that forwards persistence calls to a
  middle-tier server which owns the real connection.

The application code does not know or care which. The **domain implementation is
identical in both cases** and is deployed once. When an `Ingot` lives in a desktop
client connected only to a server, calling `ingot.getStockyard()` or
`ingot.getPickups()`:

- if the data is already present, returns it immediately;
- if not, transparently fetches it from the server over TRIP and returns it.

There is **no `LazyInitializationException`** — the concept does not exist in
Tentackle. A relation can be navigated at any time, on any tier, because the
persistence delegate always knows how to obtain the missing data, whether that
means a SQL query or a remote call. Likewise, a `@Transaction` domain method such
as `Ingot.move(...)` can be invoked from the client; it executes wherever the PDO
and its session live, opening a real database transaction on the tier that holds
the connection.

Consequences for how you write code:

- **No DTOs, no "detached entity" rules, no fetch plans.** You navigate the object
  graph as plain Java.
- **Business rules are not duplicated** across a client module and a server module.
  `IngotDomainImpl` is the one and only place `move`, `deliver`, and
  `isDeliverable` are implemented.
- **The same `Ingot` instance behaves consistently** whether it was created in a
  unit test against H2, in a fat client against PostgreSQL, or in a thin client
  against a TRIP server.

### Buddy delegates

Occasionally a method must run on the *other* delegate — typically because it needs
direct database access and should execute on the server side. Annotating a method
with `@InvokeInBuddyDelegate` redirects the call from the domain delegate to its
persistence "buddy" (or vice versa). `Ingot.isCalloffAvailable()` does exactly
this:

```java
@Override
@InvokeInBuddyDelegate
public boolean isCalloffAvailable() {
  throw new NotInvokedInBuddyDelegateException();   // body lives in the persistence impl
}
```

The domain-side body is never executed; the interceptor dispatches to the
persistence implementation, which runs the query close to the database.

---

## Creating and Using PDOs

### Scaffolding a PDO with the wizard

A PDO is several coordinated files across the `-pdo`, `-persistence`, and
`-domain` modules — the PDO interface, its persistence- and domain interfaces, and
the two implementation classes, each with the right SPI annotations and model
comment block. Rather than create them by hand, the usual starting point is the
**PDO wizard** of the
[`tentackle-wizard-maven-plugin`](../../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md):

```bash
mvn tentackle-wizard:pdo
```

Run from the aggregator (parent) directory so the wizard can place files into all
modules. It asks for the entity name, the short/long description, the table name,
and class id, and generates the interfaces and implementations in the right
modules, including the SPI annotations and an empty model comment block ready for
you to fill in. The normal build (`tentackle:analyze` → Wurbelizer → compiler)
then turns that model into the persistence mappings and DDL described above. New
projects created from the [project archetype](../../../../tentackle-project-archetype/src/main/docs/tentackle-project-archetype.md)
ship with the same templates under `templates/pdo`. (Table-less *services* are
scaffolded the same way with `mvn tentackle-wizard:operation` — see
[operation.md](operation.md).)

### Creating PDOs in code

The [`Pdo`](../java/org/tentackle/pdo/Pdo.java) class collects the factory
methods for PDOs, operations, caches, sessions, and domain contexts.

```java
// Open a session and a domain context
Session       session = Pdo.createSession();                       // uses "backend" config
DomainContext context = Pdo.createDomainContext(session);

// Create a new, transient Ingot in that context
Ingot ingot = Pdo.create(Ingot.class, context);
ingot.setIngotNumber(new IngotNumber("1234567"));
ingot.setWeight(4200);
ingot = ingot.persist();                                           // INSERT (validated, in a transaction)

// Load and navigate
Ingot loaded = Pdo.create(Ingot.class, context).select(ingot.getId());
Stockyard yard = loaded.getStockyard();                            // fetched on demand, even remotely
List<PickupOrder> pickups = loaded.getPickups();                   // component list of the aggregate

// Invoke domain logic (runs wherever the PDO lives)
if (loaded.isDeliverable()) {
  loaded.move(yard, targetYard, false, true, true);               // @Transaction
}
```

Within domain/persistence implementations you normally use the context-aware
shorthands instead of `Pdo.create(...)`:

- `on(SomeEntity.class)` — a new PDO of another type in *this* context;
- `on(SomeEntity.class, String contextName)` — a new PDO of another type in a named sub context;
- `on()` — a new PDO of the *same* type in this context;
- `me()` — the full PDO from inside a delegate.

### Transactions and validation

`@Transaction` (see
[`Transaction`](../java/org/tentackle/pdo/Transaction.java)) wraps a method
in a database transaction: if none is running, one is started before the call and
committed afterward; any exception rolls it back. If a transaction is already
running, the method joins it. This is why `move(...)` and `deliver(...)` — which
touch many rows across the aggregate and beyond — are annotated `@Transaction`.

Validation rules declared in the model (`@NotNull`, `@NotZero`, the
`@IngotValidator`) are checked by the persistence layer before a PDO is saved; see
[validation.md](../../../../tentackle-core/src/main/docs/validation.md). The same
annotations are discovered by the binding layer to drive the UI; see
[binding.md](../../../../tentackle-core/src/main/docs/binding.md).

### Saving collections of PDOs

Components of an aggregate are saved along with their root by the generated code. For lists of
PDOs outside an aggregate — rows edited in a table, say —
[`PdoUtilities`](../java/org/tentackle/pdo/PdoUtilities.java) provides the same logic, each within
one transaction of the PDOs' session (all elements must share it):

- `saveCollection(pdos, modifiedOnly)` saves every persistable PDO (or only the modified ones) and
  deletes the elements that are no longer persistable but have been stored before;
  `saveCollection(pdos, modifiedOnly, true)` uses `persist()` instead and replaces the elements by
  the returned instances. A `TrackedList` is marked unmodified afterward.
- `deleteMissingInCollection(oldPdos, newPdos)` deletes the stored elements of the old collection
  that are not contained in the new one — the usual way to remove the rows the user deleted.
- `isCollectionModified(pdos)` tells whether any element is modified, or, for a `TrackedList`,
  whether elements were added, removed or replaced.

---

## A Real-Life Walkthrough: `Ingot.move(...)`

`IngotDomainImpl.move(fromYard, toYard, rotate, updateStatistics, sendPdaMessage)`
shows the whole pattern working together. Abridged:

```java
@Override
public Ingot move(Stockyard fromYard, Stockyard toYard, boolean rotate,
                  boolean updateStatistics, boolean sendPdaMessage) {

  Ingot ingot = me().reloadTokenLocked();          // me() = the full PDO; token-locked reload

  // create a component of the aggregate and attach it to the root
  IngotMovement movement = on(IngotMovement.class); // same context as the ingot
  movement.setWhen(DateHelper.now());
  movement.setFromStockyard(fromYard);
  movement.setToStockyard(toYard);
  ingot.getMovements().add(movement);               // component list of the Ingot aggregate

  // ... pure domain rules: pile heights, neighbors, on-top checks, statistics ...

  Ingot it = ingot.completeLoading();               // another domain method on the same PDO
  ingot = (it != null) ? it : ingot.persist();      // persist the whole aggregate atomically

  if (sendPdaMessage) {
    Ump.getInstance().ingotMoved(ingot, fromYard, toYard, null);  // notify the SAP system
  }
  return ingot;
}
```

What to notice:

- The method is declared `@Transaction` on `IngotDomain`, so everything here runs
  in one transaction and rolls back on any exception.
- `me()` is the complete `Ingot`, so the domain code freely calls persistence
  methods (`reloadTokenLocked()`, `persist()`) and other domain methods
  (`isOnTop()`, `completeLoading()`) on itself.
- `on(IngotMovement.class)` and `on(Stockyard.class)` create related PDOs in the
  **same domain context** — same session, same tenant, same aggregate root once
  attached.
- Adding the `IngotMovement` to `ingot.getMovements()` and then calling
  `ingot.persist()` saves the **aggregate** (root + components) consistently.
- None of this changes whether the `Ingot` lives in the server JVM or in a remote
  client — the identical `IngotDomainImpl` runs in both, and the persistence
  delegate transparently handles local vs. remote.

---

## Summary

| Concept                                 | What it gives you                                                                     |
|-----------------------------------------|---------------------------------------------------------------------------------------|
| Three interfaces + two impls + proxy    | Emulated multiple inheritance; persistence and domain stay independent.               |
| Runtime injection via SPI               | Implementations are replaceable without touching application code.                    |
| Model comment + Wurbelizer              | Persistence interface, mapping, and DDL generated from one declaration.               |
| `DomainContext`                         | Session, aggregate root context, and tenant scoping in one object — the DDD backbone. |
| Root context & components               | Aggregates enforced structurally; components know their root with no query.           |
| Location transparency over TRIP         | Domain logic written once, runs on any tier; **no `LazyInitializationException`**.    |
| `me()` / `on(...)`                      | Readable access to the whole PDO and to context-bound related PDOs.                   |
| `@Transaction`, validation, token locks | Cross-cutting concerns applied declaratively via interceptors.                        |

See also: [trip.md](../../../../tentackle-core/src/main/docs/trip.md) for the
remoting that makes location transparency possible,
[validation.md](../../../../tentackle-core/src/main/docs/validation.md) for the
rules applied before a PDO is saved, and
[binding.md](../../../../tentackle-core/src/main/docs/binding.md) for binding PDOs
to the UI.
