# Tentackle Model Definition Syntax

The Tentackle model definition is a DSL embedded as a Java comment block in a PDO interface source file.
The model (`tentackle-model`) is parsed at build time to drive the code generation (persistence interfaces
and implementations, documentation), DDL generation, database migration and code analysis tools. 
It is primarily used by the Wurbelizer code generator, the Tentackle SQL-, the PDO-wizard- and the PDO-browser 
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

| Key           | Value                                                                 | Description                                                                  |
|---------------|-----------------------------------------------------------------------|------------------------------------------------------------------------------|
| `name`        | string (often `$classname`)                                           | Entity name (class simple name). Must be unique in the model.                |
| `id`          | integer (often `$classid`)                                            | Unique numeric class ID (non-abstract classes only).                         |
| `table`       | string (often `$tablename`)                                           | Database table name (if applicable). May include schema: `schema.tablename`. |
| `alias`       | string                                                                | Short table alias used in SQL joins. Auto-generated if omitted.              |
| `integrity`   | see [Integrity Modes](#integrity-modes)                               | Referential integrity strategy.                                              |
| `comment`     | string                                                                | optional entity description (overriding any previous comment)                |
| `extends`     | entity name                                                           | Inheritance: this entity extends the named entity.                           |
| `inheritance` | see [Inheritance Mapping Strategies](#inheritance-mapping-strategies) | Inheritance mapping strategy.                                                |

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

| Option        | Description                                                               |
|---------------|---------------------------------------------------------------------------|
| `root`        | This is a root entity.                                                    |
| `rootid`      | Adds the `rootId` attribute (filled with the root entity's id).           |
| `rootclassid` | Adds the `rootClassId` attribute (for multi-homed components).            |
| `remote`      | Supports remoting via TRIP.                                               |
| `tracked`     | Tracks whether the PDO was modified or not.                               |
| `attracked`   | Tracks the modification state per attribute.                              |
| `fulltracked` | Full tracking per attribute including access to the old persisted value.  |
| `untracked`   | No tracking (default, also used to override the model default).           |
| `tokenlock`   | Adds the tokenlock attributes (`editedBy`, `editedSince`, `editedExpiry`) |
| `tableserial` | Adds the `tableSerial` attribute.                                         |
| `normtext`    | Adds the `normText` attribute for full-text search.                       |
| `nopkey`      | No primary key `id`.                                                      |
| `nodefaults`  | Ignore global model defaults entirely.                                    |
| `cached`      | Entity provides a cache (root entities only).                             |
| `provided`    | DDL is maintained externally; no SQL generation.                          |
| `bind`        | Apply default binding options to all attributes.                          |
| `autoselect`  | Apply autoselect binding hints to all attributes.                         |
| `size`        | Apply size binding hints to all attributes.                               |
| `#Name`       | Custom stereotype `Name`.                                                 |

Case doesn't matter for the options.
Most of these options are inherited from the project-level model defaults and need
not be specified in the global options line. Typical model defaults are:

```
<tentackle.modelDefaults>remote, bind, size, autoselect, tracked, root, rootid, rootclassid</tentackle.modelDefaults>
```

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

### Overriding model defaults

The global project-wide model defaults can be disabled by prepending a `-` to the option or
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

| Category   | Types                                                                                                                                                                                 |
|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Primitives | `boolean`, `byte`, `char`, `short`, `int`, `long`, `float`, `double`                                                                                                                  |
| Wrappers   | `Boolean`, `Byte`, `Character`, `Short`, `Integer`, `Long`, `Float`, `Double`                                                                                                         |
| Numeric    | `BigDecimal`, `BMoney` (big money), `DMoney` (double money)                                                                                                                           |
| Other      | `String`, `I18NText`, `Date`, `Time`, `Timestamp`, `LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetDateTime`, `OffsetTime`, `ZonedDateTime`, `Instant`, `UUID`, `Binary`, `TBinary` |
| Custom     | Any other class name (application-specific type)                                                                                                                                      |

### Size and scale

Appended directly to the type: `String(128)`, `Double(10,2)`, `BigDecimal(15,4)`.

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

#### Semantic options

| Option       | Description                                                                                             |
|--------------|---------------------------------------------------------------------------------------------------------|
| `key`        | Marks this attribute as part of the unique domain key (UDK).                                            |
| `contextid`  | This attribute holds the context entity's ID.                                                           |
| `utc`        | Timestamp is stored and retrieved in UTC.                                                               |
| `tz`         | Apply a dedicated timezone for reads/writes (requires a `get<Attr>TimezoneConfig()` method on the PDO). |
| `unsigned`   | Numeric field is unsigned.                                                                              |
| `digitsonly` | String field accepts only digit characters.                                                             |
| `normtext`   | Include this attribute's value in the entity's normtext column.                                         |
| `shallow`    | Exclude this attribute from snapshot and copy operations.                                               |
| `hidden`     | Attribute is not declared in the interface (implementation only).                                       |
| `mute`       | Not part of the PDO — mapped to the database table only.                                                |
| `mapnull`    | Map `null` to a constant (typically empty string `""` or zero) in the database.                         |

#### Access and generation options

| Option           | Description                                                                             |
|------------------|-----------------------------------------------------------------------------------------|
| `readonly`       | Generate getter only; no setter.                                                        |
| `writeonly`      | Generate setter only; no getter.                                                        |
| `nomethod`       | Generate neither getter nor setter.                                                     |
| `nodeclare`      | Do not emit the field declaration code.                                                 |
| `noconstant`     | Do not generate the name constant.                                                      |
| `super`          | Attribute is inherited from a super entity (if not in model of superclass)              |
| `scope=<access>` | Visibility of generated methods: `public` (default), `protected`, `package`, `private`. |

#### String handling options

| Option      | Description                                     |
|-------------|-------------------------------------------------|

| `trimread`  | Trim whitespace when reading from the database. |
| `trimwrite` | Trim whitespace when writing to the database.   |
| `trim`      | `trimread` + `trimwrite`                        |

#### Binding options

| Option    | Description                                  |
|-----------|----------------------------------------------|
| `bind`    | Annotate with `@Bindable` to enable binding. |
| `size`    | Apply size binding hint.                     |
| `maxcols` | explicit maximum columns                     |
| `cols`    | explicit visible columns                     |
| `lines`   | explicit visible rows (textarea)             |
| `scale`   | explicit numeric scale                       |
| `uc`      | Convert to uppercase                         |
| `lc`      | Convert to lowercase                         |

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
validation constraints). The syntax is:

```
attributeName: @Anno1, @Anno2(param=value), ...
```

- The **attribute name** (or relation name) is followed by a colon.
- Multiple annotations are comma-separated.
- The block can span multiple lines: continuation is indicated by a trailing `,`.
  The block ends with `;`, an empty line, or two consecutive newlines.

Option syntax inside annotations creates a `org.tentackle.misc.CompoundValue`.
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
[unique | optional] index <indexName> := [+|-]<col1> [asc|desc], <col2>, ... [|/where <filter>]
```

- `unique` — creates a unique index.
- `optional` — create only if backend supports it (mainly for in-memory databases used for CI)
- `<indexName>` — identifier for the index (becomes part of the generated DDL name).
- Columns are database column names or Java attribute names, comma-separated.
- `asc` / `desc` or `+` / `-` are optional sort direction suffixes.
- following `where` or `|`: optional SQL expression defining a filtered (a.k.a. conditional) index.

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
| `reversed` | Reversed 1:1 (non-component list that returns at most one object). |

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

#### `select`

```
select = [<strategy>] [cached] [| wurbletArgs]
```

| Strategy   | Description                                                                |
|------------|----------------------------------------------------------------------------|
| `lazy`     | Fetched on first access only. Default if not `cached`.                     |
| `always`   | Fetched on every get (for very special cases, not recommended)             |
| `eager`    | Fetched together with the parent entity via left outer join.               |
| `embedded` | Default for embedded related entities (only used when printing the model). |

- `cached` — use caching select methods. Default if the related entity's `cached` option is set
 and the strategy is missing. Notice that "lazy cached" is allowed but won't update the loaded reference even if
 the cached object is modified!

- `| wurbletArgs` — extra arguments passed to the `PdoSelectList` wurblet (list relations only):

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
args = attr1[method()] attr2[ConstantExpr]
```

Additional arguments for generated `selectBy` / `deleteBy` methods (list relations only).
The first argument is always the current entity's ID and cannot be overridden.

#### `method`

```
method = <MethodNamePart>
```

Overrides the method-name fragment used in `selectBy<Name>`, `deleteBy<Name>`, `set<Name>`.

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
