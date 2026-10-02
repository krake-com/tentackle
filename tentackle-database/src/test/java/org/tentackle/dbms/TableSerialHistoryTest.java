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

package org.tentackle.dbms;

import org.testng.annotations.Test;

import org.tentackle.misc.IdSerialTuple;

import java.util.Collection;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * Tests the {@link TableSerialHistory}.
 */
public class TableSerialHistoryTest {

  @Test
  public void testMaxSizeBeforeFirstEntry() {
    TableSerialHistory history = new TableSerialHistory(100);
    assertEquals(history.getMaxSize("t"), 100, "default size for unknown table");

    history.setMaxSize("t", 2);   // configured before anything was memorized
    assertEquals(history.getMaxSize("t"), 2);

    history.memorize("t", 0, 1);
    history.memorize("t", 0, 2);
    history.memorize("t", 0, 3);
    assertEquals(history.getSize("t"), 2, "oldest entry must have been removed");

    Collection<IdSerialTuple> tuples = history.retrieve("t", 0, 10, false);
    assertNotNull(tuples);
    assertEquals(tuples.size(), 2);
    assertEquals(tuples.iterator().next().getSerial(), 2L, "entries sorted by tableserial, oldest removed");
  }

  @Test
  public void testRetrieveAndForget() {
    TableSerialHistory history = new TableSerialHistory();
    history.memorize("t", 4711, -5);    // deletion
    history.memorize("t", 0, 6);        // insert or update

    Collection<IdSerialTuple> tuples = history.retrieve("t", 4, 6, true);
    assertNotNull(tuples);
    assertEquals(tuples.size(), 2);
    assertNull(history.retrieve("t", 4, 6, false), "retrieved entries must be forgotten");
    assertNull(history.retrieve("other", 0, 10, false), "no history for unknown table");
  }

}
