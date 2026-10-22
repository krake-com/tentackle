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
import java.util.LinkedHashMap;
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
    // the grouped sessions verified below with their group at that time.
    Map<WeakReference<RemoteDbSessionImpl>, Integer> groupedSessions = new LinkedHashMap<>();

    for (Iterator<WeakReference<RemoteDbSessionImpl>> iter = sessions.iterator(); iter.hasNext(); ) {
      WeakReference<RemoteDbSessionImpl> sessionRef = iter.next();
      RemoteDbSessionImpl session = sessionRef.get();
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
                if (!forceCleanupIfNotInUse(session)) {
                  continue;   // verify again in the next run
                }
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
                if (cleanupSession(session)) {
                  iter.remove();
                  continue; // don't run into polled() below (would cause a NPE)
                }
                // else: an invocation started meanwhile -> verify again in the next run
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

            if (groupId != 0) {
              groupedSessions.put(sessionRef, groupId);
            }

            session.polled();   // reset alive flag
          }
        }
        catch (RuntimeException rx) {
          LOGGER.warning("verifying " + session + " failed -> force cleanup", rx);
          if (forceCleanupIfNotInUse(session)) {
            iter.remove();
          }
          // else: verify again in the next run
        }
      }
    }

    // run the timeout count on all group infos
    activeGroups.entrySet().removeIf(entry -> entry.getValue().hasTimedOut());

    // close all grouped sessions not belonging to alive groups
    for (Map.Entry<WeakReference<RemoteDbSessionImpl>, Integer> entry : groupedSessions.entrySet()) {
      WeakReference<RemoteDbSessionImpl> sessionRef = entry.getKey();
      RemoteDbSessionImpl session = sessionRef.get();
      if (session == null) {
        sessions.remove(sessionRef);    // was removed by GC: remove it from set too
      }
      else  {
        try {
          Db db = session.getSession();   // local copy: the client may close the session concurrently
          if (db != null) {
            int groupId = entry.getValue();
            // skip if regrouped in the meantime: will be verified in the next run
            if (groupId == db.getExportedSessionGroupId()) {
              boolean deadInTransaction = deadTxGroups.contains(groupId);
              if (deadInTransaction || !activeGroups.containsKey(groupId)) {
                if (deadInTransaction) {
                  LOGGER.info("disconnect dead {0}, group={1}, due to hanging transaction on {2}",
                              session, groupId, db);
                }
                else  {
                  LOGGER.info("disconnect dead {0}, group={1}", session, groupId);
                }
                if (cleanupSession(session)) {
                  sessions.remove(sessionRef);
                }
                // else: an invocation started meanwhile -> verify again in the next run
              }
            }
          }
        }
        catch (RuntimeException rx) {
          LOGGER.warning("verifying " + session + " failed -> force cleanup", rx);
          if (forceCleanupIfNotInUse(session)) {
            sessions.remove(sessionRef);
          }
          // else: verify again in the next run
        }
      }
    }
  }


  /**
   * Cleans up a session.<br>
   * The session is not cleaned up while a client's invocation is in progress, since the local session is not thread-safe.
   * Invocations arriving during the cleanup are rejected.
   *
   * @param session the session
   * @return true if cleaned up or moved to the zombie sessions, false if in use and still to be verified
   */
  protected boolean cleanupSession(RemoteDbSessionImpl session) {
    if (!session.enterInvocation()) {
      LOGGER.info("{0} in use by {1} -> not cleaned up", session, session.getInvokingThread());
      return false;
    }
    try {
      session.cleanup(true);
      session.closeDb(true);
    }
    catch (RuntimeException ex) {
      LOGGER.severe("session cleanup failed for " + session + "\n    -> moved to zombie sessions!", ex);
      zombieSessions.put(session, 1);
    }
    finally {
      session.exitInvocation();
    }
    return true;
  }


  /**
   * Cleans up all zombie sessions.
   */
  protected void cleanupZombieSessions() {
    for (Iterator<Map.Entry<RemoteDbSessionImpl,Integer>> iter = zombieSessions.entrySet().iterator(); iter.hasNext(); ) {
      Map.Entry<RemoteDbSessionImpl,Integer> entry = iter.next();
      RemoteDbSessionImpl session = entry.getKey();
      if (!session.enterInvocation()) {
        // in use: not a failed attempt, and the session must not be forced to clean up under the invocation's hands
        LOGGER.info("zombie {0} in use by {1} -> not cleaned up", session, session.getInvokingThread());
        continue;
      }
      try {
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
      finally {
        session.exitInvocation();
      }
    }
  }


  /**
   * Forces the cleanup of a session, unless a client's invocation is in progress.<br>
   * The local session is not thread-safe, hence it must not be closed under the invocation's hands.
   *
   * @param session the session
   * @return true if cleaned up, false if in use and still to be verified
   */
  private boolean forceCleanupIfNotInUse(RemoteDbSessionImpl session) {
    if (!session.enterInvocation()) {
      LOGGER.info("{0} in use by {1} -> no forced cleanup", session, session.getInvokingThread());
      return false;
    }
    try {
      session.forceCleanup();
    }
    finally {
      session.exitInvocation();
    }
    return true;
  }


  /**
   * Cleans up a zombie session.
   *
   * @param session the session
   * @return true if cleaned up, false if failed
   */
  protected boolean cleanupZombieSession(RemoteDbSessionImpl session) {
    if (!session.enterInvocation()) {
      LOGGER.info("zombie {0} in use by {1} -> not cleaned up", session, session.getInvokingThread());
      return false;
    }
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
    finally {
      session.exitInvocation();
    }
  }

}
