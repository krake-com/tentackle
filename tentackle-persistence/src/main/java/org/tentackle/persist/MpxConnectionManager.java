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
import java.util.Date;
import java.util.Random;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.FormatHelper;
import org.tentackle.pdo.PersistenceException;
import org.tentackle.pdo.Session;



/**
 * Multiplexing connection manager.<br>
 *
 * A connection manager for applications with a large number of Db instances,
 * e.g. application servers. The manager will multiplex N Db instances against
 * M connections, allowing N &gt; M. This is not to be mixed up with db-pooling, as connection
 * multiplexing is completely transparent to the application whereas pooling requires
 * an explicit relation to something like a session.
 * Note that the authentication must be done at the application level because
 * the pool's connections are derived from the userinfo of a server db.
 *
 * @author harald
 */
public class MpxConnectionManager extends DefaultConnectionManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(MpxConnectionManager.class);

  // args

  /** server Db to clone. */
  final protected Db  serverDb;

  /** increment size. */
  final protected int incSize;

  /** minimum size. */
  final protected int minSize;

  /** min hours to close connections. */
  final protected int minMinutes;

  /** max hours to close connections. */
  final protected int maxMinutes;



  // local

  /** free list for unattached connections. */
  protected int[] unConList;

  /** number of entries in unConList. */
  protected int unConCount;

  /** randomizer. */
  protected Random random;

  /** true if initialized. */
  protected boolean initialized;



  // connect thread

  private boolean shutdownRequested;        // true if shutdown procedure initiated
  private Thread connectThread;             // thread to bring up connections
  private final Object connectGoMutex;      // mutex to trigger connect thread to go for more connections
  private final Object connectDoneMutex;    // mutex to signal waiting threads that connections were brought up
  private volatile int conRequestCount;     // number of new connections requested
  private int warnLogSize;                  // number of connections when to start WARNING logging
  private int infoLogSize;                  // number of connections when to start INFO logging



  /**
   * Creates a new connection manager.
   *
   * @param name the name of the connection manager
   * @param serverDb the root db to use for creating connections (may be open or closed)
   * @param maxDb the maximum number of Db instances, 0 = no limit
   * @param idOffset the offset for connection ids (&gt; 0)
   * @param iniSize the initial size of the connection pool
   * @param incSize the number of connections to add if all in use
   * @param minSize the minimum number of connections
   * @param maxSize the maximum number of connections
   * @param minMinutes minimum minutes a connection should be used
   * @param maxMinutes maximum minutes a connection should be used
   */
  public MpxConnectionManager(String name, Db serverDb, int maxDb, int idOffset, int iniSize, int incSize,
                              int minSize, int maxSize, int minMinutes, int maxMinutes) {

    super(name, iniSize, maxSize, idOffset);

    if (minSize > maxSize ||
        minMinutes > maxMinutes ||
        incSize < 0 ||
        incSize > (maxSize - iniSize) ||
        (maxDb > 0 && maxDb < iniSize)) {
      throw new IllegalArgumentException("illegal or conflicting parameters");
    }

    this.serverDb   = serverDb;
    this.maxDbSize  = maxDb;
    this.incSize    = incSize;
    this.minSize    = minSize;
    this.minMinutes = minMinutes;
    this.maxMinutes = maxMinutes;

    infoLogSize = maxConSize / 4;
    warnLogSize = maxConSize / 2;

    random = new Random();

    // setup initial connections
    unConList = new int[iniSize];

    // create and start the connect thread
    connectGoMutex   = new Object();
    connectDoneMutex = new Object();

    // connections are brought up in an extra thread
    connectThread = new ConnectionThread(getName() + " Connection Management Thread");
  }


  /**
   * Creates a connection manager with reasonable values for most servers.
   *
   * Maximum of 1000 Db instances (500 clients).
   * Start with 8 connections.
   * Add 2 connections at a time if all connections are in use.
   * Don't drop below 4 connections.
   * Maximum of 100 connections, i.e. concurrent db operations (i.e. info above 25, warning above 50)
   * Within 12 to 36h (approx. once a day), close and reopen connections
   * (allows updates of the database servers's QEPs, prepared statements cache, etc...).
   *
   * The idOffset will be the connectionId of the serverDb + 1 (i.e. normally 2 for application servers)
   *
   * @param serverDb the root db to use for creating connections (may be open or closed).
   */
  public MpxConnectionManager(Db serverDb) {
    this("mpx-mgr", serverDb, 1000, serverDb.getSessionId() + 1, 8, 2, 4, 100, 720, 2160);
  }




  /**
   * Shuts down this connection manager.<br>
   * All connections are closed and the threads stopped.
   * Application servers should invoke this method when shut down.
   */
  @Override
  public synchronized void shutdown() {
    shutdownRequested = true;
    connectThread.interrupt();
    try {
      connectThread.join();   // wait until connect thread terminates
    }
    catch (InterruptedException ex) {
      LOGGER.warning(this + ": stopping the connect thread failed", ex);
    }
    // close all connections
    super.shutdown();
    unConCount = 0;
  }


  /**
   * Gets the number of connections when to start info logging.
   * <p>
   * The default is 1/4th of the maximum number of connections.
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


  /**
   * Adds the index of an unused connection to the freelist.
   *
   * @param index the index of the connection in the connections list
   */
  protected void pushUnattached(int index) {
    if (unConCount >= unConList.length) {
      // list is full, enlarge it
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
   * @return the index to the connections list, -1 if no more unattached found
   */
  protected int popUnattached() {
    return unConCount > 0 ? unConList[--unConCount] : -1;
  }



  /**
   * Create spare connections.
   *
   * @param count the number of connections to create
   * @return the number of connections created
   */
  protected int createConnections(int count) {
    try {
      int conSize;
      synchronized(this) {
        conSize = getConnectionCount();
      }
      if (conSize + count < minSize) {
        // at least to minsize
        count = minSize - conSize;
      }
      // align count before we run into DbRuntimeException in addConnection()
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
              // Half of the connections concurrently in use in most cases tells us that the db-server
              // is reaching its limits, i.e. the operations take too long. Or maxConSize is simply too low.
              // Log attached connections.
              StringBuilder buf = new StringBuilder(msg).append("\n\n  attached connections:");
              for (ManagedConnection con: ManagedConnection.getManagedConnections()) {
                if (con.getManager() == this && con.isAttached()) {
                  buf.append("\n\n  ").append(con.toDiagnosticString());
                }
              }
              LOGGER.warning(buf.toString());
            }
            else {
              // pre warning
              LOGGER.info(msg);
            }
          }
        }
      }
      for (int i=0; i < count; i++) {
        ManagedConnection con = new ManagedConnection(this, serverDb.getBackend(), serverDb.connect());    // can take some time dep. on the db backend
        con.setExpireAt(con.getEstablishedSince() + (minMinutes*60 + random.nextInt((maxMinutes - minMinutes)*60)) * 1000L);
        LOGGER.info("{0}: open connection {1}, valid until {2}",
                    this, con, FormatHelper.formatTimestamp(new Date(con.getExpireAt())));
        synchronized(this) {
          pushUnattached(addConnection(con));   // add to established connections and unattached freelist
        }
      }
      return count;
    }
    catch (SQLException ex) {
      throw new PersistenceException(this + ": creating connection failed", ex);
    }
  }


  @Override
  public synchronized int login(Session session) {
    if (!initialized) {
      createConnections(iniSize);
      connectThread.start();
      initialized = true;
    }
    int id = addDb((Db) session) + idOffset;
    LOGGER.fine("{0} logged into {1}, id={2}", session, this, id);
    return id;
  }


  @Override
  public synchronized void logout(Session session) {
    assertSessionBelongsToMe(session);
    int index = convertConnectionIdToIndex(session.getSessionId());
    removeDb(index);
    LOGGER.fine("{0} logged out from {1}, id={2}", session, this, session.getSessionId());
    // check that connection is not attached, if so, detach it!
    ManagedConnection con = ((Db) session).getConnection();
    if (con != null) {
      con.forceDetached();
      if (con.isDead()) {
        // remove connection if dead and check to reopen
        cleanupDeadConnection(con);
        /**
         * Client crashed due to a dead server connection.
         * This is a strong indicator that the database is facing some severe
         * problems. Because we don't know how many unattached connections are down
         * we will probe them here. This is a little time consuming but better
         * than waiting for other clients to crash.
         */
        int[] newUnConList = new int[unConList.length];
        int newUnConCount  = 0;
        for (int i=0; i < unConCount; i++) {
          ManagedConnection c = conList[unConList[i]];
          c.verifyConnection();
          if (c.isDead()) {
            cleanupDeadConnection(c);
          }
          else  {
            newUnConList[newUnConCount++] = c.getIndex();
          }
        }
        unConList  = newUnConList;
        unConCount = newUnConCount;

        // open any missing connections
        reopenConnections();
      }
      else  {
        // add connection to freelist
        pushUnattached(con.getIndex());
      }
    }
  }


  @Override
  public void attach(Session session) {
    assertSessionBelongsToMe(session);
    ManagedConnection con = ((Db) session).getConnection();

    int loopCount = 0;   // number of retries so far

    while (con == null) {   // while not attached

      synchronized(this) {
        // find an unattached connection
        int conIndex = popUnattached();
        if (conIndex >= 0) {
          con = conList[conIndex];
          if (con.getIndex() != conIndex) {
            throw new PersistenceException(session, "connection " + con + " has wrong index " + con.getIndex() + ", expected " + conIndex);
          }
          /**
           * If connection is detached for a long time (minMinutes/2), verify the connection first.
           * This is mainly for databases like MySQL that close connections after a certain time
           * of inactivity. For those databases it is recommended to set the idle-timeout to minTime.
           */
          if (System.currentTimeMillis() - con.getDetachedSince() > minMinutes * 30000L) {
            /**
             * Although this will block all threads from attaching/detaching a connection,
             * this is the preferred solution compared to an extra "verification thread",
             * because that thread would need some synchronisation on the connection object
             * to prevent attachment while running the verification statement.
             * However, verification happens very rarely, so the performance would suffer
             * more from permanent and mostly unnecessary syncs than rare blocking verifications --
             * hopefully... ;)
             */
            con.verifyConnection();
          }

          if (con.isDead()) {
            cleanupDeadConnection(con);
            con = null;
            continue;
          }
          else  {
            break;
          }
        }
      }

      /**
       * In order not to stop all clients for the duration of establishing new connections
       * this is done in the connecThread. We will just trigger the thread and wait.
       */
      boolean request = false;
      boolean runningOutOfConnections = false;

      synchronized(this) {
        if (conRequestCount == 0) {
          // no request running: request more connections
          conRequestCount = incSize;
          request = true;
        }
        else if (conRequestCount < 0) {
          // max connections exhausted
          conRequestCount = 0;
          loopCount++;
          if (loopCount > 20) {    // we tried 20 times and it took us about a minute. Sorry :-(
            // this will probably close the client unfriendly, but what else can we do?
            throw new PersistenceException(this + ": max. number of concurrent connections in use: " + maxConSize);
          }
          runningOutOfConnections = true;
        }
        // else: some request is already running
      }

      if (runningOutOfConnections) {
        try {
          // sleep a randomly between 1 and 5 seconds (3 average)
          long ms = loopCount > 1 ? 1000 + random.nextInt(4000) : 100 + random.nextInt(400);
          LOGGER.warning(this + ": Running out of connections! Putting " + session + " to sleep for " + ms + " ms, loop " + loopCount);
          Thread.sleep(ms);
        }
        catch (InterruptedException ex) {}
      }

      if (request) {
        // start a new request for bringing up connections
        synchronized(connectGoMutex) {
          connectGoMutex.notifyAll();
        }
      }
      // wait for connections to be brought up
      synchronized(connectDoneMutex) {
        try {
          connectDoneMutex.wait(1000);     // wait max 1 second
        }
        catch (InterruptedException ex) {
          // just continue
        }
      }
    }

    con.attachSession((Db) session);
  }


  @Override
  public void detach(Session session) {
    assertSessionBelongsToMe(session);
    ManagedConnection con = ((Db) session).getConnection();
    if (con == null) {
      throw new PersistenceException(this + ": no connection attached to " + session);
    }
    con.detachSession((Db) session);

    if (!con.isAttached()) {

      boolean closeIt = false;

      synchronized(this) {

        if (!con.getAutoCommit()) {
          /**
           * The connection has been detached even though a transaction is still running!
           * This is bad and indicates some MT problem or that some thread keeps on using
           * a Db even if there has been an exception causing a rollback.
           * Anyway: mark the connection dead so that it is closed and cleaned up.
           * The Db is also closed to prevent any further use.
           */
          LOGGER.severe("connection " + con + " detached while still in transaction -> marked dead");
          con.setDead(true);
        }

        if (con.isDead()) {
          cleanupDeadConnection(con);
          try {
            // close the db to make it unusable anymore
            session.close();
          }
          catch (RuntimeException rex) {
            LOGGER.warning("closing Db failed", rex);
          }
        }
        else if (con.getExpireAt() > 0 && con.getExpireAt() < con.getDetachedSince()) {
          // connection time elapsed: close it
          removeConnection(con.getIndex());
          closeIt = true;    // will close below
        }
        else  {
          // add unattached connection
          pushUnattached(con.getIndex());
        }
      }

      if (closeIt) {
        LOGGER.info("{0}: closing connection {1}, open since {2}",
                    this, con, FormatHelper.formatTimestamp(new Date(con.getEstablishedSince())));
        con.close();
        reopenConnections();
      }
    }
  }


  @Override
  public synchronized void forceDetach(Session session) {
    assertSessionBelongsToMe(session);
    ManagedConnection con = ((Db) session).getConnection();
    if (con != null) {
      con.forceDetached();
      pushUnattached(con.getIndex());
    }
  }


  /**
   * closes a dead connection and removes it from the
   * connection list.
   */
  private void cleanupDeadConnection(ManagedConnection con) {
    // marked dead: close it (hard close) and remove it from connection list
    removeConnection(con.getIndex());
    try {
      LOGGER.warning(this + ": closing **DEAD** connection " + con);
      con.close();
    }
    catch (PersistenceException ex) { }
  }



  /**
   * checks whether the number of connections dropped below minSize.
   * If so, reopen missing connections.
   */
  private void reopenConnections() {
    boolean reopen = false;

    synchronized(this) {
      if (conRequestCount == 0) {
        // no connect running
        int num = minSize - getConnectionCount();
        if (num > 0) {
          // we dropped below minSize: open connection(s)
          reopen = true;
          conRequestCount = num;
        }
      }
    }

    if (reopen) {
      synchronized(connectGoMutex) {
        connectGoMutex.notifyAll();   // fire connect, but don't wait for completion
      }
    }
  }


  /**
   * connection management thread
   */
  private class ConnectionThread extends Thread {

    public ConnectionThread(String name) {
      super(name);
      setDaemon(true);
      setPriority(MAX_PRIORITY);
    }

    @Override
    public void run() {
      while (!shutdownRequested) {
        synchronized (connectGoMutex) {
          try {
            connectGoMutex.wait(1000);
          }
          catch (InterruptedException ex) {
          }
          if (!shutdownRequested && conRequestCount > 0) {
            /**
             * Bring up connections.
             * This will probably throw exceptions that we catch here and log them.
             * There's not much more we can do.
             */
            try {
              // create missing connections
              int count = createConnections(conRequestCount);
              synchronized (MpxConnectionManager.this) {
                conRequestCount = count == 0 ? -1 : 0; // -1 = max. connections exhausted
              }
            }
            catch (Exception e) {
              LOGGER.warning(MpxConnectionManager.this + ": creating connections failed", e);
            }
            synchronized (connectDoneMutex) {
              connectDoneMutex.notifyAll();
            }
          }
        }
      }
    }
  }

}
