# Application Bootstrap — From `main()` to a Running Tier

## Overview and Motivation

Every Tentackle application — a database-backed console daemon, a TRIP application
server, a JavaFX desktop client, a web application in a servlet container — starts
the same way:

```java
public static void main(String[] args) {
  new MyApplication().start(args);
}
```

What differs between them is *not* the startup logic but the **order and timing** of
a fixed set of steps. A console application runs them straight through. A server adds
session pools and a TRIP listener. A desktop client cannot log in until a human has
typed a password, so it suspends midway through and resumes on a JavaFX thread. A web
application is a server that hands sessions to a servlet container.

[`Application`](../java/org/tentackle/app/Application.java) captures what all of them
have in common: it is the **process-wide singleton** that owns the session, the root
[domain context](domain-context-provider.md), the application's properties and its
lifecycle. [`AbstractApplication`](../java/org/tentackle/app/AbstractApplication.java)
implements the steps as overridable template methods; each tier's base class assembles
them into the sequence that tier needs.

This document describes that hierarchy, the template steps, and the four shapes.

---

## The Hierarchy

```text
                    Application                        (interface, tentackle-pdo)
                        │                               SessionProvider + DomainContextProvider
                    AbstractApplication                (abstract, tentackle-pdo)
                        │                               template methods + start/stop
          ┌─────────────┴──────────────┐
  AbstractClientApplication     AbstractServerApplication        (tentackle-persistence)
  (tentackle-pdo)                       │                         pools, container detection
          │                    ┌────────┴────────┐
   ConsoleApplication    ServerApplication   WebApplication<T>   (tentackle-web)
   (tentackle-pdo)       (TRIP server)       (servlet container)
          │
   DesktopApplication<C>            (tentackle-fx-rdc)
          │
   UpdatableDesktopApplication<C>   (tentackle-fx-rdc-update)
```

| Class | Module | Shape |
|-------|--------|-------|
| [`Application`](../java/org/tentackle/app/Application.java) | tentackle-pdo | The contract and the singleton registry. |
| [`AbstractApplication`](../java/org/tentackle/app/AbstractApplication.java) | tentackle-pdo | The template: properties, session, start/stop. |
| [`AbstractClientApplication`](../java/org/tentackle/app/AbstractClientApplication.java) | tentackle-pdo | Adds `updateSessionInfoAfterLogin()`. |
| [`ConsoleApplication`](../java/org/tentackle/app/ConsoleApplication.java) | tentackle-pdo | Non-interactive client: daemons, batch jobs, tools. |
| [`AbstractServerApplication`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/app/AbstractServerApplication.java) | tentackle-persistence | Serves other tiers: pools, connection manager. |
| [`ServerApplication`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/app/ServerApplication.java) | tentackle-persistence | Adds the [TRIP](../../../../tentackle-core/src/main/docs/trip.md) listener. |
| [`WebApplication<T>`](../../../../tentackle-web/src/main/java/org/tentackle/web/app/WebApplication.java) | tentackle-web | A server bridged to a web framework. |
| [`DesktopApplication<C>`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/app/DesktopApplication.java) | tentackle-fx-rdc | Interactive JavaFX client. |

Note that `DesktopApplication` is a **client**, not a server, and `WebApplication`
*is* a server — a web application talks to browsers, but from Tentackle's point of
view it serves sessions to other code, exactly like a TRIP server.

---

## `Application` — The Contract

`Application` extends `SessionProvider` and `DomainContextProvider`, which is what
makes it the **root of context propagation**: the application singleton holds the
context every PDO created without an explicit one inherits. See
[The DomainContextProvider](domain-context-provider.md).

**One per JVM.** `register()` installs the instance and throws if a different one is
already registered; `unregister()` removes it. `Application.getInstance()` returns it
from anywhere — or `null` before startup:

```java
Application app = Application.getInstance();
```

The interface also exposes the application's identity (`getName()`, `getVersion()`,
`getCreationTime()`), its `CommandLine` and `SessionInfo`, property accessors, and
the lifecycle pair `start(String[])` / `stop(int, Throwable)` with no-arg convenience
defaults.

One method has no default and no implementation in any framework class — every
application must supply it:

```java
<U extends Identifiable> U getUser(DomainContext context, long userId);
```

The framework knows a user only as an id; mapping that id to the application's own
user PDO is application knowledge. The generated project implements it as a cached
lookup:

```java
@Override
public User getUser(DomainContext context, long userId) {
  return Pdo.create(User.class, context).selectCached(userId);
}
```

Two predicates let framework code branch on the tier without knowing the class:
`isServer()` (false by default, true in `AbstractServerApplication`) and
`isInteractive()` (false by default, true in `DesktopApplication`).

---

## `AbstractApplication` — The Template

### Construction

The constructor takes a name and a version, either of which may be `null`:
`filterName()` then falls back to the class's simple name — walking up past anonymous
inner classes, which is what makes unnamed applications in unit tests still report
something useful — and `filterVersion()` falls back to `"1.0.0-SNAPSHOT"`. It also
records `creationTime` and installs a default uncaught-exception handler that logs
rather than letting threads die silently.

### `start(args)`

```java
@Override
public void start(String[] args) {
  cmdLine = new CommandLine(args);
  setProperties(cmdLine.getOptionsAsProperties());
  try {
    startup();
  }
  catch (RuntimeException e) {
    stop(1, e);      // a failed startup is a stop, not a stack trace
  }
}
```

Command-line options become application properties.
[`CommandLine`](../../../../tentackle-core/src/main/java/org/tentackle/misc/CommandLine.java)
parses `--flag` and `--key=value`; everything else is a plain argument. So
`--url=... --user=scott --statistics` arrives as three properties.

`startup()` is **abstract** — it is the one step `AbstractApplication` refuses to
guess, because the order is exactly what distinguishes the tiers.

### The steps

| Step | Default behavior |
|------|------------------|
| `initialize()` | Applies properties, initializes scripting, logs the discovered module hooks. The first step, always. |
| `login()` | *(subclass)* Creates the `SessionInfo`, obtains the [credentials](../../../../tentackle-session/src/main/docs/authentication.md), opens the session, makes it current, sets the root domain context. |
| `configure()` | Calls `configureModificationTracker()`, `configurePreferences()`, `configureSecurityManager()`. Runs **after** the connection exists. |
| `finishStartup()` | Activates statistics if asked, starts the [modification tracker](../../../../tentackle-session/src/main/docs/session.md), registers its emergency-shutdown hook. |
| `cleanup()` | Terminates PDO helper threads and closes the session. |

The split between `configure()` and `finishStartup()` matters: `configure()` prepares
singletons that need a live session, `finishStartup()` starts the threads that use
them. Subclasses override individual steps rather than the sequence.

`initializeScripting()` is a small but load-bearing default: if no default scripting
language is set and exactly one provider is on the module path, it becomes the
default. Most applications therefore never configure
[scripting](../../../../tentackle-core/src/main/docs/scripting.md) at all.

### Well-known properties

`applyProperties()` interprets a set of properties that need no application code:

| Property | Effect                                                                                                                                  |
|----------|-----------------------------------------------------------------------------------------------------------------------------------------|
| `notracker` | Don't start the modification tracker — and disable [PDO caching](cache.md) globally, since a cache without invalidation would go stale. |
| `nosecurity` | Disable the [security manager](security.md).                                                                                            |
| `statistics` | Collect PDO/operation method invocation statistics and TRIP/SQL/Transaction statistics (if server). Recommended during development.     |
| `scripting=<lang>` | Set the default scripting language.                                                                                                     |
| `locale=<tag>` | Set the default locale; falls back to the user preference, and warns if unsupported.                                                    |
| `readonlyprefs` | Silently ignore [preference](../../../../tentackle-core/src/main/docs/preferences.md) writes.                                           |
| `systemprefs` | System preferences only (forced on servers).                                                                                            |
| `noprefsync` | Turn off preference auto-sync between JVMs.                                                                                             |

Values are run through `StringHelper.evaluate(...)`, so they may reference system
properties. A property named `SYSTEM_foo` or `^foo` is *set* as system property `foo`
instead of being stored — the log deliberately records only the key at INFO level,
never the value, since it may be a password. Command-line properties take precedence
over properties from a file.

A small shell script `src/scripts/collect_statistics.sh` is provided to collect
statistics from log files.

### `stop(exitValue, throwable)`

Guarded against re-entry by a `stopping` flag, so the shutdown path can be reached
from several directions at once without running twice. It logs, runs `cleanup()`
(catching and logging failures rather than letting them mask the exit), unregisters,
and finally calls `System.exit(exitValue)` — but only if
`isSystemExitNecessaryToStop()` says so. That hook is what lets a server running
inside a container shut down without killing the container's JVM.

---

## Shape 1 — Console

[`ConsoleApplication`](../java/org/tentackle/app/ConsoleApplication.java) is the
straight-through case, and the clearest illustration of the template:

```java
@Override
protected void startup() {
  register();        // only one application at a time
  initialize();      // environment
  login();           // connect to database or server
  configure();       // tracker, preferences, security
  finishStartup();   // start the tracker
}
```

Its `login()` builds a `SessionInfo` from `--user` / `--password` / `--backend`, or
takes `--url`, `--user` and `--password` directly from the command line when both a
URL and a user are given, bypassing the properties file. It then opens the session,
makes it current, and installs the root domain context.

Despite the name, `ConsoleApplication` is the base for anything non-interactive:
daemons, batch jobs, migration tools. The generated project's daemon extends it and
overrides two steps:

```java
public class MyAppDaemon extends ConsoleApplication {

  public MyAppDaemon() { super("MyAppDaemon", Version.RELEASE); }

  @Override
  protected void configurePreferences() {
    super.configurePreferences();
    User user = PdoUtilities.getInstance().getUser(getDomainContext());
    PersistedPreferencesFactory.getInstance().setSystemOnly(user.isSystemPreferencesOnly());
  }

  @Override
  protected void finishStartup() {
    latestMessageId = on(Message.class).selectMaxId();
    Pdo.listen(this::logMessages, Message.class);
    super.finishStartup();
  }

  public static void main(String[] args) { new MyAppDaemon().start(args); }
}
```

Note the shape: call `super`, add your own work. `configurePreferences()` can already
reach the logged-in user, because `configure()` runs after `login()`.

---

## Shape 2 — Server

[`AbstractServerApplication`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/app/AbstractServerApplication.java)
uses the same `startup()` order as the console case but hardens and extends the steps:

- **`isServer()` returns true**, and `setSessionInfo()` marks the info **immutable**
  and refuses to replace it once set — a running server must not have its identity
  changed underneath it.
- **`configurePreferences()`** forces system-only preferences; **`configureModificationTracker()`**
  drops the poll interval to 1000 ms.
- **`initializeScripting()`** additionally calls `validateValidators()`, which walks
  every PDO class and touches every [validator](../../../../tentackle-core/src/main/docs/validation.md),
  compiling all validation scripts *at startup*. A server pays that cost once, at a
  predictable moment, instead of making the first user of each entity wait.
- **`finishStartup()`** creates the pools: a `MultiUserSessionPool` if the server is
  itself a client of another server (the [cascade](../../../../src/main/docs/tiers.md)),
  otherwise its own `ConnectionManager` and a `SessionPool`. The local pool hands out
  sessions with a **wiped** `SessionInfo` — no user id, no name, no password — forcing
  the caller to authenticate rather than inheriting the server's identity.
- **The constructor calls `detectContainer()`**, a JNDI lookup of `java:comp/env`. If
  it succeeds, `isSystemExitNecessaryToStop()` returns false and `cleanup()` also
  deregisters JDBC drivers loaded by the application's classloader, avoiding the
  classic container redeploy leak.

[`ServerApplication`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/app/ServerApplication.java)
adds remoting on top: it initializes the `LockManager` when connected directly to a
database, creates the `TripServer` in `finishStartup()`, starts it as the final act
of `startup()`, and on shutdown closes every open remote session before stopping the
listener. Its constructor takes the `RemoteDbConnectionImpl` subclass that defines the
server's remote API.

```java
public class MyAppServer extends ServerApplication {

  public MyAppServer() {
    super("MyAppServer", Version.RELEASE, MyAppRemoteDbConnectionImpl.class);
  }

  @Override
  protected void finishStartup() {
    super.finishStartup();
    createUpdateService();
  }
}
```

---

## Shape 3 — Desktop

### Two different `Application` types

This is the one genuinely confusing part of the hierarchy, and worth stating plainly:

| Type | Really is | Role |
|------|-----------|------|
| [`DesktopApplication<C>`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/app/DesktopApplication.java) | `extends AbstractClientApplication` | **The Tentackle application.** The singleton, the session, the context. |
| [`FxApplication`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/app/FxApplication.java) | `extends javafx.application.Application` | **The JavaFX application.** The FX lifecycle entry point. |

`FxApplication` is *not* part of the Tentackle `Application` hierarchy at all — it is
JavaFX's. The two hold references to each other: `FxApplication.start(stage)` calls
`DesktopApplication.setFxApplication(this)`, and `FxApplication.stop()` (which JavaFX
invokes when the last window closes) forwards to `Application.getInstance().stop(...)`.

### The interleaved startup

A desktop client cannot run the template straight through, because `login()` needs a
password that does not exist yet. So `DesktopApplication.startup()` runs only the
steps that need no connection, then hands control to JavaFX:

```java
@Override
protected void startup() {
  register();
  initialize();
  Application.launch(getApplicationClass(), getCommandLine().getArgs());  // javafx.application.Application
}
```

`launch(...)` blocks until the FX application exits, so `startup()` does not return
for the life of the client. The remaining steps run later, driven by the login view:

```text
main()
 └─ new MyAppFxClient().start(args)
     └─ AbstractApplication.start → startup()
         ├─ register(), initialize()
         └─ javafx.application.Application.launch(LoginApplication.class)
             └─ [FX thread] FxApplication.start(stage)
                 ├─ desktopApplication.setFxApplication(this)
                 ├─ registerUncaughtExceptionHandler()
                 └─ LoginApplication.startApplication(stage) → shows the login view
                     └─ user authenticates
                         └─ [background thread] DesktopApplication.login(view, sessionInfo)
                             ├─ createSession() / createDomainContext()
                             ├─ configure()                 "CONFIGURE APPLICATION..."
                             ├─ Fx.load(mainControllerClass) "LOADING GUI..."
                             └─ [FX thread] show main stage
                                 └─ on WINDOW_SHOWN → finishStartup(), hide login view
```

Two details are worth pulling out. First, `login()` runs on a **background service
thread**, not the FX thread, so the login view can keep repainting its progress bar
while the connection is established — which is why `DesktopApplication` overrides
`configurePreferences()`, `configureSecurityManager()` and
`configureModificationTracker()` purely to report progress before delegating to
`super`. Second, `finishStartup()` fires on `WINDOW_SHOWN` of the *main* stage: the
login window is not dismissed until the real window is up, so a failure late in
startup still leaves the user somewhere sensible.

`DesktopApplication` also gives the modification tracker its **own cloned session**,
grouped with the main one, so tracker polling does not contend with the UI's session.
And via `classMapped(...)` it pre-compiles each PDO's validators in the background the
first time the class is used — the interactive counterpart to the server's eager
`validateValidators()`.

`getApplicationClass()` returns the `FxApplication` to launch, defaulting to
[`LoginApplication`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/app/LoginApplication.java);
override it to supply a custom login view. See
[rdc.md → Application lifecycle](../../../../tentackle-fx-rdc/src/main/docs/rdc.md#application-lifecycle-app-login)
for the login package itself, and
[fx-rdc-update.md](../../../../tentackle-fx-rdc-update/src/main/docs/fx-rdc-update.md)
for `UpdatableDesktopApplication`, which adds self-update.

---

## Shape 4 — Web

[`WebApplication<T>`](../../../../tentackle-web/src/main/java/org/tentackle/web/app/WebApplication.java)
extends `AbstractServerApplication` — inheriting the pools, the container detection
and the immutable session info — and adds a cache mapping the web framework's session
key (type `T`) to a Tentackle `SessionInfo`. It slots into the template at three
points: `startup()` creates the cache before `super.startup()`, `finishStartup()`
starts it, `cleanup()` terminates it. The request-scoped `getSession(key)` /
`putSession(session)` pair is the bridge itself. See
[web.md](../../../../tentackle-web/src/main/docs/web.md).

---

## Shutdown

`stop()` is reachable from several directions, and they converge:

- the application calls `stop()` itself;
- startup throws, and `start()` calls `stop(1, e)`;
- JavaFX closes the last window → `FxApplication.stop()` → `Application.stop(...)`;
- the modification tracker terminates — cleanly (`stop(0, null)`) or unexpectedly,
  which logs `*** emergency shutdown ***` and exits with 2;
- a server sends a [`LogoutEvent`](../java/org/tentackle/app/LogoutEvent.java), whose
  default [`LogoutEventHandler`](../java/org/tentackle/app/LogoutEventHandler.java)
  terminates the modification tracker — and therefore, by the previous rule, the
  application. `LogoutEvent` is a `MasterSerialEvent` marked
  `@TripReferencable(NEVER)`; a client that needs to shut down differently replaces
  the handler.

Each `cleanup()` extends its parent's: the desktop client logs statistics and
terminates the FX toolkit, the TRIP server closes remote sessions and stops the
listener, the server shuts down pools and the connection manager, and
`AbstractApplication` closes the session last.

---

## Writing Your Own

1. **Pick the base class** from the table above.
2. **Provide a constructor** passing a name and version (`null` for defaults).
3. **Implement `getUser(context, userId)`** — the one mandatory method.
4. **Override individual template steps**, always calling `super` — not `startup()`,
   unless you are defining a genuinely new tier shape.
5. **Call `start(args)` from `main()`.**

For desktop clients, additionally implement `getMainControllerClass()` and,
optionally, `getApplicationClass()` and `configureMainStage(...)`.

---

## Summary

| Concern | How the hierarchy handles it |
|---------|------------------------------|
| **Singleton** | `register()` / `unregister()` guarantee one application per JVM; `getInstance()` reaches it from anywhere. |
| **Root context** | `Application` is a `SessionProvider` + `DomainContextProvider`; every context-less PDO inherits from it. |
| **Uniform steps** | `initialize` → `login` → `configure` → `finishStartup`, overridden individually, assembled by `startup()`. |
| **Configuration** | Command-line options become properties; well-known keys need no code; `SYSTEM_`/`^` set system properties. |
| **Tier differences** | Servers add pools and TRIP; desktop clients interleave with the FX lifecycle; web applications bridge to a container. |
| **Shutdown** | One re-entrant `stop()`; `isSystemExitNecessaryToStop()` keeps containers alive; cleanup chains through the hierarchy. |

### See also

- [The DomainContextProvider](domain-context-provider.md)
- [rdc.md → Application lifecycle](../../../../tentackle-fx-rdc/src/main/docs/rdc.md#application-lifecycle-app-login)
- [web.md](../../../../tentackle-web/src/main/docs/web.md)
- [fx-rdc-update.md](../../../../tentackle-fx-rdc-update/src/main/docs/fx-rdc-update.md)
- [tiers.md](../../../../src/main/docs/tiers.md)
- [session.md](../../../../tentackle-session/src/main/docs/session.md)
- [trip.md](../../../../tentackle-core/src/main/docs/trip.md)
