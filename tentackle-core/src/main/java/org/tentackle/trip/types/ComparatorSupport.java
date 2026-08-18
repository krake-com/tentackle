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

package org.tentackle.trip.types;

import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;

import java.util.Comparator;


interface ComparatorSupportHolder {
  TripType<Boolean> BOOLEAN_TRIP_TYPE = TripFactory.getInstance().getType(Boolean.TYPE);
}


/**
 * Serializes the optional {@link Comparator} of sorted collections and maps.
 * <p>
 * The comparator is written as part of the collection's header, right after the size.
 * It is preceded by a boolean telling whether a comparator follows at all
 * (a {@code null} comparator means natural ordering).
 */
@SuppressWarnings("rawtypes")
final class ComparatorSupport {

  /**
   * Writes the optional comparator.
   *
   * @param comparator the comparator, null if natural ordering
   * @param stream the stream to write to
   */
  static void write(Comparator comparator, TripStream stream) {
    if (comparator == null) {
      ComparatorSupportHolder.BOOLEAN_TRIP_TYPE.write(false, stream);
    }
    else {
      ComparatorSupportHolder.BOOLEAN_TRIP_TYPE.write(true, stream);
      TripType<Comparator> type = stream.getType(comparator.getClass());
      type.write(comparator, stream);
    }
  }

  /**
   * Reads the optional comparator.
   *
   * @param stream the stream to read from
   * @return the comparator, null if natural ordering
   */
  @SuppressWarnings("unchecked")
  static Comparator read(TripStream stream) {
    if (ComparatorSupportHolder.BOOLEAN_TRIP_TYPE.read(stream, stream.receiveOpCode())) {
      // get the type from the stream (either new dictionary entry or ordinal)
      stream.receiveOpCode();   // skip OBJECT
      TripType<Comparator> type = (TripType<Comparator>) stream.receiveDictionary().getType();
      // the next opcode is a reference or the handler data
      return type.read(stream, stream.receiveOpCode());
    }
    return null;
  }

  private ComparatorSupport() {
    // no instances
  }

}
