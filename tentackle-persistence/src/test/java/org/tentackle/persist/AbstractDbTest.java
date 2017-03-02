/*
 * Tentackle - http://www.tentackle.org
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoTracker;
import org.tentackle.pdo.Session;
import org.testng.Reporter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import static org.testng.Assert.*;


/**
 * Base class for persistence tests.
 *
 * @author harald
 */
public abstract class AbstractDbTest {

  /**
   * The transaction handling for the test class.
   */
  public static enum TransactionType {
    /** transaction per test class. */
    CLASS,

    /** transaction per test method. */
    METHOD,

    /** no automatic transaction handling. */
    NONE
  }

  private TransactionType txType;           // transaction handling
  private static Session session;           // session for all methods in all test classes
  private long txVoucher;                   // transaction voucher for current test class

  public AbstractDbTest(TransactionType txType) {
    this.txType = txType;
  }

  public AbstractDbTest() {
    this(TransactionType.CLASS);    // per class
  }


  @BeforeSuite
  public static void openSessionsAndStartPdoTracker() throws Exception {
    session = Pdo.createSession();
    session.open();
    session.makeCurrent();

    PdoTracker tracker = PdoTracker.getInstance();
    tracker.setSession(session.clone());
    tracker.setSleepInterval(500);   // fast polling for tests
    tracker.start();
  }

  @AfterSuite
  public static void closeSessionsAndTerminatePdoTracker() throws Exception {
    PdoTracker.getInstance().terminate();
    session.close();
  }

  @BeforeClass
  public void beforeClass() throws Exception {
    if (txType == TransactionType.CLASS) {
      beginTransaction();
    }
  }

  @AfterClass
  public void afterClass() throws Exception {
    if (txType == TransactionType.CLASS) {
      rollbackTransaction();
    }
  }

  @BeforeMethod
  public void beforeMethod() throws Exception {
    if (txType == TransactionType.METHOD) {
      beginTransaction();
    }
  }

  @AfterMethod
  public void afterMethod() throws Exception {
    if (txType == TransactionType.METHOD) {
      rollbackTransaction();
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


  /**
   * Gets the current session.
   *
   * @return the open session
   */
  public Db getSession() {
    return (Db) session;
  }


  /**
   * Runs the given class in another JVM.<br>
   * The testclass must have a main method.
   *
   * @param testClass the test class
   * @throws IOException if some IO operation failed
   */
  public void runInOtherJVM(Class<?> testClass) throws IOException {
    Process process = runClass(testClass);
    waitForProcess(process);
  }


  /**
   * Runs the given class in another JVM and waits for termination.<br>
   * The testclass must have a main method.
   *
   * @param testClass the test class
   * @throws IOException if some IO operation failed
   */
  public static Process runClass(Class<?> testClass) throws IOException {
    // add node and key in other jvm
    String javaHome = System.getProperty("java.home");
    String classPath = System.getProperty("java.class.path");
    String cmd = javaHome + "/bin/" + "java -classpath " + classPath + " " + testClass.getName();
    Reporter.log("running: " + cmd + "<br>");
    return Runtime.getRuntime().exec(cmd);
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
