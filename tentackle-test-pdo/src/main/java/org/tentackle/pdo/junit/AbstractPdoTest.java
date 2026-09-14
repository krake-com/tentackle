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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

import org.tentackle.dbms.Db;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.test.DbTestUtilities;
import org.tentackle.script.ScriptFactory;
import org.tentackle.script.ScriptingLanguage;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.sql.Backend;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Set;


/**
 * Base class for Junit5 tests on PDO level.
 * <p>
 * Works like its TestNG counterpart {@code org.tentackle.pdo.testng.AbstractPdoTest}:
 * <ul>
 *   <li>one test instance per class ({@link TestInstance.Lifecycle#PER_CLASS}), so
 *   the lifecycle methods are instance methods and the transaction may span the whole class</li>
 *   <li>the session and the modification tracker are opened once for all test classes and closed
 *   when the test run ends (TestNG's {@code @BeforeSuite}/{@code @AfterSuite})</li>
 *   <li>the tests are aborted (i.e. skipped) if no backend is available</li>
 * </ul>
 * Test classes must not run in parallel.
 *
 * @author harald
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(AbstractPdoTest.SuiteExtension.class)
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


  /**
   * Opens the session once for all test classes and closes it when the test run ends.<br>
   * Also marks the rolled back statements to be logged if a test method fails.
   */
  public static class SuiteExtension implements BeforeAllCallback, TestExecutionExceptionHandler {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(SuiteExtension.class);

    private static RuntimeException setupFailure;   // != null if opening the session failed

    /**
     * Creates the extension.
     */
    public SuiteExtension() {
      // see -Xlint:missing-explicit-ctor
    }

    @Override
    public void beforeAll(ExtensionContext context) {
      synchronized (SuiteExtension.class) {
        if (setupFailure != null) {
          throw setupFailure;   // tried once already: skip or fail the remaining classes as well
        }
        AbstractPdoTest test = (AbstractPdoTest) context.getRequiredTestInstance();
        context.getRoot().getStore(NAMESPACE).computeIfAbsent(SuiteExtension.class, _ -> {
          try {
            test.openSessionsAndStartModificationTracker();
          }
          catch (RuntimeException rex) {
            setupFailure = rex;
            throw rex;
          }
          catch (Exception ex) {
            setupFailure = new IllegalStateException("opening the session failed", ex);
            throw setupFailure;
          }
          // closed by Junit when the test run ends
          return (AutoCloseable) test::closeSessionsAndTerminateModificationTracker;
        }, AutoCloseable.class);
      }
    }

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
      if (context.getRequiredTestInstance() instanceof AbstractPdoTest test) {
        test.setRollbackLogged(true);    // log statements rolled back if the test failed
      }
      throw throwable;
    }
  }


  private static Session session;           // session for all methods in all test classes
  private static DomainContext context;     // domain context for all methods in all test classes

  private final TransactionType txType;     // transaction handling
  private final boolean commit;             // true if commit transaction, else rollback (default)
  private boolean rollbackLogged;           // true to log the rollback, even if the test succeeds
  private long txVoucher;                   // transaction voucher for the current test class


  /**
   * Creates a test.
   *
   * @param txType the transaction type
   * @param commit true if commit transaction, else rollback (default for regular tests)
   */
  public AbstractPdoTest(TransactionType txType, boolean commit) {
    this.txType = txType;
    this.commit = commit;
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
   * Returns whether statements rolled back are always logged.
   *
   * @return true to log even if the test succeeds, false is default
   */
  public boolean isRollbackLogged() {
    return rollbackLogged;
  }

  /**
   * Sets whether to log the statements rolled back.<br>
   * By default, the statements are not logged if the test succeeds.
   * Sometimes, however, it is nice to log the statements executed during the test.
   * <p>
   * Notice that the flag is reset to false when the transaction ends.
   *
   * @param rollbackLogged true to log the statements, default is false
   */
  public void setRollbackLogged(boolean rollbackLogged) {
    this.rollbackLogged = rollbackLogged;
  }

  /**
   * Opens the sessions and starts the modification tracker.<br>
   * If the database is in-memory, it will be populated before the tracker is started.
   * <p>
   * Invoked once before the first test class by the {@link SuiteExtension}.
   *
   * @throws Exception if failed
   * @see #createDatabaseTables(Db)
   */
  public void openSessionsAndStartModificationTracker() throws Exception {
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

    // set the default scripting language, if not already set and exactly one provided
    if (ScriptFactory.getInstance().getDefaultLanguage() == null) {
      Set<ScriptingLanguage> languages = ScriptFactory.getInstance().getLanguages();
      if (languages.size() == 1) {
        ScriptFactory.getInstance().setDefaultLanguage(languages.iterator().next());
      }
    }

    ModificationTracker tracker = ModificationTracker.getInstance();
    tracker.setSession(session.clone(null));
    tracker.setSleepInterval(500);   // fast polling for tests
    tracker.start();

    if (populate) {
      populateDatabase();
    }
  }

  /**
   * Terminates the modification tracker and closes the sessions.
   * <p>
   * Invoked once when the test run ends.
   *
   * @throws Exception if failed
   */
  public void closeSessionsAndTerminateModificationTracker() throws Exception {
    if (session != null) {
      ModificationTracker.getInstance().terminate();
      session.close();
    }
  }

  /**
   * Begins the transaction if type is {@link TransactionType#CLASS}.
   *
   * @throws Exception if failed
   */
  @BeforeAll
  public void beforeClass() throws Exception {
    if (txType == TransactionType.CLASS) {
      beginTransaction();
    }
  }

  /**
   * Ends the transaction if type is {@link TransactionType#CLASS}.
   *
   * @throws Exception if failed
   */
  @AfterAll
  public void afterClass() throws Exception {
    if (txType == TransactionType.CLASS) {
      endTransaction();
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
   * Returns whether the transaction should be committed or rolled back.
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
      throw new TestAbortedException("no backend found -> no tests", ex);
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
    return Pdo.createDomainContext();   // thread-local
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
        if (rollbackLogged) {
          session.rollback(txVoucher);
        }
        else {
          // don't log rollback if the test succeeds
          session.rollbackSilently(txVoucher);
        }
      }
      txVoucher = 0;
      rollbackLogged = false;
    }
  }

  /**
   * Invokes the pre-commits, if any.<br>
   * Useful for special test scenarios, since tests are usually rolled back.
   * Must be invoked explicitly from within the test.
   */
  protected void runPreCommits() {
    if (session instanceof Db db) {
      db.runPreCommits();
    }
  }

  /**
   * Invokes the pre-commits, if any.<br>
   * Useful for special test scenarios, since tests are usually rolled back.
   * Must be invoked explicitly from within the test.
   */
  protected void runPostCommits() {
    if (session instanceof Db db) {
      db.runPostCommits();
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
      classPath += modulePath;    // append the module path to the classpath
    }
    String[] cmd = {
        javaHome + File.separator + "bin" + File.separator + "java",
        "-cp",
        classPath,
        testClass.getName()
    };
    if (args != null && args.length > 0) {
      String[] aCmd = Arrays.copyOf(cmd, cmd.length + args.length);
      System.arraycopy(args, 0, aCmd, cmd.length, args.length);
      cmd = aCmd;
    }
    System.out.println("running: " + Arrays.toString(cmd));
    return Runtime.getRuntime().exec(cmd);
  }

  /**
   * Waits for the process to terminate and write stdout and stderr to the console.
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
        System.out.println(line);
      }
    }
    // collect stderr
    r = new InputStreamReader(process.getErrorStream());
    try (BufferedReader in = new BufferedReader(r)) {
      String line;
      while ((line = in.readLine()) != null) {
        System.out.println(line);
      }
    }
  }

}
