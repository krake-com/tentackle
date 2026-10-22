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

package org.tentackle.buildsupport;

import org.testng.annotations.Test;

import javax.lang.model.element.Modifier;
import java.util.StringTokenizer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link AnnotationProcessingHelper}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class AnnotationProcessingHelperTest {

  @Test
  public void escapeTokenReturnsNullForNull() {
    assertNull(AnnotationProcessingHelper.escapeToken(null));
  }

  @Test
  public void unescapeTokenReturnsNullForNull() {
    assertNull(AnnotationProcessingHelper.unescapeToken(null));
  }

  @Test
  public void escapesBlanksAndBackslashes() {
    assertEquals(AnnotationProcessingHelper.escapeToken("? extends Foo"), "?\\sextends\\sFoo");
    assertEquals(AnnotationProcessingHelper.escapeToken("a\\b"), "a\\\\b");
    assertEquals(AnnotationProcessingHelper.escapeToken("plain"), "plain");
    assertEquals(AnnotationProcessingHelper.escapeToken(""), "");
  }

  @Test
  public void unescapesBackslashesAndBlanks() {
    assertEquals(AnnotationProcessingHelper.unescapeToken("?\\sextends\\sFoo"), "? extends Foo");
    assertEquals(AnnotationProcessingHelper.unescapeToken("a\\\\b"), "a\\b");
  }

  @Test
  public void unescapeTokenLeavesTokensWithoutEscapeCharUnchanged() {
    // pre-escaping info files never contain a backslash, so nothing must be touched
    String noBackslash = "java.util.List<java.lang.String>";
    assertEquals(AnnotationProcessingHelper.unescapeToken(noBackslash), noBackslash);
  }

  @Test
  public void escapeUnescapeRoundTrip() {
    String original = "@Nullable ? super com.example.Item\\Thing";
    String escaped = AnnotationProcessingHelper.escapeToken(original);
    assertEquals(AnnotationProcessingHelper.unescapeToken(escaped), original);
  }

  @Test
  public void readModifiersParsesKnownModifiersAndIgnoresUnknownTokens() {
    Modifier[] modifiers = AnnotationProcessingHelper.readModifiers(
        new StringTokenizer("public final bogus static"));

    assertEquals(modifiers.length, 3);
    assertTrue(AnnotationProcessingHelper.isModifierSet(Modifier.PUBLIC, modifiers));
    assertTrue(AnnotationProcessingHelper.isModifierSet(Modifier.FINAL, modifiers));
    assertTrue(AnnotationProcessingHelper.isModifierSet(Modifier.STATIC, modifiers));
    assertFalse(AnnotationProcessingHelper.isModifierSet(Modifier.ABSTRACT, modifiers));
  }

  @Test
  public void readModifiersOfEmptyTokenizerIsEmptyArray() {
    Modifier[] modifiers = AnnotationProcessingHelper.readModifiers(new StringTokenizer(""));
    assertEquals(modifiers.length, 0);
  }

  @Test
  public void isModifierSetHandlesNullAndEmptyList() {
    assertFalse(AnnotationProcessingHelper.isModifierSet(Modifier.PUBLIC, null));
    assertFalse(AnnotationProcessingHelper.isModifierSet(Modifier.PUBLIC, new Modifier[0]));
  }

  @Test
  public void objectArrayToStringJoinsWithSeparator() {
    assertEquals(AnnotationProcessingHelper.objectArrayToString(new Object[] { "a", 1, "c" }, ", "), "a, 1, c");
  }

  @Test
  public void objectArrayToStringOfEmptyOrNullArrayIsEmptyString() {
    assertEquals(AnnotationProcessingHelper.objectArrayToString(new Object[0], ", "), "");
    assertEquals(AnnotationProcessingHelper.objectArrayToString(null, ", "), "");
  }

  @Test
  public void objectArrayToStringOfSingleElementOmitsSeparator() {
    assertEquals(AnnotationProcessingHelper.objectArrayToString(new Object[] { "solo" }, ", "), "solo");
  }

}
