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

import org.tentackle.common.Constants;
import org.tentackle.log.Logger;
import org.tentackle.misc.FormatHelper;
import org.tentackle.session.PersistenceException;
import org.tentackle.sql.BackendInfo;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Multiplexing connection manager.
 * <p>
 * A connection manager for applications with a large number of session instances,
 * for example, application servers. The manager will multiplex N session instances against
 * M connections, allowing N &gt; M. This is not to be mixed up with session-pooling, as connection
 * multiplexing is completely transparent to the application, whereas pooling requires
 * some handling to request and release a session.
 *
 * @author harald
 */
public class MpxConnectionManager extends DefaultConnectionManager {

  private static final Logger LOGGER = Logger.get(MpxConnectionManager.class);

  private static final int MAX_LOOP = 100;        // max. sleep-with-retry if no connection available
  private static final int BASE_MS = 50;          // base milliseconds to sleep before the next try
  private static final long REQ_MUTEX_MS = 1000;  // request connections mutex wait timeout
  private static final long DONE_MUTEX_MS = 500;  // done connections mutex wait timeout
  private static final long CONNECT_THREAD_JOIN_MS = 5000;   // max. time to wait for the connect thread to terminate


  // args

  /** the backend info. */
  protected final BackendInfo backendInfo;

  /** increment size. */
  protected final int incSize;

  /** minimum size. */
  protected final int minSize;


  // local

  /** free list for unattached connections. */
  protected int[] unConList;

  /** number of entries in unConList. */
  protected int unConCount;

  /** true if initialized. */
  protected volatile boolean initialized;

  /** serializes the initialization, which must not hold the monitor of this manager. */
  private final Object initLock = new Object();


  // optional configuration

  /** connection inactivity timeout in ms. */
  private volatile long connectionInactivityTimeoutMs;

  /** periodical fake-select to prevent premature close of idle connections. */
  private volatile boolean connectionKeepAlive;


  // connect thread

  private volatile boolean shutdownRequested;         // true if shutdown procedure initiated
  private final ConnectionThread connectThread;       // thread to bring up connections
  private int conRequestCount;                        // number of new connections requested (guarded by this)
  private volatile boolean probeRequested;            // true if the unattached connections should be verified
  private volatile int warnLogSize;                   // number of connections when to start WARNING logging
  private volatile int infoLogSize;                   // number of connections when to start INFO logging
  private final Object doneMtx;                       // connections created mutex


  /**
   * Creates a new connection manager.
   *
   * @param name the name of the connection manager
   * @param backendInfo the backend info to use for creating connections
   * @param maxSessions the maximum number of session instances, 0 = no limit
   * @param idOffset the offset for the session ids (&gt; 0)
   * @param iniSize the initial size of the connection pool
   * @param incSize the number of connections to add if all in use
   * @param minSize the minimum number of connections
   * @param maxSize the maximum number of connections
   * @param minMinutes minimum minutes a connection should be used
   * @param maxMinutes maximum minutes a connection should be used
   */
  public MpxConnectionManager(String name, BackendInfo backendInfo, int maxSessions, int idOffset, int iniSize, int incSize,
                              int minSize, int maxSize, int minMinutes, int maxMinutes) {

    super(name, iniSize, maxSize, idOffset, minMinutes, maxMinutes);

    if (minSize > maxSize) {
      throw new IllegalArgumentException("maxSize must be >= minSize");
    }
    if (maxSessions > 0 && maxSessions < iniSize) {
      throw new IllegalArgumentException("maxSessions must be 0 or >= iniSize");
    }

    this.backendInfo = backendInfo;
    this.maxDbSize = maxSessions;
    this.incSize = incSize;
    this.minSize = minSize;

    infoLogSize = maxConSize / 4;
    warnLogSize = maxConSize / 2;

    // set up initial connections
    unConList = new int[iniSize];

    // connections are brought up in an extra thread
    doneMtx = new Object();
    connectThread = new ConnectionThread(this);

    // if undefined by backendInfo -> minMinutes/2
    setConnectionInactivityTimeout(backendInfo.getBackendTimeout());
    if (backendInfo.getBackendTimeout() > 0) {
      // only if timeout set!
      setConnectionKeepAlive(backendInfo.isBackendKeepAliveEnabled());
    }
  }

  /**
   * Creates a connection manager with reasonable values for most servers.
   * <p>
   * Maximum of 1000 Db instances (500 clients).
   * Start with 8 connections.
   * Add 2 connections at a time if all connections are in use.
   * Don't drop below 4 connections.
   * Maximum of 40 connections, i.e., concurrent db operations (info above 10, warning above 20).
   * Within 12 to 36h (approx. once a day), close and reopen connections
   * (allows updates of the database server's QEPs, prepared statements cache, etc...).
   *
   * @param backendInfo backend info to use for creating connections
   * @param idOffset the offset for the session ids (&gt; 0)
   */
  public MpxConnectionManager(BackendInfo backendInfo, int idOffset) {
    this("mpx-mgr", backendInfo, 1000, idOffset, 8, 2, 4, 40, 720, 2160);
  }


  /**
   * Sets the connection inactivity timeout in minutes.<br>
   * By default, connections that weren't used for a long time (&ge; <code>minMinutes/2</code> minutes)
   * are checked for being still alive via {@link ManagedConnection#verifyConnection()} before being attached.
   * This assertion is enough for databases that usually don't close inactive sessions.<br>
   * Some databases, however, enforce inactivity timeouts with much shorter intervals
   * that cannot be deactivated or configured to a large enough value.<br>
   * In those cases, the connection verification timeout should be configured appropriately,
   * i.e., less than the database's value.
   *
   * @param connectionInactivityTimeout idle verification timeout in minutes, 0 for default (minMinutes/2)
   */
  public void setConnectionInactivityTimeout(int connectionInactivityTimeout) {
    if (connectionInactivityTimeout == 0) {
      connectionInactivityTimeoutMs = minMinutes * Constants.MINUTE_MS / 2;
    }
    else {
      connectionInactivityTimeoutMs = connectionInactivityTimeout * Constants.MINUTE_MS;
      LOGGER.info("{0}: connection inactivity timeout for {1} = {2} minutes", getName(), backendInfo, connectionInactivityTimeout);
    }
  }

  /**
   * Gets the inactivity timeout.
   *
   * @return the inactivity timeout in minutes.
   */
  public int getConnectionInactivityTimeout() {
    return (int) (connectionInactivityTimeoutMs / Constants.MINUTE_MS);
  }

  /**
   * Returns whether to send fake-selects to the backend periodically.
   *
   * @return true if fake-selects are sent periodically to prevent premature close of idle connections
   */
  public boolean isConnectionKeepAlive() {
    return connectionKeepAlive;
  }

  /**
   * Sets whether to send fake-selects to the backend periodically.
   *
   * @param connectionKeepAlive true to periodically send fake-selects to prevent premature close of idle connections
   */
  public void setConnectionKeepAlive(boolean connectionKeepAlive) {
    this.connectionKeepAlive = connectionKeepAlive;
  }

  /**
   * Shuts down this connection manager.<br>
   * All connections are closed and the threads stopped.
   */
  @Override
  public void shutdown() {
    shutdownRequested = true;   // volatile: the connect thread sees it without holding any lock
    InterruptedException ix = null;
    for (int loop=0; connectThread.isAlive() && loop < 3; loop++) {   // loop because we may receive an interrupt as well
      ix = null;
      connectThread.interrupt();
      try {
        connectThread.join(CONNECT_THREAD_JOIN_MS);   // wait until the connect-thread terminates
        break;
      }
      catch (InterruptedException ex) {
        // daemon thread is terminated via shutdown()!
        ix = ex;
      }
    }
    if (connectThread.isAlive()) {
      // the thread is stuck, most probably in a lengthy connect against an unreachable backend.
      // it is a daemon thread and will terminate as soon as the connect fails or the JVM exits.
      LOGGER.warning(this + ": stopping the connect thread failed", ix);
    }
    // close all connections
    super.shutdown();
  }

  @Override
  protected void closeConnections() {
    super.closeConnections();
    // within the same monitor: attach() must not pop an index whose connection has already been removed
    unConCount = 0;
  }

  /**
   * Gets the number of connections when to start info logging.
   * <p>
   * The default is 1/4th of the maximum connections.
   *
   * @return the info level
   */
  public int getInfoLogSize() {
    return infoLogSize;
  }

  /**
   * Sets the number of connections when to start info logging.
   *
   * @param infoLogSize the info level
   */
  public void setInfoLogSize(int infoLogSize) {
    this.infoLogSize = infoLogSize;
  }

  /**
   * Gets the number of connections when to start warning logging.
   * <p>
   * The default is half of the maximum number of connections.
   *
   * @return the warning level
   */
  public int getWarnLogSize() {
    return warnLogSize;
  }

  /**
   * Sets the number of connections when to start warning logging.
   *
   * @param warnLogSize the warning level
   */
  public void setWarnLogSize(int warnLogSize) {
    this.warnLogSize = warnLogSize;
  }

  @Override
  public int login(Db session) {
    if (shutdownRequested) {
      throw new PersistenceException(this + ": connection manager has been shut down");
    }
    session.assertNotRemote();
    initialize();
    synchronized (this) {
      if (shutdownRequested) {
        throw new PersistenceException(this + ": connection manager has been shut down");
      }
      int id = getSessionIndex() + idOffset;
      LOGGER.fine("{0} logged into {1}, id={2}", session, this, id);
      return id;
    }
  }

  /**
   * Brings up the initial connections and starts the connect thread, if not done yet.<br>
   * Not within the monitor of this manager, since connecting may take a while:
   * other sessions keep on attaching and detaching meanwhile.
   */
  private void initialize() {
    if (!initialized) {
      synchronized (initLock) {
        if (!initialized) {
          createConnections(iniSize);
          logConfiguration();
          connectThread.start();
          initialized = true;
        }
      }
    }
  }

  @Override
  public void logout(Db session) {
    ManagedConnection con;
    synchronized (this) {
      assertSessionBelongsToMe(session);
      int index = getIndexFromSession(session);
      removeSessionIndex(index);
      LOGGER.fine("{0} logged out from {1}, id={2}", session, this, session.getSessionId());
      con = session.getConnection();
      session.clearSessionId();
    }
    // check that the connection is not attached, if so, detach it!
    freeConnection(con, session);
    fireConnectionRequest();
  }

  @Override
  public void cleanup(int sessionId, ManagedConnection connection) {
    synchronized (this) {
      if (sessionId > 0) {
        int index = getIndexFromSessionId(sessionId);
        removeSessionIndex(index);
      }
      LOGGER.fine("{0}: session ID {1} cleaned up", this, sessionId);
    }
    freeConnection(connection, null);
    fireConnectionRequest();
  }

  @Override
  public void attach(Db session) {
    assertNotShutdown();
    assertSessionBelongsToMe(session);

    int loopCount = 0;   // number of retries so far

    for (;;) {   // until attached

      ManagedConnection candidate = null;   // unattached connection to verify before use
      ManagedConnection deadCon = null;     // dead connection removed, to be closed without holding the monitor

      synchronized (this) {

        ManagedConnection con = session.getConnection();
        if (con != null) {
          // already attached (e.g., within transaction)
          con.attachSession(session);   // just increment the attach-count
          break;
        }

        assertNotShutdown();    // again within the monitor: shutdown may have closed the connections meanwhile

        // find an unattached connection
        int conIndex = popUnattached();
        if (conIndex >= 0) {
          con = conList[conIndex];
          if (con.getIndex() != conIndex) {
            throw new PersistenceException(session,
                    "connection " + con + " has wrong index " + con.getIndex() + ", expected " + conIndex);
          }

          if (con.isConnectionVerificationNecessary()) {
            candidate = con;    // verify below, without blocking the connection manager
          }
          else if (con.isDead()) {
            removeDeadConnection(con);
            deadCon = con;      // close below, without blocking the connection manager
          }
          else {
            // found: attach!
            con.attachSession(session);
            break;
          }
        }
      }

      if (deadCon != null) {
        closeDeadConnection(deadCon);
        continue;
      }

      if (candidate != null) {
        // the popped connection is invisible to other sessions while being verified
        candidate.verifyConnection();
        synchronized (this) {
          if (candidate.getIndex() < 0 || candidate.isClosed()) {
            continue;   // removed in the meantime, e.g., by shutdown
          }
          if (!candidate.isDead()) {
            candidate.attachSession(session);
            break;
          }
          removeDeadConnection(candidate);
        }
        closeDeadConnection(candidate);
        continue;
      }

      /*
       * continue without synchronization (allow other threads to use the connection manager).
       * In order not to stop all clients for the duration of establishing new connections,
       * this is done in the connectThread. We will just trigger the thread and wait.
       */
      boolean request = false;
      boolean runningOutOfConnections = false;

      synchronized (this) {
        if (conRequestCount == 0) {
          // no request running: request more connections
          conRequestCount = incSize;
          request = true;
        }
        else if (conRequestCount < 0) {
          // max connections exhausted
          conRequestCount = 0;
          loopCount++;
          if (loopCount > MAX_LOOP) {    // we tried hard, but it took too long, sorry :-(
            // this will probably close the client unfriendly, but what else can we do?
            PersistenceException pex = new PersistenceException(this + ": max. number of concurrent connections in use: " + maxConSize);
            pex.setTemporary(true);
            throw pex;
          }
          runningOutOfConnections = true;
        }
        // else: some request is already running
      }

      // un-synchronized again...
      if (runningOutOfConnections) {
        try {
          // sleep randomly with increasing time according to loopCount
          int baseMs = loopCount * BASE_MS;
          long ms = baseMs + random.nextInt(baseMs);
          LOGGER.warning("{0}: Running out of connections! Putting {1} to sleep for {2} ms, attempt {3}",
                         this, session, ms, loopCount);
          Thread.sleep(ms);
        }
        catch (InterruptedException ex) {
          // daemon thread is terminated via shutdown()!
        }
      }
      else {
        if (request) {
          // start a new request for bringing up connections (not synchronized on this!)
          connectThread.requestConnections();
        }

        // wait for connections to be brought up (even if no request: may take longer)
        synchronized (doneMtx) {
          try {
            doneMtx.wait(DONE_MUTEX_MS);
            assertNotShutdown();
          }
          catch (InterruptedException ex) {
            // daemon thread is terminated via shutdown()!
          }
        }
      }
    }
  }

  @Override
  public void detach(Db session) {
    assertSessionBelongsToMe(session);

    boolean expired = false;
    boolean dead = false;
    ManagedConnection con;

    synchronized (this) {

      con = session.getConnection();
      if (con == null) {
        throw new PersistenceException(this + ": no connection attached to " + session);
      }

      con.detachSession(session);

      if (!con.isAttached()) {
        if (con.isClosed()) {
          LOGGER.warning("detached session {0}: connection {1} already closed!", session, con);
          int ndx = con.getIndex();
          if (ndx >= 0) {
            removeConnection(ndx);
          }
        }
        else {
          if (!con.getAutoCommit()) {
            /*
             * The connection has been detached, even though a transaction is still running!
             * This is bad and indicates some MT problem, or that some thread keeps on using
             * a Db even if there has been an exception causing a rollback.
             * Anyway: mark the connection dead so that it is closed and cleaned up.
             * The Db is also closed to prevent any further use.
             */
            LOGGER.severe("connection {0} detached while still in transaction", con);
            con.setDead(true);
          }

          if (con.isDead()) {
            removeDeadConnection(con);
            dead = true;    // will close below
          }
          else if (con.isExpired()) {
            // connection time elapsed: close it
            removeConnection(con.getIndex());
            expired = true;    // will close below
          }
          else {
            // add unattached connection
            pushUnattached(con.getIndex());
          }
        }
      }
    }

    // un-synchronized...
    if (dead) {
      closeDeadConnection(con);
      try {
        // close the db to make it unusable anymore
        session.close();
      }
      catch (RuntimeException rex) {
        LOGGER.warning("closing Db failed", rex);
      }
    }
    else if (expired) {
      LOGGER.info("{0}: closing expired connection {1}, open since {2}",
                  this, con, FormatHelper.formatTimestamp(new Date(con.getEstablishedSince())));
      try {
        con.close();
      }
      catch (RuntimeException rex) {
        LOGGER.warning("closing old connection failed", rex);
      }
      reopenConnections();
      fireConnectionRequest();
    }
  }

  @Override
  public void forceDetach(Db session) {
    assertSessionBelongsToMe(session);
    freeConnection(session.getConnection(), session);
  }

  /**
   * {@inheritDoc}
   * <p>
   * The connection is removed from the unattached connections while being verified.
   */
  @Override
  public void verifyIdleConnection(ManagedConnection connection) {
    if (verifyUnattached(connection)) {
      // open any missing connections
      reopenConnections();
      fireConnectionRequest();
    }
  }


  @Override
  protected ManagedConnection createConnection(BackendInfo backendInfo) {
    ManagedConnection mc = super.createConnection(backendInfo);
    mc.setConnectionInactivityTimeoutMs(connectionInactivityTimeoutMs);   // at least minMinutes/2, if not configured
    mc.setConnectionKeepAliveEnabled(connectionKeepAlive);
    return mc;
  }

  @Override
  protected void logConfiguration() {
    LOGGER.info("{0} {1}: maxSessions={2}, idOffset={3}, iniSize={4}, incSize={5}, minSize={6}, maxSize={7}, minMinutes={8}, maxMinutes={9}",
      getClass().getSimpleName(), name, maxDbSize, idOffset, iniSize, incSize, minSize, maxConSize, minMinutes, maxMinutes);
  }

  /**
   * Frees a connection attached to a session and adds it to the unattached list.<br>
   * Pending statements are cleaned up and a pending transaction is rolled back.
   * If the connection turns out to be dead, it is closed and removed.
   * <p>
   * Must be invoked without holding the monitor of this manager, which is acquired only
   * to release the session and to add the connection to the unattached list.
   * The cleanup in between may take a while, but the connection is neither attached nor
   * unattached meanwhile, and thus invisible to other sessions.
   *
   * @param con the connection (can be null if no more attached)
   * @param session the session the connection is expected to be attached to, null if any
   */
  protected void freeConnection(ManagedConnection con, Db session) {
    if (con == null) {
      return;
    }

    Db releasedDb;
    synchronized (this) {
      releasedDb = releaseConnection(con, session);
    }
    if (releasedDb != null) {
      cleanupReleasedConnection(con, releasedDb);
    }
    // else: detached in the meantime, or already attached to another session
  }

  /**
   * {@inheritDoc}
   * <p>
   * From now on, the connection is neither attached nor unattached: no other session can get it.
   */
  @Override
  protected Db releaseConnection(ManagedConnection con, Db session) {
    return con.releaseSession(session);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Adds the connection to the unattached connections afterward, or closes it, if it turned out to be dead.
   */
  @Override
  protected void cleanupReleasedConnection(ManagedConnection con, Db releasedDb) {
    // the lengthy part (cancel and close the statements, rollback) without blocking the connection manager
    try {
      con.cleanupReleased(releasedDb);
    }
    catch (RuntimeException rx) {
      LOGGER.warning(this + ": cleaning up connection " + con + " failed -> marked dead", rx);
      con.setDead(true);
    }

    boolean dead = false;
    synchronized (this) {
      if (con.getIndex() < 0) {
        return;   // removed by shutdown in the meantime
      }
      if (con.isDead() || con.isClosed()) {
        removeDeadConnection(con);
        dead = true;
      }
      else {
        // add connection to freelist
        pushUnattached(con.getIndex());
      }
    }

    if (dead) {
      closeDeadConnection(con);
      /*
       * Crashed due to a dead server connection. This is a strong indicator that the database is facing some
       * severe problems. Because we don't know how many unattached connections are down, they will be probed.
       * This is done by the connect thread, since probing may take a long time if the server is unreachable,
       * and must not block the connection manager.
       */
      probeRequested = true;

      // open any missing connections
      reopenConnections();
    }
    fireConnectionRequest();
  }

  /**
   * Adds the index of an unused connection to the freelist.
   *
   * @param index the index of the connection in the connection list
   */
  protected void pushUnattached(int index) {
    if (unConCount >= unConList.length) {
      // the list is full -> enlarge it
      int[] nUnConList = new int[unConList.length << 1];
      System.arraycopy(unConList, 0, nUnConList, 0, unConList.length);
      for (int j=unConList.length; j < nUnConList.length; j++) {
        nUnConList[j] = -1;
      }
      unConList = nUnConList;
    }
    unConList[unConCount++] = index;    // add to freelist
  }

  /**
   * Gets a connection from the unattached freelist.
   *
   * @return the index to the connection list, -1 if no more unattached found
   */
  protected int popUnattached() {
    return unConCount > 0 ? unConList[--unConCount] : -1;
  }

  /**
   * Creates spare connections.<br>
   * At least as many as necessary to reach the minimum size, but never more than the maximum size.
   *
   * @param count the number of connections to create
   * @return the number of connections created
   */
  protected int createConnections(int count) {
    int conSize;

    synchronized(this) {
      conSize = getNumConnections();
    }

    if (conSize + count < minSize) {
      // at least to minSize
      count = minSize - conSize;
    }

    // align count before we run into PersistenceException in addConnection()
    if (maxConSize > 0) {
      if (conSize + count > maxConSize) {
        count = maxConSize - conSize;
      }
      if (count == 0) {
        LOGGER.severe(this + ": *** maximum number of connections reached: " + maxConSize + " ***");
      }
      else  {
        if (conSize + count > getInfoLogSize()) {
          String msg = this + ": increasing number of connections by " + count + " to " + (conSize + count) + " of " +
                       maxConSize + " (unattached=" + unConCount + ")";
          if (conSize + count > getWarnLogSize()) {
            // Half of the connections concurrently in use in most cases tells us that the database server
            // is reaching its limits, i.e., the operations take too long. Or maxConSize is simply too low.
            // Log attached connections.
            StringBuilder buf = new StringBuilder(msg).append("\n\n  attached connections:");
            for (ManagedConnection con: ManagedConnectionMonitor.getInstance().getManagedConnections()) {
              if (con.getManager() == this && con.isAttached()) {
                buf.append("\n\n  ").append(con.toDiagnosticString());
              }
            }
            LOGGER.warning(buf.toString());
          }
          else {
            // pre-warning
            LOGGER.info(msg);
          }
        }
      }
    }

    for (int i=0; i < count; i++) {
      if (shutdownRequested) {
        return i;
      }
      ManagedConnection con = createConnection(backendInfo);  // may take some time depending on the db backend...
      synchronized(this) {
        if (!shutdownRequested) {   // within the monitor: shutdown closes the connections with the monitor held
          pushUnattached(addConnection(con));   // add to established connections and unattached freelist
          continue;
        }
      }
      // shut down while connecting: the connection would never be closed
      LOGGER.info("{0}: closing connection {1} established during shutdown", this, con);
      con.close();
      return i;
    }

    return count;
  }


  /**
   * Verifies all unattached connections and removes the dead ones.<br>
   * Invoked by the connect thread. The connections are verified without holding the monitor.
   * Each connection is taken from the unattached freelist while being verified, so that no session
   * can attach it in the meantime, while the others stay available.
   */
  protected void probeUnattachedConnections() {
    List<ManagedConnection> candidates = new ArrayList<>();
    synchronized (this) {
      probeRequested = false;
      for (int i = 0; i < unConCount; i++) {
        candidates.add(conList[unConList[i]]);
      }
    }

    for (ManagedConnection con : candidates) {
      verifyUnattached(con);
    }
    reopenConnections();
  }

  /**
   * Removes the index of a connection from the unattached freelist.
   *
   * @param index the index of the connection in the connection list
   * @return true if removed, false if not in the freelist
   */
  protected boolean removeUnattached(int index) {
    for (int i = 0; i < unConCount; i++) {
      if (unConList[i] == index) {
        System.arraycopy(unConList, i + 1, unConList, i, unConCount - (i + 1));
        unConCount--;
        return true;
      }
    }
    return false;
  }

  /**
   * Verifies an unattached connection.<br>
   * The connection is removed from the unattached connections while being verified,
   * so that no session can attach it meanwhile.
   *
   * @param con the connection
   * @return true if the connection turned out to be dead and was removed
   */
  private boolean verifyUnattached(ManagedConnection con) {
    synchronized (this) {
      if (shutdownRequested || con.getIndex() < 0 || !removeUnattached(con.getIndex())) {
        return false;   // attached or removed in the meantime
      }
    }
    con.verifyConnection();   // marks the connection dead if it fails
    synchronized (this) {
      if (con.getIndex() < 0 || con.isClosed()) {
        return false;   // removed in the meantime, e.g., by shutdown
      }
      if (!con.isDead()) {
        pushUnattached(con.getIndex());
        return false;
      }
      removeDeadConnection(con);
    }
    closeDeadConnection(con);
    return true;
  }

  /**
   * Removes a dead connection from the connection list.<br>
   * Must be invoked with the monitor held.
   * The connection must be closed via {@link #closeDeadConnection(ManagedConnection)} afterward,
   * without holding the monitor, since closing a dead connection may block until a network timeout.
   *
   * @param con the dead connection
   */
  private void removeDeadConnection(ManagedConnection con) {
    if (con.getIndex() >= 0) {    // not removed by shutdown in the meantime
      removeConnection(con.getIndex());
    }
  }

  /**
   * Closes a connection removed by {@link #removeDeadConnection(ManagedConnection)}.
   *
   * @param con the dead connection
   */
  private void closeDeadConnection(ManagedConnection con) {
    try {
      LOGGER.warning("{0}: closing connection {1}", this, con);
      con.close();
    }
    catch (PersistenceException ex) {
      LOGGER.fine("closing dead connection failed expectedly -> ignored", ex);
    }
  }

  /**
   * Checks whether the number of connections dropped below minSize.
   * If so, requests missing connections to be reopened.
   */
  private void reopenConnections() {
    synchronized(this) {
      if (conRequestCount == 0) {
        // no connect running
        int num = minSize - getNumConnections();
        if (num > 0) {
          // we dropped below minSize: open connection(s)
          conRequestCount = num;
        }
      }
    }
  }

  /**
   * Triggers the connect thread if there are outstanding connection requests.
   */
  private void fireConnectionRequest() {
    if (probeRequested || getConnectionRequestCount() > 0) {
      connectThread.requestConnections();    // fire connect, but don't wait for completion
    }
  }

  /**
   * Gets the number of requested connections.
   *
   * @return the number of connections to bring up, 0 if none, -1 if the maximum number of connections is reached
   */
  private synchronized int getConnectionRequestCount() {
    return conRequestCount;
  }

  /**
   * Sets the number of requested connections.
   *
   * @param conRequestCount the number of connections to bring up, 0 if none, -1 if the maximum number of connections is reached
   */
  private synchronized void setConnectionRequestCount(int conRequestCount) {
    this.conRequestCount = conRequestCount;
  }

  private void assertNotShutdown() {
    if (shutdownRequested) {
      throw new PersistenceException(this + " is shut down");
    }
  }

  /**
   * The connection management thread.<br>
   * The thread is stopped as soon as the connection manager is shut down or becomes unreferenced.
   */
  private static class ConnectionThread extends Thread {

    private final Object requestMtx;                            // connections requested mutex
    private final WeakReference<MpxConnectionManager> cmRef;    // weak reference to check for garbage-collected connection manager
    private boolean requestPending;                             // true if connections were requested (guarded by requestMtx)
    private boolean lastAttemptFailed;                          // true if the last connection attempt failed

    ConnectionThread(MpxConnectionManager mgr) {
      super(mgr.getName() + " Connection Management Thread");
      setDaemon(true);
      setPriority(MAX_PRIORITY);
      cmRef = new WeakReference<>(mgr);
      requestMtx = new Object();
    }

    /**
     * Wakes up this thread to bring up the requested connections.
     */
    void requestConnections() {
      synchronized (requestMtx) {
        requestPending = true;
        requestMtx.notifyAll();
      }
    }

    @Override
    public void run() {

      LOGGER.info("{0} started", getName());

      for (;;) {
        /*
         * Wait for a request.
         * Notice that the manager's monitor must not be acquired while holding requestMtx,
         * because the manager triggers this thread after releasing its monitor, never before.
         */
        synchronized (requestMtx) {
          if (!requestPending) {    // don't wait if a request came in while we were creating connections
            try {
              requestMtx.wait(REQ_MUTEX_MS);
            }
            catch (InterruptedException ex) {
              // check the termination condition below (mgr == null)
            }
          }
          requestPending = false;
        }

        MpxConnectionManager mgr = cmRef.get();
        if (mgr == null || mgr.shutdownRequested) {
          break;
        }

        if (mgr.probeRequested) {
          try {
            mgr.probeUnattachedConnections();
          }
          catch (RuntimeException e) {
            LOGGER.severe(mgr + ": probing connections failed", e);
          }
        }

        int requestCount = mgr.getConnectionRequestCount();
        if (requestCount > 0) {
          /*
           * Bring up connections. This will probably throw exceptions that we catch here and log.
           * There's not much more we can do.
           */
          try {
            // create missing connections
            int count = mgr.createConnections(requestCount);
            mgr.setConnectionRequestCount(count == 0 ? -1 : 0); // -1 = max. connections exhausted
            if (lastAttemptFailed) {
              lastAttemptFailed = false;
              LOGGER.warning("database connection problems resolved");
            }
          }
          catch (RuntimeException e) {
            if (lastAttemptFailed) {  // don't pollute the logfile if connection errors persist
              try {
                sleep(REQ_MUTEX_MS);  // limit CPU usage
              }
              catch (InterruptedException ix) {
                // the next round checks whether mgr == null (see above)
              }
            }
            else {
              lastAttemptFailed = true;
              LOGGER.severe(mgr + ": creating connections failed", e);
            }
          }

          synchronized (mgr.doneMtx) {
            mgr.doneMtx.notifyAll();
          }
        }
      }

      LOGGER.info("{0} stopped", getName());
    }
  }

}
