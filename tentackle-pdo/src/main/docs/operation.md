# Operation — Behavior Without an Entity

## Overview and Motivation

A **PDO** ([pdo.md](pdo.md)) models a *noun* — a `Customer`, an `Invoice`, an
`Ingot` — an entity that lives in a database table and carries both persistence
and domain logic. But not everything an application does belongs to a single
entity. Sending a mail, importing a file, running a month-end batch, dispatching a
command to a PLC, recalculating statistics across thousands of rows — these are
*verbs*. They operate over many PDOs (or over no PDO at all, e.g., external
resources), and there is no table that would naturally own them.

An **`Operation`** ([`Operation`](../java/org/tentackle/pdo/Operation.java)) is
Tentackle's home for exactly that kind of logic: a **service or complex
transaction that is not tied to one entity**. It reuses the full PDO machinery —
the two-delegate proxy, the `DomainContext`, SPI-based injection, and TRIP
remoting — but drops the parts that only make sense for a table-backed entity.

```java
// "send a mail through the middle-tier server"
Pdo.createOperation(MailOperation.class, context)
   .send("plsbl", recipient, "test", "this is a test from PLSBL");
```

To application code an `Operation` looks and is created much like a PDO; the
difference is what it *is for* and what it *lacks*.

---

## How an Operation Differs from a PDO

The framework's own Javadoc names the two defining differences
([`Operation.java`](../java/org/tentackle/pdo/Operation.java)):

> 1. there is no model, i.e., they are not related to database tables
> 2. either the persistence- or the domain-part is optional (but not both)

### 1. No model, no table

A PDO carries a **model** in a structured comment block, from which the Wurbelizer
generates getters/setters, relation accessors, finders, the SQL mapping, and DDL.
An Operation has **none of that**. It has:

- no table, no columns, no primary key, no identity;
- no CRUD lifecycle (`persist()`, `select(id)`, `delete()`, `reload()`, locks,
  snapshots, caching) — those live on
  [`PersistentObject`](../java/org/tentackle/pdo/PersistentObject.java), which an
  Operation does **not** extend;
- no generated code regions — an Operation interface is written entirely by hand
  (or scaffolded once by the wizard, see below).

An Operation is **pure behavior**. It is stateless with respect to the database:
it does not represent a row, it *acts on* rows.

### 2. One half is optional (but never both)

A PDO always has **both** a persistence delegate and a domain delegate. An
Operation may have:

- **only a persistence part** (`PersistentOperation`) — typical for work that runs
  close to the database and/or must execute on a remote middle-tier server (e.g.
  `MailOperation`);
- **only a domain part** (`DomainOperation`) — typical for orchestration logic with
  no direct database access of its own (e.g. `MoveOperation`, which dispatches a
  crane command);
- **both** — when an operation needs handwritten logic on each tier.

It may **never have neither**. The
[`OperationInvocationHandler`](../java/org/tentackle/pdo/OperationInvocationHandler.java)
constructor builds whichever mixins it finds, substitutes a *dummy* mixin for the
missing side, and throws `ClassNotFoundException` only when *both* are absent:

```java
if (persistenceMixin == null && domainMixin == null) {
  throw new ClassNotFoundException("neither domain- nor persistence mapping found for '" + clazz.getName() + "'");
}
else if (persistenceMixin == null) {
  persistenceMixin = Mixin.forImplementation(clazz, null, PersistentOperation.class);   // dummy
}
else if (domainMixin == null) {
  domainMixin = Mixin.forImplementation(clazz, null, DomainOperation.class);            // dummy
}
```

A dummy mixin creates no delegate, so the handler's delegate for that side stays `null`.

### What it keeps from the PDO pattern

Everything that makes PDOs pleasant to work with still applies:

- **Two-delegate proxy / emulated multiple inheritance.** The persistence and
  domain implementations never reference each other and are woven together at
  runtime.
- **`DomainContext`.** An Operation is bound to a context, so it runs in a
  session, scoped to the current tenant (see
  [pdo.md → DomainContext and DDD](pdo.md#the-domaincontext-and-ddd)).
- **Runtime injection via SPI.** Implementations are discovered, not wired by hand.
- **Location transparency over TRIP.** The persistence part may run on a remote
  middle-tier server; the domain part always runs in the local JVM.

### At a glance

|                                           | **PDO**                                             | **Operation**                                             |
|-------------------------------------------|-----------------------------------------------------|-----------------------------------------------------------|
| Represents                                | a persistent entity / aggregate (a *noun*)          | a service or transaction (a *verb*)                       |
| Model comment + generated code            | yes                                                 | **no**                                                    |
| Backing table & identity                  | yes                                                 | **none**                                                  |
| CRUD / locks / cache (`PersistentObject`) | yes                                                 | **no**                                                    |
| Delegates                                 | domain **and** persistence (both required)          | domain and/or persistence (**at least one**)              |
| Bound to a `DomainContext`                | yes                                                 | yes                                                       |
| Remoting over TRIP                        | yes                                                 | yes                                                       |
| Base classes                              | `AbstractPersistentObject` / `AbstractDomainObject` | `AbstractPersistentOperation` / `AbstractDomainOperation` |
| SPI annotations                           | `@PersistentObjectService` / `@DomainObjectService` | `@PersistentOperationService` / `@DomainOperationService` |
| Factory entry point                       | `Pdo.create(...)`                                   | `Pdo.createOperation(...)`                                |

---

## Anatomy of an Operation

Like a PDO, an Operation is described by **interfaces** and backed by **handwritten
implementation classes**, tied together by a runtime proxy. The difference is that
one of the two implementation sides may be absent.

The interface inheritance mirrors the PDO trio, rooted at `Operation`:

```java
public interface Operation<T extends Operation<T>>
       extends PersistentOperation<T>, DomainOperation<T>, EffectiveClassProvider<T> {

  DomainOperation<T>     getDomainDelegate();
  PersistentOperation<T> getPersistenceDelegate();
}
```

- [`PersistentOperation<T>`](../java/org/tentackle/pdo/PersistentOperation.java) —
  the persistence half. Extends `PersistenceDelegate`, `OperationProvider`,
  `DomainContextDependable`, `SessionDependable`. Note: **not**
  `PersistentObject` — there is no CRUD vocabulary.
- [`DomainOperation<T>`](../java/org/tentackle/pdo/DomainOperation.java) — the
  domain half. Extends `DomainDelegate`, `OperationProvider`,
  `DomainContextProvider`, `SessionProvider`.

### A domain-only operation

`MoveOperation` from the `plsbl` reference application dispatches a crane command;
it needs orchestration logic but no persistence delegate of its own.

```java
// plsbl-pdo : the operation interface
public interface MoveOperation extends Operation<MoveOperation>, MoveOperationDomain {
}

// plsbl-pdo : the domain interface — declares the business API
public interface MoveOperationDomain extends DomainOperation<MoveOperation> {

  @Secured(ExecutePermission.class)
  @Transaction
  void runMove(CommandType commandType, Ingot ingot, Stockyard toYard) throws PlsblException;
}

// plsbl-domain : the handwritten implementation, bound by SPI
@DomainOperationService(MoveOperation.class)
public class MoveOperationDomainImpl
       extends AbstractDomainOperation<MoveOperation, MoveOperationDomainImpl>
       implements MoveOperationDomain {

  public MoveOperationDomainImpl(MoveOperation operation) { super(operation); }
  public MoveOperationDomainImpl() { }

  @Override
  public void runMove(CommandType commandType, Ingot ingot, Stockyard toYard) throws PlsblException {
    PlsStatus plsStatus = on(PlsStatus.class).load();   // reach PDOs in the same context
    // ... domain rules, then dispatch the command ...
  }
}
```

There is **no** `MoveOperationPersistence` and no persistence impl. With the default
[`DefaultOperationFactory`](../java/org/tentackle/pdo/DefaultOperationFactory.java), the
persistence side is then a
[`DummyPersistentOperation`](../java/org/tentackle/pdo/DummyPersistentOperation.java):
the factory registers it as the default persistence implementation, so the handler finds
it like any other. It holds the operation's domain context and session, and nothing more.
The handler's own dummy mixin is only a fallback for factories without such a default.
Every operation method lands on `MoveOperationDomainImpl`.

### A persistence-only operation (with remoting)

`MailOperation` sends mail and must run on the middle-tier server when the client
is connected remotely; it has only a persistence part.

```java
// plsbl-pdo : the operation interface
public interface MailOperation extends Operation<MailOperation>, MailOperationPersistence {
}

// plsbl-pdo : the persistence interface — declares the service API
public interface MailOperationPersistence extends PersistentOperation<MailOperation> {
  void send(String from, String to, String subject, String body);
}

// plsbl-persistence : the implementation, bound by SPI
@PersistentOperationService(MailOperation.class)
public class MailOperationPersistenceImpl
       extends AbstractPersistentOperation<MailOperation, MailOperationPersistenceImpl>
       implements MailOperationPersistence {

  //<editor-fold defaultstate="collapsed" desc="classvariables">

  private static final PersistentOperationClassVariables<MailOperation, MailOperationPersistenceImpl> CLASSVARIABLES =
          new PersistentOperationClassVariables<>(MailOperation.class, MailOperationPersistenceImpl.class);

  @Override
  public PersistentOperationClassVariables<MailOperation, MailOperationPersistenceImpl> getClassVariables() {
    return CLASSVARIABLES;
  }

  //</editor-fold>

  @Override
  @RemoteMethod
  public void send(String from, String to, String subject, String body) {
    // @wurblet send RemoteMethod

    //<editor-fold defaultstate="collapsed" desc="code 'send' generated by wurblet RemoteMethod">//GEN-BEGIN:send
    if (getSession().isRemote()) {
      getRemoteDelegate().send(getDomainContext(), from, to, subject, body);
      return;
    }
    //</editor-fold>//GEN-END:send
    
    // ... actually send the mail (executes where the real connection lives) ...
  }
}
```

Two details worth noting for the persistence side:

- **`PersistentOperationClassVariables`** plays the role the model would play for a
  PDO. It is the per-class metadata holder (extending
  `DbOperationClassVariables`) and carries the operation's method cache.
  `AbstractPersistentOperation.getClassVariables()` throws until you override it, so
  every persistence implementation must supply its own static instance.
  If you created the operation from a wizard, the wizard will generate the
  `getClassVariables()` method for you.
- **`@RemoteMethod`** + the `getSession().isRemote()` guard is the standard pattern
  for "execute this on the middle-tier server". The (also generated) part of the body delegates to a generated
  remote delegate when the session is remote and otherwise runs locally — the same
  location-transparency mechanism PDOs use (see
  [trip.md](../../../../tentackle-core/src/main/docs/trip.md)).

### The implementations and their base classes

| Side        | Implements               | Extends                                                                                                                                      |
|-------------|--------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| Persistence | `PersistentOperation<T>` | [`AbstractPersistentOperation<T,P>`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/AbstractPersistentOperation.java) |
| Domain      | `DomainOperation<T>`     | [`AbstractDomainOperation<T,D>`](../../../../tentackle-domain/src/main/java/org/tentackle/domain/AbstractDomainOperation.java)               |

Both base classes provide `me()` (the whole Operation proxy), `getOperation()`,
`getDomainContext()`, and `getSession()`, so an implementation can freely call the
other side and reach related PDOs — exactly as in a PDO delegate. As with PDOs,
the implementations are `@TripReferencable(NEVER)`.

### How calls are routed

```
                         ┌────────────────────────────────┐
   application code ───▶ │  MoveOperation  (dynamic proxy)│
                         └───────────────┬────────────────┘
                       OperationInvocationHandler routes by interface
                ┌───────────────────────┴────────────────────────┐
                ▼                                                  ▼
   ┌──────────────────────────┐                    ┌──────────────────────────┐
   │  persistence mixin       │   at least one of  │  domain mixin            │
   │  (may be a dummy)        │   the two is real  │  MoveOperationDomainImpl │
   └──────────────────────────┘                    └──────────────────────────┘
```

[`OperationInvocationHandler.invoke(...)`](../java/org/tentackle/pdo/OperationInvocationHandler.java)
handles `Object`/`Comparable`/`Operation`/`OperationProvider`/`EffectiveClassProvider` methods directly,
then consults the
[`OperationMethodCache`](../java/org/tentackle/pdo/OperationMethodCache.java) to
dispatch each interface method to whichever delegate declares it
(`invokePersistence` or `invokeDomain`).

---

## Creating and Using Operations

The [`Pdo`](../java/org/tentackle/pdo/Pdo.java) facade exposes `createOperation(...)`
overloads (delegating to
[`OperationFactory`](../java/org/tentackle/pdo/OperationFactory.java)). The common
forms:

```java
// in a domain context (the usual case — gives session + tenant scoping)
MailOperation mail = Pdo.createOperation(MailOperation.class, context);
mail.send("plsbl", recipient, "test", "hello");

// for a session only — the application must set the context afterward
MoveOperation move = Pdo.createOperation(MoveOperation.class, session);

// without context or session — the application must set the context afterward
SawToStockOperation op = Pdo.createOperation(SawToStockOperation.class);
```

The factory also offers overloads that take a pre-built persistence or domain
delegate, and ones that create within a named sub-context — see
[`OperationFactory`](../java/org/tentackle/pdo/OperationFactory.java).

### `op()` — a fresh operation of the same type

From inside an operation implementation,
[`OperationProvider.op()`](../java/org/tentackle/pdo/OperationProvider.java) creates
a new instance of the same operation type in the same context (optionally a named
sub-context), the operation-world counterpart of a PDO's `on()`:

```java
T op();                                   // same type, same context
T op(String contextName);                 // same type, in a named sub-context
```

Operations of *another* type are created via
[`DomainContextProvider`](../java/org/tentackle/pdo/DomainContextProvider.java), which every
operation and PDO is:

```java
<O extends Operation<O>> O op(Class<O> clazz);                     // same context
<O extends Operation<O>> O op(Class<O> clazz, String contextName); // in a named sub-context
```

`op(String)` creates the named sub-context only if the current context is not already within
that name, whereas `op(Class, String)` always creates it and throws a `DomainException` if the
context is already within that name.

### Reaching PDOs from an operation

Because an operation is bound to a `DomainContext`, its implementation can create
and load PDOs in that same context with the familiar `on(...)` / `me()` helpers
inherited from the abstract base classes:

```java
PlsStatus plsStatus = on(PlsStatus.class).load();   // a PDO, in this operation's context
```

### Cross-cutting concerns

The same interceptor-based annotations used on PDO methods apply to operation
methods, since both run through the same invoker
([interceptors.md](../../../../tentackle-core/src/main/docs/interceptors.md)):

- **`@Transaction`** wraps the call in a database transaction (joining an outer one
  if present) — `MoveOperation.runMove(...)` uses it.
- **`@Secured`** enforces a permission before the call — `runMove` requires
  `ExecutePermission`.
- **`@RemoteMethod`** marks a persistence method for middle-tier execution.

---

## Scaffolding an Operation

An operation is several coordinated files across the `-pdo`, `-persistence`, and
`-domain` modules. Rather than write them by hand, use the **operation wizard** of
the [`tentackle-wizard-maven-plugin`](../../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md):

```bash
mvn tentackle-wizard:operation
```

The wizard asks for the operation name, the short/long description, and **whether
the operation has a domain part, a persistence part, or both** (at least one is
required — it stops with *"neither domain- nor persistence-part defined"*
otherwise). From your answers it generates the interface(s) and implementation(s)
in the right modules, including the SPI annotations and, for a remoting-capable
persistence part, the delegate import and the `getRemoteDelegate()` override in the
persistence implementation. The remote delegate itself is not written by the wizard —
it is generated *when needed* by the
[persistence wurblets](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md)
on the first build in which the persistence implementation carries a remote-capable
wurblet (`AssertRemote` or a `RemoteMethod` block). New projects created from the
[project archetype](../../../../tentackle-project-archetype/src/main/docs/tentackle-project-archetype.md) ship with the same
templates under `templates/operation`.

The same wizard runs **headless** when the operation name is given on the command
line — useful in scripts and for coding agents, which cannot operate the dialog:

```bash
mvn tentackle-wizard:operation -DoperationName=SendInvoice -Dcomment="sends an invoice"
```

Everything the dialog asks for has a corresponding property (`-Dprofile`,
`-Dremote`, `-DsuperOperation`, `-Dabstract`, and one per generated type; an empty
value suppresses that file). See
[the wizard plugin](../../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md#the-operation-goal)
for the full list.

---

## When to Use an Operation vs. a PDO

Use a **PDO** when you are modeling **data that lives in the database** — an entity
with identity, attributes, relations, and a lifecycle.

Use an **Operation** when you need **server-side logic or a transaction that does
not belong to a single entity**:

- complex transactions spanning many entities or aggregates;
- batch / import / export / recalculation jobs;
- integration with external systems (mail, PLC, SAP, web services);
- any "service" method that would otherwise be homeless — or worse, bolted onto an
  unrelated entity just because it needed somewhere to live.

A useful rule of thumb: if you find yourself adding a method to a PDO that does not
actually concern *that* entity's own data, it probably wants to be an Operation.

---

## Summary

| Concept                                                   | What it gives you                                                              |
|-----------------------------------------------------------|--------------------------------------------------------------------------------|
| `Operation` = PDO machinery minus the model               | Proxy, `DomainContext`, SPI injection, and TRIP remoting for table-less logic. |
| No model / no table / no CRUD                             | Pure behavior; an operation acts on rows, it is not a row.                     |
| One delegate may be absent                                | Persistence-only, domain-only, or both — but never neither.                    |
| `AbstractPersistentOperation` / `AbstractDomainOperation` | Hand-written base classes with `me()`, `on(...)`, context, and session.        |
| `@PersistentOperationService` / `@DomainOperationService` | Bind an implementation to its operation interface for runtime discovery.       |
| `Pdo.createOperation(...)` / `op()`                       | Create operations the way you create PDOs.                                     |
| `@Transaction`, `@Secured`, `@RemoteMethod`               | Same declarative cross-cutting concerns as PDOs.                               |
| Operation wizard                                          | Generates the multi-module skeleton from a short questionnaire.                |

See also: [pdo.md](pdo.md) for the entity counterpart and the shared
`DomainContext`/proxy/SPI foundations,
[trip.md](../../../../tentackle-core/src/main/docs/trip.md) for the remoting that
lets a persistence operation run on the middle tier, and
[interceptors.md](../../../../tentackle-core/src/main/docs/interceptors.md) for
`@Transaction`, `@Secured`, and friends.
