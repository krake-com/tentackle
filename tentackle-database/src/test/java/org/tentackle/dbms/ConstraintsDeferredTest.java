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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import org.tentackle.session.PersistenceException;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;

/**
 * Tests {@link Db#setConstraintsDeferred(boolean)} / {@link Db#isConstraintsDeferred()}.<br>
 * H2 (the default test backend) does not support deferred constraints, so only the
 * assertion/graceful-failure paths are exercised here; the actual deferred-checking behavior
 * is verified against Postgres.
 *
 * @author harald
 */
@SuppressWarnings("exports")
public class ConstraintsDeferredTest extends AbstractDbTest {

  public ConstraintsDeferredTest() {
    super(TransactionType.NONE);    // the transactions are managed by the test methods
  }

  @AfterMethod
  public void rollbackPendingTransaction() {
    Db db = getSession();
    while (db.isTxRunning()) {
      db.rollbackImmediately(null);
    }
  }

  @Test
  public void testDefaultIsNotDeferred() {
    assertFalse(getSession().isConstraintsDeferred());
  }

  @Test
  public void testThrowsIfNoTransactionRunning() {
    Db db = getSession();
    assertThrows(PersistenceException.class, () -> db.setConstraintsDeferred(true));
  }

  @Test
  public void testThrowsIfBackendUnsupported() {
    Db db = getSession();
    long voucher = db.begin("deferredTest");
    try {
      // H2 does not support isSetConstraintsDeferredSupported()
      assertThrows(PersistenceException.class, () -> db.setConstraintsDeferred(true));
    }
    finally {
      db.rollback(voucher);
    }
  }

}
