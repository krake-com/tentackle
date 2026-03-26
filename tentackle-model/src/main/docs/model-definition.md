# Tentackle Model Definition Syntax

The Tentackle model definition is a DSL embedded as a Java comment block in a PDO interface source file.
The parser (`tentackle-model`) reads it at build time to drive code generation (wurblets, SQL DDL, persistence layer).

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

The model definition is organized into five logical sections, separated by
section headers (comment lines starting with `##`).
The sections must appear in this order:

```
# <entity description comment>
<configuration lines>

## attributes[<global-option-overrides>]
<global options line>
<attribute lines>

## indexes
<index lines>

## validations
<attribute option lines>

## relations
<relation lines>
```

Section headers are ordinary comment lines and do not affect parsing — they are
purely for readability. However the `## attributes[...]` notation is special:
options in `[...]` appended to the `## attributes` header can **subtract** global
model defaults (see [Global Options Line](#4-global-options-line)).

---

## 3. Configuration Lines

Configuration lines have the form:

```
<key> := <value>[;]
```

The trailing semicolon is optional. Leading and trailing whitespace around key and value is trimmed.

### Entity configuration keys

| Key          | Value                                | Description |
|--------------|--------------------------------------|-------------|
| `name`       | string (often `$classname`)          | Entity name. Must be unique in the model. |
| `id`         | integer (often `$classid`)           | Numeric class ID used for serialization and mapping. |
| `table`      | string (often `$tablename`)          | Database table name. May include schema: `schema.tablename`. |
| `alias`      | string                               | Short table alias used in SQL joins. Auto-generated if omitted. |
| `integrity`  | see [Integrity Modes](#integrity-modes) | Referential integrity strategy. |
| `comment`    | string                               | Entity-level description. |
| `extends`    | entity name                          | Inheritance: this entity extends the named entity. |
| `inheritance`| `single`, `joined`, `perclass`, `multi` | Inheritance mapping strategy. |

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

### Example

```
name := $classname
id   := $classid
table := $tablename
alias := inv
integrity := related
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

| Option           | Description |
|------------------|-------------|
| `root`           | This is a root entity (owns its aggregate). |
| `remote`         | Supports remote access via TRIP. |
| `tracked`        | Track modification state (short for `tracked` tracktype). |
| `fulltracked`    | Full tracking of all changes. |
| `tokenlock`      | Adds optimistic-lock token columns. |
| `tableserial`    | Adds a table-serial column (monotonic counter per row). |
| `normtext`       | Adds a `normtext` column for full-text search. |
| `rootid`         | Adds a root-ID column (filled with the root entity's id). |
| `rootclassid`    | Adds a root-class-ID column. |
| `nopkey`         | No auto-generated primary key. |
| `nomodeldefaults`| Ignore global model defaults entirely. |
| `cached`         | Entity provides a cache (root entities only). |
| `provided`       | DDL is maintained externally; no SQL generation. |
| `bind`           | Apply default binding options to all attributes. |
| `autoselect`     | Apply autoselect to all attributes. |
| `size`           | Apply default size constraint to string attributes. |
| `#Name`          | Custom stereotype `Name`. |

### Default sorting

After `|`, list attribute names to define the default sort order. Prefix with
`+` for ascending (default) or `-` for descending:

```
[tokenlock, tracked | +name -id]
```

### Subtracting model defaults

Global defaults can be subtracted inside the `## attributes[...]` header notation:

```
## attributes[-bind, -autoselect, -size]
```

This removes the three named defaults from those inherited from the project-level model defaults.

### Example

```
[root, remote, tracked, tokenlock, tableserial, #CUSTOM | -id total]
```

---

## 5. Attribute Lines

An attribute line maps a Java field to a database column:

```
javaType[<innerType>][(size[,scale])]  javaName  columnName  [comment]  [[options]]
```

All four positional fields are required (comment and options are optional).

### Java types

| Category   | Types |
|------------|-------|
| Primitives | `boolean`, `byte`, `char`, `short`, `int`, `long`, `float`, `double` |
| Wrappers   | `Boolean`, `Byte`, `Character`, `Short`, `Integer`, `Long`, `Float`, `Double` |
| Numeric    | `BigDecimal`, `BMoney` (big money), `DMoney` (double money) |
| Other      | `String`, `Date`, `Time`, `Timestamp`, `Binary` |
| Custom     | Any other class name (application-specific type) |

### Size and scale

Appended directly to the type: `String(128)`, `Double(10,2)`, `BigDecimal(15,4)`.

### Inner/generic type

Use angle brackets: `Binary<MyClass>`, `MyType<int>`.

### Attribute options

Options follow the comment inside `[...]`. Multiple options are comma-separated.

#### Value options

| Option                   | Description |
|--------------------------|-------------|
| `default <value>`        | Default value if column is null when reading. String values should be quoted: `default "hello"`. Timestamps: `default 1970-01-01 00:00:00.000`. Booleans: `default true`. |
| `init <expr>`            | Java expression to initialize the field declaration. |
| `new <expr>`             | Java expression to set the field before first-time persistence. |

#### Semantic options

| Option       | Description |
|--------------|-------------|
| `key`        | Marks this attribute as part of the unique domain key (UDK). |
| `contextid`  | This attribute holds the context entity's ID. |
| `utc`        | Timestamp is stored and retrieved in UTC. |
| `tz` / `withtimezone` | Apply a dedicated timezone for reads/writes (requires a `get<Attr>TimezoneConfig()` method on the PDO). |
| `unsigned`   | Numeric field is unsigned. |
| `digitsonly` | String field accepts only digit characters. |
| `normtext`   | Include this attribute's value in the entity's normtext column. |
| `shallow`    | Exclude this attribute from snapshot and copy operations. |
| `hidden`     | Attribute is not declared in the interface (implementation only). |
| `mute`       | Not part of the PDO — mapped to the database table only. |
| `fromsuper`  | Attribute is inherited from a super entity; do not redeclare. |

#### Access and generation options

| Option               | Description |
|----------------------|-------------|
| `readonly`           | Generate getter only; no setter. |
| `writeonly`          | Generate setter only; no getter. |
| `nomethod`           | Generate neither getter nor setter. |
| `nodeclare`          | Do not emit the field declaration code. |
| `noconstant`         | Do not generate the name constant. |
| `scope=<access>`     | Visibility of generated methods: `public` (default), `protected`, `package`, `private`. |

#### String handling options

| Option       | Description |
|--------------|-------------|
| `uc` / `uppercase` | Convert to uppercase on write. |
| `lc` / `lowercase` | Convert to lowercase on write. |
| `trimread`   | Trim whitespace when reading from the database. |
| `trimwrite`  | Trim whitespace when writing to the database. |
| `mapnull`    | Map `null` to a constant (typically empty string `""`) in the database. |

#### Binding options

| Option           | Description |
|------------------|-------------|
| `bindable`       | Generate `@Bindable` annotation. |
| `bind=<opts>`    | Binding options string. |
| `autoselect`     | Enable auto-select behaviour for this attribute. |
| `maxcol`         | Apply `maxcol` binding option. |

#### Annotation options

Annotations can be attached to getter/setter methods. The `@` prefix is mandatory.
An optional modifier immediately after `@` controls placement:

| Modifier | Placement |
|----------|-----------|
| (none)   | Getter in interface. |
| `=`      | Setter only. |
| `+`      | Both getter and setter. |
| `~`      | Hidden: applied to implementation, not the interface. |

Modifiers can be combined, e.g. `@=~MyAnno` places the annotation on the setter implementation only.

Example: `[@NotNull, @=~MySetterAnno(value="x")]`

#### Custom stereotypes

A `#Name` token inside attribute options adds a custom stereotype to the attribute.
Example: `[#WARM, @NotNull]`

### Attribute line examples

```
String(128)   name          bname         the resource bundle name [key]
String(8)     locale        blocale       the locale, null if default [key, mapnull]
long          bundleId      bundle_id     the bundle id
Timestamp     printed       printed       first printed [utc, default 1970-01-01 00:00:00.000]
Double(10,2)  someDouble    some_double   digits with scale
Binary<Blob>  attachment    attachment    binary attachment
boolean       active        active        true if active [default true]
String(64)    password      password      hashed password [mute]
```

---

## 6. Attribute Option Lines (Validation Section)

Used in the `## validations` section to attach annotations to attributes (typically
Bean Validation constraints). The syntax is:

```
attributeName: @Anno1, @Anno2(param=value), ...
```

- The **attribute name** (or relation name) is followed by a colon.
- Multiple annotations are comma-separated.
- The block can span multiple lines: continuation is indicated by a trailing `,` or `:`.
  The block ends with `;`, an empty line, or two consecutive newlines.

Option syntax inside annotations follows standard Java annotation syntax. EL expressions
in string values are supported: `condition="{ object?.printed == null }"`.

### Examples

```
# single annotation
name: @NotNull

# multiple annotations, multi-line
total: @NotNull(scope=PersistenceScope.class),
       @Greater(value="0", scope=PersistenceScope.class, condition="{ object.printed != null }"),
       #WARM

# conditional annotation
uplink: @NotNull(condition="$slave")

# validation on a relation
Lines: @NotNull
```

---

## 7. Index Lines

Index lines appear in the `## indexes` section.

```
[unique] index <indexName> := <col1> [asc|desc], <col2> [asc|desc], ...
```

- `unique` — creates a unique index.
- `<indexName>` — identifier for the index (becomes part of the generated DDL name).
- Columns are database column names (not Java names), comma-separated.
- `asc` / `desc` are optional sort direction suffixes.

### Examples

```
unique index udk         := bname, blocale
index bundle             := bundle_id
unique index invoice_no  := subsidiary_id desc, invoiceNo
index invoice_date       := invoice_date
index entity             := objectid, objectclassid
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
- The block can span multiple lines (same continuation rules as attribute option lines).

### Relation properties

#### `relation`

```
relation = <type> [modifiers...]
```

**Types** (choose one):

| Type       | Meaning |
|------------|---------|
| `object`   | 1:1 association (default). |
| `list`     | 1:N association. |
| `reversed` | Reversed 1:1 (non-composite list that returns at most one object). |

**Modifiers** (any combination):

| Modifier     | Description |
|--------------|-------------|
| `composite`  | Related entity is a composite (root owns it). Default if the related entity is embedded. |
| `component`  | Related entity is a component (part of an aggregate). |
| `tracked`    | Track modification state of the relation. |
| `referenced` | Set the parent reference on select and construction. |
| `processed`  | Invoke a `processXXX` method on init, after select, and before save. |
| `readonly`   | No setter method generated. |
| `writeonly`  | No getter method generated. |
| `nomethod`   | No getter or setter (shorthand for `readonly writeonly`). |
| `serialized` | Lazy non-composite reference is serializable (default is transient). |
| `remoteclear`| Clear the relation before sending to server (for serialized relations). |
| `shallow`    | Exclude from snapshots and copies. |
| `immutable`  | Set relation immutable after initial load. |
| `normtext`   | Add `toString()` of related entity (or all in list) to normtext. |
| `blunt`      | The blunt method becomes part of the persistence interface. |

#### `select`

```
select = <strategy> [cached] [| wurbletArgs]
```

| Strategy   | Description |
|------------|-------------|
| `always`   | Fetched on every get. Default if `cached`. |
| `eager`    | Fetched together with the parent entity. |
| `lazy`     | Fetched on first access only. Default if not `cached`. |
| `embedded` | Fetched by parent (default for embedded entity types). |

`cached` — use caching select methods (object relations only).

`| wurbletArgs` — extra arguments passed to the `PdoSelectList` wurblet (list relations only):

```
select = lazy | -pilePosition
```

#### `delete`

```
delete = cascade
```

`cascade` — cascade-delete composite list components when integrity checks are disabled.

#### `link`

```
link = <methodName> [indexAttribute]
```

For composite relations: name of the method called to set the link in
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

Numeric attribute used as a counter for composite non-reversed list relations.

#### `comment`

```
comment = <text>
```

Comment text included in generated getter/setter Javadoc.

#### Annotations on relations

Annotations are added as bare properties (no `=`):

```
Customer: select = cached,
          @NotNull(message="{ @('please_enter_the_customer') }", condition="{object?.invoiceDate != null}"),
          #COOL
```

### Relation examples

```
# eager composite 1:N
StoredBundleKey:
    relation = composite list,
    select   = eager,
    name     = Keys,
    method   = BundleId

# serialized back-reference
StoredBundle:
    relation = serialized,
    name     = Bundle

# component with link and cascaded delete
InvoiceLine:
    relation = component referenced list,
    link     = setInvoice position,
    name     = lines,
    select   = lazy

# cached object reference
Customer: select = cached,
          @NotNull

# N:M relation
User2Group:
    relation = component list,
    name     = nmLinks,
    method   = UserId,
    nm       = UserGroup UserGroups
```

---

## 9. Comments

- A line whose first non-whitespace character is `#` is a **comment line**.
- Everything after `#` (including the `#` itself) is comment text.
- The double-hash `##` convention is used for section headers, but is not
  syntactically special.
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
Java file) or by the build tool. Common variables:

| Variable      | Resolved to |
|---------------|-------------|
| `$classname`  | Simple Java class name of the PDO interface. |
| `$classid`    | Numeric class ID from the `@ClassId` annotation. |
| `$tablename`  | Database table name from the `@TableName` annotation. |
| `$mapping`    | Path to the external `.map` file (or inline block identifier). |
| `$integrity`  | Project-default integrity mode. |
| `$slave`      | Boolean condition expression (project-specific). |

Application-defined variables (e.g. `$ou_name`, `$ou_comment`) can be introduced via the
`@{...@}` preprocessor block at the top of the file.

---

## 12. Complete Annotated Examples

### Root entity with composite list (StoredBundle)

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
 *     relation = composite list,
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

### Comprehensive entity with multiple features (Invoice)

```java
/*
 * @> $mapping
 *
 * ## Root entity for invoice lines.
 * name      := Invoice
 * id        := 1
 * comment   := the invoice header
 * table     := invoice
 * integrity := related
 *
 * ## attributes
 * [size, autoselect, tracked, tokenlock, tableserial, #CUSTOM | -id total]
 *
 * long         subsidiaryId   subsidiary_id  subsidiary ID [contextid, @MyAnno("blah")]
 * String(10)   invoiceNo      invoice_no     the unique invoice number [uc]
 * Date         invoiceDate    invoice_date   the invoice's date
 * Long         customerId     customer_id    object ID of the customer [uc, @NotNull, #blah="wow!"]
 * String(200)  address        address        the address [default "blah\nx ", uc]
 * DMoney       total          total          sum (without tax)
 * Timestamp    printed        printed        first printed [utc, default 1970-01-01 00:00:00.000]
 * Double(10,2) someDouble     some_double    digits with scale
 * Boolean      testBool       testbool       some boolean [default true]
 *
 * ## indexes
 * unique index invoice_no      := subsidiary_id desc, invoiceNo
 * index invoice_customer       := customer_id
 * index invoice_date           := invoice_date
 *
 * ## relations
 * InvoiceLine:
 *     relation = component referenced list,
 *     link     = setInvoice position,
 *     comment  = the invoice lines,
 *     name     = lines,
 *     select   = lazy
 *
 * Customer: select = cached,
 *           @NotNull(message="{ @('please_enter_the_customer') }", condition="{object?.invoiceDate != null}"),
 *           #COOL
 *
 * ## validations
 * invoiceNo:   @NotNull(message="{ @('please_enter_the_invoice-number') }")
 * invoiceDate: @NotNull(scope=AllScope.class, message="{ @('please_enter_the_invoice-date') }", condition="{ object?.printed == null }")
 * total:       @NotNull(scope=PersistenceScope.class),
 *              @Greater(value="0", scope=PersistenceScope.class, condition="{ object.printed != null }"),
 *              #WARM
 * Lines:       @NotNull
 *
 * @<
 */
```
