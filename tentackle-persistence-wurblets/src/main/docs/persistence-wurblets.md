# Tentackle Persistence Wurblets — Implementation-Level Code Generation

## Overview and Motivation

`tentackle-persistence-wurblets` is the build-time **code generation** module that produces the *implementation
side* of a Tentackle application's persistence: the executable SQL, the `PreparedStatement` plumbing, the caches,
the class variables, and the remote-delegate glue that make [PDOs](../../../../tentackle-pdo/src/main/docs/pdo.md)
actually load, store, update, and delete rows. It is the counterpart to
[`tentackle-wurblets`](../../../../tentackle-wurblets/src/main/docs/wurblets.md), which generates the *interface
side* (constants, accessor declarations, DTOs, and model documentation). Both read the same application
[model](../../../../tentackle-model/src/main/docs/model-definition.md) and the same
[`DataType`/`Backend`](../../../../tentackle-sql/src/main/docs/sql.md) definitions, so the interfaces, the
implementations, and the DDL produced by the
[SQL Maven plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) all agree on
the schema.

Like its sibling, this module is built on the [Wurbelizer](https://wurbelizer.org/how-it-works/) engine: each
generator is a `.wrbl` template that the `wurbelizer-maven-plugin` compiles into a Java class and runs at build
time, weaving the generated text into **guarded regions** of the application's source files (the
`//GEN-BEGIN:<tag>` … `//GEN-END:<tag>` fold blocks that follow a `// @wurblet <tag> <Name>` anchor). The
developer keeps full control of the surrounding class; the wurblet owns only the fenced block and regenerates it on
every build.

### Interface side vs. implementation side

|                        | [`tentackle-wurblets`](../../../../tentackle-wurblets/src/main/docs/wurblets.md)                           | `tentackle-persistence-wurblets` (this module)                                                          |
|------------------------|------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| **Generates into**     | PDO / operation *interfaces*, DTOs                                                                         | PDO / operation *implementation* classes (and some interface anchors)                                   |
| **Typical output**     | `CN_*`/`AN_*` constants, getter/setter declarations, relation method declarations, unique-domain-key types | field declarations, `SELECT`/`INSERT`/`UPDATE`/`DELETE` code, class variables, caches, remote delegates |
| **Base wurblet class** | [`ModelWurblet`](../../../../tentackle-wurblets/src/main/docs/wurblets.md#modelwurblet--the-shared-base)   | `DbModelWurblet` (extends `ModelWurblet`)                                                               |

Because this module depends on `tentackle-wurblets`, every helper available to the interface wurblets is also
available here, extended with persistence-specific machinery — most importantly the translation of the
[wurblet-argument query language](../../../../tentackle-wurblets/src/main/docs/wurblets.md#wurblet-arguments--the-query-mini-language)
into real SQL `WHERE`/`ORDER BY`/join clauses and the JDBC parameter binding that goes with it.

### Why generate the persistence implementation?

A Tentackle entity's persistence implementation is almost entirely mechanical but highly repetitive and
error-prone if written by hand: for every query you need a `SELECT` string assembled from the right column-name
constants, a `PreparedStatement` whose parameters are bound in the correct order with the correct
[`DataType`](../../../../tentackle-sql/src/main/docs/sql.md#datatype) setter, a cached statement id, dialect-aware
paging/locking, aggregate component loading, and — when remoting is enabled — a matching remote delegate. Deriving
all of this from the model guarantees it stays correct as the model evolves, supports every target
[backend](../../../../tentackle-sql/src/main/docs/sql.md) at once, and frees the developer to write only the
genuinely custom logic outside the guarded regions.

### Design principles

- **Model-driven and backend-aware.** Every statement is built from the model and the active backends; the same
  template emits correct SQL for PostgreSQL, Oracle, DB2, etc., and refuses to generate features a target backend
  doesn't support.
- **Woven into the developer's classes.** Generated implementation code lives in guarded regions, so a persistence
  class can freely mix generated queries with handwritten methods.
- **Statement caching by construction.** Generated queries register prepared-statement ids in the entity's class
  variables, so the runtime reuses statements without per-call string building.
- **Transparent remoting.** When an entity or method is remote-enabled, the wurblets generate both the local
  implementation and the [TRIP](../../../../tentackle-core/src/main/docs/trip.md) remote-delegate code, so the
  same call works whether the JVM is directly connected to the database or talking to a server.

---

## DbModelWurblet — the shared base

[`DbModelWurblet`](../java/org/tentackle/persist/wurblet/DbModelWurblet.java) extends
[`ModelWurblet`](../../../../tentackle-wurblets/src/main/docs/wurblets.md#modelwurblet--the-shared-base) and is the
base class for every wurblet in this module (pulled in via
[`header.incl`](../wurblets/org/tentackle/persist/wurblet/header.incl)'s `@{extends DbModelWurblet}`). On top of
the model loading and target resolution it inherits, it adds:

- **Tracking and context options** parsed in `run()`: `--context=<key>` (domain-context column),
  `--tracked` / `--attracked` / `--fulltracked` / `--untracked` (how modified state is tracked on the returned
  objects), exposed via `isTracked()`, `getContextAttribute()`, etc.
- **Persistability guards** — `assertEntityIsPersistable()` and `assertEntityNotEmbedded()` make a wurblet fail
  cleanly when it is applied to an entity that cannot be queried directly.
- **The query-expression model** — after parsing the
  [wurblet arguments](../../../../tentackle-wurblets/src/main/docs/wurblets.md#wurblet-arguments--the-query-mini-language),
  it exposes the parts of a query: `getExpression()`/`getExpressionArguments()` (the `WHERE` tree),
  `getExtraArguments()` (attributes to update), `getSortingArguments()`/`getDefaultSorting()` (ordering), and
  `getJoinPaths()`/`isWithJoins()`/`isWithFilteredJoins()` (eager-loading joins).
- **SQL and JDBC code builders** used inside the templates: `createRelopSql()` / `createRelopCode()` (turn a
  condition into SQL and into the Java that appends it), `createWhereSetPars()` / `createJoinSetPars()` (bind
  statement parameters in order), `createOrderBy()`, `createJoins()`, `createStatementId()`,
  `buildMethodParameters()` / `buildInvocationParameters()` (the generated method signatures and call sites), and
  `createJdbcSetterName()` / `createJdbcGetterName()` (pick the right `ResultSet`/`PreparedStatement` accessor for
  a [`DataType`](../../../../tentackle-sql/src/main/docs/sql.md#datatype)).

Two `@{config ...}@` flags adjust how a template's arguments are parsed: `pathAllowed` (the `WHERE` expression may
follow relation paths) and `groupArgs` (the update wurblets accept a second group of `<values>` after a `|`).

---

## The Wurblets

All templates live under `src/main/wurblets/org/tentackle/persist/wurblet` and carry their own usage
documentation in their leading `@{comment ...}@` block. They fall into four groups.

### Structural — declarations, class variables, and accessors

| Wurblet                                                                           | Generates                                                                                                                                                                  |
|-----------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`Declare`](../wurblets/org/tentackle/persist/wurblet/Declare.wrbl)               | Field declarations for the entity's attributes. Options: `--protected`, `--annotations`, `--mock`.                                                                         |
| [`ClassVariables`](../wurblets/org/tentackle/persist/wurblet/ClassVariables.wrbl) | The per-class `PersistentObjectClassVariables` holder (table alias, statement-id registry, super/sub-class wiring, eager relations) shared by all instances of the entity. |
| [`MethodsImpl`](../wurblets/org/tentackle/persist/wurblet/MethodsImpl.wrbl)       | Getter/setter and other method *implementations*. Options: `--noif` (no interface ⇒ no `@Override`), `--noudk`, `--noisroot`, `--mock`.                                    |
| [`MethodCache`](../wurblets/org/tentackle/persist/wurblet/MethodCache.wrbl)       | The `PdoMethodCache` for an embedded entity.                                                                                                                               |

### Query — select, update, delete, reference checks

These come in a **`Db…`** (low-level persistent object) flavor and a **`Pdo…`** (full PDO) flavor. All take a
[wurblet-argument `<expression>`](../../../../tentackle-wurblets/src/main/docs/wurblets.md#wurblet-arguments--the-query-mini-language)
and share the options `--private`, `--append=<sqltext>` and `--classvar=<classvariables>`; the `Pdo…` variants add
`--noif`. The four select wurblets additionally share `--oneshot`, which prepares the statement for a single use
instead of caching it under a `StatementId`.

| Wurblet                                                                                                                                                                     | Generates                                                                                  | Notable extra options                                                                                                                        |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| [`DbSelectList`](../wurblets/org/tentackle/persist/wurblet/DbSelectList.wrbl) / [`PdoSelectList`](../wurblets/org/tentackle/persist/wurblet/PdoSelectList.wrbl)             | A method selecting a list of objects.                                                      | `--sort`, `--lock`, `--limit`, `--offset`, `--resultset`, `--bounded`; PDO adds `--cursor`, `--immutable`, `--finallyImmutable`, `--mutable` |
| [`DbSelectUnique`](../wurblets/org/tentackle/persist/wurblet/DbSelectUnique.wrbl) / [`PdoSelectUnique`](../wurblets/org/tentackle/persist/wurblet/PdoSelectUnique.wrbl)     | A method selecting a single object.                                                        | `--sort`, `--lock`; PDO adds `--tokenlock`, `--immutable`, `--finallyImmutable`, `--mutable`                                                 |
| [`DbUpdateBy`](../wurblets/org/tentackle/persist/wurblet/DbUpdateBy.wrbl) / [`PdoUpdateBy`](../wurblets/org/tentackle/persist/wurblet/PdoUpdateBy.wrbl)                     | A bulk-update method. Takes `<expression> \| <values>` (the `groupArgs` config).           |                                                                                                                                              |
| [`DbDeleteBy`](../wurblets/org/tentackle/persist/wurblet/DbDeleteBy.wrbl) / [`PdoDeleteBy`](../wurblets/org/tentackle/persist/wurblet/PdoDeleteBy.wrbl)                     | A bulk-delete method.                                                                      |                                                                                                                                              |
| [`DbIsReferencing`](../wurblets/org/tentackle/persist/wurblet/DbIsReferencing.wrbl) / [`PdoIsReferencing`](../wurblets/org/tentackle/persist/wurblet/PdoIsReferencing.wrbl) | A method checking whether an object is referenced by others (referential-integrity guard). |                                                                                                                                              |

The select wurblets have [their own document](pdo-select.md); the reference check and the two
modifying wurblets deserve a closer look here.

#### PdoIsReferencing — the referential-integrity guard

`PdoIsReferencing` generates a `boolean` **existence check**: a `SELECT` of the `id` column with
the given `WHERE` expression, limited to a single row. No objects are loaded and nothing is
modified — the method merely answers whether at least one matching row exists.

Its main job is guarding deletion. For every non-composite object relation whose target entity has
application-checked integrity, [`PdoRelations`](#relations-and-caching) generates an
`isReferencing<Entity>By<attribute>` anchor together with the corresponding referencing-class
registration, so that `isReferenced()` — and with it the default `isRemovable()` — can ask every
registered referencing entity whether it still points to the object before it is deleted.
Handwritten anchors work the same way for custom guards (see for example `isReferencingUser` in
[`ModificationLog`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/ModificationLog.java)).
All attributes of the expression must belong to the same entity.

The [rich desktop client](../../../../tentackle-fx-rdc/src/main/docs/rdc.md) builds on this as
well: `PdoEditor.isRemoveAllowed()` consults `isRemovable()` — and thus `isReferenced()` — to
enable or disable the delete button, so removing a still-referenced PDO is already inhibited at
the GUI level instead of failing later with an SQL exception raised by a foreign-key constraint.

#### PdoUpdateBy and PdoDeleteBy — bulk writes

`PdoUpdateBy` generates a method executing a single `UPDATE <table> SET <values> WHERE <expression>`,
`PdoDeleteBy` a single `DELETE FROM <table> WHERE <expression>`; both return the number of affected
rows. The class-level write permission is still checked (see
[security](../../../../tentackle-pdo/src/main/docs/security.md)), the domain-context condition
(`--context`) still applies, and both are remote-capable like every other query wurblet.

> **Warning — bulk writes bypass the DDD aggregate rule.** These methods modify rows *directly*,
> without instantiating the PDOs. Everything the regular PDO write path guarantees is skipped:
>
> - **No optimistic locking.** The [`serial`](../../../../tentackle-persistence/src/main/docs/locking.md)
>   of the affected rows is neither checked nor incremented. A concurrent user editing one of the
>   objects will neither detect the bulk change, nor will their subsequent save be rejected — the
>   bulk change is silently overwritten, the classic lost update.
> - **No token locks.** Rows [token-locked](../../../../tentackle-persistence/src/main/docs/locking.md)
>   by other users are updated or deleted regardless.
> - **No modification tracking.** Neither a tableserial bump nor a
>   [modification-log](../../../../tentackle-session/src/main/docs/modification-tracking.md) entry
>   is written, so [PDO caches](../../../../tentackle-pdo/src/main/docs/cache.md) — local and in
>   other JVMs — are never invalidated and keep serving stale objects.
> - **No aggregate semantics.** A bulk delete removes only rows of *this* table: components are not
>   cascaded (leaving orphans when applied to a root entity), `isReferenced()` is not consulted,
>   and no domain logic or [validation](../../../../tentackle-core/src/main/docs/validation.md) runs.
>
> In a Tentackle application, all changes normally go through the aggregate root as full PDO
> operations. `PdoUpdateBy` and `PdoDeleteBy` exist for **rare corner cases where performance is
> critical** — mass operations on tables that are untracked, uncached, unlocked, and free of
> components and referencing entities (typically log- or queue-like data). Anywhere else, using
> them means *you* are responsible for consciously re-establishing the guarantees above.

### Relations and caching

| Wurblet                                                                       | Generates                                                                                                                                                                                                                                                                                                                                                                                                           |
|-------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`PdoRelations`](../wurblets/org/tentackle/persist/wurblet/PdoRelations.wrbl) | The relation handling for an entity: `loadComponents()`, `insertPlainWithComponents()`, `deletePlainWithComponents()`, the referencing-class registration, and anchors for the per-relation `PdoSelect…`/`PdoDelete…`/`PdoIsReferencing` wurblets. Toggle blocks with `--[no]loadcomponents`, `--[no]insertcomponents`, `--[no]deletecomponents`, `--nori`, `--noselects`, `--nodeletes`, `--novalidate`, `--mock`. |
| [`PdoCache`](../wurblets/org/tentackle/persist/wurblet/PdoCache.wrbl)         | A `PdoCache` for the entity. Options select the strategy (`--strategy=PRELOAD\|LRU\|LFU\|FORGET`), `--maxsize`, `--keepquota`, `--mutable`, `--udk` and additional unique (optionally filtered, method-extracted) indexes.                                                                                                                                                                                          |

### Remoting

| Wurblet                                                                       | Generates                                                                                                                                                                                                           |
|-------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`AssertRemote`](../wurblets/org/tentackle/persist/wurblet/AssertRemote.wrbl) | Verifies the entity's remote delegate interface/implementation exist, generating them from templates if missing (a no-op when remoting is disabled).                                                                |
| [`RemoteMethod`](../wurblets/org/tentackle/persist/wurblet/RemoteMethod.wrbl) | The remote-delegate invocation code for a method annotated `@RemoteMethod`. Options: `--this` (pass the PDO itself), `--nocontext` (omit the `DomainContext`), `--instance=<object>` (target object on the server). |

### Shared template fragments

The `.incl` files factor out repeated generation logic shared by several query wurblets:
[`genwhere.incl`](../wurblets/org/tentackle/persist/wurblet/genwhere.incl) (the `WHERE` clause, including the
class-id condition for single-table inheritance and the domain-context predicate),
[`genupdate.incl`](../wurblets/org/tentackle/persist/wurblet/genupdate.incl) (the `SET` list of an update), and
[`gensetpar.incl`](../wurblets/org/tentackle/persist/wurblet/gensetpar.incl) (binding statement parameters,
including join and context parameters). [`header.incl`](../wurblets/org/tentackle/persist/wurblet/header.incl) is
the shared preamble with the imports and `@{extends DbModelWurblet}`.

---

## Supporting Java Classes

| Class                                                                                     | Role                                                                                                                                                                           |
|-------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`DbModelWurblet`](../java/org/tentackle/persist/wurblet/DbModelWurblet.java)             | Base class for all persistence wurblets.                                                                                                                                       |
| [`WhereClauseGenerator`](../java/org/tentackle/persist/wurblet/WhereClauseGenerator.java) | A [`CodeGenerator`](../../../../tentackle-wurblets/src/main/docs/wurblets.md) that walks the parsed argument expression and emits the Java that builds the SQL `WHERE` clause. |
| [`JoinClauseGenerator`](../java/org/tentackle/persist/wurblet/JoinClauseGenerator.java)   | Emits the SQL for eager-loading joins, including filtered joins.                                                                                                               |
| [`RemoteIncludes`](../java/org/tentackle/persist/wurblet/RemoteIncludes.java)             | Creates and updates the generated TRIP remote interface/implementation files (from FreeMarker templates) used by `AssertRemote`.                                               |
| [`RemoteMethodHelper`](../java/org/tentackle/persist/wurblet/RemoteMethodHelper.java)     | Extracts the wurblet tags for a `@RemoteMethod` from the compiled class file.                                                                                                  |

---

## How It Fits Together

```
   model (*.map / entity definitions)        tentackle-sql (DataType, Backend)
                 │                                       │
                 └───────────────┬───────────────────────┘
                                 ▼
   tentackle-wurblets        ──►  PDO / operation *interfaces*, DTOs,
   (interface side)               constants, unique-domain-key types
                 │
                 ▼  (depends on)
   tentackle-persistence-wurblets ──►  PDO / operation *implementations*:
   (this module, implementation side)   field declarations, class variables,
                                         SELECT/INSERT/UPDATE/DELETE, caches,
                                         remote delegates (TRIP)
```

During an application build, the
[Tentackle Maven plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md) arranges for
the wurbelizer plugin to run both wurblet sets over the sources, in phases, so the interface anchors are filled
before the implementation anchors that reference them. A typical generated persistence class therefore contains a
`Declare` block, a `ClassVariables` block, a `MethodsImpl` block, a `PdoRelations` block (which in turn nests
`PdoSelect…`/`PdoDelete…` anchors), and — when the model marks the entity cacheable or remote — `PdoCache` and
`AssertRemote`/`RemoteMethod` blocks.

---

## Module Dependencies

- **[Wurbelizer](https://wurbelizer.org/)** (`org.wurbelizer:wurbelizer`) — the template engine and the weaving of
  guarded regions.
- **[`tentackle-wurblets`](../../../../tentackle-wurblets/src/main/docs/wurblets.md)** — the interface-side
  wurblets and their shared base `ModelWurblet`, the
  [wurblet-argument parser](../../../../tentackle-wurblets/src/main/docs/wurblets.md#wurblet-arguments--the-query-mini-language)
  and the model/SQL helpers this module builds upon.
- **`tentackle-build-support`** — shared build-time utilities (and FreeMarker template support for remote code).
- Transitively, **[`tentackle-model`](../../../../tentackle-model/src/main/docs/model-definition.md)** and
  **[`tentackle-sql`](../../../../tentackle-sql/src/main/docs/sql.md)** for the model and `DataType`/`Backend` APIs.

Like `tentackle-wurblets`, this is a **build-time only** module, packaged with an `Automatic-Module-Name` of
`org.tentackle.persist.wurblet`; it runs on the wurbelizer plugin's classpath and is never shipped inside the
running application.

## Related Documentation

- [Tentackle Wurblets](../../../../tentackle-wurblets/src/main/docs/wurblets.md) — the interface-side counterpart, the wurblet anchor mechanism, and the wurblet-argument query language.
- [`PdoSelectList` / `PdoSelectUnique`](pdo-select.md) — generating finder methods, including eager-loading `*` load joins.
- [Eager Relations](eager-relations.md) — model-level `select = eager` vs. domain-level load joins, and why eager loading never cascades.
- [Model Definition](../../../../tentackle-model/src/main/docs/model-definition.md) — the model the wurblets consume.
- [PDO — Persistent Domain Objects](../../../../tentackle-pdo/src/main/docs/pdo.md) — the objects these implementations realize.
- [Tentackle SQL](../../../../tentackle-sql/src/main/docs/sql.md) — `DataType`/`Backend` used to build the SQL and bind parameters.
- [TRIP](../../../../tentackle-core/src/main/docs/trip.md) — the remoting protocol behind the generated remote delegates.
- [Tentackle Maven Plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md) — drives wurblet execution during the build.
- [Wurbelizer — How it works](https://wurbelizer.org/how-it-works/) — the underlying code-generation engine.
