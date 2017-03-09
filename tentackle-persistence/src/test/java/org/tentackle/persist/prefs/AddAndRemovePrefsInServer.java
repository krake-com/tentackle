/*
 * Tentackle - a framework for java desktop applications
 * http://www.tentackle.org
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

package org.tentackle.persist.prefs;

import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import org.tentackle.common.Timestamp;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.pdo.AbstractSessionTask;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.LoginFailedException;
import org.tentackle.pdo.PdoTracker;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.SessionInfo;
import org.tentackle.persist.Db;
import org.tentackle.persist.app.ServerApplication;
import org.tentackle.persist.rmi.DbServer;
import org.tentackle.persist.rmi.RemoteDbConnectionImpl;
import org.tentackle.persist.rmi.RemoteDbSession;
import org.tentackle.persist.rmi.RemoteDbSessionImpl;
import org.tentackle.prefs.PersistedPreferences;

/**
 *
 * @author harald
 */
public class AddAndRemovePrefsInServer extends ServerApplication {

  /**
   * The logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(AddAndRemovePrefsInServer.class);


  /**
   * The remote connection.
   */
  public static class ConnectionImpl extends RemoteDbConnectionImpl {

    private static final long serialVersionUID = 1L;

    public ConnectionImpl(DbServer server, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf)
           throws RemoteException {
      super(server, port, csf, ssf);
    }

    @Override
    public RemoteDbSession createSession(SessionInfo clientInfo) throws RemoteException {
      return new SessionImpl(this, clientInfo, getServer().getSessionInfo().clone());
    }
  }

  /**
   * The remote session.
   */
  public static class SessionImpl extends RemoteDbSessionImpl {

    public SessionImpl(RemoteDbConnectionImpl con, SessionInfo clientInfo, SessionInfo serverInfo)
           throws RemoteException {
      super(con, clientInfo, serverInfo);
    }

    @Override
    public void verifySessionInfo(SessionInfo sessionInfo) throws LoginFailedException {
    }
  }


  /**
   * The server.
   */
  public AddAndRemovePrefsInServer() {
    super("TestServer", ConnectionImpl.class);
  }

  @Override
  public <U extends PersistentDomainObject<U>> U getUser(DomainContext context, long userId) {
    return null;    // not used
  }

  @Override
  protected void configurePdoTracker() {
    super.configurePdoTracker();
    PdoTracker.getInstance().setSleepInterval(250);
  }


  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    }
    catch (InterruptedException ix) {
    }
  }


  private void addPreferences() {
    PdoTracker.getInstance().addTaskAndWait(new AbstractSessionTask() {
      private static final long serialVersionUID = 1L;
      @Override
      public void run() {
        try {
          PersistedPreferences prefs = PersistedPreferences.systemNodeForPackage(AddPrefsInOtherJVM.class).node("other");
          prefs.put(MultiJVMPreferencesTest.KEY_4, MultiJVMPreferencesTest.VALUE_4);
          PersistedPreferences.systemRoot().flush();
        }
        catch (Exception bx) {
          LOGGER.severe("adding prefs failed", bx);
          stop();
        }
      }
    });
  }


  private void removePreferences() {
    PdoTracker.getInstance().addTaskAndWait(new AbstractSessionTask() {
      private static final long serialVersionUID = 1L;
      @Override
      public void run() {
        try {
          PersistedPreferences prefs = PersistedPreferences.systemNodeForPackage(AddPrefsInOtherJVM.class).node("other");
          prefs.removeNode();
          PersistedPreferences.systemRoot().flush();
        }
        catch (Exception bx) {
          LOGGER.severe("removing prefs failed", bx);
          stop();
        }
      }
    });
  }


  private void cleanup() {
    PdoTracker.getInstance().addTaskAndWait(new AbstractSessionTask() {
      private static final long serialVersionUID = 1L;
      @Override
      public void run() {
        try {
          // always cleanup because we're not running within a transaction (there is no DbPreNode/Key.deleteAll)
          for (DbPreferencesNode node : new DbPreferencesNode((Db) getSession()).selectAllObjects()) {
            node.deletePlain();     // don't trigger modcounts
            for (DbPreferencesKey key : new DbPreferencesKey((Db) getSession()).selectByNodeId(node.getId())) {
              key.deletePlain();
            }
          }
        }
        catch (Exception bx) {
          LOGGER.severe("cleanup prefs failed", bx);
          stop();
        }
      }
    });
  }


  private void runTests() {
    System.out.println(new Timestamp() + ": server started");
    sleep(3000);  // wait for startup and client to log in
    addPreferences();
    System.out.println(new Timestamp() + ": node and key added");
    sleep(3000);
    removePreferences();
    System.out.println(new Timestamp() + ": node and key removed");
    sleep(3000);
    cleanup();
    System.out.println(new Timestamp() + ": preferences cleaned up");
    sleep(3000);    // allow client to logout
  }


  /**
   * Starts the server.
   *
   * @param args command line arguments
   */
  public static void main(final String[] args) {
    AddAndRemovePrefsInServer server = new AddAndRemovePrefsInServer();
    server.start(args);
    server.runTests();
    server.stop();
  }

}
