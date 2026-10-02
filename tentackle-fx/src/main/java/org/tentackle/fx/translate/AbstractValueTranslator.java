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

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.ValueTranslator;

/**
 * Base class of a value translator.
 *
 * @author harald
 * @param <M> the model's type
 * @param <V> the view's type
 */
public abstract class AbstractValueTranslator<M,V> implements ValueTranslator<M,V> {

  /**
   * The component.
   */
  private final FxComponent component;

  /**
   * If true the translator will parse to model even incomplete information
   * as much as possible. No exceptions are thrown.
   */
  private boolean lenient;


  /**
   * Creates a translator for a given component.
   *
   * @param component the component
   */
  public AbstractValueTranslator(FxComponent component) {
    this.component = component;
  }

  @Override
  public FxComponent getComponent() {
    return component;
  }

  @Override
  public boolean isLenient() {
    return lenient;
  }

  /**
   * Sets whether parsing to model should be lenient.
   *
   * @param lenient true if lenient
   */
  public void setLenient(boolean lenient) {
    this.lenient = lenient;
  }

  @Override
  public boolean isMappingIncomplete() {
    return false;
  }

  @Override
  public void saveModelValue(M modelValue) {
    // default does nothing
  }

  @Override
  public boolean isModelModified() {
    return false;
  }

}
