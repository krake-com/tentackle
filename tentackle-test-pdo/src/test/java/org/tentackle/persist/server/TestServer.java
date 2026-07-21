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

package org.tentackle.persist.server;

import org.tentackle.common.Version;
import org.tentackle.dbms.trip.RemoteDbConnectionImpl;
import org.tentackle.dbms.trip.RemoteDbSession;
import org.tentackle.dbms.trip.RemoteDbSessionImpl;
import org.tentackle.dbms.trip.TripServer;
import org.tentackle.log.Logger;
import org.tentackle.misc.Identifiable;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.testng.ServerProcessWrapper;
import org.tentackle.persist.app.ServerApplication;
import org.tentackle.session.SessionInfo;
import org.tentackle.trip.Transport;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Server to run client transactions.
 *
 * @author harald
 */
public class TestServer extends ServerApplication {

  private static final Logger LOGGER = Logger.get(TestServer.class);

  /**
   * The remote connection.
   */
  public static class ConnectionImpl extends RemoteDbConnectionImpl {

    public ConnectionImpl(TripServer server, Transport transport) {
      super(server, transport);
    }

    @Override
    public RemoteDbSession createSession(SessionInfo clientInfo) {
      return new SessionImpl(this, clientInfo, getServer().getSessionInfo().clone());
    }
  }

  /**
   * The remote session.
   */
  public static class SessionImpl extends RemoteDbSessionImpl {

    public SessionImpl(RemoteDbConnectionImpl con, SessionInfo clientInfo, SessionInfo serverInfo) {
      super(con, clientInfo, serverInfo);
    }

    @Override
    public void verifySessionInfo(SessionInfo sessionInfo) {
    }
  }


  /**
   * The server.
   */
  public TestServer() {
    super("TestServer", Version.RELEASE, ConnectionImpl.class);
  }

  @Override
  public <U extends Identifiable> U getUser(DomainContext context, long userId) {
    return null;    // not used
  }


  /**
   * Waits until the parent closes our standard input (EOF) or the safety timeout elapses.
   * <p>
   * The parent orchestrates the shutdown by closing the subprocess's standard input
   * (see {@link ServerProcessWrapper#stopAndWait()}), so the server stays up exactly as long as the
   * test needs it. The timeout is only a safety net that prevents a leaked process should the
   * parent die without signaling.
   *
   * @param maxUptimeMillis the maximum time to keep running in milliseconds
   */
  private void awaitShutdownSignal(long maxUptimeMillis) {
    CountDownLatch stdinClosed = new CountDownLatch(1);
    Thread watcher = new Thread(() -> {
      try {
        InputStream in = System.in;
        //noinspection StatementWithEmptyBody
        while (in.read() >= 0) {
          // drain until EOF (parent closed our stdin) -> shutdown signal
        }
      }
      catch (IOException ex) {
        LOGGER.warning("reading stdin failed", ex);
      }
      finally {
        stdinClosed.countDown();
      }
    }, "shutdown-signal-watcher");
    watcher.setDaemon(true);
    watcher.start();
    try {
      stdinClosed.await(maxUptimeMillis, TimeUnit.MILLISECONDS);
    }
    catch (InterruptedException ix) {
      Thread.currentThread().interrupt();
    }
  }



  private static final String DURATION = "duration=";

  /**
   * Starts the server.
   * <p>
   * Usage: &lt;classname&gt; [duration=&lt;ms&gt;]
   * <p>
   * The server prints {@link ServerProcessWrapper#READY_MARKER} to standard output once it is listening,
   * then keeps running until the parent closes its standard input (EOF). The optional
   * {@code duration} is only a safety-net ceiling on the uptime in case the parent dies without
   * signaling; it is not the normal shutdown mechanism. Default is 60000 ms (60 s).
   *
   * @param args command line arguments
   */
  public static void main(final String[] args) {
    long maxUptime = 60000;   // safety-net ceiling on uptime in ms
    for (String arg: args) {
      if (arg.startsWith(DURATION)) {
        maxUptime = Long.parseLong(arg.substring(DURATION.length()));
      }
    }
    try {
      TestServer server = new TestServer();
      server.start(args);
      // signal readiness to the parent now that the transport is bound and accepting
      System.out.println(ServerProcessWrapper.READY_MARKER);
      System.out.flush();
      server.awaitShutdownSignal(maxUptime);
      server.stop();
    }
    catch (RuntimeException ex) {
      LOGGER.severe("test server failed", ex);
      System.exit(1);
    }
  }

}
