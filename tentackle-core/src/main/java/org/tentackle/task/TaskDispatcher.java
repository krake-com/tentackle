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

import java.util.Collection;
import org.tentackle.daemon.Terminatable;


/**
 * A dispatcher executing tasks in a serialized manner.
 *
 * @author harald
 */
public interface TaskDispatcher extends Terminatable {

  /**
   * Sets the dead interval.
   * <p>
   * @param deadInterval the dead interval, 0 to disable dead check
   */
  void setDeadInterval(long deadInterval);

  /**
   * Gets the dead interval.
   * <p>
   * @return the dead interval, 0 if none
   */
  long getDeadInterval();

  /**
   * Sets the sleep interval.
   * <p>
   * @param sleepInterval the sleep interval, must be > 0
   */
  void setSleepInterval(long sleepInterval);

  /**
   * Gets the sleep interval.
   * <p>
   * @return the sleep interval
   */
  long getSleepInterval();

  /**
   * Sets the locking method.
   * <p>
   * @param usingMutexLocking true if mutex locking, else counter locking
   * <p>
   * @throws TaskException if dispatcher is already running
   */
  void setUsingMutexLocking(boolean usingMutexLocking);

  /**
   * Returns the locking method.
   * <p>
   * @return true if mutex locking, else counter locking
   */
  boolean isUsingMutexLocking();

  /**
   * Starts the dispatcher.
   */
  void start();

  /**
   * Gets the running state.
   *
   * @return true if dispatched has been started and not terminated yet
   */
  boolean isAlive();

  /**
   * Gets the number of queued tasks.
   *
   * @return the number of tasks pending
   */
  int getQueueSize();

  /**
   * Returns whether the task queue is empty.<br>
   * Depending on the implementation this may be significantly faster than getQueueSize() == 0.
   *
   * @return true if queue is empty
   */
  boolean isQueueEmpty();

  /**
   * Adds a task listener.
   *
   * @param listener
   */
  void addTaskListener(TaskListener listener);

  /**
   * Removes a task listener.
   *
   * @param listener
   */
  void removeTaskListener(TaskListener listener);

  /**
   * Adds a task to the queue.
   * @param task the task to add
   * @return true if added, false if already in queue
   */
  boolean addTask(Task task);

  /**
   * Checks whether a tasks is still pending.<br>
   * Tasks are pending as long as they are not completed.
   * @param task the task
   * @return true if pending, else completed
   */
  boolean isTaskPending(Task task);

  /**
   * Gets all tasks.<br>
   * The returned collection is a copy of the internal queue,
   * so modifications to the collection does not affect the queue.
   *
   * @return all tasks, never null
   */
  Collection<Task> getAllTasks();

  /**
   * Returns whether some instance of a task for the given class or any subclass is pending.<br>
   *
   * @param clazz the task class
   * @return true if at least one instance is pending
   */
  boolean isInstanceOfTaskPending(Class<? extends Task> clazz);

  /**
   * Waits for a task to get completed.
   * @param task the task to check for completion
   */
  void waitForTask(Task task);

  /**
   * Adds a task to the queue.
   * @param task the task to add
   * @return true if added, false if already in queue
   */
  boolean addTaskAndWait(Task task);

  /**
   * Removes a task from the queue.
   *
   * @param task the task to remove
   * @return true if removed, false if not in queue
   */
  boolean removeTask(Task task);

  /**
   * Locks this thread.<br>
   * A locked thread is inactive and waits to be unlocked.
   * There is only one lock available.
   * <p>
   * Notice that the same thread may invoke lock more than once for mutex-based locking.
   * <p>
   * For counter based locking it is recommended to use an unlock key
   * because any thread can unlock as opposed to mutex-based locking.
   *
   * @param key optional key to unlock
   * @return the lock if really locked, else just incremented the lock count
   * @throws TaskException if {@link TaskDispatcher} itself attempts to lock
   * @see ReentrantReadWriteLock
   */
  TaskDispatcherLock lock(Object key);

  /**
   * Unlocks this thread.<br>
   * Only the lock holding the lock is allowed to unlock.
   * <p>
   * Notice that the same thread may invoke unlock more than once for mutex based locking.
   *
   * @param key the key to unlock
   * @return true if really unlocked, else just decremented the lock count
   * @throws IllegalMonitorStateException if the caller thread is not holding the lock
   * @see ReentrantReadWriteLock
   */
  boolean unlock(Object key);

  /**
   * Returns a diagnostic string.<br>
   * This is an extended version of {@link #toString()}.
   *
   * @return the disgnostic string
   */
  String toDiagnosticString();

  /**
   * Sets a timeout to shutdown this thread if idle for a given timeout.
   *
   * @param shutdownIdleTimeout idle timeout in milliseconds, 0 if no shutdown
   */
  void setShutdownIdleTimeout(long shutdownIdleTimeout);

  /**
   * Gets the shutdown idle timeout.
   *
   * @return the idle timeout, 0 if no shutdown
   */
  long getShutdownIdleTimeout();

}
