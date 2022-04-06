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
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

import org.tentackle.app.AbstractApplication;
import org.tentackle.dbms.Db;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.test.DbTestUtilities;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;
import org.tentackle.sql.Backend;

import java.util.Properties;

import static org.testng.Assert.fail;

/**
 * Base class for Junit5 tests that must run as an application.
 *
 * @author harald
 */
public abstract class TestApplication extends AbstractApplication {

  private static TestApplication application;   // necessary because @AfterAll must annotate a static method in Junit

  @AfterAll
  public static synchronized void tearDownClass() {
    if (application != null) {
      Session session = application.getSession();
      if (session != null) {
        session.close();
        Session.setCurrentSession(null);
        application.unregister();
      }
      application = null;
    }
  }



  /**
   * Creates a test application.
   *
   * @param name the application name
   * @param version the application version
   */
  public TestApplication(String name, String version) {
    super(name, version);

    synchronized (TestApplication.class) {
      if (application != null) {
        // misbehaving tests running in parallel? (still experimental Junit feature?)
        throw new AssertionFailedError("another TestApplication is still running: " + application.getClass().getName());
      }
      application = this;
    }

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
      setProperties(new Properties());
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

  @Override
  protected void startup() {
    // not invoked
  }

  @Override
  public <U extends PersistentDomainObject<U>> U getUser(DomainContext context, long userId) {
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
      fail("populating the database failed", e);
    }
  }

  /**
   * Populates the database with test data.
   */
  protected void populateDatabase() {
    // default does nothing
  }

}
