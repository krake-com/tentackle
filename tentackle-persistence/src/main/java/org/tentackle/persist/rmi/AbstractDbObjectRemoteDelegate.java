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

import java.rmi.RemoteException;
import java.util.List;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.persist.AbstractDbObject;



/**
 * Delegate for the AbstractDbObject class.<br>
 * Notice: in this class we cut off generics!
 * The reason is AbstractDbObject.getRemoteDelegate() which can't
 * be made generic without making AbstractDbObject generic.
 * This would end up with such silly things like "new Customer&lt;Customer&gt;()",
 * because java provides no runtime type information of generic type parameters.
 *
 * @param <P> the db object class
 * @author harald
 */
public interface AbstractDbObjectRemoteDelegate<P extends AbstractDbObject<P>> extends RemoteDelegate {

  long obtainReservedId() throws RemoteException;

  P selectObject(long id) throws RemoteException;

  P selectLockedObject(long id) throws RemoteException;

  List<P> selectAllObjects() throws RemoteException;

  List<IdSerialTuple> selectAllIdSerial() throws RemoteException;

  long selectSerial(long id) throws RemoteException;

  long selectMaxId() throws RemoteException;

  long selectMaxTableSerial() throws RemoteException;

  void deletePlain(long id, long serial) throws RemoteException;

  void insertPlain(P obj) throws RemoteException;

  void updatePlain(P obj) throws RemoteException;

  void dummyUpdate(P obj) throws RemoteException;

  void updateSerial(long id, long serial) throws RemoteException;

  void updateSerialAndTableSerial(long id, long serial, long tableSerial) throws RemoteException;

  List<IdSerialTuple> selectExpiredTableSerials(long oldSerial) throws RemoteException;

  List<IdSerialTuple> selectExpiredTableSerials(long oldSerial, long maxSerial) throws RemoteException;

  List<IdSerialTuple> getExpirationBacklog(long minSerial, long maxSerial) throws RemoteException;

  List<IdSerialTuple> getExpiredTableSerials(long oldSerial, long maxSerial) throws RemoteException;

  List<P> selectObjectsWithExpiredTableSerials(long oldSerial) throws RemoteException;

  DbObjectResult insertObject(P obj) throws RemoteException;

  DbObjectResult updateObject(P obj) throws RemoteException;

  DbObjectResult saveObject(P obj) throws RemoteException;

  P persistObject(P obj) throws RemoteException;

  DbObjectResult deleteObject(P obj) throws RemoteException;

  long countModification() throws RemoteException;

  long getModificationCount() throws RemoteException;

  boolean isReferenced(long id) throws RemoteException;
}
