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
 * <p>
 * The output and error streams are decoded with the platform's default charset.
 * Example:
 * <pre>
 *   ToolRunner runner = new ToolRunner(new ToolFinder().find("git")).arg("status").run();
 *   if (runner.getErrCode() == 0) {
 *     String status = runner.getOutputAsString();
 *     ...
 *   }
 * </pre>
 *
 * @see ToolFinder
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
   * @throws TentackleRuntimeException if tool not found or not executable
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
   * Adds arguments.<br>
   * Each argument is converted to a string via {@link Object#toString()}.
   *
   * @param args the arguments, never null
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
   * Runs the tool and waits for its termination.
   *
   * @return this runner
   * @throws TentackleRuntimeException if running the tool failed or the thread was interrupted
   */
  public ToolRunner run() {
    try {
      Process process = new ProcessBuilder(command).start();
      // The streams must be drained while the tool is running. Otherwise, the tool blocks
      // writing as soon as it has filled up the OS pipe buffer and waitFor would never return.
      Collector outCollector = new Collector(process.getInputStream());
      Collector errCollector = new Collector(process.getErrorStream());
      outCollector.start();
      errCollector.start();
      errCode = process.waitFor();
      outCollector.join();
      errCollector.join();
      output = outCollector.getLines();
      errors = errCollector.getLines();
      return this;
    }
    catch (InterruptedException ix) {
      Thread.currentThread().interrupt();    // don't swallow the interrupt
      throw new TentackleRuntimeException("running tool " + tool + " failed", ix);
    }
    catch (IOException iox) {
      throw new TentackleRuntimeException("running tool " + tool + " failed", iox);
    }
  }

  /**
   * Gets the standard output.
   *
   * @return the output lines, never null
   * @throws TentackleRuntimeException if run method not invoked yet
   */
  public List<String> getOutput() {
    assertExecuted();
    return output;
  }

  /**
   * Gets the standard output as a single string.<br>
   * The lines are separated by newlines.
   *
   * @return the output, never null
   * @throws TentackleRuntimeException if run method not invoked yet
   */
  public String getOutputAsString() {
    return linesToString(getOutput());
  }

  /**
   * Gets the standard error output.
   *
   * @return the error lines, never null
   * @throws TentackleRuntimeException if run method not invoked yet
   */
  public List<String> getErrors() {
    assertExecuted();
    return errors;
  }

  /**
   * Gets the standard error output as a single string.<br>
   * The lines are separated by newlines.
   *
   * @return the errors, never null
   * @throws TentackleRuntimeException if run method not invoked yet
   */
  public String getErrorsAsString() {
    return linesToString(getErrors());
  }

  /**
   * Gets the exit code of the tool.
   *
   * @return the exit code, 0 if ok
   * @throws TentackleRuntimeException if run method not invoked yet
   */
  public int getErrCode() {
    assertExecuted();
    return errCode;
  }

  /**
   * Clears arguments and results.<br>
   * The runner can be reused afterward.
   */
  public void clear() {
    command.clear();
    arg(tool);
    errCode = 0;
    output = null;
    errors = null;
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


  /**
   * Collects the lines of one of the tool's output streams in a separate thread.
   */
  private static class Collector extends Thread {

    private final InputStream inputStream;
    private final List<String> lines;
    private IOException exception;      // the failure while reading, null if none

    /**
     * Creates a collector for a stream.
     *
     * @param inputStream the stream to read from
     */
    Collector(InputStream inputStream) {
      this.inputStream = inputStream;
      this.lines = new ArrayList<>();
      setDaemon(true);    // never block the JVM from terminating
    }

    @Override
    public void run() {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
        String line;
        while ((line = reader.readLine()) != null) {
          lines.add(line);
        }
      }
      catch (IOException iox) {
        exception = iox;
      }
    }

    /**
     * Gets the collected lines.<br>
     * Must be invoked after {@link #join()} only.
     *
     * @return the lines, never null
     * @throws IOException if reading the stream failed
     */
    List<String> getLines() throws IOException {
      if (exception != null) {
        throw exception;
      }
      return lines;
    }
  }

}
