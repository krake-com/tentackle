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

import java.io.OutputStream;

/**
 * Low-level serializer for primitive values and raw bytes.
 */
public interface Serializer {

  /**
   * Gets the output stream.
   *
   * @return the output stream, never null
   */
  OutputStream getOutputStream();

  /**
   * Sets the output stream.<br>
   * Any existing pending serialized data is lost.
   *
   * @param out the output stream
   */
  void setOutputStream(OutputStream out);

  /**
   * Flushes all pending serializations.
   */
  void flush();

  /**
   * Serializes a byte array.<br>
   * Low-level method passes the bytes unmodified.
   *
   * @param bytes the byte array to serialize
   * @param offset the offset to start
   * @param length the number of bytes
   */
  void serialize(byte[] bytes, int offset, int length);

  /**
   * Serializes a byte array.<br>
   * Low-level method passes the bytes unmodified.
   *
   * @param bytes the byte array to serialize
   */
  void serialize(byte[] bytes);

  /**
   * Serializes the given op code.
   *
   * @param opCode the op code
   */
  void serializeOpCode(OpCode opCode);

  /**
   * Serializes a byte.
   *
   * @param value the byte to be serialized
   */
  void serializeByte(byte value);

  /**
   * Serializes an unsigned integer as a single byte.<br>
   * The lower 8 bits of the passed value are serialized.
   *
   * @param value the value to be serialized (0-255)
   */
  void serializeUnsignedAsByte(int value);

  /**
   * Serializes a {@code boolean} value.
   *
   * @param value the boolean to be serialized
   */
  void serializeBoolean(boolean value);

  /**
   * Serializes a {@code char} value.
   *
   * @param value the char to be serialized
   */
  void serializeChar(char value);

  /**
   * Serializes a {@code short}.
   *
   * @param value the {@code short} value to be serialized
   */
  void serializeShort(short value);

  /**
   * Serializes an unsigned integer as two bytes.<br>
   * The lower 16 bits of the passed value are serialized.
   *
   * @param value the value to be serialized as two bytes (0-65,535)
   */
  void serializeUnsignedAsShort(int value);

  /**
   * Serializes an {@code int} value.
   *
   * @param value the {@code int} value to be serialized
   */
  void serializeInt(int value);

  /**
   * Serializes a 3-byte {@code int} value.<br>
   * Notice: there is no {@code serializeUnsignedToInt3} since
   * the implementation would be identical to {@code serializeInt3}.
   *
   * @param value the {@code int} value to be serialized
   */
  void serializeInt3(int value);

  /**
   * Serializes a {@code long} value.
   *
   * @param value the {@code long} value to be serialized
   */
  void serializeLong(long value);

  /**
   * Serializes a 5-byte {@code long} value.
   *
   * @param value the {@code long} value to be serialized
   */
  void serializeLong5(long value);

  /**
   * Serializes a 6-byte {@code long} value.
   *
   * @param value the {@code long} value to be serialized
   */
  void serializeLong6(long value);

  /**
   * Serializes a 7-byte {@code long} value.
   *
   * @param value the {@code long} value to be serialized
   */
  void serializeLong7(long value);

  /**
   * Serializes a {@code float} value.
   *
   * @param value the {@code float} value to be serialized
   */
  void serializeFloat(float value);

  /**
   * Serializes a {@code double} value.
   *
   * @param value the {@code double} value to be serialized
   */
  void serializeDouble(double value);

}
