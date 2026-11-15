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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.tentackle.misc.Pool;
import org.tentackle.trip.Connection;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TripRuntimeException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests that a client migrates its QUIC connection to a new source port when its path stalls.
 * <p>
 * The client talks to the server through a {@link UdpRelay}, which black-holes a flow by its client port, in
 * both directions, the way a router does that hashed the flow onto a path that stopped delivering.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class QuicMigrationTest {

  /** How long a call may take that has to wait for a migration. */
  private static final long CALL_TIMEOUT_SECONDS = 20;

  /** How long a call is given to complete on a black-holed path that is not migrated away from. */
  private static final long STALLED_CALL_SECONDS = 5;

  /** How long a new path is given to replenish the connection IDs before it is black-holed, too. */
  private static final long SETTLE_MILLIS = 1000;


  private Path keyStore;
  private Transport server;
  private CapturingTransport client;
  private UdpRelay relay;

  @BeforeClass
  public void setUp() throws Exception {
    keyStore = QuicTestSupport.createKeyStore();
  }

  @AfterClass
  public void tearDown() throws Exception {
    Files.deleteIfExists(keyStore);
  }

  @AfterMethod(alwaysRun = true)
  public void closeEndpoints() {
    if (client != null) {
      client.close();
      client = null;
    }
    if (relay != null) {
      relay.close();
      relay = null;
    }
    if (server != null) {
      server.close();
      server = null;
    }
  }


  /**
   * A path that stops delivering in the middle of a connection is left for a new source port, and the calls
   * carry on as if nothing happened &mdash; several times in a row, which needs the connection IDs of the
   * abandoned paths to be retired and new ones to be issued.
   */
  @Test(timeOut = 120000)
  public void migratesAwayFromAStalledPath() throws Exception {
    Reporter.log("migratesAwayFromAStalledPath", true);
    EchoService remote = startEndpoints("", "");
    assertEquals(remote.add(1, 2), 3);
    QuicTripConnectionPool pool = client.pool;

    int migrations = 3;
    for (int i = 1; i <= migrations; i++) {
      // KWIK's client holds a single spare connection ID of the server, and the replacement for the one used
      // by the migration arrives only after the old path's ID was retired, some 100 ms later. A path that
      // stalls before that cannot be left anymore, so give the new path a moment to settle.
      Thread.sleep(SETTLE_MILLIS);
      int stalledPort = pool.getLocalAddress().getPort();
      relay.blackhole(stalledPort);
      // blocks until the connection found a way around the black hole
      assertEquals(call(remote, i, CALL_TIMEOUT_SECONDS), i + 1);
      assertEquals(pool.getMigrationCount(), i, "the connection did not migrate");
      assertNotEquals(pool.getLocalAddress().getPort(), stalledPort, "still on the stalled port");
    }
    assertEquals(relay.getFlowCount(), migrations + 1, "every migration must use a new source port");
  }

  /**
   * {@code migrate=0} on the client keeps the connection on its stalled path.
   */
  @Test(timeOut = 60000)
  public void noMigrationIfDisabledByClient() throws Exception {
    Reporter.log("noMigrationIfDisabledByClient", true);
    EchoService remote = startEndpoints("", "&migrate=0");
    assertEquals(remote.add(1, 2), 3);
    assertStaysOnStalledPath(remote);
  }

  /**
   * {@code migrate=0} on the server makes it refuse migrations, so the client cannot leave its stalled path.
   */
  @Test(timeOut = 60000)
  public void noMigrationIfDisabledByServer() throws Exception {
    Reporter.log("noMigrationIfDisabledByServer", true);
    EchoService remote = startEndpoints("&migrate=0", "");
    assertEquals(remote.add(1, 2), 3);
    assertStaysOnStalledPath(remote);
  }

  @Test
  public void refusesNegativeMigrate() {
    Reporter.log("refusesNegativeMigrate", true);
    QuicTripTransport transport = new QuicTripTransport(URI.create("tripq://localhost:12345?migrate=-1"));
    try {
      TripRuntimeException exception = expectThrows(TripRuntimeException.class, transport::getMigrationProbeTimeouts);
      assertTrue(exception.getMessage().contains("migrate"), "unexpected message: " + exception.getMessage());
    }
    finally {
      transport.close();
    }
  }


  /**
   * Black-holes the current path and asserts that the connection neither migrates nor gets through.
   *
   * @param remote the remote service
   */
  private void assertStaysOnStalledPath(EchoService remote) throws Exception {
    QuicTripConnectionPool pool = client.pool;
    int stalledPort = pool.getLocalAddress().getPort();
    relay.blackhole(stalledPort);
    // far more probe timeouts than it takes to migrate on loopback
    expectThrows(TimeoutException.class, () -> call(remote, 1, STALLED_CALL_SECONDS));
    assertEquals(pool.getMigrationCount(), 0);
    assertEquals(pool.getLocalAddress().getPort(), stalledPort);
    assertEquals(relay.getFlowCount(), 1);
  }

  /**
   * Invokes {@link EchoService#add(int, int)} with a timeout.
   *
   * @param remote the remote service
   * @param value the value to add 1 to
   * @param timeoutSeconds the timeout in seconds
   * @return the result
   * @throws TimeoutException if the call did not return in time
   */
  private static int call(EchoService remote, int value, long timeoutSeconds) throws Exception {
    CompletableFuture<Integer> future = new CompletableFuture<>();
    Thread.ofVirtual().start(() -> {
      try {
        future.complete(remote.add(value, 1));
      }
      catch (Throwable t) {
        future.completeExceptionally(t);
      }
    });
    return future.get(timeoutSeconds, TimeUnit.SECONDS);
  }

  /**
   * Starts the server, the relay and the client.
   *
   * @param serverParameters additional server URI parameters, starting with {@code &}
   * @param clientParameters additional client URI parameters, starting with {@code &}
   * @return the remote echo service
   */
  private EchoService startEndpoints(String serverParameters, String clientParameters) throws Exception {
    int port = QuicTestSupport.freeUdpPort();
    server = new QuicTripTransport(URI.create(
      QuicTripTransport.PROTOCOL + "://localhost:" + port + "?" + QuicTestSupport.keyStoreParameters(keyStore) +
      serverParameters));
    server.accept();
    // wait for the server to start accepting connections, otherwise the client will fail to connect
    Thread.sleep(1000);
    assertTrue(server.isAccepting());
    server.getRegistry().register(new EchoServiceImpl("echo"), "echo");

    relay = new UdpRelay(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
    // this test is not about certificate checking, and the server's certificate is self-signed
    client = new CapturingTransport(URI.create(
      QuicTripTransport.PROTOCOL + "://" + relay.getAddress().getAddress().getHostAddress() + ":" +
      relay.getAddress().getPort() + "?initial=1&increment=1&idle=1&certcheck=false" + clientParameters));
    return client.lookup(EchoService.class, "echo");
  }


  /**
   * A client transport that keeps its connection pool at hand.
   */
  private static class CapturingTransport extends QuicTripTransport {

    private volatile QuicTripConnectionPool pool;

    private CapturingTransport(URI uri) {
      super(uri);
    }

    @Override
    protected Pool<Connection> createClientConnectionPool() {
      QuicTripConnectionPool quicPool = (QuicTripConnectionPool) super.createClientConnectionPool();
      pool = quicPool;
      return quicPool;
    }
  }

}
