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
| `PersistedPreferences` + [`PersistedPreferencesFactory`](../java/org/tentackle/prefs/PersistedPreferencesFactory.java) | the application database | the default — settings roam with the user across clients |
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
