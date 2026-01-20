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

import org.tentackle.common.InterruptedRuntimeException;
import org.tentackle.common.Version;
import org.tentackle.dbms.trip.RemoteDbConnectionImpl;
import org.tentackle.dbms.trip.RemoteDbSession;
import org.tentackle.dbms.trip.RemoteDbSessionImpl;
import org.tentackle.dbms.trip.TripServer;
import org.tentackle.log.Logger;
import org.tentackle.misc.Identifiable;
import org.tentackle.pdo.DomainContext;
import org.tentackle.persist.app.ServerApplication;
import org.tentackle.session.SessionInfo;
import org.tentackle.trip.Transport;

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


  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    }
    catch (InterruptedException ix) {
      throw new InterruptedRuntimeException(ix);
    }
  }



  private static final String DURATION = "duration=";

  /**
   * Starts the server.
   * <p>
   * Usage: &lt;classname&gt; [duration=&lt;ms&gt;]
   * <p>
   * where:<br>
   * duration = optional time in ms the server will be running.
   * Default is 10000 ms (10 s).
   *
   * @param args command line arguments
   */
  public static void main(final String[] args) {
    long duration = 10000;   // default uptime in ms
    for (String arg: args) {
      if (arg.startsWith(DURATION)) {
        duration = Long.parseLong(arg.substring(DURATION.length()));
      }
    }
    try {
      TestServer server = new TestServer();
      server.start(args);
      server.sleep(duration);
      server.stop();
    }
    catch (RuntimeException ex) {
      LOGGER.severe("test server failed", ex);
      System.exit(1);
    }
  }

}
