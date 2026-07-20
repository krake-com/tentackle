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

package org.tentackle.fx.translate;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

/**
 * Unit tests for the identity translators
 * ({@link StringStringTranslator}, {@link BooleanBooleanTranslator}, {@link CharArrayCharArrayTranslator}).
 */
@SuppressWarnings("missing-explicit-ctor")
public class IdentityTranslatorTest {

  @Test
  public void testStringIdentity() {
    StringStringTranslator translator = new StringStringTranslator(null);
    assertEquals(translator.toView("hello"), "hello");
    assertEquals(translator.toModel("hello"), "hello");
    assertNull(translator.toView(null));
    assertNull(translator.toModel(null));
  }

  @Test
  public void testBooleanIdentity() {
    BooleanBooleanTranslator translator = new BooleanBooleanTranslator(null);
    assertEquals(translator.toView(Boolean.TRUE), Boolean.TRUE);
    assertEquals(translator.toModel(Boolean.FALSE), Boolean.FALSE);
    assertNull(translator.toView(null));
    assertNull(translator.toModel(null));
  }

  @Test
  public void testCharArrayIdentityReturnsSameReference() {
    CharArrayCharArrayTranslator translator = new CharArrayCharArrayTranslator(null);
    char[] secret = { 's', 'e', 'c', 'r', 'e', 't' };
    // identity: must not copy, the very same array is returned
    assertSame(translator.toView(secret), secret);
    assertSame(translator.toModel(secret), secret);
    assertNull(translator.toView(null));
    assertNull(translator.toModel(null));
  }

  @Test
  public void testDefaultsFromBaseClass() {
    StringStringTranslator translator = new StringStringTranslator(null);
    assertNull(translator.getComponent());
    assertFalse(translator.isLenient());
    assertFalse(translator.isMappingIncomplete());
    assertFalse(translator.isModelModified());
    assertFalse(translator.needsToModelTwice());

    translator.setLenient(true);
    org.testng.Assert.assertTrue(translator.isLenient());
    // saveModelValue is a no-op and must not throw
    translator.saveModelValue("anything");
  }

}
