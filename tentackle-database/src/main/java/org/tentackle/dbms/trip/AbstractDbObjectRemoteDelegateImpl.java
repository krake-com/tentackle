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

package org.tentackle.dbms.trip;

import org.tentackle.dbms.AbstractDbObject;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.session.PersistenceException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Base class for the remote delegate of AbstractDbObject.<br>
 * All other subclasses of AbstractDbObject should extend AbstractDbObjectRemoteDelegateImpl to reflect
 * the class hierarchy below AbstractDbObject.
 *
 * @param <P> the database object class
 * @author harald
 */
public class AbstractDbObjectRemoteDelegateImpl<P extends AbstractDbObject<P>>
       extends RemoteDbDelegateImpl<P>
       implements AbstractDbObjectRemoteDelegate<P> {


  /**
   * persistent object associated with the delegate's session.
   */
  protected P dbObject;


  /**
   * Creates a delegate on the serverSession socket.
   *
   * @param serverSession is the TRIP server session
   * @param clazz         is the subclass of AbstractDbObject
   */
  public AbstractDbObjectRemoteDelegateImpl(RemoteDbSessionImpl serverSession, Class<P> clazz) {
    super(serverSession, clazz);
  }


  @Override
  public void initialize() {
    /*
     * Instantiate a single object and connect to db.
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
  protected P newInstance() {
    try {
      return AbstractDbObject.newInstance(getSession(), getServicedClass());
    }
    catch (RuntimeException e) {
      throw new PersistenceException(getSession(), "creating object for class '" + getServicedClass() + "' failed", e);
    }
  }


  /**
   * Validates the object before it is saved.<br>
   * The default implementation does nothing.
   *
   * @param object the persistent object
   */
  protected void validate(P object) {
    //
  }

  @Override
  public long obtainReservedId() {
    return -dbObject.getIdSource().nextId(getSession());
  }

  @Override
  public P selectObject(long id) {
    return newInstance().selectObject(id);
  }

  @Override
  public P selectObjectForUpdate(long id) {
    return newInstance().selectObjectForUpdate(id);
  }

  @Override
  public List<P> selectAllObjects() {
    return dbObject.selectAllObjects();
  }

  @Override
  public List<IdSerialTuple> selectAllIdSerial() {
    return dbObject.selectAllIdSerial();
  }

  @Override
  public long selectSerial(long id) {
    return dbObject.selectSerial(id);
  }

  @Override
  public long selectMaxId() {
    return dbObject.selectMaxId();
  }

  @Override
  public long selectMaxTableSerial() {
    return dbObject.selectMaxTableSerial();
  }

  @Override
  public void updatePlain(P obj) {
    obj.setSession(getSession());
    obj.updatePlain();
  }

  @Override
  public void dummyUpdate(P obj) {
    obj.setSession(getSession());
    obj.dummyUpdate();
  }

  @Override
  public void updateSerial(long id, long serial) {
    dbObject.setId(id);
    dbObject.setSerial(serial);
    dbObject.updateSerial();
  }

  @Override
  public void updateAndSetSerial(long id, long serial) {
    dbObject.setId(id);
    dbObject.updateSerial(serial);
  }

  @Override
  public void updateSerialAndTableSerial(long id, long serial, long tableSerial) {
    dbObject.setId(id);
    dbObject.setSerial(serial);
    dbObject.setTableSerial(tableSerial);
    dbObject.updateSerialAndTableSerial();
  }

  @Override
  public List<IdSerialTuple> selectExpiredTableSerials(long oldSerial) {
    return dbObject.selectExpiredTableSerials(oldSerial);
  }

  @Override
  public List<IdSerialTuple> selectExpiredTableSerials(long oldSerial, long maxSerial) {
    return dbObject.selectExpiredTableSerials(oldSerial, maxSerial);
  }

  @Override
  public Collection<IdSerialTuple> getExpiredTableSerials(long oldSerial, long maxSerial) {
    Collection<IdSerialTuple> expiredTableSerials = dbObject.getExpiredTableSerials(oldSerial, maxSerial);
    if (expiredTableSerials instanceof Set) {
      // local servers internally use a TreeSet with its own comparator, which is not serializable.
      expiredTableSerials = new ArrayList<>(expiredTableSerials);
    }
    return expiredTableSerials;
  }

  @Override
  public List<P> selectObjectsWithExpiredTableSerials(long oldSerial) {
    return dbObject.selectObjectsWithExpiredTableSerials(oldSerial);
  }

  @Override
  public void deletePlain(long id, long serial) {
    dbObject.setId(id);
    dbObject.setSerial(serial);
    dbObject.deletePlain();
  }

  @Override
  public void insertPlain(P obj) {
    obj.setSession(getSession());
    obj.insertPlain();
  }

  @Override
  public DbObjectResult updateObject(P obj) {
    obj.setSession(getSession());
    obj.updateObject();
    return new DbObjectResult(obj);
  }

  @Override
  public DbObjectResult insertObject(P obj) {
    obj.setSession(getSession());
    obj.insertObject();
    return new DbObjectResult(obj);
  }

  @Override
  public DbObjectResult saveObject(P obj) {
    obj.setSession(getSession());
    validate(obj);
    obj.saveObject();
    return new DbObjectResult(obj);
  }

  @Override
  public P persistObject(P obj) {
    obj.setSession(getSession());
    validate(obj);
    return obj.persistObject() != null ? obj : null;
  }

  @Override
  public DbObjectResult deleteObject(P obj) {
    obj.setSession(getSession());
    obj.deleteObject();
    return new DbObjectResult(obj);
  }

  @Override
  public long countModification() {
    return dbObject.countModification();
  }

  @Override
  public boolean isReferenced(long id) {
    P obj = newInstance().selectObject(id);
    return obj != null && obj.isReferenced();
  }

}
