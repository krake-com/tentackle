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

import org.tentackle.dbms.Db;
import org.tentackle.log.Logger;
import org.tentackle.prefs.PersistedPreferences;
import org.tentackle.prefs.PersistedPreferencesFactory;
import org.tentackle.session.ConstraintException;
import org.tentackle.session.NotFoundException;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.SavepointHandle;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.prefs.BackingStoreException;
import java.util.prefs.NodeChangeListener;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

/**
 * Persisted preferences implementation.
 * <p>
 * The DbPreferences are an extension of the default Java {@link Preferences}. The main difference
 * is that the DbPreferences use the database as the backing store. The nodes are persisted
 * via {@link DbPreferencesNode} while the key/value-pairs are persisted via {@link DbPreferencesKey}.
 * <p>
 * Implementation details:
 * <ul>
 * <li>
 * Node- and key events are delivered to all JVMs, not only the local one.
 * </li>
 * <li>
 * Listeners are fired when the changes are flushed to the backing store
 * and not as soon as a preference is changed or the node is added/removed.
 * </li>
 * </ul>
 *
 * @author harald
 */
public class DbPreferences extends Preferences implements PersistedPreferences, Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  private static final Logger LOGGER = Logger.get(DbPreferences.class);

  private final DbPreferences root;                     // the root of this node ("this" if this is a root)
  private final DbPreferences parent;                   // the parent node, null if this is a root
  private final String absolutePath;                    // the absolute path
  private final String name;                            // the name relative to the parent
  private final DbPreferencesNode prefNode;             // the persistent node
  @SuppressWarnings("serial")
  private final Map<String,DbPreferences> childMap;     // children of this node <subname:node>
  @SuppressWarnings("serial")
  private final Map<String,DbPreferencesKey> keyMap;    // keys of this node <key:value>


  /**
   * Creates a preferences node.
   *
   * @param parent the parent node, null if this is a root node
   * @param prefNode the persistent node
   * @param children the child preferences nodes
   * @param keys the persistent keys
   */
  public DbPreferences(DbPreferences parent, DbPreferencesNode prefNode,
                       Collection<DbPreferences> children, Collection<DbPreferencesKey> keys) {

    this.root = parent == null ? this : parent.getRoot();
    this.parent = parent;
    this.prefNode = prefNode;
    this.absolutePath = prefNode.getName();
    if ("/".equals(this.absolutePath)) {
      this.name = this.absolutePath;    // root node
    }
    else {
      int ndx = this.absolutePath.lastIndexOf('/');
      if (ndx >= 0) {
        this.name = this.absolutePath.substring(ndx + 1);
      }
      else {
        throw new PersistenceException("illegal path " + this.absolutePath);
      }
    }

    childMap = new HashMap<>();
    if (children != null) {
      for (DbPreferences child : children) {
        addChild(child);
      }
    }

    keyMap = new HashMap<>();
    if (keys != null) {
      for (DbPreferencesKey key: keys) {
        putPersistentKey(key);
      }
    }
  }

  /**
   * Creates an empty preferences-node.<br>
   * The constructor is provided for TRIP.
   * <p>
   * We cannot use {@link org.tentackle.reflect.CanonicalConstructor} since
   * components of this object contain back-references to itself.
   */
  public DbPreferences() {
    this.root = null;
    this.parent = null;
    this.absolutePath = null;
    this.name = null;
    this.prefNode = null;
    this.childMap = null;
    this.keyMap = null;
  }


  @Override
  public int hashCode() {
    int hash = 3;
    hash = 59 * hash + Objects.hashCode(this.absolutePath);
    hash = 59 * hash + Objects.hashCode(this.prefNode.getUser());
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
    final DbPreferences other = (DbPreferences) obj;
    if (!Objects.equals(this.absolutePath, other.absolutePath)) {
      return false;
    }
    return Objects.equals(this.prefNode.getUser(), other.prefNode.getUser());
  }

  /**
   * Returns the string representation of this node.
   *
   * <ul>
   * <li>user node: "user:absolutePath"</li>
   * <li>system node: "&lt;system&gt;:absolutePath"</li>
   * <li>if the node is removed: "(removed)" is appended</li>
   * </ul>
   *
   * @return the string
   */
  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    if (prefNode.getUser() != null) {
      buf.append(prefNode.getUser());
    }
    else {
      buf.append("<system>");
    }
    buf.append(':').append(absolutePath);
    if (prefNode.isRemoved()) {
      buf.append(" (removed)");
    }
    return buf.toString();
  }

  @Override
  public synchronized void put(String key, String value) {
    DbPreferencesKey prefKey = getPersistentKey(key);
    if (prefKey == null) {
      prefKey = new DbPreferencesKey();
      prefKey.setKey(key);
      putPersistentKey(prefKey);
    }
    else if (prefKey.isRemoved()) {
      prefKey.setRemoved(false);
      prefKey.setModified(true);    // in case the value didn't change
    }
    prefKey.setValue(value);
  }

  @Override
  public synchronized String get(String key, String def) {
    DbPreferencesKey prefKey = getPersistentKey(key);
    return prefKey == null || prefKey.isRemoved() ? def : prefKey.getValue();
  }

  @Override
  public synchronized void remove(String key) {
    DbPreferencesKey prefKey = getPersistentKey(key);
    if (prefKey != null) {
      prefKey.setRemoved(true);
    }
  }

  @Override
  public void clear() throws BackingStoreException {
    keyMap.clear();
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
    put(key, Base64.getEncoder().encodeToString(value));
  }

  @Override
  public byte[] getByteArray(String key, byte[] def) {
    byte[] result = def;
    String value = get(key, null);
    try {
      if (value != null) {
        result = Base64.getDecoder().decode(value);
      }
    }
    catch (RuntimeException e) {
      // Ignoring exception causes specified default to be returned
    }
    return result;
  }

  @Override
  public synchronized String[] keys() throws BackingStoreException {
    return keyMap.values().stream().filter(k -> !k.isRemoved())
           .map(DbPreferencesKey::getKey).toArray(String[]::new);
  }

  @Override
  public synchronized String[] childrenNames() throws BackingStoreException {
    return childMap.values().stream().filter(c -> !c.getPersistentNode().isRemoved())
           .map(DbPreferences::name).toArray(String[]::new);
  }

  @Override
  public synchronized boolean nodeExists(String pathName) throws BackingStoreException {

    pathName = fixPathName(pathName);

    DbPreferences currentPrefs = this;
    if (pathName.startsWith("/")) {
      currentPrefs = getRoot();
    }

    DbPreferences childPrefs = currentPrefs;
    StringTokenizer stok = new StringTokenizer(pathName, "/");
    while (stok.hasMoreTokens() && !currentPrefs.getPersistentNode().isRemoved()) {
      String subName = stok.nextToken();
      childPrefs = currentPrefs.getChild(subName);
      if (childPrefs == null) {
        break;
      }
      currentPrefs = childPrefs;
    }

    return childPrefs != null && !childPrefs.getPersistentNode().isRemoved();
  }

  @Override
  public synchronized void removeNode() throws BackingStoreException {
    getPersistentNode().setRemoved(true);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String absolutePath() {
    return absolutePath;
  }

  @Override
  public void flush() throws BackingStoreException {
    getFactory().flush(this, false);
  }

  @Override
  public void sync() throws BackingStoreException {
    getFactory().flush(this, true);
  }

  @Override
  public void addPreferenceChangeListener(PreferenceChangeListener pcl) {
    getFactory().addPreferenceChangeListener(this, pcl);
  }

  @Override
  public void removePreferenceChangeListener(PreferenceChangeListener pcl) {
    getFactory().removePreferenceChangeListener(this, pcl);
  }

  @Override
  public void addNodeChangeListener(NodeChangeListener ncl) {
    getFactory().addNodeChangeListener(this, ncl);
  }

  @Override
  public void removeNodeChangeListener(NodeChangeListener ncl) {
    getFactory().removeNodeChangeListener(this, ncl);
  }

  @Override
  public void exportNode(OutputStream os) throws IOException, BackingStoreException {
    exportImpl(os, false);
  }

  @Override
  public void exportSubtree(OutputStream os) throws IOException, BackingStoreException {
    exportImpl(os, true);
  }

  @Override
  public DbPreferences parent() {
    return parent;
  }

  @Override
  public synchronized DbPreferences node(String pathName) {

    pathName = fixPathName(pathName);

    DbPreferences currentPrefs = this;
    if (pathName.startsWith("/")) {
      currentPrefs = getRoot();
    }

    DbPreferences childPrefs = currentPrefs;
    StringTokenizer stok = new StringTokenizer(pathName, "/");
    while (stok.hasMoreTokens()) {
      if (currentPrefs.getPersistentNode().isRemoved()) {
        currentPrefs.getPersistentNode().setRemoved(false);
      }
      String subName = stok.nextToken();
      childPrefs = currentPrefs.getChild(subName);
      if (childPrefs == null) {
        DbPreferencesNode childNode = new DbPreferencesNode();
        if (currentPrefs.isRootNode()) {
          childNode.setName(currentPrefs.absolutePath() + subName);
        }
        else {
          childNode.setName(currentPrefs.absolutePath() + "/" + subName);
        }
        childNode.setUser(currentPrefs.getUser());
        childPrefs = new DbPreferences(currentPrefs, childNode, null, null);
        currentPrefs.addChild(childPrefs);
      }
      currentPrefs = childPrefs;
    }

    return childPrefs;
  }

  @Override
  public boolean isUserNode() {
    return prefNode.getUser() != null;
  }

  /**
   * Gets the root of this node.<br>
   * If this is a root, "this" will be returned.
   *
   * @return the root node, never null
   */
  public DbPreferences getRoot() {
    return root;
  }

  /**
   * Gets the persistent node.
   *
   * @return the persistent node, never null
   */
  public DbPreferencesNode getPersistentNode() {
    return prefNode;
  }

  /**
   * Updates the persistent node.
   *
   * @param prefNode the persistent node
   * @return true if persistence status changed
   */
  public boolean updatePersistentNode(DbPreferencesNode prefNode) {
    boolean notPersisted = this.prefNode.isNew();
    this.prefNode.setId(prefNode.getId());    // node may be persisted in another JVM meanwhile
    this.prefNode.setSerial(prefNode.getSerial());
    this.prefNode.setTableSerial(prefNode.getTableSerial());
    this.prefNode.setRemoved(prefNode.isRemoved());
    this.prefNode.setModified(false);
    this.prefNode.setParentId(prefNode.getParentId());
    this.prefNode.setRootNodeId(prefNode.getRootNodeId());
    // the other attributes don't ever change in a node
    return notPersisted != prefNode.isNew();
  }

  /**
   * Gets the persistent keys.
   *
   * @return the keys, never null
   */
  public Collection<DbPreferencesKey> getKeys() {
    return keyMap.values();
  }

  /**
   * Gets the child nodes.
   *
   * @return the child nodes, never null
   */
  public Collection<DbPreferences> getChildren() {
    return childMap.values();
  }

  /**
   * Adds a child node.
   *
   * @param child the child node
   */
  public void addChild(DbPreferences child) {
    child.getPersistentNode().setRemoved(false);
    childMap.put(child.name(), child);
  }

  /**
   * Removes a child node.
   *
   * @param name the name relative to this node
   * @return the removed child, null if no such name
   */
  public DbPreferences removeChild(String name) {
    DbPreferences child = childMap.remove(name);
    if (child != null) {
      child.prefNode.setRemoved(true);
    }
    return child;
  }

  /**
   * Gets the child with the given relative name.
   *
   * @param name the name relative to this node
   * @return the child, null if no such name
   */
  public DbPreferences getChild(String name) {
    return childMap.get(name);
  }

  /**
   * Adds a key.
   *
   * @param key the key
   * @return the replaced key, null if the key was new
   */
  public DbPreferencesKey putPersistentKey(DbPreferencesKey key) {
    key.setRemoved(false);
    return keyMap.put(key.getKey(), key);
  }

  /**
   * Removes a key.
   *
   * @param name the key name
   * @return the removed persistent key, null if no such key
   */
  public DbPreferencesKey removePersistentKey(String name) {
    DbPreferencesKey removedKey = keyMap.remove(name);
    if (removedKey != null) {
      removedKey.setRemoved(true);
    }
    return removedKey;
  }

  /**
   * Gets the persistent key.
   *
   * @param name the name of the key
   * @return the persistent key, null if no such name
   */
  public DbPreferencesKey getPersistentKey(String name) {
    return keyMap.get(name);
  }

  /**
   * Updates the persistent key.
   *
   * @param key the key
   * @return true if persistence status changed
   */
  public boolean updatePersistentKey(DbPreferencesKey key) {
    long oldSerial = 0;
    DbPreferencesKey existingKey = getPersistentKey(key.getKey());
    if (existingKey != null) {
      oldSerial = existingKey.getSerial();
      existingKey.setSerial(key.getSerial());
      existingKey.setId(key.getId());   // may be persisted in another JVM meanwhile
      existingKey.setTableSerial(key.getTableSerial());
      existingKey.setValue(key.getValue());
      existingKey.setRemoved(key.isRemoved());
      existingKey.setNodeId(key.getNodeId());
      existingKey.setRootNodeId(key.getRootNodeId());
      existingKey.setModified(false);
    }
    else {
      key.setModified(false);
      putPersistentKey(key);
    }
    return key.getSerial() != oldSerial;
  }

  /**
   * Gets the username.
   *
   * @return the username, null if system user
   */
  public String getUser() {
    return prefNode.getUser();
  }

  /**
   * Returns whether this is a root node.
   *
   * @return true if root
   */
  public boolean isRootNode() {
    return "/".equals(absolutePath);
  }


  /**
   * Exports a preferences node.<br>
   * {@code java.util.prefs.XmlSupport} is package-scope.
   * Hence, we must use reflection to invoke the export method.
   *
   * @param os the output stream
   * @param subtree true for the whole subtree, false this node only
   */
  protected void exportImpl(OutputStream os, boolean subtree) {
    try {
      // workaround since that wouldn't work with jigsaw as it needs explicit --add-opens
      Method exportMethod = Class.forName("java.util.prefs.XmlSupport").
              getDeclaredMethod("export", OutputStream.class, Preferences.class, Boolean.TYPE);
      exportMethod.setAccessible(true);               // it's package scope!
      exportMethod.invoke(null, os, this, subtree);   // static method
    }
    catch (ClassNotFoundException | InaccessibleObjectException | IllegalAccessException |
           IllegalArgumentException | NoSuchMethodException | InvocationTargetException ex) {
      LOGGER.severe("exporting node failed", ex);
    }
  }

  /**
   * Recursive implementation of {@link #flush()}.
   *
   * @param db the session
   * @param triggerListeners true if trigger listeners
   * @return true if some nodes or keys were updated from storage
   */
  protected boolean flushImpl(Db db, boolean triggerListeners) {
    boolean updated = false;
    prefNode.setSession(db);
    try {
      if (prefNode.isRemoved()) {
        if (!prefNode.isNew()) {
          // remove this and all sub-nodes and keys
          for (DbPreferencesKey prefKey: keyMap.values()) {
            prefKey.setRemoved(true);
            if (!prefKey.isNew()) {
              try {
                prefKey.setSession(db);
                try {
                  prefKey.deleteObject();
                }
                catch (NotFoundException nfx) {
                  // already deleted -> no error
                }
              }
              finally {
                prefKey.setSession(null);
              }
            }
            if (triggerListeners) {
              getFactory().keyChanged(this, prefKey);
            }
          }
          keyMap.clear();
          for (DbPreferences child: childMap.values()) {
            child.getPersistentNode().setRemoved(true);
            child.flushImpl(db, triggerListeners);
          }
          childMap.clear();
          try {
            prefNode.deleteObject();
          }
          catch (NotFoundException nfx) {
            // already deleted -> no error
          }
          if (triggerListeners) {
            getFactory().nodeChanged(this);
          }
        }
      }
      else {
        if (prefNode.isNew()) {
          // node is new
          if (parent != null) {
            // set parent node if not root
            prefNode.setParentId(parent.prefNode.getId());    // node must exist (see flush())
            prefNode.setRootNodeId(parent.prefNode.getRootNodeId());
          }
          else {
            // this is the root node
            prefNode.reserveId();
            prefNode.setRootNodeId(prefNode.getId());
          }
        }
        if (prefNode.isModified()) {
          SavepointHandle sh = db.setSavepoint();
          try {
            prefNode.saveObject();
            db.releaseSavepoint(sh);
          }
          catch (ConstraintException | NotFoundException cx) {
            // already persisted -> ignore
            db.rollback(sh);
            // load from backend
            DbPreferencesNode updatedNode = new DbPreferencesNode(db).selectByUserAndName(prefNode.getUser(), prefNode.getName());
            if (updatedNode == null) {
              // node removed in the meantime
              prefNode.setRemoved(true);
              return flushImpl(db, triggerListeners);    // start over
            }
            updatePersistentNode(updatedNode);
            // don't treat as updated if node just added meanwhile
          }
          if (triggerListeners) {
            getFactory().nodeChanged(this);
          }
        }

        for (Iterator<Map.Entry<String,DbPreferencesKey>> iter = keyMap.entrySet().iterator();
             iter.hasNext(); ) {
          DbPreferencesKey prefKey = iter.next().getValue();
          prefKey.setSession(db);
          try {
            if (prefKey.isRemoved()) {
              if (!prefKey.isNew()) {
                try {
                  prefKey.deleteObject();
                }
                catch (NotFoundException nfx) {
                  // already deleted -> no error
                }
                if (triggerListeners) {
                  getFactory().keyChanged(this, prefKey);
                }
              }
              iter.remove();
            }
            else if (prefKey.isModified()) {
              if (prefKey.isNew()) {
                prefKey.setNodeId(prefNode.getId());
                prefKey.setRootNodeId(prefNode.getRootNodeId());
              }
              SavepointHandle sh = db.setSavepoint();
              try {
                prefKey.saveObject();
                db.releaseSavepoint(sh);
              }
              catch (ConstraintException | NotFoundException cx) {
                db.rollback(sh);    // already persisted
                DbPreferencesKey updatedKey = new DbPreferencesKey(db).selectByNodeIdAndKey(prefNode.getId(), prefKey.getKey());
                if (updatedKey == null) {
                  // key removed in the meantime
                  prefKey.setRemoved(true);
                  iter.remove();
                  updated = true;
                }
                else {
                  updated |= updatePersistentKey(updatedKey);
                }
              }
              if (triggerListeners) {
                getFactory().keyChanged(this, prefKey);
              }
            }
          }
          finally {
            prefKey.setSession(null);
          }
        }

        for (Iterator<Map.Entry<String,DbPreferences>> iter = childMap.entrySet().iterator();
             iter.hasNext(); ) {
          DbPreferences child = iter.next().getValue();
          updated |= child.flushImpl(db, triggerListeners);
          if (child.getPersistentNode().isRemoved()) {
            iter.remove();
          }
        }
      }
      return updated;
    }
    finally {
      prefNode.setSession(null);
    }
  }


  /**
   * Fixes the pathname.
   *
   * @param pathName the given pathname
   * @return the normalized checked name
   */
  private String fixPathName(String pathName) {
    if (pathName == null) {
      pathName = "";
    }

    StringBuilder buf = new StringBuilder();
    boolean lastWasSlash = false;
    for (int i=0; i < pathName.length(); i++) {
      char c = pathName.charAt(i);
      if (!Character.isWhitespace(c)) {
        if (c == '/') {
          if (lastWasSlash) {
            continue;
          }
          lastWasSlash = true;
        }
        else {
          lastWasSlash = false;
        }
        buf.append(c);
      }
    }
    if (buf.length() > 1 && buf.charAt(buf.length() - 1) == '/') {
      // remove trailing slash if not root
      buf.setLength(buf.length() - 1);
    }
    return buf.toString();
  }

  /**
   * Gets the DbPreferencesFactory singleton.
   *
   * @return the factory singleton, never null
   */
  private DbPreferencesFactory getFactory() {
    return (DbPreferencesFactory) PersistedPreferencesFactory.getInstance();
  }

}
