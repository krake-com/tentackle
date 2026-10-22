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

package org.tentackle.common;

import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests the database-semantics {@link Date}, {@link Time} and {@link Timestamp} datatypes,
 * covering freezing, cloning and the custom serialization.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class DateTimeTest {

  @SuppressWarnings("unchecked")
  private static <T> T serializeDeserialize(T object) {
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      try (ObjectOutputStream os = new ObjectOutputStream(bos)) {
        os.writeObject(object);
      }
      try (ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
        return (T) is.readObject();
      }
    }
    catch (IOException | ClassNotFoundException ex) {
      throw new RuntimeException(ex);
    }
  }

  // --- Date ---

  @Test
  public void testDateValueOfAndSerialization() {
    Date date = Date.valueOf("2020-03-15");
    assertEquals(date.toString(), "2020-03-15");
    // the date survives a serialization roundtrip unchanged
    Date restored = serializeDeserialize(date);
    assertEquals(restored.toString(), "2020-03-15");
    assertEquals(restored, date);
  }

  @Test
  public void testDateFreeze() {
    Date date = Date.valueOf("2020-03-15");
    assertFalse(date.isFrozen());
    date.freeze();
    assertTrue(date.isFrozen());
    assertThrows(TentackleRuntimeException.class, () -> date.setTime(0L));

    Date frozen = Date.createFrozen(0L);
    assertTrue(frozen.isFrozen());
    // the frozen flag is carried through serialization
    assertTrue(serializeDeserialize(frozen).isFrozen());
  }

  @Test
  public void testDateCloneIsUnfrozen() {
    Date date = Date.valueOf("2020-03-15");
    date.freeze();
    Date clone = date.clone();
    assertEquals(clone, date);
    assertNotSame(clone, date);
    assertFalse(clone.isFrozen());
    clone.setTime(0L);    // must not throw on an unfrozen clone
  }

  // --- Time ---

  @Test
  public void testTimeValueOfAndSerialization() {
    Time time = Time.valueOf("13:45:30");
    assertEquals(time.toString(), "13:45:30");
    Time restored = serializeDeserialize(time);
    assertEquals(restored.toString(), "13:45:30");
    assertEquals(restored, time);
  }

  @Test
  public void testTimeFreeze() {
    Time time = Time.valueOf("13:45:30");
    time.freeze();
    assertTrue(time.isFrozen());
    assertThrows(TentackleRuntimeException.class, () -> time.setTime(0L));
    assertTrue(Time.createFrozen(0L).isFrozen());
    assertFalse(time.clone().isFrozen());
  }

  // --- Timestamp ---

  @Test
  public void testTimestampValueOfAndSerialization() {
    Timestamp ts = Timestamp.valueOf("2020-03-15 13:45:30.123");
    assertEquals(ts.toString(), "2020-03-15 13:45:30.123");
    assertFalse(ts.isUTC());
    Timestamp restored = serializeDeserialize(ts);
    assertEquals(restored, ts);
    assertFalse(restored.isUTC());
  }

  @Test
  public void testTimestampUtcFlagSurvivesSerialization() {
    Timestamp ts = Timestamp.valueOf("2020-03-15 13:45:30.000");
    ts.setUTC(true);
    assertTrue(ts.isUTC());
    Timestamp restored = serializeDeserialize(ts);
    assertTrue(restored.isUTC());
    // a UTC timestamp keeps its wall-clock representation
    assertEquals(restored, ts);
  }

  @Test
  public void testTimestampStaticSetUtcIsNullSafe() {
    Timestamp ts = new Timestamp(0L);
    Timestamp.setUTC(ts, true);
    assertTrue(ts.isUTC());
    // must not throw for a null timestamp
    Timestamp.setUTC(null, true);
  }

  @Test
  public void testTimestampFreeze() {
    Timestamp ts = new Timestamp(0L);
    ts.freeze();
    assertTrue(ts.isFrozen());
    assertThrows(TentackleRuntimeException.class, () -> ts.setTime(1L));
    assertThrows(TentackleRuntimeException.class, () -> ts.setNanos(1));

    Timestamp frozen = Timestamp.createFrozen(0L, 500);
    assertTrue(frozen.isFrozen());
    assertEquals(frozen.getNanos(), 500);
    assertFalse(ts.clone().isFrozen());
  }

  @Test
  public void testTimestampUtcConstructor() {
    assertTrue(new Timestamp(0L, true).isUTC());
    assertFalse(new Timestamp(0L).isUTC());
  }

  @Test
  public void testTimestampValueOfKeepsNanos() {
    Timestamp timestamp = Timestamp.valueOf("2020-01-02 03:04:05.123456789");
    assertEquals(timestamp.getNanos(), 123456789);
    assertEquals(timestamp, java.sql.Timestamp.valueOf("2020-01-02 03:04:05.123456789"));
  }

}
