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
 */

package org.tentackle.common;

import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.*;


/**
 * String helper test.
 *
 * @author harald
 */
public class StringHelperTest {

  @Test
  public void testJavaNames() {
    assertTrue(StringHelper.isValidJavaClassName("org.tentackle.common.Blah"));
    assertTrue(StringHelper.isValidJavaClassName("Blah"));
    assertTrue(StringHelper.isValidJavaClassName("Blah1"));
    assertFalse(StringHelper.isValidJavaClassName("0Blah"));
    assertFalse(StringHelper.isValidJavaClassName("blah"));
    assertFalse(StringHelper.isValidJavaClassName("org.tentackle.4common.Blah"));

    List<String> parts = StringHelper.split(" 'part \\\\one' , '\" part two\" '", " ,");
    assertEquals(parts.size(), 2);
    assertEquals(parts.get(0), "'part \\one'");
    assertEquals(parts.get(1), "'\" part two\" '");

    parts = StringHelper.split("FlowPaneBuilder = FlowPane", ":= ");
    assertEquals(parts.size(), 2);
    assertEquals(parts.get(0), "FlowPaneBuilder");
    assertEquals(parts.get(1), "FlowPane");
  }

  @Test
  public void testToAsciiLetterOrDigit() {
    assertEquals("Aepfel_und_B_irnen", StringHelper.toAsciiLetterOrDigit("Äpfel-und/>?B%irnen"));
  }

  @Test
  public void testGetContinuedLine() {
    String line1 = "this is not a continued line\\\\";
    String prefix2 = "this is a continued line\\\\";
    String line2 = prefix2 + "\\";
    assertNull(StringHelper.getContinuedLine(line1));
    assertEquals(StringHelper.getContinuedLine(line2), prefix2);
  }

  @Test
  public void testNormalizer() {
    assertEquals(StringHelper.normalize(" Mülleralia-Intercom & Co.KG   , GREEN"), "MUELERALIAINTERKOMKOK,KREN");
  }

  @Test
  public void firstLine() {
    assertEquals(StringHelper.firstLine("Blah\nBlue"), "Blah");
  }

  @Test
  public void trimRight() {
    assertEquals(StringHelper.trimRight("/cut/this//", '/'), "/cut/this");
    assertEquals(StringHelper.trimRight("///", '/'), "");
  }

  @Test
  public void trimLeft() {
    assertEquals(StringHelper.trimLeft("//cut/this/", '/'), "cut/this/");
    assertEquals(StringHelper.trimLeft("///", '/'), "");
  }

  @Test
  public void fillLeft() {
    assertEquals(StringHelper.fillLeft("yyxxxy", 10, 'y'), "yyyyyyxxxy");
    assertEquals(StringHelper.fillLeft("xxxxxxxxxxxx", 10, 'y'), "xxxxxxxxxxxx");
    assertEquals(StringHelper.fillLeft(null, 0, 'y'), "");
  }

  @Test
  public void fillRight() {
    assertEquals(StringHelper.fillRight("yxxxyy", 10, 'y'), "yxxxyyyyyy");
    assertEquals(StringHelper.fillRight("xxxxxxxxxxxx", 10, 'y'), "xxxxxxxxxxxx");
    assertEquals(StringHelper.fillRight(null, 0, 'y'), "");
  }
}
