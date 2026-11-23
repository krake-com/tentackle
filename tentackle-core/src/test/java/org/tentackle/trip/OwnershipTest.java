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
import org.tentackle.trip.impl.TripError;
import org.tentackle.trip.transport.TransportClientInfo;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

/**
 * Verifies that a remote object returned to one client (unnamed, hence private) cannot be invoked
 * by another client, even when that other client is authenticated and knows the object's UUID.
 * <p>
 * Remote objects registered by name stay public and are invocable by every client.
 */
@SuppressWarnings("missing-explicit-ctor")
public class OwnershipTest {

  /**
   * The public entry point, registered by name.
   */
  public interface PublicService extends Remote {

    Object echo(Object value);

    Session login();

  }

  /**
   * A per-client remote object returned by {@link PublicService#login()}, not registered by name.
   */
  public interface Session extends Remote {

    Object echo(Object value);

    /**
     * Returns this session's own delegate UUID.
     * <p>
     * Models the premise that the UUID of a remote object is accessible to its own client.
     *
     * @return the delegate UUID
     */
    UUID id();

  }

  public static class PublicServiceImpl implements PublicService {

    @Override
    public Object echo(Object value) {
      return value;
    }

    @Override
    public Session login() {
      TransportClientInfo.get().authenticate();
      return new SessionImpl();
    }
  }

  public static class SessionImpl implements Session, RemoteRoot {

    private volatile UUID remoteId;

    @Override
    public Object echo(Object value) {
      return value;
    }

    @Override
    public UUID id() {
      return remoteId;
    }

    @Override
    public void setRegistryCoordinates(Registry registry, UUID id) {
      this.remoteId = id;
    }

    @Override
    public void unregisterFromRegistry() {
      // not needed for the test
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

  private void startServer() throws Exception {
    int port;
    try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
      port = socket.getLocalPort();
    }
    uri = new URI("trip://127.0.0.1:" + port);
    server = TripFactory.getInstance().createTransport(uri);
    server.setPreAuthenticationPolicy(new DefaultPreAuthenticationPolicy());
    server.accept();
    server.getRegistry().register(new PublicServiceImpl(), "public");
  }

  /**
   * Connects a new client with its own transport, hence a distinct client transport UUID.
   *
   * @return the client's transport
   */
  private Transport connect() {
    Transport transport = TripFactory.getInstance().createTransport(uri);
    transports.add(transport);
    return transport;
  }


  @Test
  void testForeignClientCannotInvokePrivateObject() throws Exception {
    startServer();

    // client A logs in and receives its private session
    Transport transportA = connect();
    PublicService publicA = transportA.lookup(PublicService.class, "public");
    Session sessionA = publicA.login();
    assertEquals(sessionA.echo("a"), "a");
    UUID foreignId = sessionA.id();   // the UUID is accessible to A

    // client B logs in (own transport UUID, own private session) and tries to use A's session UUID
    Transport transportB = connect();
    PublicService publicB = transportB.lookup(PublicService.class, "public");
    Session sessionB = publicB.login();
    assertEquals(sessionB.echo("b"), "b");

    Session stolen = TripFactory.getInstance().createRemoteProxy(foreignId, transportB, Session.class);
    TripError error = expectThrows(TripError.class, () -> stolen.echo("x"));
    assertEquals(error.getOpCode(), DefaultOpCode.NO_SUCH_DELEGATE);

    // no collateral damage: A still owns its session, B still owns its own, both share the named entry point
    assertEquals(sessionA.echo("a2"), "a2");
    assertEquals(sessionB.echo("b2"), "b2");
    assertEquals(publicA.echo("pa"), "pa");
    assertEquals(publicB.echo("pb"), "pb");
  }

  @Test
  void testOwnerCanInvokeFromAnotherConnectionOfSameTransport() throws Exception {
    startServer();

    Transport transport = connect();
    PublicService publicService = transport.lookup(PublicService.class, "public");
    Session session = publicService.login();
    UUID id = session.id();

    // a fresh proxy over the SAME transport (same client transport UUID) is allowed
    Session sameClient = TripFactory.getInstance().createRemoteProxy(id, transport, Session.class);
    assertEquals(sameClient.echo("ok"), "ok");
  }

}
