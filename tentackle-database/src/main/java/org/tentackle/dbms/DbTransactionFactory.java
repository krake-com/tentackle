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

package org.tentackle.dbms;

import org.tentackle.common.Constants;
import org.tentackle.common.EncryptedProperties;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.Timestamp;
import org.tentackle.log.Logger;
import org.tentackle.misc.TimeKeeper;

import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;


interface DbTransactionFactoryHolder {
  DbTransactionFactory INSTANCE = ServiceFactory.createService(DbTransactionFactory.class, DbTransactionFactory.class);
}


/**
 * Factory for transactions.<br>
 * Collects duration statistics and monitors transactions.
 */
@Service(DbTransactionFactory.class)  // defaults to self
public class DbTransactionFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static DbTransactionFactory getInstance() {
    return DbTransactionFactoryHolder.INSTANCE;
  }


  private static final Logger LOGGER = Logger.get(DbTransactionFactory.class);

  private static final String TX_IDLE_INTERVAL = "txIdleInterval";
  private static final String TX_IDLE_TIMEOUT = "txIdleTimeout";

  private static final long DEFAULT_TX_IDLE_INTERVAL = Constants.SECOND_MS;     // the default is to check every second
  private static final int DEFAULT_TX_IDLE_TIMEOUT = 60;                        // 1-minute idle transaction timeout


  private final Map<Long, WeakReference<DbTransaction>> transactions;           // txId::transaction
  private final Queue<Long> finishedTransactions;                               // txIds of finished transactions
  private final TransactionStatistics statistics;

  private TransactionSupervisor transactionSupervisor;
  private volatile long txIdleInterval = DEFAULT_TX_IDLE_INTERVAL;
  private volatile int txIdleTimeout = DEFAULT_TX_IDLE_TIMEOUT;
  private volatile boolean configChanged;
  private volatile boolean collectingStatistics;

  // the transaction counter
  private static final AtomicLong TX_COUNT = new AtomicLong();


  /**
   * Creates the transaction factory.
   */
  public DbTransactionFactory() {
    transactions = new ConcurrentHashMap<>();
    finishedTransactions = new ConcurrentLinkedQueue<>();
    statistics = new TransactionStatistics();
    configChanged = true;
  }

  /**
   * Configures the factory from the main session properties.
   *
   * @param properties the properties
   */
  public void configure(EncryptedProperties properties) {
    String val = properties.getPropertyIgnoreCase(TX_IDLE_INTERVAL);
    if (val != null) {
      setTxIdleInterval(Long.parseLong(val));
    }
    val = properties.getPropertyIgnoreCase(TX_IDLE_TIMEOUT);
    if (val != null) {
      setTxIdleTimeout(Integer.parseInt(val));
    }
  }

  /**
   * Creates a transaction.<br>
   * Notice that this method is only invoked for local sessions, never for remote sessions.
   *
   * @param db the session
   * @param txName the transaction name, null if &lt;unnamed&gt;
   * @param fromRemote true if initiated from the remote client
   * @return the transaction
   */
  public DbTransaction create(Db db, String txName, boolean fromRemote) {
    DbTransaction transaction = new DbTransaction(db, createVoucher(fromRemote), txName, TX_COUNT.incrementAndGet());
    transactions.put(transaction.getTxNumber(), new WeakReference<>(transaction));
    ensureSupervisorAlive();
    return transaction;
  }

  /**
   * Finishes a transaction.<br>
   * Invoked when a transaction is committed or rolled back.<br>
   * Notice that this method is only invoked for local sessions, never for remote sessions.
   *
   * @param transaction the transaction
   * @param rolledBack true if transaction was rolled back, false if committed
   */
  public void finish(DbTransaction transaction, boolean rolledBack) {
    transaction.complete();     // not supervised anymore (a commit of a timed out transaction is refused before)
    long txId = transaction.getTxNumber();
    transactions.remove(txId);
    if (collectingStatistics) {
      statistics.countTransaction(transaction, rolledBack);
    }
    finishedTransactions.add(txId);   // hand over to the supervisor thread asynchronously
  }

  /**
   * Sets whether statistics should be collected.
   *
   * @param collectingStatistics true if transactions are counted and execution times measured
   */
  public void setCollectingStatistics(boolean collectingStatistics) {
    this.collectingStatistics = collectingStatistics;
  }

  /**
   * Returns whether statistics are collected.
   *
   * @return true if transactions are counted and execution times measured
   */
  public boolean isCollectingStatistics() {
    return collectingStatistics;
  }

  /**
   * Logs the statistics.
   *
   * @param level the logging level
   * @param clear true if clear statistics after dump
   */
  public void logStatistics(Logger.Level level, boolean clear) {
    statistics.logStatistics(null, level, "    >TXN-Stats: ", clear);
  }

  /**
   * Gets the check interval for idle or unreferenced transactions in ms.
   *
   * @return the check interval
   */
  public long getTxIdleInterval() {
    return txIdleInterval;
  }

  /**
   * Sets the check interval for idle or unreferenced transactions in ms.
   *
   * @param txIdleInterval the check interval
   */
  public void setTxIdleInterval(long txIdleInterval) {
    if (this.txIdleInterval != txIdleInterval) {
      this.txIdleInterval = txIdleInterval;
      configChanged = true;   // after the assignment: the volatile write publishes the new value
    }
  }

  /**
   * Gets the number of check intervals that must elapse for an idle transaction to time out.
   *
   * @return the idle count
   */
  public int getTxIdleTimeout() {
    return txIdleTimeout;
  }

  /**
   * Sets the number of check intervals that must elapse for an idle transaction to time out.
   *
   * @param txIdleTimeout the idle count, &le; 0 to disable
   */
  public void setTxIdleTimeout(int txIdleTimeout) {
    if (this.txIdleTimeout != txIdleTimeout) {
      this.txIdleTimeout = txIdleTimeout;
      configChanged = true;   // after the assignment: the volatile write publishes the new value
    }
  }

  /**
   * Requests supervisor thread termination.<br>
   * If transactions are still running, the supervisor keeps running until they are finished or timed out.
   * The supervisor is started again when the next transaction is created.
   */
  public synchronized void requestTermination() {
    if (transactionSupervisor != null) {
      transactionSupervisor.requestTermination();
      transactionSupervisor.interrupt();    // wake up to re-check promptly
      if (transactions.isEmpty()) {
        transactionSupervisor = null;       // nothing to supervise: the next transaction starts a new supervisor
      }
    }
  }

  /**
   * Returns whether the supervisor thread is running.
   *
   * @return true if running, false if not started yet or terminated
   */
  public synchronized boolean isSupervisorRunning() {
    return transactionSupervisor != null && transactionSupervisor.isAlive();
  }


  /**
   * Creates a new transaction voucher.
   *
   * @param fromRemote true if the transaction was started at the remote client
   * @return the voucher
   */
  protected long createVoucher(boolean fromRemote) {
    // create a new commit/rollback voucher
    long magic;
    do {
      magic = UUID.randomUUID().getLeastSignificantBits();
    }
    while (magic == 0);

    // remote are negative, local positive
    return (fromRemote && magic > 0 || !fromRemote && magic < 0) ? -magic : magic;
  }


  /**
   * Starts the supervisor if not running.<br>
   * A supervisor that died unexpectedly is replaced as well, see {@link #dropSupervisor(TransactionSupervisor)}.
   * The new supervisor takes over all running transactions.
   */
  private synchronized void ensureSupervisorAlive() {
    if (transactionSupervisor == null || !transactionSupervisor.isAlive()) {
      configChanged = true;   // log the configuration when started
      transactionSupervisor = new TransactionSupervisor();
      transactionSupervisor.start();
    }
  }

  /**
   * Determines whether the supervisor should keep running.<br>
   * Synchronized with {@link #ensureSupervisorAlive()}, so that a transaction created concurrently
   * is either picked up by the terminating supervisor or starts a new one.
   *
   * @param supervisor the supervisor thread
   * @return true if keep running, false if terminate
   */
  private synchronized boolean keepSupervising(TransactionSupervisor supervisor) {
    if (transactionSupervisor != supervisor) {
      return false;   // dropped by requestTermination()
    }
    if (supervisor.terminationRequested && transactions.isEmpty()) {
      transactionSupervisor = null;   // the next transaction starts a new supervisor
      return false;
    }
    return true;
  }

  /**
   * Drops the terminating supervisor, unless already replaced.<br>
   * If the supervisor died unexpectedly, e.g., due to an {@link Error}, the next transaction starts a new one.
   * Otherwise, the transactions would not be supervised anymore.
   *
   * @param supervisor the supervisor thread
   */
  private synchronized void dropSupervisor(TransactionSupervisor supervisor) {
    if (transactionSupervisor == supervisor) {
      transactionSupervisor = null;
    }
  }


  /**
   * The supervisor finds unreferenced transactions and cleans up.<br>
   * It also rolls back and closes the JDBC connections of transactions being idle too long.
   * The thread is started when the first transaction (local sessions only!) is created.
   * For remote sessions, the thread will never be started and the {@code DbTransactionFactory} is just dummy,
   * for example, in a remote {@code ServerApplication}.
   */
  private class TransactionSupervisor extends Thread {

    private static class TxIdleCounter {
      final String txName;
      final long txNumber;
      final long creationTime;
      final TimeKeeper timeKeeper;
      int idleCount;

      TxIdleCounter(DbTransaction transaction) {
        // we must not keep a reference to the transaction itself!
        txName = transaction.getTxName();
        txNumber = transaction.getTxNumber();
        creationTime = transaction.getCreationTime();
        timeKeeper = transaction.getTimeKeeper();
      }

      int count() {
        return ++idleCount;
      }

      @Override
      public String toString() {
        return txName + ":" + txNumber + ", started " + new Timestamp(creationTime) + " (" + timeKeeper.end().millisToString() + " ms)";
      }
    }

    private final Map<Long, TxIdleCounter> idleMap = new HashMap<>();    // idle transactions suspected to time out
    private volatile boolean terminationRequested;                         // true if this thread should stop

    private TransactionSupervisor() {
      super("Transaction Supervisor");
      setDaemon(true);
    }

    /**
     * Requests this thread to terminate.<br>
     * The thread terminates as soon as no transactions are running anymore.
     * The flag belongs to the thread, not to the factory, so that a supervisor started
     * after the termination of a previous one is not stopped right away.
     */
    private void requestTermination() {
      terminationRequested = true;
    }

    @Override
    public void run() {
      try {
        supervise();
      }
      catch (Throwable t) {
        LOGGER.severe(getName() + " died -> restarted by the next transaction", t);
        throw t;
      }
      finally {
        dropSupervisor(this);
      }
      LOGGER.info("{0} terminated", getName());
    }

    /**
     * Supervises the transactions until terminated.
     */
    private void supervise() {
      while (keepSupervising(this)) {
        if (configChanged) {
          LOGGER.info("{0} running with interval={1} ms, maxIdleCount={2}", getName(), txIdleInterval, txIdleTimeout);
          configChanged = false;
        }
        try {
          sleep(txIdleInterval);

          // remove finished transactions
          Long finishedTxId;
          while ((finishedTxId = finishedTransactions.poll()) != null) {
            idleMap.remove(finishedTxId);
          }

          // check running transaction for idle timeout
          for (Iterator<Map.Entry<Long, WeakReference<DbTransaction>>> iter = transactions.entrySet().iterator(); iter.hasNext(); ) {
            Map.Entry<Long, WeakReference<DbTransaction>> entry = iter.next();
            DbTransaction transaction = entry.getValue().get();
            if (transaction == null) {
              // transactions cannot become unreferenced since the session is always attached when the tx starts. Attached sessions
              // are referenced by the ManagedConnection until the tx ends, which will remove the tx from the transactions-map above.
              // If we encounter an unreferenced tx, it must have been detached within the transaction or some other weird condition.
              Long txId = entry.getKey();
              TxIdleCounter idleCounter = idleMap.remove(txId);
              LOGGER.severe("unreferenced transaction {0} detected", idleCounter == null ? txId : idleCounter);
              iter.remove();
            }
            else {
              if (transaction.pollAlive()) {   // reset: will be set alive as soon as some I/O happens again
                idleMap.remove(transaction.getTxNumber());
              }
              else {
                if (txIdleTimeout > 0 &&
                    idleMap.computeIfAbsent(transaction.getTxNumber(), _ -> new TxIdleCounter(transaction)).count() > txIdleTimeout) {
                  String msg = "idle transaction " + transaction.getSession() + " timed out after " +
                                txIdleTimeout * txIdleInterval / Constants.SECOND_MS + " s";
                  idleMap.remove(transaction.getTxNumber());
                  iter.remove();
                  Db session = transaction.getSession();
                  ManagedConnection mc;
                  Connection con = null;
                  // strictly serialized with attach and detach, which the connection managers synchronize on themselves
                  synchronized (session.getConnectionManager()) {
                    if (!transaction.timeOut()) {
                      continue;   // completed by the session in the meantime
                    }
                    mc = session.getConnection();
                    if (mc != null) {
                      mc.setDead(true);
                      if (!mc.isClosed()) {
                        con = mc.getConnection();
                      }
                    }
                  }
                  if (mc != null) {
                    LOGGER.warning(msg + ":\n" + mc.toDiagnosticString());  // with statements!
                    if (con != null) {
                      // do that in a separate thread (if it gets stuck, the supervisor still keeps running!)
                      createTxCleanupThread(mc, con).start();
                    }
                    else {
                      LOGGER.warning("managed connection {0} already closed", mc);
                    }
                  }
                  else {
                    LOGGER.warning(msg + " (managed connection already detached from session {0})", session);
                  }
                }
              }
            }
          }
        }
        catch (InterruptedException ix) {
          // daemon thread is terminated via requestTermination() as soon as no transactions are running
        }
        catch (RuntimeException ex) {
          LOGGER.severe("cleanup transactions failed", ex);
        }
      }
    }

    private static Thread createTxCleanupThread(ManagedConnection mc, Connection con) {
      Thread txCleanupThread = new Thread("transaction cleanup " + mc) {
        @Override
        public void run() {
          try {
            LOGGER.warning("performing low-level rollback and close of {0}", con);
            // really low level (as if the database closed it suddenly)
            try {
              con.rollback();
            }
            catch (SQLException sr) {
              LOGGER.warning("low-level rollback failed", sr);
            }
            con.close();   // just close -> leave the cleanup to the thread stuck in transaction
          }
          catch (SQLException | RuntimeException ex) {
            LOGGER.warning("closing JDBC connection failed", ex);
          }
        }
      };
      txCleanupThread.setDaemon(true);
      return txCleanupThread;
    }
  }

}
