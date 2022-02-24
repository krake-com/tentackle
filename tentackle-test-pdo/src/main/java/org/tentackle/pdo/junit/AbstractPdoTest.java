/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.pdo.junit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.opentest4j.AssertionFailedError;

import org.tentackle.dbms.Db;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.pdo.test.DbTestUtilities;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.Pdo;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.sql.Backend;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;


/**
 * Base class for Junit5 tests on PDO level.
 *
 * @author harald
 */
public abstract class AbstractPdoTest implements DomainContextProvider {

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

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPdoTest.class);

  private static Session session;               // session for all methods in all test classes
  private static DomainContext context;         // domain context for all methods in all test classes
  private static AbstractPdoTest currentTest;   // the currently running test

  private final boolean closeIt;                // true if we're the first test that created the session
  private final TransactionType txType;         // transaction handling
  private final boolean commit;                 // true if commit transaction, else rollback (default)
  private long txVoucher;                       // transaction voucher for current test class


  /**
   * Creates a test.
   *
   * @param txType the transaction type
   * @param commit true if commit transaction, else rollback (default for regular tests)
   */
  public AbstractPdoTest(TransactionType txType, boolean commit) {
    this.txType = txType;
    this.commit = commit;
    closeIt = openSessionsAndStartModificationTracker();
    if (txType == TransactionType.CLASS) {
      beginTransaction();
    }
  }

  /**
   * Creates a test with rollback.
   *
   * @param txType the transaction type
   */
  public AbstractPdoTest(TransactionType txType) {
    this(txType, false);
  }

  /**
   * Creates a test with rollback and one transaction per class.
   */
  public AbstractPdoTest() {
    this(TransactionType.CLASS, false);    // per class
  }


  /**
   * Opens the sessions and starts the modification tracker exactly once.<br>
   * If the database is in-memory, it will be populated before the tracker is started.
   *
   * @throws Exception if failed
   * @see #createDatabaseTables(Db)
   */
  public boolean openSessionsAndStartModificationTracker() {
    synchronized (AbstractPdoTest.class) {
      if (currentTest != null) {
        throw new AssertionFailedError("another AbstractPdoTest is currently running: " + currentTest.getClass().getName());
      }
      currentTest = this;
      if (session == null) {
        session = openSession();
        context = createDomainContext();

        boolean populate = false;
        if (session instanceof Db db) {
          Backend backend = db.getBackend();
          if (backend.isDatabaseInMemory(session.getUrl())) {
            createDatabaseTables(db);
            populate = true;
          }
        }

        ModificationTracker tracker = ModificationTracker.getInstance();
        tracker.setSession(session.clone(null));
        tracker.setSleepInterval(500);   // fast polling for tests
        tracker.start();

        if (populate) {
          populateDatabase();
        }

        return true;
      }
      return false;
    }
  }

  /**
   * Terminates the modification tracker and closes the sessions.
   *
   * @throws Exception if failed
   */
  @AfterAll
  public static synchronized void closeSessionsAndTerminateModificationTracker() {
    if (currentTest != null) {
      if (currentTest.txType == TransactionType.CLASS) {
        currentTest.endTransaction();
      }
      if (currentTest.closeIt && session != null) {
        ModificationTracker.getInstance().terminate();
        session.close();
        session = null;
        context = null;
      }
      currentTest = null;
    }
  }

  /**
   * Begins the transaction if type is {@link TransactionType#METHOD}.
   *
   * @throws Exception if failed
   */
  @BeforeEach
  public void beforeMethod() throws Exception {
    if (txType == TransactionType.METHOD) {
      beginTransaction();
    }
  }

  /**
   * Ends the transaction if type is {@link TransactionType#METHOD}.
   *
   * @throws Exception if failed
   */
  @AfterEach
  public void afterMethod() throws Exception {
    if (txType == TransactionType.METHOD) {
      endTransaction();
    }
  }


  /**
   * Gets the session.
   *
   * @return the session
   */
  public Session getSession() {
    return session;
  }

  /**
   * Returns whether the transaction should be commit or rolled back.
   *
   * @return true if commit, false if rollback (default for regular tests)
   */
  public boolean isCommit() {
    return commit;
  }

  /**
   * Gets the domain context.
   *
   * @return the domain context
   */
  @Override
  public DomainContext getDomainContext() {
    return context;
  }

  /**
   * Runs the given class in another JVM.<br>
   * The test class must have a main method.
   *
   * @param testClass the test class
   * @return the exit value
   * @throws IOException if some IO operation failed
   */
  public int runInOtherJVM(Class<?> testClass) throws IOException {
    Process process = runClass(testClass);
    waitForProcess(process);
    return process.exitValue();
  }

  /**
   * Opens the session.
   *
   * @return the thread-local session
   */
  protected Session openSession() {
    Session s;
    try {
      s = Pdo.createSession();
    }
    catch (PersistenceException ex) {
      // no database? wrong database? whatever: testing environment incomplete
      throw new AssertionFailedError("no backend found -> no tests", ex);
    }
    s.makeCurrent();
    return s;
  }

  /**
   * Creates the database tables.<br>
   * The default implementation loads the model from the classpath.
   * Override this method to provide additional model sources.
   *
   * @param db the low-level database session
   * @see Model#loadFromResources(boolean)
   */
  protected void createDatabaseTables(Db db) {
    try {
      String script = DbTestUtilities.getInstance().createPopulateScript(db);
      DbTestUtilities.getInstance().runScript(db, script);
    }
    catch (ModelException e) {
      throw new AssertionFailedError("populating the database failed", e);
    }
  }

  /**
   * Populates the database with test data.
   */
  protected void populateDatabase() {
    // default does nothing
  }

  /**
   * Creates the domain context.
   *
   * @return the context (usually thread-local)
   */
  protected DomainContext createDomainContext() {
    return Pdo.createDomainContext();   // thread local
  }

  /**
   * Begins a transaction.
   */
  protected void beginTransaction() {
    if (session != null) {
      txVoucher = session.begin("test");
    }
  }

  /**
   * Commits or rolls back a transaction.
   */
  protected void endTransaction() {
    if (session != null && session.isTxRunning()) {
      if (commit) {
        session.commit(txVoucher);
      }
      else  {
        session.rollback(txVoucher);
      }
      txVoucher = 0;
    }
  }



  /**
   * Runs the given class in another JVM and waits for termination.<br>
   * The test class must have a main method.
   *
   * @param testClass the test class
   * @param args optional arguments
   * @return the process object
   * @throws IOException if some IO operation failed
   */
  public static Process runClass(Class<?> testClass, String... args) throws IOException {
    // add node and key in other jvm
    String javaHome = System.getProperty("java.home");
    String classPath = System.getProperty("java.class.path");
    String modulePath = System.getProperty("jdk.module.path");
    if (modulePath != null) {
      if (!classPath.endsWith(":")) {
        classPath += ":";
      }
      classPath += modulePath;    // just append the module path to the classpath
    }
    StringBuilder cmd = new StringBuilder();
    cmd.append(javaHome).append("/bin/java -cp ")
       .append(classPath).append(' ').append(testClass.getName());
    if (args != null) {
      for (String arg: args) {
        cmd.append(' ').append(arg);
      }
    }
    LOGGER.info(() -> "running: " + cmd);
    return Runtime.getRuntime().exec(cmd.toString());
  }

  /**
   * Waits for process to terminate and write stdout and stderr to the junit log.
   *
   * @param process the process
   * @throws IOException if some IO failed
   */
  public static void waitForProcess(Process process) throws IOException {
    try {
      process.waitFor();
    }
    catch (InterruptedException ex) {
      throw new AssertionFailedError("executing " + process + " failed", ex);
    }
    // collect stdout
    Reader r = new InputStreamReader(process.getInputStream());
    try (BufferedReader in = new BufferedReader(r)) {
      String line;
      while ((line = in.readLine()) != null) {
        String msg = line;
        LOGGER.info(() -> msg);
      }
    }
    // collect stderr
    r = new InputStreamReader(process.getErrorStream());
    try (BufferedReader in = new BufferedReader(r)) {
      String line;
      while ((line = in.readLine()) != null) {
        String msg = line;
        LOGGER.info(() -> msg);
      }
    }
  }

}
