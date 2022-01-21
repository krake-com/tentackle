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


package org.tentackle.persist.rmi;

import org.tentackle.common.Timestamp;
import org.tentackle.dbms.rmi.AbstractDbObjectRemoteDelegateImpl;
import org.tentackle.dbms.rmi.DbObjectResult;
import org.tentackle.dbms.rmi.RemoteDbSessionImpl;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.misc.ScrollableResource;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.TokenLock;
import org.tentackle.persist.AbstractPersistentObject;
import org.tentackle.session.NotFoundException;

import java.rmi.RemoteException;
import java.util.List;



/**
 * Implementation of the remote delegate for {@link PersistentDomainObject}.<br>
 *
 * @param <T> the {@code PersistentDomainObject} class
 * @param <P> the {@code AbstractPersistenObject} class (persistence implementation)
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
   * PDO with a default domain context associated to the delegate's session.
   */
  protected T pdo;


  /**
   * Creates a delegate on the serverSession socket.
   *
   * @param serverSession the RMI serverSession
   * @param persistenceClass the subclass of AbstractDbObject
   * @param pdoClass the PDO class (interface)
   * @throws java.rmi.RemoteException
   */
  public AbstractPersistentObjectRemoteDelegateImpl(RemoteDbSessionImpl serverSession, Class<P> persistenceClass, Class<T> pdoClass)
          throws RemoteException {
    super(serverSession, persistenceClass);
    this.pdoClass = pdoClass;
  }


  @Override
  @SuppressWarnings("unchecked")
  public void initialize() {
    /**
     * Try to set a default context.
     * This will fail in apps that extend the domain context, but is useful for other apps.
     * We ignore any thrown exception safely.
     */
    try {
      pdo = newInstance(Pdo.createDomainContext(getSession()));
    }
    catch (RuntimeException e) {
      /**
       * Application requires extended contextdb.
       * Leave the dbObject as it is for simple methods.
       * In all other cases, the application must pass the context via the remote method
       * and set the context explicitly.
       */
      pdo = newInstance(null);    // create without domain context
    }
    dbObject = (P) pdo.getPersistenceDelegate();
  }


  /**
   * Creates a new pdo instance of the required class
   * for methods that return a new object.
   *
   * @param context the domain context
   * @return the new pdo
   */
  protected T newInstance(DomainContext context) {
    if (context != null) {
      context.setSession(getSession());
    }
    @SuppressWarnings("unchecked")
    T newPdo = (T) (context == null ? Pdo.create((Class) pdoClass, getSession()) : Pdo.create((Class) pdoClass, context));
    // allow overloads for simple proxy objects
    ((AbstractPersistentObject) newPdo.getPersistenceDelegate()).setOverloadable(true);
    return newPdo;
  }

  /**
   * Selects by ID into a new instance.<br>
   * Throws {@link NotFoundException}, if no such PDO in database.
   *
   * @param context the domain context
   * @param id the ID
   * @return the pdo, never null
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
    // set the db in the context first as this will be copied to the object below
    if (context != null) {
      context.setSession(getSession());   // session in context is transient, so we must set it here
    }
    dbObject.setDomainContext(context);
  }


  // ----------- overridden due to security checks against malicious clients ----------------


  @Override
  public P selectObject(long id) throws RemoteException {
    throw new RemoteException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public P selectObjectForUpdate(long id) throws RemoteException {
    throw new RemoteException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public List<P> selectAllObjects() throws RemoteException {
    throw new RemoteException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public void dummyUpdate(P obj) throws RemoteException {
    throw new RemoteException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public void updateSerial(long id, long serial) throws RemoteException {
    throw new RemoteException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public void updateAndSetSerial(long id, long serial) throws RemoteException {
    throw new RemoteException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public void updateSerialAndTableSerial(long id, long serial, long tableSerial) throws RemoteException {
    throw new RemoteException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public void updatePlain(P obj) throws RemoteException {
    throw new RemoteException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public void deletePlain(long id, long serial) throws RemoteException {
    throw new RemoteException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public void insertPlain(P obj) throws RemoteException {
    throw new RemoteException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public DbObjectResult updateObject(P obj) throws RemoteException {
    throw new RemoteException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public DbObjectResult insertObject(P obj) throws RemoteException {
    throw new RemoteException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public DbObjectResult deleteObject(P obj) throws RemoteException {
    throw new RemoteException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public P persistObject(P obj) throws RemoteException {
    throw new RemoteException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public DbObjectResult saveObject(P obj) throws RemoteException {
    throw new RemoteException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public boolean isReferenced(long id) throws RemoteException {
    throw new RemoteException(ILLEGAL_METHOD_TEXT);
  }

  @Override
  public List<IdSerialTuple> selectAllIdSerial() throws RemoteException {
    throw new RemoteException(ILLEGAL_METHOD_TEXT);
  }



  // --------------- end malicious client checks ----------------


  @Override
  @SuppressWarnings("unchecked")
  public T persistImpl(T obj) throws RemoteException {
    try {
      obj.setSession(getSession());
      P po = (P) obj.getPersistenceDelegate();
      return po.persist();
    }
    catch (Exception e) {
      throw createException(e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void saveImpl(T obj) throws RemoteException {
    try {
      obj.setSession(getSession());
      P po = (P) obj.getPersistenceDelegate();
      po.save();
    }
    catch (Exception e) {
      throw createException(e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void deleteImpl(T obj) throws RemoteException {
    try {
      obj.setSession(getSession());
      P po = (P) obj.getPersistenceDelegate();
      po.delete();
      po.markDeleted();
    }
    catch (Exception e) {
      throw createException(e);
    }
  }

  @Override
  public List<T> selectAll(DomainContext cb) throws RemoteException {
    try {
      setDomainContext(cb);
      dbObject.assertRootEntity();
      return dbObject.selectAll();
    }
    catch (Exception e) {
      throw createException(e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public T select(DomainContext cb, long id, boolean locked) throws RemoteException {
    try {
      return ((P) newInstance(cb).getPersistenceDelegate()).select(id, locked);
    }
    catch (Exception e) {
      throw createException(e);
    }
  }

  @Override
  public List<T> selectByNormText(DomainContext cb, String normText) throws RemoteException {
    try {
      setDomainContext(cb);
      dbObject.assertRootEntity();
      return dbObject.selectByNormText(normText);
    }
    catch (Exception e) {
      throw createException(e);
    }
  }

  @Override
  public RemoteResultSetCursor<T> selectByNormTextAsCursor(DomainContext cb, String normText) throws RemoteException {
    try {
      setDomainContext(cb);
      dbObject.assertRootEntity();
      return createRemoteResultSetCursorDelegate(dbObject.selectByNormTextAsCursor(normText));
    }
    catch (Exception e) {
      throw createException(e);
    }
  }

  @Override
  public List<T> selectAllForCache(DomainContext cb) throws RemoteException {
    try {
      setDomainContext(cb);
      dbObject.assertRootEntity();
      // load via cache, if there is a cache at all
      return dbObject.selectAllCached();
    }
    catch (Exception e) {
      throw createException(e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public T selectForCache(DomainContext cb, long id) throws RemoteException {
    try {
      // load via cache, if there is a cache at all
      return (newInstance(cb).getPersistenceDelegate()).selectCached(id);
    }
    catch (Exception e) {
      throw createException(e);
    }
  }

  @Override
  public RemoteResultSetCursor<T> selectAllAsCursor(DomainContext cb) throws RemoteException {
    try {
      setDomainContext(cb);
      dbObject.assertRootEntity();
      return createRemoteResultSetCursorDelegate(dbObject.selectAllAsCursor());
    }
    catch (Exception e) {
      throw createException(e);
    }
  }

  @Override
  public List<T> selectAllWithExpiredTableSerials(DomainContext cb, long oldSerial) throws RemoteException {
    try {
      setDomainContext(cb);
      dbObject.assertRootEntity();
      return dbObject.selectAllWithExpiredTableSerials(oldSerial);
    }
    catch (Exception e) {
      throw createException(e);
    }
  }

  @Override
  public boolean isReferenced(DomainContext cb, long id) throws RemoteException {
    try {
      T obj = newInstance(cb).select(id);
      return obj != null && obj.isReferenced();
    }
    catch (Exception e) {
      throw createException(e);
    }
  }

  @Override
  public TokenLock updateTokenLock(DomainContext cb, long id, Timestamp tokenExpiry, long userId, Timestamp curTime) throws RemoteException  {
    try {
      P po = getPersistenceDelegate(newInstance(cb, id));
      po.setId(id);
      po.updateTokenLock(tokenExpiry, userId, curTime);
      return new TokenLock(po.getEditedBy(), po.getEditedSince(), po.getEditedExpiry());
    }
    catch (Exception e) {
      throw createException(e);
    }
  }

  @Override
  public T transferTokenLock(T pdo, long userId) throws RemoteException  {
    try {
      return pdo.transferTokenLock(userId);
    }
    catch (Exception e) {
      throw createException(e);
    }
  }

  @Override
  public void updateTokenLockOnly(DomainContext cb, long id, long editedBy, Timestamp editedSince, Timestamp editedExpiry) throws RemoteException {
    try {
      P po = getPersistenceDelegate(newInstance(cb, id));
      po.setId(id);
      po.setEditedBy(editedBy);
      po.setEditedSince(editedSince);
      po.setEditedExpiry(editedExpiry);
      po.updateTokenLockOnly();
    }
    catch (Exception e) {
      throw createException(e);
    }
  }



  /**
   * Creates and exports a remote cursor delegate for a local cursor.
   *
   * @param cursor the local cursor
   * @return the remote cursor delegate
   * @throws RemoteException
   */
  public RemoteResultSetCursor<T> createRemoteResultSetCursorDelegate(ScrollableResource<T> cursor) throws RemoteException {
    try {
      @SuppressWarnings("unchecked")
      RemoteResultSetCursor<T> cursorDelegate = getServerSession().createRemoteDelegate(
              RemoteResultSetCursor.class, RemoteResultSetCursorImpl.class, pdoClass, null, cursor);
      getServerSession().exportRemoteDelegate(cursorDelegate);
      return cursorDelegate;
    }
    catch (Exception ex) {
      throw createException(ex);
    }
  }

}
