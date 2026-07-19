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

package org.tentackle.pdo;

import org.testng.annotations.Test;

import org.tentackle.common.Timestamp;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for the {@link TokenLockInfo} record.
 */
@SuppressWarnings("missing-explicit-ctor")
public class TokenLockInfoTest {

  @Test
  public void testAccessors() {
    Timestamp since = new Timestamp(1_000_000L);
    Timestamp expiry = new Timestamp(2_000_000L);
    TokenLockInfo info = new TokenLockInfo(42L, since, expiry);
    assertEquals(info.editedBy(), 42L);
    assertEquals(info.editedSince(), since);
    assertEquals(info.editedExpiry(), expiry);
  }

  @Test
  public void testRecordEquality() {
    Timestamp since = new Timestamp(1_000_000L);
    Timestamp expiry = new Timestamp(2_000_000L);
    assertEquals(new TokenLockInfo(42L, since, expiry), new TokenLockInfo(42L, since, expiry));
  }

  @Test
  public void testToString() {
    TokenLockInfo info = new TokenLockInfo(42L, new Timestamp(1_000_000L), new Timestamp(2_000_000L));
    String s = info.toString();
    assertTrue(s.startsWith("editedBy=42"), s);
    assertTrue(s.contains("since="), s);
    assertTrue(s.contains("expiry="), s);
  }

}
