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


package org.tentackle.dbms.rmi;

import org.tentackle.common.ExceptionHelper;
import org.tentackle.io.ServerSocketConfigurator;
import org.tentackle.io.ServerSocketConfiguratorHolder;
import org.tentackle.log.Logger;
import org.tentackle.session.SessionInfo;

import java.io.Serializable;
import java.net.BindException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;



/**
 * Server side RMI-connection implementation.
 * <p>
 Overview of what happens in a Tentackle RMI application rmiServer:
 <ul>
 * <li>client gets a reference to a RemoteDbConnection in the rmiServer</li>
 * <li>client invokes login() on the connection and gets a reference to
 *   a RemoteDbSession.</li>
 * <li>each session runs in its own thread (rmiServer and client) and the rmiServer
   session gets its own Db.</li>
 * <li>all further client requests work through the session object and
 *     the remote delegates created within the session</li>
 * <li>the client invokes logout() to close the session and Db.</li>
 * <li>in case the client terminates abnormally (without invoking logout())
 *   a timeout runs logout() via finalize().</li>
 * </ul>
 * @author harald
 */


public abstract class RemoteDbConnectionImpl extends RemoteServerObject implements RemoteDbConnection {

  private static final long serialVersionUID = -1008588987968414154L;

  private static final Logger LOGGER = Logger.get(RemoteDbConnectionImpl.class);


  private final transient RmiServer rmiServer;              // the rmiServer


  /**
   * Creates a connection.<br>
   *
   * @param rmiServer the RMI-server
   * @param port the tcp-port, 0 = system default
   * @param csf the client socket factory, null = system default
   * @param ssf the rmiServer socket factory, null = system default
   * @throws RemoteException if failed to export object
   * @see RmiServer
   */
  public RemoteDbConnectionImpl(RmiServer rmiServer,
                                int port,
                                RMIClientSocketFactory csf,
                                RMIServerSocketFactory ssf) throws RemoteException {
    super(port, csf, ssf);
    this.rmiServer = rmiServer;
  }


  /**
   * Gets the RMI-server.
   *
   * @return the RMI server
   */
  public RmiServer getRmiServer() {
    return rmiServer;
  }




  /**
   * Checks the client's version information.
   * <p>
   * The default implementation does nothing.
   * It is invoked only for remote connections.
   * <p>
   * Throws {@link org.tentackle.session.VersionInfoIncompatibleException} if versions are not compatible.
   *
   * @param clientVersionInfo the rmiServer's version info from {@link org.tentackle.dbms.rmi.RemoteDbConnection}.
   */
  public void checkClientVersionInfo(Serializable clientVersionInfo) {
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
  public abstract RemoteDbSession createSession(SessionInfo clientInfo) throws RemoteException;


  /**
   * Exports the given remote object.
   * <p>
   * Notice that the delegate must not extend {@link UnicastRemoteObject}!
   *
   * @param remoteObject the object to export
   * @param port the port to export the object on, 0 if auto
   * @param csf the client-side socket factory for making calls to the remote object, null if system default
   * @param ssf the rmiServer-side socket factory for receiving remote calls, null if system default
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
        LOGGER.fine("exporting at port={0}, csf={1}, ssf={2}: {3}", p, csf, ssf, remoteObject);
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
    if (effectivePort == -1 && lastEx != null) {
      // export failed for all ports in range
      throw lastEx;
    }

    return effectivePort;
  }

  /**
   * Exports the given remote object.
   *
   * @param remoteObject the object to unexport
   * @throws RemoteException if the remote object is not currently exported
   */
  public void unexportRemoteObject(Remote remoteObject) throws RemoteException {
    UnicastRemoteObject.unexportObject(remoteObject, true);
    LOGGER.fine("{0} unexported", remoteObject);
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

}
