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

import org.tentackle.common.LocaleProvider;
import org.tentackle.fx.FxTextComponent;

/**
 * Abstract base class for fraction number translators.
 *
 * @author harald
 * @param <M> the model's number type
 */
public abstract class FractionNumberStringTranslator<M extends Number> extends NumberStringTranslator<M> {

  /**
   * Creates a fraction number translator.
   *
   * @param component the text component
   */
  public FractionNumberStringTranslator(FxTextComponent component) {
    super(component);
  }

  @Override
  public boolean needsToModelTwice() {
    return true;
  }


  @Override
  protected void initializeValidChars() {
    super.initializeValidChars();
    if (pattern != null && pattern.indexOf('E') >= 0) {
      String exp = getFormat().getDecimalFormatSymbols().getExponentSeparator();
      String expLC = exp.toLowerCase(LocaleProvider.getInstance().getLocale());
      String expUC = exp.toUpperCase(LocaleProvider.getInstance().getLocale());
      signedValidChars += expLC + expUC;
      unsignedValidChars += expLC + expUC;
    }
  }

}
