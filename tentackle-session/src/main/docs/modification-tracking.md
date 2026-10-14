# Modification Tracking

## Overview

A user in Hamburg changes a customer's address. A user in Munich has that same customer on screen, and a
third JVM — a middle-tier server — holds it in a [PDO cache](../../../../tentackle-pdo/src/main/docs/cache.md).
Nobody told either of them anything. This is the problem the **modification tracker** solves: it is the
framework's change-notification bus, and it works across processes, across tiers, and across the database.

The design rests on a single idea: **every tracked name owns a serial counter in a database table**, and every
write bumps it. A JVM that wants to know whether something changed only has to compare serials. The database
is the single source of truth, so a client connected directly to the database and a client three tiers away
from it observe the very same numbers — no message broker, no database-specific notification mechanism, no
extra infrastructure.

Concretely:

- [`ModificationTracker`](../java/org/tentackle/session/ModificationTracker.java) — the interface and the
  singleton. Lives in `tentackle-session`, so the API stays free of any database dependency.
- [`DbModificationTracker`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/DbModificationTracker.java)
  — the implementation: a high-priority thread with its own session, polling for changes.
- [`PdoModificationTracker`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/PdoModificationTracker.java)
  — the PDO-aware subclass that ordinary applications actually run.
- [`ModificationListener`](../java/org/tentackle/session/ModificationListener.java) — what the application
  registers to be told about changes, most conveniently through
  [`Pdo.listen(...)`](../../../../tentackle-pdo/src/main/java/org/tentackle/pdo/Pdo.java).
- [`MasterSerialEvent`](../java/org/tentackle/session/MasterSerialEvent.java) — a piggyback channel that lets a
  server push application-specific events down to a remote client, riding along on the tracker's poll.

> **The tracker is not a daemon thread.** As long as it runs, the JVM will not terminate — and when it dies,
> the application dies with it (see [Lifecycle](#lifecycle)). That is deliberate: an application that has
> silently lost its change notifications is an application whose caches are silently going stale, and
> Tentackle would rather stop than be wrong quietly. See
> [Correctness First](../../../../src/main/docs/correctness-first.md).

---

## The Modification Table

Everything is built on one small table, mapped by
[`DbModification`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/DbModification.java) and
named `modification`:

| Column      | Meaning                                                        |
|-------------|----------------------------------------------------------------|
| `tablename` | the **tracked name** — usually a table name, but any string     |
| `id`        | a unique numeric id for that name; **id 0 is the master row**   |
| `serial`    | the modification counter for that name                          |

The row with `id = 0` is special: it is the **master serial**, the sum of all modifications of everything.
Every count updates two rows — the row of the tracked name, and the master row — in a single statement:

```sql
UPDATE modification SET serial = serial + ? WHERE tablename = ? OR id = 0
```

That single row is what makes polling cheap. A JVM that wants to know whether *anything at all* changed reads
exactly one value:

```sql
SELECT serial FROM modification WHERE id = 0
```

If the master serial has not moved, nothing has changed anywhere and the poll is over — one round trip, one
row, regardless of how many tables the application tracks. Only when the master serial *does* move does the
tracker read all the individual serials and work out who needs to be notified.

Names need not correspond to a table. Anything can be tracked — an application-specific name like
`"pricelist-recalculated"` works exactly the same way; it simply gets a row with an id but no table behind it.
[`DbModification.addToModificationTable(...)`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/DbModification.java)
distinguishes the two cases: for a real entity table it seeds the serial from the highest `tableserial` found
in that table, and for a pure name it starts at 0.

The table is created and seeded by
`DbModification.initializeModificationTable(...)`, which is also what happens implicitly when the tracker is
given a session and finds the table missing or unreadable, and explicitly when the application calls
[`invalidate()`](#invalidate).

---

## Counting: How a Write Becomes a Serial

The persistence layer calls the tracker whenever it modifies data.
[`AbstractDbObject`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/AbstractDbObject.java)
invokes `countModification()` on insert, update and delete, and additionally
`ModificationTracker.addDeletion(...)` for deletes. Applications almost never call these themselves.

```java
long countModification(Session session, String name);
void addDeletion(Session session, String name, long id, long tableSerial);
```

Both take an explicit `session` (or `null` for the thread-local one) because the count must be made in the
*same* session — and therefore the same transaction — as the modification it describes. Neither is ever
invoked for a remote session: a remote client's write happens on the server, and the server's tracker counts
it there.

### The ModificationTally

Per tracked name the tracker keeps a
[`ModificationTally`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/ModificationTally.java).
A tally does not write to the database on every count. It increments an in-memory `pendingCount` and
remembers, per session, the transaction number and the table serials consumed. The physical
`UPDATE modification SET serial = serial + pendingCount` happens later, in `performPendingCount()` — and only
when **no transaction touching this name is still running**:

```java
public synchronized void performPendingCount() {
  if (pendingCount > 0) {
    sessionsInTx.keySet().removeIf(session -> !session.isTxRunning());
    if (sessionsInTx.isEmpty()) {
      // No more transactions running: flush to the database.
      // This ensures that triggered listeners will see the committed data!
      ...
    }
  }
}
```

That guard is the whole point. A listener fires because a serial advanced; if the serial advanced while the
writing transaction was still open, the listener would go and read data that is not committed yet — and see
the old values, or block. Deferring the flush until every relevant transaction has ended guarantees that by
the time anyone is notified, **the data is there**.

It is also a batching win: a loop saving five hundred rows produces five hundred `pendingCount` increments and
exactly one `UPDATE` on the modification table, flushed by the next poll.

The price is that the serial in the modification table lags behind until the flush. `countModification()`
returns the serial reserved for *this* modification, `lastSerial + pendingCount`, determined atomically with
the increment. It is unique per name, since it becomes the `tableserial` of the modified row, and the
`TableSerialHistory` and the expiration backlog are keyed by tableserial: a duplicate would make the caches
miss an expiration. The counter itself may be higher already due to concurrent modifications. Callers that
need the current serial read it after the fact via [`getIdSerialForName(...)`](#reading-serials-directly).

### Rollback correction

If the pending counts were simply left alone, a rolled-back transaction would still advance the serial and
invalidate everybody's caches for a change that never happened.
[`DbUtilities`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/DbUtilities.java) therefore
calls `rollbackModification(session, txNumber)` on every rollback, and each tally compares the count made by
that transaction against its total `pendingCount`:

- **all pending counts came from the rolled-back transaction** → drop them entirely. No serial moves, no cache
  is invalidated, nothing ever happened.
- **other transactions contributed too** → the tally cannot know whose numbers are whose, so it keeps the
  count and instead records the consumed table serials as **gaps** in
  [`TableSerialHistory`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/TableSerialHistory.java).

The `TableSerialHistory` is the companion mechanism for entities that carry a `tableserial` column. It
remembers, in memory on the server connected to the database, which id/tableserial tuples correspond to
deletions, rolled-back writes, or repeated updates — so that when a cache later asks "what changed between
serial X and Y?", the gaps in the answer can be explained instead of causing a needless invalidation. See
[PDO Caching](../../../../tentackle-pdo/src/main/docs/cache.md) and
[Locking](../../../../tentackle-persistence/src/main/docs/locking.md).

---

## Operation Modes

`ModificationTracker.isLocalClientMode()` selects between two counting strategies. It is not a preference —
it is a consequence of the deployment.

| | **Local client mode** | **Optimized mode** (default) |
|---|---|---|
| Who | desktop app talking straight to the database, no middle tier | servers, and remote clients |
| Counting | `performPendingCount()` runs **immediately**, inside `countModification()` | pending, flushed by the tracker thread once no transaction is running |
| Session | must use the thread-local session — the count has to join the caller's transaction | the tracker's own session |
| Tally state | no `sessionsInTx` bookkeeping, no `TableSerialHistory` | full bookkeeping |

`PdoModificationTracker.setSession(...)` decides this automatically:

```java
// enable local client mode if this is a non-server application with a local jdbc url.
// in all other cases use optimized mode.
Application application = Application.getInstance();
setLocalClientMode(!session.isRemote() && application != null && !application.isServer());
```

Local client mode exists because there is no middle tier to defer the flush to: the client *is* the writer and
the reader, and its own transaction must carry the count. It is an option for simple desktop applications with
very few parallel users, but as the interface documentation says plainly, it "isn't recommended" —
[`tiers.md`](../../../../src/main/docs/tiers.md) explains why the middle tier is worth having.

Note also that `countModification()` falls back to immediate counting when the tracker thread is not alive
(`!isAlive()`), because nothing would ever flush the pending count otherwise. The invoking threads then share the
tracker's session, which is not thread-safe: these flushes, the lazy configuration of entries and counters, and
`invalidate()` are serialized by a single lock, which `start()` acquires as well. Once the tracker thread runs,
it owns the session, and the pending counts are left to its next poll.

---

## The Tracker Thread

`DbModificationTracker` extends
[`DefaultSessionTaskDispatcher`](../java/org/tentackle/session/DefaultSessionTaskDispatcher.java), so it is
both a thread with a session and a task dispatcher (see
[Tasks and Daemons](../../../../tentackle-core/src/main/docs/tasks-daemon.md)). Its configuration is
deliberate:

```java
setDaemon(false);             // as long as this thread runs, the JVM will not terminate
setPriority(MAX_PRIORITY);    // runs periodically as reliable as possible
setSessionClosedOnTermination(true);
setSleepInterval(2000);       // 2s default; servers use 1000
```

### The poll loop

The dispatcher's main loop calls `lockInternal()` before running queued tasks, and `DbModificationTracker`
hooks in there — the polling happens on the way to acquiring the lock, at most once per half sleep interval:

1. **Flush** pending counts (local session), or, on a remote client's very first poll, tell the server this is
   the tracker's session (`configureRemoteSession()`, needed before the server will accept master serial
   events for it).
2. **Read the master serial** via `getModifiedMasterSerial()`. Unchanged → done, that is the whole poll.
3. If it moved: if the new serial is *lower* than the last one, the modification table was reinitialized
   underneath — force a full `invalidate()`.
4. **Read all id/serial tuples**, update the tallies' last serials, and for every listener whose tracked name
   advanced, collect a `ModificationEventDetail`.
5. **Fire** the listeners — immediately, or by parking a
   [delayed event](#delaying-and-coalescing).

Then `invokeDelayedListeners()` runs, and `unlockInternal(sleepMs)` shortens the sleep if a delayed event is
due sooner than the normal interval.

### Surviving trouble

The poll is wrapped in an `ExceptionHelper` chain handling two specific failures:

- **`ReconnectedException`** — the [reconnection machinery](../../../../tentackle-core/src/main/docs/io.md)
  restored the connection under the tracker's feet. Log, start over, resume. (Requires
  `Db.enableReconnection(true, >0)` on the tracker's session.)
- **`SessionClosedException`** — the server, or the database, closed the session. If
  `isReOpenOnceEnabled()` says so, reopen once and treat the poll as successful. The default implementation
  returns true only for non-grouped sessions, because a *grouped* session usually means the tracker's session
  is a lead session for something like a desktop client, and such an application must remain killable by
  closing that session — which is exactly how the [`LogoutEvent`](#a-worked-example-logoutevent) kill path
  works.

Anything else propagates and terminates the tracker — and with it, the application.

`cleanup()` also reports lateness: if the last poll is more than twice the sleep interval old, it logs a
warning, since that means long-running tasks, a blocked connection, or a session the server has closed.

### Using the tracker's thread and session

Because the tracker is a task dispatcher with a live session that ticks reliably, it doubles as the
application's general-purpose background worker. Two convenience statics on the interface cover the common
cases:

```java
// run a Runnable once on the tracker thread
ModificationTracker.submit(() -> precomputeSomething());

// run it inside a transaction of the tracker's session
ModificationTracker.transaction("cleanup", () -> purgeExpiredRows());
ModificationTracker.transaction(() -> purgeExpiredRows());   // tx named "<ModificationTracker>"
```

`DesktopApplication`, for instance, uses `ModificationTracker.submit(...)` to pre-compile validator scripts in
the background the first time a PDO class is used.

The tracker is also an
[`ExclusiveSessionProvider`](../java/org/tentackle/session/ExclusiveSessionProvider.java): an application can
borrow the tracker's session for a *short* period and give it back.

```java
Session session = ModificationTracker.getInstance().requestSession();
try {
  // exclusive use — the tracker is not polling meanwhile
}
finally {
  ModificationTracker.getInstance().releaseSession(session);
}
```

`requestSession()` locks the dispatcher (which is what triggers the poll described above); until
`releaseSession()`, no other thread gets it, and re-entrant requests must be released the matching number of
times. Keep it short: the tracker is not polling while you hold it.

---

## Listening for Changes

### The listener contract

[`ModificationListener`](../java/org/tentackle/session/ModificationListener.java):

| Method | Purpose |
|---|---|
| `String[] getNames()` | the tracked names to listen to; `null` or empty means **master listener** — fire on *any* change |
| `int getPriority()` | execution order within a poll; **lower comes first** |
| `long getTimeFrame()` | if > 0, fire at a *random* moment within this window instead (priority then has no effect) |
| `long getTimeDelay()` | delay before firing, to collect events for several names; combined with a time frame it acts as an offset |
| `void dataChanged(ModificationEvent ev)` | the callback |

[`ModificationListenerAdapter`](../java/org/tentackle/session/ModificationListenerAdapter.java) supplies the
boring parts (priority 100, no frame, no delay), and
[`PdoListener`](../../../../tentackle-pdo/src/main/java/org/tentackle/pdo/PdoListener.java) extends it to take
PDO classes rather than table names:

```java
ModificationTracker.getInstance().addModificationListener(new PdoListener(StoredBundle.class) {
  @Override
  public void dataChanged(ModificationEvent ev) {
    BundleFactory.getInstance().clearCache();
  }
});
```

Registration is per name: a listener naming three classes produces three internal entries, one per table entry
— and if the tracker has never heard of a name before, registering a listener is what configures it (creating
the modification row if necessary). `removeModificationListener(listener)` removes all entries of that
listener, comparing by identity.

### The Pdo shortcuts

For the common case, [`Pdo`](../../../../tentackle-pdo/src/main/java/org/tentackle/pdo/Pdo.java) offers
lambda-friendly wrappers so no anonymous class is needed:

```java
// with the event
PdoListener l1 = Pdo.listen(ev -> refresh(ev.getSerial()), Customer.class, Order.class);

// when the event doesn't matter
PdoListener l2 = Pdo.listen(this::reload, Customer.class);

// later
Pdo.unlisten(l1);
```

Both `listen(...)` overloads build a `PdoListener` over the classes' table names, register it with the tracker
singleton and hand it back — the returned listener is the handle you need for `unlisten(...)`. They are pure
convenience over `ModificationTracker.addModificationListener(...)`; a listener that needs a priority, a time
frame or a delay must still subclass `PdoListener` or `ModificationListenerAdapter` and override the getters.

`Pdo.terminateHelperThreads()` shuts the tracker down along with the other helper threads.

### The event

[`ModificationEvent`](../java/org/tentackle/session/ModificationEvent.java) comes in three flavors, decided by
the listener that receives it:

| Flavor | `isMasterEvent()` | `getName()` | `getDetails()` | When |
|---|---|---|---|---|
| **master** | `true` | `null` | empty | listener registered without names; carries only the master serial |
| **single** | `false` | the name | one detail | one tracked name advanced |
| **multi** | `false` | first name | one detail per name | several of the listener's names advanced in the same poll |

Each [`ModificationEventDetail`](../java/org/tentackle/session/ModificationEventDetail.java) is a
`name` + `serial` pair, and equality is by name alone — which is what lets details for the same name collapse
cleanly when a delayed event accumulates several polls' worth. `getSession()` on the event returns the
tracker's session.

> A master event has no details: `getDetails()` returns an empty collection and `getDetail(name)` returns
> `null`, so a listener can iterate the details of any event unconditionally. Check `isMasterEvent()` to
> tell a master event apart, as `PdoCache.expire(...)` does.

### Delaying and coalescing

`getTimeFrame()` and `getTimeDelay()` exist for one reason: **not every client should react at the same
instant**. If two hundred clients see the same serial move and all immediately hammer the server to reload,
the notification that was supposed to help has just created a thundering herd.

- **`getTimeDelay()`** postpones the firing by a fixed number of milliseconds. Modifications arriving in the
  meantime are merged into the same pending event — the details accumulate, the invocation time does not move.
  Useful to collect a burst of related changes into one callback.
- **`getTimeFrame()`** makes each listener fire at a *random* point inside the window, spreading the load
  across the fleet. A listener with a frame is registered with priority 0; the priority is meaningless once
  the time is random anyway.
- Given both, the delay is the offset and the frame the randomization on top of it.

Either way, **the same listener fires only once** even if the event recurs before it gets invoked — the
tracker keeps one `DelayedEvent` per listener, ordered by invocation time, and shortens its own sleep so it
wakes up when the earliest one is due.

---

## Remote Clients and Master Serial Events

A client with a remote session cannot read the modification table — there is no database to read. Instead its
tracker asks the server's tracker over TRIP, through
[`DbModificationTrackerRemoteDelegate`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/trip/DbModificationTrackerRemoteDelegate.java):

| Delegate method | Server side |
|---|---|
| `configureRemoteSession()` | marks this session as *the* tracker session (once) |
| `selectMasterSerial()` | returns the server tracker's in-memory master serial, wrapped |
| `selectIdSerialForName(name)` | one name's id/serial |
| `selectAllIdSerials()` | all id/serial tuples |
| `selectAllIdSerialNames()` | all id/serial/**name** tuples — fetched once at startup to save round trips |

The server answers from memory: its own tracker is already polling the database, so serving a client costs no
SQL. Cascade this and you have the [multi-tier](../../../../src/main/docs/tiers.md) picture — a nested server's
tracker polls its parent exactly the way a client polls it.

### Why a MasterSerial and not a long

Every poll of a remote client is a round trip that returns a single number. That is a free ride waiting to be
used — so instead of a bare `long`, the delegate returns a
[`MasterSerial`](../java/org/tentackle/session/MasterSerial.java):

```java
public interface MasterSerial {
  long serial();
}
```

When there is nothing to say, the server returns the record
[`DefaultMasterSerial(serial)`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/trip/DefaultMasterSerial.java).
When there *is* something to say, it returns a
[`MasterSerialEvent`](../java/org/tentackle/session/MasterSerialEvent.java) — the same serial, plus arbitrary
application payload — and the client's tracker handles it before returning the serial to its poll loop. The
notification costs zero extra round trips.

### Defining an event

An event is a `MasterSerialEvent` implementation: a mutable serial plus whatever payload it wants.

```java
@TripReferencable(TripReferencable.NEVER)
public class PriceListPublished implements MasterSerialEvent {

  private long serial;
  private String listName;      // payload

  @Override public void setSerial(long serial) { this.serial = serial; }
  @Override public long serial() { return serial; }

  public String getListName() { return listName; }
}
```

Two optional methods control queue behavior on the server:

- **`isOverridingOlderEvents()`** — return true if only the latest event of this kind matters (status
  information and the like). Default false: every event is delivered.
- **`isOverridingEvent(MasterSerialEvent olderEvent)`** — consulted only when the above is true; the default
  compares classes, so a newer event replaces older events of the same type.

### The handler and its mapping

The handler is a plain `Consumer` of the event, annotated with
[`@MasterSerialEventService`](../java/org/tentackle/session/MasterSerialEventService.java):

```java
@MasterSerialEventService(PriceListPublished.class)
public class PriceListPublishedHandler implements Consumer<PriceListPublished> {
  @Override
  public void accept(PriceListPublished event) {
    reloadPrices(event.getListName());
  }
}
```

`@MasterSerialEventService` is a
[`@MappedService`](../../../../tentackle-common/src/main/docs/services.md) — the mapping from event class to
handler class is resolved at build time and looked up through
[`MasterSerialEventHandlerFactory`](../java/org/tentackle/session/MasterSerialEventHandlerFactory.java)
(default:
[`DefaultMasterSerialEventHandlerFactory`](../java/org/tentackle/session/DefaultMasterSerialEventHandlerFactory.java),
backed by a `ClassMapper`). The annotation is validated at compile time by
[`MasterSerialEventServiceAnnotationProcessor`](../java/org/tentackle/session/apt/MasterSerialEventServiceAnnotationProcessor.java);
see [Annotation Processors](../../../../tentackle-core/src/main/docs/annotation-processors.md).

This indirection buys two things that a direct callback would not:

- **The server never needs to know the handlers.** It only enqueues events.
- **Different clients can react differently to the same event.** The handler is chosen by whatever is on the
  *client's* module path.

Handlers are singletons, instantiated via a no-args constructor, and **must be stateless**. There is exactly
one per event type. No handler registered means the event is silently ignored (logged at `fine`) — which may
be exactly what a given client wants.

### Sending an event

The server side enqueues on the client's session:

```java
remoteDbSessionImpl.addMasterSerialEvent(new PriceListPublished(...));
```

[`RemoteDbSessionImpl`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/trip/RemoteDbSessionImpl.java)
holds a `ConcurrentLinkedQueue` per session and rejects the call unless that session is the client's tracker
session — hence the `configureRemoteSession()` handshake on the first poll.

On the next poll,
[`DbModificationTrackerRemoteDelegateImpl.createMasterSerial(...)`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/trip/DbModificationTrackerRemoteDelegateImpl.java)
drains the queue:

- queue empty → `DefaultMasterSerial(serial)`
- exactly one event → that event, with the serial stamped into it
- several events → a
  [`MasterSerialListEvent`](../java/org/tentackle/session/MasterSerialListEvent.java), itself a
  `MasterSerialEvent` **and** an `ArrayList<MasterSerialEvent>`, each element stamped with the serial

On the client, `extractMasterSerial(...)` unwraps: if the master serial is a `Collection`, each element is
dispatched to its handler; otherwise the event itself is. Then the plain `serial()` continues through the poll
loop as if nothing had happened.

> **Append, don't insert.** The constructor, `MasterSerialListEvent.add(...)` and `addAll(...)` enforce
> `isOverridingOlderEvents()`; the positional mutators inherited from `ArrayList` (`add(int, …)`, `addFirst`,
> `set`, …) are not overridden and bypass the rule.

### A worked example: LogoutEvent

The framework ships one master serial event, and it illustrates the whole mechanism:

- [`LogoutEvent`](../../../../tentackle-pdo/src/main/java/org/tentackle/app/LogoutEvent.java) — no payload,
  `isOverridingOlderEvents()` returns true (asking twice to log out is still once).
- The server sends it from
  [`AdminExtensionAdapterRemoteDelegateImpl`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/trip/AdminExtensionAdapterRemoteDelegateImpl.java)
  when an administrator asks a client to log off.
- [`LogoutEventHandler`](../../../../tentackle-pdo/src/main/java/org/tentackle/app/LogoutEventHandler.java) in
  `tentackle-pdo` terminates the tracker — which, by default, terminates the application.
- [`LogoutEventHandler`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/app/LogoutEventHandler.java)
  in `tentackle-fx-rdc` **replaces** it for desktop clients, calling `Fx.terminate()` on the FX thread instead.

Same event, same server, two handlers — chosen by which module the client happens to have. That is the
`@MappedService` indirection paying for itself.

---

## Lifecycle

Set up by [`AbstractApplication`](../../../../tentackle-pdo/src/main/java/org/tentackle/app/AbstractApplication.java)
(see [Application Bootstrap](../../../../tentackle-pdo/src/main/docs/application.md)):

- **`configure()`** → `configureModificationTracker()` gives the tracker its session but does **not** start the
  thread. `setSession(...)` clears the counters, checks the modification table (initializing it if the check
  throws), and flags a remote session for the `configureRemoteSession()` handshake.
- **`finishStartup()`** starts the thread — unless the `notracker` property is set, in which case
  [PDO caching is globally disabled](../../../../tentackle-pdo/src/main/docs/cache.md), because a cache without
  invalidation goes stale.

Subclasses adapt it to their tier:

- [`AbstractServerApplication`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/app/AbstractServerApplication.java)
  tightens the poll to 1000 ms — the server's tracker is what every client's tracker ultimately waits on.
- [`DesktopApplication`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/app/DesktopApplication.java)
  gives the tracker its **own cloned session**, grouped with the application's lead session, so polling never
  competes with the UI's session — and so closing the lead session kills the client.

### Termination is the application's termination

`finishStartup()` registers a shutdown runnable that fires whenever the tracker thread ends:

```java
ModificationTracker.getInstance().addShutdownRunnable(() -> {
  if (ModificationTracker.getInstance().isTerminationRequested()) {
    LOGGER.info("termination requested");
    AbstractApplication.this.stop(0, null);
  }
  else {
    LOGGER.severe("*** emergency shutdown ***");
    AbstractApplication.this.stop(2, null);   // exit code 2
  }
});
```

An orderly `terminate()` exits 0. A tracker that died for any other reason exits 2 — loudly. Register your own
cleanup with `addShutdownRunnable(...)` / `removeShutdownRunnable(...)`; they run once each, in
`cleanup()`, after the pending counts have been flushed and the session closed, and an exception in one is
logged rather than allowed to stop the rest.

### invalidate()

`invalidate()` forces the tracking information to be rebuilt: counters cleared, master serial reset to 1, the
modification table reinitialized (local sessions only), all table entries refreshed. The tracker calls it on
itself when it notices the master serial went *backwards* — someone reinitialized the table underneath. It
runs as a task on the tracker thread, or directly if called from that thread or before it started.

---

## Reading Serials Directly

Not everything needs a callback. The current state is readable at any time:

```java
IdSerialTuple t1 = ModificationTracker.getInstance().getIdSerialForName("customer");     // by tracked name
IdSerialTuple t2 = ModificationTracker.getInstance().getIdSerialForClass(Customer.class); // by class

long serial = t1.getSerial();   // the table serial
long rowId  = t1.getId();       // the id of the entry in the modification table
```

Both return an [`IdSerialTuple`](../../../../tentackle-core/src/main/java/org/tentackle/misc/IdSerialTuple.java),
not a bare `long`, because the **row id** is part of the tracker's state too: it is what identifies the entry
in [modification events](#the-event), and it is what a client must be told when its caches are
[preloaded from an upstream server](../../../../tentackle-pdo/src/main/docs/cache.md#preloading-caches-at-startup-preloadcache) —
serial without id would leave the client unable to match incoming events to the tables it just received.

`getIdSerialForClass(Class)` resolves the name for you: `DbModificationTracker` uses the `@TableName` annotation
(falling back to instantiating the object to ask it), and `PdoModificationTracker` overrides this to resolve PDO
interfaces through `PdoUtilities`. Asking for a name the tracker has not seen yet configures it on the spot —
including creating its row — so neither method ever returns a meaningless zero for an unknown name.

This is how [`PdoCache`](../../../../tentackle-pdo/src/main/java/org/tentackle/pdo/PdoCache.java) establishes
its baseline `minTableSerial` when it starts up, before any event has ever fired.

### Seeding serials without a roundtrip

`DbModificationTracker.setIdSerialForName(name, idSerial)` is the write-side counterpart, and exists **solely
for [cache preloading](../../../../tentackle-pdo/src/main/docs/cache.md#preloading-caches-at-startup-preloadcache)**.
When a client receives a preloaded cache, the content arrives together with the id/serial pairs the *server's*
tracker held at snapshot time. Applying them locally lets the client adopt that baseline without asking the
server table by table — which would cost a roundtrip each, and could pair the received rows with a serial that
has already moved on.

Note that the serial is applied **unconditionally**: if an entry for the name already exists, its serial is
overwritten rather than kept. That is correct here — the incoming value belongs to the data being installed —
but it is why nothing else should call this method.

---

## Who Uses It

| Consumer | What it does |
|---|---|
| [`PdoCache`](../../../../tentackle-pdo/src/main/docs/cache.md) | expires cached PDOs via `expire(ModificationEvent)`; consults `TableSerialHistory` for gaps; checks `isLocalClientMode()` |
| [`PdoCacheOperation`](../../../../tentackle-pdo/src/main/java/org/tentackle/pdo/PdoCacheOperation.java) | [preloads the caches](../../../../tentackle-pdo/src/main/docs/cache.md#preloading-caches-at-startup-preloadcache) at startup and seeds the tracker with the upstream baseline via `setIdSerialForName` |
| [`DefaultSecurityManager`](../../../../tentackle-pdo/src/main/java/org/tentackle/security/DefaultSecurityManager.java) | a `PdoListener(Security.class)` invalidates the permission rules when they change |
| [`StoredBundleControl`](../../../../tentackle-i18n/src/main/java/org/tentackle/i18n/StoredBundleControl.java) | a `PdoListener(StoredBundle.class)` clears the bundle cache, so a translator's edit reaches every JVM |
| [`DesktopApplication`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/app/DesktopApplication.java) | uses the tracker as a background worker via `ModificationTracker.submit(...)` |
| RDC tables and forms | refresh their contents when the underlying entities change |

---

## Extending the Tracker

The implementation is a `@Service(ModificationTracker.class)` singleton, so the whole thing is replaceable —
`PdoModificationTracker` is itself nothing but a subclass registering for the same service. The interesting
hooks are `protected`:

| Method | Why override |
|---|---|
| `getModifiedMasterSerial()` | listen for changes beyond the modification table — return non-null to trigger a poll cycle |
| `selectAllIdSerials()` | track additional modification tables, e.g. those of another application |
| `createModificationEvent(details)` | supply an application-specific event subclass |
| `extractMasterSerial(...)` / `handleMasterSerialEvent(...)` | change how server-pushed events are unwrapped or dispatched |
| `isReOpenOnceEnabled()` | change the policy for reopening a session the server closed |

For application-specific server→client notifications, though, you almost never need a subclass: implement a
`MasterSerialEvent` plus an `@MasterSerialEventService` handler and the existing tracker carries it for you.

---

## Related Documentation

- [Tentackle Session](session.md) — the session, the `SessionInfo`, and the tracker's place among them.
- [PDO Caching](../../../../tentackle-pdo/src/main/docs/cache.md) — the tracker's biggest consumer; table serials, expiration, operation modes and `@PreloadCache`.
- [Locking](../../../../tentackle-persistence/src/main/docs/locking.md) — the `tableserial` and `serial` columns the tracker counts.
- [Tentackle Database](../../../../tentackle-database/src/main/docs/database.md) — where `DbModificationTracker`, `ModificationTally` and `DbModification` live.
- [Multi-Tier Cascade](../../../../src/main/docs/tiers.md) — why a tracker polls a tracker polls a database.
- [TRIP](../../../../tentackle-core/src/main/docs/trip.md) — the protocol carrying master serials and their events.
- [I/O and Networking](../../../../tentackle-core/src/main/docs/io.md) — the reconnection machinery behind `ReconnectedException`.
- [Service and Configuration API](../../../../tentackle-common/src/main/docs/services.md) — `@Service` and `@MappedService`, behind the tracker singleton and the event handlers.
- [Tasks and Daemons](../../../../tentackle-core/src/main/docs/tasks-daemon.md) — the dispatcher the tracker is built on.
- [Application Bootstrap](../../../../tentackle-pdo/src/main/docs/application.md) — where the tracker is configured and started.
- [Correctness First](../../../../src/main/docs/correctness-first.md) — why the tracker takes the application down with it.
