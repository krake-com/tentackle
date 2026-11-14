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

package org.tentackle.trip;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.common.Cryptor;
import org.tentackle.io.DecryptingInputStream;
import org.tentackle.io.EncryptingOutputStream;
import org.tentackle.trip.impl.DefaultPreAuthenticationPolicy;
import org.tentackle.trip.transport.CompressedEncryptedTransport;
import org.tentackle.trip.transport.EncryptedChannel;
import org.tentackle.trip.transport.EncryptedTransport;
import org.tentackle.trip.transport.TcpTransport;
import org.tentackle.trip.transport.TransportClientInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the encrypted channel of the PSK transports {@code tripe} and {@code tripce}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class EncryptedTransportTest {

  /** A self-contained cryptor, independent of any application-provided @Service(Cryptor.class). */
  private static final Cryptor CRYPTOR = new Cryptor("some salt", "some passphrase");

  public interface EchoService extends Remote {

    Object echo(Object value);

    void login();

  }

  public static class EchoServiceImpl implements EchoService {

    @Override
    public Object echo(Object value) {
      return value;
    }

    @Override
    public void login() {
      TransportClientInfo.get().authenticate();
    }
  }

  /** tripe with the test cryptor. */
  public static class TestEncryptedTransport extends EncryptedTransport {

    public TestEncryptedTransport(URI uri) {
      super(uri);
    }

    @Override
    protected Cryptor getCryptor() {
      return CRYPTOR;
    }
  }

  /** tripce with the test cryptor. */
  public static class TestCompressedEncryptedTransport extends CompressedEncryptedTransport {

    public TestCompressedEncryptedTransport(URI uri) {
      super(uri);
    }

    @Override
    protected Cryptor getCryptor() {
      return CRYPTOR;
    }
  }

  /** A client of the previous version: one AES/ECB packet per flush, no authentication. */
  public static class LegacyTransport extends TcpTransport {

    public LegacyTransport(URI uri) {
      super(uri);
    }

    @Override
    public TripStream createStream(Dictionary dictionary, Socket socket) {
      TripFactory factory = TripFactory.getInstance();
      try {
        return factory.createStream(dictionary,
                                    factory.createSerializer(new EncryptingOutputStream(socket.getOutputStream(), CRYPTOR)),
                                    factory.createDeserializer(new DecryptingInputStream(socket.getInputStream(), CRYPTOR)));
      }
      catch (IOException e) {
        throw new TripRuntimeException(e);
      }
    }
  }

  /**
   * A man in the middle relaying the client's connection to the server.<br>
   * Records what the client sent and optionally flips a bit of it.
   */
  private static class Relay implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final int flipOffset;
    private final ByteArrayOutputStream recorded = new ByteArrayOutputStream();
    private final List<Socket> sockets = new ArrayList<>();

    Relay(int serverPort, int flipOffset) throws IOException {
      this.flipOffset = flipOffset;
      serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
      Thread.ofVirtual().start(() -> {
        try {
          while (true) {
            Socket client = serverSocket.accept();
            Socket server = new Socket(InetAddress.getLoopbackAddress(), serverPort);
            synchronized (sockets) {
              sockets.add(client);
              sockets.add(server);
            }
            Thread.ofVirtual().start(() -> pump(client, server, true));
            Thread.ofVirtual().start(() -> pump(server, client, false));
          }
        }
        catch (IOException ignore) {
          // closed
        }
      });
    }

    int getPort() {
      return serverSocket.getLocalPort();
    }

    synchronized byte[] getRecorded() {
      return recorded.toByteArray();
    }

    private void pump(Socket from, Socket to, boolean clientToServer) {
      try {
        InputStream in = from.getInputStream();
        OutputStream out = to.getOutputStream();
        long offset = 0;
        int b;
        while ((b = in.read()) >= 0) {
          if (clientToServer) {
            synchronized (this) {
              recorded.write(b);
            }
            if (offset == flipOffset) {
              b ^= 0x01;
            }
          }
          offset++;
          out.write(b);
        }
      }
      catch (IOException ignore) {
        // closed
      }
      finally {
        try {
          to.shutdownOutput();
        }
        catch (IOException ignore) {
          // already closed
        }
      }
    }

    @Override
    public void close() throws IOException {
      serverSocket.close();
      synchronized (sockets) {
        for (Socket socket : sockets) {
          socket.close();
        }
      }
    }
  }


  private final List<Transport> transports = new ArrayList<>();
  private Transport server;
  private int port;


  @BeforeMethod
  void noRejectionDelay() {
    RejectionDelay.setInterval(0, 0);    // only the tests of the delay need one
  }

  @AfterMethod(alwaysRun = true)
  void closeTransports() {
    RejectionDelay.setInterval(RejectionDelay.DEFAULT_MIN_DELAY, RejectionDelay.DEFAULT_MAX_DELAY);
    transports.forEach(Transport::close);
    transports.clear();
    if (server != null) {
      server.close();
      server = null;
    }
  }

  private void startServer(boolean compressed, String query) throws Exception {
    try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
      port = socket.getLocalPort();
    }
    URI uri = new URI((compressed ? "tripce" : "tripe") + "://127.0.0.1:" + port + (query == null ? "" : "?" + query));
    server = compressed ? new TestCompressedEncryptedTransport(uri) : new TestEncryptedTransport(uri);
    server.setPreAuthenticationPolicy(new DefaultPreAuthenticationPolicy());
    server.accept();
    server.getRegistry().register(new EchoServiceImpl(), "echo");
  }

  private EchoService connect(boolean compressed, int port) throws Exception {
    URI uri = new URI((compressed ? "tripce" : "tripe") + "://127.0.0.1:" + port);
    Transport transport = compressed ? new TestCompressedEncryptedTransport(uri) : new TestEncryptedTransport(uri);
    transports.add(transport);
    return transport.lookup(EchoService.class, "echo");
  }

  private static void assertClosed(InputStream in) throws IOException {
    try {
      assertEquals(in.read(), -1);
    }
    catch (SocketException reset) {
      // closed as well
    }
  }

  private static byte[] largePayload() {
    byte[] payload = new byte[100_000];   // more than a packet
    for (int i = 0; i < payload.length; i++) {
      payload[i] = (byte) (i * 31 + (i >> 7));
    }
    return payload;
  }


  @Test
  void testRoundTrip() throws Exception {
    startServer(false, null);
    EchoService service = connect(false, port);
    assertEquals(service.echo("hello"), "hello");
    service.login();    // large messages are not allowed before
    byte[] payload = largePayload();
    assertEquals((byte[]) service.echo(payload), payload);
  }

  @Test
  void testCompressedRoundTrip() throws Exception {
    startServer(true, null);
    EchoService service = connect(true, port);
    assertEquals(service.echo("hello"), "hello");
    service.login();    // large messages are not allowed before
    byte[] payload = largePayload();
    assertEquals((byte[]) service.echo(payload), payload);
  }

  @Test
  void testSameMessagesDifferentCiphertext() throws Exception {
    startServer(false, null);
    byte[] first;
    byte[] second;
    try (Relay relay = new Relay(port, -1)) {
      assertEquals(connect(false, relay.getPort()).echo("hello"), "hello");
      first = relay.getRecorded();
    }
    try (Relay relay = new Relay(port, -1)) {
      assertEquals(connect(false, relay.getPort()).echo("hello"), "hello");
      second = relay.getRecorded();
    }
    assertEquals(first.length, second.length);
    // the hello is followed by the packets of the lookup and the echo
    assertTrue(first.length > EncryptedChannel.HELLO_SIZE);
    byte[] firstPackets = Arrays.copyOfRange(first, EncryptedChannel.HELLO_SIZE + EncryptedChannel.HEADER_SIZE, first.length);
    byte[] secondPackets = Arrays.copyOfRange(second, EncryptedChannel.HELLO_SIZE + EncryptedChannel.HEADER_SIZE, second.length);
    assertFalse(Arrays.equals(firstPackets, secondPackets), "identical messages encrypted identically");
  }

  @Test
  void testTamperedHelloIsRejectedSilently() throws Exception {
    startServer(false, null);
    RejectionDelay.setInterval(300, 400);
    try (Relay relay = new Relay(port, EncryptedChannel.SALT_SIZE + 3)) {   // within the encrypted request
      long start = System.nanoTime();
      expectThrows(TripStreamClosedException.class, () -> connect(false, relay.getPort()));
      long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      assertTrue(elapsed >= 300, "tampered hello rejected after " + elapsed + "ms");
    }
  }

  @Test
  void testTamperedPacketIsRejected() throws Exception {
    startServer(false, null);
    RejectionDelay.setInterval(300, 400);
    // within the first packet after the hello: the lookup
    try (Relay relay = new Relay(port, EncryptedChannel.HELLO_SIZE + EncryptedChannel.HEADER_SIZE + 3)) {
      long start = System.nanoTime();
      expectThrows(TripStreamClosedException.class, () -> connect(false, relay.getPort()));
      long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      assertTrue(elapsed >= 300, "tampered packet rejected after " + elapsed + "ms");
    }
  }

  @Test
  void testWrongKeyIsRejectedSilently() throws Exception {
    startServer(false, null);
    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
      socket.setSoTimeout(5000);
      Cryptor other = new Cryptor("other salt", "other passphrase");
      EncryptedChannel channel = new EncryptedChannel(other, socket.getInputStream(), socket.getOutputStream(), false, false);
      OutputStream out = channel.getOutputStream();
      out.write(TripConstants.MAGIC);
      out.write(new byte[16]);
      out.write(1);
      out.flush();
      // not even the server's salt
      assertClosed(socket.getInputStream());
    }
  }

  @Test
  void testGarbageIsRejectedSilently() throws Exception {
    startServer(false, null);
    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
      socket.setSoTimeout(5000);
      byte[] garbage = new byte[EncryptedChannel.HELLO_SIZE];
      Arrays.fill(garbage, (byte) 0x20);
      socket.getOutputStream().write(garbage);
      assertClosed(socket.getInputStream());
    }
  }

  @Test
  void testReplayIsRejected() throws Exception {
    startServer(false, null);
    byte[] recorded;
    try (Relay relay = new Relay(port, -1)) {
      assertEquals(connect(false, relay.getPort()).echo("hello"), "hello");
      recorded = relay.getRecorded();
    }
    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
      socket.setSoTimeout(5000);
      socket.getOutputStream().write(recorded);
      InputStream in = socket.getInputStream();
      // the hello is authentic: the server answers with a new salt ...
      assertEquals(in.readNBytes(EncryptedChannel.SALT_SIZE).length, EncryptedChannel.SALT_SIZE);
      // ... but the recorded packets were encrypted for the old one
      assertClosed(in);
    }
  }

  @Test
  void testLegacyClient() throws Exception {
    startServer(false, null);
    URI uri = new URI("trip://127.0.0.1:" + port);
    Transport legacy = new LegacyTransport(uri);
    transports.add(legacy);
    EchoService service = legacy.lookup(EchoService.class, "echo");
    assertEquals(service.echo("hello"), "hello");
    service.login();    // large messages are not allowed before
    byte[] payload = largePayload();
    assertEquals((byte[]) service.echo(payload), payload);
  }

  @Test
  void testLegacyClientRefused() throws Exception {
    startServer(false, "legacy=0");
    URI uri = new URI("trip://127.0.0.1:" + port);
    Transport legacy = new LegacyTransport(uri);
    transports.add(legacy);
    expectThrows(TripStreamClosedException.class, () -> legacy.lookup(EchoService.class, "echo"));
  }

}
