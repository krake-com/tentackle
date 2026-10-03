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
| **Generates into**     | PDO / operation *interfaces*, DTOs (plus `ColumnNames` and `DomainMethods` in the implementations)        | persistence *implementation* classes and the TRIP remote delegates                                      |
| **Typical output**     | `AN_*`/`RN_*`/`CL_*` constants, getter/setter declarations, relation method declarations, unique-domain-key types | field declarations, `SELECT`/`INSERT`/`UPDATE`/`DELETE` code, class variables, caches, remote delegates |
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
base class of the query wurblets in this module (pulled in via
[`header.incl`](../wurblets/org/tentackle/persist/wurblet/header.incl)'s `@{extends DbModelWurblet}`; the
[`QueryDSL` wurblet](#the-querydsl-wurblet) is the exception, as it takes no query expression). On top of
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
Only the four select wurblets declare `pathAllowed`, so the update, delete and reference-check wurblets reject
relation paths. A third flag, `missingModelOk`, is handled by `ModelWurblet`: `RemoteMethod` declares it, so it
also works in classes that have no entity in the model, such as operations.

Each of these methods — and the rest of `DbModelWurblet`'s API — is documented in
[The Persistence Wurblet API](persistence-wurblet-api.md#dbmodelwurblet).

Like all Tentackle wurblets, the persistence wurblets inherit `isThreadSafe()` from `ModelWurblet`. They run
concurrently in parallel builds when declared as `<wurbletDependencies>`; see
[Parallel builds](../../../../tentackle-wurblets/src/main/docs/wurblets.md#parallel-builds).

---

## The Wurblets

All templates live under `src/main/wurblets/org/tentackle/persist/wurblet` and carry their own usage
documentation in their leading `@{comment ...}@` block. They fall into four groups.

### Structural — declarations, class variables, and accessors

| Wurblet                                                                           | Generates                                                                                                                                                                  |
|-----------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`Declare`](../wurblets/org/tentackle/persist/wurblet/Declare.wrbl)               | Field declarations for the entity's attributes. Options: `--protected`, `--annotations`, `--mock`.                                                                         |
| [`ClassVariables`](../wurblets/org/tentackle/persist/wurblet/ClassVariables.wrbl) | The per-class `PersistentObjectClassVariables` holder (table alias, statement-id registry, super/sub-class wiring, the cascaded eager join tree) shared by all instances of the entity. |
| [`MethodsImpl`](../wurblets/org/tentackle/persist/wurblet/MethodsImpl.wrbl)       | Getter/setter and other method *implementations*. Options: `--noif` (no interface ⇒ no `@Override`), `--noudk`, `--noisroot`, `--mock`.                                    |
| [`MethodCache`](../wurblets/org/tentackle/persist/wurblet/MethodCache.wrbl)       | The `PdoMethodCache` for an embedded entity.                                                                                                                               |
| [`QueryDSL`](../wurblets/org/tentackle/persist/wurblet/QueryDSL.wrbl)             | The table- and field constants of the [query DSL](../../../../tentackle-database/src/main/docs/query-dsl.md). Options: `--public`, `--protected`, `--prefix[=<prefix>]`, `--table=<constant>`, `--alias=<alias>`, `--noalias`. |

`MethodsImpl` and `PdoRelations` are *generating* wurblets in a second sense: besides code, they emit further
`@wurblet` anchors, e.g. `// @wurblet selectByUniqueDomainKey PdoSelectUnique name locale` or one
`PdoSelectList` per list relation. The anchor ends up as the last line of the outer fold, and the wurbelizer
expands it into a fold of its own right after it, labeled with both wurblet names
(`generated by wurblet PdoSelectUnique/MethodsImpl`). The options `--noudk`, `--noselects`, `--nodeletes` and
`--nori` suppress these anchors when the method is written by hand instead.

`MethodCache` replaces `ClassVariables` for an embedded entity. Such an entity has no table of its own, so it
needs no class variables, only the `PdoMethodCache` that every PDO class provides.

#### The QueryDSL wurblet

[`QueryDSL`](../wurblets/org/tentackle/persist/wurblet/QueryDSL.wrbl) generates the constants a
[query-DSL](../../../../tentackle-database/src/main/docs/query-dsl.md) query is built from — the table and
the typed columns:

```java
  // @wurblet customer QueryDSL Customer id name turnover active
```

```java
  /** the query DSL table of entity Customer. */
  private static final Table CUSTOMER = DSL.table("customer", "cu");

  /** the query DSL field for 'id', column 'id'. */
  private static final Field<Long> ID = CUSTOMER.field("id", Long.class);

  /** the query DSL field for 'name', column 'name'. */
  private static final Field<String> NAME = CUSTOMER.field("name", String.class);
  ...
```

It differs from the other wurblets in two ways:

- **The entity is the first argument**, not the model of the source file. Projection queries are usually written
  where the data is *used* — a report, an export, an operation — not in the entity's own classes, and a join query
  needs the declarations of several entities in the same class. The remaining arguments name the attributes;
  without any, the fields for all attributes mapped by the entity are generated.
- **It takes no query expression**, which is why it extends `ModelWurblet` directly instead of `DbModelWurblet`.

Everything else follows from the model: the table name and alias, the column name of each attribute, and the java
type of its *effective* [`DataType`](../../../../tentackle-sql/src/main/docs/sql.md#datatype) — the wrapper for
primitives (the values of a query are objects), the external type for `Convertible`s, and the variant for types
such as the large string. Columns whose java type is generic (`Binary<T>`) become untyped fields, since there is
no class literal for them. Rename a column in the model and the constant follows on the next build; drop an
attribute and the block stops compiling where it is used.

Because the constants are ordinary java constants, they live wherever the developer puts the anchor. Generating
the declarations of two entities into one class needs `--prefix` (or `--table=`) to keep the names apart:

```java
  // @wurblet customer QueryDSL --prefix Customer id name
  // @wurblet order QueryDSL --prefix Order customerId net
```

The generated code refers to `DSL`, `Table` and `Field` from `org.tentackle.dbms.dsl` — and to
`org.tentackle.sql.DataTypeFactory` if a datatype has a variant — so the class must import them.

### Query — select, update, delete, reference checks

These come in a **`Db…`** (low-level persistent object) flavor and a **`Pdo…`** (full PDO) flavor. All take a
[wurblet-argument `<expression>`](../../../../tentackle-wurblets/src/main/docs/wurblets.md#wurblet-arguments--the-query-mini-language)
and share the options `--private`, `--append=<sqltext>` and `--classvar=<classvariables>`; the `Pdo…` variants add
`--noif`. The four select wurblets additionally share `--oneshot`, which prepares the statement for a single use
instead of caching it under a `StatementId`. They are also the only ones accepting
[optional conditions](pdo-select.md#optional-conditions-qbf-style-finders) (`attribute?<boolean>`), which imply
`--oneshot` because the SQL text then depends on the boolean method arguments.

| Wurblet                                                                                                                                                                     | Generates                                                                                  | Notable extra options                                                                                                                        |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| [`DbSelectList`](../wurblets/org/tentackle/persist/wurblet/DbSelectList.wrbl) / [`PdoSelectList`](../wurblets/org/tentackle/persist/wurblet/PdoSelectList.wrbl)             | A method selecting a list of objects.                                                      | `--sort`, `--lock`, `--limit`, `--offset`, `--resultset`, `--bounded`; PDO adds `--cursor`, `--immutable`, `--finallyImmutable`, `--mutable` |
| [`DbSelectUnique`](../wurblets/org/tentackle/persist/wurblet/DbSelectUnique.wrbl) / [`PdoSelectUnique`](../wurblets/org/tentackle/persist/wurblet/PdoSelectUnique.wrbl)     | A method selecting a single object.                                                        | `--sort`, `--lock`; PDO adds `--tokenlock`, `--immutable`, `--finallyImmutable`, `--mutable`                                                 |
| [`DbUpdateBy`](../wurblets/org/tentackle/persist/wurblet/DbUpdateBy.wrbl) / [`PdoUpdateBy`](../wurblets/org/tentackle/persist/wurblet/PdoUpdateBy.wrbl)                     | A bulk-update method. Takes `<expression> \| <values>`, the attributes after the `\|` being the `SET` list. | `--private`, `--append=<sqltext>`, `--classvar=<classvariables>`; PDO adds `--noif`                                                          |
| [`DbDeleteBy`](../wurblets/org/tentackle/persist/wurblet/DbDeleteBy.wrbl) / [`PdoDeleteBy`](../wurblets/org/tentackle/persist/wurblet/PdoDeleteBy.wrbl)                     | A bulk-delete method.                                                                      | `--private`, `--append=<sqltext>`, `--classvar=<classvariables>`; PDO adds `--noif` |
| [`DbIsReferencing`](../wurblets/org/tentackle/persist/wurblet/DbIsReferencing.wrbl) / [`PdoIsReferencing`](../wurblets/org/tentackle/persist/wurblet/PdoIsReferencing.wrbl) | A method checking whether an object is referenced by others (referential-integrity guard). | `--private`, `--append=<sqltext>`, `--classvar=<classvariables>` |

The options shared with the select wurblets have the same meaning as there (see
[Options](pdo-select.md#options)): `--private` makes the method `private`, disables remoting and implies `--noif`.
A public `PdoDeleteBy` method that is not declared in an interface needs an explicit `--noif`. `PdoUpdateBy`
never generates `@Override`, so `--noif` makes no difference there. `PdoIsReferencing` doesn't generate
`@Override` either: `PdoRelations` registers its methods by name (`addReferencingClass(classId, "isReferencing…")`),
and `isReferenced()` invokes them from there, not through an interface.
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
| [`PdoCache`](../wurblets/org/tentackle/persist/wurblet/PdoCache.wrbl)         | A [`PdoCache`](../../../../tentackle-pdo/src/main/docs/cache.md) for the entity. Options select the strategy (`--strategy=PRELOAD\|LRU\|LFU\|FORGET`, default `PRELOAD`), `--maxsize=<n>`, `--keepquota=<percent>` (default 50), `--mutable` (a non-shared cache of mutable PDOs instead of the default shared, immutable one), `--udk` (an index for the unique domain key), `--configure=<method>` (a method further configuring the created cache) and additional unique indexes. |

Every cache has an index by `id`. Each further argument of `PdoCache` adds a unique index by that attribute. An
attribute followed by a method name in square brackets uses that method instead of the getter to extract the key
(without the leading `get`). Returning `null` from it leaves the PDO out of the index, which makes a *filtered*
index:

```java
  // @wurblet cache PdoCache --udk objectId code[codeIfUser]
```

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
| [`DslDeclarationFactory`](../java/org/tentackle/persist/wurblet/DslDeclarationFactory.java) | Builds the query-DSL `Table`/`Field` constant declarations consumed by the `QueryDSL` wurblet.                                                                                |

For the complete class reference — the query model, the SQL/JDBC code builders, the `EXISTS` clause generation and
the remoting helpers — see [The Persistence Wurblet API](persistence-wurblet-api.md).

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
the wurbelizer plugin to run both wurblet sets over the sources. Each wurblet derives its code from the model
alone, not from the code other wurblets generated into other files, so the order in which the files are
processed doesn't matter. The compiler sees the result only after all of them have run.

The [PDO wizard](../../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md) lays
out a persistence implementation like this:

```java
public class MessagePersistenceImpl
       extends AbstractPersistentObject<Message, MessagePersistenceImpl>
       implements MessagePersistence {

  // @wurblet classVariables ClassVariables       (MethodCache for an embedded entity)

  // @wurblet columnNames ColumnNames             (from tentackle-wurblets)

  // @wurblet declare Declare

  // ... constructors ...

  // @wurblet methods MethodsImpl                 (nests selectByUniqueDomainKey)

  // @wurblet relations PdoRelations              (nests the relation selects, deletes, isReferencing...)

  // @wurblet cache PdoCache                      (only if the PDO is cached)

  // hand-written finders: PdoSelectList, PdoSelectUnique, ...
}
```

Remoting needs no anchor of its own: every remote-capable wurblet creates the TRIP remote delegate interface and
implementation if they are missing, and writes its remote methods into them (see
[`RemoteIncludes`](persistence-wurblet-api.md#remoteincludes)). `AssertRemote` is for a class that has no other
remote-capable wurblet, typically an operation, and `RemoteMethod` for each hand-written method annotated
`@RemoteMethod`.

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

- [The Persistence Wurblet API](persistence-wurblet-api.md) — the `org.tentackle.persist.wurblet` classes the templates compile against.
- [Tentackle Wurblets](../../../../tentackle-wurblets/src/main/docs/wurblets.md) — the interface-side counterpart, the wurblet anchor mechanism, and the wurblet-argument query language.
- [The Wurblet API](../../../../tentackle-wurblets/src/main/docs/wurblet-api.md) — `ModelWurblet`, the argument mini-language and the join model this module builds on.
- [`PdoSelectList` / `PdoSelectUnique`](pdo-select.md) — generating finder methods, including eager-loading `*` load joins.
- [Eager Relations](eager-relations.md) — model-level `select = eager[:depth]` vs. domain-level load joins, and how far each of them cascades.
- [Model Definition](../../../../tentackle-model/src/main/docs/model-definition.md) — the model the wurblets consume.
- [PDO — Persistent Domain Objects](../../../../tentackle-pdo/src/main/docs/pdo.md) — the objects these implementations realize.
- [Tentackle SQL](../../../../tentackle-sql/src/main/docs/sql.md) — `DataType`/`Backend` used to build the SQL and bind parameters.
- [TRIP](../../../../tentackle-core/src/main/docs/trip.md) — the remoting protocol behind the generated remote delegates.
- [Tentackle Maven Plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md) — drives wurblet execution during the build.
- [Wurbelizer — How it works](https://wurbelizer.org/how-it-works/) — the underlying code-generation engine.
