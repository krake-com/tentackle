/*
 * Tentackle - http://www.tentackle.org
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

import org.tentackle.pdo.DefaultSessionInfo;
import org.tentackle.pdo.SessionInfo;
import org.tentackle.pdo.TransactionEnvelope;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 *
 * @author harald
 */
public class TransactionEnvelopeTest {

  @Test
  public void testEnvelope() {
    SessionInfo ui = new DefaultSessionInfo(null, null, "TestDb");
    Db db = new Db(new DefaultConnectionManager(), ui);
    db.open();

    int rv = db.transaction(() -> {
      assertEquals(db.getTxName(), "TransactionEnvelopeTest");
      return 5;
    });
    assertEquals(rv, 5);

    // same without lambda
    rv = db.transaction(new TransactionEnvelope<Integer>() {
      @Override
      public Integer run() {
        assertEquals(db.getTxName(), "TransactionEnvelopeTest#testEnvelope");
        return 10;
      }
    });
    assertEquals(rv, 10);



    db.close();
  }

}
