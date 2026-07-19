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

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link AbstractTask} base class in isolation (without a dispatcher).
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class AbstractTaskTest {

  /**
   * A minimal concrete task.
   */
  private static class SimpleTask extends AbstractTask {

    @Serial
    private static final long serialVersionUID = 1L;

    private boolean ran;

    @Override
    public void run() {
      ran = true;
    }
  }

  /**
   * A task of a different class, used to check {@link AbstractTask#equals(Object)}.
   */
  private static class OtherTask extends AbstractTask {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public void run() {
      // nothing
    }
  }

  /**
   * Records the fired events.
   */
  private static class RecordingListener implements TaskListener {

    private final List<String> events = new ArrayList<>();

    @Override
    public void started(Task task) {
      events.add("started:" + task.getId());
    }

    @Override
    public void completed(Task task) {
      events.add("completed:" + task.getId());
    }
  }


  private SimpleTask task;

  @BeforeMethod
  public void setUp() {
    task = new SimpleTask();
  }


  @Test
  public void testDefaults() {
    assertEquals(task.getId(), 0L);
    assertNull(task.getDispatcher());
    assertNull(task.getCause());
    assertNull(task.getResult());
    assertEquals(task.getStarted(), 0L);
    assertEquals(task.getCompleted(), 0L);
    assertEquals(task.getScheduledEpochalTime(), 0L);
    assertEquals(task.getRepeatInterval(), 0L);
    assertTrue(task.isInterruptable());
  }

  @Test
  public void testSimpleAccessors() {
    task.setId(42L);
    assertEquals(task.getId(), 42L);

    Throwable cause = new IllegalStateException("boom");
    task.setCause(cause);
    assertSame(task.getCause(), cause);
    task.setCause(null);
    assertNull(task.getCause());

    task.setResult("result");
    assertEquals(task.getResult(), "result");

    task.setScheduledEpochalTime(12345L);
    assertEquals(task.getScheduledEpochalTime(), 12345L);
  }

  @Test
  public void testSetRepeatInterval() {
    long before = System.currentTimeMillis();
    task.setRepeatInterval(1000L);
    long after = System.currentTimeMillis();
    assertEquals(task.getRepeatInterval(), 1000L);
    // scheduled time must have been advanced by the repeat interval
    assertTrue(task.getScheduledEpochalTime() >= before + 1000L);
    assertTrue(task.getScheduledEpochalTime() <= after + 1000L);
  }

  @Test
  public void testSetRepeatIntervalRejectsNonPositive() {
    assertThrows(IllegalArgumentException.class, () -> task.setRepeatInterval(0L));
    assertThrows(IllegalArgumentException.class, () -> task.setRepeatInterval(-1L));
  }

  @Test
  public void testCompareToByScheduledTime() {
    SimpleTask earlier = new SimpleTask();
    earlier.setScheduledEpochalTime(100L);
    SimpleTask later = new SimpleTask();
    later.setScheduledEpochalTime(200L);
    assertTrue(earlier.compareTo(later) < 0);
    assertTrue(later.compareTo(earlier) > 0);
  }

  @Test
  public void testCompareToBreaksTieById() {
    SimpleTask a = new SimpleTask();
    a.setScheduledEpochalTime(100L);
    a.setId(1L);
    SimpleTask b = new SimpleTask();
    b.setScheduledEpochalTime(100L);
    b.setId(2L);
    assertTrue(a.compareTo(b) < 0);
    assertTrue(b.compareTo(a) > 0);
    // equal scheduled time and id
    b.setId(1L);
    assertEquals(a.compareTo(b), 0);
  }

  @Test
  public void testEqualsAndHashCode() {
    SimpleTask a = new SimpleTask();
    a.setId(7L);
    SimpleTask b = new SimpleTask();
    b.setId(7L);
    SimpleTask c = new SimpleTask();
    c.setId(8L);

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(a, c);

    // reflexive
    assertEquals(a, a);
    // null
    assertNotEquals(a, null);
    // different class, same id
    OtherTask other = new OtherTask();
    other.setId(7L);
    assertNotEquals(a, other);
    // different type entirely
    assertNotEquals(a, "not a task");
  }

  @Test
  public void testHashCodeMatchesId() {
    task.setId(123L);
    assertEquals(task.hashCode(), Long.hashCode(123L));
  }

  @Test
  public void testToStringContainsId() {
    task.setId(99L);
    assertTrue(task.toString().endsWith("[99]"), task.toString());
  }

  @Test
  public void testSetStartedFiresStarted() {
    RecordingListener listener = new RecordingListener();
    task.setId(1L);
    task.addTaskListener(listener);
    task.setStarted(555L);
    assertEquals(task.getStarted(), 555L);
    assertEquals(listener.events, List.of("started:1"));
  }

  @Test
  public void testSetCompletedFiresCompleted() {
    RecordingListener listener = new RecordingListener();
    task.setId(2L);
    task.addTaskListener(listener);
    task.setCompleted(777L);
    assertEquals(task.getCompleted(), 777L);
    assertEquals(listener.events, List.of("completed:2"));
  }

  @Test
  public void testRemoveTaskListener() {
    RecordingListener listener = new RecordingListener();
    task.setId(3L);
    task.addTaskListener(listener);
    task.removeTaskListener(listener);
    task.setStarted(1L);
    task.setCompleted(2L);
    assertTrue(listener.events.isEmpty());
  }

  @Test
  public void testMultipleListeners() {
    RecordingListener l1 = new RecordingListener();
    RecordingListener l2 = new RecordingListener();
    task.setId(4L);
    task.addTaskListener(l1);
    task.addTaskListener(l2);
    task.fireStarted();
    task.fireCompleted();
    assertEquals(l1.events, List.of("started:4", "completed:4"));
    assertEquals(l2.events, List.of("started:4", "completed:4"));
  }

  @Test
  public void testFireWithoutListeners() {
    // must not throw when no listener has ever been added
    task.fireStarted();
    task.fireCompleted();
  }

  @Test
  public void testRun() {
    assertFalse(task.ran);
    task.run();
    assertTrue(task.ran);
  }
}
