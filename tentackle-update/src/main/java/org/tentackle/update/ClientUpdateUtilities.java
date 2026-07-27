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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.function.Consumer;

interface ClientUpdateUtilitiesHolder {
  ClientUpdateUtilities INSTANCE = ServiceFactory.createService(ClientUpdateUtilities.class, ClientUpdateUtilities.class);
}

/**
 * Utility methods to implement the application update.
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


  /**
   * Creates a client update utilities instance.
   */
  public ClientUpdateUtilities() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Checks whether given directory an updatable jlink image.
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
   * Checks whether the application is running in an updatable jlink directory.
   *
   * @return true if the current working directory contains an updatable jlink image
   */
  public boolean isUpdateableJlinkApplication() {
    return isUpdatableJlinkDirectory(new File("."));
  }

  /**
   * Checks whether
   * @return true if jlink structure and updatable
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
   * Determines the jpackage's root installation directory.
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
            rootDir = null;   // not a MacOS package
          }
        }
      }
      else { // linux
        if ("bin".equals(appDir.getName())) {
          rootDir = appDir.getParentFile();
          if (!new File(rootDir, "lib/app").isDirectory()) {
            rootDir = null; // not a linux package
          }
        }
      }
    }
    return rootDir;
  }

  /**
   * Determines the installation type of the running application.
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
   * Gets the remote update service object.
   *
   * @param serviceName the service name, for ex. {@code "trip://localhost/Update:33001"}
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
   * Gets the message digest to create the checksum of the downloaded zip file.
   *
   * @return the message digest
   * @throws NoSuchAlgorithmException if no such digest
   */
  public MessageDigest getMessageDigest() throws NoSuchAlgorithmException {
    return MessageDigest.getInstance("SHA-256");
  }

  /**
   * Downloads the zip-file.
   *
   * @param updateInfo the update info
   * @param updateDir the directory to create for the update process
   * @param progressConsumer the consumer receiving the progress from 0.0 to 1.0, null if none
   * @return the downloaded ZIP file in the created update directory
   */
  public File downloadZip(UpdateInfo updateInfo, File updateDir, Consumer<Double> progressConsumer) {
    try {
      // create or re-create the update directory (must be empty!)
      if (!updateDir.mkdir()) {
        FileHelper.removeDirectory(updateDir.getPath());
        if (!updateDir.mkdir()) {
          throw new TentackleRuntimeException("cannot create update directory");
        }
      }

      // build ZIP File object
      String zipName = updateInfo.getUrl().getPath();
      int ndx = zipName.lastIndexOf('/');
      if (ndx >= 0) {
        zipName = zipName.substring(ndx + 1);
      }
      File zipFile = new File(updateDir, zipName);

      // download the ZIP file into the update directory
      try (ReadableByteChannel readableByteChannel = new CallbackReadableByteChannel(
               Channels.newChannel(updateInfo.getUrl().openStream()), updateInfo.size(), progressConsumer);
           FileOutputStream fileOutputStream = new FileOutputStream(zipFile);
           FileChannel fileChannel = fileOutputStream.getChannel()) {

        fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
      }

      // check the hash
      byte[] buffer = new byte[8192];
      int count;
      MessageDigest digest = getMessageDigest();
      try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(zipFile))) {
        while ((count = bis.read(buffer)) > 0) {
          digest.update(buffer, 0, count);
        }
      }
      String expectedChecksum = updateInfo.checksum();
      String foundChecksum = StringHelper.fillLeft(new BigInteger(1, digest.digest()).toString(16), expectedChecksum.length(), '0');
      if (!foundChecksum.equals(expectedChecksum)) {
        throw new TentackleRuntimeException("checksum failed! (" + digest + ")\nexpected: " + expectedChecksum + "\nfound:    " + foundChecksum);
      }

      return zipFile;
    }
    catch (NoSuchAlgorithmException | IOException ex) {
      throw new TentackleRuntimeException("download failed", ex);
    }
  }

}
