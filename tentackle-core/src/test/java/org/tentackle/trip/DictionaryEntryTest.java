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

package org.tentackle.trip;

import org.testng.annotations.Test;

import org.tentackle.trip.impl.TestClass;

import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

@SuppressWarnings("missing-explicit-ctor")
public class DictionaryEntryTest {

  private DictionaryEntry createEntry() {
    Dictionary dictionary = TripFactory.getInstance().createDictionary(UUID.randomUUID(), true);
    return dictionary.createEntry(TestClass.class);
  }

  @Test
  void resolvesTheComponentsOfTheType() {
    DictionaryEntry entry = createEntry();
    assertTrue(entry.getComponents().length > 0);
    for (int ordinal = 0; ordinal < entry.getComponents().length; ordinal++) {
      assertNotNull(entry.getComponent(ordinal));
      // the sender's and this side's class are the same here, so all components resolve
      assertNotNull(entry.getTypeComponent(ordinal));
      assertEquals(entry.getTypeComponent(ordinal).getName(), entry.getComponent(ordinal).getComponentName());
    }
  }

  /**
   * The component ordinals are read from the stream, so a corrupted one must be reported
   * as a {@link TripRuntimeException} rather than escaping as an {@code IndexOutOfBoundsException}.
   */
  @Test
  void rejectsComponentOrdinalsOutOfRange() {
    DictionaryEntry entry = createEntry();
    int tooLarge = entry.getComponents().length;

    for (int ordinal : new int[] { -1, Integer.MIN_VALUE, tooLarge, Integer.MAX_VALUE }) {
      TripRuntimeException tx = expectThrows(TripRuntimeException.class, () -> entry.getTypeComponent(ordinal));
      assertTrue(tx.getMessage().contains("out of range"), tx.getMessage());
      // the sibling behaves the same way
      tx = expectThrows(TripRuntimeException.class, () -> entry.getComponent(ordinal));
      assertTrue(tx.getMessage().contains("out of range"), tx.getMessage());
    }
  }

}
