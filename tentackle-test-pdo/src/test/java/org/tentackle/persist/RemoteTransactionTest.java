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

package org.tentackle.persist;

import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.dbms.Db;
import org.tentackle.log.Logger;
import org.tentackle.ns.pdo.NumberPool;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.testng.AbstractPdoTest;
import org.tentackle.pdo.testng.ServerProcessWrapper;
import org.tentackle.persist.server.TestServer;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.SessionInfo;

/**
 * Tests nested transactions of a remote session that are rolled back by an exception.
 *
 * @author harald
 */
public class RemoteTransactionTest implements DomainContextProvider {

  private static final Logger LOGGER = Logger.get(RemoteTransactionTest.class);

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


  @Test(groups = "remote1", dependsOnGroups = "local")
  public void testTransactions() throws Exception {
    Reporter.log("RemoteTransactionTest.testTransactions <br>", true);

    ServerProcessWrapper server = new ServerProcessWrapper(AbstractPdoTest.runClass(TestServer.class));
    try {
      server.awaitReady(30000);   // block until the server is listening instead of guessing with sleep
      SessionInfo sessionInfo = Pdo.createSessionInfo("client");
      session = (Db) Pdo.createSession(sessionInfo);
      context = Pdo.createDomainContext(session);
      runTx(this::transaction0);
      runTx(this::transaction1);
    }
    catch (RuntimeException ex) {
      LOGGER.severe("", ex);
      throw ex;
    }
    finally {
      if (session != null) {
        session.close();
      }
      server.stopAndWait();   // signal shutdown via stdin EOF and wait for the server to terminate
    }
  }


}
