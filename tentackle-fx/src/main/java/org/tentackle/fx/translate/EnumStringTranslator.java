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

import org.tentackle.fx.FxTextComponent;
import org.tentackle.fx.ValueTranslatorService;

import java.util.function.Function;

/**
 * Enum translator for text components.<br>
 * The view shows the enum's {@code toString()}, which may be localized,
 * and the input is mapped back by comparing with {@code toString()} of all enum constants.
 *
 * @author harald
 * @param <T> the enum type
 */
@ValueTranslatorService(modelClass = Enum.class, viewClass = String.class)
public class EnumStringTranslator<T extends Enum<T>> extends AbstractValueTranslator<T,String> {

  private final Class<T> clazz;   // the enum type

  /**
   * Creates an enum translator.
   *
   * @param component the text component
   * @param clazz the enum class
   */
  public EnumStringTranslator(FxTextComponent component, Class<T> clazz) {
    super(component);
    this.clazz = clazz;
  }

  @Override
  public Function<T, String> toViewFunction() {
    // toString() and not name() because user's view may be localized
    return v -> v == null ? null : v.toString();
  }

  @Override
  public Function<String, T> toModelFunction() {
    return s -> {
      if (s != null) {
        for (T v: clazz.getEnumConstants()) {
          // not Enum.valueOf() because user's view may be localized
          if (s.equals(v.toString())) {
            return v;
          }
        }
      }
      return null;
    };
  }

}
