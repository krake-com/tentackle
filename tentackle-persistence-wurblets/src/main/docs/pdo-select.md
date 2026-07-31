# `PdoSelectList` and `PdoSelectUnique` — Generating Query Methods

## Overview

`PdoSelectList` and `PdoSelectUnique` are the two
[persistence wurblets](persistence-wurblets.md) that turn a compact query expression into a complete,
backend-aware `SELECT` method on a [PDO](../../../../tentackle-pdo/src/main/docs/pdo.md)'s persistence
implementation class. Both read the same
[wurblet-argument query mini-language](../../../../tentackle-wurblets/src/main/docs/wurblets.md#wurblet-arguments--the-query-mini-language)
and differ only in their result cardinality:

| Wurblet                                                                             | Returns                                               | Generated body terminates in                           |
|-------------------------------------------------------------------------------------|-------------------------------------------------------|--------------------------------------------------------|
| [`PdoSelectUnique`](../wurblets/org/tentackle/persist/wurblet/PdoSelectUnique.wrbl) | a **single** PDO (or `null`)                          | `executeFirstPdoQuery(st)`                             |
| [`PdoSelectList`](../wurblets/org/tentackle/persist/wurblet/PdoSelectList.wrbl)     | a **list** of PDOs (or a `ResultSetWrapper` / cursor) | `executeListQuery(st)` / `executeTrackedListQuery(st)` |

Each is placed behind a `// @wurblet <methodName> PdoSelect… <options> <expression>` anchor inside a guarded
region of the entity's persistence implementation. On every build the wurblet regenerates the method —
its signature, the cached `SELECT` string, the JDBC parameter binding, the optional eager-loading joins and,
when the entity is remote-enabled, the matching [TRIP](../../../../tentackle-core/src/main/docs/trip.md)
remote-delegate code.

> The two have low-level siblings, [`DbSelectUnique`](../wurblets/org/tentackle/persist/wurblet/DbSelectUnique.wrbl)
> and [`DbSelectList`](../wurblets/org/tentackle/persist/wurblet/DbSelectList.wrbl), which generate the same
> queries against a plain persistent object (`Db…`) rather than a full PDO. The `Pdo…` variants additionally
> emit `@Override` against the PDO interface and add the PDO-specific options described below (`--noif`,
> `--immutable`, `--tokenlock`, `--cursor`, …).

---

## The method anchor

A query method is declared by a wurblet anchor comment; the wurbelizer fills the fold block beneath it:

```java
// @wurblet selectByGrantee PdoSelectList --sort granteeClassId granteeId
```

- `selectByGrantee` — the **method name** to generate.
- `PdoSelectList` — the wurblet.
- `--sort` — an **option** (everything starting with `--`).
- `granteeClassId granteeId` — the **expression**: two attribute conditions, `AND`-combined by default,
  each becoming a `= ?` predicate and a method parameter.

This generates:

```java
@Override
public List<Security> selectByGrantee(int granteeClassId, long granteeId) {
  if (getSession().isRemote()) {
    List<Security> list = getRemoteDelegate().selectByGrantee(getDomainContext(), granteeClassId, granteeId);
    configureRemoteObjects(getDomainContext(), list);
    return list;
  }
  PreparedStatementWrapper st = getPreparedStatement(SELECT_BY_GRANTEE_STMT,
    b -> {
      StringBuilder sql = createSelectAllInnerSql(b);
      sql.append(Backend.SQL_AND);
      sql.append(getColumnName(CN_GRANTEECLASSID));
      sql.append(Backend.SQL_EQUAL_PAR);
      sql.append(Backend.SQL_AND);
      sql.append(getColumnName(CN_GRANTEEID));
      sql.append(Backend.SQL_EQUAL_PAR);
      b.buildSelectSql(sql, false, 0, 0);
      String orderSuffix = orderBy();
      if (orderSuffix != null && !orderSuffix.isEmpty()) {
        sql.append(Backend.SQL_ORDERBY).append(orderSuffix);
      }
      return sql.toString();
    }
  );
  int ndx = 1;
  st.setInt(ndx++, granteeClassId);
  st.setLong(ndx, granteeId);
  return executeListQuery(st);
}

private static final StatementId SELECT_BY_GRANTEE_STMT = new StatementId();
```

Note the recurring structure shared by both wurblets:

1. **Remote short-circuit** — if the session is remote, delegate to the server and re-attach the returned
   objects to the local domain context (`configureRemoteObject(s)`).
2. **Cached statement** — the `SELECT` string is built once inside a lambda keyed by a `StatementId`; the
   runtime reuses the `PreparedStatement` thereafter. The column names come from the generated `CN_*`
   constants, and the surrounding SQL fragments (`SQL_AND`, `SQL_EQUAL_PAR`, `SQL_ORDERBY`, …) come from the
   active [`Backend`](../../../../tentackle-sql/src/main/docs/sql.md), so the same template produces correct
   SQL for every supported dialect.
3. **Ordered parameter binding** — parameters are bound in expression order with the correct
   [`DataType`](../../../../tentackle-sql/src/main/docs/sql.md#datatype) setter.
4. **Execution** — `executeListQuery` / `executeFirstPdoQuery` (or their tracked / joined overloads).

---

## The query expression

Both wurblets parse their non-option arguments with the
[query mini-language](../../../../tentackle-wurblets/src/main/docs/wurblets.md#wurblet-arguments--the-query-mini-language)
([`WurbletArgumentParser`](../../../../tentackle-wurblets/src/main/java/org/tentackle/wurblet/WurbletArgumentParser.java)).
An expression is a tree of operands joined by `AND` (the default), `OR` and `NOT`, with parentheses for
grouping, followed by optional sorting and eager-load terms:

```
@wurblet selectUpTo PdoSelectList --remote   processed:=:null or processed:>= +id *address
```

- **property condition** — `attribute[:operator[:value]]`. Bare `attribute` means `= ?` and adds a method
  parameter; `processed:>=` uses `>=` with a parameter; `processed:=:null` inlines `IS NULL` and adds no
  parameter. Attributes may be reached through a relation path and may address a single column of a
  multi-column type.
- **property sorting** — `+attr` ascending, `-attr` descending. Appended as `ORDER BY` and (for
  `PdoSelectUnique`) implies a one-row limit (see below).
- **load join** — `*relation` eagerly loads the named relation via an SQL join instead of a follow-up query;
  join paths can themselves be filtered.

When an attribute condition resolves to a PDO supplied through a relation path, the remote signature is
generated against that PDO (`…(<Pdo> obj)`) rather than the `DomainContext`; otherwise the first remote
parameter is the `DomainContext`.

### Argument grammar

The full form of a single argument (parsed by
[`WurbletArgument`](../../../../tentackle-wurblets/src/main/java/org/tentackle/wurblet/WurbletArgument.java)) is:

```
[ * | + | - ] [ relation | .Entity [ .relation … ] ] [ attribute[#column] [name] [?boolean] ] [ :relop [ :value | #value ] ]
```

- **leading `*` / `+` / `-`** — load join / ascending sort / descending sort. Absent ⇒ a `WHERE` condition.
- **relation path** — optional for conditions, required for load joins (see below).
- **`#column`** — for a multi-column [`DataType`](../../../../tentackle-sql/src/main/docs/sql.md#datatype),
  address one column by its suffix/getter name; `#*` selects all columns. Omitted ⇒ the type's default column
  (or all columns for `=`).
- **`[name]`** — rename the generated method parameter (e.g. `poolId[from]:>=` ⇒ parameter `from`).
- **`?boolean`** — make the condition optional (see [below](#optional-conditions-qbf-style-finders)). Must precede
  the relop.
- **`:relop[:value]`** — the operator (`=` default; `:null`/`:notnull`, `:like`/`:notlike`, `:in`/`:notin`,
  `…any`/`…all` array operators) and an optional inline `?`-bound `:value` or literal `#value`.

Both `PdoSelectList` and `PdoSelectUnique` set `@{config pathAllowed}@`, so relation paths are permitted in their
expressions.

### Optional conditions — QBF-style finders

A generated finder normally builds one fixed SQL statement, which is exactly what a cached prepared statement
needs. A query-by-form dialog, however, restricts by whatever the user actually filled in, so the statement has
to be assembled per invocation. Marking a condition with `?<boolean>` does that:

```java
// @wurblet findMatching PdoSelectList --private --sort name?withName code?withCode
```

```java
private List<Article> findMatching(String name, boolean withName, String code, boolean withCode) { … }
```

Each marked condition adds a `boolean` method parameter. It is applied — both its SQL text and its statement
parameters — only if that boolean is true. Several conditions may share one boolean; it then appears only once
in the signature. A condition with a fixed value (`processed?withProcessed:=:null`) gets its boolean but no value
parameter.

Because the SQL text is no longer fixed, such a finder always creates a one-shot prepared statement, i.e.
`--oneshot` is implied (and `--resultset`/`--cursor` are therefore rejected). Optional conditions are supported by
the four select wurblets only — the update, delete and reference-check wurblets cache their statement and refuse
them. They are also not allowed in a load-join filter or on a sorting argument.

Such finders are usually **not** part of the PDO interface (hence `--private` or `--noif`). The public method
deriving the booleans from the user's input is written by hand, because that logic is domain logic:

```java
@Override
public List<Article> findByForm(ArticleForm form) {
  return findMatching(form.getName(), form.getName() != null,
                      form.getCode(), form.getCode() != null);
}
```

**How the SQL stays valid.** The generator guards each condition with an `if`, but emits the operators and
parentheses unconditionally — taking the booleans into account when building nested expressions would be far more
complex than cleaning up afterwards. Skipping conditions can therefore leave empty groups and dangling operators
behind, e.g. `code=? AND NOT ()`. The statement is passed through
[`Backend.reduceSql`](../../../../tentackle-sql/src/main/java/org/tentackle/sql/Backend.java) before any trailing
clause is appended, which reduces that to `code=?`. Backends may override the reduction if necessary.

Conditions reached through a relation path are wrapped in one `EXISTS` clause. The clause is generated if at least
one of its conditions applies:

```java
if (withKey || withValue) {
  sql.append(Backend.SQL_EXISTS)
     …
}
```

---

## Selecting across relations, components, and embedded entities

A condition does not have to name an attribute of the entity the wurblet sits on — it can reach **through a
relation path** into a related entity, an aggregate **component**, or an **embedded** value type. How that path is
turned into SQL depends on what kind of relation each step is.

### Relation-path conditions

Write the path with dots, ending in the attribute:

```
invoice.lines.currencyId        # condition on InvoiceLine.currencyId, reached via invoice → lines
```

A path that crosses into a **separate table** (a link to another PDO, or a deeper aggregate level) is emitted as a
correlated `EXISTS (...)` subquery, *not* an inner join — so the result still consists of rows of the wurblet's
own entity, never duplicated or widened. The subquery lists the joined tables in its `FROM`, then `AND`-joins them
in its `WHERE` using the model's foreign-key / relation columns, and finally appends your condition.

This is the difference between a dotted path (a *filter*, generating an `EXISTS`) and a `*`-load join (which
*populates* the related objects — see [Load joins](#load-joins-eager-loading)).

### Component attributes and the leading-dot form

For an aggregate root entity you can address one of its components directly with a **leading dot** followed by the
component's *entity* name:

```
.InvoiceLine.currencyId         # equivalent to invoice.lines.currencyId, when the path is unambiguous
```

The leading-dot form is only legal on a root entity and only when the component has exactly one composite path
back to that root; the parser then substitutes that path. It is a convenience for the common case of "filter the
root by something on one of its components" without spelling out every relation hop.

### How the path to the root is reduced — `rootId` / `rootClassId`

A naïve component path would have to join every intermediate aggregate level (root → child → grandchild …) just to
correlate a component row with its root. Tentackle avoids that. Every aggregate **component** has a
`getRootId()` method returning the **id of its aggregate root**, and a `getRootClassId()` method returning
the root's class id. If necessary, the model appends the corresponding `rootId` and/or `rootClassId` columns
to the table (see [Model Definition → `rootid` / `rootclassid`](../../../../tentackle-model/src/main/docs/model-definition.md#entity-level-option-flags)).

When a path from a root entity begins with a chain of composite component relations,
[`WurbletArgument.getExpressionRelations()`](../../../../tentackle-wurblets/src/main/java/org/tentackle/wurblet/WurbletArgument.java)
**compacts that leading chain**: instead of generating one join predicate per hop, the generated `EXISTS` subquery
correlates the component straight to the root with a single equality

```sql
root.<id>  =  component.<rootId>      -- plus  root.<classId> = component.<rootClassId>  for multi-homed components
```

collapsing the entire intermediate aggregate path into one comparison. Only relations *after* the compacted
component prefix (e.g., a link out to a foreign PDO) still get their full join conditions in the subquery.

The mechanics live in
[`ComponentInfo`](../../../../tentackle-wurblets/src/main/java/org/tentackle/wurblet/ComponentInfo.java), which
resolves which column actually provides the root id for each component:

- if the component (or a superclass) was given the `rootid` option, its own `CN_ROOTID` column is used;
- otherwise the model's existing parent-id attribute that points at the root is used (no extra column);
- for **multi-table inheritance** where the addressed attribute lives in a child table, `isExtraJoinNecessary()`
  triggers an additional `id = id` join from the root-id-bearing table to that child table.

### Embedded entities

An **embedded** entity (a composite 1:1 value type) has no table of its own — its columns are inlined into the
owner's table behind a **column prefix**. A condition on an embedded attribute therefore needs **no join at all**:
the generator resolves the attribute to its prefixed column in the *same* table. Depending on context this is
emitted either as `getColumnName("<prefix><column>")` or as `getColumnPrefix() + CN_<ATTR>`. Chained embedding
(an embedded type inside another embedded type) simply concatenates the prefixes
([`getEmbeddingColumnPrefix()`](../../../../tentackle-wurblets/src/main/java/org/tentackle/wurblet/WurbletArgument.java)).

So a dotted path whose steps are *embedding* relations stays a plain single-table predicate, whereas a path whose
steps cross into other PDO tables becomes the `EXISTS` subquery described above — the wurblet picks the right
strategy per step from the model.

### Load joins (eager loading)

Prefix a relation with `*` to **eagerly load** it in the same `SELECT` rather than firing a follow-up query per
result (avoiding the N+1 problem). Load joins populate the returned objects; they are not `WHERE` conditions.

```
*lines                          # eagerly load the 'lines' relation
*invoice*lines                  # chained join: load invoice and, beneath it, its lines
```

The generated method builds a
[`JoinedSelect`](../../../../tentackle-pdo/src/main/docs/pdo.md) with one `Join` (a SQL `LEFT JOIN`) per path
element; each match is wired into the parent via the relation's setter/adder. Identical leading path segments
across several `*` arguments are consolidated into a single join tree and the joins are auto-named `j_1`, `j_1_1`,
… by [`JoinPathFactory`](../../../../tentackle-wurblets/src/main/java/org/tentackle/wurblet/JoinPathFactory.java).
Joins can reach into aggregate components as well as out to linked PDOs.

A join element may be **filtered** by appending `|` and a filter expression (itself wurblet arguments; quote it if
it contains more than one):

```
*invoice*lines|no:<:10          # join invoice and load only the lines with no < 10
*invoice|date:>= *lines         # filter the invoice join on date >= ? and join lines beneath it
```

> **Filtered joins force immutability.** Because a filtered eager-load returns only a *subset* of an aggregate's
> components, the returned PDO/list is automatically made [immutable](../../../../tentackle-pdo/src/main/docs/pdo.md)
> (it must not be saved back). Pass `--mutable` to override this when you know the partial load is acceptable.

The earlier `findByName … *Keys` example is exactly this: `*Keys` turns the follow-up query for the bundle's keys
into a single joined statement, and the generated body therefore returns `executeTrackedListQuery(st, js)` with the
`JoinedSelect js` carrying the `LEFT JOIN`. Note the cursor constraint from the [options](#pdoselectlist-only):
`--cursor` combined with joins requires the query to be sorted by `id`.

---

## Options

### Common to both

| Option                        | Effect                                                                                                                                                                                                 |
|-------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `--private`                   | Makes the method `private` (default `public`). Implies `--noif` and disables remoting.                                                                                                                 |
| `--noif`                      | There is no interface for the method ⇒ no `@Override` is generated.                                                                                                                                    |
| `--sort`                      | Append `ORDER BY` using the model's default sorting (`orderBy()`). Mutually exclusive with explicit `+/-` sort terms in the expression, and requires a default sorting to be configured on the entity. |
| `--lock`                      | Generate `SELECT … FOR UPDATE`; asserts a running transaction.                                                                                                                                         |
| `--append=<sqltext>`          | Append a literal SQL fragment to the generated statement.                                                                                                                                              |
| `--classvar=<classvariables>` | Take the cached `StatementId` from the referenced class-variables holder instead of emitting a local `private static final StatementId`.                                                               |
| `--oneshot`                   | Create the prepared statement for a single use (`createPreparedStatement` within a try-with-resources) instead of caching it under a `StatementId`. No `StatementId` is generated at all. Implied by [optional conditions](#optional-conditions-qbf-style-finders). Incompatible with `--resultset` and `--cursor`, which hand out a result still bound to the statement. |
| `--immutable`                 | Mark the returned PDO / list [immutable](../../../../tentackle-pdo/src/main/docs/pdo.md).                                                                                                              |
| `--finallyImmutable`          | Mark the result *finally* immutable (can never become mutable again).                                                                                                                                  |
| `--mutable`                   | Force a mutable result, overriding the automatic immutability that filtered joins otherwise impose.                                                                                                    |

> **Automatic immutability:** if the query uses filtered joins, the result is forced immutable unless
> `--mutable` is given, because a filtered eager-load returns only a subset of an aggregate's components and
> must not be saved back.

### `PdoSelectList` only

| Option        | Effect                                                                                                                                                                                                                              |
|---------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `--limit`     | Add a parameter limiting the number of returned rows (dialect-aware paging).                                                                                                                                                        |
| `--offset`    | Add a parameter skipping the first rows.                                                                                                                                                                                            |
| `--resultset` | Return the raw `ResultSetWrapper` instead of a `List` (disables remoting; incompatible with immutability and `--oneshot`).                                                                                                                          |
| `--cursor`    | Return a `ScrollableResource<Pdo>` (a `ResultSetCursor`, `RemoteResultSetCursor` when remote) for streaming large results — see [cursors.md](../../../../tentackle-persistence/src/main/docs/cursors.md). Incompatible with `--resultset`, `--oneshot`, immutability and tracking; with joins the query must be sorted by `id`. |
| `--bounded`   | Return `List<? extends MainClass>` instead of `List<MainClass>`.                                                                                                                                                                    |

### `PdoSelectUnique` only

| Option        | Effect                                                                  |
|---------------|-------------------------------------------------------------------------|
| `--tokenlock` | Request a token lock on the returned object (`obj.requestTokenLock()`). |

`PdoSelectUnique` requires at least one of: an expression, a sorting, an `--append`, or a context attribute —
otherwise it fails with *"no keys given"*. When the result could match more than one row (sorting,
`--append`, or a configured `ORDER BY`), the generated SQL is limited to a single row so that
`executeFirstPdoQuery` reliably returns the first match.

---

## Result handling

| Situation                                                      | `PdoSelectList`                                                           | `PdoSelectUnique`                                                     |
|----------------------------------------------------------------|---------------------------------------------------------------------------|-----------------------------------------------------------------------|
| plain                                                          | `executeListQuery(st)`                                                    | `executeFirstPdoQuery(st)`                                            |
| with eager joins                                               | `executeListQuery(st, js)`                                                | `executeFirstPdoQuery(st, js)`                                        |
| [tracked](persistence-wurblets.md) (`--tracked` on the entity) | `executeTrackedListQuery(st[, js])`, return type `TrackedList`            | n/a                                                                   |
| `--cursor`                                                     | `new ResultSetCursor<>(me(), rs[, js])`                                   | n/a                                                                   |
| `--resultset`                                                  | `return st.executeQuery()`                                                | n/a                                                                   |
| `--immutable` / `--finallyImmutable`                           | `list.setImmutable(true)` / `list.setFinallyImmutable()` before returning | `obj.setImmutable(true)` / `obj.setFinallyImmutable()` (null-guarded) |
| `--tokenlock`                                                  | n/a                                                                       | `obj.requestTokenLock()` (null-guarded)                               |

---

## Examples

A unique select by the entity's unique domain key:

```java
// @wurblet selectByUniqueDomainKey PdoSelectUnique name
```
```java
public NumberPool selectByUniqueDomainKey(String name) { … executeFirstPdoQuery(st); }
```

A tracked list select used by an aggregate component relation:

```java
// @wurblet selectByObject PdoSelectList --sort --tracked objectClassId objectId
```
```java
public TrackedList<Security> selectByObject(int objectClassId, long objectId) { … executeTrackedListQuery(st); }
```

A list select with an eager-loading join (`*Keys` loads the `keys` relation in the same statement):

```java
// @wurblet findByName PdoSelectList name *Keys
```
```java
public TrackedList<StoredBundle> findByName(String name) {
  JoinedSelect<StoredBundle> js = new JoinedSelect<StoredBundle>().addJoin(/* LEFT JOIN on bundle_id */);
  …
  return executeTrackedListQuery(st, js);
}
```

A streaming list of unprocessed rows (low-level `Db…` flavour, returning a result set):

```java
// @wurblet resultSetUnprocessed DbSelectList --model=$mapfile --resultset processed:=:null +id
```

More elaborate wurblet anchors:
```java
// @wurblet findByCalloff PdoSelectList Loadings.calloffId or Transports.calloffId | *Loadings *Transports
public TrackedList<Ingot> findByCalloff(long calloffId) {
  ...
}

// @wurblet findByCalloffNumber PdoSelectList Lines.calloffNumber | *Lines +Lines.position
public TrackedList<Shipment> findByCalloffNumber(String calloffNumber) {
  ...
}

// @wurblet findTopByTx PdoSelectList --private siteId upstream beginning[from]:>= beginning[upto]:< | +statisticsByTx.detail.rank *statisticsByTx|detail.rank:<
private TrackedList<StatisticsSlot> findTopByTx(int rank, long siteId, boolean upstream, Timestamp from, Timestamp upto) {
  ...
}
```

---

## Relation to `PdoRelations`

When the model declares a relation, [`PdoRelations`](../wurblets/org/tentackle/persist/wurblet/PdoRelations.wrbl)
emits the corresponding `PdoSelectUnique` / `PdoSelectList` anchors automatically (for the to-one and to-many
ends respectively), wiring the `--tracked` option and the relation's join expression for you. The standalone
anchors documented here are for the additional, hand-placed finder methods a developer adds beyond the
relation-generated ones.

---

## Related Documentation

- [Tentackle Persistence Wurblets](persistence-wurblets.md) — the module overview, the shared `DbModelWurblet`
  base, and the full wurblet catalogue.
- [Tentackle Wurblets — the query mini-language](../../../../tentackle-wurblets/src/main/docs/wurblets.md#wurblet-arguments--the-query-mini-language)
  — the grammar these wurblets parse.
- [PDO — Persistent Domain Objects](../../../../tentackle-pdo/src/main/docs/pdo.md) — immutability, token
  locks, tracked lists, and the objects these queries return.
- [Tentackle SQL](../../../../tentackle-sql/src/main/docs/sql.md) — `DataType` / `Backend` used to build the
  SQL and bind parameters.
- [Model Definition](../../../../tentackle-model/src/main/docs/model-definition.md) — the model the wurblets
  consume.
- [TRIP](../../../../tentackle-core/src/main/docs/trip.md) — the remoting protocol behind the generated remote
  delegates.
