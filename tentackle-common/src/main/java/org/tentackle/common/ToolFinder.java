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

package org.tentackle.common;

import java.io.File;
import java.util.StringTokenizer;


/**
 * Finds external tools to execute.
 *
 * @see ToolRunner
 */
public class ToolFinder {

  private static final String[] WINDOWS_SUFFIXES = { ".exe", ".cmd", ".bat" };
  private static final String[] UNIX_SUFFIXES = { ".sh" };


  /**
   * Creates a tool finder.
   */
  public ToolFinder() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Finds the file of a tool's executable.
   * <p>
   * If the tool name does not point to a valid executable and is just a name
   * (i.e., contains no path separator), the environment's PATH variable is consulted.
   * For each directory in PATH, it will also try to append well-known suffixes to the toolName,
   * depending on the OS. Windows: ".exe", ".cmd" and ".bat". Others (Unixes): ".sh".
   * <p>
   * If the tool name contains a path, it is returned as is, even if it is not executable or does not exist.
   *
   * @param toolName the platform independent tool name
   * @return file representing the tool's executable, or null if the tool name has no path
   *         and the tool cannot be found along the PATH
   */
  public File find(String toolName) {
    File file = new File(toolName);
    if (!file.canExecute() && !toolName.contains("/") && (File.separatorChar == '/' || !toolName.contains(File.separator))) {
      file = null;
      // (!exists || !executable) && just a name without relative or absolute path prepended
      // try along the PATH
      String path = System.getenv("PATH");    // same name for Unix and Windows
      if (path != null) {
        StringTokenizer stok = new StringTokenizer(path, File.pathSeparator);
        while (file == null && stok.hasMoreTokens()) {
          File toolDir = new File(stok.nextToken().trim());
          file = loadTool(toolDir, toolName);
        }
      }
    }

    return file;
  }

  /**
   * Loads a tool by its name.<br>
   * Tries OS-specific suffixes.
   *
   * @param toolDir the directory of the tool
   * @param toolName the tool's name
   * @return the tool's file, null if not found
   */
  protected File loadTool(File toolDir, String toolName) {
    File file = new File(toolDir, toolName);
    if (!file.canExecute()) {
      file = null;
      for (String suffix: File.pathSeparatorChar == ':' ? UNIX_SUFFIXES : WINDOWS_SUFFIXES) {
        file = new File(toolDir, toolName + suffix);
        if (file.canExecute()) {
          break;    // found!
        }
        file = null;
      }
    }
    return file;
  }

}
