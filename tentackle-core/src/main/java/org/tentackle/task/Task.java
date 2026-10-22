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

import java.io.Serializable;

/**
 * A task being executed by the {@link TaskDispatcher}.
 *
 * @author harald
 */
public interface Task extends Runnable, Serializable, Comparable<Task> {

  /**
   * Gets the task id.
   *
   * @return the id
   */
  long getId();

  /**
   * Sets the task id.
   *
   * @param id the id
   */
  void setId(long id);

  /**
   * Gets the task dispatcher this task has been added to.
   *
   * @return the dispatcher, null if removed from dispatcher
   */
  TaskDispatcher getDispatcher();

  /**
   * Sets the dispatcher.
   *
   * @param dispatcher the dispatcher
   */
  void setDispatcher(TaskDispatcher dispatcher);

  /**
   * Sets an exception that caused the task to fail.
   *
   * @param cause the exception, null to clear
   */
  void setCause(Throwable cause);

  /**
   * Gets the exception that caused the task to fail.
   *
   * @return the exception, null if none
   */
  Throwable getCause();

  /**
   * Gets the result of the task.
   *
   * @return the result, null if none
   */
  Serializable getResult();

  /**
   * Sets the result of the task.
   *
   * @param result the result
   */
  void setResult(Serializable result);

  /**
   * Gets the completed timestamp in milliseconds.
   *
   * @return time when completed, 0 if not completed
   */
  long getCompleted();

  /**
   * Sets the completed timestamp.<br>
   * Invoked by the dispatcher.
   *
   * @param completed the epochal time in milliseconds
   */
  void setCompleted(long completed);

  /**
   * Gets the started timestamp in milliseconds.
   *
   * @return time when started, 0 if not yet started
   */
  long getStarted();

  /**
   * Sets the started timestamp.<br>
   * Invoked by the dispatcher.
   *
   * @param started the epochal time in milliseconds
   */
  void setStarted(long started);

  /**
   * Adds a task listener.
   *
   * @param listener the listener
   */
  void addTaskListener(TaskListener listener);

  /**
   * Removes a task listener.
   *
   * @param listener the listener
   */
  void removeTaskListener(TaskListener listener);

  /**
   * Invokes {@link TaskListener#started(org.tentackle.task.Task)} for all listeners of this task.
   */
  void fireStarted();

  /**
   * Invokes {@link TaskListener#completed(org.tentackle.task.Task)} for all listeners of this task.
   */
  void fireCompleted();

  /**
   * Returns whether task is safely interruptable.<br>
   * Non-interruptable tasks will always complete their execution
   * and the {@link Thread#interrupt()} will be delayed in {@link TaskDispatcher}.
   *
   * @return true if interruptable
   */
  boolean isInterruptable();

  /**
   * Sets the earliest time to be executed in epochal milliseconds.
   *
   * @param scheduled the scheduled time, &le; current time if immediately
   */
  void setScheduledEpochalTime(long scheduled);

  /**
   * Gets the earliest time to be executed in epochal milliseconds.
   *
   * @return the scheduled time, &le; current time if immediately
   */
  long getScheduledEpochalTime();

  /**
   * Sets the repeat interval in milliseconds.<br>
   * Repeatable tasks are not removed from the queue but get a new
   * scheduled epochal time and are re-queued.
   *
   * @param repeat the repeat interval in milliseconds
   */
  void setRepeatInterval(long repeat);

  /**
   * Gets the repeat interval in milliseconds.
   *
   * @return the repeat interval in milliseconds, 0 if execute only once
   */
  long getRepeatInterval();

}
