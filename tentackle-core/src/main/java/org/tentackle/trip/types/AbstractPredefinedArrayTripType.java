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

import org.tentackle.trip.Deserializer;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.PredefinedArray;
import org.tentackle.trip.PredefinedArrayType;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.impl.DefaultOpCode;

import java.lang.reflect.Array;

/**
 * Base class for all predefined array types.
 *
 * @param <T> the type
 */
public abstract class AbstractPredefinedArrayTripType<T> extends AbstractTripType<T>
       implements PredefinedArray<T> {

  private final PredefinedArrayType subType;

  /**
   * Creates the type.
   *
   * @param clazz the array's class
   */
  public AbstractPredefinedArrayTripType(Class<T> clazz) {
    super(clazz);
    subType = PredefinedArrayType.valueOf(clazz);
    if (subType == null) {
      throw new TripRuntimeException("The class " + clazz.getName() + " is not a predefined array type");
    }
  }

  @Override
  public boolean isReferencable(T value) {
    return true;
  }

  @Override
  public boolean isFinal() {
    // see PredefinedArray interface
    return false;
  }

  @Override
  public T readWithType(TripStream stream, OpCode opCode) {
    PredefinedArrayType.assertType(stream.getDeserializer().deserializeChar(), getObjectClass());
    return read(stream, opCode);
  }

  /**
   * Writes the array header.
   *
   * @param value the array value
   * @param stream the stream to write to
   * @return true if data is following, false otherwise
   */
  protected boolean writeArrayHeader(T value, TripStream stream) {
    int length = Array.getLength(value);
    Serializer serializer = stream.getSerializer();
    if (length < 256) {
      serializer.serializeOpCode(DefaultOpCode.ARRAYN1);
      serializer.serializeChar(subType.getCode());
      if (sendReference(stream, value).isObjectFollowing()) {
        serializer.serializeUnsignedAsByte(length);
        return true;
      }
    }
    else if (length < 65536) {
      serializer.serializeOpCode(DefaultOpCode.ARRAYN2);
      serializer.serializeChar(subType.getCode());
      if (sendReference(stream, value).isObjectFollowing()) {
        serializer.serializeUnsignedAsShort(length);
        return true;
      }
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.ARRAYN);
      serializer.serializeChar(subType.getCode());
      if (sendReference(stream, value).isObjectFollowing()) {
        serializer.serializeInt(length);
        return true;
      }
    }
    return false;
  }

  /**
   * Reads the array header.<br>
   * The read starts <em>after</em> the predefined array subcode
   * and the reference id.
   *
   * @param deserializer the deserializer
   * @param opCode the array op code
   * @return the array length
   */
  protected int readArrayLength(Deserializer deserializer, OpCode opCode) {
    return switch (opCode) {
      case DefaultOpCode.ARRAYN1 -> deserializer.deserializeByteAsUnsigned();
      case DefaultOpCode.ARRAYN2 -> deserializer.deserializeShortAsUnsigned();
      case DefaultOpCode.ARRAYN -> deserializer.deserializeInt();
      default -> throw new TripRuntimeException("invalid array opcode: " + opCode);
    };
  }

  /**
   * Writes the array skip.
   *
   * @param skip the number of elements to skip
   * @param serializer the serializer
   */
  protected void writeSkip(int skip, Serializer serializer) {
    if (skip < 256) {
      serializer.serializeOpCode(DefaultOpCode.SKIP1);
      serializer.serializeUnsignedAsByte(skip);
    }
    else if (skip < 65536) {
      serializer.serializeOpCode(DefaultOpCode.SKIP2);
      serializer.serializeUnsignedAsShort(skip);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.SKIP);
      serializer.serializeInt(skip);
    }
  }

}
