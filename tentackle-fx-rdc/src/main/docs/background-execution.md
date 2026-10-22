# Background Execution

## Overview

The JavaFX application thread draws the UI and handles every event. If it is busy, the application is frozen —
no repaint, no click, no keystroke. And in a Tentackle client, almost every interesting operation is a database
call: a query, a save, a reload. On a local database that is milliseconds; through a
[middle-tier server](../../../../src/main/docs/tiers.md) on a bad link it is not. Location transparency means
the *code* looks the same either way — which is exactly why you cannot judge from the call site whether it is
safe to run inline.

So RDC's rule is blunt: **time-consuming persistence calls do not belong on the FX thread**. The tool for moving them off it is
[`Rdc.bg(...)`](../java/org/tentackle/fx/rdc/Rdc.java):

```java
Rdc.bg(node,
       () -> invoice.reload(),             // background thread
       reloaded -> refreshView(reloaded),  // FX thread, on success
       ex -> showError(ex));               // FX thread, on failure
```

The interesting part is what runs the runner. `Rdc.bg(...)` is a thin static facade over
`RdcUtilities.getInstance().runInBackground(...)`, and `RdcUtilities` is a
[`@Service`](../../../../tentackle-common/src/main/docs/services.md) — replaceable. That gives two very
different execution models out of the box:

| | **Default** ([`RdcUtilities`](../java/org/tentackle/fx/rdc/RdcUtilities.java)) | **Pooled** ([`RdcUtilitiesWithBackgroundPool`](../java/org/tentackle/fx/rdc/RdcUtilitiesWithBackgroundPool.java)) |
|---|---|---|
| Runs on | the [`ModificationTracker`](../../../../tentackle-session/src/main/docs/modification-tracking.md) thread | a dedicated thread pool |
| Session | the tracker's **one** session | one pooled session **per task** |
| Parallelism | none — tasks are queued and run one after another | up to the pool's maximum size |
| Side effect | the tracker cannot poll while a task runs | tracker keeps polling, undisturbed |
| Setup | none | subclass + `@Service(RdcUtilities.class)` |

The default costs nothing and is right for the common case: short queries, one at a time, in response to a
click. The pool is what you install when that stops being true.

---

## The `Rdc.bg` API

Three overloads, each narrowing the previous one:

```java
// 1. full control
static <V> void bg(Node node, Supplier<V> runner, Consumer<V> updateUI, Consumer<RuntimeException> failedUI);

// 2. failures become an error dialog:  rx -> Fx.error(node, rx.getLocalizedMessage(), rx)
static <V> void bg(Node node, Supplier<V> runner, Consumer<V> updateUI);

// 3. no result, no UI update — failures still become an error dialog
static void bg(Node node, Runnable runner);
```

| Parameter | Thread | Notes |
|---|---|---|
| `node` | — | optional; gets the **wait cursor** while the task runs, restored afterwards. `null` for none |
| `runner` | background | the lengthy work. Its return value is handed to `updateUI` |
| `updateUI` | **FX** | optional; posted via `Platform.runLater(...)` |
| `failedUI` | **FX** | optional; receives the `RuntimeException`. If `null`, the failure is only logged as severe |

Overload 3 wraps the `Runnable` into a `Supplier` returning `null` and delegates to overload 2, so all
three funnel into the same `runInBackground(...)` call.

A failure is only logged if overload 1 is called with `failedUI == null`; overloads 2 and 3 always show an
error dialog.

The division of labor is the whole point: **the runner touches data, the consumers touch the UI.** Neither
should do the other's job.

```java
Rdc.bg(getView(),
       () -> on(Invoice.class).selectByCustomer(customerId),     // database — background
       invoices -> table.setItems(observableList(invoices)));    // scene graph — FX thread
```

---

## The Default: Running on the ModificationTracker

With no configuration, `runInBackground(...)` wraps the runner into an
[`AbstractSessionTask`](../../../../tentackle-session/src/main/java/org/tentackle/session/AbstractSessionTask.java)
and hands it to the modification tracker:

```java
ModificationTracker modificationTracker = ModificationTracker.getInstance();
if (Application.getInstance() != null ||
    modificationTracker.isAlive()) { // MT may be not alive yet while starting up the application
  ModificationTracker.getInstance().addTask(task);
}
else {
  // no application and no tracker running?
  task.run();   // corner case in generic tools like PdoBrowser
}
```

### Why the tracker at all

It looks like an odd host for UI work until you notice what the tracker already is (see
[Modification Tracking](../../../../tentackle-session/src/main/docs/modification-tracking.md)): a live,
non-daemon thread that owns an open session, is a full
[task dispatcher](../../../../tentackle-core/src/main/docs/tasks-daemon.md), and ticks reliably at a fixed
interval. A desktop client already pays for it. Reusing it for background work means **no extra threads, no
extra database sessions, and no extra configuration** — a client with one session on the wire stays a client
with one session on the wire.

In a [`DesktopApplication`](../java/org/tentackle/fx/rdc/app/DesktopApplication.java) that session is a clone
of the login session, grouped with it, created in `configureModificationTracker()`. So background work does not
compete with anything the UI thread might still be doing on the main session either.

### What actually happens

1. If `node` was given, it gets the wait cursor.
2. The task is queued on the tracker. `DefaultSessionTaskDispatcher.addTask(...)` injects the tracker's session
   into it.
3. The tracker thread picks it up. Because that thread called `session.makeCurrent()` at startup, the tracker's
   session **is the thread-local session** — so plain `on(MyPdo.class)...` inside the runner just works,
   without a session parameter anywhere.
4. If the runner itself implements
   [`SessionDependable`](../../../../tentackle-session/src/main/java/org/tentackle/session/SessionDependable.java),
   it gets the session set explicitly before `get()` is called.
5. The result goes back through `Platform.runLater(...)` to `updateUI`; a `RuntimeException` goes to
   `failedUI`, or is logged as severe if there is none. The cursor is restored either way.

There is a fast path: a runner that already *is* an `AbstractSessionTask`, with no node and no consumers, is
queued as-is rather than wrapped.

### The consequences you must know

**Tasks are serialized.** One thread, one queue. Two `Rdc.bg(...)` calls do not overlap — the second waits for
the first. Usually invisible; occasionally the reason a screen feels sticky.

**A long task delays modification tracking.** The tracker polls between tasks, not during them. A task that
runs for thirty seconds is thirty seconds in which no `ModificationEvent` fires, no
[PDO cache](../../../../tentackle-pdo/src/main/docs/cache.md) is invalidated, and — because
`cleanup()` warns when a poll is more than twice the sleep interval late — a warning in the log saying so. The
tracker is not just a convenient thread; it has a job of its own.

**The session is shared and single.** Every background task uses the same session, one at a time. That is
consistent, but it also means a task holding a transaction open blocks the next task's, and the tracker's own
pending-count flush, until it commits.

**The `else` branch runs inline.** With no `Application` *and* a dead tracker, the task runs on the **calling
thread** — which, from a UI action, is the FX thread. This is a deliberate corner case for generic tools
where there is no tracker to defer to. In a normal application, once `finishStartup()` has started the tracker,
it does not apply — but it is worth knowing why `Rdc.bg(...)` occasionally blocks in a test harness or a tool.

> A rule of thumb: the default suits work measured in **milliseconds to a couple of seconds**, triggered by a
> user action, one at a time. Report generation, exports, bulk operations and dashboards that load six panels
> at once are what the pool is for.

---

## The Alternative: A Background Session Pool

[`RdcUtilitiesWithBackgroundPool`](../java/org/tentackle/fx/rdc/RdcUtilitiesWithBackgroundPool.java) overrides
`runInBackground(...)` to use a
[`SessionPooledExecutor`](../../../../tentackle-session/src/main/java/org/tentackle/session/SessionPooledExecutor.java)
— a thread pool where **each task gets its own session** from a
[`SessionPool`](../../../../tentackle-session/src/main/java/org/tentackle/session/SessionPool.java) for the
duration of its run.

Tasks now run in parallel, and the tracker keeps polling because nothing is queued on it anymore.

### Installing it

The class carries **no** `@Service` annotation of its own — it is a base class, not a drop-in. Subclass it and
register the subclass for `RdcUtilities`:

```java
@Service(RdcUtilities.class)
public class MyRdcUtilities extends RdcUtilitiesWithBackgroundPool {

  public static MyRdcUtilities getInstance() {   // to access the extra methods
    return (MyRdcUtilities) RdcUtilities.getInstance();
  }

  @Override
  protected SessionPool createSessionPool(Session session) {
    // 10 sessions max, at least 1 open (default is 3 and none open)
    return SessionPoolFactory.getInstance().create("BG", session, 1, 1, 1, 10, 5, 0);
  }
}
```

That is the entire migration. `Rdc.bg(...)` call sites do not change — they resolve `RdcUtilities.getInstance()`
through the SPI, and now get yours. `RdcUtilities` itself is registered as `@Service(RdcUtilities.class)`
"defaults to self", and the application's module takes precedence; see
[Service and Configuration API](../../../../tentackle-common/src/main/docs/services.md) for the ordering rules.

The typed `getInstance()` is worth adding: the [parallel-runner methods](#running-many-tasks-in-parallel) live
on the subclass, and `RdcUtilities.getInstance()` is typed to the base class.

### Sizing the pool

`createSessionPool(...)` is the hook. The default is deliberately modest:

```java
SessionPoolFactory.getInstance().create("BG", session, 0, 1, 0, 3, 5, 0);
```

| Argument | Default | Meaning |
|---|---|---|
| `name` | `"BG"` | pool name — also names the thread group and the threads |
| `session` | tracker's session | the **lead session**: the pool clones its configuration (URL, credentials, backend) |
| `initialSize` | `0` | sessions opened up front — `0` means the first task pays for opening one |
| `incrementSize` | `1` | how many to add when all are in use |
| `minimumSize` | `0` | sessions to keep open; `< 0` shuts the pool down when all are closed |
| `maximumSize` | `3` | **the parallelism ceiling**; `0` = unlimited |
| `idleMinutes` | `5` | close unused sessions after this long |
| `usageMinutes` | `0` | maximum time a session may stay in use; `0` = unlimited |

`maximumSize` is the number that matters — it is how many background tasks can truly run at once, and it is
also how many extra connections your client holds. Three is a compromise; a dashboard loading six panels wants
more, and a server-side connection budget may want fewer.

`initialSize = 1` / `minimumSize = 1`, as in the example above, trades one always-open session for a first
background task that does not have to wait for a connection handshake.

### How a task runs

`SessionPooledExecutor.submit(...)`, per task:

1. `session = sessionPool.get()` — check out a session.
2. `session.makeCurrent()` — make it the **thread-local session for this pool thread**, so `on(MyPdo.class)`
   works inside the runner exactly as it does under the default.
3. If the task is `SessionDependable`, set the session on it too.
4. `task.get()`, then the success handler.
5. `finally`: `session.clearCurrent()` and `sessionPool.put(session)` — the session goes back to the pool.

A `RuntimeException` is captured and the fail handler runs **after** the session has been returned, so a
handler that itself touches the database cannot deadlock against its own task's session. `Throwable`s that are
not `RuntimeException` are wrapped in a `TentackleRuntimeException` — or logged and rethrown if there is no
fail handler.

`RdcUtilitiesWithBackgroundPool` wraps both handlers in `Platform.runLater(...)`, restores the node's cursor,
and logs the failure as severe if there is no `failedUI`, so the `Rdc.bg(...)` contract is identical to the
default's.

### Threads and sessions are sized together

```java
public int getThreadPoolSize() {
  return sessionPool.getMaximumSize();
}
```

A limited session pool gets a **fixed** thread pool of exactly that size; tasks beyond it queue. An unlimited
session pool (`maximumSize = 0`) gets a **cached** thread pool that grows and shrinks on demand.

> **Do not size the thread pool above the session pool.** As `getThreadPoolSize()` warns, more threads than
> sessions means a thread will eventually ask an exhausted pool for a session and get an exception. Queuing is
> the correct behavior; failing is not. Override `getThreadPoolSize()` — in a `SessionPooledExecutor` subclass
> returned from `createExecutor()` — only to make the thread pool *smaller*.

Pool threads are daemons, named `BG(1)`, `BG(2)`, … in a thread group named after the pool — which makes them
easy to spot in a thread dump.

### Lifecycle

```java
protected SessionPooledExecutor createExecutor() {
  Session session = ModificationTracker.getInstance().getSession();
  SessionPooledExecutor executor = new SessionPooledExecutor(createSessionPool(session));
  session.registerCloseHandler(new SessionCloseHandler() { ... afterClose -> executor.shutdown() ... });
  return executor;
}
```

Note the irony: the pool still uses the tracker's session — as the **template** to clone its own sessions from,
and as the **lifetime signal**. When the tracker's session closes (the client is logging out, or the server has
killed it via a [`LogoutEvent`](../../../../tentackle-session/src/main/docs/modification-tracking.md#a-worked-example-logoutevent)),
the close handler shuts the executor and the pool down with it. What the pool does *not* do is run tasks on
that session — which was the point.

The executor is created in the constructor, so it exists as soon as the singleton is first resolved. It is
reachable via `getExecutor()` if you need the `ExecutorService` or the `SessionPool` directly.

### Running many tasks in parallel

The real payoff is a second `runInBackground(...)` — available **only** on the pooled implementation, which is
why the typed `getInstance()` matters:

```java
public <V> void runInBackground(Node node, Collection<Supplier<V>> runners, long timeoutMillis,
                                Consumer<Map<Supplier<V>, V>> finishedUI,
                                Consumer<Map<Supplier<V>, RuntimeException>> failedUI);

// failures shown in a dialog for you
public <V> void runInBackground(Node node, Collection<Supplier<V>> runners, long timeoutMillis,
                                Consumer<Map<Supplier<V>, V>> finishedUI);
```

Every runner goes to the pool on its own session; a dedicated waiter thread — not one of the pool's — waits on a
`CountDownLatch` for all of them, then the handlers fire on the FX thread with results keyed **by the runner
that produced them**:

```java
List<Supplier<Object>> panels = List.of(
    () -> on(Invoice.class).selectOverdue(),
    () -> on(Customer.class).selectTopTen(),
    () -> on(Ticket.class).selectOpen());

MyRdcUtilities.getInstance().runInBackground(
    dashboard, panels, 10_000,
    results -> results.forEach((panel, data) -> render(panel, data)));
```

Three queries, three sessions, one round of wall-clock time instead of three. Semantics worth remembering:

- `timeoutMillis <= 0` waits forever; otherwise a timeout adds a synthetic `PersistenceException` to the
  failure map.
- The variant without `failedUI` shows the failure messages in a warning dialog; passing `null` as `failedUI`
  only logs them.
- `failedUI` is invoked **before** `finishedUI`, and only when something failed.
- `finishedUI` is invoked **always** — including when some runners failed. Its map holds only the successful
  results, so check the failure map before treating it as complete.
- Only non-null results are put in the map.

Under the default implementation this method does not exist, and it could not: three tasks on one thread with
one session are just three tasks in a row.

---

## Rules for the Runner

Independent of which implementation is installed:

- **Do not touch the scene graph from the runner.** That is what `updateUI` is for. If you genuinely must — as
  [`TablePrinter`](../java/org/tentackle/fx/rdc/table/TablePrinter.java) must, because it needs the real
  rendered row height to paginate — use `FxUtilities.getInstance().runAndWait(...)` to hop to the FX thread and
  back.
- **Do not let the session escape.** Under the pool, the session is checked back in the moment the runner
  returns; a PDO handed to `updateUI` is fine (PDOs are never
  [detached](../../../../tentackle-pdo/src/main/docs/pdo-vs-orm.md)), a raw `Session`, cursor or open
  transaction is not.
- **Return, don't mutate.** Hand results back through the return value rather than writing to fields the FX
  thread reads. Under the pool this is a genuine data race, not a style preference.
- **Throw `RuntimeException`s.** They are what `failedUI` receives; anything else is logged, and under the pool
  wrapped.

Two framework call sites show the shape:

```java
// PdoSearch: the finder decides whether the query is slow enough to be worth it
if (finder.isSearchRunningInBackground()) {
  Rdc.bg(getView(), finder::runSearch,
         result -> { setItems(result); presetScrolling(); },
         ex -> Fx.error(getView(), ex.getLocalizedMessage(), ex));
}
else {
  Platform.runLater(() -> { setItems(finder.runSearch()); presetScrolling(); });
}

// TablePrinter: a task that is itself an AbstractSessionTask and a Supplier
Rdc.bg(tableView, new PrinterTask(items, job, pageLayout, stage), null, null);
```

Note `PdoFinder.setSearchRunningInBackground(...)`: it defaults to **false**. Not every query is worth the trip
off the thread, and the framework leaves that judgment to the entity.

---

## Choosing

Stay with the default when background work is occasional, short, and user-triggered — most CRUD clients never
need more.

Move to the pool when any of these is true:

- background tasks are **long** enough that stalling modification tracking matters;
- you want **several** tasks to run at once (a dashboard, parallel exports, a batch of independent queries);
- a task holds a **transaction** long enough to get in the next one's way;
- you need the `Collection<Supplier<V>>` parallel API.

The cost is honest and worth stating: extra threads, and up to `maximumSize` extra database sessions per
client. On a middle-tier server serving hundreds of clients, that multiplies — which is why the default pool
is three, and why the default implementation is not the pool.

---

## Related Documentation

- [tentackle-fx-rdc](rdc.md) — the rich desktop client framework `Rdc.bg(...)` belongs to.
- [Modification Tracking](../../../../tentackle-session/src/main/docs/modification-tracking.md) — the tracker whose thread and session the default implementation borrows.
- [Tentackle Session](../../../../tentackle-session/src/main/docs/session.md) — `SessionPool`, `SessionPooledExecutor` and the thread-local session.
- [Tasks and Daemons](../../../../tentackle-core/src/main/docs/tasks-daemon.md) — the task dispatcher behind `addTask(...)`.
- [Service and Configuration API](../../../../tentackle-common/src/main/docs/services.md) — how `@Service(RdcUtilities.class)` replaces the default implementation.
- [tentackle-fx](../../../../tentackle-fx/src/main/docs/fx.md) — `FxUtilities.runAndWait(...)` and the FX layer.
- [PDO Caching](../../../../tentackle-pdo/src/main/docs/cache.md) — what stops being invalidated while the tracker is busy running your task.
- [Multi-Tier Cascade](../../../../src/main/docs/tiers.md) — why a persistence call's cost is not visible at the call site.
