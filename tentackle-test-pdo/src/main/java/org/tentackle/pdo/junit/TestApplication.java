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

package org.tentackle.pdo.junit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

import org.tentackle.app.AbstractApplication;
import org.tentackle.dbms.Db;
import org.tentackle.misc.Identifiable;
import org.tentackle.misc.PropertiesUtilities;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.test.DbTestUtilities;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;
import org.tentackle.sql.Backend;

/**
 * Base class for JUnit 5 tests that must run as an application.
 * <p>
 * Works like its TestNG counterpart {@code org.tentackle.pdo.testng.TestApplication}:
 * one test instance per class ({@link TestInstance.Lifecycle#PER_CLASS}), the application
 * is set up before and torn down after all test methods of the class.
 * Test classes must not run in parallel.
 * <p>
 * Unlike {@code AbstractPdoTest}, the session is opened per test class. If the backend is in-memory,
 * the tables are created from the model for each class. Therefore, the in-memory database must not
 * outlive the session (no {@code DB_CLOSE_DELAY=-1} in the URL, no other session open on it), or the
 * second class fails to create the tables again.
 * <p>
 * The modification tracker gets the session, unless disabled via
 * {@link AbstractApplication#DISABLE_MODIFICATION_TRACKER}, but is not started.
 * There is no automatic transaction handling.
 *
 * @author harald
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class TestApplication extends AbstractApplication {

  /**
   * Creates a test application.
   *
   * @param name the application name, null for default name
   * @param version the application version, null for default version
   */
  public TestApplication(String name, String version) {
    super(name, version);
  }

  /**
   * Opens the session and registers the application.<br>
   * Aborts the tests if no backend is available.
   *
   * @throws Exception if failed
   */
  @BeforeAll
  public void setUpClass() throws Exception {
    SessionInfo sessionInfo = Pdo.createSessionInfo();
    sessionInfo.applyProperties();
    Session session;
    try {
      session = Pdo.createSession(sessionInfo);
    }
    catch (PersistenceException ex) {
      // no database? wrong database? whatever: testing environment incomplete
      throw new TestAbortedException("no backend found -> no tests", ex);
    }
    Session.setCurrentSession(session);

    boolean populate = false;
    if (session instanceof Db db) {
      Backend backend = db.getBackend();
      if (backend.isDatabaseInMemory(session.getUrl())) {
        createDatabaseTables(db);
        populate = true;
      }
    }

    if (getProperties() == null) {
      setProperties(PropertiesUtilities.getInstance().create());
    }
    applyProperties(sessionInfo.getProperties());
    if (!getProperties().containsKey(DISABLE_MODIFICATION_TRACKER)) {
      ModificationTracker.getInstance().setSession(session);
    }

    setDomainContext(Pdo.createDomainContext(session));
    register();

    initializeScripting();

    if (populate) {
      populateDatabase();
    }
  }

  /**
   * Closes the session and unregisters the application.
   *
   * @throws Exception if failed
   */
  @AfterAll
  public void tearDownClass() throws Exception {
    Session session = getSession();
    if (session != null) {
      session.close();
      Session.setCurrentSession(null);
      unregister();
    }
  }

  /**
   * Not invoked: the application is set up by {@link #setUpClass()}.
   */
  @Override
  protected void startup() {
    // not invoked
  }

  /**
   * Returns no user.<br>
   * Override if the tests need users.
   *
   * @param <U> the user type
   * @param context the domain context
   * @param userId the user's object ID
   * @return always null
   */
  @Override
  public <U extends Identifiable> U getUser(DomainContext context, long userId) {
    return null;
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
   * Populates the database with test data.<br>
   * Invoked for each test class after the tables were created, if the database is in-memory.
   * The default implementation does nothing.
   */
  protected void populateDatabase() {
    // default does nothing
  }

}
