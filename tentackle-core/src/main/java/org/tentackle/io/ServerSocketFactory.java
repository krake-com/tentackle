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
import java.rmi.server.RMIServerSocketFactory;
import java.util.Objects;

/**
 * Standard server socket factory.
 *
 * @author harald
 */
public class ServerSocketFactory implements RMIServerSocketFactory, ServerSocketConfiguratorHolder {

  protected volatile ServerSocketConfigurator socketConfigurator;   // the optional socket configurator


  @Override
  public ServerSocket createUnconfiguredServerSocket(int port) throws IOException {
    return new ServerSocket(port);
  }

  @Override
  public ServerSocket createServerSocket(int port) throws IOException {
    return createConfiguredServerSocket(port);
  }

  @Override
  public int hashCode() {
    int hash = 5;
    hash = 11 * hash + getClass().hashCode();
    hash = 11 * hash + Objects.hashCode(socketConfigurator);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final ServerSocketFactory other = (ServerSocketFactory) obj;
    return ServerSocketConfigurator.equals(this.socketConfigurator, other.socketConfigurator);
  }

  @Override
  public ServerSocketConfigurator getSocketConfigurator() {
    return socketConfigurator;
  }

  @Override
  public void setSocketConfigurator(ServerSocketConfigurator socketConfigurator) {
    this.socketConfigurator = socketConfigurator;
  }

}
