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

import org.tentackle.common.AbstractBinary;
import org.tentackle.common.Binary;
import org.tentackle.trip.Deserializer;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TBinary;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripTypeService;
import org.tentackle.trip.impl.DefaultOpCode;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Trip type for {@link Binary} and {@link TBinary}.
 */
@TripTypeService(AbstractBinary.class)
public final class BinaryTripType extends AbstractTripType<AbstractBinary<?>> {

  private static final byte JOS_BINARY = 'J';
  private static final byte TRIP_BINARY = 'T';

  /**
   * Creates the type.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public BinaryTripType() {
    super((Class) Binary.class);
  }

  @Override
  public boolean isFinal() {
    // AbstractBinary is not final, but its subtypes are final
    return true;
  }

  @Override
  public void write(AbstractBinary<?> value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.BINARY);
      serializer.serializeByte(value instanceof Binary<?> ? JOS_BINARY : TRIP_BINARY);
      int length = value.getLength();
      serializer.serializeInt(length);
      serializer.serializeBoolean(value.isFrozen());
      serializer.serialize(value.getData(), 0, length);
    }
  }

  @Override
  public AbstractBinary<?> read(TripStream stream, OpCode opCode) {
    AbstractBinary<?> value;
    Deserializer deserializer = stream.getDeserializer();
    if (opCode == DefaultOpCode.BINARY) {
      byte subType = deserializer.deserializeByte();
      int length = deserializer.deserializeInt();
      boolean frozen = deserializer.deserializeBoolean();
      byte[] data = new byte[length];
      deserializer.deserialize(data, 0, length);
      try {
        value = subType == JOS_BINARY ?
                  Binary.create(new ByteArrayInputStream(data), length, frozen) :
                  TBinary.create(new ByteArrayInputStream(data), length, frozen);
      }
      catch (IOException e) {
        throw new TripRuntimeException("cannot deserialize binary", e);
      }
    }
    else {
      value = super.read(stream, opCode);
    }
    return value;
  }

}
