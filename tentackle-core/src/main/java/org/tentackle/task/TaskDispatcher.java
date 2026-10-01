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

import org.tentackle.daemon.Terminatable;

import java.util.Collection;


/**
 * A dispatcher executing tasks in a serialized manner.
 * <p>
 * Tasks are executed one after another by the dispatcher thread, in the order they were added,
 * unless scheduled for a later time. The dispatcher can be locked by other threads to share resources
 * with the tasks.
 *
 * @author harald
 */
public interface TaskDispatcher extends Terminatable {

  /**
   * Sets the dead interval.
   *
   * @param deadInterval the dead interval in ms, 0 to disable dead check
   */
  void setDeadInterval(long deadInterval);

  /**
   * Gets the dead interval.
   *
   * @return the dead interval in ms, 0 if none
   */
  long getDeadInterval();

  /**
   * Sets the sleep interval.
   *
   * @param sleepInterval the sleep interval in ms, must be &gt; 0
   */
  void setSleepInterval(long sleepInterval);

  /**
   * Gets the sleep interval.
   *
   * @return the sleep interval in ms
   */
  long getSleepInterval();

  /**
   * Sets the timeout interval for {@link #waitForTask(Task)}.<br>
   * The wait interval should be shorter than the dead interval,
   * usually deadInterval / 2.
   *
   * @param waitInterval the wait interval in ms, 0 to disable
   */
  void setWaitInterval(long waitInterval);

  /**
   * Gets the timeout interval for {@link #waitForTask(Task)}.
   *
   * @return the wait interval in ms, 0 if disabled
   */
  long getWaitInterval();

  /**
   * Sets the locking method.
   *
   * @param usingMutexLocking true if mutex locking, else counter locking
   * @throws TaskException if dispatcher is already running
   */
  void setUsingMutexLocking(boolean usingMutexLocking);

  /**
   * Returns the locking method.
   *
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
   * @return true if dispatcher has been started and not terminated yet
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
   * @param listener the task listener
   */
  void addTaskListener(TaskListener listener);

  /**
   * Removes a task listener.
   *
   * @param listener the task listener
   */
  void removeTaskListener(TaskListener listener);

  /**
   * Adds a task to the queue.
   * 
   * @param task the task to add
   * @return true if added, false if already in queue
   */
  boolean addTask(Task task);

  /**
   * Checks whether a task is still pending.<br>
   * Tasks are pending as long as they are not completed.
   *
   * @param task the task
   * @return true if pending, else completed
   */
  boolean isTaskPending(Task task);

  /**
   * Gets the task by its id.
   *
   * @param taskId the task id
   * @return the task, null if completed or no such task
   */
  Task getTask(long taskId);

  /**
   * Gets all tasks.<br>
   * The returned collection is a copy of the internal queue,
   * so modifications to the collection do not affect the queue.
   *
   * @return all tasks, never null
   */
  Collection<Task> getAllTasks();

  /**
   * Returns whether some instance of a task for the given class or any subclass is pending.
   *
   * @param clazz the task class
   * @return true if at least one instance is pending
   */
  boolean isInstanceOfTaskPending(Class<? extends Task> clazz);

  /**
   * Waits for a task to get completed.<br>
   * The wait times out if the dispatcher shows no activity for the {@link #getWaitInterval() wait interval}.
   * A timed out task is removed from the queue.
   *
   * @param task the task to check for completion
   * @throws TaskException if the wait timed out
   */
  void waitForTask(Task task);

  /**
   * Adds a task to the queue and waits for its completion.
   *
   * @param task the task to add
   * @return true if added, false if already in queue (and not waited for)
   * @throws TaskException if the wait timed out
   * @see #waitForTask(Task)
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
   * @see java.util.concurrent.locks.ReentrantReadWriteLock
   */
  TaskDispatcherLock lock(Object key);

  /**
   * Unlocks this thread.<br>
   * Only the thread holding the lock is allowed to unlock, for mutex-based locking.
   * <p>
   * Notice that the same thread may invoke unlock more than once for mutex based locking.
   *
   * @param key the key to unlock
   * @return true if really unlocked, else just decremented the lock count
   * @throws IllegalMonitorStateException if the caller thread is not holding the lock
   * @see java.util.concurrent.locks.ReentrantReadWriteLock
   */
  boolean unlock(Object key);

  /**
   * Returns a diagnostic string.<br>
   * This is an extended version of {@link Object#toString()}.
   *
   * @return the diagnostic string
   */
  String toDiagnosticString();

  /**
   * Sets a timeout to shut down this thread if idle for the given time.
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
