# Correctness First — Why Tentackle Refuses to Be Wrong Quietly

## The claim

Tentackle is best known for **location transparency** — the same code runs at any tier and a PDO is fully
usable in any JVM (see [the multi-tier cascade](./tiers.md) and
[why it fits technical and scientific applications](./technical-scientific.md)). But location transparency is
only the *second* design commitment. The first one, the one the rest of the framework is bent around, is this:

> **A Tentackle application should be hard to make subtly wrong, and when something *is* wrong, it should fail
> loudly and early rather than corrupt data quietly.**

This document is about that commitment — what "correctness first" means, why it matters disproportionately for
*technical and scientific* software, and which concrete mechanisms in the architecture deliver it. None of these
mechanisms is a bolt-on or an optional best practice you have to remember to apply; each is wired into the place
where the relevant mistake would otherwise be made, so the *default* behavior is the correct one, and the unsafe
path is the one you have to go out of your way to take.

---

## What "correctness" means here

"Correctness" is an overloaded word, so it is worth being precise about which failure modes Tentackle sets out to
eliminate. They are the ones that do not announce themselves:

- **Silent data corruption** — a lost update, a half-written aggregate, a stale read that a user acts on.
- **Latent invalidity** — an object that violates a business rule but was persisted anyway because the rule lived
  in a UI handler that this particular code path never went through.
- **Broken aggregate boundaries** — a child written or mutated on its own, behind the root that owns the cluster's
  invariants, so the aggregate is invalid *as a whole* even though every individual row is well-formed in isolation.
- **Shared-state aliasing** — one caller mutating an object another caller is relying on because they happen to
  hold the same cached instance.
- **Lifecycle hazards** — an object that is valid here and throws there, depending on an ambient scope the caller
  cannot see (the classic detached-entity bug).
- **Skew across a fleet** — two nodes that disagree about the shape of the data because they run different
   versions and discover it only when a deserialization goes wrong in production.

A framework that is "correct first" is one that makes each of these failures either **impossible by
construction** or **loud and immediate** when it is attempted. Tentackle aims for one of those two outcomes in
every case above. The unifying principle is **fail-fast over fail-quiet**: an exception at the moment of the
mistake, naming the offending object, is always preferred to a wrong number that surfaces three weeks later in a
report.

---

## Why this matters more for technical applications

Business software is forgiving in a way that technical and scientific software is not, and the difference is not
about how much anyone cares — it is structural.

- **The data is the deliverable, and it is often irreproducible.** A measurement series, a batch genealogy, a
  device's recorded state at a moment in time — these cannot be re-keyed from a paper trail if they are corrupted.
  A wrong row in an order table can be reconciled against an invoice; a wrong row in an acquisition table may be a
  destroyed experiment.
- **Wrong is worse than down.** In a plant- or laboratory context, an application that *stops* is a nuisance; an
  application that keeps running while writing subtly wrong setpoints, measurements, or interlocks is a hazard. The
  correct response to an inconsistency is to refuse the operation and say so — not to do its best and carry on.
- **Provenance and reproducibility are requirements, not nice-to-haves.** Regulated and scientific work has to be
  able to say *exactly* what state an object was in and why. That demands aggregates that move as a unit, edits
  that can be reverted exactly, and shared snapshots that cannot drift.
- **Concurrency is real and adversarial.** Multiple operators, automated controllers, and batch jobs touch the same
  entities at once, often across [constrained links and many tiers](./technical-scientific.md). A lost-update
  window that is "rare enough to ignore" in a small office app is a daily occurrence on a busy line.
- **Fleets are long-lived and version-skewed.** Instruments outlive software cycles; nodes run mixed versions for
  years. Correctness has to survive class evolution, not just a single homogeneous deployment.

In this regime the cost asymmetry is extreme: the price of a spurious exception is a retry; the price of a silent
wrong write can be a ruined run, a safety incident, or an unreproducible result. Tentackle is tuned for that
asymmetry on purpose.

---

## How the architecture honors the claim

The sections below walk the mechanisms from the lowest-level guarantee outward. The throughline is that each one
puts the check *at the boundary where the mistake happens*, so it cannot be forgotten.

### 1. No lost update, unconditionally — the `serial` column

Every persistent object carries a `serial` version counter that is incremented on every write, and every
`UPDATE`/`DELETE` carries the serial the object had **when it was read**:

```sql
UPDATE invoice SET ... , serial = serial + 1   WHERE id = ? AND serial = ?
DELETE FROM  invoice                            WHERE id = ? AND serial = ?
```

If another transaction changed the row in the meantime, zero rows match, and the write is rejected — Tentackle
re-reads the row to distinguish "changed under me" (a temporary failure a `@Transaction` may retry) from "deleted
under me" (permanent). This is *optimistic locking*, and the crucial property is that it **cannot be turned off**:
it is a side effect of the normal write path, costs nothing on the happy path, and holds no database locks. A
stale write is *always* rejected, whether it comes from an interactive form, a batch job, an import, or an admin
tool that never asked for a lock.

On top of that floor sits the **opt-in token lock** (`editedby`/`editedsince`/`editedexpiry`), which moves the
discovery of a conflict to the *start* of editing — granted atomically by a single conditional `UPDATE` that is
*itself* serial-checked, and time-boxed so a misbehaving client cannot block an object forever. 
When clients crash, their token locks are automatically released.
The two layers are complementary: the token gives a friendly early warning, the serial gives the hard guarantee. 
The full design, including the SQL and the failure distinctions, is in
[locking.md](../../../tentackle-persistence/src/main/docs/locking.md).

> The design choice worth noticing: the unconditional guarantee is the cheap, always-on one, and the convenient
> early-warning layer is the opt-in extra. Correctness is the floor you cannot remove; UX is the layer you add.

### 2. Validation that runs where it must, and travels

A business rule that lives in a UI event handler is only enforced on the code paths that happen to go through that
handler. Tentackle's [validation layer](../../../tentackle-core/src/main/docs/validation.md) instead attaches the
rule, **as an annotation, to the member it guards**, and the persistence layer validates a PDO *before it is
saved* — so the same rule holds whether the object was edited in a form, constructed by an importer, or modified
by a remote service.

```java
@NotNull(message = "missing class ID", condition = "$isClassIdNecessary")
@GreaterOrEqual(value = "100", condition = "$isClassIdNecessary", message = "class ID must be >= 100")
Integer classId;
```

Three properties matter for correctness specifically:

- **Scope-aware.** The *same* object is validated differently depending on the situation — a
  `PersistenceScope` constraint is enforced before persisting, an `InteractiveScope` one only during editing — so
  one annotated class serves every context without duplicating (and desynchronizing) the rules.
- **Severity-aware.** A failed check is not automatically an error; `MinorSeverity` yields an informational
  warning, `MajorSeverity` a hard failure. The model decides which is which, explicitly.
- **Remoting-ready.** A `ValidationResult` is serializable and carries a *validation path*
  (`invoice.lineList[3].price`) that survives a [TRIP](../../../tentackle-core/src/main/docs/trip.md) round-trip,
  so a server can validate authoritatively and a client can still point at the exact widget that failed. The
  authoritative check does not move to the client just because the UI is there.

There is even a CI aid — `configureTestMode()` forces *every* validator to run regardless of scope or condition —
so a build can prove that all constraints wire up, all scripts compile, and no validator throws, before any of
them is relied on in production.

### 3. Shared objects that cannot be mutated behind your back — `Immutable`

A read-only PDO served from the [cache](../../../tentackle-pdo/src/main/docs/cache.md) is shared by every thread
in the JVM. If one caller could mutate it, the change would silently leak to every other reader — the
shared-state aliasing failure. Tentackle closes this with the
[`Immutable`](../../../tentackle-core/src/main/docs/immutable.md) contract: a switchable, optionally *permanent*
read-only state enforced by an `assertMutable()` guard at the top of **every** generated setter and collection
mutator.

```java
public void setName(String name) {
  if (!Objects.equals(this.name, name)) {
    assertMutable();                 // throws if immutable — before anything changes
    firePropertyChange(AN_NAME, this.name, name);
    this.name = name;
  }
}
```

The correctness-relevant guarantees:

- **Cached entities are made *finally* immutable** (and context/session-immutable), so the shared instance handed
  to callers can never be mutated, ever — a violation throws rather than corrupts.
- **Immutability cascades along aggregate boundaries.** Freezing a root freezes its components and their lists, so
  you cannot accidentally mutate a child of a shared object.
- **You cannot freeze a dirty object.** `setImmutable(true)` is refused while attributes are still modified —
  immutability is meant to capture a *clean, consistent* state, never a half-edited one.
- **Immutable ⇒ not persistable.** `isPersistable()` returns `!isImmutable()`, so a read-only object is skipped by
  persistence and can never be written back by mistake.
- **A diagnostic escape hatch, not a silent one.** If you must hunt down an illegal mutation, you set a logging
  level so the violation is logged *with a stack trace* and allowed through — opt-in, loud, and temporary, rather
  than a quiet default.

### 4. Exact, atomic undo — snapshots

Interactive editing needs a reliable "cancel". A naïve undo that restores some fields but forgets a component list
or a modification flag leaves the object in a state that never existed — a subtle corruption. Tentackle's
[snapshots](../../../tentackle-pdo/src/main/docs/snapshot.md) revert an **entire aggregate as a unit**: root
attributes, every loaded component list, and the modification flags of every object in the tree all roll back in
one call, recursing down the `TrackedList` structure.

```java
numberPool.revertToSnapshot(snapshot);                     // one call rolls back the whole aggregate
assertFalse(numberPool.isModified());                      // modification flag restored
assertEquals(numberPool.getNumberRangeList().size(), 2);   // component list restored too
```

Two deliberate refusals keep this honest:

- **Identity is never reverted.** `id`, `serial` and `tableSerial` are *not* rolled back, because they can only
  change through a real persistence operation — rolling them back would desynchronize the object from its database
  row.
- **A stale snapshot is rejected, not silently misapplied.** Once you persist after reverting, the old snapshot
  belongs to a superseded version; reverting to it throws `"not my snapshot"` rather than quietly restoring a
  state the database no longer agrees with.

Snapshots are immutable by construction (mutating one would corrupt the parallel element-snapshot bookkeeping),
and a `copy()` — the operation you use when you *do* want a usable second object — is always mutable and new. The
two are kept distinct precisely so neither can be misused as the other.

### 5. Aggregates that change only as a whole — enforced DDD boundaries

Correctness is not only a property of individual objects; it is a property of the **aggregate**. In Domain-Driven
Design the aggregate root *owns the cluster's invariants*: the aggregate is the **unit of consistency**, its internals
may be changed only through the root, and the root validates the *whole* cluster before any of it is allowed to
persist. In most frameworks that rule is a **convention** — true only as long as every developer remembers it on every
code path, and silently broken the moment one does not (the well-formed child row written behind its parent's back).
Tentackle does not leave it to discipline: the aggregate boundary is a **structural fact the persistence layer knows
about and enforces**, so the DDD-correct way is the *only* way.

The layer knows which PDO is a **root entity** and which are its **components**, and each component names its root
directly — by id and type, through the [`rootId`/`rootClassId`](../../../tentackle-pdo/src/main/docs/root-columns.md)
columns — *without a database query*. From that one structural fact, four boundary guarantees follow, each a **refusal
by the framework** rather than a recommendation to the programmer:

- **A component cannot be persisted on its own.** There is no "create a child, set its parent id, save the child" path.
  A component may be persisted only within the **root context of its own root entity**; attempt it through any other
  context, and the persistence layer throws, fail-fast. The orphaned write that a flat ORM waves through simply has *no
  API* in Tentackle.

  ```java
  InvoiceLine line = invoice.on(InvoiceLine.class);  // born into the invoice's root context
  line.setAmount(-500);
  invoice.getLines().add(line);
  invoice.persist();        // validates the ENTIRE aggregate, rejects the negative total
                            // → nothing is written; the bad state never reaches the database
  ```

- **The only door is the root, and it always validates the whole.** Persisting the root validates the *entire* cluster
  (§2) as a unit before a single row is touched — including cross-entity invariants such as *"the sum of the lines must
  be positive"* that no per-row field annotation can express. Whole-aggregate validation **is** the write; there is no
  door into the database that bypasses it.
- **A component out of context is read-only.** A component loaded outside its own root context — reached through a deep
  reference without its owning root — is made **finally immutable** (§3), so you cannot mutate an aggregate's internals
  while its root is absent to validate the change.
- **Visibility is aggregate-scoped.** Authorization is enforced at aggregate granularity: a component is visible only if
  its root is. If you may not read the `Invoice`, its `InvoiceLine`s behave as if they did not exist — the root's read
  permission is evaluated from the two coordinates carried on the component row, at no extra query.

And because undo is aggregate-scoped too, a [snapshot](../../../tentackle-pdo/src/main/docs/snapshot.md) (§4) reverts
the *entire* aggregate atomically. The unit of consistency in the code is exactly the unit of consistency in the
domain.

> The aggregate is a real thing the framework knows about, not a pattern superimposed on a flat set of
> independently-persistable rows. That is what turns "honor the aggregate boundary" from advice into an invariant. The
> full mechanics are in
> [pdo-vs-orm.md → Aggregates: Validated as a Whole, or Not Written at All](../../../tentackle-pdo/src/main/docs/pdo-vs-orm.md#aggregates-validated-as-a-whole-or-not-written-at-all)
> and [root-columns.md](../../../tentackle-pdo/src/main/docs/root-columns.md).

### 6. Objects that are never in a degraded state — the PDO model

A large class of "valid here, broken there" bugs comes from objects whose usability depends on an ambient,
invisible scope. The canonical example is the JPA detached entity that throws `LazyInitializationException` the
moment you navigate a relation outside the persistence context that loaded it.

A PDO has **no lifecycle states at all**. It carries its session and context inside itself and can always reach a
working session in *any* JVM, so a relation can be navigated at any time, on any tier:

```java
Customer c = service.loadCustomer(id);    // returned from anywhere, any tier
List<Invoice> invoices = c.getInvoices(); // just works — fetched on demand, no scope ceremony
```

> **There is no `LazyInitializationException` in Tentackle — the exception type does not exist.**

This is a correctness property, not just a convenience: behavior can safely live *on* the entity (rich domain
objects, not anemic data bags) because the entity is never in a degraded state from which its own methods would
throw. The full comparison, including why this also removes DTOs, `merge()`, fetch plans, and manual `em.clear()`,
is in [pdo-vs-orm.md](../../../tentackle-pdo/src/main/docs/pdo-vs-orm.md). Transaction demarcation is cleanly
separated from object lifetime: a
[`@Transaction`](../../../tentackle-pdo/src/main/docs/pdo-vs-orm.md#transactions-demarcation-vs-object-lifecycle)
controls *the database*, not *your objects*, so there is no "now it's detached" moment to reason about.

Aggregates build directly on this: because a root and its components know each other without a query, rich behavior
can live on the aggregate as a whole — and the boundary around it is enforced, not merely documented, as §5 details.

### 7. Cross-cutting rules that cannot be forgotten — interceptors

Transactions, permission checks, and pre/post-commit hooks are exactly the concerns that, written by hand, get
omitted on the one method that needed them. Tentackle's
[interceptors](../../../tentackle-core/src/main/docs/interceptors.md) make them **declarative annotations on the
method**, woven in by the proxy so they apply uniformly, locally or across [TRIP](../../../tentackle-core/src/main/docs/trip.md):

```java
@Transaction                          // begin/commit, rollback on exception, optional retry
@Secured(BookInvoicePermission.class) // permission checked before the body runs
void book();
```

For correctness the important details are:

- **Deterministic ordering.** The chain is built in a defined order (public interceptors in interface-source
  order, then hidden ones) — `Transaction → Secured → book()` — so the security check and the transaction boundary
  compose the same way every time, never racing.
- **Transparent retry on optimistic conflict.** Because the `serial` check (§1) raises a *temporary*
  `NotFoundException`, a `@Transaction` can re-run the whole transaction against the now-current row — turning a
  detected conflict into an automatic, correct retry rather than a surfaced error.
- **Compile-time verification.** Misplacing an interceptor annotation (a `PUBLIC` interceptor on a class method, or
  any interceptor on a non-`Interceptable` method) is a **compilation error**, not a runtime surprise. The mistake
  is caught before the code ever runs.

### 8. One source of truth, generated — the model

The shape of an entity, its columns, its relations, its validations, and its indexes are defined **once**, in the
[model](../../../tentackle-model/src/main/docs/model-definition.md) embedded in the PDO interface. The persistence
interfaces and implementations, the SQL DDL, and the migration scripts are all *generated* from that single
definition by the [Wurbelizer](../../../tentackle-wurblets/src/main/docs/wurblets.md). There is no second place
where the mapping is restated and could drift out of step with the first — the class, the table, and the migration
cannot disagree, because they share an origin. Generated code is woven into guarded regions, and the build's
annotation processors check usage at compile time, so a malformed model fails the build rather than producing
plausible-looking wrong output.

### 9. Correctness across a version-skewed fleet — TRIP

When PDOs travel between JVMs that may run different versions, a naïve serializer fails *late* and *cryptically*.
[TRIP](../../../tentackle-core/src/main/docs/trip.md) carries built-in client/server **version checking**
(`checkServerVersion` / `VersionIncompatibleException`) and a **type dictionary** tolerant of class evolution, so
incompatibility is reported as a clear, immediate error at connect time rather than as a corrupt object three
calls later. Combined with explicit [JPMS](./modules.md) encapsulation, the `ServiceFinder` SPI that works
identically in modular and non-modular deployments, deterministic interceptor ordering, and immutable thread-shared
cached PDOs, the whole stack behaves **reproducibly** — the same inputs produce the same result regardless of how
the application happens to be packaged or how many tiers separate it from the database.

---

## The pattern, in one table

Every mechanism above follows the same shape: put the check at the boundary where the mistake would be made, make
the safe behavior the default, and fail loudly when the safe path is violated.

| Failure mode it would otherwise allow              | Mechanism                                                                              | What makes it fail-fast, not fail-quiet                                                                                  |
|----------------------------------------------------|----------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| Lost update / stale write                          | [`serial` optimistic locking](../../../tentackle-persistence/src/main/docs/locking.md) | Always on; a stale `UPDATE` matches zero rows and is rejected — no DB locks, no opt-out                                  |
| Editing collision discovered too late              | [Token lock](../../../tentackle-persistence/src/main/docs/locking.md)                  | Atomic, serial-checked, auto-expiring reservation; names the holder on conflict                                          |
| Rule enforced on only some code paths              | [Validation](../../../tentackle-core/src/main/docs/validation.md)                      | Declared on the member, run before persist, scope/severity-aware, travels over TRIP                                      |
| Shared cached object mutated by one caller         | [`Immutable`](../../../tentackle-core/src/main/docs/immutable.md)                      | `assertMutable()` guard in every setter; cached PDOs finally immutable; immutable ⇒ not persistable                      |
| Half-applied undo leaving an impossible state      | [Snapshots](../../../tentackle-pdo/src/main/docs/snapshot.md)                          | Whole aggregate reverts atomically; identity never reverted; stale snapshot throws                                       |
| Child written or mutated behind its aggregate root | [Aggregate boundaries](../../../tentackle-pdo/src/main/docs/root-columns.md)           | Component persistable only in its root's context; root validates the whole aggregate; out-of-context component immutable |
| Object valid here, broken there                    | [PDO model](../../../tentackle-pdo/src/main/docs/pdo-vs-orm.md)                        | No lifecycle states; no `LazyInitializationException`; behaviour safe on the object                                      |
| Forgotten transaction / permission check           | [Interceptors](../../../tentackle-core/src/main/docs/interceptors.md)                  | Declarative, deterministically ordered, compile-time-verified placement                                                  |
| Mapping drifts from schema                         | [Model + Wurbelizer](../../../tentackle-model/src/main/docs/model-definition.md)       | Single definition generates classes, DDL and migrations together                                                         |
| Silent corruption across versions                  | [TRIP](../../../tentackle-core/src/main/docs/trip.md)                                  | Version check + type dictionary fail clearly at connect, not mid-stream                                                  |

---

## An honest accounting

Correctness-first is a set of trade-offs, and a fair description names what they cost:

- **More exceptions, by design.** Tentackle would rather throw a `PersistenceException` naming the offending
  object than do its best with inconsistent state. Code has to be written to expect and handle conflict
  (`@Transaction` retry, `LockException` on token contention). That is the point, but it is real work.
- **No implicit, always-on identity map.** Dropping the persistence context's identity map is what makes PDOs
  behave like ordinary objects (§5), but it means two independent `select()`s of the same id return equal-but-not-
  `==` instances unless you opt into the [`PdoCache`](../../../tentackle-pdo/src/main/docs/cache.md). Shared
  identity is a deliberate, model-driven choice, not a global default — see the full discussion in
  [pdo-vs-orm.md](../../../tentackle-pdo/src/main/docs/pdo-vs-orm.md#what-you-give-up-an-honest-accounting).
- **Explicit saves, not automatic dirty-flush.** You call `persist()`/`save()` where you mean it. Many teams
  consider the explicit write a correctness feature — the mutation is where you put it — but it is a different
  habit from JPA's write-behind.
- **A smaller ecosystem.** These guarantees come from a cohesive, opinionated stack rather than a mainstream one,
  with the familiar trade-off in tooling and hiring (see
  [when a business framework would be the better fit](./technical-scientific.md#when-a-business-framework-would-be-the-better-fit)).

The trade Tentackle makes is consistent across all of them: spend a little more ceremony, accept a few more loud
failures, and in exchange make the quiet, expensive failures — the corrupted measurement, the lost update, the
object that throws only in production — structurally hard to reach. For technical and scientific software, where
*wrong* costs far more than *down*, that is the trade worth making.

---

## Related Documentation

- [Why Tentackle Fits Technical and Scientific Applications](./technical-scientific.md) — the location-transparency
  companion to this document.
- [Multi-Tier Cascade](./tiers.md) — how the same artifact runs at any tier, and how caching stays coherent.
- [Exceptions and Error Handling](./exceptions.md) — the machinery behind "fail loudly": the exception hierarchy, the `temporary` classifier, and exceptions that survive a trip between tiers.
- [Locking](../../../tentackle-persistence/src/main/docs/locking.md) — optimistic `serial` and token locks in depth.
- [Validation](../../../tentackle-core/src/main/docs/validation.md) — annotation-driven, scope- and severity-aware checks.
- [Immutable](../../../tentackle-core/src/main/docs/immutable.md) — switchable read-only objects and aggregate cascading.
- [Snapshots and Copies](../../../tentackle-pdo/src/main/docs/snapshot.md) — exact, atomic aggregate undo.
- [Root Columns (`rootId`/`rootClassId`)](../../../tentackle-pdo/src/main/docs/root-columns.md) — how the aggregate boundary is made physical and enforced.
- [PDOs vs. Traditional ORMs](../../../tentackle-pdo/src/main/docs/pdo-vs-orm.md) — why a PDO is never in a degraded state, and why the aggregate is validated as a whole or not written at all.
- [Interceptors](../../../tentackle-core/src/main/docs/interceptors.md) — declarative, ordered, compile-time-verified cross-cutting concerns.
- [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md) — the single source the persistence layer is generated from.
- [TRIP](../../../tentackle-core/src/main/docs/trip.md) — versioned, evolution-tolerant remoting.
