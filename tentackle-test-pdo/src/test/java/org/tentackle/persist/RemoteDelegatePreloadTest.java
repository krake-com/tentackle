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
import org.tentackle.dbms.DbUtilities;
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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests preloading of the remote delegates.
 *
 * @author harald
 */
public class RemoteDelegatePreloadTest implements DomainContextProvider {

  private static final Logger LOGGER = Logger.get(RemoteDelegatePreloadTest.class);

  private Db session;
  private DomainContext context;

  @Override
  public DomainContext getDomainContext() {
    return context;
  }


  /**
   * Creates a number pool and rolls the transaction back.<br>
   * Fetches the delegate of {@code NumberPoolPersistenceImpl}, which is a preload candidate.
   */
  private void useNumberPool() {
    try {
      session.transaction(() -> {
        NumberPool pool = on(NumberPool.class);
        pool.setName("preloadtest");
        pool.save();
        throw new PersistenceException("do a rollback");
      });
    }
    catch (PersistenceException ex) {
      LOGGER.info("expected exception!", ex);
    }
  }


  private void openSession() {
    SessionInfo sessionInfo = Pdo.createSessionInfo("client");
    session = (Db) Pdo.createSession(sessionInfo);
    context = Pdo.createDomainContext(session);
  }

  private void closeSession() {
    if (session != null) {
      session.close();
      session = null;
      context = null;
    }
  }


  @Test(groups = "remote1", dependsOnGroups = "local")
  public void testPreloadRemoteDelegates() throws Exception {
    Reporter.log("RemoteDelegatePreloadTest.testPreloadRemoteDelegates <br>", true);

    String[] candidates = DbUtilities.getInstance().getPreloadDelegateClassNames();
    LOGGER.info("preload candidates: {0}", String.join(", ", candidates));
    assertTrue(candidates.length > 0, "no preload candidates found (missing service configuration?)");

    ServerProcessWrapper server = new ServerProcessWrapper(AbstractPdoTest.runClass(TestServer.class));
    try {
      server.awaitReady(30000);   // block until the server is listening instead of guessing with sleep

      // without preloading the delegates are fetched lazily, one roundtrip each
      openSession();
      assertEquals(session.getPreloadedDelegateCount(), 0, "nothing must be preloaded by default");
      useNumberPool();
      assertEquals(session.getPreloadedDelegateCount(), 0);
      closeSession();

      // with preloading all candidates are fetched in a single roundtrip
      openSession();
      session.preloadRemoteDelegates();
      assertEquals(session.getPreloadedDelegateCount(), candidates.length,
                   "the server must provide a delegate for each candidate");

      // using a preloaded class consumes its delegate instead of doing another roundtrip
      useNumberPool();
      assertEquals(session.getPreloadedDelegateCount(), candidates.length - 1,
                   "the delegate for NumberPoolPersistenceImpl must be taken from the preloaded delegates");

      // preloaded delegates work exactly like lazily fetched ones
      useNumberPool();
      assertEquals(session.getPreloadedDelegateCount(), candidates.length - 1);

      // a clone is a session of its own and must request preloading explicitly
      Db clone = session.clone();
      try {
        assertEquals(clone.getPreloadedDelegateCount(), 0, "a clone must not inherit the preload request");
        clone.preloadRemoteDelegates();
        assertEquals(clone.getPreloadedDelegateCount(), candidates.length);
      }
      finally {
        clone.close();
      }
    }
    catch (RuntimeException ex) {
      LOGGER.severe("", ex);
      throw ex;
    }
    finally {
      closeSession();
      server.stopAndWait();   // signal shutdown via stdin EOF and wait for the server to terminate
    }
  }

}
