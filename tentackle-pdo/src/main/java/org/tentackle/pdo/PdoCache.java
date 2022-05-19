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
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.misc.ImmutableArrayList;
import org.tentackle.security.SecurityFactory;
import org.tentackle.security.permissions.ReadPermission;
import org.tentackle.session.ModificationEvent;
import org.tentackle.session.ModificationEventDetail;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.Session;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Cache for {@link PersistentDomainObject}s.<br>
 *
 * The cache works both client- and server side.<br>
 * The cache can hold any number of unique indexes (see {@link PdoCacheIndex})
 * that are added (or removed) at runtime. The ID-index is mandatory
 * and automatically added.<br>
 * For inherited entities, the topmost super entity persistence implementation
 * usually provides the cache for all classes of the inheritance hierarchy.
 * However, subclasses may override and maintain their own cache, for example,
 * to add unique indexes not available in the super classes.<br>
 * The PDOs in the cache are by default immutable. However, for special purposes,
 * caches can also work with modifiable PDOs, which are then maintained per session
 * (needs significantly more memory!).<br>
 * The cache works in 2 main operating modes:
 * <ol>
 *   <li>preloading: all PDOs are loaded at once on the first access or after at least one PDO was modified in the database. This
 *   mode is particularly useful for master-data with a limited number of entities that change rather infrequently.</li>
 *   <li>per-PDO caching strategy: whenever a PDO is changed in the database, it is marked dirty in the cache and
 *   will be reloaded on next access. The following caching strategies are available:
 *      <ul>
 *        <li><em>forget:</em> the cache is completely invalidated when full</li>
 *        <li><em>lru:</em> the least recently PDO is removed</li>
 *        <li><em>lfu:</em> the least frequently PDO is removed</li>
 *      </ul>
 *      PDOs must provides a {@code tableSerial} for non-preloading caches!
 *   </li>
 * </ol>
 * The cache is also aware of object-lists and domain contexts. For example, a {@code selectAllCached} will return a
 * cached list of all objects.
 *
 * @param <T> the {@link PersistentDomainObject} class
 * @author harald
 */
public class PdoCache<T extends PersistentDomainObject<T>> {

  private static final Logger LOGGER = Logger.get(PdoCache.class);

  private static boolean allEnabled = true;         // false = all caches disabled. default is true

  /**
   * Holds the tableserial info per tablename.
   */
  private class SerialInfo {
    private final String tableName;                   // the table name
    private final Class<T> pdoClass;                  // the effective PDO class
    private long minTableSerial;                      // min tableserial to use for update check
    private long maxTableSerial;                      // max tableserial to use for update check
    private long tableSerial;                         // highest tableserial of all objects in cache
    private long expiredTableSerial;                  // > 0 if delayed expire check

    private SerialInfo(T pdo) {
      pdoClass = pdo.getEffectiveClass();
      tableName = pdo.getPersistenceDelegate().getTableName();
      minTableSerial = ModificationTracker.getInstance().getSerial(tableName);
    }
  }

  private final Class<T> clazz;                       // the PersistentDomainObject class
  private final boolean readOnly;                     // true if readonly
  private final boolean tableSerialProvided;          // true if PDOs provide a table serial
  private final boolean checkSecurity;                // true if check for read-permission
  private final List<PdoCacheIndex<T,?>> indexes;     // the indexes
  private final PdoCacheIndex<T,Long> idIndex;        // special ID-index (cannot be removed!)
  private final Set<DomainContext> preloadedContexts; // if preloading: set of contexts used in cache (null if no preloading)
  private final Map<DomainContext, ImmutableArrayList<T>> lists;    // lists for selectAll
  private final Set<List<T>> expiredLists;            // lists that contain expired objects
  private final Map<String, SerialInfo> serialInfos;  // the serial infos per table name

  private boolean enabled;                            // true if cache enabled
  private int maxSize;                                // maximum size, 0 = unlimited (default)
  private PdoCacheStrategy strategy;                  // caching strategy if maxSize != 0
  private int keepQuota;                              // percentage of entries to keep when applying caching strategy. Default is 50.
  private boolean inToString;                         // avoid recursion in logging
  private long selectCount;                           // number of single PDO selects
  private long selectMissingCount;                    // number of selects for unknown keys (PDO missing in database)
  private long selectNotCacheableCount;               // number of selects to non-cacheable PDOs
  private long selectAllCount;                        // number of selectAll (preloading cache)


  /**
   * Creates an instance of an PdoCache.
   *
   * @param clazz is the PersistentDomainObject-class managed by the cache
   * @param preload is true if preload all objects in domain context of cache
   * @param readOnly true if cache is readonly (shared)
   * @param checkSecurity true if check for read-permission
   */
  public PdoCache(Class<T> clazz, boolean preload, boolean readOnly, boolean checkSecurity) {

    this.clazz = clazz;
    this.readOnly = readOnly;
    this.checkSecurity = checkSecurity;

    indexes           = new ArrayList<>();                              // first index created below
    preloadedContexts = preload ? new HashSet<>() : null;               // no contexts preloaded so far
    lists             = new HashMap<>();                                // lists from selectAll
    expiredLists      = new HashSet<>();                                // expired lists
    enabled           = true;                                           // initially enabled
    keepQuota         = 50;                                             // keep 50% when shrinking
    serialInfos       = new HashMap<>();                                // serial infos per table name
    tableSerialProvided = Pdo.create(clazz).isTableSerialProvided();    // table serial provided (false if invalidate all if expire() invoked

    LOGGER.fine("creating cache for {0}", clazz);

    // createPdo default index by object-ID.
    idIndex = new PdoCacheIndex<>(clazz.getSimpleName() + ":ID") {

      @Override
      public Long extract(T pdo) {
        return pdo.getPersistenceDelegate().getId();
      }

      @Override
      public T select(DomainContext context, Long id) {
        try {
          return Pdo.create(clazz, context).selectForCache(id);
        }
        catch (RuntimeException ex) {
          // what can we do??
          throw new PdoCacheException("creating cache object failed for " + clazz + ", ID=" + id, ex);
        }
      }
    };

    addIndex(idIndex);
  }

  /**
   * Creates a cache without preloading.
   *
   * @param objectClazz is the PersistentDomainObject-class managed by the cache.
   */
  public PdoCache(Class<T> objectClazz)  {
    this(objectClazz, false, false, false);
  }


  @Override
  public String toString()  {
    return clazz.getName();
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
   * get their session set to null which will use the thread-local's
   * session to lazily load related objects.
   *
   * @return true if cache is readonly
   */
  public boolean isReadOnly() {
    return readOnly;
  }

  /**
   * Add an index to cache.<br>
   * The application need not invoke addIndex explicitly.
   * Assigning the index to the cache will be done automatically on
   * its first use. This is known as deferred index assignment and
   * has the advantage that the index is managed by the cache
   * if really used by the application.
   * However, the application can assign the index explicitly.
   * This will ensure that the index cannot be assigned to another
   * cache accidentally.
   *
   * @param index is the index to add
   */
  public synchronized void addIndex(PdoCacheIndex<T,?> index)  {
    assignIndex(index);       // assign index to cache
    index.clear();            // clear index for sure
    index.clearStatistics();
    indexes.add(index);       // add index to List (this will also build the index-map for already added objects)
    LOGGER.fine("{0}: adding {1}", this, index);
    if (index != idIndex) {
      // if not the ID-index: add objects in cache so far
      try {
        for (T object: idIndex.getObjects()) {
          index.addUnique(object);
        }
      }
      catch (PdoCacheException e)  {
        // unique violation detected: log that and invalidate cache
        emergencyInvalidate(e);
      }
    }
  }

  /**
   * Adds an index if not already added.
   *
   * @param index the index to add
   */
  public void addIndexIfNotAssigned(PdoCacheIndex<T,?> index)  {
    if (!index.isAssignedToCache(this)) {
      addIndex(index);
    }
  }

  /**
   * removes an index.
   *
   * @param index to remove
   */
  public void removeIndex(PdoCacheIndex<T,?> index) {
    resignIndex(index);       // resign index from cache
    indexes.remove(index);    // remove from index list
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
    if (preloadedContexts != null) {
      buf.append(", contexts=").append(preloadedContexts.size());
    }
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
   *
   * @return the indexes
   */
  public List<PdoCacheIndex<T,?>> getIndexes()  {
    return new CopyOnWriteList<>(indexes);
  }

  /**
   * Forces the cache to be cleared for next access.
   */
  public synchronized void invalidate() {
    LOGGER.fine(() -> "invalidating cache " + this + ": " + printStatistics());
    invalidateImpl();
  }

  /**
   * Expires object in cache with an ID of a given set.
   *
   * @param tableName the table name
   * @param expireSet is an array of long-pairs (id/tableserial), sorted by tableSerial, representing the objects to set expired
   * @param curSerial is the current table serial (from Modification-table), 0 = don't check
   */
  public synchronized void expireByExpirationInfo(String tableName, List<IdSerialTuple> expireSet, long curSerial)  {
    SerialInfo serialInfo = serialInfos.get(tableName);
    if (serialInfo != null) {

      clearMissing();   // some PDO added, removed, whatever: invalidate missing PDO info

      // Build a set of IDs and check for gaps in tableSerial
      boolean gapFound = false;
      long prevSerial = -1;
      Set<Long> idSet = new HashSet<>();

      // detect gaps and align minTableSerial for the next check
      if (curSerial > serialInfo.minTableSerial) {
        serialInfo.minTableSerial = curSerial;
      }

      for (IdSerialTuple idSer : expireSet) {
        if (prevSerial != -1 && idSer.getSerial() - prevSerial > 1) {
          gapFound = true;
        }
        idSet.add(idSer.getId());
        prevSerial = idSer.getSerial();
        if (prevSerial > serialInfo.minTableSerial) {
          serialInfo.minTableSerial = prevSerial;
        }
      }

      if (curSerial > 0 && curSerial - prevSerial > 1) {
        /*
         * if curSerial given:
         * no expirations at all (prevSerial == -1) or
         * a gap indicates that some objects have been deleted
         */
        gapFound = true;
      }

      if (gapFound) {
        /*
         * A gap was found, i.e. some objects have been deleted, or no modified objects
         * found at all or the current serial does not match the last tableserial of
         * modified objects:
         *
         * a) objects have been deleted. Deleted objects cannot appear in idSet
         *    simply because they aren't in the table anymore.
         *
         * b) a rare condition when an object has been modified (or appended) from
         *    another application, and we loaded that object into cache and got the
         *    expiration notice too late.
         *
         * It's always safe to assume (a).
         * Because we cannot tell which of our objects have been deleted,
         * we must invalidate the whole cache.
         * A solution to this problem might be examining the History or ModificationLog, but
         * this isn't worth the effort.
         */
        LOGGER.finer("{0}: some objects deleted -> invalidate all", this);
        invalidate();
        return;
      }

      // expire objects in cache and count how many such objects found
      Set<Long> foundSet = new HashSet<>();     // holds the IDs that were found in cache
      for (T object : getObjects()) {   // check all objects in cache
        if (serialInfo.tableName.equals(object.getTableName())) {
          Long id = object.getPersistenceDelegate().getId();
          if (idSet.contains(id)) {
            object.setExpired(true);
            foundSet.add(id);
            if (!inToString && LOGGER.isFinerLoggable()) {
              inToString = true;    // avoid recursion cause context.toString
              LOGGER.finer(object.toGenericString() + " context=" +
                           object.getPersistenceDelegate().getDomainContext().toDiagnosticString() + " expired in " + this);
              inToString = false;
            }
          }
        }
      }

      int foundSetSize = foundSet.size();
      boolean expireAllLists = foundSetSize < idSet.size();  // not all IDs found in cache

      int numLists = lists.size();
      if (!expireAllLists && numLists > 0) {
        // all Objects in expireSet were found in cache.
        // There are lists: check that all lists have the same size.
        // If they have the same size, it is guaranteed that they contain the same IDs
        // because all objects of all lists are in getObjects() as well.
        int size = -1;
        for (List<T> list : lists.values()) {
          if (size == -1) {
            size = list.size();
          }
          else if (size != list.size()) {
            expireAllLists = true;
            break;
          }
        }
      }

      if (expireAllLists) {
        if (isPreloading()) {
          // this is a preloading cache: invalidate all
          LOGGER.finer("{0}: some uncached objects expired or objects deleted in preloaded cache -> invalidate all", this);
          invalidate();
        }
        else {
          lists.clear();
          LOGGER.finer("{0}: some uncached objects expired or objects deleted -> all lists expired", this);
        }
      }
      else {
        /*
         * _ALL_ objects in idSet were in cache and are now marked expired
         * because there is not a single uncached and all lists are the same size.
         * Thus, we can keep all lists.
         * However, we should mark the lists with an expire-flag, i.e.
         * on the next selectAllInContext() the expired objects in the lists
         * are reloaded.
         * Notice: because there is one list per domain context it is guaranteed
         * that an object is part of no more than one list at a time.
         */
        expiredLists.addAll(lists.values());  // set all lists expired
      }
    }
  }

  /**
   * Expire dirty objects in cache.<br>
   * If the cache does not provide a tableSerial, or it is preloading,
   * it will be invalidated (i.e. all objects removed).
   * Otherwise, no objects are removed and those that were changed
   * in the persistence layer will be marked expired.
   * This is done by obtaining the IDs of all objects in the db-table with a
   * tableSerial &gt; cache.tableSerial.
   *
   * @param session the session to use, null if delay expiration check until next select
   * @param tableName the tablename
   * @param maxSerial is the max. tableSerial to scan for updates, 0 to invalidate all
   */
  public synchronized void expire(Session session, String tableName, long maxSerial)  {

    if (maxSerial == 0 || isPreloading() || !tableSerialProvided) {
      LOGGER.fine(() -> "cache invalidation requested on " + this + (session == null ? ", delayed" : (", " + session)));
      invalidateImpl();
      return;
    }

    SerialInfo serialInfo = serialInfos.get(tableName);

    if (serialInfo != null && maxSerial > serialInfo.maxTableSerial) {

      // remember upper bound of all requests
      serialInfo.maxTableSerial = maxSerial;

      // table provides a tableSerial
      if (serialInfo.expiredTableSerial == 0) {   // if not already triggered
        if (session != null) {
          // run expire check immediately
          expireObjects(session, tableName, serialInfo.tableSerial, serialInfo.maxTableSerial);
        }
        else {
          // delay expire check
          serialInfo.expiredTableSerial = serialInfo.tableSerial; // set lower bound requested (once!)
        }
        LOGGER.fine(() -> "expire requested on " + this + ", tableSerial=" + serialInfo.tableSerial + "/" + maxSerial +
                          (session == null ? ", delayed" : (", " + session)));
      }
      else {
        LOGGER.fine(() -> "expire requested on " + this + ", tableSerial=" + serialInfo.tableSerial + "/" + maxSerial +
                          (session == null ? ", delayed" : (", " + session)) +
                          " -> ignored because already requested for tableSerial=" +
                          serialInfo.expiredTableSerial + "/" + serialInfo.maxTableSerial);
      }
    }
  }

  /**
   * Same as expire but delayed until the next select.
   *
   * @param event the modification event
   */
  public void expire(ModificationEvent event)  {
    for (ModificationEventDetail detail : event.getDetails()) {
      expire(null, detail.getName(), detail.getSerial());
    }
  }

  /**
   * Shrinks the cache.<br>
   * Invoked when the cache reaches its limits.
   */
  public void shrinkCache()  {
    if (strategy == PdoCacheStrategy.FORGET || keepQuota <= 0) {
      // FORGET
      invalidate();
    }
    else {
      synchronized (this)  {
        try {
          long millis = 0;    // to determine the duration if with logging
          if (LOGGER.isFineLoggable()) {
            millis = System.currentTimeMillis();
            LOGGER.fine("shrinking cache " + this + ": " + printStatistics());
          }
          Set<T> objects = new TreeSet<>(strategy == PdoCacheStrategy.LFU ? new LFUComparator() : new LRUComparator());
          // add all objects to the set and sort by LRU or LFU
          objects.addAll(getObjects());
          // compute objects to keep
          int size = objects.size();
          int keep = maxSize * keepQuota / 100;
          if (keep >= size) {
            keep = size - 1; // special case: remove at least one!
          }
          // remove or invalidate and add?
          if (keep > size/2)  {
            // remove
            int remove = size - keep;
            for (T pdo: objects)  {
              if (remove > 0)  {
                for (PdoCacheIndex<T,?> index: indexes)  {
                  index.removeExisting(pdo);    // must exist!
                }
              }
              remove--;
            }
          }
          else  {
            // invalidate and add again
            invalidateImpl();
            int skip = size - keep;
            for (T pdo: objects)  {
              if (skip <= 0)  {
                // add object
                for (PdoCacheIndex<T,?> index: indexes)  {
                  index.addUnique(pdo);    // complain if not unique
                }
              }
              skip--;
            }
          }
          if (LOGGER.isFineLoggable()) {
            clearStatistics();
            LOGGER.fine(this + ", kept=" + getSize() + ", strategy=" + (strategy == PdoCacheStrategy.LFU ? "LFU" : "LRU") +
                    ", duration=" + (System.currentTimeMillis() - millis) + "ms");
          }
        }
        catch (RuntimeException e)  {
          // unique violation detected: log that and invalidate cache
          LOGGER.warning("shrinking cache " + this + " failed -> invalidated!\n" + printStatistics(), e);
          invalidateImpl();
        }
      }
    }
  }

  /**
   * Get the number of objects stored in cache.
   *
   * @return the number of objects in cache.
   */
  public int getSize()  {
    return idIndex.size();     // same for all indexes
  }

  /**
   * Gets all objects in cache.<br>
   * Because the objects may live in different domain-contexts (and different
   * sessions, if not readonly!) this method should be used with great care in apps
   * as it returns the objects "as is", i.e. without checking for expiration.
   * Better use selectAll or select( ... fromKey, toKey).
   *
   * @param tableName the table name, null if not restricted (multi-table inheritance)
   * @param verifyKey true if the cache key should be verified
   * @return the list of objects
   */
  public synchronized List<T> getObjects(String tableName, boolean verifyKey)  {
    try {
      List<T> pdoList = idIndex.getObjects(verifyKey);
      return tableName == null ? pdoList :
             pdoList.stream().filter(pdo -> tableName.equals(pdo.getPersistenceDelegate().getTableName())).toList();
    }
    catch (PdoCacheException e)  {
      emergencyInvalidate(e);
      // start over
      return getObjects(tableName, verifyKey);    // empty Collection
    }
  }

  /**
   * Gets all PDOs in cache.<br>
   *
   * @return the list of objects
   * @see #getObjects(String, boolean)
   */
  public List<T> getObjects()  {
    return getObjects(null, true);
  }

  /**
   * Removes all PDOs with {@code isCacheable() == false}.<br>
   * Useful if the cacheable attribute changes temporarily.
   *
   * @return the number of objects removed
   */
  public synchronized int removeNonCacheables() {
    int count = 0;
    try {
      for (Iterator<T> iter = getObjects().iterator(); iter.hasNext();) {
        T pdo = iter.next();
        if (!pdo.getPersistenceDelegate().isCacheable()) {
          // remove from indexes
          for (PdoCacheIndex<T,?> index: indexes)  {
            index.removeExisting(pdo);   // complain if vanished!
          }
          iter.remove();    // remove from collection
          count++;
        }
      }
      LOGGER.fine("{0}, {1} non-cacheables removed", this, count);
    }
    catch (PdoCacheException e)  {
      // unique violation detected: log that and invalidate cache
      count = getObjects().size();
      emergencyInvalidate(e);
    }
    return count;
  }

  /**
   * Retrieves a PDO via cache.
   *
   * @param <C> the comparable type
   * @param index the cache index to use
   * @param context the domain context
   * @param key is the Comparable used as a key
   * @param loadIfMissing is true if the object should be loaded from storage if not in cache
   *
   * @return the object or null if no such object
   */
  public <C extends Comparable<? super C>> T select(PdoCacheIndex<T,C> index, DomainContext context, C key, boolean loadIfMissing)  {
    return select(index, context, key, loadIfMissing, 0);
  }

  /**
   * Retrieve PDO via cache.<br>
   * Load from storage if not in cache.
   *
   * @param <C> the Comparable class
   * @param index the cache index to use
   * @param context the domain context
   * @param key is the Comparable used as a key
   *
   * @return the object or null if no such object
   */
  public <C extends Comparable<C>> T select(PdoCacheIndex<T,C> index, DomainContext context, C key)  {
    return select(index, context, key, true);
  }

  /**
   * Retrieves a PDO by ID.
   *
   * @param context the domain context
   * @param id is the object-ID
   * @param loadIfMissing is true if the object should be loaded from storage if not in cache
   *
   * @return the object or null if no such object
   */
  public T select(DomainContext context, long id, boolean loadIfMissing)  {
    return id > 0 ? select(idIndex, context, id, loadIfMissing) : null;
  }

  /**
   * Retrieves a PDO by ID.<br>
   * Always loads if missing in cache.
   *
   * @param context the domain context
   * @param id is the object-ID
   *
   * @return the object or null if no such object
   */
  public T select(DomainContext context, long id)  {
    return select(context, id, true);
  }

  /**
   * Adds an object to the cache.<p>
   * Can be used by apps to add an object explicitly.
   * Will roll back if object (i.e. at least one key) is
   * already in cache.
   * <p>
   * Notice: if the cache is preloading the preloading info should be updated
   * via {@link #updateContextInfo()}.
   *
   * @param pdo is the PDO to add
   * @return true if added, false if object already in cache
   */
  public boolean add(T pdo)  {
    /*
     * (notice: no initializeMinTableSerial() because we cannot guarantee that
     * the table hasn't been updated between fetching the object and invocation
     * of this method)
     */
    if (enabled && allEnabled)  {
      synchronized (this)  {
        if (maxSize > 0 && getSize() > maxSize) {
          shrinkCache();
        }
        // add to indexes
        boolean uniqueViolation = false;
        int i = 0;
        while (i < indexes.size())  {
          if (!indexes.get(i).add(pdo))  {
            uniqueViolation = true;
            break;
          }
          ++i;
        }
        if (uniqueViolation) {
          // unique violation: rollback
          while(--i >= 0) {
            indexes.get(i).remove(pdo);
          }
          return false;
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
   * Removes an object from cache.<br>
   * Can be used by apps to remove an object explicitly.
   * Does NOT remove the object from any list!
   *
   * @param pdo is the PersistentDomainObject to remove
   * @return true if removed, false if object not in cache
   */
  public synchronized boolean remove(T pdo)  {
    // remove from indexes
    boolean rv = false;
    for (PdoCacheIndex<T,?> index: indexes)  {
      rv |= index.remove(pdo);
    }
    return rv;
  }

  /**
   * Removes all objects and lists for a given session.
   *
   * @param session is the db-connection (probably closed)
   */
  public void removeObjectsForSession(Session session)  {
    if (!isReadOnly()) {
      synchronized(this)  {
        // remove all objects for given session
        for (PdoCacheIndex<T,?> index: indexes)  {
          index.removeObjectsForSession(session);
        }

        // create the list of all contexts belonging to given session
        List<DomainContext> sessionContexts = new ArrayList<>();
        for (DomainContext context: lists.keySet()) {
          if (context.getSession() == session)  {  // "==" is ok here
            sessionContexts.add(context);
          }
        }

        // remove all lists for those contexts
        for (DomainContext context: sessionContexts) {
          lists.remove(context);
        }
        // remove all the corresponding preloaded contexts
        if (preloadedContexts != null)  {
          preloadedContexts.removeAll(sessionContexts);
        }
      }
    }
  }

  /**
   * Updates the list- and domain context info for preloading caches.
   * <p>
   * The method is only necessary if objects were programmatically
   * added via {@link #add(org.tentackle.pdo.PersistentDomainObject)}
   */
  public void updateContextInfo() {
    if (isPreloading()) {
      synchronized(this)  {
        // find all contexts and fill lists per context
        preloadedContexts.clear();
        lists.clear();
        for (T pdo: getObjects()) {
          DomainContext effectiveContext = processContext(pdo.getPersistenceDelegate().getDomainContext());
          DomainContext clonedContext = null;
          if (!preloadedContexts.contains(effectiveContext)) {
            clonedContext = effectiveContext.clone(); // clone to avoid further modification
            preloadedContexts.add(clonedContext);
          }
          ImmutableArrayList<T> list = lists.get(effectiveContext);
          if (list == null) {
            list = new ImmutableArrayList<>();
            list.setSimpleEqualsAndHashCode(true);
            if (clonedContext == null) {
              clonedContext = effectiveContext.clone();  // clone to avoid later modification
            }
            lists.put(clonedContext, list);
          }
          list.add(pdo);
        }
        if (isReadOnly()) {
          // make lists immutable (elements are already immutable)
          for (ImmutableArrayList<T> list: lists.values()) {
            list.setFinallyImmutable(false);
          }
        }
      }
    }
  }

  /**
   * Retrieves a range of objects from cache.<br>
   * Objects not in cache so far will NOT be loaded from storage!
   * Furthermore, expired objects will NOT be reloaded from storage and
   * expired objects will be returned in list with isExpired() == true.
   * Thus, reasonably works only if preloading is enabled or a
   * selectAll has been invoked before.
   *
   * @param <C> the Comparable class
   * @param index the cache index to use
   * @param context the domain context
   * @param fromKey starting key, inclusive
   * @param toKey ending key, exclusive
   * @return the list of objects
   */
  public synchronized <C extends Comparable<? super C>> List<T>
         select(PdoCacheIndex<T,C> index, DomainContext context, C fromKey, C toKey)  {

    addIndexIfNotAssigned(index); // make sure index is setup and belongs to this cache

    if (isReadOnly()) {
      context = context.getThreadLocalSessionContext();
    }

    List<T> list = null;
    while (list == null) {
      expireObjects(context.getSession());    // check for delayed expiration

      if (isPreloading() && !preloadedContexts.contains(context))  {
        // this will preload all objects in context for all indexes
        selectAll(context);
        // add context
        preloadedContexts.add(context.clone());   // clone to avoid further modification
      }

      try {
        list = index.getObjects(context, fromKey, toKey);
      }
      catch (PdoCacheException e)  {
        // unique violation or key change detected: log that and invalidate cache
        emergencyInvalidate(e);
        // start over
      }
    }

    return list;
  }

  /**
   * Retrieves all objects for a domain context via cache.<br>
   * Will replace expired objects.
   *
   * @param context the domain context
   * @return the list of objects
   */
  public List<T> selectAll(DomainContext context) {

    List<T> all;

    if (enabled && allEnabled) {

      synchronized (this) {
        expireObjects(context.getSession());      // check for delayed expiration
      }

      /*
       * check if context already loaded.
       */
      DomainContext effectiveContext = processContext(context);
      ImmutableArrayList<T> list = lists.get(effectiveContext);

      if (list == null) {

        // not in cache: load from backend
        List<T> sList = selectAllFromStorage(context);

        synchronized (this) {

          if (maxSize > 0 && sList.size() > maxSize) {
            int newMaxSize = sList.size() + sList.size() / 10;    // size + 10%
            LOGGER.warning("maxSize too small in " + this + ": enlarged from " + maxSize + " to " + newMaxSize);
            maxSize = newMaxSize;
          }

          list = new ImmutableArrayList<>(sList.size());    // cacheables only
          // hence immutable list, but setImmutable is not used because we return a CopyOnWriteList
          list.setSimpleEqualsAndHashCode(true);

          // add cacheable objects to all indexes
          for (T pdo : sList) {
            if (pdo.getPersistenceDelegate().isCacheable()) {
              list.add(pdo);
              for (PdoCacheIndex<T, ?> index : indexes) {
                index.add(pdo);    // don't complain if object is already in cache!
              }
              pdo.getPersistenceDelegate().markCacheAccess();
              updateSerialInfo(pdo);
            }
          }

          // add to the List of lists
          DomainContext clonedContext = effectiveContext.clone();  // clone to avoid further modification
          lists.put(clonedContext, list);

          if (isPreloading()) {
            preloadedContexts.add(clonedContext);    // if preloading enabled, add context
          }
        }
      }
      else {
        synchronized (this) {
          /*
           * list is cached. We cannot verify that there was no key change in the
           * list. However, if the list is marked to be checked for expired
           * objects, we must scan the list for expired objects.
           */
          if (expiredLists.contains(list)) {
            int size = list.size();
            int i = 0;
            while (i < size) {
              T pdo = list.get(i); // list is an ArrayList(): get(i) is fast!
              if (pdo.getPersistenceDelegate().isExpired()) {
                T reloadedObject = pdo.getPersistenceDelegate().reload();
                if (reloadedObject == null) { // vanished, i.e. object has been deleted in db
                  list.remove(i--);   // remove from list too (costly but better than loading the whole list!)
                  // notice: this will only happen for explicitly expired objects because deleted objects cannot
                  // be found by AbstractDbObject.selectExpiredTableSerials()!
                  size--;
                  if (!inToString && LOGGER.isFinerLoggable()) {
                    inToString = true;    // avoid recursion cause context.toString
                    LOGGER.finer("object " + pdo.toGenericString() +
                                 " removed from list for context " + context.toDiagnosticString());
                    inToString = false;
                  }
                }
                else {
                  // replace in list
                  list.set(i, reloadedObject);
                  // replace in cache (this keeps lists in syncObject with index cache)
                  remove(reloadedObject);
                  add(reloadedObject);
                  updateSerialInfo(reloadedObject);
                  if (!inToString && LOGGER.isFinerLoggable()) {
                    inToString = true;    // avoid recursion cause context.toString
                    LOGGER.finer("object " + reloadedObject.toGenericString() +
                                 " reloaded in list for context " + context.toDiagnosticString());
                    inToString = false;
                  }
                }
              }
              ++i;
            }
            // check done, remove it
            if (!expiredLists.remove(list)) {
              throw new PdoCacheException("expired list not found");
            }
          }
        }
      }
      // wrap list to allow operations like sorting without affecting the cached lists.
      all = new CopyOnWriteList<>(list);
    }
    else {
      // read from storage
      all = selectAllFromStorage(context);
    }

    if (checkSecurity) {
      // notice that only root-entities are allowed to be cached! (see PdoCache-wurblet)
      List<T> checked = new ArrayList<>();
      ReadPermission permission = SecurityFactory.getInstance().getReadPermission();
      for (T pdo : all) {
        if (pdo.getPersistenceDelegate().isPermissionAccepted(permission)) {
          checked.add(pdo);
        }
      }
      all = checked;
    }

    return all;
  }

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
   * Checks whether cache is enabled at all.
   *
   * @return Value of property enabled.
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Sets cache enabled or disabled.
   * When disabled the cache always reads from storage.
   *
   * @param enabled New value of property enabled.
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Returns whether this cache is a preloading one.
   *
   * @return true if this is a preloading cache
   */
  public boolean isPreloading() {
    return preloadedContexts != null;
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
   * If the size is limited the cache will invalidate if exceeded (except in selectAll)
   *
   * @param maxSize New value of property maxSize.
   */
  public synchronized void setMaxSize(int maxSize) {
    this.maxSize = maxSize;
  }

  /**
   * Set the caching strategy.<br>
   * The strategy can be changed at any time.
   *
   * @param strategy the cache eviction strategy
   */
  public void setStrategy(PdoCacheStrategy strategy) {
    this.strategy = strategy;
  }

  /**
   * @return the cache strategy. Default is {@link PdoCacheStrategy#FORGET}.
   */
  public PdoCacheStrategy getStrategy()  {
    return strategy;
  }

  /**
   * Set the keep quota in percent for the caching strategy.
   * Will be aligned to [0...100]!
   * Special: 0 is the same as FORGET
   *        100 deletes only one object (this is slow when cache gets full, don't use it!)
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
   * @return the current keep quota, default is 50
   */
  public int getKeepQuota() {
    return keepQuota;
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
   * If the context is a root context it will be replaced
   * by the corresponding non-root context.
   * If cache is readonly, the context will be replaced
   * by a session-thread-local context.<br>
   *
   * @param context the original domain context
   * @return the context to for cache retrieval
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
   * Implementation with nesting level to detect loops.
   *
   * @param <C> the comparable type
   * @param index the cache index to use
   * @param context the domain context
   * @param key is the Comparable used as a key
   * @param loadIfMissing is true if the object should be loaded from storage if not in cache
   * @param nestingLevel nesting level, 0 = first invocation
   * @return the object or null if no such object
   */
  private synchronized <C extends Comparable<? super C>> T select(PdoCacheIndex<T, C> index, DomainContext context,
                                                                  C key, boolean loadIfMissing, int nestingLevel) {

    addIndexIfNotAssigned(index);                     // make sure index is setup and belongs to this cache
    expireObjects(context.getSession());              // check for delayed expiration

    // check if cache size limits reached
    if (maxSize > 0 && getSize() > maxSize) {
      shrinkCache();
    }

    if (isPreloading()) {
      DomainContext effectiveContext = processContext(context);
      if (!preloadedContexts.contains(effectiveContext)) {
        /*
         * if not already done for this context,
         * load all objects in context for all indexes
         */
        selectAll(context);
      }
    }

    T pdo;    // returned PDO

    if (enabled && allEnabled)  {
      // check if the object is in cache
      try {
        PdoCacheIndex<T,C>.CacheResult result = index.get(context, key);
        pdo = result.getPdo();
        boolean expired = false;

        if (pdo != null && pdo.getPersistenceDelegate().isExpired())  {
          expired = true;
          remove(pdo);    // remove it from indexes (but leave in lists!)
          if (!inToString && LOGGER.isFinerLoggable()) {
            inToString = true;    // avoid recursion cause context.toString
            LOGGER.finer("expired object " + pdo.toGenericString() +
                         " context=" + pdo.getPersistenceDelegate().getDomainContext().toDiagnosticString() +
                         " removed from " + this);
            inToString = false;
          }
          pdo = null;   // treat as if not in cache
        }

        if (pdo == null && loadIfMissing && (!isPreloading() || expired) && !index.isMissing(result.getCacheKey()))  {
          // not in cache and not preloaded or expired and not known to be missing: get it from db
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
              if (!inToString && LOGGER.isFinerLoggable()) {
                inToString = true;    // avoid recursion cause of context.toString
                LOGGER.finer("added object " + pdo.toGenericString() +
                             " context=" + pdo.getPersistenceDelegate().getDomainContext().toDiagnosticString() +
                             " to " + this);
                inToString = false;
              }
            }
            else {
              selectNotCacheableCount++;
            }
          }
          else {
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
        emergencyInvalidate(e);
        if (nestingLevel > 0) {
          // exception does not vanish -> severe error
          throw e;
        }
        // start over
        return select(index, context, key, loadIfMissing, ++nestingLevel);
      }
    }
    else  {
      // always read from storage
      pdo = index.select(context, key);
    }

    if (pdo != null && checkSecurity &&
        !pdo.getPersistenceDelegate().isPermissionAccepted(SecurityFactory.getInstance().getReadPermission())) {
      pdo = null; // no read permission
    }

    return pdo;
  }

  /**
   * Expires objects by examining the tableSerial.<br>
   * Notice: invoke from within synchronized block only!
   *
   * @param session the session to use
   * @param tableName the table name
   * @param oldSerial is highest tableserial objects are kept in cache
   * @param maxSerial is the max. tableSerial to scan for, 0 = up to end
   */
  private void expireObjects(Session session, String tableName, long oldSerial, long maxSerial)  {
    SerialInfo serialInfo = serialInfos.get(tableName);
    if (serialInfo != null) {
      try {
        T pdo = Pdo.create(serialInfo.pdoClass, session);
        if (oldSerial < serialInfo.minTableSerial) {
          oldSerial = serialInfo.minTableSerial;    // align to minTableSerial
        }
        // load info of objects that have expired
        List<IdSerialTuple> expireSet = maxSerial > 0 ?
                                        pdo.getPersistenceDelegate().getExpiredTableSerials(oldSerial, maxSerial) :
                                        pdo.getPersistenceDelegate().selectExpiredTableSerials(oldSerial);
        // process expiration info and update minTableSerial
        expireByExpirationInfo(serialInfo.tableName, expireSet, maxSerial);
      }
      catch (RuntimeException e) {
        LOGGER.logStacktrace(Logger.Level.WARNING, e);
        invalidateImpl();
      }
    }
  }

  /**
   * Checks whether a delayed expiration has been triggered.<br>
   * If so, expire.<br>
   * Invoked from within synchronized!
   */
  private void expireObjects(Session session)  {
    for (SerialInfo serialInfo : serialInfos.values()) {
      if (serialInfo.expiredTableSerial > 0) {
        expireObjects(session, serialInfo.tableName, serialInfo.expiredTableSerial, serialInfo.maxTableSerial);
        serialInfo.expiredTableSerial = 0;
      }
    }
  }

  /**
   * Updates the serial info from a PDO.
   *
   * @param pdo the PDO
   */
  private void updateSerialInfo(T pdo) {
    long tableSerial = pdo.getPersistenceDelegate().getTableSerial();
    SerialInfo serialInfo = serialInfos.computeIfAbsent(pdo.getPersistenceDelegate().getTableName(), tableName -> new SerialInfo(pdo));
    if (tableSerial > serialInfo.tableSerial) {
      serialInfo.tableSerial = tableSerial;
    }
  }

  /**
   * Assigns an index to this cache.<br>
   * Note: if already added to another cache the db-errorhandler is invoked.
   */
  private void assignIndex(PdoCacheIndex<T,?> index)  {
    index.assignCache(this);
  }

  /**
   * Resigns an index from this cache.
   * If not assigned to this cache: invoke errorhandler
   */
  private void resignIndex(PdoCacheIndex<T,?> index)  {
    // notice that idIndex is private and cannot be removed!
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
    lists.clear();
    if (preloadedContexts != null)  {
      preloadedContexts.clear();
    }
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
      // compare access time (the newer, the higher, i.e. TreeSet begins with the oldest entry)
      int rv = Long.compare(o1.getPersistenceDelegate().getCacheAccessTime(),
                            o2.getPersistenceDelegate().getCacheAccessTime());
      if (rv == 0)  {
        // take access-count
        rv = Long.compare(o1.getPersistenceDelegate().getCacheAccessCount(),
                          o2.getPersistenceDelegate().getCacheAccessCount());
        if (rv == 0)  {
          // still the same: take the ID (can't be the same)
          rv = Long.compare(o1.getPersistenceDelegate().getId(),
                            o2.getPersistenceDelegate().getId());
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
      // compare access count
      int rv = Long.compare(o1.getPersistenceDelegate().getCacheAccessCount(),
                            o2.getPersistenceDelegate().getCacheAccessCount());
      if (rv == 0)  {
        // take access time
        rv = Long.compare(o1.getPersistenceDelegate().getCacheAccessTime(),
                          o2.getPersistenceDelegate().getCacheAccessTime());
        if (rv == 0)  {
          // still the same: take the ID (can't be the same)
          rv = Long.compare(o1.getPersistenceDelegate().getId(),
                            o2.getPersistenceDelegate().getId());
        }
      }
      return rv;
    }
  }

}
