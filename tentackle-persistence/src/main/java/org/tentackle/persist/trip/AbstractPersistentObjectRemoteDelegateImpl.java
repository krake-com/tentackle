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

package org.tentackle.persist.trip;

import org.tentackle.common.Timestamp;
import org.tentackle.dbms.AbstractDbObject;
import org.tentackle.dbms.trip.AbstractDbObjectRemoteDelegateImpl;
import org.tentackle.dbms.trip.DbObjectResult;
import org.tentackle.dbms.trip.RemoteDbSessionImpl;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.misc.ScrollableResource;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.TokenLockInfo;
import org.tentackle.persist.AbstractPersistentObject;
import org.tentackle.session.NotFoundException;
import org.tentackle.session.PersistenceException;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;

/**
 * Implementation of the remote delegate for {@link AbstractPersistentObject}.<br>
 * Executes the persistence methods on the server. The low-level methods inherited from the
 * database layer are disabled to protect against malicious clients.
 *
 * @param <T> the {@code PersistentDomainObject} class
 * @param <P> the {@code AbstractPersistentObject} class (persistence implementation)
 * @author harald
 */
public class AbstractPersistentObjectRemoteDelegateImpl<T extends PersistentDomainObject<T>, P extends AbstractPersistentObject<T,P>>
       extends AbstractDbObjectRemoteDelegateImpl<P>
       implements AbstractPersistentObjectRemoteDelegate<T,P> {


  private static final String ILLEGAL_METHOD_TEXT = "remote method not allowed for PDOs";


  /**
   * The PDO interface class.
   */
  protected final Class<T> pdoClass;

  /**
   * PDO with a default domain context associated with the delegate's session.
   */
  protected T pdo;


  /**
   * Creates a delegate for the given TRIP session.
   *
   * @param serverSession the TRIP session
   * @param persistenceClass the subclass of AbstractPersistentObject
   * @param pdoClass the PDO class (interface)
   */
  public AbstractPersistentObjectRemoteDelegateImpl(RemoteDbSessionImpl serverSession, Class<P> persistenceClass, Class<T> pdoClass) {
    super(serverSession, persistenceClass);
    this.pdoClass = pdoClass;
  }


  @Override
  @SuppressWarnings("unchecked")
  public void initialize() {
    /*
     * Try to set a default context.
     * This will fail in apps that extend the domain context, but is useful for other apps.
     * We ignore any thrown exception safely.
     */
    try {
      pdo = newInstance(Pdo.createDomainContext(getSession()));
    }
    catch (RuntimeException e) {
      /*
       * Application requires an extended domain context.
       * Use the local pdo object as it is for simple methods.
       * In all other cases, the application must pass the context via the remote method
       * and set the context explicitly.
       */
      pdo = newInstance(null);    // create without domain context
    }
    dbObject = (P) pdo.getPersistenceDelegate();
  }


  /**
   * Creates a new PDO instance of the required class
   * for methods that return a new object.
   *
   * @param context the domain context
   * @return the new pdo
   */
  protected T newInstance(DomainContext context) {
    if (context != null) {
      context.setSession(getSession());
    }
    T newPdo = context == null ? Pdo.create(pdoClass, getSession()) : Pdo.create(pdoClass, context);
    // allow overloads for simple proxy objects
    ((AbstractDbObject<?>) newPdo.getPersistenceDelegate()).setOverloadable(true);
    return newPdo;
  }

  /**
   * Selects by ID into a new instance.
   *
   * @param context the domain context
   * @param id the ID
   * @return the PDO, never null
   * @throws NotFoundException if no such PDO in database
   */
  protected T newInstance(DomainContext context, long id) {
    T newPdo = newInstance(context).select(id);
    if (newPdo == null) {
      throw new NotFoundException(getSession(), "no such " + pdoClass.getSimpleName() + " with ID " + id + " in database");
    }
    return newPdo;
  }

  /**
   * Gets the persistence delegate from a PDO.
   *
   * @param pDo the PDO
   * @return the persistence delegate
   */
  @SuppressWarnings("unchecked")
  protected P getPersistenceDelegate(T pDo) {
    return (P) pDo.getPersistenceDelegate();
  }


  /**
   * Sets the domain context in the default object.
   *
   * @param context the domain context
   */
  protected void setDomainContext(DomainContext context) {
    // set the session in the context first as this will be copied to the object below
    if (context != null) {
      context.setSession(getSession());   // session in context is transient, so we must set it here
    }
    dbObject.setDomainContext(context);
  }


  // ----------- overridden due to security checks against malicious clients ----------------


  @Override
  public P selectObject(long id) {
    throw new PersistenceException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public P selectObjectForUpdate(long id) {
    throw new PersistenceException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public List<P> selectAllObjects() {
    throw new PersistenceException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public void dummyUpdate(P obj) {
    throw new PersistenceException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public void updateSerial(long id, long serial) {
    throw new PersistenceException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public void updateAndSetSerial(long id, long serial) {
    throw new PersistenceException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public void updateSerialAndTableSerial(long id, long serial, long tableSerial) {
    throw new PersistenceException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public void updatePlain(P obj) {
    throw new PersistenceException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public void deletePlain(long id, long serial) {
    throw new PersistenceException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public void insertPlain(P obj) {
    throw new PersistenceException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public DbObjectResult updateObject(P obj) {
    throw new PersistenceException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public DbObjectResult insertObject(P obj) {
    throw new PersistenceException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public DbObjectResult deleteObject(P obj) {
    throw new PersistenceException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public P persistObject(P obj) {
    throw new PersistenceException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public DbObjectResult saveObject(P obj) {
    throw new PersistenceException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public boolean isReferenced(long id) {
    throw new PersistenceException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public List<IdSerialTuple> selectAllIdSerial() {
    throw new PersistenceException(ILLEGAL_METHOD_TEXT);
  }


  // --------------- end malicious client checks ----------------


  @Override
  @SuppressWarnings("unchecked")
  public T persistImpl(T obj) {
    obj.setSession(getSession());
    P po = (P) obj.getPersistenceDelegate();
    return po.persist();
  }

  @Override
  @SuppressWarnings("unchecked")
  public long saveImpl(T obj) {
    obj.setSession(getSession());
    P po = (P) obj.getPersistenceDelegate();
    po.save();
    return po.getTableSerial();
  }

  @Override
  @SuppressWarnings("unchecked")
  public long deleteImpl(T obj) {
    obj.setSession(getSession());
    P po = (P) obj.getPersistenceDelegate();
    po.delete();
    po.markDeleted();
    return po.getTableSerial();
  }

  @Override
  public List<T> selectAll(DomainContext context) {
    setDomainContext(context);
    dbObject.assertRootEntity();
    return dbObject.selectAll();
  }

  @Override
  public List<T> selectAny(DomainContext context, Collection<Long> ids) {
    setDomainContext(context);
    dbObject.assertRootEntity();
    return dbObject.selectAny(ids);
  }

  @Override
  @SuppressWarnings("unchecked")
  public T select(DomainContext context, long id, boolean forUpdate, boolean tokenLocked) {
    return ((P) newInstance(context).getPersistenceDelegate()).select(id, forUpdate, tokenLocked);
  }

  @Override
  public T selectTokenLocked(DomainContext context, long id) {
    return newInstance(context).getPersistenceDelegate().selectTokenLocked(id);
  }

  @Override
  public List<T> selectByNormText(DomainContext context, String normText) {
    setDomainContext(context);
    dbObject.assertRootEntity();
    return dbObject.selectByNormText(normText);
  }

  @Override
  public RemoteResultSetCursor<T> selectByNormTextAsCursor(DomainContext context, String normText) {
    setDomainContext(context);
    dbObject.assertRootEntity();
    return createRemoteResultSetCursorDelegate(dbObject.selectByNormTextAsCursor(normText));
  }

  @Override
  public List<T> selectLatest(DomainContext context, long greaterId, int limit) {
    setDomainContext(context);
    dbObject.assertRootEntity();
    return dbObject.selectLatest(greaterId, limit);
  }

  @Override
  public List<T> selectAllForCache(DomainContext context) {
    setDomainContext(context);
    dbObject.assertRootEntity();
    // load via cache, if there is a cache at all
    return dbObject.selectAllCached();
  }

  @Override
  public List<T> selectAnyForCache(DomainContext context, Collection<Long> ids) {
    setDomainContext(context);
    dbObject.assertRootEntity();
    // load via cache, if there is a cache at all
    return dbObject.selectAnyCached(ids);
  }

  @Override
  public T selectForCache(DomainContext context, long id) {
    // load via cache, if there is a cache at all
    return (newInstance(context).getPersistenceDelegate()).selectCached(id);
  }

  @Override
  public RemoteResultSetCursor<T> selectAllAsCursor(DomainContext context) {
    setDomainContext(context);
    dbObject.assertRootEntity();
    return createRemoteResultSetCursorDelegate(dbObject.selectAllAsCursor());
  }

  @Override
  public List<T> selectAllWithExpiredTableSerials(DomainContext context, long oldSerial) {
    setDomainContext(context);
    dbObject.assertRootEntity();
    return dbObject.selectAllWithExpiredTableSerials(oldSerial);
  }

  @Override
  public List<T> selectAllWithExpiredTableSerials(DomainContext context, long oldSerial, long maxSerial) {
    setDomainContext(context);
    dbObject.assertRootEntity();
    return dbObject.selectAllWithExpiredTableSerials(oldSerial, maxSerial);
  }

  @Override
  public boolean isReferenced(DomainContext context, long id) {
    T obj = newInstance(context).select(id);
    return obj != null && obj.isReferenced();
  }

  @Override
  public TokenLockInfo updateTokenLock(DomainContext context, long id, Timestamp tokenExpiry, long userId, Timestamp curTime) {
    if (getSession().isRemote()) {
      // optimize, if running in remote middle tier
      return dbObject.getRemoteDelegate().updateTokenLock(context, id, tokenExpiry, userId, curTime);
    }
    P po = getPersistenceDelegate(newInstance(context, id));
    po.setId(id);
    po.updateTokenLock(tokenExpiry, userId, curTime);
    return new TokenLockInfo(po.getEditedBy(), po.getEditedSince(), po.getEditedExpiry());
  }

  @Override
  public T transferTokenLock(T pdo, long userId) {
    return pdo.transferTokenLock(userId);
  }

  @Override
  public void updateTokenLockOnly(DomainContext context, long id, long editedBy, Timestamp editedSince, Timestamp editedExpiry) {
    if (getSession().isRemote()) {
      // optimize, if running in remote middle tier
      dbObject.getRemoteDelegate().updateTokenLockOnly(context, id, editedBy, editedSince, editedExpiry);
    }
    else {
      P po = getPersistenceDelegate(newInstance(context, id));
      po.setId(id);
      po.setEditedBy(editedBy);
      po.setEditedSince(editedSince);
      po.setEditedExpiry(editedExpiry);
      po.updateTokenLockOnly();
    }
  }


  /**
   * Creates and exports a remote cursor delegate for a local cursor.
   *
   * @param cursor the local cursor
   * @return the remote cursor delegate
   */
  @SuppressWarnings("unchecked")
  public RemoteResultSetCursor<T> createRemoteResultSetCursorDelegate(ScrollableResource<T> cursor) {
    try {
      return getServerSession().createRemoteDelegate(
        RemoteResultSetCursor.class, RemoteResultSetCursorImpl.class, pdoClass, null, cursor);
    }
    catch (RuntimeException | IllegalAccessException | InstantiationException | NoSuchMethodException |
           InvocationTargetException ex) {
      throw new PersistenceException(ex);
    }
  }

}
