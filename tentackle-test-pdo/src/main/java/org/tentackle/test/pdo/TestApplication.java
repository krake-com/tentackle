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

package org.tentackle.test.pdo;

import org.tentackle.app.AbstractApplication;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

/**
 * Base class for tests that must run as an application.
 *
 * @author harald
 */
public abstract class TestApplication extends AbstractApplication {

  private Session session;
  private DomainContext context;

  /**
   * Creates a test application.
   *
   * @param name the application name
   */
  public TestApplication(String name) {
    super(name);
  }

  @BeforeClass(alwaysRun = true)
  public void setUpClass() throws Exception {
    SessionInfo sessionInfo = Pdo.createSessionInfo();
    try {
      session = Pdo.createSession(sessionInfo);
    }
    catch (PersistenceException ex) {
      // no database? wrong database? whatever: testing environment incomplete
      throw new SkipException("no backend found -> no tests");
    }
    Session.setCurrentSession(session);
    ModificationTracker.getInstance().setSession(session);
    context = Pdo.createDomainContext(session);
    register();
  }

  @AfterClass(alwaysRun = true)
  public void tearDownClass() throws Exception {
    if (session != null) {
      session.close();
      Session.setCurrentSession(null);
      unregister();
    }
  }

  @Override
  public DomainContext getDomainContext() {
    return context;
  }

  @Override
  public boolean isServer() {
    return false;
  }

  @Override
  protected void startup() {
  }

  @Override
  public <U extends PersistentDomainObject<U>> U getUser(DomainContext context, long userId) {
    return null;
  }

}
