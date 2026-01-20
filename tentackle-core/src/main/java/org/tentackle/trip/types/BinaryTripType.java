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

import org.tentackle.common.Binary;
import org.tentackle.trip.Deserializer;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripTypeService;
import org.tentackle.trip.impl.DefaultOpCode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;

/**
 * Trip type for {@link Binary}.
 */
@TripTypeService(Binary.class)
public final class BinaryTripType extends AbstractTripType<Binary<? extends Serializable>> {

  /**
   * Creates the type.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public BinaryTripType() {
    super((Class) Binary.class);
  }

  @Override
  public boolean isFinal() {
    // Binary is not final but cannot be treated as a regular object, since
    // it's internal api is too specific.
    // If this type is extended, the application must provide a custom trip type.
    return true;
  }

  @Override
  public void write(Binary<?> value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.BINARY);
      int length = value.getLength();
      serializer.serializeInt(length);
      serializer.serializeBoolean(value.isFrozen());
      serializer.serialize(value.getData(), 0, length);
    }
  }

  @Override
  public Binary<?> read(TripStream stream, OpCode opCode) {
    Binary<?> value;
    Deserializer deserializer = stream.getDeserializer();
    if (opCode == DefaultOpCode.BINARY) {
      int length = deserializer.deserializeInt();
      boolean frozen = deserializer.deserializeBoolean();
      byte[] data = new byte[length];
      deserializer.deserialize(data, 0, length);
      try {
        value = Binary.createBinary(new ByteArrayInputStream(data), length, frozen);
      }
      catch (IOException e) {
        throw new TripRuntimeException("cannot deserialize Binary", e);
      }
    }
    else {
      value = super.read(stream, opCode);
    }
    return value;
  }

}
