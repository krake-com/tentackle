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

package org.tentackle.session;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

/**
 * Unit tests for {@link TransactionIsolation}.
 * <p>
 * The levels mirror the constants of {@code java.sql.Connection}, which the session module
 * deliberately does not depend on, so the mapping has to be kept in sync by hand.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class TransactionIsolationTest {

  @Test
  public void testLevels() {
    assertEquals(TransactionIsolation.DEFAULT.getLevel(), -1);
    assertEquals(TransactionIsolation.NONE.getLevel(), 0);
    assertEquals(TransactionIsolation.READ_UNCOMMITTED.getLevel(), 1);
    assertEquals(TransactionIsolation.READ_COMMITTED.getLevel(), 2);
    assertEquals(TransactionIsolation.REPEATABLE_READ.getLevel(), 4);
    assertEquals(TransactionIsolation.SERIALIZABLE.getLevel(), 8);
  }

  @Test
  public void testEveryLevelMapsBack() {
    for (TransactionIsolation isolation : TransactionIsolation.values()) {
      assertSame(TransactionIsolation.valueOf(isolation.getLevel()), isolation);
    }
  }

  /**
   * A level the enum does not know - a backend-specific one, or a gap in the constants - falls
   * back to the default rather than failing.
   */
  @Test
  public void testUnknownLevelFallsBackToDefault() {
    assertSame(TransactionIsolation.valueOf(3), TransactionIsolation.DEFAULT);
    assertSame(TransactionIsolation.valueOf(4711), TransactionIsolation.DEFAULT);
    assertSame(TransactionIsolation.valueOf(-2), TransactionIsolation.DEFAULT);
  }

  /**
   * {@code valueOf(int)} must not shadow the enum's own {@code valueOf(String)}.
   */
  @Test
  public void testValueOfNameStillWorks() {
    assertSame(TransactionIsolation.valueOf("SERIALIZABLE"), TransactionIsolation.SERIALIZABLE);
  }

}
