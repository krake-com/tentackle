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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.pdo.PersistenceException;
import org.tentackle.persist.AbstractDbOperation;
import org.tentackle.persist.Db;
import org.tentackle.persist.DbOperationClassVariables;
import org.tentackle.persist.prefs.rmi.DbPreferencesOperationRemoteDelegate;
import org.tentackle.prefs.PersistedPreferencesFactory;

/**
 * Remoting capable operations on persisted preferences.
 *
 * @author harald
 */
public class DbPreferencesOperation extends AbstractDbOperation<DbPreferencesOperation> {

  private static final long serialVersionUID = 1L;

  private static final Logger LOGGER = LoggerFactory.getLogger(DbPreferencesOperation.class);

  private static final DbOperationClassVariables<DbPreferencesOperation> CLASSVARIABLES =
            new DbOperationClassVariables<>(DbPreferencesOperation.class);

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
   * Gets the system root preferences node.
   *
   * @return the node, never null
   */
  public DbPreferences getSystemRoot() {
    if (getSession().isRemote()) {
      try {
        return getRemoteDelegate().getSystemRoot();
      }
      catch (RemoteException rex) {
        throw PersistenceException.createFromRemoteException("<system>", rex);
      }
    }
    else {
      return loadRoot(getSession(), null);
    }
  }


  /**
   * Gets the user root preferences node.
   *
   * @param user the username
   * @return the node,.never null
   */
  public DbPreferences getUserRoot(String user) {
    if (getSession().isRemote()) {
      try {
        return getRemoteDelegate().getUserRoot(user);
      }
      catch (RemoteException rex) {
        throw PersistenceException.createFromRemoteException(user, rex);
      }
    }
    else {
      return loadRoot(getSession(), user);
    }
  }


  /**
   * Flushes a node back to storage.
   *
   * @param node the node
   */
  public void flush(DbPreferences node) {
    if (getSession().isRemote()) {
      try {
        getRemoteDelegate().flush(node);
        // updated ids and serials will be provided by the mod listeners
      }
      catch (RemoteException rex) {
        throw PersistenceException.createFromRemoteException("<system>", rex);
      }
    }
    else {
      if (!PersistedPreferencesFactory.getInstance().isReadOnly()) {
        Db db = getSession();
        long txVoucher = db.begin("flush");
        try {
          node.flushImpl(db);
          db.commit(txVoucher);
        }
        catch (Exception ex) {
          db.rollback(txVoucher);
          LOGGER.severe("root node of " + node + " removed from preferences repository", ex);
          throw ex;
        }
      }
    }
  }



  /**
   * Loads a root node and all its subnodes from database.<br>
   * If the node is new, a new instance will be created, but not persisted yet.
   *
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

    Map<Long,DbPreferences> childs = new HashMap<>();
    List<DbPreferencesKey> keys = new ArrayList<>();

    DbPreferences root = new DbPreferences(null, rootNode, childs.values(), keys);
    childs.put(rootNode.getId(), root);   // add root to simplify buildChild below

    if (!rootNode.isNew()) {
      // build whole subtree
      Map<Long,DbPreferencesNode> subNodes = new HashMap<>();
      Map<Long,DbPreferencesKey> subKeys = new HashMap<>();

      for (DbPreferencesNode prefNode : rootNode.selectByRootNodeId(rootNode.getId())) {
        if (prefNode.getId() != rootNode.getId()) {
          prefNode.setSession(null);   // clear the reference!
          subNodes.put(prefNode.getId(), prefNode);
        }
      }

      // build node hierarchy
      for (DbPreferencesNode subNode: subNodes.values()) {
        buildChild(subNode, subNodes, childs);
      }

      // add childs to their parents
      for (DbPreferences child: childs.values()) {
        long parentId = child.getPersistentNode().getParentId();
        if (parentId != 0) {
          DbPreferences parent = childs.get(parentId);
          if (parent != null) {
            parent.putChild(child);
          }
          else {
            LOGGER.warning("parent with ID={0} missing for child node {1}",
                           child.getPersistentNode().getId(), child.getPersistentNode().getId());
          }
        }
      }

      // add keys
      for (DbPreferencesKey prefKey : new DbPreferencesKey(db).selectByRootNodeId(rootNode.getId())) {
        prefKey.setSession(null);   // clear the reference!
        subKeys.put(prefKey.getId(), prefKey);
        DbPreferences parent = childs.get(prefKey.getNodeId());
        if (parent != null) {
          parent.putPersistentKey(prefKey);
        }
        else {
          LOGGER.warning("parent with ID={0} missing for key {1}",
                         prefKey.getNodeId(), prefKey.getId());
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
   * @param childs the map of child preferences node
   * @return the created preferences node
   */
  private DbPreferences buildChild(DbPreferencesNode prefNode,
                                   Map<Long,DbPreferencesNode> subNodes,
                                   Map<Long,DbPreferences> childs) {
    long parentId = prefNode.getParentId();
    DbPreferences parent = childs.get(parentId);
    if (parent == null) {
      // parent missing: build recursively
      parent = buildChild(subNodes.get(prefNode.getParentId()), subNodes, childs);
    }
    DbPreferences child = new DbPreferences(parent, prefNode, null, null);
    childs.put(prefNode.getId(), child);
    return child;
  }

}
