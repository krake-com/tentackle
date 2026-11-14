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

import org.tentackle.trip.impl.DefaultOpCode;
import org.tentackle.trip.impl.DefaultPreAuthenticationPolicy;
import org.tentackle.trip.impl.TestClass;
import org.tentackle.trip.impl.TripError;
import org.tentackle.trip.transport.TransportClientInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * A server with a {@link PreAuthenticationPolicy} talking to clients over TCP.
 */
@SuppressWarnings("missing-explicit-ctor")
public class PreAuthenticationTransportTest {

  /**
   * The public entry point, registered by name.
   */
  public interface PublicService extends Remote {

    Object echo(Object value);

    PrivateService login(boolean authenticate);

    void failLogin();

  }

  /**
   * A remote object returned to the client, not registered by name.
   */
  public interface PrivateService extends Remote {

    Object echo(Object value);

  }

  public static class PublicServiceImpl implements PublicService {

    @Override
    public Object echo(Object value) {
      return value;
    }

    @Override
    public PrivateService login(boolean authenticate) {
      if (authenticate) {
        TransportClientInfo.get().authenticate();
      }
      return new PrivateServiceImpl();
    }

    @Override
    public void failLogin() {
      long started = System.nanoTime();
      RejectionDelay.defer(started);
      failedLoginMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
      throw new TripRuntimeException("login failed");
    }
  }

  /** How long the failed login took on the server, without the deferred delay. */
  private static volatile long failedLoginMillis;

  public static class PrivateServiceImpl implements PrivateService {

    @Override
    public Object echo(Object value) {
      return value;
    }
  }


  private final List<Transport> transports = new ArrayList<>();
  private Transport server;
  private URI uri;


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

  private void startServer(PreAuthenticationPolicy policy) throws Exception {
    int port;
    try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
      port = socket.getLocalPort();
    }
    uri = new URI("trip://127.0.0.1:" + port);
    server = TripFactory.getInstance().createTransport(uri);
    server.setPreAuthenticationPolicy(policy);
    server.accept();
    server.getRegistry().register(new PublicServiceImpl(), "public");
  }

  /**
   * Connects a new client.<br>
   * Each test scenario needs its own client transport, since the server closes the connection on
   * a violation and the client's dictionary would not match a new one at the server side.
   *
   * @return the public service
   */
  private PublicService connect() {
    Transport transport = TripFactory.getInstance().createTransport(uri);
    transports.add(transport);
    return transport.lookup(PublicService.class, "public");
  }

  /**
   * Asserts that the server closed the connection.<br>
   * Depending on unread data, the server's TCP stack resets the connection instead of closing it gracefully.
   */
  private void assertClosed(InputStream in) throws IOException {
    try {
      assertEquals(in.read(), -1);
    }
    catch (SocketException reset) {
      // closed as well
    }
  }

  private TestClass createTestObject() {
    TestClass testObject = new TestClass();
    testObject.name = "not on the allowlist";
    testObject.age = 42;
    return testObject;
  }


  @Test
  void testBeforeAuthentication() throws Exception {
    startServer(new DefaultPreAuthenticationPolicy());

    PublicService service = connect();
    assertEquals(service.echo("hello"), "hello");
    assertEquals(service.echo(4711L), 4711L);
    assertEquals(service.echo(UUID.fromString("00000000-0000-0000-0000-000000000042")),
                 UUID.fromString("00000000-0000-0000-0000-000000000042"));

    TripError error = expectThrows(TripError.class, () -> service.echo(createTestObject()));
    assertEquals(error.getOpCode(), DefaultOpCode.SERVER_DESERIALIZATION_ERROR);
    assertTrue(error.getMessage().contains("not allowed before authentication"), error.getMessage());
  }

  @Test
  void testUnnamedRemoteObjectBeforeAuthentication() throws Exception {
    startServer(new DefaultPreAuthenticationPolicy());

    PublicService service = connect();
    PrivateService session = service.login(false);
    TripError error = expectThrows(TripError.class, () -> session.echo("hello"));
    assertEquals(error.getOpCode(), DefaultOpCode.NO_SUCH_DELEGATE);
    // probing is not repeatable: the connection is closed, but other clients are served
    assertEquals(connect().echo("hello"), "hello");
  }

  @Test
  void testTooManyMessages() throws Exception {
    DefaultPreAuthenticationPolicy policy = new DefaultPreAuthenticationPolicy();
    policy.setMaxMessages(3);
    startServer(policy);

    PublicService service = connect();    // the lookup is the first message
    assertEquals(service.echo("1"), "1");
    assertEquals(service.echo("2"), "2");
    TripError error = expectThrows(TripError.class, () -> service.echo("3"));
    assertTrue(error.getMessage().contains("messages"), error.getMessage());

    // no limit once authenticated
    PublicService other = connect();
    other.login(true);
    for (int i = 0; i < 10; i++) {
      assertEquals(other.echo(i), i);
    }
  }

  @Test
  void testErrorBeforeAuthenticationClosesConnection() throws Exception {
    startServer(new DefaultPreAuthenticationPolicy());

    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), uri.getPort())) {
      socket.setSoTimeout(5000);
      OutputStream out = socket.getOutputStream();
      out.write(TripConstants.MAGIC);
      out.write(new byte[16]);    // UUID
      out.write(1);               // client dictionary empty
      TripFactory factory = TripFactory.getInstance();
      TripStream stream = factory.createStream(factory.createDictionary(UUID.randomUUID(), false),
                                               factory.createSerializer(out),
                                               factory.createDeserializer(socket.getInputStream()));
      // otherwise logged with a stacktrace, and the connection kept open for the next one
      stream.sendError(DefaultOpCode.SERVER_SERIALIZATION_ERROR, new RuntimeException("forged\nlog line"));
      assertClosed(socket.getInputStream());
    }
  }

  @Test
  void testGarbageHandshake() throws Exception {
    startServer(new DefaultPreAuthenticationPolicy());

    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), uri.getPort())) {
      socket.setSoTimeout(5000);
      socket.getOutputStream().write(new byte[] { 1, 2, 3, 4 });
      assertClosed(socket.getInputStream());
    }
    // the server is still accepting
    assertEquals(connect().echo("hello"), "hello");
  }

  @Test
  void testInvalidMagicIsDelayed() throws Exception {
    startServer(new DefaultPreAuthenticationPolicy());
    RejectionDelay.setInterval(300, 400);

    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), uri.getPort())) {
      socket.setSoTimeout(5000);
      long start = System.nanoTime();
      socket.getOutputStream().write(new byte[] { 0, 0 });
      assertClosed(socket.getInputStream());
      long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      assertTrue(elapsed >= 300, "invalid magic rejected after " + elapsed + "ms");
    }
  }

  @Test
  void testValidMagicKeepsConnectionOpen() throws Exception {
    startServer(new DefaultPreAuthenticationPolicy());
    RejectionDelay.setInterval(100, 100);

    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), uri.getPort())) {
      socket.setSoTimeout(500);
      socket.getOutputStream().write(TripConstants.MAGIC);
      // the server waits for the rest of the handshake
      expectThrows(SocketTimeoutException.class, () -> socket.getInputStream().read());
    }
  }

  @Test
  void testSingleByteClosedAtDeadline() throws Exception {
    DefaultPreAuthenticationPolicy policy = new DefaultPreAuthenticationPolicy();
    policy.setFirstMessageTimeout(300);
    startServer(policy);

    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), uri.getPort())) {
      socket.setSoTimeout(5000);
      long start = System.nanoTime();
      socket.getOutputStream().write(TripConstants.MAGIC[0]);   // the server blocks reading the magic
      assertClosed(socket.getInputStream());
      long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      assertTrue(elapsed >= 300, "closed after " + elapsed + "ms");
    }
    // the server is still accepting
    assertEquals(connect().echo("hello"), "hello");
  }

  @Test
  void testIdleUnauthenticatedConnectionClosed() throws Exception {
    DefaultPreAuthenticationPolicy policy = new DefaultPreAuthenticationPolicy();
    policy.setIdleTimeout(300);
    startServer(policy);

    PublicService service = connect();
    assertEquals(service.echo(1), 1);
    Thread.sleep(700);
    expectThrows(TripStreamClosedException.class, () -> service.echo(2));
  }

  @Test
  void testIdleAuthenticatedConnectionKeptOpen() throws Exception {
    DefaultPreAuthenticationPolicy policy = new DefaultPreAuthenticationPolicy();
    policy.setIdleTimeout(300);
    startServer(policy);

    PublicService service = connect();
    service.login(true);
    Thread.sleep(700);
    assertEquals(service.echo(1), 1);
  }

  @Test
  void testUnknownNameIsRejectedDelayed() throws Exception {
    startServer(new DefaultPreAuthenticationPolicy());
    RejectionDelay.setInterval(300, 400);

    Transport transport = TripFactory.getInstance().createTransport(uri);
    transports.add(transport);
    long start = System.nanoTime();
    expectThrows(TripStreamClosedException.class, () -> transport.lookup(PublicService.class, "nope"));
    long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    assertTrue(elapsed >= 300, "unknown name rejected after " + elapsed + "ms");

    // other clients are served
    RejectionDelay.setInterval(0, 0);
    assertEquals(connect().echo("hello"), "hello");
  }

  @Test
  void testUnknownNameAfterAuthentication() throws Exception {
    startServer(new DefaultPreAuthenticationPolicy());
    RejectionDelay.setInterval(2000, 2000);

    Transport transport = TripFactory.getInstance().createTransport(uri);
    transports.add(transport);
    PublicService service = transport.lookup(PublicService.class, "public");
    assertNotNull(service.login(true));
    long start = System.nanoTime();
    TripRuntimeException ex = expectThrows(TripRuntimeException.class, () -> transport.lookup(PublicService.class, "nope"));
    long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    assertTrue(ex.getMessage().contains("remote object not found"), ex.getMessage());
    assertTrue(elapsed < 2000, "authenticated lookup delayed by " + elapsed + "ms");
  }

  @Test
  void testFailedLoginDelayedOutsideCallThread() throws Exception {
    startServer(new DefaultPreAuthenticationPolicy());
    RejectionDelay.setInterval(300, 400);
    failedLoginMillis = -1;

    PublicService service = connect();
    long start = System.nanoTime();
    expectThrows(RuntimeException.class, service::failLogin);
    long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    assertTrue(elapsed >= 300, "failed login answered after " + elapsed + "ms");
    assertTrue(failedLoginMillis >= 0 && failedLoginMillis < 100,
               "the delay blocked the call thread for " + failedLoginMillis + "ms");
    // the connection is still usable
    assertEquals(service.echo("hello"), "hello");
  }

  @Test
  void testDeferOutsideCallWaits() {
    RejectionDelay.setInterval(200, 200);
    long start = System.nanoTime();
    RejectionDelay.defer(start);
    long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    assertTrue(elapsed >= 200, "defer outside a call returned after " + elapsed + "ms");
  }

  @Test
  void testAfterAuthentication() throws Exception {
    startServer(new DefaultPreAuthenticationPolicy());

    PublicService service = connect();
    PrivateService session = service.login(true);
    TestClass testObject = createTestObject();
    assertEquals(session.echo(testObject), testObject);
    assertEquals(service.echo(testObject), testObject);

    // another client is still restricted
    PublicService otherService = connect();
    expectThrows(TripError.class, () -> otherService.echo(createTestObject()));
  }

  @Test
  void testAuditOnly() throws Exception {
    startServer(new DefaultPreAuthenticationPolicy(PreAuthenticationPolicy.Mode.AUDIT));

    PublicService service = connect();
    TestClass testObject = createTestObject();
    assertEquals(service.echo(testObject), testObject);
    assertEquals(service.login(false).echo(testObject), testObject);
  }

  @Test
  void testWithoutPolicy() throws Exception {
    startServer(null);

    PublicService service = connect();
    TestClass testObject = createTestObject();
    assertEquals(service.echo(testObject), testObject);
    assertEquals(service.login(false).echo(testObject), testObject);
  }

}
