/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.misc;

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;

import java.io.File;

/**
 * The tentackle directory within the user's home.<br>
 * Used to cache files, such as preferences, keyrings or downloaded updates.
 *
 * @author harald
 */
public class UserHomeJavaDir {

  private static final Logger LOGGER = Logger.get(UserHomeJavaDir.class);

  private static volatile File cacheDir;          // the cache directory


  /**
   * Gets the directory for tentackle files in the user's home directory.<br>
   * Defaults to {@code ${user.home}/.java/tentackle}.<br>
   * The base directory {@code ${user.home}} may be replaced by the system property
   * {@code "tentackle.user.home.java.dir"}, i.e. {@code .java/tentackle} is appended to it.<br>
   * The directory is created if it does not exist.
   *
   * @return the directory, never null
   * @throws TentackleRuntimeException if the directory could not be created or is not writable
   */
  public static File getDirectory() {
    File localDir = cacheDir;
    if (localDir == null) {
      synchronized (UserHomeJavaDir.class) {
        localDir = cacheDir;
        if (localDir == null) {
          localDir = new File(System.getProperty("tentackle.user.home.java.dir", System.getProperty("user.home")),
                              ".java" + File.separator + "tentackle");
          checkDir(localDir);
          cacheDir = localDir;
        }
      }
    }
    return localDir;
  }


  /**
   * Gets a subdirectory of the tentackle directory.<br>
   * The directories are created if they do not exist.
   *
   * @param subDirNames the chain of subdirectories
   * @return the directory, never null
   * @throws TentackleRuntimeException if the directory could not be created or is not writable
   * @see #getDirectory()
   */
  public static File getDirectory(String... subDirNames) {
    File subDir = getDirectory();
    for (String subDirName: subDirNames) {
      subDir = new File(subDir, subDirName);
    }
    checkDir(subDir);
    return subDir;
  }


  private static void checkDir(File dir) {
    if (!dir.exists()) {
      if (dir.mkdirs()) {
        LOGGER.info("created directory {0}", dir::getAbsolutePath);
      }
      else {
        throw new TentackleRuntimeException("couldn't create directory " + dir.getAbsolutePath());
      }
    }
    if (!dir.canWrite()) {
      throw new TentackleRuntimeException("directory " + dir.getAbsolutePath() + " is not writable");
    }
  }


  private UserHomeJavaDir() {
  }

}
