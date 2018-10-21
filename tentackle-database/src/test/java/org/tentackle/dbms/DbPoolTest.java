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

import java.util.ArrayList;
import java.util.List;
import org.tentackle.session.DefaultSessionInfo;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionPool;
import org.tentackle.sql.BackendInfo;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

/**
 * Session pool and connection manager test.
 *
 * @author harald
 */
public class DbPoolTest {

  private static final int NUM_THREADS = 50;
  private static final StatementId SELECT_ONE_STATEMENT_ID = new StatementId();

  private final SessionPool pool;


  private class UserThread extends Thread {

    private volatile boolean stopRequested;
    private volatile int selectCount;

    private UserThread(int index) {
      super("USER" + (index + 1));
      setPriority(MIN_PRIORITY);
    }

    @Override
    public void run() {
      while (!stopRequested) {
        Db db = (Db) pool.getSession();
        db.setOwnerThread(Thread.currentThread());
        PreparedStatementWrapper st = db.getPreparedStatement(new StatementKey(SELECT_ONE_STATEMENT_ID, getClass()), false,
          () -> "SELECT 1"
        );
        try (ResultSetWrapper rs = st.executeQuery()) {
          if (rs.next()) {
            selectCount += rs.getInt(1);
          }
          else  {
            throw new PersistenceException("cannot read 1");
          }
        }
        finally {
          db.setOwnerThread(null);
          pool.putSession(db);
        }
      }
    }

  }

  public DbPoolTest() {
    SessionInfo sessionInfo = new DefaultSessionInfo();
    BackendInfo backendInfo = new BackendInfo(sessionInfo.getProperties());
    ConnectionManager conMgr = new MpxConnectionManager("mpx", backendInfo,
            NUM_THREADS, 1, NUM_THREADS / 8, 1, NUM_THREADS / 8, NUM_THREADS / 2, 720, 2160);
    pool = new DefaultDbPool("default", conMgr, sessionInfo, 8, 2, 4, conMgr.getMaxSessions(), 60, 0);
  }


  @Test
  public void testPool() {

    UserThread[] users = new UserThread[NUM_THREADS];

    for (int i=0; i < NUM_THREADS; i++) {
      (users[i] = new UserThread(i)).start();
    }

    try {
      Thread.sleep(30000);    // give'm half a minute to run at full speed...
    }
    catch (InterruptedException ex) {
      Reporter.log(ex.getMessage() + "<br/>");
    }

    List<UserThread> failedThreads = new ArrayList<>();
    int totalSelects = 0;

    for (int i=0; i < NUM_THREADS; i++) {
      UserThread thread = users[i];
      if (!thread.isAlive()) {
        failedThreads.add(thread);
        Reporter.log(thread.getName() + ": " + thread.selectCount + " selects, *** DIED ***<br/>");
        totalSelects += thread.selectCount;
        continue;
      }
      thread.stopRequested = true;
      try {
        thread.interrupt();
        thread.join();
        Reporter.log(thread.getName() + ": " + thread.selectCount + " selects<br/>");
        totalSelects += thread.selectCount;
      }
      catch (InterruptedException ex) {
        Reporter.log(ex.getMessage() + "<br/>");
      }
    }

    Reporter.log("total selects: " + totalSelects + "<br/>");
    pool.shutdown();
//  to test GC/Cleaner: comment out pool.shutdown and uncomment the lines below
//    pool = null;
//    try {
//      System.out.println("--------------------------------------------------");
//      System.gc();
//      Thread.sleep(10000);
//      System.out.println("--------------------------------------------------");
//      System.gc();
//      Thread.sleep(10000);
//    }
//    catch (InterruptedException ex) {}
    if (!failedThreads.isEmpty()) {
      Assert.fail(failedThreads.size() + " threads died");
    }
  }

}
