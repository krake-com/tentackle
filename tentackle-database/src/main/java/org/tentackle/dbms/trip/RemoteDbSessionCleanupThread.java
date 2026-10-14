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

import org.tentackle.daemon.Scavenger;
import org.tentackle.dbms.Db;
import org.tentackle.log.Logger;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;


/**
 * Cleans up dead or crashed sessions.
 *
 * @author harald
 */
public class RemoteDbSessionCleanupThread extends Thread implements Scavenger {

  private static final Logger LOGGER = Logger.get(RemoteDbSessionCleanupThread.class);



  /** sleep time in ms. */
  private final long checkInterval;

  /** maximum number of retries to clean up a session. */
  private final int cleanupRetryMax;

  /** the sessions. */
  private final Set<WeakReference<RemoteDbSessionImpl>> sessions;

  /** map of zombie sessions to clean up count. */
  private final Map<RemoteDbSessionImpl,Integer> zombieSessions;

  /** request termination on server shutdown. */
  private volatile boolean terminationRequested;


  /**
   * Keeps the timeout info for a session group.<br>
   * Mitigates the race condition when the only active session of a session group is closed while the cleanup thread
   * is checking the others -- and closing them.
   * The remaining sessions should stay alive at least until the least recently active session (the closed one)
   * would have timed out.
   */
  private static class GroupTimeoutInfo {
    private int timeout = -1;         // timeout in polling intervals of the cleanup thread (<0 = uninitialized)
    private int timeoutCount = -1;    // consecutive timeouts  (<0 = uninitialized)

    private void update(int timeout, int timeoutCount) {
      if (this.timeout < 0 || this.timeout > timeout) {
        this.timeout = timeout;
      }
      if (this.timeoutCount < 0 || this.timeoutCount > timeoutCount) {
        this.timeoutCount = timeoutCount;
      }
    }

    private boolean hasTimedOut() {
      return ++timeoutCount > timeout;
    }
  }

  /** map of group-number::timeout-info. */
  private final Map<Integer, GroupTimeoutInfo> activeGroups;



  /**
   * Creates the cleanup thread.
   *
   * @param checkInterval milliseconds to sleep between runs
   * @param sessions the sessions
   * @param cleanupRetryMax maximum number of retries to clean up a session
   */
  public RemoteDbSessionCleanupThread(long checkInterval, Set<WeakReference<RemoteDbSessionImpl>> sessions, int cleanupRetryMax) {
    super("Remote Session Cleanup");
    this.checkInterval = checkInterval;
    this.sessions = sessions;
    this.cleanupRetryMax = cleanupRetryMax;
    this.zombieSessions = new HashMap<>();
    this.activeGroups = new HashMap<>();
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

    LOGGER.info("{0} started with interval={1} ms, max.retries={2}", getName(), checkInterval, cleanupRetryMax);

    while (!terminationRequested) {
      try {
        sleep(checkInterval);
        cleanupZombieSessions();
        verifySessions();
      }
      catch (InterruptedException ix) {
        // daemon thread is terminated via requestTermination()
      }
      catch (RuntimeException ex) {
        LOGGER.severe("cleanup sessions failed", ex);
      }
    }

    LOGGER.info("{0} terminated", getName());
  }


  /**
   * Verifies and cleans up the sessions.
   * <p>
   * Finds all session-groups or non-grouped session that are not alive anymore
   * and closes their sessions.
   * If _all_ sessions of a group have timed out, the whole group is closed.
   * Usually the modification tracker of a client sessions will keep up
   * a session-group alive. If the application uses only a single session,
   * it must set the alive flag manually.
   */
  protected void verifySessions() {

    Set<Integer> deadTxGroups = new HashSet<>(); // numbers of db groups that are dead while in a transaction

    for (Iterator<WeakReference<RemoteDbSessionImpl>> iter = sessions.iterator(); iter.hasNext(); ) {
      RemoteDbSessionImpl session = iter.next().get();
      if (session == null) {
        iter.remove();    // was removed by GC: remove it from set too
      }
      else  {
        try {
          if (session.isOpen()) {
            Db db = session.getSession();
            if (db == null || !db.isOpen()) {
              if (db != null) {
                LOGGER.warning("Db {0} already closed in {1} -> force cleanup", db, session);
                // we came too late or not MT-visible.
                // db is already closed (cleanup in parallel?)
                session.forceCleanup();   // works in parallel
              } // else: already closed
              iter.remove();
              continue;
            }

            int groupId = db.getExportedSessionGroupId();
            boolean timedOut = session.hasTimedOut();

            if (timedOut) {
              boolean inTransaction = db.isTxRunning();
              // database has timed out
              if (groupId == 0) {
                // database without a group: close session immediately
                LOGGER.info("disconnect dead " + session + ", ungrouped");
                cleanupSession(session);
                iter.remove();
                continue; // don't run into polled() below (would cause a NPE)
              }
              else {
                if (inTransaction) {
                  // there is a dead session within a transaction for this group
                  deadTxGroups.add(groupId);
                }
                // grouped sessions are handled below
              }
            }
            else if (groupId > 0) {
              // not timed out and belongs to a group and at least one session still alive
              GroupTimeoutInfo groupTimeoutInfo = activeGroups.computeIfAbsent(groupId, g -> new GroupTimeoutInfo());
              groupTimeoutInfo.update(session.getTimeout(), session.getTimeoutCount());
            }

            session.polled();   // reset alive flag
          }
        }
        catch (RuntimeException rx) {
          LOGGER.warning("verifying " + session + " failed -> force cleanup", rx);
          session.forceCleanup();
          iter.remove();
        }
      }
    }

    // run the timeout count on all group infos
    activeGroups.entrySet().removeIf(entry -> entry.getValue().hasTimedOut());

    // close all grouped sessions not belonging to alive groups
    for (Iterator<WeakReference<RemoteDbSessionImpl>> iter = sessions.iterator(); iter.hasNext(); ) {
      RemoteDbSessionImpl session = iter.next().get();
      if (session == null) {
        iter.remove();    // was removed by GC: remove it from set too
      }
      else  {
        try {
          Db db = session.getSession();   // local copy: the client may close the session concurrently
          if (db != null) {
            int groupId = db.getExportedSessionGroupId();
            if (groupId != 0) {
              boolean deadInTransaction = deadTxGroups.contains(groupId);
              if (deadInTransaction || !activeGroups.containsKey(groupId)) {
                if (deadInTransaction) {
                  LOGGER.info("disconnect dead {0}, group={1}, due to hanging transaction on {2}",
                              session, groupId, db);
                }
                else  {
                  LOGGER.info("disconnect dead {0}, group={1}", session, groupId);
                }
                cleanupSession(session);
                iter.remove();
              }
            }
          }
        }
        catch (RuntimeException rx) {
          LOGGER.warning("verifying " + session + " failed -> force cleanup", rx);
          session.forceCleanup();
          iter.remove();
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
    catch (RuntimeException ex) {
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
    catch (RuntimeException ex) {
      LOGGER.severe("zombie session cleanup failed for " + session, ex);
      return false;
    }
  }

}
