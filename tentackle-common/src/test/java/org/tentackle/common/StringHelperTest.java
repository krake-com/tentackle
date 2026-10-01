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

import org.testng.annotations.Test;

import java.util.List;
import java.util.Properties;
import java.util.function.Function;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import java.io.IOException;

/**
 * String helper test.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
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

  @Test
  public void evaluateVariables() {
    Properties properties = new Properties();
    Function<String,String> variables = properties::getProperty;
    properties.setProperty("alpha", "100");
    properties.setProperty("beta", "200");
    properties.setProperty("zet?a", "ZZZ");
    properties.setProperty("delt}a", "DDD");
    properties.setProperty("delt{a", "ddd");
    properties.setProperty("empty", " \t\n\r");
    properties.setProperty("gamma", StringHelper.evaluate("${alpha}${beta}", variables));
    assertEquals(StringHelper.evaluate("bla=${alpha}xx", variables), "bla=100xx");
    assertEquals(StringHelper.evaluate("bla=$${alpha}xx", variables), "bla=$100xx");
    assertEquals(StringHelper.evaluate("bla=${alph}xx", variables), "bla=${alph}xx");
    assertEquals(StringHelper.evaluate("bla=@{alph}xx", variables), "bla=@{alph}xx");
    assertEquals(StringHelper.evaluate("bla=${alph}xx$", variables), "bla=${alph}xx$");
    assertEquals(StringHelper.evaluate("bla=@{alph}xx$", variables), "bla=@{alph}xx$");
    assertEquals(StringHelper.evaluate("bla=${alph? y y}xx", variables), "bla= y yxx");
    assertEquals(StringHelper.evaluate("bla=${alph! y y}xx", variables), "bla= y yxx");
    assertEquals(StringHelper.evaluate("bla=${alph!}xx", variables), "bla=xx");
    assertEquals(StringHelper.evaluate("bla=${empty? y y}xx", variables), "bla= y yxx");
    assertEquals(StringHelper.evaluate("bla=${empty?}xx", variables), "bla=xx");
    assertEquals(StringHelper.evaluate("bla=${empty?}}xx", variables), "bla=}xx");
    assertEquals(StringHelper.evaluate("bla=${empty! y y}xx", variables), "bla= \t\n\rxx");
    assertEquals(StringHelper.evaluate("bla=${empty!}xx", variables), "bla= \t\n\rxx");
    assertEquals(StringHelper.evaluate("bla=${alph??y?y}xx", variables), "bla=?y?yxx");
    assertEquals(StringHelper.evaluate("bla=${alph?zzzz", variables), "bla=${alph?zzzz");
    assertEquals(StringHelper.evaluate("bla=${alph!zzzz", variables), "bla=${alph!zzzz");
    assertEquals(StringHelper.evaluate("bla=${zet\\?a}yy", variables), "bla=ZZZyy");
    assertEquals(StringHelper.evaluate("bla=${delt\\}a}yy", variables), "bla=DDDyy");
    assertEquals(StringHelper.evaluate("bla=${delt{a}yy", variables), "bla=dddyy");
    assertEquals(StringHelper.evaluate("bla=${delt\\{a}yy", variables), "bla=dddyy");
    assertEquals(StringHelper.evaluate("bla=${alpha}\\xx", variables), "bla=100xx");
    assertEquals(StringHelper.evaluate("bla=${alpha}\\\\xx", variables), "bla=100\\xx");
    assertEquals(StringHelper.evaluate("bla=${alpha}xx${beta}", variables), "bla=100xx200");
    assertEquals(StringHelper.evaluate("bla=${gamma}xx${beta}", variables), "bla=100200xx200");
    assertEquals(StringHelper.evaluate("bla=\\${gamma}xx", variables), "bla=${gamma}xx");
    assertEquals(StringHelper.evaluate("bla=\\@{gamma}xx", variables), "bla=@{gamma}xx");
    assertEquals(StringHelper.evaluate("bla=${gamma\\}xx", variables), "bla=${gamma}xx");
    assertEquals(StringHelper.evaluate("bl!a=@{gamma\\}xx", variables), "bl!a=@{gamma}xx");
    assertEquals(StringHelper.evaluate("bl?}a=@{gamma\\}xx", variables), "bl?}a=@{gamma}xx");

    // check nested variables
    properties.setProperty("omega", "a=${alpha}");
    assertEquals(StringHelper.evaluate("${omega}=a", variables), "a=100=a");
    properties.setProperty("lambda", "XX${omega}");
    assertEquals(StringHelper.evaluate("${lambda}ZZ", variables), "XXa=100ZZ");

    // detect recursion loop
    properties.setProperty("alpha", "KK${lambda}KK");
    assertEquals(StringHelper.evaluate("${lambda}ZZ", variables), "XXa=KKXX${omega}KKZZ");
    assertEquals(StringHelper.evaluate("${lambda}ZZ${omega}", variables), "XXa=KKXX${omega}KKZZa=KKXXa=${alpha}KK");

    // suppress nested evaluation
    assertEquals(StringHelper.evaluate("@{lambda}ZZ", variables), "XX${omega}ZZ");
  }


  @Test
  public void levenshteinTest() {
    assertEquals(StringHelper.levenshteinDistance("shot", "spot"), 1);
    assertEquals(StringHelper.levenshteinDistance("weight", "light"), 2);
    assertEquals(StringHelper.levenshteinDistance("release_pos_x", "x_release_loc"), 6);
  }


  @Test
  public void doubleQuotingTest() {
    String from = "This \" is to be quoted";
    assertEquals(StringHelper.toDoubleQuotes(from), "\"This \\\" is to be quoted\"");

    from = "This \" is\tto\nbe\u001Cquoted";
    assertEquals(StringHelper.toDoubleQuotes(from), "\"This \\\" is\\tto\\nbe quoted\"");

    from = "This \\\" is to be quoted";
    assertEquals(StringHelper.toDoubleQuotes(from), "\"This \\\\\\\" is to be quoted\"");
  }

  @Test
  public void countOccurrences() {
    assertEquals(StringHelper.countOccurrences(null, 'a'), 0);
    assertEquals(StringHelper.countOccurrences("", 'a'), 0);
    assertEquals(StringHelper.countOccurrences("a", 'a'), 1);
    assertEquals(StringHelper.countOccurrences("aXa", 'a'), 2);
    assertEquals(StringHelper.countOccurrences("XaX", 'a'), 1);
    assertEquals(StringHelper.countOccurrences("XXX", 'a'), 0);
  }

  @Test
  public void validJavaPackageNames() {
    assertTrue(StringHelper.isValidJavaPackageName("org.tentackle.common"));
    assertTrue(StringHelper.isValidJavaPackageName("a"));
    assertTrue(StringHelper.isValidJavaPackageName("org.my_app2"));
    assertTrue(StringHelper.isValidJavaPackageName("_internal.x"));
    assertFalse(StringHelper.isValidJavaPackageName(null));
    assertFalse(StringHelper.isValidJavaPackageName(""));
    assertFalse(StringHelper.isValidJavaPackageName("Foo.bar"));
    assertFalse(StringHelper.isValidJavaPackageName("org.Tentackle"));
    assertFalse(StringHelper.isValidJavaPackageName("-a"));
    assertFalse(StringHelper.isValidJavaPackageName("$x"));
    assertFalse(StringHelper.isValidJavaPackageName("4org"));
    assertFalse(StringHelper.isValidJavaPackageName("a."));
    assertFalse(StringHelper.isValidJavaPackageName(".a"));
    assertFalse(StringHelper.isValidJavaPackageName("a..b"));
    assertFalse(StringHelper.isValidJavaPackageName("org.int.x"));
    assertFalse(StringHelper.isValidJavaPackageName("org._"));
    assertFalse(StringHelper.isValidJavaClassName("Foo.Bar"));
    assertFalse(StringHelper.isValidJavaClassName("org..Bar"));
  }

  @Test
  public void stripEnclosingDoubleQuotes() {
    assertNull(StringHelper.stripEnclosingDoubleQuotes(null));
    assertEquals(StringHelper.stripEnclosingDoubleQuotes(""), "");
    assertEquals(StringHelper.stripEnclosingDoubleQuotes("\"\""), "");
    assertEquals(StringHelper.stripEnclosingDoubleQuotes("\"abc\""), "abc");
    assertEquals(StringHelper.stripEnclosingDoubleQuotes("abc"), "abc");
    // unpaired quotes are not enclosing
    assertEquals(StringHelper.stripEnclosingDoubleQuotes("\""), "\"");
    assertEquals(StringHelper.stripEnclosingDoubleQuotes("abc\""), "abc\"");
    assertEquals(StringHelper.stripEnclosingDoubleQuotes("\"abc"), "\"abc");
  }

  @Test
  public void readTextFromResourceWithImplicitCaller() throws IOException {
    // relative resource name: must be resolved against the caller's package, not StringHelper's
    String text = org.tentackle.common.impl.ResourceReader.readText();
    assertEquals(text, "caller relative\n");
  }

}
