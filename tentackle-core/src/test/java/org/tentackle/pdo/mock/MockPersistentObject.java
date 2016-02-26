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


package org.tentackle.pdo.mock;

import java.util.List;
import org.tentackle.common.Timestamp;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.misc.ScrollableResource;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainDelegate;
import org.tentackle.pdo.PdoCache;
import org.tentackle.pdo.PersistenceException;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.PersistentObject;
import org.tentackle.pdo.Session;
import org.tentackle.security.Permission;
import org.tentackle.security.SecurityResult;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationScope;

/**
 * A mocked persistence object.
 *
 * @param <T> the pdo type
 * @param <P> the persistent type
 * @author harald
 */
public class MockPersistentObject<T extends PersistentDomainObject<T>, P extends MockPersistentObject<T,P>>
       implements PersistentObject<T> {


  private DomainContext context;                  // application database context
  private T pdo;                                  // the pdo instance this is a delegate for
  private transient Session session;              // the session
  private transient boolean sessionImmutable;     // true if session is immutable
  private boolean immutable;                      // true if all attributes are immutable
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
  public T pdo() {
    return pdo;
  }

  @Override
  public void setPdo(T pdo) {
    this.pdo = pdo;
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

  @Override
  public void setId(long id) {
    this.id = id;
  }

  @Override
  public long getId() {
    return id < 0 ? -id : id;
  }

  @Override
  public void setSerial(long serial) {
    this.serial = serial;
  }

  @Override
  public long getSerial() {
    return serial;
  }

  @Override
  public void setTableSerial(long tableSerial) {
    this.tableSerial = tableSerial;
  }

  @Override
  public long getTableSerial() {
    return tableSerial;
  }

  @Override
  public String getTableName() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public String getClassBaseName() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public int getClassId() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public SecurityResult getSecurityResult(Permission permission) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean isPermissionAccepted(Permission permission) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean isNew() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void reserveId() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean isDeleted() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean isVirgin() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void setModified(boolean modified) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean isTracked() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean isModified() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean attributesModified() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean isPersistable() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean isReferenced() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void setExpired(boolean expired) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean isExpired() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public List<IdSerialTuple> getExpiredTableSerials(long oldSerial, long maxSerial) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public List<IdSerialTuple> selectExpiredTableSerials(long oldSerial) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public List<IdSerialTuple> selectExpiredTableSerials(long oldSerial, long maxSerial) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public PdoCache<T> getCache() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public long getCacheAccessTime() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public long getCacheAccessCount() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void markCacheAccess() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean isCached() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean isCacheable() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean isRemovable() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void prepareDelete() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void delete() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean containsPattern(String pattern) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public T findDuplicate() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public long getTokenLockTimeout() {
    throw new UnsupportedOperationException("Not supported yet.");
  }


  @Override
  public T select(long id) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public T selectLocked(long id) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public T reload() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public T reloadLocked() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public List<T> selectAll() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public T selectCached(long id) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public List<T> selectAllCached() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public T createCopyInContext(DomainContext otherContext) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public T getCopiedObject() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void save() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public T persist() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public Object getTransientData() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void setTransientData(Object data) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public long getEditedBy() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void setEditedBy(long editedBy) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean isTokenLocked() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean isTokenLockedBy(long userId) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean isTokenLockedByMe() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public Timestamp getEditedSince() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void setEditedSince(Timestamp editedSince) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public Timestamp getEditedExpiry() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void setEditedExpiry(Timestamp editedExpiry) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public <U extends PersistentDomainObject<U>> U getTokenLockObject() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public <U extends PersistentDomainObject<U>> void setTokenLockObject(U obj) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public List<ValidationResult> validate(String validationPath, ValidationScope scope) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public String toGenericString() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public Class<? extends ValidationScope>[] getDefaultScopes() {
    throw new UnsupportedOperationException("Not supported yet.");
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
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public long getContextId() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public DomainContext getBaseContext() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public DomainContext createValidContext() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void setImmutable(boolean immutable) {
    this.immutable = immutable;
  }

  @Override
  public boolean isImmutable() {
    return immutable;
  }

  @Override
  public boolean isRootEntity() {
    return false;
  }

  @Override
  public boolean isComposite() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public T clonePersistentObject() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean isDomainContextImmutable() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void setDomainContextImmutable(boolean contextImmutable) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public T createSnapshot() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void revertToSnapshot(T snapshot) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean isSnapshot() {
    return false;
  }

  @Override
  public List<T> getSnapshots() {
    return null;
  }

  @Override
  public void setNormText(String normText) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public String getNormText() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public ScrollableResource<T> selectAllAsCursor() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public List<T> selectByNormText(String normText) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public long getModificationCount() {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public List<T> selectAllForCache() {
    return selectAll();
  }

  @Override
  public T selectCachedOnly(long id) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public ScrollableResource<T> selectByNormTextAsCursor(String normText) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public List<IdSerialTuple> selectAllIdSerial() {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public long selectSerial(long id) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public long selectMaxId() {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public long selectMaxTableSerial() {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public boolean isAbstract() {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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

  @Override
  public void setRootId(long rootId) {
    this.rootId = rootId;
  }

  @Override
  public int getRootClassId() {
    return rootClassId;
  }

  @Override
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
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public DomainDelegate<T> getDomainDelegate() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public T selectForCache(long id) {
    return select(id);
  }

  @Override
  public T transferTokenLock(long userId) {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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

}
