/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.fx.translate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Tests for number translator.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class NumberTranslatorTest {

  private static final Logger log = LoggerFactory.getLogger(NumberTranslatorTest.class);

  @Test
  public void testInteger() {
    ValueStringTranslator<Integer> translator = new IntegerStringTranslator(null);
    String validChars = translator.getValidChars();
    assertEquals(validChars, "0123456789.,-");    // tests run in en_US locale

    // test back and forth with other colors
    for (Integer value: new Integer[] { 0, 1, -10, 100, 1000, -1234 }) {
      String view = translator.toView(value);
      Integer model = translator.toModel(view);
      assertEquals(model, value);
    }
  }

  @Test
  public void testDouble() {
    ValueStringTranslator<Double> translator = new DoubleStringTranslator(null);

    // test back and forth with other colors
    for (Double value: new Double[] { 0.9, 1.0, -10.0, 100.6, 1000.1, -1234.9 }) {
      String view = translator.toView(value);
      Double model = translator.toModel(view);
      assertEquals(model, value);
    }
  }

  @Test
  public void testLong() {
    ValueStringTranslator<Long> translator = new LongStringTranslator(null);
    assertEquals(translator.getValidChars(), "0123456789.,-");    // tests run in en_US locale

    for (Long value: new Long[] { 0L, 1L, -10L, 1000L, -1234L, 10_000_000_000L, Long.MIN_VALUE, Long.MAX_VALUE }) {
      String view = translator.toView(value);
      Long model = translator.toModel(view);
      assertEquals(model, value);
    }
  }

  @Test
  public void testByte() {
    ValueStringTranslator<Byte> translator = new ByteStringTranslator(null);
    assertEquals(translator.getValidChars(), "0123456789.,-");

    for (Byte value: new Byte[] { 0, 1, -10, 100, Byte.MIN_VALUE, Byte.MAX_VALUE }) {
      String view = translator.toView(value);
      Byte model = translator.toModel(view);
      assertEquals(model, value);
    }
  }

  @Test
  public void testShort() {
    ValueStringTranslator<Short> translator = new ShortStringTranslator(null);
    assertEquals(translator.getValidChars(), "0123456789.,-");

    for (Short value: new Short[] { 0, 1, -10, 1000, Short.MIN_VALUE, Short.MAX_VALUE }) {
      String view = translator.toView(value);
      Short model = translator.toModel(view);
      assertEquals(model, value);
    }
  }

  @Test
  public void testFloat() {
    ValueStringTranslator<Float> translator = new FloatStringTranslator(null);

    for (Float value: new Float[] { 0.9f, 1.0f, -10.0f, 100.6f, -1234.9f }) {
      String view = translator.toView(value);
      Float model = translator.toModel(view);
      assertEquals(model, value);
    }
  }

  @Test
  public void testIntegerValidChars() {
    assertEquals(new IntegerStringTranslator(null).getValidChars(), "0123456789.,-");
  }

  @Test
  public void testNullRoundTrip() {
    // all number translators must map null to null in both directions
    ValueStringTranslator<?>[] translators = {
        new IntegerStringTranslator(null),
        new LongStringTranslator(null),
        new ByteStringTranslator(null),
        new ShortStringTranslator(null),
        new DoubleStringTranslator(null),
        new FloatStringTranslator(null)
    };
    for (ValueStringTranslator<?> translator : translators) {
      assertNull(translator.toView(null), translator.getClass().getSimpleName() + ".toView(null)");
      assertNull(translator.toModel(null), translator.getClass().getSimpleName() + ".toModel(null)");
    }
  }

}
