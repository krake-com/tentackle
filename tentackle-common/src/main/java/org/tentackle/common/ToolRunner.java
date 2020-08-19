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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Runner for external tools.
 */
public class ToolRunner {

  private final File tool;
  private final List<String> command;
  private List<String> output;
  private List<String> errors;
  private int errCode;

  /**
   * Creates a runner for a tool.
   * <p>
   * Throws {@link TentackleRuntimeException} if tool not found or not executable.
   *
   * @param tool the tool
   */
  public ToolRunner(File tool) {
    this.tool = tool;
    if (!tool.isFile()) {
      throw new TentackleRuntimeException(tool + " not found");
    }
    if (!tool.canExecute()) {
      throw new TentackleRuntimeException(tool + " is not executable");
    }
    command = new ArrayList<>();
    arg(tool);
  }

  /**
   * Adds arguments.
   *
   * @param args the arguments
   * @return this runner
   */
  public ToolRunner arg(Object... args) {
    for (Object arg: args) {
      command.add(arg.toString());
    }
    return this;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    boolean needSep = false;
    for (String arg: command) {
      if (needSep) {
        buf.append(' ');
      }
      else {
        needSep = true;
      }
      buf.append(arg);
    }
    return buf.toString();
  }

  /**
   * Runs the tool.
   * <p>
   * Throws {@link TentackleRuntimeException} if running the tool failed.
   *
   * @return this runner
   */
  public ToolRunner run() {
    try {
      Process process = new ProcessBuilder(command).start();
      errCode = process.waitFor();
      output = collect(process.getInputStream());
      errors = collect(process.getErrorStream());
      return this;
    }
    catch (InterruptedException | IOException e) {
      throw new TentackleRuntimeException("running tool " + tool + " failed", e);
    }
  }

  /**
   * Gets the standard output.
   *
   * @return the output lines, never null
   */
  public List<String> getOutput() {
    assertExecuted();
    return output;
  }

  /**
   * Gets the standard output.
   *
   * @return the output, never null
   */
  public String getOutputAsString() {
    return linesToString(getOutput());
  }

  /**
   * Gets the standard error output.
   * <p>
   * Throws {@link TentackleRuntimeException} if run method not invoked yet.
   *
   * @return the error lines, never null
   */
  public List<String> getErrors() {
    assertExecuted();
    return errors;
  }

  /**
   * Gets the standard error output.
   * <p>
   * Throws {@link TentackleRuntimeException} if run method not invoked yet.
   *
   * @return the errors, never null
   */
  public String getErrorsAsString() {
    return linesToString(getErrors());
  }

  /**
   * Gets the error code.
   * <p>
   * Throws {@link TentackleRuntimeException} if run method not invoked yet.
   *
   * @return the error, 0 if ok
   */
  public int getErrCode() {
    assertExecuted();
    return errCode;
  }

  /**
   * Clears arguments and results.
   */
  public void clear() {
    command.clear();
    arg(tool);
    errCode = 0;
    output = null;
    errors = null;
  }


  /**
   * Collects output lines from an input stream.
   *
   * @param inputStream the input stream
   * @return the list of lines
   * @throws IOException if reading failed
   */
  private static List<String> collect(InputStream inputStream) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
    List<String> lines = new ArrayList<>();
    String line;
    while ((line = reader.readLine()) != null) {
      lines.add(line);
    }
    return lines;
  }

  private void assertExecuted() {
    if (output == null) {
      throw new TentackleRuntimeException("run() method must be invoked first");
    }
  }

  private String linesToString(List<String> lines) {
    StringBuilder buf = new StringBuilder();
    boolean needNl = false;
    for (String line: lines) {
      if (needNl) {
        buf.append('\n');
      }
      else {
        needNl = true;
      }
      buf.append(line);
    }
    return buf.toString();
  }

}
