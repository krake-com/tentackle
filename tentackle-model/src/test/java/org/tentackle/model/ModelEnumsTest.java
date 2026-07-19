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

package org.tentackle.model;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the behavior-carrying model enums.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ModelEnumsTest {

  @Test
  public void testAccessScopeToString() {
    assertEquals(AccessScope.PRIVATE.toString(), "private");
    assertEquals(AccessScope.PACKAGE.toString(), "");
    assertEquals(AccessScope.PROTECTED.toString(), "protected");
    assertEquals(AccessScope.PUBLIC.toString(), "public");
  }

  @Test
  public void testTrackType() {
    assertFalse(TrackType.UNTRACKED.isTracked());
    assertTrue(TrackType.TRACKED.isTracked());
    assertTrue(TrackType.ATTRACKED.isTracked());
    assertTrue(TrackType.FULLTRACKED.isTracked());

    assertFalse(TrackType.UNTRACKED.isAttracked());
    assertFalse(TrackType.TRACKED.isAttracked());
    assertTrue(TrackType.ATTRACKED.isAttracked());
    assertTrue(TrackType.FULLTRACKED.isAttracked());
  }

  @Test
  public void testIntegrityFlags() {
    assertFalse(Integrity.NONE.isCheckedByApplication());
    assertFalse(Integrity.NONE.isRelatedCheckedByDatabase());
    assertFalse(Integrity.NONE.isCompositesCheckedByDatabase());

    assertTrue(Integrity.SOFT.isCheckedByApplication());
    assertFalse(Integrity.SOFT.isRelatedCheckedByDatabase());
    assertFalse(Integrity.SOFT.isCompositesCheckedByDatabase());

    assertTrue(Integrity.FULL.isCheckedByApplication());
    assertTrue(Integrity.FULL.isRelatedCheckedByDatabase());
    assertTrue(Integrity.FULL.isCompositesCheckedByDatabase());

    assertFalse(Integrity.FULL_DBONLY.isCheckedByApplication());
    assertTrue(Integrity.FULL_DBONLY.isRelatedCheckedByDatabase());
    assertTrue(Integrity.FULL_DBONLY.isCompositesCheckedByDatabase());

    assertTrue(Integrity.RELATED.isRelatedCheckedByDatabase());
    assertFalse(Integrity.RELATED.isCompositesCheckedByDatabase());
    assertTrue(Integrity.COMPOSITE.isCompositesCheckedByDatabase());
    assertFalse(Integrity.COMPOSITE.isRelatedCheckedByDatabase());
  }

  @Test
  public void testInheritanceType() {
    assertFalse(InheritanceType.NONE.isAbstractClass());
    assertTrue(InheritanceType.NONE.isMappingToOwnTable());
    assertTrue(InheritanceType.NONE.isMappingToSuperTable());
    assertFalse(InheritanceType.NONE.isMappingToNoTable());

    assertTrue(InheritanceType.PLAIN.isAbstractClass());
    assertFalse(InheritanceType.PLAIN.isMappingToOwnTable());
    assertFalse(InheritanceType.PLAIN.isMappingToSuperTable());
    assertTrue(InheritanceType.PLAIN.isMappingToNoTable());

    assertTrue(InheritanceType.SINGLE.isMappingToSuperTable());
    assertFalse(InheritanceType.SINGLE.isMappingToOwnTable());

    assertTrue(InheritanceType.MULTI.isMappingToOwnTable());
    assertFalse(InheritanceType.MULTI.isMappingToSuperTable());

    assertFalse(InheritanceType.EMBEDDED.isAbstractClass());
    assertTrue(InheritanceType.EMBEDDED.isMappingToNoTable());
  }

  @Test
  public void testSimpleEnumsRoundTrip() {
    assertEquals(RelationType.valueOf("OBJECT"), RelationType.OBJECT);
    assertEquals(RelationType.valueOf("LIST"), RelationType.LIST);
    assertEquals(SortType.valueOf("ASC"), SortType.ASC);
    assertEquals(SelectionType.valueOf("EAGER"), SelectionType.EAGER);
    assertEquals(SelectionType.values().length, 4);
  }
}
