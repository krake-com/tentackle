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
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;

import java.util.Properties;

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
      if (application.session != null) {
        application.session.close();
        Session.setCurrentSession(null);
        application.unregister();
      }
      application = null;
    }
  }


  private Session session;
  private DomainContext context;

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
    try {
      session = Pdo.createSession(sessionInfo);
    }
    catch (PersistenceException ex) {
      // no database? wrong database? whatever: testing environment incomplete
      throw new TestAbortedException("no backend found -> no tests", ex);
    }
    Session.setCurrentSession(session);

    if (getProperties() == null) {
      setProperties(new Properties());
    }
    applyProperties(sessionInfo.getProperties());
    if (!getProperties().containsKey(DISABLE_MODIFICATION_TRACKER)) {
      ModificationTracker.getInstance().setSession(session);
    }

    context = Pdo.createDomainContext(session);
    register();
  }

  @Override
  public DomainContext getDomainContext() {
    return context;
  }

  @Override
  protected void startup() {
    // not invoked
  }

  @Override
  public <U extends PersistentDomainObject<U>> U getUser(DomainContext context, long userId) {
    return null;
  }

}
