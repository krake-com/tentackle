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

package org.tentackle.persist;

import java.util.Collection;
import org.tentackle.common.ServiceFactory;
import org.tentackle.dbms.Db;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.persist.lock.DefaultLockManager;

interface LockManagerHolder {
  LockManager INSTANCE = ServiceFactory.createService(LockManager.class, DefaultLockManager.class);
}

/**
 * Manager for token locks.
 * <p>
 * The lock manager maintains the state of all token locks in parallel to the editedBy-tokens of the PDOs.<br>
 * This allows to know all locks without the need to scan the PDO tables.
 * <p>
 * The lock manager is used for 2 purposes:
 * <ol>
 *  <li>If a client session crashes, the locks held by the session's user are removed.</li>
 *  <li>When the server crashes, all locks are removed at server start.</li>
 * </ol>
 * Notice that only servers provide an enabled lock manager. In 2-tier applications the
 * lockmanager is disabled and in remote clients not used at all.
 *
 * @see DefaultLockManager
 */
public interface LockManager {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static LockManager getInstance() {
    return LockManagerHolder.INSTANCE;
  }


  /**
   * Returns whether the lock manager is enabled.
   *
   * @return true if server
   */
  boolean isEnabled();

  /**
   * Removes all tokens for a given user.<br>
   * The method is invoked when a user logs out or its session crashed.
   *
   * @param session the session to use
   * @param userId the user id
   */
  void cleanupUserTokens(Db session, long userId);

  /**
   * Removes all token locks.
   *
   * @param session the session to use
   */
  void cleanupAllTokens(Db session);

  /**
   * Gets the token lock for a PDO.
   *
   * @param pdo the PDO
   * @param <T> the PDO type
   * @return the token lock, null if PDO is not locked
   */
  <T extends PersistentDomainObject<T>> TokenLock getLock(T pdo);

  /**
   * Gets the token locks for a user.
   *
   * @param userId the user's ID
   * @return the token locks
   */
  Collection<TokenLock> getLocks(long userId);

  /**
   * Gets all token locks.
   *
   * @return the token locks
   */
  Collection<TokenLock> getLocks();

  /**
   * Updates the lock token.<br>
   * The method is invoked from within a transaction after the PDO has been persisted successfully.
   *
   * @param pdo the PDO
   * @param <T> the PDO type
   */
  <T extends PersistentDomainObject<T>> void update(T pdo);

}
