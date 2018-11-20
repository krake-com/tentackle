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

package org.tentackle.persist.lock;

import org.tentackle.app.AbstractApplication;
import org.tentackle.common.Service;
import org.tentackle.dbms.Db;
import org.tentackle.log.Logger;
import org.tentackle.misc.IdentifiableKey;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.persist.AbstractPersistentObject;
import org.tentackle.persist.LockManager;
import org.tentackle.persist.TokenLock;
import org.tentackle.persist.app.ServerApplication;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.SessionUtilities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link LockManager}.
 * <p>
 * The token locks are persisted in an extra table of {@link DbTokenLock}s in parallel to the editedBy-tokens
 * of the PDOs.
 */
@Service(LockManager.class)
public class DefaultLockManager implements LockManager {

  private static final Logger LOGGER = Logger.get(DefaultLockManager.class);

  /**
   * The lockmanager is enabled in servers only.<br>
   * In 2-tier applications or 3-tier clients, the token in the PDO will be used and
   * the lock table is ignored.
   */
  private final boolean enabled;

  /**
   * Map of PDO-keys to token locks.<br>
   * The DbTokenLocks must have their sessions set to null if outside a tx!
   */
  private final Map<IdentifiableKey<?>, DbTokenLock> lockMap;


  public DefaultLockManager() {
    enabled = AbstractApplication.getRunningApplication() instanceof ServerApplication;
    lockMap = new HashMap<>();
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void cleanupUserTokens(Db session, long userId) {
    if (enabled) {
      DomainContext context = Pdo.createDomainContext(session);
      getLocks(userId).forEach(tokenLock -> {
        DbTokenLock lock = (DbTokenLock) tokenLock;
        try {
          session.transaction("cleanup user locks", () -> {
            try {
              lock.setSession(session);
              remove(lock, context);  // this will remove from lockMap as well
              return null;
            }
            finally {
              lock.setSession(null);  // in case remove failed
            }
          });
        }
        catch (RuntimeException rx) {
          LOGGER.severe("cleanup stale locktoken failed", rx);
        }
      });
    }
  }

  @Override
  public void cleanupAllTokens(Db session) {
    if (enabled) {
      DomainContext context = Pdo.createDomainContext(session);
      for (DbTokenLock lock: createTokenLock(session).selectAllObjects()) {
        try {
          session.transaction("initialize lockmanager", () -> {
            remove(lock, context);
            return null;
          });
        }
        catch (RuntimeException rx) {
          LOGGER.severe("removing locktoken failed", rx);
        }
      }
    }
  }

  @Override
  public <T extends PersistentDomainObject<T>> TokenLock getLock(T pdo) {
    synchronized (lockMap) {
      return lockMap.get(new IdentifiableKey<>(pdo));
    }
  }

  @Override
  public Collection<TokenLock> getLocks(long userId) {
    synchronized (lockMap) {
      return lockMap.values().stream()
                             .filter(lock -> lock.getLockedBy() == userId)
                             .collect(Collectors.toList());
    }
  }

  @Override
  public Collection<TokenLock> getLocks() {
    synchronized (lockMap) {
      return new ArrayList<>(lockMap.values());
    }
  }

  @Override
  public <T extends PersistentDomainObject<T>> void update(T pdo) {
    if (enabled) {
      synchronized (lockMap) {
        Db db = (Db) pdo.getSession();
        db.transaction("update lock", () -> {
          IdentifiableKey<T> key = new IdentifiableKey<>(pdo);
          DbTokenLock lock = lockMap.get(key);
          boolean locked = pdo.isTokenLocked();
          if (lock == null && locked) {
            lock = createTokenLock(db);
            try {
              lock.setId(pdo.getId());
              lock.setSerial(1);
              lock.setPdoClassId(pdo.getClassId());
              lock.setLockedBy(pdo.getEditedBy());
              lock.setLockedSince(pdo.getEditedSince());
              lock.setLockExpiry(pdo.getEditedExpiry());
              lock.insertPlain();
              lockMap.put(key, lock);
            }
            finally {
              lock.setSession(null);    // remove link to session to allow GC
            }
          }
          else if (lock != null) {
            if (locked) {
              if (lock.getLockedBy() != pdo.getEditedBy() ||
                  !Objects.equals(lock.getLockedSince(), pdo.getEditedSince()) ||
                  !Objects.equals(lock.getLockExpiry(), pdo.getEditedExpiry())) {
                lock.setSession(db);
                try {
                  lock.setLockedBy(pdo.getEditedBy());
                  lock.setLockedSince(pdo.getEditedSince());
                  lock.setLockExpiry(pdo.getEditedExpiry());
                  lock.updatePlain();
                }
                finally {
                  lock.setSession(null);    // remove link to session to allow GC
                }
              }
            }
            else {
              lock.setSession(db);
              try {
                lock.deleteObject();
                lockMap.remove(key);
              }
              finally {
                lock.setSession(null);    // remove link to session to allow GC
              }
            }
          }
          return null;
        });
      }
    }
  }



  /**
   * Removes a token lock from the lock table and the PDO.
   *
   * @param lock the token lock
   * @param context the application's domain context
   */
  private void remove(DbTokenLock lock, DomainContext context) {
    String className = SessionUtilities.getInstance().getClassName(lock.getPdoClassId());
    if (className == null) {
      throw new PersistenceException(context.getSession(), "no PDO class for classId " + lock.getPdoClassId());
    }
    PersistentDomainObject<?> pdo = Pdo.create(className, context).select(lock.getId());
    if (pdo == null) {
      throw new PersistenceException(context.getSession(), "no " + className + " for id " + lock.getId());
    }
    AbstractPersistentObject<?,?> po = (AbstractPersistentObject<?,?>) pdo.getPersistenceDelegate();
    po.clearTokenLock();
    po.updateTokenLockOnly();   // this will also remove the DbTokenLock if in map
    if (!lock.isDeleted()) {    // wasn't in map
      lock.deleteObject();
    }
    LOGGER.warning("stale tokenlock removed for {0}, userId {1}, since {2}, expiry {3}",
                   po.toGenericString(), lock.getLockedBy(), lock.getLockedSince(), lock.getLockExpiry());
  }


  /**
   * Creates a tokenlock persistent object.
   *
   * @param session the session
   * @return the PO
   */
  private DbTokenLock createTokenLock(Db session) {
    return new DbTokenLock(session);
  }

}
