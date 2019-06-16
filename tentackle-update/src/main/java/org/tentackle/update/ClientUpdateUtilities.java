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
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.common.TentackleRuntimeException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Consumer;

interface ClientUpdateUtilitiesHolder {
  ClientUpdateUtilities INSTANCE = ServiceFactory.createService(ClientUpdateUtilities.class, ClientUpdateUtilities.class);
}

/**
 * Utility methods to implement the application update.
 */
@Service(ClientUpdateUtilities.class)
public class ClientUpdateUtilities {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static ClientUpdateUtilities getInstance() {
    return ClientUpdateUtilitiesHolder.INSTANCE;
  }

  private static final String[] DIR_NAMES = { "bin", "conf", "include", "legal", "lib", "release"};


  /**
   * Checks whether the application is running in an updatable directory.
   *
   * @return true if updatable
   */
  public boolean isApplicationUpdatable() {
    for (String dirName: DIR_NAMES) {
      File dir = new File(dirName);
      if (!dir.isDirectory() || !dir.canWrite()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Gets the remote update service object.
   *
   * @param serviceName the service name, for ex. {@code "rmi://localhost/Update:33002"}
   * @param csf the optional socket factory, null if default
   * @return the update service
   */
  public UpdateService getUpdateService(String serviceName, RMIClientSocketFactory csf) {
    try {
      URI uri = new URI(serviceName);
      String host = uri.getHost();
      int port = uri.getPort();
      String path = uri.getPath();
      if (path.startsWith("/")) {
        path = path.substring(1);
      }
      Registry registry = LocateRegistry.getRegistry(host, port, csf);
      return (UpdateService) registry.lookup(path);
    }
    catch (RemoteException | URISyntaxException | NotBoundException nx) {
      throw new TentackleRuntimeException("cannot connect to update service '" + serviceName + "'", nx);
    }
  }


  /**
   * Downloads the zipfile.
   *
   * @param updateInfo the update info
   * @param updateDir the directory to create for the update process
   * @param progressConsumer the consumer receiving the progress from 0.0 to 1.0, null if none
   * @return the downloaded ZIP file in the created update directory
   */
  public File dowloadZip(UpdateInfo updateInfo, File updateDir, Consumer<Double> progressConsumer) {
    try {
      // create or re-create update directory (must be empty!)
      if (!updateDir.mkdir()) {
        FileHelper.removeDirectory(updateDir.getName());
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
               Channels.newChannel(updateInfo.getUrl().openStream()), updateInfo.getSize(), progressConsumer);
           FileOutputStream fileOutputStream = new FileOutputStream(zipFile);
           FileChannel fileChannel = fileOutputStream.getChannel()) {

        fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
      }

      // check the sha256 hash
      byte[] buffer = new byte[8192];
      int count;
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(zipFile))) {
        while ((count = bis.read(buffer)) > 0) {
          digest.update(buffer, 0, count);
        }
      }
      String shaHash = StringHelper.fillLeft(new BigInteger(1, digest.digest()).toString(16), 32, '0');
      if (!shaHash.equals(updateInfo.getShaHash())) {
        throw new TentackleRuntimeException("checksum failed, expected " + updateInfo.getShaHash() +
                                            ", found " + shaHash);
      }

      return zipFile;
    }
    catch (NoSuchAlgorithmException | IOException ex) {
      throw new TentackleRuntimeException("download failed", ex);
    }
  }

}
