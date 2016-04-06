/*
 * Tentackle - http://www.tentackle.org
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

package org.tentackle.maven;

/**
 * Bundle key.
 *
 * @author harald
 */
public class BundleKey {

  private final String path;          // java class containing the method invocation
  private final String name;          // the classname of the bundle
  private final String key;           // the bundle key

  /**
   * Creates a bundle key.
   *
   * @param path the java class containing the method invocation
   * @param name the classname of the bundle
   * @param key the bundle key
   */
  public BundleKey(String path, String name, String key) {
    this.path = path;
    this.name = name;
    this.key = key;
  }

  /**
   * Gets the java class containing the method invocation.
   *
   * @return the path
   */
  public String getPath() {
    return path;
  }

  /**
   * Gets the classname of the bundle.
   *
   * @return the bundle class
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the bundle key.
   *
   * @return the key
   */
  public String getKey() {
    return key;
  }


  public String getBundleName() {
    String bundleName = name;
    if (name.endsWith("()")) {
      // method invocation such as getBundle()
      bundleName = path;    // assume the same as the classname
    }
    else {
      int ndx = name.lastIndexOf('.');
      if (ndx > 0) {
        String lastName = name.substring(ndx + 1);
        if (Character.isLowerCase(lastName.charAt(0)) ||    // first char lowercase (variable)
            lastName.toUpperCase().equals(lastName)) {      // or all uppercase (final constant)
          // variable reference such as "resources"
          bundleName = path;    // assume the same as the classname
        }
      }
    }
    return bundleName;
  }

  @Override
  public String toString() {
    return path + ": " + name + " -> '" + key + "'";
  }

}
