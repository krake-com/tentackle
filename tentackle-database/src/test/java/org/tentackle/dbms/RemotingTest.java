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

import org.tentackle.trip.TripFactory;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;

/**
 * Tests serialization/deserialization of remote objects.
 */
@SuppressWarnings("missing-explicit-ctor")
public class RemotingTest {

  @Test
  public void testRemoteTransaction() {
    RemoteTransaction tx = new RemoteTransaction(-1234, "Test", 100, 1);
    RemoteTransaction copy = TripFactory.getInstance().copy(tx);
    assertNotSame(copy, tx);
    assertEquals(copy.getTxVoucher(), tx.getTxVoucher());
    assertEquals(copy.getTxName(), tx.getTxName());
    assertEquals(copy.getTxNumber(), tx.getTxNumber());
    assertEquals(copy.getTxLevel(), tx.getTxLevel());
  }

}
