/*
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

package org.tentackle.fx.translate;

import java.util.function.Function;
import org.tentackle.fx.FxTextComponent;
import org.tentackle.fx.ValueTranslatorService;
import org.tentackle.misc.FormatHelper;

/**
 * Byte translator.
 *
 * @author harald
 */
@ValueTranslatorService(modelClass = Byte.class, viewClass = String.class)
public class ByteStringTranslator extends NumberStringTranslator<Byte> {

  /**
   * Creates a translator.
   *
   * @param component the text component
   */
  public ByteStringTranslator(FxTextComponent component) {
    super(component);
  }

  @Override
  public Function<Byte, String> toViewFunction() {
    return v -> v == null ? null : getFormat().format(v);
  }

  @Override
  public Function<String, Byte> toModelFunction() {
    return s -> s == null ? null : parse(s).byteValue();
  }

  @Override
  public String getDefaultPattern() {
    return FormatHelper.getIntegerPattern();
  }

}
