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

  /**
   * Returned by {@link #readSkip} for an opcode that is not a skip.<br>
   * Skip counts are never negative, so this cannot collide with a real one.
   */
  protected static final int NO_SKIP = -1;

  /** Maximum number of elements in a block, such as {@link DefaultOpCode#BYTEN}. */
  protected static final int MAX_BLOCK_LENGTH = 255;

  /**
   * Maximum length of a run of default values kept within a block.<br>
   * Inside a block, each default value costs one byte. Ending the block costs a skip and a new
   * block header (or the opcode of a single value), 2 bytes each.
   */
  protected static final int MAX_INNER_ZEROS = 3;

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
    if (opCode instanceof DefaultOpCode code) {
      return switch (code) {
        case ARRAYN1 -> deserializer.deserializeByteAsUnsigned();
        case ARRAYN2 -> deserializer.deserializeShortAsUnsigned();
        case ARRAYN -> deserializer.deserializeInt();
        default -> throw new TripRuntimeException("invalid array opcode: " + opCode);
      };
    }
    throw new TripRuntimeException("invalid array opcode: " + opCode);
  }

  /**
   * Reads the array skip, if the opcode is one.
   * <p>
   * The counterpart of {@link #writeSkip}, shared by the read loop of every predefined array type.
   * <p>
   * The opcode is narrowed to {@link DefaultOpCode} before switching: with the {@link OpCode}
   * interface as the selector type, javac compiles the switch to an {@code invokedynamic
   * typeSwitch}, whose generated body is a linear chain of comparisons instead of a jump table.
   * An opcode of another implementation is not a skip and takes the {@link #NO_SKIP} path.
   *
   * @param deserializer the deserializer
   * @param opCode the opcode (already read from the stream)
   * @return the number of elements to skip, {@link #NO_SKIP} if the opcode is not a skip
   */
  protected int readSkip(Deserializer deserializer, OpCode opCode) {
    if (opCode instanceof DefaultOpCode code) {
      int skip = switch (code) {
        case SKIP -> deserializer.deserializeInt();
        case SKIP2 -> deserializer.deserializeShortAsUnsigned();
        case SKIP1 -> deserializer.deserializeByteAsUnsigned();
        default -> NO_SKIP;
      };
      if (skip < NO_SKIP) {
        // only the 4-byte form can be negative, and only from a corrupted stream.
        // Left unchecked, it would move the write position backwards and loop forever.
        throw new TripRuntimeException("negative skip count " + skip + " for " + this);
      }
      return skip;
    }
    return NO_SKIP;
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
