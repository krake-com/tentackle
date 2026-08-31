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
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

/**
 * Unit tests for {@link TransactionWritability}.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class TransactionWritabilityTest {

  @Test
  public void testWritableFlags() {
    assertNull(TransactionWritability.DEFAULT.getWritable(), "the default leaves it to the backend");
    assertEquals(TransactionWritability.READ_WRITE.getWritable(), Boolean.TRUE);
    assertEquals(TransactionWritability.READ_ONLY.getWritable(), Boolean.FALSE);
  }

  @Test
  public void testEveryFlagMapsBack() {
    for (TransactionWritability writability : TransactionWritability.values()) {
      assertSame(TransactionWritability.valueOf(writability.getWritable()), writability);
    }
  }

  /**
   * An unknown writability is the default and so is none at all.
   */
  @Test
  public void testNoFlagIsTheDefault() {
    assertSame(TransactionWritability.valueOf((Boolean) null), TransactionWritability.DEFAULT);
  }

  /**
   * {@code valueOf(Boolean)} must not shadow the enum's own {@code valueOf(String)}.
   */
  @Test
  public void testValueOfNameStillWorks() {
    assertSame(TransactionWritability.valueOf("READ_ONLY"), TransactionWritability.READ_ONLY);
  }

}
