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

package org.tentackle.maven.plugin.check;

import java.util.Locale;

/**
 * A bundle key referenced in the sources via {@code <reference>.getString("<key>")}.
 *
 * @author harald
 */
public class BundleKey {

  private final String path;          // the class containing the method invocation
  private final String name;          // the reference, qualified by its import or package
  private final String key;           // the bundle key

  /**
   * Creates a bundle key.
   *
   * @param path the name of the class containing the method invocation, nested classes separated by a dot
   * @param name the reference {@code getString} is invoked on, qualified by its import or package
   * @param key the bundle key
   */
  public BundleKey(String path, String name, String key) {
    this.path = path;
    this.name = name;
    this.key = key;
  }

  /**
   * Gets the name of the class containing the method invocation.
   *
   * @return the class name, nested classes separated by a dot
   */
  public String getPath() {
    return path;
  }

  /**
   * Gets the reference {@code getString} is invoked on.<br>
   * Qualified by the matching single-type import or, if there is none, by the package of the class.
   *
   * @return the qualified reference
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

  /**
   * Gets the name of the bundle the key belongs to.
   * <p>
   * If the reference is a method invocation, such as {@code getBundle()}, or
   * a variable or constant, i.e. its last segment starts lowercase or is all uppercase, the bundle
   * is that of the class containing the invocation. Otherwise, the reference is the bundle's class.
   *
   * @return the bundle name, may still contain nested class names
   */
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
            lastName.toUpperCase(Locale.ROOT).equals(lastName)) {      // or all uppercase (final constant)
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
