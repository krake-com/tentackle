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

import org.tentackle.misc.CompoundValue;
import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.script.ScriptVariable;
import org.tentackle.validate.DefaultScope;
import org.tentackle.validate.ScopeConfigurator;
import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationRuntimeException;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationUtilities;
import org.tentackle.validate.Validator;
import org.tentackle.validate.ValidatorCompoundValueFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * An abstract validator.
 *
 * @param <T> the validation annotation
 * @author harald
 */
public abstract class AbstractValidator<T extends Annotation> implements Validator {

  private AnnotatedElement element;           // the annotated element, if this is a fixed(annotated) validator
  private String validatedElementName;        // the validated element name
  private int validationIndex = -1;           // the index if part of a ValidationList, -1 if single validator

  private volatile CompoundValue conditionParameter;                // the condition
  private volatile CompoundValue messageParameter;                  // the message
  private volatile CompoundValue valueParameter;                    // the value given by the annotation (fixed or last varying type)
  private volatile Map<Class<?>, CompoundValue> valueParameterMap;  // value types (varying type only, null if fixed)


  @Override
  public String toString() {
    return getClass().getName();
  }


  @Override
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
    boolean applies = true;
    CompoundValue condPar = getConditionParameter();
    if (condPar != null) {
      Object value = condPar.getValue(validationContext.getParentObject(), createScriptVariables(validationContext));
      if (value instanceof Boolean) {
        applies = ((Boolean) value);
      }
      else  {
        throw new ValidationRuntimeException("result of condition '" + condPar + "' is not a Boolean");
      }
    }
    return applies;
  }

  @Override
  public void validate() {
    CompoundValue par = getConditionParameter();
    if (par != null) {
      par.validate();
    }
    par = getMessageParameter();
    if (par != null) {
      par.validate();
    }
    String value = getValue();
    if (value != null && !value.isEmpty()) {
      // just create the CV and compile the script, if any. We don't know the type yet. However, since
      // the script is cached, it is still compiled only once.
      ValidatorCompoundValueFactory.getInstance().createValueParameter(value, Object.class).validate();
    }
  }


  /**
   * Gets the compound value of the condition parameter.
   *
   * @return the condition parameter, null if no condition
   * @see #getCondition()
   */
  protected CompoundValue getConditionParameter() {
    CompoundValue localPar = conditionParameter;
    if (localPar == null && !getCondition().isEmpty()) {
      synchronized (this) {
        localPar = conditionParameter;
        if (localPar == null) {
          localPar = conditionParameter = ValidatorCompoundValueFactory.getInstance().createConditionParameter(getCondition());
        }
      }
    }
    return localPar;
  }

  /**
   * Gets the value for given type.
   *
   * @param <V> the value type
   * @param clazz the type of the value, null to get from validation context
   * @param validationContext the validation context
   * @return the evaluated value
   */
  @SuppressWarnings("unchecked")
  protected <V> V getValue(Class<V> clazz, ValidationContext validationContext) {
    if (clazz == null) {
      clazz = (Class<V>) validationContext.getType();
      if (clazz == null) {
        Object object = validationContext.getObject();
        if (object == null) {
          throw new ValidationRuntimeException("could not determine object's type for " + this);
        }
        clazz = (Class<V>) EffectiveClassProvider.getEffectiveClass(object);
      }
    }
    CompoundValue localPar = getValueParameter(clazz);
    return (V) localPar.getValue(validationContext.getParentObject(), createScriptVariables(validationContext));
  }

  /**
   * Gets the value according to its type
   *
   * @param validationContext the validation context
   * @return the evaluated value
   */
  protected Object getValue(ValidationContext validationContext) {
    return getValue(null, validationContext);
  }

  /**
   * Gets the compound value of the value parameter.
   *
   * @param clazz the compound value's type (usually from the {@link ValidationContext})
   * @return the value parameter, never null
   * @see #getValue()
   */
  protected CompoundValue getValueParameter(Class<?> clazz) {
    CompoundValue valPar = valueParameter;
    if (valPar == null) {
      synchronized (this) {
        valPar = valueParameter;
        if (valPar == null) {
          valPar = valueParameter = ValidatorCompoundValueFactory.getInstance().createValueParameter(getValue(), clazz);
        }
      }
    }
    else if (valPar.getClazz() != clazz) {
      // when using TT's validators via ValidationUtilities, the clazz is always the same, but applications could derive their
      // own implementations or use them in other scenarios. If this is the case, we switch to mapped mode.
      Map<Class<?>, CompoundValue> localMap = valueParameterMap;
      if (localMap == null) {
        synchronized (this) {
          localMap = valueParameterMap;
          if (localMap == null) {
            localMap = valueParameterMap = new ConcurrentHashMap<>();
            localMap.put(valPar.getClazz(), valPar);
          }
        }
      }
      valPar = valueParameter = localMap.computeIfAbsent(clazz, c -> ValidatorCompoundValueFactory.getInstance().createValueParameter(getValue(), c));
    }
    return valPar;
  }



  /**
   * Creates the message.
   *
   * @param message the default message if annotation's message parameter is empty
   * @param validationContext the validation context
   * @return the evaluated message
   */
  protected String createMessage(String message, ValidationContext validationContext) {
    CompoundValue msgPar = getMessageParameter();
    if (msgPar != null) {
      Object value = msgPar.getValue(validationContext.getParentObject(), createScriptVariables(validationContext));
      if (value != null) {
        message = value.toString();
      }
    }
    return message;
  }

  /**
   * Gets the message parameter.
   *
   * @return the message parameter, null if annotiation's message text is empty
   * @see #getMessage()
   */
  protected CompoundValue getMessageParameter() {
    CompoundValue localPar = messageParameter;
    if (localPar == null && !getMessage().isEmpty()) {
      synchronized (this) {
        localPar = messageParameter;
        if (localPar == null) {
          localPar = messageParameter = ValidatorCompoundValueFactory.getInstance().createMessageParameter(getMessage());
        }
      }
    }
    return localPar;
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
