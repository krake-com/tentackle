# Embedded Entities vs. Application-Specific DataTypes

## The question this document answers

Sooner or later every Tentackle model needs to store something that is *more than a plain column but less than a
root entity* — an address, a money amount with its currency, a measurement with its unit, a product number with a
fixed internal layout, a validity period, an audit stamp. Tentackle offers **two** very different ways to model
such a thing, and choosing the wrong one leads to either over-engineering or a painful migration later:

- an **embedded entity** — a full Tentackle entity whose columns are *folded into the owner's table* instead of
  living in a table of its own;
- an **application-specific DataType** — a custom Java *value type* that teaches the persistence layer how to map
  one Java object to one (or several) database columns.

They look superficially similar — both let a sub-structure ride along inside a parent row — but they are not the
same *kind of thing* at all, and the single sharpest way to see the difference is this:

> An **embedded entity is `DomainContext`-aware: it is a real domain object that knows its session, its aggregate
> root, its tenant, and its security scope.** An **application-specific DataType is just a value** — an inert lump
> of data with conversion rules, that knows nothing about who is asking, which tenant it belongs to, or where it
> lives.

Everything else — how they are declared, how they are stored, when to pick one — follows from that distinction.
The rest of this document develops it, shows how each is declared, and gives a decision procedure with worked
examples, including the *same* concept (a product number) modeled both ways.

---

## The decisive difference: a context-aware object vs. a bare value

The [`DomainContext`](../../../tentackle-pdo/src/main/java/org/tentackle/pdo/DomainContext.java) is, per the
[PDO documentation](../../../tentackle-pdo/src/main/docs/pdo.md#the-domaincontext-and-ddd), "the single most
important concept for understanding how PDOs relate to Domain-Driven Design." It answers three questions for an
object *at all times*:

1. **In which session does it live?** — the database/remote session it loads and saves through.
2. **Which aggregate does it belong to?** — through the *root context*, every component knows its root entity
   without a query.
3. **Which logical data space (tenant) does it belong to?** — through optional extensions that scope queries.

An **embedded entity is a PDO**, so it carries a `DomainContext` and inherits *all three* answers. That makes it a
participant in the framework's correctness machinery:

- **It belongs to an aggregate.** An embedded `Address` inside a `Customer` shares the customer's **root context**,
  so it is saved, validated, snapshotted, and made [immutable](../../../tentackle-core/src/main/docs/immutable.md)
  *as part of the customer*, as one consistent unit.
- **It is security- and tenant-scoped.** The `SecurityManager` and tenant scoping apply to it through the same
  context that governs its owner — a permission expressed on the aggregate root governs the embedded value too,
  with no extra query.
- **It is validated in context.** Its [validations](../../../tentackle-core/src/main/docs/validation.md) run with
  the owner's scope, and the framework's fail-fast aggregate rules (a component cannot be persisted in the wrong
  context; a component read outside its root context becomes immutable) apply.
- **It travels with its context across JVMs.** When the owner moves over [TRIP](../../../tentackle-core/src/main/docs/trip.md),
  the context travels too and `DomainContext.assertPermissions()` is re-checked on arrival.

An **application-specific DataType is none of this.** It is *just a value*. It has:

- **no session** — it cannot load, save, navigate, or query anything; it is read and written as column data by the
  entity that owns it;
- **no aggregate membership** — it is not a component, has no root context, and plays no part in
  aggregate-consistency rules;
- **no tenant or security scope** — it does not know who is asking or which logical data space it lives in; any
  such rules belong to the *entity* that holds it, never to the value;
- **no identity or lifecycle** — it is created, converted, and discarded like an `Integer` or a `String`.

In fact the `DataType` *service* itself is a **stateless singleton** that merely describes how to convert a Java
value to and from column(s). The value it maps is inert by design. This is not a limitation to work around — it is
the whole point: a DataType is the right tool *precisely when* the thing you are storing is a pure value that
should carry no context.

> **One sentence to remember:** if the thing needs to know *its session, its aggregate, its tenant, or its
> permissions*, it must be a (possibly embedded) **entity**; if it is a self-contained **value** that is the same
> regardless of who holds it or where, it is a **DataType**.

---

## Embedded entities

### What they are

An embedded entity is an ordinary entity in the [model](../../../tentackle-model/src/main/docs/model-definition.md)
— it has a model definition, attributes, validations, and (optionally) its own domain behavior — but its
**inheritance mapping strategy is `embedded`**, so it does not get a table of its own. (Because it has no table,
it declares **no indexes** and owns **no components** of its own. It *may*, however, embed further embedded
entities and hold **outgoing relations** — references/associations to other entities.) Instead, the owning entity
declares a relation to it, and the embedded entity's columns are written **into the owner's table**, each prefixed
to keep names unique:

```
| Strategy   | Description                                                          |
|------------|----------------------------------------------------------------------|
| embedded   | Embedded entity mapped into a parent entity via an embedding relation |
```

The owner references it as a normal `object` relation; the column prefix defaults to `<relationName>_`
(lowercase), and **prefixes concatenate for nested embedded entities**. There is no separate row and no separate
`id`, but — and this is the point — there *is* a `DomainContext`: the embedded entity shares the owner's root
context and is, in every behavioral sense, a member of the owner's aggregate.

### How it is declared (sketch)

The embedded type is a model like any other, but mapped `embedded`:

```
# Address — embedded value entity, no table of its own
name        := Address
inheritance := embedded

## attributes
String(64)  street   street
String(16)  zip      zip
String(64)  city     city
```

> An embedded entity declares **no `## indexes` section of its own** — it has no table to index. Indexes over its
> columns are declared on the **owning** entity, which references the embedded attribute by a path (see below).

An owner embeds it through a relation, which controls the column prefix and may index the embedded columns:

```
## relations
Address:
    relation = object,
    name     = billing            # columns become billing_street, billing_zip, billing_city
Address:
    relation = object,
    name     = shipping           # columns become shipping_street, shipping_zip, shipping_city

## indexes
index billing_zip := billing.zip   # the OWNER indexes an embedded attribute by path
```

The generated `Customer` then has `getBillingAddress()` / `getShippingAddress()` returning real `Address` objects,
each sharing the customer's domain context, and the `customer` table carries `billing_street`, `billing_zip`, …,
`shipping_street`, … as ordinary columns the owner can index, filter, and sort by.

### What you get

- **Context-awareness (the headline).** It is a PDO bound to the owner's `DomainContext`: it knows its session,
  belongs to the aggregate, and is governed by the same tenant and security scope. It participates in the
  aggregate-consistency safeguards described in
  [pdo.md → Aggregates prevent whole classes of bugs](../../../tentackle-pdo/src/main/docs/pdo.md#aggregates-prevent-whole-classes-of-bugs).
- **Individually-mapped columns.** Every attribute is its own real column with its own name in the owner's table.
  The **owner** can declare an **index** over it (by a path such as `billing.zip`), and you can write a `WHERE`
  clause on it and sort by it — backend-agnostically.
- **A real domain object.** It can carry behavior, per-attribute, scope/severity-aware
  [validations](../../../tentackle-core/src/main/docs/validation.md), and **outgoing relations** — it may reference
  or associate to other entities (e.g., an embedded `Address` pointing to a `Country`).
- **Reuse across owners.** The same `Address` type embeds into `Customer`, `Supplier`, `Site`, each with its own
  prefix — define once, embed many times.
- **Nesting and references, but no owned components.** Embedded entities compose: a `GeoLocation` can embed inside
  `Address` inside `Customer`, the prefixes stacking up, and they may hold outgoing relations to other entities.
  What they may **not** do is own **components** (separate-table aggregate parts) or declare indexes of their own
  (see below).

### What it costs

- **It is a full entity to define and maintain** — a model, generated interfaces and implementation classes, a
  place in the module graph. Real ceremony for something small.
- **It is not a free-floating type.** You embed it via a *relation* on a specific owner; it is not something you
  drop into an arbitrary attribute slot the way you use `String` or a DataType.

---

## Application-specific DataTypes

### What they are

A DataType is the persistence layer's notion of *"a Java type the model is allowed to use for an attribute, and
the rules for reading/writing it to the database."* Tentackle ships DataTypes for the common Java and Tentackle
types (`String`, `BigDecimal`, `BMoney`, the date/time types, `UUID`, …); an application adds its **own** by
implementing [`org.tentackle.sql.DataType<T>`](../../../tentackle-sql/src/main/java/org/tentackle/sql/DataType.java)
and registering it as a service:

> Implementations must be annotated with `@Service(DataType.class)`. DataTypes are singletons … hence they must
> not maintain any state … The application may define their own types (that's why `DataType` is not an enum).

That statelessness is the structural signature of "just a value": the DataType singleton describes a *mapping*
(how many columns, the `SqlType`/size/scale per backend, how to read a column into the Java object and back), and
the value it maps carries no session, no context, no identity. `isPredefined()` must return `false` for an
application type, which tells the wurblets to emit **generic** read/write code rather than assume hand-written
type-specific methods exist.

Once registered, the type is usable in any attribute line of any entity — and notably, it is *the same value*
whatever entity, tenant, or session happens to hold it:

```
ProductNumber  productNumber  product_number   the coil identifier
```

### Two flavors, by column count

The model-definition guide states the rule directly:

> If an application-specific type maps to only **one** database column, the column's Java type can be wrapped by an
> outer type (see [`org.tentackle.misc.Convertible`](../../../tentackle-core/src/main/java/org/tentackle/misc/Convertible.java)).
> If it maps to **multiple** columns, an application-specific type must be defined (see `org.tentackle.sql.DataType`).

1. **Single-column → `Convertible<T>` (the lightweight path).** Implement `Convertible<T>` with `toExternal()`
   (instance → column value), a static `toInternal(T)` (column value → instance) and a static `getDefault()`. In
   the model you write the wrapper syntax `OuterType<columnType>`, e.g. `Priority<Integer>`. Ideal for enums and
   thin wrappers — an enum that persists as an `int`:

   ```java
   public enum Priority implements Convertible<Integer> {
     LOW(10), NORMAL(20), HIGH(30);
     private final Integer code;
     Priority(Integer code) { this.code = code; }
     public Integer toExternal()                  { return code; }
     public static Priority toInternal(Integer c) { /* lookup */ }
     public static Priority getDefault()          { return NORMAL; }
   }
   ```

2. **Multi-column or backend-specific → a full `DataType` (the heavyweight path).** When one value needs several
   columns (the built-in `BMoney` maps to two — amount and scale), or different SQL types/sizes for different
   backends, implement the full `DataType<T>` interface. This is more work and comes with deployment rules: the
   type should live in **its own module** (so the PDO modules don't drag in `tentackle-sql`), and that module must
   be registered with the wurbelizer plugin (`wurbletDependency`) and the tentackle-sql plugin.

### A worked example: a three-column `Position3D`

A real multi-column DataType from an industrial Tentackle application: a gripper's `X/Y/Z` position, where the
three coordinates always travel together as one value but each must reach its own integer column.

The **value** is a plain immutable record — a pure value with no `DomainContext`. It knows how to parse and format
itself (`valueOf` / `toString` over an `"x/y/z"` string), offers a little behavior (`midPoint`, copy-`with`
helpers), and carries TRIP hints, but it is the *same* position regardless of who holds it. The `@RecordDTO`
annotation ([DTO wurblet](../../../tentackle-wurblets/src/main/docs/dto-wurblet.md)) is what lets the build add a
builder and the copy-`with` factories to the record:

```java
@RecordDTO
public record Position3D(int x, int y, int z) implements Serializable {

  public static final Position3D ORIGIN = new Position3D(0, 0, 0);

  /** Parse "x/y/z" — also recognises the model literal "Position3D.ORIGIN". */
  public static Position3D valueOf(String str) { /* tokenize on '/' */ }

  /** Parsable "x/y/z" form. */
  @Override public String toString() { return x + "/" + y + "/" + z; }

  public Position3D midPoint(Position3D other) { /* ... */ }
}
```

The **mapping** is a separate, stateless `@Service(DataType.class)` singleton that teaches the persistence layer
how to spread that one value across three columns. The essentials:

```java
@Service(DataType.class)
public class Position3DType extends AbstractDataType<Position3D> {

  @Override public String  getJavaType()                 { return "Position3D"; }
  @Override public boolean isPredefined()                { return false; }       // emit generic read/write code
  @Override public int     getColumnCount(Backend b)     { return 3; }

  // one INTEGER column per coordinate, suffixed _x / _y / _z onto the attribute name
  @Override public SqlType getSqlType(Backend b, int i)  { return SqlType.INTEGER; }
  @Override public Optional<String> getColumnSuffix(Backend b, int i) {
    return Optional.of(switch (i) { case 0 -> "_x"; case 1 -> "_y"; case 2 -> "_z";
                                    default -> throw new IndexOutOfBoundsException(i); });
  }

  // value -> columns
  @Override public Object[] set(Backend b, PreparedStatement ps, int pos,
                                Position3D v, boolean mapNull, Integer size) throws SQLException {
    if (v == null) { /* setNull x3 */ return new Object[] { null, null, null }; }
    ps.setInt(pos, v.x()); ps.setInt(pos + 1, v.y()); ps.setInt(pos + 2, v.z());
    return new Object[] { v.x(), v.y(), v.z() };
  }

  // columns -> value (a null first column means the whole value is null)
  @Override public Position3D get(Backend b, ResultSet rs, int[] pos,
                                  boolean mapNull, Integer size) throws SQLException {
    int x = rs.getInt(pos[0]);
    return rs.wasNull() ? null : new Position3D(x, rs.getInt(pos[1]), rs.getInt(pos[2]));
  }
}
```

In the model an attribute then uses it like any other type, and the three columns appear automatically:

```
Position3D  pickupPosition  pickup_position   where the gripper picks up
# stored as pickup_position_x, pickup_position_y, pickup_position_z (all INTEGER)
```

Finally, the **UI** teaches a text field how to edit the value, by registering a
[`ValueTranslator`](../../../tentackle-core/src/main/docs/binding.md) — a third, presentation-side service that is
again a *pure value* conversion (`Position3D` ⇄ `String`), reusing the value class's own `toString` / `valueOf`:

```java
@ValueTranslatorService(modelClass = Position3D.class, viewClass = String.class)
public class Position3DTranslator extends ValueStringTranslator<Position3D> {

  public Position3DTranslator(FxTextComponent component) { super(component); }

  @Override public Function<Position3D, String> toViewFunction()  { return m -> m == null ? null : m.toString(); }
  @Override public Function<String, Position3D> toModelFunction() { return v -> v == null ? null : Position3D.valueOf(v); }
}
```

Notice what this example makes concrete:

- **Three single-responsibility services across three modules.** The value (`Position3D`) lives in the shared
  `…-common` DTO module; the persistence mapping (`Position3DType`) lives in a dedicated `…-types` module so the
  PDO modules don't pull in `tentackle-sql`; and the UI editing (`Position3DTranslator`) lives in the `…-gui`
  module so the lower tiers stay free of any JavaFX dependency. The same context-free value flows through all three.
- **The whole is the unit, not the parts.** `getSortableColumns()` returns `null` — a position is *not* meaningfully
  ordered by the database as a value (only each raw column is). You store and retrieve it whole; you do not
  `WHERE`/`ORDER BY` on "positions". If you *did* need to filter by `y >= 1000` with an index, that would be the
  signal to model it as an **embedded entity** instead — exactly the trade-off this document is about.
- **No context, by design.** Nothing in either class touches a session, a tenant, an aggregate, or a permission.

### What you get

- **A pure value — exactly when you want one.** No session, no aggregate, no tenant, no security; the value is the
  same regardless of who holds it. When the concept genuinely *is* context-free, this is a feature, not a gap.
- **A reusable *type*, not a relation.** Once registered, it is available to *every* attribute of *every* entity,
  exactly like `String`. No per-owner wiring.
- **Atomic value semantics.** Parsing, formatting, range checks, and domain rules live inside the Java class, and
  the UI can teach a field to edit it through a [`ValueTranslator`](../../../tentackle-core/src/main/docs/binding.md).
- **Opaque, efficient storage.** Even a multi-column type is, to the model, a single attribute — no relation
  overhead, no extra object identity.

### What it costs

- **No context-awareness at all.** It cannot be tenant-scoped, security-governed, validated-in-aggregate, or
  navigated. Any such logic must live on the *entity* that holds the value. If you find yourself wishing the value
  knew its tenant or its aggregate, that is the signal you actually want an (embedded) entity.
- **The sub-structure is invisible to the database (mostly).** You generally do **not** query, index, or sort by
  the *internal parts*. (A multi-column type whose `isColumnCountBackendSpecific()` is true is explicitly barred
  from per-column `CN_…` constants and from being a wurblet `WHERE`-clause argument.)
- **Cross-module setup for the heavyweight path**, and **statelessness/freezing rules**: the singleton holds no
  state, and a *mutable* value type must implement
  [`Freezable`](../../../tentackle-common/src/main/java/org/tentackle/common/Freezable.java) so it can be frozen
  once stored (`DataType.isMutable()`).

---

## The core distinction, side by side

| Dimension                               | Embedded entity                                                                                                                               | Application-specific DataType                                                 |
|-----------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| **What is it?**                         | A full, **`DomainContext`-aware** entity with no table of its own                                                                             | A bare Java **value** + its column-mapping rules                              |
| **Knows its session?**                  | **Yes** — it is a PDO bound to a session                                                                                                      | No                                                                            |
| **Aggregate / root context?**           | **Yes** — shares the owner's root context as part of its aggregate                                                                            | No — it stands outside any aggregate                                          |
| **Tenant & security scope?**            | **Yes** — governed by the owner's context and `SecurityManager`                                                                               | No — any scoping lives on the holding entity                                  |
| **Validated in aggregate context?**     | **Yes** — with the owner's scope and fail-fast aggregate rules                                                                                | As a standalone value only                                                    |
| **Unit of meaning**                     | The individual **attributes**                                                                                                                 | The **whole value**                                                           |
| **Storage**                             | One real column **per attribute**, prefixed into the owner's table                                                                            | One or more columns the model treats as one opaque attribute                  |
| **Query/index/sort by internal parts?** | **Yes** — each part is a real column the **owner** can index (by path), filter and sort                                                       | **Normally no**                                                               |
| **Domain behaviour**                    | Rich: methods, per-attribute validation, outgoing relations, may nest further **embedded** entities (but no owned components, no own indexes) | Conversion/parsing/formatting on the value class                              |
| **Reuse**                               | Embedded into owners via a **relation** (per-owner prefix)                                                                                    | Available to **any attribute of any entity**, like `String`                   |
| **Identity / lifecycle**                | None of its own; lives in the owner's row, shares the aggregate                                                                               | None; it *is* a value                                                         |
| **How declared**                        | A model with `inheritance := embedded` + an `object` relation                                                                                 | `@Service(DataType.class)` (multi-column) or `Convertible<T>` (single-column) |
| **Defining cost**                       | A whole entity (model + generated classes)                                                                                                    | A value class; plus a module + plugin wiring for a full `DataType`            |

Two complementary one-line tests:

- **Context test:** *does the thing need to know its session, aggregate, tenant, or permissions?* Yes → **embedded
  entity**. No → **DataType**.
- **Parts test:** *do you ever want the database to see the inside* — index a postal code, filter by a currency,
  sort by a "valid-from" date? Yes → **embedded entity** (each part is a real column). No → **DataType** (opaque).

The two usually agree because they are two faces of the same nature: a context-aware object is one the framework
must reason about structurally (so its parts are real columns), whereas a bare value is one the framework only
stores (so its structure stays inside Java).

---

## A worked contrast: a product number, both ways

The [binding documentation](../../../tentackle-core/src/main/docs/binding.md#a-worked-example-a-domain-value-type-with-live-input-assistance)
uses an industrial **product number** — an aluminum-coil identifier with a fixed composite layout
`AAAA-QQQ-TTT-WWWW-LLLL-XXX` (alloy, quality, thickness, width, length, optional test segment). The *same* concept
can be modeled either way, and which you pick is dictated entirely by how you need to *use* it.

**As a DataType (a context-free, atomic identifier).** If the product number is, to the business, a single code
you store, display, parse, and validate as a unit — the *same* code no matter which order, tenant, or session
references it, and you never need the database to filter by "all coils of alloy 5083" — model it as
`ProductNumber`, a value type stored in **one** `product_number` column. The segment layout is a Java concern: the
value class parses and formats `AAAA-QQQ-…`, and a `ProductNumberTranslator` gives the text field live input
assistance. It carries no `DomainContext`, and it shouldn't — a product number means the same thing everywhere.
This is the natural choice and the one the binding doc illustrates.

**As an embedded entity (context-aware, with queryable parts).** If instead you need to **query and index by the
segments** ("every coil with `thickness = 300` and `width >= 1000`", with database indexes), *or* the number must
participate in the owner's aggregate and tenant/security scope, then it is more than a value: model it as an
**embedded entity** with `alloy`, `quality`, `thickness`, `width`, `length` and `test` as individual attributes
folded into the owner's table as `pn_alloy`, `pn_quality`, `pn_thickness`, … (which the owner can then index). Now
the database can do the filtering and sorting, and the embedded number shares the owner's domain context.

> Same domain concept, opposite models — chosen by whether the number is a *context-free value* or a
> *context-aware, decomposable object*. That single question is the whole decision.

---

## Decision guide

Ask these in order; the first "yes" usually settles it.

1. **Does the thing need to know its session, aggregate, tenant, or permissions** — i.e., must it be governed by a
   `DomainContext`? → **Embedded entity.** Only an entity is context-aware.

2. **Do you need to index, filter, sort, or join on its *internal fields*, backend-agnostically?**
   → **Embedded entity.** Only it gives every part a real, named column.

3. **Does the sub-structure carry meaningful domain *behavior* or per-field validation you want to reuse, and
   possibly nest?** → **Embedded entity.**

4. **Is it a self-contained, context-free *value* whose internal structure is purely a Java concern (parse/format/
   validate as a whole), stored opaquely?** → **DataType.**

5. **Is it a single-column wrapper or an enum that maps to one existing column type?**
   → **DataType via [`Convertible<T>`](../../../tentackle-core/src/main/java/org/tentackle/misc/Convertible.java)** —
   the lightweight path; no separate module needed.

6. **Does the atomic value genuinely need *several* columns (or backend-specific SQL types/sizes)?**
   → **Full [`DataType`](../../../tentackle-sql/src/main/java/org/tentackle/sql/DataType.java)** in its own module,
   registered with the wurbelizer and tentackle-sql plugins.

7. **Is it conceptually a *separate thing with its own identity and lifecycle* that other rows reference and that
   outlives any single owner?** → Neither — that is a **root entity / component with its own table**. See
   [pdo.md](../../../tentackle-pdo/src/main/docs/pdo.md).

### Rules of thumb

- **Needs a `DomainContext` → embedded entity. Just a value → DataType.** This is the master rule; the rest are
  corollaries.
- **Care about the parts → embedded entity. Care about the whole → DataType.**
- **Index or `WHERE` on a sub-field?** That alone forces an embedded entity.
- **Tenant-scoped or security-governed?** That belongs to an entity (the embedded one or its owner), never to a
  value.
- **Prefer `Convertible` over a full `DataType`** whenever a single column suffices — far less wiring.
- **A mutable DataType value must implement `Freezable`** so it freezes once stored.
- **Embedded ≠ component-with-its-own-table.** Embedded means *folded into the owner's row*; if it needs its own
  table, identity, and lifecycle, it is a component or root entity instead.

---

## Related Documentation

- [PDO / Persistent Domain Objects](../../../tentackle-pdo/src/main/docs/pdo.md) — the
  [`DomainContext` and DDD](../../../tentackle-pdo/src/main/docs/pdo.md#the-domaincontext-and-ddd) section that an
  embedded entity participates in, and when a thing should be a root/component entity instead.
- [`DomainContext`](../../../tentackle-pdo/src/main/java/org/tentackle/pdo/DomainContext.java) — session, root
  context, and tenant scoping in one object; what makes an entity context-aware and a value not.
- [Model Definition](../../../tentackle-model/src/main/docs/model-definition.md) — attribute lines, the `embedded`
  inheritance/relation strategy, column prefixes, and where DataTypes plug into the attribute grammar.
- [`DataType`](../../../tentackle-sql/src/main/java/org/tentackle/sql/DataType.java) — the stateless SPI for an
  application-specific value type, including the multi-column and per-backend mapping methods.
- [`Convertible`](../../../tentackle-core/src/main/java/org/tentackle/misc/Convertible.java) — the lightweight,
  single-column wrapper path (enums and thin value wrappers).
- [`Freezable`](../../../tentackle-common/src/main/java/org/tentackle/common/Freezable.java) — why a mutable value
  type must be freezable once stored.
- [Binding](../../../tentackle-core/src/main/docs/binding.md) — the `ProductNumber` value type and how a
  `ValueTranslator` gives a custom type live input assistance in the UI.
- [The DTO Wurblet and `@RecordDTO`](../../../tentackle-wurblets/src/main/docs/dto-wurblet.md) — how the `@RecordDTO`
  annotation on a value record (like `Position3D`) drives the build to generate its builder and copy factories.
- [Validation](../../../tentackle-core/src/main/docs/validation.md) — declaring constraints on an embedded
  entity's attributes (run with the owner's scope) or on a value as a whole.
- [Immutable](../../../tentackle-core/src/main/docs/immutable.md) — how an embedded entity inherits its owner's
  immutability as part of the aggregate.
