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

import org.testng.annotations.Test;

import org.tentackle.trip.impl.DefaultTripStream;
import org.tentackle.trip.impl.EchoTransceiver;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the budget for memory allocated ahead of its data, i.e. arrays, which applies to authenticated clients as well.
 */
@SuppressWarnings("missing-explicit-ctor")
public class AllocationBudgetTest {

  private static final long LIMIT = 4L * 1024 * 1024;


  /**
   * Sends an object and reads it back.
   *
   * @param transceiver the transceiver
   * @param object the object to send
   * @param limit the allocation limit of the reading stream
   * @return the object read
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  private <T> T echo(EchoTransceiver transceiver, T object, long limit) {
    transceiver.reset();
    TripStream out = transceiver.getStream();
    TripType<T> type = out.getType((Class<T>) object.getClass());
    type.write(object, out);
    transceiver.echo();
    TripStream in = transceiver.getStream();
    in.setAllocationLimit(limit);
    OpCode opCode = in.getDeserializer().deserializeOpCode();
    if (type instanceof PredefinedArray arrayType) {
      return (T) arrayType.readWithType(in, opCode);
    }
    return type.read(in, opCode);
  }

  private <T> T echo(T object, long limit) {
    return echo(new EchoTransceiver(), object, limit);
  }

  private void assertRefused(Object object, long limit) {
    TripPolicyViolationException ex = expectThrows(TripPolicyViolationException.class, () -> echo(object, limit));
    assertTrue(ex.getMessage().contains("bytes per message"), ex.getMessage());
  }

  /**
   * Creates sparse double arrays.
   */
  private double[][] sparseMatrix(int rows, int columns) {
    double[][] matrix = new double[rows][columns];
    for (int i = 0; i < Math.min(rows, columns); i++) {
      matrix[i][i] = i + 1;
    }
    return matrix;
  }


  @Test
  void testSparseMatrixIsAccepted() {
    // the common case: about 8 MB, far below the default
    double[][] matrix = sparseMatrix(1000, 1000);
    double[][] result = echo(matrix, DefaultTripStream.getDefaultAllocationLimit());
    assertEquals(result, matrix);
  }

  @Test
  void testArraysOfArraysAreRefused() {
    // each array is moderate, but they add up to 16 MB
    assertRefused(sparseMatrix(20, 100_000), LIMIT);
    Object[] arrays = new Object[20];
    for (int i = 0; i < arrays.length; i++) {
      arrays[i] = new long[100_000];
    }
    assertRefused(arrays, LIMIT);
  }

  @Test
  void testSingleArrayIsRefused() {
    assertRefused(new long[1_000_000], LIMIT);
    assertRefused(new String[1_000_000], LIMIT);
    assertRefused(new Object[1_000_000], LIMIT);
    assertEquals(echo(new byte[1_000_000], LIMIT).length, 1_000_000);   // 1 byte per element
  }

  @Test
  void testBudgetIsPerMessage() {
    EchoTransceiver transceiver = new EchoTransceiver();
    assertEquals(echo(transceiver, new double[300_000], LIMIT).length, 300_000);
    transceiver.clear();    // the next message
    assertEquals(echo(transceiver, new double[300_000], LIMIT).length, 300_000);
    // same message: 4.8 MB in total
    expectThrows(TripPolicyViolationException.class, () -> echo(transceiver, new double[300_000], LIMIT));
  }

  @Test
  void testBackReferencesAreFree() {
    double[] shared = new double[300_000];
    Object[] arrays = { shared, shared, shared, shared };
    Object[] result = echo(arrays, LIMIT);
    assertSame(result[3], result[0]);
  }

  @Test
  void testUnlimited() {
    assertEquals(echo(sparseMatrix(20, 100_000), 0).length, 20);
  }

  @Test
  void testDefaultLimit() {
    long defaultLimit = DefaultTripStream.getDefaultAllocationLimit();
    assertEquals(defaultLimit, Long.getLong(DefaultTripStream.ALLOCATION_LIMIT_PROPERTY, DefaultTripStream.DEFAULT_ALLOCATION_LIMIT));
    try {
      DefaultTripStream.setDefaultAllocationLimit(-1);
      assertEquals(DefaultTripStream.getDefaultAllocationLimit(), 0);
    }
    finally {
      DefaultTripStream.setDefaultAllocationLimit(defaultLimit);
    }
  }

}
