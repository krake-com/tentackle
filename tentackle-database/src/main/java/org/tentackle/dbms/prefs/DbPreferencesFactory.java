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

import org.tentackle.common.InterruptedRuntimeException;
import org.tentackle.common.Service;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.prefs.DbPreferencesOperation.RefreshInfo;
import org.tentackle.log.Logger;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.prefs.PersistedPreferencesFactory;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.session.ModificationEvent;
import org.tentackle.session.ModificationEventDetail;
import org.tentackle.session.ModificationListenerAdapter;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.prefs.BackingStoreException;
import java.util.prefs.NodeChangeEvent;
import java.util.prefs.NodeChangeListener;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

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
   * Gets the factory singleton.
   *
   * @return the factory
   */
  public static  DbPreferencesFactory getInstance() {
    return (DbPreferencesFactory) PersistedPreferencesFactory.getInstance();
  }

  private static final Logger LOGGER = Logger.get(DbPreferencesFactory.class);

  /**
   * Modification tracker name.
   */
  private static final String TRACKER_NAME = "preferences";

  private static final long WRITE_LOCK_POLL_MS = 100;   // interval to check whether the write lock's transaction has ended
  private static final long WRITE_LOCK_MAX_WAIT_MS = 60000;   // max. time to wait for the write lock


  /**
   * Listeners are managed by the repository, not the nodes.
   * This allows un- and reloading the nodes without losing the listeners.
   * <p>
   * Notice that when a node is removed, its listeners will <em>not</em> be removed.
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

    private static final AtomicLong LAST_SERIAL = new AtomicLong();   // last serial, shared by all roots

    private final DbPreferences node;   // the root node holding the whole tree
    private long serial;                // the serial version of the whole tree (guarded by the factory)

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
      serial = LAST_SERIAL.incrementAndGet();
    }
  }


  private volatile boolean autoSync;        // auto-sync flag (read by the modification tracker)
  private volatile boolean readOnly;        // readonly flag
  private volatile boolean systemOnly;      // system-only flag

  private final Map<String,Root> roots;     // loaded roots, key is username ("" for system)

  private final Map<ListenerKey,Set<NodeChangeListener>> nodeListeners;       // listeners for added/removed nodes
  private final Map<ListenerKey,Set<PreferenceChangeListener>> prefListeners; // listeners for changed keys

  private List<Runnable> pendingEvents = new ArrayList<>();   // listener invocations to fire once the monitor is released

  private long nodeTableSerial;             // highest tableSerial of _all_ DbPreferencesNodes or remote repo
  private long keyTableSerial;              // highest tableSerial of _all_ DbPreferencesKeys

  private Db fallbackDb;                    // session used if there is no thread-local session (guarded by this factory)

  /*
   * The write lock serializes the modifications of the backing store, see acquireWriteLock.
   */
  private final Object writeLock = new Object();
  private Db writeOwner;                    // the session holding the write lock, null if none (guarded by writeLock)
  private long writeOwnerTxNumber;          // the owner's transaction holding the write lock until it ends, 0 if none (guarded by writeLock)
  private int writeHoldCount;               // number of nested acquisitions in progress by the owner (guarded by writeLock)

  private final Object scheduleLock = new Object();   // guards the scheduled update
  private boolean updateScheduled;          // true if the update thread is running
  private long scheduledNodeMaxSerial;      // node serial to update the repository to, 0 if none
  private long scheduledKeyMaxSerial;       // key serial to update the repository to, 0 if none


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
        nodeTableSerial = ModificationTracker.getInstance().getIdSerialForName(TRACKER_NAME).getSerial();
        keyTableSerial = 0;
        ModificationTracker.getInstance().addModificationListener(
                new ModificationListenerAdapter(TRACKER_NAME) {

          @Override
          public void dataChanged(ModificationEvent ev) {
            if (isAutoSync()) {
              scheduleUpdate(ev.getSerial(), 0);
            }
          }
        });
      }
      else {
        nodeTableSerial = new DbPreferencesNode(db).getModificationCount();
        keyTableSerial = new DbPreferencesKey(db).getModificationCount();
        ModificationTracker.getInstance().addModificationListener(
                new ModificationListenerAdapter(DbPreferencesNode.CLASSVARIABLES.tableName, DbPreferencesKey.CLASSVARIABLES.tableName) {

          @Override
          public void dataChanged(ModificationEvent ev) {
            long nodeMaxSerial = 0;
            long keyMaxSerial = 0;
            for (ModificationEventDetail detail : ev.getDetails()) {
              if (DbPreferencesNode.CLASSVARIABLES.tableName.equals(detail.getName())) {
                nodeMaxSerial = detail.getSerial();
              }
              else if (DbPreferencesKey.CLASSVARIABLES.tableName.equals(detail.getName())) {
                keyMaxSerial = detail.getSerial();
              }
            }
            if (isAutoSync()) {
              scheduleUpdate(nodeMaxSerial, keyMaxSerial);
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
  public DbPreferences getSystemRoot() {
    return systemRoot();
  }

  @Override
  public DbPreferences getUserRoot() {
    return isSystemOnly() ? systemRoot() : userRoot();
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
  public DbPreferences systemRoot() {
    return getRootTree(null, null);
  }

  @Override
  public DbPreferences userRoot() {
    return getRootTree(null, getUserName());
  }


  /**
   * Gets the current serial version of given user- or system root.
   *
   * @param user the username, null or "" if system
   * @return the serial, 0 if no such root loaded
   */
  public synchronized long getSerial(String user) {
    String key = user == null ? "" : user;
    Root root = roots.get(key);
    return root == null ? 0 : root.serial;
  }

  /**
   * Adds a preferences changed listener.
   * <p>
   * Notice that when a node is removed, its listeners will <em>not</em> be removed.
   * So, if the node is added again, the listeners are still there.
   * Listeners have to be removed by the application explicitly.
   *
   * @param node the node
   * @param pcl the listener
   */
  public synchronized void addPreferenceChangeListener(DbPreferences node, PreferenceChangeListener pcl) {
    ListenerKey lk = new ListenerKey(node);
    Set<PreferenceChangeListener> listeners = prefListeners.computeIfAbsent(lk, k -> new HashSet<>());
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
   * Notice that when a node is removed, its listeners will <em>not</em> be removed.
   * So, if the node is added again, the listeners are still there.
   * Listeners have to be removed by the application explicitly.
   *
   * @param node the node
   * @param ncl the listener
   */
  public synchronized void addNodeChangeListener(DbPreferences node, NodeChangeListener ncl) {
    ListenerKey lk = new ListenerKey(node);
    Set<NodeChangeListener> listeners = nodeListeners.computeIfAbsent(lk, k -> new HashSet<>());
    listeners.add(ncl);
  }

  /**
   * Removes a node listener.
   *
   * @param node the node
   * @param ncl the listener
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
   * Invokes listeners when a node is added or removed.
   *
   * @param node the added or removed node
   */
  public void nodeChanged(DbPreferences node) {
    synchronized (this) {
      DbPreferences parent = node.parent();
      if (parent != null) {
        Set<NodeChangeListener> listeners = nodeListeners.get(new ListenerKey(parent));
        if (listeners != null) {
          NodeChangeEvent ev = new NodeChangeEvent(parent, node);
          boolean removed = node.getPersistentNode().isRemoved();
          for (NodeChangeListener listener : listeners) {
            pendingEvents.add(removed ? () -> listener.childRemoved(ev) : () -> listener.childAdded(ev));
          }
        }
      }
      if (node.isRootNode() && node.getPersistentNode().isRemoved()) {
        removeRoot(node);
      }
    }
    firePendingEvents();
  }

  /**
   * Invokes listeners when a key is added, updated or removed.
   *
   * @param node the node holding the key
   * @param key the changed key
   */
  public void keyChanged(DbPreferences node, DbPreferencesKey key) {
    synchronized (this) {
      Set<PreferenceChangeListener> listeners = prefListeners.get(new ListenerKey(node));
      if (listeners != null) {
        PreferenceChangeEvent ev = new PreferenceChangeEvent(node, key.getKey(), key.isRemoved() ? null : key.getValue());
        for (PreferenceChangeListener listener : listeners) {
          pendingEvents.add(() -> listener.preferenceChange(ev));
        }
      }
    }
    firePendingEvents();
  }

  /**
   * Fires the listener invocations queued by {@link #nodeChanged} and {@link #keyChanged}.<br>
   * The listeners are invoked without holding the monitor of this factory, since they are application code.
   * A listener waiting for another thread that accesses the preferences would deadlock otherwise.
   * If the monitor is held by the current thread, the invocations stay queued until the outermost
   * synchronized operation completes.
   */
  private void firePendingEvents() {
    if (Thread.holdsLock(this)) {
      return;   // fired by the outermost operation, once the monitor is released
    }
    List<Runnable> events;
    synchronized (this) {
      if (pendingEvents.isEmpty()) {
        return;
      }
      events = pendingEvents;
      pendingEvents = new ArrayList<>();
    }
    for (Runnable event : events) {
      try {
        event.run();
      }
      catch (RuntimeException rex) {
        LOGGER.severe("preferences listener failed", rex);
      }
    }
  }

  /**
   * Discards the listener invocations queued since the given mark.<br>
   * Used if the operation that queued them failed, i.e., the changes were not persisted.
   * Must be invoked with the monitor of this factory held.
   *
   * @param mark the number of pending invocations before the operation
   */
  private void discardPendingEvents(int mark) {
    if (pendingEvents.size() > mark) {
      pendingEvents.subList(mark, pendingEvents.size()).clear();
    }
  }

  /**
   * Flushes a node and all of its sub-nodes to the backing store.
   * <p>
   * The change listeners are invoked once the flush completed, without holding the monitor of this factory.
   * If the flush fails, they are not invoked at all.
   * <p>
   * If the session is running a transaction, the flush joins it and the modified rows stay locked in the database
   * until the transaction ends. So does the write lock of this factory, see {@link #acquireWriteLock(Db)}.
   *
   * @param node the preferences node
   * @param sync true if load modifications from storage before flush
   * @throws BackingStoreException if failed
   */
  public void flush(DbPreferences node, boolean sync) throws BackingStoreException {
    Db db = requestDb(node.isUserNode(), true);
    try {
      boolean locked;
      try {
        locked = acquireWriteLock(db);
      }
      catch (RuntimeException rex) {
        throw new BackingStoreException(rex);
      }
      try {
        flushSynchronized(db, node, sync);
      }
      finally {
        if (locked) {
          releaseWriteLock(db);
        }
      }
    }
    finally {
      releaseDb(db);
      firePendingEvents();
    }
  }

  private synchronized void flushSynchronized(Db db, DbPreferences node, boolean sync) throws BackingStoreException {
    int eventMark = pendingEvents.size();
    boolean flushed = false;
    try {
      flushImpl(db, node, sync);
      flushed = true;
    }
    finally {
      if (!flushed) {
        discardPendingEvents(eventMark);
      }
    }
  }

  private void flushImpl(Db db, DbPreferences node, boolean sync) throws BackingStoreException {
    try {
      boolean updated = db.transaction(() -> {
        try {
          return new DbPreferencesOperation(db).flush(node, sync, false);
        }
        catch (RuntimeException rex) {
          // remove the root node from the repo (forces reload on next access)
          removeRoot(node);
          throw rex;
        }
      });

      if (updated) {
        LOGGER.warning("nodes where updated or deleted by another JVM");
      }
    }
    catch (RuntimeException rex) {
      throw new BackingStoreException(rex);
    }
  }

  /**
   * Acquires the write lock.<br>
   * The write lock serializes the modifications of the backing store by the sessions of this JVM.
   * It is acquired before the monitor of this factory, which guards the in-memory repository.
   * <p>
   * If the session is running a transaction, the lock is held until the transaction ends,
   * since the modified rows stay locked in the database until then. Hence, another session waits for the
   * write lock without holding the monitor, instead of waiting for the row locks while holding it.
   * Otherwise, the owner of the transaction could not even read a preference before committing, and both
   * would wait for each other until the database's lock timeout.
   * <p>
   * The lock is reentrant for the owning session.
   * <p>
   * If the owner's transaction does not end in time, e.g., because the application leaked it, the session proceeds
   * without the lock. It then waits for the row locks as before, which fails after the database's lock timeout.
   *
   * @param db the session modifying the backing store
   * @return true if acquired, false if not acquired because the current thread holds the monitor of this factory
   * or the lock was not released in time
   * @throws InterruptedRuntimeException if interrupted while waiting
   */
  private boolean acquireWriteLock(Db db) {
    long deadline = System.currentTimeMillis() + WRITE_LOCK_MAX_WAIT_MS;
    synchronized (writeLock) {
      while (writeOwner != db && isWriteLocked()) {
        if (Thread.holdsLock(this)) {
          return false;   // waiting with the monitor held would block the owner as well
        }
        if (System.currentTimeMillis() >= deadline) {
          LOGGER.warning("{0} still holds the preferences write lock within transaction {1} -> {2} proceeds without",
                         writeOwner, writeOwnerTxNumber, db);
          return false;
        }
        try {
          writeLock.wait(WRITE_LOCK_POLL_MS);   // the end of the owner's transaction is not notified
        }
        catch (InterruptedException ix) {
          Thread.currentThread().interrupt();
          throw new InterruptedRuntimeException(ix);
        }
      }
      if (writeOwner != db || !isWriteLocked()) {
        writeOwner = db;
        writeOwnerTxNumber = db.isTxRunning() ? db.getTxNumber() : 0;
        writeHoldCount = 0;
      }
      writeHoldCount++;
      return true;
    }
  }

  /**
   * Releases the write lock acquired by {@link #acquireWriteLock(Db)}.<br>
   * If acquired within a transaction, the lock is held until the transaction ends.
   *
   * @param db the session modifying the backing store
   */
  private void releaseWriteLock(Db db) {
    synchronized (writeLock) {
      if (writeOwner == db && writeHoldCount > 0 && --writeHoldCount == 0) {
        if (!isWriteLocked()) {
          writeOwner = null;
        }
        writeLock.notifyAll();
      }
    }
  }

  /**
   * Returns whether the write lock is held.<br>
   * Must be invoked with the {@link #writeLock} held.
   *
   * @return true if held by a modification in progress or by the owner's transaction
   */
  private boolean isWriteLocked() {
    Db owner = writeOwner;
    return owner != null &&
           (writeHoldCount > 0 ||
            writeOwnerTxNumber != 0 && owner.isOpen() && owner.isTxRunning() && owner.getTxNumber() == writeOwnerTxNumber);
  }

  /**
   * Gets the root node for given user and all of its sub-nodes and keys at once.
   *
   * @param db the optional db, null if request a db temporarily
   * @param user the username, null or empty if system user
   * @return the root node, never null
   */
  public synchronized DbPreferences getRootTree(Db db, String user) {
    String key = user == null ? "" : user;
    Root root = roots.get(key);
    if (root == null) {
      boolean needRelease = false;
      if (db == null) {
        db = requestDb(true, false);
        needRelease = true;
      }
      try {
        root = new Root(new DbPreferencesOperation(db).loadRootTree(user, !db.isRemote()));
        roots.put(key, root);
      }
      finally {
        if (needRelease) {
          releaseDb(db);
        }
      }
    }
    return root.node;
  }

  /**
   * Checks all preferences and tries to fix if possible.<br>
   * The method is provided since {@link DbPreferencesNode} and {@link DbPreferencesKey} provide no referential integrity
   * via database-enforced foreign keys, but only at application level.<br>
   * Invoke this method, whenever the database has been modified manually via SQL.
   *
   * @param db the session (must be local)
   */
  public void checkAllPreferences(Db db) {
    db.assertNotRemote();   // must be a direct JDBC link
    db.transaction(() -> {
      Map<Long, DbPreferencesNode> nodeMap = new HashMap<>();   // map ID:Node
      Set<DbPreferencesNode> processedNodes = new HashSet<>();  // already processed nodes
      Collection<DbPreferencesNode> allNodes = new DbPreferencesNode(db).selectAllObjects();
      Collection<DbPreferencesKey> allKeys = new DbPreferencesKey(db).selectAllObjects();
      LOGGER.info("processing {0} nodes and {1} keys", allNodes.size(), allKeys.size());
      for (DbPreferencesNode node : allNodes) {
        nodeMap.put(node.getId(), node);
      }
      processPreferences(processedNodes, nodeMap, allKeys);
      allNodes.removeAll(processedNodes);
      for (DbPreferencesNode node: allNodes) {
        LOGGER.severe("orphan node found: {0}", node);
      }
      for (DbPreferencesKey key: allKeys) {
        LOGGER.severe("orphan key found: {0}", key);
      }
      return null;
    });
  }


  /**
   * Returns the absolute path name of the node corresponding to the package of the specified object.
   *
   * @param clazz the class
   * @return the path name
   * @throws IllegalArgumentException if the package has no preferences node associated with it
   */
  protected String nodeName(Class<?> clazz) {
    if (clazz.isArray()) {
      throw new IllegalArgumentException("Arrays have no associated preferences node");
    }
    return ReflectionHelper.getPackagePathName(clazz);
  }

  /**
   * Removes a root node of given node from the repository.
   *
   * @param node the root node or one of its sub-nodes
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
   * <p>
   * The change listeners are invoked once the repository is updated, without holding the monitor of this factory.
   *
   * @param db the session
   * @param nodeMaxSerial the new table serial for prefnodes
   * @param keyMaxSerial the new table serial for prefkeys
   */
  protected void updateRepository(Db db, long nodeMaxSerial, long keyMaxSerial) {
    try {
      boolean locked = acquireWriteLock(db);    // the update counts a modification in the backing store
      try {
        synchronized (this) {
          int eventMark = pendingEvents.size();
          boolean updated = false;
          try {
            updateRepositoryImpl(db, nodeMaxSerial, keyMaxSerial);
            updated = true;
          }
          finally {
            if (!updated) {
              discardPendingEvents(eventMark);
            }
          }
        }
      }
      finally {
        if (locked) {
          releaseWriteLock(db);
        }
      }
    }
    finally {
      firePendingEvents();
    }
  }

  /**
   * Schedules the update of the repository on behalf of the modification tracker.
   * <p>
   * The repository is not updated by the tracker thread, since it must not wait for the monitor of this factory.
   * The thread holding the monitor may wait for the tracker thread, for example to configure the modification
   * counter in a flush, and both would wait for each other until the tracker's wait interval elapsed.
   * Instead, the update is performed by a separate thread, using the session of {@link #requestDb(boolean, boolean)}.
   * Updates scheduled while the thread is still running are merged.
   *
   * @param nodeMaxSerial the new table serial for prefnodes, 0 if unchanged
   * @param keyMaxSerial the new table serial for prefkeys, 0 if unchanged
   */
  protected void scheduleUpdate(long nodeMaxSerial, long keyMaxSerial) {
    synchronized (scheduleLock) {
      scheduledNodeMaxSerial = Math.max(scheduledNodeMaxSerial, nodeMaxSerial);
      scheduledKeyMaxSerial = Math.max(scheduledKeyMaxSerial, keyMaxSerial);
      if (updateScheduled) {
        return;   // picked up by the running thread
      }
      updateScheduled = true;
    }
    try {
      // a platform thread: it uses the JDBC driver while holding the monitor
      Thread.ofPlatform().daemon().name("preferences update").start(this::runScheduledUpdates);
    }
    catch (RuntimeException | Error ex) {
      synchronized (scheduleLock) {
        updateScheduled = false;
      }
      throw ex;
    }
  }

  /**
   * Performs the scheduled updates until there are no more.
   */
  private void runScheduledUpdates() {
    for (;;) {
      long nodeMaxSerial;
      long keyMaxSerial;
      synchronized (scheduleLock) {
        nodeMaxSerial = scheduledNodeMaxSerial;
        keyMaxSerial = scheduledKeyMaxSerial;
        if (nodeMaxSerial == 0 && keyMaxSerial == 0) {
          updateScheduled = false;
          return;
        }
        scheduledNodeMaxSerial = 0;
        scheduledKeyMaxSerial = 0;
      }
      try {
        Db db = requestDb(false, false);
        try {
          updateRepository(db, nodeMaxSerial, keyMaxSerial);
        }
        finally {
          releaseDb(db);
        }
      }
      catch (RuntimeException ex) {
        LOGGER.severe("updating the preferences repository failed", ex);
      }
    }
  }

  private void updateRepositoryImpl(Db db, long nodeMaxSerial, long keyMaxSerial) {
    if (nodeMaxSerial != nodeTableSerial || keyMaxSerial != keyTableSerial) {
      if (db.isRemote()) {
        // rebuild all roots if serials don't match with the ones in the remote repository
        for (Root root: roots.values()) {
          rebuildRoot(db, root);
        }
      }
      else {
        // get tableserial/id-pairs
        boolean rebuildRoots = false;
        Collection<IdSerialTuple> nodeExpireSet = null;
        Collection<IdSerialTuple> keyExpireSet = null;
        if (nodeMaxSerial > 0) {
          nodeExpireSet = new DbPreferencesNode(db).getExpiredTableSerials(nodeTableSerial, nodeMaxSerial);
          if (isRebuildNecessary(nodeTableSerial, nodeExpireSet, nodeMaxSerial)) {
            rebuildRoots = true;
          }
        }
        if (!rebuildRoots && keyMaxSerial > 0) {
          keyExpireSet = new DbPreferencesKey(db).getExpiredTableSerials(keyTableSerial, keyMaxSerial);
          if (isRebuildNecessary(keyTableSerial, keyExpireSet, keyMaxSerial)) {
            rebuildRoots = true;
          }
        }

        Set<String> usersModified = new HashSet<>();  // roots affected, "" is system
        // TreeSet to sort by id (because children are always added _after_ their parents, see flushImpl)
        Set<DbPreferencesNode> changedNodes = new TreeSet<>();
        Set<DbPreferencesKey> changedKeys = new TreeSet<>();

        Map<Long,DbPreferencesNode> idNodeMap = new HashMap<>();    // related nodes map by ID

        if (!rebuildRoots) {
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
              rebuildRoots = true;
            }
          }

          if (!rebuildRoots && keyExpireSet != null) {
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
              rebuildRoots = true;
            }
          }
        }

        if (rebuildRoots) {
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
        ModificationTracker.getInstance().countModification(db, TRACKER_NAME);
      }
    }
  }

  /**
   * Requests for a session to work with.<br>
   * By default, the thread-local session is used.
   * If there is no thread-local session, a session cloned from the modification tracker's session is used,
   * see {@link #getFallbackDb()}.
   * <p>
   * In preparation for replication layers, a requested session must be released after use.
   *
   * @param forUser true if a user session is required (no modification tracker fallback)
   * @param forWrite true if the session is used for writing (flush), else read
   * @return the session
   */
  protected Db requestDb(boolean forUser, boolean forWrite) {
    Db db = (Db) Session.getCurrentSession();
    if (db == null) {
      if (forUser) {
        throw new PersistenceException("no thread-local session for " + Thread.currentThread());
      }
      db = getFallbackDb();
    }
    return db;
  }

  /**
   * Gets the session used if there is no thread-local session.
   * <p>
   * The modification tracker's session cannot be used directly, since it is owned by the tracker thread.
   * Borrowing it via {@link ModificationTracker#requestSession()} would deadlock: the tracker thread invokes
   * the modification listener of this factory, which needs the monitor of this factory, while the borrowing
   * thread holds the monitor and waits for the tracker. Instead, a dedicated session is cloned from the
   * tracker's session. It is used with the monitor of this factory held only (or by the constructor,
   * before the factory is published), and closed when the tracker terminates.
   *
   * @return the session, never null
   */
  private synchronized Db getFallbackDb() {
    Db db = fallbackDb;
    if (db == null || !db.isOpen()) {
      ModificationTracker tracker = ModificationTracker.getInstance();
      Db trackerDb = (Db) tracker.getSession();
      if (trackerDb == null) {
        throw new PersistenceException("no session for " + tracker);
      }
      Db clonedDb = trackerDb.clone("preferences");
      tracker.addShutdownRunnable(clonedDb::close);
      fallbackDb = db = clonedDb;
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
   * Gets the current username.
   *
   * @return the username
   */
  protected String getUserName() {
    return requestDb(true, false).getSessionInfo().getUserName();
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
    RefreshInfo info = new DbPreferencesOperation(db).loadRefreshInfo(root.node, db.isRemote() ? root.serial : 0);
    if (info != null) {
      Collection<DbPreferencesNode> prefNodes = info.prefNodes();
      Collection<DbPreferencesKey> prefKeys = info.prefKeys();

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
            parent.addChild(node);
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
          boolean updated;
          DbPreferencesKey key = node.getPersistentKey(prefKey.getKey());
          if (key == null) {
            node.putPersistentKey(prefKey);
            idKeyMap.put(prefKey.getId(), prefKey);
            updated = true;
          }
          else {
            updated = node.updatePersistentKey(prefKey);
          }
          if (updated) {
            keyChanged(node, prefKey);
          }
        }
      }

      // remove keys
      Set<DbPreferencesKey> persistedKeys = new HashSet<>(prefKeys);
      for (DbPreferencesKey prefKey: idKeyMap.values()) {
        if (!prefKey.isNew() && !persistedKeys.contains(prefKey)) {
          // if not created in this JVM
          DbPreferences node = idNodeMap.get(prefKey.getNodeId());
          if (node != null) {
            prefKey = node.removePersistentKey(prefKey.getKey());
            if (prefKey != null) {
              keyChanged(node, prefKey);
            }
          }
        }
      }

      // remove nodes
      Set<DbPreferencesNode> persistedNodes = new HashSet<>(prefNodes);
      for (DbPreferences node: idNodeMap.values()) {
        if (!node.getPersistentNode().isNew() && !persistedNodes.contains(node.getPersistentNode())) {
          // if not created in this JVM
          DbPreferences parent = node.parent();
          if (parent != null) {
            node = parent.removeChild(node.name());
            if (node != null) {
              nodeChanged(node);
            }
          }
        }
      }

      if (db.isRemote()) {
        root.serial = info.serial();
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
   * @param changedKeys the set of changes keys
   */
  private void updateRoot(Root root,
                          Set<DbPreferencesNode> changedNodes,
                          Set<DbPreferencesKey> changedKeys) {

    // to speed up: build maps of ID:node and ID:key
    Map<Long,DbPreferences> idNodeMap = new HashMap<>();
    Map<String,DbPreferences> pathNodeMap = new HashMap<>();
    addNodeToMaps(root.node, idNodeMap, pathNodeMap);

    long rootId = root.node.getPersistentNode().getId();

    if (rootId == 0) {
      // special handling if this is the first node for this user at all:
      // in this case the changedNodes contain the root id
      for (DbPreferencesNode node: changedNodes) {
        if (Objects.equals(root.node.getUser(), node.getUser())) {
          rootId = node.getRootNodeId();
          break;
        }
      }
    }

    // add or update nodes
    for (DbPreferencesNode prefNode: changedNodes) {
      if (prefNode.getRootNodeId() == rootId) {   // only nodes belonging to this root
        DbPreferences node = pathNodeMap.get(prefNode.getName());
        if (node == null) {
          // add node
          DbPreferences parent = idNodeMap.get(prefNode.getParentId());  // must exist bec. nodes are sorted by id
          if (parent != null) {
            node = new DbPreferences(parent, prefNode, null, null);
            parent.addChild(node);
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
          if (node.updatePersistentKey(prefKey)) {
            keyChanged(node, prefKey);
          }
        }
        else {
          LOGGER.warning("node ID=" + prefKey.getNodeId() + " vanished for key " + prefKey);
        }
      }
    }

    root.countUpdate();
  }

  /**
   * Adds all sub-nodes and keys of a node to given maps.
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
    for (DbPreferences child: node.getChildren()) {
      addNodeToMaps(child, idNodeMap, pathNodeMap, idKeyMap);
    }
  }

  /**
   * Adds all sub-nodes of a node to given maps.
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

    for (DbPreferences child: node.getChildren()) {
      addNodeToMaps(child, idNodeMap, pathNodeMap);
    }
  }

  private void processPreferences(Set<DbPreferencesNode> processedNodes, Map<Long, DbPreferencesNode> nodeMap,
                                  Collection<DbPreferencesKey> allKeys) {
    // check root nodes
    for (DbPreferencesNode node : nodeMap.values()) {
      if (node.getParentId() == 0 && node.getRootNodeId() != node.getId()) {
        node.setRootNodeId(node.getId());
        node.saveObject();
        LOGGER.info("fixed rootNodeId of root-node {0} to {1}", node, node.getRootNodeId());
      }
    }

    // process parent chain to root
    for (DbPreferencesNode node: nodeMap.values()) {
      if (node.getParentId() != 0) {
        DbPreferencesNode root = findRoot(nodeMap, node);
        if (root == null) {
          LOGGER.severe("missing root for node {0}", node.getId());
        }
        else {
          // go down all sub-nodes and keys and set the rootNodeId if missing
          processNode(processedNodes, nodeMap.values(), allKeys, root);
        }
      }
    }
  }

  private void processNode(Set<DbPreferencesNode> processedNodes, Collection<DbPreferencesNode> allNodes,
                           Collection<DbPreferencesKey> allKeys, DbPreferencesNode node) {
    if (!processedNodes.contains(node)) {
      for (DbPreferencesKey key: findKeys(allKeys, node)) {
        if (key.getRootNodeId() != node.getRootNodeId()) {
          key.setRootNodeId(node.getRootNodeId());
          key.saveObject();
          LOGGER.info("fixed rootNodeId of key {0} to {1}", key, key.getRootNodeId());
        }
      }
      for (DbPreferencesNode child: findChildren(allNodes, node)) {
        if (child.getRootNodeId() != node.getRootNodeId()) {
          child.setRootNodeId(node.getRootNodeId());
          child.saveObject();
          LOGGER.info("fixed rootNodeId of node {0} to {1}", child, child.getRootNodeId());
        }
        processNode(processedNodes, allNodes, allKeys, child);
      }
      processedNodes.add(node);
    }
  }

  private DbPreferencesNode findRoot(Map<Long, DbPreferencesNode> nodeMap, DbPreferencesNode node) {
    while (node != null && node.getParentId() != 0) {
      node = nodeMap.get(node.getParentId());
    }
    return node;
  }

  private Collection<DbPreferencesNode> findChildren(Collection<DbPreferencesNode> allNodes, DbPreferencesNode node) {
    Collection<DbPreferencesNode> children = new ArrayList<>();
    for (DbPreferencesNode child: allNodes) {
      if (child.getParentId() == node.getId()) {
        children.add(child);
      }
    }
    return children;
  }

  private Collection<DbPreferencesKey> findKeys(Collection<DbPreferencesKey> allKeys, DbPreferencesNode node) {
    Collection<DbPreferencesKey> keys = new ArrayList<>();
    for (Iterator<DbPreferencesKey> iter = allKeys.iterator(); iter.hasNext(); ) {
      DbPreferencesKey key = iter.next();
      if (key.getNodeId() == node.getId()) {
        keys.add(key);
        iter.remove();
      }
    }
    return keys;
  }

  private boolean isRebuildNecessary(long lastSerial, Collection<IdSerialTuple> expireSet, long maxSerial) {
    boolean rebuildNecessary = false;
    for (IdSerialTuple idSer: expireSet) {
      long serial = idSer.getSerial();
      if (serial < 0 ||   // deletion from gap history
          lastSerial > 0 && serial - lastSerial > 1) {  // gap in tableSerials
        rebuildNecessary = true;
        break;
      }
      lastSerial = serial;
    }
    if (maxSerial > lastSerial) {
      rebuildNecessary = true;  // no expired PDOs found or gap at the end
    }
    return rebuildNecessary;
  }

}
