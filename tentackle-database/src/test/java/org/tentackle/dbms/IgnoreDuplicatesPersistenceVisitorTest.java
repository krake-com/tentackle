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

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Pure in-memory unit tests for {@link IgnoreDuplicatesPersistenceVisitor}'s object-identity
 * dedup logic, without touching a database.
 * <p>
 * The DB-backed scenario (a duplicate insert actually skipped during a real persistence
 * operation) is covered by {@link ModificationLogCrudTest#testDuplicateInsertIsSilentlySkippedByVisitor}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class IgnoreDuplicatesPersistenceVisitorTest {

  @Test
  public void testVisitTracksObjectIdentity() {
    IgnoreDuplicatesPersistenceVisitor visitor = new IgnoreDuplicatesPersistenceVisitor();

    // two distinct instances, never persisted: only object identity matters here
    ModificationLog obj1 = new ModificationLog();
    ModificationLog obj2 = new ModificationLog();

    visitor.visit(obj1, DbModificationType.INSERT);
    assertTrue(visitor.isPersistenceOperationAllowed(obj1, DbModificationType.INSERT),
               "the first visit of an object must be allowed");

    visitor.visit(obj1, DbModificationType.UPDATE);
    assertFalse(visitor.isPersistenceOperationAllowed(obj1, DbModificationType.UPDATE),
                "a second visit of the same object must be reported as a duplicate");

    visitor.visit(obj2, DbModificationType.INSERT);
    assertTrue(visitor.isPersistenceOperationAllowed(obj2, DbModificationType.INSERT),
               "a distinct object must not be affected by another object's duplicate");
  }

}
