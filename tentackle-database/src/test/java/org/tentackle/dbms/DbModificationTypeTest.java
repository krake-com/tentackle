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

import org.tentackle.session.PersistenceException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;

/**
 * Unit tests for {@link DbModificationType} and the static helpers of {@link ModificationType}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DbModificationTypeTest {

  @Test
  public void testExternalCharValues() {
    assertEquals(DbModificationType.BEGIN.toExternal(), Character.valueOf('B'));
    assertEquals(DbModificationType.COMMIT.toExternal(), Character.valueOf('C'));
    assertEquals(DbModificationType.INSERT.toExternal(), Character.valueOf('I'));
    assertEquals(DbModificationType.UPDATE.toExternal(), Character.valueOf('U'));
    assertEquals(DbModificationType.DELETE.toExternal(), Character.valueOf('D'));
    assertEquals(DbModificationType.RESET.toExternal(), Character.valueOf('Z'));
  }

  @Test
  public void testToInternalImplRoundTrip() {
    for (DbModificationType type : DbModificationType.values()) {
      assertSame(DbModificationType.toInternalImpl(type.toExternal()), type,
                 "toExternal/toInternalImpl must round-trip for " + type);
    }
  }

  @Test
  public void testToInternalImplReturnsNullForUnknownChar() {
    // deliberately no exception, see ModificationTypeFactory
    assertNull(DbModificationType.toInternalImpl('?'));
    assertNull(DbModificationType.toInternalImpl('b'), "matching is case sensitive");
  }

  @Test
  public void testToInternalDelegatesToFactory() {
    assertSame(ModificationType.toInternal('I'), DbModificationType.INSERT);
  }

  @Test
  public void testToInternalThrowsForUnknownChar() {
    assertThrows(PersistenceException.class, () -> ModificationType.toInternal('?'));
  }

  @Test
  public void testGetDefaultThrows() {
    assertThrows(PersistenceException.class, ModificationType::getDefault);
  }

}
