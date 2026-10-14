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

package org.tentackle.dbms.trip;

import org.testng.annotations.Test;

import org.tentackle.dbms.ConnectionManager;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.DbPool;
import org.tentackle.dbms.DefaultConnectionManager;
import org.tentackle.session.DefaultSessionInfo;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;
import org.tentackle.trip.Registry;
import org.tentackle.trip.Transport;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests that a {@link RemoteDbSessionImpl} is closed only once, even if the client's logout
 * races with the {@link RemoteDbSessionCleanupThread}.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class RemoteDbSessionCloseTest {

  private static final long AWAIT_SECONDS = 10;

  /**
   * The pooled session for the next {@link TestSession}.<br>
   * Static, since {@link RemoteDbSessionImpl#openDb()} is invoked from the super constructor,
   * before the fields of the subclass are initialized.
   */
  private static volatile Db nextDb;


  /**
   * A remote session without a TRIP-server, authentication or client.
   */
  private static class TestSession extends RemoteDbSessionImpl {

    private TestSession(RemoteDbConnectionImpl con, SessionInfo sessionInfo) {
      super(con, sessionInfo, sessionInfo);
    }

    @Override
    protected Db openDb() {
      return nextDb;
    }

    @Override
    public void verifySessionInfo(SessionInfo sessionInfo) {
      // no authentication
    }

    @Override
    protected int determineTimeout(Session session, SessionInfo clientInfo, SessionInfo serverInfo) {
      return 1;
    }
  }


  @Test(timeOut = 60000)
  public void concurrentCloseReturnsSessionOnlyOnce() throws Exception {
    AtomicInteger putCount = new AtomicInteger();
    CountDownLatch firstPutEntered = new CountDownLatch(1);
    CountDownLatch releaseFirstPut = new CountDownLatch(1);

    SessionInfo sessionInfo = new DefaultSessionInfo();
    ConnectionManager conMgr = new DefaultConnectionManager("remote-close-test", 2, 2, 1, 0, 0);
    DbPool pool = new DbPool("remote-close-pool", conMgr, sessionInfo, 0, 1, 1, 0, 2, 0, 0) {
      @Override
      public void put(Session session) {
        if (putCount.incrementAndGet() == 1) {
          // hold the first closer within closeDb, so the second one runs concurrently
          firstPutEntered.countDown();
          try {
            releaseFirstPut.await(AWAIT_SECONDS, TimeUnit.SECONDS);
          }
          catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
        }
        super.put(session);
      }
    };

    try {
      nextDb = (Db) pool.get();
      TestSession session = new TestSession(new RemoteDbConnectionImpl(null, createTransport()) {
        @Override
        public RemoteDbSession createSession(SessionInfo clientInfo) {
          throw new UnsupportedOperationException();
        }
      }, sessionInfo);
      assertTrue(session.isOpen());

      AtomicReference<Throwable> failure = new AtomicReference<>();
      // the client's logout
      Thread client = new Thread(() -> {
        try {
          session.closeDb(false);
        }
        catch (Throwable t) {
          failure.compareAndSet(null, t);
        }
      });
      // the cleanup thread closing a timed-out session
      Thread cleanup = new Thread(() -> {
        try {
          session.closeDb(true);
        }
        catch (Throwable t) {
          failure.compareAndSet(null, t);
        }
      });

      client.start();
      assertTrue(firstPutEntered.await(AWAIT_SECONDS, TimeUnit.SECONDS), "closeDb did not return the session to the pool");
      cleanup.start();

      // wait until the second closer either returned the session as well (the bug) or waits for the first one
      long deadline = System.currentTimeMillis() + AWAIT_SECONDS * 1000;
      while (putCount.get() < 2 && cleanup.getState() != Thread.State.BLOCKED &&
             System.currentTimeMillis() < deadline) {
        Thread.sleep(10);
      }

      releaseFirstPut.countDown();
      client.join(AWAIT_SECONDS * 1000);
      cleanup.join(AWAIT_SECONDS * 1000);

      assertNull(failure.get(), "closing failed: " + failure.get());
      assertEquals(putCount.get(), 1, "the session must be returned to the pool exactly once");
      assertFalse(session.isOpen());
      assertTrue(session.getClosedSince() > 0);
    }
    finally {
      nextDb = null;
      pool.shutdown();
      conMgr.shutdown();
    }
  }


  /**
   * Creates a transport whose registry knows no delegates.
   *
   * @return the transport
   */
  private static Transport createTransport() {
    Registry registry = createStub(Registry.class);
    return (Transport) Proxy.newProxyInstance(Transport.class.getClassLoader(), new Class<?>[] { Transport.class },
                                               (_, method, _) -> method.getName().equals("getRegistry") ?
                                                                 registry : defaultValue(method.getReturnType()));
  }

  @SuppressWarnings("unchecked")
  private static <T> T createStub(Class<T> clazz) {
    return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz },
                                      (_, method, _) -> defaultValue(method.getReturnType()));
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive() || type == void.class) {
      return null;
    }
    // the zero value of the primitive type, i.e. the default of a one-element array
    return Array.get(Array.newInstance(type, 1), 0);
  }

}
