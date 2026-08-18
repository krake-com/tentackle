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

package org.tentackle.common;

import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link BMoney} and {@link DMoney}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class MoneyTest {

  @Test
  public void testScaleAndRounding() {
    // the double constructor rounds HALF_UP using the canonical string representation,
    // so the classic 2.675 -> 2.68 (not 2.67) holds
    assertEquals(new BMoney(2.675, 2).toString(), "2.68");
    assertEquals(new BMoney(1.005, 2).toString(), "1.01");
    assertEquals(new BMoney(1.234, 2).toString(), "1.23");

    // the string constructor rounds the same way
    assertEquals(new BMoney("1.235", 2).toString(), "1.24");

    // the fixed scale is retained
    assertEquals(new BMoney(1.0, 4).scale(), 4);
    assertEquals(new BMoney(2).toString(), "0.00");
  }

  @Test
  public void testBigIntegerConstructor() {
    // the scale places the comma within the given integer
    assertEquals(new BMoney(BigInteger.valueOf(12345), 2).toString(), "123.45");
  }

  @Test
  public void testRescaleConstructor() {
    // value-preserving rescale (rounding HALF_UP)
    BMoney m = new BMoney("1.5", 1);
    assertEquals(new BMoney(m, 2).toString(), "1.50");
    assertEquals(new BMoney(new BMoney("1.239", 3), 2).toString(), "1.24");
  }

  @Test
  public void testSignPredicates() {
    assertTrue(new BMoney("-0.01", 2).isNegative());
    assertFalse(new BMoney("-0.01", 2).isPositive());
    assertFalse(new BMoney("-0.01", 2).isZero());

    assertTrue(new BMoney("0.00", 2).isZero());
    assertFalse(new BMoney("0.00", 2).isPositive());
    assertFalse(new BMoney("0.00", 2).isNegative());

    assertTrue(new BMoney("0.01", 2).isPositive());
  }

  @Test
  public void testArithmetic() {
    BMoney a = new BMoney("10.00", 2);
    BMoney b = new BMoney("2.50", 2);

    assertEquals(a.add(b).toString(), "12.50");
    assertEquals(a.subtract(b).toString(), "7.50");
    assertEquals(a.multiply(3).toString(), "30.00");
    assertEquals(a.divide(4.0).toString(), "2.50");
    // divide(BMoney) yields a plain double ratio
    assertEquals(a.divide(b), 4.0, 0.0);
    assertEquals(a.invert().toString(), "-10.00");
    assertEquals(a.invert().absolute().toString(), "10.00");
    assertEquals(a.absolute().toString(), "10.00");

    // the operands remain unchanged (BMoney is effectively immutable)
    assertEquals(a.toString(), "10.00");
    assertEquals(b.toString(), "2.50");
  }

  @Test
  public void testMixedScaleArithmetic() {
    // the operand is rescaled to the scale of this value, the result keeps that scale
    BMoney a = new BMoney("1.00", 2);
    BMoney b = new BMoney("2.500", 3);

    assertEquals(a.add(b).toString(), "3.50");
    assertEquals(a.add(b).scale(), 2);
    assertEquals(a.subtract(b).toString(), "-1.50");

    // ... and the other way round: the wider scale wins if it is the receiver's
    assertEquals(new BMoney("1.000", 3).add(new BMoney("2.50", 2)).toString(), "3.500");
    assertEquals(new BMoney("1.000", 3).subtract(new BMoney("2.50", 2)).toString(), "-1.500");

    // rescaling the operand rounds HALF_UP
    assertEquals(new BMoney("1.00", 2).add(new BMoney("0.005", 3)).toString(), "1.01");
    assertEquals(new BMoney("1.00", 2).add(new BMoney("0.004", 3)).toString(), "1.00");

    // the operands remain unchanged
    assertEquals(a.toString(), "1.00");
    assertEquals(b.toString(), "2.500");
  }

  @Test
  public void testMixedScaleArithmeticForDMoney() {
    DMoney a = new DMoney("1.00", 2);

    assertEquals(a.add(new DMoney("2.500", 3)).toString(), "3.50");
    assertEquals(a.subtract(new DMoney("2.500", 3)).toString(), "-1.50");
    assertTrue(a.add(new DMoney("2.500", 3)) instanceof DMoney);

    // BMoney operands are accepted as well
    assertEquals(a.add(new BMoney("2.500", 3)).toString(), "3.50");
  }

  @Test
  public void testSmallestPositive() {
    assertEquals(new BMoney(2).smallestPositive().toString(), "0.01");
    assertEquals(new BMoney(3).smallestPositive().toString(), "0.001");
    assertEquals(new BMoney(0).smallestPositive().toString(), "1");
  }

  @Test
  public void testToWords() {
    // digits after the comma are ignored
    assertEquals(new BMoney("234.77", 2).toWords(), "two three four");
    assertEquals(new BMoney("0.99", 2).toWords(), "zero");
    // the sign is ignored as well
    assertEquals(new BMoney("-5.00", 2).toWords(), "five");
  }

  @Test
  public void testEqualsIsScaleSensitiveButCompareToIsNot() {
    BMoney oneScale1 = new BMoney("1.0", 1);
    BMoney oneScale2 = new BMoney("1.00", 2);
    // BigDecimal.equals compares scale as well
    assertNotEquals(oneScale2, oneScale1);
    // ... whereas compareTo is value based
    assertEquals(oneScale1.compareTo(oneScale2), 0);
  }

  @Test
  public void testClone() {
    BMoney m = new BMoney("42.42", 2);
    BMoney clone = m.clone();
    assertEquals(clone, m);
    assertNotSame(clone, m);
  }

  @Test
  public void testDMoneyBigDecimalValue() {
    // the decimal is stored with scale 0 (value moved left by scale digits when read back)
    DMoney m = new DMoney("12.34", 2);
    assertEquals(m.bigDecimalValue(), new BigDecimal("1234"));
    assertEquals(m.bigDecimalValue().scale(), 0);
  }

  @Test
  public void testDMoneyArithmeticReturnsDMoney() {
    DMoney a = new DMoney("10.00", 2);
    DMoney b = new DMoney("2.50", 2);

    // the overridden operations keep the DMoney runtime type (covariant returns)
    assertTrue(a.add(b) instanceof DMoney);
    assertTrue(a.subtract(b) instanceof DMoney);
    assertTrue(a.multiply(2) instanceof DMoney);
    assertTrue(a.divide(2.0) instanceof DMoney);
    assertTrue(a.invert() instanceof DMoney);
    assertTrue(a.absolute() instanceof DMoney);
    assertTrue(a.smallestPositive() instanceof DMoney);

    assertEquals(a.add(b).toString(), "12.50");
    assertEquals(a.invert().toString(), "-10.00");
    assertEquals(a.smallestPositive().toString(), "0.01");
  }

  @Test
  public void testDMoneyIsBMoney() {
    assertTrue(new DMoney("1.00", 2) instanceof BMoney);
  }

}
