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
 * Predefined array types.<br>
 * A subcode for the predefined array type op-code.
 */
public enum PredefinedArrayType {

  /** {@code Boolean[]}. */
  BOOLEAN('O', Boolean[].class),
  /** {@code boolean[]}. */
  BOOLEAN_PRIMITIVE('o', boolean[].class),
  /** {@code Byte[]}. */
  BYTE('B', Byte[].class),
  /** {@code byte[]}. */
  BYTE_PRIMITIVE('b', byte[].class),
  /** {@code Short[]}. */
  SHORT('S', Short[].class),
  /** {@code short[]}. */
  SHORT_PRIMITIVE('s', short[].class),
  /** {@code Integer[]}. */
  INT('I', Integer[].class),
  /** {@code int[]}. */
  INT_PRIMITIVE('i', int[].class),
  /** {@code Long[]}. */
  LONG('L', Long[].class),
  /** {@code long[]}. */
  LONG_PRIMITIVE('l', long[].class),
  /** {@code Float[]}. */
  FLOAT('F', Float[].class),
  /** {@code float[]}. */
  FLOAT_PRIMITIVE('f', float[].class),
  /** {@code Double[]}. */
  DOUBLE('D', Double[].class),
  /** {@code double[]}. */
  DOUBLE_PRIMITIVE('d', double[].class),
  /** {@code Character[]}. */
  CHAR('C', Character[].class),
  /** {@code char[]}. */
  CHAR_PRIMITIVE('c', char[].class),
  /** {@code String[]}. */
  STRING('T', String[].class);

  private final char code;
  private final Class<?> clazz;

  /**
   * Creates a new predefined array type.
   *
   * @param code the unique code
   * @param clazz the class of the predefined array type
   */
  PredefinedArrayType(char code, Class<?> clazz) {
    this.code = code;
    this.clazz = clazz;
  }

  /**
   * Gets the code.
   *
   * @return the code
   */
  public char getCode() {
    return code;
  }

  /**
   * Gets the class of the predefined array type.
   *
   * @return the class
   */
  public Class<?> getArrayClass() {
    return clazz;
  }

  /**
   * Gets the predefined array type for the given code.
   *
   * @param code the code
   * @return the predefined array type, or null if not found
   */
  public static PredefinedArrayType valueOf(char code) {
    for (PredefinedArrayType type : PredefinedArrayType.values()) {
      if (type.getCode() == code) {
        return type;
      }
    }
    return null;
  }

  /**
   * Gets the predefined array type for the given class.
   *
   * @param clazz the class
   * @return the predefined array type, or null if not found
   */
  public static PredefinedArrayType valueOf(Class<?> clazz) {
    for (PredefinedArrayType type : PredefinedArrayType.values()) {
      if (type.getArrayClass() == clazz) {
        return type;
      }
    }
    return null;
  }

  /**
   * Asserts that the given type corresponds to the same as the given class.
   *
   * @param code the type code
   * @param clazz the expected class
   * @throws IllegalArgumentException if the code is unknown or denotes another class
   */
  public static void assertType(char code, Class<?> clazz) {
    PredefinedArrayType type = valueOf(code);
    if (type == null) {
      throw new IllegalArgumentException("unknown predefined array type code: " + code);
    }
    if (type.getArrayClass() != clazz) {
      throw new IllegalArgumentException("type mismatch: " + type.getArrayClass() + " != " + clazz);
    }
  }

}
