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

import org.tentackle.common.ToolFinder;

import java.io.File;
import java.util.Optional;

/**
 * Finds java tools to execute on behalf of a maven plugin.<br>
 * Honors the toolchain, if any.
 */
public class JavaToolFinder extends ToolFinder {

  private final Toolchain toolchain;

  /**
   * Creates the toolfinder.
   *
   * @param toolchain the optional toolchain, null if none
   */
  public JavaToolFinder(Toolchain toolchain) {
    this.toolchain = toolchain;
  }

  /**
   * Creates a toolfinder without toolchain.
   */
  public JavaToolFinder() {
    this(null);
  }

  /**
   * Finds the file of a java tool.
   * <p>
   * If the finder was created with a toolchain, the toolchain is consulted first.
   * Otherwise, or if not found by the toolchain, it looks in the directory of the currently running
   * command (usually java). This is safe for all JDK tools, whether JAVA_HOME or PATH is configured correctly or not.<br>
   * If not found, the environment's JAVA_HOME is tried.<br>
   * If still not found, the environment's PATH variable is consulted.
   * <p>
   * Except for the toolchain, it will also try to append well-known suffixes to the toolName, depending on the OS.
   * Windows: ".exe", ".cmd" and ".bat".
   * Others (Unixes): ".sh".
   *
   * @param toolName the platform independent tool name
   * @return file representing the tool's executable, or null if the tool can not be found
   * @see ToolFinder#find(String)
   */
  @Override
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
        file = super.find(toolName);
      }
    }

    return file;
  }

  /**
   * Locates the directory of the currently running command.<br>
   * The command is usually something like .../bin/java, so this is .../bin.
   *
   * @return the tool directory, null if the directory could not be determined
   */
  private File locateJavaToolDir() {
    File toolDir = null;
    Optional<String> javaCommand = ProcessHandle.current().info().command();
    if (javaCommand.isPresent()) {
      toolDir = new File(javaCommand.get()).getParentFile();
    }
    return toolDir;
  }

}
