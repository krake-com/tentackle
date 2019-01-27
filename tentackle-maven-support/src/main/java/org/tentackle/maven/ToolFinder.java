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

/**
 * Finds java tools to execute on behalf of a maven plugin.
 */
public class ToolFinder {

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
   * Finds the file of a tool's excecutable.
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
      // no toolchain or no such tool found: try JAVA_HOME
      String javaHomeEnv = System.getenv("JAVA_HOME");
      if (javaHomeEnv != null) {
        File binDir = new File(javaHomeEnv, "bin");
        file = new File(binDir, toolName);
        if (!file.isFile()) {
          // try to append .exe for windozes
          file = new File(binDir, toolName + ".exe");
          if (!file.isFile()) {
            file = null;
          }
        }
      }
    }

    return file;
  }

}
