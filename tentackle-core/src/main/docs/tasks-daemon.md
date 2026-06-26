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

## See also

- [Tentackle Core](core.md) — the runtime foundation that hosts both packages.
- [Tentackle Session](../../../../tentackle-session/src/main/docs/session.md) — background work that
  touches the database runs within a session context.
