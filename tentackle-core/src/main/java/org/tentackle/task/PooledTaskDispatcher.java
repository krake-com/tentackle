/**
 * Tentackle - http://www.tentackle.org
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

import org.tentackle.daemon.DaemonSupervisor;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;

/**
 * A task dispatcher using a pool with a fixed number of threads.
 * <p>
 * The dispatcher is rather simple as the dispatching threads are used
 * round robin. Furthermore, the locking keys are mapped based on their
 * hashvalue (hence the fixed number of threads).
 *
 * @author harald
 */
public class PooledTaskDispatcher extends AbstractPooledTaskDispatcher {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(PooledTaskDispatcher.class);

  private final boolean supervised;                   // true if use supervisors
  private long deadInterval;                          // if supervised: dead detection interval
  private final TaskDispatcher[] dispatchers;         // task dispatcher pool
  private final DaemonSupervisor supervisor;          // supervisor (if supervised)
  private int dispatcherIndex;                        // last used dispatcher (round robin)
  private volatile boolean alive;                     // true if dispatcher is alive
  private volatile boolean terminationRequested;      // true if termination requested


  /**
   * Creates a pooled task dispatcher.<br>
   *
   * @param poolSize the number of threads
   * @param name the pool name
   * @param useMutexLocking true use a mutex for locking, else just a counter
   * @param sleepInterval keep alive in milliseconds
   * @param supervised true to use a {@link DaemonSupervisor} for each dispatcher
   * @param deadInterval interval in milliseconds to detect a dead dispatcher, 0 to disable detection (ignored if supervised = false)
   */
  public PooledTaskDispatcher(int poolSize, String name, boolean useMutexLocking, long sleepInterval, boolean supervised, long deadInterval) {

    super(name, useMutexLocking, sleepInterval);

    this.supervised   = supervised;
    this.deadInterval = deadInterval;

    if (poolSize < 2) {
      throw new IllegalArgumentException("poolsize must be > 1");
    }

    dispatchers = new TaskDispatcher[poolSize];
    dispatcherIndex = poolSize - 1; // start with 0

    if (supervised) {
      supervisor = createSupervisor();
      for (int i=0; i < poolSize; i++) {
        dispatchers[i] = null;
      }
    }
    else  {
      supervisor = null;
      for (int i=0; i < poolSize; i++) {
        dispatchers[i] = createDispatcher(i);
        dispatchers[i].addTaskListener(taskListener);
      }
    }
  }


  /**
   * Creates a non-supervising pooled task dispatcher.<br>
   *
   * @param poolSize the number of threads
   * @param name the pool name
   * @param useMutexLocking true use a mutex for locking, else just a counter
   * @param sleepInterval keep alive in milliseconds
   */
  public PooledTaskDispatcher(int poolSize, String name, boolean useMutexLocking, long sleepInterval) {
    this(poolSize, name, useMutexLocking, sleepInterval, false, 0);
  }


  @Override
  public String toDiagnosticString() {
    StringBuilder buf = new StringBuilder();
    for (TaskDispatcher dispatcher: dispatchers) {
      if (dispatcher != null) {
        buf.append(dispatcher.toDiagnosticString());
        buf.append('\n');
      }
    }
    return buf.toString();
  }


  /**
   * Gets the pool size.
   *
   * @return the pool size
   */
  public int getPoolSize() {
    return dispatchers.length;
  }


  /**
   * Returns whether dispatchers are supervised.
   *
   * @return true if supervised
   */
  public boolean isSupervised() {
    return supervised;
  }


  @Override
  public long getDeadInterval() {
    return deadInterval;
  }

  @Override
  public void setDeadInterval(long deadInterval) {
    this.deadInterval = deadInterval;
  }

  @Override
  public void setShutdownIdleTimeout(long shutdownIdleTimeout) {
    if (shutdownIdleTimeout != 0) {
      throw new UnsupportedOperationException("pooled dispatcher does not support shutdown idle timeout");
    }
  }

  @Override
  public long getShutdownIdleTimeout() {
    return 0;
  }


  /**
   * Gets all task dispatchers.
   *
   * @return the dispatchers
   */
  public TaskDispatcher[] getDispatchers() {
    return dispatchers;
  }

  /**
   * Creates a dispatcher for the pool.
   *
   * @param index the dispatcher index [0 ... poolsize-1]
   * @return the dispatcher
   */
  protected TaskDispatcher createDispatcher(int index) {
    return new DefaultTaskDispatcher(getName() + "-" + index, isUsingMutexLocking(),
                                     getSleepInterval(), isSupervised() ? getDeadInterval() : 0);
  }


  /**
   * Creates a daemon supervisor.
   * <p>
   * The method is only invoked if isSupervised() = true.
   *
   * @return the supervisor
   */
  protected DaemonSupervisor createSupervisor() {
    return new DaemonSupervisor("Supervisor for " + getName(), getDeadInterval(), 0, dispatchers.length) {

      private int dsptchrIndex;

      @Override
      public Thread createDaemon(int index) {
        TaskDispatcher dispatcher = createDispatcher(index);
        dispatcher.addTaskListener(taskListener);
        dsptchrIndex = index;
        dispatchers[index] = dispatcher;
        return (Thread) dispatcher;
      }

      @Override
      public void cleanupDaemon(Thread daemon) {
        super.cleanupDaemon(daemon);
        synchronized(PooledTaskDispatcher.this) {
          dispatchers[dsptchrIndex].removeTaskListener(taskListener);    // for sure
          dispatchers[dsptchrIndex] = null;
        }
      }
    };
  }



  @Override
  public void start() {
    if (isSupervised()) {
      supervisor.start();
    }
    else  {
      for (TaskDispatcher dispatcher: dispatchers) {
        dispatcher.start();
      }
      alive = true;
    }
  }

  @Override
  public boolean isAlive() {
    if (isSupervised()) {
      return supervisor.isAlive();
    }
    else  {
      return alive;
    }
  }

  @Override
  public void terminate() {
    if (isSupervised()) {
      supervisor.terminate();
    }
    else  {
      for (TaskDispatcher dispatcher: dispatchers) {
        dispatcher.terminate();
      }
      alive = false;
    }
  }

  @Override
  public void requestTermination() {
    terminationRequested = true;
    if (isSupervised()) {
      supervisor.requestTermination();
    }
    else  {
      for (TaskDispatcher dispatcher: dispatchers) {
        dispatcher.requestTermination();
      }
    }
  }

  @Override
  public boolean isTerminationRequested() {
    return terminationRequested;
  }

  /**
   * {@inheritDoc}
   * <p>
   * The key is mapped to the dispatcher as hashval % poolsize.
   */
  @Override
  public TaskDispatcherLock lock(Object key) {
    return getDispatcherForKey(key).lock(key);
  }


  /**
   * {@inheritDoc}
   * <p>
   * The key is mapped to the dispatcher as hashval % poolsize.
   */
  @Override
  public boolean unlock(Object key) {
    return getDispatcherForKey(key).unlock(key);
  }


  /**
   * {@inheritDoc}
   * <p>
   * We just do a simple round robin here.
   *
   * @return the dispatcher
   */
  @Override
  protected TaskDispatcher getNextDispatcher() {
    for (;;) {
      int loopCount = 0;
      while (loopCount < 2) {
        dispatcherIndex++;
        if (dispatcherIndex >= dispatchers.length) {
          dispatcherIndex = 0;
          loopCount++;
        }
        TaskDispatcher dispatcher = dispatchers[dispatcherIndex];
        if (dispatcher != null) {
          return dispatcher;
        }
      }
      // all dispatchers are dead: wait a bit until some are restarted
      LOGGER.info("all dispatchers are dead -> waiting for {0} ms ...", getSleepInterval());
      try {
        Thread.sleep(getSleepInterval());
      }
      catch (InterruptedException iex) {
        // start over
      }
    }
  }



  /**
   * Gets the dispatcher by locking key.
   *
   * @param key the locking key
   * @return the dispatcher to use
   */
  private TaskDispatcher getDispatcherForKey(Object key) {
    /**
     * Key null is always assigned to dispatcher 0.
     * The rest is assigned modulo poolsize.
     */
    int index = key == null ? 0 : key.hashCode() % dispatchers.length;

    if (isSupervised()) {
      for (;;) {
        synchronized(this) {
          TaskDispatcher dispatcher = dispatchers[index];
          if (dispatcher != null && dispatcher.isAlive()) {
            return dispatcher;
          }
        }
        // dispatcher is dead: wait a bit until restarted
        LOGGER.info("dispatcher[{0}] is not alive -> waiting for {1} ms ...", index, getSleepInterval());
        try {
          Thread.sleep(getSleepInterval());
        }
        catch (InterruptedException iex) {
          // start over
        }
      }
    }
    else  {
      return dispatchers[index];
    }
  }

}
