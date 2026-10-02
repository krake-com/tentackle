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
import org.tentackle.session.SessionInfo;
import org.tentackle.session.TransactionIsolation;
import org.tentackle.session.TransactionWritability;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendInfo;
import org.tentackle.sql.BackendVersionInfo;

import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The default implementation of a connection manager.<br>
 * Each session will get its own physical connection.
 * <p>
 * Although this manager implements a strict 1:1 mapping between sessions and connections,
 * it can be easily extended to implement an M:N mapping, see the {@link MpxConnectionManager}.
 *
 * @author harald
 */
public class DefaultConnectionManager implements ConnectionManager {

  private static final Logger LOGGER = Logger.get(DefaultConnectionManager.class);

  private static final long TIMEOUT_THREAD_JOIN_MS = 5000;    // max. time to wait for the attach-timeout thread to terminate



  /** name of the connection manager. */
  protected final String name;

  /** initial size. */
  protected final int iniSize;

  /** offset for session IDs. */
  protected final int idOffset;

  /** min minutes to close connections. */
  protected final int minMinutes;

  /** max minutes to close connections. */
  protected final int maxMinutes;

  /** randomizer. */
  protected final Random random;

  /** maximum number of connections, 0 = unlimited. */
  protected final int maxConSize;



  /** current allocation size for {@link Db}s logged in. **/
  protected int dbSize;

  /** maximum number of {@link Db}s, 0 = unlimited. */
  protected int maxDbSize;

  /** free list for dbList (unused entries in dbList). */
  protected int[] freeDbList;

  /** number of entries in freeDbList. */
  protected int freeDbCount;

  /** managed connections. */
  protected ManagedConnection[] conList;

  /** free list for conList (unused entries in conList). */
  protected int[] freeConList;

  /** number of entries in freeConList. */
  protected int freeConCount;


  private int maxCountForClearWarnings = 1000;  // trigger when to clearWarning() on a connection (enabled by default)

  private volatile long attachTimeout;          // max. attach timeout, 0 to disable check (default)
  private volatile boolean shutdownRequested;   // true if connection manager and timeout thread should shut down
  private AttachTimeoutThread timeoutThread;    // thread to check for connections attached too long (default null)

  private boolean configurationLogged;          // true if configuration logged (once)

  private final Map<String, BackendVersionInfo> versionInfoMap;   // version info per URL


  /**
   * Creates a new connection manager.
   *
   * @param name the name of the connection manager
   * @param iniSize the initial size of the session table
   * @param maxSize the maximum number of connections, 0 = unlimited (dangerous!)
   * @param idOffset the offset for session ids (&gt; 0)
   * @param minMinutes minimum minutes a connection should be used, 0 to keep the connection forever
   * @param maxMinutes maximum minutes a connection should be used
   */
  public DefaultConnectionManager(String name, int iniSize, int maxSize, int idOffset,
                                  int minMinutes, int maxMinutes) {

    this.name = Objects.requireNonNull(name, "name");
    if (name.isEmpty()) {
      throw new IllegalArgumentException("empty name");
    }
    if (iniSize < 1) {
      throw new IllegalArgumentException("initial size must be > 0");
    }
    if (idOffset < 1) {
      throw new IllegalArgumentException("connection ID offset must be > 0");
    }
    if (maxSize != 0 && maxSize < iniSize) {
      throw new IllegalArgumentException("maxSize must be 0 or >= iniSize");
    }
    if (minMinutes > maxMinutes) {
      throw new IllegalArgumentException("maxMinutes must be >= minMinutes");
    }

    this.iniSize = iniSize;
    this.idOffset = idOffset;
    this.maxDbSize = maxSize;     // Db and Cons use the same max-setting!
    this.maxConSize = maxSize;
    this.minMinutes = minMinutes;
    this.maxMinutes = maxMinutes;

    random = new Random();
    dbSize = iniSize;
    conList = new ManagedConnection[iniSize];
    freeDbList = new int[iniSize];
    freeConList = new int[iniSize];

    for (int i=iniSize-1; i >= 0; i--) {
      freeDbList[freeDbCount++] = i;
      freeConList[freeConCount++] = i;
      conList[i] = null;
    }

    versionInfoMap = new ConcurrentHashMap<>();
  }

  /**
   * Creates a connection manager.
   * <p>
   * With an initial size of 2, a maximum of 8 concurrent connections and an id offset of 1.
   * This is the default connection manager for 2-tier client applications.
   * The max connections will prevent applications from tearing down the database server
   * by opening connections excessively. The usual client application holds 2 connections and temporarily
   * a few more.<br>
   * Within 12 to 36h (approx. once a day), close and reopen connections
   * (allows updates of the database server's QEPs, prepared statements cache, etc...).
   */
  public DefaultConnectionManager() {
    this("default-mgr", 2, 8, 1, 720, 2160);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }


  /**
   * Sets the countForClearWarnings trigger, 0 = app must eat the warnings!
   *
   * @param maxCountForClearWarnings the maximum count
   */
  public void setMaxCountForClearWarnings(int maxCountForClearWarnings) {
    this.maxCountForClearWarnings = maxCountForClearWarnings;
  }

  /**
   * Gets the current setting for clearWarnings() trigger.
   *
   * @return the countForClearWarnings trigger, 0 = app must eat the warnings!
   */
  public int getMaxCountForClearWarnings()  {
    return maxCountForClearWarnings;
  }


  /**
   * Gets the attach-timeout.
   *
   * @return the maximum attach-time, 0 for check is disabled (default)
   */
  public long getAttachTimeout() {
    return attachTimeout;
  }

  /**
   * Sets the attach-timeout.
   *
   * @param attachTimeout the maximum attach-time, 0 for check is disabled (default)
   */
  public synchronized void setAttachTimeout(long attachTimeout) {
    this.attachTimeout = attachTimeout;
    if (attachTimeout > 0) {
      if (timeoutThread == null) {
        timeoutThread = new AttachTimeoutThread(this);
        timeoutThread.start();
      }
    }
    else {
      if (timeoutThread != null) {
        // don't wait for its termination: we're holding the monitor the thread needs
        timeoutThread.requestTermination();
        timeoutThread.interrupt();
        timeoutThread = null;
      }
    }
  }


  @Override
  public synchronized int getMaxSessions() {
    return maxDbSize;
  }

  @Override
  public int getSessionIdOffset() {
    return idOffset;
  }

  @Override
  public synchronized int getNumSessions() {
    return dbSize - freeDbCount;
  }


  @Override
  public int getMaxConnections() {
    return maxConSize;
  }


  @Override
  public synchronized int getNumConnections() {
    return conList.length - freeConCount;
  }


  @Override
  public synchronized Collection<ManagedConnection> getConnections() {
    Collection<ManagedConnection> connections = new ArrayList<>();
    for (ManagedConnection con: conList) {
      if (con != null) {
        connections.add(con);
      }
    }
    return connections;
  }


  @Override
  public int login(Db session) {
    session.assertNotRemote();
    BackendInfo backendInfo = session.getBackendInfo();
    if (backendInfo.getUser() == null && backendInfo.getPassword() == null) {
      // for direct jdbc connections via backend config:
      // take user/passwd from session info if missing in backend info
      SessionInfo sessionInfo = session.getSessionInfo();
      backendInfo = new BackendInfo(backendInfo, sessionInfo.getUserName(), sessionInfo.getPassword());
    }

    ManagedConnection con = null;
    int id;
    synchronized (this) {
      if (!configurationLogged) {
        logConfiguration();
        configurationLogged = true;
      }
      id = getSessionIndex();   // throws PersistenceException if too many sessions
    }

    try {
      con = createConnection(backendInfo);    // lengthy operation outside synchronized!
      synchronized (this) {
        /*
         * because we add the connections in the same order as the sessions (1:1 mapping), the
         * index returned after adding the connection must be the same as for the session.
         */
        if (addConnection(con) != id) {
          throw new PersistenceException(this + ": session- and connection-list out of sync");
        }
      }
    }
    catch (RuntimeException rx) {
      synchronized (this) {
        removeSessionIndex(id);
      }
      if (con != null) {
        con.close();
      }
      throw rx;
    }

    id += idOffset;
    LOGGER.info("{0}: assigned {1} to connection {2}, id={3}", this, session.getName(), con, id);
    return id;
  }


  @Override
  public synchronized void logout(Db session) {
    assertSessionBelongsToMe(session);
    int index = getIndexFromSession(session);
    removeSessionIndex(index);
    try {
      ManagedConnection con = removeConnection(index);
      LOGGER.info("{0}: released {1} from connection {2}, id={3}", this, session.getName(), con, session.getSessionId());
      con.close();  // physically close the removed connection
    }
    finally {
      session.clearSessionId();
    }
  }

  @Override
  public synchronized void cleanup(int sessionId, ManagedConnection connection) {
    ManagedConnection con;
    if (sessionId > 0) {
      int index = getIndexFromSessionId(sessionId);
      removeSessionIndex(index);
      con = removeConnection(index);
    }
    else {
      con = connection;
    }
    LOGGER.info("{0}: cleanup session ID {1} from connection {2}", this, sessionId, con);
    con.close();  // physically close the removed connection
  }


  @Override
  public void attach(Db session) {
    /*
     * The happy path is synchronized only once.
     * Only in case of a dead or expired connection, we need a second synchronized block.
     * However, the lengthy close and reopen are not synchronized and therefore will not block other
     * threads from using the connection manager.
     */
    ManagedConnection con = attachImpl(session);    // <<-- synchronized

    if (con != null) {    // need re-open
      // not synchronized -> does not block the connection manager
      try {
        con.close();
      }
      catch (RuntimeException ex) {
        LOGGER.warning("closing connection failed -> ignored", ex);
      }
      // reopen the connection
      con = createConnection(session.getBackendInfo());

      synchronized(this) {
        int index = getIndexFromSession(session);
        conList[index] = con;
        LOGGER.info("{0}: connection {1} reopened", this, con);
        con.attachSession(session);
      }
    }
  }


  @Override
  public synchronized void detach(Db session) {
    assertSessionBelongsToMe(session);
    int index = getIndexFromSession(session);
    ManagedConnection con = conList[index];
    con.detachSession(session);
  }


  @Override
  public synchronized void forceDetach(Db session) {
    assertSessionBelongsToMe(session);
    int index = getIndexFromSession(session);
    ManagedConnection con = conList[index];
    con.forceDetached();
  }


  @Override
  public void shutdown() {

    shutdownRequested = true;   // volatile: the timeout thread sees it without holding any lock

    AttachTimeoutThread t;
    synchronized (this) {
      t = timeoutThread;
      timeoutThread = null;
    }

    if (t != null) {
      // shutdown monitoring thread, not synchronized: the thread locks this manager
      t.requestTermination();
      t.interrupt();
      try {
        t.join(TIMEOUT_THREAD_JOIN_MS);   // wait until thread terminates
      }
      catch (InterruptedException ex) {
        // daemon thread is terminated via shutdown()!
        LOGGER.warning(this + ": stopping the timeout thread failed", ex);
      }
      if (t.isAlive()) {
        // The thread is stuck.
        // It is a daemon thread and will terminate as soon as it is done.
        LOGGER.warning("{0}: the timeout thread did not terminate within {1} ms", this, TIMEOUT_THREAD_JOIN_MS);
      }
    }

    synchronized (this) {
      // close all connections
      for (int i=0; i < conList.length; i++) {
        if (conList[i] != null) {
          ManagedConnection con = removeConnection(i);
          LOGGER.info("{0}: close connection {1}", this, con);
          con.close();
        }
      }
    }
  }



  /**
   * Logs the connection managers configuration.
   */
  protected void logConfiguration() {
    LOGGER.info("{0} {1}: iniSize={2}, idOffset={3}, maxSize={4}, minMinutes={5}, maxMinutes={6}",
      getClass().getSimpleName(), name, iniSize, idOffset, maxDbSize, minMinutes, maxMinutes);
  }


  /**
   * Gets the index for a new session.
   *
   * @return the index of session in the list
   */
  protected int getSessionIndex() {
    if (freeDbCount == 0) {
      // no more free Db entries: double the list size
      if (maxDbSize > 0 && dbSize >= maxDbSize) {
        PersistenceException pex = new PersistenceException(this + ": max. number of sessions exceeded (" + maxDbSize + ")");
        pex.setTemporary(true);
        throw pex;
      }

      int newSize = dbSize << 1;
      if (maxDbSize > 0 && newSize > maxDbSize) {
        newSize = maxDbSize;
      }
      int[] nFreeDbList = new int[newSize];
      System.arraycopy(freeDbList, 0, nFreeDbList, 0, dbSize);
      for (int i=newSize-1; i >= dbSize; i--) {
        nFreeDbList[freeDbCount++] = i;
        nFreeDbList[i] = -1;
      }
      dbSize = newSize;
      freeDbList = nFreeDbList;
    }

    return freeDbList[--freeDbCount];
  }


  /**
   * Removes a session index.
   *
   * @param index the index of session in the list
   */
  protected void removeSessionIndex(int index) {
    freeDbList[freeDbCount++] = index;
  }


  /**
   * Adds a connection to the list.
   *
   * @param con the connection to add
   * @return the index of connection in the list
   */
  protected int addConnection(ManagedConnection con) {
    if (freeConCount == 0) {
      // no more free connection entries: double the list size
      if (maxConSize > 0 && conList.length >= maxConSize) {
        PersistenceException pex = new PersistenceException(this + ": max. number of connections exceeded (" + maxConSize + ")");
        pex.setTemporary(true);
        throw pex;
      }
      // no more free connection entries: double the list size
      int newSize = conList.length << 1;
      if (maxConSize > 0 && newSize > maxConSize) {
        newSize = maxConSize;
      }
      ManagedConnection[] nConList = new ManagedConnection[newSize];
      System.arraycopy(conList, 0, nConList, 0, conList.length);
      int[] nFreeConList = new int[newSize];
      System.arraycopy(freeConList, 0, nFreeConList, 0, conList.length);
      for (int i=newSize-1; i >= conList.length; i--) {
        nFreeConList[freeConCount++] = i;
        nFreeConList[i] = -1;
        nConList[i] = null;
      }
      conList = nConList;
      freeConList = nFreeConList;
    }

    int index = freeConList[--freeConCount];
    conList[index] = con;
    con.setIndex(index);
    return index;
  }


  /**
   * Removes a connection from the list.
   *
   * @param index the index of connection in the list
   * @return the removed connection
   */
  protected ManagedConnection removeConnection(int index) {
    ManagedConnection con = conList[index];
    conList[index] = null;
    freeConList[freeConCount++] = index;
    con.setIndex(-1);
    return con;
  }


  /**
   * Gets the internal array index from the session's session ID.
   *
   * @param session the session
   * @return the array index
   */
  protected int getIndexFromSession(Db session) {
    int sessionId = session.getSessionId();
    if (sessionId <= 0) {
      throw new PersistenceException(session, this + ": session already closed");
    }
    int ndx = sessionId - idOffset;
    if (ndx < 0 || ndx >= dbSize) {
      throw new PersistenceException(session, this + ": invalid connection id=" + sessionId +
              ", expected " + idOffset + " - " + (idOffset + dbSize - 1));
    }
    return ndx;
  }

  /**
   * Gets the internal array index from the session's session ID.
   *
   * @param sessionId the session ID
   * @return the array index
   */
  protected int getIndexFromSessionId(int sessionId) {
    if (sessionId <= 0) {
      throw new PersistenceException(this + ": session already closed");
    }
    int ndx = sessionId - idOffset;
    if (ndx < 0 || ndx >= dbSize) {
      throw new PersistenceException(this + ": invalid connection id=" + sessionId +
              ", expected " + idOffset + " - " + (idOffset + dbSize - 1));
    }
    return ndx;
  }


  /**
   * Asserts that the session belongs to this manager.
   *
   * @param session the session
   */
  protected void assertSessionBelongsToMe(Db session) {
    if (session.getConnectionManager() != this) {
      throw new PersistenceException(session,
              "session " + session + " does not belong to " + this + " but to " + session.getConnectionManager());
    }
  }


  /**
   * Creates a managed connection.
   *
   * @param backendInfo the backend info
   * @return the managed connection
   */
  protected ManagedConnection createConnection(BackendInfo backendInfo) {
    if (!backendInfo.isConnectable()) {
      throw new PersistenceException(this + ": backend info " + backendInfo + " is not connectable");
    }

    Connection con;
    ManagedConnection mc;
    BackendVersionInfo versionInfo;

    try {
      con = connect(backendInfo);
    }
    catch (SQLException sx) {
      throw new PersistenceException(this + ": connection to '" + backendInfo + "' failed", sx);
    }

    try {
      versionInfo = versionInfoMap.computeIfAbsent(backendInfo.getUrl(), url -> {
        try {
          DatabaseMetaData metaData = con.getMetaData();    // maybe a lengthy operation -> cached per URL
          return new BackendVersionInfo(metaData.getDatabaseMajorVersion(), metaData.getDatabaseMinorVersion());
        }
        catch (SQLException sx) {
          throw new PersistenceException(this + ": connection metadata could not be retrieved", sx);
        }
      });

      Backend backend = backendInfo.getBackend();
      backend.validateVersion(versionInfo);
      mc = createManagedConnection(backend, versionInfo, con);
      if (backendInfo.getBackendTimeout() > 0) {
        mc.setConnectionInactivityTimeoutMs(backendInfo.getBackendTimeout() * Constants.MINUTE_MS);
        mc.setConnectionKeepAliveEnabled(backendInfo.isBackendKeepAliveEnabled());
      }
    }
    catch (RuntimeException rx) {
      closeUnmanagedConnection(con);
      throw rx;
    }

    if (!mc.getAutoCommit()) {
      String mcMsg = mc.toString();
      mc.close();
      throw new PersistenceException(this + ": connection " + mcMsg + " is not in autoCommit mode -> closed");
    }

    mc.setMaxCountForClearWarnings(maxCountForClearWarnings);
    if (minMinutes > 0) {
      mc.setExpireAt(mc.getEstablishedSince() +
                     (minMinutes * 60L + random.nextInt((maxMinutes - minMinutes) * 60)) * 1000L);
    }

    LOGGER.info(() -> MessageFormat.format("{0}: open connection {1}, {2} {3}, {4}, {5}, valid until {6}",
                                           this, mc, backendInfo.getBackend().getName(), versionInfo,
                                           TransactionIsolation.valueOf(mc.getTransactionIsolation()),
                                           TransactionWritability.valueOf(!mc.isReadOnly()),
                                           FormatHelper.formatTimestamp(new Date(mc.getExpireAt()))));
    return mc;
  }


  /**
   * Creates the low-level JDBC connection.
   *
   * @param backendInfo the backend info
   * @return the connection in autocommit mode
   * @throws SQLException if the connection could not be established
   */
  protected Connection connect(BackendInfo backendInfo) throws SQLException {
    return backendInfo.connect();
  }

  /**
   * Wraps a low-level JDBC connection created by {@link #connect(BackendInfo)} into a managed connection.
   *
   * @param backend the backend
   * @param versionInfo the backend's database version
   * @param connection the low-level connection
   * @return the managed connection
   */
  protected ManagedConnection createManagedConnection(Backend backend, BackendVersionInfo versionInfo, Connection connection) {
    return new ManagedConnection(this, backend, versionInfo, connection);
  }

  /**
   * Closes a low-level JDBC connection created by {@link #connect(BackendInfo)}
   * that could not be turned into a managed connection.
   *
   * @param connection the low-level connection
   */
  protected void closeUnmanagedConnection(Connection connection) {
    try {
      connection.close();
    }
    catch (SQLException sx) {
      LOGGER.severe("closing connection failed -> ignored", sx);
    }
  }


  /**
   * Attaches the session to a connection.
   *
   * @param session the session
   * @return null if attached, the connection if dead or expired and needs to be reopened
   */
  private synchronized ManagedConnection attachImpl(Db session) {
    assertSessionBelongsToMe(session);
    int index = getIndexFromSession(session);
    ManagedConnection con = conList[index];
    boolean verified;
    if (con.isConnectionVerificationNecessary()) {
      con.verifyConnection();
      verified = true;
    }
    else {
      verified = false;
    }
    if (con.isDead()) {
      if (verified) {
        LOGGER.info("{0}: database closed idle connection {1}", this, con);
      }
      else {
        // marked dead unexpectedly!
        LOGGER.warning("{0}: closing ***DEAD*** connection {1}", this, con);
      }
    }
    else if (con.isExpired()) {
      LOGGER.info("{0}: closing connection {1}, open since {2}",
                  this, con, FormatHelper.formatTimestamp(new Date(con.getEstablishedSince())));
    }
    else if (con.isClosed()) {
      LOGGER.warning("{0}: connection {1} is closed", this, con);
    }
    else {
      con.attachSession(session);
      con = null;
    }
    return con;
  }



  /**
   * The attach-timeout thread.<br>
   * Detaches connections that have been attached for longer than attachTimeout milliseconds.
   * The thread is stopped as soon as the connection manager is shut down or becomes unreferenced.
   */
  private static class AttachTimeoutThread extends Thread {

    private final WeakReference<DefaultConnectionManager> cmRef;
    private volatile boolean terminationRequested;    // true if this thread should stop
    private long attachTimeout;

    AttachTimeoutThread(DefaultConnectionManager mgr) {
      super(mgr.getName() + " Attach Timeout Thread");
      setDaemon(true);
      cmRef = new WeakReference<>(mgr);
      attachTimeout = mgr.attachTimeout;
    }

    /**
     * Requests this thread to terminate.<br>
     * The flag belongs to the thread, not to the manager, so that a thread already retired
     * cannot be revived by re-enabling the attach-timeout, which would start a second thread.
     */
    void requestTermination() {
      terminationRequested = true;
    }

    @Override
    public void run() {

      LOGGER.info("{0} started with timeout={1}ms", getName(), attachTimeout);

      for (;;) {
        try {
          sleep(attachTimeout);
        }
        catch (InterruptedException iex) {
          // sleep interrupted -> check termination conditions below (mgr == null)
        }

        DefaultConnectionManager mgr = cmRef.get();
        if (mgr == null || terminationRequested || mgr.shutdownRequested || mgr.attachTimeout <= 0) {
          break;
        }

        attachTimeout = mgr.attachTimeout;

        // check for attached connections
        long curTime = System.currentTimeMillis();

        ManagedConnection[] connections;
        synchronized (mgr) {
          connections = mgr.conList.clone();    // fast array copy!
        }
        for (ManagedConnection con : connections) {
          if (con != null && con.isAttached() && (curTime - con.getAttachedSince()) > attachTimeout) {
            Db db = con.getSession();
            if (db != null) {   // still un-synchronized
              synchronized (mgr) {    // attach/detach is synchronized on me
                long attachedMillis = curTime - con.getAttachedSince();
                if (db.getConnection() == con && con.isAttached() &&
                    attachedMillis > attachTimeout) {   // double check within synchronized block
                  // timed out
                  try {
                    LOGGER.warning("detaching timed out connection " + con + " from " + db.getName() +
                            " (" + attachedMillis + "ms)");
                    db.forceDetached();
                  }
                  catch (RuntimeException rex) {
                    LOGGER.severe("detaching " + con + " failed", rex);
                  }
                }
              }
            }
          }
        }
      }

      LOGGER.info("{0} stopped", getName());
    }
  }

}
