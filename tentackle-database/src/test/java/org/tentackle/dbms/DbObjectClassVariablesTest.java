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

import org.tentackle.dbms.DbObjectClassVariables.ForeignReference;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Properties;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link DbObjectClassVariables}.
 * <p>
 * Class variables are a "singleton per class" and therefore shared by every thread working with that
 * class. The tests below cover the state that is built up lazily and concurrently; the parts needing
 * a session belong to the database-backed tests.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DbObjectClassVariablesTest {

  /**
   * Creates class variables that are not registered globally.
   * <p>
   * A null tablename keeps {@link DbObjectClassVariables#putVariables} from entering them into the
   * static class map, so each test gets an independent instance.
   *
   * @return the class variables
   */
  private DbObjectClassVariables<ModificationLog> createVariables() {
    return new DbObjectClassVariables<>(ModificationLog.class, 0, null);
  }


  @Test
  public void testForeignReferencesAreIteratedAsASnapshot() {
    DbObjectClassVariables<ModificationLog> cv = createVariables();

    Collection<ForeignReference> snapshot = cv.getForeignReferences();
    assertNotSame(snapshot, cv.foreignReferences,
                  "isReferencedImpl() queries the database per reference and must iterate a copy, " +
                  "not the live set that addReferencingClass() writes to");

    cv.foreignReferences.add(new ForeignReference(new Method[0], 0, "isReferencingSomething"));
    assertTrue(snapshot.isEmpty(), "a snapshot already taken must not change");
    assertEquals(cv.getForeignReferences().size(), 1, "a fresh snapshot picks the new reference up");
  }

  @Test
  public void testForeignReferencesKeepTheirPriorityOrder() {
    DbObjectClassVariables<ModificationLog> cv = createVariables();
    ForeignReference low = new ForeignReference(new Method[0], 7, "isReferencingLater");
    ForeignReference high = new ForeignReference(new Method[0], 1, "isReferencingFirst");
    cv.foreignReferences.add(low);
    cv.foreignReferences.add(high);

    assertEquals(cv.getForeignReferences().stream().toList(), java.util.List.of(high, low),
                 "the snapshot must retain the sorted order of the TreeSet");
  }

  /**
   * The properties must be loaded under the instance monitor.
   * <p>
   * They are lazily loaded from arbitrary threads and, unlike the other lazily determined values,
   * must not be recomputed by a racing thread: {@link DbObjectClassVariables#putProperty} mutates
   * them, so a second instance would swallow whatever was already put into the first one.
   */
  @Test
  public void testPropertiesAreLoadedUnderTheInstanceMonitor() throws NoSuchMethodException {
    Method method = DbObjectClassVariables.class.getDeclaredMethod("getProperties");
    assertTrue(Modifier.isSynchronized(method.getModifiers()),
               "getProperties() lazily loads shared mutable state and must be synchronized");
  }

  @Test
  public void testPropertiesAreASingleMutableInstance() {
    DbObjectClassVariables<ModificationLog> cv = createVariables();

    Properties properties = cv.getProperties();
    assertNotNull(properties, "never null, even without a properties resource");
    assertSame(cv.getProperties(), properties, "loaded once and kept");

    cv.putProperty("someKey", "someValue");
    assertEquals(cv.getProperty("someKey"), "someValue", "a put property must be readable back");
    assertSame(cv.getProperties(), properties, "and must not be lost by reloading");
  }

}
