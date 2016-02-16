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


package org.tentackle.persist;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.pdo.PersistenceException;
import org.tentackle.pdo.Session;


/**
 * The default implementation of a connection manager.<br>
 * Each Db will get its own physical connection.
 * This kind of manager is useful for 2-tier applications directly connecting
 * to a database backend.
 * <p>
 * Although this manager implements a strict 1:1 mapping between dbs and connections
 * it can be easily extended to implememt a M:N mapping, see the {@link MpxConnectionManager}.
 *
 * @author harald
 */
public class DefaultConnectionManager extends Thread implements ConnectionManager {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConnectionManager.class);

  private static final long LOOP_MS = 1000;     // minimum loop time for timeout checks

  /** name of the connection manager. */
  final protected String name;

  /** initial size. */
  final protected int iniSize;

  /** offset for connection IDs. */
  final protected int idOffset;

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

  /** maximum number of connections, 0 = unlimited. */
  protected final int maxConSize;

  /** free list for conList (unused entries in conList). */
  protected int[] freeConList;

  /** number of entries in freeConList. */
  protected int freeConCount;


  private long attachTimeout;                   // max. attach timeout, 0 to disable check

  private int maxCountForClearWarnings = 1000;  // trigger when to clearWarning() on a connection (enabled by default)

  private volatile boolean shutdownRequested;   // true if thread should shutdown


  /**
   * Creates a new connection manager.
   *
   * @param name the name of the connection manager
   * @param iniSize the initial iniSize of the db table
   * @param maxSize the maximum number of connections, 0 = unlimited (dangerous!)
   * @param idOffset the offset for connection ids (&gt; 0)
   */
  public DefaultConnectionManager(String name, int iniSize, int maxSize, int idOffset) {

    super(name);

    if (iniSize < 1) {
      throw new IllegalArgumentException("initial size must be > 0");
    }
    if (idOffset < 1) {
      throw new IllegalArgumentException("connection ID offset must be > 0");
    }
    if (maxSize != 0 && maxSize < iniSize) {
      throw new IllegalArgumentException("maxSize must be 0 or >= iniSize");
    }
    // name != null already checked by super(name)

    this.name = name;
    this.iniSize = iniSize;
    this.idOffset = idOffset;
    this.maxDbSize = maxSize;     // Db and Cons use the same max-setting!
    this.maxConSize = maxSize;

    dbSize = iniSize;
    conList = new ManagedConnection[iniSize];
    freeDbList = new int[iniSize];
    freeConList = new int[iniSize];

    for (int i=iniSize-1; i >= 0; i--) {
      freeDbList[freeDbCount++] = i;
      freeConList[freeConCount++] = i;
      conList[i] = null;
    }
  }

  /**
   * Creates a connection manager.
   * <p>
   * With an initial size of 2, a maximum of 8 concurrent connections and an id offset of 1.
   * This is the default connection manager for 2-tier client applications.
   * The max connections will prevent ill behaving applications from tearing down the dbserver
   * by opening connections excessively. The usual application holds 2 connections and temporarily
   * 1 or 2 more. If you need more, change the connection manager in Db.
   */
  public DefaultConnectionManager() {
    this("default-mgr", 2, 8, 1);
  }


  /**
   * Gets the name of the manager.
   */
  @Override
  public String toString() {
    return name;
  }


  /**
   * Sets the countForClearWarnings trigger, 0 = app must eat the warnings!
   *
   * @param maxCountForClearWarnings the maxcount
   */
  public void setMaxCountForClearWarnings(int maxCountForClearWarnings) {
    this.maxCountForClearWarnings = maxCountForClearWarnings;
  }

  /**
   * Gets the current setting for clearWarnings() trigger
   *
   * @return the countForClearWarnings trigger, 0 = app must eat the warnings!
   */
  public int getMaxCountForClearWarnings()  {
    return maxCountForClearWarnings;
  }


  /**
   * Gets the attach timeout.
   *
   * @return the max. attach time, 0 if check is disabled
   */
  public long getAttachTimeout() {
    return attachTimeout;
  }

  /**
   * Sets the attach timeout.
   *
   * @param attachTimeout the max. attach time, 0 if check is disabled (default)
   */
  public void setAttachTimeout(long attachTimeout) {
    this.attachTimeout = attachTimeout;
  }


  /**
   * Adds a Db to the list.
   *
   * @param db the db to add
   * @return the index of db in the dblist
   */
  protected int addDb(Db db) {
    if (freeDbCount == 0) {
      // no more free Db entries: double the list size
      if (maxDbSize > 0 && dbSize >= maxDbSize) {
        throw new PersistenceException(db, this + ": max. number of Db instances exceeded (" + maxDbSize + ")");
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
   * Removes a Db from the list.
   *
   * @param index the index of db in the dblist
   */
  protected void removeDb(int index) {
    freeDbList[freeDbCount++] = index;
  }



  /**
   * Adds a connection to the list.
   *
   * @param con the connection to add
   * @return the index of connection in the conlist
   */
  protected int addConnection(ManagedConnection con) {
    if (freeConCount == 0) {
      // no more free connection entries: double the list size
      if (maxConSize > 0 && conList.length >= maxConSize) {
        throw new PersistenceException(this + ": max. number of connections exceeded (" + maxConSize + ")");
      }
      // no more free Db entries: double the list size
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
   * @param index the index of connection in the conlist
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
   * Gets the number of established connections
   *
   * @return the number of connections
   */
  public int getConnectionCount() {
    return conList.length - freeConCount;
  }



  // ---------------- implements ConnectionManager -----------------


  @Override
  public int getMaxSessions() {
    return maxDbSize;
  }


  @Override
  public int getNumSessions() {
    return dbSize - freeDbCount;
  }


  @Override
  public int getMaxConnections() {
    return maxConSize;
  }


  @Override
  public int getNumConnections() {
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
  public int login(Session session) {

    ManagedConnection con = createConnection((Db) session);

    synchronized(this) {
      int id = addDb((Db) session);
      /**
       * because we add the connections in the same order as the db (1:1 mapping), the
       * index returned after adding the conncetion must be the same as for the db.
       */
      if (addConnection(con) != id) {
        con.close();
        removeDb(id);
        throw new PersistenceException(this + ": db- and connection-list out of sync");
      }

      id += idOffset;
      LOGGER.info("{0}: assigned {1} to connection {2}, id={3}", this, session, con, id);
      return id;
    }
  }


  @Override
  public synchronized void logout(Session session) {
    assertSessionBelongsToMe(session);
    int id = session.getSessionId();
    int index = convertConnectionIdToIndex(id);
    removeDb(index);
    ManagedConnection con = removeConnection(index);
    LOGGER.info("{0}: released {1} from connection {2}, id={3}", this, session, con, id);
    con.close();  // physically close the removed connection
  }


  @Override
  public void attach(Session session) {
    assertSessionBelongsToMe(session);
    int index = convertConnectionIdToIndex(session.getSessionId());
    ManagedConnection con = conList[index];
    if (con.isDead()) {
      synchronized (this) {
        // try to reopen
        LOGGER.warning(this + ": closing **DEAD** connection " + con);
        try {
          con.close();
        }
        catch (PersistenceException ex) {
        }
        // reopen the connection
        con = createConnection((Db) session);
        conList[index] = con;
        LOGGER.warning(this + ": connection " + con + " reopened");
      }
    }
    con.attachSession((Db) session);
  }


  @Override
  public void detach(Session session) {
    assertSessionBelongsToMe(session);
    int index = convertConnectionIdToIndex(session.getSessionId());
    ManagedConnection con = conList[index];
    con.detachSession((Db) session);
  }


  @Override
  public synchronized void forceDetach(Session session) {
    assertSessionBelongsToMe(session);
    int index = convertConnectionIdToIndex(session.getSessionId());
    ManagedConnection con = conList[index];
    con.forceDetached();
  }


  @Override
  public synchronized void shutdown() {
    // shutdown monitoring thread (will shutdown anytime soon)
    shutdownRequested = true;

    // close all connections
    for (int i=0; i < conList.length; i++) {
      if (conList[i] != null) {
        ManagedConnection con = removeConnection(i);
        LOGGER.info("{0}: close connection {1}", this, con);
        con.close();
      }
    }
  }


  // --------------------- implements Thread ---------------------------

  @Override
  public void run() {

    while (!shutdownRequested) {
      if (getAttachTimeout() <= 0) {
        // no timeout check, may be enabled later...
        try {
          sleep(LOOP_MS);
        }
        catch (InterruptedException iex) {
          // simply continue
        }
      }
      else  {
        try {
          sleep(attachTimeout);

          // check for attached connections
          long curTime = System.currentTimeMillis();

          for (ManagedConnection con: conList) {
            if (con != null) {
              long attachedMillis = curTime - con.getAttachedSince();
              if (con.isAttached() && attachedMillis > attachTimeout) {
                Db db = con.getSession();
                synchronized (db) {    // set/get connection is sync'd on Db
                  if (db.getConnection() == con && con.isAttached()) {
                    // timed out
                    try {
                      LOGGER.warning("detaching timed out connection " + con + " from " + db.getName() + " (" + attachedMillis + "ms)");
                      con.forceDetached();
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
        catch (InterruptedException iex) {
          // simply continue
        }
      }
    }

  }


  /**
   * Converts the connection id to the internal Db-array index.
   *
   * @param id the connection id (&gt; 0)
   * @return the array index
   */
  protected int convertConnectionIdToIndex(int id) {
    int ndx = id - idOffset;
    if (ndx < 0 || ndx >= dbSize) {
      throw new PersistenceException(this + ": invalid connection id=" + id + ", expected " + idOffset + " - " + (idOffset + dbSize - 1));
    }
    return ndx;
  }


  /**
   * Asserts that the session belongs to this manager.
   *
   * @param session the session
   */
  protected void assertSessionBelongsToMe(Session session) {
    if (session.getSessionManager() != this) {
      throw new PersistenceException(session,
              "session " + session + " does not belong to " + this + " but to " + session.getSessionManager());
    }
  }


  /**
   * Creates a connection for a given db.
   *
   * @param db the db
   * @return the connection
   */
  private ManagedConnection createConnection(Db db) {
    ManagedConnection con;

    try {
      con = new ManagedConnection(this, db.getBackend(), db.connect());
    }
    catch (SQLException e) {
      throw new PersistenceException(e);
    }

    if (!con.getAutoCommit()) {
      con.close();
      throw new PersistenceException(this + ": connection " + con + " is not in autoCommit mode");
    }
    con.setMaxCountForClearWarnings(maxCountForClearWarnings);
    return con;
  }

}
