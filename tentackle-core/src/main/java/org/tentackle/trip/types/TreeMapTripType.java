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
import org.tentackle.trip.TripInstantiationException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;
import java.util.TreeMap;


interface TreeMapTypeHolder {
  TripType<Integer> INTEGER_TRIP_TYPE = TripFactory.getInstance().getType(Integer.TYPE);
  TripType<Boolean> BOOLEAN_TRIP_TYPE = TripFactory.getInstance().getType(Boolean.TYPE);
}


/**
 * Trip type for {@link TreeMap}.
 *
 * @param <T> the map type
 */
// created by CollectionTypeBuilder, not mapped by @TripTypeService
@SuppressWarnings("rawtypes")
public class TreeMapTripType<T extends TreeMap> extends MapTripType<T> {

  private final Constructor<T> comparatorConstructor;

  /**
   * Creates the type.
   *
   * @param clazz the tree map's class
   */
  public TreeMapTripType(Class<T> clazz) {
    super(clazz);
    try {
      comparatorConstructor = clazz.getDeclaredConstructor(Comparator.class);
    }
    catch (NoSuchMethodException e) {
      throw new TripInstantiationException(clazz, "missing comparator constructor", e);
    }
  }

  /**
   * Gets the comparator constructor.
   *
   * @return the comparator constructor, never null
   */
  public Constructor<T> getComparatorConstructor() {
    return comparatorConstructor;
  }

  @Override
  protected void writeHeader(T value, TripStream stream) {
    super.writeHeader(value, stream);
    Comparator comparator = value.comparator();
    if (comparator == null) {
      TreeMapTypeHolder.BOOLEAN_TRIP_TYPE.write(false, stream);
    }
    else {
      TreeMapTypeHolder.BOOLEAN_TRIP_TYPE.write(true, stream);
      TripType<Comparator> type = stream.getType(comparator.getClass());
      type.write(comparator, stream);
    }
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected MapHeader<T> readHeader(TripStream stream, OpCode opCode) {
    int size = TreeMapTypeHolder.INTEGER_TRIP_TYPE.read(stream, opCode);
    boolean hasComparator = TreeMapTypeHolder.BOOLEAN_TRIP_TYPE.read(stream, stream.receiveOpCode());
    if (hasComparator) {
      // get the type from the stream (either new dictionary entry or ordinal)
      stream.receiveOpCode();   // skip OBJECT
      TripType<Comparator> type = (TripType<Comparator>) stream.receiveDictionary().getType();
      // the next opcode is a reference or the handler data
      Comparator comparator = type.read(stream, stream.receiveOpCode());
      try {
        return new MapHeader<>(size, comparatorConstructor.newInstance(comparator));
      }
      catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
        throw new TripInstantiationException(getObjectClass(),
                                             "cannot create instance with comparator " +
                                             comparator.getClass().getName(), e);
      }
    }
    return new MapHeader<>(size, createMap(size));
  }

}
