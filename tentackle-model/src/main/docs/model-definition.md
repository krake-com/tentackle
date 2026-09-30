# Tentackle Model Definition Syntax

The Tentackle model definition is a DSL embedded as a Java comment block in a [PDO](../../../../tentackle-pdo/src/main/docs/pdo.md) interface source file.
The model (`tentackle-model`, see the [module overview](./tentackle-model.md)) is parsed at build time to drive the code generation (persistence interfaces
and implementations, documentation), DDL generation, database migration and code analysis tools. 
It is primarily used by the [Wurbelizer code generator](../../../../tentackle-wurblets/src/main/docs/wurblets.md), the
[Tentackle SQL-](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md), the
[PDO-wizard-](../../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md) and the PDO-browser 
maven plugins.

---

## 1. Block Delimiters

A model definition is enclosed in a Java block comment that starts with `@>` and ends with `@<`:

```java
/*
 * @> $mapping
 *
 * ...model content...
 *
 * @<
 */
```

The token after `@>` is the **block tag** — typically `$mapping` (a variable resolved by the wurblet preprocessor to a file path). Everything between the delimiters is the model source.

---

## 2. Document Structure

The model definition is organized into five logical sections.
The order of sections is not significant, but the
recommended order is:

```
# <entity description comment>
<configuration lines>

## attributes
[<global-option-overrides>]
<attribute lines>

## indexes
<index lines>

## validations
<attribute option lines>

## relations
<relation lines>
```

Comments are allowed anywhere in the model definition.
Comment lines starting with `##` do not affect parsing — they are purely for readability.
Single hash comments (`#`), however, apply to the following line and become part of the model description.

Options in `[...]` (see [Global Options Line](#4-global-options-line)) override the project-level model defaults that are usually
provided by a property in the parent pom and referenced via the `<modelDefaults>` configuration
parameter of the wurbelizer and tentackle-sql maven plugins. 

---

## 3. Configuration Lines

Configuration lines have the form:

```
<key> := <value>[;]
```

The trailing semicolon is optional. Leading and trailing whitespace around key and value is trimmed.

### Entity configuration keys

| Key           | Value                                                                 | Description                                                                                                                                                  |
|---------------|-----------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `name`        | string (often `$classname`)                                           | Entity name (class simple name). Must be unique in the model.                                                                                                |
| `id`          | integer (often `$classid`)                                            | Unique numeric class ID (non-abstract classes only).                                                                                                         |
| `table`       | string (often `$tablename`)                                           | Database table name (if applicable). May include schema: `schema.tablename` — see [Table Names](../../../../tentackle-session/src/main/docs/table-names.md). |
| `alias`       | string                                                                | Short table alias used in SQL joins. Auto-generated if omitted.                                                                                              |
| `integrity`   | see [Integrity Modes](#integrity-modes)                               | Referential integrity strategy.                                                                                                                              |
| `comment`     | string                                                                | optional entity description (overriding any previous comment)                                                                                                |
| `extends`     | entity name                                                           | Inheritance: this entity extends the named entity.                                                                                                           |
| `inheritance` | see [Inheritance Mapping Strategies](#inheritance-mapping-strategies) | Inheritance mapping strategy.                                                                                                                                |

### Integrity Modes

| Value              | App check | DB foreign keys (related) | DB foreign keys (composite) |
|--------------------|-----------|---------------------------|-----------------------------|
| `none`             | no        | no                        | no                          |
| `soft`             | yes       | no                        | no                          |
| `related`          | yes       | yes                       | no                          |
| `composite`        | yes       | no                        | yes                         |
| `related_dbonly`   | no        | yes                       | no                          |
| `composite_dbonly` | no        | no                        | yes                         |
| `full_dbonly`      | no        | yes                       | yes                         |
| `full`             | yes       | yes                       | yes                         |

### Inheritance Mapping Strategies

| Value      | Description                                                                               |
|------------|-------------------------------------------------------------------------------------------|
| `none`     | No inheritance (default).                                                                 |
| `plain`    | Plain Java inheritance (table per concrete class).                                        |
| `single`   | All entities are mapped to a single table with the class id as the discriminator column.  |
| `multi`    | Each entity is mapped to its own table joined via the object id column (table per class). |
| `embedded` | Embedded entity mapped into a parent entity via an embedding relation.                    |

### Example

```
name := Invoice
id   := 2002
table := td.invoice
alias := inv
integrity := full
```

The values are usually defined as wurblet variables, so the above can be written as:

```
name := $classname
id   := $classid
table := $tablename
alias := $alias
integrity := $integrity
```

---

## 4. Global Options Line

The global options line sets entity-level flags and default sorting. It appears
once in the `## attributes` section and has the form:

```
[option, option, #Stereotype, ... | sortAttr1 +sortAttr2 -sortAttr3]
```

Everything must be enclosed in `[...]`. Options are comma-separated.
A `|` separator divides flags from default sort attributes. Stereotypes are
prefixed with `#`.

### Entity-level option flags

| Option        | Description                                                                                                                          |
|---------------|--------------------------------------------------------------------------------------------------------------------------------------|
| `root`        | This is a root entity.                                                                                                               |
| `rootid`      | Adds the `rootId` attribute (filled with the root entity's id).                                                                      |
| `rootclassid` | Adds the `rootClassId` attribute (for multi-homed components).                                                                       |
| `remote`      | Supports remoting via TRIP.                                                                                                          |
| `tracked`     | Tracks whether the PDO was modified or not.                                                                                          |
| `attracked`   | Tracks the modification state per attribute.                                                                                         |
| `fulltracked` | Full tracking per attribute including access to the old persisted value.                                                             |
| `untracked`   | No tracking (default, also used to override the model default).                                                                      |
| `tokenlock`   | Adds the tokenlock attributes (`editedBy`, `editedSince`, `editedExpiry`)                                                            |
| `tableserial` | Adds the `tableSerial` attribute (table-wide serial bumped on every modification; used for change detection and cache invalidation). |
| `normtext`    | Adds the `normText` attribute for full-text search.                                                                                  |
| `nopkey`      | No primary key `id`.                                                                                                                 |
| `nodefaults`  | Ignore global model defaults entirely.                                                                                               |
| `cached`      | Entity provides a cache (root entities only).                                                                                        |
| `provided`    | DDL is maintained externally; no SQL generation.                                                                                     |
| `bind`        | Annotate methods with `@Bindable` to enable binding.                                                                                 |
| `size`        | Add maxcols binding option to `@Bindable` annotation, where applicable.                                                              |
| `autoselect`  | Add `autoselect` binding option to `@Bindable` annotation, where applicable.                                                         |
| `..strip`     | All stripping options (nostrip, lstrip, rstrip, strip = default)                                                                     |
| `#Name`       | Custom stereotype `Name`.                                                                                                            |

Case doesn't matter for the options.
The global options line also accepts the attribute options that are not bound to a single attribute —
the [access and generation options](#access-and-generation-options) (`readonly`, `nomethod`, `mute`,
the access scope keywords, …), the [string handling options](#string-handling-options) and the
[binding options](#binding-options) such as `uc` or `lc`. They become the entity's defaults for all its
attributes, which can override them individually.
Most of these options are inherited from the project-level model defaults and need
not be specified in the global options line. Typical model defaults are:

```
<tentackle.modelDefaults>remote, bind, size, autoselect, tracked, root, rootid, rootclassid</tentackle.modelDefaults>
```

The project-level model defaults also accept `deferred`, `deferrable` and `immediate` (unlike
the options above, these are *not* valid in an entity's own global options line — they only
control the default deferrability of unique indexes and foreign keys, see
[Index Lines](#7-index-lines)). Setting `deferred` or `deferrable` there makes every unique
index and foreign key that doesn't specify its own keyword `DEFERRABLE INITIALLY IMMEDIATE`
(never `INITIALLY DEFERRED` — the project-wide default never changes existing behavior unless a
transaction explicitly opts in via `@Transaction(deferred=true)`). `immediate` is a documented
no-op, kept for symmetry with the index-line grammar. Backends that don't support deferred
constraint checking silently fall back to plain, non-deferrable DDL (with a build-time warning).

Besides the flags `tracked`, `attracked`, `fulltracked`, `untracked`, `root`, `rootid`, `rootclassid`, `bind`,
`size`, `autoselect` and `remote`, the project-level model defaults accept `cascade` (synonyms:
`cascaded`, `cascadeddelete`, `deletioncascaded`), which makes every component relation cascade its
deletion as if it declared [`delete = cascade`](#delete). Unlike an entity's global options line, the
model defaults turn an option off with a leading `!` only — `-` is not recognized there. That's why the
defaults used by the wizard's batch mode read `!ROOT, !ROOTID, !ROOTCLASSID, !BIND, UNTRACKED`.

Whereas `root`, `rootid` and `rootclassid` are automatically determined by inspecting the
model as a whole. The following rules apply:

- If the entity is not used as a component of an aggregate, it is treated as a `root` entity. 
  Notice that root entities are not necessarily aggregate roots but can stand alone as well.
- If the entity is used as a component of an aggregate and has no attribute pointing to
  its root entity, a `rootId` attribute is added, containing the root entity's id.
- If the entity is used as a component of more than one aggregate, a `rootClassId`
  attribute is added, containing the root entity's class id.


### Default sorting

After `|`, list attribute names to define the default sort order. Prefix with
`+` for ascending (default) or `-` for descending:

```
[tokenlock, tableserial | +name -id]
```

As a shortcut, the `|` may be omitted if every sort attribute is prefixed with `+` or `-` and none of
them is named like an option keyword (the parser tries the option first):

```
[tokenlock, tableserial, +name, -id]
```

### Overriding model defaults

The global project-wide model defaults can be disabled by prepending a `-` (or `!`) to the option or
overridden if mutually exclusive, e.g.:

```
## attributes
[-root, untracked]
```

This makes the entity an untracked component, even if it is used in a component relation of another entity.
The application can still handle the root property explicitly by overriding the `isRootEntity()` method.

### Example

```
[tokenlock, tableserial, #accounting | -id total]
```

---

## 5. Attribute Lines

An attribute line maps a Java field to a database column:

```
javaType[<innerType>][(size[,scale])]  javaName  columnName  [comment]  [[options]]
```

All four positional fields are required (comment and options are optional).

### Java types

| Category   | Types                                                                                                                                                                                                |
|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Primitives | `boolean`, `byte`, `char`, `short`, `int`, `long`, `float`, `double`                                                                                                                                 |
| Wrappers   | `Boolean`, `Byte`, `Character`, `Short`, `Integer`, `Long`, `Float`, `Double`                                                                                                                        |
| Numeric    | `BigDecimal`, `BMoney`, `DMoney`                                                                                                                                                                     |
| Other      | `String`, `I18NText`, `Date`, `Time`, `Timestamp`, `LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetDateTime`, `OffsetTime`, `ZonedDateTime`, `Instant`, `UUID`, `Binary`, `TBinary`, `XML`, `JSON` |
| Custom     | Any other class name (application-specific type)                                                                                                                                                     |

`JSON` and `XML` are string variants and affect the database's column type only, i.e., the java type 
seen by the application is `String`. Postgres, for example, provides a special query syntax for those types.
If the application needs real JSON or XML objects, wrap the type within a `Convertible` (see below),
for example: `MyJsonObject<JSON>`. Tentackle does not provide such implementations to avoid dependencies
on any of the various JSON or XML implementations.
See the concrete backends of what is supported and how it is mapped.

### Size and scale

Appended directly to the type: `String(128)`, `Double(10,2)`, `BigDecimal(15,4)`.
`String(0)` selects a large string (a string variant), that is mapped to a `CLOB` in the database, if supported by the database.

### Inner/generic type

Some predefined types like `Binary` are generic and require an inner type.

If an application-specific type maps to only one database column, the column's Java type can be wrapped 
by an outer type (see `org.tentackle.misc.Convertible`). If it maps to multiple columns, 
an application-specific type must be defined (see `org.tentackle.sql.DataType`).

Examples: `Binary<MyClass>`, `MyType<int>`, `MyEnum<String>`.

### Attribute options

Options follow the comment inside `[...]`. Multiple options are comma-separated.

#### Value options

| Option            | Description                                                                    |
|-------------------|--------------------------------------------------------------------------------|
| `default <value>` | The SQL default value of a column.                                             |
| `init <expr>`     | Java expression to initialize the field declaration when a PDO is initialized. |
| `new <expr>`      | Java expression to set the field before first-time persistence (virgin PDO)    |

String values should be quoted: `default "hello world"`. 
Timestamps: `default "1970-01-01 00:00:00.000"`. 
Booleans: `default true`.

##### `default` vs. `init` vs. `new`

These three look similar but act at different times and on different layers:

- **`default <value>`** is a *database* default. It is emitted into the DDL `DEFAULT` clause of
  the generated column, so the value is supplied by the database whenever a row is inserted
  without that column (for example by another application, a migration, or a `mute` column). It
  is a literal SQL value, not Java code, and does not initialize the Java field.
- **`init <expr>`** is a *Java field initializer*. The expression is placed verbatim on the field
  declaration in the persistence implementation (`type name = <expr>;`), so it applies to *every*
  instance from the moment it is constructed — freshly created or about to be reloaded from the
  database (the read then overwrites it). Use it to avoid `null` starting values, e.g.
  `init BigDecimal.ZERO` or `init ""`.
- **`new <expr>`** runs *once, just before the first save* of a virgin (never-persisted) PDO,
  from the generated `presetVirgin()` method. Unlike `init` it does not touch reloaded objects,
  and unlike `default` it is real Java executed on the object, so it can call methods or read
  other fields. Use it to stamp values that only make sense for a brand-new object.

`default` and `init`/`new` are independent: a column may carry a DDL default *and* a Java
initializer, and the two need not agree.

#### Semantic options

| Option      | Description                                                                                                           |
|-------------|-----------------------------------------------------------------------------------------------------------------------|
| `key`       | Marks this attribute as part of the [unique domain key](../../../../tentackle-pdo/src/main/docs/domain-key.md) (UDK). |
| `context`   | This attribute holds the context entity's ID.                                                                         |
| `utc`       | Timestamp only: set the UTC serialization flag.                                                                       |
| `tz`        | date related types only: use with timezone (PDO must provide a `get<Attr>TimezoneConfig()` method).                   |
| `normtext`  | Include this attribute's value in the entity's normtext column.                                                       |
| `shallow`   | Exclude this attribute from snapshot and copy operations.                                                             |
| `hidden`    | Attribute is not declared in the interface (implementation only).                                                     |
| `mute`      | Not part of the PDO — mapped to the database table only.                                                              |
| `mapnull`   | Map `null` to a constant (typically empty string `""` or 0) in the database.                                          |

##### Timezone handling (`utc` and `tz`)

Temporal attributes are a classic source of confusion, so the two timezone-related options
deserve a closer look. Both apply **only to the legacy `java.sql` types** — `utc` to `Timestamp`,
`tz` to `Timestamp`, `Date` or `Time`. The `java.time` types (`LocalDate`, `LocalDateTime`,
`OffsetDateTime`, `ZonedDateTime`, `Instant`, …) already encode their own offset/zone semantics
and use neither option; reach for one of those types first if you can.

- **`utc`** sets the UTC serialization flag on the column. The timestamp is read from and written
  to the database as an absolute instant in UTC, independent of the JVM's default timezone. This
  is the right choice for "point in time" values (audit stamps, event times) that must compare
  correctly across servers running in different timezones.

- **`tz`** makes the generated JDBC code apply an explicit timezone through the
  `Calendar`-argument variants of the JDBC accessors (`rs.getTimestamp(col, cal)` /
  `ps.setTimestamp(col, val, cal)`). The `Calendar` is *not* fixed at build time — the PDO must
  supply it at runtime through a method named after the attribute:

  ```java
  Calendar get<AttributeName>TimezoneConfig();   // e.g. getPrintedTimezoneConfig()
  ```

  This lets the effective timezone be chosen per object or per user (a tenant's or a document's
  own zone, say) rather than being a fixed constant. Use `tz` when a *wall-clock* date/time must
  be stored and reproduced in a specific, data-driven zone; use `utc` when you want a
  zone-independent absolute instant. They are different mechanisms and are not normally combined.

##### `context`

Marks the attribute that holds the **id of the object spanning this object's
[domain context](../../../../tentackle-pdo/src/main/docs/domain-context-provider.md)** — for
example the tenant/mandator in a multi-tenant application. When a PDO is placed into a
`DomainContext`, the generated `determineContextId()` copies `context.getContextId()` into this
attribute, and the generated queries add a context condition so that objects belonging to one
context are not visible from another. There may be at most one `context` attribute per entity,
and it is not allowed on embedded attributes. Entities that live only in the default context do
not need it.

##### `mute`

A `mute` attribute is mapped to a real database column but is **deliberately invisible to the
PDO**: no field is declared, no getter/setter is generated, and it appears neither in the domain
interface nor in snapshots or copies. Because the persistence layer never reads or writes it, the
column must be able to exist without the PDO's help — hence a `mute` column must be nullable or
carry a database `default` (the model validation enforces this). Use it for columns that are
populated outside the normal object lifecycle: by a database trigger, a migration, another
application, or hand-written code. In the [complete example](#entity-with-inheritance-and-nm-relation-user)
below, `User.password` is `mute` because it is written through a dedicated password-hashing path
rather than a plain setter.

#### Access and generation options

| Option           | Description                                                                                                                                             |
|------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `readonly`       | Generate getter only; no setter.                                                                                                                        |
| `writeonly`      | Generate setter only; no getter.                                                                                                                        |
| `nomethod`       | Generate neither getter nor setter.                                                                                                                     |
| `nodeclare`      | Do not emit the backing-field declaration. The column is still mapped (read/written); the value is held elsewhere (e.g. a derived or superclass field). |
| `noconstant`     | Do not generate the name constants (the `AN_`/`CN_` attribute- and column-name constants and the size constant).                                        |
| `super`          | Attribute is inherited from a super entity (if not in model of superclass)                                                                              |
| `<access>`       | Visibility of generated methods, given as the bare keyword: `public` (default), `protected`, `package`, `private`.                                     |

##### `super`

Marks an attribute as **inherited from a super entity**. When an entity `extends` another
*modeled* entity, the generator already knows the inherited attributes and does not regenerate
them, so `super` is not needed there. It is for the case where the attribute lives in a superclass
that is **not itself described by a model** (a hand-written or framework base class): listing the
attribute in the subclass model — flagged `super` — tells the generator that the field, name
constant and accessors already exist upstream, so it must *use* the attribute (in queries, the
UDK, etc.) without emitting a second declaration for it.

#### String handling options

| Option      | Description                                  |
|-------------|----------------------------------------------|
| `trimread`  | Trim to size when reading from the database. |
| `trimwrite` | Trim to size when writing to the database.   |
| `trim`      | `trimread` + `trimwrite`                     |

#### Binding options

The binding options are added to the `@Bindable` annotation.

| Option        | Description                                                                  |
|---------------|------------------------------------------------------------------------------|
| `maxcols=<n>` | override maximum columns.                                                    |
| `cols=<n>`    | explicit visible columns.                                                    |
| `lines=<n>`   | explicit visible rows.                                                       |
| `scale=<n>`   | override numeric scale.                                                      |
| `filler=<n>`  | filler character. Default is blank. (<n> = 'x' or numeric)                   |
| `uc`          | Convert to uppercase.                                                        |
| `lc`          | Convert to lowercase.                                                        |
| `unsigned`    | Numeric field is unsigned.                                                   |
| `digits`      | field accepts only digit characters.                                         |
| `strip`       | remove leading and trailing filler characters (default)                      |
| `nostrip`     | no string stripping (filler trimming)                                        |
| `lstrip`      | remove leading filler characters                                             |
| `rstrip`      | remove trailing filler characters                                            |
| `bind`        | Annotate methods with `@Bindable` to enable binding.                         |
| `size`        | Add maxcols binding option to `@Bindable` annotation, where applicable.      |
| `autoselect`  | Add `autoselect` binding option to `@Bindable` annotation, where applicable. |

The semantic option `utc` (see above) is added to the `@Bindable` annotation as well, so that the UI
component interprets the timestamp as UTC.

Options inherited from the entity's options can be overridden.
To disable an option for an attribute, prepend a `-` (or `!`) to the option, for example, `-bind`.
A leading `+` forces an option on.

#### Annotation options

Annotations can be attached to getter/setter methods. The `@` prefix is mandatory.
An optional modifier immediately after `@` controls placement:

| Modifier | Placement                                                  |
|----------|------------------------------------------------------------|
| (none)   | Getter and setter in interface.                            |
| `=`      | Setter only.                                               |
| `+`      | Both getter and setter.                                    |
| `~`      | Hidden: applied to implementation only, not the interface. |

Modifiers can be combined, e.g. `@=~MyAnno` places the annotation on the setter implementation only.

Example: `[@NotNull, @=~MySetterAnno(value="x")]`

#### Custom stereotypes

A `#Name` token inside attribute options adds a custom stereotype to the attribute.
Stereotypes become part of the model and can be used by application-specific wurblets,
code generators, model analysis tools, etc.
 
Example: `[#Accounting, #Log]`

### Attribute line examples

```
IngotNumber<String>($ingot_no) ingotNumber     ingot_no        the ingot number [normtext]
Long                           productId       product_id      ID of the product the ingot belongs to, null if unknown
String($product_suffix)        productSuffix   product_suffix  optional product number suffix [uc]
String                         comment         ingot_comment   special comment [normtext]
int                            weight          weight          net weight in kg [unsigned]
int                            length          length          sawn length in mm [unsigned]
int                            thickness       thickness       thickness in mm [unsigned]
int                            width           width           width in mm [unsigned]
Long                           stockyardId     stockyard_id    ID of the stockyard, null if not in stock
Integer                        pilePosition    pile_position   vertical pos. within pile, 1=bottom, null if not in stock
Timestamp                      inStockSince    in_stock_since  in stock since, null if not in stock
Timestamp                      releasedSince   released_since  released from stock since, null if still in or never in stock
Position3D                     stockPosition   stock_position  x, y, z position in stock in mm, null if not in stock
```

---

## 6. Attribute Option Lines (Validation Section)

Used in the `## validations` section to attach annotations to attributes (typically
[validation](../../../../tentackle-core/src/main/docs/validation.md) constraints). The syntax is:

```
attributeName: @Anno1, @Anno2(param=value), ...
```

- The **attribute name** (or relation name) is followed by a colon.
- Multiple annotations are comma-separated.
- The block can span multiple lines: continuation is indicated by a trailing `,`.
  The block ends with `;`, an empty line, or two consecutive newlines.

Option syntax inside annotations creates a
[`CompoundValue`](../../../../tentackle-core/src/main/docs/compound-value.md).
This can be a constant, a method reference or a script expression (usually Groovy,
but Ruby and JSR223 are also supported).

### Examples

```
# single annotation
name: @NotNull

# multiple annotations, multi-line
total: @NotNull(scope=PersistenceScope.class),
       @Greater(value="0", scope=PersistenceScope.class, condition="{ object.printed != null }"),
       #WARM

# conditional with method reference
uplink: @NotNull(condition="$slave")

# validation on a relation
Lines: @NotNull
```

---

## 7. Index Lines

Index lines appear in the `## indexes` section.

```
[unique | optional] [deferred | deferrable | immediate] index <indexName> := [+|-]<col1> [asc|desc], <col2>, ... [|/where <filter>]
```

- `unique` — creates a unique index.
- `optional` — create only if backend supports it (mainly for in-memory databases used for CI)
- `<indexName>` — identifier for the index (becomes part of the generated DDL name).
- Columns are database column names or Java attribute names, comma-separated.
- `asc` / `desc` or `+` / `-` are optional sort direction suffixes.
- following `where` or `|`: optional SQL expression defining a filtered (a.k.a. conditional) index.

Not every backend can do all of this. If a backend supports neither the requested filter nor a function, the
index is skipped when declared `optional` and rejected at build time otherwise. Oracle has no partial indexes at
all and emulates them with a `CASE` expression — the conditional uniqueness is enforced, but the index is not
used for reads. See [The Oracle Backend](../../../../tentackle-sql/src/main/docs/oracle.md).

### Deferred constraint checking

`deferred`, `deferrable` and `immediate` control whether the unique index is created as a
*deferrable* constraint, letting a transaction postpone its check to commit time (e.g. Postgres,
Oracle) instead of checking it immediately after every statement. They are only allowed on
`unique` indexes (a plain index has nothing to defer) and are mutually exclusive with a filter
condition or a function-based column (deferrable constraints must be a plain column list).

| Keyword      | DDL                                                                              | Meaning                                                                                                                                      |
|--------------|----------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| *(none)*     | `CREATE UNIQUE INDEX`                                                            | Not deferrable (default, unchanged behavior).                                                                                                |
| `immediate`  | `CREATE UNIQUE INDEX`                                                            | Explicitly not deferrable — overrides a project-wide `deferred`/`deferrable` model default.                                                  |
| `deferrable` | `ALTER TABLE ... ADD CONSTRAINT ... UNIQUE (...) DEFERRABLE INITIALLY IMMEDIATE` | Deferrable, but checked immediately unless a transaction opts in via `@Transaction(deferred=true)` / `Session.setConstraintsDeferred(true)`. |
| `deferred`   | `ALTER TABLE ... ADD CONSTRAINT ... UNIQUE (...) DEFERRABLE INITIALLY DEFERRED`  | Deferrable and checked at the end of every transaction by default.                                                                           |

On a backend that doesn't support deferred constraint checking (e.g. H2), a `deferred`/
`deferrable` index or foreign key silently falls back to plain, non-deferrable DDL — a build-time
warning is logged, but the build does not fail. This is what lets `deferred`/`deferrable` be set
in the project-wide model defaults even for projects whose CI runs against H2.

Foreign keys are never declared explicitly (they're derived from `integrity`/relations), so their
deferrability can only come from the project-wide model default, never from a per-foreign-key
keyword — see [Global Options Line](#4-global-options-line).

### Examples

```
unique index udk := name, -position
# index with embedded type
index slot := slot_id, detail.rank
# optional filtered index
optional unique index code := code | type=0
```

---

## 8. Relation Lines

Relation lines appear in the `## relations` section. A relation describes how
this entity is associated with another entity.

```
ClassName:
    property = value,
    property = value,
    ...
```

- `ClassName` is the simple name of the related entity.
- Properties are `key = value` pairs or bare flags (value is empty string), separated by commas.
- The block can span multiple lines (the same continuation rules as attribute option lines).
- A bare `default` is accepted and ignored. It merely documents that the relation uses the default
  settings, e.g. `Customer: default`.

### Relation properties

#### `relation`

```
relation = <type> [modifiers...]
```

**Types** (choose one):

| Type       | Meaning                                                            |
|------------|--------------------------------------------------------------------|
| `object`   | 1:1 association (default).                                         |
| `list`     | 1:N association.                                                   |

Since `object` is the default, `relation = reversed` is short for `relation = object reversed`
(see the `reversed` modifier below).

There is deliberately **no `set` relation type**: a hash- or tree-based set of PDOs is unsound,
because a PDO's `hashCode`/`equals` are id-based and change when a new object is first persisted, and
because cyclic graphs are deserialized through partially built placeholders. `1:N` associations are
always ordered lists (a [`TrackedList`](../../../../tentackle-core/src/main/docs/misc.md#trackedlist--knowing-what-changed)
when the aggregate is tracked). See
[Object identity — why relations are lists](../../../../tentackle-pdo/src/main/docs/pdo.md#object-identity--equals-hashcode-and-why-relations-are-lists).

**Modifiers** (any combination):

| Modifier      | Description                                                                       |
|---------------|-----------------------------------------------------------------------------------|
| `component`   | Related entity is a component of this entity.                                     |
| `composite`   | Synonym to `component`. Read as: "relation creates a composite" (or aggregate).   |
| `tracked`     | Return a `TrackedList` instead of `List`. Default if aggregate entity is tracked. |
| `referenced`  | Set the parent reference on select and construction (default for components).     |
| `processed`   | Invoke a `processXXX` method on init, after select, and before save.              |
| `readonly`    | No setter method generated.                                                       |
| `writeonly`   | No getter method generated.                                                       |
| `nomethod`    | No getter or setter (shorthand for `readonly writeonly`).                         |
| `serialized`  | Reference is serializable (default for components).                               |
| `remoteclear` | Clear the relation before sending to server (for component relations).            |
| `shallow`     | Exclude from snapshots and copies.                                                |
| `immutable`   | Make the list and/or object(s) immutable when loaded from the database.           |
| `normtext`    | Add `toString()` of related entity (or all in list) to normtext.                  |
| `blunt`       | The generated `bluntXXX` method is added to the persistence interface.            |
| `reversed`    | Reversed 1:1: a list relation with at most one element — the foreign entity points back to this one, but the relation is accessed as a single object. |

#### `select`

```
select = [<strategy>[:<depth>]] [cached] [| wurbletArgs]
```

| Strategy   | Description                                                                                                                                                       |
|------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `lazy`     | Fetched on first access only. Default if not `cached`.                                                                                                            |
| `always`   | Fetched on every get (for very special cases, not recommended)                                                                                                    |
| `eager`    | Fetched together with the parent entity via left outer join (see [eager relations](../../../../tentackle-persistence-wurblets/src/main/docs/eager-relations.md)). |
| `embedded` | Default for embedded related entities (only used when printing the model).                                                                                        |

- `:<depth>` — only for `eager`: the number of relation hops the generated join tree may span, counting this
 relation as the first one. Defaults to `1`, which joins the related entity alone. A higher value additionally
 joins the `eager` relations of the related entity, and so on, until the depth is exhausted; the remaining
 levels are then fetched by follow-up selects. Because every generic read of the entity pays for the whole
 tree, raise it only for a small, bounded chain that is virtually always needed together — see
 [eager relations](../../../../tentackle-persistence-wurblets/src/main/docs/eager-relations.md).

```
select = eager:2
```

- `cached` — use caching select methods. Default if the related entity's `cached` option is set
 and the strategy is missing. Notice that "lazy cached" is allowed but won't update the loaded reference even if
 the cached object is modified!

- `| wurbletArgs` — extra arguments passed to the [`PdoSelectList`](../../../../tentackle-persistence-wurblets/src/main/docs/pdo-select.md) wurblet (list relations only):

```
select = lazy | -pilePosition
```

#### `delete`

```
delete = cascade
```

`cascade` — cascade-delete components (only necessary if integrity checks are disabled).

#### `link`

```
link = <methodName> [indexAttribute]
```

For component relations: name of the method called to set the link in
`saveReferencingRelations`. Use `set` alone as a placeholder for the model-derived method name.
If `indexAttribute` is given, the method is invoked as `method(this, ndx)` where `ndx`
is a 0-based index (lists only). `indexAttribute` may be a path for embedded attributes.

#### `args`

```
args = foreign-attribute2[method()|ConstantExpr] foreign-attribute3[method()|ConstantExpr] ...
```

Additional arguments for the generated `selectBy` / `deleteBy` methods of list relations and reversed object relations.
The methods are generated in the persistence layer of the related foreign entity.
Since the first method argument (foreign attribute) is always the current entity's ID, additional filter parameters start
at the second argument. Each argument is described as a pair of the foreign entity's attribute name and a 
method call or constant expression in square brackets.
The generated methods are then invoked with the ID plus the return values of the given method calls 
or constant expressions in the given order.

Example:
```
args = forCreditor[isCreditor()] lineType[CalculationLineType.ON_CALCULATION]
```

#### `method`

```
method = <MethodNamePart>
```

Overrides part of the following method names: `select[By]<methodname>`, `delete[By]<methodname>`, `set<methodname>`.
`[By]` is added for lists.

#### `name`

```
name = <RelationName>
```

Overrides the relation name (defaults to the class name). Getter/setter are generated as
`get<Name>` / `set<Name>` (object) or `get<Name>` / `set<Name>` (when explicitly set,
regardless of relation type). For lists without an explicit name: `get<ClassName>List` /
`set<ClassName>List`.

#### `prefix`

```
prefix = <columnPrefix>
```

Column prefix for embedded relations. Defaults to `<relationName>_` (lowercase).
Prefixes are concatenated for nested embedded entities.

#### `nm`

```
nm = <OppositeEntity> [mnMethodName] [nmScope]
```

Declares this list relation as the N:M link-table side.
- `OppositeEntity` — the entity on the other side of the N:M relation.
- `mnMethodName` — optional method name to access the list of opposite entities.
- `nmScope` — optional visibility scope for the nm method (e.g. `private`).

#### `scope`

```
scope = public|protected|package|private
```

Visibility of generated getter/setter methods (default: `public`).

#### `count`

```
count = <counterAttribute>
```

Numeric attribute used as a counter for component (non-reversed) list relations.

#### `comment`

```
comment = <text>
```

Comment text included in generated getter/setter Javadoc, overrides the comment
text from the relation line, if any.

#### Annotations and stereotypes on relations

Annotations and stereotypes are added separated by commas:

```
Customer: @NotNull(message="{ @('please_enter_the_customer') }", condition="{object?.invoiceDate != null}"),
          #COOL
```

### Relation examples

```
# 1:N component relation
InvoiceLine:
    relation = component list,
    link     = setInvoice position,
    name     = lines

# 1:1 object relation
Customer: @NotNull

# N:M relation
User2Group:
    relation = component list,
    name     = nmLinks,
    method   = UserId,
    nm       = UserGroup UserGroups
```

---

## 9. Comments

- A line whose first non-whitespace character is `#` is a **comment line**
- Everything after `#` (including the `#` itself) is comment text and becomes part of the model.
- The double-hash `##` convention is used for section headers, but is not
  syntactically special and is only provided for readability.
- Inline trailing text after the closing `]` of attribute options or in the
  column positions of an attribute line is treated as the attribute's comment.

---

## 10. Line Continuation

### Backslash continuation (any line)

A physical line ending with `\` is joined with the next line. The backslash and
any trailing whitespace before the newline are removed.

### Comma/colon continuation (attribute option lines and relation lines)

Multi-line blocks (attribute option lines and relation lines) are continued as
long as the last non-whitespace character on the line is `,` or `:`. The block
ends when a `;` is encountered, or when an empty line (or double newline) is found.

---

## 11. Variable Substitutions

The model source can contain `$variable` tokens that are resolved before parsing.
These are typically injected by the Wurblet preprocessor (`@{...@}` blocks in the
Java file) or by the build tool (see the Wurbelizer docs). Common variables:

| Variable     | Resolved to                                                    |
|--------------|----------------------------------------------------------------|
| `$classname` | Simple Java class name of the PDO interface.                   |
| `$classid`   | Numeric class ID from the `@ClassId` annotation.               |
| `$tablename` | Database table name from the `@TableName` annotation.          |
| `$mapping`   | Path to the external `.map` file (or inline block identifier). |
| `$integrity` | Project-default integrity mode.                                |

Application-defined variables (e.g. `$ou_name`, `$ou_comment`) can be introduced via the
`@{...@}` preprocessor block at the top of the file. A good place to put them is
the `<wurbletProperties>` section in the project's parent pom.

---

## 12. Complete Examples

### Root entity with a list of components (StoredBundle)

```java
/*
 * @> $mapping
 *
 * # resource bundle
 * name      := $classname
 * id        := $classid
 * table     := $tablename
 * alias     := bndl
 * integrity := composite
 *
 * ## attributes
 * [root, remote, tracked, tableserial]
 *
 * String(128)  name    bname    the resource bundle name [key]
 * String(8)    locale  blocale  the locale, null if default [key, mapnull]
 *
 * ## indexes
 * unique index udk := bname, blocale
 *
 * ## validations
 * name: @NotNull
 *
 * ## relations
 * StoredBundleKey:
 *     relation = component list,
 *     select   = eager,
 *     name     = Keys,
 *     method   = BundleId
 *
 * @<
 */
```

### Component entity with serialized back-reference (StoredBundleKey)

```java
/*
 * @> $mapping
 *
 * # bundle key with translation
 * name      := $classname
 * id        := $classid
 * table     := $tablename
 * alias     := bkey
 * integrity := composite
 *
 * ## attributes
 * [remote, tracked]
 *
 * long    bundleId  bundle_id  the bundle id
 * String  key       bkey       the resource bundle key
 * String  value     bvalue     the localized string
 *
 * ## indexes
 * index bundle := bundle_id
 *
 * ## validations
 * key:   @NotNull
 * value: @NotNull
 *
 * ## relations
 * StoredBundle:
 *     relation = serialized,
 *     name     = Bundle
 *
 * @<
 */
```

### Entity with inheritance and N:M relation (User)

```java
/*
 * @> $mapping
 *
 * # User
 * name      := $classname
 * table     := $tablename
 * id        := $classid
 * extends   := OrgUnit
 * integrity := $integrity
 *
 * ## attributes
 * [cached]
 *
 * String(64)  password                  password      hashed password [mute]
 * boolean     loginAllowed              login_allowed true if login is allowed
 * boolean     passwordChangeable        passwd_chgbl  true if user may change own password
 *
 * ## indexes
 *
 * ## relations
 * User2Group:
 *     relation = component list,
 *     name     = nmLinks,
 *     method   = UserId,
 *     nm       = UserGroup UserGroups
 *
 * ## validations
 *
 * @<
 */
```

---

## Related Documentation

- [Tentackle Model](./tentackle-model.md) — the model API module that parses and represents this DSL.
- [PDO / Persistent Domain Objects](../../../../tentackle-pdo/src/main/docs/pdo.md) — the objects whose persistence layer is generated from the model.
- [Wurblets](../../../../tentackle-wurblets/src/main/docs/wurblets.md) and [Persistence Wurblets](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md) — the code generators driven by the model.
- [Pdo Select](../../../../tentackle-persistence-wurblets/src/main/docs/pdo-select.md) and [Eager Relations](../../../../tentackle-persistence-wurblets/src/main/docs/eager-relations.md) — how `select` strategies in the `## relations` section turn into generated queries.
- [Validation](../../../../tentackle-core/src/main/docs/validation.md) — the annotations attached in the `## validations` section.
- [Embedded vs. Datatype](../../../../src/main/docs/embedded-vs-datatype.md) — choosing between an `embedded` entity and a custom attribute type.
- [Root Columns](../../../../tentackle-pdo/src/main/docs/root-columns.md) — the `rootid` / `rootclassid` options and how components find their root.
- [Locking](../../../../tentackle-persistence/src/main/docs/locking.md) — the `tokenlock` and `serial` options for optimistic and token locking.
- [Snapshots and Copies](../../../../tentackle-pdo/src/main/docs/snapshot.md) — what the `shallow` option excludes.
- [Cache](../../../../tentackle-pdo/src/main/docs/cache.md) — the `cached` entity option.
- [Tentackle SQL Maven Plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) — generates DDL and migrations from the model.
- [Tentackle Wizard Maven Plugin](../../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md) — the interactive PDO/operation wizard.
- [Tentackle MCP Maven Plugin](../../../../tentackle-mcp-maven-plugin/src/main/docs/tentackle-mcp-maven-plugin.md) — serves this document and the loaded model to coding agents, and validates a candidate definition without a build.
