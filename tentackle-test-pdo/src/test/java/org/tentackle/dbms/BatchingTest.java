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

import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.misc.TimeKeeper;
import org.tentackle.pdo.testng.AbstractPdoTest;

/**
 * Compares simple inserts with batched inserts.
 */
public class BatchingTest extends AbstractPdoTest {

  private static final int BATCHSIZE = 100;             // rows per batch
  private static final int COUNT = BATCHSIZE * 10;      // number of rows (must be a multiple of BATCHSIZE)

  public BatchingTest() {
    super(TransactionType.METHOD);
  }

  /**
   * Inserts without batching.
   */
  @Test(groups = "local", priority = 1)
  public void testSimpleInserts() {
    Reporter.log("BatchingTest.testSimpleInserts <br>", true);
    runSimpleInserts();
  }

  /**
   * Inserts with batching.
   */
  @Test(groups = "local", priority = 1)
  public void testBatchInserts() {
    Reporter.log("BatchingTest.testBatchInserts <br>", true);
    runBatchInserts();
  }

  /**
   * Test again after DB warmup.
   */
  @Test(groups = "local", priority = 2)
  public void testAgainSimpleInserts() {
    Reporter.log("BatchingTest.testAgainSimpleInserts <br>", true);
    runSimpleInserts();
  }

  /**
   * Test again after DB warmup.
   */
  @Test(groups = "local", priority = 2)
  public void testAgainBatchInserts() {
    Reporter.log("BatchingTest.testAgainBatchInserts <br>", true);
    runBatchInserts();
  }


  private void runSimpleInserts() {
    Db session = (Db) getSession();
    ModificationLog log = new ModificationLog(session, DbModificationType.INSERT);
    TimeKeeper timeKeeper = new TimeKeeper();
    for (int i=0; i < COUNT; i++) {
      new ModificationLog(log, DbModificationType.INSERT).saveObject();
    }
    timeKeeper.end();
    Reporter.log("elapsed time for " + COUNT + " simple inserts: " + timeKeeper.millis() + " ms <br>", true);
  }

  private void runBatchInserts() {
    Db session = (Db) getSession();
    ModificationLog log = new ModificationLog(session, DbModificationType.INSERT);
    log.setSerial(1);
    PreparedStatementWrapper st = log.getPreparedStatement(log.getClassVariables().insertStatementId, log::createInsertSql);
    TimeKeeper timeKeeper = new TimeKeeper();
    int batchCount = 0;
    for (int i=1; i <= COUNT; i++) {
      log.setId(i);
      log.setFields(st);
      st.addBatch();
      if (i % BATCHSIZE == 0) {
        int[] result = st.executeBatch(i >= COUNT);
        batchCount++;
        for (int r : result) {
          if (r != 1) {
            Assert.fail("insert failed");
            break;
          }
        }
      }
    }
    if (batchCount != COUNT / BATCHSIZE) {
      Assert.fail("batch failed");
    }
    timeKeeper.end();
    Reporter.log("elapsed time for " + COUNT + " batch inserts: " + timeKeeper.millis() + " ms <br>", true);
  }

}
