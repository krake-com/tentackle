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
package org.tentackle.persist;

import org.tentackle.pdo.TransactionRetryPolicy;
import org.tentackle.pdo.TransactionRetryPolicyService;

/**
 * A fast retry policy for the tests.<br>
 * Retries 3 times after 1 ms each.
 */
@TransactionRetryPolicyService("test")
public class TestTransactionRetryPolicy implements TransactionRetryPolicy {

  /** maximum number of retries. */
  public static final int MAX_RETRIES = 3;

  /**
   * Creates the retry policy.
   */
  public TestTransactionRetryPolicy() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public long waitMillis(String txName, int count) {
    return count > MAX_RETRIES ? 0 : 1;
  }

}
