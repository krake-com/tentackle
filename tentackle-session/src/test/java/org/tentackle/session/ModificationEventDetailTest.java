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

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertThrows;

/**
 * Unit tests for {@link ModificationEventDetail}.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class ModificationEventDetailTest {

  @Test
  public void testAccessors() {
    ModificationEventDetail detail = new ModificationEventDetail("mytable", 42);
    assertEquals(detail.getName(), "mytable");
    assertEquals(detail.getSerial(), 42);
  }

  @Test
  public void testToString() {
    assertEquals(new ModificationEventDetail("mytable", 42).toString(), "mytable:42");
  }

  /**
   * Details are identified by their name alone, so that a {@link ModificationEvent} holds at most
   * one detail per name, no matter which serial it was seen with.
   */
  @Test
  public void testEqualityIsDecidedByTheNameAlone() {
    ModificationEventDetail detail = new ModificationEventDetail("mytable", 42);
    assertEquals(detail, new ModificationEventDetail("mytable", 4711));
    assertEquals(detail.hashCode(), new ModificationEventDetail("mytable", 4711).hashCode());
    assertNotEquals(detail, new ModificationEventDetail("othertable", 42));
    assertNotEquals(detail, null);
    assertNotEquals(detail, "mytable");
  }

  @Test
  public void testNamelessDetailsAreRefused() {
    assertThrows(NullPointerException.class, () -> new ModificationEventDetail(null, 42));
    assertThrows(IllegalArgumentException.class, () -> new ModificationEventDetail("", 42));
  }

}
