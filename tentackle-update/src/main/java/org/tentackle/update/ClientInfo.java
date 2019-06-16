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

package org.tentackle.update;

import org.tentackle.common.StringHelper;

import java.io.Serializable;

/**
 * Client application info sent to the update server.
 */
public class ClientInfo implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String application;
  private final String version;
  private final String platform;
  private final String architecture;

  /**
   * Creates a client application info.
   *
   * @param application the client application name
   * @param version the client application version
   * @param platform the operating system
   * @param architecture the hardware architecture
   */
  public ClientInfo(String application, String version, String platform, String architecture) {
    this.application = application;
    this.version = version;
    this.platform = platform;
    this.architecture = architecture;
  }

  /**
   * Creates a client application info.<br>
   * Platform and architecture are determined from the system properties.
   *
   * @param application the client application name
   * @param version the client application version
   */
  public ClientInfo(String application, String version) {
    this(application, version, StringHelper.getPlatform(), StringHelper.getArchitecture());
  }

  /**
   * Gets the application name.
   *
   * @return the client application
   */
  public String getApplication() {
    return application;
  }

  /**
   * Gets the application version.
   *
   * @return the version the client application is currently running
   */
  public String getVersion() {
    return version;
  }

  /**
   * Gets the client's platform.
   *
   * @return the platform
   */
  public String getPlatform() {
    return platform;
  }

  /**
   * Gets the architecture.
   *
   * @return the architecture
   */
  public String getArchitecture() {
    return architecture;
  }


  @Override
  public String toString() {
    return "ClientInfo{" +
           "application='" + application + '\'' +
           ", version='" + version + '\'' +
           ", platform='" + platform + '\'' +
           ", architecture='" + architecture + '\'' +
           '}';
  }
}
