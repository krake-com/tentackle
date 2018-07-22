/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.fx.convert;

import javafx.scene.paint.Color;
import org.testng.annotations.Test;

import org.tentackle.fx.translate.StringColorTranslator;

import static org.testng.Assert.assertEquals;

/**
 *
 * @author harald
 */
public class StringColorTranslatorTest {

  @Test
  public void testTranslate() {
    StringColorTranslator conv = new StringColorTranslator(null);
    Color c = Color.WHITE;
    String s = conv.toModel(c);
    assertEquals(s, "0xffffffff");
    assertEquals(conv.toView(s), c);

    // test back and forth with other colors
    for (Color color : new Color[] { Color.BEIGE, Color.AQUA, Color.BURLYWOOD, Color.GREENYELLOW,
                                     new Color(0.312, 0.477, 0.555, 1.0)}) {
      String str = conv.toModel(color);
      // toString() because Color.equals compares floats
      assertEquals(conv.toView(str).toString(), color.toString());
    }
  }

}
