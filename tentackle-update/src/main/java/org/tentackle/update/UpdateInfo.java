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

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.trip.ClientTransportCloseRequest;
import org.tentackle.trip.TripDeduplicatable;
import org.tentackle.trip.TripReferencable;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Update info sent back to the client.
 * <p>
 * The record itself is not encrypted. To protect it in transit, the update service should be
 * published via the PSK-encrypted {@code tripe} transport rather than via TLS, because a new
 * server version may come with a new certificate the outdated client does not trust yet.
 * <p>
 * Requests the client to close the transport after the reply has been received,
 * since the client is about to terminate for the update.
 *
 * @param version the new application version
 * @param urlStr the string representation of the ZIP file's URL
 * @param checksum the SHA-256 checksum hash to verify the downloaded file
 * @param size the size of the ZIP file in bytes, -1 if unknown
 * @param updateExecutor the filename of the script to execute to perform the update
 *
 * @see ClientInfo
 * @see org.tentackle.trip.transport.EncryptedTransport
 */
@TripReferencable(TripReferencable.NEVER)
@TripDeduplicatable(TripDeduplicatable.NEVER)
public record UpdateInfo(String version, String urlStr, String checksum, long size, String updateExecutor)
       implements ClientTransportCloseRequest {

  /**
   * Creates the update info.
   *
   * @param version the new application version
   * @param url the URL of the ZIP file the client must download
   * @param checksum the SHA-256 checksum hash to verify the downloaded file
   * @param size the size of the ZIP file in bytes, -1 if unknown
   * @param updateExecutor the filename of the script to execute to perform the update
   */
  public UpdateInfo(String version, URL url, String checksum, long size, String updateExecutor) {
    this(version, url.toExternalForm(), checksum, size, updateExecutor);
  }

  /**
   * Gets the URL to download the update.
   *
   * @return the URL pointing to the ZIP file, usually via http or https
   */
  public URL getUrl() {
    try {
      return new URI(urlStr).toURL();
    }
    catch (URISyntaxException | MalformedURLException mx) {
      throw new TentackleRuntimeException(mx);
    }
  }

  @Override
  public boolean isCloseAllTransportsRequested() {
    // Close the transport gracefully after having received the update info.
    // This avoids nasty socket reset exceptions in the server when the client terminates
    // after updating and restarts.
    return true;
  }

}
