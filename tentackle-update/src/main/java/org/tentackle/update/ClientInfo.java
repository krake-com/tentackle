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
import org.tentackle.trip.TripDeduplicatable;
import org.tentackle.trip.TripReferencable;

/**
 * Client application info sent to the update server.
 *
 * @param application  the client application name
 * @param version      the client application version
 * @param platform     the operating system
 * @param architecture the hardware architecture
 * @param installationType the installation type
 */
@TripReferencable(TripReferencable.NEVER)
@TripDeduplicatable(TripDeduplicatable.NEVER)
public record ClientInfo(String application, String version, String platform,
                         String architecture, InstallationType installationType) {

  /**
   * Creates a client application-info.<br>
   * Platform and architecture are determined from the system properties.
   *
   * @param application the client application name
   * @param version     the client application version
   */
  public ClientInfo(String application, String version, InstallationType installationType) {
    this(application, version, StringHelper.getPlatform(), StringHelper.getArchitecture(), installationType);
  }

  /**
   * Gets the application name.
   *
   * @return the client application
   */
  @Override
  public String application() {
    return application;
  }

  /**
   * Gets the application version.
   *
   * @return the version the client application is currently running
   */
  @Override
  public String version() {
    return version;
  }

  /**
   * Gets the client's platform.
   *
   * @return the platform
   */
  @Override
  public String platform() {
    return platform;
  }

  /**
   * Gets the architecture.
   *
   * @return the architecture
   */
  @Override
  public String architecture() {
    return architecture;
  }

  /**
   * Gets the installation type.
   *
   * @return the installation type
   */
  @Override
  public InstallationType installationType() {
    return installationType;
  }

  @Override
  public String toString() {
    return "ClientInfo{" +
           "application='" + application + '\'' +
           ", version='" + version + '\'' +
           ", platform='" + platform + '\'' +
           ", architecture='" + architecture + '\'' +
           ", installationType=" + installationType +
           '}';
  }
}
