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

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Default task.
 *
 * @author harald
 */
public abstract class AbstractTask implements Task {

  @Serial
  private static final long serialVersionUID = 1L;

  private Throwable cause;                            // an exception thrown in a runnable
  private Serializable result;                        // the result (return value) of the task
  private long started;                               // timestamp when started
  private volatile long completed;                    // timestamp when completed (volatile: publishes the dispatcher's post-run bookkeeping to completion observers)
  private long scheduled;                             // time scheduled for next execution
  private long repeat;                                // repeat interval
  private long id;                                    // the (unique) task id
  private transient TaskDispatcher dispatcher;        // the dispatcher
  private transient List<TaskListener> listeners;     // task listeners


  /**
   * Parent constructor.
   */
  public AbstractTask() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * {@inheritDoc}
   * <p>
   * Tasks are sorted by scheduled time + id.
   *
   * @param   o the object to be compared.
   * @return  a negative integer, zero, or a positive integer as this object
   *          is less than, equal to, or greater than the specified object.
   */
  @Override
  public int compareTo(Task o) {
    int rv = Long.compare(getScheduledEpochalTime(), o.getScheduledEpochalTime());
    if (rv == 0) {
      rv = Long.compare(getId(), o.getId());
    }
    return rv;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final AbstractTask other = (AbstractTask) obj;
    return this.id == other.id;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(id);
  }

  @Override
  public String toString() {
    return super.toString() + '[' + id + ']';
  }

  @Override
  public void setScheduledEpochalTime(long scheduled) {
    this.scheduled = scheduled;
  }

  @Override
  public long getScheduledEpochalTime() {
    return scheduled;
  }

  @Override
  public long getRepeatInterval() {
    return repeat;
  }

  @Override
  public void setRepeatInterval(long repeat) {
    if (repeat <= 0) {
      throw new IllegalArgumentException("interval must be positive: " + repeat);
    }
    this.repeat = repeat;
    this.scheduled = System.currentTimeMillis() + repeat;
  }

  @Override
  public long getId() {
    return id;
  }

  @Override
  public void setId(long id) {
    this.id = id;
  }

  @Override
  public TaskDispatcher getDispatcher() {
    return dispatcher;
  }

  @Override
  public void setDispatcher(TaskDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @Override
  public void setCause(Throwable cause) {
    this.cause = cause;
  }

  @Override
  public Throwable getCause() {
    return cause;
  }

  @Override
  public Serializable getResult() {
    return result;
  }

  @Override
  public void setResult(Serializable result) {
    this.result = result;
  }

  @Override
  public long getCompleted() {
    return completed;
  }

  @Override
  public void setCompleted(long completed) {
    this.completed = completed;
    fireCompleted();
  }

  @Override
  public long getStarted() {
    return started;
  }

  @Override
  public void setStarted(long started) {
    this.started = started;
    fireStarted();
  }

  @Override
  public void addTaskListener(TaskListener listener) {
    synchronized (this) {
      getListeners().add(listener);
    }
  }

  @Override
  public void removeTaskListener(TaskListener listener) {
    synchronized (this) {
      getListeners().remove(listener);
    }
  }

  @Override
  public void fireStarted() {
    if (listeners != null) {
      for (TaskListener listener : listeners) {
        listener.started(this);
      }
    }
  }

  @Override
  public void fireCompleted() {
    if (listeners != null) {
      for (TaskListener listener : listeners) {
        listener.completed(this);
      }
    }
  }

  @Override
  public boolean isInterruptable() {
    return true;
  }


  /**
   * Gets the listeners.
   *
   * @return the listeners, never null
   */
  private List<TaskListener> getListeners() {
    if (listeners == null) {
      listeners = new ArrayList<>();
    }
    return listeners;
  }

}
