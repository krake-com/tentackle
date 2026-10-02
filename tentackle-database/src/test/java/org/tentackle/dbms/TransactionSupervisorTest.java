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

import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the lifecycle of the transaction supervisor of the {@link DbTransactionFactory}.
 */
public class TransactionSupervisorTest extends AbstractDbTest {

  public TransactionSupervisorTest() {
    super(TransactionType.NONE);
  }


  @Test
  public void testRestartAfterTermination() throws InterruptedException {
    DbTransactionFactory factory = new DbTransactionFactory();    // not the singleton used by the other tests
    factory.setTxIdleInterval(50);
    Db db = getSession();
    try {
      assertFalse(factory.isSupervisorRunning(), "supervisor must not run before the first transaction");

      factory.finish(factory.create(db, "first", false), false);
      assertTrue(factory.isSupervisorRunning(), "first transaction must start the supervisor");

      factory.requestTermination();
      assertFalse(factory.isSupervisorRunning(), "supervisor must be terminated");

      factory.finish(factory.create(db, "second", false), false);
      Thread.sleep(500);    // several supervisor intervals
      assertTrue(factory.isSupervisorRunning(), "next transaction must restart the supervisor");
    }
    finally {
      factory.requestTermination();
    }
  }

}
