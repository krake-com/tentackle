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

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.misc.CompoundValue;
import org.tentackle.script.Script;
import org.tentackle.script.ScriptConverter;
import org.tentackle.script.ScriptRuntimeException;
import org.tentackle.validate.validator.AbstractScriptValidationMessageConverter;
import org.tentackle.validate.validator.MessageScriptConverter;
import org.tentackle.validate.validator.ValidationScriptConverter;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Default compound value factory for validators.
 */
@Service(ValidatorCompoundValueFactory.class)
public class DefaultValidatorCompoundValueFactory implements ValidatorCompoundValueFactory {

  /** maps language-name to message-script-converter. */
  private final Map<String, ScriptConverter> messageConverterMap;

  /** maps language-name to validation-script-converter. */
  private final Map<String, ScriptConverter> validationConverterMap;


  public DefaultValidatorCompoundValueFactory() {
    // load message converters
    messageConverterMap = new HashMap<>();
    for (Map.Entry<String,String> entry:
        ServiceFactory.getServiceFinder().createNameMap(MessageScriptConverter.class.getName()).entrySet()) {
      try {
        String language = StringHelper.stripEnclosingDoubleQuotes(entry.getKey());
        ScriptConverter converter = (ScriptConverter) Class.forName(entry.getValue()).getDeclaredConstructor().newInstance();
        messageConverterMap.put(language, converter);
      }
      catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
          NoSuchMethodException | InvocationTargetException ex) {
        throw new ValidationRuntimeException("message script converter configuration failed", ex);
      }
    }

    // load validation converters
    validationConverterMap = new HashMap<>();
    for (Map.Entry<String,String> entry:
        ServiceFactory.getServiceFinder().createNameMap(ValidationScriptConverter.class.getName()).entrySet()) {
      try {
        String language = StringHelper.stripEnclosingDoubleQuotes(entry.getKey());
        ScriptConverter converter = (ScriptConverter) Class.forName(entry.getValue()).getDeclaredConstructor().newInstance();
        validationConverterMap.put(language, converter);
      }
      catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
          NoSuchMethodException | InvocationTargetException ex) {
        throw new ValidationRuntimeException("validation script converter configuration failed", ex);
      }
    }
  }


  @Override
  public CompoundValue createValueParameter(String value, Class<?> clazz) {
    return createCompoundValue(value, clazz, validationConverterMap::get);
  }


  @Override
  public CompoundValue createConditionParameter(String condition) {
    return createCompoundValue(condition, Boolean.class, validationConverterMap::get);
  }


  @Override
  public CompoundValue createMessageParameter(String message) {
    CompoundValue cv = createCompoundValue(message, String.class, messageConverterMap::get);
    Script script = cv.getScript();
    if (script != null) {
      if (message.contains(AbstractScriptValidationMessageConverter.I18N_INTRO)) {   // i18n script
        if (messageConverterMap.get(script.getLanguage().getName()) == null) {
          throw new ScriptRuntimeException("missing message script converter for " + script.getLanguage());
        }
      }
    }
    return cv;
  }


  /**
   * Creates a compound value.<br>
   * The created compound value is always created as cached and threadsafe execution required.
   *
   * @param value the string value
   * @param clazz the class of the value
   * @param scriptConverterProvider the optional script converter provider
   * @return the compound value
   */
  protected CompoundValue createCompoundValue(String value, Class<?> clazz, Function<String, ScriptConverter> scriptConverterProvider) {
    return new CompoundValue(value, clazz, true, true, scriptConverterProvider);
  }

}
