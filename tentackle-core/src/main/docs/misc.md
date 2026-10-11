# Miscellaneous — Core Contracts, Collections, Pooling, and Utilities

## Overview and Motivation

The name `org.tentackle.misc` suggests a junk drawer of leftovers, but that undersells it. The
package holds several of the **contracts the upper layers are built from**, plus the collections,
pooling and formatting machinery those layers depend on. The clearest evidence is
[`PersistentObject`](../../../../tentackle-pdo/src/main/java/org/tentackle/pdo/PersistentObject.java),
the persistence half of every [PDO](../../../../tentackle-pdo/src/main/docs/pdo.md), which extends
five interfaces defined here:

```java
public interface PersistentObject<T extends PersistentDomainObject<T>>
       extends ..., Snapshotable<T>, Modifiable, SerialNumbered, ..., Immutable, ... {
```

So "is this object modified?", "give me a snapshot to revert to", "what is your id and serial?" and
"can you be made read-only?" are all questions answered by contracts in `org.tentackle.misc`. They
live here — rather than in the PDO layer — precisely because they are *not* about persistence: a
plain value object, a UI model, or a collection can implement them just as well, and the framework's
own collections do.

The rest of the package earns its place by being genuinely cross-cutting: the
[`Pool`](../java/org/tentackle/misc/Pool.java) that both database sessions and TRIP connections are
managed by, the [`TrackedList`](../java/org/tentackle/misc/TrackedList.java) that makes OR-mapping
of collections possible, the locale-aware [`FormatHelper`](../java/org/tentackle/misc/FormatHelper.java)
used in three dozen classes across the framework, and the
[`SmartDateTimeParser`](../java/org/tentackle/misc/time/SmartDateTimeParser.java) in `misc.time`
that turns `".+1d15h =paris"` into *tomorrow, 3pm local time, in Europe/Paris*.

This document groups the package by what the classes are *for*. Two topics that live here are
documented in depth elsewhere and are only cross-referenced below:
[`Immutable`](immutable.md) and [`Snapshotable`](../../../../tentackle-pdo/src/main/docs/snapshot.md).


## Core Contracts

Small interfaces that the rest of the framework implements. They are the reason the package sits
so low in the dependency graph.

| Interface                                                            | Contract                                                                                     |
|----------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| [`Identifiable`](../java/org/tentackle/misc/Identifiable.java)         | An object with an id (and thus a class + id identity). Extends `Serializable`.               |
| [`SerialNumbered`](../java/org/tentackle/misc/SerialNumbered.java)     | An `Identifiable` that also carries a `long` serial — a version, sequence or serial number.  |
| [`Modifiable`](../java/org/tentackle/misc/Modifiable.java)             | An object that can tell whether it has been modified.                                        |
| [`Snapshotable<T>`](../java/org/tentackle/misc/Snapshotable.java)      | An object that can take snapshots and revert to them — see [snapshot.md](../../../../tentackle-pdo/src/main/docs/snapshot.md). |
| [`Immutable`](../java/org/tentackle/misc/Immutable.java)              | An object that can be switched read-only — see [immutable.md](immutable.md).                  |
| [`Convertible`](../java/org/tentackle/misc/Convertible.java)          | A type that maps to and from another representation (`toExternal` / `toInternal` / `getDefault`) — how an enum or custom type becomes a database column via [`ConvertibleType`](../../../../tentackle-sql/src/main/java/org/tentackle/sql/datatypes/ConvertibleType.java). |
| [`ShortLongText`](../java/org/tentackle/misc/ShortLongText.java)      | An object offering both a short label and a longer description.                              |
| [`ScrollableResource<T>`](../java/org/tentackle/misc/ScrollableResource.java) | An `AutoCloseable` resource with a current row (1-based) — the abstraction behind database cursors. |

`Snapshotable` deserves one clarification that is easy to get wrong: a snapshot is **not** a copy.
It holds the information needed to *revert* the object to its state at the time the snapshot was
taken; use `copy()` if you want an independent object.

Two functional interfaces round this out:
[`Provider<T,E>`](../java/org/tentackle/misc/Provider.java) and
[`Acceptor<T,E>`](../java/org/tentackle/misc/Acceptor.java) mirror `Supplier` and `Consumer` but
permit a checked exception — the escape hatch you want when a lambda has to do I/O.


## Collections

### TrackedList — knowing what changed

[`TrackedList<E>`](../java/org/tentackle/misc/TrackedList.java) (with the concrete
[`TrackedArrayList`](../java/org/tentackle/misc/TrackedArrayList.java)) is a `List` that records
its **structural modifications**: additions and removals are tracked separately, a replacement
counts as a removal plus an addition, and every removed object is kept in a separate list.

That is exactly what OR-mapping needs. When an aggregate is saved, the persistence layer asks the
list what happened: which elements are new (insert), which are gone (delete), which remain
(update). Without the removal history, a deleted component would simply vanish from memory with no
record that the row must go. The class is not restricted to PDOs, though — any list that needs a
dirty flag can use it.

`TrackedList` also composes with the other contracts, which is what makes aggregates work as a unit:

- it is [`Immutable`](immutable.md) — and setting the list immutable cascades to any elements that
  are themselves `Immutable`;
- it is [`Snapshotable`](../../../../tentackle-pdo/src/main/docs/snapshot.md) — with unlimited
  snapshots, cascading into `Snapshotable` elements, and reverting invalidates all later snapshots;
- it can be configured to reject `null` elements outright;
- [`TrackedListListener`](../java/org/tentackle/misc/TrackedListListener.java) reports the events.

A related question is why OR-mapped relations are *lists* at all and never sets. Two properties of
PDOs make a hash- or tree-based `Set` unsound: their `hashCode`/`equals` are id-based and change when
a transient object is first persisted, and cyclic graphs are deserialized through partially built
placeholders whose ids are not yet set — either way a set's bucketing would be corrupted, while a
list stores by position and never hashes its elements. See
[Object identity — why relations are lists](../../../../tentackle-pdo/src/main/docs/pdo.md#object-identity-equals-hashcode-and-why-relations-are-lists).

### Copy-on-write wrappers

[`CopyOnWriteCollection`](../java/org/tentackle/misc/CopyOnWriteCollection.java),
[`CopyOnWriteList`](../java/org/tentackle/misc/CopyOnWriteList.java) and
[`CopyOnWriteSet`](../java/org/tentackle/misc/CopyOnWriteSet.java) wrap an existing collection and
clone it lazily before the *first* modification, leaving the original untouched.

> **Not** the `java.util.concurrent` copy-on-write classes. Those are thread-safe and copy on
> *every* mutation. These are not thread-safe and copy *once*. The purpose is different: cheaply
> handing out a collection that the receiver may modify without disturbing the owner.

With `iteratorModifying=false` every operation works except `Iterator.remove()`; set it to `true`
and `iterator()` copies the collection so the iterator can modify it. `CopyOnWriteList` has the same
switch for its list iterators (`listIteratorModifying`, on by default, since `List.sort` modifies
through them). The views returned by `subList()` and `reversed()` may be written to as well, so they
are backed by the copy and create it on demand. The wrapped collection must be `Cloneable` with a
public `clone()` method.

### Identifiable maps and keys

[`IdentifiableMap<T>`](../java/org/tentackle/misc/IdentifiableMap.java) maps
[`Identifiable`](../java/org/tentackle/misc/Identifiable.java)s by a key composed of class **and**
object id — necessary because ids are only unique per class, so a naive `Map<Long,?>` would
collide across entity types. [`IdentifiableKey<T>`](../java/org/tentackle/misc/IdentifiableKey.java)
is that composite key, usable for hashing or as a `Comparable` (sorted by class name, then id). The
map starts as a hash map; the first call to `getAllSorted()` adds a tree map that is maintained
alongside from then on, trading a little speed for ordering only when ordering is actually asked for. It is not thread-safe: build and consume it
from a single thread (as `loadComponents()` does), or synchronize externally.

### Immutable collections

[`ImmutableCollection`](../java/org/tentackle/misc/ImmutableCollection.java),
[`ImmutableArrayList`](../java/org/tentackle/misc/ImmutableArrayList.java) and
[`ImmutableException`](../java/org/tentackle/misc/ImmutableException.java) implement the switchable
read-only model — see [immutable.md](immutable.md) for the full contract and how mutators are
guarded.


## Pooling

A small, complete pooling framework used by the framework's own expensive resources —
[`DbPool`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/DbPool.java) for database
sessions and `AbstractConnectionPool` for [TRIP](trip.md) connections both build on it.

| Type                                                             | Role                                                                        |
|------------------------------------------------------------------|-----------------------------------------------------------------------------|
| [`Pool<T>`](../java/org/tentackle/misc/Pool.java)                 | The pool: `get()` / `put()`, sizes, timeouts, `shutdown()`, `monitor()`      |
| [`Poolable<T>`](../java/org/tentackle/misc/Poolable.java)         | What a pooled resource must offer: its pool and its pool id                 |
| [`PoolSlot<T>`](../java/org/tentackle/misc/PoolSlot.java)         | One slot in the pool                                                        |
| [`AbstractPool`](../java/org/tentackle/misc/AbstractPool.java) / [`AbstractPoolSlot`](../java/org/tentackle/misc/AbstractPoolSlot.java) | Base implementations: min/max sizes, fixed increments, idle timeouts |
| [`PoolTimeoutThread`](../java/org/tentackle/misc/PoolTimeoutThread.java) | **One** thread for *all* pools, closing instances idle or in use too long |

A poolable's `poolId` doubles as its state: 0 when idle in the pool, greater than 0 while lent out,
-1 once removed. Resources are created lazily on the first `get()`, which also registers the pool
with the timeout thread. A resource that died while idle in the pool (closed by the peer, for
example) is dropped and replaced instead of being handed out, and returning a closed resource
removes it from the pool. The monitor replaces dead idle resources as well, up to the minimum size.
A pool that cannot create a resource without blocking for long overrides
`AbstractPool.restoreMinimumSize()` to leave that to the next `get()`, since the monitor runs on the
one thread all pools share.

While a resource is lent, its slot holds only a weak reference to it. A resource that was lent but
never returned and has been garbage collected is thus detected by the monitor and removed from the
pool with a warning naming the thread that took it. `AbstractPool.getSlotCountPerThread()` and
`logSlotCountPerThread()` report which threads hold how many resources — logged automatically when
the pool is exhausted, since that is often a sign of such a leak.

The timeout thread exists for long-running servers: releasing resources and
capping memory matters more, over days of uptime, than keeping a warm connection around forever.
Both an idle timeout (`getIdleMinutes()`, honored only above the minimum size) and a maximum usage
time (`getUsageMinutes()`, counted from the first use) are supported — the latter recycles resources
that have been alive too long regardless of activity. A negative minimum size shuts the pool down
once all of its resources have timed out.


## Formatting and Parsing

### FormatHelper — localized patterns in one place

[`FormatHelper`](../java/org/tentackle/misc/FormatHelper.java) is the framework's most widely used
`misc` class. It provides the **localized format patterns** for integers, floating-point numbers,
money, dates, times and timestamps — each in a long and a `short` variant, and for temporals also
with a zone offset or a time zone.

The patterns come from a resource bundle per locale, so they follow the
[i18n](../../../../tentackle-common/src/main/docs/bundle.md) story rather than being hard-coded,
and every getter exists both with an explicit `Locale` and without (using the current locale from
the `LocaleProvider`). An application that dislikes a default overrides it once at startup with
`FormatHelper.applyPatterns(...)`, passing a `Patterns` record; every formatter in the application —
including the [FX components and table cells](../../../../tentackle-fx/src/main/docs/tables.md) —
picks the change up.

### SmartDateTimeParser — typing dates the way people think

[`SmartDateTimeParser<T>`](../java/org/tentackle/misc/time/SmartDateTimeParser.java) in
`org.tentackle.misc.time` parses any `Temporal` from input that a user would plausibly type.
Instead of demanding a full, correctly formatted date, it accepts shorthand and relative
expressions. The often-quoted example says it best:

```
".+1d15h =paris"   →   tomorrow, 15:00 local time, converted to Europe/Paris
```

Parsing runs in four steps:

1. **Text filters** normalize the input — turning `5/30/21` into `053021` for the number shortcut,
   expanding `+2:30` to `+02:30`, resolving `berlin` to `Europe/Berlin`.
2. **Shortcuts** modify the current value according to patterns found in the first word of the
   input, removing what they consume. If a shortcut applied, an optional second word sets the zone
   offset or time zone — prefixed with `=`, it converts the value instead.
3. If no shortcut applied, the input goes to a regular `DateTimeFormatter`.
4. If that throws, **error handlers** get a chance to repair the input and retry, each at most once.

The built-in vocabulary:

| Element                                                                    | Effect                                                                 |
|----------------------------------------------------------------------------|------------------------------------------------------------------------|
| [`NowShortcut`](../java/org/tentackle/misc/time/NowShortcut.java)            | Input starting with `.`, `,` or `/` means *now*                       |
| [`AddShortcut`](../java/org/tentackle/misc/time/AddShortcut.java)            | `+10h` adds ten hours; units are `y`, `M`, `d`, `h`, `m`, `s`; a bare `-10` defaults to days (or hours for time-only types) |
| [`NumberShortcut`](../java/org/tentackle/misc/time/NumberShortcut.java)      | `5h` → 05:00:00.000; `7M` → July 1st, midnight. Sets lesser fields to their minimum. Four and six digits are read as `ddMM`, `ddMMyy`, `HHmm`, `HHmmss` (in the order of the locale's pattern) |
| [`ZoneShortcut`](../java/org/tentackle/misc/time/ZoneShortcut.java) / [`OffsetShortcut`](../java/org/tentackle/misc/time/OffsetShortcut.java) | A zone or offset; prefixed with `=` it *converts* the value, otherwise it just sets it |
| [`DateFilter`](../java/org/tentackle/misc/time/DateFilter.java), [`ZoneFilter`](../java/org/tentackle/misc/time/ZoneFilter.java), [`OffsetFilter`](../java/org/tentackle/misc/time/OffsetFilter.java) | The normalizing filters |
| [`MissingTimeHandler`](../java/org/tentackle/misc/time/MissingTimeHandler.java), [`MissingZoneIdHandler`](../java/org/tentackle/misc/time/MissingZoneIdHandler.java), [`MissingOffsetHandler`](../java/org/tentackle/misc/time/MissingOffsetHandler.java) | The error handlers that fill in what the input omitted: midnight or the rest of a partial time, the default zone, the current offset |

`NumberShortcut`'s sliding window deserves a mention: `010120` becomes 2020-01-01 while `010199`
becomes 1999-01-01, not 2099 — a `WindowProvider` decides where the century flips.

Applications can register their own filters and shortcuts. Most code, though, reaches for
[`DateTimeUtilities`](../java/org/tentackle/misc/DateTimeUtilities.java) — a `@Service` singleton
wrapping preconfigured parsers with `parseLocalDate`, `parseZonedDateTime`, `parseShortLocalTime`
and friends for the whole `java.time` family.

### CSV and text

- [`CsvConverter`](../java/org/tentackle/misc/CsvConverter.java) — a `@Service` converting values
  to and from delimiter-separated strings, RFC 4180 compliant. `toCsvLenient()` picks the first of
  comma, semicolon, blank and tab that avoids quoting altogether. Each line is one record, so quoted
  values must not span lines. It is what the RDC table export writes when
  [tentackle-fx-rdc-poi](../../../../tentackle-fx-rdc-poi/src/main/docs/poi.md) is absent.
- [`SubString`](../java/org/tentackle/misc/SubString.java) — a string segment described by its
  indexes, materialized lazily, so index arithmetic costs no string allocations.
- [`CompoundValue`](../java/org/tentackle/misc/CompoundValue.java) — builds objects from strings:
  a constant, a `$`-prefixed dotted reference to a field/method (negatable with `!` for booleans),
  or a [script](scripting.md) in Groovy or JRuby. Used for dynamic parameters in annotations —
  see [CompoundValue](compound-value.md) for the full syntax and its role in
  [validation](validation.md).


## Utilities

| Class                                                                                     | Purpose                                                                                  |
|-------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| [`ObjectUtilities`](../java/org/tentackle/misc/ObjectUtilities.java)                       | Object helpers, mainly for [`CompoundValue`](compound-value.md) conversions; a `@Service`, so applications extend it for their own types |
| [`DiagnosticUtilities`](../java/org/tentackle/misc/DiagnosticUtilities.java)               | Diagnostics (thread dumps and friends); also a `@Service`                                |
| [`ConcurrencyHelper`](../java/org/tentackle/misc/ConcurrencyHelper.java)                   | Concurrency and multithreading helpers                                                   |
| [`TimeKeeper`](../java/org/tentackle/misc/TimeKeeper.java)                                 | A `nanoTime`-based duration with a fluent API — used throughout for timing and statistics |
| [`Canonicalizer<T>`](../java/org/tentackle/misc/Canonicalizer.java)                        | Makes equal objects share one reference, cutting memory in large object graphs           |
| [`CommandLine`](../java/org/tentackle/misc/CommandLine.java)                               | Parses `--option` / `--option=value` plus plain arguments                                |
| [`XUUID`](../java/org/tentackle/misc/XUUID.java)                                           | An id plus a `UUID` (`"id-uuid"`), so an external reference can be checked against brute-force guessing without changing the domain logic |
| [`NamedCounter`](../java/org/tentackle/misc/NamedCounter.java) / [`NamedValue<T>`](../java/org/tentackle/misc/NamedValue.java) | Thread-safe counters/values by name — e.g. numbering threads          |
| [`InheritableThreadLocalHashTable`](../java/org/tentackle/misc/InheritableThreadLocalHashTable.java) | A thread-local hashtable where each child thread gets its own copy             |
| [`UserHomeJavaDir`](../java/org/tentackle/misc/UserHomeJavaDir.java)                       | The per-user cache directory, `${user.home}/.java/tentackle`; the system property `tentackle.user.home.java.dir` replaces `${user.home}` |
| [`IdSerialTuple`](../java/org/tentackle/misc/IdSerialTuple.java) / [`IdSerialNameTuple`](../java/org/tentackle/misc/IdSerialNameTuple.java) | Id + serial (+ name) tuples, mainly for compact serialization |

### Holders

[`Holder<T>`](../java/org/tentackle/misc/Holder.java) is a `Consumer` and `Supplier` in one — the
standard trick for setting a value from inside a lambda when the enclosing method needs it back.
[`VolatileHolder`](../java/org/tentackle/misc/VolatileHolder.java) does the same with a `volatile`
reference, and [`BlockingHolder`](../java/org/tentackle/misc/BlockingHolder.java) turns it into a
tiny handoff: one thread blocks in `get()` until another calls `accept()`.

### Properties

[`PropertiesUtilities`](../java/org/tentackle/misc/PropertiesUtilities.java) (a `@Service`) loads
`java.util.Properties` through [`PropertiesResource`](../java/org/tentackle/misc/PropertiesResource.java)
loaders registered with
[`@PropertiesResourceService`](../java/org/tentackle/misc/PropertiesResourceService.java). Out of
the box only [`DefaultPropertiesResource`](../java/org/tentackle/misc/DefaultPropertiesResource.java)
is present, which tries the name as a file first and as a classpath resource second. An application
can add loaders to override properties from environment variables, a YAML file, or anywhere else —
see the [service API](../../../../tentackle-common/src/main/docs/services.md). All loaders are
applied in ascending `priority`, so a higher priority overrides a lower one; without an explicit
priority, the loaders get 100, 200, … in reverse discovery order. A loader annotated with
`stopIfNotFound = true` stops the chain once it *did* find the resource — the attribute's name
notwithstanding.

For the common case of overriding keys from the environment — a container deployment passing the
database URL, for instance — `applyEnvironment(properties, prefix)` does this without a custom
loader: for every key starting with a letter, it looks up the environment variable derived by
`toEnvironmentVariable(key)` (dots become underscores, other non-alphanumeric characters are dropped,
the rest is upper-cased, e.g. `tentackle.url` → `TENTACKLE_URL`), optionally with a prefix such as
`MYAPP_`, and replaces the value if the variable is set. Only existing keys are overridden; the
framework does not call it by itself.

[`ChainedProperties`](../java/org/tentackle/misc/ChainedProperties.java) is a `Properties` whose
defaults survive [TRIP](trip.md). A plain `Properties` keeps its defaults in a `java.util` field,
which TRIP must not reflect into, so it arrives with its own entries only — silently dropping
everything it inherited. `ChainedProperties` exposes the chain through a `@CanonicalGetter` and
takes it back through a `@CanonicalConstructor`, so the whole chain travels. The entire chain must
consist of `ChainedProperties`, and must not be cyclic.

[`PropertySupport`](../java/org/tentackle/misc/PropertySupport.java),
[`PropertyEvent`](../java/org/tentackle/misc/PropertyEvent.java) and
[`PropertyListener`](../java/org/tentackle/misc/PropertyListener.java) replace
`java.beans.PropertyChangeSupport` and friends, which belong to the `java.desktop` module since Java 9
— a dependency a headless server has no business carrying. Listeners are invoked *before* the
property changes, so a listener can veto the change by throwing a `RuntimeException`.


## Package Layout

| Group             | Types                                                                                                                              |
|-------------------|------------------------------------------------------------------------------------------------------------------------------------|
| Core contracts    | `Identifiable`, `SerialNumbered`, `Modifiable`, `Snapshotable`, `Immutable`, `Convertible`, `ShortLongText`, `ScrollableResource`, `Provider`, `Acceptor` |
| Collections       | `TrackedList`, `TrackedArrayList`, `TrackedListListener`, `CopyOnWrite{Collection,List,Set}`, `IdentifiableMap`, `IdentifiableKey`, `Immutable{Collection,ArrayList}`, `ImmutableException` |
| Pooling           | `Pool`, `Poolable`, `PoolSlot`, `AbstractPool`, `AbstractPoolSlot`, `PoolTimeoutThread`                                            |
| Formatting        | `FormatHelper`, `DateTimeUtilities`, `CsvConverter`, `SubString`, `CompoundValue`                                                  |
| Utilities         | `ObjectUtilities`, `DiagnosticUtilities`, `ConcurrencyHelper`, `TimeKeeper`, `Canonicalizer`, `CommandLine`, `XUUID`, `NamedCounter`, `NamedValue`, `InheritableThreadLocalHashTable`, `UserHomeJavaDir`, `IdSerialTuple`, `IdSerialNameTuple` |
| Holders           | `Holder`, `VolatileHolder`, `BlockingHolder`                                                                                      |
| Properties        | `PropertiesUtilities`, `PropertiesResource`, `DefaultPropertiesResource`, `PropertiesResourceService`, `ChainedProperties`, `PropertySupport`, `PropertyEvent`, `PropertyListener` |
| `misc.time`       | `SmartDateTimeParser`, the shortcuts (`Now`, `Add`, `Number`, `Zone`, `Offset`), the filters (`Date`, `Zone`, `Offset`), the missing-value handlers |


## Related Documentation

- [Tentackle Core](core.md) — the runtime foundation this package belongs to.
- [CompoundValue](compound-value.md) — `CompoundValue` and `ObjectUtilities` in depth.
- [Immutable](immutable.md) — the switchable read-only contract and its collections.
- [Snapshots and Copies](../../../../tentackle-pdo/src/main/docs/snapshot.md) — `Snapshotable` and `TrackedList` snapshot semantics in depth.
- [PDO](../../../../tentackle-pdo/src/main/docs/pdo.md) — `PersistentObject` implements the contracts defined here.
- [Resource Bundles](../../../../tentackle-common/src/main/docs/bundle.md) — where `FormatHelper`'s localized patterns come from.
- [Service and Configuration API](../../../../tentackle-common/src/main/docs/services.md) — how the `@Service` singletons here are replaced.
- [Tentackle Database](../../../../tentackle-database/src/main/docs/database.md) — session pooling and cursors in practice.
