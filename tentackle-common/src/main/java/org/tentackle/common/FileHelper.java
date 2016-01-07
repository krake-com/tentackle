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
 *
 */

package org.tentackle.common;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Helper methods for file handling.
 *
 * @author harald
 */
public class FileHelper {

  /**
   * Loads properties.
   *
   * @param filename the filename, null if return empty properties
   * @param asResource true if load from the classpath, false if from filesystem
   * @return the properties, empty if filename is null
   * @throws FileNotFoundException if no such property file
   * @throws IOException if reading the property file failed
   */
  public static Properties loadProperties(String filename, boolean asResource) throws FileNotFoundException, IOException {
    Properties dbProps = new Properties();
    if (filename != null) {
      if (filename.indexOf('.') < 0) {
        filename += ".properties";
      }
      if (!filename.startsWith("/")) {
        filename = "/" + filename;
      }
      InputStream is = null;
      try {
        if (asResource) {
          is = FileHelper.class.getResourceAsStream(filename);
          if (is == null) {
            // try other variant
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream(filename);
            if (is == null) {
              throw new FileNotFoundException("no such resource");
            }
          }
        }
        else  {
          is = new FileInputStream(filename);
        }
        dbProps.load(is);
      }
      finally {
        if (is != null) {
          try {
            is.close();
          }
          catch (IOException ex) {}
        }
      }
    }
    return dbProps;
  }


  private FileHelper() {}

}
