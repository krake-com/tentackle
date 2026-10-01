# Immutable — Switchable Read-Only Objects

## Overview and Motivation

Many objects in an application must be protected against modification — but only
*sometimes*, and not necessarily forever:

- **Cached master data.** A [PDO](../../../../tentackle-pdo/src/main/docs/pdo.md)
  served from the read cache is shared by many threads; nobody may change it, or
  the change would silently leak to every other reader.
- **Read-only views.** A CRUD form opened for *viewing* must reject edits, while
  the very same entity class is fully editable in an *edit* form.
- **Remoting and aggregates.** A component loaded as part of another root's
  aggregate must not be persisted on its own, so it is handed out read-only.

Plain Java offers only `final` (compile-time, all-or-nothing) and the various
`Collections.unmodifiableXxx` wrappers (one-way, throw `UnsupportedOperationException`).
Tentackle needs something richer: an object that can be **switched** between
mutable and immutable at runtime, that can be **locked permanently** when needed,
that **cascades** the state to the components of an aggregate, and that — for
debugging — can **log** violations instead of throwing.

That mechanism is the
[`Immutable`](../java/org/tentackle/misc/Immutable.java) interface in
`org.tentackle.misc`. It is implemented by the framework's collections and by
every persistent entity.

> **`Immutable` vs. `Freezable` — don't confuse them.**
> [`Freezable`](../../../../tentackle-common/src/main/java/org/tentackle/common/Freezable.java)
> (in `tentackle-common`) is for mutable *value objects* — a
> `Date`, a money amount — which must become permanently read-only once they are
> stored in an entity field. Freezing is **one-way and final**: a frozen object
> can never be unfrozen (though its clones come out unfrozen). `Immutable`, by
> contrast, is a **switchable** state on container/entity objects that can normally
> be toggled back and forth — *unless* it has been made *finally* immutable.
> The two cooperate: a PDO's scalar attributes are frozen value objects, while the
> PDO itself is `Immutable`.

---

## The `Immutable` Contract

```java
public interface Immutable {
  void  setImmutable(boolean immutable);   // switch the state
  void  setFinallyImmutable();             // lock immutable forever
  boolean isImmutable();
  boolean isFinallyImmutable();
  void  setImmutableLoggingLevel(Level level);   // log instead of throw
  Level getImmutableLoggingLevel();
}
```

Every implementor moves between **three states**:

| State                 | `isImmutable()` | `isFinallyImmutable()` | Can change back?                     |
|-----------------------|:---------------:|:----------------------:|--------------------------------------|
| **mutable** (default) |     `false`     |        `false`         | —                                    |
| **immutable**         |     `true`      |        `false`         | yes — `setImmutable(false)`          |
| **finally immutable** |     `true`      |         `true`         | **no** — throws `ImmutableException` |

- Objects are **mutable by default**.
- `setImmutable(true)` / `setImmutable(false)` toggles the middle state.
- `setFinallyImmutable()` locks the object: any later attempt to make it mutable
  again throws an
  [`ImmutableException`](../java/org/tentackle/misc/ImmutableException.java)
  (an unchecked `TentackleRuntimeException`).
- The (im)mutable property **applies to all components as well**, provided they
  also implement `Immutable` — see *Cascading* below.

### The logging escape hatch

A violation normally **throws** `ImmutableException`. But if an
`immutableLoggingLevel` is set, the violation is **logged at that level and the
modification is allowed through** instead. This is a diagnostic aid: you can turn
a hard failure into a stack-trace in the log to discover *who* is illegally
mutating a shared/cached object, without crashing the application.

---

## How Mutators Are Guarded

The universal pattern is a single guard method, `assertMutable()`, called at the
top of every state-changing operation:

```java
protected void assertMutable() {
  if (isImmutable()) {
    RuntimeException ex = new ImmutableException("... is immutable");
    if (immutableLoggingLevel == null) {
      throw ex;                                   // hard failure
    }
    LOGGER.log(immutableLoggingLevel, ex.getMessage(), ex);   // soft: log & allow
  }
}
```

Each layer supplies its own version (collections throw `ImmutableException`
directly; entities wrap it in a `PersistenceException` so the offending object is
named), but the shape is identical.

---

## Collections

[`ImmutableCollection<E>`](../java/org/tentackle/misc/ImmutableCollection.java)
extends both `Collection<E>` and `Immutable`, adding the cascade-aware overloads
and a `simpleEqualsAndHashCode` switch.

The two concrete classes are
[`ImmutableArrayList`](../java/org/tentackle/misc/ImmutableArrayList.java) and the
modification-tracking
[`TrackedArrayList`](../java/org/tentackle/misc/TrackedArrayList.java) (see
[snapshots](../../../../tentackle-pdo/src/main/docs/snapshot.md)). Three points are
worth highlighting.

**1. Mutators assert first.** `add`, `remove`, `set`, `addAll`, `retainAll`, … all
call `assertMutable()` before touching the backing array:

```java
public boolean add(E e) {
  assertMutable();
  return super.add(e);
}
```

This includes the bulk operations of `ArrayList` that would otherwise bypass `remove(int)`
and `set(int, E)` — `clear`, `removeAll`, `removeIf`, `removeLast`, `replaceAll` and
`subList(…).clear()`. Operations that would not change the list pass even if it is immutable:
replacing an element by the very same instance, removing elements that aren't there, or clearing
an empty list. Sorting is not considered a modification. A `TrackedArrayList` additionally
refuses to become immutable while it still holds removed objects, and rejects any mutation of
a snapshot.

> Despite the name, an `ImmutableArrayList` is **mutable by default** — the class
> merely has the *ability* to become immutable. You must call `setImmutable(true)`.

**2. Cascade to elements (`withElements`).** The collection can push its state into
the elements it holds:

```java
public void setImmutable(boolean immutable, boolean withElements) {
  if (!immutable && finallyImmutable) {
    throw new ImmutableException("list is finally immutable");
  }
  this.immutable = immutable;
  if (withElements) {
    for (Object obj : this) {
      if (obj instanceof Immutable) {
        ((Immutable) obj).setImmutable(immutable);   // propagate
      }
    }
  }
}
```

The single-argument `setImmutable(boolean)` defaults `withElements` to `true`.

**3. Clones come out mutable.** Cloning an immutable list returns a **mutable**
copy (`immutable` and `finallyImmutable` are reset to `false` in `clone()`). This
is what lets a snapshot or a copy of an aggregate start life editable.

---

## Entities (PDOs)

Persistent objects implement `Immutable` in
[`AbstractDbObject`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/AbstractDbObject.java)
and refine it up the chain
(`AbstractDbObject` → `AbstractPersistentObject` → generated `…PersistenceImpl`).

### Switching state — with a safety check

```java
public void setImmutable(boolean immutable) {
  if (immutable) {
    if (attributesModified()) {
      // you may not freeze an object that still has unsaved edits
      throw new PersistenceException(this, new ImmutableException("object is already modified"));
    }
  }
  else if (isFinallyImmutable()) {
    throw new PersistenceException(this, new ImmutableException("object is finally immutable"));
  }
  this.immutable = immutable;
}

public void setFinallyImmutable() {
  setImmutable(true);
  finallyImmutable = true;
}
```

Making an object immutable is refused while it still has **modified attributes** —
immutability is meant to capture a *clean*, consistent state, not freeze a
half-edited one.

### Generated setters honor it

Every generated attribute setter calls `assertMutable()` *before* it changes
anything — and only when the value actually differs, so a no-op assignment to an
immutable object is harmless. From `NumberPoolPersistenceImpl`:

```java
public void setName(String name) {
  if (!Objects.equals(this.name, name)) {
    assertMutable();
    firePropertyChange(AN_NAME, this.name, name);
    this.name = name;
  }
}
```

For entities, `assertMutable()` is also overridden in `AbstractPersistentObject`
to additionally reject any mutation of a **snapshot** (`"object is a snapshot"`),
since [snapshots](../../../../tentackle-pdo/src/main/docs/snapshot.md) are immutable
by construction.

### Cascading through the aggregate

Immutability follows DDD aggregate boundaries: making a root immutable makes its
**composite components** immutable too. The
[`PdoRelations`](../../../../tentackle-persistence-wurblets/src/main/wurblets/org/tentackle/persist/wurblet/PdoRelations.wrbl)
wurblet generates a `setImmutable` override that walks each composite relation —
for example, in `NumberPoolPersistenceImpl`:

```java
public void setImmutable(boolean immutable) {
  super.setImmutable(immutable);
  if (numberRangeList != null) {
    numberRangeList.setImmutable(immutable);   // the TrackedList cascades on to its elements
  }
}
```

The same generated code also stamps the immutable flag onto component lists **at
load time**, so components that are lazily fetched into an already-immutable root
become immutable as well.

### `isPersistable`

An immutable object is, by default, **not persistable** — `isPersistable()`
returns `!isImmutable()`. Persistence operations (`insert`, `update`,
`saveCollection`, …) skip or refuse immutable objects, so a read-only object can
never be accidentally written back.

---

## A Separate Axis: Domain-Context Immutability

`Immutable` governs the object's *attributes and components*. Two related but
**independent** flags on
[`AbstractPersistentObject`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/AbstractPersistentObject.java)
and `AbstractDbObject` protect the object's *environment*:

- **`setDomainContextImmutable(boolean)`** — locks the
  [`DomainContext`](../../../../tentackle-pdo/src/main/docs/pdo.md) so the object
  cannot be re-homed into another tenant/session context.
- **`setSessionImmutable(boolean)`** — locks the database session the object is
  bound to.

A truly locked-down, shareable object sets all three, as the PDO cache does:

```java
pd.setFinallyImmutable();         // attributes + components frozen forever
pd.setDomainContextImmutable(true);
pd.setSessionImmutable(true);
```

---

## Where Immutability Is Applied

- **The PDO read cache.** Cached, read-only entities are made *finally* immutable
  (and context/session-immutable) so the shared instances handed to callers can
  never be mutated. See
  [`PdoCacheIndex`](../../../../tentackle-pdo/src/main/java/org/tentackle/pdo/PdoCacheIndex.java).
- **The model `immutable` relation option.** In a PDO's
  [model](../../../../tentackle-model/src/main/docs/model-definition.md), a relation
  may be declared `immutable`, which makes the loaded list and/or its objects
  immutable straight out of the database.
- **Read-only forms.** The FX/RDC CRUD layer puts an entity into a read-only state
  for "view" mode.
- **Snapshots.** Every [snapshot](../../../../tentackle-pdo/src/main/docs/snapshot.md)
  is immutable; a snapshot of a `finallyImmutable` object is itself immutable,
  whereas a `copy()` is **always mutable** (clones reset the flag).

---

## Worked Example

```java
NumberPool pool = ...;                       // a root with a numberRangeList component
pool.setImmutable(true);                     // freeze the whole aggregate

pool.setName("changed");                     // -> PersistenceException(ImmutableException)
pool.getNumberRangeList().add(range);        // -> ImmutableException (cascaded to the list)
assertFalse(pool.isPersistable());           // immutable => not writable

pool.setImmutable(false);                    // allowed: not *finally* immutable
pool.setName("changed");                     // ok again

pool.setFinallyImmutable();                  // lock for good
pool.setImmutable(false);                    // -> ImmutableException "finally immutable"

// diagnostic mode: don't crash, just find the culprit
pool.setImmutable(true);
pool.setImmutableLoggingLevel(Level.WARNING);
pool.setName("oops");                        // logged with stack trace, change applied
```

---

## Rules of Thumb

- **`Immutable` is switchable; `Freezable` is one-way.** Use `Immutable` for
  entities and containers, `Freezable` for value objects stored in fields.
- **`ImmutableArrayList` starts mutable.** The name describes the *capability*; call
  `setImmutable(true)` to engage it.
- **You cannot freeze a dirty object.** `setImmutable(true)` is refused while
  attributes are modified.
- **Immutability cascades** to composite components (and a list cascades to its
  elements) — making a root immutable makes the whole aggregate immutable.
- **Clones/copies are mutable**, even from a `finallyImmutable` original.
- **Immutable ⇒ not persistable.** Read-only objects are skipped by persistence.
- **Set a logging level to debug** illegal mutations instead of throwing.

## See Also

- [`Immutable`](../java/org/tentackle/misc/Immutable.java),
  [`ImmutableCollection`](../java/org/tentackle/misc/ImmutableCollection.java),
  [`ImmutableArrayList`](../java/org/tentackle/misc/ImmutableArrayList.java) — the core contracts and base impls.
- [`Freezable`](../../../../tentackle-common/src/main/java/org/tentackle/common/Freezable.java) — the one-way value-object counterpart ([freezable.md](../../../../tentackle-common/src/main/docs/freezable.md) explains why it exists and how it relates to mutable JDK types like `java.util.Date`).
- [snapshot.md](../../../../tentackle-pdo/src/main/docs/snapshot.md) — snapshots are immutable; copies are mutable.
- [pdo.md](../../../../tentackle-pdo/src/main/docs/pdo.md) — the aggregate model immutability cascades over.
- [model-definition.md](../../../../tentackle-model/src/main/docs/model-definition.md) — the `immutable` relation option.
- [misc.md](misc.md) — the `org.tentackle.misc` package these contracts and collections live in.
