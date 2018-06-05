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
import java.net.Socket;
import java.net.SocketException;

/**
 * Configures a soeket.<br>
 * Sets socket options, etc...
 *
 * @author harald
 */
public interface SocketConfigurator extends Serializable {

  /**
   * Compares two socket configurators.
   * <p>
   * Invalid configurators are treated as null.
   *
   * @param sc1 the 1st configurator, null if none
   * @param sc2 the 2nd configurator, null if none
   * @return true if logically equal
   */
  static boolean equals(SocketConfigurator sc1, SocketConfigurator sc2) {
    if (sc1 == null) {
      return sc2 == null || !sc2.isValid();
    }
    if (sc2 == null) {
      return !sc1.isValid();
    }
    return sc1.equals(sc2);
  }


  /**
   * Configures the given socket.
   *
   * @param socket the socket
   * @throws SocketException if configuration failed
   */
  void configure(Socket socket) throws SocketException;

  /**
   * Returns whether this configurator contains valid configurations.
   *
   * @return true if options valid, false if {@link #configure} would do nothing at all
   */
  boolean isValid();

}
