# Domain Keys — the Business Identity of a Root Entity

## Overview and Motivation

Every PDO has a **technical identity**: the object `id` (plus its
[`serial`](../../../../tentackle-persistence/src/main/docs/locking.md) version counter). But no user
ever says "invoice 4711002"; the business identifies its objects by **natural keys** — a user by
their username, a country by its ISO code, a stored resource bundle by `(name, locale)`. Tentackle
makes this business identity a first-class concept: the **unique domain key** (UDK).

A domain key is declared in the [model](#declaring-a-domain-key-the-key-option), woven into the
generated interfaces, and then used pervasively by the framework: it is the `toString()` of a root
entity, the [duplicate check](#findduplicate-the-duplicate-check) before saving, the value a
user types into a [text field bound to a PDO](#binding-a-text-field-to-a-pdo), a
[cache index](cache.md#indexes-and-keys), and the natural lookup for things like logins.

Domain keys belong to **root entities** only: identity is a property of the
[aggregate](pdo.md), and components are identified through their root (see
[rootId / rootClassId](root-columns.md)).

## Declaring a Domain Key: the `key` Option

An attribute becomes part of the domain key via the `key`
[attribute option](../../../../tentackle-model/src/main/docs/model-definition.md#semantic-options)
in the model. If the key consists of more than one attribute, each one gets the option — here the
real model of [`StoredBundle`](../../../../tentackle-i18n/src/main/docs/i18n.md), whose identity is
`(name, locale)`:

```
## attributes
[root, remote, tracked, tableserial]

String(128)    name            bname              the resource bundle name [key]
String(8)      locale          blocale            the locale, null if default [key, mapnull]

## indexes
unique index udk := bname, blocale
```

Things to know:

- **`key` does not create a database constraint.** It declares the *semantic* identity; the
  unique index that enforces it must be declared explicitly in the `## indexes` section, as above.
  (The framework offers a friendly [duplicate check](#findduplicate-the-duplicate-check) on top,
  but the database index is the hard guarantee.)
- The key attributes are collected **including inherited attributes** and — less obviously — the
  attributes of the aggregate's **components** (recursively, stopping at sub-roots and embedded
  entities). A root's identity may thus include a component attribute.
- **Embedded attributes cannot be domain keys** — the model rejects the combination.
- A subclass that simply inherits its super entity's domain key gets nothing regenerated — the
  inherited key *is* its key.

## What Gets Generated

Three [wurblets](../../../../tentackle-wurblets/src/main/docs/wurblets.md) plus one on the
persistence side turn the `key` option into API:

**`Methods`** annotates the getter of each key attribute with
[`@DomainKey`](../java/org/tentackle/pdo/DomainKey.java) — the runtime-retained marker a reader
encounters in generated code — and declares the type-safe unique select
(suppress with `--noudk`):

```java
@Persistent(ordinal=1, comment="the resource bundle name")
@NotNull
@DomainKey
String getName();

@Persistent(ordinal=2, comment="the locale, null if default")
@DomainKey
String getLocale();

StoredBundle selectByUniqueDomainKey(String name, String locale);
```

**`UniqueDomainKey`** generates the key *type* — but only for **multi-member** keys. A
single-member key needs no wrapper: the attribute's type (e.g. `String`) *is* the key type. For
two or more members, a nested record is woven into the PDO interface:

```java
record StoredBundleUDK(String name, String locale)
       implements Serializable, Comparable<StoredBundleUDK> {

  public StoredBundleUDK(StoredBundle pdo) {
    this(pdo.getName(), pdo.getLocale());
  }

  @Override
  public int compareTo(StoredBundleUDK other) {
    int rv = Compare.compare(name, other.name);
    if (rv == 0) {
      rv = Compare.compare(locale, other.locale);
    }
    return rv;
  }
}
```

A record is the natural fit: value equality, an ordering that compares member by member
(null-safe via `Compare.compare`), and `Serializable` — so a domain key travels over
[TRIP](../../../../tentackle-core/src/main/docs/trip.md) like any other value.

**`DomainMethods`** implements the [runtime API](#the-runtime-api) in the domain implementation:
`isUniqueDomainKeyProvided()` returning `true`, `getUniqueDomainKeyType()`,
`getUniqueDomainKey()` / `setUniqueDomainKey()` mapping to the attribute getters/setters, and
`findByUniqueDomainKey()` delegating to `selectByUniqueDomainKey(...)`.

**[`PdoSelectUnique`](../../../../tentackle-persistence-wurblets/src/main/docs/pdo-select.md)**
generates the implementation of `selectByUniqueDomainKey(...)` into the persistence layer — a
remote-aware unique select like any other generated query method:

```java
// @wurblet selectByUniqueDomainKey PdoSelectUnique name locale
```

## The Runtime API

The generic access to the domain key lives on
[`DomainObject<T>`](../java/org/tentackle/pdo/DomainObject.java), i.e. on every PDO:

| Method                        | Meaning                                                                          |
|-------------------------------|----------------------------------------------------------------------------------|
| `isUniqueDomainKeyProvided()` | Does this entity define a domain key? The guard for all methods below.           |
| `getUniqueDomainKeyType()`    | The key's type: the attribute type, or the generated `…UDK` record.              |
| `getUniqueDomainKey()`        | The key *value* — the attribute value, or a new record snapshot of the members.  |
| `setUniqueDomainKey(Object)`  | Applies a key value to the PDO through its setters (used to preset new objects). |
| `findByUniqueDomainKey(Object)` | Loads the PDO with that business identity, `null` if none.                     |

The defaults in
[`AbstractDomainObject`](../../../../tentackle-domain/src/main/docs/domain.md) assert that the PDO
is a root entity and throw a `DomainException` — so generic code must check
`isUniqueDomainKeyProvided()` (which defaults to `false`) before touching the rest.

Because the methods are typed `Object`, they are the **reflective** entry — framework and generic
code use them; application code normally calls the type-safe `selectByUniqueDomainKey(...)`
directly.

Alongside the API, [`PdoUtilities.getMembers(...)`](../java/org/tentackle/pdo/PdoUtilities.java)
exposes the `@DomainKey` annotation as
[`PdoMember.isDomainKey()`](../java/org/tentackle/pdo/PdoMember.java) — the per-attribute
reflection view UI frameworks build on.

### `findDuplicate()`: the duplicate check

[`PersistentObject.findDuplicate()`](../java/org/tentackle/pdo/PersistentObject.java) asks: *would
saving this object violate the business identity?* The default implementation runs
`findByUniqueDomainKey(getUniqueDomainKey())` and returns the found object if it is a different
one. The [Rich Desktop Client](../../../../tentackle-fx-rdc/src/main/docs/rdc.md) invokes it
before saving and can tell the user *which* object already claims the key — a far better message
than the constraint-violation exception the unique index would raise.

## How the Framework Uses Domain Keys

- **`toString()`.** The PDO proxy routes `toString()` to the **domain delegate**, and there a root
  entity's string representation *is* its domain key (components fall back to their singular
  name) — which is why log output and debugger views show business identities, not ids. The
  *persistence* delegate's `toString()`, in contrast, returns the technical `toGenericString()`
  (class plus id/serial).

### Binding a text field to a PDO

In the [Rich Desktop Client](../../../../tentackle-fx-rdc/src/main/docs/rdc.md), a text component
can be bound directly to a **PDO-typed** attribute — say, a `User` field on a form. The
[`PdoStringTranslator`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/translate/PdoStringTranslator.java)
makes that work via the domain key:

- *to view*: the PDO is rendered as its domain key (using the value translator registered for the
  key type, so formatting rules apply).
- *to model*: the typed string is converted to a key and resolved via `findByUniqueDomainKey`. If
  no such PDO exists, the translator opens the entity's **interactive finder**, presetting the
  search criteria from the (possibly incomplete) input — via `setUniqueDomainKey`, or a static
  `lenientValueOf(String)` factory the key type may optionally provide.

So "type the key, get the object — or a search dialog" is default behavior, not something the
application has to build. Related conveniences: the default PDO editor gives initial **focus** to
the first changeable domain-key field, and creating a new PDO from the finder presets it with the
domain key the user searched for.

### Other consumers

- **Caching.** The `--udk` option of the [PdoCache wurblet](cache.md) adds a cache index keyed by
  the domain key, so `selectByUniqueDomainKey`-style lookups are served from the
  [cache](cache.md#indexes-and-keys) without touching the database.
- **Login.** The archetype's server looks up the `User` by its unique domain key (the username) to
  authenticate a session — see the
  [walkthrough](../../../../tentackle-project-archetype/src/main/docs/myapp-walkthrough.md).
- **I18n.** [`StoredBundle`](../../../../tentackle-i18n/src/main/docs/i18n.md) resolves remote
  resource bundles by their `(name, locale)` domain key.

## Related Documentation

- [Model Definition](../../../../tentackle-model/src/main/docs/model-definition.md) — the `key`
  attribute option and the `## indexes` section.
- [Wurblets](../../../../tentackle-wurblets/src/main/docs/wurblets.md) — `Methods`,
  `UniqueDomainKey` and `DomainMethods` in the generation pipeline.
- [PdoSelectList / PdoSelectUnique](../../../../tentackle-persistence-wurblets/src/main/docs/pdo-select.md) —
  how the unique select is generated.
- [Domain Objects](../../../../tentackle-domain/src/main/docs/domain.md) — the domain layer the
  key methods live in.
- [PDO Caching](cache.md) — the domain key as a cache index.
- [PDO](pdo.md) — aggregates, root entities, and the overall programming model.
