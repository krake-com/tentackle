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

package org.tentackle.pdo;

/**
 * Policy to determine whether and when a transaction is retried.<br>
 * Policies are services annotated with {@link TransactionRetryPolicyService} and selected
 * by name via {@link Transaction#retryPolicy()}.
 *
 * @see DefaultTransactionRetryPolicy
 * @author harald
 */
public interface TransactionRetryPolicy {

  /**
   * Computes the number of milliseconds to wait before retrying the transaction.
   *
   * @param txName the transaction name, never null
   * @param count the number of invocations so far, starting at 1
   * @return the number of milliseconds to wait, 0 to fail
   */
  long waitMillis(String txName, int count);

}
