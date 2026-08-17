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

package org.tentackle.quiche.ffm;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.ValueLayout;

/**
 * The C types of the quiche API, named as they appear in {@code quiche.h}.
 * <p>
 * Spelling them out keeps the function descriptors readable and puts the platform assumptions in one
 * place. The only assumption made is a 64-bit data model &mdash; {@code size_t} and {@code ssize_t} are
 * eight bytes wide. All platforms the {@code jetty-quiche-native} artifact ships binaries for are 64-bit.
 */
public final class ValueLayouts {

  /**
   * A pointer, {@code void *} and friends.
   */
  public static final AddressLayout POINTER = ValueLayout.ADDRESS;

  /**
   * C {@code bool}, one byte.
   */
  public static final ValueLayout.OfBoolean BOOL = ValueLayout.JAVA_BOOLEAN;

  /**
   * C {@code uint8_t}.
   */
  public static final ValueLayout.OfByte UINT8 = ValueLayout.JAVA_BYTE;

  /**
   * C {@code int}.
   */
  public static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT;

  /**
   * C {@code uint32_t}. Java has no unsigned int: values above {@link Integer#MAX_VALUE} appear negative
   * and must be treated with {@link Integer#toUnsignedLong(int)} where that matters.
   */
  public static final ValueLayout.OfInt UINT32 = ValueLayout.JAVA_INT;

  /**
   * C {@code uint64_t}. Same caveat as {@link #UINT32}, see {@link Long#toUnsignedString(long)}.
   */
  public static final ValueLayout.OfLong UINT64 = ValueLayout.JAVA_LONG;

  /**
   * C {@code size_t} on a 64-bit platform.
   */
  public static final ValueLayout.OfLong SIZE_T = ValueLayout.JAVA_LONG;

  /**
   * C {@code ssize_t} on a 64-bit platform. This is the return type of the functions that either fill a
   * buffer or report one of the {@code QUICHE_ERR_} codes.
   */
  public static final ValueLayout.OfLong SSIZE_T = ValueLayout.JAVA_LONG;

  /**
   * C {@code socklen_t}.
   */
  public static final ValueLayout.OfInt SOCKLEN_T = ValueLayout.JAVA_INT;

  private ValueLayouts() {
  }

}
