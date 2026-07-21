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

package org.tentackle.prefs.other;

import org.tentackle.common.Version;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.prefs.DbPreferencesKey;
import org.tentackle.dbms.prefs.DbPreferencesNode;
import org.tentackle.dbms.trip.RemoteDbConnectionImpl;
import org.tentackle.dbms.trip.RemoteDbSession;
import org.tentackle.dbms.trip.RemoteDbSessionImpl;
import org.tentackle.dbms.trip.TripServer;
import org.tentackle.log.Logger;
import org.tentackle.misc.Identifiable;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.testng.ServerProcessWrapper;
import org.tentackle.persist.app.ServerApplication;
import org.tentackle.prefs.PersistedPreferences;
import org.tentackle.prefs.RemotePreferencesTest;
import org.tentackle.session.AbstractSessionTask;
import org.tentackle.session.LoginFailedException;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.SessionInfo;
import org.tentackle.trip.Transport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serial;
import java.util.prefs.BackingStoreException;

/**
 *
 * @author harald
 */
public class AddAndRemovePrefsInServer extends ServerApplication {

  private static final Logger LOGGER = Logger.get(AddAndRemovePrefsInServer.class);

  /** Command from the client: add the node and key. */
  public static final String ADD_COMMAND = "add";

  /** Command from the client: remove the node and key. */
  public static final String REMOVE_COMMAND = "remove";

  /** Marker acknowledging that the node and key were added and committed. */
  public static final String ADDED_MARKER = "@@ADDED@@";

  /** Marker acknowledging that the node and key were removed and committed. */
  public static final String REMOVED_MARKER = "@@REMOVED@@";


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
    public void verifySessionInfo(SessionInfo sessionInfo) throws LoginFailedException {
    }
  }


  /**
   * The server.
   */
  public AddAndRemovePrefsInServer() {
    super("TestServer", Version.RELEASE, ConnectionImpl.class);
  }

  @Override
  public <U extends Identifiable> U getUser(DomainContext context, long userId) {
    return null;    // not used
  }

  @Override
  protected void configureModificationTracker() {
    super.configureModificationTracker();
    ModificationTracker.getInstance().setSleepInterval(250);
  }


  private void addPreferences() {
    ModificationTracker.getInstance().addTaskAndWait(new AbstractSessionTask() {
      @Serial
      private static final long serialVersionUID = 1L;
      @Override
      public void run() {
        try {
          PersistedPreferences prefs = PersistedPreferences.systemNodeForPackage(AddAndRemovePrefsInServer.class);
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
      @Serial
      private static final long serialVersionUID = 1L;
      @Override
      public void run() {
        try {
          PersistedPreferences prefs = PersistedPreferences.systemNodeForPackage(AddAndRemovePrefsInServer.class);
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
      @Serial
      private static final long serialVersionUID = 1L;
      @Override
      public void run() {
        try {
          // always stop because we're not running within a transaction (there is no DbPreNode/Key.deleteAll)
          for (DbPreferencesNode node : new DbPreferencesNode((Db) getSession()).selectAllObjects()) {
            node.deletePlain();     // don't trigger modification counts
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


  /**
   * Serves the client's commands until it closes our standard input.
   * <p>
   * The client drives the test step by step: it sends {@link #ADD_COMMAND} once it has logged in
   * and registered its listeners, waits for the {@link #ADDED_MARKER} acknowledgment and the change
   * to propagate, then sends {@link #REMOVE_COMMAND}, and finally closes our standard input to stop
   * the server. This replaces the previous fixed-sleep choreography, which raced the client's login
   * and event propagation against wall-clock delays.
   */
  private void serveCommands() {
    cleanupData();    // in case some garbage remained from a previous failed run
    // signal readiness now that the transport is bound and the store is clean
    System.out.println(ServerProcessWrapper.READY_MARKER);
    System.out.flush();
    try (BufferedReader commands = new BufferedReader(new InputStreamReader(System.in))) {
      String command;
      while ((command = commands.readLine()) != null) {   // null == EOF == client asked us to stop
        switch (command) {
          case ADD_COMMAND -> {
            addPreferences();
            acknowledge(ADDED_MARKER);
          }
          case REMOVE_COMMAND -> {
            removePreferences();
            acknowledge(REMOVED_MARKER);
          }
          default -> LOGGER.warning("ignoring unknown command: " + command);
        }
      }
    }
    catch (IOException ex) {
      LOGGER.severe("reading commands failed", ex);
    }
    finally {
      cleanupData();
    }
  }

  /**
   * Acknowledges a completed step to the client.
   *
   * @param marker the marker to print
   */
  private void acknowledge(String marker) {
    System.out.println(marker);
    System.out.flush();
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
      server.serveCommands();
      server.stop();
    }
    catch (RuntimeException ex) {
      ex.printStackTrace();
      System.exit(1);
    }
  }

}
