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

import org.tentackle.session.PersistenceException;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Base class for a transaction.
 */
public abstract class AbstractTransaction implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  private static final String UNNAMED_TRANSACTION = "_";

  private final long txVoucher;                   // the random voucher for commit or rollback
  private final String txName;                    // the transaction name
  private final long txNumber;                    // the unique transaction number
  private int txLevel;                            // the transaction level

  private transient Db session;                   // the session
  private transient Deque<String> nestedTxNames;  // nested transaction names

  /**
   * Creates a transaction object.
   *
   * @param txVoucher the transaction voucher
   * @param txName the transaction name, null if default unnamed
   * @param txNumber the transaction number
   * @param txLevel the initial nesting level (usually 1)
   */
  public AbstractTransaction(long txVoucher, String txName, long txNumber, int txLevel) {
    this.txVoucher = txVoucher;
    this.txName = txName == null ? UNNAMED_TRANSACTION : txName;
    this.txNumber = txNumber;
    this.txLevel = txLevel;
  }

  /**
   * Gets the session.
   *
   * @return the session
   */
  public Db getSession() {
    return session;
  }

  /**
   * Sets the session.
   *
   * @param session the session to set
   */
  public void setSession(Db session) {
    this.session = session;
  }

  /**
   * Gets the transaction voucher.
   *
   * @return the transaction voucher
   */
  public long getTxVoucher() {
    return txVoucher;
  }

  /**
   * Gets the transaction name.
   *
   * @return the transaction name, never null
   */
  public String getTxName() {
    return txName;
  }

  /**
   * Gets the transaction number.
   *
   * @return the transaction number
   */
  public long getTxNumber() {
    return txNumber;
  }

  /**
   * Gets the transaction nesting level.
   *
   * @return the nesting level
   */
  public int getTxLevel() {
    return txLevel;
  }

  /**
   * Increments the transaction level.<br>
   * Does nothing if the level is invalid.
   *
   * @param txName the name of the nested transaction, null if unnamed
   */
  public void incrementTxLevel(String txName) {
    if (isTxLevelValid()) {
      if (txName == null) {
        txName = UNNAMED_TRANSACTION;
      }
      if (nestedTxNames == null) {
        nestedTxNames = new ArrayDeque<>();
      }
      nestedTxNames.push(txName);
      txLevel++;
    }
  }

  /**
   * Decrements the transaction level.<br>
   * Does nothing if the level is invalid.
   *
   * @throws PersistenceException if the level would drop below 1
   */
  public void decrementTxLevel() {
    if (isTxLevelValid()) {
      if (txLevel <= 1) {
        throw new PersistenceException(session, "unbalanced tx level " + txLevel);
      }
      if (nestedTxNames != null) {
        nestedTxNames.pop();
      }
      txLevel--;
    }
  }

  /**
   * Marks the txLevel invalid.
   * <p>
   * Will suppress any checks and warnings.
   */
  public void invalidateTxLevel() {
    txLevel = Integer.MIN_VALUE;
    nestedTxNames = null;
  }

  /**
   * Returns whether the txLevel is valid.
   *
   * @return true if valid
   */
  public boolean isTxLevelValid() {
    return txLevel != Integer.MIN_VALUE;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append("TX").append(txNumber);
    if (isTxLevelValid()) {
      buf.append('.').append(txLevel);
    }
    buf.append("-[");
    if (txVoucher < 0) {
      buf.append("< ");  // from remote
    }
    buf.append(txName);
    if (nestedTxNames != null) {
      for (Iterator<String> iterator = nestedTxNames.descendingIterator(); iterator.hasNext(); ) {
        buf.append(" > ").append(iterator.next());
      }
    }
    buf.append(']');
    return buf.toString();
  }

}
