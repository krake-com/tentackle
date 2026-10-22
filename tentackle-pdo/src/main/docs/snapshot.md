# Snapshots and Copies

## Overview and Motivation

A **snapshot** records the complete state of an object at a point in time so that
the object can later be **reverted** to exactly that state. The classic use case
is editing: before a user starts changing a [PDO](pdo.md) in a form, the
application takes a snapshot; if the user cancels, the PDO is reverted and looks
exactly as it did before — including all of its loaded components and the
modification flags of every object in the aggregate.

A **copy** is the closely related operation of producing a brand-new, independent
object that carries the same data. Both operations share the same machinery and
the same SPI contract, which is why they are documented together.

> **The single most important rule:** a snapshot is **not** a copy of the object.
> It does not represent "the object as it was" as a usable second object — it only
> holds the information needed to *revert the original* back to that state. You
> never work with a snapshot as if it were the entity; you hand it back to the
> object it came from.
>
> ```java
> NumberPool pool = ...;
> NumberPool snapshot = pool.createSnapshot();   // remember current state
> // ... user edits pool ...
> pool.revertToSnapshot(snapshot);               // undo all edits
> ```

The contract lives in the core module as the
[`Snapshotable<T>`](../../../../tentackle-core/src/main/java/org/tentackle/misc/Snapshotable.java)
interface; the two concrete implementations that matter in practice are
[`TrackedList`](../../../../tentackle-core/src/main/java/org/tentackle/misc/TrackedList.java)
(snapshotting a collection) and the PDO persistence delegate
[`AbstractPersistentObject`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/AbstractPersistentObject.java)
(snapshotting an entity and its aggregate).

---

## The `Snapshotable` Contract

Any type that supports snapshots implements `Snapshotable<T>`:

| Method                                           | Purpose                                                               |
|--------------------------------------------------|-----------------------------------------------------------------------|
| `T createSnapshot()`                             | Take a snapshot of the current state and register it.                 |
| `void revertToSnapshot(T snapshot)`              | Restore the object to a previously taken snapshot.                    |
| `boolean isSnapshot()`                           | True if *this* object is itself a snapshot (snapshots are immutable). |
| `List<? extends T> getSnapshots()`               | All snapshots still reachable, oldest first.                          |
| `void discardSnapshot(T)` / `discardSnapshots()` | Drop one / all snapshots.                                             |
| `T copy()`                                       | Produce an independent copy (implemented *via* snapshot+revert).      |
| `boolean isCopy()` / `setCopy(boolean)`          | Mark / query the "is a copy" state.                                   |

Two properties hold across all implementations:

- **Unbounded history.** You may take as many snapshots as you like. They form an
  ordered timeline.
- **Reverting truncates the future.** Reverting to a snapshot discards that
  snapshot *and every snapshot taken after it*. The timeline cannot fork — once
  you go back, the later states are gone.

### Snapshots are normally not held strongly

Implementations keep their registered snapshots through
`java.lang.ref.WeakReference`s. That means a snapshot that the application no
longer references can simply be garbage-collected, and it then quietly disappears
from `getSnapshots()`. As a result, **explicitly discarding a snapshot is usually
unnecessary** — just stop referencing it. `discardSnapshot()` exists for the cases
where you want the slot freed deterministically.

---

## Snapshotting a Collection — `TrackedList`

[`TrackedList`](../../../../tentackle-core/src/main/java/org/tentackle/misc/TrackedList.java)
is the framework's modification-tracking list (it knows which elements were added,
removed or replaced, which is what OR-mapping needs to decide what to write back).
It is `Snapshotable`, and its snapshot semantics are the model for everything else.

`createSnapshot()` in
[`TrackedArrayList`](../../../../tentackle-core/src/main/java/org/tentackle/misc/TrackedArrayList.java):

1. Clones the list (a clone is unmodified, mutable, with no removed objects).
2. Registers the clone as a snapshot (weakly).
3. Snapshots the **structure**: the set of removed objects and the modification
   flags (`modified`, `added`, `removed`, `immutable`, …).
4. Snapshots the **elements**. For every element:
   - if the element is itself `Snapshotable` (e.g., a component PDO), the list
     recursively calls `element.createSnapshot()` and stores that *element
     snapshot* in a parallel list (`elementSnapshots`);
   - otherwise only the **reference** to the element is stored.

This parallel `elementSnapshots` list maintains a strict 1:1 relationship with the
snapshot list, which is why **snapshots are immutable** — changing them would break
that correspondence.

`revertToSnapshot()` then has two modes:

- **Normal revert** (`isCopy() == false`): the list restores its own structure
  (removed objects, flags, contents) from the snapshot, and for every
  `Snapshotable` element calls `element.revertToSnapshot(elementSnapshot)` so the
  whole tree rolls back in lockstep.
- **Copy revert** (`isCopy() == true`): instead of restoring, it builds fresh
  content by calling `element.copy()` on each snapshot element — this is how deep
  copies of aggregates are produced (see below).

The static helpers `TrackedList.createSnapshot(list)` and
`TrackedList.revertToSnapshot(list, snapshot)` null-guard these calls and are what
the generated persistence code uses.

---

## Snapshotting a PDO

A PDO snapshot is taken on the **persistence delegate**
([`AbstractPersistentObject`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/AbstractPersistentObject.java));
the domain delegate carries no snapshot state of its own). It has to cover both
the entity's own attributes *and* its loaded components, so the aggregate reverts
as a unit.

### Taking the snapshot — `createSnapshot()`

```java
public T createSnapshot() {
  T snapshot = PdoFactory.getInstance().create(getPdoClass(), clone());   // 1
  PersistentObject<T> persistentSnapshot = snapshot.getPersistenceDelegate();
  persistentSnapshot.setDomainContext(getDomainContext());               // 2
  getClassVariables().getCreateComponentsInSnapshotMethod()
                     .invoke(this, persistentSnapshot);                  // 3
  addSnapshot(snapshot);                                                 // 4
  snapshot.setCopy(false);
  return snapshot;
}
```

1. **Clone the delegate.** `clone()` is `final` (so applications cannot break the
   contract) and produces a shallow copy of the persistence object with its proxy
   reference cleared. The clone is then wrapped in a fresh PDO proxy. Because the
   attributes of a PDO are either immutable types or *frozen* values, a shallow
   clone is enough for them — there is deliberately **no
   `createAttributesInSnapshot`** step.
2. **Carry over the domain context** so the snapshot is a fully usable PDO.
3. **Snapshot the components.** `createComponentsInSnapshot(...)` is the
   per-entity hook that snapshots each component `TrackedList`. It is *generated*
   (see below) and marks the snapshot object with `objectIsSnapshot = true`.
4. **Register** the snapshot (weakly) on the original.

### Reverting — `revertToSnapshot()`

```java
public void revertToSnapshot(T snapshot) {
  if (snapshot != null) {
    assertValidSnapshot(snapshot);    // must be a snapshot, and *my* snapshot
    applySnapshot(snapshot);
  }
}
```

`applySnapshot()` invokes two generated hooks on the original object, passing the
snapshot's delegate:

- `revertAttributesToSnapshot(snapshot)` — copies the scalar fields back.
- `revertComponentsToSnapshot(snapshot)` — reverts each component list via
  `TrackedList.revertToSnapshot(...)`.

`assertValidSnapshot()` enforces the two invariants: the argument must actually be
a snapshot (`isSnapshot()`), and — unless we are building a copy — it must be one
of *this* object's own registered snapshots. Reverting to a snapshot that was
already discarded (by reverting to it or to an earlier snapshot, or explicitly), or
that belongs to a different object, throws a `PersistenceException` with
`"not my snapshot"`. After a successful revert, the snapshot and all later ones are
discarded.

> `id`, `serial` and `tableSerial` are deliberately **not** reverted — they can
> only change through an actual persistence operation, so rolling them back would
> desynchronize the object from the database row it represents.

### What gets reverted

`revertAttributesToSnapshot` runs up the inheritance chain
(`AbstractDbObject` → `AbstractPersistentObject` → generated impl), restoring at
each level:

- **Generated level** (e.g.
  [`NumberPoolPersistenceImpl`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/ns/NumberPoolPersistenceImpl.java)):
  every mapped attribute and its `…Persisted` shadow field.
- **`AbstractPersistentObject`**: context immutability, cache bookkeeping,
  expiry, normtext, validation and persistable flags. The snapshot list is not
  reverted but truncated, as described above.
- **`AbstractDbObject`**: the session (holder), and — **only when not a copy**, because
  copies are always mutable and new — `modified`, the `immutable` / `finallyImmutable`
  flags, `overloadable`, property support, and the session's immutability.

### How the snapshot hooks are wired (generated code)

The three hooks
(`createComponentsInSnapshot`, `revertAttributesToSnapshot`,
`revertComponentsToSnapshot`) are typed to the concrete implementation class, so
they cannot be called through a fixed interface signature. Instead
[`PersistentObjectClassVariables`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/PersistentObjectClassVariables.java)
locates them reflectively with `findSnapshotMethod()`, walking the class hierarchy
to find the **most specific** override:

```java
Method method = clazz.getDeclaredMethod(methodName, clazz);   // exact impl type
```

The methods themselves are emitted into the persistence implementation by the
[`PdoRelations`](../../../../tentackle-persistence-wurblets/src/main/wurblets/org/tentackle/persist/wurblet/PdoRelations.wrbl)
and
[`MethodsImpl`](../../../../tentackle-persistence-wurblets/src/main/wurblets/org/tentackle/persist/wurblet/MethodsImpl.wrbl)
[wurblets](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md).
For each component relation the generator produces a transient
`…Snapshot` field plus the create/revert logic — for example, in
`NumberPoolPersistenceImpl`:

```java
private transient TrackedList<NumberRange> numberRangeListSnapshot;

protected void createComponentsInSnapshot(NumberPoolPersistenceImpl snapshot) {
  super.createComponentsInSnapshot(snapshot);
  snapshot.numberRangeListSnapshot = TrackedList.createSnapshot(numberRangeList);
}

protected void revertComponentsToSnapshot(NumberPoolPersistenceImpl snapshot) {
  super.revertComponentsToSnapshot(snapshot);
  numberRangeList = TrackedList.revertToSnapshot(numberRangeList, snapshot.numberRangeListSnapshot);
  numberRangeListLoaded = snapshot.numberRangeListLoaded;
}
```

Because the component lists are themselves `Snapshotable`, the `TrackedList`
recursion carries the snapshot all the way down the aggregate — root, components,
sub-components — automatically.

### Snapshots are immutable

A snapshot PDO has `isSnapshot() == true`, and `assertMutable()` is overridden to
reject any setter on it (`"object is a snapshot"`). This protects the parallel
element-snapshot bookkeeping in the lists from being corrupted. If you need an
editable second object, take a **copy**, not a snapshot.

---

## Copies

`copy()` produces a **new, independent, mutable** object with the same data,
implemented elegantly on top of snapshots:

```java
public T copy() {
  T copiedPdo = on();                                  // a new, empty PDO
  AbstractPersistentObject copiedPo = copiedPdo.getPersistenceDelegate();
  DomainContext ctx = copiedPo.getDomainContext();
  copiedPo.objectIsCopy = true;                        // changes how revert behaves
  copiedPo.applySnapshot(pdo.createSnapshot());        // pour the state into the new object
  copiedPo.setModified(true);
  copiedPo.setDomainContext(ctx);                      // restore the new object's root context
  return copiedPdo;
}
```

The trick is the `objectIsCopy` flag: when the new (empty) object reverts the
source's snapshot, the "copy revert" branch runs everywhere — `TrackedList`
rebuilds its contents by **copying** each element rather than reverting it, and
the attribute revert skips the identity/immutability fields. The result:

- The copy and every component are **new** (`isNew()`, `id == 0`, `serial == 0`).
- The copy is **always mutable**, even if the source was `finallyImmutable`.
- Only **loaded** components are copied (a snapshot captures the object *as is*).
  If you want a logically complete copy of the whole aggregate, call
  `loadComponents(false)` before `copy()`.

> A `copy()` is *semantically* equivalent but not a *technically identical* clone.
> If you need a byte-for-byte deep copy (same ids, same serials), use
> [`ObjectSerializer.copy(Object)`](../../../../tentackle-common/src/main/java/org/tentackle/common/ObjectSerializer.java)
> instead, which round-trips the object through serialization.

A real-world use of `copy()` is the CRUD "duplicate" action in
[`PdoCrud`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/crud/PdoCrud.java):
the current PDO's components are loaded and `pdo.copy()` becomes the new, unsaved
entity in the form.

---

## Worked Example

From
[`SnapshotTest`](../../../../tentackle-test-pdo/src/test/java/org/tentackle/pdo/SnapshotTest.java):

```java
NumberPool numberPool = ... ;                  // root with two NumberRange components
numberPool = numberPool.persist();
long id = numberPool.getId();
assertFalse(numberPool.isModified());

NumberPool snapshot = numberPool.createSnapshot();   // remember the saved state

// mutate the aggregate: add a component, change an attribute
NumberRange r = ...; numberPool.getNumberRangeList().add(r);
numberPool.setRealm("none");
assertEquals(numberPool.getNumberRangeList().size(), 3);
assertTrue(numberPool.isModified());

numberPool.revertToSnapshot(snapshot);               // roll the whole aggregate back
assertFalse(numberPool.isModified());                // modification flag restored
assertEquals(numberPool.getId(), id);                // identity untouched
assertEquals(numberPool.getRealm(), "test");         // attribute restored
assertEquals(numberPool.getNumberRangeList().size(), 2);   // component list restored
```

Note how reverting restores the **modification flag**, the **attribute**, *and*
the **component list size** in one call — the entire aggregate moves together.

Reverting has discarded the snapshot, so trying to revert to it again — for example,
after the object was changed and persisted once more — throws:

```java
numberPool = numberPool.persist();          // serial advances
numberPool.revertToSnapshot(snapshot);      // -> PersistenceException "not my snapshot"
```

And a copy:

```java
NumberPool copy = numberPool.copy();
assertTrue(copy.isCopy());
assertTrue(copy.isNew());                    // brand new, id/serial == 0
assertEquals(copy.getNumberRangeList().size(), 2);
for (NumberRange range : copy.getNumberRangeList()) {
  assertTrue(range.isNew());                 // components are new too
}

numberPool.setFinallyImmutable();
assertTrue(numberPool.createSnapshot().isImmutable());   // snapshots inherit immutability
assertFalse(numberPool.copy().isImmutable());            // copies are always mutable
```

---

## Rules of Thumb

- **Use a snapshot to undo**, a copy to **duplicate**. Never treat a snapshot as a
  usable second object — it is immutable and belongs to its origin.
- **Reverting truncates the timeline.** The snapshot you revert to, and all later
  ones, become invalid.
- **You normally don't discard snapshots** — weak references clean them up. Discard
  only when you want the slot freed deterministically.
- **Identity is never reverted.** `id`/`serial`/`tableSerial` survive a revert, since
  only a persistence operation may change them.
- **Snapshots/copies capture the object *as is*.** Call `loadComponents(false)`
  first if you need the full aggregate.
- For a technically identical deep clone (same ids), reach for
  `ObjectSerializer.copy()` rather than `Snapshotable.copy()`.

## See Also

- [pdo.md](pdo.md) — the PDO / two-delegate architecture these snapshots operate on.
- [immutable.md](../../../../tentackle-core/src/main/docs/immutable.md) — the immutability model; snapshots are immutable, copies are mutable.
- [operation.md](operation.md) — operations, the entity-less sibling of PDOs.
- [`Snapshotable`](../../../../tentackle-core/src/main/java/org/tentackle/misc/Snapshotable.java),
  [`TrackedList`](../../../../tentackle-core/src/main/java/org/tentackle/misc/TrackedList.java) — the core contracts
  ([misc.md](../../../../tentackle-core/src/main/docs/misc.md) covers the package they live in, including `TrackedList`'s modification tracking).
- [persistence-wurblets.md](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md) — the code generation that wires component snapshots.
