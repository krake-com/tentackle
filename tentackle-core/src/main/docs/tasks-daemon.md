# Tasks and Daemons — Background Execution

## Overview and Motivation

Long-running applications need to do work *beside* the request that triggered it: run a job later,
repeat a job on an interval, serialize jobs that must not overlap, and run supervised background
threads that can be terminated cleanly on shutdown. `tentackle-core` exports two small, related
packages for this:

- `org.tentackle.task` — a **task dispatcher** that runs `Task`s one at a time, with optional
  scheduling, repetition and mutual exclusion.
- `org.tentackle.daemon` — a **daemon supervisor** that owns the life cycle of background threads
  and shuts them down gracefully.

The two connect naturally: the default dispatcher is itself a supervised daemon thread.

## Tasks (`org.tentackle.task`)

### `Task` and `AbstractTask`

A [`Task`](../java/org/tentackle/task/Task.java) is a `Runnable` that is also `Serializable` and
`Comparable<Task>` (so the dispatcher can order it). Most tasks extend
[`AbstractTask`](../java/org/tentackle/task/AbstractTask.java) and implement `run()`. Two properties
control *when* and *how often* a task runs:

```java
task.setScheduledEpochalTime(when);   // absolute time (epoch millis) at which to run; 0 = as soon as possible
task.setRepeatInterval(millis);       // > 0 to re-queue the task this many ms after each run
```

### `TaskDispatcher` and `DefaultTaskDispatcher`

A [`TaskDispatcher`](../java/org/tentackle/task/TaskDispatcher.java) executes its tasks **serially**.
[`DefaultTaskDispatcher`](../java/org/tentackle/task/DefaultTaskDispatcher.java) is a `Thread` that
implements it (and `Supervisable`, see below):

```java
TaskDispatcher dispatcher = new DefaultTaskDispatcher("background");
dispatcher.start();

dispatcher.addTask(task);                 // queue a task
boolean done = dispatcher.addTaskAndWait(task);   // queue and block until it finished
dispatcher.waitForTask(task);             // block until a queued task finished
dispatcher.removeTask(task);              // de-queue if still pending
```

Useful operations on the dispatcher:

- **Queue inspection** — `getQueueSize()`, `isQueueEmpty()`, `getTask(id)`, `getAllTasks()`,
  `isTaskPending(task)`, `isInstanceOfTaskPending(MyTask.class)`.
- **Mutual exclusion** — `lock(key)` / `unlock(key)` hand out a
  [`TaskDispatcherLock`](../java/org/tentackle/task/TaskDispatcherLock.java); enable it with
  `setUsingMutexLocking(true)` so tasks sharing a key never run concurrently.
- **Timing** — `setSleepInterval`, `setWaitInterval`, `setDeadInterval` and
  `setShutdownIdleTimeout` tune the polling loop and the idle auto-shutdown.
- **Diagnostics** — `toDiagnosticString()`, `isAlive()`.

### `TaskListener`

Register a [`TaskListener`](../java/org/tentackle/task/TaskListener.java) on a dispatcher or on an
individual task to observe task life-cycle events.
[`TaskException`](../java/org/tentackle/task/TaskException.java) reports task failures.

## Daemons (`org.tentackle.daemon`)

Daemons are ordinary background threads whose life cycle is owned by a
[`DaemonSupervisor`](../java/org/tentackle/daemon/DaemonSupervisor.java). The supervisor periodically
checks its daemons and can terminate or kill them — for example on application shutdown.

The package is a small set of capability interfaces:

| Interface | Meaning |
|-----------|---------|
| [`Terminatable`](../java/org/tentackle/daemon/Terminatable.java) | something (typically a `Thread`) that can be asked to terminate **gracefully** |
| [`Killable`](../java/org/tentackle/daemon/Killable.java) | a thread that may be forcibly killed |
| [`Supervisable`](../java/org/tentackle/daemon/Supervisable.java) | a `Killable` thread that opts into supervision, allowing the supervisor extra checks and control |
| [`Scavenger`](../java/org/tentackle/daemon/Scavenger.java) | a cleanup service (e.g. a cleanup thread) that may bypass certain checks to avoid spurious exceptions |

Because `DefaultTaskDispatcher` implements `Supervisable` (and `TaskDispatcher` extends
`Terminatable`), a task dispatcher can be placed under a `DaemonSupervisor` and torn down cleanly
together with the rest of the application's background threads.

## Which Dispatcher Do I Want?

`org.tentackle.task` deliberately knows nothing about database sessions or the UI. Two higher
layers build on it, and picking the right entry point is mostly a question of **who provides the
session**:

|                  | [`DefaultTaskDispatcher`](../java/org/tentackle/task/DefaultTaskDispatcher.java) | [`DefaultSessionTaskDispatcher`](../../../../tentackle-session/src/main/java/org/tentackle/session/DefaultSessionTaskDispatcher.java) | [`Rdc.bg(...)`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/Rdc.java) |
|------------------|----------------------------------------------------------------|----------------------------------------------------------------|----------------------------------------------------------------|
| Module           | `tentackle-core`                                               | `tentackle-session`                                            | `tentackle-fx-rdc`                                             |
| Database session | none                                                           | one **exclusive** session owned by the dispatcher              | provided behind the scenes (tracker session or session pool)   |
| Task type        | `AbstractTask`                                                 | `AbstractSessionTask`                                          | any lambda — wrapped into a session task for you               |
| Thread           | you create and `start()` it                                    | you create and `start()` it                                    | reuses the modification tracker (or a pool)                    |
| Typical use      | timers, repeated jobs, serializing work without persistence    | server-side database jobs: housekeeping, imports, schedules    | moving a persistence call off the JavaFX thread                |

### Plain dispatcher — work without a session

The `DefaultTaskDispatcher` described above is the right choice whenever the background work does
not touch the database at all, or manages its own resources: recurring timers, protocol
housekeeping, serialized access to some non-database resource.

### `SessionTaskDispatcher` — the dispatcher owns a session

For background work *on the database*, `tentackle-session` provides
[`SessionTaskDispatcher`](../../../../tentackle-session/src/main/java/org/tentackle/session/SessionTaskDispatcher.java),
implemented by `DefaultSessionTaskDispatcher` on top of `DefaultTaskDispatcher`. It adds exactly
one thing: a [`Session`](../../../../tentackle-session/src/main/docs/session.md) that belongs to
the dispatcher thread — **exclusively**. The session must not be pooled and must not be used by any
other thread; since all tasks run serially on that one thread, they can share it safely.

Tasks extend
[`AbstractSessionTask`](../../../../tentackle-session/src/main/java/org/tentackle/session/AbstractSessionTask.java):
`addTask(...)` injects the dispatcher's session, and the task's `run()` works through
`getSession()`. Two knobs matter in long-running setups: `setSessionKeptAlive(true)` pings the
session while the dispatcher is idle, so a remote server does not consider it dead, and
`setSessionClosedOnTermination(true)` closes the session when the dispatcher terminates.

A complete example — an hourly cleanup job on its own session:

```java
public class CleanupTask extends AbstractSessionTask {

  @Override
  public void run() {
    DomainContext context = Pdo.createDomainContext(getSession());
    for (Invoice invoice : Pdo.create(Invoice.class, context).selectExpired()) {
      invoice.delete();
    }
  }
}

SessionInfo info = SessionInfoFactory.getInstance().create("jobrunner", password, "myapp");
DefaultSessionTaskDispatcher dispatcher = new DefaultSessionTaskDispatcher(
        "jobs", SessionFactory.getInstance().create(info), false, 30000, 0);
dispatcher.setSessionKeptAlive(true);
dispatcher.setSessionClosedOnTermination(true);
dispatcher.start();

CleanupTask task = new CleanupTask();
task.setRepeatInterval(3_600_000);    // re-queue one hour after each run
dispatcher.addTask(task);
```

The best-known `SessionTaskDispatcher` is one you never create yourself: the
[`ModificationTracker`](../../../../tentackle-session/src/main/docs/modification-tracking.md).
Every client (and every caching server) already runs one — a live thread with an open session that
accepts tasks like any other dispatcher. That is precisely what the desktop client exploits:

### `Rdc.bg(...)` — the JavaFX client facade

In a rich desktop client, the question is rarely "which dispatcher" but "how do I get this
database call off the FX thread". The answer is
[`Rdc.bg(...)`](../../../../tentackle-fx-rdc/src/main/docs/background-execution.md): it wraps a
lambda into an `AbstractSessionTask`, queues it on the modification tracker (or, opt-in, on a
session-pool-backed thread pool), and posts the result back to the FX thread. You never create a
thread, a dispatcher, or a session. The details — execution models, serialization consequences,
error handling — are in [Background Execution](../../../../tentackle-fx-rdc/src/main/docs/background-execution.md).

**Rule of thumb:** no database → plain `DefaultTaskDispatcher`; database jobs in a server or
standalone process → `DefaultSessionTaskDispatcher` with `AbstractSessionTask`s; JavaFX client →
`Rdc.bg(...)` and stop thinking about dispatchers.

## See also

- [Tentackle Core](core.md) — the runtime foundation that hosts both packages.
- [Tentackle Session](../../../../tentackle-session/src/main/docs/session.md) — background work that
  touches the database runs within a session context.
- [Modification Tracking](../../../../tentackle-session/src/main/docs/modification-tracking.md) — the
  ever-present `SessionTaskDispatcher` in clients and caching servers.
- [Background Execution](../../../../tentackle-fx-rdc/src/main/docs/background-execution.md) —
  `Rdc.bg(...)`, the desktop client's front end to all of this.
