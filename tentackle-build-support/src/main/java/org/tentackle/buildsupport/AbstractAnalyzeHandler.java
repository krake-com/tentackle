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

package org.tentackle.buildsupport;

import org.tentackle.common.StringHelper;

import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import java.io.File;
import java.util.StringTokenizer;

/**
 * Base class for {@link AnalyzeHandler}.
 *
 * @author harald
 */
public abstract class AbstractAnalyzeHandler implements AnalyzeHandler {

  /**
   * Gets the directory in the given base directory for a classname.<br>
   * Each dot-separated part of the classname becomes a subdirectory.
   * The directory is not created.
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

  /**
   * Prints a message of the specified kind at the location of the element.<br>
   * The simple name of the handler class is appended to the message.
   *
   * @param kind the kind of message
   * @param msg  the message
   * @param e    the element to use as a position hint
   */
  protected void print(Diagnostic.Kind kind, CharSequence msg, Element e) {
    getProcessor().getProcessingEnvironment().getMessager().printMessage(
        kind, msg + " [" + StringHelper.lastAfter(getClass().getName(), '.') + "]", e);
  }

}
