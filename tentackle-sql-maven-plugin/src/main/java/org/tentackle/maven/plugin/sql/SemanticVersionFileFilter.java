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

package org.tentackle.maven.plugin.sql;

import java.io.File;
import java.util.StringTokenizer;

/**
 * Default semantic version filter.<br>
 * Interprets the name of the directory holding a hint file as a {@code major.minor.hotfix} version
 * (dots or underscores, with an optional suffix like {@code -SNAPSHOT} that is ignored).
 * A hint file applies if its version is greater than the database version.
 * Directories whose name does not start with a digit are permanent, i.e. their hints always apply.
 *
 * @author harald
 */
//@Service(VersionFileFilter.class)  no @Service anno since it's part of the plugin itself
public class SemanticVersionFileFilter implements VersionFileFilter {

  @Override
  public String getName() {
    return "semantic versioning";
  }

  @Override
  public boolean isValid(String version, File hintFile) {
    if (version == null) {
      return true;    // no filtering
    }
    // directory holds the version
    File dir = hintFile.getParentFile();
    if (dir != null) {
      String dirName = dir.getName();
      if (!Character.isDigit(dirName.charAt(0))) {    // permanent if not starting with a digit
        return true;
      }
      return isHintApplying(version, dirName);
    }
    return false;
  }

  /**
   * Checks whether hint applies.
   *
   * @param version the application's database version
   * @param hint the migration hint number, either dotted or with underscores with optional trailing -SNAPSHOT, -M1 or whatever
   * @return true if hint applies
   */
  public boolean isHintApplying(String version, String hint) {
    int ndx = hint.indexOf('-');    // cut -SNAPSHOT, -M1 and things like that
    if (ndx > 0) {
      hint = hint.substring(0, ndx);
    }
    // split version major/minor/hotfix
    StringTokenizer versionTokenizer = new StringTokenizer(version, ".");
    StringTokenizer hintTokenizer = new StringTokenizer(hint, "._");    // allows directory names M_X_Y as well as M.X.Y

    while (versionTokenizer.hasMoreTokens() && hintTokenizer.hasMoreTokens()) {
      String versionToken = versionTokenizer.nextToken();
      String hintToken = hintTokenizer.nextToken();
      try {
        int versionNumber = Integer.parseInt(versionToken);
        try {
          int hintNumber = Integer.parseInt(hintToken);
          if (versionNumber < hintNumber) {
            return true;
          }
          if (versionNumber > hintNumber) {
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
        if (versionToken.compareTo(hintToken) > 0) {
          return false;
        }
      }
    }
    return !versionTokenizer.hasMoreTokens() && hintTokenizer.hasMoreTokens();    // 3.2.0 is newer than 3.2-SNAPSHOT
  }

}
