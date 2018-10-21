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

import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;

/**
 * Extends {@link UnicastRemoteObject} to get the port and socket factories.
 *
 * @author harald
 */
public class RemoteServerObject extends UnicastRemoteObject {

  private static final long serialVersionUID = 1L;

  /**
   * @serial port number on which to export object
   */
  private final int port;

  /**
   * @serial client-side socket factory (if any)
   */
  private final RMIClientSocketFactory csf;

  /**
   * @serial server-side socket factory (if any) to use when exporting object
   */
  private final RMIServerSocketFactory ssf;

  /**
   * Creates and exports a new UnicastRemoteObject object using an anonymous
   * port.
   *
   * @throws RemoteException if failed to export object
   */
  public RemoteServerObject() throws RemoteException {
    this.port = 0;
    this.csf = null;
    this.ssf = null;
  }

  /**
   * Creates and exports a new UnicastRemoteObject object using the particular
   * supplied port.
   *
   * @param port the port number on which the remote object receives calls (if
   * <code>port</code> is zero, an anonymous port is chosen)
   *
   * @throws RemoteException if failed to export object
   */
  public RemoteServerObject(int port) throws RemoteException {
    super(port);
    this.port = port;
    this.csf = null;
    this.ssf = null;
  }

  /**
   * Creates and exports a new UnicastRemoteObject object using the particular
   * supplied port and socket factories.
   *
   * @param port the port number on which the remote object receives calls (if
   * <code>port</code> is zero, an anonymous port is chosen)
   * @param csf the client-side socket factory for making calls to the remote
   * object
   * @param ssf the server-side socket factory for receiving remote calls
   * @throws RemoteException if failed to export object
   */
  public RemoteServerObject(int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
    super(port, csf, ssf);
    this.port = port;
    this.csf = csf;
    this.ssf = ssf;
  }


  /**
   * Gets the port.
   *
   * @return the port, 0 if default
   */
  public int getRMIPort() {
    return port;
  }

  /**
   * Gets the client socket factory.
   *
   * @return the socket factory, null if default
   */
  public RMIClientSocketFactory getRMICsf() {
    return csf;
  }

  /**
   * Gets the server socket factory.
   *
   * @return the socket factory, null if default
   */
  public RMIServerSocketFactory getRMISsf() {
    return ssf;
  }

}
