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
import org.tentackle.session.Session;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Cache index.<br>
 * Holds the mapping of unique keys to PDOs.
 *
 * @param <T> the {@link PersistentDomainObject} type
 * @param <C> the {@link Comparable} type
 * @author harald
 */
public abstract class PdoCacheIndex<T extends PersistentDomainObject<T>, C extends Comparable<? super C>> {

  private static final Logger LOGGER = Logger.get(PdoCacheIndex.class);

  /**
   * The key for the cache map.<br>
   * We must compare C with domain context, because the same object may live in different
   * contexts. The domain context is essential, especially when it comes to different
   * Db (database connections)!
   * The CacheKey does not allow null context or null keys and throws PdoCacheException
   * if it detects such.
   */
  public final class CacheKey implements Comparable<CacheKey> {

    private final DomainContext context;      // the context the object lives in
    private final int sessionInstanceNumber;  // to avoid memory leaks if the application changed the session of an object
    private final C key;                      // the comparable that uniquely identifies the object

    /**
     * Creates a cache key.
     *
     * @param context the domain context
     * @param key the unique key from the PDO
     */
    private CacheKey(DomainContext context, C key) {
      if (context == null) {
        throw new PdoCacheException("null context");
      }
      if (key == null)  {
        throw new PdoCacheException("null key");
      }
      this.context = context;
      this.sessionInstanceNumber = context.getSessionInstanceNumber();
      this.key = key;
    }

    /**
     * Gets the domain context.
     *
     * @return the domain context
     */
    public DomainContext getContext() {
      return context;
    }

    /**
     * Gets the session instance number to assert session was not changed.
     *
     * @return the session instance number
     */
    public int getSessionInstanceNumber() {
      return sessionInstanceNumber;
    }

    /**
     * Gets the PDO's key.
     *
     * @return the key
     */
    public C getKey() {
      return key;
    }

    @Override
    public int compareTo (CacheKey obj) {
      // context/session first for getObjects(context, from, to)
      int rv = context.compareTo(obj.context);  // never null
      if (rv == 0)  {
        rv = key.compareTo(obj.key);    // never null
      }
      return rv;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj != null && obj.getClass() == getClass()) {
        @SuppressWarnings("unchecked")
        CacheKey otherKey = (CacheKey) obj;
        return otherKey.context.equals(context) &&
               otherKey.key.equals(key);
      }
      return false;
    }

    @Override
    public int hashCode() {
      int hash = 5;
      hash = 97 * hash + (context != null ? context.hashCode() : 0);
      hash = 97 * hash + (key != null ? key.hashCode() : 0);
      return hash;
    }

    @Override
    public String toString()  {
      return context.toDiagnosticString() + ", value '" + key + '\'';
    }
  }


  /**
   * The result from a cache query.
   */
  protected final class CacheResult {

    private final T pdo;
    private final CacheKey cacheKey;

    private CacheResult(T pdo, CacheKey cacheKey) {
      this.pdo = pdo;
      this.cacheKey = cacheKey;
    }

    /**
     * Gets the PDO.
     *
     * @return the PDO if cached, null if not in cache
     */
    public T getPdo() {
      return pdo;
    }

    /**
     * Gets the cache key.
     *
     * @return the internal key
     */
    public CacheKey getCacheKey() {
      return cacheKey;
    }
  }


  private final String name;                    // symbolic name of the index
  private final TreeMap<CacheKey, T> cacheMap;  // map of cache indexes to PDOs
  private final Set<CacheKey> missingSet;       // set of keys for missing PDOs (not in the database)

  private PdoCache<T> cache;                    // the cache, null if not already added
  private long accessCount;                     // for statistics if fineLoggable
  private long missCount;                       // number of cache-misses


  /**
   * Create a new index.<br>
   *
   * @param name is the symbolic name of the index (for diagnostics only).
   */
  public PdoCacheIndex(String name)  {
    this.name = name;
    cacheMap  = new TreeMap<>();
    missingSet = new HashSet<>();
  }


  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    if (cache != null) {
      sb.append(cache).append(", ");
    }
    sb.append("index '").append(name).append('\'');
    return sb.toString();
  }

  /**
   * Selects a PDO by key from storage.<br>
   * This method needs to be implemented for each index.
   *
   * @param context the domain context to select the object from
   * @param key the Comparable used to uniquely identify the object (never null)
   *
   * @return the selected object or null if it does not exist.
   */
  public abstract T select(DomainContext context, C key);

  /**
   * Extracts the {@link Comparable} that uniquely identifies the object from that object.<br>
   * This method needs to be implemented for each index.
   *
   * @param object the object to extract the key from (never null)
   *
   * @return the {@link Comparable}, null if the object must not be added to the cache
   *
   */
  public abstract C extract(T object);


  /**
   * Adds the key to the set of missing PDOs.
   *
   * @param ck the internal key
   */
  protected void addToMissing(CacheKey ck) {
    missingSet.add(ck);
  }

  /**
   * Removes the key from the set of missing PDOs.
   *
   * @param ck the internal key
   */
  protected void removeFromMissing(CacheKey ck) {
    missingSet.remove(ck);
  }

  /**
   * Returns whether the key is known to belong to a missing PDO.
   *
   * @param ck the internal key
   * @return true if known to be missing, false if unknown
   */
  protected boolean isMissing(CacheKey ck) {
    return missingSet.contains(ck);
  }

  /**
   * Clears all keys for missing PDOs.
   */
  protected void clearMissing() {
    missingSet.clear();
  }

  /**
   * Maintains the link to the cache.<br>
   * This is an implementation detail invoked by {@link PdoCache#addIndex} and {@link PdoCache#removeIndex} only.
   *
   * @param cache the cache to assign this index, null to remove index from the cache
   */
  protected void assignCache(PdoCache<T> cache) {
    if (cache != null)  {
      if (this.cache != null) {
        if (this.cache != cache) {
          throw new PdoCacheException(this + " is already assigned to " + this.cache);
        }
        return;
      }
    }
    else  {
      // clear assignment
      if (this.cache == null) {
        LOGGER.warning("{0} is not assigned to any cache", this);
        return;
      }
    }
    this.cache = cache;
  }

  /**
   * Checks if the index is assigned to cache.
   *
   * @param cache the cache to check
   * @return true if assigned
   */
  protected boolean isAssignedToCache(PdoCache<T> cache)  {
    return this.cache == cache;
  }

  /**
   * Clears the contents of the cache index.
   */
  protected void clear()  {
    cacheMap.clear();
    clearMissing();
  }

  /**
   * Gets the number of objects in this index.
   *
   * @return the number of objects
   */
  protected int size()  {
    return cacheMap.size();
  }

  /**
   * Gets the cache index statistics as a printable string.
   *
   * @return the string for cache stats
   */
  protected String printStatistics()  {
    int rate = (int)(100L - missCount * 100L / (accessCount == 0 ? 1 : accessCount));
    return "size=" + size() + ", accesses=" + accessCount + ", misses=" + missCount + ", hit rate=" + rate + "%";
  }

  /**
   * Clears the cache statistics.
   */
  protected void clearStatistics()  {
    accessCount = 0;
    missCount   = 0;
  }

  /**
   * Gets the PDO from the cache by key.
   *
   * @param context the domain context
   * @param key the Comparable that uniquely identifies the object
   *
   * @return the result holding the PDO and the internal cache key, null if the key is illegal (i.e., null)
   */
  protected CacheResult get(DomainContext context, C key) {

    CacheKey ck;
    try {
      ck = new CacheKey(cache.processContext(context), key);
    }
    catch (PdoCacheException e)  {
      // no need to invalidate the whole cache, but a hint to an application problem
      logInvalidKey(e);
      return null;
    }

    T obj = cacheMap.get(ck);

    accessCount++;
    if (obj == null) {
      missCount++;
    }

    LOGGER.finer(() -> this + (obj == null ? ": cache miss for '" : ": cache hit for '") + ck + "', " + printStatistics());

    if (obj != null)  {
      // this will throw an Exception and forces invalidation of the cache!
      assertKeyIsUnchanged(obj, ck);
    }

    return new CacheResult(obj, ck);
  }

  /**
   * Walks through all objects maintained by this cache index.
   *
   * @param verifyKey true if the cache key should be verified
   * @param consumer the consumer to process the object
   */
  protected void walk(boolean verifyKey, Consumer<T> consumer) {
    for (Map.Entry<CacheKey, T> entry: cacheMap.entrySet()) {
      T pdo = entry.getValue();
      if (verifyKey) {
        assertKeyIsUnchanged(pdo, entry.getKey());
      }
      consumer.accept(pdo);
    }
  }

  /**
   * Walks through a subset of objects.
   *
   * @param context the domain context
   * @param fromKey the start of range
   * @param fromInclusive {@code true} if the low endpoint is to be included in the returned view
   * @param toKey the end of range
   * @param toInclusive {@code true} if the high endpoint is to be included in the returned view
   * @param consumer the consumer
   */
  protected void walk(DomainContext context, C fromKey, boolean fromInclusive, C toKey, boolean toInclusive,
                      Consumer<T> consumer) {

    context = cache.processContext(context);
    cacheMap.subMap(new CacheKey(context, fromKey), fromInclusive, new CacheKey(context, toKey), toInclusive)
            .forEach((key, pdo) -> {
              assertKeyIsUnchanged(pdo, key);
              consumer.accept(pdo);
            });
  }

  /**
   * Returns whether the cache is readonly.
   *
   * @return true if readonly
   */
  protected boolean isReadOnly() {
    return cache != null && cache.isReadOnly();
  }

  /**
   * Process the PDO.<br>
   * If cache is readonly, the context will be replaced
   * by a session-thread-local context and the pdo will be
   * made immutable.<br>
   * If the context is a root context, it will be replaced
   * by the corresponding non-root context.
   *
   * @param pdo the PDO
   */
  protected void processPdo(T pdo) {
    PersistentObject<T> pd = pdo.getPersistenceDelegate();
    DomainContext context = pd.getDomainContext();
    DomainContext processedContext = cache.processContext(context);
    if (processedContext != context) {
      pd.setDomainContextImmutable(false);   // for sure
      pd.setDomainContext(processedContext);
    }
    if (isReadOnly()) {
      pd.setFinallyImmutable();
      pd.setDomainContextImmutable(true);
      pd.setSessionImmutable(true);
    }
  }

  /**
   * Returns whether this index already holds an object with the same key as the given object.<br>
   * Objects whose key evaluates to {@code null} are never held by the index.
   *
   * @param object the object (never null)
   * @return true if the key is already in use
   */
  protected boolean containsKeyOf(T object) {
    C keyValue = extract(object);
    if (keyValue != null) {
      try {
        // same context processing as in processPdo, but without modifying the object
        return cacheMap.containsKey(new CacheKey(cache.processContext(object.getPersistenceDelegate().getDomainContext()),
                                                 keyValue));
      }
      catch (PdoCacheException e) {
        logInvalidKey(e);
      }
    }
    return false;
  }

  /**
   * Adds an object to the index.<br>
   * Does not throw an exception if it is already in cache.
   *
   * @param object the object to append (never null)
   *
   * @return true if added, false if the object was replaced in index or the key in the object evaluated to null
   */
  protected boolean add(T object) {
    processPdo(object);
    try {
      C keyValue = extract(object);
      if (keyValue != null) {
        return cacheMap.put(new CacheKey(object.getPersistenceDelegate().getDomainContext(), keyValue), object) == null;
      }
      return false;
    }
    catch (PdoCacheException e)  {
      logInvalidKey(e);
      return false;
    }
  }

  /**
   * Adds a unique object to the index.<br>
   * Unique violations usually indicate an error in comparing
   * contexts or that the context of a cached object was changed
   * accidentally.
   *
   * @param object the object to append (never null)
   */
  protected void addUnique(T object) {
    processPdo(object);
    C keyValue = extract(object);
    if (keyValue != null) {
      CacheKey ck;
      try {
        ck = new CacheKey(object.getPersistenceDelegate().getDomainContext(), keyValue);
      }
      catch (PdoCacheException e) {
        logInvalidKey(e);
        return;   // don't add to index (but no reason to invalidate the cache)
      }
      if (cacheMap.put(ck, object) != null) {
        throw new PdoCacheException(object,
                                    "unique cache violation detected in " + this + " for " + object.toGenericString() +
                                    ", key = " + ck);
      }
    }
  }

  /**
   * Removes an object from this cache index.<br>
   * Does not throw an exception if it not in the cache.
   * For use by PdoCache.remove() only.
   *
   * @param object the object to remove (never null)
   *
   * @return true if the object was removed
   */
  protected boolean remove(T object) {
    try {
      C key = extract(object);
      return key != null &&
             cacheMap.remove(new CacheKey(object.getPersistenceDelegate().getDomainContext(), key)) != null;
    }
    catch (PdoCacheException e)  {
      logInvalidKey(e);
      return false;
    }
  }

  /**
   * Remove an object from this cache index.<br>
   * Assumes that the object really is in the cache.
   *
   * @param object the object to remove (never null)
   */
  protected void removeExisting(T object) {
    CacheKey ck;
    C keyValue = extract(object);
    if (keyValue != null) {   // null keys cannot be in the cache and are silently skipped
      try {
        ck = new CacheKey(object.getPersistenceDelegate().getDomainContext(), keyValue);
      }
      catch (PdoCacheException e) {
        logInvalidKey(e);
        return;   // nothing removed (but no reason to invalidate the cache)
      }
      if (cacheMap.remove(ck) == null) {
        throw new PdoCacheException(object,
                                    "remove from cache failed from " + this + " for " + object.toGenericString() +
                                    ", key='" + ck + "'");
      }
    }
  }

  /**
   * Removes all objects for the given session from the index.
   * <p>
   * This will also remove objects that have changed their session
   * but were originally stored with the given session.
   * Avoids memory leaks if the application is misbehaving.
   *
   * @param session the session
   */
  protected void removeObjectsForSession(Session session) {
    cacheMap.keySet().removeIf(key ->
                                 key.context.getSession() == session ||    // == is ok here
                                 key.sessionInstanceNumber == session.getInstanceNumber());
  }


  /**
   * We cannot be sure that the application did not alter an object which is already
   * in cache in such a way that this would alter the ordering with respect to the
   * cacheMap. Furthermore, the CacheKey might be a copy of a primitive type since
   * it must have been converted to a Comparable (e.g.: long -> Long).
   * As a consequence, we MUST check every object retrieved from the cache that
   * its key has NOT been altered. If so, the complete cache MUST be invalidated
   * (cause the ordering of the tree is corrupted in an unknown way).
   * This is done in PdoCache.
   */
  private void assertKeyIsUnchanged(T object, CacheKey key) {
    // assert that the key was not changed in the object by application
    CacheKey objectKey = new CacheKey(object.getPersistenceDelegate().getDomainContext(), extract(object));
    if (objectKey.compareTo(key) != 0) {
      throw new PdoCacheException(object,
                                  "modified key detected in " + this + " for " + object.toGenericString() +
                                  ", expected='" + key + "', found='" + objectKey + "'");
    }
  }

  /**
   * log invalid CacheKey.
   */
  private void logInvalidKey(Throwable t)  {
    LOGGER.warning("illegal access to " + this, t);
  }

}
