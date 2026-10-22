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

import org.testng.annotations.Test;

import org.tentackle.pdo.Pdo;
import org.tentackle.session.PersistenceException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests {@code @Transaction(retry = true)}.
 */
public class TransactionRetryDbTest extends AbstractPersistenceDbTest {

  /**
   * Creates the test.<br>
   * No test-managed transaction: the annotated method must start its own to be retryable.
   */
  public TransactionRetryDbTest() {
    super(TransactionType.NONE);
  }


  private DemoOperation createOperation() {
    return Pdo.createOperation(DemoOperation.class, getDomainContext());
  }

  private int getAttempts(DemoOperation operation) {
    return ((DemoOperationPersistenceImpl) operation.getPersistenceDelegate()).getAttempts();
  }


  @Test
  public void testRetriedUntilSuccess() {
    DemoOperation operation = createOperation();
    assertEquals(operation.executeWithRetry(2, true), 3);
    assertFalse(getSession().isTxRunning());
  }

  @Test
  public void testNonTemporaryNotRetried() {
    DemoOperation operation = createOperation();
    PersistenceException ex = expectThrows(PersistenceException.class, () -> operation.executeWithRetry(2, false));
    assertFalse(ex.isTemporary());
    assertEquals(getAttempts(operation), 1);
    assertFalse(getSession().isTxRunning());
  }

  @Test
  public void testRetriesExhausted() {
    DemoOperation operation = createOperation();
    PersistenceException ex = expectThrows(PersistenceException.class, () -> operation.executeWithRetry(10, true));
    assertTrue(ex.isTemporary());
    assertEquals(getAttempts(operation), TestTransactionRetryPolicy.MAX_RETRIES + 1);
    assertFalse(getSession().isTxRunning());
  }

  @Test
  public void testNestedTransactionNotRetried() {
    DemoOperation operation = createOperation();
    long txVoucher = getSession().begin("outer");
    try {
      expectThrows(PersistenceException.class, () -> operation.executeWithRetry(2, true));
      assertEquals(getAttempts(operation), 1, "nested transactions are retried by the outermost one only");
    }
    finally {
      getSession().rollbackSilently(txVoucher);
    }
    assertFalse(getSession().isTxRunning());
  }

}
