/**
 * Tentackle - http://www.tentackle.org
 * Copyright (C) 2001-2008 Harald Krake, harald@krake.de, +49 7722 9508-0
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

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.EventObject;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.prefs.BackingStoreException;
import java.util.prefs.NodeChangeEvent;
import java.util.prefs.NodeChangeListener;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import org.tentackle.common.Compare;
import org.tentackle.io.Base64;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.pdo.PersistenceException;
import org.tentackle.pdo.Session;
import org.tentackle.prefs.PersistedPreferences;
import org.tentackle.prefs.PersistedPreferencesFactory;



/**
 * Database backend for the {@link Preferences} API.<br>
 *
 * Tentackle preferences are stored in a database rather than a file(unix) or registry(windows).
 * The persistent classes are:
 * <ol>
 * <li>{@link DbPreferencesNode}: the nodes representing the preferences hierarchy.</li>
 * <li>{@link DbPreferencesKey}: the key/value pairs corresponding to the nodes.</li>
 * </ol>
 *
 * Unlike the default JDK-backends, tentackle preferences are synchronized between
 * JVMs, i.e. a (flushed) change in one JVM will show up in all other JVMs.
 * As a consequence, NodeChangeEvents and PreferenceChangeEvents work
 * across JVM-boundaries.
 * The synchronization is based on the {@link ModificationTracker} by use of the tableSerial.
 * Notice that events in the local JVM are enqueued at the time of change (i.e. put(),
 * remove(), node() or removeNode()) while other JVMs will become aware of the changes
 * only after the changes are flush()ed to persistent storage.
 * The underlying mechanism works as follows:
 * <ul>
 * <li>a key is modified: the corresponding key is updated to the db and all other JVMs will identify
 *   this by its tableSerial. If the corresponding key is already loaded in other JVMs, it is reloaded
 *   from the db and the optional PreferenceChangeListeners invoked for its node.
 * </li>
 * <li>a key is added or removed: the key is deleted from or added to the db and its node is updated allowing
 *   other JVMs to detect the node by its tableSerial and to update its keys.
 *   If PreferenceChangeListeners are registered, an Event will be triggered.
 * </li>
 * <li>a node is added or removed: the node is inserted into or removed from the db and its parent is
 *   updated to allow detection by its tableserial. NodeChangeEvents are triggered accordingly.
 *   The fact that "some" nodes have been deleted is detected by gaps in the retrieved tableSerials.
 * </li>
 * </ul>
 *
 * Compared to {@link java.util.prefs.AbstractPreferences} (which we can not simply extend
 * due to various design issues), the DbPreferences differ slightly in the following aspects:
 * <ul>
 * <li>PreferenceChangeEvents are only triggered if there really is a change. Thus, invoking put() without
 *   an effective change will *not* trigger an event.
 * </li>
 * <li>there is only little difference between sync() and flush() if autoSync=true, because a sync
 *   is automatically performed "in background" by the ModificationThread.
 *   If autoSync=false, sync() has to be invoked explicitly to reflect changes made by other JVMs.
 *   AutoSync is the recommended mode for most applications.
 * </li>
 * <li>nodeExists() only returns true, if the node is persistent (regardless whether we accessed it yet
 *   or created by another JVM). However, the semantics of nodeExists("") remain, i.e. "return true if not deleted".
 *   Thus, nodeExists("") == false just means that the node has been removed by removeNode(),
 *   either by the current or another JVM. However, true does *NOT* mean, that the node exists,
 *   i.e. is persistent in the database. It just means, that it has not been removed.
 * </li>
 * </ul>
 *
 * @author harald
 */
public class DbPreferences extends Preferences implements PersistedPreferences {

  /**
   * the logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DbPreferences.class);


  /** transaction name for flush. */
  private static final String TX_FLUSH = "flush";

  /** transaction name for remove node. */
  private static final String TX_REMOVE_NODE = "remove node";






  private String name;                                // name relative to the parent
  private String absolutePath;                        // absolute pathname
  private boolean userMode;                           // true if this is a user-node (speeds up)
  private DbPreferences parent;                       // parent node, null = this is the root
  private DbPreferences root;                         // the root preference (non-static because of user and system-scope)
  private DbPreferencesNode node;                     // the database node (backing store), null = deleted
  private Map<String, DbPreferencesKey> keys;         // list of keys associated with this node
                                                      // (lazy if no PreferenceChangeListeners registered)
  private Map<String, DbPreferences> childPrefs;      // child preferences, key is the name relative to the parent
  private PreferenceChangeListener[] prefListeners;   // preference change listeners
  private NodeChangeListener[] nodeListeners;         // node change listeners


  /**
   * Creates a preference node with the specified parent and the specified
   * name relative to its parent. Don't use this constructor for root-nodes!
   *
   * @param parent the parent of this preference node
   * @param name the name of this preference node, relative to its parent
   * @throws IllegalArgumentException if <tt>name</tt> contains a slash
   *          (<tt>'/'</tt>),  or <tt>parent</tt> is <tt>null</tt> and
   *          name isn't <tt>""</tt>.
   */
  public DbPreferences(DbPreferences parent, String name) {

    if (parent == null) {
      throw new IllegalArgumentException("illegal constructor for root node");
    }

    if (name.indexOf('/') != -1) {
      throw new IllegalArgumentException("name '" + name + "' contains '/'");
    }
    if (name.isEmpty()) {
      throw new IllegalArgumentException("illegal name: empty string");
    }
    absolutePath = parent.parent == null ?
                      "/" + name :
                      parent.absolutePath() + "/" + name;

    this.name     = name;
    this.parent   = parent;
    this.userMode = parent.userMode;
    this.root     = parent.root;

    initializeNode();
  }


  /**
   * Creates a root node.<br>
   * Called once per user and once for system scope.
   *
   * @param userMode true if user mode, else system mode
   */
  public DbPreferences(boolean userMode) {

    this.userMode = userMode;

    name = "";
    absolutePath = "/";
    root = this;

    initializeNode();
  }


  /**
   * Constructs DbPreferences from a DbPreferencesNode.
   * Only used from loadNodes().
   * Package scope!
   * @param parent the parent preferences
   * @param node the node
   * @throws BackingStoreException if creation failed
   */
  public DbPreferences(DbPreferences parent, DbPreferencesNode node) throws BackingStoreException {

    if (node.getParentId() != parent.node.getId())  {
      throw new BackingStoreException(
                        "parent-ID mismatch in node '" + node + "': got ID=" +
                        node.getParentId() + ", expected ID=" + parent.node.getId());
    }

    this.node         = node;
    this.absolutePath = node.getName();
    this.name         = node.getBaseName();
    this.parent       = parent;
    this.userMode     = parent.userMode;
    this.root         = parent.root;

    getFactory().updateNodeTableSerial(node.getTableSerial());

    childPrefs = new TreeMap<>();
  }


  /**
   * Gets the persistence node.
   *
   * @return the node
   */
  public DbPreferencesNode getNode() {
    return node;
  }

  /**
   * Gets the persistence parent.
   *
   * @return the parent
   */
  public DbPreferences getParent() {
    return parent;
  }

  /**
   * Gets the name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the absolute path.
   *
   * @return the path
   */
  public String getAbsolutePath() {
    return absolutePath;
  }


  public boolean isRootNode() {
    return "/".equals(absolutePath);
  }

  /**
   * Gets the root node.
   *
   * @return the root node
   */
  public DbPreferences getRoot() {
    return root;
  }

  /**
   * Gets the child preferences.
   *
   * @return the child preferences
   */
  public Map<String, DbPreferences> getChildPrefs() {
    return childPrefs;
  }


  /**
   * Gets the persistence key/value pairs.
   *
   * @return the keys
   */
  public Map<String, DbPreferencesKey> getKeys() {
    return keys;
  }



  /**
   * Gets the factory singleton.
   *
   * @return the factory
   */
  private DbPreferencesFactory getFactory() {
    return (DbPreferencesFactory) PersistedPreferencesFactory.getInstance();
  }


  /**
   * initializes the node
   */
  private void initializeNode() {
    childPrefs = new TreeMap<>();
    Session session = getFactory().getSession();
    node = getFactory().createNode().selectByUserAndName(
                    userMode ? session.getSessionInfo().getUserName() : null,
                    absolutePath);
    if (node == null) {
      // node is new
      node = getFactory().createNode();
      node.setUser(userMode ? session.getSessionInfo().getUserName() : null);
      node.setName(absolutePath);
      if (parent != null) {
        node.setParentId(parent.node.getId());
        node.setRootNodeId(parent.node.getRootNodeId());
      }
    }
    else  {
      // node already exists
      getFactory().updateNodeTableSerial(node.getTableSerial());
    }
    getFactory().addPreferences(this);
  }



  /**
   * loads all keys from the backing store for the current node
   */
  private void loadKeys() {
    keys = new TreeMap<>();
    for (DbPreferencesKey key: getFactory().createKey().selectByNodeId(node.getId())) {
      keys.put(key.getKey(), key);
      getFactory().addKey(key);
    }
  }

  /**
   * unloads all keys
   */
  private void unloadKeys() {
    if (keys != null) {
      for (DbPreferencesKey key: keys.values()) {
        getFactory().removeKey(key);
      }
    }
    keys = null;
  }


  /**
   * loads all child-nodes from the backing store.
   */
  private void loadNodes() {
    List<DbPreferencesNode> childNodes = getFactory().createNode().selectByParentId(node.getId());
    // remove from maps before clear
    for (DbPreferences child: childPrefs.values()) {
      if (child.node != null) {
        getFactory().removeNode(child.node);
      }
    }
    childPrefs.clear();
    for (DbPreferencesNode n: childNodes)  {
      String basename = n.getBaseName();
      try {
        DbPreferences childPref = getFactory().createPreferences(this, n);
        childPrefs.put(basename, childPref);    // append to node cache
        getFactory().addPreferences(childPref); // append to maps
      }
      catch (BackingStoreException ex) {
        throw new IllegalStateException("loading child nodes failed for " + this, ex);
      }
    }
  }



  /**
   * gets a reference to a key
   */
  private DbPreferencesKey getKey(String key) {
    if (keys == null) {
      loadKeys();
    }
    return keys.get(key);
  }



  /**
   * assert that node is not deleted
   */
  private void assertNotRemoved() throws IllegalStateException {
    if (node == null) {
      // node has been deleted
      throw new IllegalStateException("node '" + absolutePath + "' has been removed");
    }
  }


  /**
   * Updates the serial and tableserial of a parent node.
   * The method is invoked whenever a key or child node is added or removed.
   *
   * @param node the parent node to update
   * @throws BackingStoreException if update failed
   */
  protected void updateParentNode(DbPreferencesNode node) throws BackingStoreException {
    try {
      node.saveObject();
    }
    catch (PersistenceException e) {
      throw new BackingStoreException(e);
    }
  }


  /**
   * update parent node to trigger other jvms
   */
  private void updateParentNode() throws BackingStoreException {
    if (parent != null && parent.node != null)  {
      // update parent to trigger events in other jvms
      DbPreferences parentPref = getFactory().getPreferences(node.getParentId());
      if (parentPref == null) {
        throw new BackingStoreException("parent of " + node + " not in cache");
      }
      DbPreferencesNode parentNode = parentPref.node;
      if (parentNode == null) {
        throw new BackingStoreException("parentpref of " + node + " has no node");
      }
      updateParentNode(parentNode);
    }
  }



  /**
   * Recursive implementation of flush.
   *
   * @throws java.util.prefs.BackingStoreException
   */
  private void flushImpl() throws BackingStoreException {

    if (node == null) {
      throw new BackingStoreException("node already removed");
    }

    // saveObject node if new
    boolean nodeIsNew = node.isNew();

    if (nodeIsNew) {
      // node is new
      if (parent != null) {
        // set parent node if not root
        node.setParentId(parent.node.getId());    // node must exist (see flush())
        node.setRootNodeId(parent.node.getRootNodeId());
      }
      else {
        // this is the root node
        node.reserveId();
        node.setRootNodeId(node.getId());
      }
      try {
        node.saveObject();
      }
      catch (PersistenceException e) {
        /**
         * Either somebody else already created that node or there really
         * is a db-error.
         */
        throw new BackingStoreException(e);
      }
      // append new node to map
      getFactory().addPreferences(this);
      // update parent to trigger events in other jvms
      updateParentNode();
    }

    // saveObject the keys (if loaded)
    if (keys != null) {
      boolean updateNode = false;
      for (Iterator<DbPreferencesKey> iter=keys.values().iterator(); iter.hasNext(); )  {
        DbPreferencesKey key = iter.next();
        if (key.isDeleted())  {
          // remove it
          key.setId(key.getId());   // set the positive ID first
          try {
            key.deleteObject();
          }
          catch (PersistenceException e) {
            throw new BackingStoreException(e);
          }
          iter.remove();    // remove key physically
          // remove key from map
          getFactory().removeKey(key);
          updateNode = true;    // some key has been deleted: update node to trigger other jvms
        }
        else if (key.isModified()) {
          if (key.isNew())  {
            // some key has been added: update node to trigger other jvms
            updateNode = true;
          }
          try {
            key.saveObject();
          }
          catch (PersistenceException e) {
            /**
             * Either somebody else already created that key or there really
             * is a db-error. We can't update the key if db.isConstraintViolation()
             * because the backend usually terminates the transaction upon
             * such an error.
             */
            throw new BackingStoreException(e);
          }
          // update keymap
          getFactory().addKey(key);
        }
      }
      if (updateNode && !nodeIsNew) {
        // keys have been added or deleted (and node already exists): trigger other jvms modthread
        updateParentNode(node);
      }
    }

    // process child nodes
    for (DbPreferences child: childPrefs.values()) {
      child.flushImpl();
    }
  }




  /**
   * recover this node and all its descendents.
   * Necessary after BackingStoreException in flush().
   * Invoked from within flush().
   *
   * Notice: the listeners are not recovered, i.e. lost
   */
  private void recover()  {

    LOGGER.warning("*** recovering node " + this + " ***");

    // remove the keys for this node
    if (keys != null) {
      for (DbPreferencesKey key: keys.values()) {
        getFactory().removeKey(key);
      }
      keys = null;
    }

    // remove this node from its parent
    if (parent != null) {
      parent.childPrefs.remove(name);
    }

    // remove the node from the global pool
    if (node != null) {
      getFactory().removeNode(node);
    }

    node = null;
    nodeListeners = null;
    prefListeners = null;

    // do that for all childs (need a copy cause of remove() in collection)
    DbPreferences[] childs = new DbPreferences[childPrefs.size()];
    childPrefs.values().toArray(childs);

    for (DbPreferences child: childs)  {
      child.recover();
    }
  }


  /**
   * tokenizer contains <name> {'/' <name>}*
   */
  private DbPreferences node(StringTokenizer path) {

    String token = path.nextToken();

    if (token.equals("/"))  { // Check for consecutive slashes
      throw new IllegalArgumentException("Consecutive slashes in path");
    }

    DbPreferences child = childPrefs.get(token);
    if (child == null) {
      if (token.length() > MAX_NAME_LENGTH) {
        throw new IllegalArgumentException("Node name " + token + " too long");
      }
      child = getFactory().createPreferences(this, token);
      if (child.node.isNew()) {
        enqueueNodeAddedEvent(child);
      }
      childPrefs.put(token, child);
    }
    if (!path.hasMoreTokens()) {
      return child;
    }
    path.nextToken();  // Consume slash
    if (!path.hasMoreTokens()) {
      throw new IllegalArgumentException("Path ends with slash");
    }
    return child.node(path);
  }


  /**
   * Recurive implementation for removeNode.
   * Invoked with locks on all nodes on path from parent of "removal root"
   * to this (including the former but excluding the latter).
   */
  private void removeNodeImpl() throws BackingStoreException {
    if (node != null) {

      loadNodes();    // get all nodes
      loadKeys();     // and keys

      // recursively remove all children
      for (DbPreferences child: childPrefs.values()) {
        child.removeNodeImpl();
      }

      try {
        node.deleteObject();
      }
      catch (PersistenceException e) {
        throw new BackingStoreException(e);
      }

      getFactory().removeNode(node);

      // delete all keys for this node.
      if (!keys.isEmpty()) {
        DbPreferencesKey key = keys.values().iterator().next();   // pick the first
        if (key.isLoggingModification(ModificationLog.DELETE)) {
          // delete key by key
          for (DbPreferencesKey k: keys.values()) {
            k.deleteObject();
          }
        }
        else  {
          key.deleteByNodeId(node.getId());
        }
      }
      unloadKeys();

      keys          = null;
      childPrefs    = null;
      nodeListeners = null;
      prefListeners = null;

      node = null;                        // mark node deleted

      parent.enqueueNodeRemovedEvent(this);
    }
  }


  /**
   * @return true if any node listeners are registered on this node
   */
  public boolean areNodeListenersRegistered()  {
    return nodeListeners != null && nodeListeners.length > 0;
  }


  /**
   * Returns whether reference listeners are registered.
   *
   * @return true if any preferences listeners are registered on this node
   */
  public boolean arePreferenceListenersRegistered()  {
    return prefListeners != null && prefListeners.length > 0;
  }


  /**
   * Returns the string represenation of this node.
   *
   * <ul>
   * <li>if node is deleted: "&lt;absolutePath&gt; [deleted]"</li>
   * <li>user node: "&lt;user&gt;:&lt;absolutePath&gt;"</li>
   * <li>system node: "&lt;system&gt;:&lt;absolutePath&gt;"</li>
   * </ul>
   *
   * @return the string
   */
  @Override
  public String toString() {
    if (node == null) {
      return absolutePath + " [deleted]";
    }
    if (userMode) {
      return node.getUser() + ":" + absolutePath;
    }
    else  {
      return "<system>:" + absolutePath;
    }
  }




  // --------------- implements Preferences --------------------------

  @Override
  public String name() {
    return name;
  }


  @Override
  public String absolutePath() {
    return absolutePath;
  }


  @Override
  public boolean isUserNode() {
    return userMode;
  }


  @Override
  public void put(String key, String value) {

    if (key == null || value == null) {
      throw new NullPointerException();
    }
    if (key.length() > MAX_KEY_LENGTH)  {
      throw new IllegalArgumentException("key too long: <" + key + ">");
    }
    if (value.length() > MAX_VALUE_LENGTH)  {
      throw new IllegalArgumentException("value too long: <" + value + ">");
    }

    synchronized(getFactory()) {
      assertNotRemoved();
      if (!getFactory().isReadOnly()) {
        DbPreferencesKey k = getKey(key);
        if (k == null) {
          // add a new key/value-pair to the node
          k = getFactory().createKey();
          if (!node.isIdValid()) {
            /**
             * node is not saved to disk: delay setting the nodeID
             * by setLazyNode(). See prepareSetFields in DbPreferencesKey.
             */
            k.setLazyNode(node);
          }
          else  {
            k.setNodeId(node.getId());
            k.setRootNodeId(node.getRootNodeId());
          }
          k.setKey(key);
          keys.put(key, k);
        }
        else if (k.isDeleted())  {
          // was marked deleted in remove(): enable it again
          k.setId(-k.getId());
        }
        String oldValue = k.getValue();
        k.setValue(value);
        if (Compare.compare(oldValue, value) != 0)  {
          enqueuePreferenceChangeEvent(key, value);
        }
      }
    }
  }


  @Override
  public String get(String key, String def) {
    if (key == null) {
      throw new NullPointerException("null key");
    }
    synchronized(getFactory()) {
      assertNotRemoved();
      DbPreferencesKey k = getKey(key);
      return k == null || k.isDeleted() || k.getValue() == null ? def : k.getValue();
    }
  }


  @Override
  public void remove(String key) {
    synchronized(getFactory()) {
      assertNotRemoved();
      if (!getFactory().isReadOnly()) {
        DbPreferencesKey k = getKey(key);
        if (k != null)  {
          if (k.isIdValid())  {
            /**
             * We don't delete the key right now, but mark it as "deleted" by
             * negating the ID. Notice that getId() always returns a positive ID,
             * so the following code will always set a negative ID.
             */
            k.setId(-k.getId());
          }
          else {
            // never saved to disk: remove from keys right now
            keys.remove(key);
          }
          // else: already marked deleted
          enqueuePreferenceChangeEvent(key, null);
        }
      }
    }
  }


  @Override
  public void clear() throws BackingStoreException {
    if (!getFactory().isReadOnly())  {
      String[] keyArray = keys();
      for (String keyArray1 : keyArray) {
        remove(keyArray1);
      }
    }
  }


  @Override
  public void sync() throws BackingStoreException {
    flush();  // flush first
    // explicit expiration
    getFactory().expireKeys(getFactory().createKey().getModificationCount());
    getFactory().expireNodes(getFactory().createNode().getModificationCount());
  }


  @Override
  public void flush() throws BackingStoreException {
    if (!getFactory().isReadOnly()) {
      synchronized (getFactory()) {
        // single transaction
        Session session = getFactory().getSession();
        long txVoucher = session.begin(TX_FLUSH);
        try {
          /**
           * go up to the first parent with a new node
           */
          DbPreferences prefs = this;
          while (prefs.parent != null && prefs.parent.node != null && prefs.parent.node.isNew()) {
            prefs = prefs.parent;
          }
          // flush starting at first non-persistent node
          prefs.flushImpl();
          session.commit(txVoucher);
        }
        catch (BackingStoreException ex) {
          session.rollback(txVoucher);   // some severe db-error or unique violation: rollback
          /**
           * recover the node, i.e. load from storage and invalidate all modifications made so far. This is the only way
           * to get a working preferences tree again.
           */
          recover();
          throw ex;
        }
      }
    }
  }


  @Override
  public void putInt(String key, int value) {
    put(key, Integer.toString(value));
  }


  @Override
  public int getInt(String key, int def) {
    int result = def;
    try {
      String value = get(key, null);
      if (value != null) {
        result = Integer.parseInt(value);
      }
    }
    catch (NumberFormatException e) {
      // Ignoring exception causes specified default to be returned
    }
    return result;
  }


  @Override
  public void putLong(String key, long value) {
    put(key, Long.toString(value));
  }


  @Override
  public long getLong(String key, long def) {
    long result = def;
    try {
      String value = get(key, null);
      if (value != null) {
        result = Long.parseLong(value);
      }
    }
    catch (NumberFormatException e) {
      // Ignoring exception causes specified default to be returned
    }
    return result;
  }


  @Override
  public void putBoolean(String key, boolean value) {
    put(key, String.valueOf(value));
  }


  @Override
  public boolean getBoolean(String key, boolean def) {
    boolean result = def;
    String value = get(key, null);
    if (value != null) {
      if (value.equalsIgnoreCase("true")) {
        result = true;
      }
      else if (value.equalsIgnoreCase("false")) {
        result = false;
      }
    }
    return result;
  }


  @Override
  public void putFloat(String key, float value) {
    put(key, Float.toString(value));
  }


  @Override
  public float getFloat(String key, float def) {
    float result = def;
    try {
      String value = get(key, null);
      if (value != null) {
        result = Float.parseFloat(value);
      }
    }
    catch (NumberFormatException e) {
      // Ignoring exception causes specified default to be returned
    }
    return result;
  }


  @Override
  public void putDouble(String key, double value) {
    put(key, Double.toString(value));
  }


  @Override
  public double getDouble(String key, double def) {
    double result = def;
    try {
      String value = get(key, null);
      if (value != null) {
        result = Double.parseDouble(value);
      }
    }
    catch (NumberFormatException e) {
      // Ignoring exception causes specified default to be returned
    }
    return result;
  }


  @Override
  public void putByteArray(String key, byte[] value) {
    put(key, Base64.byteArrayToBase64(value));
  }


  @Override
  public byte[] getByteArray(String key, byte[] def) {
    byte[] result = def;
    String value = get(key, null);
    try {
      if (value != null) {
        result = Base64.base64ToByteArray(value);
      }
    }
    catch (RuntimeException e) {
      // Ignoring exception causes specified default to be returned
    }
    return result;
  }


  @Override
  public String[] keys() throws BackingStoreException {
    synchronized(getFactory()) {
      assertNotRemoved();
      if (keys == null) {
        loadKeys();
      }
      String keyNames[] = new String[keys.size()];
      keys.keySet().toArray(keyNames);
      return keyNames;
    }
  }


  @Override
  public String[] childrenNames() throws BackingStoreException {
    synchronized(getFactory()) {
      assertNotRemoved();
      loadNodes();    // update childCache
      // else: nodes and childCache up to date
      String[] childNames = new String[childPrefs.size()];
      childPrefs.keySet().toArray(childNames);
      return childNames;
    }
  }


  @Override
  public DbPreferences parent() {
    assertNotRemoved();
    return parent;
  }


  @Override
  public DbPreferences node(String path) {
    synchronized(getFactory()) {
      assertNotRemoved();
      if (path.isEmpty()) {
        return this;
      }
      if (path.equals("/")) {
        return root;
      }
      if (path.charAt(0) != '/') {
        // relative path
        return node(new StringTokenizer(path, "/", true));
      }
    }

    // absolute path: start at root node
    return root.node(new StringTokenizer(path.substring(1), "/", true));
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation differs from AbstractPreferences because
   * it checks the persistent storage rather than the cache, i.e.
   * a node only created in memory but not yet written to persistent storage
   * is considered non-existant. As a consequence it is possible to decide
   * whether a system-preference has been overridden by a user-preference.
   * (see CompositePreferences).
   * Notice that "" will return true even if not written to disk to
   * denote "not deleted" (which does not mean "saved" -- a little confusing)
   */
  @Override
  public boolean nodeExists(String pathName) throws BackingStoreException {
    synchronized(getFactory()) {
      if (pathName.isEmpty()) {  // special for ""
        return node != null;
      }
      assertNotRemoved();
      if (pathName.equals("/")) {
        // root
        return true;
      }
      if (pathName.charAt(0) != '/') {
        // relative path: make absolute
        pathName = absolutePath + "/" + pathName;
      }

      // go by absolute path
      DbPreferences pref = getFactory().getPreferences(node.getUser(), pathName);  // check cache first
      if (pref != null && !pref.node.isNew()) {
        return true;  // persistent node exists in cache
      }

      // check if node exists in storage
      return getFactory().createNode().selectByUserAndName(node.getUser(), pathName) != null;
    }
  }


  @Override
  public void removeNode() throws BackingStoreException {
    if (this == root) {
      throw new UnsupportedOperationException("Can't remove the root!");
    }
    if (!getFactory().isReadOnly()) {
      synchronized(getFactory()) {
        assertNotRemoved();
        Session session = getFactory().getSession();
        long txVoucher = session.begin(TX_REMOVE_NODE);
        try {
          updateParentNode(); // update parent to trigger other jvms
          removeNodeImpl();   // remove this node and all subnodes
          parent.childPrefs.remove(name);
          session.commit(txVoucher);
        }
        catch (Exception e) {
          session.rollback(txVoucher);
          if (e instanceof BackingStoreException) {
            throw (BackingStoreException)e;
          }
          throw new BackingStoreException(e);
        }
      }
    }
  }


  /**
   * Registers the specified listener to receive <i>preference change
   * events</i> for this preference node.  A preference change event is
   * generated when a preference is added to this node, removed from this
   * node, or when the value associated with a preference is changed.
   * (Preference change events are <i>not</i> generated by the {@link
   * #removeNode()} method, which generates a <i>node change event</i>.
   * Preference change events <i>are</i> generated by the <tt>clear</tt>
   * method.)
   *
   * <p>Events are generated even for changes made outside this JVM. For the local
   * JVM events are generated before the changes have been made persistent. For all
   * other JVMs events are generated *after* flush()/sync().
   *
   * @param pcl The preference change listener to add.
   * @throws NullPointerException if <tt>pcl</tt> is null.
   * @throws IllegalStateException if this node (or an ancestor) has been
   *         removed with the {@link #removeNode()} method.
   * @see #removePreferenceChangeListener(PreferenceChangeListener)
   * @see #addNodeChangeListener(NodeChangeListener)
   */
  @Override
  public void addPreferenceChangeListener(PreferenceChangeListener pcl) {
    if (pcl == null) {
      throw new NullPointerException("Change listener is null.");
    }
    synchronized(getFactory()) {
      assertNotRemoved();
      if (prefListeners == null) {
        prefListeners = new PreferenceChangeListener[1];
        prefListeners[0] = pcl;
      }
      else {
        PreferenceChangeListener[] old = prefListeners;
        prefListeners = new PreferenceChangeListener[old.length + 1];
        System.arraycopy(old, 0, prefListeners, 0, old.length);
        prefListeners[old.length] = pcl;
      }
      // load keys if not yet done
      if (keys == null) {
        loadKeys();
      }
    }
    startEventDispatchThreadIfNecessary();
  }


  @Override
  public void removePreferenceChangeListener(PreferenceChangeListener pcl) {
    synchronized(getFactory()) {
      assertNotRemoved();
      if ((prefListeners == null) || (prefListeners.length == 0)) {
        throw new IllegalArgumentException("Listener not registered.");
      }
      // Copy-on-write
      PreferenceChangeListener[] newPl = new PreferenceChangeListener[prefListeners.length - 1];
      int i = 0;
      while (i < newPl.length && prefListeners[i] != pcl) {
        newPl[i] = prefListeners[i++];
      }

      if (i == newPl.length && prefListeners[i] != pcl) {
        throw new IllegalArgumentException("Listener not registered.");
      }
      while (i < newPl.length) {
        newPl[i] = prefListeners[++i];
      }
      prefListeners = newPl;
    }
  }


  /**
   * Registers the specified listener to receive <i>node change events</i>
   * for this node.  A node change event is generated when a child node is
   * added to or removed from this node.  (A single {@link #removeNode()}
   * invocation results in multiple <i>node change events</i>, one for every
   * node in the subtree rooted at the removed node.)
   *
   * <p>Events are generated even for changes made outside this JVM. For the local
   * JVM events are generated before the changes have been made persistent. For all
   * other JVMs events are generated *after* flush()/sync().
   *
   * <p>Node creation will always generate an even for the local JVM. Other
   * JVMs get that event only in case the node is created on disk (and not updated
   * in case another JVM already created that node).
   *
   * @param ncl The <tt>NodeChangeListener</tt> to add.
   * @throws NullPointerException if <tt>ncl</tt> is null.
   * @throws IllegalStateException if this node (or an ancestor) has been
   *         removed with the {@link #removeNode()} method.
   * @see #removeNodeChangeListener(NodeChangeListener)
   * @see #addPreferenceChangeListener(PreferenceChangeListener)
   */
  @Override
  public void addNodeChangeListener(NodeChangeListener ncl) {
    if (ncl == null) {
      throw new NullPointerException("Change listener is null.");
    }
    synchronized(getFactory()) {
      assertNotRemoved();
      if (nodeListeners == null) {
        nodeListeners = new NodeChangeListener[1];
        nodeListeners[0] = ncl;
      }
      else {
        NodeChangeListener[] old = nodeListeners;
        nodeListeners = new NodeChangeListener[old.length + 1];
        System.arraycopy(old, 0, nodeListeners, 0, old.length);
        nodeListeners[old.length] = ncl;
      }
    }
    startEventDispatchThreadIfNecessary();
  }


  @Override
  public void removeNodeChangeListener(NodeChangeListener ncl) {
    synchronized(getFactory()) {
      assertNotRemoved();
      if ((nodeListeners == null) || (nodeListeners.length == 0)) {
        throw new IllegalArgumentException("Listener not registered.");
      }
      NodeChangeListener[] newNl = new NodeChangeListener[nodeListeners.length - 1];
      int i = 0;
      while (i < nodeListeners.length && nodeListeners[i] != ncl) {
        newNl[i] = nodeListeners[i++];
      }
      if (i == nodeListeners.length) {
        throw new IllegalArgumentException("Listener not registered.");
      }
      while (i < newNl.length) {
        newNl[i] = nodeListeners[++i];
      }
      nodeListeners = newNl;
    }
  }





  /**
   * Queue of pending notification events.  When a preference or node
   * change event for which there are one or more listeners occurs,
   * it is placed on this queue and the queue is notified.  A background
   * thread waits on this queue and delivers the events.  This decouples
   * event delivery from preference activity, greatly simplifying
   * locking and reducing opportunity for deadlock.
   */
  private static final List<EventObject> EVENT_QUEUE = new LinkedList<>();

  /**
   * These two classes are used to distinguish NodeChangeEvents on
   * eventQueue so the event dispatch thread knows whether to call
   * childAdded or childRemoved.
   */
  @SuppressWarnings("serial")
  private static class NodeAddedEvent extends NodeChangeEvent {

    NodeAddedEvent(Preferences parent, Preferences child) {
      super(parent, child);
    }
  }


  @SuppressWarnings("serial")
  private static class NodeRemovedEvent extends NodeChangeEvent {

    NodeRemovedEvent(Preferences parent, Preferences child) {
      super(parent, child);
    }
  }

  /**
   * A single background thread ("the event notification thread") monitors
   * the event queue and delivers events that are placed on the queue.
   */
  private static class EventDispatchThread extends Thread {

    @Override
    public void run() {
      while(true) {
        // Wait on eventQueue till an event is present
        EventObject event;
        synchronized(EVENT_QUEUE) {
          try {
            while (EVENT_QUEUE.isEmpty())  {
              EVENT_QUEUE.wait();    // wait for notify
            }
            event = EVENT_QUEUE.remove(0);
          }
          catch (InterruptedException e) {
            return;   // terminate thread
          }
        }

        // Now we have event & hold no locks; deliver evt to listeners
        DbPreferences src = (DbPreferences) event.getSource();
        if (event instanceof PreferenceChangeEvent) {
          PreferenceChangeEvent pce = (PreferenceChangeEvent) event;
          PreferenceChangeListener[] listeners = src.prefListeners();
          for (PreferenceChangeListener listener : listeners) {
            listener.preferenceChange(pce);
          }
        }
        else {
          NodeChangeEvent nce = (NodeChangeEvent) event;
          NodeChangeListener[] listeners = src.nodeListeners();
          if (nce instanceof NodeAddedEvent) {
            for (NodeChangeListener listener : listeners) {
              listener.childAdded(nce);
            }
          }
          else {
            for (NodeChangeListener listener : listeners) {
              listener.childRemoved(nce);
            }
          }
        }
      }
    }
  }

  private static Thread eventDispatchThread = null;

  /**
   * This method starts the event dispatch thread the first time it
   * is called.  The event dispatch thread will be started only
   * if someone registers a listener.
   */
  private static synchronized void startEventDispatchThreadIfNecessary() {
    if (eventDispatchThread == null) {
      eventDispatchThread = new EventDispatchThread();
      eventDispatchThread.setDaemon(true);
      eventDispatchThread.start();
    }
  }

  /**
   * Return this node's preference/node change listeners.  Even though
   * we're using a copy-on-write lists, we use synchronized accessors to
   * ensure information transmission from the writing thread to the
   * reading thread.
   *
   * @return the array of listeners
   */
  PreferenceChangeListener[] prefListeners() {
    return prefListeners;
  }

  /**
   * @return the array of node listeners
   */
  NodeChangeListener[] nodeListeners() {
    return nodeListeners;
  }

  /**
   * Enqueue a preference change event for delivery to registered
   * preference change listeners unless there are no registered
   * listeners.  Invoked with this.lock held.
   */
  void enqueuePreferenceChangeEvent(String key, String newValue) {
    if (arePreferenceListenersRegistered()) {
      synchronized(EVENT_QUEUE) {
        EVENT_QUEUE.add(new PreferenceChangeEvent(this, key, newValue));
        EVENT_QUEUE.notifyAll();
      }
    }
  }

  /**
   * Enqueue a "node added" event for delivery to registered node change
   * listeners unless there are no registered listeners.  Invoked with
   * this.lock held.
   */
  void enqueueNodeAddedEvent(Preferences child) {
    if (areNodeListenersRegistered()) {
      synchronized(EVENT_QUEUE) {
        EVENT_QUEUE.add(new NodeAddedEvent(this, child));
        EVENT_QUEUE.notifyAll();
      }
    }
  }

  /**
   * Enqueue a "node removed" event for delivery to registered node change
   * listeners unless there are no registered listeners.  Invoked with
   * this.lock held.
   */
  void enqueueNodeRemovedEvent(Preferences child) {
    if (areNodeListenersRegistered()) {
      synchronized(EVENT_QUEUE) {
        EVENT_QUEUE.add(new NodeRemovedEvent(this, child));
        EVENT_QUEUE.notifyAll();
      }
    }
  }


  @Override
  public void exportNode(OutputStream os) throws IOException, BackingStoreException {
    exportImpl(os, false);
  }


  @Override
  public void exportSubtree(OutputStream os) throws IOException, BackingStoreException {
    exportImpl(os, true);
  }


  /**
   * Exports a preferences node.<br>
   * XmlSupport is package scope.
   * Hence, we must use reflection to invoke the export method.
   *
   * @param os the output stream
   * @param subtree true if whole subtree, false this node only
   */
  private void exportImpl(OutputStream os, boolean subtree) {
    try {
      Method exportMethod = Class.forName("java.util.prefs.XmlSupport").
              getDeclaredMethod("export", OutputStream.class, Preferences.class, Boolean.TYPE);
      exportMethod.setAccessible(true);               // it's package scope!
      exportMethod.invoke(null, os, this, subtree);   // static method
    }
    catch (Exception ex) {
      LOGGER.severe("exporting node failed", ex);
    }
  }

}
