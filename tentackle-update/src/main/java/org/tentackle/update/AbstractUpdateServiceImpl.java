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
 * <p>
 * Derives the update info purely from a naming convention below the update base URL:
 * <pre>
 *   &lt;updateURL&gt;/&lt;artifactName&gt;-&lt;version&gt;-&lt;platform&gt;-&lt;architecture&gt;[-pkg].zip
 *   &lt;updateURL&gt;/&lt;artifactName&gt;-&lt;version&gt;-&lt;platform&gt;-&lt;architecture&gt;[-pkg].sha256
 * </pre>
 * where {@code -pkg} denotes a jpackage installation. The checksum hashes and the (known) download sizes
 * are cached per URL, so the HTTP server is contacted only once per artifact.
 * <p>
 * The service is invoked by all clients concurrently and is therefore thread-safe.
 * Applications only implement {@link #getArtifactName()} and {@link #getVersion()} and must provide
 * a public constructor with a single {@code String} argument, the update base URL,
 * as required by {@link ServerUpdateUtilities#startUpdateService}.
 * All other steps are {@code protected} methods that can be overridden to deviate from the convention.
 */
public abstract class AbstractUpdateServiceImpl implements UpdateService {

  private final Map<String, Long> downloadSizes = new ConcurrentHashMap<>();      // size in bytes of the download ZIPs
  private final Map<String, String> checksumHashes = new ConcurrentHashMap<>();   // checksum hashes of the download ZIPs

  private volatile String updateURL;    // read by all clients concurrently

  /**
   * Creates an update service object.
   *
   * @param updateURL the HTTP(S) base URL to download the updates from
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
   * Gets the application version offered to the clients.<br>
   * Usually the version of the server itself.
   *
   * @return the version
   */
  public abstract String getVersion();



  /**
   * Gets the update base URL.
   *
   * @return the URL, always with a trailing slash
   */
  public String getUpdateURL() {
    return updateURL;
  }

  /**
   * Sets the update base URL.<br>
   * A trailing slash is appended, if missing.
   *
   * @param updateURL the URL
   */
  public void setUpdateURL(String updateURL) {
    this.updateURL = updateURL.endsWith("/") ? updateURL : (updateURL + "/");
  }


  /**
   * {@inheritDoc}
   * <p>
   * The version offered is always {@link #getVersion()}, regardless of the client's version.
   *
   * @throws TentackleRuntimeException if the checksum or the download size could not be determined,
   *         for example because the artifact is not published
   */
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
      throw new TentackleRuntimeException("cannot create update info", ex);
    }
  }


  /**
   * Creates the update info to be returned to the client.
   *
   * @param version the new application version
   * @param zipURL the URL to download the ZIP file
   * @param checksumHash the SHA-256 checksum hash of the ZIP file
   * @param size the download size in bytes, -1 if unknown
   * @param updateExecutor the filename of the script to execute to perform the update
   * @return the update info
   */
  protected UpdateInfo createUpdateInfo(String version, URL zipURL, String checksumHash, long size, String updateExecutor) {
    return new UpdateInfo(version, zipURL, checksumHash, size, updateExecutor);
  }

  /**
   * Creates the name of the update executor.<br>
   * The script is located in the {@code bin} directory of the unzipped update.
   *
   * @param clientInfo the client info
   * @return the executor name, {@code update.cmd} for Windows, else {@code update.sh}
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
   * Creates the base URL name without extension.<br>
   * The default implementation returns
   * {@code <updateURL>/<artifactName>-<version>-<platform>-<architecture>}
   * with {@code -pkg} appended for jpackage installations.
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
   * Determines the checksum hash.<br>
   * The hash is the first whitespace-delimited token of the checksum file,
   * so the output of {@code sha256sum} can be used as is. The hash is cached.
   *
   * @param checksumURL the URL of the checksum hash file
   * @return the hash value
   * @throws IOException if the download failed or the file is empty
   */
  protected String determineChecksumHash(URL checksumURL) throws IOException {
    String checksumUrlStr = checksumURL.toString();
    String shaHash = checksumHashes.get(checksumUrlStr);
    if (shaHash == null) {
      StringTokenizer stok = new StringTokenizer(new String(FileHelper.download(checksumURL)));
      if (!stok.hasMoreTokens()) {
        throw new IOException("empty checksum file " + checksumUrlStr);
      }
      shaHash = stok.nextToken();
      checksumHashes.put(checksumUrlStr, shaHash);
    }
    return shaHash;
  }

  /**
   * Determines the download size of the ZIP file.<br>
   * Sends an HTTP {@code HEAD} request and takes the {@code content-length} of the response.
   * Redirects are followed, so the ZIP may be served by a CDN or mirror.
   * <p>
   * Only a determinate size is cached. An unknown size is not, because it would otherwise
   * be answered for this artifact until the server is restarted, even though the very next
   * request might get a proper content-length.
   *
   * @param zipURL the download URL
   * @return the size in bytes, -1 if no content-length in header response
   * @throws IOException if failed or the artifact is not available (HTTP status other than 2xx)
   */
  protected long determineDownloadSize(URL zipURL) throws IOException {
    String zipUrlStr = zipURL.toString();
    Long size = downloadSizes.get(zipUrlStr);
    if (size == null) {
      try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
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
        if (ex instanceof InterruptedException) {
          Thread.currentThread().interrupt();   // preserve the interrupt status
        }
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
