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

import java.io.Serializable;

/**
 * Socket performance preferences as an object.
 *
 * @author harald
 */
public class SocketPerformancePreferences implements Serializable {

  private static final long serialVersionUID = 1L;

  private final int connectionTime;
  private final int latency;
  private final int bandwidth;

  /**
   * Creates a socket preferences object.
   *
   * @param connectionTime an {@code int} expressing the relative importance of
   * a short connection time
   * @param latency an {@code int} expressing the relative importance of low
   * latency
   * @param bandwidth an {@code int} expressing the relative importance of high
   * bandwidth
   */
  public SocketPerformancePreferences(int connectionTime, int latency, int bandwidth) {
    this.connectionTime = connectionTime;
    this.latency = latency;
    this.bandwidth = bandwidth;
  }

  /**
   * Gets the connection time.
   *
   * @return an {@code int} expressing the relative importance of
   * a short connection time
   */
  public int getConnectionTime() {
    return connectionTime;
  }

  /**
   * Gets the latency.
   *
   * @return an {@code int} expressing the relative importance of low
   * latency
   */
  public int getLatency() {
    return latency;
  }

  /**
   * Gets the bandwidth.
   *
   * @return an {@code int} expressing the relative importance of high
   * bandwidth
   */
  public int getBandwidth() {
    return bandwidth;
  }

  @Override
  public String toString() {
    return "[" + connectionTime + ',' + latency + ',' + bandwidth + ']';
  }

  @Override
  public int hashCode() {
    int hash = 3;
    hash = 11 * hash + this.connectionTime;
    hash = 11 * hash + this.latency;
    hash = 11 * hash + this.bandwidth;
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
    final SocketPerformancePreferences other = (SocketPerformancePreferences) obj;
    if (this.connectionTime != other.connectionTime) {
      return false;
    }
    if (this.latency != other.latency) {
      return false;
    }
    return this.bandwidth == other.bandwidth;
  }

}
