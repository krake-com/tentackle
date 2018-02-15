/**
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

package org.tentackle.buildsupport;

import java.io.File;
import java.util.StringTokenizer;

/**
 * Base class for {@link AnalyzeHandler}.
 *
 * @author harald
 */
public abstract class AbstractAnalyzeHandler implements AnalyzeHandler {

  /**
   * Creates a directory in given basedir for a classname.
   *
   * @param baseDir the base directory
   * @param className the classname
   * @return the directory holding analyze files for the class
   */
  protected File getDirectory(File baseDir, String className) {
    File file = baseDir;
    StringTokenizer stok = new StringTokenizer(className, ".");
    while (stok.hasMoreTokens()) {
      String token = stok.nextToken();
      file = new File(file, token);
    }
    return file;
  }

}
