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

package org.tentackle.persist.rmi;

import org.tentackle.common.Timestamp;
import org.tentackle.dbms.rmi.AbstractDbObjectRemoteDelegate;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.TokenLockInfo;
import org.tentackle.persist.AbstractPersistentObject;

import java.rmi.RemoteException;
import java.util.Collection;
import java.util.List;

/**
 * Remote delegate for {@link PersistentDomainObject}.
 *
 * @param <T> the PDO class (interface)
 * @param <P> the persistence implementation class
 * @author harald
 */
public interface AbstractPersistentObjectRemoteDelegate<T extends PersistentDomainObject<T>, P extends AbstractPersistentObject<T,P>>
       extends AbstractDbObjectRemoteDelegate<P> {

  T persistImpl(T obj) throws RemoteException;

  long saveImpl(T obj) throws RemoteException;

  long deleteImpl(T obj) throws RemoteException;

  T select(DomainContext cb, long id, boolean forUpdate, boolean tokenLocked) throws RemoteException;

  T selectTokenLocked(DomainContext cb, long id) throws RemoteException;

  List<T> selectAll(DomainContext cb) throws RemoteException;

  List<T> selectAny(DomainContext cb, Collection<Long> ids) throws RemoteException;

  RemoteResultSetCursor<T> selectAllAsCursor(DomainContext cb) throws RemoteException;

  List<T> selectByNormText(DomainContext cb, String normText) throws RemoteException;

  RemoteResultSetCursor<T> selectByNormTextAsCursor(DomainContext cb, String normText) throws RemoteException;

  List<T> selectLatest(DomainContext cb, long greaterId, int limit) throws RemoteException;

  List<T> selectAllForCache(DomainContext cb) throws RemoteException;

  List<T> selectAnyForCache(DomainContext cb, Collection<Long> ids) throws RemoteException;

  T selectForCache(DomainContext cb, long id) throws RemoteException;

  List<T> selectAllWithExpiredTableSerials(DomainContext cb, long oldSerial) throws RemoteException;

  List<T> selectAllWithExpiredTableSerials(DomainContext cb, long oldSerial, long maxSerial) throws RemoteException;

  boolean isReferenced(DomainContext cb, long id) throws RemoteException;

  TokenLockInfo updateTokenLock(DomainContext cb, long id, Timestamp tokenExpiry, long userId, Timestamp curTime) throws RemoteException;

  T transferTokenLock(T pdo, long userId) throws RemoteException;

  void updateTokenLockOnly(DomainContext cb, long id, long editedBy, Timestamp editedSince, Timestamp editedExpiry) throws RemoteException;

}
