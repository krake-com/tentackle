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

package org.tentackle.sql.metadata;

import org.testng.annotations.Test;

import java.sql.DatabaseMetaData;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Tests the {@link ForeignKeyAction} enum.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ForeignKeyActionTest {

  @Test
  public void testSqlAndToString() {
    assertEquals(ForeignKeyAction.CASCADE.getSql(), "CASCADE");
    assertEquals(ForeignKeyAction.CASCADE.toString(), "CASCADE");
    assertEquals(ForeignKeyAction.SET_NULL.getSql(), "SET NULL");
    assertEquals(ForeignKeyAction.NO_ACTION.toString(), "NO ACTION");
    assertEquals(ForeignKeyAction.SET_DEFAULT.getSql(), "SET DEFAULT");
    assertEquals(ForeignKeyAction.INITIALLY_DEFERRED.getSql(), "INITIALLY DEFERRED");
    assertEquals(ForeignKeyAction.INITIALLY_IMMEDIATE.getSql(), "INITIALLY IMMEDIATE");
    assertEquals(ForeignKeyAction.NOT_DEFERRABLE.getSql(), "NOT DEFERRABLE");
  }

  @Test
  public void testGetAction() {
    assertEquals(ForeignKeyAction.CASCADE.getAction(), DatabaseMetaData.importedKeyCascade);
    assertEquals(ForeignKeyAction.RESTRICT.getAction(), DatabaseMetaData.importedKeyRestrict);
    assertEquals(ForeignKeyAction.SET_NULL.getAction(), DatabaseMetaData.importedKeySetNull);
  }

  @Test
  public void testCreateFromAction() {
    assertEquals(ForeignKeyAction.createFromAction(DatabaseMetaData.importedKeyCascade), ForeignKeyAction.CASCADE);
    assertEquals(ForeignKeyAction.createFromAction(DatabaseMetaData.importedKeySetNull), ForeignKeyAction.SET_NULL);
    assertEquals(ForeignKeyAction.createFromAction(DatabaseMetaData.importedKeyNoAction), ForeignKeyAction.NO_ACTION);
  }

  @Test
  public void testCreateFromUnknownActionReturnsNull() {
    assertNull(ForeignKeyAction.createFromAction(-999));
  }
}
