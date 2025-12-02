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

}
