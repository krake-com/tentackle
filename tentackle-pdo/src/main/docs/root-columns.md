# The `rootId` and `rootClassId` Columns

## Overview and Motivation

Two columns are quietly added to entity tables by the Tentackle model when — and
only when — they are actually needed:

| Attribute     | Column        | Java type | Holds                                                      |
|---------------|---------------|-----------|------------------------------------------------------------|
| `rootId`      | `rootid`      | `long`    | the **id** of the aggregate root this component belongs to |
| `rootClassId` | `rootclassid` | `int`     | the **class id** of that aggregate root (its type)         |

Both belong exclusively to **components** — PDOs that live inside another PDO's
[aggregate](pdo.md#aggregates-and-the-root-context). A root entity never carries
them (it *is* the root), and an [embedded](../../../../tentackle-persistence-wurblets/src/main/docs/pdo-select.md#embedded-entities) entity
never carries them (it has no table of its own). Their single purpose is to let any
component name its aggregate root **directly**, by id and type, without walking back
up through every intermediate aggregate level and without a database query.

That one capability turns out to underpin four otherwise unrelated parts of the
framework — Domain-Driven Design boundaries, authorization, immutability, and query
performance. This document explains what the columns are, when the model adds them,
and how each of those four concerns leans on them.

The constants are defined in
[`Constants`](../../../../tentackle-common/src/main/java/org/tentackle/common/Constants.java)
(`CN_ROOTID`/`AN_ROOTID`, `CN_ROOTCLASSID`/`AN_ROOTCLASSID`) and surfaced on every
PDO through
[`PersistentObject`](../java/org/tentackle/pdo/PersistentObject.java)
(`getRootId()`, `isRootIdProvided()`, `getRootClassId()`, `isRootClassIdProvided()`).

---

## When the Model Adds Them

`rootid` and `rootclassid` are two of the entity-level option flags described in
[Model Definition → Entity-level option flags](../../../../tentackle-model/src/main/docs/model-definition.md#entity-level-option-flags),
but you rarely write them by hand. They are normally part of the project-wide
`modelDefaults`:

```
<tentackle.modelDefaults>remote, bind, size, autoselect, tracked, root, rootid, rootclassid</tentackle.modelDefaults>
```

Listing them as defaults does **not** add a column to every table. It enables the
model to add them *automatically, per entity, only where the topology demands it*.
The inference runs over the model as a whole
([`ModelImpl`](../../../../tentackle-model/src/main/java/org/tentackle/model/impl/ModelImpl.java))
and applies three rules:

1. **`root`** — If an entity is **not** used as a component of any aggregate, it is a
   *root entity*. (A root entity need not actually have components — it can stand
   alone. It corresponds to the DDD *aggregate root*.)

2. **`rootId`** — If an entity **is** a component of an aggregate but has **no
   attribute that already points at its root entity**, a `rootId` column is added and
   filled with the root entity's id. If a parent-id attribute that already reaches the
   root exists, that column is reused and no extra column is generated.

3. **`rootClassId`** — If a component type is used in **more than one** aggregate
   (a *multi-homed* component — the same table is shared by different root classes),
   a `rootClassId` column is added so that a component row also records *which kind*
   of root it belongs to, not just which id. The same applies to a component without a
   single root entity that is the target of a [deep reference](#relation-to-security).
   A component with exactly one root entity needs no column: its generated
   `getRootClassId()` simply returns the root's class id as a constant.

The model exposes what the raw model asked for through the
`...AccordingToModel` predicates
([`isProvidingRootIdAccordingToModel()`](../../../../tentackle-model/src/main/java/org/tentackle/model/Entity.java),
`isProvidingRootClassIdAccordingToModel()`, `isRootEntityAccordingToModel()`),
which the inference then resolves into the effective per-entity flags.

Two combinations are rejected at model-validation time
([`EntityImpl`](../../../../tentackle-model/src/main/java/org/tentackle/model/impl/EntityImpl.java)):

- an **embedded** entity may not provide `rootid`/`rootclassid` (it has no table); and
- a **root** entity may not provide them either (it is its own root).

The global options line cannot switch the inferred flags off: neither `-root` nor
`untracked` lowers what the model defaults and the topology imply (see
[model-definition.md](../../../../tentackle-model/src/main/docs/model-definition.md)).
An entity that is *programmatically* either a root entity or a component is modeled
like a normal component with `[root]` in its global options, the `MethodsImpl` wurblet
is given `--noisroot`, and the application overrides `isRootEntity()` to decide at
runtime.

### Setting and reading the values at runtime

For a new component, the values are stamped from the
[`DomainContext`](../java/org/tentackle/pdo/DomainContext.java) when the id is
assigned
([`AbstractPersistentObject.newId()`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/AbstractPersistentObject.java)):

```java
if (!isRootEntity()) {
  if (isRootIdProvided())      { setRootId(context.getRootId()); }
  if (isRootClassIdProvided()) { setRootClassId(context.getRootClassId()); }
}
```

On load, the same context coordinates (`getRootId()` / `getRootClassId()`) flow back
from the columns. Because every root entity maintains its own copy of the domain
context — the **root context** — the component and its root always agree on these two
numbers.

---

## Relation to DDD and Aggregates

Tentackle treats DDD [aggregates](pdo.md#aggregates-and-the-root-context) as
first-class citizens: every PDO is either a root entity (the *aggregate root*) or a
direct/indirect component reachable only through it. `rootId`/`rootClassId` are how
the persistence layer makes that boundary *physical*.

- **The aggregate root is always one hop away.** A grandchild component three levels
  deep does not need to traverse parent → child → grandchild to find its root; the
  `rootId` (and, if multi-homed, `rootClassId`) name the root outright. This is what
  lets a component "know" its root entity **without a database query**.

- **The boundary is enforced, not merely documented.** When the whole aggregate is
  persisted as one consistent unit, the root context spans the root and every
  component; the root coordinates carried by each component are what the framework
  checks the parts against. A component whose `rootId`/`rootClassId` do not match the
  context it is being manipulated in is recognized as foreign to that aggregate.

- **Multi-homed components stay unambiguous.** When the same component type can belong
  to several different root classes, `rootClassId` keeps the back-reference exact — an
  `Attachment` row knows it belongs to *Invoice #42*, not merely to *id 42* of some
  unspecified type.

---

## Relation to Security

Tentackle's [authorization subsystem](security.md) answers *"may this grantee perform
this permission on this object within this domain context?"* on every tier, and it
enforces **read** access at the aggregate granularity. `rootId`/`rootClassId` are what
make that enforcement possible for components.

The principle is: **a component is visible only if its aggregate root is visible.**
If you may not read the `Invoice`, you may not read its `InvoiceLine`s either — and the
framework behaves as if the rows did not exist rather than raising an error.

- For a **root entity**, every loaded instance is checked against the read permission
  (`isReadAllowed()`); a denied root entity is silently dropped. When loading within a
  root context — which is how the components of an aggregate are loaded — the context's
  root entity is checked once beforehand
  ([`assertRootContextIsAccepted()`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/AbstractPersistentObject.java)).

- For a **component loaded outside its own root context** (a deep link / deep
  reference that reaches lines without their owning root), the framework reads the
  component's `rootClassId` + `rootId` and asks the
  [`SecurityManager`](../java/org/tentackle/security/SecurityManager.java) to evaluate
  the **read permission of that root entity**. If the verdict is *denied*, the
  component is dropped from the result (`return null`) — never handed to the caller:

  ```java
  SecurityResult sr = SecurityFactory.getInstance().getSecurityManager()
      .evaluate(context, SecurityFactory.getInstance().getReadPermission(),
                poRootClassId, poRootId);
  if (!sr.isAccepted()) {
    return null;   // root not readable -> component does not exist for this user
  }
  ```

Because the check needs only two integers carried on the component row, it costs no
extra query, and a small `lastCheckedRoot` cache skips re-evaluating the same root
across a result list. Without `rootClassId`/`rootId`, evaluating a rule defined on the
*root* while selecting *components* would require re-loading the root for every row.

---

## Relation to Immutability

The same root coordinates let the framework decide, at load time, whether an object
may be modified — protecting the DDD invariant that **an aggregate is responsible for
its own consistency, and nothing may write to it behind its back**.

When a component is loaded through a path that is **not** its own root context — for
example, via a [deep reference](pdo.md) that pulls lines without their owning root, or
from within a *different* aggregate's context — the framework marks it
**finally immutable**
([`setFinallyImmutable()`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/AbstractPersistentObject.java)):

```java
if (poRootId != 0 && poRootId != context.getRootId() ||
    poRootClassId != 0 && poRootClassId != context.getRootClassId()) {
  po.setFinallyImmutable();   // wrong root context -> read-only
  ...
}
```

This prevents a subtle and otherwise silent class of bug: editing a component that you
happened to reach through the wrong aggregate and persisting it where it does not
belong. The object is still perfectly usable for **reading**; it simply refuses to be
changed or saved outside the aggregate that owns it. The dual root/component case
(an entity that is programmatically sometimes a root and sometimes a component) uses
the very same `rootId`/`rootClassId` comparison to become read-only when loaded as a
borrowed component. See the immutability mechanics in
[immutable.md](../../../../tentackle-core/src/main/docs/immutable.md).

---

## Relation to Performance

This is where the columns earn their keep on the hot path. They exist primarily to
**collapse aggregate-traversal joins into a single equality**, and to compare types as
integers instead of strings.

- **One comparison instead of a join chain.** A condition on a deeply nested component
  would naively require joining every intermediate aggregate level (root → child →
  grandchild → …) just to correlate the component row with its root. When a path from a
  root begins with a chain of composite component relations, the generator
  ([`pdo-select` → How the path to the root is reduced](../../../../tentackle-persistence-wurblets/src/main/docs/pdo-select.md#how-the-path-to-the-root-is-reduced--rootid--rootclassid))
  **compacts that whole leading chain** into one predicate in the correlated `EXISTS`
  subquery:

  ```sql
  root.<id> = component.<rootId>        -- plus  root.<classId> = component.<rootClassId>  for multi-homed components
  ```

  Only relations *after* the compacted component prefix (e.g., a link out to a foreign
  PDO) still need their full join conditions.
  [`ComponentInfo`](../../../../tentackle-wurblets/src/main/java/org/tentackle/wurblet/ComponentInfo.java)
  resolves which column actually supplies the root id: the component's own
  `CN_ROOTID` when it has the `rootid` option, otherwise an existing parent-id
  attribute that already reaches the root (so no redundant column is added).

- **Loading a whole aggregate is one indexed lookup.** Selecting all components of a
  given root is a single `WHERE rootid = ?` (`AND rootclassid = ?` when multi-homed)
  against an indexed column — no multi-level join, no per-row follow-up.

- **Class ids are ints, not class names.** `rootClassId` stores the root's
  [class id](pdo.md) rather than its fully-qualified class name. Comparing integers is
  faster than comparing strings in both Java and the database, and — just as
  importantly — it survives refactoring: renaming or repackaging a class does not
  change its class id, so stored rows stay valid.

- **No query to find the root at runtime.** Because the value travels on the row, the
  security and immutability checks above, and any application code that needs the
  root's identity, read it from memory instead of issuing a lookup.

The trade-off is one (or two) extra small columns on component tables and the
discipline of stamping them on insert — paid once, on writes, to save joins and
queries on every read.

---

## Summary

| Concern          | What `rootId` / `rootClassId` provide                                                                       |
|------------------|-------------------------------------------------------------------------------------------------------------|
| **DDD**          | A component names its aggregate root directly; the aggregate boundary becomes physical and enforceable.     |
| **Security**     | Read access is checked against the *root*; an unreadable root hides all its components, at no extra query.  |
| **Immutability** | A component loaded outside its own root context is made read-only, preventing cross-aggregate writes.       |
| **Performance**  | Aggregate-traversal join chains collapse to one indexed equality; type comparison is an int, refactor-safe. |

The columns appear only where the model topology requires them — components without an
existing path to their root get `rootId`; components shared across root classes also
get `rootClassId` — and are absent from root and embedded entities entirely.

### See also

- [pdo.md → Aggregates and the root context](pdo.md#aggregates-and-the-root-context)
- [security.md](security.md)
- [pdo-select.md → How the path to the root is reduced](../../../../tentackle-persistence-wurblets/src/main/docs/pdo-select.md#how-the-path-to-the-root-is-reduced--rootid--rootclassid)
- [model-definition.md → Entity-level option flags](../../../../tentackle-model/src/main/docs/model-definition.md#entity-level-option-flags)
- [immutable.md](../../../../tentackle-core/src/main/docs/immutable.md)
