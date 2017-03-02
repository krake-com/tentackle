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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.prefs.BackingStoreException;
import java.util.prefs.NodeChangeEvent;
import java.util.prefs.NodeChangeListener;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import org.tentackle.common.Service;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.pdo.ModificationEvent;
import org.tentackle.pdo.ModificationEventDetail;
import org.tentackle.pdo.ModificationListenerAdapter;
import org.tentackle.pdo.PdoTracker;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.PersistenceException;
import org.tentackle.pdo.Session;
import org.tentackle.persist.Db;
import org.tentackle.prefs.PersistedPreferencesFactory;


/**
 * Repository and factory for database backed preferences.
 *
 * @author harald
 */
@Service(PersistedPreferencesFactory.class)
public class DbPreferencesFactory implements PersistedPreferencesFactory {

  /**
   * The logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DbPreferencesFactory.class);

  /**
   * Listeners are managed by the repository, not the nodes.
   * This allows un- and reloading the nodes without losing the listeners.
   * <p>
   * Notice that when a node is removed, their listeners will <em>not</em> be removed.
   * So, if the node is added again, the listeners are still there.
   * Listeners have to be removed by the application explicitly.
   */
  private static class ListenerKey {
    private final String user;    // "" = system
    private final String path;

    public ListenerKey(DbPreferences node) {
      user = node.isUserNode() ? node.getUser() : "";
      path = node.absolutePath();
    }

    @Override
    public int hashCode() {
      int hash = 7;
      hash = 31 * hash + Objects.hashCode(this.user);
      hash = 31 * hash + Objects.hashCode(this.path);
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final ListenerKey other = (ListenerKey) obj;
      if (!Objects.equals(this.user, other.user)) {
        return false;
      }
      return Objects.equals(this.path, other.path);
    }

  }


  private boolean autoSync;                 // autosync flag
  private boolean readOnly;                 // readonly flag
  private boolean systemOnly;               // systemonly flagc

  private DbPreferences systemRoot;                           // the one and only system root node, null if not loaded
  private final Map<String,DbPreferences> userRoots;          // loaded roots for users

  private final Map<ListenerKey,Set<NodeChangeListener>> nodeListeners;       // listeners for added/removed nodes
  private final Map<ListenerKey,Set<PreferenceChangeListener>> prefListeners; // listeners for changed keys

  private long nodeTableSerial;             // highest tableSerial of _all_ DbPreferencesNodes
  private long keyTableSerial;              // highest tableSerial of _all_ DbPreferencesKeys


  /**
   * Creates the factory.
   */
  public DbPreferencesFactory() {
    userRoots = new HashMap<>();
    nodeListeners = new HashMap<>();
    prefListeners = new HashMap<>();
    autoSync = true;

    Db db = requestDb(false, false);
    if (!db.isRemote()) {
      try {
        nodeTableSerial = new DbPreferencesNode(db).getModificationCount();
        keyTableSerial = new DbPreferencesKey(db).getModificationCount();
      }
      finally {
        releaseDb(db);
      }
    }

    PdoTracker.getInstance().addModificationListener(
            new ModificationListenerAdapter(DbPreferencesNode.TABLENAME, DbPreferencesKey.TABLENAME) {

      @Override
      public void dataChanged(ModificationEvent ev) {
        long nodeMaxSerial = 0;
        long keyMaxSerial = 0;
        for (ModificationEventDetail detail: ev.getDetails()) {
          switch(detail.getName()) {
            case DbPreferencesNode.TABLENAME:
              nodeMaxSerial = detail.getSerial();
              break;
            case DbPreferencesKey.TABLENAME:
              keyMaxSerial = detail.getSerial();
              break;
          }
        }
        if (isAutoSync()) {
          updateRepository((Db) ev.getSession(), nodeMaxSerial, keyMaxSerial);
        }
      }
    });
  }

  @Override
  public synchronized void invalidate() {
    systemRoot = null;
    userRoots.clear();
  }

  @Override
  public boolean isAutoSync() {
    return autoSync;
  }

  @Override
  public void setAutoSync(boolean autoSync) {
    this.autoSync = autoSync;
  }

  @Override
  public boolean isReadOnly() {
    return readOnly;
  }

  @Override
  public void setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
  }

  @Override
  public boolean isSystemOnly() {
    return systemOnly;
  }

  @Override
  public void setSystemOnly(boolean systemOnly) {
    this.systemOnly = systemOnly;
  }

  @Override
  public synchronized DbPreferences getSystemRoot() {
    if (systemRoot == null) {
      Db db = requestDb(false, false);
      try {
        systemRoot = new DbPreferencesOperation(db).getSystemRoot();
      }
      finally {
        releaseDb(db);
      }
    }
    return systemRoot;
  }

  @Override
  public synchronized DbPreferences getUserRoot() {

    if (isSystemOnly()) {
      return getSystemRoot();
    }

    String user = getUserName();
    DbPreferences root = userRoots.get(user);
    if (root == null) {
      Db db = requestDb(true, false);
      try {
        root = new DbPreferencesOperation(db).getUserRoot(user);
      }
      finally {
        releaseDb(db);
      }
      userRoots.put(user, root);
    }
    return root;
  }

  @Override
  public DbPreferences userNodeForPackage(Class<?> c) {
    return getUserRoot().node(nodeName(c));
  }

  @Override
  public DbPreferences systemNodeForPackage(Class<?> c) {
    return getSystemRoot().node(nodeName(c));
  }

  @Override
  public Preferences systemRoot() {
    return getSystemRoot();
  }

  @Override
  public Preferences userRoot() {
    return getUserRoot();
  }


  /**
   * Adds a preferences changed listener.
   * <p>
   * Notice that when a node is removed, their listeners will <em>not</em> be removed.
   * So, if the node is added again, the listeners are still there.
   * Listeners have to be removed by the application explicitly.
   *
   * @param node the node
   * @param pcl the listener
   */
  public synchronized void addPreferenceChangeListener(DbPreferences node, PreferenceChangeListener pcl) {
    ListenerKey lk = new ListenerKey(node);
    Set<PreferenceChangeListener> listeners = prefListeners.get(lk);
    if (listeners == null) {
      listeners = new HashSet<>();
      prefListeners.put(lk, listeners);
    }
    listeners.add(pcl);
  }

  /**
   * Removes a preferences changed listener.
   *
   * @param node the node
   * @param pcl the listener
   */
  public synchronized void removePreferenceChangeListener(DbPreferences node, PreferenceChangeListener pcl) {
    ListenerKey lk = new ListenerKey(node);
    Set<PreferenceChangeListener> listeners = prefListeners.get(lk);
    if (listeners != null) {
      listeners.remove(pcl);
      if (listeners.isEmpty()) {
        prefListeners.remove(lk);
      }
    }
  }

  /**
   * Adds a node listener.
   * <p>
   * Notice that when a node is removed, their listeners will <em>not</em> be removed.
   * So, if the node is added again, the listeners are still there.
   * Listeners have to be removed by the application explicitly.
   *
   * @param node the node
   * @param pcl the listener
   */
  public synchronized void addNodeChangeListener(DbPreferences node, NodeChangeListener ncl) {
    ListenerKey lk = new ListenerKey(node);
    Set<NodeChangeListener> listeners = nodeListeners.get(lk);
    if (listeners == null) {
      listeners = new HashSet<>();
      nodeListeners.put(lk, listeners);
    }
    listeners.add(ncl);
  }

  /**
   * Removes a node listener.
   *
   * @param node the node
   * @param pcl the listener
   */
  public synchronized void removeNodeChangeListener(DbPreferences node, NodeChangeListener ncl) {
    ListenerKey lk = new ListenerKey(node);
    Set<NodeChangeListener> listeners = nodeListeners.get(lk);
    if (listeners != null) {
      listeners.remove(ncl);
      if (listeners.isEmpty()) {
        nodeListeners.remove(lk);
      }
    }
  }

  /**
   * Invoke listeners when a node is added or removed.
   *
   * @param node the added or removed node
   */
  public void nodeChanged(DbPreferences node) {
    DbPreferences parent = node.parent();
    if (parent != null) {
      Set<NodeChangeListener> listeners = nodeListeners.get(new ListenerKey(parent));
      if (listeners != null) {
        NodeChangeEvent ev = new NodeChangeEvent(node.parent(), node);
        for (NodeChangeListener listener: listeners) {
          if (node.getPersistentNode().isRemoved()) {
            listener.childRemoved(ev);
          }
          else {
            listener.childAdded(ev);
          }
        }
      }
    }
  }

  /**
   * Invoke listeners when a key is added, updated or removed.
   *
   * @param node the node holding the key
   * @param key the changed key
   */
  public void keyChanged(DbPreferences node, DbPreferencesKey key) {
    Set<PreferenceChangeListener> listeners = prefListeners.get(new ListenerKey(node));
    if (listeners != null) {
      PreferenceChangeEvent ev = new PreferenceChangeEvent(node, key.getKey(), key.isRemoved() ? null : key.getValue());
      for (PreferenceChangeListener listener: listeners) {
        listener.preferenceChange(ev);
      }
    }
  }


  /**
   * Flushes a node and all of its subnodes to the backing store.
   *
   * @param node the preferences node
   * @throws BackingStoreException
   */
  public synchronized void flush(DbPreferences node) throws BackingStoreException {
    Db db = requestDb(node.isUserNode(), true);
    try {
      db.transaction(() -> {
        try {
          new DbPreferencesOperation(db).flush(node);
        }
        catch (RuntimeException rex) {
          // remove the root node from the repo (forces reload on next access)
          removeRoot(node);
        }
        return null;
      });
    }
    finally {
      releaseDb(db);
    }
  }

  /**
   * Syncs a node and all of its subnodes from and to the backing store.<br>
   * Same as flush but loads any modifications into repo first and then flushes all mods.
   *
   * @param node the preferences node
   * @throws BackingStoreException
   */
  public synchronized void sync(DbPreferences node) throws BackingStoreException {
    Db db = requestDb(node.isUserNode(), true);
    try {
      long nodeMaxSerial = new DbPreferencesNode(db).getModificationCount();
      long keyMaxSerial = new DbPreferencesKey(db).getModificationCount();
      updateRepository(db, nodeMaxSerial, keyMaxSerial);
    }
    finally {
      releaseDb(db);
    }
    flush(node);
  }



  /**
   * Removes a root node of given node from the repository.
   *
   * @param root the root node or one of its subnodes
   */
  protected synchronized void removeRoot(DbPreferences root) {
    if (root.isUserNode()) {
      userRoots.remove(root.getUser());
    }
    else {
      systemRoot = null;
    }
  }

  /**
   * Updates the repository.<br>
   * Invoked whenever the data in prefnode or prefkey has changed.
   *
   * @param db the session
   * @param nodeMaxSerial the new table serial for prefnodes
   * @param keyMaxSerial the nore table serial for prefkeys
   */
  protected void updateRepository(Db db, long nodeMaxSerial, long keyMaxSerial) {
    if (db.isRemote()) {
      // todo: via remote delegate
    }
    else {
      // get tableserial/id-pairs
      boolean someRemoved = false;
      List<IdSerialTuple> nodeExpireSet = null;
      List<IdSerialTuple> keyExpireSet = null;
      if (nodeMaxSerial > 0) {
        nodeExpireSet = new DbPreferencesNode(db).getExpiredTableSerials(nodeTableSerial, nodeMaxSerial);
        if (PdoUtilities.getInstance().isSomePdoRemoved(nodeExpireSet, nodeMaxSerial)) {
          someRemoved = true;
        }
      }
      if (!someRemoved && keyMaxSerial > 0) {
        keyExpireSet = new DbPreferencesKey(db).getExpiredTableSerials(keyTableSerial, keyMaxSerial);
        if (PdoUtilities.getInstance().isSomePdoRemoved(keyExpireSet, keyMaxSerial)) {
          someRemoved = true;
        }
      }

      boolean systemModified = false;               // true if systemroot affected
      Set<String> usersModified = new HashSet<>();  // user roots affected
      // TreeSet to sort by id (because childs are always added _after_ their parents, see flushImpl)
      Set<DbPreferencesNode> changedNodes = new TreeSet<>();
      Set<DbPreferencesKey> changedKeys = new TreeSet<>();

      if (!someRemoved) {
        if (nodeExpireSet != null) {
          // figure out roots to update
          for (IdSerialTuple idSer: nodeExpireSet) {
            DbPreferencesNode prefNode = new DbPreferencesNode(db).selectObject(idSer.getId());
            if (prefNode != null) {
              changedNodes.add(prefNode);
              String user = prefNode.getUser();
              if (user == null) {
                systemModified = true;
              }
              else {
                usersModified.add(user);
              }
            }
            else {
              // removed in the meantime???
              someRemoved = true;
              break;
            }
          }
        }
        if (keyExpireSet != null) {
          // update keys
          for (IdSerialTuple idSer: keyExpireSet) {
            DbPreferencesKey prefKey = new DbPreferencesKey(db).selectObject(idSer.getId());
            if (prefKey != null) {
              changedKeys.add(prefKey);
              DbPreferencesNode prefNode = new DbPreferencesNode(db).selectObject(prefKey.getNodeId());
              if (prefNode != null) {
                changedNodes.add(prefNode);   // add even if node itself wasn't modified
                String user = prefNode.getUser();
                if (user == null) {
                  systemModified = true;
                }
                else {
                  usersModified.add(user);
                }
              }
              else {
                someRemoved = true;
                break;
              }
            }
            else {
              someRemoved = true;
              break;
            }
          }
        }
      }

      if (someRemoved) {
        // rebuild all roots (rare condition, hopefully...)
        if (systemRoot != null) {
          rebuildRoot(db, systemRoot);
        }
        for (DbPreferences root: userRoots.values()) {
          rebuildRoot(db, root);
        }
      }
      else {
        // only added or updated nodes or keys: update roots
        if (systemModified && systemRoot != null) {
          updateRoot(systemRoot, changedNodes, changedKeys);
        }
        for (String user: usersModified) {
          DbPreferences root = userRoots.get(user);
          if (root != null) {
            updateRoot(root, changedNodes, changedKeys);
          }
        }
      }
    }

    nodeTableSerial = nodeMaxSerial;
    keyTableSerial = keyMaxSerial;
  }


  /**
   * Rebuilds the given root from scratch.<br>
   * Reloads all nodes and keys.
   *
   * @param db the session
   * @param root the root node
   */
  protected void rebuildRoot(Db db, DbPreferences root) {
    long rootId = root.getPersistentNode().getId();
    // load all nodes and keys for this root (ordered by id, i.e. creation)
    List<DbPreferencesNode> prefNodes = new DbPreferencesNode(db).selectByRootNodeId(rootId);
    List<DbPreferencesKey> prefKeys = new DbPreferencesKey(db).selectByRootNodeId(rootId);

    // to speed up: build maps of ID:node and ID:key
    Map<Long,DbPreferences> nodeMap = new HashMap<>();
    Map<Long,DbPreferencesKey> keyMap = new HashMap<>();
    addNodeToMaps(root, nodeMap, keyMap);

    // add or update nodes
    for (DbPreferencesNode prefNode : prefNodes) {
      DbPreferences node = nodeMap.get(prefNode.getId());
      if (node == null) {
        // node is new
        DbPreferences parent = nodeMap.get(prefNode.getParentId());
        if (parent == null) {
          // must exist, because prefNodes are sorted by id!
          LOGGER.severe("parent ID={0} missing for node ID={1}", prefNode.getParentId(), prefNode.getId());
          removeRoot(root);   // force reload next access
        }
        else {
          node = new DbPreferences(this, parent, prefNode, null, null);
          parent.putChild(node);
          nodeMap.put(prefNode.getId(), node);
          nodeChanged(node);
        }
      }
      else {
        // just id,serial or tableserial changed -> no listener to invoke
        node.updatePersistentNode(prefNode);
      }
    }

    // add or update keys
    for (DbPreferencesKey prefKey: prefKeys) {
      DbPreferences node = nodeMap.get(prefKey.getNodeId());
      if (node == null) {
        LOGGER.severe("node ID={0} missing for key ID={1}", prefKey.getNodeId(), prefKey.getId());
        removeRoot(root);   // force reload next access
      }
      else {
        DbPreferencesKey key = node.getPersistentKey(prefKey.getKey());
        if (key == null) {
          node.putPersistentKey(prefKey);
          keyMap.put(prefKey.getId(), prefKey);
        }
        else {
          node.updatePersistentKey(prefKey);
        }
        keyChanged(node, prefKey);
      }
    }

    // remove keys
    Set<DbPreferencesKey> persistedKeys = new HashSet<>(prefKeys);
    for (DbPreferencesKey prefKey: keyMap.values()) {
      if (!prefKey.isNew()) {   // if not created in this JVM
        if (!persistedKeys.contains(prefKey)) {
          DbPreferences node = nodeMap.get(prefKey.getNodeId());
          if (node != null) {
            prefKey = node.removePersistentKey(prefKey.getKey());
            if (prefKey != null) {
              keyChanged(node, prefKey);
            }
          }
        }
      }
    }

    // remove nodes
    Set<DbPreferencesNode> persistedNodes = new HashSet<>(prefNodes);
    for (DbPreferences node: nodeMap.values()) {
      if (!node.getPersistentNode().isNew()) {  // if not created in this JVM
        if (!persistedNodes.contains(node.getPersistentNode())) {
          DbPreferences parent = node.parent();
          if (parent != null) {
            node = parent.removeChild(node.name());
            if (node != null) {
              nodeChanged(node);
            }
          }
        }
      }
    }
  }


  /**
   * Updates a root node.
   *
   * @param root the root preferences node
   * @param changedNodes the changed nodes
   * @param changedKeys the changes keys
   */
  protected void updateRoot(DbPreferences root,
                            Set<DbPreferencesNode> changedNodes,
                            Set<DbPreferencesKey> changedKeys) {

    // to speed up: build maps of ID:node and ID:key
    Map<Long,DbPreferences> nodeMap = new HashMap<>();
    Map<Long,DbPreferencesKey> keyMap = new HashMap<>();
    addNodeToMaps(root, nodeMap, keyMap);

    long rootId = root.getPersistentNode().getId();

    // add or update nodes
    for (DbPreferencesNode prefNode: changedNodes) {
      if (prefNode.getRootNodeId() == rootId) {   // only nodes belonging to this root
        DbPreferences node = nodeMap.get(prefNode.getId());
        if (node == null) {
          // add node
          DbPreferences parent = nodeMap.get(prefNode.getParentId());
          if (parent != null) {
            node = new DbPreferences(this, parent, prefNode, null, null);
            parent.putChild(node);
            nodeMap.put(prefNode.getId(), node);
            nodeChanged(node);
          }
          else {
            LOGGER.warning("parent node ID=" + prefNode.getParentId() + " vanished for node " + prefNode);
          }
        }
        else {
          node.updatePersistentNode(prefNode);
        }
      }
    }

    // now that all nodes are added: add or update the keys
    for (DbPreferencesKey prefKey: changedKeys) {
      if (prefKey.getRootNodeId() == rootId) {
        DbPreferences node = nodeMap.get(prefKey.getNodeId());
        if (node != null) {
          node.updatePersistentKey(prefKey);
          keyChanged(node, prefKey);
        }
        else {
          LOGGER.warning("node ID=" + prefKey.getNodeId() + " valinished for key " + prefKey);
        }
      }
    }
  }


  /**
   * Requests for a session to work with.<br>
   * By default, the thread-local session is used.
   * If there is no default session, the mod tracker's session is used.
   * <p>
   * In preparation for replication layers such as poolkeeper,
   * a requested session must be released after use.
   *
   * @param forUser true if need a user session (no modtracker fallback)
   * @param forWrite true if the session is used for writing (flush), else read
   * @return the session
   */
  protected Db requestDb(boolean forUser, boolean forWrite) {
    Db db = (Db) Session.getCurrentSession();
    if (db == null) {
      if (forUser) {
        throw new PersistenceException("no thread local session for " + Thread.currentThread());
      }
      // no thread-local session: use the one from the mod tracker
      db = (Db) PdoTracker.getInstance().getSession();
      if (db == null) {
        throw new PersistenceException("no session for " + PdoTracker.getInstance());
      }
    }
    return db;
  }

  /**
   * Releases the session after use.
   *
   * @param db the session
   */
  protected void releaseDb(Db db) {
    // default does nothing
  }

  /**
   * Gets the current user name.
   *
   * @return the username
   */
  protected String getUserName() {
    return requestDb(true, false).getSessionInfo().getUserName();
  }


  /**
   * Returns the absolute path name of the node corresponding to the package of the specified object.
   *
   * @param clazz the class
   * @return the path name
   * @throws IllegalArgumentException if the package has node preferences node associated with it.
   */
  private String nodeName(Class<?> clazz) {
    if (clazz.isArray()) {
      throw new IllegalArgumentException("Arrays have no associated preferences node");
    }
    String className = clazz.getName();
    int pkgEndIndex = className.lastIndexOf('.');
    if (pkgEndIndex < 0) {
      return "/<unnamed>";
    }
    String packageName = className.substring(0, pkgEndIndex);
    return "/" + packageName.replace('.', '/');
  }

  /**
   * Adds all subnodes and keys of a node to given maps.
   *
   * @param node the node
   * @param nodeMap the node map (ID:node)
   * @param keyMap the keymap (ID:key)
   */
  private void addNodeToMaps(DbPreferences node,
                               Map<Long,DbPreferences> nodeMap,
                               Map<Long,DbPreferencesKey> keyMap) {
    nodeMap.put(node.getPersistentNode().getId(), node);
    for (DbPreferencesKey key: node.getKeys()) {
      keyMap.put(key.getId(), key);
    }
    for (DbPreferences child: node.getChilds()) {
      addNodeToMaps(child, nodeMap, keyMap);
    }
  }

}
