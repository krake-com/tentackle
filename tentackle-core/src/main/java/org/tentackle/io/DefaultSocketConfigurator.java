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

import java.io.Serial;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;

/**
 * The default implementation of a {@link SocketConfigurator}.
 *
 * @author harald
 */
public class DefaultSocketConfigurator extends SocketConfiguratorBase implements SocketConfigurator {

  @Serial
  private static final long serialVersionUID = 1L;


  /**
   * Gets SO_KEEPALIVE.
   *
   * @return null if do not configure
   */
  public Boolean getKeepAlive() {
    return keepAlive;
  }

  /**
   * Sets SO_KEEPALIVE.
   *
   * @param keepAlive null if do not configure
   */
  public void setKeepAlive(Boolean keepAlive) {
    this.keepAlive = keepAlive;
  }

  /**
   * Gets SO_OOBINLINE.
   *
   * @return null if do not configure
   */
  public Boolean getOOBInline() {
    return oobInline;
  }

  /**
   * Sets SO_OOBINLINE.
   *
   * @param oobInline null if do not configure
   */
  public void setOOBInline(Boolean oobInline) {
    this.oobInline = oobInline;
  }


  /**
   * Gets TCP_NODELAY.
   *
   * @return null if do not configure
   */
  public Boolean getTcpNoDelay() {
    return tcpNoDelay;
  }

  /**
   * Sets TCP_NODELAY.
   *
   * @param tcpNoDelay null if do not configure
   */
  public void setTcpNoDelay(Boolean tcpNoDelay) {
    this.tcpNoDelay = tcpNoDelay;
  }


  /**
   * Gets SO_SNDBUF.
   *
   * @return null if do not configure
   */
  public Integer getSendBufferSize() {
    return sendBufferSize;
  }

  /**
   * Sets SO_SNDBUF.
   *
   * @param sendBufferSize null if do not configure
   */
  public void setSendBufferSize(Integer sendBufferSize) {
    this.sendBufferSize = sendBufferSize;
  }

  /**
   * Gets SO_LINGER.
   *
   * @return null if do not configure, -1 to disable
   */
  public Integer getSoLinger() {
    return soLinger;
  }

  /**
   * Sets SO_LINGER.
   *
   * @param soLinger null if do not configure, -1 to disable
   */
  public void setSoLinger(Integer soLinger) {
    this.soLinger = soLinger;
  }


  /**
   * Gets IP_TOS.
   *
   * @return null if do not configure
   */
  public Integer getTrafficClass() {
    return trafficClass;
  }

  /**
   * Sets IP_TOS.
   *
   * @param trafficClass null if do not configure
   */
  public void setTrafficClass(Integer trafficClass) {
    this.trafficClass = trafficClass;
  }



  @Override
  public void configure(Socket socket) throws SocketException {
    if (keepAlive != null) {
      socket.setKeepAlive(keepAlive);
    }
    if (oobInline != null) {
      socket.setOOBInline(oobInline);
    }
    if (performancePreferences != null) {
      socket.setPerformancePreferences(performancePreferences.getConnectionTime(),
                                       performancePreferences.getLatency(),
                                       performancePreferences.getBandwidth());
    }
    if (reuseAddress != null) {
      socket.setReuseAddress(reuseAddress);
    }
    if (sendBufferSize != null) {
      socket.setSendBufferSize(sendBufferSize);
    }
    if (soLinger != null) {
      if (soLinger < 0) {
        socket.setSoLinger(false, 0);
      }
      else  {
        socket.setSoLinger(true, soLinger);
      }
    }
    if (soTimeout != null) {
      socket.setSoTimeout(soTimeout);
    }
    if (tcpNoDelay != null) {
      socket.setTcpNoDelay(tcpNoDelay);
    }
    if (trafficClass != null) {
      socket.setTrafficClass(trafficClass);
    }
  }

  @Override
  public boolean isValid() {
    return super.isValid() ||
           keepAlive != null || oobInline != null || tcpNoDelay != null ||
           sendBufferSize != null || soLinger != null || trafficClass != null;
  }

  @Override
  public int hashCode() {
    int hash = super.hashCode();
    if (hash != 0) {
      hash = 11 * hash + Objects.hashCode(keepAlive);
      hash = 11 * hash + Objects.hashCode(oobInline);
      hash = 11 * hash + Objects.hashCode(tcpNoDelay);
      hash = 11 * hash + Objects.hashCode(sendBufferSize);
      hash = 11 * hash + Objects.hashCode(soLinger);
      hash = 11 * hash + Objects.hashCode(trafficClass);
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
    final DefaultSocketConfigurator other = (DefaultSocketConfigurator) obj;
    if (!Objects.equals(keepAlive, other.keepAlive)) {
      return false;
    }
    if (!Objects.equals(oobInline, other.oobInline)) {
      return false;
    }
    if (!Objects.equals(tcpNoDelay, other.tcpNoDelay)) {
      return false;
    }
    if (!Objects.equals(sendBufferSize, other.sendBufferSize)) {
      return false;
    }
    if (!Objects.equals(soLinger, other.soLinger)) {
      return false;
    }
    return Objects.equals(trafficClass, other.trafficClass);
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(super.toString());
    if (keepAlive != null) {
      buf.append(", ").append(SO_KEEPALIVE).append('=').append(keepAlive);
    }
    if (oobInline != null) {
      buf.append(", ").append(SO_OOBINLINE).append('=').append(oobInline);
    }
    if (tcpNoDelay != null) {
      buf.append(", ").append(TCP_NODELAY).append('=').append(tcpNoDelay);
    }
    if (sendBufferSize != null) {
      buf.append(", ").append(SO_SNDBUF).append('=').append(sendBufferSize);
    }
    if (soLinger != null) {
      buf.append(", ").append(SO_LINGER).append('=').append(soLinger);
    }
    if (trafficClass != null) {
      buf.append(", ").append(IP_TOS).append('=').append(trafficClass);
    }
    String str = buf.toString();
    if (str.startsWith(", ")) {
      return str.substring(2);
    }
    return str;
  }

}
