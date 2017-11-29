/**
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


package org.tentackle.dbms.rmi;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.tentackle.daemon.Scavenger;
import org.tentackle.dbms.Db;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;

import static java.lang.Thread.sleep;


/**
 * Cleans up dead or crashed sessions.
 *
 * @author harald
 */
public class RemoteDbSessionCleanupThread extends Thread implements Scavenger {

  /**
   * the logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteDbSessionCleanupThread.class);



  /** sleep time in ms. */
  private final long checkInterval;

  /** maximum number of retries to clean up a session. */
  private final int cleanupRetryMax;

  /** the sessions. */
  private final Set<WeakReference<RemoteDbSessionImpl>> sessions;

  /** map of zombie sessions to cleanup count. */
  private final Map<RemoteDbSessionImpl,Integer> zombieSessions;

  /** request termination on server shutdown. */
  private volatile boolean terminationRequested;


  /**
   * Creates the cleanup thread.
   *
   * @param checkInterval milliseconds to sleep between runs
   * @param sessions the sessions
   * @param cleanupRetryMax maximum number of retries to clean up a session
   */
  public RemoteDbSessionCleanupThread(long checkInterval, Set<WeakReference<RemoteDbSessionImpl>> sessions, int cleanupRetryMax) {
    super("Session Cleanup");
    this.checkInterval = checkInterval;
    this.sessions = sessions;
    this.cleanupRetryMax = cleanupRetryMax;
    this.zombieSessions = new HashMap<>();
    setDaemon(true);    // stop JVM if the last user process terminates
  }


  /**
   * Requests thread termination.
   */
  public void requestTermination() {
    terminationRequested = true;
    interrupt();
  }

  
  @Override
  public void run() {

    LOGGER.info(getName() + " started with interval=" + checkInterval + ", max.retries=" + cleanupRetryMax);

    while (!terminationRequested) {
      try {
        sleep(checkInterval);
        cleanupZombieSessions();
        verifySessions();
      }
      catch (InterruptedException ix) {
        // ignore
      }
      catch (Exception ex) {
        LOGGER.severe("cleanup sessions failed", ex);
      }
    }

    LOGGER.info(getName() + " terminated");
  }


  /**
   * Verifies and cleans up the sessions.
   * <p>
   * Finds all db-groups or non-grouped db that are not alive anymore
   * and closes their sessions.
   * If _all_ dbs of a group have timed out, the whole group is closed.
   * Usually the ModificationThread of a client connection will keep up
   * a db-group alive. If the application uses only a single db-session,
   * it must set the db-alive manually.
   */
  protected void verifySessions() {

    Set<Integer> aliveGroups = new HashSet<>();  // numbers of db groups that are still alive
    Set<Integer> deadTxGroups = new HashSet<>(); // numbers of db groups that are dead while in a transaction

    for (Iterator<WeakReference<RemoteDbSessionImpl>> iter = sessions.iterator(); iter.hasNext(); ) {
      RemoteDbSessionImpl session = iter.next().get();
      if (session == null) {
        iter.remove();    // was removed by GC: remove it from set too
      }
      else  {
        if (session.isOpen()) {

          Db db = session.getSession();
          if (db == null || !db.isOpen()) {
            if (db != null) {
              LOGGER.warning("session Db " + db + " already closed in " + session +
                             " -> cleanup forced and removed from session list");
              // we came too late or not MT-visible.
              // db is already closed (cleanup in parallel?)
              session.forceCleanup();   // works in parallel
            } // else: already closed
            iter.remove();
            continue;
          }

          int group = db.getSessionGroupId();

          boolean timedOut = session.hasTimedOut();

          if (timedOut) {
            boolean inTransaction;
            synchronized (RemoteDbSessionImpl.class) {
              // synchronized double check
              inTransaction = db.isTxRunning();
              timedOut = session.hasTimedOut();
            }
            if (timedOut) {
              // database has timed out
              if (group == 0) {
                // database without a group: close session immediately
                LOGGER.info("disconnect dead " + session + ", ungrouped");
                cleanupSession(session);
                iter.remove();
                continue; // don't run into polled() below (would cause a nullp)
              }
              else  {
                if (inTransaction) {
                  // there is a dead session within a transaction for this group
                  deadTxGroups.add(group);
                }
                // grouped connections are handled below
              }
            }
          }

          if (!timedOut) {
            if (group > 0) {
              // belongs to a group and at least one connection still alive
              aliveGroups.add(group);    // append group if not yet done
            }
          }
          session.polled();   // reset alive flag
        }
      }
    }

    // close all grouped connections not belonging to alive groups
    for (Iterator<WeakReference<RemoteDbSessionImpl>> iter = sessions.iterator(); iter.hasNext(); ) {
      RemoteDbSessionImpl session = iter.next().get();
      if (session == null) {
        iter.remove();    // was removed by GC: remove it from set too
      }
      else  {
        if (session.isOpen()) {
          int group = session.getSession().getSessionGroupId();
          if (group > 0) {
            boolean deadInTransaction = deadTxGroups.contains(group);
            boolean notAlive = !aliveGroups.contains(group);
            if (deadInTransaction || notAlive) {
              // close the db of the group
              if (deadInTransaction) {
                LOGGER.info("disconnect dead {0}, group={1}, due to hanging transaction on {2}",
                            session, group, session.getSession());
              }
              else  {
                LOGGER.info("disconnect dead {0}, group={1}", session, group);
              }
              cleanupSession(session);
              iter.remove();
            }
          }
        }
      }
    }
  }


  /**
   * Cleans up a session.
   *
   * @param session the session
   */
  protected void cleanupSession(RemoteDbSessionImpl session) {
    try {
      session.cleanup(true);
      session.closeDb(true);
    }
    catch (Exception ex) {
      LOGGER.severe("session cleanup failed for " + session + "\n    -> moved to zombie sessions!", ex);
      zombieSessions.put(session, 1);
    }
  }


  /**
   * Cleans up all zombie sessions.
   */
  protected void cleanupZombieSessions() {
    for (Iterator<Map.Entry<RemoteDbSessionImpl,Integer>> iter = zombieSessions.entrySet().iterator(); iter.hasNext(); ) {
      Map.Entry<RemoteDbSessionImpl,Integer> entry = iter.next();
      RemoteDbSessionImpl session = entry.getKey();
      if (cleanupZombieSession(session)) {
        iter.remove();
      }
      else {
        // failed
        int cleanupCount = entry.getValue();
        if (cleanupCount >= cleanupRetryMax) {
          LOGGER.severe("zombie session refused to cleanup " + cleanupCount + " times -> to GC: " + session);
          session.forceCleanup();
          iter.remove();
        }
        else  {
          entry.setValue(cleanupCount + 1);
        }
      }
    }
  }


  /**
   * Cleans up a zombie session.
   *
   * @param session the session
   * @return true if cleaned up, false if failed
   */
  protected boolean cleanupZombieSession(RemoteDbSessionImpl session) {
    try {
      LOGGER.info("cleaning up zombie " + session);
      session.cleanup(true);
      session.closeDb(true);
      return true;
    }
    catch (Exception ex) {
      LOGGER.severe("zombie session cleanup failed for " + session, ex);
      return false;
    }
  }

}
