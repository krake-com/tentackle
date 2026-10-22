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

package org.tentackle.fx;

import org.tentackle.bind.Binder;

import javafx.scene.Node;
import java.util.function.Function;

/**
 * A value translator.<br>
 * Converts between view and model types.
 * <p>
 * Notice: tentackle uses the term "translator" instead of "converter" to prevent
 * tentackle-translators from being mixed up with JavaFX StringConverters.
 *
 * @author harald
 * @param <M> the model's type
 * @param <V> the view's type
 */
public interface ValueTranslator<M,V> {

  /**
   * Gets the component.
   *
   * @return the component
   */
  FxComponent getComponent();

  /**
   * Function to translate a model value to a view value.
   *
   * @return the function
   */
  Function<M,V> toViewFunction();

  /**
   * Function to translate a view value to a model value.
   *
   * @return the function
   */
  Function<V,M> toModelFunction();

  /**
   * Returns whether parsing to model should be lenient.
   *
   * @return true if lenient, false is default
   */
  boolean isLenient();

  /**
   * Returns whether the model value is only mapped partially to the view.<br>
   * Such data types may hold more data that could have been changed by the translator, but
   * the view object still looks unchanged.<br>
   * An example for such a type is {@link org.tentackle.common.I18NText}.
   *
   * @return true if view may look unchanged but the model could be modified
   */
  boolean isMappingIncomplete();

  /**
   * Remembers the model value for later check if changed.<br>
   * Only invoked if {@link #isMappingIncomplete()} is <code>true</code>.
   *
   * @param modelValue the model value
   */
  void saveModelValue(M modelValue);

  /**
   * Returns whether the model is modified compared to the value saved via {@link #saveModelValue(Object)}.<br>
   * Only invoked if {@link #isMappingIncomplete()} is <code>true</code>.
   *
   * @return true if model is modified
   */
  boolean isModelModified();

  /**
   * Returns whether toModel must be invoked twice.<br>
   * An example is the {@link org.tentackle.fx.translate.DoubleStringTranslator}:<br>
   * let fraction number input be 1.211111 and formatting scale 2.
   * <ul>
   * <li>after first updateModel the model contains 1.211111</li>
   * <li>after formatting and updateView, the view contains 1.21 but the model still contains 1.211111</li>
   * <li>after second updateModel the model contains the correct value 1.21</li>
   * </ul>
   * @return true to force a second translation roundtrip
   */
  default boolean needsToModelTwice() {
    return false;
  }

  /**
   * Invoked from the binder after a binding property has been modified
   * via {@link Binder#putBindingProperty(Object, Object)}.<br>
   * Not to be mixed up with the component's properties (see {@link Node#getProperties()}),
   * which may require explicit invocation of this method.<br>
   * May be necessary if some action must be taken by the translator, for example
   * re-initialization, cleanup, etc.
   */
  default void bindingPropertiesUpdated() {
    // default does nothing.
  }

  /**
   * Converts a model value to a view value.
   *
   * @param value the model's value
   * @return the view's value
   */
  default V toView(M value) {
    return toViewFunction().apply(value);
  }

  /**
   * Converts a view value to a model value.
   *
   * @param value the view's value
   * @return the model's value
   */
  default M toModel(V value) {
    return toModelFunction().apply(value);
  }

}
