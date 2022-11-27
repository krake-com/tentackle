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

import org.tentackle.common.I18NText;
import org.tentackle.fx.FxTextComponent;
import org.tentackle.fx.ValueTranslatorService;

import java.util.function.Function;

/**
 * {@link I18NText} translator.
 * 
 * @author harald
 */
@ValueTranslatorService(modelClass = I18NText.class, viewClass = String.class)
public class I18NTextStringTranslator extends ValueStringTranslator<I18NText> {

  public I18NTextStringTranslator(FxTextComponent component) {
    super(component);
  }

  @Override
  public Function<I18NText, String> toViewFunction() {
    return v -> v == null ? null : v.get();   // get text in current locale
  }

  @Override
  public Function<String, I18NText> toModelFunction() {
    return s -> {
      if (s != null) {
        Object modelValue = getComponent().getBinding().getModelValue();
        if (modelValue instanceof I18NText i18NText) {
          return i18NText.with(s);    // with text in current locale
        }
        return new I18NText(s);
      }
      return null;
    };
  }

}
