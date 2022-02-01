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



package org.tentackle.session;

import java.io.Serializable;


/**
 * Exception thrown if client's and server's versions don't match.
 *
 * @author harald
 */
public class VersionInfoIncompatibleException extends LoginFailedException {

  private static final long serialVersionUID = 1L;

  private final Serializable clientVersion;
  private final Serializable serverVersion;

  /**
   * Creates a version incompatible exception.
   *
   * @param message the optional message
   * @param clientVersion the client version
   * @param serverVersion the server version
   */
  public VersionInfoIncompatibleException(String message, Serializable clientVersion, Serializable serverVersion) {
    super(message);
    this.clientVersion = clientVersion;
    this.serverVersion = serverVersion;
  }

  /**
   * Gets the client's version.
   *
   * @return the version
   */
  public Serializable getClientVersion() {
    return clientVersion;
  }

  /**
   * Gets the server's version.
   *
   * @return the version
   */
  public Serializable getServerVersion() {
    return serverVersion;
  }

}
