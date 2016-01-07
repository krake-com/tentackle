/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.test;

import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoTracker;
import org.tentackle.pdo.Session;
import org.tentackle.pdo.SessionInfo;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

/**
 * Abstract PDO test.
 * <p>
 * Opens a session, runs a transaction, rolls back and closes the session.
 *
 * @author harald
 */
public abstract class AbstractPdoTest implements DomainContextProvider {

  private Session session;
  private DomainContext context;
  private long txVoucher;

  @BeforeClass
  public void setUpClass() throws Exception {
    SessionInfo sessionInfo = Pdo.createSessionInfo(null, null, "test-backend");
    session = Pdo.createSession(sessionInfo);
    session.open();
    Session.setCurrentSession(session);
    PdoTracker.getInstance().setSession(session);
    context = Pdo.createDomainContext();
    txVoucher = session.begin("namesource-test");
  }

  @AfterClass
  public void tearDownClass() throws Exception {
    session.rollback(txVoucher);
    session.close();
    Session.setCurrentSession(null);
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
   * Gets the domain context.
   *
   * @return the domain context
   */
  @Override
  public DomainContext getDomainContext() {
    return context;
  }

}
