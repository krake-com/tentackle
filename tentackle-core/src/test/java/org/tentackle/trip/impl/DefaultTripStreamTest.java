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

package org.tentackle.trip.impl;

import org.testng.annotations.Test;

import org.tentackle.trip.DictionaryEntry;
import org.tentackle.trip.Reference;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests for {@link DefaultTripStream}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultTripStreamTest {

  private static DefaultTripStream createStream(DefaultDictionary dictionary) {
    return new DefaultTripStream(dictionary,
                                 new DefaultSerializer(new ByteArrayOutputStream()),
                                 new DefaultDeserializer(new ByteArrayInputStream(new byte[0])));
  }

  /**
   * A stream caches client-only entries, but must switch to the server's entry once it replaced them.
   */
  @Test
  public void cachedClientEntryIsReplacedByServerEntry() {
    DefaultDictionary dictionary = new DefaultDictionary(UUID.randomUUID(), false);
    DefaultTripStream stream = createStream(dictionary);
    DictionaryEntry clientEntry = dictionary.createTemporaryEntry(TestClass.class);
    dictionary.addEntry(clientEntry);
    assertSame(stream.getDictionaryEntry(TestClass.class), clientEntry);
    assertSame(stream.getDictionaryEntry(TestClass.class), clientEntry);    // from the cache

    DictionaryEntry serverEntry = new DefaultDictionary(UUID.randomUUID(), true).createEntry(TestClass.class);
    dictionary.addEntry(serverEntry);
    assertSame(stream.getDictionaryEntry(TestClass.class), serverEntry);
    assertSame(stream.getDictionaryEntry(TestClass.class), serverEntry);
  }

  /**
   * Whether the reference array is recycled or replaced, a cleared stream must start over.
   */
  @Test
  public void clearResetsReferences() {
    DefaultTripStream stream = createStream(new DefaultDictionary(UUID.randomUUID(), true));
    for (int count : new int[] { 0, 1, 10, 256, 1000, 3 }) {
      stream.clear();
      for (int i = 0; i < count; i++) {
        Object object = new Object();
        Reference<Object> reference = stream.getReference(object, false);
        assertEquals(reference.getReferenceId(), i);
        assertSame(stream.getReference(object, false), reference);
        assertSame(stream.getReference(Object.class, i), reference);
      }
      String deduplicated = "deduplicated-" + count;
      assertEquals(stream.getReference(deduplicated, true).getReferenceId(), count);
      stream.clear();
      expectThrows(RuntimeException.class, () -> stream.getReference(Object.class, 0));
      assertEquals(stream.getReference(deduplicated, true).getReferenceId(), 0);
      assertEquals(stream.getReference(new Object(), false).getReferenceId(), 1);
    }
    assertTrue(stream.getClassCount().size() <= 2);
  }

}
