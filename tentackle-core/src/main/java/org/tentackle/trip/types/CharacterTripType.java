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
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripTypeService;
import org.tentackle.trip.impl.DefaultOpCode;

/**
 * Trip type for {@link Character}.
 */
@TripTypeService(Character.class)
public sealed class CharacterTripType extends AbstractTripType<Character> permits CharacterPrimitiveTripType {

  /**
   * Creates the type.
   */
  public CharacterTripType() {
    this(Character.class);
  }

  /**
   * Super constructor for the primitive type.
   *
   * @param clazz the class of the type
   */
  protected CharacterTripType(Class<Character> clazz) {
    super(clazz);
  }

  @Override
  public void write(Character value, TripStream stream) {
    if (value == null) {
      stream.getSerializer().serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      writeChar(value, stream);
    }
  }

  @Override
  public Character read(TripStream stream, OpCode opCode) {
    Deserializer deserializer = stream.getDeserializer();
    // Narrowed to DefaultOpCode before switching: with the OpCode interface as the selector
    // type, javac compiles the switch to an invokedynamic typeSwitch, whose generated body is
    // a linear chain of comparisons instead of a jump table. An opcode of another
    // implementation is not one of ours and takes the same path as an unknown one.
    if (opCode instanceof DefaultOpCode code) {
      return switch (code) {
        case CHAR -> deserializer.deserializeChar();
        case CHAR1 -> (char) deserializer.deserializeByteAsUnsigned();
        default -> super.read(stream, opCode);
      };
    }
    return super.read(stream, opCode);
  }


  /**
   * Writes a char value.
   * <p>
   * The primitive counterpart of {@link #write(Character, TripStream)}, so that a primitive component
   * can be written without boxing it first.
   *
   * @param value the value to write
   * @param stream the stream to write to
   */
  public void writeChar(char value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    if (value >>> 8 == 0) {
      serializer.serializeOpCode(DefaultOpCode.CHAR1);
      serializer.serializeUnsignedAsByte(value);
    }
    else {
      serializer.serializeOpCode(DefaultOpCode.CHAR);
      serializer.serializeChar(value);
    }
  }

  /**
   * Reads a char value.
   * <p>
   * The primitive counterpart of {@link #read(TripStream, OpCode)}, so that a primitive component
   * can be read without boxing it. The opcode switch is deliberately repeated instead of delegating:
   * going through the boxed method is exactly the allocation this avoids.
   *
   * @param stream the stream to read from
   * @param opCode the op code (already read from the stream)
   * @return the value
   * @throws TripRuntimeException if the opcode does not denote a char
   */
  public char readChar(TripStream stream, OpCode opCode) {
    if (opCode instanceof DefaultOpCode code) {
      Deserializer deserializer = stream.getDeserializer();
      return switch (code) {
        case CHAR -> deserializer.deserializeChar();
        case CHAR1 -> (char) deserializer.deserializeByteAsUnsigned();
        default -> throw new TripRuntimeException("illegal opcode " + opCode + " for " + this);
      };
    }
    throw new TripRuntimeException("illegal opcode " + opCode + " for " + this);
  }
}
