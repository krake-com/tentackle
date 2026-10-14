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
import org.tentackle.common.InterruptedRuntimeException;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.log.Logger;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


interface ManagedConnectionMonitorHolder {
  ManagedConnectionMonitor INSTANCE = createAndStartMonitor();

  private static ManagedConnectionMonitor createAndStartMonitor() {
    ManagedConnectionMonitor monitor = ServiceFactory.createService(ManagedConnectionMonitor.class, ManagedConnectionMonitor.class);
    monitor.start();
    return monitor;
  }
}


/**
 * Maintains a set of all open managed connections.<br>
 * The connections are held by a weak reference, and the set is cleaned up once a minute.<br>
 * Optionally, if {@link ManagedConnection#isConnectionKeepAliveEnabled()}, idle connections with
 * {@link ManagedConnection#isConnectionVerificationNecessary()} {@code == true}
 * will be verified (via dummy SELECTs) periodically to prevent premature closing by the database backend.
 * This is especially useful for databases running in the cloud, where the idle connection interval
 * cannot be deactivated or made longer than the idle times maintained by the {@link MpxConnectionManager}.
 * <p>
 * Notice: the monitor thread never stops! It is started when first referenced by
 * {@link ManagedConnectionMonitor#getInstance()}.
 */
@Service(ManagedConnectionMonitor.class)    // defaults to self
public class ManagedConnectionMonitor extends Thread {

  /**
   * Gets the running monitor thread.
   *
   * @return the thread (singleton)
   */
  public static ManagedConnectionMonitor getInstance() {
    return ManagedConnectionMonitorHolder.INSTANCE;
  }

  private static boolean running;

  /**
   * Returns whether the monitor is running.<br>
   * Useful to avoid {@link #getInstance()} since this will start it, if not running yet.
   *
   * @return true if running, false if no {@link ManagedConnection}s created ever (remote server, for example)
   */
  public static synchronized boolean isRunning() {
    return running;
  }

  private static synchronized void started() {
    running = true;
  }


  private static final Logger LOGGER = Logger.get(ManagedConnectionMonitor.class);

  private static final int WARN_CONNECTIONS_CLOSE_COUNT = 600;    // warning level for excessive connection consumption
  private static final int INFO_CONNECTIONS_CLOSE_COUNT = 60;     // info level for excessive connection consumption
  private static final String LOG_MESSAGE = "{0} managed connections closed within the last minute";


  private final Set<WeakReference<ManagedConnection>> managedConnections;   // weak set!

  /**
   * Creates the monitor thread.
   */
  public ManagedConnectionMonitor() {
    super("Managed Connection Monitor");
    setDaemon(true);
    managedConnections = ConcurrentHashMap.newKeySet();
  }

  /**
   * Registers a managed connection.
   *
   * @param managedConnection the managed connection
   */
  public void registerManagedConnection(ManagedConnection managedConnection) {
    managedConnections.add(new WeakReference<>(managedConnection));
  }

  /**
   * Gets a list of all open managed connections.
   *
   * @return the open connections
   */
  public Collection<ManagedConnection> getManagedConnections() {
    List<ManagedConnection> openConnections = new ArrayList<>();
    for (WeakReference<ManagedConnection> managedConnectionRef : managedConnections) {
      ManagedConnection managedConnection = managedConnectionRef.get();
      if (managedConnection != null && !managedConnection.isClosed()) {
        openConnections.add(managedConnection);
      }
      // we don't clean up here! this is done once a minute below...
    }
    return openConnections;
  }

  @Override
  public void run() {
    started();
    LOGGER.info("{0} started", getName());
    for (;;) {    // loops forever
      try {
        pause();
        loop();
      }
      catch (RuntimeException rx) {
        LOGGER.severe("verification failed -> keep on running...", rx);
      }
    }
  }


  /**
   * Pauses the thread between loop runs.
   */
  protected void pause() {
    try {
      sleep(Constants.MINUTE_MS);
    }
    catch (InterruptedException e) {
      throw new InterruptedRuntimeException(e);
    }
  }

  /**
   * A single loop run.
   */
  protected void loop() {
    long currentTimeMillis = System.currentTimeMillis();
    int closeCount = 0;

    for (Iterator<WeakReference<ManagedConnection>> iter = managedConnections.iterator(); iter.hasNext(); ) {
      ManagedConnection managedConnection = iter.next().get();
      if (managedConnection == null || managedConnection.isClosed()) {
        iter.remove();
        closeCount++;
      }
      else {
        inspect(managedConnection, currentTimeMillis);
      }
    }

    if (closeCount >= WARN_CONNECTIONS_CLOSE_COUNT) {
      LOGGER.warning(LOG_MESSAGE, closeCount);
    }
    else if (closeCount >= INFO_CONNECTIONS_CLOSE_COUNT) {
      LOGGER.info(LOG_MESSAGE, closeCount);
    }
    else {
      LOGGER.fine(LOG_MESSAGE, closeCount);
    }
  }

  /**
   * Inspects the idle connection.
   *
   * @param managedConnection the connection
   * @param currentTimeMillis the current epochal time
   */
  protected void inspect(ManagedConnection managedConnection, long currentTimeMillis) {
    if (managedConnection.isConnectionKeepAliveEnabled() &&
        managedConnection.isConnectionVerificationNecessary(currentTimeMillis) &&
        currentTimeMillis - managedConnection.getLastVerified() >= managedConnection.getConnectionInactivityTimeoutMs()) {

      // the connection manager makes sure that the connection isn't attached while being verified
      managedConnection.getManager().verifyIdleConnection(managedConnection);
      if (!managedConnection.isDead()) {
        LOGGER.fine("{0} verified", managedConnection);
      }
      // else marked DEAD! -> will be cleaned up when used again
    }
  }

}
