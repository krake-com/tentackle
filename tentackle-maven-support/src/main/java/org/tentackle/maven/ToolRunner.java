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

import org.apache.maven.plugin.MojoExecutionException;

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
   *
   * @param tool the tool
   * @throws MojoExecutionException if tool not found or not executable
   */
  public ToolRunner(File tool) throws MojoExecutionException {
    this.tool = tool;
    if (!tool.isFile()) {
      throw new MojoExecutionException(tool + " not found");
    }
    if (!tool.canExecute()) {
      throw new MojoExecutionException(tool + " is not executable");
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
   *
   * @return this runner
   * @throws MojoExecutionException if running the tool failed
   */
  public ToolRunner run() throws MojoExecutionException {
    try {
      Process process = new ProcessBuilder(command).start();
      errCode = process.waitFor();
      output = collect(process.getInputStream());
      errors = collect(process.getErrorStream());
      return this;
    }
    catch (InterruptedException | IOException e) {
      throw new MojoExecutionException("running tool " + tool + " failed", e);
    }
  }

  /**
   * Gets the standard output.
   *
   * @return the output lines, never null
   * @throws MojoExecutionException if run method not invoked
   */
  public List<String> getOutput() throws MojoExecutionException {
    assertExecuted();
    return output;
  }

  /**
   * Gets the standard output.
   *
   * @return the output, never null
   * @throws MojoExecutionException if run method not invoked
   */
  public String getOutputAsString() throws MojoExecutionException {
    return linesToString(getOutput());
  }

  /**
   * Gets the standard error output.
   *
   * @return the error lines, never null
   * @throws MojoExecutionException if run method not invoked
   */
  public List<String> getErrors() throws MojoExecutionException {
    assertExecuted();
    return errors;
  }

  /**
   * Gets the standard error output.
   *
   * @return the errors, never null
   * @throws MojoExecutionException if run method not invoked
   */
  public String getErrorsAsString() throws MojoExecutionException {
    return linesToString(getErrors());
  }

  /**
   * Gets the error code.
   *
   * @return the error, 0 if ok
   * @throws MojoExecutionException if run method not invoked
   */
  public int getErrCode() throws MojoExecutionException {
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

  private void assertExecuted() throws MojoExecutionException {
    if (output == null) {
      throw new MojoExecutionException("run() method must be invoked first");
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
