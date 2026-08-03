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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A task dispatcher to keep sessions alive.
 *
 * @author harald
 */
public class SessionKeepAliveDaemon extends DefaultTaskDispatcher {

  private static final Logger LOGGER = Logger.get(SessionKeepAliveDaemon.class);

  private static final long SLEEP_INTERVAL = 10000;       // 10 seconds (effective value doesn't really matter)



  private final long minAliveInterval;            // the minimum alive-interval
  private final Map<Session,Task> sessionTaskMap; // the session/task map
  private final ExecutorService executorService;  // background executor service
  private final AtomicInteger threadNumber;       // set alive thread number
  private ThreadGroup threadGroup;                // the thread group


  /**
   * Creates the daemon.<br>
   *
   * @param minAliveInterval the minimum alive-interval in ms
   */
  public SessionKeepAliveDaemon(long minAliveInterval) {
    super("Session Keep Alive Daemon", false, SLEEP_INTERVAL, SLEEP_INTERVAL * 10);
    this.minAliveInterval = minAliveInterval;
    this.sessionTaskMap = new ConcurrentHashMap<>();
    this.executorService = createExecutorService();
    this.threadNumber = new AtomicInteger(1);
    setPriority(MAX_PRIORITY);
  }

  @Override
  protected void cleanup() {
    executorService.shutdown();
    super.cleanup();
  }

  @Override
  public void run() {
    threadGroup = Thread.currentThread().getThreadGroup();
    super.run();
  }

  /**
   * Handles the change of a keep alive-interval of a session.
   *
   * @param session the session
   */
  public void keepAliveIntervalChanged(Session session) {
    long interval = session.getKeepAliveInterval();
    Task task = sessionTaskMap.get(session);
    if (task != null) {
      removeTask(task);
      sessionTaskMap.remove(session);
    }
    if (interval != 0 && session.isOpen()) {
      if (interval > 0 && interval < minAliveInterval) {
        interval = minAliveInterval;
      }
      task = createAliveTask(session, interval);
      sessionTaskMap.put(session, task);
      addTask(task);
      LOGGER.info("added keep-alive={0}ms for {1}", interval, session);
    }
    else  {
      LOGGER.info("removed keep-alive for {0}", session);
    }
  }


  /**
   * Removes the task for the given session.
   *
   * @param session the session
   */
  public void removeAliveTask(Session session) {
    Task task = sessionTaskMap.remove(session);
    if (task != null) {
      removeTask(task);
    }
    LOGGER.info("{0} removed from keep-alive", session.getName());
  }


  /**
   * Gets the executor service.
   *
   * @return the executor service
   */
  public ExecutorService getExecutorService() {
    return executorService;
  }


  /**
   * Creates the executor service.<br>
   *
   * @return the executor service
   */
  protected ExecutorService createExecutorService() {
    return Executors.newCachedThreadPool(runnable -> {
      Thread t = new Thread(threadGroup, runnable, "Keep Alive Thread(" + threadNumber.getAndIncrement() + ")", 0);
      t.setDaemon(true);                    // don't inhibit termination of JVM
      t.setPriority(Thread.MAX_PRIORITY);   // don't miss any keep-alive
      return t;
    });
  }


  /**
   * Creates a keep-alive task.
   *
   * @param session the session
   * @param interval the keep-alive interval in milliseconds
   * @return the created task
   */
  protected Task createAliveTask(Session session, long interval) {
    // (sessionTaskMap.size() + 1) to make sure that even if all tasks time out,
    // the keep-alive interval is still enough to check all tasks.
    return new SessionKeepAliveTask(this, session, interval, (sessionTaskMap.size() + 1));
  }

}
