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

package org.tentackle.daemon;

import org.tentackle.log.Logger;
import org.tentackle.misc.DiagnosticUtilities;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A supervisor controlling the life cycle of daemons.
 * <p>
 * Daemons are ordinary threads created by {@link #createDaemon(int)}. Optionally, daemons
 * may implement {@link Supervisable} which allows further
 * checks and control, such as the detection and killing of dead daemons.
 * <p>
 * Every {@link #getCheckInterval() check interval}, the supervisor restarts terminated daemons,
 * unless disabled or the {@link #getMaxStartCount() maximum start count} is reached.
 *
 * @author harald
 */
public abstract class DaemonSupervisor extends Thread implements Scavenger {

  private static final Logger LOGGER = Logger.get(DaemonSupervisor.class);

  /**
   * The daemon termination info.
   */
  public static class TerminationInfo {

    private final long since;         // termination time
    private final String name;        // the daemon name
    private final Object cause;       // the termination cause

    private TerminationInfo(String name, Object cause) {
      this.since = System.currentTimeMillis();
      this.name = name;
      this.cause = cause;
    }

    /**
     * Gets the epochal termination time.
     *
     * @return the time in millis
     */
    public long getSince() {
      return since;
    }

    /**
     * Gets the name of the terminated daemon.
     *
     * @return the daemon name
     */
    public String getName() {
      return name;
    }

    /**
     * Gets the termination cause.
     *
     * @return the cause, usually an exception
     */
    public Object getCause() {
      return cause;
    }
  }

  /**
   * Daemon wrapper.
   * <p>
   * The mutable state is written by the supervisor thread in {@link #run()} while holding
   * only the <em>read</em> lock (the write lock is reserved for replacing the daemon array).
   * Two read-lock holders exclude neither each other nor establish a happens-before edge,
   * so the state must be published explicitly for the accessors invoked by other threads.
   */
  private class Daemon {

    private volatile Thread supervisedDaemon;              // the managed daemon
    private final int daemonIndex;                         // the slot index
    private volatile Supervisable supervisable;            // != null if daemon is a Supervisable
    private final AtomicInteger startCount;                // number of starts
    private volatile boolean enabled = true;               // true if daemon is enabled (default)
    private volatile TerminationInfo terminationInfo;      // the termination info, null if daemon running
    private volatile TerminationInfo lastTerminationInfo;  // the saved termination info when new daemon was started

    /**
     * Creates a daemon wrapper.
     *
     * @param daemonIndex the daemon slot index
     */
    private Daemon(int daemonIndex) {
      this.daemonIndex = daemonIndex;
      this.startCount = new AtomicInteger();
    }

    /**
     * Sets the supervised thread.
     *
     * @param supervisedDaemon the thread
     */
    private void setThread(Thread supervisedDaemon) {
      this.supervisedDaemon = supervisedDaemon;
      supervisable = supervisedDaemon instanceof Supervisable ? (Supervisable) supervisedDaemon : null;
    }

    /**
     * Returns whether the start count has exceeded.
     *
     * @return true if too many starts
     */
    private boolean isStartCountExceeded() {
      return maxStartCount > 0 && startCount.get() >= maxStartCount;
    }

    /**
     * Starts the supervised thread.
     */
    private void start() {
      int attempt = startCount.incrementAndGet();
      LOGGER.info(() -> {
        StringBuilder msg = new StringBuilder("starting ");
        msg.append(this);
        msg.append(" (attempt ");
        msg.append(attempt);
        if (maxStartCount > 0) {
          msg.append(" of ");
          msg.append(maxStartCount);
        }
        msg.append(")");
        return msg.toString();
      });
      supervisedDaemon.start();
    }

    /**
     * Cleans up the wrapper.
     */
    private void clear() {
      if (supervisable != null) {
        lastTerminationInfo = null;    // daemon terminated: clear old cause in case no current cause
        terminationInfo = new TerminationInfo(supervisable.getName(), supervisable.getTerminationCause());
      }
      supervisedDaemon = null;
      supervisable = null;
    }

    /**
     * Kills the daemon.
     * <p>
     * Daemons are not required to be {@link Supervisable} (see {@link #createDaemon(int)}).
     * An ordinary thread cannot be killed, so it is just handed over to the killed-daemons
     * list, where {@link #run()} keeps interrupting it until it has terminated.
     */
    private void kill() {
      if (supervisable != null) {
        supervisable.kill();
      }
      synchronized(killedDaemons) {
        killedDaemons.add(supervisedDaemon);
      }
      clear();
    }

    /**
     * Clears the start counter.
     */
    private void clearStartCount() {
      startCount.set(0);
    }

    /**
     * Sets the slot as enabled.
     *
     * @param enabled true if enabled
     */
    private void setEnabled(boolean enabled) {
      if (this.enabled != enabled) {
        this.enabled = enabled;
        if (enabled) {
          startCount.set(0);
        }
      }
    }

    /**
     * Returns whether the slot is enabled.
     *
     * @return true if enabled
     */
    private boolean isEnabled() {
      return enabled;
    }

    @Override
    public String toString() {
      return (supervisable != null ? "supervisable" : "") + " daemon#" + daemonIndex + ": " + supervisedDaemon;
    }
  }



  private volatile long checkInterval;        // checking interval in milliseconds
  private volatile int maxStartCount;         // max. number of restarts, 0 = unlimited
  private Daemon[] daemons;                   // the daemons under supervision (guarded by lock)
  private final List<Thread> killedDaemons;   // daemons killed but still alive

  private volatile boolean stopRequested;     // true if stop has been requested
  private final ReentrantReadWriteLock lock;  // to lock supervisor when number of daemons is changed


  /**
   * Creates a supervisor.
   *
   * @param name the supervisor name
   * @param checkInterval the checking interval in milliseconds
   * @param maxStartCount max. number of starts, 0 = unlimited
   * @param daemonNum number of daemons to start
   * @throws IllegalArgumentException if one of the parameters is out of range
   */
  public DaemonSupervisor(String name, long checkInterval, int maxStartCount, int daemonNum) {

    super(name);

    setCheckInterval(checkInterval);
    setMaxStartCount(maxStartCount);

    lock = new ReentrantReadWriteLock();
    killedDaemons = new ArrayList<>();

    setDaemon(true);
    setDaemonNum(daemonNum);
  }

  /**
   * Creates a supervisor for one daemon.
   *
   * @param name the supervisor name
   * @param checkInterval the checking interval in milliseconds
   * @param maxStartCount max. number of starts, 0 = unlimited
   */
  public DaemonSupervisor(String name, long checkInterval, int maxStartCount) {
    this(name, checkInterval, maxStartCount, 1);
  }

  /**
   * Gets the check interval in milliseconds.
   *
   * @return the check interval
   */
  public long getCheckInterval() {
    return checkInterval;
  }

  /**
   * Sets the check interval in milliseconds.
   *
   * @param checkInterval the check interval, must be &gt; 0
   * @throws IllegalArgumentException if the interval is not positive
   */
  public void setCheckInterval(long checkInterval) {
    if (checkInterval < 1) {
      throw new IllegalArgumentException("checkInterval must be > 0");
    }
    this.checkInterval = checkInterval;
    LOGGER.info("{0}: check interval = {1}", this, checkInterval);
  }

  /**
   * Gets the maximum number of starts.
   *
   * @return the maximum number of starts, 0 = unlimited
   */
  public int getMaxStartCount() {
    return maxStartCount;
  }

  /**
   * Sets the maximum number of starts.
   *
   * @param maxStartCount the maximum number of starts, 0 = unlimited
   */
  public void setMaxStartCount(int maxStartCount) {
    if (maxStartCount < 0) {
      throw new IllegalArgumentException("maxStartCount must be >= 0");
    }
    this.maxStartCount = maxStartCount;
    LOGGER.info("{0}: max. start count = {1}", this, maxStartCount);
  }

  /**
   * Gets the number of daemons.
   *
   * @return the number of daemons
   */
  public int getDaemonNum() {
    lock.readLock().lock();
    try {
      return daemons == null ? 0 : daemons.length;
    }
    finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Sets the number of daemons.
   * <p>
   * If the number is increased, the corresponding number of
   * daemons is started immediately. If the number is decreased,
   * the surplus daemons are killed, starting at the tail.
   * Daemons not implementing {@link Supervisable} cannot be killed directly:
   * they are interrupted repeatedly until they have terminated.
   *
   * @param daemonNum the number of daemons, 0 for none
   */
  public void setDaemonNum(int daemonNum) {

    if (daemonNum < 0) {
      throw new IllegalArgumentException("daemonNum must be >= 0");
    }
    LOGGER.info("{0}: number of daemons = {1}", this, daemonNum);

    lock.writeLock().lock();

    try {

      if (daemons == null) {    // initial setup
        daemons = new Daemon[daemonNum];
        for (int i = 0; i < daemons.length; i++) {
          daemons[i] = new Daemon(i);    // clear: start daemon in run()
        }
      }
      else if (daemonNum != daemons.length) {   // already setup
        // save current state
        Daemon[] oldDaemons = daemons;

        int daemonDiff = daemonNum - oldDaemons.length;
        Daemon[] newDaemons = new Daemon[daemonNum];

        if (daemonDiff < 0) {
          /*
           * Kill "from tail".
           * Every surplus daemon must be killed, not just the Supervisables: simply dropping
           * a slot would leave its daemon running unsupervised, outliving even the supervisor.
           * An ordinary thread cannot be killed directly, but kill() hands it over to the
           * killed-daemons list, where run() interrupts it until it has terminated.
           */
          for (int i = oldDaemons.length - 1; i >= daemonNum; i--) {
            Daemon daemon = oldDaemons[i];
            if (daemon.supervisedDaemon != null &&
                (daemon.supervisable == null || !daemon.supervisable.isKilled())) {
              daemon.kill();
            }
          }
          // the surviving daemons keep their slot index
          System.arraycopy(oldDaemons, 0, newDaemons, 0, daemonNum);
        }
        else {    // daemonDiff > 0
          for (int i = 0; i < daemonNum; i++) {
            newDaemons[i] = i < oldDaemons.length ? oldDaemons[i] : new Daemon(i);
          }
        }

        daemons = newDaemons;
      }
    }
    finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Clears the start counter of a daemon.<br>
   * A daemon may invoke this once it is running properly, so that the maximum start count
   * limits the number of consecutive failed starts only.
   *
   * @param index the daemon index
   */
  public void clearStartCount(int index) {
    Daemon daemon = getDaemon(index);
    if (daemon != null) {
      daemon.clearStartCount();
    }
  }

  /**
   * Determines whether the daemon for the given index is enabled or not.
   * <p>
   * Only enabled daemons are started. If a running daemon is disabled,
   * it will be killed.
   *
   * @param index the daemon index
   * @return true if enabled
   */
  public boolean isDaemonEnabled(int index) {
    Daemon daemon = getDaemon(index);
    return daemon != null && daemon.isEnabled();
  }

  /**
   * Sets the enabled state for daemon at the given index.<br>
   * Setting a daemon to disabled will kill it if running
   * and prevent the daemon from being (re-)started.
   * By default, all daemons are enabled.
   * Setting the daemon to "enabled" will also clear the start counter.
   *
   * @param index the daemon index
   * @param enabled true if enabled
   */
  public void setDaemonEnabled(int index, boolean enabled) {
    Daemon daemon = getDaemon(index);
    if (daemon != null) {
      daemon.setEnabled(enabled);
    }
  }

  /**
   * Requests to stop this supervisor.<br>
   * Does not wait and returns immediately.
   *
   * @see #terminate()
   */
  public void requestTermination() {
    stopRequested = true;
    safeInterrupt();
  }

  /**
   * Terminates the supervisor.<br>
   * Waits until the supervisor has terminated.<br>
   * Daemons implementing {@link Supervisable} will be killed. Ordinary threads are left running.
   */
  public void terminate() {
    requestTermination();
    for (;;) {
      try {
        join();
        break;
      }
      catch (InterruptedException e)  {
        // daemon thread is terminated via requestTermination!
        LOGGER.warning("termination interrupted -> ignored");
      }
    }
  }

  /**
   * Creates a new startable daemon.
   * <p>
   * The daemon may be any ordinary thread.
   * It may optionally implement the interface {@link Supervisable}
   * which allows monitoring the health status and killing the daemon
   * if necessary.
   *
   * @param index the daemon's slot [0 ... daemonNum-1]
   * @return the daemon (not started yet), null if wait for the interval and try again
   */
  public abstract Thread createDaemon(int index);

  /**
   * Gets a currently running daemon thread.
   *
   * @param index the daemon's slot [0 ... daemonNum-1]
   * @return the running daemon, null if no daemon running
   */
  public Thread getRunningDaemon(int index) {
    Thread t = getStartedDaemon(index);
    return t == null || !t.isAlive() ? null : t;
  }

  /**
   * Gets an already started daemon thread.
   *
   * @param index the daemon's slot [0 ... daemonNum-1]
   * @return the started daemon, null if no such daemon started yet for this slot
   */
  public Thread getStartedDaemon(int index) {
    Daemon daemon = getDaemon(index);
    return daemon == null ? null : daemon.supervisedDaemon;   // read once: may be cleared concurrently
  }

  /**
   * Gets the latest termination info.
   * <p>
   * Notice that the info may be non-null even if the daemon is
   * running properly. In this case, the termination info refers
   * to the previously terminated instance.
   *
   * @param index the daemon index
   * @return the info, null if none
   */
  public TerminationInfo getTerminationInfo(int index) {
    TerminationInfo info = null;
    Daemon daemon = getDaemon(index);
    if (daemon != null) {
      info = daemon.terminationInfo;
      if (info == null) {
        // new daemon started in the meantime? (i.e., current cause cleared)
        info = daemon.lastTerminationInfo;    // try saved cause
      }
    }
    return info;
  }

  /**
   * Gets all started daemons.
   *
   * @return all started daemons, never null
   */
  public Thread[] getStartedDaemons() {
    lock.readLock().lock();
    try {
      /*
       * Collect in a single pass and read each slot exactly once.
       * The supervisor thread starts and clears daemons while holding only the read-lock,
       * i.e., concurrently with this method: sizing the array in a first pass and filling it
       * in a second would overflow the array whenever a daemon started in between,
       * and would leave trailing nulls whenever one terminated in between.
       */
      List<Thread> startedThreads = new ArrayList<>(daemons.length);
      for (Daemon daemon: daemons) {
        Thread startedThread = daemon.supervisedDaemon;
        if (startedThread != null) {
          startedThreads.add(startedThread);
        }
      }
      return startedThreads.toArray(new Thread[0]);
    }
    finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Gets all running daemons.
   *
   * @return all running daemons, never null
   */
  public Thread[] getRunningDaemons() {
    List<Thread> runningThreads = new ArrayList<>();
    for (Thread thread: getStartedDaemons()) {
      if (thread.isAlive()) {
        runningThreads.add(thread);
      }
    }
    return runningThreads.toArray(new Thread[0]);
  }

  /**
   * Gets the earliest start time of all supervised daemons.
   *
   * @return the start time, 0 if no supervisable daemon started at all
   */
  public long getEarliestStartTime() {
    long time = 0;
    for (Thread thread: getStartedDaemons()) {
      if (thread instanceof Supervisable) {
        long startedAt = ((Supervisable) thread).startedAt();
        if (startedAt != 0 && (time == 0 || startedAt < time)) {
          time = startedAt;
        }
      }
    }
    return time;
  }

  /**
   * Gets the latest start time of all supervised daemons.
   *
   * @return the start time, 0 if no supervisable daemon started at all
   */
  public long getLatestStartTime() {
    long time = 0;
    for (Thread thread: getStartedDaemons()) {
      if (thread instanceof Supervisable) {
        long startedAt = ((Supervisable) thread).startedAt();
        if (startedAt > time) {
          time = startedAt;
        }
      }
    }
    return time;
  }

  /**
   * Gets the earliest termination time of all supervised daemons.
   *
   * @return the termination time, 0 if no supervised terminated daemon
   */
  public long getEarliestTerminationTime() {
    long time = 0;
    for (Thread thread: getStartedDaemons()) {
      if (thread instanceof Supervisable) {
        long terminatedAt = ((Supervisable) thread).terminatedAt();
        if (terminatedAt != 0 && (time == 0 || terminatedAt < time)) {
          time = terminatedAt;
        }
      }
    }
    return time;
  }

  /**
   * Gets the latest termination time of all supervised daemons.
   *
   * @return the termination time, 0 if no supervised terminated daemon
   */
  public long getLatestTerminationTime() {
    long time = 0;
    for (Thread thread: getStartedDaemons()) {
      if (thread instanceof Supervisable) {
        long terminatedAt = ((Supervisable) thread).terminatedAt();
        if (terminatedAt > time) {
          time = terminatedAt;
        }
      }
    }
    return time;
  }

  /**
   * Does any cleanup necessary whenever a daemon terminated
   * or crashed.
   * <p>
   * The default implementation does nothing.
   *
   * @param daemon the terminated daemon
   */
  public void cleanupDaemon(Thread daemon) {
    // no default implementation
  }

  /**
   * Safely interrupts the supervisor.<br>
   * The supervisor is interrupted while sleeping, not while checking the daemons.
   */
  public void safeInterrupt() {
    /*
     * Acquiring the write lock waits until {@link #run()} has left the daemon checking loop,
     * so that the interrupt hits the supervisor while it is sleeping.
     * This is impossible if the invoker already holds the read lock, which is the case when
     * {@link #createDaemon(int)} or {@link #cleanupDaemon(Thread)} request the termination:
     * a {@link ReentrantReadWriteLock} cannot be upgraded, so the write lock would wait for
     * the very thread asking for it. The supervisor is obviously not sleeping then,
     * hence it is interrupted directly.
     */
    if (lock.getReadHoldCount() > 0) {
      interrupt();
    }
    else {
      lock.writeLock().lock();
      try {
        interrupt();
      }
      finally {
        lock.writeLock().unlock();
      }
    }
  }

  /**
   * Gets the daemon for the given index.
   *
   * @param index the daemon index
   * @return the daemon, null if index out of range
   */
  private Daemon getDaemon(int index) {
    lock.readLock().lock();
    try {
      int daemonNum = getDaemonNum();
      if (index < 0 || index >= daemonNum) {
        return null;
      }
      return daemons[index];
    }
    finally {
      lock.readLock().unlock();
    }
  }


  @Override
  public void run() {

    LOGGER.info("daemon supervisor {0} started", getClass().getName());

    while (!stopRequested) {

      lock.readLock().lock();

      try {

        for (int daemonIndex=0; daemonIndex < daemons.length; daemonIndex++) {

          Daemon daemon = daemons[daemonIndex];

          if (daemon.supervisable != null && daemon.supervisable.isDead() && !daemon.supervisable.isKilled()) {
            if (daemon.supervisedDaemon != null && !daemon.supervisedDaemon.isAlive()) {
              LOGGER.warning("daemon has terminated: {0}", daemon.supervisable);
            }
            else {
              DiagnosticUtilities.getInstance().logStackDump(Logger.Level.WARNING, "dead daemon detected: " + daemon.supervisable);
            }

            try {
              daemon.kill();    // mark it killed (even if !alive anymore)
            }
            catch (RuntimeException rex) {
              LOGGER.severe("killing " + daemon.supervisedDaemon + " failed", rex);
              /*
               * If kill() sets isKilled first and then fails, kill will be tried only once.
               * If kill fails before setting isKilled(), killing will be retried over and over.
               * In both cases cleanup() will be invoked on the next round.
               */
            }
          }

          if (daemon.supervisedDaemon != null && daemon.supervisedDaemon.isAlive()) {
            if (!isDaemonEnabled(daemonIndex)) {
              daemon.kill();
            }
            else  {
              // survived at least one interval -> clear termination cause
              daemon.lastTerminationInfo = daemon.terminationInfo;    // save old cause
              daemon.terminationInfo = null;
            }
          }
          else {
            // no more alive
            if (daemon.supervisedDaemon != null) {
              LOGGER.info("cleanup: {0}", daemon.supervisedDaemon);
              try {
                cleanupDaemon(daemon.supervisedDaemon);
                daemon.clear();
              }
              catch (RuntimeException rex) {
                LOGGER.severe("cleanup " + daemon.supervisedDaemon + " failed", rex);
              }
            }

            if (daemon.isEnabled() && !daemon.isStartCountExceeded()) {
              try {
                daemon.setThread(createDaemon(daemonIndex));
              }
              catch (RuntimeException rex) {
                LOGGER.severe("creating daemon failed", rex);
              }

              if (daemon.supervisedDaemon != null) {
                daemon.start();
              }
            }
          }
        }
      }
      finally {
        lock.readLock().unlock();
      }

      try {
        sleep(checkInterval);
      }
      catch (InterruptedException ex) {
        // daemon thread is terminated via requestTermination!
        LOGGER.warning("interrupted -> ignored");
      }

      // verify that all killed threads really have terminated
      synchronized(killedDaemons) {
        for (Iterator<Thread> iter = killedDaemons.iterator(); iter.hasNext(); ) {
          Thread killedDaemon = iter.next();
          try {
            if (killedDaemon.isAlive()) {
              // killed but still alive: interrupt wait() or I/O or any other blocking operation
              killedDaemon.interrupt();
            }
            else  {
              // properly terminated
              iter.remove();
              LOGGER.info("{0} has terminated", killedDaemon);
            }
          }
          catch (RuntimeException rex) {
            LOGGER.severe("cleanup pending thread failed", rex);
          }
        }
      }
    }

    // shutdown all daemons
    lock.readLock().lock();
    try {
      for (Daemon daemon: daemons) {
        if (daemon.supervisable != null && !daemon.supervisable.isKilled()) {
          daemon.kill();
        }
      }
    }
    finally {
      lock.readLock().unlock();
    }

    LOGGER.info("daemon supervisor {0} terminated", getClass().getName());
  }

}
