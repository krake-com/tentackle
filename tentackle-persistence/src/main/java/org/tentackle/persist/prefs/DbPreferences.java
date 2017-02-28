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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.prefs.BackingStoreException;
import java.util.prefs.NodeChangeListener;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import org.tentackle.io.Base64;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.pdo.PersistenceException;
import org.tentackle.persist.Db;
import org.tentackle.prefs.PersistedPreferences;
import org.tentackle.prefs.PersistedPreferencesFactory;

/**
 * Persisted preferences implementation.
 * <p>
 * Implementation details:
 * <ul>
 * <li>
 * Node- and key events are delivered to all JVMs, not only the local one.
 * </li>
 * <li>
 * Listeners are fired when the changes are flushed to the backing store
 * and not as soon as a preference is changed or node is added/removed.
 * </li>
 * </ul>
 *
 * @author harald
 */
public class DbPreferences extends Preferences implements PersistedPreferences, Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * The logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DbPreferences.class);

  private final DbPreferences root;
  private final DbPreferences parent;
  private final String absolutePath;
  private final String name;
  private final DbPreferencesNode node;

  private final Map<String,DbPreferences> childMap;     // childs of this node <subname:node>
  private final Map<String,DbPreferencesKey> keyMap;    // keys of this node <key:value>


  public DbPreferences(DbPreferences parent, DbPreferencesNode node,
                       Collection<DbPreferences> childs, Collection<DbPreferencesKey> keys) {

    this.root = parent == null ? this : parent.getRoot();
    this.parent = parent;
    this.node = node;
    this.absolutePath = node.getName();
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
    keyMap = new HashMap<>();
    setChilds(childs);
    setKeys(keys);
  }

  @Override
  public int hashCode() {
    int hash = 3;
    hash = 59 * hash + Objects.hashCode(this.absolutePath);
    hash = 59 * hash + Objects.hashCode(this.node.getUser());
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
    return Objects.equals(this.node.getUser(), other.node.getUser());
  }


  public DbPreferencesFactory getFactory() {
    return (DbPreferencesFactory) PersistedPreferencesFactory.getInstance();
  }

  public DbPreferences getRoot() {
    return root;
  }

  public DbPreferencesNode getNode() {
    return node;
  }

  public void updateNode(DbPreferencesNode node) {
    this.node.setId(node.getId());    // node may be persisted in another JVM meanwhile
    this.node.setSerial(node.getSerial());
    this.node.setTableSerial(node.getTableSerial());
    // the other attributes don't ever change in a node
  }

  public Collection<DbPreferencesKey> getKeys() {
    return keyMap.values();
  }

  public Collection<DbPreferences> getChilds() {
    return childMap.values();
  }

  void setChilds(Collection<DbPreferences> childs) {
    childMap.clear();
    if (childs != null) {
      for (DbPreferences child: childs) {
        putChild(child);
      }
    }
  }

  DbPreferences putChild(DbPreferences child) {
    child.getNode().setRemoved(false);
    return childMap.put(child.name(), child);
  }

  DbPreferences removeChild(String name) {
    DbPreferences child = childMap.remove(name);
    if (child != null) {
      child.node.setRemoved(true);
    }
    return child;
  }

  DbPreferences getChild(String name) {
    return childMap.get(name);
  }

  void setKeys(Collection<DbPreferencesKey> keys) {
    keyMap.clear();
    if (keys != null) {
      for (DbPreferencesKey key: keys) {
        putKey(key);
      }
    }
  }

  DbPreferencesKey putKey(DbPreferencesKey key) {
    key.setRemoved(false);
    return keyMap.put(key.getKey(), key);
  }

  DbPreferencesKey removeKey(String name) {
    DbPreferencesKey removedKey = keyMap.remove(name);
    if (removedKey != null) {
      removedKey.setRemoved(true);
    }
    return removedKey;
  }

  DbPreferencesKey getKey(String name) {
    return keyMap.get(name);
  }


  /**
   *
   * @param key
   * @return null if key added, else the old value
   */
  String updateKey(DbPreferencesKey key) {
    String oldValue = null;
    DbPreferencesKey existingKey = getKey(key.getKey());
    if (existingKey != null) {
      oldValue = existingKey.getValue();
      existingKey.setValue(key.getValue());
      existingKey.setId(key.getId());   // may be persisted in another JVM meanwhile
      existingKey.setSerial(key.getSerial());
      existingKey.setTableSerial(key.getTableSerial());
    }
    else {
      putKey(key);
    }
    return oldValue;
  }


  public String getUser() {
    return node.getUser();
  }


  @Override
  public synchronized void put(String key, String value) {
    DbPreferencesKey prefKey = getKey(key);
    if (prefKey == null) {
      prefKey = new DbPreferencesKey();
      prefKey.setKey(key);
      putKey(prefKey);
    }
    prefKey.setValue(value);
  }

  @Override
  public synchronized String get(String key, String def) {
    DbPreferencesKey prefKey = getKey(key);
    return prefKey == null || prefKey.isRemoved() ? def : prefKey.getValue();
  }

  @Override
  public synchronized void remove(String key) {
    removeKey(key);
  }

  @Override
  public void clear() throws BackingStoreException {
    setKeys(null);
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
  public synchronized String[] keys() throws BackingStoreException {
    return keyMap.keySet().toArray(new String[keyMap.size()]);
  }

  @Override
  public synchronized String[] childrenNames() throws BackingStoreException {
    return childMap.keySet().toArray(new String[childMap.size()]);
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
    while (stok.hasMoreTokens() && !currentPrefs.getNode().isRemoved()) {
      String subName = stok.nextToken();
      childPrefs = currentPrefs.getChild(subName);
      if (childPrefs == null) {
        break;
      }
      currentPrefs = childPrefs;
    }

    return childPrefs != null && !childPrefs.getNode().isRemoved();
  }

  @Override
  public synchronized void removeNode() throws BackingStoreException {
    getNode().setRemoved(true);
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
  public boolean isUserNode() {
    return node.getUser() != null;
  }

  public boolean isRootNode() {
    return "/".equals(absolutePath);
  }

  /**
   * Returns the string represenation of this node.
   *
   * <ul>
   * <li>user node: "user:absolutePath"</li>
   * <li>system node: "&lt;system&gt;:absolutePath"</li>
   * <li>if node is removed: "(removed)" is appended</li>
   * </ul>
   *
   * @return the string
   */
  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    if (node.getUser() != null) {
      buf.append(node.getUser());
    }
    else {
      buf.append("<system>");
    }
    buf.append(':').append(absolutePath);
    if (node.isRemoved()) {
      buf.append(" (removed)");
    }
    return buf.toString();
  }


  @Override
  public void flush() throws BackingStoreException {
    getFactory().flush(this);
  }

  @Override
  public void sync() throws BackingStoreException {
    getFactory().sync(this);
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
      if (currentPrefs.getNode().isRemoved()) {
        currentPrefs.getNode().setRemoved(false);
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
        childPrefs = new DbPreferences(currentPrefs, childNode, null, null);
        currentPrefs.putChild(childPrefs);
      }
      currentPrefs = childPrefs;
    }

    return childPrefs;
  }

  /**
   * Fixes the pathname.
   *
   * @param pathName the given pathname
   * @return the normalized checked name
   */
  protected String fixPathName(String pathName) {
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
   * Exports a preferences node.<br>
   * XmlSupport is package scope.
   * Hence, we must use reflection to invoke the export method.
   *
   * @param os the output stream
   * @param subtree true if whole subtree, false this node only
   */
  protected void exportImpl(OutputStream os, boolean subtree) {
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



  /**
   * Recursive implementation of flush.
   */
  protected void flushImpl(Db db) {
    node.setSession(db);
    try {
      if (node.isRemoved()) {
        if (!node.isNew()) {
          // remove this and all subnodes and keys
          new DbPreferencesKey(db).deleteByNodeId(node.getId());
          for (DbPreferencesKey key: keyMap.values()) {
            key.setRemoved(true);
            getFactory().keyChanged(this, key);
          }
          keyMap.clear();
          for (DbPreferences child: childMap.values()) {
            child.getNode().setRemoved(true);
            child.flushImpl(db);
          }
          childMap.clear();
          node.deleteObject();
          getFactory().nodeChanged(this);
        }
      }
      else {
        if (node.isNew()) {
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
        }
        if (node.isModified()) {
          node.saveObject();
          getFactory().nodeChanged(this);
        }

        for (DbPreferencesKey key: keyMap.values()) {
          key.setSession(db);
          try {
            if (key.isRemoved()) {
              if (!key.isNew()) {
                key.deleteObject();
                getFactory().keyChanged(this, key);
              }
            }
            else if (key.isModified()) {
              if (key.isNew()) {
                key.setNodeId(node.getId());
                key.setRootNodeId(node.getRootNodeId());
              }
              key.saveObject();
              getFactory().keyChanged(this, key);
            }
          }
          finally {
            key.setSession(null);
          }
        }

        for (DbPreferences child: childMap.values()) {
          child.flushImpl(db);
        }
      }
    }
    finally {
      node.setSession(null);
    }
  }

}
