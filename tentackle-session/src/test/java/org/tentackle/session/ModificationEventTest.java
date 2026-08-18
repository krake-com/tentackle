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

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tests the modification event.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class ModificationEventTest {

  @Test
  public void testMasterEventProvidesNoDetails() {
    ModificationEvent event = new ModificationEvent(session(), 42);

    Assert.assertTrue(event.isMasterEvent());
    Assert.assertFalse(event.isSingleEvent());
    Assert.assertFalse(event.isMultiEvent());
    Assert.assertEquals(event.getSerial(), 42);
    Assert.assertNull(event.getName());

    // a listener must be able to iterate the details of any event, master or not
    Assert.assertNotNull(event.getDetails());
    Assert.assertTrue(event.getDetails().isEmpty());
    for (ModificationEventDetail detail: event.getDetails()) {
      Assert.fail("unexpected detail " + detail);
    }
    Assert.assertNull(event.getDetail("whatever"));
    Assert.assertNotNull(event.toString());
  }


  @Test
  public void testSingleNamedEvent() {
    ModificationEvent event = new ModificationEvent(session(), List.of(new ModificationEventDetail("foo", 7)));

    Assert.assertFalse(event.isMasterEvent());
    Assert.assertTrue(event.isSingleEvent());
    Assert.assertFalse(event.isMultiEvent());
    Assert.assertEquals(event.getName(), "foo");
    Assert.assertEquals(event.getSerial(), 7);
    Assert.assertEquals(event.getDetails().size(), 1);
    Assert.assertEquals(event.getDetail("foo").getSerial(), 7);
    Assert.assertNull(event.getDetail("bar"));
  }


  @Test
  public void testMultiNamedEventKeepsAllDetails() {
    List<ModificationEventDetail> details = new ArrayList<>();
    details.add(new ModificationEventDetail("foo", 7));
    details.add(new ModificationEventDetail("bar", 8));
    details.add(new ModificationEventDetail("baz", 9));
    ModificationEvent event = new ModificationEvent(session(), details);

    Assert.assertTrue(event.isMultiEvent());
    Assert.assertEquals(event.getDetails().size(), 3);
    Assert.assertEquals(event.getName(), "foo");    // the first one
    Assert.assertEquals(event.getSerial(), 7);
    Assert.assertEquals(event.getDetail("baz").getSerial(), 9);
  }


  @Test
  public void testDetailsAreNotSilentlyTruncated() {
    // a null in the middle used to end the loop, dropping everything behind it
    List<ModificationEventDetail> details = Arrays.asList(
        new ModificationEventDetail("foo", 7), null, new ModificationEventDetail("baz", 9));

    Assert.assertThrows(NullPointerException.class, () -> new ModificationEvent(session(), details));
  }


  @Test
  public void testMissingDetailsAreRefused() {
    Assert.assertThrows(IllegalArgumentException.class, () -> new ModificationEvent(session(), List.of()));
    Assert.assertThrows(IllegalArgumentException.class, () -> new ModificationEvent(session(), null));
  }


  /**
   * Creates a session that is never actually used by the event.
   *
   * @return the session
   */
  private Session session() {
    return (Session) Proxy.newProxyInstance(
        Session.class.getClassLoader(), new Class<?>[] { Session.class },
        (proxy, method, args) -> switch (method.getName()) {
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "stub session";
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }

}
