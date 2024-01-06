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

import org.tentackle.common.InterruptedRuntimeException;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Executes runnables on a pool of sessions.<br>
 * Useful to run background tasks in parallel, each task with its own session.
 */
public class SessionPooledExecutor {

  private static final Logger LOGGER = Logger.get(SessionPooledExecutor.class);

  private final SessionPool sessionPool;
  private final ThreadGroup threadGroup;
  private final ExecutorService executorService;
  private final AtomicInteger theadNumber = new AtomicInteger(1);

  /**
   * Creates the executor service for a session pool.
   *
   * @param sessionPool the session pool
   */
  public SessionPooledExecutor(SessionPool sessionPool) {
    this.sessionPool = sessionPool;
    executorService = createExecutorService();
    threadGroup = createThreadGroup();
  }

  /**
   * Gets the session pool.
   *
   * @return the pool
   */
  public SessionPool getSessionPool() {
    return sessionPool;
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
   * Gets the thread group for the thread pool.
   *
   * @return the thread group
   */
  public ThreadGroup getThreadGroup() {
    return threadGroup;
  }

  /**
   * Gets the size of the thread pool.<br>
   * The default implementation returns the maximum size of the session pool.<br>
   * If the session pool is unlimited, the thread pool will be unlimited too.<br>
   * Override this method, if sizes differ.<br>
   * Caution: using an unlimited or larger thread pool with a limited or smaller session pool will
   * cause an exception when all sessions are in use!
   *
   * @return the number of threads or unlimited dynamic size if &le; 0
   * @see #createExecutorService()
   */
  public int getThreadPoolSize() {
    return sessionPool.getMaxSize();
  }

  /**
   * Shuts down the executor service and the pool.
   */
  public void shutdown() {
    executorService.shutdown();
    sessionPool.shutdown();
  }

  /**
   * Submits a task for execution in background by a thread pool.
   *
   * @param task the task to execute
   * @param successHandler the handler to be invoked when the task finished successfully, null if none
   * @param failHandler the handler to be invoked when the task failed with an exception, null if none
   * @param <V> the type returned by the task
   */
  public <V> void submit(Supplier<V> task, Consumer<V> successHandler, Consumer<RuntimeException> failHandler) {
    executorService.submit(() -> {
      Session session = null;
      RuntimeException failEx = null;
      try {
        session = sessionPool.getSession();
        session.makeCurrent();
        if (task instanceof SessionDependable) {
          ((SessionDependable) task).setSession(session);
        }
        V value = task.get();
        if (successHandler != null) {
          successHandler.accept(value);
        }
      }
      catch (RuntimeException rx) {
        failEx = rx;
        // run the fail handler _after_ the session was returned to the pool
      }
      catch (Throwable t) {
        // how??
        if (failHandler == null) {
          LOGGER.severe("execution error", t);    // log this for sure!
          throw t;
        }
        // wrap into runtime exception and pass to failHandler
        failEx = new TentackleRuntimeException(t);
      }
      finally {
        if (session != null) {
          session.clearCurrent();
          sessionPool.putSession(session);
        }
      }

      if (failEx != null) {
        if (failHandler != null) {
          failHandler.accept(failEx);
        }
        else {
          LOGGER.severe("background task failed", failEx);
        }
      }
    });
  }

  /**
   * Submits a collection of tasks for background execution in multiple threads of a thread pool and waits for their termination.<br>
   * If there are failures, the fail handler (if any) will be invoked <em>before</em> the success handler.
   *
   * @param tasks the tasks to execute
   * @param timeoutMillis the timeout in milliseconds, &le; 0 if wait infinitely
   * @param successHandler updater invoked when all runners finished, null if none
   * @param failHandler updater invoked only if execution of some runner failed, null if log only as error
   * @param <V> the type returned by the runners
   */
  public <V> void submit(Collection<Supplier<V>> tasks, long timeoutMillis, Consumer<Map<Supplier<V>, V>> successHandler, Consumer<Map<Supplier<V>, RuntimeException>> failHandler) {
    CountDownLatch latch = new CountDownLatch(tasks.size());
    Map<Supplier<V>, V> results = new ConcurrentHashMap<>();
    Map<Supplier<V>, RuntimeException> failures = new ConcurrentHashMap<>();

    executorService.submit(() -> {
      // wait for all tasks finished
      try {
        if (timeoutMillis <= 0) {
          latch.await();
        }
        else if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {   // 5 minutes should really be enough
          failures.put(() -> null, new PersistenceException("parallel execution timed out"));
        }
        // all finished or timed out
        if (failHandler != null && !failures.isEmpty()) {
          // only invoked if there are failures
          failHandler.accept(failures);
        }
        if (successHandler != null) {
          // always invoked
          successHandler.accept(results);
        }
      }
      catch (InterruptedException ix) {
        InterruptedRuntimeException irx = new InterruptedRuntimeException(ix);
        failures.put(() -> null, irx);
        throw irx;
      }
    });

    for (Supplier<V> task : tasks) {
      submit(task,
             result -> {
               if (result != null) {
                 results.put(task, result);
               }
               latch.countDown();
             },
             failure -> {
               if (failure != null) {
                 failures.put(task, failure);
               }
               latch.countDown();
             });
    }
  }


  /**
   * Creates the thread group of the threads in the pool.
   *
   * @return the thread group
   */
  protected ThreadGroup createThreadGroup() {
    return new ThreadGroup(sessionPool.getName() + " thread pool");
  }

  /**
   * Creates the executor service.<br>
   * For unlimited session pools the thread pool will also be unlimited and
   * the number of threads will be adjusted automatically, i.e. new threads are created when
   * necessary (tasks are not queued) and unused threads are removed when no more used for a certain amount of time.<br>
   * For limited session pools a fixed size thread pool is used and tasks queued, if all threads are in use.
   *
   * @return the executor service
   * @see #getThreadPoolSize()
   */
  protected ExecutorService createExecutorService() {
    int threadPoolSize = getThreadPoolSize();
    return threadPoolSize <= 0 ?
           Executors.newCachedThreadPool(this::createThread) :
           Executors.newFixedThreadPool(threadPoolSize, this::createThread);
  }

  /**
   * Creates a new thread.
   *
   * @param runnable the runnable to run
   * @return the thread
   */
  protected Thread createThread(Runnable runnable) {
    Thread thread = new Thread(threadGroup, runnable, sessionPool.getName() + "(" + theadNumber.getAndIncrement() + ")");
    thread.setDaemon(true);
    return thread;
  }

}
