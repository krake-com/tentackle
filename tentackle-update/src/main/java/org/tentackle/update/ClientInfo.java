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
 * @param platform     the operating system, such as {@code windows}, {@code macosx} or {@code linux}
 * @param architecture the hardware architecture, such as {@code amd64} or {@code aarch64}
 * @param installationType the installation type
 *
 * @see UpdateInfo
 * @see org.tentackle.trip.transport.EncryptedTransport
 */
@TripReferencable(TripReferencable.NEVER)
@TripDeduplicatable(TripDeduplicatable.NEVER)
public record ClientInfo(String application, String version, String platform,
                         String architecture, InstallationType installationType) {

  /**
   * Creates a client application-info.<br>
   * Platform and architecture are determined from the running JVM via
   * {@link StringHelper#getPlatform()} and {@link StringHelper#getArchitecture()}.
   *
   * @param application the client application name
   * @param version     the client application version
   * @param installationType the installation type
   */
  public ClientInfo(String application, String version, InstallationType installationType) {
    this(application, version, StringHelper.getPlatform(), StringHelper.getArchitecture(), installationType);
  }

}
