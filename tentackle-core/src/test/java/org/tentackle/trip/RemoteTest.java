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

import org.testng.annotations.Test;

import org.tentackle.common.Timestamp;
import org.tentackle.trip.impl.DemoLogin;
import org.tentackle.trip.impl.DemoLoginImpl;
import org.tentackle.trip.impl.DemoSession;
import org.tentackle.trip.impl.TestClass;
import org.tentackle.trip.impl.TestSubClass;
import org.tentackle.trip.transport.LoopbackTransport;

import java.net.URI;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;

@SuppressWarnings("missing-explicit-ctor")
public class RemoteTest {

  @Test
  void testSimpleInvocation() {
    LoopbackTransport transport;
    try {
      transport = (LoopbackTransport) TripFactory.getInstance().createTransport(new URI("loopback://localhost:0"));
      transport.putConnection(transport.getConnection());   // start up client transport
    }
    catch (Exception e) {
      throw new TripRuntimeException("cannot initialize transport", e);
    }
    // register at the server side (corresponds to RemoteDbConnectionImpl)
    transport.getConnectionPool().getServerTransport().getRegistry().register(new DemoLoginImpl("1.0.0"), "login");

    // the initial proxy at the client side (corresponds to RemoteDbConnection)
    RemoteLookup lookup = transport.getRemoteLookup();
    assertFalse(lookup instanceof Registry);
    UUID remoteId = lookup.lookup("login");
    DemoLogin login = TripFactory.getInstance().createRemoteProxy(remoteId, transport, DemoLogin.class);
    assertEquals(login.getVersion(),"1.0.0");

    DemoSession session = login.login("harr", "0815");
    assertEquals(session.getUsername(), "harr");
    TestClass testObject = new TestClass();
    testObject.name = "don't send me back";
    testObject.age = 42;
    testObject.child = new TestSubClass(testObject, 100.0, new Timestamp());
    TestClass echo = session.echo(testObject);

    assertEquals(echo, testObject);
    assertNotSame(echo, testObject);
    assertSame(echo.name, testObject.name);   // optimized using the local reference
    assertNotSame(echo.child, testObject.child);
    assertSame(((TestSubClass) echo.child).parent, echo);

    transport.close();
  }

}
