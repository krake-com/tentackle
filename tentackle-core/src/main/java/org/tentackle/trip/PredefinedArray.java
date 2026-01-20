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

package org.tentackle.trip;

/**
 * Predefined array type.
 * <p>
 * Predefined array types are used for primitives and their wrappers
 * (like {@code boolean[]}, {@code Byte[]}, {@code Character[]}, etc.)
 * and {@code String[]}.<br>
 * Although they are final, i.e., you cannot write {@code Boolean[] bools = new boolean[10]},
 * they can be assigned to a variable of type {@code Object[]}, which is <em>not</em> final.<br>
 * When reading a component with a compile-type of {@code Object[]} from a stream,
 * the predefined array type is determined by the {@link PredefinedArrayType} which follows the {@link OpCode}.
 * However, since the {@link TripType}s of predefined arrays are not dictionary-based, their {@link TripType} implementation
 * has no way to detect whether the runtime type was already read from the stream or not.
 * For those rare cases, when the type is used directly and not as a component of another object,
 * the method {@link #readWithType(TripStream, OpCode)} must be invoked
 * instead of {@link TripType#read(TripStream, OpCode)}.
 * <p>
 * Notice that for the reasons mentioned above, {@link TripType#isFinal()} for
 * predefined arrays must return false!
 *
 * @param <T> the type of the predefined array
 */
public interface PredefinedArray<T> {

  /**
   * Reads the predefined array from the stream, including the array type.
   *
   * @param stream the stream to read from
   * @param opCode the op code
   * @return the array
   */
  T readWithType(TripStream stream, OpCode opCode);

}
