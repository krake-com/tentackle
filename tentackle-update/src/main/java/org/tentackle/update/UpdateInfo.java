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

import org.tentackle.common.Cryptor;
import org.tentackle.common.TentackleRuntimeException;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Update info sent back to the client.
 */
public class UpdateInfo implements Serializable {

  private static final long serialVersionUID = 1L;

  // transfer as strings because it may be encrypted
  private final String version;
  private final String url;
  private final String shaHash;
  private final String size;
  private final String updateExecutor;
  private final boolean encrypted;

  /**
   * Creates the update info.<br>
   * The internal state is encrypted if a {@link Cryptor} is configured.
   *
   * @param version the new application version
   * @param url the URL of the ZIP file the client must download
   * @param shaHash the saltless SHA256 hash to verify the downloaded file
   * @param size the size of the ZIP file
   * @param updateExecutor the filename to execute to perform the update
   */
  public UpdateInfo(String version, URL url, String shaHash, long size, String updateExecutor) {
    Cryptor cryptor = Cryptor.getInstance();
    if (cryptor != null) {
      this.version = cryptor.encrypt64(version);
      this.url = cryptor.encrypt64(url.toExternalForm());
      this.shaHash = cryptor.encrypt64(shaHash);
      this.size = cryptor.encrypt64(Long.toString(size));
      this.updateExecutor = cryptor.encrypt64(updateExecutor);
      encrypted = true;
    }
    else {
      this.version = version;
      this.url = url.toExternalForm();
      this.shaHash = shaHash;
      this.size = Long.toString(size);
      this.updateExecutor = updateExecutor;
      encrypted = false;
    }
  }

  /**
   * Gets the new application version.<br>
   * Just an info that can be shown to the user.
   *
   * @return the new version
   */
  public String getVersion() {
    return encrypted ? Cryptor.getInstance().decrypt64(version) : version;
  }

  /**
   * Gets the URL to download the update.
   *
   * @return the URL pointing to the ZIP file, usually via http or https
   */
  public URL getUrl() {
    try {
      return new URL(encrypted ? Cryptor.getInstance().decrypt64(url) : url);
    }
    catch (MalformedURLException mx) {
      throw new TentackleRuntimeException(mx);
    }
  }

  /**
   * Gets the SHA hash to verify the download.
   *
   * @return the hash
   */
  public String getShaHash() {
    return encrypted ? Cryptor.getInstance().decrypt64(shaHash) : shaHash;
  }

  /**
   * Gets the size of the ZIP file.
   *
   * @return the size
   */
  public long getSize() {
    return Long.parseLong(encrypted ? Cryptor.getInstance().decrypt64(size) : size);
  }

  /**
   * Gets the name of the file to perform the update.
   *
   * @return the updater, usually {@code "update.sh"} or {@code "update.cmd"}, depending on the platform
   */
  public String getUpdateExecutor() {
    return encrypted ? Cryptor.getInstance().decrypt64(updateExecutor) : updateExecutor;
  }

  @Override
  public String toString() {
    return "UpdateInfo{" +
           "version='" + getVersion() + '\'' +
           ", url=" + getUrl() +
           ", shaHash='" + getShaHash() + '\'' +
           ", size=" + getSize() +
           ", updateExecutor='" + getUpdateExecutor() + '\'' +
           '}';
  }
}
