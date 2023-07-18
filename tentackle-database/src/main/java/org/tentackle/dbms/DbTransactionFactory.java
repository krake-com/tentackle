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
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.Timestamp;
import org.tentackle.log.Logger;
import org.tentackle.misc.TimeKeeper;
import org.tentackle.session.PersistenceException;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

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


  private static final Logger LOG = Logger.get(DbTransactionFactory.class);

  private static final String TX_IDLE_INTERVAL = "txidleinterval";
  private static final String TX_IDLE_TIMEOUT = "txidletimeout";

  private static final long DEFAULT_TX_IDLE_INTERVAL = Constants.SECOND_MS;     // default is to check every second
  private static final int DEFAULT_TX_IDLE_TIMEOUT = 60;                        // 1 minute idle transaction timeout


  private final Map<Long, WeakReference<DbTransaction>> transactions;           // txId::transaction
  private final TransactionStatistics statistics;

  private TransactionSupervisor transactionSupervisor;
  private long txIdleInterval = DEFAULT_TX_IDLE_INTERVAL;
  private int txIdleTimeout = DEFAULT_TX_IDLE_TIMEOUT;
  private volatile boolean configChanged;
  private boolean collectingStatistics;
  private volatile boolean terminationRequested;


  /**
   * Creates the transaction factory.
   */
  public DbTransactionFactory() {
    transactions = new ConcurrentHashMap<>();
    statistics = new TransactionStatistics();
    configChanged = true;
  }

  /**
   * Configures the factory from the main session properties.
   *
   * @param properties the properties
   */
  public void configure(Properties properties) {
    String val = properties.getProperty(TX_IDLE_INTERVAL);
    if (val != null) {
      setTxIdleInterval(Long.parseLong(val));
    }
    val = properties.getProperty(TX_IDLE_TIMEOUT);
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
   * @param fromRemote true if initiated from remote client
   */
  public DbTransaction create(Db db, String txName, boolean fromRemote) {
    DbTransaction transaction = new DbTransaction(db, txName, fromRemote);
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
   */
  public void finish(DbTransaction transaction) {
    transactions.remove(transaction.getTxNumber());
    if (collectingStatistics) {
      statistics.countTransaction(transaction);
    }
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
    statistics.logStatistics(toString(), level, "    >TXN-Stats: ", clear);
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
    configChanged = this.txIdleInterval != txIdleInterval;
    this.txIdleInterval = txIdleInterval;
  }

  /**
   * Gets the number of check intervals that must elapse to for an idle transaction to time out.
   *
   * @return the idle count
   */
  public int getTxIdleTimeout() {
    return txIdleTimeout;
  }

  /**
   * Sets the number of check intervals that must elapse to for an idle transaction to time out.
   *
   * @param txIdleTimeout the idle count, &le; 0 to disable
   */
  public void setTxIdleTimeout(int txIdleTimeout) {
    configChanged = this.txIdleTimeout != txIdleTimeout;
    this.txIdleTimeout = txIdleTimeout;
  }

  /**
   * Requests supervisor thread termination.
   */
  public void requestTermination() {
    if (transactionSupervisor != null) {
      terminationRequested = true;
      transactionSupervisor.interrupt();
    }
  }


  /**
   * The supervisor finds unreferenced transactions and cleans up.<br>
   * It also closes sessions with transactions being idle too long.
   * The thread is started when the first transaction (local sessions only!) is created.
   * For remote sessions, the thread will never be started and the {@code DbTransactionFactory} is just dummy,
   * for example in a remote {@code ServerApplication}.
   */
  private class TransactionSupervisor extends Thread {

    private class TxIdleCounter {
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

    private TransactionSupervisor() {
      super("Transaction Supervisor");
      setDaemon(true);
    }

    @Override
    public void run() {
      while (!terminationRequested) {
        if (configChanged) {
          LOG.info("{0} running with interval={1} ms, maxIdleCount={2}", getName(), txIdleInterval, txIdleTimeout);
          configChanged = false;
        }
        try {
          sleep(txIdleInterval);
          for (Iterator<Map.Entry<Long, WeakReference<DbTransaction>>> iter = transactions.entrySet().iterator(); iter.hasNext(); ) {
            Map.Entry<Long, WeakReference<DbTransaction>> entry = iter.next();
            DbTransaction transaction = entry.getValue().get();
            if (transaction == null) {
              // transactions cannot become unreferenced since the session is always attached when the tx starts. Attached sessions
              // are referenced by the ManagedConnection until the tx ends, which will remove the tx from the transactions map above.
              // If we encounter an unreferenced tx, it must have been detached within the transaction or some other weird condition.
              Long txId = entry.getKey();
              TxIdleCounter idleCounter = idleMap.remove(txId);
              LOG.severe("unreferenced transaction {0} detected", idleCounter == null ? txId : idleCounter);
              iter.remove();
            }
            else {
              if (transaction.isAlive()) {
                transaction.setAlive(false);  // polled (will be setAlive as soon as some I/O happens again)
                idleMap.remove(transaction.getTxNumber());
              }
              else {
                if (txIdleTimeout > 0 &&
                    idleMap.computeIfAbsent(transaction.getTxNumber(), txNumber -> new TxIdleCounter(transaction)).count() > txIdleTimeout) {
                  String msg = "idle transaction " + transaction + " timed out after " +
                                txIdleTimeout * txIdleInterval / Constants.SECOND_MS + " s -> aborted and session closed!";
                  LOG.warning(msg);
                  idleMap.remove(transaction.getTxNumber());
                  iter.remove();
                  try {
                    Db session = transaction.getSession();
                    try {
                      session.rollbackImmediately(new PersistenceException(transaction.getSession(), msg));
                    }
                    catch (RuntimeException rb) {
                      LOG.warning("rollback failed", rb);
                    }
                    session.close();    // session must not be used anymore!
                    if (session.isPooled()) {
                      session.getPool().putSession(session);    // remove closed session from pool immediately (don't wait for GC cleanup)
                    }
                  }
                  catch (RuntimeException rc) {
                    LOG.warning("closing session failed", rc);
                  }
                }
              }
            }
          }
        }
        catch (InterruptedException ix) {
          // daemon thread is terminated via requestTermination()
        }
        catch (RuntimeException ex) {
          LOG.severe("cleanup transactions failed", ex);
        }
      }

      LOG.info("{0} terminated", getName());
    }
  }

  private synchronized void ensureSupervisorAlive() {
    if (transactionSupervisor == null) {
      transactionSupervisor = new TransactionSupervisor();
      transactionSupervisor.start();
    }
  }

}
