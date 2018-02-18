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

import java.io.BufferedInputStream;
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
   * Creates an input stream from the given name.
   *
   * @param name the resource name
   * @param asResource true if load from the classpath, false if from filesystem
   * @return the input stream
   * @throws IOException if reading the resources failed
   * @throws FileNotFoundException if no such resources found
   */
  public static InputStream createInputStream(String name, boolean asResource) throws IOException {
    InputStream is;
    if (asResource) {
      is = FileHelper.class.getResourceAsStream(name);
      if (is == null) {
        // try other variant
        is = Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
        if (is == null) {
          throw new FileNotFoundException("no such resource");
        }
      }
    }
    else {
      is = new FileInputStream(name);
    }
    is = new BufferedInputStream(is);
    return is;
  }


  /**
   * Loads properties from a resource.
   *
   * @param name the properties name, null if return empty properties
   * @param asResource true if load from the classpath, false if from filesystem
   * @return the properties, empty if filename is null
   * @throws IOException if reading the property file failed
   * @throws FileNotFoundException if no properties found
   */
  public static Properties loadProperties(String name, boolean asResource) throws IOException {
    Properties dbProps = new Properties();
    if (name != null) {
      if (name.indexOf('.') < 0) {
        name += ".properties";
      }
      if (!name.startsWith("/")) {
        name = "/" + name;
      }
      try (InputStream is = createInputStream(name, asResource)) {
        dbProps.load(is);
      }
    }
    return dbProps;
  }


  /**
   * Loads properties.<br>
   * Tries to load from filesystem first, then from classpath.
   *
   * @param name the properties name, null if return empty properties
   * @return the properties, empty if filename is null
   * @throws IOException if reading the property file failed
   * @throws FileNotFoundException if no properties found
   */
  public static Properties loadProperties(String name) throws IOException {
    try {
      return loadProperties(name, false);
    }
    catch (FileNotFoundException e1) {
      // try as resource
      return loadProperties(name, true);
    }
  }


  private FileHelper() {}

}
