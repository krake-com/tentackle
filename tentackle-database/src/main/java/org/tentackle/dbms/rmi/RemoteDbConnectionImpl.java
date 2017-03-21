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


package org.tentackle.dbms.rmi;

import java.io.Serializable;
import java.net.BindException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import org.tentackle.common.ExceptionHelper;
import org.tentackle.io.ServerSocketConfigurator;
import org.tentackle.io.ServerSocketConfiguratorHolder;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.VersionInfoIncompatibleException;



/**
 * Server side RMI-connection implementation.
 * <p>
 * Overview of what happens in a Tentackle RMI application server:
 * <ul>
 * <li>client gets a reference to a RemoteDbConnection in the server</li>
 * <li>client invokes login() on the connection and gets a reference to
 *   a RemoteDbSession.</li>
 * <li>each session runs in its own thread (server & client) and the server
 *   session gets its own Db.</li>
 * <li>all further client requests work through the session object and
 *     the remote delegates created within the session</li>
 * <li>the client invokes logout() to close the session and Db.</li>
 * <li>in case the client terminates abnormally (without invoking logout())
 *   a timeout runs logout() via finalize().</li>
 * </ul>
 * @author harald
 */


abstract public class RemoteDbConnectionImpl extends RemoteServerObject implements RemoteDbConnection {

  private static final long serialVersionUID = -1008588987968414154L;

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteDbConnectionImpl.class);


  private final DbServer server;              // the server


  /**
   * Creates a connection.<br>
   *
   * @param server the DbServer
   * @param port the tcp-port, 0 = system default
   * @param csf the client socket factory, null = system default
   * @param ssf the server socket factory, null = system default
   * @throws RemoteException
   * @see DbServer
   */
  public RemoteDbConnectionImpl(DbServer server,
                                int port,
                                RMIClientSocketFactory csf,
                                RMIServerSocketFactory ssf) throws RemoteException {
    super(port, csf, ssf);
    this.server = server;
  }


  /**
   * Gets the DbServer.
   *
   * @return the db server
   */
  public DbServer getServer() {
    return server;
  }


  /**
   * Gets the session timeout count.
   *
   * @param session the session attached to the {@link RemoteDbSession}
   * @param clientInfo the UserInfo from the client
   * @param serverInfo the UserInfo to establish the connection to the database server
   * @return the timeout
   */
  public int getSessionTimeout(Session session, SessionInfo clientInfo, SessionInfo serverInfo) {
    return getServer().getSessionTimeout();
  }

  /**
   * Gets the tcp port for this connection.
   *
   * @param session the session attached to the {@link RemoteDbSession}
   * @param clientInfo the UserInfo from the client
   * @param serverInfo the UserInfo to establish the connection to the database server
   * @return the tcp-port for this connection, 0 = system default
   */
  public int getPort(Session session, SessionInfo clientInfo, SessionInfo serverInfo) {
    return getRMIPort();
  }

  /**
   * Gets the client socket factory according to the userinfos.
   *
   * @param session the session attached to the {@link RemoteDbSession}
   * @param clientInfo the UserInfo from the client
   * @param serverInfo the UserInfo to establish the connection to the database server
   * @return the client socket factory for this connection, null = system default
   */
  public RMIClientSocketFactory getClientSocketFactory(Session session, SessionInfo clientInfo, SessionInfo serverInfo) {
    return getRMICsf();
  }

  /**
   * Gets the server socket factory.
   *
   * @param session the session attached to the {@link RemoteDbSession}
   * @param clientInfo the UserInfo from the client
   * @param serverInfo the UserInfo to establish the connection to the database server
   * @return the server socket factory for this connection, null = system default
   */
  public RMIServerSocketFactory getServerSocketFactory(Session session, SessionInfo clientInfo, SessionInfo serverInfo) {
    return getRMISsf();
  }


  /**
   * Checks the client's version information.
   * <p>
   * The default implementation does nothing.
   * It is invoked from {@link org.tentackle.persist.Db#open()} for remote connections.
   *
   * @param clientVersionInfo the server's version info from {@link org.tentackle.persist.rmi.RemoteDbConnection}.
   * @throws VersionInfoIncompatibleException if versions are not compatible
   */
  public void checkClientVersionInfo(Serializable clientVersionInfo) throws VersionInfoIncompatibleException {
    // default is ok
  }



  /**
   * Creates the session.<br>
   * Needs to be implemented by the application.
   *
   * @param clientInfo the client info (login info)
   * @return the created session
   * @throws RemoteException if creation failed
   */
  abstract public RemoteDbSession createSession(SessionInfo clientInfo) throws RemoteException;


  /**
   * Exports the given remote object.
   * <p>
   * Notice that the delegate must not extend {@link UnicastRemoteObject}!
   *
   * @param remoteObject the object to export
   * @param port the port to export the object on, 0 if auto
   * @param csf the client-side socket factory for making calls to the remote object, null if system default
   * @param ssf the server-side socket factory for receiving remote calls, null if system default
   * @return the effective port, 0 if system default
   * @throws RemoteException if export failed
   */
  public int exportRemoteObject(Remote remoteObject, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf)
             throws RemoteException {

    if (remoteObject instanceof UnicastRemoteObject) {
      throw new RemoteException("delegate " + remoteObject.getClass().getName() +
              " must not extend UnicastRemoteObject!");
    }

    int portRange = 0;
    if (ssf instanceof ServerSocketConfiguratorHolder) {
      ServerSocketConfiguratorHolder sscd = (ServerSocketConfiguratorHolder) ssf;
      ServerSocketConfigurator ssc = sscd.getSocketConfigurator();
      if (ssc != null) {
        portRange = ssc.getPortRange();
        if (ssc.getPort() > 0) {
          port = ssc.getPort();
        }
      }
    }
    if (portRange < 1 || port == 0) {
      portRange = 1;
    }

    RemoteException lastEx = null;
    int effectivePort = -1;
    int maxPort = port + portRange;
    for (int p = port; p < maxPort; p++) {
      try {
        UnicastRemoteObject.exportObject(remoteObject, p, csf, ssf);
        effectivePort = p;
        break;
      }
      catch (RemoteException ex) {
        if (ExceptionHelper.extractException(BindException.class, true, ex) == null) {
          throw ex;   // some other exception
        }
        // BindException: try next port
        lastEx = ex;
      }
    }
    if (effectivePort == -1) {
      // export failed for all ports in range
      throw lastEx;   // cannot be null
    }

    LOGGER.fine("{0} exported on port {1}", remoteObject, effectivePort);

    return effectivePort;
  }

  /**
   * Exports the given remote object.
   *
   * @param remoteObject the object to unexport
   */
  public void unexportRemoteObject(Remote remoteObject) throws RemoteException {
    UnicastRemoteObject.unexportObject(remoteObject, true);
    LOGGER.fine("{0} unexported", remoteObject);
  }

  /**
   * Overridden to detect unwanted garbage collection as this
   * should never happen.
   * If using DbServer, this will not happen because it keeps a reference
   * to the connection object.
   */
  @Override
  protected void finalize() throws Throwable {
    try {
      LOGGER.warning("Connection object " + getClass().getName() + " finalized unexpectedly:\n" + this);
    }
    catch (Exception ex) {
      // don't stop finalization if just the logging failed
    }
    finally {
      super.finalize();
    }
  }



  // ------------------ implements RemoteDbConnection -----------------

  @Override
  public Serializable getServerVersionInfo() throws RemoteException {
    return null;
  }


  @Override
  public RemoteDbSession login(SessionInfo clientInfo) throws RemoteException {
    // check client compatibility
    checkClientVersionInfo(clientInfo.getClientVersionInfo());
    // create the session
    RemoteDbSession session = createSession(clientInfo);
    // export the session
    if (session instanceof Exportable) {
      ((Exportable) session).exportMe();
    }
    return session;
  }


  @Override
  public void logout(RemoteDbSession session) throws RemoteException {
    session.close();
  }


}
