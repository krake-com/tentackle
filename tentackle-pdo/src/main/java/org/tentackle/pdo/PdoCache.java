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

package org.tentackle.pdo;

import org.tentackle.log.Logger;
import org.tentackle.misc.CopyOnWriteList;
import org.tentackle.misc.Holder;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.session.ModificationEvent;
import org.tentackle.session.ModificationEventDetail;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.Session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Cache for {@link PersistentDomainObject}s.<br>
 *
 * The cache maintains any number of unique indexes (see {@link PdoCacheIndex})
 * that are added (or removed) at runtime. The ID index is mandatory,
 * automatically added, and cannot be removed.<br>
 * For inherited entities, the topmost super entity usually provides the cache for
 * all classes of the inheritance hierarchy.
 * However, subclasses may maintain their own cache, for example,
 * to add unique indexes not available in the super classes.<br>
 * The PDOs in the cache are made immutable by default, i.e., the cache is in the
 * so-called <em>readonly mode</em>. This allows multiple threads to use the PDOs
 * provided by the cache in parallel in a thread-safe manner.<br>
 * However, for special purposes, caches can also work with mutable PDOs,
 * then maintained per session, which needs significantly more memory, of course,
 * and some application-specific code to avoid multi-threading and synchronization issues.
 * <p>
 * The cache works in one of 4 operating modes ({@link PdoCacheStrategy}):
 * <ul>
 *   <li><em>PRELOAD</em>: loads all PDOs at once.<br>
 *        This is the default and recommended for the majority of master-data entities with
 *        a rather small number of PDOs.<br>
 *        The first select from the cache will load all PDOs from the database
 *        so that further selects will not need a physical load anymore.</li>
 *    <li><em>LRU</em>: when {@code maxSize} is reached, the least recently used PDOs are removed, depending on the {@code keepQuota}.
 *    This is the preferred mode if the number of entities is large and all PDOs cannot (or should not) be kept in memory.</li>
 *    <li><em>LFU</em>: same as LRU, but the least frequently used PDOs are removed.</li>
 *    <li><em>FORGET</em>: when {@code maxSize} is reached, the cache is completely invalidated (all PDOs removed from the cache).</li>
 * </ul>
 *
 * <p>
 * Although the main purpose of caching is to provide fast access to PDOs by their object ID or additional unique domain keys,
 * the cache is also able to maintain lists of PDOs. When invoking {@link #selectAll} for the first time, all PDOs of the
 * given domain context are loaded from the database into the cache and the {@code maxSize} is automatically enlarged, if not
 * inhibited by {@code maxSizeHardLimit}. From then on, the cache behaves for that context like a {@link PdoCacheStrategy#PRELOAD}
 * cache: it never evicts, never loads single PDOs on a cache miss, and keeps the list coherent instead.
 * <p>
 * When PDOs are updated, inserted, or deleted, the PDOs held in the cache (and optional lists) are updated according
 * to the configuration of the PDOs and the application's operation mode:
 *
 * <ul>
 *   <li>
 *     If the PDO's database table does not contain a {@code tableserial}-column
 *     (see {@link PersistentDomainObject#isTableSerialProvided()} or the model's {@code [tableserial]} option),
 *     the whole cache is invalidated. This is perfectly ok for preloading caches with small tables,
 *     since the time to load the whole table is about the same as loading a single PDO.
 *   <li>
 *     With a {@code tableserial}-column, it depends on the application's operation mode:
 *     <ul>
 *       <li>
 *         The application is <em>not</em> a middle-tier server and directly connected to the database:
 *         this is known as {@code local client mode} (see {@link ModificationTracker#isLocalClientMode()}) and
 *         provided for simple applications without a middle-tier server, usually for the
 *         desktop, with only one or very few concurrent users. In this mode, such modifications
 *         will update the so-called <em>modification table</em> immediately. As a result, the modification table takes part
 *         in those transactions, which limits the overall performance because of additional
 *         exclusive database locks.<br>
 *         The cache <em>can</em> determine which PDOs were updated according to their tableserials
 *         via selecting them from the database.
 *         Those PDOs are set to "expired" (see {@link PersistentDomainObject#isExpired()}).
 *         However, if PDOs were deleted, there is no way to figure out which ones,
 *         and the cache is invalidated whenever a gap is detected in the tableserials.
 *       </li>
 *       <li>
 *         The application is a middle-tier server (connected to the database or to another middle-tier server)
 *         or a client connected to a middle-tier server: this is the preferred mode and works well even for thousands
 *         of concurrent users.<br>
 *         Modifications will not update the modification table immediately but delayed at some appropriate time,
 *         when the corresponding transactions have finished. When PDOs are updated, inserted, or deleted,
 *         their corresponding tableserials are remembered in memory, the so-called <em>tableserial history</em>.<br>
 *         Whenever a cache expiration is detected (via {@link PdoCache#expire(ModificationEvent)}), the
 *         tableserials of the updated PDOs are selected from the database <em>and</em> combined with the history.
 *         As a result, there are no gaps anymore, and the cache knows exactly which PDOs to expire, which to remove,
 *         and which to append (to the lists, if any). However, if an unexpected gap is still detected, the cache is
 *         invalidated and a warning logged.
 *       </li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>
 * Rule of thumb: go with the default for small tables (i.e., readonly preloading cache w/o a tableserial).
 * For large tables, enable the tableserial and, in middle-tier mode, use a readonly preloading cache if the table fits
 * into memory, or a reasonably sized readonly LRU cache otherwise.
 * <p>
 * If you need to work with all PDOs at once (using {@link #selectAll}) <em>and</em> large tables
 * with a tableserial, the {@link PdoCacheStrategy} has no impact anymore, except for the
 * {@link #select}s <em>before</em> the first invocation of {@link #selectAll} ({@link PdoCacheStrategy#PRELOAD}
 * vs. all others).
 * <p>
 * For performance reasons, the caches of remote clients or servers always retrieve their updates
 * from the remote caches, not the database.
 * <p>
 * Since a cache is shared by all users, cached PDOs are always readable: the generated code overrides
 * {@code AbstractPersistentObject#isReadAllowed()} to return true, so {@code ReadPermission} rules are not applied
 * to cached entities. Use {@code ViewPermission} rules instead.
 *
 * @param <T> the {@link PersistentDomainObject} class
 * @author harald
 */
public class PdoCache<T extends PersistentDomainObject<T>> {

  private static final Logger LOGGER = Logger.get(PdoCache.class);

  // volatile: switched from anywhere and read by every cache on nearly every operation
  private static volatile boolean allEnabled = true;   // false = all caches disabled. default is true

  /**
   * Checks whether all caches are enabled at all.
   *
   * @return Value of property enabled.
   */
  public static boolean isAllEnabled() {
    return allEnabled;
  }

  /**
   * Sets all caches enabled or disabled.
   *
   * @param enabled New value of property enabled.
   */
  public static void setAllEnabled(boolean enabled) {
    allEnabled = enabled;
  }


  /**
   * Holds the session with their current txNo and expired tableserials.<br>
   * Used for cache pollution detection.
   */
  private static class TxIdSet {
    private final TreeSet<Long> tableSerials;
    private long txNo;
    private TxIdSet(long txNo) {
      this.txNo = txNo;
      tableSerials = new TreeSet<>();
    }
  }

  /**
   * Holds the tableserial info per tablename.
   */
  private class SerialInfo {
    private final String tableName;                   // the table name
    private final Class<T> pdoClass;                  // the effective PDO class
    private final Map<Session, TxIdSet> tsInTx;       // tableSerials expired within the current transaction
    private final long modificationId;                // row id of the modification table for this table
    private long minTableSerial;                      // min tableserial to use for update check
    private long maxTableSerial;                      // max tableserial to use for update check
    private long tableSerial;                         // highest tableserial of all objects in the cache
    private long expiredTableSerial;                  // > 0 if delayed expire check

    private SerialInfo(String tableName, Class<T> pdoClass, IdSerialTuple idSerial) {
      this.tableName = tableName;
      this.pdoClass = pdoClass;
      this.modificationId = idSerial.getId();
      this.minTableSerial = idSerial.getSerial();
      // keys are weak!
      // Db made equals/hashCode final to guarantee the default implementation from Object
      tsInTx = new WeakHashMap<>();
    }

    private SerialInfo(String tableName, Class<T> pdoClass) {
      this(tableName, pdoClass, ModificationTracker.getInstance().getIdSerialForName(tableName));
    }

    private void addExpiredInTx(Session session, long tableSerial) {
      if (session.isTxRunning()) {
        long txNo = session.getTxNumber();
        TxIdSet txIdSet = tsInTx.computeIfAbsent(session, k -> new TxIdSet(txNo));
        if (txIdSet.txNo != txNo) {
          txIdSet.tableSerials.clear();    // the previous transaction has ended
          txIdSet.txNo = txNo;
        }
        txIdSet.tableSerials.add(tableSerial);
      }
      else {
        tsInTx.remove(session);
      }
    }

    private boolean isExpiredInTx(Session session, long oldSerial, long maxSerial) {
      if (session.isTxRunning()) {
        TxIdSet txIdSet = tsInTx.get(session);
        return txIdSet != null && txIdSet.txNo == session.getTxNumber() &&
               !txIdSet.tableSerials.subSet(oldSerial, false, maxSerial, true).isEmpty();
      }
      return false;
    }
  }


  /**
   * The list of all PDOs of a domain context and the IDs to refresh or remove on the next access.
   */
  private static class SelectAllList<T> {

    private List<T> list;                     // the PDOs via select all
    private final TreeSet<Long> refreshIds;   // the sorted IDs to load on the next refreshList
    private final TreeSet<Long> removeIds;    // the sorted IDs to remove on the next refreshList

    private SelectAllList(int size) {
      refreshIds = new TreeSet<>();
      removeIds = new TreeSet<>();
      list = new ArrayList<>(size);
    }

    /**
     * Replaces the list with a copy before it is modified.
     * <p>
     * {@code selectAll} hands the list out to the application (wrapped, but the wrapper copies only
     * on write, so reading goes to this very list). Modifying it in place would therefore let a
     * refresh in one thread corrupt an iteration in another. Refreshing instead builds a new list
     * and leaves the published one alone, so every caller keeps a stable snapshot.
     * <p>
     * Copying here rather than in {@code selectAll} keeps reads free: refreshes are rare, whereas
     * a preloading cache may be read very often and may hold the whole table.
     */
    private void copyBeforeModification() {
      list = new ArrayList<>(list);
    }

    private void addRefreshIds(Collection<Long> ids) {
      refreshIds.addAll(ids);
      removeIds.removeAll(ids);
    }

    private void addRemoveIds(Collection<Long> ids) {
      removeIds.addAll(ids);
      refreshIds.removeAll(ids);
    }

    private void clearIds()  {
      refreshIds.clear();
      removeIds.clear();
    }

    private boolean isExpired() {
      return !removeIds.isEmpty() || !refreshIds.isEmpty();
    }
  }

  private final Class<T> clazz;                               // the PersistentDomainObject class
  private final boolean readOnly;                             // true if readonly
  private final boolean tableSerialProvided;                  // true if PDOs provide a table serial
  private final List<PdoCacheIndex<T,?>> indexes;             // the indexes
  private final PdoCacheIndex<T,Long> idIndex;                // special ID-index (cannot be removed!)
  private final Map<String, SerialInfo> serialInfos;          // the serial infos per table name
  private final Map<DomainContext, SelectAllList<T>> saLists; // lists for selectAll

  /*
   * The configuration below is volatile. This is because it is read and written through accessors that do
   * not all synchronize on this cache (and is read from application threads at any time), unlike
   * the statistics counters further down, which are only ever touched while holding the monitor.
   */
  private volatile boolean enabled;               // true if cache enabled
  private volatile PdoCacheStrategy strategy;     // caching strategy
  private volatile int maxSize;                   // maximum size, 0 = unlimited (default)
  private volatile boolean maxSizeHardLimit;      // true if selectAll throws an exception if the list is larger than maxSize
  private volatile int keepQuota;                 // Percentage of entries to keep when applying caching strategy. Default is 50.

  private long selectCount;                       // select-count for existing PDOs
  private long selectMissingCount;                // select-count for PDOs missing in the database
  private long selectNotCacheableCount;           // select-count to non-cacheable PDOs
  private long selectAllCount;                    // select-count for the whole table in preloading caches


  /**
   * Creates a cache.
   *
   * @param clazz the PersistentDomainObject-class managed by the cache
   * @param readOnly true if the cache is readonly (PDOs are immutable, session is thread-local)
   */
  public PdoCache(Class<T> clazz, boolean readOnly) {
    LOGGER.fine("creating cache for {0}, readOnly={1}", clazz, readOnly);

    this.clazz = clazz;
    this.readOnly = readOnly;

    strategy = PdoCacheStrategy.PRELOAD;
    enabled = true;
    keepQuota = 50;
    tableSerialProvided = Pdo.create(clazz).isTableSerialProvided();

    indexes = new ArrayList<>();
    saLists = new HashMap<>();
    serialInfos = new HashMap<>();
    idIndex = createIdIndex();
    addIndex(idIndex);
  }


  @Override
  public String toString()  {
    return "cache '" + clazz.getName() + "'";
  }

  /**
   * Gets the class handled by this cache.
   *
   * @return the class, never null
   */
  public Class<T> getObjectClass() {
    return clazz;
  }

  /**
   * Returns whether this is a readonly cache.<br>
   * Objects in a readonly cache will be set to immutable and
   * get their session set to null, which will use the thread-local's
   * session to lazily load related objects.
   *
   * @return true if the cache is readonly
   */
  public boolean isReadOnly() {
    return readOnly;
  }

  /**
   * Sets cache enabled or disabled.
   * When disabled, the cache always reads from storage.
   *
   * @param enabled New value of property enabled.
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Checks whether cache is enabled at all.
   *
   * @return Value of property enabled.
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Returns whether this cache is preloading.
   *
   * @return true if preloading
   */
  public boolean isPreloading() {
    return strategy == PdoCacheStrategy.PRELOAD;
  }

  /**
   * Gets the maximum cache size.
   *
   * @return Value of property maxSize.
   */
  public synchronized int getMaxSize() {
    return maxSize;
  }

  /**
   * Sets the maximum cache size.<br>
   * Default is 0 = unlimited.
   * If the size is exceeded, PDOs are evicted according to the {@link PdoCacheStrategy}.
   * Caches holding lists (see {@link #selectAll}) never evict, but enlarge {@code maxSize} instead,
   * unless {@link #setMaxSizeHardLimit(boolean)} is set.
   *
   * @param maxSize New value of property maxSize.
   */
  public synchronized void setMaxSize(int maxSize) {
    this.maxSize = maxSize;
  }

  /**
   * Returns whether {@code maxSize} is a hard limit.
   *
   * @return true if selectAll cannot enlarge size
   */
  public synchronized boolean isMaxSizeHardLimit() {
    return maxSizeHardLimit;
  }

  /**
   * Sets whether {@code maxSize} is a hard limit.
   *
   * @param maxSizeHardLimit true if selectAll cannot enlarge size (default is false)
   */
  public synchronized void setMaxSizeHardLimit(boolean maxSizeHardLimit) {
    this.maxSizeHardLimit = maxSizeHardLimit;
  }

  /**
   * Set the caching strategy.<br>
   * The strategy can be changed at any time.
   *
   * @param strategy the cache eviction strategy
   */
  public void setStrategy(PdoCacheStrategy strategy) {
    this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
  }

  /**
   * Gets the caching strategy.
   *
   * @return the cache strategy
   */
  public PdoCacheStrategy getStrategy()  {
    return strategy;
  }

  /**
   * Set the keep quota in percent for the caching strategy.
   * Will be aligned to [0...100]!
   * Special: 0 is the same as FORGET
   *        100 deletes only one object (this is slow when the cache gets full, don't use it!)
   * Reasonable values are 25 up to 75.
   *
   * @param keepQuota in percent
   */
  public void setKeepQuota(int keepQuota) {
    if (keepQuota < 0) {
      keepQuota = 0;
    }
    else if (keepQuota > 100) {
      keepQuota = 100;
    }
    this.keepQuota = keepQuota;
  }

  /**
   * Gets the current keep quota.
   *
   * @return the current keep quota (default is 50)
   */
  public int getKeepQuota() {
    return keepQuota;
  }

  /**
   * Adds and assigns an index to this cache.<br>
   * Ensures that the index cannot be assigned to another cache accidentally.<br>
   * Applications usually don't need to invoke addIndex explicitly.
   * This is done automatically on its first use and known as "deferred index assignment".
   * <p>
   * Any attempt to add an index to more than one cache results in a {@link PdoCacheException}.
   *
   * @param index the index to add
   * @see #removeIndex(PdoCacheIndex)
   */
  public synchronized void addIndex(PdoCacheIndex<T,?> index)  {
    assignIndex(index);
    index.clear();
    index.clearStatistics();
    indexes.add(index);
    LOGGER.fine("{0}: adding {1}", this, index);
    if (index != idIndex) {
      // if the index is not the ID index: add objects in cache so far
      try {
        idIndex.walk(true, index::addUnique);
      }
      catch (PdoCacheException e)  {
        // unique violation detected: log that and invalidate cache
        emergencyInvalidate(e);
      }
    }
  }

  /**
   * Removes and resigns an index from this cache.
   * <p>
   * The ID index is mandatory and cannot be removed: attempting to do so throws a {@link PdoCacheException}.
   *
   * @param index to remove
   * @see #addIndex(PdoCacheIndex)
   */
  public synchronized void removeIndex(PdoCacheIndex<T,?> index) {
    if (index == idIndex) {
      throw new PdoCacheException(this + ": the ID index cannot be removed");
    }
    LOGGER.fine("{0}: removing {1}", this, index);
    resignIndex(index);       // resign index from cache
    indexes.remove(index);    // remove from the index list
  }

  /**
   * Prints the cache statistics.
   *
   * @return the stats as a string
   */
  public synchronized String printStatistics()  {
    StringBuilder buf = new StringBuilder();
    buf.append("selects=").append(selectCount)
       .append(", missing=").append(selectMissingCount)
       .append(", not cacheable=").append(selectNotCacheableCount)
       .append(", selectAll=").append(selectAllCount);
    for (PdoCacheIndex<T,?> index: indexes)  {
      buf.append('\n').append(index).append(": ").append(index.printStatistics());
    }
    return buf.toString();
  }

  /**
   * Clears the cache statistics.
   */
  public synchronized void clearStatistics()  {
    selectCount = 0;
    selectMissingCount = 0;
    selectNotCacheableCount = 0;
    selectAllCount = 0;
    for (PdoCacheIndex<T,?> index: indexes)  {
      index.clearStatistics();
    }
  }

  /**
   * Gets the indexes registered for this cache.
   * <p>
   * The returned list is a snapshot: the wrapper copies only on write, so wrapping the cache's own
   * list would let {@link #addIndex} or {@link #removeIndex} modify it while the caller iterates.
   *
   * @return the indexes
   */
  public synchronized List<PdoCacheIndex<T,?>> getIndexes()  {
    return new CopyOnWriteList<>(new ArrayList<>(indexes));
  }

  /**
   * Forces the cache to be cleared for next access.
   */
  public synchronized void invalidate() {
    LOGGER.fine(() -> "invalidating " + this + ": " + printStatistics());
    invalidateImpl();
  }

  /**
   * Adds an expiration for the given tableserial.<br>
   * If the PDO does not provide a tableSerial, the cache will be invalidated immediately.
   * Otherwise, the expiration will be performed at the next selection from this cache.
   * <p>
   * Also invoked from {@code org.tentackle.persist.AbstractPersistentObject#expireCache(long)} to
   * add the expired tableserial in the JVM(s) while modifying the PDO. After the transaction
   * has been committed, the JVM will see the modifications reflected immediately by the cache
   * and doesn't have to wait for the {@link ModificationTracker}.
   *
   * @param session the session to use
   * @param tableName the tablename
   * @param maxSerial is the new tableserial of the PDO
   */
  public synchronized void expire(Session session, String tableName, long maxSerial) {
    if (tableSerialProvided) {
      SerialInfo serialInfo = serialInfos.get(tableName);
      if (serialInfo != null) {   // null if no objects in cache
        if (session != null) {
          // It is perfectly ok to modify a PDO within a transaction.
          // However, it is not allowed to select this PDO from the cache within the same
          // transaction because this would cause cache pollution if the transaction is rolled back.
          serialInfo.addExpiredInTx(session, maxSerial);  // remember the tableserial of the modified PDO
        }
        if (maxSerial > serialInfo.maxTableSerial) {
          // remember the upper bound of all requests
          serialInfo.maxTableSerial = maxSerial;
          if (serialInfo.expiredTableSerial == 0) {   // if no delayed expiration triggered
            // delay expiration check
            serialInfo.expiredTableSerial = serialInfo.tableSerial; // set the lower bound requested (once!)
          }
        }
      }
    }
    else {
      // no tableserial -> no way to determine which PDOs need an update
      invalidateImpl();
    }
  }

  /**
   * Expires the cache according to the given {@link ModificationEvent}.<br>
   * If the PDO does not provide a tableSerial, the cache will be invalidated immediately.
   * Otherwise, the expiration will be performed at the next selection from this cache.
   *
   * @param event the modification event
   */
  public void expire(ModificationEvent event)  {
    for (ModificationEventDetail detail : event.getDetails()) {
      expire(null, detail.getName(), detail.getSerial());
    }
  }

  /**
   * Get the number of objects stored in the cache.
   *
   * @return the number of objects
   */
  public int getSize()  {
    return idIndex.size();     // same for all indexes
  }

  /**
   * Retrieves a PDO via cache.
   *
   * @param <C> the comparable type
   * @param index the cache index to use
   * @param context the domain context
   * @param key the Comparable used as a key
   * @param loadIfMissing true if the object should be loaded from storage if not in cache
   * @return the object or null if no such object
   */
  public <C extends Comparable<? super C>> T select(
    PdoCacheIndex<T,C> index, DomainContext context, C key, boolean loadIfMissing)  {

    addIndexIfNotAssigned(index);
    return select(index, context, key, loadIfMissing, 0);
  }

  /**
   * Retrieves a PDO via the cache.<br>
   * Loads from storage if not in cache.
   *
   * @param <C> the Comparable class
   * @param index the cache index to use
   * @param context the domain context
   * @param key the Comparable used as a key
   *
   * @return the object or null if no such object
   */
  public <C extends Comparable<? super C>> T select(PdoCacheIndex<T,C> index, DomainContext context, C key)  {
    return select(index, context, key, true);
  }

  /**
   * Retrieves a PDO by ID.
   *
   * @param context the domain context
   * @param id the object-ID
   * @param loadIfMissing true if the object should be loaded from storage if not in cache
   *
   * @return the object or null if no such object
   */
  public T select(DomainContext context, long id, boolean loadIfMissing)  {
    T pdo;
    if (id > 0) {
      pdo = select(idIndex, context, id, loadIfMissing);
      if (pdo == null && loadIfMissing) {
        // this is usually an application error, since the ID is obviously wrong, or database/cache corrupted
        LOGGER.warning("no such {0} with ID={1}, context=\"{2}\"", clazz.getName(), id, context.toDiagnosticString());
      }
    }
    else {
      pdo = null;
    }
    return pdo;
  }

  /**
   * Retrieves a PDO by ID.<br>
   * Always loads if missing in the cache.
   *
   * @param context the domain context
   * @param id the object-ID
   *
   * @return the object or null if no such object
   */
  public T select(DomainContext context, long id)  {
    return select(context, id, true);
  }

  /**
   * Retrieves a range of objects from the cache.<br>
   * The method loads or updates a list of all objects for the given context.<br>
   * If caching is disabled, a {@link PdoCacheException} is thrown, since there
   * is no way to emulate the subset selection w/o an index.
   *
   * @param <C> the Comparable class
   * @param index the cache index to use
   * @param context the domain context
   * @param fromKey the start of range
   * @param fromInclusive {@code true} if the low endpoint is to be included in the returned view
   * @param toKey the end of range
   * @param toInclusive {@code true} if the high endpoint is to be included in the returned view
   * @return the list of objects
   */
  public <C extends Comparable<? super C>> List<T>
         select(PdoCacheIndex<T,C> index, DomainContext context, C fromKey, boolean fromInclusive, C toKey, boolean toInclusive) {

    addIndexIfNotAssigned(index);
    return select(index, context, fromKey, fromInclusive, toKey, toInclusive, 0);
  }

  /**
   * Retrieves all objects for a domain context via this cache.
   *
   * @param context the domain context
   * @return the list of objects
   */
  public List<T> selectAll(DomainContext context) {
    return selectAll(context, 0);
  }

  /**
   * Retrieves the objects for a domain context via cache for the given IDs.<br>
   * Will replace expired objects.
   *
   * @param context the domain context
   * @param ids the object IDs
   * @return the list of objects
   */
  public List<T> selectAny(DomainContext context, Collection<Long> ids) {
    List<T> list = new ArrayList<>();
    for (Long id : ids) {
      T pdo = select(context, id);
      if (pdo != null) {
        list.add(pdo);
      }
    }
    return list;
  }

  /**
   * Removes all objects and lists for a given session.
   *
   * @param session the session (probably closed)
   */
  public void removeObjectsForSession(Session session)  {
    if (!isReadOnly()) {
      synchronized(this)  {
        // remove all objects for the given session
        for (PdoCacheIndex<T,?> index: indexes)  {
          index.removeObjectsForSession(session);
        }

        // create the list of all contexts belonging to the given session
        List<DomainContext> sessionContexts = new ArrayList<>();
        for (DomainContext context: saLists.keySet()) {
          if (context.getSession() == session)  {  // "==" is ok here
            sessionContexts.add(context);
          }
        }

        // remove all lists for those contexts
        for (DomainContext context: sessionContexts) {
          saLists.remove(context);
        }
      }
    }
  }

  /**
   * Gets the preloaded content for this cache.<br>
   * Only for preloading, read-only caches!
   * <p>
   * Throws a {@link PdoCacheException} if caching is disabled or the cache is not a preloading read-only cache.
   *
   * @param context the domain context
   * @return the preloaded content, never null
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public synchronized PdoCachePreloadContent<T> getPreloadContent(DomainContext context) {
    assertPreloadingReadOnly();
    List<T> pdoList = refreshList(context);
    if (pdoList == null) {
      throw new PdoCacheException(this + ": caching is disabled");
    }
    PdoCachePreloadContent.TableInfo<T>[] tableInfos = new PdoCachePreloadContent.TableInfo[serialInfos.size()];
    int i = 0;
    for (Map.Entry<String, SerialInfo> serialInfoEntry : serialInfos.entrySet()) {
      SerialInfo serialInfo = serialInfoEntry.getValue();
      tableInfos[i++] = new PdoCachePreloadContent.TableInfo<>(
        serialInfo.tableName, serialInfo.pdoClass,
        new IdSerialTuple(serialInfo.modificationId, serialInfo.minTableSerial));
    }
    return new PdoCachePreloadContent<>(clazz, pdoList, tableInfos);
  }

  /**
   * Applies the given preload content to this cache.<br>
   * Only for preloading, read-only, uninitialized caches!
   *
   * @param context the domain context
   * @param preloadContent the preload content from the remote server
   * @return the number of objects loaded from the preload content
   */
  public synchronized int applyPreloadContent(DomainContext context, PdoCachePreloadContent<T> preloadContent) {
    assertPreloadingReadOnly();
    if (isInitialized()) {
      throw new PdoCacheException(this + ": cache is already initialized");
    }
    if (preloadContent.pdoClass() != clazz) {
      throw new PdoCacheException(this + ": preload content for wrong class: " + preloadContent.pdoClass());
    }

    // create the serialInfos first with the mintableserial from the preload content (don't ask the ModificationTracker!)
    for (PdoCachePreloadContent.TableInfo<T> tableInfo : preloadContent.tableInfo()) {
      serialInfos.put(tableInfo.tableName(), new SerialInfo(tableInfo.tableName(), tableInfo.pdoClass(), tableInfo.idSerial()));
    }
    // add the PDOs to the cache, all indexes, and update the serial infos
    return createAndAddSelectAllList(processContext(context), preloadContent.pdoList()).list.size();
  }

  /**
   * Returns whether this cache is initialized.<br>
   *
   * @return true if cache is initialized, false otherwise
   */
  public synchronized boolean isInitialized() {
    return !serialInfos.isEmpty();
  }


  /**
   * Asserts that the cache is a preloading read-only cache.<br>
   */
  protected void assertPreloadingReadOnly() {
    if (!isPreloading()) {
      throw new PdoCacheException(this + ": is not a preloading cache");
    }
    if (!isReadOnly()) {
      throw new PdoCacheException(this + ": is not a read-only cache");
    }
  }

  /**
   * Adds an index if not already added.
   *
   * @param index the index to add
   */
  protected void addIndexIfNotAssigned(PdoCacheIndex<T,?> index)  {
    if (!index.isAssignedToCache(this)) {
      addIndex(index);
    }
  }

  /**
   * Walks through all objects in the cache.<br>
   * Because the objects may live in different domain-contexts (and different
   * sessions, if not readonly), PDOs with the same id may exist more than
   * once in the cache.
   *
   * @param verifyKey true if the cache key should be verified
   * @param consumer the consumer for each PDO
   */
  protected void walk(boolean verifyKey, Consumer<T> consumer)  {
    idIndex.walk(verifyKey, consumer);
  }

  /**
   * Adds an object to the cache.<p>
   * Can be used by apps to add an object explicitly.
   * Nothing is added if at least one of its keys is already in the cache.
   * Indexes whose key evaluates to {@code null} for this object (filtered indexes) are skipped.
   *
   * @param pdo is the PDO to add
   * @return true if added, false if the object is already in the cache or caching is disabled
   */
  protected boolean add(T pdo)  {
    if (enabled && allEnabled)  {
      synchronized (this)  {
        shrinkCache();    // optionally shrink the cache
        // check all indexes first, so that nothing must be rolled back
        for (PdoCacheIndex<T,?> index: indexes)  {
          if (index.containsKeyOf(pdo)) {
            return false;
          }
        }
        for (PdoCacheIndex<T,?> index: indexes)  {
          index.add(pdo);   // filtered indexes skip the PDO if its key is null
        }
        pdo.getPersistenceDelegate().markCacheAccess();
        updateSerialInfo(pdo);
        return true;
      }
    }
    else  {
      return false; // not added cause disabled
    }
  }

  /**
   * Removes an object from the cache.<br>
   * Can be used by apps to remove an object explicitly.
   * Does NOT remove the object from any list!
   *
   * @param pdo the PersistentDomainObject to remove
   * @return true if removed, false if the object is not in cache
   */
  protected synchronized boolean remove(T pdo)  {
    // remove from indexes
    boolean rv = false;
    for (PdoCacheIndex<T,?> index: indexes)  {
      rv |= index.remove(pdo);
    }
    return rv;
  }

  /**
   * Shrinks the cache if size limits reached and no cached lists present.
   */
  protected synchronized void shrinkCache()  {
    if (maxSize > 0 && getSize() > maxSize && saLists.isEmpty()) {
      if (strategy == PdoCacheStrategy.FORGET || keepQuota <= 0) {
        // evict the whole cache
        invalidate();
      }
      else {
        try {
          Holder<Long> millis = new Holder<>();    // to determine the duration if with logging
          LOGGER.fine(() -> {
            millis.accept(System.currentTimeMillis());
            return "shrinking " + this + ": " + printStatistics();
          });
          Set<T> objects = new TreeSet<>(
            switch (strategy) {
              case LFU -> new LFUComparator();
              case LRU -> new LRUComparator();
              default ->
                throw new PdoRuntimeException(this + ": limits reached! Consider setting maxSize to 0 for strategy " + strategy);
            });
          // add all objects to the set and sort by LRU or LFU
          walk(true, objects::add);
          // compute objects to keep
          int size = objects.size();
          int keep = maxSize * keepQuota / 100;
          if (keep >= size) {
            keep = size - 1; // special case: remove at least one!
          }
          // remove or invalidate and add?
          if (keep > size / 2) {
            // remove
            int remove = size - keep;
            for (T pdo : objects) {
              if (remove > 0) {
                for (PdoCacheIndex<T, ?> index : indexes) {
                  index.removeExisting(pdo);    // must exist!
                }
              }
              remove--;
            }
          }
          else {
            // clear the indexes and add the survivors again (the serial infos remain valid for the survivors)
            for (PdoCacheIndex<T, ?> index : indexes) {
              index.clear();
            }
            int skip = size - keep;
            for (T pdo : objects) {
              if (skip <= 0) {
                for (PdoCacheIndex<T, ?> index : indexes) {
                  index.addUnique(pdo);    // complain if not unique
                }
              }
              skip--;
            }
          }
          Long ms = millis.get();
          if (ms != null) {
            clearStatistics();
            LOGGER.fine(this + " shrunk: kept=" + getSize() + ", strategy=" + strategy +
                        ", duration=" + (System.currentTimeMillis() - ms) + "ms");
          }
        }
        catch (RuntimeException e) {
          // unique violation detected: log that and invalidate cache
          LOGGER.warning("shrinking " + this + " failed -> invalidated!\n" + printStatistics(), e);
          invalidateImpl();
        }
      }
    }
  }

  /**
   * Creates the ID index.
   *
   * @return the default index via object-ID
   */
  protected PdoCacheIndex<T, Long> createIdIndex() {
    return PdoCacheFactory.getInstance().createCacheIndex(
      clazz.getSimpleName() + ":ID",
      (context, id) -> Pdo.create(clazz, context).selectForCache(id),
      (pdo) -> pdo.getPersistenceDelegate().getId());
  }

  /**
   * Updates or creates the list of all objects for the given context.
   *
   * @param context the domain context
   * @return the list of objects found, null if caching is disabled
   */
  protected synchronized List<T> refreshList(DomainContext context) {
    if (enabled && allEnabled) {

      expireDelayed(context.getSession());
      DomainContext effectiveContext = processContext(context);
      SelectAllList<T> sal = saLists.get(effectiveContext);

      if (sal == null) {
        // list is not in cache: load from backend or remote cache
        List<T> sList = selectAllFromStorage(effectiveContext);

        if (maxSize > 0 && sList.size() > maxSize) {
          if (maxSizeHardLimit) {
            // not a PdoCacheException: the cache is consistent, so there is nothing to invalidate or retry
            throw new PdoRuntimeException(context.getSession(),
                                          this + ": maxSize=" + maxSize + " is a hard limit, requested list size is " + sList.size());
          }
          int newMaxSize = sList.size() + sList.size() / 10;    // size + 10%
          LOGGER.info("maxSize in {0} enlarged from {1} to {2}", this, maxSize, newMaxSize);
          maxSize = newMaxSize;
        }

        sal = createAndAddSelectAllList(effectiveContext, sList);
      }
      else if (sal.isExpired()) {
        // the list was handed out to the application before, so refresh a copy of it, not the list itself
        sal.copyBeforeModification();

        // the list is cached: update or append all PDOs in refreshIds
        Map<Long, T> loadedMap = new TreeMap<>();   // updated or inserted PDOs

        int loadNum = sal.refreshIds.size();
        if (loadNum == 1) {
          T pdo = Pdo.create(clazz, effectiveContext).selectForCache(sal.refreshIds.first());
          if (pdo != null) {
            loadedMap.put(pdo.getId(), pdo);
            sal.refreshIds.clear();
          }
        }
        else if (loadNum > 1) {
          for (T pdo : Pdo.create(clazz, effectiveContext).selectAnyForCache(sal.refreshIds)) {
            loadedMap.put(pdo.getId(), pdo);
            sal.refreshIds.remove(pdo.getId());
          }
        }
        // list.refreshIds now contains PDO that were removed in the meantime from storage
        sal.addRemoveIds(sal.refreshIds);

        int size = sal.list.size();
        boolean updateListInPlace = isDeleteBetterThanRebuild(size, sal.removeIds.size());

        int i = 0;
        while (i < size) {
          T pdo = sal.list.get(i); // list is an ArrayList(): get(i) is fast!
          boolean wasRemoved = sal.removeIds.contains(pdo.getId());
          if (pdo.getPersistenceDelegate().isExpired() || wasRemoved) {
            T reloadedObject = loadedMap.get(pdo.getId());
            if (reloadedObject == null) {
              if (!wasRemoved) {
                // not removed -> must load explicitly
                reloadedObject = pdo.on().getPersistenceDelegate().selectForCache(pdo.getId());
              }
              if (reloadedObject == null || !reloadedObject.getPersistenceDelegate().isCacheable()) {
                remove(pdo);
                if (updateListInPlace) {
                  sal.list.remove(i--);   // could be expensive, but should happen rarely
                  size--;
                }
                reloadedObject = null;
                LOGGER.finer(() -> this + ": object " + pdo.toGenericString() +
                                   " removed from list for context " + context.toDiagnosticString());
              }
            }
            else {
              loadedMap.remove(pdo.getId());
            }

            if (reloadedObject != null) {   // loaded via refreshIds or explicitly
              if (updateListInPlace) {
                sal.list.set(i, reloadedObject);
              }
              // replace the object in the cache as well to keep it in sync with the indexes
              remove(pdo);
              add(reloadedObject);
              updateSerialInfo(reloadedObject);
              LOGGER.finer(() -> this + ": object " + pdo.toGenericString() +
                                 " reloaded in list for context " + context.toDiagnosticString());
            }
          }
          i++;
        }

        if (!loadedMap.isEmpty()) {
          // There are inserts!
          // It is not guaranteed that Pdo#selectAll returns the PDOs sorted by ID.
          // Most databases, however, do so.
          // Since new PDOs take a higher ID than the existing ones and loadedMap is sorted by ID,
          // the resulting list will usually be sorted by ID as well.
          Collection<T> loadedEntities = loadedMap.values();
          if (updateListInPlace) {
            sal.list.addAll(loadedEntities);
          }
          loadedEntities.forEach(pdo -> {
            add(pdo);
            updateSerialInfo(pdo);
          });
        }

        sal.clearIds();

        if (!updateListInPlace) {
          // rebuild the list from the idIndex
          List<T> list = sal.list;
          list.clear();
          idIndex.walk(true, pdo -> {
            if (effectiveContext.equals(pdo.getPersistenceDelegate().getDomainContext())) {
              list.add(pdo);
            }
          });
        }
      }

      return sal.list;
    }
    return null;
  }

  /**
   * Read all PDOs from storage.
   *
   * @param context the domain context
   * @return the list of all PDOs
   */
  protected List<T> selectAllFromStorage(DomainContext context) {
    synchronized(this) {
      selectAllCount++;
    }
    return Pdo.create(clazz, context).selectAllForCache();
  }

  /**
   * Invalidate the cache and log that.
   * @param t the cause for invalidation
   */
  protected void emergencyInvalidate(Throwable t) {
    // unique violation detected: log that and invalidate cache
    invalidateImpl();
    String msg = "cache- or index-relevant data of objects were modified by the application";
    if (t != null) {
      LOGGER.warning(t.getMessage() + ":" + msg, t);
    }
    else  {
      LOGGER.warning(msg);
    }
  }

  /**
   * Process the domain context.<br>
   * If the context is a root context, it will be replaced
   * by the corresponding non-root context.
   * If the cache is readonly, the context will be replaced
   * by a session-thread-local context.<br>
   *
   * @param context the original domain context
   * @return the context to use for cache retrieval
   */
  protected DomainContext processContext(DomainContext context) {
    if (context.isRootContext()) {
      context = context.getNonRootContext();
    }
    if (isReadOnly()) {
      context = context.getThreadLocalSessionContext();
    }
    return context;
  }

  /**
   * Estimates whether deleting object by object within the list is
   * probably more efficient than rebuilding the list from the idIndex.<br>
   * A deletion will copy within the list and doesn't allocate memory.
   * Rebuilding clears the list and walks through the objects
   * of the index re-creating the list.
   * Hence, the larger the list and the more deletions, the more likely rebuilding could
   * be more efficient.
   * <p>
   * Anyway, deletions should be rare since caches are mainly used for master-data,
   * and as master-data is usually referenced by transaction-data, it cannot
   * be deleted once used due to foreign key constraints.
   *
   * @param listSize the size of the list
   * @param deleteCount the number of objects to delete
   * @return true for delete, false for rebuild
   */
  protected boolean isDeleteBetterThanRebuild(int listSize, int deleteCount) {
    return deleteCount < 3 || listSize * deleteCount < 100000;
  }


  /**
   * Creates and adds a new SelectAllList for the given context and PDOs.
   * <ul>
   *   <li>adds each cacheable PDO to all indexes</li>
   *   <li>updates the serial infos</li>
   * </ul>
   *
   * @param effectiveContext the effective domain context
   * @param pdoList the PDOs to add
   * @return the new SelectAllList
   */
  private SelectAllList<T> createAndAddSelectAllList(DomainContext effectiveContext, List<T> pdoList) {
    SelectAllList<T> sal = new SelectAllList<>(pdoList.size());

    // add cacheable objects to all indexes
    for (T pdo : pdoList) {
      PersistentObject<T> pd = pdo.getPersistenceDelegate();
      if (pd.isCacheable()) {
        sal.list.add(pdo);
        for (PdoCacheIndex<T, ?> index : indexes) {
          index.add(pdo);   // this will replace the object if already in the index
        }
        pd.markCacheAccess();
        updateSerialInfo(pdo);
      }
    }

    // add to the List of lists
    DomainContext clonedContext = effectiveContext.clone();  // clone to avoid further modification
    saLists.put(clonedContext, sal);
    return sal;
  }

  /**
   * Implementation with nesting level to detect loops.
   *
   * @param context the domain context
   * @param nestingLevel nesting level, 0 = first invocation
   * @return the list of objects
   */
  private List<T> selectAll(DomainContext context, int nestingLevel) {
    List<T> list;
    synchronized (this) {
      if (enabled && allEnabled) {
        // Outside the try-block: a cache pollution detected by the delayed expiration
        // must reach the caller to roll back the transaction, not be retried.
        expireDelayed(context.getSession());
      }
      try {
        list = refreshList(context);
      }
      catch (PdoCacheException e) {
        // unique violation or key change detected: log that and invalidate cache
        if (nestingLevel > 0) {
          // exception does not vanish -> severe error
          emergencyInvalidate(null);
          throw e;
        }
        emergencyInvalidate(e);
        // start over
        return selectAll(context, ++nestingLevel);
      }
    }
    if (list != null) {
      // wrap the list to allow operations like sorting without affecting the cached lists.
      return new CopyOnWriteList<>(list);
    }
    // caching disabled: read from storage
    return selectAllFromStorage(context);
  }

  /**
   * Implementation with nesting level to detect loops.
   *
   * @param <C> the comparable type
   * @param index the cache index to use
   * @param context the domain context
   * @param key the Comparable used as a key
   * @param loadIfMissing true if the object should be loaded from storage if not in cache
   * @param nestingLevel nesting level, 0 = first invocation
   * @return the object or null if no such object
   */
  private synchronized <C extends Comparable<? super C>> T select(
    PdoCacheIndex<T, C> index, DomainContext context, C key, boolean loadIfMissing, int nestingLevel) {

    T pdo;

    if (enabled && allEnabled)  {

      boolean listLoaded = false;
      DomainContext effectiveContext = processContext(context);
      if (saLists.containsKey(effectiveContext) || isPreloading()) {
        refreshList(context);   // invokes expireDelayed as well
        listLoaded = true;
      }
      else {
        expireDelayed(context.getSession());
        shrinkCache();          // evict according to the strategy if maxSize is exceeded
      }

      try {
        PdoCacheIndex<T,C>.CacheResult result = index.get(context, key);
        if (result == null) {
          return null;          // illegal key, e.g., null (already logged by the index)
        }
        pdo = result.getPdo();

        if (pdo != null && pdo.getPersistenceDelegate().isExpired() && !listLoaded) {
          // pdo is expired and cache is in non-list mode (for this context)
          remove(pdo);
          if (LOGGER.isFinerLoggable()) {
            LOGGER.finer("expired object " + pdo.toGenericString() +
                         " context=\"" + pdo.getPersistenceDelegate().getDomainContext().toDiagnosticString() +
                         "\" removed from " + this);
          }
          pdo = null;   // treat as if not in cache
        }

        if (pdo == null && loadIfMissing && !listLoaded && !index.isMissing(result.getCacheKey()))  {
          // not in cache and no list loaded and not known to be missing: get it from the database or remote cache
          pdo = index.select(context, key);
          if (pdo != null) {
            index.removeFromMissing(result.getCacheKey());
            if (pdo.getPersistenceDelegate().isCacheable()) {
              selectCount++;
              // add to all indexes
              for (PdoCacheIndex<T,?> ndx: indexes)  {
                ndx.addUnique(pdo);     // complain if not unique
              }
              updateSerialInfo(pdo);   // update tableSerial if higher
              if (LOGGER.isFinerLoggable()) {
                LOGGER.finer("added object " + pdo.toGenericString() +
                             " context=\"" + pdo.getPersistenceDelegate().getDomainContext().toDiagnosticString() +
                             "\" to " + this);
              }
            }
            else {
              selectNotCacheableCount++;
            }
          }
          else {
            // not in the database
            index.addToMissing(result.getCacheKey());
            selectMissingCount++;
          }
        }

        if (pdo != null)  {
          pdo.getPersistenceDelegate().markCacheAccess();
        }
      }
      catch (PdoCacheException e)  {
        // unique violation or key change detected: log that and invalidate cache
        if (nestingLevel > 0) {
          // exception does not vanish -> severe error
          emergencyInvalidate(null);
          throw e;
        }
        emergencyInvalidate(e);
        // start over
        return select(index, context, key, loadIfMissing, ++nestingLevel);
      }
    }
    else  {
      // caching disabled: always read from storage
      pdo = index.select(context, key);
    }

    return pdo;
  }

  /**
   * Implementation with nesting level to detect loops.
   *
   * @param <C> the Comparable class
   * @param index the cache index to use
   * @param context the domain context
   * @param fromKey the start of range
   * @param fromInclusive {@code true} if the low endpoint is to be included in the returned view
   * @param toKey the end of range
   * @param toInclusive {@code true} if the high endpoint is to be included in the returned view
   * @return the list of objects
   */
  private synchronized <C extends Comparable<? super C>> List<T> select(
    PdoCacheIndex<T, C> index, DomainContext context,
    C fromKey, boolean fromInclusive,
    C toKey, boolean toInclusive,
    int nestingLevel) {

    if (refreshList(context) != null) {
      try {
        List<T> list = new ArrayList<>();
        index.walk(context, fromKey, fromInclusive, toKey, toInclusive, list::add);
        return list;
      }
      catch (PdoCacheException e) {
        if (nestingLevel > 0) {
          // exception does not vanish -> severe error
          emergencyInvalidate(null);
          throw e;
        }
        emergencyInvalidate(e);
        // start over
        return select(index, context, fromKey, fromInclusive, toKey, toInclusive, ++nestingLevel);
      }
    }
    throw new PdoCacheException(context.getSession(), this + ": caching is disabled");
  }

  /**
   * Expires objects by examining the tableSerial.<br>
   * Notice: invoke from within synchronized block only!
   *
   * @param session the session to use
   * @param tableName the table name
   * @param oldSerial the highest tableserial of the objects kept in the cache
   * @param maxSerial is the max. tableSerial to scan for
   */
  private void expireImpl(Session session, String tableName, long oldSerial, long maxSerial) {
    SerialInfo serialInfo = serialInfos.get(tableName);
    if (serialInfo != null) {
      if (session.isTxRunning()) {
        // check if there are tableserials expired within this running tx
        if (serialInfo.isExpiredInTx(session, oldSerial, maxSerial)) {
          // this exception will cause a rollback of the current transaction
          serialInfo.tsInTx.remove(session);
          throw new PdoCacheException(session, "pollution of " + this +
                " prevented: attempt to persist modified PDOs and select them from cache within the same transaction")
                  .evaluate();
        }
      }
      serialInfo.tsInTx.keySet().removeIf(s -> !s.isTxRunning());

      try {
        if (oldSerial < serialInfo.minTableSerial) {
          oldSerial = serialInfo.minTableSerial;    // align to minTableSerial
        }
        if (maxSerial > oldSerial) {
          expireByExpirationInfo(serialInfo.tableName,
                                 Pdo.create(serialInfo.pdoClass, session)
                                    .getPersistenceDelegate()
                                    .getExpiredTableSerials(oldSerial, maxSerial),
                                 maxSerial);
        }
      }
      catch (RuntimeException e) {
        LOGGER.logStacktrace(Logger.Level.WARNING, e);
        invalidateImpl();
      }
    }
  }

  /**
   * Expires or removes the objects in the cache.<br>
   * Expiration tuples with a negative ID indicate a deletion.
   * An id of 0 indicates a rolled back insert or update.<br>
   * If a tableserial gap is found, the whole cache is invalidated, since
   * we must assume either some malfunction or that we're running in local-client mode,
   * which does not support deletion- and rollback detection.
   *
   * @param tableName the table name
   * @param expirations the expiration tuples (id/tableserial), sorted by abs(tableSerial)
   * @param curSerial is the current table serial (from Modification-table), 0 = don't check
   */
  private void expireByExpirationInfo(String tableName, Collection<IdSerialTuple> expirations, long curSerial)  {
    SerialInfo serialInfo = serialInfos.get(tableName);
    if (serialInfo != null) {

      clearMissing();   // some PDO added, removed, whatever: invalidate missing PDO info

      // Build a set of IDs and check for gaps in tableSerial
      long prevSerial = serialInfo.minTableSerial;
      Set<Long> deletedIds = new HashSet<>();   // the latest delete in expirations
      Set<Long> modifiedIds = new HashSet<>();  // the latest modification in expirations

      // align minTableSerial for the next check
      if (curSerial > serialInfo.minTableSerial) {
        serialInfo.minTableSerial = curSerial;
      }

      // for the expired IDs, find the latest modification and deletion and detect possible gaps
      record Gap(long from, long to) {
        @Override
        public String toString() {
          return "[" + from + "-" + to + "]";
        }
      }

      List<Gap> gaps = null;

      for (IdSerialTuple tuple : expirations) {
        long serial = Math.abs(tuple.getSerial());
        if (serial - prevSerial > 1) {
          if (gaps == null) {
            gaps = new ArrayList<>();
          }
          gaps.add(new Gap(prevSerial + 1, serial));
        }
        prevSerial = serial;
        if (prevSerial > serialInfo.minTableSerial) {
          serialInfo.minTableSerial = prevSerial;
        }

        if (tuple.getId() != 0) {
          // not a rolled back update or insert
          if (tuple.getSerial() < 0) {
            // remove from cache
            deletedIds.add(tuple.getId());
            modifiedIds.remove(tuple.getId());
          }
          else {
            // update or insert
            modifiedIds.add(tuple.getId());
            deletedIds.remove(tuple.getId());
          }
        }
      }

      if (curSerial > 0 && curSerial - prevSerial > 0) {
        // A gap at the end of the serials detected.
        // This must be a rollback since the TableSerialExpirationBacklog gets only serials
        // up to the last committed transaction.
        // Due to modifications in the local JVM (see AbstractPersistentObject#expireCache),
        // the curSerial might be higher, since the "expected" serial is NOT rolled back.
        serialInfo.maxTableSerial = prevSerial;
        if (serialInfo.minTableSerial > serialInfo.maxTableSerial) {
          serialInfo.minTableSerial = prevSerial;
        }
        if (serialInfo.tableSerial > prevSerial) {
          // This should not happen since we have pollution detection during running transactions (see TxIdSet).
          // But one never knows...
          if (gaps == null) {
            gaps = new ArrayList<>();
          }
          LOGGER.warning("{0}: possible cache pollution detected", this);
          gaps.add(new Gap(prevSerial + 1, curSerial));
        }
      }

      if (gaps != null) {
        /*
         * Gaps were found, i.e., some objects were deleted, or modifications were rolled back
         * without notifying the TableSerialHistory, or no modified objects
         * found, or the current serial does not match the last tableserial of
         * modified objects, or there is cache pollution.
         * This could happen if the ModificationTracker is in local-client-mode.
         * In middle-tier servers, however, this indicates that something seriously went wrong.
         *
         * In any case: we must invalidate the whole cache!
         */
        boolean localClientMode = ModificationTracker.getInstance().isLocalClientMode();
        StringBuilder buf = null;
        if (!localClientMode || LOGGER.isFineLoggable()) {
          buf = new StringBuilder();
          for (Gap gap : gaps) {
            if (!buf.isEmpty()) {
              buf.append(", ");
            }
            buf.append(gap);
          }
        }
        if (localClientMode) {
          if (buf != null) {
            // this is ok in local client mode
            LOGGER.finer("{0}: gaps detected in local client mode: {1} -> invalidate ", this, buf);
          }
        }
        else {
          // but should not happen in regular mode
          LOGGER.warning("{0}: unexpected gaps detected: {1} -> invalidate ", this, buf);
        }

        invalidate();
        return;
      }

      // expire or delete objects in the cache (all contexts!)
      List<T> removedPdos = new ArrayList<>();
      walk(true, pdo -> {
        if (serialInfo.tableName.equals(pdo.getTableName())) {
          Long id = pdo.getPersistenceDelegate().getId();
          if (modifiedIds.contains(id)) {
            pdo.getPersistenceDelegate().setExpired(true);
            LOGGER.finer(() -> pdo.toGenericString() + " context=\"" +
                               pdo.getPersistenceDelegate().getDomainContext().toDiagnosticString() +
                               "\" expired in " + this);
          }
          else if (deletedIds.contains(id)) {
            removedPdos.add(pdo);
            LOGGER.finer(() -> pdo.toGenericString() + " context=\"" +
                               pdo.getPersistenceDelegate().getDomainContext().toDiagnosticString() +
                               "\" removed in " + this);
          }
        }
      });
      removedPdos.forEach(this::remove);

      // remember PDOs to load or remove on the next refreshList for existing lists
      saLists.values().forEach(sal -> {
        sal.addRefreshIds(modifiedIds);
        sal.addRemoveIds(deletedIds);
      });
    }
  }

  /**
   * Checks whether a delayed expiration has been triggered.<br>
   * If so, expire.<br>
   * Invoked from within synchronized!
   *
   * @param session the session
   */
  private void expireDelayed(Session session)  {
    if (tableSerialProvided) {
      for (SerialInfo serialInfo : serialInfos.values()) {
        if (serialInfo.expiredTableSerial > 0) {    // serialInfo.maxTableSerial is also set in this case!
          expireImpl(session, serialInfo.tableName, serialInfo.expiredTableSerial, serialInfo.maxTableSerial);
          serialInfo.expiredTableSerial = 0;
        }
      }
    }
  }

  /**
   * Updates the serial info from a PDO.
   *
   * @param pdo the PDO
   */
  private void updateSerialInfo(T pdo) {
    PersistentObject<T> pd = pdo.getPersistenceDelegate();
    SerialInfo serialInfo = serialInfos.computeIfAbsent(
      pd.getTableName(), tableName -> new SerialInfo(tableName, pdo.getEffectiveClass()));
    long tableSerial = pd.getTableSerial();
    if (tableSerial > serialInfo.tableSerial) {
      serialInfo.tableSerial = tableSerial;
    }
  }

  /**
   * Assigns an index to this cache.
   */
  private void assignIndex(PdoCacheIndex<T,?> index)  {
    index.assignCache(this);
  }

  /**
   * Resigns an index from this cache.
   */
  private void resignIndex(PdoCacheIndex<T,?> index)  {
    index.assignCache(null);
  }

  /**
   * Invalidates the cache.<br>
   * Invoked from within synchronized!
   */
  private void invalidateImpl() {
    for (PdoCacheIndex<T,?> index: indexes)  {
      index.clear();
    }
    saLists.clear();
    serialInfos.clear();
  }

  /**
   * Clears the missing PDO info in all indexes.
   */
  private void clearMissing() {
    for (PdoCacheIndex<T,?> index: indexes)  {
      index.clearMissing();
    }
  }


  // Least Recently Used Comparator
  private static class LRUComparator implements Comparator<PersistentDomainObject<?>> {
    /**
     * Compares by last access time.
     */
    @Override
    public int compare(PersistentDomainObject<?> o1, PersistentDomainObject<?> o2) {
      PersistentObject<?> p1 = o1.getPersistenceDelegate();
      PersistentObject<?> p2 = o2.getPersistenceDelegate();
      // compare access time (the newer, the higher, i.e., TreeSet begins with the oldest entry)
      int rv = Long.compare(p1.getCacheAccessTime(), p2.getCacheAccessTime());
      if (rv == 0)  {
        // take access-count
        rv = Long.compare(p1.getCacheAccessCount(), p2.getCacheAccessCount());
        if (rv == 0)  {
          rv = Long.compare(p1.getId(), p2.getId());
          if (rv == 0) {
            // this is very unlikely to happen, but multiple instances of PDOs with different contexts could
            // happily exist within the cache
            rv = p1.getDomainContext().compareTo(p2.getDomainContext());
          }
        }
      }
      return rv;
    }
  }


  // Least Frequently Used Comparator
  private static class LFUComparator implements Comparator<PersistentDomainObject<?>> {
    /**
     * Compares by access count
     */
    @Override
    public int compare(PersistentDomainObject<?> o1, PersistentDomainObject<?> o2) {
      PersistentObject<?> p1 = o1.getPersistenceDelegate();
      PersistentObject<?> p2 = o2.getPersistenceDelegate();
      // compare access count
      int rv = Long.compare(p1.getCacheAccessCount(), p2.getCacheAccessCount());
      if (rv == 0)  {
        // take access time
        rv = Long.compare(p1.getCacheAccessTime(), p2.getCacheAccessTime());
        if (rv == 0)  {
          rv = Long.compare(p1.getId(), p2.getId());
          if (rv == 0) {
            // this is very unlikely to happen, but multiple instances of PDOs with different contexts could
            // happily exist within the cache
            rv = p1.getDomainContext().compareTo(p2.getDomainContext());
          }
        }
      }
      return rv;
    }
  }

}
