/**
 * Tentackle - http://www.tentackle.org
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


package org.tentackle.misc;

import org.testng.Assert;
import org.testng.annotations.Test;


/**
 * Tests the string helper.
 *
 * @author harald
 */
public class StringHelperTest {

  @Test
  public void testToAsciiLetterOrDigit() {
    Assert.assertEquals("Aepfel_und_B_irnen", StringHelper.toAsciiLetterOrDigit("Äpfel-und/>?B%irnen"));
  }

  @Test
  public void testGetContinuedLine() {
    String line1 = "this is not a continued line\\\\";
    String prefix2 = "this is a continued line\\\\";
    String line2 = prefix2 + "\\";
    Assert.assertNull(StringHelper.getContinuedLine(line1));
    Assert.assertEquals(StringHelper.getContinuedLine(line2), prefix2);
  }

  @Test
  public void testNormalizer() {
    Assert.assertEquals(StringHelper.normalize(" Mülleralia-Intercom & Co.KG   , GREEN"), "MUELERALIAINTERKOMKOK,KREN");
  }

  @Test
  public void firstLine() {
    Assert.assertEquals(StringHelper.firstLine("Blah\nBlue"), "Blah");
  }

  @Test
  public void trimRight() {
    Assert.assertEquals(StringHelper.trimRight("/cut/this//", '/'), "/cut/this");
    Assert.assertEquals(StringHelper.trimRight("///", '/'), "");
  }

  @Test
  public void trimLeft() {
    Assert.assertEquals(StringHelper.trimLeft("//cut/this/", '/'), "cut/this/");
    Assert.assertEquals(StringHelper.trimLeft("///", '/'), "");
  }

  @Test
  public void fillLeft() {
    Assert.assertEquals(StringHelper.fillLeft("yyxxxy", 10, 'y'), "yyyyyyxxxy");
    Assert.assertEquals(StringHelper.fillLeft("xxxxxxxxxxxx", 10, 'y'), "xxxxxxxxxxxx");
    Assert.assertEquals(StringHelper.fillLeft(null, 0, 'y'), "");
  }

  @Test
  public void fillRight() {
    Assert.assertEquals(StringHelper.fillRight("yxxxyy", 10, 'y'), "yxxxyyyyyy");
    Assert.assertEquals(StringHelper.fillRight("xxxxxxxxxxxx", 10, 'y'), "xxxxxxxxxxxx");
    Assert.assertEquals(StringHelper.fillRight(null, 0, 'y'), "");
  }

}
