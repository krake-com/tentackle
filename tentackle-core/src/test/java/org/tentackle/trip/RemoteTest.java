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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

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

  @Test
  void testMethodIndexCache() throws Exception {
    LoopbackTransport transport;
    try {
      transport = (LoopbackTransport) TripFactory.getInstance().createTransport(new URI("loopback://localhost:0"));
      transport.putConnection(transport.getConnection());   // start up client transport
    }
    catch (Exception e) {
      throw new TripRuntimeException("cannot initialize transport", e);
    }
    transport.getConnectionPool().getServerTransport().getRegistry().register(new DemoLoginImpl("1.0.0"), "login");

    RemoteLookup lookup = transport.getRemoteLookup();
    UUID remoteId = lookup.lookup("login");
    DemoLogin login = TripFactory.getInstance().createRemoteProxy(remoteId, transport, DemoLogin.class);
    DemoSession session = login.login("harr", "0815");

    // repeated invocations of the same method with parameters must hit the method-index
    // cache: the map must hold one entry per distinct method, not one per call
    TestClass testObject = new TestClass();
    testObject.name = "ping";
    testObject.age = 42;
    for (int i = 0; i < 3; i++) {
      assertEquals(session.echo(testObject), testObject);
    }
    assertEquals(session.getUsername(), "harr");
    assertEquals(session.getUsername(), "harr");    // should map to same slot

    InvocationHandler handler = Proxy.getInvocationHandler(session);
    Field mapField = handler.getClass().getDeclaredField("methodTypeMap");
    mapField.setAccessible(true);
    Map<?, ?> methodTypeMap = (Map<?, ?>) mapField.get(handler);
    assertEquals(methodTypeMap.size(), 2);    // echo + getUsername

    transport.close();
  }

  /**
   * Remote proxies are shared among threads, the transport's {@link RemoteLookup} for example.
   * Concurrent first invocations must not corrupt the proxy's method-index cache.
   */
  @Test
  void testConcurrentInvocationsOnSharedProxy() throws Exception {
    // the loopback transport provides a single connection only: concurrent calls need TCP
    int port;
    try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
      port = socket.getLocalPort();
    }
    URI uri = new URI("trip://127.0.0.1:" + port);
    Transport server = TripFactory.getInstance().createTransport(uri);
    server.accept();
    Transport transport = TripFactory.getInstance().createTransport(uri);
    try {
      server.getRegistry().register(new DemoLoginImpl("1.0.0"), "login");
      UUID remoteId = transport.getRemoteLookup().lookup("login");
      DemoLogin login = TripFactory.getInstance().createRemoteProxy(remoteId, transport, DemoLogin.class);
      DemoSession session = login.login("harr", "0815");

      // a HashMap is not safe for concurrent puts and gets: the cache must be a concurrent map
      InvocationHandler handler = Proxy.getInvocationHandler(session);
      Field mapField = handler.getClass().getDeclaredField("methodTypeMap");
      mapField.setAccessible(true);
      assertTrue(mapField.get(handler) instanceof ConcurrentMap, "method-index cache is not thread-safe");

      int threadCount = 8;
      int rounds = 50;
      CyclicBarrier barrier = new CyclicBarrier(threadCount);
      List<Future<?>> futures = new ArrayList<>();
      ExecutorService executor = Executors.newFixedThreadPool(threadCount);
      try {
        for (int t = 0; t < threadCount; t++) {
          int threadNo = t;
          futures.add(executor.submit(() -> {
            barrier.await();    // start together: the first invocations race on the empty cache
            for (int i = 0; i < rounds; i++) {
              assertEquals(session.getUsername(), "harr");
              TestClass testObject = new TestClass();
              testObject.name = "thread " + threadNo;
              testObject.age = i;
              assertEquals(session.echo(testObject), testObject);
            }
            return null;
          }));
        }
        for (Future<?> future : futures) {
          future.get(30, TimeUnit.SECONDS);   // rethrows any assertion error of the worker
        }
      }
      finally {
        executor.shutdownNow();
      }

      Map<?, ?> methodTypeMap = (Map<?, ?>) mapField.get(handler);
      assertEquals(methodTypeMap.size(), 2);    // echo + getUsername
    }
    finally {
      transport.close();
      server.close();
    }
  }

}
