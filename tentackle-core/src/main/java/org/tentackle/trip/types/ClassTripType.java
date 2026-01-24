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

import org.tentackle.trip.DictionaryEntry;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripTypeService;
import org.tentackle.trip.impl.DefaultOpCode;

/**
 * Trip type for {@link Class}.
 * <p>
 * Serializes the dictionary for the class instead of just the class name.
 * This reduces the size of the serialized data significantly, especially for proxies
 * (PDOs, for example).
 */
@TripTypeService(Class.class)
public final class ClassTripType extends AbstractTripType<Class<?>> {

  /**
   * Creates the type.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public ClassTripType() {
    super((Class) Class.class);
  }

  @Override
  public void write(Class<?> value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.CLASS);
      stream.sendDictionary(value);
    }
  }

  @Override
  public Class<?> read(TripStream stream, OpCode opCode) {
    if (opCode == DefaultOpCode.NULL) {
      return null;
    }
    if (opCode == DefaultOpCode.CLASS) {
      DictionaryEntry entry = stream.receiveDictionary();
      return stream.getClass(entry.getClassName());
    }
    return super.read(stream, opCode);
  }

}
