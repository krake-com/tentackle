/*
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.persist;

import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import org.tentackle.dbms.Db;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionFactory;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionInfoFactory;
import org.tentackle.sql.Backend;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Base class for the database-backed tests of this module.
 * <p>
 * The module's other tests run without a session at all (see {@code src/test/README.md}); this one
 * gives the few subsystems that cannot be tested that way &mdash; cursors, the read and delete
 * path, the token locks and the joined selects &mdash; a real in-memory H2 to work against.
 * <p>
 * The recipe is the one of {@code tentackle-database}'s {@code AbstractDbTest}, which cannot be
 * reused here because it lives in that module's test sources and is not published. Two scripts
 * build the schema:
 * <ol>
 *   <li>{@code META-INF/sql/H2/createttmodel.sql}, generated at build time by the SQL maven plugin
 *       from {@code tokenlock.map} and the modification tables, and</li>
 *   <li>{@code demo-schema.sql}, the hand-written DDL of the {@code Demo*} test PDOs, which have
 *       no model to generate one from.</li>
 * </ol>
 * Every test method runs in its own transaction, which is rolled back afterwards, so the tests
 * never see each other's rows.
 *
 * @author harald
 */
@SuppressWarnings("exports")
public abstract class AbstractPersistenceDbTest {

  /**
   * The transaction handling for the test class.
   */
  public enum TransactionType {
    /** transaction per test class. */
    CLASS,

    /** transaction per test method. */
    METHOD,

    /** no automatic transaction handling. */
    NONE
  }


  /**
   * The user ID of the test session.
   * <p>
   * Given explicitly because the token-lock operations refuse to work for user 0
   * (see {@link AbstractPersistentObject#getContextUserId()}).
   */
  public static final long USER_ID = 42;

  private static Session session;           // session for all methods in all test classes
  private static DomainContext context;     // domain context for all methods in all test classes

  private final TransactionType txType;     // transaction handling
  private long txVoucher;                   // voucher of the running transaction


  /**
   * Creates a test.
   *
   * @param txType the transaction type
   */
  protected AbstractPersistenceDbTest(TransactionType txType) {
    this.txType = txType;
  }

  /**
   * Creates a test with one transaction per method.
   */
  protected AbstractPersistenceDbTest() {
    this(TransactionType.METHOD);
  }


  /**
   * Opens the session and creates the schema.
   */
  @BeforeSuite(alwaysRun = true)
  public void openSession() {
    Session s;
    try {
      SessionInfo sessionInfo = SessionInfoFactory.getInstance().create();
      sessionInfo.setUserId(USER_ID);
      s = SessionFactory.getInstance().create(sessionInfo);
    }
    catch (PersistenceException ex) {
      // no database? wrong database? whatever: testing environment incomplete
      throw new SkipException("no backend found -> no tests", ex);
    }
    /*
     * Deliberately NOT made the thread-local session: this module's session-free tests run in the
     * same JVM and on the same thread, and some of them assert that there is no current session
     * (AbstractPersistentOperationTest.testCreateValidContext, for one). Everything below passes
     * the session explicitly through the domain context anyway.
     */
    session = s;

    if (s instanceof Db db) {
      createDatabaseTables(db);
    }

    context = Pdo.createDomainContext(session);
  }

  /**
   * Closes the session.
   */
  @AfterSuite(alwaysRun = true)
  public void closeSession() {
    if (session != null) {
      session.close();
      session = null;
      context = null;
    }
  }


  @BeforeClass(alwaysRun = true)
  public void beforeClass() {
    if (txType == TransactionType.CLASS) {
      beginTransaction();
    }
  }

  @AfterClass(alwaysRun = true)
  public void afterClass() {
    if (txType == TransactionType.CLASS) {
      rollbackTransaction();
    }
  }

  @BeforeMethod(alwaysRun = true)
  public void beforeMethod() {
    if (txType == TransactionType.METHOD) {
      beginTransaction();
    }
  }

  @AfterMethod(alwaysRun = true)
  public void afterMethod() {
    if (txType == TransactionType.METHOD) {
      rollbackTransaction();
    }
  }


  /**
   * Gets the session shared by all database-backed tests.
   *
   * @return the open session
   */
  public Db getSession() {
    return (Db) session;
  }

  /**
   * Gets the domain context shared by all database-backed tests.
   *
   * @return the context
   */
  public DomainContext getDomainContext() {
    return context;
  }


  /**
   * Creates the database tables.<br>
   * Only ever invoked for an in-memory database, which starts out empty, so that a shared database
   * such as a CI instance can never be dropped by a test run.
   *
   * @param db the low-level database session
   */
  protected void createDatabaseTables(Db db) {
    Backend backend = db.getBackend();
    if (!backend.isDatabaseInMemory(db.getUrl())) {
      throw new SkipException("the tests of this module own their schema and run in-memory only");
    }
    // the generated script holds no DROP statements, so wipe whatever a previous run left over
    db.runScript("drop", "DROP ALL OBJECTS;");
    db.runScript("model", loadScript("/META-INF/sql/" + backend.getName() + "/createttmodel.sql"));
    db.runScript("demo", loadScript("/demo-schema.sql"));
  }


  /**
   * Loads a SQL script from the test classpath.
   *
   * @param resource the resource path
   * @return the SQL script
   */
  private String loadScript(String resource) {
    try (InputStream is = getClass().getResourceAsStream(resource)) {
      if (is == null) {
        throw new IOException("no such resource");
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
    catch (IOException ix) {
      throw new PersistenceException("cannot load the DDL from " + resource, ix);
    }
  }


  private void beginTransaction() {
    txVoucher = session.begin("test");
  }

  private void rollbackTransaction() {
    if (session.isTxRunning()) {
      session.rollback(txVoucher);
      txVoucher = 0;
    }
  }

}
