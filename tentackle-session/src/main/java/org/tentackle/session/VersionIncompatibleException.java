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

package org.tentackle.session;

import java.io.Serial;

/**
 * Exception thrown if client's and server's versions don't match.
 *
 * @author harald
 */
public class VersionIncompatibleException extends LoginFailedException {

  @Serial
  private static final long serialVersionUID = 1L;

  private static final String UNKNOWN_VERSION = "?";

  private final String clientVersion;
  private final String serverVersion;

  /**
   * Creates a version incompatible exception with the unknown version information {@code "?"}.
   */
  public VersionIncompatibleException() {
    super();
    this.clientVersion = UNKNOWN_VERSION;
    this.serverVersion = UNKNOWN_VERSION;
  }

  /**
   * Creates a version incompatible exception.
   *
   * @param message the optional message
   * @param clientVersion the client version
   * @param serverVersion the server version
   */
  public VersionIncompatibleException(String message, String clientVersion, String serverVersion) {
    super(message);
    this.clientVersion = clientVersion;
    this.serverVersion = serverVersion;
  }

  /**
   * Gets the client's version.
   *
   * @return the version
   */
  public String getClientVersion() {
    return clientVersion;
  }

  /**
   * Gets the server's version.
   *
   * @return the version
   */
  public String getServerVersion() {
    return serverVersion;
  }

}
