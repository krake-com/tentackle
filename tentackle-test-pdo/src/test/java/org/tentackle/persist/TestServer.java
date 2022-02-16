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

package org.tentackle.persist;

import org.tentackle.common.InterruptedRuntimeException;
import org.tentackle.common.Version;
import org.tentackle.dbms.rmi.RemoteDbConnectionImpl;
import org.tentackle.dbms.rmi.RemoteDbSession;
import org.tentackle.dbms.rmi.RemoteDbSessionImpl;
import org.tentackle.dbms.rmi.RmiServer;
import org.tentackle.log.Logger;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.persist.app.ServerApplication;
import org.tentackle.session.SessionInfo;

import java.io.Serial;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;

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

    @Serial
    private static final long serialVersionUID = 1L;

    public ConnectionImpl(RmiServer server, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf)
           throws RemoteException {
      super(server, port, csf, ssf);
    }

    @Override
    public RemoteDbSession createSession(SessionInfo clientInfo) throws RemoteException {
      return new SessionImpl(this, clientInfo, getRmiServer().getSessionInfo().clone());
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
  public <U extends PersistentDomainObject<U>> U getUser(DomainContext context, long userId) {
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
   * Default is 10000ms (10s).
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
