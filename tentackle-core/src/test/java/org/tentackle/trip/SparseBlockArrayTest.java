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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Random;

import static org.testng.Assert.assertTrue;

/**
 * Tests the block encoding of {@code boolean[]}, {@code char[]} and {@code short[]}.
 * <p>
 * Like {@code byte[]} (see {@link SparseByteArrayTest}), these arrays are encoded with skip codes
 * for runs of default values and with one-byte-per-element blocks for the other values.
 * {@code char[]} and {@code short[]} values that don't fit into a byte are sent as single values.
 */
@SuppressWarnings("missing-explicit-ctor")
public class SparseBlockArrayTest {

  private static final int LENGTH = 10_000;

  /**
   * The value kinds of an element.
   */
  private enum Kind { ZERO, SMALL, WIDE }


  @Test
  public void testZeroBeforeWideValue() {
    // a single zero in front of a value that doesn't fit into a block got lost
    ArrayRoundTrip.encodeAndVerify(new char[] { 'a', 0, 'Ā', 'b' });
    ArrayRoundTrip.encodeAndVerify(new char[] { 0, 'Ā' });
    ArrayRoundTrip.encodeAndVerify(new char[] { 'a', 0, 0, 'Ā' });
    ArrayRoundTrip.encodeAndVerify(new short[] { 1, 0, 1000, 2 });
    ArrayRoundTrip.encodeAndVerify(new short[] { 0, 1000 });
    ArrayRoundTrip.encodeAndVerify(new short[] { 1, 0, 0, -1000 });
  }

  @Test
  public void testRoundTripBoundaries() {
    // zero runs around the length kept within a block, small runs around the block limit,
    // wide values (char[] and short[] only) in between
    Random random = new Random(4711);
    for (Class<?> componentType : new Class<?>[] { boolean.class, char.class, short.class }) {
      int maxWide = componentType == boolean.class ? 0 : 2;
      for (int zeros = 1; zeros <= 6; zeros++) {
        for (int smalls = 1; smalls <= 3; smalls++) {
          for (int wides = 0; wides <= maxWide; wides++) {
            int period = zeros + smalls + wides;
            for (int length : new int[] { 0, 1, 2, 3, 254, 255, 256, 257, 510, 511, 512, 1000 }) {
              for (int offset = 0; offset < period; offset++) {
                Kind[] kinds = new Kind[length];
                for (int i = 0; i < length; i++) {
                  int phase = (i + offset) % period;
                  kinds[i] = phase < zeros ? Kind.ZERO : phase < zeros + smalls ? Kind.SMALL : Kind.WIDE;
                }
                ArrayRoundTrip.encodeAndVerify(create(componentType, kinds, random));
              }
            }
          }
        }
      }
    }
  }

  @Test
  public void testRandomRoundTrip() {
    Random random = new Random(42);
    Kind[] values = Kind.values();
    for (Class<?> componentType : new Class<?>[] { boolean.class, char.class, short.class }) {
      int kindCount = componentType == boolean.class ? 2 : 3;
      for (int run = 0; run < 200; run++) {
        Kind[] kinds = new Kind[random.nextInt(2000)];
        for (int i = 0; i < kinds.length; i++) {
          kinds[i] = values[random.nextInt(kindCount)];
        }
        ArrayRoundTrip.encodeAndVerify(create(componentType, kinds, random));
      }
    }
  }


  @DataProvider
  public Object[][] sizes() {
    return new Object[][] {
      // pattern, maximum encoded bytes per element for boolean[], char[] and short[]
      { new Kind[] { Kind.SMALL },                                            1.01 },
      { new Kind[] { Kind.SMALL, Kind.ZERO },                                 1.02 },
      { new Kind[] { Kind.SMALL, Kind.ZERO, Kind.ZERO },                      1.02 },
      { new Kind[] { Kind.SMALL, Kind.ZERO, Kind.ZERO, Kind.ZERO },           1.02 },
      { new Kind[] { Kind.SMALL, Kind.SMALL, Kind.SMALL, Kind.ZERO },         1.02 },
      // 4 zeros: a skip (2) and a single value (2) per 5 elements
      { new Kind[] { Kind.SMALL, Kind.ZERO, Kind.ZERO, Kind.ZERO, Kind.ZERO }, 0.81 },
      { new Kind[] { Kind.ZERO },                                             0.01 },
    };
  }

  @Test(dataProvider = "sizes")
  public void testSize(Kind[] pattern, double maxBytesPerElement) {
    Random random = new Random(43);
    Kind[] kinds = new Kind[LENGTH];
    for (int i = 0; i < LENGTH; i++) {
      kinds[i] = pattern[i % pattern.length];
    }
    for (Class<?> componentType : new Class<?>[] { boolean.class, char.class, short.class }) {
      int size = ArrayRoundTrip.encodeAndVerify(create(componentType, kinds, random));
      double bytesPerElement = (double) size / LENGTH;
      System.out.printf("%-8s %-40s encoded=%6d   bytes/element=%5.3f%n",
                        componentType.getSimpleName() + "[]", Arrays.toString(pattern),
                        size, bytesPerElement);
      assertTrue(bytesPerElement <= maxBytesPerElement,
                 componentType + "[] " + Arrays.toString(pattern) + ": " + bytesPerElement +
                 " bytes/element > " + maxBytesPerElement);
    }
  }

  @Test
  public void testWideValues() {
    // a wide value ends the block: 9 small values in a block (2 + 9) and the wide value (3)
    Kind[] kinds = new Kind[LENGTH];
    for (int i = 0; i < LENGTH; i++) {
      kinds[i] = i % 10 == 9 ? Kind.WIDE : Kind.SMALL;
    }
    Random random = new Random(44);
    for (Class<?> componentType : new Class<?>[] { char.class, short.class }) {
      int size = ArrayRoundTrip.encodeAndVerify(create(componentType, kinds, random));
      assertTrue(size <= LENGTH * 14 / 10 + 10, componentType + "[]: " + size + " bytes");
    }
  }


  /**
   * Creates an array.
   *
   * @param componentType boolean.class, char.class or short.class
   * @param kinds the kinds of the elements
   * @param random the random generator
   * @return the array
   */
  private static Object create(Class<?> componentType, Kind[] kinds, Random random) {
    Object array = Array.newInstance(componentType, kinds.length);
    for (int i = 0; i < kinds.length; i++) {
      Kind kind = kinds[i];
      if (componentType == boolean.class) {
        Array.setBoolean(array, i, kind != Kind.ZERO);
      }
      else if (componentType == char.class) {
        Array.setChar(array, i, switch (kind) {
          case ZERO -> (char) 0;
          case SMALL -> (char) (1 + random.nextInt(255));
          case WIDE -> (char) (0x100 + random.nextInt(0xFF00));
        });
      }
      else {
        Array.setShort(array, i, switch (kind) {
          case ZERO -> (short) 0;
          case SMALL -> (short) (random.nextBoolean() ? 1 + random.nextInt(127) : -1 - random.nextInt(128));
          case WIDE -> (short) (random.nextBoolean() ? 128 + random.nextInt(32640) : -129 - random.nextInt(32640));
        });
      }
    }
    return array;
  }

}
