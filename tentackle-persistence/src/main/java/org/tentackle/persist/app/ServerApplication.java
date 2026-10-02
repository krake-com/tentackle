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

package org.tentackle.persist.app;

import org.tentackle.dbms.trip.RemoteDbConnectionImpl;
import org.tentackle.dbms.trip.RemoteDbSessionImpl;
import org.tentackle.dbms.trip.TripServer;
import org.tentackle.log.Logger;
import org.tentackle.persist.LockManager;


/**
 * Base class for TRIP application servers.
 * <p>
 * Starts a {@link TripServer} the clients connect to and, if connected directly to the database,
 * initializes the {@link LockManager}.
 *
 * @author harald
 */
public abstract class ServerApplication extends AbstractServerApplication {

  private static final Logger LOGGER = Logger.get(ServerApplication.class);

  private final Class<? extends RemoteDbConnectionImpl> connectionClass;    // the connection class
  private TripServer tripServer;                                            // the TRIP server instance


  /**
   * Creates an instance of an application server.
   *
   * @param name the application name, null for default name
   * @param version the application version, null for the default version
   * @param connectionClass the class of the connection object to instantiate, null = default or from the server's properties
   */
  public ServerApplication(String name, String version, Class<? extends RemoteDbConnectionImpl> connectionClass) {
    super(name, version);
    this.connectionClass = connectionClass;
  }


  /**
   * Gets the TRIP server.
   *
   * @return the TRIP server
   */
  public TripServer getTripServer() {
    return tripServer;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to start the TRIP server after the startup has finished.
   */
  @Override
  protected void startup() {
    super.startup();
    LOGGER.fine("start TRIP services");
    startTripServer();
  }


  /**
   * Creates the TRIP server instance (but does not start it).<br>
   * The default implementation creates a {@link TripServer}.
   *
   * @param connectionClass the class of the connection object to instantiate,
   *                        null = default or from server's properties
   * @return the created TRIP server
   */
  protected TripServer createTripServer(Class<? extends RemoteDbConnectionImpl> connectionClass) {
    return new TripServer(getSessionInfo(), connectionClass);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to initialize the {@link LockManager} (if connected directly to the database)
   * and to create the TRIP server.
   */
  @Override
  protected void finishStartup() {
    if (!getSession().isRemote()) {
      LockManager.getInstance().initialize(getSession());
      // see also org.tentackle.dbms.trip.RemoteDbSessionImpl.cleanup
    }
    super.finishStartup();
    tripServer = createTripServer(connectionClass);
  }


  /**
   * Starts the TRIP server.<br>
   * The default implementation just does {@code tripServer.start()}.
   */
  protected void startTripServer() {
    tripServer.start();
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to close all open client sessions and stop the TRIP server.
   */
  @Override
  protected void cleanup() {
    // kill all sessions and GC resources.
    for (RemoteDbSessionImpl session: RemoteDbSessionImpl.getOpenSessions()) {
      try {
        session.close();
      }
      catch (RuntimeException rex) {
        LOGGER.warning("closing pending session " + session + " failed:", rex);
      }
    }

    RemoteDbSessionImpl.stopCleanupThread();

    if (tripServer != null) {
      tripServer.stop();
      tripServer = null;
    }

    super.cleanup();
  }

}
