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

import java.util.Collection;
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
import org.tentackle.persist.prefs.DbPreferencesOperation.RefreshInfo;
import org.tentackle.prefs.PersistedPreferencesFactory;


/**
 * Repository and factory for database backed preferences.
 * <p>
 * The DbPreferencesFactory implements {@link PersistedPreferencesFactory} which in turn
 * extends {@link java.util.prefs.PreferencesFactory}. As such, it can be used as a drop-in
 * replacement for the default JRE preferences factory. By default, however, it is available
 * to the application only via {@link PersistedPreferencesFactory#getInstance()}, which is the
 * preferred way for tentackle applications to use preferences.
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
   * Modification tracker name.
   */
  private static final String TRACKER_NAME = "preferences";


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

    private ListenerKey(DbPreferences node) {
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



  /**
   * The root node with all nodes and keys loaded.
   */
  private static class Root {

    private static long lastSerial;     // last serial

    private final DbPreferences node;   // the root node holding the whole tree
    private long serial;                // the serial version of the whole tree

    /**
     * Creates a root node.
     *
     * @param node the preferences node
     */
    private Root(DbPreferences node) {
      this.node = node;
      countUpdate();
    }

    private void countUpdate() {
      serial = ++lastSerial;
    }
  }


  private boolean autoSync;                 // autosync flag
  private boolean readOnly;                 // readonly flag
  private boolean systemOnly;               // systemonly flagc

  private final Map<String,Root> roots;     // loaded roots, key is username ("" for system)

  private final Map<ListenerKey,Set<NodeChangeListener>> nodeListeners;       // listeners for added/removed nodes
  private final Map<ListenerKey,Set<PreferenceChangeListener>> prefListeners; // listeners for changed keys

  private long nodeTableSerial;             // highest tableSerial of _all_ DbPreferencesNodes or remote repo
  private long keyTableSerial;              // highest tableSerial of _all_ DbPreferencesKeys


  /**
   * Creates the factory.
   */
  public DbPreferencesFactory() {
    roots = new HashMap<>();
    nodeListeners = new HashMap<>();
    prefListeners = new HashMap<>();
    autoSync = true;

    Db db = requestDb(false, false);
    try {
      if (db.isRemote()) {
        nodeTableSerial = PdoTracker.getInstance().getSerial(TRACKER_NAME);
        keyTableSerial = 0;
        PdoTracker.getInstance().addModificationListener(
                new ModificationListenerAdapter(TRACKER_NAME) {

          @Override
          public void dataChanged(ModificationEvent ev) {
            if (isAutoSync()) {
              updateRepository((Db) ev.getSession(), ev.getSerial(), 0);
            }
          }
        });
      }
      else {
        nodeTableSerial = new DbPreferencesNode(db).getModificationCount();
        keyTableSerial = new DbPreferencesKey(db).getModificationCount();
        PdoTracker.getInstance().addModificationListener(
                new ModificationListenerAdapter(DbPreferencesNode.TABLENAME, DbPreferencesKey.TABLENAME) {

          @Override
          public void dataChanged(ModificationEvent ev) {
            long nodeMaxSerial = 0;
            long keyMaxSerial = 0;
            for (ModificationEventDetail detail : ev.getDetails()) {
              switch (detail.getName()) {
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
    }
    finally {
      releaseDb(db);
    }
  }

  @Override
  public synchronized void invalidate() {
    roots.clear();
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
    return getRootTree(null);
  }

  @Override
  public synchronized DbPreferences getUserRoot() {
    if (isSystemOnly()) {
      return getSystemRoot();
    }
    return getRootTree(getUserName());
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
   * Gets the current serial version of given user- or system root.
   *
   * @param user the username, null of "" if system
   * @return the serial, 0 if no such root loaded
   */
  public long getSerial(String user) {
    String key = user == null ? "" : user;
    Root root = roots.get(key);
    return root == null ? 0 : root.serial;
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
    if (node.isRootNode() && node.getPersistentNode().isRemoved()) {
      removeRoot(node);
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
   * Same as flush but loads any modifications into repo first and then flushes all mods.<br>
   * Necessary if autoSync is set to false (default is true).
   *
   *
   * @param node the preferences node
   * @throws BackingStoreException
   */
  public synchronized void sync(DbPreferences node) throws BackingStoreException {
    Db db = requestDb(node.isUserNode(), true);
    try {
      updateRepository(db,
                       new DbPreferencesNode(db).getModificationCount(),
                       new DbPreferencesKey(db).getModificationCount());
    }
    finally {
      releaseDb(db);
    }
    flush(node);
  }



  /**
   * Removes a root node of given node from the repository.
   *
   * @param node the root node or one of its subnodes
   */
  protected synchronized void removeRoot(DbPreferences node) {
    String user = node.getUser();
    if (user == null) {
      user = "";
    }
    roots.remove(user);
  }

  /**
   * Updates the repository.<br>
   * Invoked whenever the data in prefnode or prefkey has changed.
   *
   * @param db the session
   * @param nodeMaxSerial the new table serial for prefnodes
   * @param keyMaxSerial the nore table serial for prefkeys
   */
  protected synchronized void updateRepository(Db db, long nodeMaxSerial, long keyMaxSerial) {
    if (nodeMaxSerial != nodeTableSerial || keyMaxSerial != keyTableSerial) {
      if (db.isRemote()) {
        // rebuild all roots if serials don't match with the ones in the remote repository
        for (Root root: roots.values()) {
          rebuildRoot(db, root);
        }
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

        Set<String> usersModified = new HashSet<>();  // roots affected, "" is system
        // TreeSet to sort by id (because childs are always added _after_ their parents, see flushImpl)
        Set<DbPreferencesNode> changedNodes = new TreeSet<>();
        Set<DbPreferencesKey> changedKeys = new TreeSet<>();

        Map<Long,DbPreferencesNode> idNodeMap = new HashMap<>();    // related nodes map by ID

        if (!someRemoved) {
          if (nodeExpireSet != null) {
            // figure out roots to update
            List<DbPreferencesNode> prefNodes =
                    new DbPreferencesNode(db).selectObjectsWithExpiredTableSerials(nodeTableSerial);
            if (prefNodes.size() == nodeExpireSet.size()) {
              for (DbPreferencesNode prefNode : prefNodes) {
                changedNodes.add(prefNode);
                idNodeMap.put(prefNode.getId(), prefNode);
                String user = prefNode.getUser();
                usersModified.add(user == null ? "" : user);
              }
            }
            else {
              // removed in the meantime???
              someRemoved = true;
            }
          }

          if (!someRemoved && keyExpireSet != null) {
            // update keys
            List<DbPreferencesKey> prefKeys =
                    new DbPreferencesKey(db).selectObjectsWithExpiredTableSerials(keyTableSerial);
            if (prefKeys.size() == keyExpireSet.size()) {
              for (DbPreferencesKey prefKey : prefKeys) {
                changedKeys.add(prefKey);
                DbPreferencesNode prefNode = idNodeMap.get(prefKey.getNodeId());
                if (prefNode == null) {
                  prefNode = new DbPreferencesNode(db).selectObject(prefKey.getNodeId());
                  if (prefNode != null) {
                    idNodeMap.put(prefNode.getId(), prefNode);
                  }
                }
                if (prefNode != null) {
                  changedNodes.add(prefNode);   // add even if node itself wasn't modified bec. of listeners
                  String user = prefNode.getUser();
                  usersModified.add(user == null ? "" : user);
                }
              }
            }
            else {
              someRemoved = true;
            }
          }
        }

        if (someRemoved) {
          // rebuild all roots
          for (Root root: roots.values()) {
            rebuildRoot(db, root);
          }
        }
        else {
          // only added or updated nodes or keys: update roots
          for (String user: usersModified) {
            Root root = roots.get(user);
            if (root != null) {
              updateRoot(root, changedNodes, changedKeys);
            }
          }
        }
      }

      nodeTableSerial = nodeMaxSerial;
      keyTableSerial = keyMaxSerial;
      if (!db.isRemote()) {
        PdoTracker.getInstance().countModification(TRACKER_NAME);
      }
    }
  }


  /**
   * Rebuilds the given root from scratch.<br>
   * Reloads all nodes and keys.
   *
   * @param db the session
   * @param root the root node
   */
  private void rebuildRoot(Db db, Root root) {
    // load all nodes and keys for this root (ordered by id, i.e. creation)
    RefreshInfo info = new DbPreferencesOperation(db).refreshRoot(root.node, db.isRemote() ? root.serial : 0);
    if (info != null) {
      Collection<DbPreferencesNode> prefNodes = info.prefNodes;
      Collection<DbPreferencesKey> prefKeys = info.prefKeys;

      // to speed up: build maps of ID:node and ID:key
      Map<Long,DbPreferences> idNodeMap = new HashMap<>();
      Map<String,DbPreferences> pathNodeMap = new HashMap<>();
      Map<Long,DbPreferencesKey> idKeyMap = new HashMap<>();
      addNodeToMaps(root.node, idNodeMap, pathNodeMap, idKeyMap);

      // add or update nodes
      for (DbPreferencesNode prefNode : prefNodes) {
        DbPreferences node = pathNodeMap.get(prefNode.getName());
        if (node == null) {
          // node is new
          DbPreferences parent = idNodeMap.get(prefNode.getParentId());   // must exist bec. nodes are sorted by id
          if (parent == null) {
            // must exist, because prefNodes are sorted by id!
            LOGGER.severe("parent ID={0} missing for node ID={1}", prefNode.getParentId(), prefNode.getId());
            removeRoot(root.node);   // force reload next access
          }
          else {
            node = new DbPreferences(parent, prefNode, null, null);
            parent.putChild(node);
            idNodeMap.put(prefNode.getId(), node);
            nodeChanged(node);
          }
        }
        else {
          // just id,serial or tableserial changed
          if (node.updatePersistentNode(prefNode)) {
            nodeChanged(node);
            idNodeMap.put(prefNode.getId(), node);    // in case ID has changed (node was new)
          }
        }
      }

      // add or update keys
      for (DbPreferencesKey prefKey: prefKeys) {
        DbPreferences node = idNodeMap.get(prefKey.getNodeId());    // must exist now!
        if (node == null) {
          LOGGER.severe("node ID={0} missing for key ID={1}", prefKey.getNodeId(), prefKey.getId());
          removeRoot(root.node);   // force reload next access
        }
        else {
          DbPreferencesKey key = node.getPersistentKey(prefKey.getKey());
          if (key == null) {
            node.putPersistentKey(prefKey);
            idKeyMap.put(prefKey.getId(), prefKey);
          }
          else {
            node.updatePersistentKey(prefKey);
          }
          keyChanged(node, prefKey);
        }
      }

      // remove keys
      Set<DbPreferencesKey> persistedKeys = new HashSet<>(prefKeys);
      for (DbPreferencesKey prefKey: idKeyMap.values()) {
        if (!prefKey.isNew()) {   // if not created in this JVM
          if (!persistedKeys.contains(prefKey)) {
            DbPreferences node = idNodeMap.get(prefKey.getNodeId());
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
      for (DbPreferences node: idNodeMap.values()) {
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

      if (db.isRemote()) {
        root.serial = info.serial;
      }
      else {
        root.countUpdate();
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
  private void updateRoot(Root root,
                          Set<DbPreferencesNode> changedNodes,
                          Set<DbPreferencesKey> changedKeys) {

    // to speed up: build maps of ID:node and ID:key
    Map<Long,DbPreferences> idNodeMap = new HashMap<>();
    Map<String,DbPreferences> pathNodeMap = new HashMap<>();
    addNodeToMaps(root.node, idNodeMap, pathNodeMap);

    long rootId = root.node.getPersistentNode().getId();

    // add or update nodes
    for (DbPreferencesNode prefNode: changedNodes) {
      if (prefNode.getRootNodeId() == rootId) {   // only nodes belonging to this root
        DbPreferences node = pathNodeMap.get(prefNode.getName());
        if (node == null) {
          // add node
          DbPreferences parent = idNodeMap.get(prefNode.getParentId());  // must exist bec. nodes are sorted by id
          if (parent != null) {
            node = new DbPreferences(parent, prefNode, null, null);
            parent.putChild(node);
            idNodeMap.put(prefNode.getId(), node);
            nodeChanged(node);
          }
          else {
            LOGGER.warning("parent node ID=" + prefNode.getParentId() + " vanished for node " + prefNode);
          }
        }
        else {
          if (node.updatePersistentNode(prefNode)) {
            nodeChanged(node);
            idNodeMap.put(prefNode.getId(), node);    // in case ID changed
          }
        }
      }
    }

    // now that all nodes are added: add or update the keys
    for (DbPreferencesKey prefKey: changedKeys) {
      if (prefKey.getRootNodeId() == rootId) {
        DbPreferences node = idNodeMap.get(prefKey.getNodeId());    // must exist now
        if (node != null) {
          node.updatePersistentKey(prefKey);
          keyChanged(node, prefKey);
        }
        else {
          LOGGER.warning("node ID=" + prefKey.getNodeId() + " valinished for key " + prefKey);
        }
      }
    }

    root.countUpdate();
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
   * Gets the root node for given user and all of its subnodes and keys at once.
   *
   * @param user the username, null or empty if system user
   * @return the root node, never null
   */
  public DbPreferences getRootTree(String user) {
    String key = user == null ? "" : user;
    Root root = roots.get(key);
    if (root == null) {
      Db db = requestDb(true, false);
      try {
        root = new Root(new DbPreferencesOperation(db).loadRoot(user));
        roots.put(key, root);
      }
      finally {
        releaseDb(db);
      }
    }
    return root.node;
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
   * @param idNodeMap the nodes mapped by ID
   * @param pathNodeMap the nodes mapped by path
   * @param idKeyMap the keys mapped by ID
   */
  private void addNodeToMaps(DbPreferences node,
                             Map<Long,DbPreferences> idNodeMap,
                             Map<String,DbPreferences> pathNodeMap,
                             Map<Long,DbPreferencesKey> idKeyMap) {

    if (!node.getPersistentNode().isVirgin()) {
      idNodeMap.put(node.getPersistentNode().getId(), node);
    }
    pathNodeMap.put(node.absolutePath(), node);

    for (DbPreferencesKey key: node.getKeys()) {
      if (!key.isVirgin()) {
        idKeyMap.put(key.getId(), key);
      }
    }
    for (DbPreferences child: node.getChilds()) {
      addNodeToMaps(child, idNodeMap, pathNodeMap, idKeyMap);
    }
  }

  /**
   * Adds all subnodes of a node to given maps.
   *
   * @param node the node
   * @param idNodeMap the nodes mapped by ID
   * @param pathNodeMap the nodes mapped by path
   */
  private void addNodeToMaps(DbPreferences node,
                             Map<Long,DbPreferences> idNodeMap,
                             Map<String,DbPreferences> pathNodeMap) {

    if (!node.getPersistentNode().isVirgin()) {
      idNodeMap.put(node.getPersistentNode().getId(), node);
    }
    pathNodeMap.put(node.absolutePath(), node);

    for (DbPreferences child: node.getChilds()) {
      addNodeToMaps(child, idNodeMap, pathNodeMap);
    }
  }

}
