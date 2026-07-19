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

package org.tentackle.sql;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Tests the SQL rendering helpers in {@link NonStandardCommons} that do not require a backend.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class NonStandardCommonsTest {

  @Test
  public void testSequenceWithStartAndIncrement() {
    assertEquals(NonStandardCommons.sqlCreateSequence("seq", 100L, 5L),
                 "CREATE SEQUENCE seq INCREMENT BY 5 START WITH 100");
  }

  @Test
  public void testSequenceWithoutStart() {
    assertEquals(NonStandardCommons.sqlCreateSequence("seq", null, 5L),
                 "CREATE SEQUENCE seq INCREMENT BY 5");
  }

  @Test
  public void testSequenceWithoutIncrement() {
    assertEquals(NonStandardCommons.sqlCreateSequence("seq", 100L, null),
                 "CREATE SEQUENCE seq START WITH 100");
  }

  @Test
  public void testSequenceNameOnly() {
    assertEquals(NonStandardCommons.sqlCreateSequence("seq", null, null),
                 "CREATE SEQUENCE seq");
  }
}
