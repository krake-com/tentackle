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

import org.tentackle.common.FileHelper;
import org.tentackle.common.TentackleRuntimeException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.OptionalLong;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side update service implementation.
 */
public abstract class AbstractUpdateServiceImpl implements UpdateService {

  private final Map<String, Long> downloadSizes = new ConcurrentHashMap<>();      // size in bytes of the download ZIPs
  private final Map<String, String> checksumHashes = new ConcurrentHashMap<>();   // checksum hashes of the download ZIPs

  private volatile String updateURL;    // read by all clients concurrently

  /**
   * Creates an update service object.
   *
   * @param updateURL the http url where to download the updates from
   */
  public AbstractUpdateServiceImpl(String updateURL) {
    setUpdateURL(updateURL);
  }


  /**
   * Gets the artifact name excluding the version.
   * <p>
   * Example:
   * <pre>admin-jlink-client</pre>
   *
   * @return the artifact name
   */
  public abstract String getArtifactName();

  /**
   * Gets the server version.
   *
   * @return the version
   */
  public abstract String getVersion();



  /**
   * Gets the update base URL.
   *
   * @return the url (with trailing slash!)
   */
  public String getUpdateURL() {
    return updateURL;
  }

  /**
   * Sets the update base URL.
   *
   * @param updateURL the url
   */
  public void setUpdateURL(String updateURL) {
    this.updateURL = updateURL.endsWith("/") ? updateURL : (updateURL + "/");
  }


  @Override
  public UpdateInfo getUpdateInfo(ClientInfo clientInfo) {
    try {
      URL zipURL = createZipURL(clientInfo);
      URL checksumURL = createChecksumURL(clientInfo);
      String checksumHash = determineChecksumHash(checksumURL);
      long size = determineDownloadSize(zipURL);
      String updateExecutor = createUpdateExecutor(clientInfo);
      return createUpdateInfo(getVersion(), zipURL, checksumHash, size, updateExecutor);
    }
    catch (IOException ex) {
      throw new RuntimeException("cannot create update info", ex);
    }
  }


  /**
   * Creates the update info to be returned to the client.
   *
   * @param version the new application version
   * @param zipURL the URL to download the zip file
   * @param checksumHash the checksum hash
   * @param size the download size
   * @param updateExecutor the filename to execute to perform the update
   * @return the update info
   */
  protected UpdateInfo createUpdateInfo(String version, URL zipURL, String checksumHash, long size, String updateExecutor) {
    return new UpdateInfo(version, zipURL, checksumHash, size, updateExecutor);
  }

  /**
   * Creates the name of the update executor.
   *
   * @param clientInfo the client info
   * @return the executor name
   */
  protected String createUpdateExecutor(ClientInfo clientInfo) {
    return clientInfo.platform().contains("win") ? "update.cmd" : "update.sh";
  }

  /**
   * Creates the URL for the ZIP file.
   *
   * @param clientInfo the client info
   * @return the URL
   */
  protected URL createZipURL(ClientInfo clientInfo) {
    try {
      return new URI(createBaseUrlName(clientInfo) + ".zip").toURL();
    }
    catch (URISyntaxException | MalformedURLException e) {
      throw new TentackleRuntimeException(e);
    }
  }

  /**
   * Creates the URL for the checksum hash file.
   *
   * @param clientInfo the client info
   * @return the URL
   */
  protected URL createChecksumURL(ClientInfo clientInfo) {
    try {
      return new URI(createBaseUrlName(clientInfo) + ".sha256").toURL();
    }
    catch (URISyntaxException | MalformedURLException e) {
      throw new TentackleRuntimeException(e);
    }
  }

  /**
   * Creates the base URL name without extension.
   *
   * @param clientInfo the client info
   * @return the URL string
   */
  protected String createBaseUrlName(ClientInfo clientInfo) {
    StringBuilder buf = new StringBuilder();
    buf.append(getUpdateURL())
       .append(getArtifactName())
       .append('-').append(getVersion())
       .append('-').append(clientInfo.platform())
       .append('-').append(clientInfo.architecture());
    if (clientInfo.installationType() == InstallationType.JPACKAGE) {
      buf.append("-pkg");
    }
    return buf.toString();
  }

  /**
   * Determines the checksum hash.
   *
   * @param checksumURL the URL of the checksum hash file
   * @return the hash value
   * @throws IOException if failed
   */
  protected String determineChecksumHash(URL checksumURL) throws IOException {
    String checksumUrlStr = checksumURL.toString();
    String shaHash = checksumHashes.get(checksumUrlStr);
    if (shaHash == null) {
      shaHash = new StringTokenizer(new String(FileHelper.download(checksumURL))).nextToken();
      checksumHashes.put(checksumUrlStr, shaHash);
    }
    return shaHash;
  }

  /**
   * Determines the download size of the ZIP file.
   * <p>
   * Only a determinate size is cached. An unknown size is not, because it would otherwise
   * be answered for this artifact until the server is restarted, even though the very next
   * request might get a proper content-length.
   *
   * @param zipURL the download URL
   * @return the size in bytes, -1 if no content-length in header response
   * @throws IOException if failed or the artifact is not available
   */
  protected long determineDownloadSize(URL zipURL) throws IOException {
    String zipUrlStr = zipURL.toString();
    Long size = downloadSizes.get(zipUrlStr);
    if (size == null) {
      try (HttpClient client = HttpClient.newHttpClient()) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(zipURL.toURI())
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (!isSuccessful(response.statusCode())) {
          // don't cache the content-length of an error page as the size of the ZIP file!
          throw new IOException("cannot download " + zipUrlStr + ": HTTP status " + response.statusCode());
        }
        OptionalLong contentLength = response.headers().firstValueAsLong("content-length");
        size = contentLength.orElse(-1L);
        if (size >= 0) {
          downloadSizes.put(zipUrlStr, size);
        }
      }
      catch (InterruptedException | URISyntaxException ex) {
        throw new IOException("cannot determine expected download size of " + zipUrlStr, ex);
      }
    }
    return size;
  }

  /**
   * Returns whether an HTTP status code denotes success.
   *
   * @param statusCode the status code
   * @return true if 2xx
   */
  protected boolean isSuccessful(int statusCode) {
    return statusCode >= 200 && statusCode < 300;
  }

}
