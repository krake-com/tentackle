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

package org.tentackle.jta;

import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;

import org.tentackle.common.EncryptedProperties;
import org.tentackle.dbms.ConnectionManager;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.GlobalTransactionMode;
import org.tentackle.dbms.PreparedStatementWrapper;
import org.tentackle.dbms.ResultSetWrapper;
import org.tentackle.dbms.StatementWrapper;
import org.tentackle.misc.PropertiesUtilities;
import org.tentackle.session.DefaultSessionInfo;
import org.tentackle.sql.BackendInfo;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

/**
 * Base class for the JTA tests.
 * <p>
 * Runs against the standalone Narayana transaction manager and two XA-capable H2 in-memory databases.
 * Each test starts with empty tables.
 *
 * @author harald
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public abstract class AbstractJtaTest {

  /** the table created in each database. */
  protected static final String TABLE_NAME = "jtatest";

  protected TransactionManager tm;
  protected Db db1;
  protected Db db2;

  private final List<Db> sessions = new ArrayList<>();
  private final List<ConnectionManager> connectionManagers = new ArrayList<>();


  /**
   * Gets the global transaction mode of the sessions.
   *
   * @return the mode
   */
  protected abstract GlobalTransactionMode getMode();

  /**
   * Creates the connection manager for a database.
   *
   * @param name the name of the connection manager
   * @param backendInfo the database's backend info
   * @return the connection manager
   */
  protected ConnectionManager createConnectionManager(String name, BackendInfo backendInfo) {
    return new XaConnectionManager(name, 2, 8, 1, 720, 2160);
  }


  @BeforeClass
  public void openSessions() {
    tm = com.arjuna.ats.jta.TransactionManager.transactionManager();
    JtaTransactionSupport.setTransactionManager(tm);
    String prefix = getClass().getSimpleName().toLowerCase();
    db1 = openSession(prefix + "1", getMode());
    db2 = openSession(prefix + "2", getMode());
    createTable(db1);
    createTable(db2);
  }

  @AfterClass(alwaysRun = true)
  public void closeSessions() {
    for (Db db : sessions) {
      db.close();   // no harm if already closed
    }
    for (ConnectionManager connectionManager : connectionManagers) {
      connectionManager.shutdown();
    }
    JtaTransactionSupport.setTransactionManager(null);
  }

  @AfterMethod(alwaysRun = true)
  public void cleanup() throws SystemException {
    if (tm.getStatus() != Status.STATUS_NO_TRANSACTION) {
      tm.rollback();
    }
    for (Db db : List.of(db1, db2)) {
      if (db.isTxRunning()) {
        db.rollbackImmediately(null);
      }
      assertFalse(db.isTxRunning(), db + " still in transaction");
      deleteRows(db);
    }
  }


  /**
   * Opens a session to an H2 in-memory database.
   *
   * @param dbName the database name
   * @param mode the global transaction mode
   * @return the session
   */
  protected Db openSession(String dbName, GlobalTransactionMode mode) {
    EncryptedProperties properties = createProperties(dbName, mode);
    ConnectionManager connectionManager = createConnectionManager(dbName + "-mgr", new BackendInfo(properties));
    connectionManagers.add(connectionManager);
    return openSession(properties, connectionManager);
  }

  /**
   * Opens a session to an H2 in-memory database.
   *
   * @param dbName the database name
   * @param mode the global transaction mode
   * @param connectionManager the connection manager
   * @return the session
   */
  protected Db openSession(String dbName, GlobalTransactionMode mode, ConnectionManager connectionManager) {
    return openSession(createProperties(dbName, mode), connectionManager);
  }

  private Db openSession(EncryptedProperties properties, ConnectionManager connectionManager) {
    Db db = new Db(connectionManager, new DefaultSessionInfo(properties));
    sessions.add(db);
    return db;
  }

  private EncryptedProperties createProperties(String dbName, GlobalTransactionMode mode) {
    EncryptedProperties properties = PropertiesUtilities.getInstance().create();
    properties.setProperty("url", "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
    properties.setProperty("user", "tentackle");
    properties.setProperty("password", "tentackle");
    properties.setProperty(Db.JTA, mode.name().toLowerCase());
    return properties;
  }

  /**
   * Creates the test table.
   *
   * @param db the session
   */
  protected void createTable(Db db) {
    try (StatementWrapper st = db.createStatement()) {
      st.executeUpdate("CREATE TABLE " + TABLE_NAME + " (id INT PRIMARY KEY, val VARCHAR(64))");
    }
  }

  private void deleteRows(Db db) {
    try (StatementWrapper st = db.createStatement()) {
      st.executeUpdate("DELETE FROM " + TABLE_NAME);
    }
  }

  /**
   * Inserts a row.
   *
   * @param db the session
   * @param id the row's ID
   */
  protected void insertRow(Db db, int id) {
    try (PreparedStatementWrapper st = db.createPreparedStatement(_ -> "INSERT INTO " + TABLE_NAME + " (id, val) VALUES (?, ?)")) {
      st.setInt(1, id);
      st.setString(2, "row " + id);
      assertEquals(st.executeUpdate(), 1);
    }
  }

  /**
   * Counts the rows.
   *
   * @param db the session
   * @return the number of rows
   */
  protected int countRows(Db db) {
    try (StatementWrapper st = db.createStatement();
         ResultSetWrapper rs = st.executeQuery("SELECT COUNT(*) FROM " + TABLE_NAME)) {
      rs.next();
      return rs.getInt(1);
    }
  }

}
