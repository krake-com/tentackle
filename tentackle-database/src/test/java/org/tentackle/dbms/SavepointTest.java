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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.tentackle.session.PersistenceException;
import org.tentackle.session.SavepointHandle;
import org.tentackle.sql.Backend;
import org.tentackle.sql.SqlType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link Db#setSavepoint()}/{@link Db#setSavepoint(String)}, {@link Db#rollback(SavepointHandle)}
 * and {@link Db#releaseSavepoint(SavepointHandle)} — a public {@link Db}/{@link org.tentackle.session.Session}
 * API surface without any prior test coverage.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class SavepointTest extends AbstractDbTest {

  private static final String TABLE_NAME = "savepointtest";

  public SavepointTest() {
    super(TransactionType.NONE);   // the transactions are managed by the test methods
  }

  @AfterMethod
  public void rollbackPendingTransaction() {
    Db db = getSession();
    while (db.isTxRunning()) {
      db.rollbackImmediately(null);
    }
  }

  @BeforeClass(dependsOnMethods = "beforeClass")
  public void createTestTable() {
    Db db = getSession();
    Backend backend = db.getBackend();
    StringBuilder ddl = new StringBuilder(backend.sqlCreateTableIntro(TABLE_NAME, null));
    ddl.append("    ").append(backend.sqlCreateColumn("val", null, SqlType.VARCHAR, 64, 0, true, null, false, false));
    ddl.append(backend.sqlCreateTableClosing(TABLE_NAME, null));
    try (StatementWrapper st = db.createStatement()) {
      st.executeUpdate(ddl.toString());
    }
  }

  private void insertRow(Db db, String value) {
    try (PreparedStatementWrapper st = db.createPreparedStatement(_ -> "INSERT INTO " + TABLE_NAME + " (val) VALUES (?)")) {
      st.setString(1, value);
      assertEquals(st.executeUpdate(), 1);
    }
  }

  private int countRows(Db db) {
    try (StatementWrapper st = db.createStatement();
         ResultSetWrapper rs = st.executeQuery("SELECT COUNT(*) FROM " + TABLE_NAME)) {
      rs.next();
      return rs.getInt(1);
    }
  }


  @Test
  public void testSetAndRollbackToSavepoint() {
    Db db = getSession();
    long voucher = db.begin("savepointTest");

    // the table is shared across the whole suite (see AbstractDbTest), so previously committed
    // rows from other test methods may already be there: compare against a baseline, not zero
    insertRow(db, "A");   // also attaches the session, a precondition for setSavepoint() below
    int afterA = countRows(db);

    SavepointHandle handle = db.setSavepoint();
    insertRow(db, "B");
    assertEquals(countRows(db), afterA + 1, "both rows must be visible before the rollback");

    db.rollback(handle);
    assertEquals(countRows(db), afterA, "row B must be gone after rolling back to the savepoint");

    db.commit(voucher);
    assertEquals(countRows(db), afterA, "row A must have survived the commit");
  }


  @Test
  public void testNamedSavepoint() {
    Db db = getSession();
    long voucher = db.begin("namedSavepointTest");

    insertRow(db, "A");
    int afterA = countRows(db);

    SavepointHandle handle = db.setSavepoint("mark");
    insertRow(db, "B");

    db.rollback(handle);
    assertEquals(countRows(db), afterA);

    db.commit(voucher);
  }


  @Test
  public void testReleaseSavepoint() {
    Db db = getSession();
    long voucher = db.begin("releaseSavepointTest");
    insertRow(db, "attach");   // attaches the session, a precondition for setSavepoint() below

    SavepointHandle handle = db.setSavepoint();
    db.releaseSavepoint(handle);

    assertThrows(PersistenceException.class, () -> db.rollback(handle));

    db.commit(voucher);
  }


  @Test
  public void testIsTxWithSavepoints() {
    Db db = getSession();
    long voucher = db.begin("isTxWithSavepointsTest");
    insertRow(db, "attach");   // attaches the session, a precondition for setSavepoint() below

    assertFalse(db.isTxWithSavepoints(), "no savepoint set yet");
    db.setSavepoint();
    assertTrue(db.isTxWithSavepoints(), "a savepoint has been set");

    db.commit(voucher);
  }


  @Test
  public void testRollbackToUnknownSavepointThrows() {
    Db db = getSession();
    long voucher = db.begin("unknownSavepointTest");

    SavepointHandle unknown = new SavepointHandle("never-registered");
    assertThrows(PersistenceException.class, () -> db.rollback(unknown));

    db.commit(voucher);
  }

}
