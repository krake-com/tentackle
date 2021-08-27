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

import java.util.Random;

/**
 * The default transaction retry policy.
 * <p>
 * Retries 7 times after random values around 0.5, 1.0, 2.0, 4.0, 8.0, 16.0 and 32.0 seconds.<br>
 * Should be enough in most cases.
 * <p>
 * Application can easily replace this default policy by providing their own implementation
 * annotated with {@code @TransactionRetryPolicyService("")}.
 */
@TransactionRetryPolicyService("")
public class DefaultTransactionRetryPolicy implements TransactionRetryPolicy {

  private static final Random rand = new Random();
  private static final long OFFSET_WAIT_MS = 250;   // random value between 250 ...
  private static final int RANGE_WAIT_MS = 500;     // ... and 750
  private static final int MAX_RETRIES = 7;

  @Override
  public long waitMillis(String txName, int count) {
    return count > MAX_RETRIES ? 0 : (OFFSET_WAIT_MS + rand.nextInt(RANGE_WAIT_MS)) * (1 << (count - 1));    // double time each invocation
  }

}
