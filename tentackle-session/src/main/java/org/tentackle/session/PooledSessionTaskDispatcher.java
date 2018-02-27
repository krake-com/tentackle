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


package org.tentackle.session;

import org.tentackle.daemon.DaemonSupervisor;
import org.tentackle.log.Logger;
import org.tentackle.task.PooledTaskDispatcher;
import org.tentackle.task.TaskDispatcher;

/**
 *
 * @author harald
 */
public class PooledSessionTaskDispatcher extends PooledTaskDispatcher {

  private static final Logger LOGGER = Logger.get(PooledSessionTaskDispatcher.class);


  private final SessionInfo sessionInfo;          // the session info
  private final int poolSize;                     // the poolsize

  private Session[] dbSessions;                   // the sessions


  /**
   * Creates a pooled db task dispatcher.<br>
   *
   * @param sessionInfo the session info
   * @param poolSize the fixed pool size
   * @param name the pool name
   * @param useMutexLocking true use a mutex for locking, else just a counter
   * @param sleepInterval keep alive in milliseconds
   * @param supervised true to use a {@link DaemonSupervisor} for each dispatcher
   * @param deadInterval interval in milliseconds to detect a dead dispatcher, 0 to disable detection (ignored if supervised = false)
   */
  public PooledSessionTaskDispatcher(SessionInfo sessionInfo, int poolSize,
                                     String name, boolean useMutexLocking, long sleepInterval, boolean supervised, long deadInterval) {

    super(poolSize, name, useMutexLocking, sleepInterval, supervised, deadInterval);

    this.sessionInfo = sessionInfo;
    this.poolSize = poolSize;
  }

  /**
   * Creates a non-supervising pooled db task dispatcher.<br>
   *
   * @param sessionInfo the session info
   * @param poolSize the pool size
   * @param name the pool name
   * @param useMutexLocking true use a mutex for locking, else just a counter
   * @param sleepInterval keep alive in milliseconds
   */
  public PooledSessionTaskDispatcher(SessionInfo sessionInfo, int poolSize,
                                     String name, boolean useMutexLocking, long sleepInterval) {
    this(sessionInfo, poolSize, name, useMutexLocking, sleepInterval, false, 0);
  }

  /**
   * Gets the db sessions.
   *
   * @return the db sessions
   */
  public synchronized Session[] getDbSessions() {
    if (dbSessions == null) {
      dbSessions = new Session[poolSize];
    }
    return dbSessions;
  }

  /**
   * Gets the user info.
   *
   * @return the user info
   */
  public SessionInfo getSessionInfo() {
    return sessionInfo;
  }


  /**
   * Creates a new db session.
   *
   * @return the session
   */
  protected Session createSession() {
    return SessionFactory.getInstance().create(getSessionInfo());
  }


  @Override
  protected DefaultSessionTaskDispatcher createDispatcher(int index) {
    Session session;
    synchronized(this) {
      session = getDbSessions()[index];
      if (session == null || !session.isOpen()) {
        session = createSession();
      }
    }
    DefaultSessionTaskDispatcher dispatcher =
        new DefaultSessionTaskDispatcher(getName() + "-" + index, session, isUsingMutexLocking(),
                                getSleepInterval(), isSupervised() ? getDeadInterval() : 0);
    dispatcher.setSessionClosedOnTermination(true);   // @todo make class implement SessionTaskDispatcher...
    dispatcher.setSessionKeptAlive(true);
    return dispatcher;
  }


  @Override
  protected DaemonSupervisor createSupervisor() {
    return new DaemonSupervisor("Supervisor for " + getName(), getDeadInterval(), 0, getDispatchers().length) {

      private int dispatcherIndex;

      @Override
      public Thread createDaemon(int index) {
        TaskDispatcher dispatcher = createDispatcher(index);
        dispatcher.addTaskListener(taskListener);
        dispatcherIndex = index;
        getDispatchers()[index] = dispatcher;
        return (Thread) dispatcher;
      }

      @Override
      public void cleanupDaemon(Thread daemon) {
        super.cleanupDaemon(daemon);
        synchronized(PooledSessionTaskDispatcher.this) {
          getDispatchers()[dispatcherIndex].removeTaskListener(taskListener);    // for sure
          getDispatchers()[dispatcherIndex] = null;
          try {
            getDbSessions()[dispatcherIndex].close();
            getDbSessions()[dispatcherIndex] = null;
          }
          catch (RuntimeException re) {
            LOGGER.warning("db session could not be closed", re);
          }
        }
      }
    };
  }

}
