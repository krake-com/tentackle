# The Persistence Wurblet API — `org.tentackle.persist.wurblet` Class Reference

## Overview

[`persistence-wurblets.md`](persistence-wurblets.md) describes *what* the persistence wurblets generate. This
document describes the Java classes behind them: the package `org.tentackle.persist.wurblet`, shipped in
`tentackle-persistence-wurblets`.

It is the implementation-side counterpart of
[The Wurblet API](../../../../tentackle-wurblets/src/main/docs/wurblet-api.md), which documents
`org.tentackle.wurblet` — the base classes, the argument mini-language and the join model this package builds on.
Read that one first; almost everything here consumes it.

Where the interface-side package parses a model and a query expression, this package **turns that parsed
expression into executable code**. Six classes do the work:

- **`DbModelWurblet`** — the base class of every query wurblet, and by far the largest: it owns the parsed query,
  the tracking/context options, and roughly forty helper methods that emit SQL fragments, JDBC parameter binding,
  method signatures and relation-navigation code.
- **`WhereClauseGenerator`** and **`JoinClauseGenerator`** — the two `CodeGenerator` implementations that walk the
  argument expression tree; the first emits *Java code that builds SQL at runtime*, the second emits *literal SQL*.
- **`DslDeclarationFactory`** — generates the [query-DSL](../../../../tentackle-database/src/main/docs/query-dsl.md)
  table and field constants.
- **`RemoteIncludes`** and **`RemoteMethodHelper`** — the [TRIP](../../../../tentackle-core/src/main/docs/trip.md)
  remote-delegate machinery.

Like its sibling this is a **build-time only** module, packaged with an `Automatic-Module-Name` of
`org.tentackle.persist.wurblet`. It runs on the `wurbelizer-maven-plugin`'s classpath and never ships inside the
running application.

### Who consumes these classes

| Class                   | Used by                                                                                                                                                   |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DbModelWurblet`        | every template in this module except `QueryDSL`, via [`header.incl`](../wurblets/org/tentackle/persist/wurblet/header.incl)'s `@{extends DbModelWurblet}` |
| `WhereClauseGenerator`  | [`genwhere.incl`](../wurblets/org/tentackle/persist/wurblet/genwhere.incl) — and thus every select/update/delete/reference wurblet                        |
| `JoinClauseGenerator`   | `DbModelWurblet` itself, when a load join carries a filter                                                                                                |
| `DslDeclarationFactory` | [`QueryDSL.wrbl`](../wurblets/org/tentackle/persist/wurblet/QueryDSL.wrbl)                                                                                |
| `RemoteIncludes`        | `AssertRemote`, `RemoteMethod`, `MethodsImpl`, `PdoCache` and all ten `Db…`/`Pdo…` query wurblets                                                         |
| `RemoteMethodHelper`    | [`RemoteMethod.wrbl`](../wurblets/org/tentackle/persist/wurblet/RemoteMethod.wrbl)                                                                        |

---

## Package Map

```
org.tentackle.wurblet.ModelWurblet            (tentackle-wurblets)
 └── DbModelWurblet .......................... base of every query wurblet
       │
       ├── owns  ──► WurbletArgumentParser     (the parsed query: expression, sorting,
       │                                        extra args, join paths — normalized)
       │
       ├── delegates ──► WhereClauseGenerator   ─── implements CodeGenerator<Object>
       │                    │  emits JAVA that appends SQL to a StringBuilder at runtime
       │                    └── uses ComponentInfo for the EXISTS clause of component paths
       │
       └── delegates ──► JoinClauseGenerator    ─── implements CodeGenerator<Object>
                            emits LITERAL SQL for the filter of a load join

  independent helpers
  ───────────────────
  DslDeclarationFactory ── record Declaration(comment, code)  → Table/Field constants
  RemoteIncludes ──────── HeapStreams + FreeMarker            → TRIP delegate files
  RemoteMethodHelper ──── wraps RemoteMethodInfo               → remote method signatures
```

| Class                                                                                       | Kind  | Role                                                                                                                                                     |
|---------------------------------------------------------------------------------------------|-------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`DbModelWurblet`](../java/org/tentackle/persist/wurblet/DbModelWurblet.java)               | class | Base for all persistence wurblets: query model, tracking/context options, SQL/JDBC/method code builders.                                                 |
| [`WhereClauseGenerator`](../java/org/tentackle/persist/wurblet/WhereClauseGenerator.java)   | class | Emits the Java that assembles the SQL `WHERE` clause, including `EXISTS` subqueries for relation paths.                                                  |
| [`JoinClauseGenerator`](../java/org/tentackle/persist/wurblet/JoinClauseGenerator.java)     | class | Emits the literal SQL for a filtered load join.                                                                                                          |
| [`DslDeclarationFactory`](../java/org/tentackle/persist/wurblet/DslDeclarationFactory.java) | class | Builds the query-DSL `Table`/`Field` constant declarations from the model.                                                                               |
| [`RemoteIncludes`](../java/org/tentackle/persist/wurblet/RemoteIncludes.java)               | class | Creates the TRIP remote interface/implementation files from FreeMarker templates and hands out the streams the wurblets write their remote methods into. |
| [`RemoteMethodHelper`](../java/org/tentackle/persist/wurblet/RemoteMethodHelper.java)       | class | Wraps the analyze-time `RemoteMethodInfo` of an `@RemoteMethod` and builds its declaration/invocation parameter strings.                                 |

---

## DbModelWurblet

[`DbModelWurblet`](../java/org/tentackle/persist/wurblet/DbModelWurblet.java) extends
[`ModelWurblet`](../../../../tentackle-wurblets/src/main/docs/wurblet-api.md#modelwurblet), so everything
documented there — model loading, target resolution, `getOption`, the naming and comment helpers,
`assertSupportedByBackends` — is available here too. This section covers only what it adds.

### Configuration flags

Two `@{config …}@` flags in the template control how the arguments are parsed:

| Flag          | Effect                                                                                                                                                                                                                                     |
|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `groupArgs`   | The `\|` character separates the `WHERE` expression from a second group of arguments (`getExtraArguments()`). Used by `DbUpdateBy`/`PdoUpdateBy` for the `SET` values. Without it, `\|` only ends the expression and improves readability. |
| `pathAllowed` | Expression arguments may follow relation paths to other entities. Without it, any path argument is rejected outright.                                                                                                                      |

> The two are read in an `if`/`else if` chain, so a template declaring **both** only gets `groupArgs`. No template
> in the reactor currently needs both, but it is a trap if one ever does.

### Lifecycle: what `run()` adds

```
run()
 ├─ 1. read the config flags (groupArgs / pathAllowed) — before super.run()
 ├─ 2. super.run()                       (ModelWurblet: load the model, resolve the entity)
 └─ 3. if the entity was resolved:
        ├─ seed the tracking flags from the entity's TrackType and the context attribute
        │     from getContextIdAttribute()
        ├─ apply the option overrides: --tracked / --attracked / --fulltracked / --untracked /
        │     --context=<key>
        ├─ build the WurbletArgumentParser over the positional arguments
        ├─ reject relation paths unless pathAllowed
        ├─ reject optional conditions (?boolean) inside join filters — they are supported
        │     only in the WHERE expression
        └─ if remoting is on: reject joined relations that are neither composite, nor
              serialized, nor EAGER (they could not be transported), and apply the same
              check to their opposite relations
```

Everything is skipped when the entity is `null`, which happens only under `missingModelOk`.

### Options

| Option            | Accessor                | Meaning                                                                                                  |
|-------------------|-------------------------|----------------------------------------------------------------------------------------------------------|
| `--tracked`       | `isTracked()`           | Return a `TrackedList` instead of a plain `List`, even if the model does not say `[TRACKED]`.            |
| `--attracked`     | `isAttracked()`         | As `--tracked`, plus per-attribute `is…Modified()`.                                                      |
| `--fulltracked`   | `isFullTracked()`       | As `--attracked`, plus the last persisted values are kept.                                               |
| `--untracked`     | —                       | Disable whatever the model declared.                                                                     |
| `--context=<key>` | `getContextAttribute()` | The domain-context column. `--context=` with an empty value turns the default context predicate **off**. |

The three predicates nest: `fullTracked` implies `attracked` implies `tracked`.

### Guards

| Method                                                  | Rejects                                                                                                                                        |
|---------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| `isEntityPersistable()` / `assertEntityIsPersistable()` | An abstract entity whose hierarchy maps to no table — nothing to run SQL against.                                                              |
| `assertEntityNotEmbedded()`                             | An embedded entity, naming the entities it is embedded in. It has no table of its own to select from.                                          |
| `assertNoOptionalArguments()`                           | Any `?boolean` condition. Called by the wurblets that cache their prepared statement, since an optional condition makes the SQL text variable. |

### The parsed query

| Method                                                                    | Returns                                                                                                                                                                                                                |
|---------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `getExpression()`                                                         | The `WurbletArgumentExpression` tree for the `WHERE` clause.                                                                                                                                                           |
| `getExpressionArguments()`                                                | The flat list of arguments inside that tree.                                                                                                                                                                           |
| `getExtraArguments()`                                                     | The arguments after `\|` — the `SET` list of an update.                                                                                                                                                                |
| `getMethodArguments()`                                                    | **Join-filter arguments first, then the parser's own method arguments.** This ordering is the JDBC parameter binding order, so it must match the order in which the SQL is assembled.                                  |
| `getSortingArguments()` / `isWithSorting()`                               | The explicit `+`/`-` sort keys.                                                                                                                                                                                        |
| `getDefaultSorting()` / `getDefaultSortKeys()` / `isWithDefaultSorting()` | The model's default sorting. `getDefaultSorting()` walks **up the inheritance tree** until it finds an entity that declares one; `getDefaultSortKeys()` re-parses those into `WurbletArgument`s.                       |
| `getJoinPaths()` / `isWithJoins()`                                        | The consolidated load-join paths — **normalized**, so every node holds exactly one `Join`. All the generation code relies on this and simply reads `getElements().get(0)`.                                             |
| `isWithFilteredJoins(componentsOnly)`                                     | Whether any join carries a filter, i.e. whether the result must be made immutable.                                                                                                                                     |
| `isWithOptionalArguments()`                                               | Whether any condition is optional, which implies `--oneshot`.                                                                                                                                                          |
| `createOptionalCondition(args)`                                           | The Java boolean expression deciding whether a group of arguments is part of the query: the `\|\|` of their distinct boolean names, or `null` as soon as one argument is mandatory (the group is then always emitted). |
| `isPdoProvidingArguments()`                                               | Whether some fixed value is a method call such as `getBlah()` rather than a literal.                                                                                                                                   |
| `isAbstractJoinPath(paths)`                                               | Whether any join touches an abstract entity — the generated casts are then unchecked and need a `@SuppressWarnings`.                                                                                                   |

### SQL builders

**`createRelopCode(arg)` and `createRelopSql(arg)`** are the same switch over the relational operator, rendered
two different ways. Both first verify the array operator against every active backend via
`assertSupportedByBackends`, and both quote a literal value that is not already quoted.

|             | `createRelopCode`                         | `createRelopSql`                               |
|-------------|-------------------------------------------|------------------------------------------------|
| Produces    | Java: `.append(Backend.SQL_EQUAL_PAR)`    | SQL: `=?`                                      |
| Consumed by | `WhereClauseGenerator` (runtime assembly) | `JoinClauseGenerator` (fixed join filter text) |

The switch maps `=`, `<>`/`!=`, `<`, `>`, `<=`, `>=`, `LIKE`, `NOT LIKE`, `IS NULL` and `IS NOT NULL` onto the
`Backend.SQL_*` constants, choosing the `…_PAR` variant (which carries the `?`) unless the value is literal or the
argument is an array. An unrecognized relop falls through to the raw text plus `?`. Array arguments then get the
matching `SQL_ARRAY_{ANY,ALL,IN,NOT_IN}_PAR` suffix appended.

**`createOrderBy(sortKeys)` / `createOrderBy()`** build the `ORDER BY` list, expanding a multi-column datatype
into its sortable columns (and failing with *"… is not sortable by the database"* if it has none). Column
qualification depends on where the attribute lives:

- **multi-table inheritance** → qualify with the top super-entity's `CLASSVARIABLES`;
- **an attribute of another entity** → find the load join that brought it in and emit the literal
  `"j_1.column"`, honoring the embedding column prefix; if no join matches and the attribute is not reachable
  through an embedding path, the build fails with *"missing join for sort key"*;
- **an attribute of this entity** → `getColumnName(CN_…)` for PDOs, the bare constant otherwise.

**`createJoins()`** emits the whole `JoinedSelect<T>` construction for eager loading — a nested chain of
`addJoin(new Join<>(JoinType.LEFT, …))` calls, one per normalized path node, each with a lambda that links the
loaded row into its parent. List and object relations differ:

|              | List relation                                                                                                                    | Object relation                                                                                         |
|--------------|----------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| Join columns | parent `id` → child foreign attribute                                                                                            | parent foreign key → child `id`                                                                         |
| Linking code | `getXBlunt().addBlunt(child)`, or the setter when the relation is reversed; a link method is invoked when the model declares one | the setter on the child's persistence delegate, walking the embedding getter path for embedded entities |

`Blunt` is appended to the setter name when the relation is serialized. `createOptionalWhereForJoin` adds the
extra predicates for relations with more than one method argument (a value becomes `=?`, otherwise the columns are
compared) and finally appends the join's own filter SQL produced by `JoinClauseGenerator`.

### JDBC parameter binding

| Method                                           | Emits                                                                                                                                                                                                                                                                                                                                                                                                                                             |
|--------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `createWhereSetPars(WurbletArgument)`            | The binding for one condition. Arrays become `st.setArray(ndx++, Element.class, columnIndex, value, Backend.SQL_ARRAY_…)`. With an explicit column index, a *predefined* multi-column type (`BMoney`, `OffsetDateTime`, …) binds one column via `st.set(SqlType.…, ndx++, …)`, while an application type uses `ndx += st.set(DATATYPE, ndx, value, columnIndex, mapNull, size)`. Without a column index it delegates to the attribute form below. |
| `createWhereSetPars(Attribute, String argument)` | The single-column form: `st.set<Type>(ndx++, code)` for predefined types (with a trailing `ndx` advance when the type spans several columns), `ndx += st.set(DATATYPE, ndx, code, mapNull, size)` otherwise. Rejects backend-specific column counts in a `WHERE` clause.                                                                                                                                                                          |
| `createJoinSetPars()`                            | Binds the *value* method arguments of every join, descending recursively into the sub-paths — in the same order `createJoins()` emitted them.                                                                                                                                                                                                                                                                                                     |

`createStatementId()` turns the wurblet's guard name into the `UPPER_SNAKE_CASE` name of the cached statement id,
suffixed `_STMT` (`selectByName` → `SELECT_BY_NAME_STMT`).

### Method signatures

`buildMethodParameters(limit, offset)` and `buildInvocationParameters(limit, offset)` produce the declaration and
the call site of the generated method, so they must stay in lockstep. Both walk `getMethodArguments()`,
**deduplicating by argument name**, and both append a `boolean <name>` parameter for each distinct optional
condition. An array argument is declared as `Collection<Element>` — the element type being the application type
for [convertibles](../../../../tentackle-sql/src/main/docs/sql.md) and the boxed type otherwise. The no-argument
overloads pass `false, false`.

Four one-liners keep the templates readable: `acs(str, s)` / `pcs(str, s)` append/prepend to a comma-separated
list, `aas(str)` prefixes a non-empty string with `", "`, and `as(str)` maps `null` to the empty string.

### DataType and convertible handling

| Method                            | Purpose                                                                                                                                                                                                                                                            |
|-----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `createJdbcSetterName(DataType)`  | The `PreparedStatementWrapper` setter: `set` + the Java type, with `setLargeString` for the large `String` variant.                                                                                                                                                |
| `createJdbcGetterName(Attribute)` | The `ResultSetWrapper` getter. Nullable wrapper types get an **`A` prefix** — `getABoolean`, `getAByte`, `getAShort`, `getALong`, `getAFloat`, `getADouble` — to distinguish them from the primitive getters; the large `String` variant becomes `getLargeString`. |
| `getModelCode(attr, jdbcCode)`    | Wraps JDBC-side code in `<Type>.toInternal(…)` for a convertible attribute; returns it unchanged otherwise.                                                                                                                                                        |
| `getJdbcCode(attr, modelCode)`    | The inverse: appends `.toExternal()`. An enum-style constant is converted directly; otherwise a null guard is generated — falling back to `getDefault().toExternal()` when the inner type is primitive, and to `null` when it is nullable.                         |

### Relation navigation code

These build the code `PdoRelations` and `ClassVariables` weave into the persistence implementation.

| Method                                                            | Produces                                                                                                                                                                                                                                                                                                               |
|-------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `isRelationTransient(Relation)`                                   | Whether the field needs the `transient` modifier — true for lazy, non-composite, non-serialized relations.                                                                                                                                                                                                             |
| `createRelationArgString` / `createRelationWurbletArgString`      | The `(a, b)` invocation argument list, and the space-separated foreign attribute names used to build a nested wurblet anchor.                                                                                                                                                                                          |
| `createRelationSelectCode(Relation)`                              | `on(Foo.class).selectBy…(args)`, prefixed with a `(Foo<?>)` cast when the foreign entity is abstract and the framework's own `select`/`selectCached` is used — without it the generic type would not match.                                                                                                            |
| `createRelationDeleteCode` / `createRelationLinkCode`             | The bulk-delete call and the back-reference setter (honoring an explicit link method and its optional index argument).                                                                                                                                                                                                 |
| `createRelationUpdateReferenceCode(relation, pdo, blunt)`         | The code that writes the back-reference. The `blunt` request is **silently downgraded** unless the opposite relation is a serialized object relation with lazy or eager selection; a declared link method wins over the setter when not blunt. The one-argument overload fixes the PDO to `me()` and never uses blunt. |
| `createRelationSetFirstArgMethodName` / `getFirstMethodAttribute` | The setter for a single-argument relation; both raise a `ModelException` on a relation whose method arguments do not fit.                                                                                                                                                                                              |
| `getEagerJoinTree()` / `getEagerJoinPaths()`                      | The eager join tree of the entity, cascaded to each relation's `select = eager:<depth>`, and the same tree as `*a*b` load join terms for the generated relation finders. Both return nothing when a subclass declares an eager relation, because a polymorphic select cannot vary its joins by row class.               |
| `createEagerJoins()`                                              | The whole trailing `List.of(...)` argument of `PersistentObjectClassVariables.create(...)` — nested `addJoin(...)` chains with hierarchical table aliases (`e_1`, `e_1_1`). Deliberately separate from `createJoins()`: the class variables are a *static* initializer, so joined columns must be string literals.      |

---

## WhereClauseGenerator

[`WhereClauseGenerator`](../java/org/tentackle/persist/wurblet/WhereClauseGenerator.java) implements
`CodeGenerator<Object>` and is handed to `WurbletArgumentExpression.toCode(…)`. It does **not** produce SQL — it
produces the *Java source* that appends SQL to a `StringBuilder` named `sql` at runtime, which is what allows a
generated finder to omit optional conditions and still reuse the same code path.

```java
// for the argument   processed:>=
        sql.append(getColumnName(CN_PROCESSED));
        sql.append(Backend.SQL_GREATEROREQUAL_PAR);
```

`generate(Object)` dispatches on the node type:

| Node                        | Emits                                                                                                            |
|-----------------------------|------------------------------------------------------------------------------------------------------------------|
| `WurbletArgumentOperator`   | `sql.append(Backend.SQL_AND);` — the operator's own `Backend` constant, so the keyword stays dialect-controlled. |
| `WurbletArgumentExpression` | A parenthesized recursion: `SQL_LEFT_PARENTHESIS`, the nested code, `SQL_RIGHT_PARENTHESIS`.                     |
| `WurbletArgument`           | The condition itself — see below.                                                                                |
| anything else               | `sql.append(<t>);`                                                                                               |

### Path arguments and the EXISTS clause

When an argument reaches its attribute through a relation path, the condition has to be expressed as a subquery.
The generator emits the `EXISTS` clause **once per group** — for the first argument of the group, the one the
parser gave a non-null `getExistsRelations()` (see
[the parser's grouping rules](../../../../tentackle-wurblets/src/main/docs/wurblet-api.md#wurbletargumentparser)).
It assembles:

1. `EXISTS (` followed by the table names and aliases of every component in the group (via
   [`ComponentInfo`](../../../../tentackle-wurblets/src/main/docs/wurblet-api.md#componentinfo)) and of every
   relation's foreign entity, comma-separated;
2. `WHERE`, then the aggregate-root equations for the components — `id = <component>.rootId`, plus an extra
   `id = id` join when multi-table inheritance puts the attribute in a different table than the root id;
3. one join predicate per relation — for an object relation the local foreign-key column against the foreign
   `CN_ID`, for a list relation the local `CN_ID` against the foreign key column — followed by the additional
   predicates of relations that carry more than one method argument.

The chain deliberately ends on a trailing `.append(Backend.SQL_AND)`, because the argument's own column condition
is appended right after it. The closing parenthesis is emitted separately, when the parser flagged the argument as
`isEndOfExistsClause()`.

### Column conditions

For every column of the attribute's datatype (all of them for a plain `=`, or just the selected one), the
generator emits the column name followed by `createRelopCode(argument)`. How the column name is qualified depends
on the context:

| Situation                               | Emitted                                                                                 |
|-----------------------------------------|-----------------------------------------------------------------------------------------|
| Path argument                           | `<Impl>.CLASSVARIABLES.getColumnName(<Impl>.CN_…)`, or the literal embedded column name |
| `pathAllowed` + multi-table inheritance | `<Impl>.CLASSVARIABLES.getColumnName(CN_…)`                                             |
| `pathAllowed` + PDO                     | `getColumnName(CN_…)`, or the literal column name for an embedded attribute             |
| `pathAllowed` + low-level object        | the bare `CN_…` constant                                                                |
| no `pathAllowed`                        | the bare constant, prefixed with `getColumnPrefix() + ` when embedded                   |

### Optional conditions

`wrapOptional` encloses generated code in `if (<condition>) { … }` and re-indents it. It is applied three times
per argument: around the `EXISTS` preamble and around the closing parenthesis with the *group's* condition (the
`||` of every boolean in the clause, from `createOptionalCondition`), and around the column condition with the
argument's *own* boolean. The result is a clause that appears in the SQL only when at least one of its conditions
is active — with sub-expressions that end up empty removed by `Backend.reduceSql(…)`.

---

## JoinClauseGenerator

[`JoinClauseGenerator`](../java/org/tentackle/persist/wurblet/JoinClauseGenerator.java) is the second
`CodeGenerator<Object>`, used for the **filter of a load join** (`*invoice|date:>=*lines`). It is constructed with
the wurblet and one `Join`, and reached only from `DbModelWurblet.createOptionalWhereForJoin`.

The contrast with `WhereClauseGenerator` is the point:

|                  | `WhereClauseGenerator`                    | `JoinClauseGenerator`                                            |
|------------------|-------------------------------------------|------------------------------------------------------------------|
| Output           | Java code appending to `sql` at runtime   | literal SQL text                                                 |
| Operators        | `Backend.SQL_AND` constants               | the plain words `AND` / `OR`                                     |
| Column reference | `getColumnName(CN_…)` resolved at runtime | `<joinAlias>.<column>` — the alias assigned by `JoinPathFactory` |
| Relop            | `createRelopCode`                         | `createRelopSql`                                                 |

The no-argument `generate()` prefixes the whole filter with `Backend.SQL_AND`, since it is appended to an existing
join condition. Because the text is fixed, a join filter cannot contain optional conditions — `DbModelWurblet.run()`
rejects them explicitly.

---

## DslDeclarationFactory

[`DslDeclarationFactory`](../java/org/tentackle/persist/wurblet/DslDeclarationFactory.java) is not a wurblet at
all — it is a plain factory driven by [`QueryDSL.wrbl`](../wurblets/org/tentackle/persist/wurblet/QueryDSL.wrbl),
producing the constants a [query-DSL](../../../../tentackle-database/src/main/docs/query-dsl.md) query is built
from. Each result is a `record Declaration(String comment, String code)` — the Javadoc text and the Java line,
without indentation.

The constructor rejects an entity that has no table to select from: an embedded one, or an abstract one whose
hierarchy maps to no table. It then seeds the defaults, all of which the `QueryDSL` options override:

| Property         | Default                                                  | Option                         |
|------------------|----------------------------------------------------------|--------------------------------|
| `visibility`     | `private`                                                | `--public`, `--protected`      |
| `constantPrefix` | empty; `getDefaultConstantPrefix()` offers `ENTITYNAME_` | `--prefix[=<prefix>]`          |
| `tableConstant`  | the uppercased entity name                               | `--table=<constant>`           |
| `tableAlias`     | the model's table alias                                  | `--alias=<alias>`, `--noalias` |

`determineAttributes(javaNames)` resolves the named attributes — or every mapped attribute when the list is empty
— and `assertSameTable` rejects an attribute stored in a different table, pointing at the entity that needs its
own block.

`createFieldDeclaration` derives the field's Java type from the attribute's **effective** `DataType`: the boxed
type for primitives (query values are objects), the external type for convertibles. Two cases are special:

- a **generic** Java type such as `Binary<T>` has no class literal, so the field becomes an untyped
  `Field<Object>` and the comment says why;
- a datatype with a **variant** (the large string, for instance) cannot be identified by its class alone, so the
  field is built from `DataTypeFactory.getInstance().get(String.class, "large")` instead of `String.class`.

---

## RemoteIncludes

[`RemoteIncludes`](../java/org/tentackle/persist/wurblet/RemoteIncludes.java) manages the two generated
[TRIP](../../../../tentackle-core/src/main/docs/trip.md) files that make a PDO or operation remotable: the remote
delegate **interface** and its **implementation**. It is constructed by every remote-capable wurblet and does two
things.

**Bootstrapping the files.** All names come from `NamingRules`: the PDO/operation interface (from
`getPdoClassName()`, falling back to the class name), the remote interface and implementation, and their packages.
The target directories are located by stripping the package path off the wurblet's own source directory, so the
delegates always land in the same Maven module as the persistence implementation. If a file does not exist yet, it
is generated from a FreeMarker template — `RemoteInterface.ftl` or `RemoteImplementation.ftl` — taken from the
wurblet property `templateDir`, or from `<projectRoot>/templates/{pdo|operation}`. With neither property set the
whole run terminates.

The template model resolves the *remote super* types, special-casing the framework bases and deriving everything
else through `NamingRules`:

| Super class                   | Remote super interface / implementation               | Package                      |
|-------------------------------|-------------------------------------------------------|------------------------------|
| `AbstractPersistentObject`    | `AbstractPersistentObjectRemoteDelegate` / `…Impl`    | `org.tentackle.persist.trip` |
| `AbstractPersistentOperation` | `AbstractPersistentOperationRemoteDelegate` / `…Impl` | `org.tentackle.persist.trip` |
| `AbstractDbObject`            | `AbstractDbObjectRemoteDelegate` / `…Impl`            | `org.tentackle.dbms.trip`    |
| anything else                 | derived from the super class's own name and package   | derived                      |

For operations the model also records whether the source is an *abstract* operation, detected by matching
`extends <Interface><` against the source text.

**Carrying the generated methods.** `getInterfaceStream()` and `getImplementationStream()` hand out the
`PrintStream`s of two wurbelizer heap files named `<RemoteInterface>/methods` and `<RemoteImplementation>/methods`.
Each query wurblet writes its remote counterpart there, and the remote sources pick it up through their own
`Include` anchors. `discard()` marks both heap files as discarded, so a wurblet reading incomplete content throws
a `WurbelDiscardException` instead of generating from it.

---

## RemoteMethodHelper

[`RemoteMethodHelper`](../java/org/tentackle/persist/wurblet/RemoteMethodHelper.java) backs the `RemoteMethod`
wurblet. Unlike everything else in this package it reads no model: it wraps a `RemoteMethodInfo` produced by the
`tentackle-build-support` annotation processor during the **analyze** run, i.e. the real compiled signature of the
method annotated `@RemoteMethod`.

The constructor records what the method returns (`void`, a PDO/DbObject, a collection of them, or a cursor — a
PDO implies the object flavors), whether it is `static`, and — when the wurblet was given `--this` — appends a
synthetic `this` parameter typed as the enclosing class.

The three parameter-string builders differ in exactly which parameters they drop and how `this` is rendered:

| Method                                 | Skips                | `this` becomes                                  |
|----------------------------------------|----------------------|-------------------------------------------------|
| `getDeclarationParameterString()`      | session parameters   | `<PdoInterface> obj` (varargs keep their `...`) |
| `getInvocationParameterString()`       | session parameters   | `me()` for a PDO, the plain name otherwise      |
| `getRemoteInvocationParameterString()` | the `this` parameter | — (session parameters are kept)                 |

The session parameter is dropped from declarations and local invocations because the remote delegate already
carries the session; it survives in the remote invocation string. `getUpdateDbInParametersStatements()` returns a
`getSession().applyTo(x)` statement for every DbObject or PDO parameter, so objects arriving from a client are
re-bound to the server's session. `getFirstName()`, `isFirstInstanceOfDb()`, `isFirstInstanceOfDomainContext()`
and `getParamCount()` let the template decide how to shape the call, and `getGenericReturnType()` normalizes the
diamond `<>` to the empty string.

---

## How a Select Wurblet Flows Through the Package

```
  @wurblet selectRecent PdoSelectList --remote  processed:>= or processed:=:null  +id  *address
        │
        ▼
  DbModelWurblet.run()
        │  ModelWurblet loads the model and resolves the Entity
        │  WurbletArgumentParser splits: expression | sorting | joins
        │  getJoinPaths() consolidates and normalizes the joins, naming them j_1, j_1_1, …
        ▼
  the template (PdoSelectList.wrbl) calls, in order:
        │
        ├─ buildMethodParameters() ........ Timestamp processed  (the method signature)
        ├─ createStatementId() ............ SELECT_RECENT_STMT
        ├─ genwhere.incl
        │     └─ expression.toCode(new WhereClauseGenerator(this))
        │            └─ per argument: createRelopCode()      → sql.append(Backend.SQL_…)
        ├─ createOrderBy() ................ .append(getColumnName(CN_ID)).append(SQL_SORTASC)
        ├─ createJoins() .................. JoinedSelect<T> js = …addJoin(…)
        │     └─ per filtered join: new JoinClauseGenerator(this, join).generate()
        │            └─ createRelopSql()                     → literal " AND j_1.date>=?"
        ├─ gensetpar.incl
        │     ├─ createJoinSetPars() ...... bind the join value arguments   (first!)
        │     └─ createWhereSetPars() ..... bind the WHERE arguments
        └─ RemoteIncludes.getInterfaceStream() / getImplementationStream()
                                            the matching remote delegate method
```

The binding order is the reason `getMethodArguments()` puts join-filter arguments **before** the expression
arguments: `createJoins()` emits the join predicates into the SQL before the `WHERE` clause, so their `?`
placeholders come first.

---

## Error Handling

The conventions are those of
[the interface-side package](../../../../tentackle-wurblets/src/main/docs/wurblet-api.md#error-handling-conventions).
Specific to this package:

| Condition                                                                        | Reaction                                                              |
|----------------------------------------------------------------------------------|-----------------------------------------------------------------------|
| A relation path where the template does not allow one                            | `WurbelException` — *"relation paths not allowed in wurblet …"*       |
| An optional condition inside a join filter, or in a statement-caching wurblet    | `WurbelException`                                                     |
| A joined non-composite relation that is not serialized, under remoting           | `WurbelException` — it could not be transported                       |
| An array operator no active backend supports                                     | `WurbelException` via `assertSupportedByBackends`                     |
| A backend-specific column count in a `WHERE` clause or a join                    | `WurbelException`                                                     |
| A sort key from a joined entity with no matching join                            | `WurbelException` — *"missing join for sort key"*                     |
| Neither `templateDir` nor `projectRoot` set when a remote file must be generated | `WurbelTerminationException` — aborts the whole run                   |
| A remote heap file whose content is incomplete                                   | `RemoteIncludes.discard()`, so readers raise `WurbelDiscardException` |

---

## Testing

Unlike the interface-side package, most of this one is covered by unit tests:
[`DbModelWurbletTest`](../../test/java/org/tentackle/persist/wurblet/DbModelWurbletTest.java),
[`WhereClauseGeneratorTest`](../../test/java/org/tentackle/persist/wurblet/WhereClauseGeneratorTest.java),
[`JoinClauseGeneratorTest`](../../test/java/org/tentackle/persist/wurblet/JoinClauseGeneratorTest.java),
[`DslDeclarationFactoryTest`](../../test/java/org/tentackle/persist/wurblet/DslDeclarationFactoryTest.java) and
[`RemoteMethodHelperTest`](../../test/java/org/tentackle/persist/wurblet/RemoteMethodHelperTest.java).

They share [`WurbletTestSupport`](../../test/java/org/tentackle/persist/wurblet/WurbletTestSupport.java), which
builds a **real** `Entity` from a model-definition string — covering a single-column type, a multi-column one
(`BMoney`) and a nullable one — rather than mocking the model API, and instantiates the wurblet directly. That
works because everything tested is a pure function of the model plus the wurblet arguments: no `Wurbler`
container and no source file to weave into. It is the shortest path to understanding what any of these generators
actually emits.

---

## Related Documentation

- [Persistence Wurblets](persistence-wurblets.md) — the templates and what they generate.
- [The Wurblet API](../../../../tentackle-wurblets/src/main/docs/wurblet-api.md) — the base classes, the argument
  mini-language and the join model this package consumes.
- [PdoSelectList and PdoSelectUnique](pdo-select.md) — the query language from the user's point of view.
- [Eager Relations](eager-relations.md) — what `createJoins()` and `createEagerJoins()` produce, and how far a
  model-level eager relation cascades.
- [Wurblets — Interface-Level Code Generation](../../../../tentackle-wurblets/src/main/docs/wurblets.md) — the
  interface-side counterpart.
- [The Query DSL](../../../../tentackle-database/src/main/docs/query-dsl.md) — what `DslDeclarationFactory` feeds.
- [TRIP](../../../../tentackle-core/src/main/docs/trip.md) — the remoting protocol behind the generated delegates.
- [Tentackle SQL](../../../../tentackle-sql/src/main/docs/sql.md) — the `DataType`/`Backend` constants every
  generated statement is built from.
- [Model Definition Syntax](../../../../tentackle-model/src/main/docs/model-definition.md) — the model driving it all.
