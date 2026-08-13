# The Wurblet API — `org.tentackle.wurblet` Class Reference

## Overview

[`wurblets.md`](wurblets.md) describes *what* the Tentackle wurblets generate. This document describes the Java
classes that make that generation possible: the package `org.tentackle.wurblet`, shipped in
`tentackle-wurblets`.

The package contains no wurblet templates. It contains the **runtime library the templates compile against**:

- the **base classes** a `.wrbl` template extends (`ModelWurblet`, `DTOWurblet`, `IncludeWurblet`),
- the parser and object model for the **wurblet-argument mini-language** — the compact
  `attr:relop:value`/`+sort`/`*join` expressions that drive the query generators,
- the **join model** that consolidates eager-loading relation paths into named SQL joins,
- a handful of **model-side helpers** (`ComponentInfo`, `AnnotationOption`, `ModelCommentSupport`) and the
  wurbelizer-aware `Model` replacement `TentackleWurbletsModel`.

Everything here is **build-time only**. The module is loaded on the `wurbelizer-maven-plugin`'s classpath and is
never shipped inside a running application. It is packaged with an `Automatic-Module-Name` of
`org.tentackle.wurblet` rather than a `module-info.java`.

### Who consumes these classes

| Consumer | Uses |
|---|---|
| The templates in `tentackle-wurblets/src/main/wurblets` (`AttributeNames`, `ColumnNames`, `Methods`, `Relations`, …) | `ModelWurblet` (via [`header.incl`](../wurblets/org/tentackle/wurblet/header.incl)), `AnnotationOption`, `ModelCommentSupport` |
| [`tentackle-persistence-wurblets`](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md) — `DbModelWurblet`, `WhereClauseGenerator`, `JoinClauseGenerator` and the `Pdo*`/`Db*` templates | the whole argument and join model: `WurbletArgumentParser`, `WurbletArgument`, `WurbletArgumentExpression`, `WurbletArgumentOperator`, `CodeGenerator`, `WurbletRelation`, `Join`, `JoinPath`, `ComponentInfo` |
| Your own project wurblets | anything here — `ModelWurblet` in particular is the documented extension point |

---

## Package Map

```
org.wurbelizer.wurblet.AbstractWurblet                  (wurbelizer)
 │
 ├── IncludeWurblet ......................... base of the Include wurblet
 │
 └── org.wurbelizer.wurblet.AbstractJavaWurblet          (wurbelizer)
      │
      ├── ModelWurblet ...................... base of every model-driven wurblet
      │        │                              (and of DbModelWurblet in persistence-wurblets)
      │        └─ uses ─► ComponentInfo, AnnotationOption, TentackleWurbletsModel
      │
      └── DTOWurblet ........................ base of the DTO wurblet
               └─ inner class Property

  the argument mini-language
  ──────────────────────────
  WurbletArgumentParser ─── builds ──►  WurbletArgumentExpression  ─┐
        │                                       │                  ├─ implements
        │                                       └─ operands ───────┤   WurbletArgumentOperand
        └── creates ──► WurbletArgument ────────────────────────────┘
                            │  type  ──► WurbletArgumentType   (CONDITION / EXTRA / SORT / JOIN)
                            │  operators ─► WurbletArgumentOperator (AND / OR / NOT / ANDNOT / ORNOT)
                            └── path ──► WurbletRelation (Relation + optional filter)

  the join model
  ──────────────
  JoinPathFactory ── createPaths(args) ──► List<JoinPath>
                                             └── elements: List<Join> ──► WurbletRelation
                                             └── paths:    List<JoinPath>   (continuations)

  helpers
  ───────
  CodeGenerator<T>          functional interface used to render expressions
  ModelCommentSupport       static printers for the ModelComment wurblet
  TentackleWurbletsModel    @Service(Model.class) — heap files + deferred load errors
```

| Class | Kind | Role |
|---|---|---|
| [`ModelWurblet`](../java/org/tentackle/wurblet/ModelWurblet.java) | class | Base for all model-driven wurblets: loads the model, locates the target PDO/operation, parses options, provides the template helper toolkit. |
| [`DTOWurblet`](../java/org/tentackle/wurblet/DTOWurblet.java) | class | Base for the `DTO` wurblet: parses the property model (here-doc or `@RecordDTO` record) and the DTO options. |
| [`IncludeWurblet`](../java/org/tentackle/wurblet/IncludeWurblet.java) | class | Base for the `Include` wurblet: parses its four options and deletes the included file on cleanup. |
| [`WurbletArgument`](../java/org/tentackle/wurblet/WurbletArgument.java) | class | One parsed argument of the mini-language: attribute + relop + value, sort key, or load join. |
| [`WurbletArgumentType`](../java/org/tentackle/wurblet/WurbletArgumentType.java) | enum | The four argument kinds and the grammar rules each one permits. |
| [`WurbletArgumentParser`](../java/org/tentackle/wurblet/WurbletArgumentParser.java) | class | Parses a whole argument list into an expression tree plus sorting, extra and join arguments. |
| [`WurbletArgumentExpression`](../java/org/tentackle/wurblet/WurbletArgumentExpression.java) | class | A node of the boolean expression tree: *n* operands joined by *n-1* operators. |
| [`WurbletArgumentOperand`](../java/org/tentackle/wurblet/WurbletArgumentOperand.java) | interface | Marker implemented by `WurbletArgument` and `WurbletArgumentExpression`. |
| [`WurbletArgumentOperator`](../java/org/tentackle/wurblet/WurbletArgumentOperator.java) | enum | `AND`, `OR`, `NOT`, `ANDNOT`, `ORNOT` and their `Backend.SQL_*` constant names. |
| [`WurbletRelation`](../java/org/tentackle/wurblet/WurbletRelation.java) | class | One element of a relation path: a model `Relation` plus an optional parsed filter. |
| [`Join`](../java/org/tentackle/wurblet/Join.java) | class | A single load join — a `WurbletRelation` plus the generated SQL alias. |
| [`JoinPath`](../java/org/tentackle/wurblet/JoinPath.java) | class | A tree of joins: a chain of `Join` elements plus continuation paths. |
| [`JoinPathFactory`](../java/org/tentackle/wurblet/JoinPathFactory.java) | class | `@Service` singleton that consolidates and names join paths. |
| [`ComponentInfo`](../java/org/tentackle/wurblet/ComponentInfo.java) | class | Resolves how to join an aggregate component back to its root (the `rootId` column and its table). |
| [`AnnotationOption`](../java/org/tentackle/wurblet/AnnotationOption.java) | class | Parses the `=`/`+`/`~` modifiers carried by model annotation strings. |
| [`ModelCommentSupport`](../java/org/tentackle/wurblet/ModelCommentSupport.java) | class | Static printers rendering an entity's relationship graph for the `ModelComment` wurblet. |
| [`TentackleWurbletsModel`](../java/org/tentackle/wurblet/TentackleWurbletsModel.java) | class | `Model` implementation understanding heap files and deferring load errors. |
| [`CodeGenerator<T>`](../java/org/tentackle/wurblet/CodeGenerator.java) | interface | Functional interface for rendering nested structures to code. |

---

## ModelWurblet

[`ModelWurblet`](../java/org/tentackle/wurblet/ModelWurblet.java) is the workhorse of the package. Every
model-driven template gets it as its base class through
[`header.incl`](../wurblets/org/tentackle/wurblet/header.incl):

```
@{package org.tentackle.wurblet}@
...
@{extends ModelWurblet}@
@{args}@
```

`DbModelWurblet` in `tentackle-persistence-wurblets` extends it further and adds the SQL-generation helpers used
by the persistence templates.

### Lifecycle: what `run()` does

`ModelWurblet` overrides `run()`. A template's own body executes *after* it, so by the time template code runs,
everything below is already resolved.

```
run()
 ├─ 1. read the wurblet configuration; note the "missingModelOk" flag
 ├─ 2. super.run()                                    (wurbelizer parses the Java source)
 ├─ 3. split container args into optionArgs (leading "--" stripped) and wurbletArgs
 ├─ 4. read the extra properties: model, otherModels, modelName, backends,
 │                                modelDefaults, entityAliases
 ├─ 5. create the model directory if it does not exist
 ├─ 6. register the target Backends on the model's EntityFactory (model validation)
 ├─ 7. apply ModelDefaults and EntityAliases, then load
 │        a) every otherModels directory  (dependency modules — loaded first)
 │        b) the module's own model directory
 ├─ 8. resolve the Entity: a path (contains a file separator) is loaded from a URL,
 │        anything else is looked up by entity name
 └─ 9. compute the effective "remote" flag, then re-throw any deferred load error
          that turns out to be related to this entity
```

Steps 4 and 5 read from the wurbelizer's `PROPSPACE_EXTRA` property space, which the
[Tentackle Maven plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md) populates:

| Property | Meaning |
|---|---|
| `model` | The current Maven module's model directory. Mandatory — a missing property aborts the wurblet. Created if it does not exist. |
| `otherModels` | Whitespace/comma-separated model directories of dependency modules. Loaded **before** the own model. |
| `modelName` | Selects a named `Model` via `ModelManager`; defaults to `Model.getInstance()`. |
| `backends` | The target [backends](../../../../tentackle-sql/src/main/docs/sql.md) used to validate the model and to answer `getBackends()`. |
| `modelDefaults` | Parsed into `ModelDefaults` and applied to the model. |
| `entityAliases` | Parsed into `EntityAliases` and applied to the model. |

> `modelDefaults` and `entityAliases` must be identical for the own model and all `otherModels`, since they are
> set on the single shared `Model` instance before loading.

### Error handling

Model problems are not reported inline into the generated source — that would only clutter it. Instead they abort
the whole wurbel run:

- A `ModelException` while loading a directory becomes a `WurbelTerminationException`.
- If the exception can be associated with a model element and no load error is pending yet, it is **deferred**:
  stashed on the [`TentackleWurbletsModel`](#tentacklewurbletsmodel) instead of thrown, so it can later be
  attributed to the concrete entity that actually causes it. Step 9 of `run()` re-throws it once the entity is
  known and `ModelException.isRelatedTo(entity)` confirms the connection.
- A missing entity aborts too — unless the wurblet's configuration contains `missingModelOk`, in which case
  `getEntity()` simply returns `null` and the template is expected to cope.

### Locating the target: PDO, operation, interface, class

`getPdoClassName()` figures out which entity the anchor belongs to by inspecting the parsed Java source, trying
four strategies in order and caching the outcome:

| # | Source evidence | Result |
|---|---|---|
| 1 | A `@DomainObjectService(Xyz.class)` or `@PersistentObjectService(Xyz.class)` annotation | `pdoClassName = Xyz`, `isPdo = true` |
| 2 | A `@DomainOperationService(Xyz.class)` or `@PersistentOperationService(Xyz.class)` annotation | `pdoClassName = Xyz`, `isOperation = true` (**`isPdo` stays `false`**) |
| 3 | An interface `extends SomePersistentObject<Xyz>` — the first `extends` clause's type parameter | `pdoClassName = Xyz`, `isPdo = true`, `isInterface = true` |
| 4 | A class definition, e.g. `extends AbstractPersistentObject<Adresse, AdressePersistenceImpl>` or a generified `<T extends UmzugsListe<T>, …>` | the first type argument / the bound of `T`, `isPdo = true` |
| 5 | Fallback: the interface name itself (manually implemented PDOs, TT1 migrations) | the interface name; `isPdo` remains `false` |

If none matches, a `WurbelException` is thrown. Two consequences are worth remembering when writing a template:

- **Operations are not PDOs here.** For an operation, `isOperation()` is `true` while `isPdo()` is `false`.
- **`isOperation()` and `isInterface()` are side effects.** Both fields are only assigned inside
  `getPdoClassName()`, so they are meaningful only after `getPdoClassName()` or `isPdo()` has been called.
  Note also that `isInterface()` **overrides** `AbstractJavaWurblet.isInterface()`: it reports "the PDO class was
  derived from an interface `extends` clause" (strategy 3), not the wurbelizer's plain "this source file is an
  interface". Use `isGenerified()` — which inspects the raw class definition — when you need to know whether the
  surrounding class uses generics (abstract inheritable classes do; final concrete PDO classes must not, or the
  generated code will not compile).

### Options

Arguments starting with `--` become *options* (stored without the dashes), everything else stays a positional
*wurblet argument*.

```java
List<String> getArgs()          // everything, as passed
List<String> getOptionArgs()    // options, "--" stripped
List<String> getWurbletArgs()   // positional arguments
String       getOption(String)  // "" for a flag, the value for --opt=value, null if absent
```

Options understood by `ModelWurblet` itself:

| Option | Effect |
|---|---|
| `--method=<name>` | Name of the generated method. Defaults to the wurblet tag (`getGuardName()`), see `getMethodName()`. |
| `--model=<mapping>` | The entity model to use. If it contains a file separator it is treated as a path and loaded from that URL; otherwise it is an entity name. Defaults to `getPdoClassName()`. |
| `--remote` / `--noremote` | Force remoting on/off. Otherwise taken from the entity's options, falling back to `ModelDefaults`. |

### The template helper toolkit

Everything below is `public` and meant to be called from inside a `.wrbl` Java block.

**Model access**

| Method | Purpose |
|---|---|
| `getEntity()` | The resolved `Entity` (may be `null` under `missingModelOk`). |
| `getModelDirName()`, `getModelFileName()`, `getModelDefaults()` | The model context. |
| `getBackends()` | The target backends — never `null`, possibly empty. |
| `isPartOfInheritanceHierarchy()` | True when the entity's top super-entity is abstract. |
| `isMuteOptionSet(Entity)` | True if at least one attribute carries `[MUTE]` and is not already excluded by `noConstant`/`noDeclare`/`fromSuper`. |
| `orderByInheritanceLevelAndClassId(List<Entity>)` | Sorts in place by `ordinal`, then `classId`; returns the same list. |
| `getEmbeddedTableAttributes()` | The embedded attributes among the entity's table attributes. |

**Names and types**

| Method | Purpose |
|---|---|
| `getEffectiveDataType(Attribute)` | The attribute's effective `DataType`, resolving `ConvertibleType`s. Wraps `ModelException` into `WurbelException`. |
| `getColumnName(Attribute, int columnIndex)` | The column name; `columnIndex < 0` means "no suffix". |
| `getColumnNameConstant(Attribute, int columnIndex)` | The `CN_…` constant name. Rejects backend-specific types when a column index is given, because their column count varies per backend. |
| `getNonPrimitiveJavaType(Attribute)` | The boxed Java type. |
| `deriveClassNameForEntity(Entity)` | Applies this class's naming pattern to another entity: with `getClassName() == "MyFirmaPersistenceImpl"` and entity `Firma`, `deriveClassNameForEntity(Kontakt)` yields `"MyKontaktPersistenceImpl"`. Throws if the current class name does not contain the entity name. |
| `determinePackageName(String simpleName)` | Scans the source's `import` statements for the package of a simple class name, defaulting to the current package. Costly, and backed by a **static cache keyed by the simple name alone** — so use it only when the package cannot be derived by rule. |
| `isIdAttribute`, `isSerialAttribute`, `isIdOrSerialAttribute`, `isAttributeDerived` | Attribute classification shortcuts. |

**Relation method names**

| Method | Produces |
|---|---|
| `createRelationSelectMethodName(Relation)` | `select` + `By…` for list relations (explicit method name, or the concatenated foreign attribute names), or `selectCached` for cached object relations. |
| `createListRelationDeleteMethodName(Relation)` | `deleteBy` + the explicit method name or the concatenated foreign attribute names. |
| `createDeclaredArgsForSelectOrDeleteMethod(Relation)` | The comma-separated `Type name` parameter list derived from the relation's method arguments. |

**Code and comment emission**

| Method | Purpose |
|---|---|
| `createAccessorCode(Entity, String path, boolean createSetter)` | Turns a dotted path into chained accessor code, e.g. `invoice.customer.name` → `getInvoice().getCustomer().getName`. A `#` selects a single column of a multi-column datatype (`period#from`). The trailing parentheses are **not** emitted, so the caller appends `()` or `(value)`. Multi-column types are immutable, so a setter path with `#` is rejected. |
| `createMultiLineComment(String lead, String comment)` | Renders a multi-line comment into a Javadoc block, prefixing each line with `<br>` and the given lead. |
| `createDocTagComment(String \| Attribute \| Relation)` | Normalizes a comment for `@param`/`@return`: makes it start with a lowercase `the …`. Boolean attributes are returned unchanged; an all-caps `THE ` prefix is left alone. |
| `firstToLowerCase(String)` | Lowercases the first character *only* when the second is not uppercase — so `ID` and `URL` survive intact. |
| `appendCommaSeparated`, `prependCommaSeparated` | Build comma-separated lists in a `StringBuilder`. |

**Validation and structure**

| Method | Purpose |
|---|---|
| `assertSupportedByBackends(String feature, Function<Backend,Boolean> validator)` | Runs the validator against every active backend. A `false` result or a thrown `RuntimeException` marks that backend as unsupported; if any fail, a single `WurbelException` naming all of them (with their messages) aborts the build. This is how a template refuses to generate code for a feature the project's databases cannot execute. |
| `createComponentInfo(Entity component)` | Builds a [`ComponentInfo`](#componentinfo) for joining one of the entity's components. |
| `getAnnotationOptions(List<String> annotations, String annotationType)` | Filters the model's annotation strings by (simple) annotation type and wraps each in an [`AnnotationOption`](#annotationoption). |

---

## DTOWurblet

[`DTOWurblet`](../java/org/tentackle/wurblet/DTOWurblet.java) backs the `DTO` wurblet. Unlike the model wurblets
it extends `AbstractJavaWurblet` **directly** — a DTO is not tied to a persistent entity, so no model is loaded
and none of the `ModelWurblet` machinery applies. It parses its own options straight from
`getContainer().getArgs()`.

The generation semantics are documented in [The DTO Wurblet and `@RecordDTO`](dto-wurblet.md). What follows is
the class's own contract.

### Two input modes

`run()` branches on `isRecord()`:

**Record mode** — the source is a Java `record` annotated with `@RecordDTO`. The record's components were captured
at annotation-processing time by `tentackle-build-support`; the wurblet reads them back from the analyze file
`<package>/<Class>/` + `RecordDTOInfo.INFO_FILE_NAME` and turns each `RecordDTOInfoParameter` into a `Property`.
If that file cannot be read, the reaction depends on whether a compile-error log exists in the analyze directory:

- error log present → `WurbelDiscardException` (the source did not compile; nothing useful can be generated yet),
- no error log → `WurbelException` hinting at a missing `@RecordDTO` annotation.

**Property-model mode** — the first non-`--` argument names the file holding the model, normally a here-document
("heap file") embedded in the DTO source's leading comment. Each line is `<type> <name> <comment>`:

- lines starting with `#` are comments, blank lines are skipped;
- a line starting with `[` defines **global annotations** applied to every property;
- a line starting with the wurbelizer origin-info lead records the originating source file and line, so parse
  errors can point back into the real `.java` file rather than the extracted heap file;
- multi-word types are re-joined by tracking the generic nesting level, so `Map<Long, String>` survives being
  split on whitespace.

The superclass name is picked up via `getSuperClassName()` when the DTO extends another class (used for the `^`
inheritance prefix).

### The `Property` inner class

Each property's *prefix* is everything before the first character that can start a Java identifier, so prefixes
combine freely and order does not matter (`+!Foo bar` is the same as `!+Foo bar`).

| Prefix | Field set | Meaning |
|---|---|---|
| `^` | `inherited` | Passed up to the super class's constructor. |
| `=` | `mutable` | A setter is generated. |
| `~` | `mutable` + `unserialized` | Mutable and `transient`. |
| `!` | `required` | Builder mode only: `build()` fails if never set. Implies immutable. |
| `+` | `withFrom` | Also generate a `fromX(…)`/`withX(…)` copy method. |

`^`, `=`, `~` and `!` are evaluated as an if/else chain **in that order**, so a property carrying more than one of
them keeps the first: `=~Foo bar` is mutable but *not* transient. `+` is independent and combines with any of them.

The comment part may carry options in square brackets, parsed by `OptionParser`:

- `[@Anno]`, `[@Anno(x=1)]` → per-property annotations,
- `[-@Anno]` / `[!@Anno]` → cancel a global annotation for this property,
- `[public]`, `[protected]`, `[package]`, `[private]` → the accessor `AccessScope` (default `PUBLIC`);
  anything else is rejected.

Global annotations are merged in afterwards: `addGlobalAnnotation` skips a global annotation whose name the
property already declares (or explicitly cancels), and `cleanupAnnotations` then drops the `-@`/`!@` markers so
they never reach the generated source.

Derived names the template uses directly:

| Method | Yields |
|---|---|
| `getGetterName()` | `isX` for `boolean`, else `getX`. |
| `getSetterName()` | `setX`. |
| `getFromName()` | `withX` when `--with` was given, else `fromX`. |
| `getBuilderName()` | The builder method name — `builderPrefix` + `X`, or the bare property name when no prefix is configured. |
| `getConstant()` | `PN_<NAME>` — the property-name constant emitted under `--names`. |
| `isPrimitive()` | Heuristic: the type's first character is lowercase. |
| `getHashCodeInvocation()` | `Double.hashCode(x)` / `Float.hashCode(x)` / `Long.hashCode(x)` / the bare field for other primitives / `Objects.hashCode(x)` for references. |

### Options

| Option | Field | Notes |
|---|---|---|
| `--builder`, `--builder[@Anno]` | `withBuilder`, `builderAnnotation` | The bracketed form attaches an annotation to the generated builder. |
| `--builderScope=<scope>` | `builderScope` | `AccessScope` of the builder's constructor; defaults to `PROTECTED`. Implies `--builder`. |
| `--builderPrefix=<prefix>` | `builderPrefix` | Prefix for builder setter methods. Implies `--builder`. |
| `--from` / `--with` | `withFrom`, `asWithers` | Force copy factories for all properties; `--with` names them `withX` instead of `fromX`. |
| `--equals`, `--hashCode` | `withEquals`, `withHashCode` | Rejected for records (which get them for free). |
| `--validate[=<code>]` | `validate` | Validate after construction. Defaults to `ValidationUtilities.getInstance().validate(this)`; rejected for records. See [validation](../../../../tentackle-core/src/main/docs/validation.md). |
| `--names` | `withNames` | Emit the `PN_*` constants. |
| `--canonical` | `canonical` | Emit `@Canonical*` annotations for [TRIP](../../../../tentackle-core/src/main/docs/trip.md). |
| `--nofinal` | `noFinal` | Serialization-friendly non-final fields (JEP-500). |
| `--nott` | `nott` | No Tentackle dependency in the generated code. **Mutually exclusive with `--canonical`.** |

`needConstructor` is set as soon as any property is immutable.

### Validation performed at parse time

Property-model mode rejects, with the originating source file and line number:

- an empty or malformed Java type (must start with a letter),
- a property name that is not a valid Java identifier,
- a `^` inherited property in builder mode,
- a `!` required property outside builder mode,
- a duplicate property name.

---

## IncludeWurblet

[`IncludeWurblet`](../java/org/tentackle/wurblet/IncludeWurblet.java) is the smallest class in the package. It
extends `AbstractWurblet` (not the Java-aware subclass — an include has nothing to do with Java structure) and
exists only because `run()` and `cleanup()` need overriding.

`run()` collects the flags `--comment`, `--missingok`, `--translate` and `--delete` into protected fields the
template reads, and takes the first positional argument as the file name (a missing file name is a usage error).
`cleanup()` deletes the included file afterwards when `--delete` was given, logging the deletion.

---

## The Wurblet-Argument Mini-Language

This is the compact query notation used by the select/update/delete generators:

```
@wurblet selectUpTo PdoSelectList --remote   processed:=:null or processed:>=   +id     *address
                                             └──────── expression ──────────┘ └ sort ┘  └ join ┘
```

Four classes carry the grammar: `WurbletArgumentType` defines what each kind of argument may contain,
`WurbletArgument` parses one argument, `WurbletArgumentParser` parses the whole list into a tree, and
`WurbletArgumentExpression` is that tree.

### WurbletArgumentType

The enum is a table of grammar rules. Each constant declares which parts an argument of that kind may carry:

| Type | Produced by | Path | Attribute | `[name]` | `:relop` | `?boolean` |
|---|---|---|---|---|---|---|
| `CONDITION` | the default inside the expression | optional | **required** | yes | yes | yes |
| `EXTRA` | an argument after `\|` when argument grouping is on (typically a SQL `UPDATE` assignment) | not allowed | **required** | yes | no | no |
| `SORT` | a leading `+` / `-` (or a bare argument after the expression when grouping is off) | optional | **required** | no | no | no |
| `JOIN` | a leading `*` | **required** | not allowed | no | no | no |

The predicates are `isPathOptional/Required/Allowed`, `isAttributeOptional/Required/Allowed`, `isNameOptional`,
`isRelopOptional` and `isConditionOptional`; `WurbletArgument`'s constructor consults them to produce precise
error messages such as *"relops not allowed for SORT-argument '…'"*.

> Note a discrepancy worth knowing: `SORT` declares `pathOptional = true`, so a sort key reached through a
> relation path parses successfully, even though the class comment on `WurbletArgument` describes relation paths
> as illegal for sorting.

### WurbletArgument

The [full grammar](../java/org/tentackle/wurblet/WurbletArgument.java) is documented in the class's Javadoc:

```
[*|+|-] [Relation|.Entity[.Relation...]] [Attribute[#column][[name]][?boolean]] [:relop[:value|#value]]
```

The constructor parses it strictly left to right:

1. **Type prefix.** `*` → `JOIN`, `+`/`-` → `SORT` (with `SortType.ASC`/`DESC`), otherwise `CONDITION` with the
   relop preset to `=` — or, once the expression is finished, `EXTRA` (grouping on) / `SORT` ascending
   (grouping off, a convenience so trailing sort keys need no `+`).
2. **Relop and value.** Everything after the first `:`. A second `:` introduces a bind value, a `#` introduces a
   literal value spliced straight into the SQL text. The relop is then normalized:

   | Written | Becomes |
   |---|---|
   | `:null` / `:notnull` | `IS NULL` / `IS NOT NULL` (as a literal, no bind parameter) |
   | `:like` / `:notlike` | `LIKE` / `NOT LIKE` |
   | `:in` / `:notin` | array operators `IN (?)` / `NOT IN (?)` — no other operator may be combined |
   | a relop ending in `any` / `all` | array operators `ANY(?)` / `ALL(?)`, keeping the leading comparison (`:>=any`), defaulting to `=` when none is given |

3. **Optional-condition marker.** `?<name>` names the boolean method argument that switches the condition on.
   The name must be a valid Java identifier. A `?` appearing *after* the relop is rejected explicitly rather than
   silently absorbed into the value.
4. **Method-argument name.** `[name]` overrides the default argument name.
5. **Relation path.** Dots separate relations (asterisks for joins). A **leading dot** names an aggregate
   component of a root entity directly (`.InvoiceLine.currencyId`); the path to it is resolved from the
   component's composite paths and rejected if the entity is not actually a root, or the named entity is not
   actually its component. Embedding relations are collected separately into the *embedding path* and are
   materialized as prefixed, embedded copies of the relation via `Relation.createEmbedded(…)`.
6. **Attribute and column.** The final token resolves to an `Attribute` of the entity the path arrived at. An
   embedded attribute is rebuilt with its full path name and prefixed column name. A `#suffix` selects a single
   column of a multi-column datatype (matched against column suffix or alias, with or without leading
   underscore); `#*` explicitly means "all columns". If a multi-column type is used with anything other than a
   plain `=` and no column was named, the type must declare exactly one sortable column — otherwise the argument
   is rejected as ambiguous.
7. **Consistency checks.** Backend-specific-column-count datatypes are rejected outright. Array operators reject
   literal and simple values and demand an explicit column for multi-column types. Literal values are converted
   via `DataType.toLiteral(…)` and require the datatype to support literals; quoted values are converted to code
   via `DataType.valueOfLiteralToCode(…)`.

Beyond the parsed state (`getAttribute()`, `getDataType()`, `getColumnIndex()`, `getRelop()`, `getValue()`,
`getSortType()`, `isArray()`, `getArrayOperator()`, `isOptional()`, `getOptionalName()`, `getJdbcValue()`, …) the
class carries two derived views the generators rely on:

**Embedding helpers.** `getEmbeddingPath()` returns the relation chain into an embedded entity;
`getEmbeddingPrefixCount()`, `getEmbeddingColumnPrefix()` and `getEmbeddingGetterPrefix()` produce the column and
getter prefixes needed for *nested* embeddings — the innermost embedding relation is excluded, since it is
already accounted for by the attribute itself.

**Path compaction.** `getExpressionRelations()` walks the relation path and, for a root entity, folds a leading
chain of single-argument composite relations into a single `component` (`getComponent()`), returning only the
relations that remain. This is what lets a condition on a deeply nested component be generated as one join
against the component's table using its `rootId` rather than a chain of joins — see
[`root-columns.md`](../../../../tentackle-pdo/src/main/docs/root-columns.md) and
[`ComponentInfo`](#componentinfo).

**EXISTS bookkeeping.** `existsRelations`, `existsComponents`, `existsArguments` and `endOfExistsClause` are not
parsed — the parser fills them in (see below) so that several path conditions can share one SQL `EXISTS` clause.

### WurbletArgumentParser

[`WurbletArgumentParser`](../java/org/tentackle/wurblet/WurbletArgumentParser.java) turns the raw argument list
into four buckets plus an expression tree.

```
                    ┌─ getExpression()          the boolean tree (WurbletArgumentExpression)
  parse(args) ──────┼─ getExpressionArguments() flat list of the arguments inside that tree
                    ├─ getExtraArguments()      arguments after '|' (grouping enabled)
                    ├─ getSortingArguments()    + / - keys
                    └─ getJoinArguments()       * load joins  ──► getJoinPaths()

  getMethodArguments() = extra + expression      (in that order)
  getAllArguments()    = method + sorting + join
```

**Where the expression ends.** Implicitly at the first top-level sorting or join argument, or explicitly at a
top-level `|`. A sorting key or join inside parentheses is a hard error.

**Tokenizing.** `splitArg` splits each raw argument around `(`, `)` and `|` so that `(foo` and `bar)` work
without spaces. Braces may be escaped with a backslash (the backslash itself doubles), and an empty `()` pair is
deliberately *not* split — so parentheses appearing inside a value survive.

**Operators.** `WurbletArgumentOperator.toInternal` recognizes `and`, `or`, `not` case-insensitively. `AND`
followed by `NOT` collapses into `ANDNOT`, `OR` followed by `NOT` into `ORNOT`; any other doubled operator is an
error. A missing operator defaults to `AND`. `NOT` may only start an expression or precede a nested expression.

**Grouping into EXISTS clauses.** As the parser walks the operands it accumulates the relation paths of
consecutive `AND`-ed path arguments into one shared group: the first such argument receives the group's
`existsRelations`/`existsComponents` sets, every member gets the shared `existsArguments` list, and the last one
is flagged `endOfExistsClause`. Any parenthesis, any `|`, and any operator other than `AND` closes the group.
`WhereClauseGenerator` in `tentackle-persistence-wurblets` uses exactly this to emit one `EXISTS (…)` subquery
instead of one per condition.

**Post-checks.** `verifyOptionalArguments()` rejects a `?boolean` name that collides with a real method argument
name. `isWithOptionalArguments()` reports whether the generated finder needs the one-shot prepared-statement
treatment described in
[Optional conditions](../../../../tentackle-persistence-wurblets/src/main/docs/pdo-select.md#optional-conditions-qbf-style-finders).

`createArgument(...)` is public and assigns each argument its 1-based `getIndex()`; `getJoinPaths()` lazily
delegates to `JoinPathFactory`.

The parser is the one class in the package with its own unit test,
[`WurbletArgumentParserTest`](../../test/java/org/tentackle/wurblet/WurbletArgumentParserTest.java) — a good
place to look for worked examples of the grammar.

### WurbletArgumentExpression, WurbletArgumentOperand, WurbletArgumentOperator

`WurbletArgumentExpression` is a node holding *n* operands and *n-1* operators (or *n* operators when the
expression opens with a `NOT`). Both `WurbletArgument` and `WurbletArgumentExpression` implement the marker
interface `WurbletArgumentOperand`, so nesting is uniform.

- `addOperand(operator, operand)` enforces the structural rules (no operator other than `NOT` at the start of an
  expression; `NOT` must be followed by a nested expression) and returns the *effective* operator — `null` for
  the first operand, `AND` when none was given.
- `needParenthesesAfterAndOperator()` reports whether the node contains `OR`/`ORNOT` and therefore needs
  parentheses when embedded after an `AND`.
- `getMergedPaths()` consolidates the path arguments of this node into `JoinPath`s — but returns an empty list
  when the node contains `OR`, since alternatives cannot share one `EXISTS` clause. (Public API; no generator in
  the reactor currently calls it.)
- `toCode(CodeGenerator<Object>)` walks operands and operators in order, delegating every element's rendering to
  the supplied generator. `toString()` uses it with a generator that renders plain infix text with parentheses —
  handy when debugging a template.

`WurbletArgumentOperator` carries both the human-readable text (`AND`, `OR`, `NOT`, `AND NOT`, `OR NOT`) and the
name of the matching `Backend` constant (`SQL_AND`, `SQL_OR`, …) that the generated Java code references, so the
emitted SQL keywords stay backend-controlled.

`CodeGenerator<T>` is the functional interface used for that rendering: `String generate(T t) throws
WurbelException`. `WhereClauseGenerator` and `JoinClauseGenerator` in the persistence-wurblets module are its
real implementations.

### WurbletRelation

One element of a relation path: the model `Relation` plus an optional **filter** — the expression after a
`|` inside a load join, as in `*invoice|date:>=*lines`. When a filter is present the constructor immediately
tokenizes it with the wurbelizer's `ArgScanner` (so a multi-argument filter can be quoted) and parses it with a
nested `WurbletArgumentParser` bound to the relation's *foreign* entity; the result is available via
`getParser()`.

> `equals`/`hashCode` consider **only the relation**, not the filter. Two joins over the same relation with
> different filters therefore compare equal, which is what makes join consolidation collapse them.

Filtered load joins return PDOs that are automatically made immutable, since a filtered join may deliver
incomplete objects that must not be written back.

---

## The Join Model

Load joins (`*relation*subrelation`) start life as `JOIN`-typed `WurbletArgument`s. Turning a set of them into
SQL requires consolidating shared prefixes and assigning table aliases — that is what these three classes do.

### Join

A single join: the `WurbletRelation` it wraps, the `WurbletArgument` it came from, and a mutable `name` — the SQL
alias assigned during naming. Identity is defined by the wrapped relation alone.

### JoinPath

`JoinPath` implements `Path<JoinPath, Join>` from `tentackle-common`. It is a tree:

```
JoinPath
 ├── elements : List<Join>       a chain of joins sharing one trunk
 └── paths    : List<JoinPath>   continuations branching off the end of that trunk
```

| Method | Purpose |
|---|---|
| `isFiltered(boolean componentsOnly)` | Whether any join in this path's trunk carries a filter. With `componentsOnly`, scanning stops at the first non-composite relation. |
| `findJoin(Entity component)` | Locates the join whose foreign entity is the given component, searching the trunk and all sub-paths. Throws a `WurbelException` if the component is reachable through more than one path. |
| `findJoin(List<WurbletRelation> path)` | Locates the join matching a concrete relation path, descending into sub-paths for the remainder. Returns `null` when the path does not exist here. |
| `normalize()` | Rewrites the tree so every node's trunk holds exactly **one** element, pushing trailing elements into nested single-element paths. Applied recursively, depth first. |

### JoinPathFactory

An SPI singleton (`@Service(JoinPathFactory.class)`, obtained through `ServiceFactory` — see
[services.md](../../../../tentackle-common/src/main/docs/services.md)), so a project can substitute its own
implementation. Besides the two `PathFactory` `create` methods it provides:

```java
List<JoinPath> createPaths(List<WurbletArgument> arguments)
```

which converts each path argument into a flat `JoinPath`, **merges** paths sharing a leading segment into one
trunk with child paths (inherited from `PathFactory`), and finally **names** every join. Naming is positional and
deterministic:

- top-level paths get `j_1`, `j_2`, …;
- a trunk with a single element takes the path's own prefix;
- a trunk with several elements numbers them `j_1_1`, `j_1_2`, …;
- sub-paths continue from the last element's name: `j_1_2_1`, and so on.

These names become the SQL table aliases in the generated `LEFT OUTER JOIN` clauses — see
[Eager Relations](../../../../tentackle-persistence-wurblets/src/main/docs/eager-relations.md).

---

## Model-Side Helpers

### ComponentInfo

Created via `ModelWurblet.createComponentInfo(Entity)`. Given a component of the wurblet's entity, it works out
**how to join that component back to its aggregate root**, which is not uniform: depending on inheritance, the
`rootId` may live on the component's own table, on a super-entity's table, or in an explicitly provided
`rootId` column.

The constructor resolves:

| Accessor | Meaning |
|---|---|
| `getJoinedEntity()` | The right-hand side of the join. |
| `getRootIdColumnName()` | The Java expression naming the column that holds the root id — either `<Impl>.CN_<ROOTATTR>` when some ancestor supplies a root attribute, or the plain `CN_ROOTID` constant. |
| `getRootIdClassName()` | The implementation class that declares that constant, derived with `deriveClassNameForEntity(…)`. |
| `isExtraJoinNecessary()` / `getExtraClassName()` | True when multi-table inheritance puts the joined attribute in a different table than the root id, so an extra join by `id` is required. |

For a `PLAIN` (single-table) hierarchy the entity itself is the top entity; otherwise the search starts at
`getTopSuperEntity()`. Walking upwards stops as soon as an entity either provides an explicit `rootId` column or
declares a root attribute. See [`root-columns.md`](../../../../tentackle-pdo/src/main/docs/root-columns.md) for
the concepts.

### AnnotationOption

Model attributes may carry annotations with a single-character modifier right after the `@`. `AnnotationOption`
strips those modifiers (up to two of them) and exposes the result:

| Modifier | Predicate | Meaning |
|---|---|---|
| `=` | `isSetterOnly()` | The annotation applies to the setter only. |
| `+` | `isSetterAndGetter()` | The annotation applies to both accessors. |
| `~` | `isHidden()` | Applies to the implementation layer only, not to the interface. |

`getAnnotation()` returns the cleaned annotation string, `getAnnotationType()` the type name without the `@` and
without parameters (lazily computed). `ModelWurblet.getAnnotationOptions(annotations, type)` filters a model's
annotation list by type, comparing simple names so both `@Bindable` and `@org.tentackle.bind.Bindable` match.
Used by `Methods`, `Relations`, `MethodsImpl` and `PdoRelations`.

### ModelCommentSupport

A static utility class (private constructor) holding the rendering logic for the `ModelComment` wurblet, which
emits a Javadoc block describing how an entity is embedded in the model. Every method writes to a `PrintStream`.

| Method | Renders |
|---|---|
| `printVia(relation, out)` | How the link is established: ` embedded`, or ` via <attribute>` / ` via <Entity>.<attribute>`, with additional method arguments joined by ` & `. |
| `printReferencedBy(entity, relation, indent, out)` | An incoming reference: the referencing entity, its root entities, `deeply` for deep references, `composite`, the alias, and the cardinality tag `[1:1]`, `[1:N]` or `[N:M]`. |
| `printComponents(allRelations, compositeRelations, subEntities, indent, out)` | The aggregate tree, recursively: `+` for components, `^` for sub-entities. The `allRelations` set breaks recursion loops; an already-printed composite is abbreviated with a trailing `...`. |
| `printSubEntities(subEntities, indent, out)` | The inheritance tree below an entity. |
| `printNonCompositeRelations(entity, relations, rootsFromForeignEntity, indent, out)` | Outgoing non-composite relations, sorted by foreign entity name, annotated with roots, `from <SubEntity>`, `reversed`, `deeply` and the cardinality tag. |

Sorting by name throughout keeps the generated comment stable across builds, so regeneration produces no
spurious diffs.

### TentackleWurbletsModel

Declared `@Service(Model.class)`, so it **replaces** `ModelImpl` as the model implementation whenever
`tentackle-wurblets` is on the classpath. It adds two things the wurbelizer needs:

**Heap files.** `createReader(URL)` intercepts `file:` URLs whose path starts with a dot and opens them through
`WurbelHelper` instead of the normal file reader. Those are the here-documents the wurbelizer extracts from
source comments — including the model definitions themselves. The constructor also copies the wurbelizer's
encoding charset into Tentackle's `Settings`, so both sides read the same bytes the same way.

**Deferred load errors.** `getLoadingException()`/`setLoadingException()` hold the first `WurbelException` from
loading the model as a whole. `ModelWurblet.run()` uses this to postpone reporting a model error until it reaches
the entity the error actually relates to, so the developer sees the failure attributed to the right source file
rather than to whichever file happened to be wurbled first.

---

## Error-Handling Conventions

Three wurbelizer exception types are used deliberately and mean different things:

| Exception | Thrown when | Effect |
|---|---|---|
| `WurbelException` | A normal generation error: a malformed argument, an unknown attribute, an unsupported backend feature. | Fails this wurblet. |
| `WurbelTerminationException` | The model itself is broken (`ModelWurblet.run()`). | Aborts the entire wurbel run, so a single model error does not scatter follow-up errors across every generated file. |
| `WurbelDiscardException` | `DTOWurblet` in record mode when the analyze info is missing *and* a compile-error log exists. | The source did not compile; the generated region is discarded rather than replaced with garbage. |

`ModelException`s from the model API are consistently wrapped into `WurbelException` with a message naming the
offending attribute, relation or datatype.

---

## Extending the Package

- **A custom model wurblet.** Write a `.wrbl` that starts with `@{extends ModelWurblet}@` (or include
  [`header.incl`](../wurblets/org/tentackle/wurblet/header.incl)) and use the helper toolkit above. Declare
  `@{phase 2}` if your template must see the output of phase-1 wurblets in the same file.
- **A custom base class.** Extend `ModelWurblet` in Java, as `DbModelWurblet` does, and point `@{extends …}@` at
  it. Remember to call `super.run()`.
- **A custom join layout.** Provide your own `@Service(JoinPathFactory.class)` implementation to change how join
  paths are merged or how aliases are named.
- **A custom model implementation.** `TentackleWurbletsModel` shows the pattern: extend `ModelImpl` and register
  it with `@Service(Model.class)`.

---

## Related Documentation

- [Tentackle Wurblets — Interface-Level Code Generation](wurblets.md) — the templates and what they generate.
- [The DTO Wurblet and `@RecordDTO`](dto-wurblet.md) — the `DTO` wurblet in depth.
- [The Persistence Wurblet API](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblet-api.md)
  — the companion class reference: `DbModelWurblet` and the generators that turn the classes documented here into SQL.
- [Persistence Wurblets](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md) — the
  implementation-side generators that consume the argument and join model.
- [PdoSelectList and PdoSelectUnique](../../../../tentackle-persistence-wurblets/src/main/docs/pdo-select.md) —
  the mini-language from the user's point of view.
- [Eager Relations](../../../../tentackle-persistence-wurblets/src/main/docs/eager-relations.md) — what the join
  model produces.
- [Tentackle Model](../../../../tentackle-model/src/main/docs/tentackle-model.md) and
  [Model Definition Syntax](../../../../tentackle-model/src/main/docs/model-definition.md) — the API and DSL these
  classes read.
- [Tentackle SQL](../../../../tentackle-sql/src/main/docs/sql.md) — `DataType` and `Backend`.
- [Wurbelizer — How it works](https://wurbelizer.org/how-it-works/) — `AbstractWurblet`, `AbstractJavaWurblet`,
  the `Wurbler` runtime and guarded regions.
