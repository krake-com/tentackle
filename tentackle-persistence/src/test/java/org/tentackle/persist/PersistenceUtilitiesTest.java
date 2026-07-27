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

package org.tentackle.persist;

import org.testng.annotations.Test;

import org.tentackle.dbms.DbClassVariablesFactory;
import org.tentackle.dbms.DbUtilities;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link PersistenceUtilities} and {@link PersistentClassVariablesFactory}.
 * <p>
 * Both replace their counterparts of the tentackle-database module through the service API to make
 * them PDO-aware. The methods needing a session (connection manager, remote session cleanup,
 * commit/rollback notification) are left to the database-backed tests.
 */
@SuppressWarnings("missing-explicit-ctor")
public class PersistenceUtilitiesTest {

  @Test
  public void testRegisteredAsTheDbUtilities() {
    assertEquals(DbUtilities.getInstance().getClass(), PersistenceUtilities.class,
                 "@Service(DbUtilities.class) must replace the database module's utilities");
  }

  @Test
  public void testRegisteredAsTheClassVariablesFactory() {
    assertEquals(DbClassVariablesFactory.getInstance().getClass(), PersistentClassVariablesFactory.class);
    assertSame(PersistentClassVariablesFactory.getInstance(), DbClassVariablesFactory.getInstance());
  }

  @Test
  public void testCreateObjectCreatesThePdoProxyForPdoClasses() {
    DemoEntity pdo = DbUtilities.getInstance().createObject(DemoEntity.class);
    assertTrue(pdo.getPersistenceDelegate() instanceof DemoEntityPersistenceImpl,
               "a PDO interface must be created through the PDO factory, not by newInstance()");
  }

  @Test
  public void testCreateObjectCreatesPlainDbObjectsAsWell() {
    DemoEntityPersistenceImpl po = DbUtilities.getInstance().createObject(DemoEntityPersistenceImpl.class);
    assertSame(po.getClass(), DemoEntityPersistenceImpl.class, "the low-level object is created directly");
    assertNull(po.getPdo(), "not linked to a PDO proxy");
  }

  @Test
  public void testCreateObjectOfSomethingElse() {
    assertNull(DbUtilities.getInstance().createObject(String.class), "neither a PDO nor a db object");
  }

  @Test
  public void testServicedClassOfAPersistenceImplementationIsThePdoInterface() {
    assertSame(DbUtilities.getInstance().getServicedClass(DemoEntityPersistenceImpl.class), DemoEntity.class);
  }

  @Test
  public void testTableSerialTableNameOfAnEntityWithoutTableSerials() {
    assertNull(DbUtilities.getInstance().determineTableSerialTableName(DemoEntityPersistenceImpl.CLASSVARIABLES),
               "no tableserial column, hence no table holding it");
  }

  @Test
  public void testTableSerialTableNameIsTheTableOfTheTopmostEntity() {
    assertEquals(DbUtilities.getInstance().determineTableSerialTableName(DemoSubPersistenceImpl.CLASSVARIABLES),
                 "demobase", "the derived entity shares the table of its base entity");
  }

}
