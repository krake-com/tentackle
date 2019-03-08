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
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.persist.AbstractPersistentObject;
import org.tentackle.persist.LockManager;
import org.tentackle.persist.TokenLock;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.SessionUtilities;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
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
   * PdoKey for a PDO.
   */
  private static class PdoKey {
    final int classId;
    final long pdoId;

    PdoKey(int classId, long pdoId) {
      this.classId = classId;
      this.pdoId = pdoId;
    }

    PdoKey(DbTokenLock lock) {
      this.classId = lock.getPdoClassId();
      this.pdoId = lock.getPdoId();
    }

    PdoKey(PersistentDomainObject pdo) {
      this.classId = pdo.getClassId();
      this.pdoId = pdo.getId();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      PdoKey key = (PdoKey) o;
      return classId == key.classId &&
          pdoId == key.pdoId;
    }

    @Override
    public int hashCode() {
      return Objects.hash(classId, pdoId);
    }
  }


  /**
   * PdoKey for a transaction in flight.
   */
  private static class TransactionKey {
    final long txNo;
    final WeakReference<Db> sessionRef;

    public TransactionKey(Db session, long txNo) {
      this.txNo = txNo;
      sessionRef = new WeakReference<>(session);
    }

    public TransactionKey(Db session) {
      this(session, session.getTxNumber());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TransactionKey that = (TransactionKey) o;
      return txNo == that.txNo;
    }

    @Override
    public int hashCode() {
      return Objects.hash(txNo);
    }
  }



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
  private final Map<PdoKey, DbTokenLock> lockMap;

  /**
   * Map of transaction numbers to a list of locks that were created but not committed yet.
   */
  private final Map<TransactionKey, Map<PdoKey, DbTokenLock>> createdLocksInFlight;

  /**
   * Map of transaction numbers to a list of locks that were updated but not committed yet.
   */
  private final Map<TransactionKey, Map<PdoKey, DbTokenLock>> updatedLocksInFlight;

  /**
   * Map of transaction numbers to a list of locks that were removed but not committed yet.
   */
  private final Map<TransactionKey, Map<PdoKey, DbTokenLock>> removedLocksInFlight;

  /**
   * Flag to rebuild the internal map in case some unexpected persistence exception happened.
   */
  private boolean rebuild;


  /**
   * Creates the lock manager.<br>
   * Enabled only if running within a server application.
   */
  public DefaultLockManager() {
    enabled = AbstractApplication.isServer();
    lockMap = new HashMap<>();
    createdLocksInFlight = new HashMap<>();
    updatedLocksInFlight = new HashMap<>();
    removedLocksInFlight = new HashMap<>();
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
  public void initialize(Db session) {
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
      return lockMap.get(new PdoKey(pdo.getClassId(), pdo.getId()));
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
      try {
        synchronized (lockMap) {
          PdoKey key = new PdoKey(pdo);
          DbTokenLock lock = lockMap.get(key);
          boolean locked = pdo.isTokenLocked();
          if (lock == null && locked) {
            Db db = (Db) pdo.getSession();
            db.assertTxRunning();
            lock = createTokenLock(db);
            try {
              lock.setId(key.pdoId);
              lock.setSerial(1);
              lock.setPdoClassId(key.classId);
              lock.setLockedBy(pdo.getEditedBy());
              lock.setLockedSince(pdo.getEditedSince());
              lock.setLockExpiry(pdo.getEditedExpiry());
              lock.insertPlain();
              lockMap.put(key, lock);
              addCreatedLock(lock);
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
                Db db = (Db) pdo.getSession();
                db.assertTxRunning();
                lock.setSession(db);
                addUpdatedLock(lock);
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
              Db db = (Db) pdo.getSession();
              db.assertTxRunning();
              lock.setSession(db);
              try {
                lock.deleteObject();
                lockMap.remove(key);
                addRemovedLock(lock);
              }
              finally {
                lock.setSession(null);    // remove link to session to allow GC
              }
            }
          }
        }
      }
      catch (RuntimeException rx) {
        LOGGER.severe("trigger re-initialization of lock manager", rx);
        rebuild = true;
        throw rx;
      }
    }
  }


  @Override
  public void rollback(Db session, long txNumber) {
    if (enabled) {
      TransactionKey txKey = new TransactionKey(session, txNumber);
      synchronized (lockMap) {
        // remove the created in flight
        Map<PdoKey, DbTokenLock> createdLocks = createdLocksInFlight.get(txKey);
        if (createdLocks != null) {
          for (PdoKey key : createdLocks.keySet()) {
            lockMap.remove(key);
          }
        }
        createdLocksInFlight.remove(txKey);

        // restore updated locks
        Map<PdoKey, DbTokenLock> updatedLocks = updatedLocksInFlight.get(txKey);
        if (updatedLocks != null) {
          for (Map.Entry<PdoKey, DbTokenLock> entry : updatedLocks.entrySet()) {
            lockMap.put(entry.getKey(), entry.getValue());
          }
        }
        updatedLocksInFlight.remove(txKey);

        // add removed locks
        Map<PdoKey, DbTokenLock> removedLocks = removedLocksInFlight.get(txKey);
        if (removedLocks != null) {
          for (Map.Entry<PdoKey, DbTokenLock> entry : removedLocks.entrySet()) {
            lockMap.put(entry.getKey(), entry.getValue());
          }
        }
        removedLocksInFlight.remove(txKey);

        rebuildIfFailed(session);
      }
    }
  }

  @Override
  public void commit(Db session, long txNumber) {
    if (enabled) {
      TransactionKey txKey = new TransactionKey(session, txNumber);
      synchronized (lockMap) {
        createdLocksInFlight.remove(txKey);
        updatedLocksInFlight.remove(txKey);
        removedLocksInFlight.remove(txKey);
        rebuildIfFailed(session);
      }
    }
  }


  /**
   * Removes a token lock from the lock table and the PDO.
   *
   * @param lock the token lock
   * @param context the application's domain context
   */
  protected void remove(DbTokenLock lock, DomainContext context) {
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
  protected DbTokenLock createTokenLock(Db session) {
    return new DbTokenLock(session);
  }


  /**
   * Reloads locks from the database into the internal map.<br>
   * There must be no transactions running!
   *
   * @param session the session
   */
  protected void reloadLocks(Db session) {
    lockMap.clear();
    createTokenLock((Db) session).selectAllObjects().forEach(lock -> lockMap.put(new PdoKey(lock), lock));
  }


  /**
   * A lock was created within a transaction.
   *
   * @param lock the new lock
   */
  private void addCreatedLock(DbTokenLock lock) {
    TransactionKey txKey = new TransactionKey(lock.getSession());
    PdoKey key = new PdoKey(lock);
    createdLocksInFlight.computeIfAbsent(txKey, tk -> new HashMap<>()).put(key, lock);
    Map<PdoKey, DbTokenLock> updatedLocks = updatedLocksInFlight.get(txKey);
    if (updatedLocks != null) {
      updatedLocks.remove(key);
    }
    Map<PdoKey, DbTokenLock> removedLocks = removedLocksInFlight.get(txKey);
    if (removedLocks != null) {
      removedLocks.remove(key);
    }
  }

  /**
   * A lock was updated within a transaction.
   *
   * @param lock the updated lock
   */
  private void addUpdatedLock(DbTokenLock lock) {
    DbTokenLock copy = createTokenLock(lock.getSession());
    copy.setId(lock.getId());
    copy.setSerial(lock.getSerial());
    copy.setPdoClassId(lock.getPdoClassId());
    copy.setLockedBy(lock.getLockedBy());
    copy.setLockedSince(lock.getLockedSince());
    copy.setLockExpiry(lock.getLockExpiry());
    // add the first update only
    updatedLocksInFlight.computeIfAbsent(new TransactionKey(lock.getSession()), tk -> new HashMap<>()).putIfAbsent(new PdoKey(copy), copy);
  }

  /**
   * A lock was removed within a transaction.
   *
   * @param lock the removed lock
   */
  private void addRemovedLock(DbTokenLock lock) {
    TransactionKey txKey = new TransactionKey(lock.getSession());
    PdoKey pdoKey = new PdoKey(lock);
    Map<PdoKey, DbTokenLock> updatedLocks = updatedLocksInFlight.get(txKey);
    if (updatedLocks != null) {
      updatedLocks.remove(pdoKey);
    }
    Map<PdoKey, DbTokenLock> createdLocks = createdLocksInFlight.get(txKey);
    if (createdLocks != null) {
      if (createdLocks.remove(pdoKey) != null) {
        // lock was created in flight: don't add to the removed in flight
        return;
      }
    }
    removedLocksInFlight.computeIfAbsent(txKey, tk -> new HashMap<>()).put(pdoKey, lock);
  }

  /**
   * Rebuilds the internal if rebuild requested and no locks are in flight.
   *
   * @param session the session
   */
  private void rebuildIfFailed(Db session) {
    if (rebuild) {
      // we can only rebuild if there are no locks in flight (i.e. no locking relevant tx running)
      if (isTxMapEmpty(createdLocksInFlight) &&
          isTxMapEmpty(updatedLocksInFlight) &&
          isTxMapEmpty(removedLocksInFlight)) {

        reloadLocks(session);
        rebuild = false;
        LOGGER.warning("lock manager re-initialized");
      }
    }
  }

  /**
   * Checks if in-flight map is empty and removes dummy weak references.
   *
   * @param txMap the in-flight map
   * @return true if empty
   */
  private boolean isTxMapEmpty(Map<TransactionKey, Map<PdoKey, DbTokenLock>> txMap) {
    // remove unreferenced transactions
    for (Iterator<TransactionKey> iter = txMap.keySet().iterator(); iter.hasNext(); ) {
      TransactionKey txKey = iter.next();
      if (txKey.sessionRef.get() == null) {
        iter.remove();
      }
    }
    return txMap.isEmpty();
  }

}
