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
package org.tentackle.maven;

import org.apache.maven.toolchain.Toolchain;

import java.io.File;
import java.util.Optional;
import java.util.StringTokenizer;

/**
 * Finds java tools to execute on behalf of a maven plugin.
 */
public class ToolFinder {

  private static final String[] WINDOWS_SUFFIXES = { ".exe", ".cmd", ".bat" };
  private static final String[] UNIX_SUFFIXES = { ".sh" };


  private final Toolchain toolchain;

  /**
   * Creates the toolfinder.
   *
   * @param toolchain the optional toolchain
   */
  public ToolFinder(Toolchain toolchain) {
    this.toolchain = toolchain;
  }

  /**
   * Creates a toolfinder without toolchain.
   */
  public ToolFinder() {
    this(null);
  }

  /**
   * Finds the file of a tool's excecutable.
   * <p>
   * First, it looks in the same directory where the current command (usually java) is located.
   * This is safe for all JDK tools, whether JAVA_HOME or PATH is configured correctly or not.<br>
   * If not found, the environment JAVA_HOME ist tried.<br>
   * If still not found, the environment's PATH variable is consulted.
   * <p>
   * Depending on the OS, it will also try to append well-known suffixes to the toolName.
   * Windows: ".exe", ".cmd" and ".bat".
   * Others (Unixes): ".sh".
   *
   * @param toolName the platform independent tool name
   * @return file representing the tool's executable, or null if the tool can not be found
   */
  public File find(String toolName) {
    File file = null;

    // locate via toolchain, if possible
    if (toolchain != null) {
      String fileName = toolchain.findTool(toolName);
      if (fileName != null) {
        file = new File(fileName);
      }
    }

    if (file == null) {
      // no toolchain or no such tool found: try java tool dir
      File toolDir = locateJavaToolDir();
      if (toolDir != null) {
        file = loadTool(toolDir, toolName);
      }
      if (file == null) {
        // try JAVA_HOME
        String javaHomeEnv = System.getenv("JAVA_HOME");
        if (javaHomeEnv != null) {
          toolDir = new File(javaHomeEnv, "bin");
          file = loadTool(toolDir, toolName);
        }
      }
      if (file == null) {
        // try along the PATH
        String path = System.getenv("PATH");    // same name for unixes and windozes
        if (path != null) {
          StringTokenizer stok = new StringTokenizer(path, File.pathSeparator);
          while (file == null && stok.hasMoreTokens()) {
            toolDir = new File(stok.nextToken().trim());
            file = loadTool(toolDir, toolName);
          }
        }
      }
    }

    return file;
  }

  /**
   * Locate the directory of the currently running process.
   * This is usually something like .../bin/java.
   *
   * @return the tooldir, null if could not be determined
   */
  private File locateJavaToolDir() {
    File toolDir = null;
    Optional<String> javaCommand = ProcessHandle.current().info().command();
    if (javaCommand.isPresent()) {
      toolDir = new File(javaCommand.get()).getParentFile();
    }
    return toolDir;
  }

  /**
   * Loads a tool by its name.<br>
   * Tries OS specific suffixes.
   *
   * @param toolDir the directory of the tool
   * @param toolName the tool's name
   * @return the tool's file, null if not found
   */
  private File loadTool(File toolDir, String toolName) {
    File file = new File(toolDir, toolName);
    if (!file.isFile()) {
      file = null;
      for (String suffix: File.pathSeparatorChar == ':' ? UNIX_SUFFIXES : WINDOWS_SUFFIXES) {
        file = new File(toolDir, toolName + suffix);
        if (file.isFile()) {
          break;    // found!
        }
        file = null;
      }
    }
    return file;
  }

}
