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

package org.tentackle.io;

import java.net.ServerSocket;
import java.net.SocketException;
import java.util.Objects;

/**
 * The default implementation of a {@link SocketConfigurator}.
 *
 * @author harald
 */
public class DefaultServerSocketConfigurator extends SocketConfiguratorBase implements ServerSocketConfigurator {

  private static final long serialVersionUID = 1L;


  @Override
  public int getPort() {
    return port;
  }

  /**
   * Sets the port.
   *
   * @param port the port, 0 if default
   */
  public void setPort(int port) {
    this.port = port;
  }


  @Override
  public int getPortRange() {
    return portRange;
  }

  /**
   * Sets the port range.
   *
   * @param portRange the port range
   */
  public void setPortRange(int portRange) {
    this.portRange = portRange;
  }


  /**
   * Gets SO_RCVBUF.
   *
   * @return null if do not configure
   */
  public Integer getReceiveBufferSize() {
    return receiveBufferSize;
  }

  /**
   * Sets SO_RCVBUF.
   *
   * @param receiveBufferSize null if do not configure
   */
  public void setReceiveBufferSize(Integer receiveBufferSize) {
    this.receiveBufferSize = receiveBufferSize;
  }




  @Override
  public void configure(ServerSocket socket) throws SocketException {
    if (performancePreferences != null) {
      socket.setPerformancePreferences(performancePreferences.getConnectionTime(),
                                       performancePreferences.getLatency(),
                                       performancePreferences.getBandwidth());
    }
    if (receiveBufferSize != null) {
      socket.setReceiveBufferSize(receiveBufferSize);
    }
    if (reuseAddress != null) {
      socket.setReuseAddress(reuseAddress);
    }
    if (soTimeout != null) {
      socket.setSoTimeout(soTimeout);
    }
  }

  @Override
  public boolean isValid() {
    return super.isValid() ||
           port > 0 ||
           portRange > 1 ||
           receiveBufferSize != null;
  }

  @Override
  public int hashCode() {
    int hash = super.hashCode();
    if (hash != 0) {
      hash = 11 * hash + Objects.hashCode(port);
      hash = 11 * hash + Objects.hashCode(portRange);
      hash = 11 * hash + Objects.hashCode(receiveBufferSize);
    }
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (!super.equals(obj)) {
      return false;
    }
    if (obj == null) {
      return true;    // see SocketConfiguratorBase.equals()
    }
    final DefaultServerSocketConfigurator other = (DefaultServerSocketConfigurator) obj;
    if (port != other.port) {
      return false;
    }
    if (portRange != other.portRange) {
      return false;
    }
    return Objects.equals(receiveBufferSize, other.receiveBufferSize);
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(super.toString());
    if (receiveBufferSize != null) {
      buf.append(", ").append(SO_SNDBUF).append('=').append(sendBufferSize);
    }
    if (port > 0) {
      buf.append(", ").append(PORT).append('=').append(port);
    }
    if (portRange > 1) {
      buf.append(", ").append(RANGE).append('=').append(portRange);
    }
    String str = buf.toString();
    if (str.startsWith(", ")) {
      return str.substring(2);
    }
    return str;
  }

}
