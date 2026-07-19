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
import org.tentackle.trip.impl.DefaultOpCode;

import java.util.HashMap;
import java.util.Map;

/**
 * Trip type for immutable maps.
 * <p>
 * Since immutable maps cannot be modified, we create a HashMap first
 * and then copy the contents of the original map into the HashMap.
 *
 * @param <T> the map type
 */
// created by MapTypeBuilder, not mapped by @TripTypeService
@SuppressWarnings("rawtypes")
public class ImmutableMapTripType<T extends Map> extends MapTripType<T> {

  private static final OpCode OP_CODE = DefaultOpCode.IMMUTABLE_MAP;

  /**
   * Creates the type.
   *
   * @param clazz the map's class
   */
  public ImmutableMapTripType(Class<T> clazz) {
    super(clazz);
  }

  @Override
  public boolean isInstantiable() {
    return false;
  }

  @Override
  protected boolean isConverted() {
    return true;
  }

  @Override
  protected OpCode getOpCode() {
    return OP_CODE;
  }

  @Override
  @SuppressWarnings("unchecked")
  protected T convert(T value) {
    return (T) Map.copyOf(value);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected T createMap(int size) {
    return (T) new HashMap<>(size);
  }

}
