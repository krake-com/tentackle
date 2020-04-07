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

package org.tentackle.test.pdo;

import org.testng.Reporter;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.Pdo;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import static org.testng.Assert.fail;

/**
 * Base class for tests on PDO level.
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


  private static Session session;           // session for all methods in all test classes
  private static DomainContext context;     // domain context for all methods in all test classes

  private final TransactionType txType;     // transaction handling
  private final boolean commit;             // true if commit transaction, else rollback (default)
  private long txVoucher;                   // transaction voucher for current test class


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
   * Opens the sessions and starts the modification tracker.
   *
   * @throws Exception if failed
   */
  @BeforeSuite(alwaysRun = true)
  public void openSessionsAndStartModificationTracker() throws Exception {
    session = openSession();
    context = createDomainContext();

    ModificationTracker tracker = ModificationTracker.getInstance();
    tracker.setSession(session.clone());
    tracker.setSleepInterval(500);   // fast polling for tests
    tracker.start();
  }

  /**
   * Terminates the modification tracker and closes the sessions.
   *
   * @throws Exception if failed
   */
  @AfterSuite(alwaysRun = true)
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
  @BeforeClass(alwaysRun = true)
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
  @AfterClass(alwaysRun = true)
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
  @BeforeMethod(alwaysRun = true)
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
  @AfterMethod(alwaysRun = true)
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
   * The testclass must have a main method.
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
      throw new SkipException("no backend found -> no tests", ex);
    }
    s.makeCurrent();
    return s;
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
   * The testclass must have a main method.
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
    Reporter.log("running: " + cmd + "<br>");
    return Runtime.getRuntime().exec(cmd.toString());
  }

  /**
   * Waits for process to terminate and write stdout and stderr to the reporter log.
   *
   * @param process the process
   * @throws IOException if some IO failed
   */
  public static void waitForProcess(Process process) throws IOException {
    try {
      process.waitFor();
    }
    catch (InterruptedException ex) {
      fail("executing " + process + " failed<br>", ex);
    }
    // collect stdout
    Reader r = new InputStreamReader(process.getInputStream());
    try (BufferedReader in = new BufferedReader(r)) {
      String line;
      while ((line = in.readLine()) != null) {
        Reporter.log(line + "<br>");
      }
    }
    // collect stderr
    r = new InputStreamReader(process.getErrorStream());
    try (BufferedReader in = new BufferedReader(r)) {
      String line;
      while ((line = in.readLine()) != null) {
        Reporter.log(line + "<br>");
      }
    }
  }

}
