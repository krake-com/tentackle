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

package org.tentackle.fx.rdc.search;

import org.testng.annotations.Test;

import org.tentackle.fx.rdc.PdoStubs;
import org.tentackle.fx.rdc.PdoStubs.Alpha;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for the non-UI parts of the {@link PdoSearch} controller.
 * <p>
 * The controller is created without loading its FXML, so no JavaFX toolkit is needed.
 */
@SuppressWarnings("missing-explicit-ctor")
public class PdoSearchTest {

  @Test
  public void testFilterViewablePdos() {
    PdoSearch<Alpha> search = new PdoSearch<>();
    Alpha visible1 = PdoStubs.create(Alpha.class);
    Alpha hidden = PdoStubs.createNotViewable(Alpha.class);
    Alpha visible2 = PdoStubs.create(Alpha.class);

    List<Alpha> filtered = search.filterViewablePdos(Arrays.asList(visible1, null, hidden, visible2));
    assertEquals(filtered, List.of(visible1, visible2), "nulls and PDOs not allowed to view must be removed");
  }

  @Test
  public void testFilterNullList() {
    assertTrue(new PdoSearch<Alpha>().filterViewablePdos(null).isEmpty());
  }

  @Test
  public void testNoDataMessage() {
    PdoSearch<Alpha> search = new PdoSearch<>();
    search.setNoDataMessage("nothing found");
    assertEquals(search.getNoDataMessage(), "nothing found");
  }

  @Test
  public void testDoubleClickHandler() {
    PdoSearch<Alpha> search = new PdoSearch<>();
    assertNull(search.getDoubleClickHandler());
    Consumer<Alpha> handler = _ -> {};
    search.setDoubleClickHandler(handler);
    assertSame(search.getDoubleClickHandler(), handler);
  }

}
