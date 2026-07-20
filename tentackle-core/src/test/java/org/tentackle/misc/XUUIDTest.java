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

package org.tentackle.misc;

import org.testng.annotations.Test;

import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;

/**
 * Tests for {@link XUUID}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class XUUIDTest {

  private static final UUID UUID_A = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
  private static final UUID UUID_B = UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221100");

  @Test
  public void testToString() {
    XUUID xuuid = new XUUID(42, UUID_A);
    assertEquals(xuuid.toString(), "42-" + UUID_A);
    assertEquals(xuuid.getId(), 42);
  }

  @Test
  public void testValueOfRoundTrip() {
    XUUID xuuid = new XUUID(42, UUID_A);
    XUUID parsed = XUUID.valueOf(xuuid.toString());
    assertEquals(parsed, xuuid);
    assertEquals(parsed.getId(), 42);
    assertEquals(parsed.toString(), xuuid.toString());
  }

  @Test
  public void testValueOfNull() {
    assertNull(XUUID.valueOf(null));
  }

  @Test
  public void testValueOfBlankMapsToNil() {
    assertSame(XUUID.valueOf(""), XUUID.NIL);
    assertSame(XUUID.valueOf("   "), XUUID.NIL);
  }

  @Test
  public void testNil() {
    assertEquals(XUUID.NIL.getId(), 0);
    assertEquals(XUUID.NIL.toString(), "0-" + new UUID(0, 0));
  }

  @Test
  public void testValueOfNegativeId() {
    // a leading dash means index 0, which is not > 0, so it is treated as malformed
    assertThrows(IllegalArgumentException.class, () -> XUUID.valueOf("-5-" + UUID_A));
  }

  @Test
  public void testValueOfMissingDash() {
    assertThrows(IllegalArgumentException.class, () -> XUUID.valueOf("12345"));
  }

  @Test
  public void testValueOfMalformedUuid() {
    assertThrows(IllegalArgumentException.class, () -> XUUID.valueOf("1-not-a-uuid"));
  }

  @Test
  public void testValueOfMalformedId() {
    assertThrows(NumberFormatException.class, () -> XUUID.valueOf("x-" + UUID_A));
  }

  @Test
  public void testEqualsAndHashCode() {
    XUUID a1 = new XUUID(1, UUID_A);
    XUUID a2 = new XUUID(1, UUID_A);
    XUUID differentId = new XUUID(2, UUID_A);
    XUUID differentUuid = new XUUID(1, UUID_B);

    assertEquals(a1, a1);
    assertEquals(a1, a2);
    assertEquals(a1.hashCode(), a2.hashCode());
    assertNotEquals(a1, differentId);
    assertNotEquals(a1, differentUuid);
    assertNotEquals(a1, null);
    assertNotEquals(a1, "1-" + UUID_A);
  }

  @Test
  public void testNullUuidRejected() {
    assertThrows(NullPointerException.class, () -> new XUUID(1, null));
  }

  @Test
  public void testIdentifiableConstructor() {
    Identifiable identifiable = new Identifiable() {
      @Override
      public long getId() {
        return 77;
      }
      @Override
      public String toGenericString() {
        return "Thing[77]";
      }
    };
    XUUID xuuid = new XUUID(identifiable, UUID_A);
    assertEquals(xuuid.getId(), 77);
    assertEquals(xuuid.toString(), "77-" + UUID_A);
    // toGenericString delegates to the identifiable and appends the uuid
    assertEquals(xuuid.toGenericString(), "Thing[77]{" + UUID_A + "}");
    // equals only considers id + uuid, so it matches the plain constructor
    assertEquals(xuuid, new XUUID(77, UUID_A));
  }

  @Test
  public void testGenericStringWithoutIdentifiable() {
    XUUID xuuid = new XUUID(9, UUID_A);
    assertEquals(xuuid.toGenericString(), "[9]{" + UUID_A + "}");
  }

  @Test
  public void testNullIdentifiableRejected() {
    assertThrows(NullPointerException.class, () -> new XUUID(null, UUID_A));
  }

  @Test
  public void testDistinctInstances() {
    assertEquals(new XUUID(1, UUID_A), new XUUID(1, UUID_A));
    assertFalse(new XUUID(1, UUID_A).equals(XUUID.NIL));
  }
}
