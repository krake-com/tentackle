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

package org.tentackle.dbms;

import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link StatementId}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class StatementIdTest {

  @Test
  public void testValuesAreUniqueAndStrictlyIncreasing() {
    StatementId a = new StatementId();
    StatementId b = new StatementId();
    assertTrue(a.value() > 0, "ids are 1-based");
    assertEquals(b.value(), a.value() + 1, "ids are handed out consecutively");
  }

  @Test
  public void testManyIdsAreDistinct() {
    Set<Integer> seen = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      assertTrue(seen.add(new StatementId().value()), "every id must be unique");
    }
  }

  @Test
  public void testToStringIsTheDecimalValue() {
    StatementId id = new StatementId();
    assertEquals(id.toString(), Integer.toString(id.value()));
  }

  @Test
  public void testIdentityEquality() {
    StatementId a = new StatementId();
    StatementId b = new StatementId();
    // no equals/hashCode override: identity semantics
    assertEquals(a, a);
    assertNotEquals(a, b);
  }

}
