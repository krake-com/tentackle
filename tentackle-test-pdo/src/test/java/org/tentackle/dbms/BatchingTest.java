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

import org.tentackle.misc.Duration;
import org.tentackle.test.pdo.AbstractPdoTest;

public class BatchingTest extends AbstractPdoTest {

  private static final int BATCHSIZE = 100;             // rows per batch
  private static final int COUNT = BATCHSIZE * 10;      // number of rows (must be a multiple of BATCHSIZE)

  public BatchingTest() {
    super(TransactionType.METHOD);
  }

  /**
   * Inserts without batching.
   */
  @Test(groups = "local", priority = 0)
  public void testInserts() {
    Db session = (Db) getSession();
    ModificationLog log = new ModificationLog(session, ModificationLog.INSERT);
    Duration duration = new Duration();
    for (int i=0; i < COUNT; i++) {
      new ModificationLog(log, ModificationLog.INSERT).saveObject();
    }
    duration.end();
    Reporter.log("elapsed time for " + COUNT + " simple inserts: " + duration.millis() + " ms <br>", true);
  }

  /**
   * Inserts with batching.
   */
  @Test(groups = "local", priority = 1)
  public void testBatchInserts() {
    Db session = (Db) getSession();
    ModificationLog log = new ModificationLog(session, ModificationLog.INSERT);
    log.setSerial(1);
    PreparedStatementWrapper st = log.getPreparedStatement(log.getClassVariables().insertStatementId, () -> log.createInsertSql());
    Duration duration = new Duration();
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
    duration.end();
    Reporter.log("elapsed time for " + COUNT + " batch inserts: " + duration.millis() + " ms <br>", true);
  }

  /**
   * Test again after DB warmup.
   */
  @Test(groups = "local", priority = 2)
  public void testAgainBoth() {
    testBatchInserts();
    testInserts();
  }

}
