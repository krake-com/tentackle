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

package org.tentackle.session;

/**
 * Enum that maps the low-level integer constants of the transaction isolation levels in <code>java.sql.Connection</code>.
 */
public enum TransactionIsolation {

  // we don't refer to the constants because we don't want a module requirement for java.sql in the session module!

  /**
   * Default transaction isolation.<br>
   * Usually READ_COMMITTED.
   */
  DEFAULT(-1),

  /**
   * Transactions are not supported at all.
   */
  NONE(0),

  /**
   * Dirty reads, non-repeatable reads, and phantom reads can occur.
   */
  READ_UNCOMMITTED(1),

  /**
   * Dirty reads are prevented, non-repeatable reads and phantom reads can occur.
   * <p>
   * This is usually the default for most databases.
   */
  READ_COMMITTED(2),

  /**
   * Dirty reads and non-repeatable reads are prevented, phantom reads can occur.
   * <p>
   * Applications running this isolation level should be prepared for temporary serialization errors
   * and retry such transactions.
   */
  REPEATABLE_READ(4),

  /**
   * Dirty reads, non-repeatable reads, and phantom reads are prevented.
   * <p>
   * Applications running this isolation level should be prepared for temporary serialization errors
   * and retry such transactions.
   */
  SERIALIZABLE(8);



  private final int level;    // isolation level (corresponds to Connection#TRANSACTION_...)

  TransactionIsolation(int level) {
    this.level = level;
  }

  /**
   * Gets the low-level isolation level constant.
   *
   * @return the low-level integer constant
   * @see java.sql.Connection
   */
  public int getLevel() {
    return level;
  }


  /**
   * Derives the transaction isolation enum from the low-level constant integer.
   *
   * @param level the low-level constant
   * @return the transaction isolation, {@link #DEFAULT} if no such level
   */
  public static TransactionIsolation valueOf(int level) {
    for (TransactionIsolation transactionIsolation : TransactionIsolation.values()) {
      if (transactionIsolation.level == level) {
        return transactionIsolation;
      }
    }
    return TransactionIsolation.DEFAULT;
  }

}
