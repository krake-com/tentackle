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

package org.tentackle.pdo.mock;

import org.tentackle.common.Timestamp;
import org.tentackle.log.Logger;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.misc.IdentifiableMap;
import org.tentackle.misc.ImmutableException;
import org.tentackle.misc.ScrollableResource;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainDelegate;
import org.tentackle.pdo.PdoCache;
import org.tentackle.pdo.PdoMethodCache;
import org.tentackle.pdo.PdoMethodCacheProvider;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.PersistentObject;
import org.tentackle.security.Permission;
import org.tentackle.security.SecurityResult;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.validate.ValidationScope;

import java.io.Serial;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A mocked persistence object.
 *
 * @param <T> the pdo type
 * @param <P> the persistent type
 * @author harald
 */
public class MockPersistentObject<T extends PersistentDomainObject<T>, P extends MockPersistentObject<T,P>>
       implements PersistentObject<T>, PdoMethodCacheProvider<T> {

  /**
   * Exception message if the method is not implemented.
   */
  public static final String UNSUPPORTED = "not implemented";


  @Serial
  private static final long serialVersionUID = 1L;



  @SuppressWarnings("rawtypes")
  private static final Map<Class, PdoMethodCache> METHOD_CACHE_MAP = new ConcurrentHashMap<>();


  private DomainContext context;                  // application database context
  private T pdo;                                  // the pdo instance this is a delegate for
  private transient Session session;              // the session
  private transient boolean sessionImmutable;     // true if session is immutable
  private boolean immutable;                      // true if all attributes are immutable
  private boolean finallyImmutable;               // true if object cannot be made mutable again
  private long id;                                // unique Object ID
  private long serial;                            // serial-nummer (version to detect simultaneous updates)
  private long tableSerial;                       // last table serial from countModification (only if isTableSerialValid() == true)
  private long rootId;                            // root entity id
  private int rootClassId;                        // class if of the root entity


  /**
   * Creates an application database object.
   *
   * @param pdo the persistent domain object this is a delegate for
   * @param context the database context
   */
  public MockPersistentObject(T pdo, DomainContext context) {
    this.pdo     = pdo;
    this.context = context;
    this.session = context == null ? null : context.getSession();
  }


  /**
   * Creates an application database object without a domain context
   * for a given connection.<p>
   * Note: the application must set the context.
   *
   * @param pdo the persistent domain object this is a delegate for
   * @param session the session (must be an instance of {@link Session}).
   */
  public MockPersistentObject(T pdo, Session session) {
    this.pdo = pdo;
    this.session  = session;
  }

  /**
   * Creates an application database object without a database context.<p>
   * Note: the application must set the context.
   *
   * @param pdo the persistent domain object this is a delegate for
   */
  public MockPersistentObject(T pdo) {
    this.pdo = pdo;
  }


  /**
   * Creates an application database object without a database context.
   */
  public MockPersistentObject() {
  }


  @Override
  public T getPdo() {
    return pdo;
  }

  @Override
  public T me() {
    return pdo;
  }

  /**
   * Sets the PDO.<br>
   *
   * @param pdo the pdo
   */
  public void setPdo(T pdo) {
    this.pdo = pdo;
  }

  @SuppressWarnings("unchecked")
  @Override
  public PdoMethodCache<T> getPdoMethodCache() {
    // pdo.getEffectiveClass() is routed through the invocation handler, but that's ok for mocking
    return METHOD_CACHE_MAP.computeIfAbsent(pdo.getEffectiveClass(), PdoMethodCache::new);
  }

  @Override
  public void setSessionImmutable(boolean sessionImmutable) {
    this.sessionImmutable = sessionImmutable;
  }

  @Override
  public boolean isSessionImmutable() {
    return sessionImmutable;
  }

  @Override
  public void setSession(Session session) {
    if (isSessionImmutable() && this.session != session) {
      throw new PersistenceException(this.session, "illegal attempt to change the immutable session of " + this +
                                              " from " + this.session + " to " + session);
    }
    this.session = session;
  }

  @Override
  public Session getSession() {
    return session;
  }

  public void setId(long id) {
    this.id = id;
  }

  @Override
  public long getId() {
    return id < 0 ? -id : id;
  }

  public void setSerial(long serial) {
    this.serial = serial;
  }

  @Override
  public long getSerial() {
    return serial;
  }

  public void setTableSerial(long tableSerial) {
    this.tableSerial = tableSerial;
  }

  @Override
  public long getTableSerial() {
    return tableSerial;
  }

  @Override
  public String getTableName() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public String getClassBaseName() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public int getClassId() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public SecurityResult getSecurityResult(Permission permission) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean isPermissionAccepted(Permission permission) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean isNew() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public void reserveId() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public void reserveId(long id) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean isDeleted() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean isVirgin() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean isTracked() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean isModified() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean attributesModified() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean differsPersisted() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean isPersistable() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean isReferenced() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public void setExpired(boolean expired) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean isExpired() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public Collection<IdSerialTuple> getExpiredTableSerials(long oldSerial, long maxSerial) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public List<IdSerialTuple> selectExpiredTableSerials(long oldSerial) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public List<IdSerialTuple> selectExpiredTableSerials(long oldSerial, long maxSerial) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public PdoCache<T> getCache() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public long getCacheAccessTime() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public long getCacheAccessCount() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public void markCacheAccess() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean isCached() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean isCacheable() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean isRemovable() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public void delete() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean containsPattern(String pattern) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public T findDuplicate() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public long getTokenLockTimeout() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }


  @Override
  public T select(long id) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public T selectTokenLocked(long id) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public T selectForUpdate(long id) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public T reload() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public T reloadTokenLocked() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public T reloadForUpdate() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public List<T> selectAll() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public List<T> selectAny(Collection<Long> ids) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public List<T> selectAnyCached(Collection<Long> ids) {
    return selectAny(ids);
  }

  @Override
  public List<T> selectLatest(long greaterId, int limit) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public T selectCached(long id) {
    return select(id);
  }

  @Override
  public List<T> selectAllCached() {
    return selectAll();
  }

  @Override
  public void save() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public T persist() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public long getEditedBy() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean isTokenLocked() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean isTokenLockedBy(long userId) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean isTokenLockedByMe() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean isTokenLockableByMe() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public Timestamp getEditedSince() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public Timestamp getEditedExpiry() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public String toGenericString() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public Class<? extends ValidationScope>[] getDefaultScopes() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public DomainContext getDomainContext() {
    return context;
  }

  @Override
  public void setDomainContext(DomainContext context) {
    this.context = context;
  }

  @Override
  public void determineContextId() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public long getContextId() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public DomainContext getBaseContext() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public DomainContext createValidContext() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public void setImmutable(boolean immutable) {
    if (!immutable && finallyImmutable) {
      throw new ImmutableException("object is finally immutable");
    }
    this.immutable = immutable;
  }

  @Override
  public void setFinallyImmutable() {
    setImmutable(true);
    finallyImmutable = true;
  }

  @Override
  public boolean isImmutable() {
    return immutable;
  }

  @Override
  public boolean isFinallyImmutable() {
    return finallyImmutable;
  }

  @Override
  public void setImmutableLoggingLevel(Logger.Level immutableLoggingLevel) {

  }

  @Override
  public Logger.Level getImmutableLoggingLevel() {
    return null;
  }

  @Override
  public boolean isRootEntity() {
    return false;
  }

  @Override
  public boolean isEmbedded() {
    return false;
  }

  @Override
  public <E extends PersistentDomainObject<E>> E getEmbeddingParent() {
    return null;
  }

  @Override
  public <C extends PersistentDomainObject<C>> boolean isRootEntityOf(C component) {
    return isRootEntity() &&
           component != null &&
           component.getRootId() == getId() &&
           component.getRootClassId() == getClassId();
  }

  @Override
  public boolean isComposite() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean isDomainContextImmutable() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public void setDomainContextImmutable(boolean contextImmutable) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public T createSnapshot() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public void revertToSnapshot(T snapshot) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean isSnapshot() {
    return false;
  }

  @Override
  public List<T> getSnapshots() {
    return Collections.emptyList();
  }

  @Override
  public void discardSnapshot(T snapshot) {
  }

  @Override
  public void discardSnapshots() {
  }

  @Override
  public String getNormText() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public ScrollableResource<T> selectAllAsCursor() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public List<T> selectByNormText(String normText) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public long getModificationCount() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public List<T> selectAllForCache() {
    return selectAll();
  }

  @Override
  public List<T> selectAnyForCache(Collection<Long> ids) {
    return selectAny(ids);
  }

  @Override
  public T selectCachedOnly(long id) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public ScrollableResource<T> selectByNormTextAsCursor(String normText) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public IdentifiableMap<? extends PersistentDomainObject<?>> loadComponents(boolean onlyLoaded) {
    return new IdentifiableMap<>();
  }

  @Override
  public T copy() {
    return null;
  }

  @Override
  public boolean isCopy() {
    return false;
  }

  @Override
  public void setCopy(boolean copy) {

  }

  @Override
  public List<IdSerialTuple> selectAllIdSerial() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public long selectSerial(long id) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public long selectMaxId() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public long selectMaxTableSerial() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean isAbstract() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean isWriteAllowed() {
    return true;
  }

  @Override
  public boolean isTableSerialProvided() {
    return false;
  }

  @Override
  public boolean isTokenLockProvided() {
    return false;
  }

  @Override
  public boolean isNormTextProvided() {
    return false;
  }

  @Override
  public long getRootId() {
    return rootId;
  }

  public void setRootId(long rootId) {
    this.rootId = rootId;
  }

  @Override
  public int getRootClassId() {
    return rootClassId;
  }

  public void setRootClassId(int rootClassId) {
    this.rootClassId = rootClassId;
  }

  @Override
  public boolean isRootIdProvided() {
    return false;
  }

  @Override
  public boolean isRootClassIdProvided() {
    return false;
  }

  @Override
  public void validate() {
    //
  }

  @Override
  public void requestTokenLock() {
    //
  }

  @Override
  public void releaseTokenLock() {
    //
  }

  @Override
  public T persistTokenLocked() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public DomainDelegate<T> getDomainDelegate() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public T selectForCache(long id) {
    return select(id);
  }

  @Override
  public T transferTokenLock(long userId) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public boolean isViewAllowed() {
    return true;
  }

  @Override
  public boolean isEditAllowed() {
    return true;
  }

  @Override
  public boolean isValidated() {
    return !isModified();
  }

  @Override
  public String toIdString() {
    return getClassId() + ":" + getId();
  }

  @Override
  public List<T> selectAllWithExpiredTableSerials(long oldSerial) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public List<T> selectAllWithExpiredTableSerials(long oldSerial, long maxSerial) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

}
