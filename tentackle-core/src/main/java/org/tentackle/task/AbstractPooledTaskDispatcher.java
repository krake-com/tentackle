/*
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.tentackle.common.TentackleRuntimeException;

/**
 * A task dispatcher using a pool of threads.
 *
 * @author harald
 */
public abstract class AbstractPooledTaskDispatcher implements TaskDispatcher {

  private String name;                                // the dispatcher's basename
  private boolean usingMutexLocking;                  // true if use a mutex lock, else just a counter
  private long sleepInterval;                         // keepalive in ms
  private final List<TaskListener> listeners;         // listeners

  protected final Map<Task,TaskDispatcher> taskMap;   // the pending tasks
  protected final TaskListener taskListener;          // the task listener (to remove executed tasks)


  /**
   * Task listener to fire listeners and cleanup taskmap.
   */
  public class PooledTaskListener implements TaskListener {

    @Override
    public void started(Task task) {
      fireStarted(task);
    }

    @Override
    public void completed(Task task) {
      taskMap.remove(task);
      fireCompleted(task);
    }
  }


  /**
   * Creates a pooled task dispatcher.<br>
   *
   * @param name the pool name
   * @param useMutexLocking true use a mutex for locking, else just a counter
   * @param sleepInterval keep alive in milliseconds
   */
  public AbstractPooledTaskDispatcher(String name, boolean useMutexLocking, long sleepInterval) {

    setName(name);
    setUsingMutexLocking(useMutexLocking);
    setSleepInterval(sleepInterval);

    taskMap = new ConcurrentHashMap<>();
    listeners = new ArrayList<>();
    taskListener = createTaskListener();
  }


  /**
   * Sets the name.
   *
   * @param name the name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the pool name.
   *
   * @return the pool's name.
   */
  public String getName() {
    return name;
  }

  @Override
  public long getSleepInterval() {
    return sleepInterval;
  }

  @Override
  public void setSleepInterval(long sleepInterval) {
    this.sleepInterval = sleepInterval;
  }

  @Override
  public boolean isUsingMutexLocking() {
    return usingMutexLocking;
  }

  @Override
  public void setUsingMutexLocking(boolean usingMutexLocking) {
    this.usingMutexLocking = usingMutexLocking;
  }

  @Override
  public synchronized void addTaskListener(TaskListener listener) {
    listeners.add(listener);
  }

  @Override
  public synchronized void removeTaskListener(TaskListener listener) {
    listeners.remove(listener);
  }

  @Override
  public int getQueueSize() {
    return taskMap.size();
  }

  @Override
  public boolean isQueueEmpty() {
    return taskMap.isEmpty();
  }

  @Override
  public synchronized boolean addTask(Task task) {
    if (taskMap.containsKey(task)) {
      return false;   // task already added
    }
    TaskDispatcher dispatcher = getNextDispatcher();
    if (!dispatcher.addTask(task)) {
      throw new TentackleRuntimeException("dispatcher " + dispatcher + " out of sync");
    }
    taskMap.put(task, dispatcher);
    return true;
  }

  @Override
  public boolean isTaskPending(Task task) {
    TaskDispatcher dispatcher = getDispatcherForTask(task);
    return dispatcher != null && dispatcher.isTaskPending(task);
  }

  @Override
  public Collection<Task> getAllTasks() {
    return new ArrayList<>(taskMap.keySet());
  }

  @Override
  public boolean isInstanceOfTaskPending(Class<? extends Task> clazz) {
    for (Task task: taskMap.keySet()) {
      if (clazz.isAssignableFrom(task.getClass())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void waitForTask(Task task) {
    TaskDispatcher dispatcher = getDispatcherForTask(task);
    if (dispatcher != null) {
      dispatcher.waitForTask(task);
    }
  }

  @Override
  public boolean addTaskAndWait(Task task) {
    if (addTask(task)) {
      waitForTask(task);
      return true;
    }
    return false;
  }

  @Override
  public boolean removeTask(Task task) {
    TaskDispatcher dispatcher = getDispatcherForTask(task);
    return dispatcher != null && dispatcher.removeTask(task);
  }



  /**
   * Creates the task listener.
   *
   * @return the listener for all dispatchers
   */
  protected TaskListener createTaskListener() {
    return new PooledTaskListener();
  }

  /**
   * Invoke {@link TaskListener#started(org.tentackle.task.Task)}
   * for all listeners.
   * @param task the task
   */
  protected void fireStarted(Task task) {
    for (TaskListener listener: listeners) {
      listener.started(task);
    }
  }

  /**
   * Invoke {@link TaskListener#completed(org.tentackle.task.Task)}
   * for all listeners.
   * @param task the task
   */
  protected void fireCompleted(Task task) {
    for (TaskListener listener: listeners) {
      listener.completed(task);
    }
  }

  /**
   * Gets the next dispatcher to add a task to.
   *
   * @return the dispatcher
   */
  protected abstract TaskDispatcher getNextDispatcher();



  /**
   * Gets the task dispatcher for a given task.
   *
   * @param task the task
   * @return the dispatcher, null if no dispatcher for this task
   */
  private TaskDispatcher getDispatcherForTask(Task task) {
    return taskMap.get(task);
  }

}
