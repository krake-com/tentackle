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
import org.tentackle.common.FileHelper;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TransportManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

interface ClientUpdateUtilitiesHolder {
  ClientUpdateUtilities INSTANCE = ServiceFactory.createService(ClientUpdateUtilities.class, ClientUpdateUtilities.class);
}

/**
 * Client side utility methods to implement the application update.<br>
 * Detects whether the application runs from an updatable jlink or jpackage image,
 * looks up the remote {@link UpdateService}, and downloads and verifies the update.
 * <p>
 * Replaceable via {@link Service}.
 */
@Service(ClientUpdateUtilities.class)
public class ClientUpdateUtilities {

  private static final Logger LOGGER = Logger.get(ClientUpdateUtilities.class);

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static ClientUpdateUtilities getInstance() {
    return ClientUpdateUtilitiesHolder.INSTANCE;
  }

  private static final String[] JLINK_SUBDIR_NAMES = {".", "bin", "conf", "include", "legal", "lib" };

  /** Default timeout for connecting to the download host and reading from it. */
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

  /** Default maximum size of a download of unknown size. */
  private static final long DEFAULT_MAX_DOWNLOAD_SIZE = 2L * 1024 * 1024 * 1024;

  /** Name of the downloaded ZIP if the URL does not provide a plain file name. */
  private static final String DEFAULT_ZIP_FILE_NAME = "update.zip";

  /** A plain file name without any path components. */
  private static final Pattern PLAIN_FILE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9._-]{0,254}");


  /**
   * Creates a client update utilities instance.
   */
  public ClientUpdateUtilities() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Checks whether the given directory holds an updatable jlink image.<br>
   * The directory itself and its subdirectories {@code bin}, {@code conf}, {@code include},
   * {@code legal} and {@code lib} must exist and be writable.
   *
   * @param dir the directory to check
   * @return true if updatable jlink structure found
   */
  public boolean isUpdatableJlinkDirectory(File dir) {
    boolean isJlinkDirectory = true;
    if (dir.isDirectory() && Files.isWritable(Paths.get(dir.getAbsolutePath()))) {
      for (String dirName : JLINK_SUBDIR_NAMES) {
        File subdir = new File(dir, dirName);
        if (!subdir.isDirectory() || !Files.isWritable(Paths.get(subdir.getAbsolutePath()))) {
          isJlinkDirectory = false;
          break;
        }
      }
    }
    else {
      isJlinkDirectory = false;
    }
    return isJlinkDirectory;
  }

  /**
   * Checks whether the application is running in an updatable jlink directory.<br>
   * Jlink applications are started from the image's root directory, so the current
   * working directory is checked.
   *
   * @return true if the current working directory contains an updatable jlink image
   * @see #isUpdatableJlinkDirectory(File)
   */
  public boolean isUpdateableJlinkApplication() {
    return isUpdatableJlinkDirectory(new File("."));
  }

  /**
   * Checks whether the application is running in an updatable jpackage installation.<br>
   * The runtime embedded in the package must be an updatable jlink image.
   * It is located in {@code runtime} (Windows), {@code Contents/runtime/Contents/Home} (macOS)
   * or {@code lib/runtime} (Linux) below the package's root directory.
   *
   * @return true if jpackage structure found and updatable
   * @see #determineJPackageRoot()
   * @see #isUpdatableJlinkDirectory(File)
   */
  public boolean isUpdatableJPackageApplication() {
    File runtimeDir = null;
    File rootDir = determineJPackageRoot();
    if (rootDir != null) {
      String platform = StringHelper.getPlatform();
      if (platform.contains("win")) {
        runtimeDir = new File(rootDir, "runtime");
      }
      else if (platform.contains("mac")) {
        runtimeDir = new File(rootDir, "Contents/runtime/Contents/Home");
      }
      else { // linux
        runtimeDir = new File(rootDir, "lib/runtime");
      }
    }
    return runtimeDir != null && isUpdatableJlinkDirectory(runtimeDir);
  }

  /**
   * Determines the jpackage's root installation directory.<br>
   * Derived from the path of the running executable:
   * <ul>
   *   <li>Windows: the executable's directory, which must contain {@code app}</li>
   *   <li>macOS: two levels above {@code Contents/MacOS}, which must contain {@code Contents}</li>
   *   <li>Linux: the parent of {@code bin}, which must contain {@code lib/app}</li>
   * </ul>
   *
   * @return the installation directory, null if not a jpackage installation
   */
  public File determineJPackageRoot() {
    File rootDir = null;
    Optional<String> javaCommand = ProcessHandle.current().info().command();
    if (javaCommand.isPresent()) {
      File appDir = new File(javaCommand.get()).getParentFile();
      String platform = StringHelper.getPlatform();
      if (platform.contains("win")) {
        rootDir = appDir;
        if (!new File(rootDir, "app").isDirectory()) {
          rootDir = null;   // not a windows package
        }
      }
      else if (platform.contains("mac")) {
        if ("MacOS".equals(appDir.getName())) {
          rootDir = appDir.getParentFile().getParentFile();
          if (!new File(rootDir, "Contents").isDirectory()) {
            rootDir = null;   // not a macOS package
          }
        }
      }
      else { // linux
        if ("bin".equals(appDir.getName())) {
          rootDir = appDir.getParentFile();
          if (!new File(rootDir, "lib/app").isDirectory()) {
            rootDir = null; // not a Linux package
          }
        }
      }
    }
    return rootDir;
  }

  /**
   * Determines the installation type of the running application.<br>
   * Jlink is checked first, then jpackage.
   *
   * @return the installation type, null if not an updatable installation
   */
  public InstallationType determineInstallationType() {
    InstallationType type = null;
    if (isUpdateableJlinkApplication()) {
      type = InstallationType.JLINK;
    }
    else if (isUpdatableJPackageApplication()) {
      type = InstallationType.JPACKAGE;
    }
    return type;
  }

  /**
   * Gets the remote update service object.<br>
   * If a {@link Cryptor} is configured, the service name may be given as an encrypted URL
   * disguised as an {@code http:}, {@code https:} or {@code rmi:} URL (see {@link Cryptor#deriveURL}).
   *
   * @param serviceName the service URL, for ex. {@code "trip://localhost:33001/Update"}
   * @return the update service
   */
  public UpdateService getUpdateService(String serviceName) {
    Cryptor cryptor = Cryptor.getInstance();
    if (cryptor != null) {
      serviceName = cryptor.deriveURL(serviceName, "http:", "https:", "rmi:");
    }
    URI uri = URI.create(serviceName);
    Transport transport = TransportManager.getInstance().requestTransport(uri);
    LOGGER.info("connecting to {0}", transport);
    return transport.lookup(UpdateService.class, uri.getPath());
  }

  /**
   * Gets the message digest to create the checksum of the downloaded ZIP file.
   *
   * @return the message digest, SHA-256
   * @throws NoSuchAlgorithmException if no such digest
   */
  public MessageDigest getMessageDigest() throws NoSuchAlgorithmException {
    return MessageDigest.getInstance("SHA-256");
  }

  /**
   * Gets the timeout for connecting to the download host and for each read from it.
   *
   * @return the timeout, 30 seconds by default
   */
  public Duration getTimeout() {
    return DEFAULT_TIMEOUT;
  }

  /**
   * Gets the maximum size of a download whose size the server did not announce.<br>
   * If the size is announced (see {@link UpdateInfo#size()}), the download must not exceed it.
   *
   * @return the maximum size in bytes, 2 GB by default
   */
  public long getMaxDownloadSize() {
    return DEFAULT_MAX_DOWNLOAD_SIZE;
  }

  /**
   * Validates the update info received from the server before anything is downloaded.<br>
   * The download URL must be {@code http} or {@code https}, a checksum must be provided,
   * and the update executor, if any, must be a plain file name, since it is resolved against
   * the {@code bin} directory of the unzipped update and executed.
   *
   * @param updateInfo the update info
   * @throws TentackleRuntimeException if the update info is invalid
   */
  public void validateUpdateInfo(UpdateInfo updateInfo) {
    String protocol = updateInfo.getUrl().getProtocol();
    if (!"https".equalsIgnoreCase(protocol) && !"http".equalsIgnoreCase(protocol)) {
      throw new TentackleRuntimeException("unsupported download protocol: " + protocol);
    }
    String checksum = updateInfo.checksum();
    if (checksum == null || checksum.isBlank()) {
      throw new TentackleRuntimeException("no checksum provided to verify the download of " + updateInfo.urlStr());
    }
    String updateExecutor = updateInfo.updateExecutor();
    if (updateExecutor != null && !isPlainFileName(updateExecutor)) {
      throw new TentackleRuntimeException("invalid update executor: " + updateExecutor);
    }
  }

  /**
   * Downloads the ZIP file and verifies its checksum.<br>
   * The update info is validated first (see {@link #validateUpdateInfo(UpdateInfo)}).
   * The update directory is created. If it already exists, it is removed and created again,
   * so it is always empty. The checksum is computed while downloading and compared case-insensitively
   * against {@link UpdateInfo#checksum()}. The download must not exceed the announced {@link UpdateInfo#size()},
   * or {@link #getMaxDownloadSize()} if no size was announced. If the download fails, the ZIP file is removed.
   *
   * @param updateInfo the update info
   * @param updateDir the directory to create for the update process
   * @param progressConsumer the consumer receiving the progress from 0.0 to 1.0 (negative if the size is unknown), null if none
   * @return the downloaded ZIP file in the created update directory
   * @throws TentackleRuntimeException if the update info is invalid, the download failed or exceeded the size,
   *         or the checksum does not match
   */
  public File downloadZip(UpdateInfo updateInfo, File updateDir, Consumer<Double> progressConsumer) {
    validateUpdateInfo(updateInfo);
    String expectedChecksum = updateInfo.checksum().trim();
    long maxSize = updateInfo.size() >= 0 ? updateInfo.size() : getMaxDownloadSize();

    try {
      // create or re-create the update directory (must be empty!)
      if (!updateDir.mkdir()) {
        FileHelper.removeDirectory(updateDir.getPath());
        if (!updateDir.mkdir()) {
          throw new TentackleRuntimeException("cannot create update directory");
        }
      }

      File zipFile = new File(updateDir, createZipFileName(updateInfo));
      boolean verified = false;
      try {
        // download the ZIP file into the update directory and compute its digest on the fly
        MessageDigest digest = getMessageDigest();
        URLConnection connection = updateInfo.getUrl().openConnection();
        connection.setConnectTimeout((int) getTimeout().toMillis());
        connection.setReadTimeout((int) getTimeout().toMillis());
        long size;
        try (ReadableByteChannel readableByteChannel = new CallbackReadableByteChannel(
                 Channels.newChannel(new DigestInputStream(connection.getInputStream(), digest)),
                 updateInfo.size(), progressConsumer);
             FileOutputStream fileOutputStream = new FileOutputStream(zipFile);
             FileChannel fileChannel = fileOutputStream.getChannel()) {

          // one byte more than allowed to detect an oversized download
          size = fileChannel.transferFrom(readableByteChannel, 0, maxSize == Long.MAX_VALUE ? maxSize : maxSize + 1);
        }
        if (size > maxSize) {
          throw new TentackleRuntimeException("download of " + updateInfo.urlStr() + " exceeds " + maxSize + " bytes");
        }

        // check the hash, padded to the digest's own hex length, not to the length of whatever the server sent
        byte[] digestBytes = digest.digest();
        String foundChecksum = StringHelper.fillLeft(new BigInteger(1, digestBytes).toString(16), digestBytes.length * 2, '0');
        // the hash is hex, so the case carries no information: sha256sum writes it lowercase, certutil uppercase
        if (!foundChecksum.equalsIgnoreCase(expectedChecksum)) {
          throw new TentackleRuntimeException("checksum failed! (" + digest.getAlgorithm() + ")\nexpected: " + expectedChecksum + "\nfound:    " + foundChecksum);
        }
        verified = true;
        return zipFile;
      }
      finally {
        if (!verified && zipFile.exists() && !zipFile.delete()) {
          LOGGER.warning("could not remove unverified download {0}", zipFile);
        }
      }
    }
    catch (NoSuchAlgorithmException | IOException ex) {
      throw new TentackleRuntimeException("download failed", ex);
    }
  }

  /**
   * Creates the name of the downloaded ZIP file in the update directory.<br>
   * The default is the last segment of the download URL's path, if it is a plain file name,
   * else {@code update.zip}.
   *
   * @param updateInfo the update info
   * @return the file name
   */
  protected String createZipFileName(UpdateInfo updateInfo) {
    String zipName = updateInfo.getUrl().getPath();
    int ndx = zipName.lastIndexOf('/');
    if (ndx >= 0) {
      zipName = zipName.substring(ndx + 1);
    }
    return isPlainFileName(zipName) ? zipName : DEFAULT_ZIP_FILE_NAME;
  }

  /**
   * Returns whether a name sent by the server is a plain file name.<br>
   * Only letters, digits, dots, dashes and underscores are allowed, and it must not start with a dot or dash.
   * This excludes path separators, {@code .}, {@code ..}, drive letters and NTFS alternate data streams.
   *
   * @param name the name
   * @return true if plain file name
   */
  protected boolean isPlainFileName(String name) {
    return name != null && PLAIN_FILE_NAME_PATTERN.matcher(name).matches();
  }

}
