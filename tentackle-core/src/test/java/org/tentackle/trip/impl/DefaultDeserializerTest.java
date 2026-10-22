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

import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStreamClosedException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

/**
 * Tests for {@link DefaultDeserializer}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultDeserializerTest {

  /**
   * Strings are decoded straight from the buffer if they fit, and in chunks otherwise.
   * Both must work across buffer refills, with multibyte characters split by the refill.
   */
  @Test
  public void deserializeUtf8() {
    String[] strings = {
      "", "a", "äöü€", "x".repeat(15), "€".repeat(5) + "y", "ü".repeat(40), "long ascii string ".repeat(500)
    };
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    bos.write(42);
    for (String string : strings) {
      bos.writeBytes(string.getBytes(StandardCharsets.UTF_8));
    }
    // a tiny buffer: every string of more than a few characters crosses a refill
    DefaultDeserializer deserializer = new DefaultDeserializer(new byte[16], new ByteArrayInputStream(bos.toByteArray()));
    assertEquals(deserializer.deserializeByte(), 42);
    for (String string : strings) {
      assertEquals(deserializer.deserializeUtf8(string.getBytes(StandardCharsets.UTF_8).length), string);
    }
    expectThrows(TripStreamClosedException.class, deserializer::deserializeByte);
  }

  @Test
  public void deserializeUtf8RejectsNegativeLength() {
    DefaultDeserializer deserializer = new DefaultDeserializer(new ByteArrayInputStream(new byte[0]));
    expectThrows(TripRuntimeException.class, () -> deserializer.deserializeUtf8(-1));
  }

  /**
   * A corrupted length must end up as a closed stream, not in allocating that length.
   */
  @Test
  public void deserializeUtf8WithCorruptedLength() {
    DefaultDeserializer deserializer = new DefaultDeserializer(new ByteArrayInputStream(new byte[100]));
    expectThrows(TripStreamClosedException.class, () -> deserializer.deserializeUtf8(Integer.MAX_VALUE));
  }

}
