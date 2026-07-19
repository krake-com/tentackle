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

import org.tentackle.trip.OpCode;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;

import java.util.EnumSet;
import java.util.Iterator;


interface EnumSetTypeHolder {
  TripType<Integer> INTEGER_TRIP_TYPE = TripFactory.getInstance().getType(Integer.TYPE);
  @SuppressWarnings("rawtypes")
  TripType<Class> CLASS_TRIP_TYPE = TripFactory.getInstance().getType(Class.class);
}


/**
 * Trip type for {@link EnumSet}s.
 * <p>
 * EnumSets provide no usable constructors. The element type is serialized
 * in the header instead, and the set is created via {@link EnumSet#noneOf}.
 *
 * @param <T> the enum set type
 */
// created by CollectionTypeBuilder, not mapped by @TripTypeService
@SuppressWarnings("rawtypes")
public class EnumSetTripType<T extends EnumSet> extends CollectionTripType<T> {

  /**
   * Creates the type.
   *
   * @param clazz the enum set's class
   */
  public EnumSetTripType(Class<T> clazz) {
    super(clazz);
  }

  @Override
  public boolean isInstantiable() {
    return false;   // no sized or no-args constructor: created via EnumSet.noneOf instead
  }

  @Override
  protected void writeHeader(T value, TripStream stream) {
    super.writeHeader(value, stream);
    EnumSetTypeHolder.CLASS_TRIP_TYPE.write(elementType(value), stream);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected CollectionHeader<T> readHeader(TripStream stream, OpCode opCode) {
    int size = EnumSetTypeHolder.INTEGER_TRIP_TYPE.read(stream, opCode);
    Class elementType = EnumSetTypeHolder.CLASS_TRIP_TYPE.read(stream, stream.receiveOpCode());
    return new CollectionHeader<>(size, (T) EnumSet.noneOf(elementType));
  }


  /**
   * Determines the element type of the given enum set.
   *
   * @param value the enum set
   * @return the enum class
   */
  @SuppressWarnings("unchecked")
  private Class<?> elementType(T value) {
    Iterator<?> iterator = value.iterator();
    if (!iterator.hasNext()) {
      // empty set: derive the element type from its complement
      iterator = EnumSet.complementOf(value).iterator();
    }
    if (iterator.hasNext()) {
      return ((Enum<?>) iterator.next()).getDeclaringClass();
    }
    throw new TripRuntimeException("cannot determine the element type of an empty enum set of a constant-less enum");
  }

}
