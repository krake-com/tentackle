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


package org.tentackle.dbms.rmi;

import org.tentackle.dbms.AbstractDbObject;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.session.PersistenceException;

import java.rmi.RemoteException;
import java.util.List;


/**
 * Base class for the remote delegate of AbstractDbObject.<br>
 * All other subclasses of AbstractDbObject should extend AbstractDbObjectRemoteDelegateImpl to reflect
 * the class hierarchy below AbstractDbObject.
 *
 * @param <P> the database object class
 * @author harald
 */
public class AbstractDbObjectRemoteDelegateImpl<P extends AbstractDbObject<P>>
       extends RemoteDelegateImpl<P>
       implements AbstractDbObjectRemoteDelegate<P> {


  /**
   * persistent object associated to the delegate's session.
   */
  protected P dbObject;


  /**
   * Creates a delegate on the serverSession socket.
   *
   * @param serverSession is the RMI serverSession
   * @param clazz is the subclass of AbstractDbObject
   * @throws RemoteException
   */
  public AbstractDbObjectRemoteDelegateImpl(RemoteDbSessionImpl serverSession, Class<P> clazz) throws RemoteException {
    super(serverSession, clazz);
  }


  @Override
  public void initialize() {
    /**
     * instantiate a singe object and connect to db.
     * This object is used for all methods not returning a new object and thus
     * minimizes object construction.
     */
    dbObject = newInstance();
    dbObject.setOverloadable(true);
  }


  /**
   * Creates a new instance of the required class
   * for methods that return a new object.
   *
   * @return the new object
   */
  @SuppressWarnings("unchecked")
  protected P newInstance() {
    try {
      return AbstractDbObject.newInstance(getSession(), getServicedClass());
    }
    catch (Exception e) {
      throw new PersistenceException(getSession(), "creating object for class '" + getServicedClass() + "' failed", e);
    }
  }


  /**
   * Validates the object before it is saved.<br>
   * The default implementation does nothing.
   *
   * @param object the persistent object
   * @throws java.rmi.RemoteException
   */
  protected void validate(P object) throws RemoteException {
    //
  }

  @Override
  public long obtainReservedId() throws RemoteException {
   try {
      return -dbObject.getIdSource().nextId(getSession());
    }
    catch (Exception e) {
      throw createException(e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public P selectObject(long id) throws RemoteException {
    try {
      return newInstance().selectObject(id);
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public P selectObjectForUpdate(long id) throws RemoteException {
    try {
      return newInstance().selectObjectForUpdate(id);
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<P> selectAllObjects() throws RemoteException {
    try {
      return dbObject.selectAllObjects();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public List<IdSerialTuple> selectAllIdSerial() throws RemoteException {
    try {
      return dbObject.selectAllIdSerial();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public long selectSerial(long id) throws RemoteException {
    try {
      return dbObject.selectSerial(id);
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public long selectMaxId() throws RemoteException {
    try {
      return dbObject.selectMaxId();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public long selectMaxTableSerial() throws RemoteException {
    try {
      return dbObject.selectMaxTableSerial();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public void updatePlain(P obj) throws RemoteException {
    try {
      obj.setSession(getSession());
      obj.updatePlain();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public void dummyUpdate(P obj) throws RemoteException {
    try {
      obj.setSession(getSession());
      obj.dummyUpdate();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public void updateSerial(long id, long serial) throws RemoteException {
    try {
      dbObject.setId(id);
      dbObject.setSerial(serial);
      dbObject.updateSerial();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public void updateAndSetSerial(long id, long serial) throws RemoteException {
    try {
      dbObject.setId(id);
      dbObject.updateSerial(serial);
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public void updateSerialAndTableSerial(long id, long serial, long tableSerial) throws RemoteException {
    try {
      dbObject.setId(id);
      dbObject.setSerial(serial);
      dbObject.setTableSerial(tableSerial);
      dbObject.updateSerialAndTableSerial();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public List<IdSerialTuple> selectExpiredTableSerials(long oldSerial) throws RemoteException {
    try {
      return dbObject.selectExpiredTableSerials(oldSerial);
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public List<IdSerialTuple> selectExpiredTableSerials(long oldSerial, long maxSerial) throws RemoteException {
    try {
      return dbObject.selectExpiredTableSerials(oldSerial, maxSerial);
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public List<IdSerialTuple> getExpirationBacklog(long minSerial, long maxSerial) throws RemoteException {
    try {
      return dbObject.getExpirationBacklog(minSerial, maxSerial);
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public List<IdSerialTuple> getExpiredTableSerials(long oldSerial, long maxSerial) throws RemoteException {
    try {
      return dbObject.getExpiredTableSerials(oldSerial, maxSerial);
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public List<P> selectObjectsWithExpiredTableSerials(long oldSerial) throws RemoteException {
    try {
      return dbObject.selectObjectsWithExpiredTableSerials(oldSerial);
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public void deletePlain(long id, long serial) throws RemoteException {
    try {
      dbObject.setId(id);
      dbObject.setSerial(serial);
      dbObject.deletePlain();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public void insertPlain(P obj) throws RemoteException {
    try {
      obj.setSession(getSession());
      obj.insertPlain();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public DbObjectResult updateObject(P obj) throws RemoteException {
    try {
      obj.setSession(getSession());
      obj.updateObject();
      return new DbObjectResult(obj);
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public DbObjectResult insertObject(P obj) throws RemoteException {
    try {
      obj.setSession(getSession());
      obj.insertObject();
      return new DbObjectResult(obj);
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public DbObjectResult saveObject(P obj) throws RemoteException {
    try {
      obj.setSession(getSession());
      validate(obj);
      obj.saveObject();
      return new DbObjectResult(obj);
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public P persistObject(P obj) throws RemoteException {
    try {
      obj.setSession(getSession());
      validate(obj);
      return obj.persistObject() != null ? obj : null;
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public DbObjectResult deleteObject(P obj) throws RemoteException {
    try {
      obj.setSession(getSession());
      obj.deleteObject();
      return new DbObjectResult(obj);
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public long countModification() throws RemoteException {
    try {
      return dbObject.countModification();
    }
    catch (Exception e) {
      throw createException(e);
    }
  }

  @Override
  public boolean isReferenced(long id) throws RemoteException {
    try {
      P obj = newInstance().selectObject(id);
      return obj != null && obj.isReferenced();
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

}
