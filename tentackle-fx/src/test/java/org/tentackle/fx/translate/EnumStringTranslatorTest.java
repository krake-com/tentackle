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
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

/**
 * Unit tests for {@link EnumStringTranslator}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class EnumStringTranslatorTest {

  private enum Color {
    RED, GREEN, BLUE;

    @Override
    public String toString() {
      // deliberately different from name() to make sure translation is based on toString()
      return name().toLowerCase();
    }
  }

  private EnumStringTranslator<Color> newTranslator() {
    return new EnumStringTranslator<>(null, Color.class);
  }

  @Test
  public void testToView() {
    EnumStringTranslator<Color> translator = newTranslator();
    assertEquals(translator.toView(Color.RED), "red");
    assertEquals(translator.toView(Color.BLUE), "blue");
    assertNull(translator.toView(null));
  }

  @Test
  public void testToModelResolvesConstant() {
    EnumStringTranslator<Color> translator = newTranslator();
    assertSame(translator.toModel("green"), Color.GREEN);
    assertSame(translator.toModel("blue"), Color.BLUE);
  }

  @Test
  public void testToModelReturnsNullForUnknownOrNull() {
    EnumStringTranslator<Color> translator = newTranslator();
    assertNull(translator.toModel(null));
    assertNull(translator.toModel("purple"), "unknown label must map to null");
    assertNull(translator.toModel("RED"), "matching is on toString(), not name()");
  }

  @Test
  public void testRoundTrip() {
    EnumStringTranslator<Color> translator = newTranslator();
    for (Color c : Color.values()) {
      assertSame(translator.toModel(translator.toView(c)), c);
    }
  }

}
