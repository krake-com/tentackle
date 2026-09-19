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

package org.tentackle.quiche.ffm;

import org.tentackle.quiche.QuicheTestSupport;

import org.testng.Reporter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Drives a complete QUIC handshake and a stream exchange between two {@link QuicheConn}s.
 * <p>
 * No sockets are involved: the datagrams are handed from one connection to the other in memory, which
 * makes this deterministic and independent of any timing. It exercises everything the FFM layer consists
 * of &mdash; {@link QuicheConfig}, {@link PemExporter}, {@link SockAddr}, {@link RecvInfo},
 * {@link SendInfo} and {@link QuicheConn} &mdash; and is therefore the test that says whether the
 * binding is right.
 */
public class HandshakeTest {

  /**
   * The ALPN protocol the two ends agree on.
   */
  private static final String ALPN = "tripj";

  /**
   * A datagram buffer, large enough for anything quiche produces at the default payload size.
   */
  private static final int BUFFER_SIZE = 2048;

  /**
   * The addresses the two ends believe they are talking between.
   */
  private static final InetSocketAddress CLIENT_ADDRESS = new InetSocketAddress("127.0.0.1", 40001);
  private static final InetSocketAddress SERVER_ADDRESS = new InetSocketAddress("127.0.0.1", 40002);


  private Path keyStoreFile;
  private Path certificateChain;
  private Path privateKey;
  private Path trustedCertificates;
  private Path otherKeyStoreFile;
  private Path otherTrustedCertificates;

  @BeforeClass
  public void setUp() throws Exception {
    keyStoreFile = QuicheTestSupport.createKeyStore();
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (var in = Files.newInputStream(keyStoreFile)) {
      keyStore.load(in, QuicheTestSupport.STORE_PASSWORD.toCharArray());
    }
    certificateChain = PemExporter.writeCertificateChain(keyStore, QuicheTestSupport.CERTIFICATE_ALIAS);
    privateKey = PemExporter.writePrivateKey(keyStore, QuicheTestSupport.CERTIFICATE_ALIAS,
                                             QuicheTestSupport.STORE_PASSWORD.toCharArray());
    // the self-signed server certificate is its own CA
    trustedCertificates = PemExporter.writeTrustedCertificates(keyStore);

    // an unrelated CA, so a client trusting only this one must reject the server's certificate
    otherKeyStoreFile = QuicheTestSupport.createKeyStore();
    KeyStore otherKeyStore = KeyStore.getInstance("PKCS12");
    try (var in = Files.newInputStream(otherKeyStoreFile)) {
      otherKeyStore.load(in, QuicheTestSupport.STORE_PASSWORD.toCharArray());
    }
    otherTrustedCertificates = PemExporter.writeTrustedCertificates(otherKeyStore);
  }

  @AfterClass
  public void tearDown() throws Exception {
    for (Path file : new Path[] {keyStoreFile, certificateChain, privateKey, trustedCertificates,
                                 otherKeyStoreFile, otherTrustedCertificates}) {
      if (file != null) {
        Files.deleteIfExists(file);
      }
    }
  }


  @Test(timeOut = 30000)
  public void testExportedPemIsWellFormed() throws Exception {
    Reporter.log("the keystore is exported to PEM files BoringSSL can read", true);
    String certificate = Files.readString(certificateChain);
    assertTrue(certificate.startsWith("-----BEGIN CERTIFICATE-----\n"), certificate);
    assertTrue(certificate.trim().endsWith("-----END CERTIFICATE-----"), certificate);

    String key = Files.readString(privateKey);
    assertTrue(key.startsWith("-----BEGIN PRIVATE KEY-----\n"), "the key must be PKCS#8");
    assertTrue(key.trim().endsWith("-----END PRIVATE KEY-----"));

    // PemExporter falls back to a plain createTempFile if the filesystem has no POSIX permissions
    // (Windows), where the temporary directory is owner-only anyway. Nothing to assert there.
    if (privateKey.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      Reporter.log("the private key file is readable by nobody but its owner", true);
      var permissions = Files.getPosixFilePermissions(privateKey);
      assertEquals(permissions.size(), 2, "unexpected permissions " + permissions);
    }
  }

  @Test(timeOut = 30000)
  public void testHandshakeAndStreamExchange() {
    Reporter.log("a client and a server complete the handshake and exchange data on a stream", true);
    try (QuicheConfig clientConfig = clientConfig();
         QuicheConfig serverConfig = serverConfig();
         Arena arena = Arena.ofShared()) {

      byte[] clientScid = connectionId();
      byte[] serverScid = connectionId();

      try (QuicheConn client = QuicheConn.connect("localhost", clientScid,
                                                  CLIENT_ADDRESS, SERVER_ADDRESS, clientConfig);
           QuicheConn server = QuicheConn.accept(serverScid, null,
                                                 SERVER_ADDRESS, CLIENT_ADDRESS, serverConfig)) {

        Wire wire = new Wire(arena, client, server);
        wire.exchange();

        assertTrue(client.isEstablished(), "the client did not complete the handshake");
        assertTrue(server.isEstablished(), "the server did not complete the handshake");
        Reporter.log("negotiated ALPN protocol: " + client.getApplicationProtocol(), true);
        assertEquals(client.getApplicationProtocol(), ALPN);
        assertEquals(server.getApplicationProtocol(), ALPN);

        Reporter.log("the client writes a request on stream 0 and the server reads it", true);
        byte[] request = "hello quiche".getBytes(StandardCharsets.UTF_8);
        MemorySegment out = arena.allocate(request.length);
        MemorySegment.copy(request, 0, out, java.lang.foreign.ValueLayout.JAVA_BYTE, 0, request.length);
        assertEquals(client.streamSend(0, out, request.length, false), request.length);
        wire.exchange();

        assertEquals(server.streamReadableNext(), 0L);
        assertEquals(read(arena, server, 0), "hello quiche");

        Reporter.log("the server answers on the same stream and closes it", true);
        byte[] response = "hello tentackle".getBytes(StandardCharsets.UTF_8);
        MemorySegment back = arena.allocate(response.length);
        MemorySegment.copy(response, 0, back, java.lang.foreign.ValueLayout.JAVA_BYTE, 0, response.length);
        assertEquals(server.streamSend(0, back, response.length, true), response.length);
        wire.exchange();

        assertEquals(client.streamReadableNext(), 0L);
        assertEquals(read(arena, client, 0), "hello tentackle");
        assertTrue(client.streamFinished(0), "the fin flag did not arrive");

        Reporter.log("closing the connection makes the peer see it as closed too", true);
        assertTrue(client.closeConnection(true, 0, "bye"));
        wire.exchange();
        assertTrue(server.isClosed() || server.isDraining(), "the server ignored the CONNECTION_CLOSE");
      }
    }
  }

  @Test(timeOut = 30000)
  public void testStreamLimitIsAdvertised() {
    Reporter.log("the peer grants exactly the number of streams the configuration asks for", true);
    try (QuicheConfig clientConfig = clientConfig();
         QuicheConfig serverConfig = serverConfig();
         Arena arena = Arena.ofShared()) {

      try (QuicheConn client = QuicheConn.connect("localhost", connectionId(),
                                                  CLIENT_ADDRESS, SERVER_ADDRESS, clientConfig);
           QuicheConn server = QuicheConn.accept(connectionId(), null,
                                                 SERVER_ADDRESS, CLIENT_ADDRESS, serverConfig)) {
        new Wire(arena, client, server).exchange();
        assertTrue(client.isEstablished());
        assertEquals(client.peerStreamsLeftBidi(), 4L);
      }
    }
  }

  @Test(timeOut = 30000)
  public void testAlpnMismatchFailsTheHandshake() {
    Reporter.log("a client offering a different ALPN protocol is refused", true);
    try (QuicheConfig clientConfig = clientConfig().setApplicationProtocols("something-else");
         QuicheConfig serverConfig = serverConfig();
         Arena arena = Arena.ofShared()) {

      try (QuicheConn client = QuicheConn.connect("localhost", connectionId(),
                                                  CLIENT_ADDRESS, SERVER_ADDRESS, clientConfig);
           QuicheConn server = QuicheConn.accept(connectionId(), null,
                                                 SERVER_ADDRESS, CLIENT_ADDRESS, serverConfig)) {
        new Wire(arena, client, server).exchangeQuietly();
        assertFalse(client.isEstablished(), "the handshake must not succeed without a common ALPN protocol");
      }
    }
  }

  /**
   * This is what {@code QuicheTripConnectionPool.certificateFailure} relies on to tell a rejected
   * certificate apart from any other handshake failure: {@code quiche_conn_local_error} reporting a
   * QUIC transport error (not an application one) whose code is a TLS {@code bad_certificate}/
   * {@code unknown_ca}/... alert, per RFC 9001 &sect;4.8.
   */
  @Test(timeOut = 30000)
  public void testCertificateRejectionReportsTheAlertAsLocalError() {
    Reporter.log("a client that does not trust the server's certificate reports a TLS alert as its local error", true);
    // deliberately not clientConfig(): it trusts the server's actual CA, which is exactly what must
    // be untrusted here - trust only an unrelated CA instead
    try (QuicheConfig clientConfig = new QuicheConfig()
                                        .setApplicationProtocols(ALPN)
                                        .loadVerifyLocations(otherTrustedCertificates)
                                        .verifyPeer(true)
                                        .setMaxIdleTimeout(30_000)
                                        .setInitialMaxData(1_000_000)
                                        .setInitialMaxStreamDataBidiLocal(100_000)
                                        .setInitialMaxStreamDataBidiRemote(100_000)
                                        .setInitialMaxStreamsBidi(4)
                                        .setInitialMaxStreamsUni(0);
         QuicheConfig serverConfig = serverConfig();
         Arena arena = Arena.ofShared()) {

      try (QuicheConn client = QuicheConn.connect("localhost", connectionId(),
                                                  CLIENT_ADDRESS, SERVER_ADDRESS, clientConfig);
           QuicheConn server = QuicheConn.accept(connectionId(), null,
                                                 SERVER_ADDRESS, CLIENT_ADDRESS, serverConfig)) {
        new Wire(arena, client, server).exchangeQuietly();
        assertFalse(client.isEstablished(), "the handshake must not succeed with an untrusted certificate");

        ConnectionError error = client.getLocalError();
        assertTrue(error != null, "expected quiche_conn_local_error to report the rejected certificate");
        assertFalse(error.isApp(), "a TLS alert is a QUIC transport error, not an application one");
        long alert = error.errorCode() - 0x100;
        assertTrue(alert == 42 || alert == 43 || alert == 44 || alert == 45 ||
                   alert == 46 || alert == 48 || alert == 49 || alert == 116,
                   "unexpected alert number " + alert + " (error code " + error.errorCode() + "), " +
                   "reason: " + error.reason());
      }
    }
  }

  @Test(timeOut = 30000)
  public void testConnectionIdsAreExchanged() {
    Reporter.log("each side learns the connection id the other chose", true);
    try (QuicheConfig clientConfig = clientConfig();
         QuicheConfig serverConfig = serverConfig();
         Arena arena = Arena.ofShared()) {

      byte[] serverScid = connectionId();
      try (QuicheConn client = QuicheConn.connect("localhost", connectionId(),
                                                  CLIENT_ADDRESS, SERVER_ADDRESS, clientConfig);
           QuicheConn server = QuicheConn.accept(serverScid, null,
                                                 SERVER_ADDRESS, CLIENT_ADDRESS, serverConfig)) {
        new Wire(arena, client, server).exchange();
        assertEquals(server.getSourceId(), serverScid);
        assertEquals(client.getDestinationId(), serverScid);
        assertTrue(server.getTraceId().length() > 0);
      }
    }
  }


  /**
   * Creates a client configuration verifying the test's self-signed certificate.
   *
   * @return the configuration
   */
  private QuicheConfig clientConfig() {
    return new QuicheConfig()
      .setApplicationProtocols(ALPN)
      .loadVerifyLocations(trustedCertificates)
      .verifyPeer(true)
      .setMaxIdleTimeout(30_000)
      .setInitialMaxData(1_000_000)
      .setInitialMaxStreamDataBidiLocal(100_000)
      .setInitialMaxStreamDataBidiRemote(100_000)
      .setInitialMaxStreamsBidi(4)
      .setInitialMaxStreamsUni(0);
  }

  /**
   * Creates a server configuration presenting the test's self-signed certificate.
   *
   * @return the configuration
   */
  private QuicheConfig serverConfig() {
    return new QuicheConfig()
      .setApplicationProtocols(ALPN)
      .loadCertChain(certificateChain)
      .loadPrivateKey(privateKey)
      .verifyPeer(false)
      .setMaxIdleTimeout(30_000)
      .setInitialMaxData(1_000_000)
      .setInitialMaxStreamDataBidiLocal(100_000)
      .setInitialMaxStreamDataBidiRemote(100_000)
      .setInitialMaxStreamsBidi(4)
      .setInitialMaxStreamsUni(0);
  }

  /**
   * Creates a random connection id.
   *
   * @return the connection id
   */
  private static byte[] connectionId() {
    byte[] id = new byte[Quiche.MAX_CONN_ID_LEN];
    new SecureRandom().nextBytes(id);
    return id;
  }

  /**
   * Reads everything a stream currently holds.
   *
   * @param arena the arena to allocate the buffer from
   * @param conn the connection
   * @param streamId the stream
   * @return the data as a string
   */
  private static String read(Arena arena, QuicheConn conn, long streamId) {
    MemorySegment buffer = arena.allocate(BUFFER_SIZE);
    StringBuilder text = new StringBuilder();
    boolean[] fin = new boolean[1];
    long read;
    while ((read = conn.streamRecv(streamId, buffer, BUFFER_SIZE, fin)) > 0) {
      byte[] bytes = buffer.asSlice(0, read).toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
      text.append(new String(bytes, StandardCharsets.UTF_8));
    }
    return text.toString();
  }


  /**
   * Moves datagrams between two connections until neither has anything left to send.
   * <p>
   * This replaces the UDP socket and the event loop of the real transport, so a failure here is a failure
   * of the binding and never of the networking.
   */
  private static final class Wire {

    private final MemorySegment buffer;
    private final SendInfo sendInfo;
    private final QuicheConn client;
    private final QuicheConn server;
    private final RecvInfo toClient;
    private final RecvInfo toServer;

    Wire(Arena arena, QuicheConn client, QuicheConn server) {
      this.client = client;
      this.server = server;
      buffer = arena.allocate(BUFFER_SIZE);
      sendInfo = new SendInfo(arena);
      toClient = new RecvInfo(arena);
      toClient.set(SERVER_ADDRESS, CLIENT_ADDRESS);
      toServer = new RecvInfo(arena);
      toServer.set(CLIENT_ADDRESS, SERVER_ADDRESS);
    }

    /**
     * Runs the exchange until it settles, failing on any protocol error.
     */
    void exchange() {
      exchange(false);
    }

    /**
     * Runs the exchange until it settles, tolerating the errors of a handshake that is meant to fail.
     */
    void exchangeQuietly() {
      exchange(true);
    }

    private void exchange(boolean tolerateFailures) {
      // an upper bound: a successful handshake settles in a handful of rounds, and a bug that keeps
      // both ends talking forever must fail the test rather than hang it
      for (int round = 0; round < 50; round++) {
        boolean moved = drain(client, server, toServer, tolerateFailures)
                      | drain(server, client, toClient, tolerateFailures);
        if (!moved) {
          return;
        }
      }
      throw new IllegalStateException("the connections never stopped exchanging packets");
    }

    /**
     * Moves everything one side wants to send to the other side.
     *
     * @param from the sending connection
     * @param to the receiving connection
     * @param info the addresses to hand to the receiver
     * @param tolerateFailures true to swallow the errors of a deliberately failing handshake
     * @return true if at least one datagram was moved
     */
    private boolean drain(QuicheConn from, QuicheConn to, RecvInfo info, boolean tolerateFailures) {
      boolean moved = false;
      List<byte[]> datagrams = new ArrayList<>();
      long length;
      while ((length = send(from, tolerateFailures)) > 0) {
        datagrams.add(buffer.asSlice(0, length).toArray(java.lang.foreign.ValueLayout.JAVA_BYTE));
      }
      for (byte[] datagram : datagrams) {
        MemorySegment.copy(datagram, 0, buffer, java.lang.foreign.ValueLayout.JAVA_BYTE, 0, datagram.length);
        try {
          to.recv(buffer, datagram.length, info);
          moved = true;
        }
        catch (QuicheException e) {
          if (!tolerateFailures) {
            throw e;
          }
        }
      }
      return moved;
    }

    /**
     * Asks a connection for its next datagram.
     *
     * @param conn the connection
     * @param tolerateFailures true to report a failure as "nothing to send"
     * @return the length, or a value {@code <= 0} if there is nothing to send
     */
    private long send(QuicheConn conn, boolean tolerateFailures) {
      try {
        return conn.send(buffer, BUFFER_SIZE, sendInfo);
      }
      catch (QuicheException e) {
        if (tolerateFailures) {
          return Quiche.ERR_DONE;
        }
        throw e;
      }
    }
  }

}
