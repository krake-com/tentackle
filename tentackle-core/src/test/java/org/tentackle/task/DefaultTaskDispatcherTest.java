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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the task-execution, queue-management and life-cycle behavior of the
 * {@link DefaultTaskDispatcher} (as opposed to {@link TaskDispatcherTest},
 * which stress-tests the locking).
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultTaskDispatcherTest {

  private static final long SLEEP_INTERVAL = 100;     // in ms
  private static final long AWAIT_TIMEOUT = 5000;     // in ms

  private DefaultTaskDispatcher dispatcher;


  @AfterMethod
  private void tearDown() {
    if (dispatcher != null && !dispatcher.isKilled() && dispatcher.isAlive()) {
      dispatcher.terminate();
    }
    dispatcher = null;
  }


  /**
   * A task recording its execution, optionally failing or returning a result.
   */
  private static class RecordingTask extends AbstractTask {

    @Serial
    private static final long serialVersionUID = 1L;

    private final AtomicInteger runCount = new AtomicInteger();
    private final RuntimeException toThrow;
    private final java.io.Serializable resultToSet;

    RecordingTask() {
      this(null, null);
    }

    RecordingTask(RuntimeException toThrow, java.io.Serializable resultToSet) {
      this.toThrow = toThrow;
      this.resultToSet = resultToSet;
    }

    @Override
    public void run() {
      runCount.incrementAndGet();
      if (resultToSet != null) {
        setResult(resultToSet);
      }
      if (toThrow != null) {
        throw toThrow;
      }
    }
  }


  private DefaultTaskDispatcher startDispatcher(String name) {
    dispatcher = new DefaultTaskDispatcher(name, false, SLEEP_INTERVAL, 0);
    dispatcher.start();
    return dispatcher;
  }

  private static void await(BooleanSupplier condition) {
    long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT;
    while (System.currentTimeMillis() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      try {
        Thread.sleep(5);
      }
      catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        fail("interrupted while awaiting condition");
      }
    }
    fail("condition not met within " + AWAIT_TIMEOUT + "ms");
  }


  // --- configuration / validation -------------------------------------------

  @Test
  public void testConstructorAssignsName() {
    dispatcher = new DefaultTaskDispatcher("named", false, SLEEP_INTERVAL, 0);
    assertTrue(dispatcher.getName().startsWith("named("), dispatcher.getName());
    assertTrue(dispatcher.isDaemon());
    assertFalse(dispatcher.isUsingMutexLocking());
  }

  @Test
  public void testSleepIntervalMustBePositive() {
    dispatcher = new DefaultTaskDispatcher("cfg", false, SLEEP_INTERVAL, 0);
    assertThrows(IllegalArgumentException.class, () -> dispatcher.setSleepInterval(0));
    assertThrows(IllegalArgumentException.class, () -> dispatcher.setSleepInterval(-1));
  }

  @Test
  public void testDeadIntervalMustNotBeNegative() {
    dispatcher = new DefaultTaskDispatcher("cfg", false, SLEEP_INTERVAL, 0);
    assertThrows(IllegalArgumentException.class, () -> dispatcher.setDeadInterval(-1));
  }

  @Test
  public void testDeadIntervalMustExceedSleepInterval() {
    dispatcher = new DefaultTaskDispatcher("cfg", false, 100, 0);
    // dead interval <= sleep interval is invalid
    assertThrows(IllegalArgumentException.class, () -> dispatcher.setDeadInterval(100));
    assertThrows(IllegalArgumentException.class, () -> dispatcher.setDeadInterval(50));
    // greater is fine
    dispatcher.setDeadInterval(200);
    assertEquals(dispatcher.getDeadInterval(), 200);
  }

  @Test
  public void testConstructorRejectsDeadIntervalBelowSleepInterval() {
    assertThrows(IllegalArgumentException.class,
                 () -> new DefaultTaskDispatcher("cfg", false, 100, 50));
  }

  @Test
  public void testIntervalAccessors() {
    dispatcher = new DefaultTaskDispatcher("cfg", false, SLEEP_INTERVAL, 0);
    dispatcher.setSleepInterval(30);
    assertEquals(dispatcher.getSleepInterval(), 30);
    dispatcher.setWaitInterval(15);
    assertEquals(dispatcher.getWaitInterval(), 15);
    dispatcher.setShutdownIdleTimeout(1234);
    assertEquals(dispatcher.getShutdownIdleTimeout(), 1234);
  }

  @Test
  public void testSetUsingMutexLockingWhileAliveThrows() {
    startDispatcher("mutex-switch");
    await(dispatcher::isAlive);
    assertThrows(TaskException.class, () -> dispatcher.setUsingMutexLocking(true));
  }

  @Test
  public void testSetUsingMutexLockingBeforeStart() {
    dispatcher = new DefaultTaskDispatcher("mutex", false, SLEEP_INTERVAL, 0);
    dispatcher.setUsingMutexLocking(true);
    assertTrue(dispatcher.isUsingMutexLocking());
    dispatcher.setUsingMutexLocking(false);
    assertFalse(dispatcher.isUsingMutexLocking());
  }

  @Test
  public void testToDiagnosticString() {
    dispatcher = new DefaultTaskDispatcher("diag", false, SLEEP_INTERVAL, 0);
    String s = dispatcher.toDiagnosticString();
    assertNotNull(s);
    assertTrue(s.contains("diag("), s);
    assertTrue(s.contains("sleepInterval="), s);
  }


  // --- queue management (without executing) ---------------------------------

  @Test
  public void testAddTaskAssignsIdAndQueues() {
    dispatcher = new DefaultTaskDispatcher("queue", false, SLEEP_INTERVAL, 0);
    assertTrue(dispatcher.isQueueEmpty());
    RecordingTask task = new RecordingTask();
    assertTrue(dispatcher.addTask(task));
    assertTrue(task.getId() != 0);
    assertSame(task.getDispatcher(), dispatcher);
    assertEquals(dispatcher.getQueueSize(), 1);
    assertFalse(dispatcher.isQueueEmpty());
    assertTrue(dispatcher.isTaskPending(task));
    assertSame(dispatcher.getTask(task.getId()), task);
  }

  @Test
  public void testAddTaskTwiceReturnsFalse() {
    dispatcher = new DefaultTaskDispatcher("queue", false, SLEEP_INTERVAL, 0);
    RecordingTask task = new RecordingTask();
    assertTrue(dispatcher.addTask(task));
    assertFalse(dispatcher.addTask(task));
    assertEquals(dispatcher.getQueueSize(), 1);
  }

  @Test
  public void testAddTaskBelongingToOtherDispatcherThrows() {
    dispatcher = new DefaultTaskDispatcher("queue", false, SLEEP_INTERVAL, 0);
    DefaultTaskDispatcher other = new DefaultTaskDispatcher("other", false, SLEEP_INTERVAL, 0);
    RecordingTask task = new RecordingTask();
    assertTrue(other.addTask(task));
    assertThrows(TaskException.class, () -> dispatcher.addTask(task));
  }

  @Test
  public void testGetAllTasksIsCopy() {
    dispatcher = new DefaultTaskDispatcher("queue", false, SLEEP_INTERVAL, 0);
    RecordingTask t1 = new RecordingTask();
    RecordingTask t2 = new RecordingTask();
    dispatcher.addTask(t1);
    dispatcher.addTask(t2);
    Collection<Task> all = dispatcher.getAllTasks();
    assertEquals(all.size(), 2);
    assertTrue(all.contains(t1));
    assertTrue(all.contains(t2));
    // modifying the returned copy must not affect the dispatcher
    all.clear();
    assertEquals(dispatcher.getQueueSize(), 2);
  }

  @Test
  public void testIsInstanceOfTaskPending() {
    dispatcher = new DefaultTaskDispatcher("queue", false, SLEEP_INTERVAL, 0);
    assertFalse(dispatcher.isInstanceOfTaskPending(RecordingTask.class));
    dispatcher.addTask(new RecordingTask());
    assertTrue(dispatcher.isInstanceOfTaskPending(RecordingTask.class));
    assertTrue(dispatcher.isInstanceOfTaskPending(AbstractTask.class));
  }

  @Test
  public void testRemoveTask() {
    dispatcher = new DefaultTaskDispatcher("queue", false, SLEEP_INTERVAL, 0);
    RecordingTask task = new RecordingTask();
    dispatcher.addTask(task);
    assertTrue(dispatcher.removeTask(task));
    assertFalse(dispatcher.isTaskPending(task));
    assertNull(task.getDispatcher());
    assertEquals(dispatcher.getQueueSize(), 0);
    // removing again returns false
    assertFalse(dispatcher.removeTask(task));
  }


  // --- execution ------------------------------------------------------------

  @Test
  public void testTaskGetsExecuted() {
    startDispatcher("exec");
    RecordingTask task = new RecordingTask();
    dispatcher.addTask(task);
    await(() -> task.getCompleted() != 0);
    assertEquals(task.runCount.get(), 1);
    assertTrue(task.getStarted() != 0);
    assertTrue(task.getCompleted() >= task.getStarted());
    assertNull(task.getCause());
    // completed tasks are removed from the id map
    assertNull(dispatcher.getTask(task.getId()));
  }

  @Test
  public void testTaskResultIsPreserved() {
    startDispatcher("exec");
    RecordingTask task = new RecordingTask(null, new Result("done"));
    dispatcher.addTask(task);
    await(() -> task.getCompleted() != 0);
    assertEquals(task.getResult(), new Result("done"));
    assertNull(task.getCause());
  }

  @Test
  public void testTaskExceptionIsCaptured() {
    startDispatcher("exec");
    RuntimeException boom = new IllegalStateException("boom");
    RecordingTask task = new RecordingTask(boom, null);
    dispatcher.addTask(task);
    await(() -> task.getCompleted() != 0);
    assertSame(task.getCause(), boom);
  }

  @Test
  public void testAddTaskAndWait() {
    startDispatcher("wait");
    RecordingTask task = new RecordingTask();
    assertTrue(dispatcher.addTaskAndWait(task));
    assertEquals(task.runCount.get(), 1);
    assertTrue(task.getCompleted() != 0);
  }

  @Test
  public void testWaitForTask() {
    startDispatcher("wait");
    RecordingTask task = new RecordingTask();
    dispatcher.addTask(task);
    dispatcher.waitForTask(task);
    assertTrue(task.getCompleted() != 0);
  }

  @Test
  public void testDispatcherListenerFired() {
    startDispatcher("listener");
    List<String> events = new CopyOnWriteArrayList<>();
    TaskListener listener = new TaskListener() {
      @Override
      public void started(Task task) {
        events.add("started");
      }
      @Override
      public void completed(Task task) {
        events.add("completed");
      }
    };
    dispatcher.addTaskListener(listener);
    RecordingTask task = new RecordingTask();
    dispatcher.addTask(task);
    await(() -> events.contains("completed"));
    assertTrue(events.contains("started"));
    assertTrue(events.contains("completed"));

    dispatcher.removeTaskListener(listener);
    events.clear();
    RecordingTask task2 = new RecordingTask();
    dispatcher.addTaskAndWait(task2);
    assertTrue(events.isEmpty());
  }

  @Test
  public void testScheduledTaskDelayed() {
    startDispatcher("scheduled");
    RecordingTask task = new RecordingTask();
    task.setScheduledEpochalTime(System.currentTimeMillis() + 400);
    dispatcher.addTask(task);
    // shortly after adding it must not have run yet
    try {
      Thread.sleep(100);
    }
    catch (InterruptedException ex) {
      fail("interrupted");
    }
    assertEquals(task.runCount.get(), 0);
    // eventually it runs
    await(() -> task.getCompleted() != 0);
    assertEquals(task.runCount.get(), 1);
  }

  @Test
  public void testRepeatingTask() {
    startDispatcher("repeat");
    RecordingTask task = new RecordingTask();
    task.setRepeatInterval(50);
    dispatcher.addTask(task);
    await(() -> task.runCount.get() >= 3);
    // repeating tasks stay registered
    assertSame(dispatcher.getTask(task.getId()), task);
    assertTrue(dispatcher.removeTask(task));
    int countAfterRemoval = task.runCount.get();
    try {
      Thread.sleep(200);
    }
    catch (InterruptedException ex) {
      fail("interrupted");
    }
    // must not run appreciably more after removal
    assertTrue(task.runCount.get() <= countAfterRemoval + 1,
               "expected no further executions after removal");
  }

  @Test
  public void testDispatcherCannotLockItself() {
    startDispatcher("self-lock");
    CopyOnWriteArrayList<Throwable> captured = new CopyOnWriteArrayList<>();
    RecordingTask task = new RecordingTask() {
      @Serial
      private static final long serialVersionUID = 1L;
      @Override
      public void run() {
        super.run();
        try {
          dispatcher.lock("key");
        }
        catch (RuntimeException ex) {
          captured.add(ex);
        }
      }
    };
    dispatcher.addTask(task);
    await(() -> task.getCompleted() != 0);
    assertEquals(captured.size(), 1);
    assertTrue(captured.getFirst() instanceof TaskException);
  }


  // --- dead detection & kill ------------------------------------------------

  @Test
  public void testDeadDetection() {
    dispatcher = new DefaultTaskDispatcher("dead", false, 100, 1000);
    // not marked dead initially (no activity recorded yet)
    assertFalse(dispatcher.isDead());
    dispatcher.setDead(true);
    assertTrue(dispatcher.isDead());
    dispatcher.setDead(false);
    assertFalse(dispatcher.isDead());
  }

  @Test
  public void testSetDeadWithoutDeadIntervalThrows() {
    dispatcher = new DefaultTaskDispatcher("dead", false, SLEEP_INTERVAL, 0);
    assertFalse(dispatcher.isDead());
    assertThrows(TaskException.class, () -> dispatcher.setDead(true));
  }

  @Test
  public void testKill() {
    startDispatcher("kill");
    await(dispatcher::isAlive);
    dispatcher.kill();
    assertTrue(dispatcher.isKilled());
    // most operations reject a killed dispatcher
    assertThrows(TaskException.class, () -> dispatcher.addTask(new RecordingTask()));
    assertThrows(TaskException.class, () -> dispatcher.getTask(1));
    assertThrows(TaskException.class, dispatcher::getQueueSize);
    TaskListener noopListener = new TaskListener() {
      @Override
      public void started(Task task) {
      }
      @Override
      public void completed(Task task) {
      }
    };
    assertThrows(TaskException.class, () -> dispatcher.addTaskListener(noopListener));
  }

  @Test
  public void testTerminate() {
    startDispatcher("terminate");
    await(dispatcher::isAlive);
    assertFalse(dispatcher.isTerminationRequested());
    dispatcher.terminate();
    assertFalse(dispatcher.isAlive());
    assertTrue(dispatcher.isTerminationRequested());
    assertTrue(dispatcher.terminatedAt() >= dispatcher.startedAt());
  }


  /**
   * A serializable result value.
   */
  private record Result(String value) implements java.io.Serializable {
  }
}
