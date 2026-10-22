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

import java.util.Random;

import static org.testng.Assert.assertTrue;

/**
 * Measures the payload reduction of sparse {@code byte[]} arrays.
 * <p>
 * {@code byte[]} is encoded with skip codes for runs of zeros and with blocks for runs of
 * non-zero bytes. This test serializes arrays of various sizes and zero distributions,
 * verifies the round trip and reports the encoded size relative to the raw array length:
 * <pre>
 *   reduction = (1 - encodedSize / length) * 100
 * </pre>
 * A negative reduction is an overhead, as for dense arrays without any zeros.
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class SparseByteArrayTest {

  private static final int[] SIZES = { 200, 10_000, 1 << 20 };


  @DataProvider
  public Object[][] variations() {
    return new Object[][] {
      // name, generator, minimum reduction in percent for length >= 10000
      { "all-zero",              Generator.ALL_ZERO,            99.0 },
      { "random-1%",             Generator.random(0.01),        95.0 },
      { "random-5%",             Generator.random(0.05),        79.0 },
      { "random-10%",            Generator.random(0.10),        61.0 },
      { "random-25%",            Generator.random(0.25),        24.0 },
      { "random-50%",            Generator.random(0.50),         2.0 },
      { "random-75%",            Generator.random(0.75),        -1.5 },
      { "random-90%",            Generator.random(0.90),        -1.5 },
      { "dense-no-zeros",        Generator.DENSE,               -1.0 },
      { "alternating",           Generator.ALTERNATING,         -1.5 },
      { "1-of-3-non-zero",       Generator.ONE_OF_THREE,        -1.0 },
      { "clusters-64-of-1024",   Generator.clusters(64, 1024),  92.0 },
      { "clusters-16-of-256",    Generator.clusters(16, 256),   91.0 },
      { "head-10%-used",         Generator.head(0.10),          89.0 },
      { "tail-10%-used",         Generator.tail(0.10),          89.0 },
    };
  }

  @Test(dataProvider = "variations")
  public void testReduction(String name, Generator generator, double minReduction) {
    for (int size : SIZES) {
      byte[] data = generator.generate(size, new Random(size));
      int encodedSize = ArrayRoundTrip.encodeAndVerify(data);
      double reduction = (1.0 - (double) encodedSize / size) * 100.0;
      System.out.printf("%-22s length=%9d   non-zero=%6.2f%%   encoded=%9d   reduction=%7.2f%%%n",
                        name, size, nonZeroPercent(data), encodedSize, reduction);
      // tiny arrays are dominated by the header and the random distribution is not yet stable
      if (size >= 10_000) {
        assertTrue(reduction >= minReduction,
                   name + " with length " + size + ": reduction " + reduction + "% < " + minReduction + "%");
      }
    }
  }


  @Test
  public void testRoundTripBoundaries() {
    // zero runs around the length kept within a block, non-zero runs around the block limit
    Random random = new Random(4711);
    for (int zeros = 1; zeros <= 6; zeros++) {
      for (int nonZeros = 1; nonZeros <= 3; nonZeros++) {
        for (int length : new int[] { 0, 1, 2, 3, 254, 255, 256, 257, 510, 511, 512, 1000 }) {
          for (int offset = 0; offset < zeros + nonZeros; offset++) {
            byte[] data = new byte[length];
            for (int i = 0; i < length; i++) {
              if ((i + offset) % (zeros + nonZeros) >= zeros) {
                data[i] = nonZero(random);
              }
            }
            ArrayRoundTrip.encodeAndVerify(data);
          }
        }
      }
    }
  }


  private static double nonZeroPercent(byte[] data) {
    int count = 0;
    for (byte b : data) {
      if (b != 0) {
        count++;
      }
    }
    return count * 100.0 / data.length;
  }

  /**
   * Returns a random non-zero byte.
   */
  private static byte nonZero(Random random) {
    return (byte) (1 + random.nextInt(255));
  }


  /**
   * Generates the test data.
   */
  @FunctionalInterface
  interface Generator {

    Generator ALL_ZERO = (size, random) -> new byte[size];

    Generator DENSE = (size, random) -> {
      byte[] data = new byte[size];
      for (int i = 0; i < size; i++) {
        data[i] = nonZero(random);
      }
      return data;
    };

    Generator ALTERNATING = (size, random) -> {
      byte[] data = new byte[size];
      for (int i = 0; i < size; i += 2) {
        data[i] = nonZero(random);
      }
      return data;
    };

    Generator ONE_OF_THREE = (size, random) -> {
      byte[] data = new byte[size];
      for (int i = 0; i < size; i += 3) {
        data[i] = nonZero(random);
      }
      return data;
    };

    /**
     * Uniformly distributed non-zero bytes.
     *
     * @param density the probability of a byte being non-zero
     * @return the generator
     */
    static Generator random(double density) {
      return (size, random) -> {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
          if (random.nextDouble() < density) {
            data[i] = nonZero(random);
          }
        }
        return data;
      };
    }

    /**
     * Runs of non-zero bytes at the start of each period.
     *
     * @param runLength the length of a non-zero run
     * @param period the distance between the starts of two runs
     * @return the generator
     */
    static Generator clusters(int runLength, int period) {
      return (size, random) -> {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
          if (i % period < runLength) {
            data[i] = nonZero(random);
          }
        }
        return data;
      };
    }

    /**
     * Non-zero bytes at the start, zeros behind (a partially filled buffer).
     *
     * @param fraction the used fraction of the array
     * @return the generator
     */
    static Generator head(double fraction) {
      return (size, random) -> {
        byte[] data = new byte[size];
        int used = (int) (size * fraction);
        for (int i = 0; i < used; i++) {
          data[i] = nonZero(random);
        }
        return data;
      };
    }

    /**
     * Zeros at the start, non-zero bytes behind.
     *
     * @param fraction the used fraction of the array
     * @return the generator
     */
    static Generator tail(double fraction) {
      return (size, random) -> {
        byte[] data = new byte[size];
        int used = (int) (size * fraction);
        for (int i = size - used; i < size; i++) {
          data[i] = nonZero(random);
        }
        return data;
      };
    }

    /**
     * Generates the array.
     *
     * @param size the array length
     * @param random the random generator
     * @return the array
     */
    byte[] generate(int size, Random random);
  }

}
