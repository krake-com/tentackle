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

package org.tentackle.trip.transport;

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.misc.AbstractPoolSlot;
import org.tentackle.misc.Pool;
import org.tentackle.trip.Connection;
import org.tentackle.trip.TripRuntimeException;

/**
 * A pool slot holding a TRIP client {@link Connection}.<br>
 * An unused connection that has been closed is obsolete and will be removed from the pool.
 */
public class ConnectionPoolSlot extends AbstractPoolSlot<Connection> {

  /**
   * Creates a connection slot.
   *
   * @param pool       the pool
   * @param slotNumber the slot number within the pool (== pool-ID - 1)
   * @param connection the managed connection
   */
  public ConnectionPoolSlot(Pool<Connection> pool, int slotNumber, Connection connection) {
    super(pool, slotNumber, connection);
  }

  @Override
  protected TentackleRuntimeException createException(String msg) {
    return new TripRuntimeException(msg);
  }

  @Override
  public boolean isObsolete() {
    Connection connection = getPoolable();
    return connection != null && !connection.isOpen();
  }

}
