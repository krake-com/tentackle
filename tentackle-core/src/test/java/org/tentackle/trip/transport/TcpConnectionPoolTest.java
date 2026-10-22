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

package org.tentackle.trip.transport;

import org.testng.SkipException;
import org.testng.annotations.Test;

import org.tentackle.io.SslParameters;
import org.tentackle.io.TcpServerSocketFactory;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.fail;

/**
 * Tests for the connect timeout of the {@link TcpConnectionPool}.<br>
 * A server that does not answer at all must not block the pool &mdash; and with it the timeout thread all
 * pools share &mdash; for as long as the operating system keeps retrying.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class TcpConnectionPoolTest {

  private static final String LOOPBACK = "127.0.0.1";
  private static final String STORE_PASSWORD = "secret";

  /** The seconds a connection attempt bounded to a single second is waited for, at most. */
  private static final int PATIENCE = 15;


  @Test
  public void connectsWithTheDefaultTimeout() {
    RecordingTransport transport = new RecordingTransport(URI.create("trip://" + LOOPBACK + ":9"));
    TcpConnectionPool pool = createPool(transport);

    TripRuntimeException failure = expectThrows(TripRuntimeException.class, pool::createSocket);

    assertTrue(failure.getCause() instanceof SocketTimeoutException, "unexpected cause: " + failure.getCause());
    long maxMillis = TimeUnit.SECONDS.toMillis(TcpConnectionPool.DEFAULT_CONNECT_TIMEOUT);
    assertTrue(transport.socket.timeout > maxMillis - 1000 && transport.socket.timeout <= maxMillis,
               "connected with a timeout of " + transport.socket.timeout + " ms");
  }

  @Test
  public void connectsWithTheConfiguredTimeout() {
    RecordingTransport transport = new RecordingTransport(URI.create("trip://" + LOOPBACK + ":9?connecttimeout=3"));
    TcpConnectionPool pool = createPool(transport);

    expectThrows(TripRuntimeException.class, pool::createSocket);

    assertTrue(transport.socket.timeout > 2000 && transport.socket.timeout <= 3000,
               "connected with a timeout of " + transport.socket.timeout + " ms");
    assertEquals(((InetSocketAddress) transport.socket.endpoint).getPort(), 9);
  }

  @Test
  public void closesTheSocketThatCouldNotBeConnected() {
    RecordingTransport transport = new RecordingTransport(URI.create("trip://" + LOOPBACK + ":9"));
    TcpConnectionPool pool = createPool(transport);

    expectThrows(TripRuntimeException.class, pool::createSocket);

    assertTrue(transport.socket.isClosed(), "the socket was leaked");
  }

  @Test
  public void rejectsAConnectTimeoutThatIsNotPositive() {
    for (String timeout : List.of("0", "-1")) {
      TcpTransport transport = new TcpTransport(URI.create("trip://" + LOOPBACK + ":9?connecttimeout=" + timeout));
      expectThrows(IllegalArgumentException.class,
                   () -> createPool(transport));
    }
  }

  @Test
  public void connectsToAListeningServer() throws Exception {
    try (ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getByName(LOOPBACK))) {
      TcpTransport transport = new TcpTransport(URI.create("trip://" + LOOPBACK + ":" + serverSocket.getLocalPort()));
      TcpConnectionPool pool = createPool(transport);

      try (Socket socket = pool.createSocket()) {
        assertTrue(socket.isConnected());
        assertEquals(socket.getPort(), serverSocket.getLocalPort());
        assertTrue(socket.getTcpNoDelay(), "Nagle's algorithm is not disabled");
      }
    }
  }

  @Test
  public void keepsNagleIfRequested() throws Exception {
    try (ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getByName(LOOPBACK))) {
      TcpTransport transport = new TcpTransport(URI.create("trip://" + LOOPBACK + ":" + serverSocket.getLocalPort() + "?nodelay=0"));
      TcpConnectionPool pool = createPool(transport);

      try (Socket socket = pool.createSocket()) {
        assertFalse(socket.getTcpNoDelay(), "Nagle's algorithm is disabled");
      }
    }
  }

  /**
   * A server whose accept queue is full does not answer the connection request at all, just like a
   * server behind a firewall dropping the packets.
   */
  @Test
  public void givesUpOnAServerNotAnsweringTheConnectionRequest() throws Exception {
    List<Socket> pending = new ArrayList<>();
    try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getByName(LOOPBACK))) {
      SocketAddress address = serverSocket.getLocalSocketAddress();
      if (!fillAcceptQueue(address, pending)) {
        // other platforms refuse the connection or keep accepting: nothing to wait for
        throw new SkipException("a full accept queue does not leave connection requests unanswered on this platform");
      }
      TcpTransport transport = new TcpTransport(
        URI.create("trip://" + LOOPBACK + ":" + serverSocket.getLocalPort() + "?connecttimeout=1"));
      TcpConnectionPool pool = createPool(transport);

      assertTimesOut(pool, serverSocket);
    }
    finally {
      for (Socket socket : pending) {
        socket.close();
      }
    }
  }

  /**
   * The TLS handshake is performed by the first write to the socket, which is the TRIP preamble sent when
   * the pooled connection is created. A server accepting the TCP connection without ever answering the
   * handshake must not block that forever.
   */
  @Test
  public void givesUpOnAServerNotAnsweringTheTlsHandshake() throws Exception {
    // never accepts, but the operating system establishes the TCP connections on its behalf
    try (ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getByName(LOOPBACK))) {
      SslTransport transport = new SslTransport(
        URI.create("trips://" + LOOPBACK + ":" + serverSocket.getLocalPort() + "?connecttimeout=1"));
      TcpConnectionPool pool = createPool(transport);

      assertTimesOut(pool, serverSocket);
    }
  }

  /**
   * The socket is connected after it has been created, so it does not learn the host name from the
   * socket factory anymore. The endpoint identification must still be applied to the name in the URI,
   * by default, unless turned off with {@code hostcheck=false}.
   */
  @Test
  public void identifiesTheEndpointOfAnSslConnection() throws Exception {
    Path keyStoreFile = Files.createTempFile("tentackle-tcp-keystore", ".p12");
    Files.delete(keyStoreFile);   // keytool creates it
    Path trustStoreFile = null;
    try {
      generateKeyPair(keyStoreFile, "localhost");
      trustStoreFile = deriveTrustStore(keyStoreFile, "localhost");
      SslParameters serverParameters = new SslParameters(URI.create(
        "trips://localhost:0?ksfile=" + uriPath(keyStoreFile) + "&kspass=" + STORE_PASSWORD));
      String clientQuery = "?tsfile=" + uriPath(trustStoreFile) + "&tspass=" + STORE_PASSWORD;

      try (ServerSocket serverSocket = new TcpServerSocketFactory(serverParameters).createServerSocket(0)) {
        AtomicReference<Throwable> serverError = new AtomicReference<>();
        Thread serverThread = new Thread(() -> {
          for (int i = 0; i < 3; i++) {
            try (Socket accepted = serverSocket.accept()) {
              ((SSLSocket) accepted).startHandshake();
              try {
                accepted.getInputStream().read();   // until the client closes
              }
              catch (IOException e) {
                // the client closing with unread session tickets may reset the connection (Windows)
              }
            }
            catch (IOException e) {
              serverError.compareAndSet(null, e);   // the rejected client makes the 2nd handshake fail
            }
          }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        SslTransport transport = new SslTransport(
          URI.create("trips://localhost:" + serverSocket.getLocalPort() + clientQuery));
        TcpConnectionPool pool = createPool(transport);
        try (Socket socket = pool.createSocket()) {
          SSLSocket sslSocket = (SSLSocket) socket;
          assertEquals(sslSocket.getSession().getPeerHost(), "localhost");
          assertNotNull(sslSocket.getSession().getPeerCertificates());
          assertEquals(sslSocket.getSoTimeout(), 0, "the handshake timeout must not apply to the calls");
        }
        if (serverError.get() != null) {
          fail("server handshake failed", serverError.get());
        }

        // the certificate is issued for localhost only
        SslTransport otherTransport = new SslTransport(
          URI.create("trips://" + LOOPBACK + ":" + serverSocket.getLocalPort() + clientQuery));
        TcpConnectionPool otherPool = createPool(otherTransport);
        expectThrows(TripRuntimeException.class, otherPool::createSocket);

        // the chain is still verified, but not the host name
        SslTransport uncheckedTransport = new SslTransport(
          URI.create("trips://" + LOOPBACK + ":" + serverSocket.getLocalPort() + clientQuery + "&hostcheck=false"));
        try (Socket socket = createPool(uncheckedTransport).createSocket()) {
          assertNotNull(((SSLSocket) socket).getSession().getPeerCertificates());
        }
        serverThread.join(5000);
      }
    }
    finally {
      Files.deleteIfExists(keyStoreFile);
      if (trustStoreFile != null) {
        Files.deleteIfExists(trustStoreFile);
      }
    }
  }


  /**
   * Creates the client pool of a transport, as the transport does for its first connection.
   */
  private static TcpConnectionPool createPool(TcpTransport transport) {
    return new TcpConnectionPool(transport, TripFactory.getInstance().createDictionary(transport.getId(), false));
  }

  /**
   * Asserts that getting a connection from the pool fails due to the connect timeout of one second.
   *
   * @param pool the pool
   * @param serverSocket the server socket, closed to release the pool if it does not give up
   */
  private static void assertTimesOut(TcpConnectionPool pool, ServerSocket serverSocket) throws Exception {
    CompletableFuture<Object> result = new CompletableFuture<>();
    Thread thread = new Thread(() -> {
      try {
        result.complete(pool.get());
      }
      catch (Throwable t) {
        result.completeExceptionally(t);
      }
    }, "connect");
    thread.setDaemon(true);
    long started = System.nanoTime();
    thread.start();
    try {
      result.get(PATIENCE, TimeUnit.SECONDS);
      fail("connected to a server that does not answer");
    }
    catch (TimeoutException e) {
      serverSocket.close();
      fail("still connecting after " + PATIENCE + " seconds");
    }
    catch (ExecutionException e) {
      Throwable failure = e.getCause();
      assertTrue(failure instanceof TripRuntimeException, "unexpected failure: " + failure);
      assertTrue(failure.getCause() instanceof SocketTimeoutException, "unexpected cause: " + failure.getCause());
      long seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started);
      assertTrue(seconds >= 1, "gave up before the timeout elapsed");
    }
    assertEquals(pool.getSize(), 0);
  }

  /**
   * Opens connections that are never accepted until the server stops answering.
   *
   * @param address the server's address
   * @param pending the connections opened, to be closed by the caller
   * @return true if the server does not answer anymore, false if it still does
   */
  private static boolean fillAcceptQueue(SocketAddress address, List<Socket> pending) {
    for (int i = 0; i < 16; i++) {
      Socket socket = new Socket();
      pending.add(socket);
      try {
        socket.connect(address, 300);
      }
      catch (SocketTimeoutException e) {
        return true;
      }
      catch (IOException e) {
        return false;   // refused
      }
    }
    return false;
  }

  /**
   * Renders a filesystem path for embedding in a URI query, see {@code SslSocketFactoryTest}.
   */
  private static String uriPath(Path path) {
    return path.toString().replace('\\', '/');
  }

  /**
   * Generates a self-signed RSA key pair for the given host into the keystore.
   */
  private static void generateKeyPair(Path keyStore, String host) throws Exception {
    String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
    Process process = new ProcessBuilder(
      keytool,
      "-genkeypair",
      "-alias", host,
      "-dname", "CN=" + host,
      "-ext", "san=dns:" + host,
      "-keyalg", "RSA",
      "-keysize", "2048",
      "-validity", "1",
      "-keystore", keyStore.toString(),
      "-storetype", "PKCS12",
      "-storepass", STORE_PASSWORD,
      "-keypass", STORE_PASSWORD)
      .redirectErrorStream(true)
      .start();
    String output = new String(process.getInputStream().readAllBytes());
    assertTrue(process.waitFor() == 0, "keytool failed: " + output);
  }

  /**
   * Derives a truststore containing the named certificate from the given keystore as trusted entry.
   */
  private static Path deriveTrustStore(Path keyStoreFile, String alias) throws Exception {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (var in = Files.newInputStream(keyStoreFile)) {
      keyStore.load(in, STORE_PASSWORD.toCharArray());
    }
    KeyStore trustStore = KeyStore.getInstance("PKCS12");
    trustStore.load(null, null);
    trustStore.setCertificateEntry(alias, keyStore.getCertificate(alias));
    Path trustStoreFile = Files.createTempFile("tentackle-tcp-truststore", ".p12");
    try (OutputStream out = Files.newOutputStream(trustStoreFile)) {
      trustStore.store(out, STORE_PASSWORD.toCharArray());
    }
    return trustStoreFile;
  }


  /**
   * A transport whose sockets record how they are connected instead of connecting.
   */
  private static class RecordingTransport extends TcpTransport {

    private final RecordingSocket socket = new RecordingSocket();

    RecordingTransport(URI uri) {
      super(uri);
    }

    @Override
    protected SocketFactory createSocketFactory() {
      return new SocketFactory() {
        @Override
        public Socket createSocket() {
          return socket;
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
          throw new IOException("connected without a timeout");
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
          throw new IOException("connected without a timeout");
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
          throw new IOException("connected without a timeout");
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
               throws IOException {
          throw new IOException("connected without a timeout");
        }
      };
    }
  }


  /**
   * A socket that records the timeout it is connected with and then fails as if that timeout elapsed.
   */
  private static class RecordingSocket extends Socket {

    private volatile SocketAddress endpoint;
    private volatile int timeout = -1;

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
      this.endpoint = endpoint;
      this.timeout = timeout;
      throw new SocketTimeoutException("scripted");
    }
  }

}
