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

package org.tentackle.quic;

import org.testng.Reporter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.tentackle.trip.Connection;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TripFactory;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests that the pool releases the QUIC connection it replaces when reconnecting.
 * <p>
 * KWIK stops a connection's keep-alive actor when the connection is closed explicitly, but not when it
 * is terminated silently &mdash; which is exactly how a connection ends when the idle timeout strikes,
 * the peer goes away or the network is lost. The actor's scheduler runs on a <em>non-daemon</em> platform
 * thread, so simply dropping the old connection leaks one such thread per connection ever lost, and the
 * client JVM never terminates.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class QuicReconnectTest {

  /**
   * The QUIC max idle timeout in seconds.<br>
   * Kept short: KWIK derives the keep-alive ping interval from it (half of it), and a scheduler that has
   * been shut down still runs its last scheduled ping before its thread terminates.
   */
  private static final int QUIC_IDLE = 2;

  /** How long to wait for the threads of the closed connections to terminate. */
  private static final long THREAD_TIMEOUT_MILLIS = 30000;


  private Path keyStore;

  @BeforeClass
  public void setUp() throws Exception {
    keyStore = QuicTestSupport.createKeyStore();
  }

  @AfterClass
  public void tearDown() throws Exception {
    Files.deleteIfExists(keyStore);
  }


  @Test(timeOut = 180000)
  public void reconnectsWithoutLeakingTheLostConnection() throws Exception {
    Reporter.log("reconnectsWithoutLeakingTheLostConnection", true);
    int port = QuicTestSupport.freeUdpPort();
    String serverUri = QuicTripTransport.PROTOCOL + "://localhost:" + port + "?" +
                       QuicTestSupport.keyStoreParameters(keyStore) + "&quicidle=" + QUIC_IDLE;

    Set<String> schedulerThreads = schedulerThreads();   // whatever is already running is none of our business

    Transport server = TripFactory.getInstance().createTransport(URI.create(serverUri));
    server.accept();
    // wait for the server to start accepting connections, otherwise the client will fail to connect
    Thread.sleep(1000);
    Transport client = TripFactory.getInstance().createTransport(URI.create(
      QuicTripTransport.PROTOCOL + "://localhost:" + port + "?initial=1&increment=1&quicidle=" + QUIC_IDLE));
    try {
      Connection connection = client.getConnection();
      client.putConnection(connection);
      assertTrue(connection.isOpen());

      // take the server down: the client's QUIC connection dies with it
      server.close();
      waitUntilClosed(connection);

      // ... and comes up again, so that the pool can re-establish the QUIC connection
      server = TripFactory.getInstance().createTransport(URI.create(serverUri));
      server.accept();
      // wait for the server to start accepting connections, otherwise the client will fail to connect
      Thread.sleep(1000);

      Connection reconnected = client.getConnection();
      assertTrue(reconnected.isOpen(), "the pool did not re-establish the QUIC connection");
      client.putConnection(reconnected);
      Reporter.log("reconnected", true);
    }
    finally {
      client.close();
      server.close();
    }

    // Both QUIC connections - the lost one and the one closed with the pool - must have been closed
    // explicitly, otherwise their keep-alive schedulers keep a non-daemon thread running forever.
    Set<String> leaked = waitForSchedulerThreadsToTerminate(schedulerThreads);
    assertTrue(leaked.isEmpty(),
               "the QUIC connections left " + leaked.size() + " non-daemon thread(s) behind: " + leaked);
  }


  /**
   * Waits until the given connection is not open anymore.
   *
   * @param connection the connection
   * @throws InterruptedException if interrupted while waiting
   */
  private void waitUntilClosed(Connection connection) throws InterruptedException {
    long deadline = System.currentTimeMillis() + THREAD_TIMEOUT_MILLIS;
    while (connection.isOpen() && System.currentTimeMillis() < deadline) {
      Thread.sleep(100);
    }
    assertFalse(connection.isOpen(), "the client did not notice that the QUIC connection is gone");
  }

  /**
   * Waits for the scheduler threads started since the given baseline to terminate.
   *
   * @param baseline the scheduler threads running before the test started
   * @return the threads still running after {@link #THREAD_TIMEOUT_MILLIS}, empty if all terminated
   * @throws InterruptedException if interrupted while waiting
   */
  private Set<String> waitForSchedulerThreadsToTerminate(Set<String> baseline) throws InterruptedException {
    long deadline = System.currentTimeMillis() + THREAD_TIMEOUT_MILLIS;
    Set<String> threads;
    do {
      threads = schedulerThreads();
      threads.removeAll(baseline);
      if (threads.isEmpty()) {
        break;
      }
      Thread.sleep(250);
    }
    while (System.currentTimeMillis() < deadline);
    return threads;
  }

  /**
   * Gets the names of the currently running non-daemon scheduler threads.<br>
   * KWIK's keep-alive actor runs on a plain {@code ScheduledThreadPoolExecutor}, i.e. on a non-daemon
   * thread named by the JDK's default thread factory. Restricting the check to those keeps unrelated
   * threads of the test infrastructure out of it.
   *
   * @return the thread names
   */
  private Set<String> schedulerThreads() {
    return Thread.getAllStackTraces().keySet().stream()
                 .filter(thread -> !thread.isDaemon())
                 .map(Thread::getName)
                 .filter(name -> name.startsWith("pool-"))
                 .collect(Collectors.toSet());
  }

}
