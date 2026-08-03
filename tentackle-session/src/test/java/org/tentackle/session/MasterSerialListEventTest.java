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

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Tests that a list event drops the events it overrides, whichever way it is filled.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class MasterSerialListEventTest {

  /**
   * A status-like event: only the latest one counts.
   */
  private static class StatusEvent implements MasterSerialEvent {

    private final int id;
    private long serial;

    StatusEvent(int id) {
      this.id = id;
    }

    @Override
    public boolean isOverridingOlderEvents() {
      return true;
    }

    @Override
    public void setSerial(long serial) {
      this.serial = serial;
    }

    @Override
    public long serial() {
      return serial;
    }

    @Override
    public String toString() {
      return "status(" + id + ')';
    }
  }


  /**
   * An ordinary event: every one of them is delivered.
   */
  private static class PlainEvent implements MasterSerialEvent {

    private long serial;

    @Override
    public void setSerial(long serial) {
      this.serial = serial;
    }

    @Override
    public long serial() {
      return serial;
    }
  }


  @Test
  public void testAddDropsOverriddenEvents() {
    MasterSerialListEvent listEvent = new MasterSerialListEvent();
    listEvent.add(new PlainEvent());
    listEvent.add(new StatusEvent(1));
    StatusEvent latest = new StatusEvent(2);
    listEvent.add(latest);

    Assert.assertEquals(listEvent.size(), 2);
    Assert.assertSame(listEvent.get(1), latest);
  }


  @Test
  public void testConstructorDropsOverriddenEvents() {
    StatusEvent latest = new StatusEvent(2);
    MasterSerialListEvent listEvent = new MasterSerialListEvent(
        List.of(new PlainEvent(), new StatusEvent(1), latest));

    Assert.assertEquals(listEvent.size(), 2, "the constructor kept an overridden event");
    Assert.assertSame(listEvent.get(1), latest);
  }


  @Test
  public void testAddAllDropsOverriddenEvents() {
    MasterSerialListEvent listEvent = new MasterSerialListEvent();
    StatusEvent latest = new StatusEvent(2);

    Assert.assertTrue(listEvent.addAll(List.of(new PlainEvent(), new StatusEvent(1), latest)));

    Assert.assertEquals(listEvent.size(), 2, "addAll kept an overridden event");
    Assert.assertSame(listEvent.get(1), latest);
    Assert.assertFalse(listEvent.addAll(List.of()));
  }


  @Test
  public void testPlainEventsAreAllKept() {
    MasterSerialListEvent listEvent = new MasterSerialListEvent(
        List.of(new PlainEvent(), new PlainEvent(), new PlainEvent()));

    Assert.assertEquals(listEvent.size(), 3);
  }


  @Test
  public void testSerialIsCarriedByTheListItself() {
    MasterSerialListEvent listEvent = new MasterSerialListEvent();
    listEvent.setSerial(4711);
    Assert.assertEquals(listEvent.serial(), 4711);
  }

}
