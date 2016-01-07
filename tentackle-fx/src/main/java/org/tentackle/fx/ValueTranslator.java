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

package org.tentackle.fx;

import java.util.function.Function;

/**
 * A value translator.<br>
 * Converts between view and model types.<br>
 * <p>
 * Notice: tentackle uses the term "translator" instead of "converter" to prevent
 * tentackle-translators from being mixed up with JavaFX StringConverters.
 *
 * @author harald
 * @param <M> the model's type
 * @param <V> the view's type
 */
public abstract class ValueTranslator<M,V> {

  /**
   * the component.
   */
  private final FxComponent component;

  /**
   * If true the translator will parse to model even incomplete information
   * as much as possible. No exceptions are thrown.
   */
  private boolean lenient;


  /**
   * Creates a string translator for a given text component.
   *
   * @param component the component
   */
  public ValueTranslator(FxComponent component) {
    this.component = component;
  }


  /**
   * Function to translate a model value to a view value.<br>
   *
   * @return the function
   */
  public abstract Function<M,V> toViewFunction();

  /**
   * Function to translate a view value to a model value.<br>
   *
   * @return the function
   */
  public abstract Function<V,M> toModelFunction();

  /**
   * Returns whether toModel must be invoked twice.<br>
   * An example is a DoubleStringTranslator:<br>
   * let fraction number input be 1.211111 and formatting scale 2.
   * <ul>
   * <li>after first updateModel the model contains 1.211111</li>
   * <li>after formatting and updateView, the view contains 1.21 but the model still contains 1.211111</li>
   * <li>after second updateModel the model contains the correct value 1.21</li>
   * </ul>
   * @return true if needs a second translation roundtrip
   */
  public boolean needsToModelTwice() {
    return false;
  }

  /**
   * Gets the component.
   *
   * @return the component
   */
  public FxComponent getComponent() {
    return component;
  }

  /**
   * Converts a model value to a view value.
   *
   * @param value the model's value
   * @return the view's value
   */
  public V toView(M value) {
    return toViewFunction().apply(value);
  }

  /**
   * Converts a view value to a model value.
   *
   * @param value the view's value
   * @return the model's value
   */
  public M toModel(V value) {
    return toModelFunction().apply(value);
  }


  /**
   * Returns whether parsing to model should be lenient.
   *
   * @return true if lenient, false is default
   */
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

}
