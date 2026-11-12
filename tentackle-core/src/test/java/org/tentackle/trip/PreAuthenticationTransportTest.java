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
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
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
  }

  public static class PrivateServiceImpl implements PrivateService {

    @Override
    public Object echo(Object value) {
      return value;
    }
  }


  private final List<Transport> transports = new ArrayList<>();
  private Transport server;
  private URI uri;


  @AfterMethod(alwaysRun = true)
  void closeTransports() {
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
