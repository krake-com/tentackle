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
import org.tentackle.common.StringHelper;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link LockManager}.
 * <p>
 * The token locks are persisted in an extra table of {@link DbTokenLock}s in parallel to the token-lock columns
 * of the PDOs. Additionally, the locks are kept in memory along with the name of the domain context
 * they were created in. Changes within transactions are tracked and reverted on rollback.
 * <p>
 * Applications may keep certain token locks on session cleanup or server startup by overriding
 * {@link #isTokenLockToKeep(DbTokenLock, DomainContext)}.
 */
@Service(LockManager.class)
public class DefaultLockManager implements LockManager {

  private static final Logger LOGGER = Logger.get(DefaultLockManager.class);

  /**
   * Holds the lock, the name of the domain context and the tainted flag.<br>
   * A lock is tainted if its context name is unknown, i.e., it was loaded from the database.
   */
  private record Lock(DbTokenLock tokenLock, String contextName, boolean tainted) {

    /**
     * Creates an untainted lock.
     *
     * @param tokenLock the token lock
     * @param contextName the last name of the domain context chain
     */
    Lock(DbTokenLock tokenLock, String contextName) {
      this(tokenLock, contextName, false);
    }

    /**
     * Creates a tainted lock.
     *
     * @param tokenLock the token lock
     */
    Lock(DbTokenLock tokenLock) {
      this(tokenLock, "", true);
    }

    @Override
    public String toString() {
      return tokenLock.toDiagnosticString() + toContextString();
    }

    private String toContextString() {
      StringBuilder buf = new StringBuilder();
      if (!StringHelper.isAllWhitespace(contextName)) {
        buf.append(", context '").append(contextName).append('\'');
      }
      if (tainted) {
        buf.append(", tainted");
      }
      return buf.toString();
    }
  }

  /**
   * Key identifying a PDO by its class ID and object ID.
   */
  private record PdoKey(int classId, long pdoId) {

    PdoKey(DbTokenLock lock) {
      this(lock.getPdoClassId(), lock.getPdoId());
    }

    @SuppressWarnings("rawtypes")
    PdoKey(PersistentDomainObject pdo) {
      this(pdo.getClassId(), pdo.getId());
    }

    @Override
    public String toString() {
      return "classId " + classId + ", id " + pdoId;
    }
  }


  /**
   * Key for a transaction in flight.
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

    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder();
      buf.append("TX:").append(txNo);
      Db session = sessionRef.get();
      if (session != null) {
        buf.append(", ").append(session);
      }
      return buf.toString();
    }
  }



  /**
   * The lock manager is enabled in servers connected directly to the database only.<br>
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
   * Map of transactions to the locks that were created but not committed yet.
   */
  private final Map<TransactionKey, Map<PdoKey, Lock>> createdLocksInFlight;

  /**
   * Map of transactions to the original state of the locks that were updated but not committed yet.
   */
  private final Map<TransactionKey, Map<PdoKey, Lock>> updatedLocksInFlight;

  /**
   * Map of transactions to the locks that were removed but not committed yet.
   */
  private final Map<TransactionKey, Map<PdoKey, Lock>> removedLocksInFlight;

  /**
   * Flag to rebuild the internal map in case some unexpected persistence exception happened.
   */
  private boolean rebuild;


  /**
   * Creates the lock manager.<br>
   * Enabled only if running within a server application connected directly to the database.
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

  /**
   * {@inheritDoc}
   * <p>
   * Token locks for which {@link #isTokenLockToKeep(DbTokenLock, DomainContext)} returns true are kept.
   * Tokens taken over by another user in the meantime are not removed.
   */
  @Override
  public void cleanupUserTokens(Db session, long userId) {
    if (enabled) {
      DomainContext context = Pdo.createDomainContext(session);

      // Take a snapshot of the user's locks while holding the monitor, but do the database work
      // later to avoid a potential deadlock.
      List<DbTokenLock> userTokenLocks;
      synchronized (lockMap) {
        userTokenLocks = lockMap.values().stream()
                                .map(Lock::tokenLock)
                                .filter(dbTokenLock -> dbTokenLock.getLockedBy() == userId)
                                .toList();
      }

      for (DbTokenLock dbTokenLock : userTokenLocks) {
        if (isTokenLockToKeep(dbTokenLock, context)) {
          LOGGER.warning("kept token lock for class {0}, id {1}, userId {2}, since {3}, expiry {4}",
                         dbTokenLock.getPdoClassId(), dbTokenLock.getPdoId(),
                         dbTokenLock.getLockedBy(), dbTokenLock.getLockedSince(), dbTokenLock.getLockExpiry());
        }
        else {
          try {
            session.transaction("cleanup user locks", () -> {
              try {
                dbTokenLock.setSession(session);
                // this will remove from lockMap as well
                removeStaleTokenLock(dbTokenLock, context, userId);
                return null;
              }
              finally {
                dbTokenLock.setSession(null);  // in case remove failed
              }
            });
          }
          catch (RuntimeException rx) {
            LOGGER.severe("cleanup stale token lock failed", rx);
          }
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Token locks for which {@link #isTokenLockToKeep(DbTokenLock, DomainContext)} returns true are kept
   * as tainted locks, since their domain context is unknown.
   */
  @Override
  public void initialize(Db session) {
    if (enabled) {
      DomainContext context = Pdo.createDomainContext(session);

      // classify outside the monitor: isTokenLockToKeep() is an application hook and may hit the database
      List<DbTokenLock> keptTokenLocks = new ArrayList<>();
      List<DbTokenLock> staleTokenLocks = new ArrayList<>();
      for (DbTokenLock dbTokenLock : createTokenLock(session).selectAllObjects()) {
        if (isTokenLockToKeep(dbTokenLock, context)) {
          keptTokenLocks.add(dbTokenLock);
        }
        else {
          staleTokenLocks.add(dbTokenLock);
        }
      }

      synchronized (lockMap) {
        for (DbTokenLock dbTokenLock : keptTokenLocks) {
          // add as tainted lock since we don't know the context name
          lockMap.put(new PdoKey(dbTokenLock), new Lock(dbTokenLock));
          dbTokenLock.setSession(null);    // remove link to session to allow GC
          LOGGER.warning("added token lock for class {0}, id {1}, userId {2}, since {3}, expiry {4}",
                         dbTokenLock.getPdoClassId(), dbTokenLock.getPdoId(),
                         dbTokenLock.getLockedBy(), dbTokenLock.getLockedSince(), dbTokenLock.getLockExpiry());
        }
      }

      // again, never hold the monitor across a transaction (see cleanupUserTokens)
      for (DbTokenLock dbTokenLock : staleTokenLocks) {
        try {
          session.transaction("initialize lockmanager", () -> {
            removeStaleTokenLock(dbTokenLock, context, 0);   // no owner check: no clients yet
            return null;
          });
        }
        catch (RuntimeException rx) {
          LOGGER.severe("removing token lock failed", rx);
        }
      }

      synchronized (lockMap) {
        if (lockMap.isEmpty()) {
          LOGGER.info("lock manager initialized");
        }
        else {
          LOGGER.warning("lock manager initialized with {0} tainted locks", lockMap.size());
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

  /**
   * {@inheritDoc}
   * <p>
   * Creates, updates or removes the {@link DbTokenLock} according to the token-lock attributes of the PDO.
   * If an unexpected exception occurs, the internal map is rebuilt from the database as soon as
   * there are no more transactions in flight.
   *
   * @throws LockException if the lock was created within a domain context the PDO's context does not belong to
   */
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
              Lock createdlock = new Lock(dbTokenLock, pdo.getDomainContext().getNames().getLast());
              lockMap.put(key, createdlock);
              addCreatedLock(createdlock);
              LOGGER.fine("created {0}", () -> pdoLockToString(pdo, createdlock));
            }
            finally {
              dbTokenLock.setSession(null);    // remove link to session to allow GC
            }
          }
          else if (lock != null) {
            Db db = (Db) pdo.getSession();
            if (lock.tainted) {
              Lock untaintedLock = new Lock(lock.tokenLock, pdo.getDomainContext().getNames().getLast());
              lockMap.put(key, untaintedLock);
              LOGGER.warning("{0} updated to untainted within {1}", lock, pdo.getDomainContext().toDiagnosticString());
              lock = untaintedLock;
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
                  Lock updatedLock = lock;    // due to lambda below
                  LOGGER.fine("updated {0}", () -> pdoLockToString(pdo, updatedLock));
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
                Lock removedLock = lock;    // due to lambda below
                LOGGER.fine("released {0}", () -> pdoLockToString(pdo, removedLock));
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
        // The monitor was released along with the try-block above, so re-acquire it: rebuild is read
        // by rebuildIfFailed() under the monitor and would otherwise be missed by the committing thread.
        synchronized (lockMap) {
          rebuild = true;
        }
        throw rx;   // triggers a rollback, hopefully...
      }
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Reverts all lock changes made within the transaction.
   */
  @Override
  public void rollback(Db session, long txNumber) {
    if (enabled) {
      TransactionKey txKey = new TransactionKey(session, txNumber);
      synchronized (lockMap) {
        // remove the created in flight
        Map<PdoKey, Lock> createdLocks = createdLocksInFlight.get(txKey);
        if (createdLocks != null) {
          for (PdoKey key : createdLocks.keySet()) {
            Lock removedLock = lockMap.remove(key);
            if (removedLock != null) {
              LOGGER.fine("rolled back {0}", () -> lockToString(key, removedLock));
            }
          }
        }
        createdLocksInFlight.remove(txKey);

        // restore updated locks
        Map<PdoKey, Lock> updatedLocks = updatedLocksInFlight.get(txKey);
        if (updatedLocks != null) {
          if (LOGGER.isFineLoggable()) {
            for (Map.Entry<PdoKey, Lock> entry : updatedLocks.entrySet()) {
              PdoKey pdoKey = entry.getKey();
              Lock lock = entry.getValue();
              lockMap.put(pdoKey, lock);
              LOGGER.fine("updated {0}", lockToString(pdoKey, lock));
            }
          }
          else {
            lockMap.putAll(updatedLocks);
          }
        }
        updatedLocksInFlight.remove(txKey);

        // add removed locks
        Map<PdoKey, Lock> removedLocks = removedLocksInFlight.get(txKey);
        if (removedLocks != null) {
          if (LOGGER.isFineLoggable()) {
            for (Map.Entry<PdoKey, Lock> entry : removedLocks.entrySet()) {
              PdoKey pdoKey = entry.getKey();
              Lock lock = entry.getValue();
              lockMap.put(pdoKey, lock);
              LOGGER.fine("restored {0}", lockToString(pdoKey, lock));
            }
          }
          else {
            lockMap.putAll(removedLocks);
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
   * Creates a token lock persistent object.
   *
   * @param session the session
   * @return the PO
   */
  protected DbTokenLock createTokenLock(Db session) {
    return new DbTokenLock(session);
  }

  /**
   * Returns whether a token lock should be kept on session cleanup or after server start.<br>
   * The default implementation returns false.
   *
   * @param dbTokenLock the token lock
   * @param context the domain context to use
   * @return true to keep it
   */
  protected boolean isTokenLockToKeep(DbTokenLock dbTokenLock, DomainContext context) {
    return false;
  }

  /**
   * Reloads locks from the database into the internal map.<br>
   * Invoked to rebuild the map after an unexpected error in {@link #update(PersistentDomainObject)}.
   * There must be no transactions running, and the caller must hold the monitor of the internal map!
   * <p>
   * The context names are restored from the old map, if possible.
   * Otherwise, the locks are marked as tainted.
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
      dbTokenLock.setSession(null);    // remove link to session to allow GC
    });
    int taintCount = taintCountHolder.get();
    if (taintCount > 0) {
      // tainted locks created!
      // This will disable all domain context name checks for those remaining active locks,
      // which is probably desirable to prevent later excessive errors.
      // At least, all those operations will be logged as warnings (lock is tainted...).
      LOGGER.warning("{0} locks reloaded in total, {1} tainted!", lockMap.size(), taintCount);
    }
    else {
      LOGGER.info("{0} locks reloaded", lockMap.size());
    }
  }


  /**
   * Removes a stale token lock from the lock table and the PDO.
   * <p>
   * Must be invoked from within a transaction and <em>without</em> holding the monitor of
   * {@code lockMap}: the method updates the PDO and thus acquires database row locks, which must
   * never happen while the monitor is held (see {@link #cleanupUserTokens} for the deadlock this
   * would cause). The map itself is accessed under short, self-contained synchronizations.
   *
   * @param dbTokenLock the token lock
   * @param context the application's domain context
   * @param expectedUserId the user the token must still belong to, 0 to remove it regardless
   */
  private void removeStaleTokenLock(DbTokenLock dbTokenLock, DomainContext context, long expectedUserId) {
    String className = SessionUtilities.getInstance().getClassName(dbTokenLock.getPdoClassId());
    if (className == null) {
      throw new PersistenceException(context.getSession(), "no PDO class for classId " + dbTokenLock.getPdoClassId());
    }
    PdoKey pdoKey = new PdoKey(dbTokenLock);

    String contextName;
    synchronized (lockMap) {
      Lock lock = lockMap.get(pdoKey);
      contextName = lock == null ? null : lock.contextName;
    }
    if (contextName != null && !context.isWithinContext(contextName)) {
      // upgrade the domain context to avoid exception
      context = context.clone(contextName);
    }

    PersistentDomainObject<?> pdo = Pdo.create(className, context).select(dbTokenLock.getId());
    String poStr;
    if (pdo == null) {
      poStr = className + "[" + dbTokenLock.getId() + "]";
      LOGGER.severe("missing {0} in {1}", poStr, context.getSession());
      // just remove from map
      synchronized (lockMap) {
        lockMap.remove(pdoKey);
      }
    }
    else {
      AbstractPersistentObject<?, ?> po = (AbstractPersistentObject<?, ?>) pdo.getPersistenceDelegate();
      poStr = po.toGenericString();
      if (expectedUserId != 0 && po.getEditedBy() != expectedUserId) {
        // another user took the token over in the meantime: leave it alone, it's not stale anymore
        LOGGER.warning("token lock for {0} taken over by userId {1}: stale token of userId {2} not removed",
                       poStr, po.getEditedBy(), expectedUserId);
        return;
      }
      po.clearTokenLock();
      po.updateTokenLockOnly();   // this will also remove the DbTokenLock if in map and set DbTokenLock.session to null
    }

    if (dbTokenLock.getSession() != null) {   // if not removed by po.updateTokenLockOnly() above
      DbTokenLock reloadedDbTokenLock = dbTokenLock.reloadObject();
      if (reloadedDbTokenLock != null) {    // still in database table (wasn't in map or PDO didn't exist)
        LOGGER.warning("removing obsolete token lock for {0}", poStr);
        reloadedDbTokenLock.deleteObject();
      }
    }

    LOGGER.warning("stale token lock removed for {0}, userId {1}, since {2}, expiry {3}",
                   poStr, dbTokenLock.getLockedBy(), dbTokenLock.getLockedSince(), dbTokenLock.getLockExpiry());
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
    createdLocksInFlight.computeIfAbsent(txKey, _ -> new HashMap<>()).put(key, lock);
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
    updatedLocksInFlight.computeIfAbsent(new TransactionKey(dbTokenLock.getSession()), _ -> new HashMap<>())
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
    removedLocksInFlight.computeIfAbsent(txKey, _ -> new HashMap<>()).put(pdoKey, lock);
  }

  /**
   * Rebuilds the internal map if a rebuild was requested and no locks are in flight.
   *
   * @param session the session
   */
  private void rebuildIfFailed(Db session) {
    if (rebuild) {
      // we can only rebuild if there are no locks in flight (i.e., no locking relevant transactions running)
      if (isTxMapEmpty(createdLocksInFlight) &&
          isTxMapEmpty(updatedLocksInFlight) &&
          isTxMapEmpty(removedLocksInFlight)) {

        reloadLocks(session);
        rebuild = false;
      }
    }
  }

  /**
   * Checks if the in-flight map is empty.<br>
   * Removes the entries whose session has been garbage-collected.
   *
   * @param txMap the in-flight map
   * @return true if empty
   */
  private static boolean isTxMapEmpty(Map<TransactionKey, Map<PdoKey, Lock>> txMap) {
    // remove unreferenced transactions
    txMap.keySet().removeIf(txKey -> txKey.sessionRef.get() == null);
    return txMap.isEmpty();
  }

  @SuppressWarnings("rawtypes")
  private static String pdoLockToString(PersistentDomainObject pdo, Lock lock) {
    return "lock for " + pdo.toGenericString() +
           " locked by " + lock.tokenLock.getLockedBy() +
           ", since " + lock.tokenLock.getLockedSince() +
           ", expiry " + lock.tokenLock.getLockExpiry() +
           lock.toContextString() + " (" + pdo.getSession() + ')';
  }

  private static String lockToString(PdoKey pdoKey, Lock lock) {
    return "lock for " + pdoKey + ", " + lock;
  }

}
