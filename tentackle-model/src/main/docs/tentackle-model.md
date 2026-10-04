# Tentackle Model — the `tentackle-model` Module

## Overview

`tentackle-model` is the **build-time model API** of Tentackle. It turns the entity
[model definitions](model-definition.md) — the DSL embedded in PDO interface sources — into a navigable,
validated, in-memory object graph (`Entity`, `Attribute`, `Relation`, `Index`, `ForeignKey`, …) and offers the
services that read, validate, pretty-print, and migrate that graph.

It is the **single source of truth** every code- and schema generator reads from. The
[wurblets](../../../../tentackle-wurblets/src/main/docs/wurblets.md) (interface side) and
[persistence-wurblets](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md)
(implementation side) consume it to generate Java; the
[SQL Maven plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) consumes it
to generate and migrate DDL; the PDO-wizard and PDO-browser plugins consume it to display and edit entities.
Because they all read the same model, the generated interfaces, implementations, and the database schema can never
disagree about names, sizes, or types.

> This module is primarily a **build-time** dependency. It is on the classpath of the Wurbelizer and Tentackle
> Maven plugins, not normally inside the running application. It `requires transitive org.tentackle.sql` for the
> `DataType` and `Backend` abstractions used to resolve column types and validate against the target databases.

The DSL *grammar* this module parses is documented separately in
[Model Definition Syntax](model-definition.md). This document describes the *module* — its Java API, its
lifecycle, and how the wurblets drive it.

---

## Where the Model Comes From

A model definition lives in a Java block comment (`@> … @<`) inside a PDO interface. During the build the
Wurbelizer preprocessor extracts each such block, expands its `$variables`, and writes the resulting model source
into a file under the module's model directory (`target/wurbel/...`, configured as the wurblet `model` property).

To load the model back from a jar or the classpath after the build, the
[SQL Maven plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) can dump the
model sources as resources (`dumpModelSource`) together with a `META-INF/MODEL-INDEX.LIST` listing them
(`dumpModelIndex`). The project archetype's PDO module is configured this way.

```
   PDO interface source (.java)
   /* @> $mapping … @< */                         model directory             tentackle-model
            │  Wurbelizer preprocessor                  │   (target/wurbel)        object graph
            │  extracts block, expands $vars            ▼                              │
            └──────────────────────────────►  one model file per entity  ──parse──►  Entity / Attribute /
                                                                                       Relation / Index / …
```

So `tentackle-model` does **not** read `.java` files. It reads the extracted, variable-free model sources (from a
directory, a single URL, a jar, or classpath resources) and builds the object graph from them.

---

## Package Layout

| Package                       | Role                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
|-------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `org.tentackle.model`         | The public model **API**: interfaces (`Model`, `ModelManager`, `ModelElement`, `Commented`, `Entity`, `Attribute`, `Relation`, `Index`, `IndexAttribute`, `ForeignKey`, `EntityFactory`, `EntityInfo`, `ModelDirectory`, `CommonOptions`, `EntityOptions`, `AttributeOptions`), value/config types (`ModelDefaults`, `EntityAliases`, `SourceInfo`, `AttributeSorting`, `MethodArgument`), enums (`Integrity`, `InheritanceType`, `RelationType`, `SelectionType`, `TrackType`, `SortType`, `AccessScope`, `Deferrability`), SPI hooks (`CustomModelValidator`, `NameVerifier`, `CodeFactory`, `ModelUtilities`) and exceptions (`ModelException`, `ModelError`). |
| `org.tentackle.model.impl`    | The default implementation of the API (`ModelImpl`, `EntityImpl`, `AttributeImpl`, `RelationImpl`, `ModelManagerImpl`, `EntityFactoryImpl`, …). `ModelImpl`, `ModelManagerImpl` and `NameVerifierImpl` are selected via `@Service` and are replaceable; the entity factory and the model directories are created by overridable `ModelImpl.create…()` methods.                                                                                                                                                                                                                                                                                                    |
| `org.tentackle.model.parse`   | The DSL **parser**: `Document`, `OptionParser`, `LineType` and the concrete `*Line` classes created by `LineType.createLine`. Turns model source text into a `Document` of typed lines that the `EntityFactory` assembles into an `Entity`.                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `org.tentackle.model.print`   | The **pretty-printer**: `EntityPrinter` and its helpers (`AttributePrinter`, `RelationPrinter`, `IndexPrinter`, `GlobalOptionsPrinter`, `AnnotationPrinter`, `PrintConfiguration`). Renders an `Entity` back into canonical model-definition text that parses into an equivalent entity.                                                                                                                                                                                                                                                                                                                                                                          |
| `org.tentackle.model.migrate` | **Database migration** support: `TableMigrator`, `ColumnMigrator`, `IndexMigrator`, `ForeignKeyMigrator`, `ColumnMigration`, `CustomMigrationValidator`. Diff the model against live database metadata (from `tentackle-sql`) and emit the `ALTER`/`CREATE`/`DROP` statements to reconcile them.                                                                                                                                                                                                                                                                                                                                                                  |
| `org.tentackle.model.service` | `Hook` — the `ModuleHook` SPI service for resource-bundle lookup.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |

The `module-info` exports `org.tentackle.model`, `.impl`, `.migrate`, `.parse` and `.print`, and provides
`org.tentackle.common.ModuleHook` via the `service.Hook`.

---

## The Object Graph

Entities, attributes, relations and indexes implement `ModelElement` (`getName`, `getOrdinal`, `getSourceInfo`,
`getParent`, `getModel`). The graph is rooted in `Model` and reachable through `Entity`.

```
Model ──┬─► Entity ──┬─► Attribute (one per logical field; maps to 1..n DB columns via DataType)
        │            ├─► Relation  (object / list, component / reference, embedding, n:m, …)
        │            └─► Index ──► IndexAttribute
        └─► ForeignKey (via getForeignKeys(), derived from the relations and the integrity)
```

### `Entity`

The top-level element and the richest part of the API. Besides identity (`getClassId`, `getTableName`,
`getSchemaName`, `getTableAlias`, `getIntegrity`, `getOptions`) it exposes the full **derived** view of an entity:

- **Attributes** in many flavors — own, inherited, embedded, sub-entity, mapped, table, all-combined — plus
  lookups by Java or column name (`getAttributeByJavaName`, `getAttributeByColumnName`), the unique domain key
  (`getUniqueDomainKey`), the context-id attribute, and the default `getSorting()`.
- **Relations** in the same flavors, plus the *reverse* direction: `getReferencingRelations()` and its
  inherited/sub-entity variants tell an entity who points *at* it — essential for cascade and integrity logic.
- **Inheritance** — `getSuperEntity`, `getSuperEntities`, `getSubEntities`, `getLeafEntities`,
  `getInheritanceType`/`getHierarchyInheritanceType`, `getTableProvidingEntity`, `isAbstract`.
- **Aggregate / composite structure** — `isComposite`, `getComponents`/`getAllComponents`,
  `getCompositePaths`, `getRootEntity`/`getRootEntities`, `getRootAttribute(s)`, `isRootEntity`, and the
  `...AccordingToModel` predicates that report what the *raw* model asked for (before the model-wide root/rootId/
  rootClassId inference described in [model-definition.md](model-definition.md)).
- **Embedding** — `isEmbedded`, `getEmbeddingEntities`, `getEmbeddingPaths`, `getEmbeddedAttributes`,
  `getEmbeddedRelations`.
- **Deep references** — `getDeepReferences`, `isDeeplyReferenced`, `getDeepReferencesToComponents`,
  `getDeeplyReferencedComponents`.
- **DDL** — `sqlCreateTable(Backend)` renders the `CREATE TABLE` for a given [backend](../../../../tentackle-sql/src/main/docs/sql.md).

Many getters come in a family of six — *own* / *inherited* / *sub-entity* / *including inherited* /
*including sub-entities* / *all* (e.g. `getAttributes`, `getInheritedAttributes`, `getSubEntityAttributes`,
`getAttributesIncludingInherited`, `getAttributesIncludingSubEntities`, `getAllAttributes`) — so a generator can
ask for exactly the slice it needs without re-walking the hierarchy itself.

### `Attribute`

One per logical field. Knows its `Entity`, its model column name and the **backend-specific** column name(s)
(`getColumnName(Backend, columnIndex)`), its `DataType`, `getSize`/`getScale`, nullability, Java type
(`getJavaType`, `getApplicationTypeName`, `getInnerTypeName`), its `AttributeOptions`, and the generated accessor
names (`getGetterName`, `getSetterName`, `getMethodNameSuffix`). A single attribute can map to several columns
(multi-column `DataType`); `isConvertible`, `isEmbedded`, `isHidden`, `isImplicit` classify it. `createEmbedded(...)`
projects it into an embedding entity.

### `Relation`

A typed association to another entity. Carries the related entity (`getForeignEntity`), the linking attributes
(`getAttribute`/`getForeignAttribute`), the back/`nm` relations, the `RelationType` (object/list), and the full
set of [relation modifiers](model-definition.md) as predicates: `isComposite`, `isReferenced`, `isProcessed`,
`isReversed`, `isShallow`, `isImmutable`, `isTracked`, `isSerialized`, `isDeepReference`, `isEmbedding`/`isEmbedded`,
etc. It also resolves selection strategy (`getSelectionType`, `isSelectionCached`, `getSelectionWurbletArguments`),
deletion (`isDeletionCascaded`), the generated method names (`getGetterName`, `getSetterName`, `getMethodName`,
`getLinkMethodName`, `getNmMethodName`), the extra arguments of the generated select and delete methods
(`getMethodArgs`, a list of `MethodArgument`), the eager join depth (`getEagerDepth`), declared Java types and any
annotations/stereotypes.

### Enums

`Integrity` (NONE, SOFT, RELATED, COMPOSITE, …, FULL), `InheritanceType` (NONE, PLAIN, SINGLE, MULTI, EMBEDDED),
`RelationType` (OBJECT, LIST), `SelectionType` (ALWAYS, EAGER, LAZY, EMBEDDED), `TrackType` (UNTRACKED, TRACKED,
ATTRACKED, FULLTRACKED), `SortType` (ASC, DESC), `AccessScope` (PRIVATE, PACKAGE, PROTECTED, PUBLIC) and
`Deferrability` (NOT_DEFERRABLE, DEFERRABLE_IMMEDIATE, DEFERRABLE_DEFERRED, see `Index.getDeferrability()` and
`ForeignKey.getDeferrability()`) — each the typed counterpart of an option documented in
[model-definition.md](model-definition.md).

### Options

`getOptions()` returns the parsed options: `EntityOptions` for an entity (the global options line) and
`AttributeOptions` for an attribute, both extending `CommonOptions` (access, generation, string handling and
binding options, stereotypes). `Entity.getSorting()` returns the default sorting as a list of `AttributeSorting`.

---

## Loading and Lifecycle

### `ModelManager` and `Model`

`ModelManager` (singleton via `ServiceFactory`) creates and caches named `Model` instances. There is normally one
model per build — the `"default"` model (`ModelManager.DEFAULT_MODEL_NAME`), reachable directly via
`Model.getInstance()`. Additional named models can be created, e.g., to generate PDOs from *another* project's
model alongside your own.

`Model` is the façade for one model. Its lifecycle:

1. **Configure** — `setModelDefaults(ModelDefaults)`, `setEntityAliases(EntityAliases)`, `setIndexName(...)`,
   `setSchemaNameMapped(...)`, and on its `EntityFactory`, `setBackends(Collection<Backend>)` to enable
   backend validation. The defaults, aliases, index name and schema mapping can also be preset by the system
   properties `tentackle.modelDefaults`, `tentackle.entityAliases`, `tentackle.indexName` and `tentackle.mapSchemas`,
   which is convenient for unit tests.
2. **Load** — entities are read, parsed and cached. A source is reloaded only if it has changed:
   - `loadFromDirectory(dir, updateRelations)` — a model directory (the usual case during a build). Hidden files
     are ignored. The directory is rescanned only when files were added or removed
     (`ModelDirectory.hasChanged()`); changed file contents are detected per entity (`EntityInfo.hasChanged()`).
   - `loadFromURL(url, updateRelations)` — a single model file.
   - `loadFromJar(file, updateRelations)` / `loadFromResources(updateRelations)` — the model files listed in
     `META-INF/<indexName>`, by default `MODEL-INDEX.LIST` (`Model.DEFAULT_INDEX_NAME`).
3. **Resolve relations** — pass `updateRelations = false` while the model is still incomplete (e.g., when loading
   dependency models first), then call `updateRelations()` once everything is present. This second pass is what
   wires `Relation` objects to their foreign entities and computes the derived views (referencing relations,
   composite paths, root inference, deep references). Loading with `updateRelations = true` does both at once.
4. **Query** — `getAllEntities`, `getByEntityName`, `getByTableName`, `getByClassId`, `getByURL`,
   `getForeignKeys`, `getEntityInfo(Entity)`.
5. **Refresh / clear** — `refreshModel()` reloads all model directories and updates the relations;
   `clearModel()` empties the cache.

If schema names are mapped (`setSchemaNameMapped(true)`, for backends without schema support), a schema-qualified
table name such as `td.invoice` is folded into the flat table name `td_invoice` when the entity is added to the model.

Almost every method throws `ModelException` when the model is inconsistent. A `ModelException` can be tied to a
specific `ModelElement` (`getElement()`, `isRelatedTo(element)`), which lets a generator defer a model-wide error
until it reaches the offending entity instead of failing the whole run blindly.

### `EntityFactory`, `Document` and the parser

`Model.getEntityFactory()` builds entities. Given a model source it is wrapped in a `parse.Document`, the
`parse` package classifies each logical line into a `LineType` (empty, comment, configuration — including the
index lines —, global option, attribute, attribute option, relation, relation option) and produces typed `Line`
objects, and `EntityFactory.createEntity(Document, ModelDefaults)` assembles them into an `Entity`.
`createEntity(SourceInfo)` creates an empty entity and is used internally by `createEntity(Document, ModelDefaults)`.
The index lines are stored with the entity and parsed only when the relations are updated, i.e. once the whole
model is known.

### `ModelDefaults` and `EntityAliases`

`ModelDefaults` carries the project-wide defaults (track type, root/rootId/rootClassId auto-flags, bind/size/
autoselect, deletionCascaded, remote, deferrability). `bind`, `size` and `autoselect` are initial values that the
entity's own options may override. The track type is only ever *raised* — e.g., a default of `tracked` will not
downgrade an entity already declared `fulltracked`. `root`, `rootid` and `rootclassid` are switched on where the
model requires them, and `remote` overrides the entity's setting. Entities with the `nodefaults` option ignore most
of the defaults (`EntityOptions.noModelDefaults()`). Defaults embedded in a model source (`#[...]` as the first line,
written by the SQL plugin's model dump) take precedence over the model's defaults.
The defaults are typically passed from the parent POM (the `modelDefaults` configuration of the Wurbelizer/SQL
plugins).

`EntityAliases` maps alternative entity names (aliases) used in relation or inheritance declarations to the real
entity names, e.g. `Port = LogisticNode`, which eases refactoring.
Both are documented from the user's side in [model-definition.md](model-definition.md#overriding-model-defaults).

### Table Aliases

Every entity providing a table has a table alias, which qualifies the columns in the generated SQL and is part of
the foreign key names (`<alias>_<column>_fkey`). If the model does not define one with `alias :=`, it is generated
when the relations are updated. The entities at the core of a domain model are usually the ones with the most
relations, and they show up in most of the joins, so the generator hands out the short aliases by centrality:

1. **Rank** — the entities are sorted by the number of related entities, counting relations in both directions,
   components as well as plain references. An inheritance hierarchy counts as a single entity. Ties are resolved by
   the sum of the related entities' own number of related entities (being related to the core counts more than being
   related to the periphery), and finally by the entity name.
2. **Initials** — the prefixes of the camel-case initials and digits (`CustomerOrder` → `c`, `co`) are assigned in
   rounds of increasing length: in each round, the entities in ranking order take their first free candidate of
   that length. A central `CustomerOrder` thus gets `c`, while a peripheral `Currency` has to look further.
3. **Syllables** — the entities still without an alias try the first letters of their syllables and the first letter
   followed by the next consonants (from three characters on), again in rounds of increasing length.
4. **Counter** — whatever is left gets its first letter followed by a counter (`v1`).

Candidates already taken or not valid for one of the backends (such as `as` or `on`) are skipped. Explicit aliases
are always respected, and aliases generated by an earlier load stay as they are.

Because the aliases depend on the whole model, adding entities or relations may change the generated aliases of
other entities, and hence their foreign key names. Define the alias explicitly where this matters.

---

## Extension Points (SPI)

The module is open for application-specific customization through `ServiceFactory`/`@Service` lookups:

| SPI                    | Purpose                                                                                                                                                                                                                                                                                                                  |
|------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `CustomModelValidator` | Applications register `@Service(CustomModelValidator.class)` implementations to enforce extra rules on the whole model (collected by `CustomModelValidator.loadValidators()`). `validate(Model)` is called at the end of `loadFromDirectory(...)` whenever the directory was (re)loaded, and may throw `ModelException`. |
| `NameVerifier`         | Enforces application naming conventions for entity, table, alias, attribute, column, relation and index names, on top of backend rules. Returns a diagnostic message (or `null` if OK). Can be switched off via `setEnabled(false)`.                                                                                     |
| `CodeFactory`          | Produces reusable Java code snippets (e.g. the `@Bindable` annotation text) for generators such as wurblets, and the DDL for tables, indexes and foreign keys (used by `Entity.sqlCreateTable`, `Index.sqlCreateIndex` and the migrators). Defaults to itself; override via `@Service`.                                  |
| `ModelUtilities`       | Syllable extraction and fuzzy name matching, used to generate table aliases and to detect renamed columns during migration. Defaults to itself; override via `@Service`.                                                                                                                                                 |
| `Model` impl           | The whole `Model`/`ModelManager` implementation is itself an SPI — the wurblets substitute their own `TentackleWurbletsModel` (see below).                                                                                                                                                                               |

---

## Pretty-Printing and Migration

### `print` — model → text

`EntityPrinter` renders an `Entity` back to canonical model-definition source (driven by a
`PrintConfiguration`). The printed source parses into an equivalent entity, but the original layout and the comments
that are not part of the model (e.g. `##` lines) are not preserved. In compact mode, options implied by the model
defaults or the model structure are omitted.
`PrintConfiguration.optionAnnotations` selects the annotations printed as attribute options rather than in the
validations section. The printer is used by the SQL plugin's model dump, the PDO wizard and the MCP plugin.

### `migrate` — model vs. live database → DDL

The `migrate` package compares the model against actual database metadata (`tentackle-sql`'s `TableMetaData`,
`ColumnMetaData`, `IndexMetaData`, `ForeignKeyMetaData`) and emits the statements to reconcile them:
`TableMigrator` (tables), `ColumnMigrator`/`ColumnMigration` (add/alter/drop columns), `IndexMigrator` and
`ForeignKeyMigrator`. `CustomMigrationValidator` is the SPI for application-specific migration rules. This is what
the [SQL Maven plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) uses to
migrate an existing schema rather than recreating it.

---

## How the Wurblets Use the Model

The [wurblets](../../../../tentackle-wurblets/src/main/docs/wurblets.md) are this module's principal client. The
key integration class is
[`ModelWurblet`](../../../../tentackle-wurblets/src/main/java/org/tentackle/wurblet/ModelWurblet.java), the shared
base of every model-driven wurblet, and
[`TentackleWurbletsModel`](../../../../tentackle-wurblets/src/main/java/org/tentackle/wurblet/TentackleWurbletsModel.java),
an `@Service(Model.class)` subclass of `ModelImpl` that the wurblet build registers in place of the default model
implementation. `TentackleWurbletsModel` adds two things: awareness of *heap files* (model sources named with a
leading dot, used by the DTO wurblet) and a *deferred loading exception* so a model-wide error is reported at the
concrete entity it relates to rather than aborting every file. It also adopts the Wurbelizer's source encoding.

### What `ModelWurblet.run()` does

On each wurblet invocation, `ModelWurblet` drives the `tentackle-model` lifecycle from the Wurbelizer plugin's
properties:

1. Reads the mandatory `model` directory property (creating the directory if absent) and the optional
   `otherModels`, `modelName`, `backends`, `modelDefaults` and `entityAliases` properties from the build
   configuration. The wurblet configuration option `missingModelOk` tolerates a missing entity.
2. Resolves the `Model` (`Model.getInstance()` or `ModelManager.getModel(modelName)`), then
   `model.getEntityFactory().setBackends(...)` from the `BackendFactory` so the model is validated against the
   project's target databases.
3. Applies `model.setModelDefaults(...)` and `model.setEntityAliases(...)`.
4. Loads dependency models first with `loadFromDirectory(other, false)` (relations not yet resolved), then the
   own model with `loadFromDirectory(modelDir, true)` to resolve relations across the whole set.
5. Resolves the **target entity** for this source file — by file path (`loadFromURL`) or by entity name
   (`getByEntityName`) — and stores it in `getEntity()`. A `ModelException` is converted into a
   `WurbelTerminationException` so model errors abort the run cleanly instead of cluttering the generated code.
   A model-wide error that could be associated with an entity is deferred and thrown only by the wurblets of the
   entities it relates to.
6. Determines the effective `remote` flag from the entity's options, or from the model defaults if there is no
   entity (overridable with `--remote`/`--noremote`).

The model is a singleton of the wurblets' classloader. The Wurbelizer plugin loads the `wurbletDependencies` in a
separate classloader for each execution, so the model is loaded **once per execution**, and subsequent wurblets on
other files reuse the populated graph (`loadFromDirectory` only reloads what has changed). If the wurblets are
plugin dependencies instead, the model is shared by all executions of the build and the wurblets run sequentially
(see `WurbletThreadSafety`).

### What the wurblets read from the model

Each generator walks the `Entity` it was given and emits Java from the typed graph:

| Wurblet                                                                | Reads from the model                                                                                         | Emits                                                                     |
|------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------|
| `AttributeNames` / `ColumnNames` / `ColumnLengths`                     | `getAttributes`/`getMappedAttributes`, `getColumnName(Backend,i)`, `getSize`                                 | `AN_*`, `RN_*` / `CN_*`, `CP_*` / `CL_*` constants                        |
| `Methods`                                                              | attribute getters/setters, options, `getUniqueDomainKey`                                                     | accessor declarations + UDK select                                        |
| `Relations`                                                            | `getRelations`, relation type/scope/read-write/nm                                                            | navigation methods                                                        |
| `UniqueDomainKey` / `DomainMethods`                                    | `getUniqueDomainKey`, root inference                                                                         | the UDK record + domain key methods                                       |
| `ModelComment`                                                         | referencing/composite/inheritance views (`getReferencingRelations`, `getCompositePaths`, super/sub entities) | a block comment documenting the entity's usage, via `ModelCommentSupport` |
| persistence wurblets (`DbModelWurblet` and the `Pdo*`/`Db*` templates) | attributes, relations, `DataType`, eager relations (`getEagerDepth`)                                         | SQL and the persistence implementation                                    |

`ModelWurblet` also provides helpers built on the model API — `getColumnNameConstant`, `getEffectiveDataType`,
`createAccessorCode`, relation method-name builders and `assertSupportedByBackends(...)` (which fails the build if a
chosen feature isn't supported by every active `Backend`). `CodeFactory` supplies shared snippets such as the
`@Bindable` annotation.

```
   Wurbelizer plugin properties
   (model, backends, modelDefaults, …)
                │
                ▼
        ModelWurblet.run()  ──►  Model (TentackleWurbletsModel)
                │                    │  loadFromDirectory(...) + updateRelations()
                │                    ▼
                │            Entity / Attribute / Relation graph   ◄── validated against Backends
                ▼                    │
        getEntity() ─────────────────┘
                │
                ▼
   AttributeNames / Methods / Relations / UniqueDomainKey / ModelComment / DTO / …  ──►  generated Java
```

---

## Related Documentation

- [Model Definition Syntax](model-definition.md) — the DSL this module parses (the grammar reference).
- [Tentackle Wurblets](../../../../tentackle-wurblets/src/main/docs/wurblets.md) — interface-side generators that read this model.
- [Persistence Wurblets](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md) — implementation-side generators.
- [Tentackle SQL](../../../../tentackle-sql/src/main/docs/sql.md) — `DataType`/`Backend`, used for column resolution and validation.
- [SQL Maven Plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) — generates and migrates DDL from this model.
- [Tentackle Maven Plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md) — drives the build that extracts and runs the model.
- [PDO — Persistent Domain Objects](../../../../tentackle-pdo/src/main/docs/pdo.md) — the runtime pattern the model describes.
- [MCP Maven Plugin](../../../../tentackle-mcp-maven-plugin/src/main/docs/tentackle-mcp-maven-plugin.md) — serves this model to coding agents.
