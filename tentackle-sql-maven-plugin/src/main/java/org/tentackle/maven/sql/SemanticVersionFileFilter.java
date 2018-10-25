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

package org.tentackle.maven.sql;

import org.tentackle.common.Service;

import java.io.File;
import java.util.StringTokenizer;

/**
 * Default semantic version filter.
 * Implements major/minor/hotfix-version.
 *
 * @author harald
 */
@Service(VersionFileFilter.class)
public class SemanticVersionFileFilter implements VersionFileFilter {

  @Override
  public String getName() {
    return "semantic versioning";
  }

  @Override
  public boolean isValid(String version, File hintFile) {
    // directory holds the version
    File dir = hintFile.getParentFile();
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
          int filterNumber = Integer.parseInt(filterToken);
          try {
            int nameNumber = Integer.parseInt(nameToken);
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
