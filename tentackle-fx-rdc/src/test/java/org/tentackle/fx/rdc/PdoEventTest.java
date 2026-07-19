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

package org.tentackle.fx.rdc;

import org.testng.annotations.Test;

import javafx.event.Event;
import javafx.event.EventType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

/**
 * Unit tests for the {@link PdoEvent} event-type taxonomy.
 */
@SuppressWarnings("missing-explicit-ctor")
public class PdoEventTest {

  @Test
  public void testAnyIsRootedInEventAny() {
    assertEquals(PdoEvent.ANY.getName(), "PDO");
    assertSame(PdoEvent.ANY.getSuperType(), Event.ANY);
  }

  @Test
  public void testCrudTypesDeriveFromAny() {
    for (EventType<PdoEvent> type : java.util.List.of(
        PdoEvent.CREATE, PdoEvent.READ, PdoEvent.UPDATE, PdoEvent.DELETE)) {
      assertSame(type.getSuperType(), PdoEvent.ANY, type + " must derive from PdoEvent.ANY");
    }
  }

  @Test
  public void testCrudTypeNames() {
    assertEquals(PdoEvent.CREATE.getName(), "CREATE");
    assertEquals(PdoEvent.READ.getName(), "READ");
    assertEquals(PdoEvent.UPDATE.getName(), "UPDATE");
    assertEquals(PdoEvent.DELETE.getName(), "DELETE");
  }

  @Test
  public void testCrudTypesAreDistinct() {
    java.util.Set<EventType<PdoEvent>> types = new java.util.HashSet<>(java.util.List.of(
        PdoEvent.ANY, PdoEvent.CREATE, PdoEvent.READ, PdoEvent.UPDATE, PdoEvent.DELETE));
    assertEquals(types.size(), 5, "all event types must be distinct");
  }

}
