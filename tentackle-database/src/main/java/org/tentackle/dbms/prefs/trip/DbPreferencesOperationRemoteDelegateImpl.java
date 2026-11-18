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

package org.tentackle.dbms.prefs.trip;

import org.tentackle.dbms.Db;
import org.tentackle.dbms.prefs.DbPreferences;
import org.tentackle.dbms.prefs.DbPreferencesFactory;
import org.tentackle.dbms.prefs.DbPreferencesKey;
import org.tentackle.dbms.prefs.DbPreferencesNode;
import org.tentackle.dbms.prefs.DbPreferencesOperation;
import org.tentackle.dbms.prefs.DbPreferencesOperation.RefreshInfo;
import org.tentackle.dbms.trip.AbstractDbOperationRemoteDelegateImpl;
import org.tentackle.dbms.trip.RemoteDbSessionImpl;
import org.tentackle.log.Logger;
import org.tentackle.session.PersistenceException;

import java.util.HashSet;
import java.util.Set;

/**
 * Server-side implementation of the {@link DbPreferencesOperationRemoteDelegate}.
 * <p>
 * The access rules are determined by the remote session, see
 * {@link RemoteDbSessionImpl#isPreferencesAccessAllowed(String, boolean)}. By default, the client may read
 * the system preferences and read and write the preferences of its own user, i.e. the user of the remote session.
 *
 * @author harald
 */
public class DbPreferencesOperationRemoteDelegateImpl
       extends AbstractDbOperationRemoteDelegateImpl<DbPreferencesOperation>
       implements DbPreferencesOperationRemoteDelegate {

  private static final Logger LOGGER = Logger.get(DbPreferencesOperationRemoteDelegateImpl.class);

  public DbPreferencesOperationRemoteDelegateImpl(RemoteDbSessionImpl serverSession,
                                                  Class<DbPreferencesOperation> clazz) {
    super(serverSession, clazz);
  }

  @Override
  public DbPreferences loadRootTree(String user, boolean forced) {
    assertReadAllowed(user);
    return dbOperation.loadRootTree(user, forced);
  }

  @Override
  public RefreshInfo loadRefreshInfo(String user, long serial) {
    assertReadAllowed(user);
    DbPreferences node = DbPreferencesFactory.getInstance().getRootTree(getSession(), user);
    return dbOperation.loadRefreshInfo(node, serial);
  }

  @Override
  public boolean flush(DbPreferences node, boolean sync) {
    assertWriteAllowed(node);
    return dbOperation.flush(node, sync, true);
  }


  /**
   * Returns whether the given username denotes the system preferences.
   *
   * @param user the username
   * @return true if system
   */
  protected boolean isSystemUser(String user) {
    return user == null || user.isEmpty();
  }

  /**
   * Returns whether the client may read the preferences of the given user.
   *
   * @param user the username, null or empty for the system preferences
   * @return true if allowed
   * @see RemoteDbSessionImpl#isPreferencesAccessAllowed(String, boolean)
   */
  protected boolean isReadAllowed(String user) {
    return getServerSession().isPreferencesAccessAllowed(user, false);
  }

  /**
   * Returns whether the client may write the preferences of the given user.
   *
   * @param user the username, null or empty for the system preferences
   * @return true if allowed
   * @see RemoteDbSessionImpl#isPreferencesAccessAllowed(String, boolean)
   */
  protected boolean isWriteAllowed(String user) {
    return getServerSession().isPreferencesAccessAllowed(user, true);
  }

  /**
   * Asserts that the client may read the preferences of the given user.
   *
   * @param user the username, null or empty for the system preferences
   * @throws PersistenceException if denied
   */
  protected void assertReadAllowed(String user) {
    if (!isReadAllowed(user)) {
      throw accessDenied("read", user);
    }
  }

  /**
   * Asserts that the client may flush the given node.
   * <p>
   * The node and all its subnodes and keys must belong to a user the client may write.
   * Since the client sends the persisted nodes and keys along with their IDs, it is not
   * sufficient to check the user given by the client: the persisted nodes and keys must
   * belong to that user's tree in the database as well. Otherwise, a client could modify
   * or delete the nodes and keys of other users by forging the IDs.
   *
   * @param node the node to flush
   * @throws PersistenceException if denied
   */
  protected void assertWriteAllowed(DbPreferences node) {
    if (node == null) {
      throw new PersistenceException(getSession(), "missing preferences node");
    }
    String user = node.getUser();
    if (!isWriteAllowed(user)) {
      throw accessDenied("write", user);
    }

    Db db = getSession();
    DbPreferencesNode rootNode = new DbPreferencesNode(db).selectByUserAndName(user, "/");
    long rootId = rootNode == null ? 0 : rootNode.getId();
    Set<Long> nodeIds = new HashSet<>();
    Set<Long> keyIds = new HashSet<>();
    if (rootId != 0) {
      for (DbPreferencesNode persistedNode : new DbPreferencesNode(db).selectByRootNodeId(rootId)) {
        nodeIds.add(persistedNode.getId());
      }
      for (DbPreferencesKey persistedKey : new DbPreferencesKey(db).selectByRootNodeId(rootId)) {
        keyIds.add(persistedKey.getId());
      }
    }
    assertTreeOwned(node, user, rootId, nodeIds, keyIds);
  }

  /**
   * Recursively asserts that a node, its keys and subnodes belong to the given user's tree.
   *
   * @param node the node
   * @param user the user
   * @param rootId the ID of the user's persisted root node, 0 if none
   * @param nodeIds the IDs of the user's persisted nodes
   * @param keyIds the IDs of the user's persisted keys
   */
  private void assertTreeOwned(DbPreferences node, String user, long rootId, Set<Long> nodeIds, Set<Long> keyIds) {
    DbPreferencesNode prefNode = node.getPersistentNode();
    if (prefNode == null || isSystemUser(user) != isSystemUser(prefNode.getUser()) ||
        !isSystemUser(user) && !user.equals(prefNode.getUser())) {
      throw accessDenied("write", prefNode == null ? null : prefNode.getUser());
    }
    if (!prefNode.isNew()) {
      if (prefNode.getRootNodeId() != rootId) {
        throw foreignObject(prefNode, user);
      }
      if (!nodeIds.contains(prefNode.getId()) && new DbPreferencesNode(getSession()).selectObject(prefNode.getId()) != null) {
        // exists, but not in the user's tree
        throw foreignObject(prefNode, user);
      }
      // else: removed in the meantime -> handled by flush
    }
    for (DbPreferencesKey prefKey : node.getKeys()) {
      if (!prefKey.isNew()) {
        if (prefKey.getRootNodeId() != rootId) {
          throw foreignObject(prefKey, user);
        }
        if (!keyIds.contains(prefKey.getId()) && new DbPreferencesKey(getSession()).selectObject(prefKey.getId()) != null) {
          throw foreignObject(prefKey, user);
        }
      }
    }
    for (DbPreferences child : node.getChildren()) {
      assertTreeOwned(child, user, rootId, nodeIds, keyIds);
    }
  }

  private PersistenceException accessDenied(String access, String user) {
    String what = isSystemUser(user) ? "system preferences" : "preferences of user '" + user + "'";
    LOGGER.warning("{0} access to {1} denied for {2}", access, what, getServerSession());
    return new PersistenceException(getSession(), access + " access to " + what + " denied");
  }

  private PersistenceException foreignObject(Object object, String user) {
    LOGGER.warning("{0} does not belong to the preferences of {1}, denied for {2}",
                   object, isSystemUser(user) ? "system" : user, getServerSession());
    return new PersistenceException(getSession(), "write access denied: " + object +
                                                  " does not belong to the preferences flushed");
  }

}
