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

package org.tentackle.fx.translate;

import javafx.scene.paint.Color;

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.ValueTranslatorService;

import java.util.function.Function;

/**
 * A string/color translator.<br>
 * Useful for color pickers bound to a string in the model.
 *
 * @author harald
 */
@ValueTranslatorService(modelClass = String.class, viewClass = Color.class)
public class StringColorTranslator extends AbstractValueTranslator<String, Color> {

  /**
   * The fixed color string length.
   * Ex.: "0xffffffff" for WHITE.
   */
  public static final int COLOR_LENGTH = 10;

  /**
   * Creates the translator.
   *
   * @param component the component
   */
  public StringColorTranslator(FxComponent component) {
    super(component);
  }

  @Override
  public Function<String, Color> toViewFunction() {
    return m -> m == null ? null : Color.valueOf(m);
  }

  @Override
  public Function<Color, String> toModelFunction() {
    return c -> c == null ? null : c.toString();
  }

}
