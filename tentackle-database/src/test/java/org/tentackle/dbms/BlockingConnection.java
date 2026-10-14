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

import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendVersionInfo;

import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * A managed connection whose I/O can be made to block, like against an unresponsive database server.
 *
 * @author harald
 */
class BlockingConnection extends ManagedConnection {

  static final long AWAIT_MILLIS = 10000;

  /**
   * Blocks a thread once, until released.
   */
  static class Blocker {

    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);

    void block() {
      entered.countDown();
      // not interruptible, like a lengthy network operation
      for (;;) {
        try {
          if (released.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS)) {
            return;
          }
        }
        catch (InterruptedException ex) {
          // keep on waiting
        }
      }
    }

    /**
     * Waits until a thread is blocked.
     */
    void awaitBlocked() throws InterruptedException {
      assertTrue(entered.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS), "the operation to block was not invoked");
    }

    /**
     * Releases the blocked thread.
     */
    void release() {
      released.countDown();
    }
  }


  /**
   * Runs an action in another thread and asserts that it completes while some other thread is blocked.
   *
   * @param what what the action does, for the failure message
   * @param blocker the blocker of the other thread, released before returning
   * @param action the action
   */
  static void assertNotBlocked(String what, Blocker blocker, Runnable action) throws InterruptedException {
    Thread thread = new Thread(action, what);
    thread.setDaemon(true);
    thread.start();
    thread.join(2000);
    boolean blocked = thread.isAlive();
    blocker.release();
    thread.join(AWAIT_MILLIS);
    assertFalse(blocked, what + " was blocked by the I/O of another thread");
  }


  private final AtomicReference<Blocker> closeBlocker = new AtomicReference<>();
  private final AtomicReference<Blocker> autoCommitBlocker = new AtomicReference<>();
  private final AtomicReference<Blocker> verifyBlocker = new AtomicReference<>();

  BlockingConnection(ConnectionManager manager, Backend backend, BackendVersionInfo backendVersionInfo, Connection connection) {
    super(manager, backend, backendVersionInfo, connection);
  }

  /**
   * Blocks the next {@link #close()}.
   *
   * @return the blocker
   */
  Blocker blockClose() {
    Blocker blocker = new Blocker();
    closeBlocker.set(blocker);
    return blocker;
  }

  /**
   * Blocks the next {@link #getAutoCommit()}, as invoked when cleaning up a forcibly detached connection.
   *
   * @return the blocker
   */
  Blocker blockAutoCommit() {
    Blocker blocker = new Blocker();
    autoCommitBlocker.set(blocker);
    return blocker;
  }

  /**
   * Blocks the next {@link #verifyConnection()}.
   *
   * @return the blocker
   */
  Blocker blockVerify() {
    Blocker blocker = new Blocker();
    verifyBlocker.set(blocker);
    return blocker;
  }

  @Override
  public void close() {
    block(closeBlocker);
    super.close();
  }

  @Override
  public boolean getAutoCommit() {
    block(autoCommitBlocker);
    return super.getAutoCommit();
  }

  @Override
  public boolean verifyConnection() {
    block(verifyBlocker);
    return super.verifyConnection();
  }

  private void block(AtomicReference<Blocker> blockerRef) {
    Blocker blocker = blockerRef.getAndSet(null);
    if (blocker != null) {
      blocker.block();
    }
  }

}
