/*
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

package org.tentackle.persist.app;

import java.rmi.RemoteException;
import org.tentackle.log.Logger;
import org.tentackle.dbms.rmi.RmiServer;
import org.tentackle.dbms.rmi.RemoteDbConnectionImpl;
import org.tentackle.dbms.rmi.RemoteDbSessionImpl;
import org.tentackle.persist.LockManager;


/**
 * RMI Application Server.
 *
 * @author harald
 */
public abstract class ServerApplication extends AbstractServerApplication {

  /**
   * Gets the running server application.<br>
   *
   * @return the application
   */
  public static ServerApplication getServerApplication() {
    return (ServerApplication) getRunningApplication();
  }


  private static final Logger LOGGER = Logger.get(ServerApplication.class);

  private final Class<? extends RemoteDbConnectionImpl> connectionClass;    // the connection class
  private RmiServer rmiServer;                                              // the RMI server instance


  /**
   * Creates an instance of an application server.
   *
   * @param name the application name
   * @param connectionClass the class of the connection object to instantiate, null = default or from serverInfo's properties file
   */
  public ServerApplication(String name, Class<? extends RemoteDbConnectionImpl> connectionClass) {
    super(name);
    this.connectionClass = connectionClass;
  }


  /**
   * Gets the RMI server.
   *
   * @return the RMI server
   */
  public RmiServer getRmiServer() {
    return rmiServer;
  }


  @Override
  protected void startup() {
    super.startup();

    LOGGER.fine("start RMI services");
    // start the RMI-server
    startRmiServer();
  }


  /**
   * Creates the RMI-server instance (but does not start it).<br>
   * The default implementation creates a {@link RmiServer}.
   *
   * @param connectionClass the class of the connection object to instantiate,
   *                        null = default or from serverInfo's properties file
   * @return the created RmiServer
   */
  protected RmiServer createRmiServer(Class<? extends RemoteDbConnectionImpl> connectionClass) {
    return new RmiServer(getSessionInfo(), connectionClass);
  }


  @Override
  protected void finishStartup() {
    LockManager.getInstance().cleanupAllTokens(getSession());
    super.finishStartup();
    rmiServer = createRmiServer(connectionClass);
  }


  /**
   * Starts the RMI-server.<br>
   * The default implementation just does {@code rmiServer.start()}.
   */
  protected void startRmiServer() {
    rmiServer.start();
  }


  @Override
  protected void cleanup() {
    // kill all sessions and GC resources.
    for (RemoteDbSessionImpl session: RemoteDbSessionImpl.getOpenSessions()) {
      try {
        session.close();
      }
      catch (RemoteException | RuntimeException rex) {
        LOGGER.warning("closing pending session " + session + " failed:", rex);
      }
    }

    RemoteDbSessionImpl.stopCleanupThread();

    if (rmiServer != null) {
      rmiServer.stop();
      rmiServer = null;
    }

    super.cleanup();
  }

}
