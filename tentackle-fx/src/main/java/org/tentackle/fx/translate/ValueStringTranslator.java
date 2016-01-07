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

import org.tentackle.fx.FxTextComponent;
import org.tentackle.fx.ValueTranslator;

/**
 * A value translator for text components.
 *
 * @author harald
 */
public abstract class ValueStringTranslator<T> extends ValueTranslator<T,String> {

  /**
   * Creates the translator.
   *
   * @param component the text component
   */
  public ValueStringTranslator(FxTextComponent component) {
    super(component);
  }

  @Override
  public FxTextComponent getComponent() {
    return (FxTextComponent) super.getComponent();
  }

  @Override
  public T toModel(String text) {
    if (text != null) {
      char filler = getComponent().getFiller();
      if (filler != ' ') {
        text = text.replace(filler, ' ');
      }
      text = text.trim();
      if (!text.isEmpty()) {
        return super.toModel(text);
      }
    }
    return null;
  }


  /**
   * Gets the valid input characters.
   *
   * @return the valid input chars, null if unlimited
   */
  public String getValidChars() {
    return null;
  }

}
