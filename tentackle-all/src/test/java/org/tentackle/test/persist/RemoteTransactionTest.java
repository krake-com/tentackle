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

package org.tentackle.test.persist;

import org.tentackle.dbms.Db;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.ns.pdo.NumberPool;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.Pdo;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.SessionInfo;
import org.tentackle.test.pdo.AbstractPdoTest;
import org.tentackle.test.pdo.TestServer;
import org.testng.annotations.Test;



/**
 * Tests for persisted preferences.
 * <p>
 * IMPORTANT: must be the last test in suite!
 *
 * @author harald
 */
public class RemoteTransactionTest implements DomainContextProvider {

  /**
   * The logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteTransactionTest.class);

  private Db session;
  private DomainContext context;

  @Override
  public DomainContext getDomainContext() {
    return context;
  }


  private void transaction02() {
    session.transaction(() -> {
      NumberPool pool = on(NumberPool.class);
      pool.setName("test");
      pool.save();
      throw new PersistenceException("do a rollback");
    });
  }

  private void transaction01() {
    session.transaction(() -> {
      transaction02();
      return null;
    });
  }

  private void transaction0() {
    session.transaction(() -> {
      transaction01();
      return null;
    });
  }


  private void transaction12() {
    session.transaction(() -> {
      NumberPool pool = on(NumberPool.class);
      pool.setName("test");
      pool.save();
      pool = on(NumberPool.class);
      pool.setName("test");
      pool.save();    // ConstraintException -> rollback
      return null;
    });
  }

  private void transaction11() {
    session.transaction(() -> {
      transaction12();
      return null;
    });
  }

  private void transaction1() {
    session.transaction(() -> {
      transaction11();
      return null;
    });
  }



  private void runTx(Runnable runnable) {
    try {
      runnable.run();
    }
    catch (PersistenceException ex) {
      LOGGER.info("expected exception!", ex);
    }
  }


  @Test
  public void testTransactions() throws Exception {
    Process process = AbstractPdoTest.runClass(TestServer.class, "duration=4000");
    Thread.sleep(1500);
    try {
      SessionInfo sessionInfo = Pdo.createSessionInfo("client");
      session = (Db) Pdo.createSession(sessionInfo);
      session.open();
      context = Pdo.createDomainContext(session);
      runTx(this::transaction0);
      runTx(this::transaction1);
    }
    catch (Exception ex) {
      LOGGER.severe("", ex);
      throw ex;
    }
    finally {
      if (session != null) {
        session.close();
      }
      AbstractPdoTest.waitForProcess(process);
    }
  }


}
