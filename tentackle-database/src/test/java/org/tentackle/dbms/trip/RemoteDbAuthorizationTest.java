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

package org.tentackle.dbms.trip;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.dbms.AbstractDbTest;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.DbModification;
import org.tentackle.dbms.ModificationLog;
import org.tentackle.dbms.prefs.DbPreferences;
import org.tentackle.dbms.prefs.DbPreferencesKey;
import org.tentackle.dbms.prefs.DbPreferencesNode;
import org.tentackle.dbms.prefs.DbPreferencesOperation;
import org.tentackle.dbms.prefs.trip.DbPreferencesOperationRemoteDelegate;
import org.tentackle.session.DefaultSessionInfo;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.expectThrows;

/**
 * Tests the authorization of remote clients beyond the login.
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class RemoteDbAuthorizationTest extends AbstractDbTest {

  /**
   * The local session for the next {@link TestSession}.<br>
   * Static, since {@link RemoteDbSessionImpl#openDb()} is invoked from the super constructor.
   */
  private static volatile Db nextDb;

  /**
   * A remote session without a TRIP-server, authentication or client.
   */
  private static class TestSession extends RemoteDbSessionImpl {

    private TestSession(SessionInfo sessionInfo) {
      super(RemoteSessionTestSupport.createConnection(), sessionInfo, sessionInfo);
    }

    @Override
    protected Db openDb() {
      return nextDb;
    }

    @Override
    public void verifySessionInfo(SessionInfo sessionInfo) {
      // no authentication
    }

    @Override
    protected int determineTimeout(Session session, SessionInfo clientInfo, SessionInfo serverInfo) {
      return 1000;    // no server
    }
  }


  private final List<TestSession> sessions = new ArrayList<>();
  private Session previousSession;

  public RemoteDbAuthorizationTest() {
    super(TransactionType.NONE);
  }

  @BeforeMethod
  public void saveCurrentSession() {
    previousSession = Session.getCurrentSession();
  }

  @AfterMethod(alwaysRun = true)
  public void closeSessions() {
    for (TestSession session : sessions) {
      if (session.isOpen()) {
        session.close();
      }
    }
    sessions.clear();
    Session.setCurrentSession(previousSession);
  }


  private TestSession login(String userName) {
    SessionInfo sessionInfo = new DefaultSessionInfo();
    sessionInfo.setUserName(userName);
    nextDb = getSession().clone(null);
    TestSession session = new TestSession(sessionInfo);
    sessions.add(session);
    return session;
  }


  @Test
  public void testInternalClassesDeniedUnlessPrivileged() {
    TestSession session = login(uniqueUser("alice"));
    for (Class<?> clazz : List.of(ModificationLog.class, DbModification.class,
                                  DbPreferencesNode.class, DbPreferencesKey.class)) {
      expectThrows(PersistenceException.class, () -> session.getRemoteDelegate(clazz.getName()));
    }
    // regular clients access the preferences via the operation
    assertNotNull(session.getRemoteDelegate(DbPreferencesOperation.class.getName()));

    session.setPrivilegedSession(true);
    assertNotNull(session.getRemoteDelegate(ModificationLog.class.getName()));
  }


  @Test
  public void testPreferencesOfOtherUserNotReadable() {
    String alice = uniqueUser("alice");
    DbPreferencesOperationRemoteDelegate delegate = getPreferencesDelegate(login(alice));
    assertNotNull(delegate.loadRootTree(alice, true));
    assertNotNull(delegate.loadRootTree(null, true));   // system preferences are readable
    expectThrows(PersistenceException.class, () -> delegate.loadRootTree(uniqueUser("bob"), true));
    expectThrows(PersistenceException.class, () -> delegate.loadRefreshInfo(uniqueUser("bob"), 0));
  }

  @Test
  public void testSystemPreferencesNotWritable() {
    DbPreferencesOperationRemoteDelegate delegate = getPreferencesDelegate(login(uniqueUser("alice")));
    DbPreferences systemTree = delegate.loadRootTree(null, true);
    expectThrows(PersistenceException.class, () -> delegate.flush(systemTree, false));
  }

  @Test
  public void testPrivilegedSessionAccessesAllPreferences() {
    TestSession session = login(uniqueUser("admin"));
    session.setPrivilegedSession(true);
    DbPreferencesOperationRemoteDelegate delegate = getPreferencesDelegate(session);
    delegate.flush(delegate.loadRootTree(null, true), false);
    assertNotNull(delegate.loadRootTree(uniqueUser("bob"), true));
  }

  @Test
  public void testForeignUserTreeNotWritable() {
    String alice = uniqueUser("alice");
    String bob = uniqueUser("bob");
    DbPreferencesOperationRemoteDelegate delegate = getPreferencesDelegate(login(alice));
    DbPreferences bobTree = delegate.loadRootTree(alice, true);
    bobTree.getPersistentNode().setUser(bob);     // forged user
    expectThrows(PersistenceException.class, () -> delegate.flush(bobTree, false));
  }

  @Test
  public void testForgedKeyIdNotWritable() {
    String alice = uniqueUser("alice");
    String bob = uniqueUser("bob");
    Db db = getSession();
    DbPreferencesNode aliceRoot = createRoot(db, alice);
    DbPreferencesKey bobKey = createKey(db, createRoot(db, bob), "secret");

    DbPreferencesOperationRemoteDelegate delegate = getPreferencesDelegate(login(alice));

    // flushing the own, unmodified tree is allowed
    assertFalse(delegate.flush(delegate.loadRootTree(alice, true), false));

    // bob's key with its own root id
    DbPreferences aliceTree = delegate.loadRootTree(alice, true);
    aliceTree.putPersistentKey(forgeKey(bobKey, bobKey.getRootNodeId()));
    expectThrows(PersistenceException.class, () -> delegate.flush(aliceTree, false));

    // bob's key disguised as one of alice's tree
    DbPreferences aliceTree2 = delegate.loadRootTree(alice, true);
    aliceTree2.putPersistentKey(forgeKey(bobKey, aliceRoot.getId()));
    expectThrows(PersistenceException.class, () -> delegate.flush(aliceTree2, false));

    // bob's key is unchanged
    DbPreferencesKey reloaded = new DbPreferencesKey(db).selectObject(bobKey.getId());
    assertNotNull(reloaded);
    assertEquals(reloaded.getValue(), "bob's value");
  }


  @Test
  public void testGroupingWithSessionOfOtherUserDenied() {
    String alice = uniqueUser("alice");
    TestSession alice1 = login(alice);
    TestSession alice2 = login(alice);
    TestSession bob = login(uniqueUser("bob"));

    expectThrows(PersistenceException.class,
                 () -> bob.getRemoteDelegate().groupWith(alice1.getSession().getSessionId()));
    alice2.getRemoteDelegate().groupWith(alice1.getSession().getSessionId());
    assertEquals(alice2.getSession().getExportedSessionGroupId(), alice1.getSession().getSessionId());
  }


  private static String uniqueUser(String name) {
    return name + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private static DbPreferencesOperationRemoteDelegate getPreferencesDelegate(TestSession session) {
    return session.getRemoteDelegate(DbPreferencesOperation.class.getName());
  }

  private static DbPreferencesNode createRoot(Db db, String user) {
    DbPreferencesNode root = new DbPreferencesNode(db);
    root.setUser(user);
    root.setName("/");
    root.reserveId();
    root.setRootNodeId(root.getId());
    root.saveObject();
    return root;
  }

  private static DbPreferencesKey createKey(Db db, DbPreferencesNode node, String key) {
    DbPreferencesKey prefKey = new DbPreferencesKey(db);
    prefKey.setNodeId(node.getId());
    prefKey.setRootNodeId(node.getRootNodeId());
    prefKey.setKey(key);
    prefKey.setValue("bob's value");
    prefKey.saveObject();
    return prefKey;
  }

  private static DbPreferencesKey forgeKey(DbPreferencesKey original, long rootNodeId) {
    DbPreferencesKey forged = new DbPreferencesKey();
    forged.setId(original.getId());
    forged.setSerial(original.getSerial());
    forged.setNodeId(original.getNodeId());
    forged.setRootNodeId(rootNodeId);
    forged.setKey(original.getKey());
    forged.setValue("overwritten");
    return forged;
  }

}
