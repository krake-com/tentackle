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

import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests the selection of the id source by {@link DbObjectClassVariables#getIdSource(Db)}.
 */
public class ClassIdSourceTest extends AbstractDbTest {

  public ClassIdSourceTest() {
    super(TransactionType.NONE);
  }


  /**
   * Creates class variables that are not registered globally.
   *
   * @return the class variables
   */
  private DbObjectClassVariables<ModificationLog> createVariables() {
    return new DbObjectClassVariables<>(ModificationLog.class, 0, null);
  }


  @Test
  public void testDefaultIdSourceFollowsTheSession() {
    DbObjectClassVariables<ModificationLog> cv = createVariables();
    Db db1 = getSession();
    Db db2 = db1.clone("classIdSourceTest");
    try {
      IdSource otherSource = new ObjectId("other_object_id");
      db2.setDefaultIdSource(otherSource);    // as if db2 were configured for a backend without sequences

      assertSame(cv.getIdSource(db1), db1.getDefaultIdSource());
      assertSame(cv.getIdSource(db2), otherSource, "the second session must not get the id source of the first");
      assertSame(cv.getIdSource(db1), db1.getDefaultIdSource());
    }
    finally {
      db2.close();
    }
  }


  @Test
  public void testClassSpecificIdSourceIsConfiguredOncePerBackend() {
    DbObjectClassVariables<ModificationLog> cv = createVariables();
    cv.putProperty(Db.IDSOURCE, "table:class_object_id");
    Db db1 = getSession();
    Db db2 = db1.clone("classIdSourceTest");
    try {
      IdSource source = cv.getIdSource(db1);
      assertTrue(source instanceof ObjectId objectId && objectId.getName().equals("class_object_id"),
                 "unexpected id source " + source);
      assertSame(cv.getIdSource(db2), source, "sessions on the same backend share the class-specific id source");
    }
    finally {
      db2.close();
    }
  }

}
