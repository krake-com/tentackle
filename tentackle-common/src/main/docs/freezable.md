# Freezable — Making Old-School Mutable Value Types Safe

## The problem: value objects that aren't immutable

A *value object* is defined by its value, not its identity: two dates representing the same day are
interchangeable, and once an entity says "the invoice date is 2026-06-25," that value should not silently change
underneath it. Conceptually such objects ought to be **immutable**, like `String`, `BigDecimal` or the
`java.time` types.

But the classic JDK date/time types — `java.util.Date` and its JDBC subclasses `java.sql.Date`,
`java.sql.Time`, `java.sql.Timestamp` — are **mutable**. They expose `setTime(long)` (and the deprecated
`setYear`, `setMonth`, …). The same is true of any byte-buffer-backed binary value. This is a well-known design
wart, and it is actively dangerous the moment such an object becomes an **attribute of a persistence entity**:

```java
java.util.Date d = new java.util.Date();
invoice.setDate(d);     // entity now references d
...
d.setTime(0);           // OOPS: mutated the invoice's date behind its back
```

The caller still holds a reference to the very object the entity stored (*aliasing*). Mutating it bypasses
everything the entity setter is responsible for:

- **dirty tracking** — the change never goes through `setDate(...)`, so the
  [tracked](../../../../tentackle-pdo/src/main/docs/snapshot.md) entity never marks itself modified and the
  edit is lost on save (or saved without anyone noticing);
- **immutability** — an entity made [`Immutable`](../../../../tentackle-core/src/main/docs/immutable.md) (a
  cached master-data row, a read-only view, an aggregate component) could still be mutated through an aliased
  field value, defeating the whole guarantee;
- **validation and property-change events** — none of them fire.

Java's only built-in answer is `final` (compile-time, all-or-nothing) — useless here, because the field value
arrives at runtime. Tentackle needs a value object that can be made **read-only at runtime, permanently, once it
is handed to an entity**. That is `Freezable`.

---

## The `Freezable` contract

[`Freezable`](../java/org/tentackle/common/Freezable.java) (in `tentackle-common`) is a tiny interface:

```java
public interface Freezable {
  void    freeze();        // make this object immutable
  boolean isFrozen();

  static void freeze(Freezable freezable) {   // null-safe helper, keeps generated code small
    if (freezable != null) {
      freezable.freeze();
    }
  }
}
```

Two rules define its semantics:

1. **Freezing is one-way and final.** Once frozen, an object can **never** be unfrozen. There is no
   `unfreeze()`. After `freeze()`, every mutator throws a
   [`TentackleRuntimeException`](../java/org/tentackle/common/TentackleRuntimeException.java).
2. **Clones come out unfrozen.** Cloning a frozen object yields a fresh, **mutable** instance.
   This is what lets you take a frozen stored value, copy it, edit the copy, and assign it back — the normal
   "change a date" workflow.

---

## The Freezable value types

Because you cannot retrofit `freeze()` onto `java.util.Date`, Tentackle ships drop-in **subclasses** that add
the capability. They are the types you use as model attributes instead of the raw JDK ones:

| Tentackle type                                                                                                | Extends / base                                                       | Adds                                                        |
|---------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|-------------------------------------------------------------|
| [`Date`](../java/org/tentackle/common/Date.java)                                                              | `java.sql.Date`                                                      | freezability **+** timezone-stable `YYYYMMDD` serialization |
| [`Time`](../java/org/tentackle/common/Time.java)                                                              | `java.sql.Time`                                                      | freezability + database time semantics                      |
| [`Timestamp`](../java/org/tentackle/common/Timestamp.java)                                                    | `java.sql.Timestamp`                                                 | freezability + optional UTC flag                            |
| [`Binary`](../java/org/tentackle/common/Binary.java) / [`TBinary`](../java/org/tentackle/common/TBinary.java) | [`AbstractBinary`](../java/org/tentackle/common/AbstractBinary.java) | freezability over a serialized byte buffer                  |

Each carries a `frozen` flag (private in the date/time types, protected in `AbstractBinary`) and overrides
every state-changing method to check it first. From [`Date`](../java/org/tentackle/common/Date.java):

```java
@Override
public void setTime(long date) {
  assertNotFrozen();        // throws "date is frozen" once frozen
  super.setTime(date);
}

private void assertNotFrozen() {
  if (frozen) {
    throw new TentackleRuntimeException("date is frozen");
  }
}

@Override
public Date clone() {
  Date date = (Date) super.clone();
  date.frozen = false;      // clones are always mutable
  return date;
}
```

> These types solve a *second* problem at the same time: stable cross-timezone semantics. `java.sql.Date`
> serializes epochal milliseconds, so a date can shift by a day when deserialized in another timezone — fatal
> for remoting. Tentackle's `Date` serializes the calendar `YYYYMMDD` instead, so the value is identical
> everywhere. The `frozen` flag is part of that serialized state (`writeBoolean(frozen)`), so freezing survives
> a trip over [TRIP](../../../../tentackle-core/src/main/docs/trip.md). See
> [common.md → Database-aware data types](common.md#database-aware-data-types).

Note what is **not** here: `BMoney` / `DMoney` extend `BigDecimal` and are immutable by nature, so they don't
need `Freezable`. Freezability is only for value types the JDK left mutable.

Frozen instances can also be created directly:

- `Date.createFrozen(epoch)`, `Time.createFrozen(epoch)` and `Timestamp.createFrozen(epoch, nanos)`
  (the latter with the UTC flag off). `DateHelper.MIN_DATE` and `DateHelper.MIN_TIMESTAMP` are frozen
  this way, so the shared constants cannot be modified by accident.
- `Binary.create(stream, bufSize, frozen)` / `TBinary.create(...)` and the corresponding constructors,
  as used when reading a binary from the database.

The exception messages name the type: "date is frozen", "time is frozen", "timestamp is frozen" and
"binary is frozen".

> **One loophole.** `AbstractBinary.getData()` returns the internal byte array, not a copy, for
> serialization purposes. Writing into that array changes even a frozen binary — don't.

---

## How the persistence layer uses it

The link between "this attribute is a mutable value type" and "freeze it" lives in the
[`DataType`](../../../../tentackle-sql/src/main/java/org/tentackle/sql/DataType.java) abstraction. A data type
reports whether its Java representation is a mutable, freezable object:

```java
/** Mutable objects may change their state and must implement Freezable. */
boolean isMutable();
```

`isMutable()` returns `true` exactly for the date/time and binary types above. The code generator
([`MethodsImpl`](../../../../tentackle-persistence-wurblets/src/main/wurblets/org/tentackle/persist/wurblet/MethodsImpl.wrbl))
then emits a **freeze on assignment** in every generated setter for such an attribute:

```java
void setInvoiceDate(Date invoiceDate) {
  Freezable.freeze(invoiceDate);          // generated only when the DataType isMutable()
  if (!Objects.equals(this.invoiceDate, invoiceDate)) {
    assertMutable();                      // the entity's own Immutable guard
    firePropertyChange(AN_INVOICEDATE, this.invoiceDate, invoiceDate);
    this.invoiceDate = invoiceDate;
  }
}
```

The effect: **the instant a value object is stored in an entity attribute, it is frozen.** The caller may keep
its reference, but can no longer mutate the stored value — the aliasing bug from the opening example becomes a
loud `TentackleRuntimeException` instead of silent corruption. To change the value, you must call the setter with
a *different* (or cloned-and-edited) instance, which routes through dirty tracking, the immutability check
(`assertMutable()`) and property-change notification as intended.

---

## Relationship to `Immutable`

`Freezable` and [`Immutable`](../../../../tentackle-core/src/main/docs/immutable.md) are deliberately different
mechanisms for two different kinds of objects, and they **cooperate**:

|             | `Freezable`                                                                   | `Immutable`                                                   |
|-------------|-------------------------------------------------------------------------------|---------------------------------------------------------------|
| Applies to  | mutable **value objects** in entity fields (`Date`, `Timestamp`, `Binary`, …) | **entities / containers** (PDOs, `ImmutableArrayList`)        |
| Direction   | **one-way and final** — never unfrozen                                        | **switchable** back and forth (until `setFinallyImmutable()`) |
| Granularity | a single scalar value                                                         | an object and, cascading, its whole aggregate                 |
| Lives in    | `tentackle-common`                                                            | `tentackle-core` (`org.tentackle.misc`)                       |

A PDO's scalar attributes are **frozen value objects**, while the PDO itself is **`Immutable`**. Freezing the
field values closes the aliasing hole that `Immutable` alone cannot — `assertMutable()` stops you from *calling
the setter* on an immutable entity, but only `Freezable` stops you from mutating a value you already pulled out
of (or pushed into) it.

See the [Immutable documentation](../../../../tentackle-core/src/main/docs/immutable.md) for the entity/container
side, including its three-state model and aggregate cascading.

---

## Rules of thumb

- **Use Tentackle's value types** (`Date`, `Time`, `Timestamp`, `Binary`/`TBinary`) for model attributes, not
  the raw `java.util` / `java.sql` types — you get freezability *and* timezone-stable serialization.
- **Don't hold on to a stored value expecting to mutate it.** Once assigned to an entity, it is frozen. To change
  it, build/clone a new instance and call the setter.
- **Clone to edit.** `clone()` of a frozen value returns a mutable one.
- **Freezing is permanent.** There is no unfreeze; a value's only path back to mutability is a fresh clone.

---

## See Also

- [Immutable — Switchable Read-Only Objects](../../../../tentackle-core/src/main/docs/immutable.md) — the
  switchable entity/container counterpart, and how the two cooperate.
- [common.md → Database-aware data types](common.md#database-aware-data-types) — the value types that implement
  `Freezable` and their database/timezone semantics.
- [Tentackle SQL](../../../../tentackle-sql/src/main/docs/sql.md) — the `DataType` abstraction whose
  `isMutable()` flag drives freeze-on-assignment.
- [Snapshots](../../../../tentackle-pdo/src/main/docs/snapshot.md) — dirty tracking that aliasing mutation would
  otherwise defeat.
