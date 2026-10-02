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


package org.tentackle.session;

import org.tentackle.log.Logger;
import org.tentackle.task.DefaultTaskDispatcher;
import org.tentackle.task.Task;

/**
 * A thread executing tasks in a serialized manner on an exclusive session.
 * <p>
 * The dispatcher thread becomes the owner of the session and makes it its thread-local session.
 * Tasks extending {@link AbstractSessionTask} get the session applied when added.
 * The dispatcher terminates when its session gets closed.
 *
 * @author harald
 */
public class DefaultSessionTaskDispatcher extends DefaultTaskDispatcher implements SessionTaskDispatcher {

  private static final Logger LOGGER = Logger.get(DefaultSessionTaskDispatcher.class);


  private volatile boolean sessionClosedOnTermination;  // true if the session should be closed
  private volatile boolean sessionKeptAlive;            // true if ping to keep the session alive periodically
  private volatile Session session;                     // the session this thread is attached to


  /**
   * Creates an execution thread for a given session.<br>
   * The session must not be used by any other thread!
   *
   * @param name                      the dispatcher's name
   * @param session                   the open session, must not be pooled
   * @param useMutexLocking           true use a mutex for locking, else just a counter
   * @param keepAliveMillis           keep alive in milliseconds
   * @param deadInterval              interval in milliseconds to detect a dead dispatcher, 0 to disable detection
   * @throws IllegalArgumentException if the session is pooled
   */
  public DefaultSessionTaskDispatcher(String name, Session session,
                                      boolean useMutexLocking, long keepAliveMillis, long deadInterval) {
    super(name, useMutexLocking, keepAliveMillis, deadInterval);
    if (session != null && session.isPooled()) {
      throw new IllegalArgumentException(session + " must not be pooled");
    }
    this.session = session;
  }

  /**
   * Creates a dispatcher to be configured later.
   *
   * @param name the dispatcher's name
   */
  public DefaultSessionTaskDispatcher(String name) {
    super(name);
  }


  @Override
  public void setSessionKeptAlive(boolean sessionKeptAlive) {
    this.sessionKeptAlive = sessionKeptAlive;
  }

  @Override
  public boolean isSessionKeptAlive() {
    return sessionKeptAlive;
  }


  @Override
  public Session getSession() {
    return session;
  }

  @Override
  public void setSession(Session session) {
    if (Thread.currentThread() == this) {
      // only the dispatcher thread can change the session
      session.setOwnerThread(this);
      session.makeCurrent();
    }
    else {
      // no change allowed from another thread unless the dispatcher wasn't started yet
      assertNotAlive();
    }
    this.session = session;
  }

  @Override
  public boolean isSessionClosedOnTermination() {
    return sessionClosedOnTermination;
  }

  @Override
  public void setSessionClosedOnTermination(boolean closeSessionOnTermination) {
    this.sessionClosedOnTermination = closeSessionOnTermination;
  }


  @Override
  protected SessionTaskDispatcherLock createLock(Object key) {
    return new SessionTaskDispatcherLock(this, key);
  }


  /**
   * Adds a task to the queue.<br>
   * Applies the dispatcher's session to {@link AbstractSessionTask}s.
   *
   * @param task the task to add
   * @return true if added, false if already in the queue
   */
  @Override
  public boolean addTask(Task task) {
    if (task instanceof AbstractSessionTask) {
      ((AbstractSessionTask) task).setSession(session);
    }
    return super.addTask(task);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to run {@link Session#setAlive(boolean)} when sleeping for a while, if the
   * session is {@link #isSessionKeptAlive() kept alive}. Requests termination if that fails
   * or the session has been closed.
   */
  @Override
  protected void unlockInternal(long sleepMs) {
    if (sessionKeptAlive && sleepMs > 0) {
      try {
        LOGGER.fine("{0}: keep alive on {1}", this, session);
        session.setAlive(true);
      }
      catch (RuntimeException ex) {
        // some severe exception, e.g., connection lost, backend error, etc...
        LOGGER.severe("ping failed -> terminating " + this, ex);
        requestTermination();
      }
    }
    else if (!session.isOpen()) {
      LOGGER.info("session " + session + " closed -> terminating " + this);
      requestTermination();
    }
    super.unlockInternal(sleepMs);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to set the thread-local session.
   */
  @Override
  public void run() {
    // don't let any other thread change my thread-local session!
    // see Session.setCurrentSession.
    session.setOwnerThread(this);
    session.makeCurrent();
    super.run();
  }



  /**
   * {@inheritDoc}
   * <p>
   * Overridden to close the session, if {@link #isSessionClosedOnTermination()}.
   */
  @Override
  protected void cleanup() {
    super.cleanup();

    if (session != null && sessionClosedOnTermination) {
      try {
        session.close();
      }
      catch (RuntimeException ex) {
        LOGGER.severe("cleaning up " + this + " failed", ex);
      }
      session = null;
    }
  }

}
