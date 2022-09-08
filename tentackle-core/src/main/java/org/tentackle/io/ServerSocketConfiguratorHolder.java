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

package org.tentackle.io;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * A holder for a {@link ServerSocketConfigurator}.
 *
 * @author harald
 */
public interface ServerSocketConfiguratorHolder {

  /**
   * Gets the socket configurator.
   *
   * @return the socket configurator, null if none
   */
  ServerSocketConfigurator getSocketConfigurator();

  /**
   * Sets the socket configurator.
   *
   * @param socketConfigurator the socket configurator, null if none
   */
  void setSocketConfigurator(ServerSocketConfigurator socketConfigurator);

  /**
   * Creates an un-configured server socket on the specified port (port 0
   * indicates an anonymous port).
   *
   * @param port the port number
   * @return the server socket on the specified port
   * @exception IOException if an I/O error occurs during server socket creation
   */
  ServerSocket createUnconfiguredServerSocket(int port) throws IOException;

  /**
   * Creates a configured server socket on the specified port (port 0
   * indicates an anonymous port).
   *
   * @param port the port number
   * @return the server socket on the specified port
   * @exception IOException if an I/O error occurs during server socket creation
   */
  default ServerSocket createConfiguredServerSocket(int port) throws IOException {
    ServerSocketConfigurator configurator = getSocketConfigurator();
    ServerSocket socket = createUnconfiguredServerSocket(port);
    if (configurator != null) {
      configurator.configure(socket);
    }
    return socket;
  }

}
