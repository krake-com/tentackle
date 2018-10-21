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


package org.tentackle.validate.validator;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.tentackle.common.ServiceFactory;
import org.tentackle.misc.CompoundValue;
import org.tentackle.common.StringHelper;
import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.script.Script;
import org.tentackle.script.ScriptConverter;
import org.tentackle.script.ScriptVariable;
import org.tentackle.validate.DefaultScope;
import org.tentackle.validate.ScopeConfigurator;
import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationRuntimeException;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationUtilities;
import org.tentackle.validate.Validator;


/**
 * An abstract validator.
 *
 * @param <T> the validation annotation
 * @author harald
 */
public abstract class AbstractValidator<T extends Annotation> implements Validator {

  /** script variable for effective scope. */
  public static final String VAR_SCOPE = "scope";

  /** script variable for validation path. */
  public static final String VAR_PATH = "path";

  /** script variable for the value. */
  public static final String VAR_VALUE = "value";

  /** script variable for the parent class. */
  public static final String VAR_CLASS = "clazz";


  /** <language-name> : <converter> */
  private static final Map<String, ScriptConverter> MESSAGE_CONVERTER_MAP = new HashMap<>();
  private static final Map<String, ScriptConverter> VALIDATION_CONVERTER_MAP = new HashMap<>();

  static {
    // load message converters
    for (Map.Entry<String,String> entry:
         ServiceFactory.getServiceFinder().createNameMap(MessageScriptConverter.class.getName()).entrySet()) {
      try {
        String language = StringHelper.stripEnclosingDoubleQuotes(entry.getKey());
        ScriptConverter converter = (ScriptConverter) Class.forName(entry.getValue()).getDeclaredConstructor().newInstance();
        MESSAGE_CONVERTER_MAP.put(language, converter);
      }
      catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
             NoSuchMethodException | SecurityException | InvocationTargetException ex) {
        throw new ValidationRuntimeException("message script converter configuration failed", ex);
      }
    }

    // load validation converters
    for (Map.Entry<String,String> entry:
         ServiceFactory.getServiceFinder().createNameMap(ValidationScriptConverter.class.getName()).entrySet()) {
      try {
        String language = StringHelper.stripEnclosingDoubleQuotes(entry.getKey());
        ScriptConverter converter = (ScriptConverter) Class.forName(entry.getValue()).getDeclaredConstructor().newInstance();
        VALIDATION_CONVERTER_MAP.put(language, converter);
      }
      catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
             NoSuchMethodException | SecurityException | InvocationTargetException ex) {
        throw new ValidationRuntimeException("validation script converter configuration failed", ex);
      }
    }
  }


  protected T annotation;                       // the annotation
  protected AnnotatedElement element;           // the annotated element, if this is a fixed(annotated) validator
  protected String validatedElementName;        // the validated element name
  protected int validationIndex = -1;           // the index if part of a ValidationList, -1 if single validator

  protected CompoundValue valueParameter;       // the value given by the annotation
  protected CompoundValue conditionParameter;   // the condition
  protected CompoundValue messageParameter;     // the message


  @Override
  public String toString() {
    return getClass().getName();
  }


  @Override
  @SuppressWarnings("unchecked")
  public void setAnnotation(Annotation annotation) {
    this.annotation = (T) annotation;
  }

  @Override
  public T getAnnotation() {
    return annotation;
  }


  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public Class<? extends ValidationScope>[] getConfiguredScopes(ValidationContext validationContext) {
    Class<? extends ValidationScope>[] scopes = getScope();
    if (scopes.length == 1 && DefaultScope.class.isAssignableFrom(scopes[0])) {
      // the default scope applies
      if (validationContext != null && validationContext.getParentObject() instanceof ScopeConfigurator) {
        return ((ScopeConfigurator) validationContext.getParentObject()).getDefaultScopes();
      }
      return new Class[] { DefaultScope.class };
    }
    return scopes;
  }

  @Override
  public void setAnnotatedElement(AnnotatedElement element) {
    this.element = element;
  }

  @Override
  public AnnotatedElement getAnnotatedElement() {
    return element;
  }


  @Override
  public void setValidatedElementName(String name) {
    this.validatedElementName = name;
  }

  @Override
  public String getValidatedElementName() {
    return validatedElementName;
  }


  @Override
  public void setValidationIndex(int index) {
    this.validationIndex = index;
  }

  @Override
  public int getValidationIndex() {
    return validationIndex;
  }


  /**
   * {@inheritDoc}
   * <p>
   * The default implementation checks the boolean value
   * of the optional <code>condition=...</code> expression.
   * If no such condition given, it returns true.
   *
   * @return true if it applies
   */
  @Override
  public boolean isConditionValid(ValidationContext validationContext) {
    String condition = getCondition();
    if (condition != null && !condition.isEmpty()) {
      if (conditionParameter == null) {
        conditionParameter = createConditionParameter(condition);
      }
      ScriptVariable[] variables = createScriptVariables(validationContext);
      Object value = conditionParameter.getValue(validationContext.getParentObject(), variables);
      if (value instanceof Boolean) {
        return ((Boolean) value);
      }
      else  {
        throw new ValidationRuntimeException("result of condition '" + condition + "' is not a Boolean");
      }
    }
    else  {
      return true;
    }
  }


  /**
   * Creates a compound value.
   *
   * @param value the string value
   * @param clazz the class of the value
   * @return the compound value
   */
  protected CompoundValue createCompoundValue(String value, Class<?> clazz) {
    CompoundValue cv = new CompoundValue(value, clazz);
    cv.setScriptCached(true);
    cv.setScriptExecutedThreadSafe(true);
    return cv;
  }


  /**
   * Creates the compound value for the value parameter.
   *
   * @param value the value from the annotation
   * @param clazz the type of the value
   * @return the created compound value
   */
  protected CompoundValue createValueParameter(String value, Class<?> clazz) {
    CompoundValue compVal = createCompoundValue(value, clazz);
    Script script = compVal.getScript();
    if (script != null) {
      script.setConverter(VALIDATION_CONVERTER_MAP.get(script.getLanguage().getName()));  // null is ok
    }
    return compVal;
  }


  /**
   * Creates the compound value for the confition parameter.
   *
   * @param condition the condition text from the annotation
   * @return the created compound value
   */
  protected CompoundValue createConditionParameter(String condition) {
    CompoundValue compVal = createCompoundValue(condition, Boolean.class);
    Script script = compVal.getScript();
    if (script != null) {
      script.setConverter(VALIDATION_CONVERTER_MAP.get(script.getLanguage().getName()));  // null is ok
    }
    return compVal;
  }


  /**
   * Creates the compound value for the message parameter.
   *
   * @param message the message text from the annotation
   * @return the created compound value
   */
  protected CompoundValue createMessageParameter(String message) {
    CompoundValue compVal = createCompoundValue(message, String.class);
    Script script = compVal.getScript();
    if (script != null) {
      // set the message script converter
      ScriptConverter converter = MESSAGE_CONVERTER_MAP.get(script.getLanguage().getName());
      if (converter == null) {
        throw new ValidationRuntimeException("missing message script converter for " + script.getLanguage());
      }
      script.setConverter(converter);
    }
    return compVal;
  }



  /**
   * Gets the value for given type.
   *
   * @param <V> the value type
   * @param value the value from the annotation
   * @param clazz the type of the value, null to get from validation context
   * @param validationContext the validation context
   * @return the evaluated value
   */
  @SuppressWarnings("unchecked")
  protected <V> V getValue(String value, Class<V> clazz, ValidationContext validationContext) {
    if (clazz == null) {
      clazz = (Class<V>) validationContext.getType();
      if (clazz == null) {
        Object object = validationContext.getObject();
        if (object == null) {
          throw new ValidationRuntimeException("could not determine object's type for " + this);
        }
        clazz = (Class<V>) object.getClass();
      }
    }
    if (valueParameter == null) {
      // determine the parameter once
      valueParameter = createValueParameter(value, clazz);
    }
    Object parentObject = validationContext.getParentObject();
    ScriptVariable[] variables = createScriptVariables(validationContext);
    return (V) valueParameter.getValue(parentObject, variables);
  }


  /**
   * Gets the value according to its type
   *
   * @param value the value from the annotation
   * @param validationContext the validation context
   * @return the evaluated value
   */
  protected Object getValue(String value, ValidationContext validationContext) {
    return getValue(value, null, validationContext);
  }



  /**
   * Creates the message.
   *
   * @param message the default message from the validator
   * @param validationContext the validation context
   * @return the evaluated message
   */
  protected String createMessage(String message, ValidationContext validationContext) {
    String annoMessage = getMessage();
    if (!annoMessage.isEmpty()) {
      if (messageParameter == null) {
        // determine the parameter once
        messageParameter = createMessageParameter(annoMessage);
      }
      Object parentObject = validationContext.getParentObject();
      ScriptVariable[] variables = createScriptVariables(validationContext);
      Object value = messageParameter.getValue(parentObject, variables);
      if (value != null) {
        return value.toString();
      }
    }
    return message;
  }

  /**
   * Creates a list of a single failed result.
   *
   * @param message the fail message
   * @param validationContext the validation context
   * @return the results
   */
  protected List<? extends ValidationResult> createFailedValidationResult(String message, ValidationContext validationContext) {
    String msg = createMessage(message, validationContext);
    ValidationResult result = ValidationUtilities.getInstance().createFailedValidationResult(
            this, validationContext, msg == null || msg.isEmpty() ? message : msg);
    List<ValidationResult> results = new ArrayList<>(1);
    results.add(result);
    return results;
  }


  /**
   * Casts given object to a comparable.
   *
   * @param <C> the comparable type
   * @param obj the object
   * @return the object as a Comparable or null if obj is null
   */
  @SuppressWarnings("unchecked")
  public <C> Comparable<C> assertComparable(C obj) {
    if (obj != null && !(obj instanceof Comparable)) {
      throw new ValidationRuntimeException(obj.getClass() + " is not a Comparable");
    }
    return (Comparable<C>) obj;
  }


  /**
   * Creates the script variables from the validation context.
   *
   * @param validationContext the validation context
   * @return the script variables
   */
  protected ScriptVariable[] createScriptVariables(ValidationContext validationContext) {
    return new ScriptVariable[] {
      new ScriptVariable(VAR_SCOPE, validationContext.getEffectiveScope()),
      new ScriptVariable(VAR_PATH, validationContext.getValidationPath()),
      new ScriptVariable(VAR_VALUE, validationContext.getObject()),
      new ScriptVariable(VAR_CLASS, EffectiveClassProvider.getEffectiveClass(validationContext.getParentObject()))
    };
  }

}
