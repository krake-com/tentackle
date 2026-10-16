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

import org.testng.Reporter;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionFactory;
import org.tentackle.session.SessionInfoFactory;
import org.tentackle.sql.Backend;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import static org.testng.Assert.fail;


/**
 * Base class for persistence tests.
 *
 * @author harald
 */
@SuppressWarnings("exports")
public abstract class AbstractDbTest {

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
   * System property telling H2 which address to bind its server to.
   * <p>
   * By default H2 binds the server of an {@code AUTO_SERVER=TRUE} database to the host's
   * network address and writes that address into the lock file. A second JVM then connects to
   * it from what H2 considers a remote address and gets rejected with a bare {@code EOF},
   * because remote access is disabled unless explicitly allowed. Pinning the address to
   * localhost keeps the connection local and is what makes the multi-JVM tests work.
   * <p>
   * The property must be set before the H2 driver is loaded, hence the static initializer,
   * and it must be handed to every JVM spawned by {@link #runClass(Class)}.
   */
  private static final String H2_BIND_ADDRESS_PROPERTY = "h2.bindAddress";

  static {
    if (System.getProperty(H2_BIND_ADDRESS_PROPERTY) == null) {
      System.setProperty(H2_BIND_ADDRESS_PROPERTY, "localhost");
    }
  }

  private static Session session;           // session for all methods in all test classes

  private final TransactionType txType;     // transaction handling
  private long txVoucher;                   // transaction voucher for current test class

  public AbstractDbTest(TransactionType txType) {
    this.txType = txType;
  }

  public AbstractDbTest() {
    this(TransactionType.CLASS);    // per class
  }


  /**
   * Opens the session and, if the test owns the schema, creates the tables.<br>
   * The tracker is started afterwards, so that it finds its modification table in place.
   *
   * @throws Exception if failed
   * @see #createDatabaseTables(Db)
   */
  @BeforeSuite
  public void openSessionsAndStartPdoTracker() throws Exception {
    session = openSession();

    if (session instanceof Db db && isSchemaOwnedByTest(db)) {
      createDatabaseTables(db);
    }

    ModificationTracker tracker = ModificationTracker.getInstance();
    tracker.setSession(session.clone(null));
    tracker.setSleepInterval(500);   // fast polling for tests
    tracker.start();
  }

  @AfterSuite
  public void closeSessionsAndTerminatePdoTracker() throws Exception {
    if (session != null) {
      ModificationTracker.getInstance().terminate();
      session.close();
    }
  }


  /**
   * Opens the session.
   *
   * @return the thread-local session
   */
  protected Session openSession() {
    Session s;
    try {
      s = SessionFactory.getInstance().create(SessionInfoFactory.getInstance().create());
    }
    catch (PersistenceException ex) {
      // no database? wrong database? whatever: testing environment incomplete
      throw new SkipException("no backend found -> no tests", ex);
    }
    s.makeCurrent();
    return s;
  }


  /**
   * Returns whether the tests own the database schema and must create it themselves.<br>
   * This is the case for H2, which is the default test backend: both the in-memory and the
   * file-backed AUTO_SERVER variants start out empty (or stale, if a previous run left a file
   * behind). A shared database such as the PostgreSQL CI instance is prepared out-of-band and
   * must never be dropped by a test run.
   *
   * @param db the low-level database session
   * @return true if {@link #createDatabaseTables(Db)} should be invoked
   */
  protected boolean isSchemaOwnedByTest(Db db) {
    return "H2".equals(db.getBackend().getName());
  }


  /**
   * Creates the database tables of this module.<br>
   * Runs the DDL generated at build time by the SQL maven plugin, which is on the test
   * classpath as {@code META-INF/sql/<backend>/createttmodel.sql}.
   * <p>
   * Notice that this module sits below {@code tentackle-test-pdo} in the reactor and thus
   * cannot use its {@code DbTestUtilities}, which would build the same script from the model.
   * <p>
   * Only invoked for H2 (see {@link #isSchemaOwnedByTest(Db)}), whose {@code DROP ALL OBJECTS}
   * this relies on.
   *
   * @param db the low-level database session
   */
  protected void createDatabaseTables(Db db) {
    // the generated script holds no DROP statements, so wipe whatever a previous run left over
    db.runScript("drop", "DROP ALL OBJECTS;");
    db.runScript("populate", loadCreateScript(db.getBackend()));
  }


  /**
   * Loads the generated DDL for the given backend from the test classpath.
   *
   * @param backend the backend
   * @return the SQL script
   */
  private String loadCreateScript(Backend backend) {
    String resource = "/META-INF/sql/" + backend.getName() + "/createttmodel.sql";
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
   * @return the exit value
   * @throws IOException if some IO operation failed
   */
  public int runInOtherJVM(Class<?> testClass) throws IOException {
    Process process = runClass(testClass);
    waitForProcess(process);
    return process.exitValue();
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
    String modulePath = System.getProperty("jdk.module.path");
    if (modulePath != null) {
      if (!classPath.endsWith(File.pathSeparator)) {
        classPath += File.pathSeparator;
      }
      classPath += modulePath;    // just append the module path to the classpath
    }
    String[] cmd = {
        javaHome + File.separator + "bin" + File.separator + "java",
        // the child connects to the same H2 AUTO_SERVER database and needs the same binding
        "-D" + H2_BIND_ADDRESS_PROPERTY + "=" + System.getProperty(H2_BIND_ADDRESS_PROPERTY),
        "-cp",
        classPath,
        testClass.getName()
    };
    Reporter.log("running: " + Arrays.toString(cmd) + "<br>");
    return Runtime.getRuntime().exec(cmd);
  }

  /**
   * Waits for the process to terminate and writes its stdout and stderr to the reporter log.<br>
   * Both streams are drained while the process is running. Otherwise, a process writing more than
   * the pipe buffer can hold would block forever (on Windows, the buffer holds only a few kilobytes).
   *
   * @param process the process
   * @throws IOException if some IO failed
   */
  public static void waitForProcess(Process process) throws IOException {
    // drain stderr in another thread while stdout is read in this one
    FutureTask<List<String>> stderr = new FutureTask<>(() -> readLines(process.getErrorStream()));
    Thread.ofPlatform().daemon().name("stderr-drain").start(stderr);
    List<String> lines = readLines(process.getInputStream());
    try {
      process.waitFor();
      lines.addAll(stderr.get());
    }
    catch (InterruptedException ex) {
      fail("executing " + process + " failed<br>", ex);
    }
    catch (ExecutionException ex) {
      throw ex.getCause() instanceof IOException iox ? iox : new IOException(ex.getCause());
    }
    for (String line : lines) {
      Reporter.log(line + "<br>");
    }
  }

  /**
   * Reads all lines from a stream until EOF.
   *
   * @param stream the stream
   * @return the lines
   * @throws IOException if reading failed
   */
  private static List<String> readLines(InputStream stream) throws IOException {
    List<String> lines = new ArrayList<>();
    try (BufferedReader in = new BufferedReader(new InputStreamReader(stream))) {
      String line;
      while ((line = in.readLine()) != null) {
        lines.add(line);
      }
    }
    return lines;
  }

}
