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

package org.tentackle.test.prefs;

import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.prefs.BackingStoreException;
import org.tentackle.common.Timestamp;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.prefs.DbPreferencesKey;
import org.tentackle.dbms.prefs.DbPreferencesNode;
import org.tentackle.dbms.rmi.RemoteDbConnectionImpl;
import org.tentackle.dbms.rmi.RemoteDbSession;
import org.tentackle.dbms.rmi.RemoteDbSessionImpl;
import org.tentackle.dbms.rmi.RmiServer;
import org.tentackle.log.Logger;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.persist.app.ServerApplication;
import org.tentackle.prefs.PersistedPreferences;
import org.tentackle.session.AbstractSessionTask;
import org.tentackle.session.LoginFailedException;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.SessionInfo;

/**
 *
 * @author harald
 */
public class AddAndRemovePrefsInServer extends ServerApplication {

  private static final Logger LOGGER = Logger.get(AddAndRemovePrefsInServer.class);


  /**
   * The remote connection.
   */
  public static class ConnectionImpl extends RemoteDbConnectionImpl {

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
  protected void configureModificationTracker() {
    super.configureModificationTracker();
    ModificationTracker.getInstance().setSleepInterval(250);
  }


  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    }
    catch (InterruptedException ix) {
    }
  }


  private void addPreferences() {
    ModificationTracker.getInstance().addTaskAndWait(new AbstractSessionTask() {
      private static final long serialVersionUID = 1L;
      @Override
      public void run() {
        try {
          PersistedPreferences prefs = PersistedPreferences.systemNodeForPackage(AddAndRemovePrefsInServer.class).node("other");
          prefs.put(RemotePreferencesTest.KEY_4, RemotePreferencesTest.VALUE_4);
          prefs.flush();
        }
        catch (BackingStoreException | RuntimeException bx) {
          LOGGER.severe("adding prefs failed", bx);
          stop();
        }
      }
    });
  }


  private void removePreferences() {
    ModificationTracker.getInstance().addTaskAndWait(new AbstractSessionTask() {
      private static final long serialVersionUID = 1L;
      @Override
      public void run() {
        try {
          PersistedPreferences prefs = PersistedPreferences.systemNodeForPackage(AddAndRemovePrefsInServer.class).node("other");
          prefs.removeNode();
          prefs.flush();
        }
        catch (BackingStoreException | RuntimeException bx) {
          LOGGER.severe("removing prefs failed", bx);
          stop();
        }
      }
    });
  }


  private void cleanupData() {
    ModificationTracker.getInstance().addTaskAndWait(new AbstractSessionTask() {
      private static final long serialVersionUID = 1L;
      @Override
      public void run() {
        try {
          // always stop because we're not running within a transaction (there is no DbPreNode/Key.deleteAll)
          for (DbPreferencesNode node : new DbPreferencesNode((Db) getSession()).selectAllObjects()) {
            node.deletePlain();     // don't trigger modcounts
            for (DbPreferencesKey key : new DbPreferencesKey((Db) getSession()).selectByNodeId(node.getId())) {
              key.deletePlain();
            }
          }
        }
        catch (RuntimeException bx) {
          LOGGER.severe("cleanup prefs failed", bx);
          stop();
        }
      }
    });
  }


  private void runTests() {
    try {
      cleanupData();    // in case some garbage remained from failed test
      System.out.println(new Timestamp() + ": server started");
      sleep(3000);  // wait for startup and client to log in
      addPreferences();
      System.out.println(new Timestamp() + ": node and key added");
      sleep(3000);
      removePreferences();
      System.out.println(new Timestamp() + ": node and key removed");
      sleep(3000);
    }
    finally {
      cleanupData();
      System.out.println(new Timestamp() + ": preferences cleaned up");
    }
    sleep(3000);    // allow client to logout
  }


  /**
   * Starts the server.
   *
   * @param args command line arguments
   */
  public static void main(final String[] args) {
    try {
      AddAndRemovePrefsInServer server = new AddAndRemovePrefsInServer();
      server.start(args);
      server.runTests();
      server.stop();
    }
    catch (RuntimeException ex) {
      ex.printStackTrace();
      System.exit(1);
    }
  }

}
