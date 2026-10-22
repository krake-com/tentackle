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

import java.io.InputStream;

/**
 * Low-level deserializer for primitive values and raw bytes.
 */
public interface Deserializer {

  /**
   * Gets the input stream.
   *
   * @return the input stream, never null
   */
  InputStream getInputStream();

  /**
   * Sets the input stream.<br>
   * Any pending data input is lost.
   *
   * @param in the input stream
   */
  void setInputStream(InputStream in);

  /**
   * Deserializes a byte array.<br>
   * Low-level method passes the bytes unmodified.
   *
   * @param bytes the byte array to deserialize into
   * @param offset the array offset
   * @param length the number of bytes
   */
  void deserialize(byte[] bytes, int offset, int length);

  /**
   * Deserializes a byte to an op code.
   *
   * @return the op code
   */
  OpCode deserializeOpCode();

  /**
   * Deserializes a byte.
   *
   * @return the byte value
   */
  byte deserializeByte();

  /**
   * Deserializes a byte as an unsigned integer.
   *
   * @return an integer value from 0 to 255
   */
  int deserializeByteAsUnsigned();

  /**
   * Deserializes a {@code boolean} value.
   *
   * @return the boolean value
   */
  boolean deserializeBoolean();

  /**
   * Deserializes a {@code char} value.
   *
   * @return the char value
   */
  char deserializeChar();

  /**
   * Deserializes a {@code short} value.
   *
   * @return the {@code short} value
   */
  short deserializeShort();

  /**
   * Deserializes two bytes as an unsigned integer.
   *
   * @return an integer value from 0 to 65,535
   */
  int deserializeShortAsUnsigned();

  /**
   * Deserializes an {@code int} value.
   *
   * @return the {@code int} value
   */
  int deserializeInt();

  /**
   * Deserializes a 3-byte {@code int} value.
   *
   * @return the {@code int} value
   */
  int deserializeInt3();

  /**
   * Deserializes three bytes as an unsigned integer.
   *
   * @return an integer value from 0 to 16,777,215
   */
  int deserializeInt3AsUnsigned();

  /**
   * Deserializes a {@code long} value.
   *
   * @return the {@code long} value
   */
  long deserializeLong();

  /**
   * Deserializes a 5-byte {@code long} value.
   *
   * @return the {@code long} value
   */
  long deserializeLong5();

  /**
   * Deserializes a 6-byte {@code long} value.
   *
   * @return the {@code long} value
   */
  long deserializeLong6();

  /**
   * Deserializes a 7-byte {@code long} value.
   *
   * @return the {@code long} value
   */
  long deserializeLong7();

  /**
   * Deserializes a {@code float} value.
   *
   * @return the {@code float} value
   */
  float deserializeFloat();

  /**
   * Deserializes a {@code double} value.
   *
   * @return the {@code double} value
   */
  double deserializeDouble();

  /**
   * Deserializes a UTF-8 encoded string.
   *
   * @param length the number of bytes, not the number of characters
   * @return the string
   * @throws TripRuntimeException if the length is negative
   */
  String deserializeUtf8(int length);

}
