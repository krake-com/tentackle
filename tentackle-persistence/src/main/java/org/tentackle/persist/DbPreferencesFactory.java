/**
 * Tentackle - http://www.tentackle.org
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
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.prefs.BackingStoreException;
import java.util.prefs.InvalidPreferencesFormatException;
import org.tentackle.common.Compare;
import org.tentackle.common.Service;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.pdo.DefaultDomainContext;
import org.tentackle.pdo.PersistenceException;
import org.tentackle.pdo.Session;
import org.tentackle.pdo.SessionHolder;
import org.tentackle.prefs.PersistedPreferences;
import org.tentackle.prefs.PersistedPreferencesFactory;
import org.tentackle.prefs.PersistedPreferencesXmlSupport;




/**
 * Implementation of a PersistedPreferencesFactory.
 */
@Service(PersistedPreferencesFactory.class)
public class DbPreferencesFactory implements PersistedPreferencesFactory {

  /**
   * the logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DbPreferencesFactory.class);





  // key for preNameMap
  private static class NodeIndex implements Comparable<NodeIndex> {

    private final String user;
    private final String name;

    private NodeIndex(String user, String name) {
      this.user = user;
      this.name = name;
    }

    private NodeIndex(DbPreferencesNode node)  {
      this.user = node.getUser();
      this.name = node.getName();
    }

    @Override
    public int compareTo(NodeIndex o) {
      int rv = Compare.compare(user, o.user);
      if (rv == 0)  {
        rv = Compare.compare(name, o.name);
      }
      return rv;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof NodeIndex && compareTo((NodeIndex)obj) == 0;
    }

    @Override
    public int hashCode() {
      int hash = 5;
      hash = 97 * hash + (user != null ? user.hashCode() : 0);
      hash = 97 * hash + (name != null ? name.hashCode() : 0);
      return hash;
    }
  }




  private final Map<Long,DbPreferences>       prefIdMap;      // for expiration processing: id map of nodes
  private final Map<Long,DbPreferencesKey>    keyIdMap;       // for expiration processing: id map of keys
  private final Map<NodeIndex,DbPreferences>  prefNameMap;    // dto. indexed by user + name
  private final SessionHolder                 sessionHolder;  // thread-local session holder

  private DbPreferences systemRoot;         // the system root
  private DbPreferences userRoot;           // the user root
  private boolean autoSync;                 // autosync flag
  private boolean readOnly;                 // readonly flag
  private boolean systemOnly;               // systemonly flag
  private long nodeTableSerial;             // highest tableSerial of _all_ DbPreferencesNodes
  private long keyTableSerial;              // highest tableSerial of _all_ DbPreferencesKeys



  /**
   * Creates the factory.
   */
  public DbPreferencesFactory() {
    autoSync = true;
    prefIdMap = new TreeMap<>();
    keyIdMap = new TreeMap<>();
    prefNameMap = new TreeMap<>();
    // preset tableserials
    sessionHolder = new DefaultDomainContext();
    nodeTableSerial = createNode().getModificationCount();
    keyTableSerial = createKey().getModificationCount();
  }

  /**
   * Gets the session for a persistence operation.
   *
   * @return the session
   */
  public Session getSession() {
    return sessionHolder.getSession();
  }

  /**
   * Updates the highest node table serial.
   *
   * @param newTableSerial the new serial
   */
  public void updateNodeTableSerial(long newTableSerial) {
    if (newTableSerial > nodeTableSerial)  {
      nodeTableSerial = newTableSerial;
    }
  }

  /**
   * Updates the highest keys table serial.
   *
   * @param newTableSerial the new serial
   */
  public void updateKeyTableSerial(long newTableSerial) {
    if (newTableSerial > keyTableSerial)  {
      keyTableSerial = newTableSerial;
    }
  }

  /**
   * Adds a preferences node the maps.
   *
   * @param prefs the preferences node
   */
  public void addPreferences(DbPreferences prefs) {
    if (!prefs.getNode().isNew()) {
      updateNodeTableSerial(prefs.getNode().getTableSerial());
      prefIdMap.put(prefs.getNode().getId(), prefs);
    }
    prefNameMap.put(new NodeIndex(prefs.getNode()), prefs);
  }

  /**
   * Removes a node.
   *
   * @param node the node
   */
  public void removeNode(DbPreferencesNode node) {
    prefIdMap.remove(node.getId());
    prefNameMap.remove(new NodeIndex(node));
  }

  /**
   * Gets the cached preferences by node id.
   *
   * @param nodeId the node id
   * @return the preferences, null if not in cache
   */
  public DbPreferences getPreferences(long nodeId) {
    return prefIdMap.get(nodeId);
  }

  /**
   * Gets the cached preferences by user and node path.
   *
   * @param user the user
   * @param path the node path
   * @return the preferences, null if not in cache
   */
  public DbPreferences getPreferences(String user, String path) {
    return prefNameMap.get(new NodeIndex(user, path));
  }

  /**
   * Adds a key/value pair.
   *
   * @param key the key
   */
  public void addKey(DbPreferencesKey key) {
    if (!key.isNew()) {
      updateKeyTableSerial(key.getTableSerial());
      keyIdMap.put(key.getId(), key);
    }
  }

  /**
   * Removes a key.
   *
   * @param key the key
   */
  public void removeKey(DbPreferencesKey key) {
    keyIdMap.remove(key.getId());
  }


  /**
   * Expire the preferences keys.<br>
   * Invoked from the ModificationTracker (registered in DbPreferencesKey)
   *
   * @param maxSerial is the current tableserial
   */
  public void expireKeys(long maxSerial) {
    // get tableserial/id-pairs
    List<IdSerialTuple> expiredList = createKey().getExpiredTableSerials(keyTableSerial, maxSerial);

    for (IdSerialTuple idSer: expiredList) {
      // process only keys that are modified by other jvms and in our keyMap
      DbPreferencesKey key = keyIdMap.get(idSer.getId());
      if (key != null && key.getTableSerial() < idSer.getSerial())  {
        // update value, if not same jvm (the rest cannot change!)
        DbPreferencesKey nkey = createKey().selectObject(idSer.getId());
        // update key value and status info
        key.setValue(nkey.getValue());
        key.setSerial(nkey.getSerial());
        key.setTableSerial(nkey.getTableSerial());
        // fire listeners if any for this node
        DbPreferences pref = prefIdMap.get(key.getNodeId());
        if (pref != null) {
          LOGGER.fine("key updated in {0}: {1}", pref, key);
          pref.enqueuePreferenceChangeEvent(key.getKey(), key.getValue());
        }
      }
    }
    keyTableSerial = maxSerial;
  }


  /**
   * Expires the preferences nodes.<br>
   * Invoked from the ModificationTracker (registered in DbPreferencesNode).
   *
   * @param maxSerial is the current tableserial
   */
  public void expireNodes(long maxSerial)  {
    // get tableserial/id-pairs
    List<IdSerialTuple> expireSet = createNode().getExpiredTableSerials(nodeTableSerial, maxSerial);

    // determine whether some nodes have been removed or not
    // see ModificationCounter why this trick works ;-)
    boolean someNodesRemoved = false;
    long lastSerial = -1;
    for (IdSerialTuple idSer: expireSet) {
      if (lastSerial != -1 && idSer.getSerial() - lastSerial > 1) {
        // gap in tableSerials: delete() invoked at least once
        someNodesRemoved = true;
        break;
      }
      lastSerial = idSer.getSerial();
    }
    if (maxSerial - lastSerial > 1) {
      someNodesRemoved = true;  // lastserial == -1 (no expired nodes found) or gap at end
    }

    if (someNodesRemoved) {
      LOGGER.fine("some nodes have been removed");
    }

    for (IdSerialTuple idSer: expireSet) {
      // load the node
      DbPreferencesNode expiredNode = createNode().selectObject(idSer.getId());

      DbPreferences pref = prefIdMap.get(idSer.getId());    // check if node is already loaded

      if (pref == null) {
        // node is not known so far: possibly it does not have an ID yet, so giv'em an ID if new
        pref = prefNameMap.get(new NodeIndex(expiredNode));
        if (pref != null && pref.getNode().isNew()) {
          pref.getNode().setId(expiredNode.getId());   // serial and tableserial will be updated below
          pref.getNode().setParentId(expiredNode.getParentId());
          prefIdMap.put(pref.getNode().getId(), pref);
          if (pref.getParent() != null) {
            pref.getParent().getChildIds().add(expiredNode.getId());
          }
          LOGGER.fine("assigned ID(s) to node {0}", pref.getNode());
        }
      }

      if (pref != null && pref.getNode() != null) {
        if (pref.getNode().getTableSerial() < idSer.getSerial())  {
          // check for added/removed keys
          if (pref.getKeys() != null)  {  // keys != null if preflisteners registered
            List<DbPreferencesKey> keyList = createKey().selectByNodeId(idSer.getId());
            // check for added keys
            for (DbPreferencesKey key: keyList) {
              if (!pref.getKeys().containsKey(key.getKey()))  {
                // key is new: add to keys
                pref.getKeys().put(key.getKey(), key);   // add to node key list
                keyIdMap.put(key.getId(), key);     // add to key-cache
                pref.enqueuePreferenceChangeEvent(key.getKey(), key.getValue());
                LOGGER.fine("key added to node {0}: {1}", pref.getNode(), key);
              }
            }
            // check for removed keys
            for (Iterator<DbPreferencesKey> iter=pref.getKeys().values().iterator(); iter.hasNext(); ) {
              DbPreferencesKey key = iter.next();
              if (!keyList.contains(key)) {
                // key removed
                iter.remove();                  // this will also remove from the map
                keyIdMap.remove(key.getId());   // remove from global keymap too
                pref.enqueuePreferenceChangeEvent(key.getKey(), null);
                LOGGER.fine("key removed from node {0}: {1}", pref.getNode(), key);
              }
            }
          }
        }

        if (expiredNode != null) {
          // update serial and tableserial
          pref.getNode().setSerial(expiredNode.getSerial());
          pref.getNode().setTableSerial(expiredNode.getTableSerial());
        }

        /**
         * Added nodes only matter if there's a nodelistener registered on the parent.
         * Removed nodes matter if there's a nodelistener on the parent _or_ some nodes
         * have been deleted (that are already loaded)
         */
        if (someNodesRemoved || pref.areNodeListenersRegistered())  {
          // get child nodes to check for added or deleted nodes
          List<DbPreferencesNode> currentNodes = createNode().selectByParentId(pref.getNode().getId());
          if (pref.areNodeListenersRegistered()) {
            // check for added nodes
            for (DbPreferencesNode node: currentNodes)  {
              if (!pref.getChildIds().contains(node.getId()))  {
                try {
                  // node has been added
                  DbPreferences child = createPreferences(pref, node);
                  prefIdMap.put(node.getId(), child);
                  prefNameMap.put(new NodeIndex(node), child);
                  pref.getChildPrefs().put(child.getName(), child);
                  pref.getChildIds().add(node.getId());
                  pref.enqueueNodeAddedEvent(child);
                  LOGGER.fine("node {0} added to {1}", child, pref);
                }
                catch (BackingStoreException ex) {
                  throw new PersistenceException(sessionHolder.getSession(), "loading added node " + node + " failed", ex);
                }
              }
            }
          }
          if (someNodesRemoved) {
            // check for deleted nodes
            for (Long id: pref.getChildIds())  {
              boolean removed = true;
              for (DbPreferencesNode node: currentNodes)  {
                if (id == node.getId()) {
                  removed = false;
                  break;
                }
              }
              if (removed)  {
                try {
                  DbPreferences child = prefIdMap.get(id);    // must exist
                  child.removeNode();
                  pref.enqueueNodeRemovedEvent(child);
                  LOGGER.fine("node {0} removed from {1}", child, pref);
                }
                catch (BackingStoreException ex) {
                  throw new PersistenceException(sessionHolder.getSession(), "removing deleted node " + pref.getNode() + " failed", ex);
                }
              }
            }
          }
        }
      }
    }

    nodeTableSerial = maxSerial;    // next check starts at maxSerial
  }



  @Override
  public DbPreferences userRoot() {
    if (userRoot == null) {
      userRoot = createPreferences(true);
    }
    return userRoot;
  }

  @Override
  public PersistedPreferences getUserRoot() {
    return userRoot();
  }

  @Override
  public DbPreferences systemRoot() {
    if (systemRoot == null) {
      systemRoot = createPreferences(false);
    }
    return systemRoot;
  }

  @Override
  public PersistedPreferences getSystemRoot() {
    return systemRoot();
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
  public DbPreferences userNodeForPackage(Class<?> c) {
    return userRoot().node(nodeName(c));
  }

  @Override
  public DbPreferences systemNodeForPackage(Class<?> c) {
    return systemRoot().node(nodeName(c));
  }

  @Override
  public void importPreferences(InputStream is) throws IOException, InvalidPreferencesFormatException {
    PersistedPreferencesXmlSupport.importPreferences(is);
  }


  /**
   * Creates a DbPreferences instance.
   *
   * @param userMode true if user mode
   * @return the preferences instance
   */
  public DbPreferences createPreferences(boolean userMode) {
    return new DbPreferences(userMode);
  }

  /**
   * Creates a DbPreferences instance from a parent and a name.
   *
   * @param parent the parent preferences node
   * @param name the name of the child node
   * @return the preferences instance
   */
  public DbPreferences createPreferences(DbPreferences parent, String name) {
    return new DbPreferences(parent, name);
  }

  /**
   * Creates a DbPreferences instance from a parent and a node.
   * Protected scope!
   *
   * @param parent the parent preferences
   * @param node the node
   * @return the preferences instance
   * @throws BackingStoreException if creation failed
   */
  public DbPreferences createPreferences(DbPreferences parent, DbPreferencesNode node) throws BackingStoreException {
    return new DbPreferences(parent, node);
  }


  /**
   * Creates a new DbPreferencesNode.<br>
   * All nodes are created through this method.
   * This allows replacing the concrete implementation by
   * overriding the method.
   *
   * @return the new node within the current db
   */
  public DbPreferencesNode createNode() {
    DbPreferencesNode node = new DbPreferencesNode();
    node.setSessionHolder(sessionHolder);
    return node;
  }


  /**
   * Creates a new DbPreferencesKey.
   * All keys are created through this method.
   * This allows replacing the concrete implementation by
   * overriding the method.
   *
   * @return the new key within the current db
   */
  public DbPreferencesKey createKey() {
    DbPreferencesKey key = new DbPreferencesKey();
    key.setSessionHolder(sessionHolder);
    return key;
  }


  /**
   * Returns the absolute path name of the node corresponding to the package of the specified object.
   *
   * @param clazz the class
   * @return the path name
   * @throws IllegalArgumentException if the package has node preferences node associated with it.
   */
  protected static String nodeName(Class<?> clazz) {
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

}
