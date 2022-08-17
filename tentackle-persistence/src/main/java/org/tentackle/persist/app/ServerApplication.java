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

import org.tentackle.dbms.rmi.RemoteDbConnectionImpl;
import org.tentackle.dbms.rmi.RemoteDbSessionImpl;
import org.tentackle.dbms.rmi.RmiServer;
import org.tentackle.log.Logger;
import org.tentackle.pdo.PdoFactory;
import org.tentackle.persist.LockManager;
import org.tentackle.validate.Validator;
import org.tentackle.validate.ValidatorCache;

import java.rmi.RemoteException;


/**
 * RMI Application Server.
 *
 * @author harald
 */
public abstract class ServerApplication extends AbstractServerApplication {

  private static final Logger LOGGER = Logger.get(ServerApplication.class);

  private final Class<? extends RemoteDbConnectionImpl> connectionClass;    // the connection class
  private RmiServer rmiServer;                                              // the RMI server instance


  /**
   * Creates an instance of an application server.
   *
   * @param name the application name
   * @param version the application version
   * @param connectionClass the class of the connection object to instantiate, null = default or from serverInfo's properties file
   */
  public ServerApplication(String name, String version, Class<? extends RemoteDbConnectionImpl> connectionClass) {
    super(name, version);
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


  @Override
  protected void initializeScripting() {
    super.initializeScripting();
    validateValidators();
  }

  /**
   * Validates the validators.<br>
   * The default implementation loads all {@link Validator}s of all PDO classes and
   * invokes {@link Validator#validate()}. This will load all {@link org.tentackle.misc.CompoundValue}s
   * and compiles the scripts, if any.
   */
  protected void validateValidators() {
    LOGGER.info("validating validators");
    for (String pdoName: PdoFactory.getInstance().getPersistenceMapper().getMap().keySet()) {
      Class<?> pdoClass = PdoFactory.getInstance().create(pdoName).getEffectiveClass();
      for (Validator validator: ValidatorCache.getInstance().getFieldValidators(pdoClass)) {
        validator.validate();
      }
    }
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
    if (!getSession().isRemote()) {
      LockManager.getInstance().initialize(getSession());
    }
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
