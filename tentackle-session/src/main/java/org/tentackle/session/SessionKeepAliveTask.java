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

import org.tentackle.common.ExceptionHelper;
import org.tentackle.log.Logger;
import org.tentackle.task.AbstractTask;
import org.tentackle.trip.TripStreamClosedException;

import java.io.Serial;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The task to keep a session alive.<br>
 * The task uses the {@link java.util.concurrent.ExecutorService} to spawn asynchronous pings.
 * This is necessary because a remote session
 * may block on slow or bad communication lines which should not block the whole task dispatcher.
 */
public class SessionKeepAliveTask extends AbstractTask {

  @Serial
  private static final long serialVersionUID = 1L;

  private static final Logger LOGGER = Logger.get(SessionKeepAliveTask.class);

  private static final int MAX_FAILURES = 10;     // max. 10 failures -> then close the db


  private final transient SessionKeepAliveDaemon daemon;    // the keep alive daemon
  private final transient Session session;                  // the session to keep alive
  private final long timeOut;                               // execution timeout in milliseconds
  private final transient Callable<Boolean> aliveCallable;  // the worker to setAlive

  private transient Future<Boolean> future;                 // pending future, null = finished
  private transient int failCount;                          // number of failures in setAlive()

  /**
   * Creates a keep alive task.
   *
   * @param daemon the keep alive daemon
   * @param session the session
   * @param interval the check interval in milliseconds, &le; 0 to close the session
   * @param timeOut the timeout interval in milliseconds
   */
  public SessionKeepAliveTask(SessionKeepAliveDaemon daemon, Session session, long interval, long timeOut) {
    this.daemon = daemon;
    this.session = session;
    this.timeOut = timeOut;

    aliveCallable = createAliveCallable();
    if (interval > 0) {
      setRepeatInterval(interval);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void run() {
    if (session.isOpen()) {
      if (getRepeatInterval() > 0) {
        LOGGER.fine("keep-alive {0}", session);
        if (future == null) {
          future = daemon.getExecutorService().submit(aliveCallable);
        }
        try {
          // wait for operation to complete, abort if timeout
          future.get(timeOut, TimeUnit.MILLISECONDS);
          failCount = 0;
          future = null;
        }
        catch (TimeoutException tex) {
          LOGGER.fine("setAlive still pending for {0}", session);
        }
        catch (ExecutionException eex) {
          boolean remoteClosed = session.isRemote() && ExceptionHelper.extractException(true, eex, TripStreamClosedException.class) != null;
          if (!session.isOpen() || remoteClosed) {
            // this is usually a race condition, when sessions are closed while the keepalive task is running in parallel
            LOGGER.info("session {0} closed in the meantime{1}", session, remoteClosed ? " by remote server" : "");
            daemon.removeAliveTask(session, this);
          }
          else {
            failCount++;
            LOGGER.warning("setAlive failed for " + session, eex);
            if (failCount > MAX_FAILURES) {
              LOGGER.severe("setAlive failed " + failCount + " times in sequence -> closing " + session, eex);
              // non-blocking close
              daemon.getExecutorService().submit(createCloseCallable());
              // it is up to the application to re-open it
              daemon.removeAliveTask(session, this);
            }
          }
        }
        catch (InterruptedException iex) {
          // the task repeats anyway
          LOGGER.warning("interrupted -> ignored", iex);
        }
      }
      else {
        // non-blocking close requested
        LOGGER.fine("close requested for {0}", session);
        daemon.getExecutorService().submit(createCloseCallable());
        daemon.removeAliveTask(session, this);
      }
    }
    else {
      LOGGER.info("{0} is closed", session);
      daemon.removeAliveTask(session, this);
    }
  }

  @Override
  public String toString() {
    return "keep alive " + session.getName();
  }


  /**
   * Creates the alive callable.
   *
   * @return the callable
   */
  protected Callable<Boolean> createAliveCallable() {
    return () -> {
      try {
        session.setAlive(true);
        return Boolean.TRUE;
      }
      catch (RuntimeException ex) {
        throw new ExecutionException("set alive failed", ex);
      }
    };
  }

  /**
   * Creates the close callable.
   *
   * @return the callable
   */
  protected Callable<Boolean> createCloseCallable() {
    return () -> {
      try {
        session.close();
        return Boolean.TRUE;
      }
      catch (RuntimeException ex) {
        throw new ExecutionException("close failed", ex);
      }
    };
  }

}
