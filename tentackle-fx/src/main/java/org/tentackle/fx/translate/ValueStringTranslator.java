/*
 * Tentackle - https://tentackle.org.
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

/**
 * A value translator for text components.
 *
 * @author harald
 * @param <M> the model's type
 */
public abstract class ValueStringTranslator<M> extends AbstractValueTranslator<M,String> {

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

  /**
   * {@inheritDoc}
   * <p>
   * Replaces the filler by spaces and trims the text before parsing.
   * Empty input is translated to null. A temporary error of the component is cleared.
   */
  @Override
  public M toModel(String text) {
    FxTextComponent component = getComponent();
    if (component != null) {
      if (text != null) {
        char filler = component.getFiller();
        if (filler != ' ') {
          text = text.replace(filler, ' ');
        }
        text = text.trim();
        if (text.isEmpty()) {
          text = null;
        }
      }
      if (component.isErrorTemporary()) {
        component.setError(null);    // clear temporary parsing error, if any
      }
    }
    return super.toModel(text);
  }


  /**
   * Gets the valid input characters.<br>
   * Applied by the component in addition to its own valid characters.
   *
   * @return the valid input chars, null if unlimited
   */
  public String getValidChars() {
    return null;
  }

}
