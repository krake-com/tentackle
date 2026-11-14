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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.trip.impl.DefaultPreAuthenticationPolicy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link MultiplexedConnectionGuard}.
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class MultiplexedConnectionGuardTest {

  private static final String NAME = "QUIC connection to /10.9.8.7:4711";

  private TcpTransport transport;
  private DefaultPreAuthenticationPolicy policy;

  /**
   * A stream's handler whose authentication the test decides.
   */
  private class TestHandler extends ConnectionHandler {

    private volatile boolean authenticated;

    TestHandler() {
      super(transport, NAME, new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
    }

    @Override
    public boolean isAuthenticated() {
      return authenticated;
    }
  }


  @BeforeMethod
  public void setUp() {
    transport = new TcpTransport(URI.create("trip://localhost:0"));
    policy = new DefaultPreAuthenticationPolicy();
    policy.setFirstMessageTimeout(200);
    policy.setIdleTimeout(200);
    transport.setPreAuthenticationPolicy(policy);
  }

  @AfterMethod
  public void tearDown() {
    transport.close();
  }


  @Test
  public void closedWithoutStream() throws Exception {
    CountDownLatch closed = new CountDownLatch(1);
    MultiplexedConnectionGuard guard = MultiplexedConnectionGuard.admit(transport, NAME, closed::countDown);
    assertNotNull(guard);
    assertEquals(policy.getAdmittedConnections(), 1);
    assertTrue(closed.await(5, TimeUnit.SECONDS), "a connection without a stream was not closed");
    assertEquals(policy.getAdmittedConnections(), 0, "the admission was not released");
  }

  @Test
  public void notClosedWhileStreamOpen() throws Exception {
    CountDownLatch closed = new CountDownLatch(1);
    MultiplexedConnectionGuard guard = MultiplexedConnectionGuard.admit(transport, NAME, closed::countDown);
    assertNotNull(guard);
    TestHandler handler = new TestHandler();
    guard.streamOpened(handler);
    assertFalse(closed.await(600, TimeUnit.MILLISECONDS), "closed although a stream is open");

    guard.streamClosed(handler);    // idle from now on
    assertTrue(closed.await(5, TimeUnit.SECONDS), "an idle connection was not closed");
  }

  @Test
  public void notRestrictedOnceAuthenticated() throws Exception {
    CountDownLatch closed = new CountDownLatch(1);
    MultiplexedConnectionGuard guard = MultiplexedConnectionGuard.admit(transport, NAME, closed::countDown);
    assertNotNull(guard);
    TestHandler handler = new TestHandler();
    guard.streamOpened(handler);
    handler.authenticated = true;
    guard.streamClosed(handler);
    assertTrue(guard.isAuthenticated());
    assertEquals(policy.getAdmittedConnections(), 0, "an authenticated connection still counts");
    assertFalse(closed.await(600, TimeUnit.MILLISECONDS), "an authenticated connection was closed");
  }

  @Test
  public void closedReleases() {
    MultiplexedConnectionGuard guard = MultiplexedConnectionGuard.admit(transport, NAME, () -> {});
    assertNotNull(guard);
    guard.closed();
    assertEquals(policy.getAdmittedConnections(), 0);
  }

  @Test
  public void refusedAboveRate() {
    policy.setConnectionRate(1);
    policy.setConnectionBurst(1);
    CountDownLatch closed = new CountDownLatch(2);
    assertNotNull(MultiplexedConnectionGuard.admit(transport, NAME, closed::countDown));
    assertNull(MultiplexedConnectionGuard.admit(transport, NAME, closed::countDown));
    assertEquals(closed.getCount(), 1, "the refused connection was not closed");
  }

  @Test
  public void noPolicy() {
    transport.setPreAuthenticationPolicy(null);
    MultiplexedConnectionGuard guard = MultiplexedConnectionGuard.admit(transport, NAME, () -> {});
    assertNotNull(guard);
    TestHandler handler = new TestHandler();
    guard.streamOpened(handler);
    guard.streamClosed(handler);
    guard.closed();
  }

}
