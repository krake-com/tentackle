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

import java.util.Objects;

/**
 * Enum for read-write or read-only transactions.
 */
public enum TransactionWritability {

  /**
   * Default transaction writability.<br>
   * Usually READ_WRITE.
   */
  DEFAULT(null),

  /**
   * Read-write transaction.
   */
  READ_WRITE(true),

  /**
   * Read-only transaction.
   */
  READ_ONLY(false);



  private final Boolean writable;    // boolean internal value

  TransactionWritability(Boolean writable) {
    this.writable = writable;
  }

  /**
   * Gets the low-level writable boolean value.
   *
   * @return the writable boolean, null if default
   */
  public Boolean getWritable() {
    return writable;
  }


  /**
   * Derives the transaction writability enum from the low-level boolean.
   *
   * @param writable the low-level constant
   * @return the transaction writability, {@link #DEFAULT} if null
   */
  public static TransactionWritability valueOf(Boolean writable) {
    for (TransactionWritability transactionWritability : TransactionWritability.values()) {
      if (Objects.equals(transactionWritability.writable, writable)) {
        return transactionWritability;
      }
    }
    return TransactionWritability.DEFAULT;
  }

}
