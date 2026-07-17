# Preferences — Persistable, Database-Backed Settings

## Overview and Motivation

Desktop and server applications constantly need to remember small pieces of state: window
geometry, the last directory used by a file dialog, per-table column layouts, user options. The
JDK answers this with [`java.util.prefs.Preferences`](https://docs.oracle.com/en/java/javase/25/docs/api/java.prefs/java/util/prefs/Preferences.html),
but its backing store is local to the machine (registry or `~/.java`), which is the wrong place for
a multi-tier application whose users roam between clients.

Tentackle's preferences API in `org.tentackle.prefs` (exported by **`tentackle-core`**, which
`requires transitive java.prefs`) keeps the familiar `Preferences` programming model but lets the
**backing store be pluggable** — most usefully, the application's own database, so a user's settings
follow them to any client connected to the same tier.

## The API

### `PersistedPreferences` — the node interface

[`PersistedPreferences`](../java/org/tentackle/prefs/PersistedPreferences.java) mirrors
`java.util.prefs.Preferences` but is *just an interface*, so the implementation can be swapped at
runtime. It exposes the usual tree of nodes with the static entry points:

```java
PersistedPreferences sys  = PersistedPreferences.systemRoot();      // system space
PersistedPreferences user = PersistedPreferences.userRoot();        // user space (or system, if system-only)
PersistedPreferences only = PersistedPreferences.userRootOnly();    // always user space
```

All three delegate to the [`PersistedPreferencesFactory`](../java/org/tentackle/prefs/PersistedPreferencesFactory.java).

### `PersistedPreferencesFactory` — choosing the backing store

The factory is the swap point. By default `PersistedPreferences` and the JDK's `Preferences`
co-exist independently and Tentackle stores its nodes **in the database**. Because
`PersistedPreferences` can also act as a drop-in `PreferencesFactory`, even third-party code that
talks to the standard preferences API can be redirected onto the database store.

Two implementations ship in the box:

| Implementation | Backing store | Use when |
|----------------|---------------|----------|
| [`DbPreferences`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/prefs/DbPreferences.java) + [`DbPreferencesFactory`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/prefs/DbPreferencesFactory.java) (in [`tentackle-database`](../../../../tentackle-database/src/main/docs/database.md)) | the application database (tables `prefnode` / `prefkey`) | the default — settings roam with the user across clients ([see below](#the-database-implementation-dbpreferences)) |
| [`DefaultPreferences`](../java/org/tentackle/prefs/DefaultPreferences.java) + [`DefaultPreferencesFactory`](../java/org/tentackle/prefs/DefaultPreferencesFactory.java) | the JDK's `java.util.prefs` | you want the plain local store (registry / files) and *no* database persistence |

To force the local JDK store, extend `DefaultPreferencesFactory` and register it as a service:

```java
@Service(PersistedPreferencesFactory.class)
public class JavaUtilPreferencesFactory extends DefaultPreferencesFactory {
  // optionally override isSystemOnly(), systemRoot(), systemNodeForPackage(), userRoot()
  // e.g. to prepend a sub-node for non-class preferences such as table settings,
  // or because the system root is usually not writable by normal users
}
```

### `CompositePreferences` — user space layered over system space

[`CompositePreferences`](../java/org/tentackle/prefs/CompositePreferences.java) combines the system
and user trees so that **user settings override system defaults** — the behavior most applications
want. It also adds convenience for exporting and importing via file dialogs. It is `abstract`: an
application extends it to obtain its own namespace (derived from the class name).

The shadowing works in both directions:

- **Reading** (`getString(key)` etc.) looks the key up in **user space first** and falls back to
  system space only if the user has no value — the user tree is a sparse overlay holding nothing
  but the deviations.
- **Writing** (`setString(key, value)`) keeps the overlay sparse: if the new value equals the
  system-space value (or is `null`), the *user* entry is **removed** instead of stored, so the
  user follows future changes of the system default again instead of pinning a stale copy of it.

With `systemOnly` (per instance, or globally via `PersistedPreferencesFactory.setSystemOnly`),
user space is skipped entirely and writes go to the system tree.

## The Database Implementation (DbPreferences)

The promise "settings roam with the user" is kept by
[`DbPreferences`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/prefs/DbPreferences.java)
and its factory in `org.tentackle.dbms.prefs` (**`tentackle-database`**), registered as
`@Service(PersistedPreferencesFactory.class)` and therefore active by default in any application
with a database session.

### Storage model

Two tables hold everything:
[`DbPreferencesNode`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/prefs/DbPreferencesNode.java)
(`prefnode`, the tree structure) and
[`DbPreferencesKey`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/prefs/DbPreferencesKey.java)
(`prefkey`, the key/value pairs). A node carries a **user** column — empty for system space — so
the user and system trees are ordinary rows in the same tables. Every node and key also stores the
id of its root node, which is what makes whole-tree loading cheap (two `SELECT`s by root id).
Referential integrity between the two tables is maintained at the application level, not by
database foreign keys; `DbPreferencesFactory.checkAllPreferences(db)` repairs the structure after
manual SQL surgery.

### The in-memory repository

The factory keeps a **repository of complete trees**, one per user plus one for system space. The
first access to `userRoot()` or `systemRoot()` loads the *entire* tree in one go — locally with
the two selects above, in a remote client with a single
[TRIP](trip.md) call ([`DbPreferencesOperation`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/prefs/DbPreferencesOperation.java)),
answered by the server from *its* repository. From then on every `get(...)` is a pure in-memory
lookup: **reading a preference never costs a database round trip**.

Writes are buffered the same way: `put(...)`/`remove(...)` only mutate the in-memory tree.
`flush()` persists all pending changes of the subtree **in one transaction**; `sync()` first merges
changes from the backing store, then flushes. Consequently — as with `java.util.prefs` — change
listeners fire when changes reach the backing store, not at `put(...)` time.

### How a change propagates to other clients

Distribution rides on [modification tracking](../../../../tentackle-session/src/main/docs/modification-tracking.md):

1. On a session directly connected to the database, the factory listens for tableserial changes of
   `prefnode`/`prefkey`. After a flush — its own or one from another JVM — it updates the affected
   trees **incrementally** from the expired id/serial pairs (falling back to a full reload if the
   serials show gaps or deletions) and fires the registered `PreferenceChangeListener`s /
   `NodeChangeListener`s. It then bumps the **named master serial `"preferences"`**.
2. A remote client's `ModificationTracker` polls the master serials anyway. When `"preferences"`
   changes, the factory asks the server for a refresh: one remote call per loaded tree, returning
   `null` if the client is already up to date, otherwise the tree's current state, which is diffed
   against the local one — again firing the change listeners for exactly what changed.

The net effect: a user changes a table layout on client A, the flush lands on the server, and
within one tracker poll interval client B sees the new value — including listener notifications,
which is why node- and key events are delivered *in all JVMs*, not just the one that made the
change. `PersistedPreferencesFactory.setAutoSync(false)` turns the automatic refresh off.

Sessions are never opened for this: reads and refreshes use the thread-local session, or fall back
to the modification tracker's session. Only *user-space* operations require a thread-local session,
because the user name is taken from its `SessionInfo`.

### When the store is unreachable

- **Reads keep working.** Loaded trees are served from memory, so a lost connection does not break
  `get(...)` — the application keeps running with the values it has.
- **`flush()` fails loudly** with the checked `BackingStoreException` (wrapping the persistence
  exception), and the affected tree is evicted from the repository so the next access reloads a
  consistent state instead of retrying on top of a half-flushed one.
- If concurrent modifications from another JVM are detected during a flush, they are merged
  (`sync`) or logged as a warning — the flush itself remains atomic.
- `PersistedPreferencesFactory.setReadOnly(true)` turns every flush into a no-op — useful for
  kiosk or demo setups that should never write settings back.

## A concrete consumer

The [POI spreadsheet exporter](../../../../tentackle-fx-rdc-poi/src/main/docs/poi.md) uses preferences
to remember the last export directory per table (key prefix `lastXlsNames/`). The same mechanism
backs RDC window geometry and table column layouts — settings that, thanks to the database backing
store, are restored on whatever client the user signs in to next.

## See also

- [Tentackle Core](core.md) — the runtime foundation that hosts this API.
- [Service and Configuration API](../../../../tentackle-common/src/main/docs/services.md) — how the
  factory is discovered and swapped.
- [Tentackle Session](../../../../tentackle-session/src/main/docs/session.md) — the context that ties a
  user to the database where their preferences live.
- [Modification Tracking](../../../../tentackle-session/src/main/docs/modification-tracking.md) — the
  mechanism that propagates preference changes to other clients.
- [Tentackle Database](../../../../tentackle-database/src/main/docs/database.md) — the module hosting
  the `DbPreferences` implementation.
