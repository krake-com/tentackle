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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Trip type for immutable collections.
 * <p>
 * Since immutable collections cannot be modified, we create an ArrayList first
 * and then copy the contents of the original collection into the ArrayList.
 *
 * @param <T> the collection type
 */
// created by CollectionTypeBuilder, not mapped by @TripTypeService
@SuppressWarnings("rawtypes")
public class ImmutableCollectionTripType<T extends Collection> extends CollectionTripType<T> {

  private static final OpCode OP_CODE = DefaultOpCode.IMMUTABLE_COLLECTION;

  /**
   * Creates the type.
   *
   * @param clazz the collection's class
   */
  public ImmutableCollectionTripType(Class<T> clazz) {
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
    return Set.class.isAssignableFrom(getObjectClass()) ? (T) Set.copyOf(value) : (T) List.copyOf(value);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected T createCollection(int size) {
    return (T) new ArrayList<>(size);
  }

}
