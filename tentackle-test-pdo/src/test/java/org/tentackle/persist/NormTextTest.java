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

package org.tentackle.persist;

import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.common.StringNormalizer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link NormText} LIKE-pattern helper.
 * <p>
 * {@code NormText} turns a user's search input into a pattern ready for a
 * {@code LIKE} / {@code NOT LIKE} expression against a normtext column. This
 * is a pure unit test: it exercises {@code NormText}'s own contract (wildcard
 * wrapping, inversion detection, and the "matches all" state) and defers the
 * phonetic reduction itself to {@link StringNormalizer}.
 *
 * @author harald
 */
public class NormTextTest {

  /**
   * Wraps the normalized form of {@code input} the way {@code NormText} does.
   *
   * @param input the raw input
   * @return the expected LIKE pattern
   */
  private static String expectedPattern(String input) {
    return "%" + StringNormalizer.getInstance().normalize(input) + "%";
  }


  @Test(groups = "local")
  public void testNullInputMatchesAll() {
    Reporter.log("NormTextTest.testNullInputMatchesAll <br>", true);

    NormText normText = new NormText(null);
    assertTrue(normText.isMatchingAll(), "null input must match all");
    assertNull(normText.getPattern(), "matching-all pattern must be null");
    assertFalse(normText.isInverted(), "null input must not be inverted");
  }


  @Test(groups = "local")
  public void testEmptyInputMatchesAll() {
    Reporter.log("NormTextTest.testEmptyInputMatchesAll <br>", true);

    NormText normText = new NormText("");
    assertTrue(normText.isMatchingAll(), "empty input must match all");
    assertNull(normText.getPattern(), "matching-all pattern must be null");
    assertFalse(normText.isInverted(), "empty input must not be inverted");
  }


  @Test(groups = "local")
  public void testPunctuationOnlyMatchesAll() {
    Reporter.log("NormTextTest.testPunctuationOnlyMatchesAll <br>", true);

    // input that normalizes to the empty string (only wildcards/whitespace)
    NormText normText = new NormText("  %_  ");
    assertTrue(normText.isMatchingAll(), "input that normalizes to empty must match all");
    assertNull(normText.getPattern(), "matching-all pattern must be null");
    assertFalse(normText.isInverted(), "leading whitespace must not invert");
  }


  @Test(groups = "local")
  public void testPlainWord() {
    Reporter.log("NormTextTest.testPlainWord <br>", true);

    NormText normText = new NormText("Mueller");
    assertFalse(normText.isMatchingAll(), "a real word must not match all");
    assertFalse(normText.isInverted(), "a plain word must not be inverted");
    assertEquals(normText.getPattern(), expectedPattern("Mueller"), "pattern must be the wrapped normtext");
  }


  @Test(groups = "local")
  public void testPatternIsWrappedWithWildcards() {
    Reporter.log("NormTextTest.testPatternIsWrappedWithWildcards <br>", true);

    String pattern = new NormText("Mueller").getPattern();
    assertTrue(pattern.startsWith("%"), "pattern must start with a wildcard");
    assertTrue(pattern.endsWith("%"), "pattern must end with a wildcard");
    assertTrue(pattern.length() > 2, "pattern must carry the normalized content between the wildcards");
  }


  @Test(groups = "local")
  public void testInvertedWithExclamationMark() {
    Reporter.log("NormTextTest.testInvertedWithExclamationMark <br>", true);

    NormText normText = new NormText("!Mueller");
    assertTrue(normText.isInverted(), "leading '!' must select NOT LIKE");
    assertFalse(normText.isMatchingAll(), "there is still content to match");
    // the leading '!' is stripped by normalization, so the pattern matches the plain word
    assertEquals(normText.getPattern(), expectedPattern("!Mueller"), "pattern must ignore the leading '!'");
    assertEquals(normText.getPattern(), new NormText("Mueller").getPattern(),
                 "inverted and plain form must share the same pattern");
  }


  @Test(groups = "local")
  public void testInvertedWithMinusSign() {
    Reporter.log("NormTextTest.testInvertedWithMinusSign <br>", true);

    NormText normText = new NormText("-Mueller");
    assertTrue(normText.isInverted(), "leading '-' must select NOT LIKE");
    assertFalse(normText.isMatchingAll(), "there is still content to match");
    assertEquals(normText.getPattern(), expectedPattern("-Mueller"), "pattern must ignore the leading '-'");
  }


  @Test(groups = "local")
  public void testInvertedButEmptyStillMatchesAll() {
    Reporter.log("NormTextTest.testInvertedButEmptyStillMatchesAll <br>", true);

    // a lone '!' is detected as inverted but normalizes to empty
    NormText normText = new NormText("!");
    assertTrue(normText.isInverted(), "lone '!' is still detected as inverted");
    assertTrue(normText.isMatchingAll(), "nothing remains after normalization, so it matches all");
    assertNull(normText.getPattern(), "matching-all pattern must be null");
  }

}
