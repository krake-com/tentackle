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

import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

@SuppressWarnings("missing-explicit-ctor")
public class TransactionRetryPolicyTest {

  @Test
  public void testPolicy() {

    TransactionRetryPolicy policy = new DefaultTransactionRetryPolicy();
    for (int count = 1; count <= 8; count++) {
      long wait = policy.waitMillis("test", count);
      Reporter.log("count=" + count + " -> " + wait + " ms<BR>", true);
      if (count >= 8) {
        Assert.assertEquals(wait, 0);
      }
    }

  }

  /**
   * The wait must stay within the documented random window
   * [250 .. 750) ms, doubled on every retry, and drop to 0 after 7 retries.
   */
  @Test
  public void testExponentialBackoffBounds() {
    TransactionRetryPolicy policy = new DefaultTransactionRetryPolicy();
    // repeat to account for the randomized component
    for (int iteration = 0; iteration < 1000; iteration++) {
      for (int count = 1; count <= 7; count++) {
        long multiplier = 1L << (count - 1);
        long wait = policy.waitMillis("test", count);
        Assert.assertTrue(wait >= 250 * multiplier,
                          "count=" + count + " wait=" + wait + " below lower bound");
        Assert.assertTrue(wait < 750 * multiplier,
                          "count=" + count + " wait=" + wait + " above upper bound");
      }
    }
  }

  @Test
  public void testNoWaitAfterMaxRetries() {
    TransactionRetryPolicy policy = new DefaultTransactionRetryPolicy();
    for (int count = 8; count <= 20; count++) {
      Assert.assertEquals(policy.waitMillis("test", count), 0, "no retry beyond the maximum");
    }
  }

}
