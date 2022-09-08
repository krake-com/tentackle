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

import org.tentackle.app.Application;
import org.tentackle.common.Service;
import org.tentackle.dbms.Db;
import org.tentackle.log.Logger;
import org.tentackle.misc.Holder;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.LockException;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.persist.AbstractPersistentObject;
import org.tentackle.persist.LockManager;
import org.tentackle.persist.TokenLock;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.SessionUtilities;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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
   * holds the lock and the name of the domain context.
   */
  private record Lock(DbTokenLock tokenLock, String contextName, boolean tainted) {

    Lock(DbTokenLock tokenLock, String contextName) {
      this(tokenLock, contextName, false);
    }

    Lock(DbTokenLock tokenLock) {
      this(tokenLock, "", true);
    }

    @Override
    public String toString() {
      return tokenLock.toDiagnosticString();
    }
  }

  /**
   * PdoKey for a PDO.
   */
  private record PdoKey(int classId, long pdoId) {

    PdoKey(DbTokenLock lock) {
      this(lock.getPdoClassId(), lock.getPdoId());
    }

    @SuppressWarnings("rawtypes")
    PdoKey(PersistentDomainObject pdo) {
      this(pdo.getClassId(), pdo.getId());
    }
  }


  /**
   * PdoKey for a transaction in flight.
   */
  private static class TransactionKey {
    final long txNo;
    final WeakReference<Db> sessionRef;

    private TransactionKey(Db session, long txNo) {
      this.txNo = txNo;
      sessionRef = new WeakReference<>(session);
    }

    private TransactionKey(Db session) {
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
  private final Map<PdoKey, Lock> lockMap;

  /**
   * Map of transaction numbers to a list of locks that were created but not committed yet.
   */
  private final Map<TransactionKey, Map<PdoKey, Lock>> createdLocksInFlight;

  /**
   * Map of transaction numbers to a list of locks that were updated but not committed yet.
   */
  private final Map<TransactionKey, Map<PdoKey, Lock>> updatedLocksInFlight;

  /**
   * Map of transaction numbers to a list of locks that were removed but not committed yet.
   */
  private final Map<TransactionKey, Map<PdoKey, Lock>> removedLocksInFlight;

  /**
   * Flag to rebuild the internal map in case some unexpected persistence exception happened.
   */
  private boolean rebuild;


  /**
   * Creates the lock manager.<br>
   * Enabled only if running within a server application.
   */
  public DefaultLockManager() {
    Application application = Application.getInstance();
    enabled = application != null && application.isServer() && !application.getSession().isRemote();
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
        DbTokenLock dbTokenLock = (DbTokenLock) tokenLock;
        try {
          session.transaction("cleanup user locks", () -> {
            try {
              dbTokenLock.setSession(session);
              remove(dbTokenLock, context);  // this will remove from lockMap as well
              return null;
            }
            finally {
              dbTokenLock.setSession(null);  // in case remove failed
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
      Lock lock = lockMap.get(new PdoKey(pdo));
      return lock == null ? null : lock.tokenLock;
    }
  }

  @Override
  public Collection<TokenLock> getLocks(long userId) {
    synchronized (lockMap) {
      return lockMap.values().stream()
                    .map(Lock::tokenLock)
                    .filter(tokenLock -> tokenLock.getLockedBy() == userId)
                    .collect(Collectors.toList());
    }
  }

  @Override
  public Collection<TokenLock> getLocks() {
    synchronized (lockMap) {
      return lockMap.values().stream().map(Lock::tokenLock).collect(Collectors.toList());
    }
  }

  @Override
  public <T extends PersistentDomainObject<T>> void update(T pdo) {
    if (enabled) {
      try {
        synchronized (lockMap) {
          PdoKey key = new PdoKey(pdo);
          Lock lock = lockMap.get(key);
          boolean locked = pdo.isTokenLocked();
          if (lock == null && locked) {
            Db db = (Db) pdo.getSession();
            db.assertTxRunning();
            DbTokenLock dbTokenLock = createTokenLock(db);
            try {
              dbTokenLock.setId(key.pdoId);
              dbTokenLock.setSerial(1);
              dbTokenLock.setPdoClassId(key.classId);
              dbTokenLock.setLockedBy(pdo.getEditedBy());
              dbTokenLock.setLockedSince(pdo.getEditedSince());
              dbTokenLock.setLockExpiry(pdo.getEditedExpiry());
              dbTokenLock.insertPlain();
              List<String> names = pdo.getDomainContext().getNames();
              lock = new Lock(dbTokenLock, names.get(names.size() - 1));
              lockMap.put(key, lock);
              addCreatedLock(lock);
            }
            finally {
              dbTokenLock.setSession(null);    // remove link to session to allow GC
            }
          }
          else if (lock != null) {
            Db db = (Db) pdo.getSession();
            if (lock.tainted) {
              LOGGER.warning("{0} is tainted -> within {1}", lock, pdo.getDomainContext().toDiagnosticString());
            }
            else if (!pdo.getDomainContext().isWithinContext(lock.contextName)) {
              // lock is neither unnamed nor a valid name within the PDO's context
              throw new LockException(db, lock.contextName);
            }
            DbTokenLock dbTokenLock = lock.tokenLock;
            if (locked) {
              if (dbTokenLock.getLockedBy() != pdo.getEditedBy() ||
                  !Objects.equals(dbTokenLock.getLockedSince(), pdo.getEditedSince()) ||
                  !Objects.equals(dbTokenLock.getLockExpiry(), pdo.getEditedExpiry())) {
                db.assertTxRunning();
                dbTokenLock.setSession(db);
                addUpdatedLock(lock);
                try {
                  dbTokenLock.setLockedBy(pdo.getEditedBy());
                  dbTokenLock.setLockedSince(pdo.getEditedSince());
                  dbTokenLock.setLockExpiry(pdo.getEditedExpiry());
                  dbTokenLock.updatePlain();
                }
                finally {
                  dbTokenLock.setSession(null);    // remove link to session to allow GC
                }
              }
            }
            else {
              db.assertTxRunning();
              dbTokenLock.setSession(db);
              try {
                dbTokenLock.deleteObject();
                lockMap.remove(key);
                addRemovedLock(lock);
              }
              finally {
                dbTokenLock.setSession(null);    // remove link to session to allow GC
              }
            }
          }
        }
      }
      catch (LockException lx) {
        throw lx;   // already locked for same user, but in wrong context (see above)
      }
      catch (RuntimeException rx) {
        LOGGER.severe("trigger re-initialization of lock manager -> PLEASE FIX THE ROOT-CAUSE A.S.A.P.", rx);
        // Some severe internal error occurred: reload the maps as soon as there are no more transactions in flight.
        // This will keep the server running in a reasonable way, but must be fixed a.s.a.p. in the application's code.
        // See also the note regarding tainted locks in reloadLocks() below.
        rebuild = true;
        throw rx;   // triggers a rollback, hopefully...
      }
    }
  }


  @Override
  public void rollback(Db session, long txNumber) {
    if (enabled) {
      TransactionKey txKey = new TransactionKey(session, txNumber);
      synchronized (lockMap) {
        // remove the created in flight
        Map<PdoKey, Lock> createdLocks = createdLocksInFlight.get(txKey);
        if (createdLocks != null) {
          for (PdoKey key : createdLocks.keySet()) {
            lockMap.remove(key);
          }
        }
        createdLocksInFlight.remove(txKey);

        // restore updated locks
        Map<PdoKey, Lock> updatedLocks = updatedLocksInFlight.get(txKey);
        if (updatedLocks != null) {
          lockMap.putAll(updatedLocks);
        }
        updatedLocksInFlight.remove(txKey);

        // add removed locks
        Map<PdoKey, Lock> removedLocks = removedLocksInFlight.get(txKey);
        if (removedLocks != null) {
          lockMap.putAll(removedLocks);
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
   * @param dbTokenLock the token lock
   * @param context the application's domain context
   */
  protected void remove(DbTokenLock dbTokenLock, DomainContext context) {
    String className = SessionUtilities.getInstance().getClassName(dbTokenLock.getPdoClassId());
    if (className == null) {
      throw new PersistenceException(context.getSession(), "no PDO class for classId " + dbTokenLock.getPdoClassId());
    }
    PdoKey pdoKey = new PdoKey(dbTokenLock);
    synchronized (lockMap) {
      Lock lock = lockMap.get(pdoKey);
      if (lock != null && !context.isWithinContext(lock.contextName)) {
        // upgrade the domain context to avoid exception
        context = context.clone(lock.contextName);
      }
    }
    PersistentDomainObject<?> pdo = Pdo.create(className, context).select(dbTokenLock.getId());
    String poStr;
    if (pdo == null) {
      poStr = className + "[" + dbTokenLock.getId() + "]";
      LOGGER.severe("missing {0} for id {1} in {2}", className, dbTokenLock.getId(), context.getSession());
      // just remove from map
      synchronized (lockMap) {
        lockMap.remove(pdoKey);
      }
    }
    else {
      AbstractPersistentObject<?, ?> po = (AbstractPersistentObject<?, ?>) pdo.getPersistenceDelegate();
      poStr = po.toGenericString();
      po.clearTokenLock();
      po.updateTokenLockOnly();   // this will also remove the DbTokenLock if in map
    }
    if (!dbTokenLock.isDeleted()) {    // wasn't in map
      dbTokenLock.deleteObject();
    }
    LOGGER.warning("stale tokenlock removed for {0}, userId {1}, since {2}, expiry {3}",
                   poStr, dbTokenLock.getLockedBy(), dbTokenLock.getLockedSince(), dbTokenLock.getLockExpiry());
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
   * <p>
   * The method is protected to allow application-specific lock managers to keep certain tokens
   * on server startup after a crash, for example.
   *
   * @param session the session
   */
  protected void reloadLocks(Db session) {
    Map<PdoKey, Lock> oldLockMap = new HashMap<>(lockMap);    // to restore the context names, if possible
    Holder<Integer> taintCountHolder = new Holder<>(0);
    lockMap.clear();
    createTokenLock(session).selectAllObjects().forEach(dbTokenLock -> {
      PdoKey pdoKey = new PdoKey(dbTokenLock);
      Lock oldLock = oldLockMap.get(pdoKey);
      Lock lock;
      if (oldLock != null) {
        lock = new Lock(dbTokenLock, oldLock.contextName);    // name could be restored
      }
      else {
        lock = new Lock(dbTokenLock);   // not found in old map: create a tainted lock
        taintCountHolder.accept(taintCountHolder.get() + 1);
      }
      lockMap.put(pdoKey, lock);
    });
    int taintCount = taintCountHolder.get();
    if (taintCount > 0) {
      // tainted locks created!
      // This will disable all domain context name checks for those remaining active locks,
      // which is probably desirable to prevent subsequent excessive errors.
      // At least, all those operations will be logged as warnings (lock is tainted...).
      LOGGER.warning("{0} locks reloaded in total, {1} tainted!", lockMap.size(), taintCount);
    }
    else {
      LOGGER.info("{0} locks reloaded", lockMap.size());
    }
  }


  /**
   * A lock was created within a transaction.
   *
   * @param lock the new lock
   */
  private void addCreatedLock(Lock lock) {
    DbTokenLock dbTokenLock = lock.tokenLock;
    TransactionKey txKey = new TransactionKey(dbTokenLock.getSession());
    PdoKey key = new PdoKey(dbTokenLock);
    createdLocksInFlight.computeIfAbsent(txKey, tk -> new HashMap<>()).put(key, lock);
    Map<PdoKey, Lock> updatedLocks = updatedLocksInFlight.get(txKey);
    if (updatedLocks != null) {
      updatedLocks.remove(key);
    }
    Map<PdoKey, Lock> removedLocks = removedLocksInFlight.get(txKey);
    if (removedLocks != null) {
      removedLocks.remove(key);
    }
  }

  /**
   * A lock was updated within a transaction.
   *
   * @param lock the updated lock
   */
  private void addUpdatedLock(Lock lock) {
    DbTokenLock dbTokenLock = lock.tokenLock;
    DbTokenLock copyTokenLock = createTokenLock(dbTokenLock.getSession());
    copyTokenLock.setId(dbTokenLock.getId());
    copyTokenLock.setSerial(dbTokenLock.getSerial());
    copyTokenLock.setPdoClassId(dbTokenLock.getPdoClassId());
    copyTokenLock.setLockedBy(dbTokenLock.getLockedBy());
    copyTokenLock.setLockedSince(dbTokenLock.getLockedSince());
    copyTokenLock.setLockExpiry(dbTokenLock.getLockExpiry());
    // add the first update only
    updatedLocksInFlight.computeIfAbsent(new TransactionKey(dbTokenLock.getSession()), tk -> new HashMap<>())
                        .putIfAbsent(new PdoKey(copyTokenLock), new Lock(copyTokenLock, lock.contextName));
  }

  /**
   * A lock was removed within a transaction.
   *
   * @param lock the removed lock
   */
  private void addRemovedLock(Lock lock) {
    DbTokenLock dbTokenLock = lock.tokenLock;
    TransactionKey txKey = new TransactionKey(dbTokenLock.getSession());
    PdoKey pdoKey = new PdoKey(dbTokenLock);
    Map<PdoKey, Lock> updatedLocks = updatedLocksInFlight.get(txKey);
    if (updatedLocks != null) {
      updatedLocks.remove(pdoKey);
    }
    Map<PdoKey, Lock> createdLocks = createdLocksInFlight.get(txKey);
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
      // we can only rebuild if there are no locks in flight (i.e. no locking relevant transactions running)
      if (isTxMapEmpty(createdLocksInFlight) &&
          isTxMapEmpty(updatedLocksInFlight) &&
          isTxMapEmpty(removedLocksInFlight)) {

        reloadLocks(session);
        rebuild = false;
      }
    }
  }

  /**
   * Checks if in-flight map is empty and removes dummy weak references.
   *
   * @param txMap the in-flight map
   * @return true if empty
   */
  private boolean isTxMapEmpty(Map<TransactionKey, Map<PdoKey, Lock>> txMap) {
    // remove unreferenced transactions
    txMap.keySet().removeIf(txKey -> txKey.sessionRef.get() == null);
    return txMap.isEmpty();
  }

}
