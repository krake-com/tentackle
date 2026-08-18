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

import org.tentackle.session.PersistenceException;
import org.tentackle.sql.Backend;
import org.tentackle.sql.backends.H2;
import org.tentackle.sql.backends.Postgres;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for the SQL created by {@link AbstractPersistentObject}.
 * <p>
 * All statements below are built from the class variables and an explicitly passed
 * {@link Backend}, so they can be verified without a session, let alone a database.
 * The statements taking the backend from the session ({@code createSelectMaxIdSql} and friends)
 * are covered by the database-backed tests of {@code tentackle-test-pdo}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class AbstractPersistentObjectSqlTest {

  private final Backend backend = new Postgres();

  private DemoEntityPersistenceImpl entity() {
    return new DemoEntityPersistenceImpl();
  }

  private DemoComponentPersistenceImpl component() {
    return new DemoComponentPersistenceImpl();
  }


  @Test
  public void testSelectAllInnerSql() {
    assertEquals(entity().createSelectAllInnerSql(backend).toString(),
                 "dem.* FROM demoentity dem WHERE 1=1");
  }

  @Test
  public void testSelectAllInnerSqlDependsOnTheBackend() {
    // the H2 backend writes the "AS" before a table alias, PostgreSQL does not
    assertEquals(entity().createSelectAllInnerSql(new H2()).toString(),
                 "dem.*" + Backend.SQL_FROM + "demoentity" + new H2().sqlAsBeforeTableAlias() + "dem WHERE 1=1");
  }

  @Test
  public void testSelectAllByIdInnerSql() {
    assertEquals(entity().createSelectAllByIdInnerSql(backend).toString(),
                 "dem.* FROM demoentity dem WHERE 1=1 AND dem.id=?");
  }

  @Test
  public void testSelectIdInnerSqlWithoutAlias() {
    assertEquals(entity().createSelectIdInnerSql(false).toString(), "id FROM demoentity WHERE 1=1");
  }

  @Test
  public void testSelectAllSql() {
    assertEquals(entity().createSelectAllSql(backend), "SELECT dem.* FROM demoentity dem WHERE 1=1");
  }

  @Test
  public void testSelectAllSqlWithContextConditionAndOrderBy() {
    // the component restricts the select to its root entity and is ordered
    assertEquals(component().createSelectAllSql(backend),
                 "SELECT dcm.* FROM democomponent dcm WHERE 1=1 AND dcm.rootid=? ORDER BY dcm.ctext");
  }

  @Test
  public void testSelectAllSqlOfAnInheritedEntity() {
    // the derived entity shares the table of its base entity: restricted by the class ID
    assertEquals(new DemoSubPersistenceImpl().createSelectAllSql(backend),
                 "SELECT dbs.* FROM demobase dbs WHERE 1=1 AND dbs.classid=9004");
  }

  @Test
  public void testSelectAnySql() {
    assertTrue(entity().createSelectAnySql(backend).startsWith("SELECT dem.* FROM demoentity dem WHERE 1=1 AND id="),
               entity().createSelectAnySql(backend));
  }

  @Test
  public void testSelectByNormTextSql() {
    assertEquals(component().createSelectByNormTextSql(backend, false),
                 "SELECT dcm.* FROM democomponent dcm WHERE 1=1 AND dcm.rootid=? AND dcm.normtext LIKE ?" +
                 " ORDER BY dcm.ctext");
  }

  @Test
  public void testSelectByInvertedNormTextSql() {
    assertEquals(component().createSelectByNormTextSql(backend, true),
                 "SELECT dcm.* FROM democomponent dcm WHERE 1=1 AND dcm.rootid=? AND dcm.normtext NOT LIKE ?" +
                 " ORDER BY dcm.ctext");
  }

  @Test
  public void testSelectAllWithExpiredTableSerialsSql() {
    assertEquals(entity().createSelectAllWithExpiredTableSerialsSql(backend),
                 "SELECT dem.* FROM demoentity dem WHERE 1=1 AND tableserial>? ORDER BY tableserial,id");
  }

  @Test
  public void testSelectAllWithExpiredTableSerialsSql2() {
    assertEquals(entity().createSelectAllWithExpiredTableSerialsSql2(backend),
                 "SELECT dem.* FROM demoentity dem WHERE 1=1 AND tableserial>? AND tableserial<=? " +
                 "ORDER BY tableserial,id");
  }

  @Test
  public void testExpiredTableSerialsAreOrderedByTheEntitysOrderBy() {
    assertEquals(component().createSelectAllWithExpiredTableSerialsSql(backend),
                 "SELECT dcm.* FROM democomponent dcm WHERE 1=1 AND tableserial>? AND dcm.rootid=? " +
                 "ORDER BY dcm.ctext");
  }

  @Test
  public void testSelectSerialSql() {
    assertEquals(entity().createSelectSerialSql(), "SELECT serial FROM demoentity WHERE id=?");
  }

  @Test
  public void testStatementsOfAnInheritedEntityReferToTheTopSuperTable() {
    assertEquals(new DemoSubPersistenceImpl().createSelectSerialSql(), "SELECT serial FROM demobase WHERE id=?");
  }

  @Test
  public void testDummyUpdateSql() {
    assertEquals(entity().createDummyUpdateSql(), "UPDATE demoentity SET id=id WHERE id=?");
  }

  @Test
  public void testUpdateSerialSql() {
    assertEquals(entity().createUpdateSerialSql(), "UPDATE demoentity SET serial=serial+1 WHERE id=? AND serial=?");
  }

  @Test
  public void testUpdateSerialAndTableSerialSql() {
    assertEquals(entity().createUpdateSerialAndTableSerialSql(),
                 "UPDATE demoentity SET serial=serial+1,tableserial=? WHERE id=? AND serial=?");
  }

  @Test
  public void testSelectExpiredTableSerials1Sql() {
    assertEquals(entity().createSelectExpiredTableSerials1Sql(),
                 "SELECT id,tableserial FROM demoentity WHERE tableserial>? ORDER BY tableserial,id");
  }

  @Test
  public void testSelectExpiredTableSerials2Sql() {
    assertEquals(entity().createSelectExpiredTableSerials2Sql(),
                 "SELECT id,tableserial FROM demoentity WHERE tableserial>? AND tableserial<=? ORDER BY tableserial,id");
  }

  @Test
  public void testExpiredTableSerialsOfAnInheritedEntityAreRestrictedToItsClassIds() {
    // without a table alias: the statement selects from the base table directly
    assertEquals(new DemoSubPersistenceImpl().createSelectExpiredTableSerials1Sql(),
                 "SELECT id,tableserial FROM demobase WHERE tableserial>? AND classid=9004 ORDER BY tableserial,id");
  }

  @Test
  public void testUpdateTokenLockSql() {
    assertEquals(component().createUpdateTokenLockSql(),
                 "UPDATE democomponent SET editedby=?,editedsince=?,editedexpiry=? WHERE id=? AND serial=? AND " +
                 " (editedby=? OR editedby=0 OR editedexpiry<? OR editedexpiry IS NULL) ");
  }

  @Test
  public void testUpdateTokenLockOnlySql() {
    assertEquals(component().createUpdateTokenLockOnlySql(),
                 "UPDATE democomponent SET editedby=?,editedsince=?,editedexpiry=? WHERE id=?");
  }

  @Test
  public void testSelectTokenLockSql() {
    assertEquals(component().createSelectTokenLockSql(),
                 "SELECT editedby,editedsince,editedexpiry,serial FROM democomponent WHERE id=?");
  }

  @Test
  public void testTransferTokenLockSql() {
    assertEquals(component().createTransferTokenLockSql(),
                 "UPDATE democomponent SET editedby=? WHERE id=? AND serial=?");
  }

  @Test
  public void testTokenLockSqlRequiresTheTokenLockColumns() {
    DemoEntityPersistenceImpl entity = entity();
    assertTrue(expectThrows(PersistenceException.class, entity::createUpdateTokenLockSql)
                   .getMessage().contains("does not provide the edited-token columns"));
    expectThrows(PersistenceException.class, entity::createUpdateTokenLockOnlySql);
    expectThrows(PersistenceException.class, entity::createSelectTokenLockSql);
    expectThrows(PersistenceException.class, entity::createTransferTokenLockSql);
  }

}
