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

package org.tentackle.dbms.trip;

import org.tentackle.dbms.AbstractDbObject;
import org.tentackle.trip.TripDeduplicatable;
import org.tentackle.trip.TripReferencable;

import java.io.Serial;
import java.io.Serializable;

/**
 * Result of a remote persistence operation.<br>
 * Returns the possibly new object-ID, the changed serial, and the table serial to the client.
 *
 * @param id the object ID
 * @param serial the object's serial
 * @param tableSerial the table serial
 */
@TripReferencable(TripReferencable.NEVER)
@TripDeduplicatable(TripDeduplicatable.NEVER)
public record DbObjectResult(long id, long serial, long tableSerial) implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates a result for the client.
   *
   * @param po the persistence object
   */
  public DbObjectResult(AbstractDbObject<?> po) {
    this(po.getId(), po.getSerial(), po.getTableSerial());
  }

}
