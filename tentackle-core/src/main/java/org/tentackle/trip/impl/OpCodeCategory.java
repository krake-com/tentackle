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

package org.tentackle.trip.impl;

/**
 * Code category to speed up type lookups.
 */
public enum OpCodeCategory {

  /**
   * Reference type.
   *
   * @see DefaultOpCode#REFERENCE
   * @see DefaultOpCode#REFERENCE1
   * @see DefaultOpCode#REFERENCE2
   * @see DefaultOpCode#REFERENCE3
   */
  REFERENCE,

  /**
   * Ordinal type.
   *
   * @see DefaultOpCode#COMPONENT_ORDINAL
   * @see DefaultOpCode#COMPONENT_ORDINAL1
   * @see DefaultOpCode#COMPONENT_ORDINAL2
   * @see DefaultOpCode#COMPONENT_ORDINAL3
   * @see DefaultOpCode#COMPONENT_ORDINAL_SKIP1
   * @see DefaultOpCode#COMPONENT_ORDINAL_SKIP15
   */
  COMPONENT_ORDINAL,

  /**
   * Dictionary ordinal type.
   *
   * @see DefaultOpCode#DICTIONARY_ORDINAL
   * @see DefaultOpCode#DICTIONARY_ORDINAL1
   * @see DefaultOpCode#DICTIONARY_ORDINAL2
   * @see DefaultOpCode#DICTIONARY_ORDINAL3
   */
  DICTIONARY_ORDINAL,

  /**
   * Fixed type.<br>
   * Fixed types need not be checked against the runtime values.
   */
  FIXED_TYPE,

  /**
   * Dictionary-based type.<br>
   * Those types need to be checked against the runtime values
   * and always serialize either a new dictionary entry or its ordinal.
   */
  DICTIONARY_BASED,

  /**
   * Predefined array type.
   *
   * @see DefaultOpCode#ARRAYN
   * @see DefaultOpCode#ARRAYN1
   * @see DefaultOpCode#ARRAYN2
   */
  PREDEFINED_ARRAY,

  /**
   * Low-level serialization/deserialization error.
   */
  ERROR

}
