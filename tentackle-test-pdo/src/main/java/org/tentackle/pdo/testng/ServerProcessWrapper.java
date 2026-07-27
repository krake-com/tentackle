/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.pdo.testng;

import org.testng.Reporter;

import org.tentackle.common.InterruptedRuntimeException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A wrapper for a server process to orchestrate deterministic startup, hand-shaking, and shutdown orchestration
 * between a client and a server process started in separate JVMs.
 * <ul>
 *   <li><b>Startup:</b> the server prints {@link #READY_MARKER} on its standard output once it is
 *       listening. {@link #awaitReady(long)} blocks until that marker appears (or a timeout elapses),
 *       so the client never connects too early.</li>
 *   <li><b>Hand-shaking:</b> {@link #sendCommand(String)} writes a line to the server's standard
 *       input and {@link #awaitOutput(String, long)} blocks until the server prints a matching line.
 *       This lets a test drive the server step by step instead of racing timed phases against it.</li>
 *   <li><b>Shutdown:</b> {@link #stopAndWait()} closes the server's standard input. The server
 *       treats the resulting EOF as the signal to shut down, so it stays up exactly as long as the
 *       test needs it &mdash; no fixed uptime to outrun.</li>
 * </ul>
 * The server's {@code stdout} and {@code stderr} are drained continuously by daemon threads (so the
 * subprocess never blocks on a full pipe) and forwarded to the TestNG {@link Reporter} log from the
 * test thread when the process terminates.
 * <p>
 * Output markers are matched by exact line equality and are treated as one-shot: {@link #awaitOutput}
 * returns immediately if the marker was already seen, so each distinct marker should be emitted once.
 *
 * @author harald
 */
public class ServerProcessWrapper implements AutoCloseable {

  /**
   * Marker a cooperating helper server prints on its own line to standard output once it is ready
   * to accept connections.
   */
  public static final String READY_MARKER = "@@TRIP-SERVER-READY@@";

  private final Process process;                              // the wrapped subprocess
  private final BufferedWriter commandWriter;                 // writes commands to the server's stdin
  private final Queue<String> capturedOutput = new ConcurrentLinkedQueue<>();  // drained stdout/stderr lines
  private final Set<String> seenOutput = ConcurrentHashMap.newKeySet();        // distinct stdout lines seen so far
  private final Map<String, CountDownLatch> markerLatches = new ConcurrentHashMap<>();  // awaited stdout markers
  private final Thread stdoutDrain;                           // drains and scans standard output
  private final Thread stderrDrain;                           // drains standard error

  /**
   * Wraps a freshly started subprocess and begins draining its output.
   *
   * @param process the subprocess, typically obtained from {@link AbstractPdoTest#runClass}
   */
  public ServerProcessWrapper(Process process) {
    this.process = process;
    this.commandWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
    stdoutDrain = startDrain(process.getInputStream(), true, "server-stdout-drain");
    stderrDrain = startDrain(process.getErrorStream(), false, "server-stderr-drain");
  }

  /**
   * Gets the wrapped process.
   *
   * @return the subprocess
   */
  public Process getProcess() {
    return process;
  }

  /**
   * Waits until the server signals that it is ready to accept connections.
   *
   * @param timeoutMs the maximum time to wait in milliseconds
   * @throws InterruptedException if interrupted while waiting
   * @throws IllegalStateException if the server did not become ready in time (e.g. it failed to start)
   */
  public void awaitReady(long timeoutMs) throws InterruptedException {
    awaitOutput(READY_MARKER, timeoutMs);
  }

  /**
   * Waits until the server prints the given marker on a line of its own to standard output.
   *
   * @param marker the exact line to wait for
   * @param timeoutMs the maximum time to wait in milliseconds
   * @throws InterruptedException if interrupted while waiting
   * @throws IllegalStateException if the marker did not appear in time
   */
  public void awaitOutput(String marker, long timeoutMs) throws InterruptedException {
    CountDownLatch latch = markerLatches.computeIfAbsent(marker, m -> new CountDownLatch(1));
    if (seenOutput.contains(marker)) {
      return;   // already emitted before we started waiting
    }
    if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
      throw new IllegalStateException(
          "server did not emit '" + marker + "' within " + timeoutMs + " ms (alive=" + process.isAlive() + ")");
    }
  }

  /**
   * Sends a command line to the server via its standard input.
   *
   * @param command the command (without a trailing newline)
   * @throws IOException if the command could not be written (e.g. the server already terminated)
   */
  public void sendCommand(String command) throws IOException {
    commandWriter.write(command);
    commandWriter.newLine();
    commandWriter.flush();
  }

  /**
   * Signals the server to stop and waits for it to terminate.
   * <p>
   * Closing the subprocess's standard input produces an EOF the server uses as its shutdown signal.
   * The captured standard output and error are then forwarded to the {@link Reporter} log.
   * Must be called from the test thread so the reporter attaches the output to the running test.
   *
   * @return the exit value of the process
   * @throws InterruptedException if interrupted while waiting for termination
   */
  public int stopAndWait() throws InterruptedException {
    try {
      commandWriter.close();    // EOF on the server's stdin -> orderly shutdown
    }
    catch (IOException ignore) {
      // the process may already have terminated
    }
    int exitValue = process.waitFor();
    stdoutDrain.join();
    stderrDrain.join();
    flushCapturedOutput();
    return exitValue;
  }

  /**
   * Stops the server if it is still running.
   *
   * @throws InterruptedRuntimeException if interrupted while waiting for termination
   */
  @Override
  public void close()  {
    if (process.isAlive()) {
      try {
        stopAndWait();
      }
      catch (InterruptedException e) {
        throw new InterruptedRuntimeException(e);
      }
    }
  }

  /**
   * Starts a daemon thread that drains a subprocess stream line by line.
   *
   * @param stream the stream to drain
   * @param detectMarkers true to record lines for {@link #awaitOutput} (standard output only)
   * @param name the thread name
   * @return the started thread
   */
  private Thread startDrain(InputStream stream, boolean detectMarkers, String name) {
    Thread thread = new Thread(() -> {
      try (BufferedReader in = new BufferedReader(new InputStreamReader(stream))) {
        String line;
        while ((line = in.readLine()) != null) {
          if (detectMarkers) {
            // record before releasing so a waiter that just registered its latch cannot miss the line
            seenOutput.add(line);
            CountDownLatch latch = markerLatches.get(line);
            if (latch != null) {
              latch.countDown();
            }
          }
          capturedOutput.add(line);
        }
      }
      catch (IOException ex) {
        capturedOutput.add("draining " + name + " failed: " + ex);
      }
    }, name);
    thread.setDaemon(true);
    thread.start();
    return thread;
  }

  /**
   * Forwards the captured subprocess output to the reporter log.
   */
  private void flushCapturedOutput() {
    String line;
    while ((line = capturedOutput.poll()) != null) {
      Reporter.log(line + "<br>");
    }
  }
}
