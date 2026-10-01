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

package org.tentackle.validate;

import org.tentackle.common.ServiceFactory;
import org.tentackle.misc.CompoundValue;
import org.tentackle.script.ScriptRuntimeException;


interface ValidatorCompoundValueFactoryHolder {
  ValidatorCompoundValueFactory INSTANCE = ServiceFactory.createService(
    ValidatorCompoundValueFactory.class, DefaultValidatorCompoundValueFactory.class);
}


/**
 * Factory to create {@link CompoundValue}s for validators.
 * <p>
 * Creates the compound values for the {@code value}, {@code condition} and {@code message} parameters
 * of the validation annotations.
 * <p>
 * Replaceable via &#64;{@link org.tentackle.common.Service}.
 */
public interface ValidatorCompoundValueFactory {

  /**
   * Gets the singleton.
   *
   * @return the singleton
   */
  static ValidatorCompoundValueFactory getInstance() {
    return ValidatorCompoundValueFactoryHolder.INSTANCE;
  }


  /**
   * Creates the compound value for the value parameter.
   *
   * @param value the value from the annotation
   * @param clazz the type of the value
   * @return the created compound value
   */
  CompoundValue createValueParameter(String value, Class<?> clazz);

  /**
   * Creates the compound value for the condition parameter.
   *
   * @param condition the condition text from the annotation
   * @return the created compound value of type {@link Boolean}
   */
  CompoundValue createConditionParameter(String condition);

  /**
   * Creates the compound value for the message parameter.
   *
   * @param message the message text from the annotation
   * @return the created compound value
   * @throws ScriptRuntimeException if message contains a script-based I18N translation
   *         and there is no message {@link org.tentackle.script.ScriptConverter} for the corresponding scripting language
   */
  CompoundValue createMessageParameter(String message);

}
