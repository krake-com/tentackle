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

import org.tentackle.session.MasterSerial;
import org.tentackle.trip.TripDeduplicatable;
import org.tentackle.trip.TripReferencable;

/**
 * The default master serial.
 *
 * @param serial the master serial
 * @param generation the generation of the modification table the server's tracker is working on,
 *                   see {@link org.tentackle.dbms.DbModification#initializeModificationTable(org.tentackle.dbms.Db)}
 */
@TripDeduplicatable(TripDeduplicatable.NEVER)
@TripReferencable(TripReferencable.NEVER)
public record DefaultMasterSerial(long serial, long generation) implements MasterSerial {

}
