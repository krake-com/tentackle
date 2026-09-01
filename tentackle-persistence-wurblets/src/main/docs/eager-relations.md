# Eager Relations

Tentackle offers two distinct ways to pull a related object into the same `SELECT` as its parent instead of
firing a follow-up query (the classic N+1 problem):

1. **Model-level eager loading** — declaring `select = eager` on a relation in the
   [model](../../../../tentackle-model/src/main/docs/model-definition.md). This is a *persistence* property of the
   relation: the related object is fetched whenever the parent is loaded by a **generic** select.
2. **Domain-level load joins** — the `*relation` term of the
   [`PdoSelectList` / `PdoSelectUnique`](pdo-select.md) wurblets. This is a *domain* decision made per finder
   method, independent of the model.

The two look similar but solve different problems and have very different scope. The most important thing to
understand is that **model-level eager loading cascades only as far as the model declares**, and by default not
at all, whereas **load joins are explicit and may chain to any depth you ask for**.

---

## 1. Model-level eager loading (`select = eager`)

In a relation line you can choose the fetch strategy:

```
## relations
Address:
    relation = object,
    select   = eager
```

The available strategies are `lazy` (the default — fetched on first access), `eager`, `always` and `embedded`
(see [Model Definition → `select`](../../../../tentackle-model/src/main/docs/model-definition.md#select)).
With `eager`, the related object is loaded **together with its parent via a `LEFT OUTER JOIN`** rather than on
first access.

The strategy may carry a **cascade depth**, written `eager:<n>`:

```
## relations
Address:
    relation = object,
    select   = eager:2
```

`n` is the number of relation hops the generated join tree may span, counting this relation as the first one.
It defaults to `1` — the single-level join described below — so a plain `eager` behaves exactly as it always
did.

With `Invoice -eager:2-> Address` and `Address -eager-> Country`, every generic read of an invoice becomes a
single statement:

```sql
SELECT inv.*, e_1.*, e_1_1.*
  FROM invoice inv
  LEFT JOIN (SELECT adr.* FROM address adr) e_1   ON inv.address_id = e_1.id
  LEFT JOIN (SELECT cty.* FROM country cty) e_1_1 ON e_1.country_id = e_1_1.id
```

At depth `1` the country would instead be fetched by a follow-up select on first access. Note that the
`Address -eager-> Country` relation needs no depth of its own: the budget comes from the relation that starts
the chain.

### What "eager" actually applies to

Eager loading is wired into the **generic selects** — the methods the persistence layer generates for *every*
entity: `select(id)`, `selectAll()`, and the relation finders that other entities generate to load *this*
entity (`selectByXxx`). Concretely:

- The eager joins are baked into the entity's `CLASSVARIABLES` as a list of
  [`Join`](../../../../tentackle-pdo/src/main/docs/pdo.md)s (one `LEFT JOIN` per eager relation, each carrying
  its cascaded sub-joins), assembled by
  [`DbModelWurblet.getEagerJoinTree()`](../java/org/tentackle/persist/wurblet/DbModelWurblet.java)
  and emitted by [`ClassVariables`](../wurblets/org/tentackle/persist/wurblet/ClassVariables.wrbl). The runtime
  applies these joins to the standard by-id / select-all statements.
- When another entity's relation generates a finder for this entity,
  [`PdoRelations`](../wurblets/org/tentackle/persist/wurblet/PdoRelations.wrbl) appends the same join tree as
  `*relation` load joins to that generated `PdoSelectList` / `PdoSelectUnique`, so the eager objects ride along
  in the relation select as well.

In other words, "eager" is a property of how the entity is *generically* read. It does not magically change
handwritten queries.

### The cascade is capped — and why

The depth is the **only** thing that ends the cascade. A relation is followed when it is declared `eager` and
the budget of the relation that started the chain still allows another hop; when the budget runs out, the
remaining levels are loaded by ordinary follow-up selects. A cycle
(`A -eager-> B -eager-> A`) is therefore harmless: it simply produces a bounded chain of joins.

The depth of the **root** relation governs its whole subtree. A nested relation must still be declared `eager`
to be pulled in, but its own `:n` is not consulted while it is nested — that value applies when *its* entity is
the one being selected. So the emitted join tree is a function of a single number, which keeps it predictable.

There is one case in which the optimization is abandoned altogether (nothing is joined and every relation falls
back to a follow-up select): when a **subclass** of the entity declares an eager relation. The joins of a
polymorphic select cannot depend on the class of the row.

**Why the default is 1.** A transitively-cascading eager strategy is the classic way to destroy a relational
application: each additional eager level over a *list* relation multiplies into the join, every parent row is
fanned out across the cross-product of all its descendants, the row count explodes, and a query that was meant
to fetch "one object and its address" ends up dragging half the database through a single statement. Long
`LEFT JOIN` chains are slow to plan, slow to execute, and slow to de-duplicate on the client. Because a
model-level eager relation is paid for by **every** generic read of the entity, the cascade is off unless you
ask for it.

Raise the depth when the whole chain is small, bounded and virtually always needed together — typically a
series of 1:1 / N:1 hops, which add no rows at all. If instead only certain queries need the deeper graph, or
you need to filter it, that is a signal that you want a **load join in a specific finder** (next section), not
a model-wide depth.

### When to use it

Reach for `select = eager` only when a related object is *almost always* needed together with its parent and
the relation is shallow and bounded (a 1:1 link to a small satellite row, a short component list). It is a
blunt, global instrument: every generic read of the entity pays for it, and a depth greater than 1 multiplies
that cost. For everything else, prefer `lazy` (the default) and add targeted load joins where a particular
access path needs them.

---

## 2. Domain-level load joins (`*relation`)

The second mechanism lives entirely in the **domain layer**. When you write a finder with
[`PdoSelectList` / `PdoSelectUnique`](pdo-select.md), prefixing a relation with `*` eagerly loads it in that
**one** query:

```java
// @wurblet findByName PdoSelectList name *Keys
```

```java
// chained / filtered joins are allowed:
// @wurblet load PdoSelectList id *invoice*lines
// @wurblet load PdoSelectList id *invoice*lines|no:<:10
```

This is **domain-related, not model-related.** Key differences from model eager:

| Aspect         | Model `select = eager`                  | Load join `*relation`                                        |
|----------------|-----------------------------------------|--------------------------------------------------------------|
| Where declared | In the model (relation line)            | In a finder anchor in the persistence implementation         |
| Scope          | Global — affects **every** generic read | Local — affects **only that one finder**                     |
| Cascading      | Capped by `eager:<n>`, **1** by default  | **Explicit** — you chain `*a*b*c` to whatever depth you need |
| Filtering      | No                                      | Yes — `*lines                                                |no:<:10` loads a subset                    |
| Granularity    | All-or-nothing for the entity           | Tuned per access path                                        |

Because a load join is scoped to a single finder, it lets a particular domain operation say "for *this* use
case I need the invoice, its lines, and their currencies in one round-trip" without imposing that cost on every
other reader of the entity. Filtered joins additionally let you load only part of an aggregate (which forces
the result [immutable](../../../../tentackle-pdo/src/main/docs/pdo.md) unless you pass `--mutable`, since a
partial aggregate must not be saved back).

See [`PdoSelectList` and `PdoSelectUnique` → Load joins](pdo-select.md#load-joins-eager-loading) for the full
grammar, join consolidation, immutability rules, and the `--cursor` constraint.

---

## Choosing between the two

- The relation is small, shallow, and needed on virtually every read of the parent ⇒ **`select = eager`** in
  the model — plus a depth if the entity behind it is just as inseparable.
- Only certain queries need the related data, or you need to follow a deeper / filtered path, or you want to
  avoid burdening other readers ⇒ **leave the relation `lazy` and add a `*` load join** in the specific finder.

When in doubt, default to `lazy` and add load joins where profiling shows an N+1 problem. It is far easier to
add an eager load join to one finder than to discover, months later, that a model-wide `eager` flag is quietly
inflating every select of a central entity.

---

## Related Documentation

- [`PdoSelectList` and `PdoSelectUnique`](pdo-select.md) — the wurblets that implement load joins, including the
  full `*relation` grammar and filtered joins.
- [Tentackle Persistence Wurblets](persistence-wurblets.md) — the module overview and wurblet catalogue.
- [Model Definition → `select`](../../../../tentackle-model/src/main/docs/model-definition.md#select) — the
  relation `select` strategies (`lazy`, `eager[:depth]`, `always`, `embedded`).
- [PDO — Persistent Domain Objects](../../../../tentackle-pdo/src/main/docs/pdo.md) — aggregates, components,
  immutability, and the `JoinedSelect` machinery these loads build on.
