/*
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.task;

import org.tentackle.common.InterruptedRuntimeException;
import org.tentackle.common.Timestamp;
import org.tentackle.daemon.Supervisable;
import org.tentackle.log.Logger;
import org.tentackle.misc.NamedCounter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A thread executing tasks in a serialized manner.
 * <p>
 * Primarily, the task dispatcher executes tasks from a queue.
 * Additionally, dispatchers can be locked by the application, which will
 * prevent tasks from being executed until the dispatcher is unlocked again.
 * This is particularly useful whenever resources must be shared between the dispatcher and the application.<br>
 * The TaskDispatcher's lock/unlock synchronization may be either mutex- or counter-based.
 * Mutex-based is the preferred method if the lock and its corresponding unlock-operation are invoked
 * from the same thread. Any attempt to unlock from a different thread will result
 * in an {@link IllegalMonitorStateException}.<br>
 * If different threads may lock and unlock, counter-based synchronization should be used instead.
 * For example, TRIP services requests via threads from a threadpool and consecutive TRIP-requests
 * may be serviced by different threads.<br>
 * With counter-based synchronization, it is strongly recommended to provide
 * an application-specific locking key for each lock/unlock operation. This ensures
 * that only threads with the correct key (usually a shared resource) may unlock the dispatcher.
 *
 * @author harald
 */
public class DefaultTaskDispatcher extends Thread implements TaskDispatcher, Supervisable  {

  /**
   * Name-based daemon instance counter.
   */
  public static final NamedCounter INSTANCE_COUNTER = new NamedCounter();   // daemon instance counter

  private static final Logger LOGGER = Logger.get(DefaultTaskDispatcher.class);

  private static final AtomicLong TASK_ID = new AtomicLong();   // task ID counter

  private static final long DEFAULT_SLEEP_INTERVAL = 1000;      // 1s
  private static final long DEFAULT_DEAD_INTERVAL = 60000;      // 60s


  // the configuration is written by the setters from arbitrary threads and read by the dispatcher
  private volatile boolean usingMutexLocking;           // true if using a mutex lock, else just a counter
  private volatile ReentrantReadWriteLock mutex;        // mutex lock
  private volatile long sleepInterval;                  // keepalive in ms
  private volatile long deadInterval;                   // dead detection interval in ms, 0 if disabled
  private volatile long waitInterval;                   // timeout for waitForTask, 0 if disabled
  private volatile long shutdownIdleTimeout;            // shutdown thread after idle milliseconds, 0 for permanent

  private long lastActivity;                            // last activity, 0 if a task is currently executed (dispatcher thread only)
  private final ConcurrentSkipListSet<Task> queue;      // the job queue
  private final ConcurrentHashMap<Long,Task> idMap;     // tasks by id
  private final Object queueLock;                       // makes composite updates of queue + idMap atomic
  private final List<TaskListener> listeners;           // task listeners (notified without holding a lock)
  private final ReentrantReadWriteLock interruptMutex;  // mutex lock to prevent interrupts during execution of the task
  private final Object interruptLock;                   // makes testing and setting delayInterrupt atomic
  private int nonMutexLockCount;                        // non-mutex lock count
  private Object unlockKey;                             // optional key to unlock
  private volatile long lastMillis;                     // last milliseconds to check for dead dispatcher
  private volatile boolean terminationRequested;        // true if termination has been requested
  private volatile boolean killed;                      // true if daemon has been killed
  private volatile boolean delayInterrupt;              // true if delay the interrupt until in safe code
  private volatile boolean wasInterrupted;              // true if interrupted while executing a task
  private volatile long startTime;                      // time when dispatcher was started
  private volatile long terminationTime;                // time when dispatcher was terminated
  private volatile RuntimeException terminationCause;   // the termination cause



  /**
   * Creates a task dispatcher.<br>
   *
   * @param name the dispatcher's name
   * @param usingMutexLocking true use a mutex for locking, else just a counter
   * @param sleepInterval keep alive in milliseconds
   * @param deadInterval interval in milliseconds to detect a dead dispatcher, 0 to disable detection
   */
  public DefaultTaskDispatcher(String name, boolean usingMutexLocking, long sleepInterval, long deadInterval) {
    super(name + "(" + INSTANCE_COUNTER.next(name) + ")");

    setDaemon(true);
    setUsingMutexLocking(usingMutexLocking);
    setSleepInterval(sleepInterval);
    setDeadInterval(deadInterval);
    setWaitInterval(deadInterval / 2);

    queue = new ConcurrentSkipListSet<>();
    /*
     * fireStarted/fireCompleted iterate the listeners without a lock, and a listener may well
     * add or remove listeners from within the notification (a one-shot listener deregistering
     * itself is the obvious case). Iterating a plain list would skip the remaining listeners
     * or throw a ConcurrentModificationException, which aborts the task execution.
     * Note this is java.util.concurrent's list, not the copy-once org.tentackle.misc.CopyOnWriteList.
     */
    listeners = new CopyOnWriteArrayList<>();
    idMap = new ConcurrentHashMap<>();
    queueLock = new Object();
    interruptMutex = new ReentrantReadWriteLock();
    interruptLock = new Object();
  }

  /**
   * Creates a task dispatcher with counter-based locking, 1 s sleep, 30 s dead interval.
   *
   * @param name the dispatcher's name
   */
  public DefaultTaskDispatcher(String name) {
    this(name, false, DEFAULT_SLEEP_INTERVAL, DEFAULT_DEAD_INTERVAL);
  }

  /**
   * Checks whether the current thread is this task dispatcher itself.
   *
   * @return true if invoker is this task dispatcher
   */
  public boolean isTaskDispatcherThread() {
    return Thread.currentThread() == this;
  }

  @Override
  public void setShutdownIdleTimeout(long shutdownIdleTimeout) {
    this.shutdownIdleTimeout = shutdownIdleTimeout;
  }

  @Override
  public long getShutdownIdleTimeout() {
    return shutdownIdleTimeout;
  }

  @Override
  public long getSleepInterval() {
    return sleepInterval;
  }

  @Override
  public void setSleepInterval(long sleepInterval) {
    if (sleepInterval <= 0) {
      throw new IllegalArgumentException(this + ": sleep interval must be > 0");
    }
    this.sleepInterval = sleepInterval;
    assertIntervalsInRange();
  }

  @Override
  public long getDeadInterval() {
    return deadInterval;
  }

  @Override
  public void setDeadInterval(long deadInterval) {
    if (deadInterval < 0) {
      throw new IllegalArgumentException(this + ": dead interval must be >= 0");
    }
    this.deadInterval = deadInterval;
    assertIntervalsInRange();
  }

  @Override
  public long getWaitInterval() {
    return waitInterval;
  }

  @Override
  public void setWaitInterval(long waitInterval) {
    this.waitInterval = waitInterval;
  }

  @Override
  public synchronized boolean isUsingMutexLocking() {
    return usingMutexLocking;
  }

  @Override
  public synchronized void setUsingMutexLocking(boolean usingMutexLocking) {
    assertNotAlive();
    this.usingMutexLocking = usingMutexLocking;
    if (usingMutexLocking) {
      mutex = new ReentrantReadWriteLock();
    }
    else  {
      mutex = null;
      nonMutexLockCount = 0;
    }
  }

  @Override
  public synchronized String toDiagnosticString() {
    StringBuilder buf = new StringBuilder(toString());
    buf.append("\n    class=").append(getClass())
       .append(", useMutexLocking=").append(isUsingMutexLocking())
       .append(", sleepInterval=").append(getSleepInterval())
       .append(", deadInterval=").append(getDeadInterval())
       .append(", waitInterval=").append(getWaitInterval())
       .append("\n    lastMillis=").append(new Timestamp(lastMillis))
       .append(", stopRequested=").append(terminationRequested)
       .append(", dead=").append(isDead())
       .append(", killed=").append(isKilled());
    if (unlockKey != null) {
      buf.append(", unlockKey='").append(unlockKey).append("'");
    }
    return buf.toString();
  }

  @Override
  public boolean isDead() {
    long localLastMillis = lastMillis;
    return deadInterval > 0 && localLastMillis > 0 && System.currentTimeMillis() > localLastMillis + deadInterval;
  }

  @Override
  public void setDead(boolean dead) {
    assertNotKilled();
    if (dead) {
      if (deadInterval <= 0) {
        throw new TaskException("cannot mark dispatcher dead with deadInterval <= 0");
      }
      lastMillis = 1;
    }
    else  {
      // force alive
      lastMillis = System.currentTimeMillis();
    }
  }

  @Override
  public long startedAt() {
    return startTime;
  }

  @Override
  public long terminatedAt() {
    return terminationTime;
  }

  @Override
  public RuntimeException getTerminationCause() {
    return terminationCause;
  }

  @Override
  public void kill() {
    assertNotKilled();
    terminate();
    killed = true;
  }

  @Override
  public boolean isKilled() {
    return killed;
  }

  @Override
  public void addTaskListener(TaskListener listener) {
    assertNotKilled();
    listeners.add(listener);
  }

  @Override
  public void removeTaskListener(TaskListener listener) {
    assertNotKilled();
    listeners.remove(listener);
  }

  @Override
  public boolean addTask(Task task) {
    assertNotKilled();
    synchronized(task) {
      if (task.getDispatcher() != null && task.getDispatcher() != this) {
        throw new TaskException("task " + task + " already belongs to dispatcher " +
                                task.getDispatcher() + ", cannot be added to " + this);
      }
      synchronized (queueLock) {
        if (task.getId() != 0 && queue.contains(task)) {
          LOGGER.fine("{0}: task {1} already in queue -- not added", this, task);
          return false;
        }
        task.setDispatcher(this);
        if (task.getId() == 0) {
          // task never added before: set a unique id
          task.setId(TASK_ID.incrementAndGet());
        }
        queue.add(task);
        idMap.put(task.getId(), task);
      }
      LOGGER.fine("{0}: task {1} added", this, task);
      safeInterrupt();   // wake up!
      return true;
    }
  }

  @Override
  public boolean isTaskPending(Task task) {
    assertNotKilled();
    synchronized (queueLock) {
      // never observe a repeating task while it is being rescheduled (see executeTask)
      return queue.contains(task);
    }
  }

  @Override
  public Collection<Task> getAllTasks() {
    return new ArrayList<>(idMap.values());
  }

  @Override
  public Task getTask(long taskId) {
    assertNotKilled();
    return idMap.get(taskId);
  }

  @Override
  public boolean isInstanceOfTaskPending(Class<? extends Task> clazz) {
    for (Task task: idMap.values()) {
      if (clazz.isAssignableFrom(task.getClass())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void waitForTask(Task task) {
    long startedToWait = System.currentTimeMillis();
    long oldMillis = lastMillis;
    do {
      try {
        synchronized(task) {
          if (isTaskPending(task)) {
            task.wait(sleepInterval);
            if (task.getCompleted() != 0) {
              return;
            }
            long newMillis = lastMillis;
            if (newMillis != oldMillis) {
              // there is some activity and therefore obviously no deadlock -> reset wait interval
              startedToWait = System.currentTimeMillis();
              oldMillis = newMillis;
            }
          }
          else  {
            return;
          }
        }
      }
      catch (InterruptedException ex) {
        throw new InterruptedRuntimeException(ex);
      }
    } while (waitInterval <= 0 || System.currentTimeMillis() - startedToWait < waitInterval);

    removeTask(task);

    // Could be a deadlock: the waiting thread blocks a task being executed somehow.
    // we don't want a dead dispatcher...
    throw new TaskException("pending task timed out");
  }

  @Override
  public boolean addTaskAndWait(Task task) {
    if (addTask(task)) {
      waitForTask(task);
      return true;
    }
    return false;
  }

  @Override
  public boolean removeTask(Task task) {
    assertNotKilled();
    boolean removed;
    synchronized (queueLock) {
      removed = queue.remove(task);
      if (removed) {
        idMap.remove(task.getId());
        task.setDispatcher(null);   // this triggers the warning when finished
      }
    }
    if (removed && task.getStarted() != 0 && task.getCompleted() == 0) {
      LOGGER.warning("{0}: removed task {1} is still running -> dispatcher remains blocked until task has finished!", this, task);
    }
    return removed;
  }

  @Override
  public int getQueueSize() {
    assertNotKilled();
    return queue.size();
  }

  @Override
  public boolean isQueueEmpty() {
    assertNotKilled();
    return queue.isEmpty();
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to delay interrupt while running tasks.
   */
  @Override
  public void interrupt() {
    /*
     * The interruptLock makes testing 'delayInterrupt' and acting on it atomic with respect
     * to executeTask, which flips the flag under the same lock. Without it this is a
     * check-then-act race: this thread could read 'delayInterrupt' as false, be descheduled
     * while the dispatcher enters a task declared non-interruptable, and interrupt it anyway.
     */
    synchronized (interruptLock) {
      if (delayInterrupt) {
        wasInterrupted = true;    // re-asserted once the task has finished, see executeTask
      }
      else  {
        super.interrupt();
      }
    }
  }

  @Override
  public void requestTermination() {
    assertNotKilled();
    terminationRequested = true;
    interrupt();
  }

  @Override
  public boolean isTerminationRequested() {
    return terminationRequested;
  }

  @Override
  public void terminate() {

    requestTermination();

    if (Thread.currentThread() != this) {
      for (;;) {
        try {
          join(deadInterval > 0 ? deadInterval : (sleepInterval * 10));
          break;
        }
        catch (InterruptedException e)  {
          LOGGER.warning("termination interrupted -> ignored");
        }
      }

      if (isAlive()) {
        LOGGER.severe(this + " does not terminate -> marking dispatcher killed!");
        killed = true;
      }

      cleanup();
    }
  }

  @Override
  public TaskDispatcherLock lock(Object key) {

    assertNotKilled();

    LOGGER.finer("{0}: lock requested for {1}", this, key);

    checkNullKey(key);

    Thread currentThread = Thread.currentThread();
    if (currentThread == this) {
      throw new TaskException(this + ": is not allowed to lock itself");
    }

    return lockImpl(key);
  }

  @Override
  public boolean unlock(Object key) {

    assertNotKilled();

    LOGGER.finer("{0}: unlock requested for {1}", this, key);

    checkNullKey(key);

    return unlockImpl(key);
  }


  /**
   * Asserts that this dispatcher hasn't been killed yet.
   */
  protected void assertNotKilled() {
    if (isKilled()) {
      throw new TaskException("dispatcher " + this + " has already been killed");
    }
  }


  /**
   * Invoke {@link TaskListener#started(org.tentackle.task.Task)}
   * for all listeners.
   * @param task the task
   */
  protected void fireStarted(Task task) {
    for (TaskListener listener: listeners) {
      try {
        listener.started(task);
      }
      catch (RuntimeException ex) {
        LOGGER.warning("invoking listener failed", ex);
      }
    }
  }


  /**
   * Invoke {@link TaskListener#completed(org.tentackle.task.Task)}
   * for all listeners.
   * @param task the task
   */
  protected void fireCompleted(Task task) {
    for (TaskListener listener: listeners) {
      try {
        listener.completed(task);
      }
      catch (RuntimeException ex) {
        LOGGER.warning("invoking listener failed", ex);
      }
    }
  }


  /**
   * Requests a lock for executing a task from the queue.
   */
  protected void lockInternal() {
    lockImpl(null);
  }


  /**
   * Releases a lock after having executed a task from the queue (or none).
   *
   * @param sleepMs milliseconds to sleep after unlocking, 0 if no sleep, limited to {@link #getSleepInterval()}
   */
  protected void unlockInternal(long sleepMs) {
    unlockImpl(null);
    if (sleepMs > 0 && !wasInterrupted) {
      if (sleepMs > sleepInterval) {
        sleepMs = sleepInterval;
      }
      sleepForInterval(sleepMs);
    }
    wasInterrupted = false;
  }




  @Override
  public void run() {

    startTime = System.currentTimeMillis();
    lastActivity = startTime;

    LOGGER.info("{0} started", this);

    if (sleepInterval <= 0) {
      throw new TaskException("sleep interval not configured");
    }
    assertIntervalsInRange();

    try {
      while (!terminationRequested && !isKilled()) {

        lockInternal();

        lastMillis = System.currentTimeMillis();

        Task task = nextTask();

        if (terminationRequested || isKilled()) {
          break;
        }

        if (task == null) {
          LOGGER.finer("{0}: queue is empty", this);
          if (lastActivity != 0 && shutdownIdleTimeout > 0 && lastMillis - lastActivity > shutdownIdleTimeout) {
            LOGGER.info("shutting down {0} due to idle timeout", this);
            break;
          }
          unlockInternal(sleepInterval);   // unlock and wait (until interrupted due to the added task or time elapsed)
        }
        else if (task.getScheduledEpochalTime() > lastMillis) {
          long sleepMs = task.getScheduledEpochalTime() - lastMillis;
          LOGGER.finer("{0}: next task {1} will be executed in {2}ms", this, task, sleepMs);
          lastActivity = lastMillis;
          unlockInternal(sleepMs);
        }
        else {
          try {
            executeTask(task);
          }
          catch (RuntimeException ex) {
            // some other exception (not task.run)
            LOGGER.severe("executing task failed", ex);
          }
        }
      }
    }
    catch (RuntimeException rex) {
      if (!terminationRequested) {
        LOGGER.severe("dispatcher failed", rex);
        terminationCause = rex;
      }
    }

    try {
      cleanup();
    }
    catch (RuntimeException ex) {
      LOGGER.warning("cleanup failed", ex);
    }

    terminationTime = System.currentTimeMillis();
    LOGGER.info("{0} {1}", this, (isKilled() ? "killed" : "terminated"));
  }


  /**
   * Determines the next task to execute.<br>
   * If the only tasks left are scheduled for the future, the task with the minimum scheduled
   * time will be returned.
   *
   * @return the task, null if no more tasks
   */
  protected Task nextTask() {
    Task executableTask = null;
    long minScheduled = Long.MAX_VALUE;
    // the queue is sorted fifo, not the optional scheduled time
    for (Task task : queue) {
      long scheduled = task.getScheduledEpochalTime();    // 0 for most tasks, otherwise some calculated or fixed value
      if (scheduled <= lastMillis) {
        executableTask = task;
        break;    // ready to execute
      }
      // not ready for execution yet: remember the earliest task
      if (scheduled < minScheduled) {
        minScheduled = scheduled;
        executableTask = task;
      }
    }
    return executableTask;
  }

  /**
   * Executes a task.
   *
   * @param task the task
   */
  protected void executeTask(Task task) {

    lastMillis = System.currentTimeMillis();
    lastActivity = 0;   // task is pending -> don't time out
    task.setStarted(lastMillis);
    LOGGER.fine("{0}: task {1} started", this, task);

    fireStarted(task);

    synchronized (task) {
      try {
        interruptMutex.writeLock().lock();
        try {
          synchronized (interruptLock) {
            if (!task.isInterruptable()) {
              delayInterrupt = true;
              if (Thread.interrupted()) {
                // an interrupt landed just before the flag was raised: consume it, the task
                // must not start out with a pending interrupt it declared it cannot handle
                wasInterrupted = true;
              }
            }
          }
          task.run();
        }
        catch (Throwable t) {
          task.setCause(t);
          LOGGER.severe("task '" + task + "', ID=" + task.getId() + " terminated abnormally", t);
        }
        finally {
          synchronized (interruptLock) {
            delayInterrupt = false;
            if (wasInterrupted) {
              // deliver what was withheld while the task was running
              wasInterrupted = false;
              super.interrupt();
            }
          }
          interruptMutex.writeLock().unlock();
        }

        if (task.getDispatcher() == null) {
          LOGGER.warning("{0}: already removed task {1} completed", this, task);
        }

        lastMillis = System.currentTimeMillis();

        // Finish all dispatcher-side bookkeeping *before* signaling completion.
        // setCompleted() sets the (volatile) completion timestamp and fires the
        // task's completion listeners, and the notifyAll() below wakes waitForTask().
        // All of these observers must see a consistent dispatcher state: the task
        // already dequeued and - unless it repeats - removed from the id map.
        // Otherwise, a completion observer could still find the just-completed task
        // via getTask() or isTaskPending() (see DefaultTaskDispatcherTest).
        unlockInternal(0);

        // A repeating task must be dequeued and re-queued because the queue is sorted
        // by the scheduled time, which is about to change (see AbstractTask.compareTo).
        // The lock keeps that update atomic: without it a concurrent removeTask() could
        // hit the gap, fail to remove the task and thus fail to clear its dispatcher,
        // whereupon the task would be re-queued below and repeat forever.
        synchronized (queueLock) {
          queue.remove(task);

          long repeat = task.getRepeatInterval();
          if (repeat > 0 && task.getDispatcher() == this) {
            // if repeat and not removed from the dispatcher in the meantime
            task.setScheduledEpochalTime(lastMillis + repeat);
            queue.add(task);
          }
          else  {
            idMap.remove(task.getId());
          }
        }

        try {
          task.setCompleted(lastMillis);
        }
        catch (RuntimeException ex) {
          LOGGER.severe("cannot set task '" + task + "', ID=" + task.getId() + " completed", ex);
        }
        lastActivity = lastMillis;
      }
      finally {
        task.notifyAll();
      }
    }

    LOGGER.fine("{0}: task {1} completed", this, task);

    fireCompleted(task);
  }

  /**
   * Does any necessary cleanup after the dispatcher thread has been terminated.<br>
   */
  protected void cleanup() {
    terminationRequested = true;  // in case termination due to some exception
  }

  /**
   * Sleeps for the given sleep interval.
   *
   * @param sleepMs the sleep interval in ms
   */
  protected void sleepForInterval(long sleepMs) {
    try {
      sleep(sleepMs);
    }
    catch (InterruptedException ex) {
      // daemon thread is terminated via requestTermination!
      LOGGER.fine("interrupted!");
    }
  }

  /**
   * Creates a dispatcher lock.
   *
   * @param key the locking key
   * @return the lock
   */
  protected TaskDispatcherLock createLock(Object key) {
    return new DefaultTaskDispatcherLock(this, key);
  }

  /**
   * Asserts that the dispatcher is still alive.
   */
  protected void assertAlive() {
    if (lastMillis > 0 && !isAlive()) {
      throw new TaskException("dispatcher " + this + " has already been terminated");
    }
  }

  /**
   * Asserts that the dispatcher is not running.
   */
  protected void assertNotAlive() {
    if (isAlive()) {
      throw new TaskException("dispatcher " + this + " is already running");
    }
  }

  /**
   * Asserts that the intervals are in range, if set
   */
  protected void assertIntervalsInRange() {
    if (deadInterval > 0 && sleepInterval > 0 && deadInterval <= sleepInterval) {
      throw new IllegalArgumentException(this + ": dead detection interval must be > sleep interval");
    }
  }


  /**
   * Waits for the lock.
   *
   * @param key the optional unlock-key
   * @return the lock if really locked, else just incremented the lock count
   */
  private TaskDispatcherLock lockImpl(Object key) {

    int lockingLevel;

    for (;;) {    // repeat if the key doesn't match

      if (usingMutexLocking) {
        /*
         * We don't need a 'synchronized(this)' here because
         * if there is already a lock, only the same thread can lock it again.
         * If it is a different thread, the lock() below will block.
         */
        for (;;) {
          assertAlive();
          assertNotKilled();
          try {
            /*
             * We don't use lock() here because the operation should be interruptable
             * (which is currently the case for termination or when a task has been added).
             * Interrupts will force to check whether the dispatcher is still alive,
             * which is a must for supervised dispatchers. Otherwise, threads waiting
             * for a lock on a dispatcher that has been killed in the meantime will wait forever.
             */
            mutex.writeLock().lockInterruptibly();
            break;
          }
          catch (InterruptedException ie) {
            // daemon thread is terminated via requestTermination!
          }
          catch (RuntimeException re) {
            throw new TaskException("mutex lock failed", re);
          }
        }

        try {
          lockingLevel = mutex.getWriteHoldCount();
          if (checkLockKey(key, lockingLevel)) {
            break;
          }
        }
        catch (RuntimeException re) {
          mutex.writeLock().unlock();   // must work
          if (re instanceof TaskException) {
            throw re;
          }
          else  {
            throw new TaskException("obtaining lock failed", re);
          }
        }
      }
      else {
        synchronized(this) {
          assertAlive();
          assertNotKilled();
          nonMutexLockCount++;
          // we need synchronization here because otherwise another thread
          // may increment and checkLockKey in parallel
          lockingLevel = nonMutexLockCount;
          try {
            if (checkLockKey(key, lockingLevel)) {
              break;
            }
          }
          catch (RuntimeException re) {
            nonMutexLockCount--;
            if (re instanceof TaskException) {
              throw re;
            }
            else  {
              throw new TaskException("counter lock failed", re);
            }
          }
          // try again...
        }
      }
    }

    return lockingLevel == 1 ? createLock(key) : null;
  }


  /**
   * Sets and checks the locking key.<br>
   * If lockingLevel == 1, the key is just set.
   * Otherwise, it is compared with the current key, and if it does
   * not match, the nested lock is decremented by one and the thread
   * waits for an unlock operation by another thread.
   *
   * @param key the locking key to be checked or set
   * @param lockingLevel the locking level (1 to set the key, else nested lock)
   * @return true if key matches and/or lock set
   */
  private synchronized boolean checkLockKey(Object key, int lockingLevel) {
    assertAlive();
    assertNotKilled();
    if (lockingLevel == 1) {
      // set the locking key only for the first lock
      unlockKey = key;
      LOGGER.fine("{0}: lock succeeded for {1}", this, unlockKey);
    }
    else if (key != unlockKey) {
      // nested lock for a different key: unlock and start over
      if (usingMutexLocking) {
        unlockImpl(unlockKey);
        throw new TaskException(this + ": same thread '" + Thread.currentThread()
                + "' requested nested mutex lock with non-matching key! Expected="
                + (unlockKey == null ? "<null>" : unlockKey.toString())
                + ", got=" + (key == null ? "<null>" : key.toString()));
      }
      else {
        // another thread: wait for the counter to become released completely
        nonMutexLockCount--;    // revert increment of the lock count
        try {
          LOGGER.finer("{0}: waiting for unlock of {1} ...", this, unlockKey);
          wait(sleepInterval); // release monitor on this and wait for notification
        }
        catch (InterruptedException ie) {
          // daemon thread is terminated via requestTermination!
        }
        return false; // try again...
      }
    }

    return true;    // key matches or lock set
  }


  /**
   * Performs the unlock operation.
   *
   * @param key the unlock-key
   * @return true if really unlocked, else just decremented the lock count
   */
  private boolean unlockImpl(Object key) {

    int lockingLevel;

    if (usingMutexLocking) {
      /*
       * We don't need a 'synchronized(this)' here because we can assume
       * that the same thread that locked the dispatcher will also unlock it.
       * If it is a different thread or there is no lock at all,
       * the "unlock()" below will fail with an IllegalMonitorStateException.
       */
      checkUnlockKey(key);
      try {
        mutex.writeLock().unlock();
      }
      catch (RuntimeException re) {
        throw new TaskException("unlock failed", re);
      }
      lockingLevel = mutex.getWriteHoldCount();
    }
    else {
      synchronized(this) {
        try {
          checkUnlockKey(key);
          if (nonMutexLockCount <= 0) {
            throw new TaskException("dispatcher is not locked at all");
          }
          nonMutexLockCount--;
          lockingLevel = nonMutexLockCount;
        }
        finally {
          notifyAll();    // notify all threads waiting for a lock (see lockImpl: wait() above)
        }
      }
    }

    return lockingLevel == 0;
  }



  /**
   * Safely interrupt the dispatcher without interrupting any running task.
   * (even if the task was interruptable)
   */
  private void safeInterrupt() {
    if (interruptMutex.readLock().tryLock()) {
      // not executing any task
      try {
        super.interrupt();    // interrupt possible sleep()
      }
      finally {
        interruptMutex.readLock().unlock();
      }
    }
    // else the executing task: no need to interrupt because not sleeping
  }

  /**
   * Warns if the key is not null for counter-based dispatchers.
   *
   * @param key the lock/unlock key
   */
  private void checkNullKey(Object key) {
    if (!usingMutexLocking && key == null) {
      LOGGER.warning(this + ": counter-based locking and null key is potentially unsafe! Please use a key!",
                     new TaskException(">>> invoked from >>>"));
    }
  }


  /**
   * Checks for matching the unlock-key.
   *
   * @param key the key to unlock
   */
  private void checkUnlockKey(Object key) {
    if (key != unlockKey) {
      throw new TaskException(this + ": non-matching unlock key! Expected="
              + (unlockKey == null ? "<null>" : unlockKey.toString())
              + ", got=" + (key == null ? "<null>" : key.toString()));
    }
  }

}
