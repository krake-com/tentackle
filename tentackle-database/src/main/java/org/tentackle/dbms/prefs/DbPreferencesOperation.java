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

package org.tentackle.dbms.prefs;

import org.tentackle.dbms.AbstractDbOperation;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.DbOperationClassVariables;
import org.tentackle.dbms.prefs.trip.DbPreferencesOperationRemoteDelegate;
import org.tentackle.log.Logger;
import org.tentackle.prefs.PersistedPreferencesFactory;
import org.tentackle.trip.TripDeduplicatable;
import org.tentackle.trip.TripReferencable;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Remoting capable operations on persisted preferences.
 *
 * @author harald
 */
public class DbPreferencesOperation extends AbstractDbOperation<DbPreferencesOperation> {

  @Serial
  private static final long serialVersionUID = 1L;

  private static final Logger LOGGER = Logger.get(DbPreferencesOperation.class);

  private static final DbOperationClassVariables<DbPreferencesOperation> CLASSVARIABLES =
      DbOperationClassVariables.create(DbPreferencesOperation.class);


  /**
   * Holds all nodes and keys for a user- or system-root along with its version.
   *
   * @param serial    the serial version of the preferences tree.
   * @param prefNodes all prefnodes.
   * @param prefKeys  all prefkeys.
   */
    @TripReferencable(TripReferencable.NEVER)
    @TripDeduplicatable(TripDeduplicatable.NEVER)
    public record RefreshInfo(long serial,
                              @SuppressWarnings("serial") Collection<DbPreferencesNode> prefNodes,
                              @SuppressWarnings("serial") Collection<DbPreferencesKey> prefKeys)
           implements Serializable {

      @Serial
      private static final long serialVersionUID = 1L;

      /**
       * Creates a refresh-info.
       *
       * @param serial    the serial version of the node tree
       * @param prefNodes the persistent nodes
       * @param prefKeys  the persistent keys
       */
      public RefreshInfo(long serial, Collection<DbPreferencesNode> prefNodes, Collection<DbPreferencesKey> prefKeys) {
        this.serial = serial;
        this.prefNodes = prefNodes;
        this.prefKeys = prefKeys;
      }
    }


  @Override
  public DbOperationClassVariables<DbPreferencesOperation> getClassVariables() {
    return CLASSVARIABLES;
  }

  @Override
  public DbPreferencesOperationRemoteDelegate getRemoteDelegate() {
    return (DbPreferencesOperationRemoteDelegate) super.getRemoteDelegate();
  }

  /**
   * Creates an operation object.
   *
   * @param session the session
   */
  public DbPreferencesOperation(Db session) {
    super(session);
  }

  /**
   * Creates an operation object not associated to a session.<br>
   * The session must be set via {@link #setSession} in order to use it.
   */
  public DbPreferencesOperation() {
  }


  /**
   * Loads the root preferences node with all its sub-nodes and keys.
   *
   * @param user the username, "" if system user
   * @param forced true always load from storage, false if try repository first
   * @return the node, never null
   */
  public DbPreferences loadRootTree(String user, boolean forced) {
    Db db = getSession();
    if (db.isRemote()) {
      return getRemoteDelegate().loadRootTree(user, forced);
    }
    else {
      return forced ? loadRoot(db, user) : DbPreferencesFactory.getInstance().getRootTree(db, user);
    }
  }


  /**
   * Loads all persisted nodes and keys suitable to refresh a whole preferences tree.
   *
   * @param node the root node
   * @param serial the current serial to load from repo, &lt; 0 to load unconditionally
   * @return the nodes and keys if current serial outdated, null if serials match and no need to refresh
   */
  public RefreshInfo loadRefreshInfo(DbPreferences node, long serial) {
    Db db = getSession();
    if (db.isRemote()) {
      return getRemoteDelegate().loadRefreshInfo(node.getUser(), serial);
    }
    else {
      DbPreferencesFactory factory = DbPreferencesFactory.getInstance();
      if (serial <= 0) {
        // load all nodes and keys for this root (ordered by id, i.e. creation)
        long repoSerial = factory.getSerial(node.getUser());
        long rootId = node.getPersistentNode().getId();
        return new RefreshInfo(repoSerial,
                               new DbPreferencesNode(db).selectByRootNodeId(rootId),
                               new DbPreferencesKey(db).selectByRootNodeId(rootId));
      }
      // request from remote client: load from repo (persisted only!)
      synchronized(factory) {
        // the serial must be the one of the tree returned, i.e., read after the tree is loaded, if not yet done
        node = factory.getRootTree(db, node.getUser());
        long repoSerial = factory.getSerial(node.getUser());
        if (serial != repoSerial) {
          List<DbPreferencesNode> prefNodes = new ArrayList<>();
          List<DbPreferencesKey> prefKeys = new ArrayList<>();
          addNodeToLists(node, prefNodes, prefKeys);
          return new RefreshInfo(repoSerial, prefNodes, prefKeys);
        }
      }
      return null;
    }
  }


  /**
   * Flushes a node back to storage.
   *
   * @param node the node
   * @param sync true if load modifications from storage before flush
   * @param orphan true if node is an orphan not loaded from its parent, false if node is part of repository
   * @return true if some nodes or keys were updated from storage
   */
  public boolean flush(DbPreferences node, boolean sync, boolean orphan) {
    // go up the tree until the first persisted node or the root node
    while (!node.isRootNode() && node.getPersistentNode().isNew()) {
      node = node.parent();
    }

    DbPreferences topNode = node;
    boolean updated = false;

    Db db = getSession();
    if (db.isRemote()) {
      updated = getRemoteDelegate().flush(node, sync);
      // updated ids and serials will be provided by the mod listeners
    }
    else {
      if (!PersistedPreferencesFactory.getInstance().isReadOnly()) {
        updated = db.transaction("flush", () -> {
          if (sync) {
            DbPreferencesFactory factory = DbPreferencesFactory.getInstance();
            factory.updateRepository(db,
                                     new DbPreferencesNode(db).getModificationCount(),
                                     new DbPreferencesKey(db).getModificationCount());
          }
          return topNode.flushImpl(db, !orphan);
        });
      }
    }

    return updated;
  }



  /**
   * Loads a root node and all its sub-nodes from database.<br>
   * If the node is new, a new instance will be created, but not persisted yet.
   *
   * @param db the session
   * @param user the username, null if system root
   * @return the root preferences
   */
  private DbPreferences loadRoot(Db db, String user) {

    DbPreferencesNode rootNode = new DbPreferencesNode(db).selectByUserAndName(user, "/");
    if (rootNode == null) {
      rootNode = new DbPreferencesNode();
      rootNode.setUser(user);
      rootNode.setName("/");
    }

    Map<Long,DbPreferences> children = new HashMap<>();
    List<DbPreferencesKey> keys = new ArrayList<>();

    DbPreferences root = new DbPreferences(null, rootNode, children.values(), keys);
    children.put(rootNode.getId(), root);   // add root to simplify buildChild below

    if (!rootNode.isNew()) {
      // build the whole subtree
      Map<Long,DbPreferencesNode> subNodes = new HashMap<>();

      for (DbPreferencesNode prefNode : rootNode.selectByRootNodeId(rootNode.getId())) {
        if (prefNode.getId() != rootNode.getId()) {
          prefNode.setSession(null);   // clear the reference!
          subNodes.put(prefNode.getId(), prefNode);
        }
      }

      // build node hierarchy
      for (DbPreferencesNode subNode: subNodes.values()) {
        buildChild(subNode, subNodes, children);
      }

      // add children to their parents
      for (DbPreferences child: children.values()) {
        long parentId = child.getPersistentNode().getParentId();
        if (parentId != 0) {
          DbPreferences parent = children.get(parentId);
          if (parent != null) {
            parent.addChild(child);
          }
          else {
            LOGGER.warning("parent missing for child node {0}", child.getPersistentNode());
          }
        }
      }

      // add keys
      for (DbPreferencesKey prefKey : new DbPreferencesKey(db).selectByRootNodeId(rootNode.getId())) {
        prefKey.setSession(null);   // clear the reference!
        DbPreferences parent = children.get(prefKey.getNodeId());
        if (parent != null) {
          parent.putPersistentKey(prefKey);
        }
        else {
          LOGGER.warning("parent missing for key {0} -> key removed!", prefKey);
          prefKey.setSession(db);
          prefKey.deleteObject();
        }
      }
    }

    return root;
  }

  /**
   * Recursively build child nodes.
   *
   * @param prefNode the current node
   * @param subNodes the map of child prefnode
   * @param children the map of child preferences node
   * @return the created preferences node
   */
  private DbPreferences buildChild(DbPreferencesNode prefNode,
                                   Map<Long,DbPreferencesNode> subNodes,
                                   Map<Long,DbPreferences> children) {
    long parentId = prefNode.getParentId();
    DbPreferences parent = children.get(parentId);
    if (parent == null) {
      // parent missing: build recursively
      parent = buildChild(subNodes.get(prefNode.getParentId()), subNodes, children);
    }
    DbPreferences child = new DbPreferences(parent, prefNode, null, null);
    children.put(prefNode.getId(), child);
    return child;
  }

  /**
   * Adds all sub-nodes and keys of a node to given lists.<br>
   * Only persisted nodes and keys are added.
   *
   * @param node the node
   * @param prefNodes the nodes mapped by ID
   * @param prefKeys the keys mapped by ID
   */
  private void addNodeToLists(DbPreferences node,
                              List<DbPreferencesNode> prefNodes,
                              List<DbPreferencesKey> prefKeys) {

    if (!node.getPersistentNode().isNew()) {
      prefNodes.add(node.getPersistentNode());
    }

    for (DbPreferencesKey key: node.getKeys()) {
      if (!key.isNew() && !key.isRemoved()) {
        prefKeys.add(key);
      }
    }
    for (DbPreferences child: node.getChildren()) {
      if (!child.getPersistentNode().isNew() && !child.getPersistentNode().isRemoved()) {
        addNodeToLists(child, prefNodes, prefKeys);
      }
    }
  }

}
