/*
 * Tentackle - a framework for java desktop applications
 * http://www.tentackle.org
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

package org.tentackle.maven.sql;

import java.io.File;
import java.util.StringTokenizer;
import java.util.function.Predicate;

/**
 * Filter files according to major/minor/hotfix-version.
 *
 * @author harald
 */
public class VersionFileFilter implements Predicate<File> {

  /** the minimum version. */
  private final String version;

  /**
   * Creates a filter.
   *
   * @param version the minimum version
   */
  public VersionFileFilter(String version) {
    this.version = version;
  }

  @Override
  public boolean test(File t) {
    // directory holds the version
    File dir = t.getParentFile();
    if (dir != null) {
      String name = dir.getName();
      int ndx = name.indexOf('-');    // cut -SNAPSHOT and things like that
      if (ndx > 0) {
        name = name.substring(0, ndx);
      }
      // split version major/minor/hotfix
      StringTokenizer filterTokenizer = new StringTokenizer(version, ".");
      StringTokenizer nameTokenizer = new StringTokenizer(name, ".");
      while (filterTokenizer.hasMoreTokens() && nameTokenizer.hasMoreTokens()) {
        String filterToken = filterTokenizer.nextToken();
        String nameToken = nameTokenizer.nextToken();
        try {
          int filterNumber = Integer.valueOf(filterToken);
          try {
            int nameNumber = Integer.valueOf(nameToken);
            if (filterNumber > nameNumber) {
              return false;
            }
          }
          catch (NumberFormatException nfx) {
            // not a number
            return false;
          }
        }
        catch (NumberFormatException nfx) {
          // not a number: compare as strings
          if (filterToken.compareTo(nameToken) > 0) {
            return false;
          }
        }
      }
    }
    return true;
  }

}
