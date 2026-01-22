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
import org.tentackle.reflect.CanonicalConstructor;
import org.tentackle.reflect.CanonicalField;
import org.tentackle.trip.TripReferencable;

import java.util.Objects;

/**
 * Client application info sent to the update server.
 */
@TripReferencable(TripReferencable.NEVER)
public class ClientInfo {

  @CanonicalField(ordinal = 0) private final String application;
  @CanonicalField(ordinal = 1) private final String version;
  @CanonicalField(ordinal = 2) private final String platform;
  @CanonicalField(ordinal = 3)private final String architecture;
  @CanonicalField(ordinal = 4)private final InstallationType installationType;

  /**
   * Creates a client application-info.
   *
   * @param application the client application name
   * @param version the client application version
   * @param platform the operating system
   * @param architecture the hardware architecture
   */
  @CanonicalConstructor
  public ClientInfo(String application, String version, String platform, String architecture, InstallationType installationType) {
    this.application = Objects.requireNonNull(application);
    this.version = Objects.requireNonNull(version);
    this.platform = Objects.requireNonNull(platform);
    this.architecture = Objects.requireNonNull(architecture);
    this.installationType = Objects.requireNonNull(installationType);
  }

  /**
   * Creates a client application-info.<br>
   * Platform and architecture are determined from the system properties.
   *
   * @param application the client application name
   * @param version the client application version
   */
  public ClientInfo(String application, String version, InstallationType installationType) {
    this(application, version, StringHelper.getPlatform(), StringHelper.getArchitecture(), installationType);
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

  /**
   * Gets the installation type.
   *
   * @return the installation type
   */
  public InstallationType getInstallationType() {
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
