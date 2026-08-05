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
 * Some constants used in the Trip protocol.
 */
public class TripConstants {

  /**
   * The 2-byte magic number (TRIP connection header): <code>&laquo; &raquo;</code><br>
   * Which is followed by the 16-byte UUID of the transport and a single byte telling
   * whether the client's dictionary is still empty (see {@link Dictionary#isEmpty()}).
   */
  public static final byte[] MAGIC = new byte[] { (byte) 0xAB, (byte) 0xBB };

  /**
   * Previous version header, w/o the dictionary byte.<br>
   * (Tentackle version <= 25.18.2.1)
   */
  public static final byte[] MAGIC_1 = new byte[] { (byte) 0xBB, (byte) 0xAB };

  /**
   * Maximum value for unsigned bytes.
   */
  public static final int UNSIGNED_BYTE_MAX = 0xff;

  /**
   * Maximum value for unsigned shorts.
   */
  public static final int UNSIGNED_SHORT_MAX = 0xffff;

  /**
   * Maximum value for unsigned 3-byte integers.
   */
  public static final int UNSIGNED_INT3_MAX = 0xffffff;

  /**
   * Maximum value for 3-byte signed integers.
   */
  public static final int INT3_MAX = 0x7fffff;

  /**
   * Minimum value for 3-byte signed integers.
   */
  public static final int INT3_MIN = -INT3_MAX - 1;

  /**
   * Maximum value for 5-byte signed longs.
   */
  public static final long LONG5_MAX = 0x7fffffffffL;

  /**
   * Minimum value for 5-byte signed longs.
   */
  public static final long LONG5_MIN = -LONG5_MAX - 1;

  /**
   * Maximum value for 6-byte signed longs.
   */
  public static final long LONG6_MAX = 0x7fffffffffffL;

  /**
   * Minimum value for 6-byte signed longs.
   */
  public static final long LONG6_MIN = -LONG6_MAX - 1;

  /**
   * Maximum value for 7-byte signed longs.
   */
  public static final long LONG7_MAX = 0x7fffffffffffffL;

  /**
   * Minimum value for 7-byte signed longs.
   */
  public static final long LONG7_MIN = -LONG7_MAX - 1;


  private TripConstants() {}

}
