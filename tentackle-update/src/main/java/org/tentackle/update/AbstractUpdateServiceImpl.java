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
import org.tentackle.log.Logger;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.OptionalLong;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

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
 * The service is invoked before the login, so the {@link ClientInfo} is untrusted input:
 * it is checked by {@link #validateClientInfo(ClientInfo)} before it is used to build any URL.
 * <p>
 * The service is invoked by all clients concurrently and is therefore thread-safe.
 * Applications only implement {@link #getArtifactName()} and {@link #getVersion()} and must provide
 * a public constructor with a single {@code String} argument, the update base URL,
 * as required by {@link ServerUpdateUtilities#startUpdateService}.
 * All other steps are {@code protected} methods that can be overridden to deviate from the convention.
 */
public abstract class AbstractUpdateServiceImpl implements UpdateService {

  private static final Logger LOGGER = Logger.get(AbstractUpdateServiceImpl.class);

  /**
   * Maximum size of a checksum file.<br>
   * The file holds a hash and a file name only.
   */
  private static final long MAX_CHECKSUM_FILE_SIZE = 64 * 1024;

  /**
   * Maximum number of entries per metadata cache.<br>
   * There is one entry per published artifact, so this is a safety net only.
   */
  private static final int MAX_CACHE_ENTRIES = 256;

  /**
   * Default timeout for connecting to the update host and for its response.
   */
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

  /**
   * Valid platform and architecture names.<br>
   * Covers everything {@code StringHelper.getPlatform()} and {@code StringHelper.getArchitecture()} produce,
   * but nothing that could change the structure of a URL, such as {@code /}, {@code .}, {@code ?} or {@code #}.
   */
  private static final Pattern CLIENT_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,32}");

  /**
   * SHA-256 hash in hex.
   */
  private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");

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
   * <p>
   * The client info is validated first (see {@link #validateClientInfo(ClientInfo)}).
   * If the update info cannot be created, the reason is logged, but not passed on to the client,
   * which has not logged in.
   *
   * @throws TentackleRuntimeException if the client info is invalid, or the checksum or the download size
   *         could not be determined, for example because the artifact is not published
   */
  @Override
  public UpdateInfo getUpdateInfo(ClientInfo clientInfo) {
    validateClientInfo(clientInfo);
    try {
      URL zipURL = createZipURL(clientInfo);
      URL checksumURL = createChecksumURL(clientInfo);
      String checksumHash = determineChecksumHash(checksumURL);
      long size = determineDownloadSize(zipURL);
      String updateExecutor = createUpdateExecutor(clientInfo);
      return createUpdateInfo(getVersion(), zipURL, checksumHash, size, updateExecutor);
    }
    catch (IOException | RuntimeException ex) {
      // platform and architecture are validated, the other fields of the client info are not: don't log them
      LOGGER.warning("cannot create update info for " + clientInfo.platform() + "-" + clientInfo.architecture() +
                     " (" + clientInfo.installationType() + ")", ex);
      // no cause: its message may reveal internals, such as URLs and host names, to an unauthenticated client
      throw new TentackleRuntimeException("cannot create update info");
    }
  }


  /**
   * Validates the client info.<br>
   * The client info is sent by a client that has not logged in, and the platform and architecture
   * become part of the URLs the server fetches. Both must therefore consist of 1 to 32 letters,
   * digits or underscores. The installation type may be null, which is treated like {@link InstallationType#JLINK}.
   * <p>
   * The application name and version are not used by the default implementation and therefore not checked.
   * Subclasses that use them must validate them as well.
   *
   * @param clientInfo the client info
   * @throws TentackleRuntimeException if the client info is invalid
   */
  protected void validateClientInfo(ClientInfo clientInfo) {
    if (clientInfo == null || !isValidClientName(clientInfo.platform()) || !isValidClientName(clientInfo.architecture())) {
      throw new TentackleRuntimeException("invalid client info");
    }
  }

  /**
   * Returns whether a checksum hash is valid.<br>
   * The default implementation accepts SHA-256 hashes in hex, upper- or lowercase.
   * <p>
   * Anything else, such as the first word of an error page some CDNs deliver with status 200, is refused
   * rather than cached and passed on to every client, which would refuse the download as tampered.
   *
   * @param checksumHash the hash
   * @return true if valid
   */
  protected boolean isValidChecksum(String checksumHash) {
    return checksumHash != null && SHA256_PATTERN.matcher(checksumHash).matches();
  }

  /**
   * Gets the timeout for connecting to the update host and for its responses.<br>
   * A hanging update host must not block the threads of the update service for good.
   *
   * @return the timeout, 30 seconds by default
   */
  protected Duration getTimeout() {
    return DEFAULT_TIMEOUT;
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
   * <p>
   * The client info has already been validated by {@link #validateClientInfo(ClientInfo)}.
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
   * <p>
   * The file is fetched with an HTTP {@code GET}. Redirects are followed.
   *
   * @param checksumURL the URL of the checksum hash file
   * @return the hash value
   * @throws IOException if the download failed (HTTP status other than 2xx), the file is too large,
   *         empty, or does not start with a valid hash (see {@link #isValidChecksum(String)})
   */
  protected String determineChecksumHash(URL checksumURL) throws IOException {
    String checksumUrlStr = checksumURL.toString();
    String shaHash = checksumHashes.get(checksumUrlStr);
    if (shaHash == null) {
      StringTokenizer stok = new StringTokenizer(
          new String(download(checksumURL, MAX_CHECKSUM_FILE_SIZE), StandardCharsets.ISO_8859_1));
      if (!stok.hasMoreTokens()) {
        throw new IOException("empty checksum file " + checksumUrlStr);
      }
      shaHash = stok.nextToken();
      if (!isValidChecksum(shaHash)) {
        throw new IOException("malformed checksum in " + checksumUrlStr);
      }
      cache(checksumHashes, checksumUrlStr, shaHash);
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
      HttpResponse<Void> response = send(createHttpRequest(zipURL, "HEAD"), HttpResponse.BodyHandlers.discarding());
      if (!isSuccessful(response.statusCode())) {
        // don't cache the content-length of an error page as the size of the ZIP file!
        throw new IOException("cannot download " + zipUrlStr + ": HTTP status " + response.statusCode());
      }
      OptionalLong contentLength = response.headers().firstValueAsLong("content-length");
      size = contentLength.orElse(-1L);
      if (size >= 0) {
        cache(downloadSizes, zipUrlStr, size);
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

  /**
   * Creates the HTTP client to access the update host.<br>
   * Redirects are followed, except from HTTPS to HTTP.
   *
   * @return the client, shut down by the caller
   * @see #getTimeout()
   */
  protected HttpClient createHttpClient() {
    return HttpClient.newBuilder()
                     .followRedirects(HttpClient.Redirect.NORMAL)
                     .connectTimeout(getTimeout())
                     .build();
  }

  /**
   * Creates an HTTP request to the update host.
   *
   * @param url the URL
   * @param method the HTTP method, {@code GET} or {@code HEAD}
   * @return the request
   * @throws IOException if the URL is not a valid HTTP(S) URI
   * @see #getTimeout()
   */
  protected HttpRequest createHttpRequest(URL url, String method) throws IOException {
    try {
      return HttpRequest.newBuilder()
                        .uri(url.toURI())
                        .timeout(getTimeout())
                        .method(method, HttpRequest.BodyPublishers.noBody())
                        .build();
    }
    catch (URISyntaxException | IllegalArgumentException ex) {
      throw new IOException("invalid URL " + url, ex);
    }
  }


  /**
   * Downloads a small file into memory.
   *
   * @param url the URL
   * @param maxSize the maximum size in bytes
   * @return the contents
   * @throws IOException if the download failed, the HTTP status is not 2xx, or the file exceeds the maximum size
   */
  private byte[] download(URL url, long maxSize) throws IOException {
    // the content-length may be missing or wrong: the limiting handler counts the bytes actually received
    HttpResponse<byte[]> response = send(createHttpRequest(url, "GET"),
        HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), maxSize));
    if (!isSuccessful(response.statusCode())) {
      throw new IOException("cannot download " + url + ": HTTP status " + response.statusCode());
    }
    return response.body();
  }

  /**
   * Sends an HTTP request to the update host.<br>
   * In contrast to {@link HttpRequest#timeout()}, which covers the response headers only,
   * the {@link #getTimeout()} applies to the whole exchange, including the body.
   *
   * @param request the request
   * @param bodyHandler the response body handler
   * @param <T> the response body type
   * @return the response
   * @throws IOException if failed or timed out
   */
  private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) throws IOException {
    HttpClient client = createHttpClient();
    try {
      return client.sendAsync(request, bodyHandler).get(getTimeout().toMillis(), TimeUnit.MILLISECONDS);
    }
    catch (ExecutionException ex) {
      if (ex.getCause() instanceof IOException iox) {
        throw iox;
      }
      throw new IOException("request " + request + " failed", ex.getCause());
    }
    catch (TimeoutException ex) {
      throw new HttpTimeoutException("request " + request + " timed out");
    }
    catch (InterruptedException ex) {
      Thread.currentThread().interrupt();   // preserve the interrupt status
      throw new IOException("request " + request + " interrupted", ex);
    }
    finally {
      client.shutdownNow();   // not close(), which would wait for a hanging exchange to complete
    }
  }

  /**
   * Adds an entry to a metadata cache, unless the cache is full.
   *
   * @param cache the cache
   * @param key the key
   * @param value the value
   * @param <V> the value type
   */
  private <V> void cache(Map<String, V> cache, String key, V value) {
    if (cache.size() < MAX_CACHE_ENTRIES) {
      cache.put(key, value);
    }
  }

  /**
   * Returns whether a platform or architecture name sent by the client is valid.
   *
   * @param name the name
   * @return true if valid
   */
  private boolean isValidClientName(String name) {
    return name != null && CLIENT_NAME_PATTERN.matcher(name).matches();
  }

}
