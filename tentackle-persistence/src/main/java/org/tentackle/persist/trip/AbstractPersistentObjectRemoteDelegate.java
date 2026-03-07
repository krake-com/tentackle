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
import org.tentackle.dbms.trip.AbstractDbObjectRemoteDelegate;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.TokenLockInfo;
import org.tentackle.persist.AbstractPersistentObject;

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

  T persistImpl(T obj);

  long saveImpl(T obj);

  long deleteImpl(T obj);

  T select(DomainContext cb, long id, boolean forUpdate, boolean tokenLocked);

  T selectTokenLocked(DomainContext cb, long id);

  List<T> selectAll(DomainContext cb);

  List<T> selectAny(DomainContext cb, Collection<Long> ids);

  RemoteResultSetCursor<T> selectAllAsCursor(DomainContext cb);

  List<T> selectByNormText(DomainContext cb, String normText);

  RemoteResultSetCursor<T> selectByNormTextAsCursor(DomainContext cb, String normText);

  List<T> selectLatest(DomainContext cb, long greaterId, int limit);

  List<T> selectAllForCache(DomainContext cb);

  List<T> selectAnyForCache(DomainContext cb, Collection<Long> ids);

  T selectForCache(DomainContext cb, long id);

  List<T> selectAllWithExpiredTableSerials(DomainContext cb, long oldSerial);

  List<T> selectAllWithExpiredTableSerials(DomainContext cb, long oldSerial, long maxSerial);

  boolean isReferenced(DomainContext cb, long id);

  TokenLockInfo updateTokenLock(DomainContext cb, long id, Timestamp tokenExpiry, long userId, Timestamp curTime);

  T transferTokenLock(T pdo, long userId);

  void updateTokenLockOnly(DomainContext cb, long id, long editedBy, Timestamp editedSince, Timestamp editedExpiry);

}
