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
 * Remote delegate for {@link AbstractPersistentObject}.<br>
 * The methods are invoked by the persistence delegate of a PDO in a remote session
 * and executed on the server.
 *
 * @param <T> the PDO class (interface)
 * @param <P> the persistence implementation class
 * @author harald
 */
public interface AbstractPersistentObjectRemoteDelegate<T extends PersistentDomainObject<T>, P extends AbstractPersistentObject<T,P>>
       extends AbstractDbObjectRemoteDelegate<P> {

  /**
   * Persists a PDO.
   *
   * @param obj the PDO
   * @return the persisted PDO
   * @see AbstractPersistentObject#persist()
   */
  T persistImpl(T obj);

  /**
   * Saves a PDO.
   *
   * @param obj the PDO
   * @return the table serial
   * @see AbstractPersistentObject#save()
   */
  long saveImpl(T obj);

  /**
   * Deletes a PDO.
   *
   * @param obj the PDO
   * @return the table serial
   * @see AbstractPersistentObject#delete()
   */
  long deleteImpl(T obj);

  /**
   * Selects a PDO by its ID.
   *
   * @param context the domain context
   * @param id the object ID
   * @param forUpdate true if select FOR UPDATE
   * @param tokenLocked true if request a token lock as well
   * @return the PDO, null if no such PDO
   * @see AbstractPersistentObject#select(long, boolean, boolean)
   */
  T select(DomainContext context, long id, boolean forUpdate, boolean tokenLocked);

  /**
   * Selects a PDO by its ID and requests a token lock.
   *
   * @param context the domain context
   * @param id the object ID
   * @return the PDO, null if no such PDO
   * @see AbstractPersistentObject#selectTokenLocked(long)
   */
  T selectTokenLocked(DomainContext context, long id);

  /**
   * Selects all PDOs.
   *
   * @param context the domain context
   * @return the PDOs
   * @see AbstractPersistentObject#selectAll()
   */
  List<T> selectAll(DomainContext context);

  /**
   * Selects all PDOs with the given IDs.
   *
   * @param context the domain context
   * @param ids the object IDs
   * @return the PDOs
   * @see AbstractPersistentObject#selectAny(Collection)
   */
  List<T> selectAny(DomainContext context, Collection<Long> ids);

  /**
   * Selects all PDOs as a cursor.
   *
   * @param context the domain context
   * @return the remote cursor
   * @see AbstractPersistentObject#selectAllAsCursor()
   */
  RemoteResultSetCursor<T> selectAllAsCursor(DomainContext context);

  /**
   * Selects all PDOs matching a normtext.
   *
   * @param context the domain context
   * @param normText the normtext to search for
   * @return the PDOs
   * @see AbstractPersistentObject#selectByNormText(String)
   */
  List<T> selectByNormText(DomainContext context, String normText);

  /**
   * Selects all PDOs matching a normtext as a cursor.
   *
   * @param context the domain context
   * @param normText the normtext to search for
   * @return the remote cursor
   * @see AbstractPersistentObject#selectByNormTextAsCursor(String)
   */
  RemoteResultSetCursor<T> selectByNormTextAsCursor(DomainContext context, String normText);

  /**
   * Selects the latest PDOs.
   *
   * @param context the domain context
   * @param greaterId the ID the PDOs must be greater than
   * @param limit the maximum number of PDOs
   * @return the PDOs
   * @see AbstractPersistentObject#selectLatest(long, int)
   */
  List<T> selectLatest(DomainContext context, long greaterId, int limit);

  /**
   * Selects all PDOs via the server's cache, if any.
   *
   * @param context the domain context
   * @return the PDOs
   * @see AbstractPersistentObject#selectAllCached()
   */
  List<T> selectAllForCache(DomainContext context);

  /**
   * Selects all PDOs with the given IDs via the server's cache, if any.
   *
   * @param context the domain context
   * @param ids the object IDs
   * @return the PDOs
   * @see AbstractPersistentObject#selectAnyCached(Collection)
   */
  List<T> selectAnyForCache(DomainContext context, Collection<Long> ids);

  /**
   * Selects a PDO via the server's cache, if any.
   *
   * @param context the domain context
   * @param id the object ID
   * @return the PDO, null if no such PDO
   * @see AbstractPersistentObject#selectCached(long)
   */
  T selectForCache(DomainContext context, long id);

  /**
   * Selects all PDOs with a table serial greater than the given serial.
   *
   * @param context the domain context
   * @param oldSerial the last known table serial
   * @return the PDOs
   * @see AbstractPersistentObject#selectAllWithExpiredTableSerials(long)
   */
  List<T> selectAllWithExpiredTableSerials(DomainContext context, long oldSerial);

  /**
   * Selects all PDOs with a table serial within a given range.
   *
   * @param context the domain context
   * @param oldSerial the last known table serial (exclusive)
   * @param maxSerial the maximum table serial (inclusive)
   * @return the PDOs
   * @see AbstractPersistentObject#selectAllWithExpiredTableSerials(long, long)
   */
  List<T> selectAllWithExpiredTableSerials(DomainContext context, long oldSerial, long maxSerial);

  /**
   * Checks whether a PDO is referenced by other PDOs.
   *
   * @param context the domain context
   * @param id the object ID
   * @return true if the PDO exists and is referenced
   * @see AbstractPersistentObject#isReferenced()
   */
  boolean isReferenced(DomainContext context, long id);

  /**
   * Requests or releases a token lock.
   *
   * @param context the domain context
   * @param id the object ID
   * @param tokenExpiry the time when the token will expire, null to release the lock
   * @param userId the user ID of the current user
   * @param curTime the current time
   * @return the token lock attributes after the update
   * @see AbstractPersistentObject#updateTokenLock(Timestamp, long, Timestamp)
   */
  TokenLockInfo updateTokenLock(DomainContext context, long id, Timestamp tokenExpiry, long userId, Timestamp curTime);

  /**
   * Transfers a token lock to another user.
   *
   * @param pdo the PDO
   * @param userId the user ID of the new token holder, 0 to clear the token
   * @return the PDO with the updated token lock attributes
   * @see AbstractPersistentObject#transferTokenLock(long)
   */
  T transferTokenLock(T pdo, long userId);

  /**
   * Updates the token lock columns only.
   *
   * @param context the domain context
   * @param id the object ID
   * @param editedBy the user ID of the token holder, 0 if unlocked
   * @param editedSince the time when editing started
   * @param editedExpiry the time when the token will expire
   * @see AbstractPersistentObject#updateTokenLockOnly()
   */
  void updateTokenLockOnly(DomainContext context, long id, long editedBy, Timestamp editedSince, Timestamp editedExpiry);

}
