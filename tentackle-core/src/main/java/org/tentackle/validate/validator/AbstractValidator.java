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

import org.tentackle.common.ServiceFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.misc.CompoundValue;
import org.tentackle.reflect.EffectiveClassProvider;
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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;


/**
 * An abstract validator.
 *
 * @param <T> the validation annotation
 * @author harald
 */
public abstract class AbstractValidator<T extends Annotation> implements Validator {

  /** maps language-name to message-script-converter. */
  protected static final Map<String, ScriptConverter> MESSAGE_CONVERTER_MAP = new HashMap<>();

  /** maps language-name to validation-script-converter. */
  protected static final Map<String, ScriptConverter> VALIDATION_CONVERTER_MAP = new HashMap<>();


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

  protected volatile CompoundValue valueParameter;       // the value given by the annotation
  protected volatile CompoundValue conditionParameter;   // the condition
  protected volatile CompoundValue messageParameter;     // the message


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
    if (scopes.length == 1 && DefaultScope.class.isAssignableFrom(scopes[0]) &&   // only the default scope applies ...
        validationContext != null && validationContext.getParentObject() instanceof ScopeConfigurator) {
      // ... and the parent is a ScopeConfigurator
      scopes = ((ScopeConfigurator) validationContext.getParentObject()).getDefaultScopes();
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
      CompoundValue localPar = conditionParameter;
      if (localPar == null) {
        synchronized (this) {
          localPar = conditionParameter;
          if (localPar == null) {
            localPar = conditionParameter = createConditionParameter(condition);
          }
        }
      }
      Object value = localPar.getValue(validationContext.getParentObject(), createScriptVariables(validationContext));
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

  /**
   * Creates the compound value for the value parameter.
   *
   * @param value the value from the annotation
   * @param clazz the type of the value
   * @return the created compound value
   */
  protected CompoundValue createValueParameter(String value, Class<?> clazz) {
    return createCompoundValue(value, clazz, VALIDATION_CONVERTER_MAP::get);
  }

  /**
   * Creates the compound value for the confition parameter.
   *
   * @param condition the condition text from the annotation
   * @return the created compound value
   */
  protected CompoundValue createConditionParameter(String condition) {
    return createCompoundValue(condition, Boolean.class, VALIDATION_CONVERTER_MAP::get);
  }

  /**
   * Creates the compound value for the message parameter.
   *
   * @param message the message text from the annotation
   * @return the created compound value
   */
  protected CompoundValue createMessageParameter(String message) {
    // missing converter is detected by the tentackle-check-maven-plugin, if compoundvalue is a script
    return createCompoundValue(message, String.class, MESSAGE_CONVERTER_MAP::get);
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
    CompoundValue localPar = valueParameter;
    if (localPar == null) {
      // determine the parameter once
      synchronized (this) {
        localPar = valueParameter;
        if (localPar == null) {
          localPar = valueParameter = createValueParameter(value, clazz);
        }
      }
    }
    return (V) localPar.getValue(validationContext.getParentObject(), createScriptVariables(validationContext));
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
      CompoundValue localPar = messageParameter;
      if (localPar == null) {
        // determine the parameter once
        synchronized (this) {
          localPar = messageParameter;
          if (localPar == null) {
            localPar = messageParameter = createMessageParameter(annoMessage);
          }
        }
      }
      Object value = localPar.getValue(validationContext.getParentObject(), createScriptVariables(validationContext));
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
   * @return immutable list of the result
   */
  protected List<? extends ValidationResult> createFailedValidationResult(String message, ValidationContext validationContext) {
    String msg = createMessage(message, validationContext);
    ValidationResult result = ValidationUtilities.getInstance().createFailedValidationResult(
            this, validationContext, msg == null || msg.isEmpty() ? message : msg);
    return List.of(result);
  }

  /**
   * Casts given object to a comparable.
   *
   * @param <C> the comparable type
   * @param obj the object
   * @return the object as a Comparable or null if obj is null
   */
  @SuppressWarnings("unchecked")
  protected <C> Comparable<C> assertComparable(C obj) {
    if (obj != null && !(obj instanceof Comparable)) {
      throw new ValidationRuntimeException(obj.getClass() + " is not a Comparable");
    }
    return (Comparable<C>) obj;
  }

  /**
   * Creates the script variables from the validation context.
   *
   * @param validationContext the validation context
   * @return the modifiable set of script variables
   */
  protected Set<ScriptVariable> createScriptVariables(ValidationContext validationContext) {
    Set<ScriptVariable> variables = new HashSet<>();
    variables.add(new ScriptVariable(ScriptVariable.VAR_SCOPE, validationContext.getEffectiveScope()));
    variables.add(new ScriptVariable(ScriptVariable.VAR_PATH, validationContext.getValidationPath()));
    variables.add(new ScriptVariable(ScriptVariable.VAR_OBJECT, validationContext.getParentObject()));
    variables.add(new ScriptVariable(ScriptVariable.VAR_VALUE, validationContext.getObject()));
    variables.add(new ScriptVariable(ScriptVariable.VAR_CLASS, EffectiveClassProvider.getEffectiveClass(validationContext.getParentObject())));
    return variables;
  }

}
