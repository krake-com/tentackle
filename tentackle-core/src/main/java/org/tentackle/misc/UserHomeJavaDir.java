/*
 * Tentackle - http://www.tentackle.org.
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

import java.io.File;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;

/**
 * The user's home cache.
 *
 * @author harald
 */
public class UserHomeJavaDir {


  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(UserHomeJavaDir.class);


  private static volatile boolean initialized;    // true if cacheDir initialized
  private static volatile File cacheDir;          // the cache directory, null if cache backup disabled


  /**
   * Gets the directory tentackle files in the user's home directory.<br>
   * Defaults to {@code ${user.home}/.java/tentackle}.<br>
   * May be configured by the system property {@code "tentackle.user.home.java.dir"}.
   *
   * @return the directory
   */
  public static File getDirectory() {
    if (!initialized) {
      cacheDir = new File(System.getProperty("tentackle.user.home.java.dir", System.getProperty("user.home")),
                          ".java" + File.separator + "tentackle");
      checkDir(cacheDir);
      initialized = true;
    }
    return cacheDir;
  }


  /**
   * Gets a subdirectory of the cache dir.<br>
   * The directories are created if it does not exist.
   *
   * @param subDirNames the names of the subdirectories
   * @return the directory
   */
  public static File getDirectory(String... subDirNames) {
    File subDir = getDirectory();
    for (String subDirName: subDirNames) {
      subDir = new File(getDirectory(), subDirName);
    }
    checkDir(subDir);
    return subDir;
  }



  private static void checkDir(File dir) {
    if (!dir.exists()) {
      if (dir.mkdirs()) {
        LOGGER.info("created directory " + dir.getAbsolutePath());
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
