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

package org.tentackle.trip.provider;

import org.testng.annotations.Test;

import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

/**
 * Tests for {@link AbstractTripComponentProvider}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class AbstractTripComponentProviderTest {

  @SuppressWarnings("unused")
  public static class Bean {
    private String first;
    private transient String skipped;
    private int second;
  }

  private static class Provider extends AbstractTripComponentProvider<Bean> {
    Provider(String... fieldNames) {
      super(Bean.class, fieldNames);
    }
  }

  @Test
  public void providesFieldsInDeclarationOrder() {
    TripType<Bean> type = TripFactory.getInstance().getType(Bean.class);
    TripComponent<Bean, ?>[] components = new Provider("second", "first").getComponents(type);
    assertEquals(components.length, 2);
    assertEquals(components[0].getName(), "first");
    assertEquals(components[1].getName(), "second");
  }

  @Test
  public void rejectsUnknownOrTransientFields() {
    TripType<Bean> type = TripFactory.getInstance().getType(Bean.class);
    assertThrows(TripRuntimeException.class, () -> new Provider("first", "nosuchfield").getComponents(type));
    assertThrows(TripRuntimeException.class, () -> new Provider("skipped").getComponents(type));
  }

}
