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

import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.trip.impl.DefaultOpCode;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@SuppressWarnings("missing-explicit-ctor")
public class OpCodeTest {

  @Test
  public void assertCodesAreUnique() {
    Set<Integer> codes = new HashSet<>();
    int minCode = Integer.MAX_VALUE;
    int maxCode = Integer.MIN_VALUE;
    Collection<OpCode> allOpCodes = TripFactory.getInstance().getAllOpCodes();
    for (OpCode opCode : allOpCodes) {
      int code = opCode.toExternal();
      minCode = Math.min(minCode, code);
      maxCode = Math.max(maxCode, code);
      assertEquals(opCode, TripFactory.getInstance().getOpCode(code));
      if (!codes.add(code)) {
        fail("duplicate op code: " + String.format("0x%02X", code));
      }
      if (opCode.isFixedType()) {
        assertTrue(opCode.getFixedType().isFinal());
        assertFalse(opCode.isDictionaryBased());
      }
      else if (opCode.isDictionaryBased()) {
        assertFalse(opCode.isFixedType());
      }
    }
    Reporter.log("count=" + allOpCodes.size() + ", min=" + String.format("0x%02X", minCode) +
                 ", max=" + String.format("0x%02X", maxCode) + "<br>", true);
    assertEquals(minCode, 0x00);
    assertEquals(maxCode, 0x83);   // from AdditionalOpCode!
  }

  @Test
  public void expectInvalidOpcode() {
    boolean exceptionThrown = false;
    try {
      OpCode opCode = TripFactory.getInstance().getOpCode(0x84);
      fail("unexpected valid op code" + opCode);
    }
    catch (TripRuntimeException tx) {
      assertEquals(tx.getMessage(), "invalid op code: 0x84");
      exceptionThrown = true;
    }
    assertTrue(exceptionThrown);
  }

  @Test
  public void testReferenceCodes() {
    // see ReferenceTripType implementation
    assertEquals((DefaultOpCode.REFERENCE.getCode() & 0x7), 0);
    assertEquals((DefaultOpCode.REFERENCE1.getCode() & 0x7), 1);
    assertEquals((DefaultOpCode.REFERENCE2.getCode() & 0x7), 2);
    assertEquals((DefaultOpCode.REFERENCE3.getCode() & 0x7), 3);
    assertEquals((DefaultOpCode.REFERENCE_SENT).getCode() & 0x7, 4);
    assertEquals((DefaultOpCode.REFERENCE1_SENT).getCode() & 0x7, 5);
    assertEquals((DefaultOpCode.REFERENCE2_SENT).getCode() & 0x7, 6);
    assertEquals((DefaultOpCode.REFERENCE3_SENT).getCode() & 0x7, 7);
  }

}
