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

import org.tentackle.bind.Binder;
import org.tentackle.common.LocaleProvider;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.log.Logger;
import org.tentackle.misc.Holder;
import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.reflect.ReflectionHelper;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;


interface ValidationUtilitiesHolder {
  ValidationUtilities INSTANCE = ServiceFactory.createService(ValidationUtilities.class, ValidationUtilities.class);
}


/**
 * Utilities for validation.
 * <p>
 * Determines the validators from the annotations, applies them according to the effective scope
 * and their conditions, and provides helper methods for validation results and localized messages.
 * <p>
 * Replaceable via &#64;{@link Service}.
 *
 * @author harald
 */
@Service(ValidationUtilities.class)    // defaults to self
public class ValidationUtilities {

  private static final Logger LOGGER = Logger.get(ValidationUtilities.class);


  /**
   * Gets the singleton.
   *
   * @return the singleton
   */
  public static ValidationUtilities getInstance() {
    return ValidationUtilitiesHolder.INSTANCE;
  }


  /**
   * Mapping result from {@link #mapValidationPath}.
   *
   * @param binder the binder
   * @param bindingPath the binding path
   */
  public record MapResult(Binder binder, String bindingPath) {

    /**
     * Creates a mapping result.<br>
     * A leading dot of the binding path is removed.
     *
     * @param binder the binder
     * @param bindingPath the binding path
     * @throws NullPointerException if binder or bindingPath is null
     */
    public MapResult {
      Objects.requireNonNull(binder);
      bindingPath = Objects.requireNonNull(bindingPath).startsWith(".") ? bindingPath.substring(1) : bindingPath;
    }

  }


  /**
   * Key to identify a validator of a field or method.<br>
   * Since the validation index is unique per class, field and method annotations
   * of the same attribute get different keys and are both applied.
   *
   * @param elementName  the name of the validated element
   * @param validatorClass the class of the validator
   * @param validationIndex the invocation index of the validator
   */
  public record ValidatorKey(String elementName, Class<?> validatorClass, int validationIndex) {

    /**
     * Creates a validator key from a validator.
     *
     * @param validator the validator
     */
    public ValidatorKey(Validator validator) {
      this(validator.getValidatedElementName(), validator.getClass(), validator.getValidationIndex());
    }

  }




  /**
   * Special classloader if used in tools (for example, tentackle-test maven plugin).
   */
  private ClassLoader validationBundleClassLoader;

  /**
   * In testmode all validators will be executed, regardless of scope or condition.
   */
  private Logger.Level testLogLevel;


  /**
   * Creates the validation utilities.
   */
  public ValidationUtilities() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Configures test mode.<p>
   * In test mode, all validators will be executed, regardless of scope or condition,
   * and logged in the given log level.<br>
   * Useful to check whether validators work at all, scripts compile and run, etc...<br>
   * Notice that the conditions and scopes are evaluated in test mode as well, although their results are ignored.<p>
   * Test mode is disabled by default.
   *
   * @param testLogLevel != null if running in test mode, null to clear test mode
   */
  public void configureTestMode(Logger.Level testLogLevel) {
    this.testLogLevel = testLogLevel;
  }

  /**
   * Gets the classloader to load the validation bundle class.
   *
   * @return the classloader, never null (default to {@code getClass().getClassLoader()})
   */
  public ClassLoader getValidationBundleClassLoader() {
    return validationBundleClassLoader == null ? getClass().getClassLoader() : validationBundleClassLoader;
  }

  /**
   * Sets the classloader to load the validation bundle class.
   *
   * @param validationBundleClassLoader the classloader, null to use the default
   */
  public void setValidationBundleClassLoader(ClassLoader validationBundleClassLoader) {
    this.validationBundleClassLoader = validationBundleClassLoader;
  }

  /**
   * Gets the default validation path.<br>
   * The default implementation just returns the object's class basename
   * with the first letter in lowercase (according to variable name rules).
   *
   * @param object the object to validate
   * @return the default (root) validation path, {@code "null"} if the object is null
   */
  public String getDefaultValidationPath(Object object) {
    Class<?> clazz = EffectiveClassProvider.getEffectiveClass(object);
    return clazz == null ? "null" : StringHelper.firstToLower(clazz.getSimpleName());
  }

  /**
   * Creates a validation result.
   *
   * @param validator the validator
   * @param validationContext the validation context
   * @param message the message
   * @return the validation result
   */
  public ValidationResult createValidationResult(Validator validator, ValidationContext validationContext, String message) {
    ValidationSeverity severity = ValidationSeverityFactory.getInstance().getValidationSeverity(validator.getSeverity());
    if (severity != null) {
      return severity.createValidationResult(validator, validationContext, message);
    }
    throw new ValidationRuntimeException("missing implementation for " + validator.getSeverity());
  }

  /**
   * Convenience method for simple objects such as DTOs.<br>
   * Validates for the default validation path and the {@link org.tentackle.validate.scope.AllScope}.
   *
   * @param object the validateable object
   * @throws ValidationFailedException if the validation failed
   * @see #getDefaultValidationPath(Object)
   */
  public void validate(Object object) {
    List<ValidationResult> results;
    try {
      results = validate(object, getDefaultValidationPath(object),
                         ValidationScopeFactory.getInstance().getAllScope());
    }
    catch (ValidationFailedException vfx) {
      results = vfx.getResults();
    }
    if (hasFailed(results)) {
      throw new ValidationFailedException(resultsToString(results), results);
    }
  }

  /**
   * Validates an object.<br>
   * The method first applies the field validators followed by the object validators.
   *
   * @param object the object to be validated
   * @param validationPath the validation path, null if {@link #getDefaultValidationPath(java.lang.Object)}
   * @param scope the validation scope
   * @return the validation results, empty if okay, never null
   */
  public List<ValidationResult> validate(Object object, String validationPath, ValidationScope scope) {
    Class<?> clazz = EffectiveClassProvider.getEffectiveClass(object);
    List<Validator> validators = ValidatorCache.getInstance().getFieldValidators(clazz);
    List<ValidationResult> results = validateFields(validators, scope, validationPath, object);
    try {
      validators = ValidatorCache.getInstance().getObjectValidators(clazz);
      results.addAll(validateObject(validators, scope, validationPath, object, object, clazz));
    }
    catch (ValidationFailedException vfx) {
      vfx.reThrow(results);
    }
    return results;
  }

  /**
   * Validates a collection of objects.<br>
   * Elements of the collection implementing {@link Validateable} will be validated
   * by calling the {@link Validateable#validate(String, ValidationScope)} method.
   * All other objects will be validated by calling {@link #validate(Object, String, ValidationScope)}.
   * The validation path of each element gets the element's index appended, for example {@code "lines[3]"}.
   * Null elements are skipped and not counted.
   *
   * @param collection the objects to validate
   * @param validationPath the validation path, null if {@link #getDefaultValidationPath(java.lang.Object)} of each element
   * @param scope the validation scope
   * @return the validation results, empty if okay, never null
   */
  public List<ValidationResult> validateCollection(Collection<?> collection,
                                                   String validationPath, ValidationScope scope)  {
    List<ValidationResult> results = new ArrayList<>();
    if (collection != null && !collection.isEmpty()) {
      try {
        int ndx = 0;
        for (Object obj : collection) {
          if (obj != null) {
            String path = (validationPath == null ? getDefaultValidationPath(obj) : validationPath) + "[" + (ndx++) + "]";
            if (obj instanceof Validateable validateable) {
              // redirect to the object in case the validate-method is overridden
              results.addAll(validateable.validate(path, scope));
            }
            else {
              results.addAll(validate(obj, path, scope));
            }
          }
        }
      }
      catch (ValidationFailedException vfx) {
        vfx.reThrow(results);
      }
    }
    return results;
  }

  /**
   * Gets all validators for all annotated fields or methods of the given class.<br>
   * If an attribute is annotated both at the field and the (getter-)method level,
   * the validators of both are applied.
   * The validators are sorted by priority and order of declaration.
   *
   * @param clazz the class to inspect
   * @param scope optional validation scope, null = all
   * @return the validators
   */
  public List<Validator> getFieldValidators(Class<?> clazz, ValidationScope scope) {
    Map<ValidatorKey,Validator> validatorMap = new HashMap<>();
    addFieldValidatorsToMap(validatorMap, clazz, scope);
    List<Validator> validators = new ArrayList<>(validatorMap.values());
    sortValidators(validators);
    return validators;
  }

  /**
   * Gets the validators for an annotated field and/or method.<br>
   * The validators are sorted by priority and order of declaration.
   *
   * @param field the field, null if none
   * @param method the (getter-)method, null if none
   * @param scope optional validation scope, null = all
   * @return the validators
   */
  public List<Validator> getFieldValidators(Field field, Method method, ValidationScope scope) {
    Map<ValidatorKey,Validator> validatorMap = new HashMap<>();
    addFieldValidatorsToMap(validatorMap, field, method, scope);
    List<Validator> validators = new ArrayList<>(validatorMap.values());
    sortValidators(validators);
    return validators;
  }

  /**
   * Gets all validators for the annotated class.
   *
   * @param clazz the class to inspect
   * @param scope optional validation scope, null = all
   * @return the list of object validators
   */
  public List<Validator> getObjectValidators(Class<?> clazz, ValidationScope scope) {
    List<Validator> validators = new ArrayList<>();
    Holder<Integer> validationIndexHolder = new Holder<>(0);
    for (Annotation annotation: clazz.getAnnotations()) {
      extractValidators(annotation, clazz, validators, scope, validationIndexHolder);
    }
    sortValidators(validators);
    return validators;
  }

  /**
   * Applies validators for fields of the given parent object.<br>
   * The model values are determined by the annotated elements per validator.
   *
   * @param validators the validators for the fields
   * @param effectiveScope the effective scope, null = all
   * @param validationPath the validation path corresponding to <code>object</code>,
   *                       null if {@link #getDefaultValidationPath(java.lang.Object) }
   * @param parentObject the parent object to validate
   * @return the list of validation results
   */
  public List<ValidationResult> validateFields(
          List<Validator> validators, ValidationScope effectiveScope,
          String validationPath, Object parentObject) {

    if (validationPath == null) {
      validationPath = getDefaultValidationPath(parentObject);
    }

    List<ValidationResult> allResults = new ArrayList<>();

    boolean inTest = testLogLevel != null;

    Map<String, ValidationContext> contextMap = new HashMap<>();    // same validation context for all validators of each field

    try {
      for (Validator validator : validators) {

        ValidationContext validationContext = contextMap.computeIfAbsent(validationPath + "." + validator.getValidatedElementName(), path -> {
          // build validation context
          Supplier<?> objectSupplier = null;      // lazy object provider
          Class<?> type = null;                   // the object's type

          AnnotatedElement element = validator.getAnnotatedElement();
          if (element instanceof Field field) {
            type = field.getType();
            objectSupplier = parentObject == null ? null : () -> {
              try {
                return field.get(parentObject);
              }
              catch (IllegalAccessException ex1) {
                field.setAccessible(true);    // set accessible and try again
                try {
                  return field.get(parentObject);
                }
                catch (InaccessibleObjectException | IllegalAccessException ex2) {
                  throw new ValidationRuntimeException("cannot access field " + field, ex2);
                }
              }
            };
          }
          else if (element instanceof Method method) {
            type = method.getReturnType();
            objectSupplier = parentObject == null ? null : () -> {
              try {
                try {
                  return method.invoke(parentObject);
                }
                catch (IllegalAccessException ex) {
                  method.setAccessible(true);    // set accessible and try again
                  return method.invoke(parentObject);
                }
              }
              catch (InvocationTargetException ix) {
                Throwable cause = ix.getTargetException();
                if (cause instanceof RuntimeException) {
                  throw (RuntimeException) cause;
                }
                throw new ValidationRuntimeException(cause.getMessage(), cause);
              }
              catch (InaccessibleObjectException | IllegalAccessException | IllegalArgumentException ex) {
                throw new ValidationRuntimeException("cannot invoke method " + method, ex);
              }
            };
          }
          return ValidationContextFactory.getInstance().create(path, type, objectSupplier, parentObject, effectiveScope, allResults);
        });

        if (inTest) {
          logTest(validator, validationContext);
        }

        // check if scope applies
        // runs the condition check even if in testmode
        if (validator.isConditionValid(validationContext) &&
            (effectiveScope == null || effectiveScope.appliesTo(validator.getConfiguredScopes(validationContext)))
            ||
            inTest) {
          // condition and scope apply
          List<? extends ValidationResult> results = validator.validate(validationContext);
          if (results != null && !results.isEmpty()) {  // should never be null, but who knows...
            allResults.addAll(results);
          }
        }
      }
    }
    catch (ValidationFailedException vfx) {
      // validation aborted
      vfx.reThrow(allResults);
    }

    return allResults;
  }

  /**
   * Applies validators to the given object.
   *
   * @param validators the validators to be applied
   * @param effectiveScope the effective scope, null = all
   * @param validationPath the validation path corresponding to <code>object</code>,
   *                       null if {@link #getDefaultValidationPath(java.lang.Object) }
   * @param parentObject the parent object the validated object is declared in
   * @param object the object to verify
   * @param type the type of the object, null if derive from the object
   * @return the list of validation results
   */
  public List<ValidationResult> validateObject(
          List<Validator> validators, ValidationScope effectiveScope,
          String validationPath, Object parentObject, Object object, Class<?> type) {

    List<ValidationResult> allResults = new ArrayList<>();

    if (!validators.isEmpty()) {

      if (validationPath == null) {
        validationPath = getDefaultValidationPath(object);
      }

      // same validation context for all object validators per object
      ValidationContext validationContext = ValidationContextFactory.getInstance().create(
          validationPath, type, object, parentObject, effectiveScope, allResults);

      boolean inTest = testLogLevel != null;

      try {
        for (Validator validator : validators) {

          if (inTest) {
            logTest(validator, validationContext);
          }

          // check if scope applies and validator is valid (even if in testmode)
          if (validator.isConditionValid(validationContext) &&
              (effectiveScope == null || effectiveScope.appliesTo(validator.getConfiguredScopes(validationContext)))
              ||
              inTest) {
            // condition and scope apply
            try {
              List<? extends ValidationResult> results = validator.validate(validationContext);
              if (results != null && !results.isEmpty()) {    // should never be null, but who knows...
                allResults.addAll(results);
              }
            }
            catch (RuntimeException ex) {
              // just log the error for diagnostics
              LOGGER.severe("validator '" + validator + "' failed", ex);
              throw ex;
            }
          }
        }
      }
      catch (ValidationFailedException vfx) {
        // validation aborted
        vfx.reThrow(allResults);
      }
    }

    return allResults;
  }

  /**
   * Checks whether a list of validation results contains a failed validation.
   *
   * @param results the list of validation results
   * @return true if a validation failed
   */
  public boolean hasFailed(List<ValidationResult> results) {
    for (ValidationResult result: results) {
      if (result.hasFailed()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether a list of validation results contains a message.
   *
   * @param results the list of validation results
   * @return true if a message found
   */
  public boolean hasMessage(List<ValidationResult> results) {
    for (ValidationResult result: results) {
      if (result.hasMessage()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Concatenates the validation results toString() to a single string.
   *
   * @param results the list of validation results
   * @return the string, never null
   */
  public String resultsToString(List<ValidationResult> results) {
    StringBuilder buf = new StringBuilder();
    if (results != null) {
      for (ValidationResult result: results) {
        if (!buf.isEmpty()) {
          buf.append('\n');
        }
        buf.append(result.toString());
      }
    }
    return buf.toString();
  }

  /**
   * Concatenates the validation results getMessage() to a single string.
   *
   * @param results the list of validation results
   * @return the string, never null
   */
  public String resultsToMessage(List<ValidationResult> results) {
    StringBuilder buf = new StringBuilder();
    if (results != null) {
      for (ValidationResult result: results) {
        if (!buf.isEmpty()) {
          buf.append('\n');
        }
        buf.append(result.getMessage());
      }
    }
    return buf.toString();
  }

  /**
   * Maps binding paths.<br>
   * Translates one binding path (usually a validation path from a validation result) to
   * binding paths and their corresponding binder. Binders may be nested as it is often
   * the case in UIs.
   *
   * @param mappers the validation mappers
   * @param binder the top level binder
   * @param bindingPath the binding path
   * @return the mapped binder and binding path
   */
  public MapResult mapValidationPath(NavigableSet<ValidationMapper> mappers, Binder binder, String bindingPath) {

    Binder currentBinder = binder;
    String currentPath = bindingPath;

    if (mappers != null) {
      // translate validation path to binding path and optionally switch to other binders
      Set<String> doneSet = new HashSet<>();  // to detect binder/mapper loops
      for (;;) {
        /*
         * The binding path from the results is always longer than the binding path of the validation mapper,
         * hence lowerEntry.
         */
        ValidationMapper mapper = mappers.lower(new ValidationMapper(currentPath, currentBinder, null, null));
        if (mapper != null) {
          // some mapper found
          if (currentPath.startsWith(mapper.getValidationPath()) &&
              (mapper.getBinder() == null || mapper.getBinder().equals(currentBinder))) {

            currentPath = mapper.map(currentPath);

            if (mapper.getNextBinder() != null) {
              currentBinder = mapper.getNextBinder();
              LOGGER.fine("{0}:{1} translated to {2}:{3}",
                          binder.getInstanceNumber(), bindingPath, currentBinder.getInstanceNumber(), currentPath);
            }
            else {
              LOGGER.fine("{0}:{1} translated to {2}",
                          binder.getInstanceNumber(), bindingPath, currentPath);
              break;
            }
          }
          else {
            break;
          }
        }
        else {
          break;
        }

        // add current binder + binding path to already done set
        if (!doneSet.add(currentBinder.getInstanceNumber() + ":" + currentPath)) {
          LOGGER.warning("validation mapper loop detected for {0}:{1}", currentBinder.getInstanceNumber(), currentPath);
          break;
        }
      }
    }

    return new MapResult(currentBinder, currentPath);
  }

  /**
   * Gets a formatted and localized string.
   * <p>
   * Instead of
   * <pre>java.text.MessageFormat.format(my.domain.application.package.Bundle.getString("{0}_is_wrong"), value)</pre>
   * you can write in a Groovy-script:
   * <pre>ValidationUtilities.getInstance().format(clazz, '{0}_is_wrong', value)</pre>
   * Or much shorter directly as a message parameter in the validator:
   * <pre>message={ @('{0}_is_wrong', value) }</pre>
   * <p>
   * This method will look up a class named <code>ValidationBundle</code> in the package or super-packages of where the validator is used
   * and invokes its public static method {@code getString(String)}.
   *
   * @param clazz the class to look for <code>ValidationBundle</code> in the same package or its super-packages
   * @param pattern the message pattern
   * @param args arguments to pattern
   * @return the formatted and localized string, null if pattern is null
   * @throws ValidationRuntimeException if clazz is null, no valid {@code ValidationBundle} was found,
   *         or the bundle provides no translation for the pattern
   *
   * @see MessageFormat
   */
  public String format(Class<?> clazz, String pattern, Object... args) {

    if (pattern != null) {
      if (clazz == null) {
        throw new ValidationRuntimeException("clazz is necessary to determine package");
      }
      // find package of given object
      String packageName = clazz.getPackageName();

      // go up the package hierarchy until a class ValidationBundle is found
      for (;;) {
        Class<?> bundleClass = null;
        try {
          String bundleName = packageName + (packageName.isEmpty() ? "" : ".") + "ValidationBundle";
          bundleClass = getValidationBundleClassLoader().loadClass(bundleName);
          // class must contain a public static method getString(String.class)
          Method bundleMethod = bundleClass.getDeclaredMethod("getString", String.class);
          if (!bundleMethod.getReturnType().equals(String.class)) {
            throw new ValidationRuntimeException(bundleClass.getName() + "." + bundleMethod.getName() + " does not return a String");
          }
          if (!Modifier.isStatic(bundleMethod.getModifiers())) {
            throw new ValidationRuntimeException(bundleClass.getName() + "." + bundleMethod.getName() + " is not static");
          }
          if (!Modifier.isPublic(bundleMethod.getModifiers())) {
            throw new ValidationRuntimeException(bundleClass.getName() + "." + bundleMethod.getName() + " is not public");
          }
          // okay, here we go...
          try {
            String localizedPattern = (String) bundleMethod.invoke(null, pattern);
            return MessageFormat.format(localizedPattern, args);
          }
          catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new ValidationRuntimeException("no localization for '" + pattern + "' in locale " +
                                                 LocaleProvider.getInstance().getLocale(), ex);
          }
        }
        catch (ClassNotFoundException e1) {
          int ndx = packageName.lastIndexOf('.');
          if (ndx > 0) {
            packageName = packageName.substring(0, ndx);
          }
          else if (!packageName.isEmpty()) {
            packageName = "";
          }
          else {
            break;
          }
        }
        catch (NoSuchMethodException e2) {
          throw new ValidationRuntimeException(bundleClass.getName() + " does not provide the method getString()");
        }
      }

      throw new ValidationRuntimeException("no ValidationBundle found for " + clazz);
    }

    return null;
  }

  /**
   * Gets a formatted and localized string.
   *
   * @param object the object to look for <code>ValidationBundle</code> in the same package
   * @param pattern the message pattern
   * @param args arguments to pattern
   * @return the formatted and localized string
   *
   * @see #format(java.lang.Class, java.lang.String, java.lang.Object...)
   */
  public String format(Object object, String pattern, Object... args) {
    Class<?> clazz;
    if (object instanceof Class) {
      // object itself is a class
      clazz = (Class<?>) object;
    }
    else {
      clazz = EffectiveClassProvider.getEffectiveClass(object);
    }
    return format(clazz, pattern, args);
  }


  /**
   * Adds all validators for all annotated fields or methods of the given class to a map.<br>
   * If an attribute is annotated both at the field and the (getter-)method level,
   * the validators of both are applied.
   *
   * @param validatorMap the validator map
   * @param clazz the class to inspect
   * @param scope optional validation scope, null = all
   */
  protected void addFieldValidatorsToMap(Map<ValidatorKey,Validator> validatorMap, Class<?> clazz, ValidationScope scope) {
    Holder<Integer> validationIndexHolder = new Holder<>(0);
    for (Field field: ReflectionHelper.getAllFields(clazz, null, true, null, true)) {
      for (Validator validator: getElementValidators(field, scope, validationIndexHolder)) {
        LOGGER.fine("field validator @{0} found for {1}:{2}",
                    validator.getClass().getName(), clazz.getName(), field.getName());
        validatorMap.put(new ValidatorKey(validator), validator);
      }
    }

    for (Method method: ReflectionHelper.getAllMethods(clazz, null, true, null, true)) {
      if (!method.isBridge() && ReflectionHelper.isGetter(method)) {
        for (Validator validator: getElementValidators(method, scope, validationIndexHolder)) {
          LOGGER.fine("method validator @{0} found for {1}:{2}",
                      validator.getClass().getName(), clazz.getName(), method.getName());
          validatorMap.put(new ValidatorKey(validator), validator);
        }
      }
    }
  }

  /**
   * Adds the validators for an annotated field and/or method to a map.
   *
   * @param validatorMap the validator map
   * @param field the field, null if none
   * @param method the (getter-)method, null if none
   * @param scope optional validation scope, null = all
   */
  protected void addFieldValidatorsToMap(Map<ValidatorKey,Validator> validatorMap, Field field, Method method, ValidationScope scope) {
    boolean fineLoggable = LOGGER.isFineLoggable();  // faster than lambda because only one check if fine is not loggable
    Holder<Integer> validationIndexHolder = new Holder<>(0);
    if (field != null) {
      for (Validator validator: getElementValidators(field, scope, validationIndexHolder)) {
        if (fineLoggable) {
          LOGGER.fine("field validator @{0} found for {1}:{2}",
                      validator.getClass().getName(), field.getDeclaringClass().getName(), field.getName());
        }
        validatorMap.put(new ValidatorKey(validator), validator);
      }
    }
    if (method != null && ReflectionHelper.isGetter(method)) {
      for (Validator validator: getElementValidators(method, scope, validationIndexHolder)) {
        if (fineLoggable) {
          LOGGER.fine("method validator @{0} found for {1}:{2}",
                      validator.getClass().getName(), method.getDeclaringClass().getName(), method.getName());
        }
        validatorMap.put(new ValidatorKey(validator), validator);
      }
    }
  }

  /**
   * Sorts validators by priority and validation index.<br>
   * The lowest priority (0) comes last, the highest first.
   *
   * @param validators the validators
   */
  protected void sortValidators(List<Validator> validators) {
    // the list does not contain null-values, hence no null checks
    validators.sort((o1, o2) -> {
      int rv = o2.getPriority() - o1.getPriority();
      if (rv == 0) {
        rv = o1.getValidationIndex() - o2.getValidationIndex();
      }
      return rv;
    });
  }

  /**
   * Gets the member-name of the annotated element.
   *
   * @param element the annotated element
   * @return the element's name
   */
  protected String getAnnotatedElementName(AnnotatedElement element) {
    return switch (element) {
      case Field field -> field.getName();
      case Method method -> ReflectionHelper.getGetterPathName(method);
      case Class<?> clazz -> clazz.getName();
      case null, default -> throw new ValidationRuntimeException("cannot determine element name");
    };
  }

  /**
   * Extracts the validators for an annotation.<br>
   * Supports repeated annotations as well.
   *
   * @param annotation the annotation
   * @param element the annotated element
   * @param validators the validation annotation(s)
   * @param scope the validation scope, null = all
   * @param validationIndexHolder the holder of the next validation index
   */
  protected void extractValidators(Annotation annotation, AnnotatedElement element,
                                   List<Validator> validators, ValidationScope scope, Holder<Integer> validationIndexHolder) {

    // check the annotations present on given annotation
    for (Annotation anno: annotation.annotationType().getAnnotations()) {
      if (anno instanceof Validation validation) {
        Class<? extends Validator> validatorClazz = validation.value();   // get validator implementation class
        addValidator(annotation, element, validationIndexHolder.get(), validators, scope, validatorClazz);
        validationIndexHolder.accept(validationIndexHolder.get() + 1);
      }
      else if (anno instanceof RepeatableValidation) {
        Class<? extends Annotation> repeatedAnnoClazz = ((RepeatableValidation) anno).value();
        Annotation[] repeatedAnnotations = element.getAnnotationsByType(repeatedAnnoClazz);   // this returns proxies!
        /*
         * We need to analyze the annotations of repeatedAnnotations again, because the
         * returned proxies are not instanceof Validation. (is this a bug in Java8?)
         */
        for (Annotation repeatedAnno: repeatedAnnotations) {
          for (Annotation anno2: repeatedAnno.annotationType().getAnnotations()) {
            if (anno2 instanceof Validation validation) {
              Class<? extends Validator> validatorClazz = validation.value();   // get validator implementation class
              addValidator(repeatedAnno, element, validationIndexHolder.get(), validators, scope, validatorClazz);
              validationIndexHolder.accept(validationIndexHolder.get() + 1);
            }
          }
        }
      }
    }
  }

  /**
   * Adds a validator to a list.
   *
   * @param annotation the annotation
   * @param element the annotated element
   * @param validationIndex the invocation index
   * @param validators the list of validators
   * @param scope the validation scope
   * @param validatorClass the class to create a validator instance from
   * @throws ValidationRuntimeException if the validator could not be created
   */
  protected void addValidator(Annotation annotation, AnnotatedElement element, int validationIndex,
                              List<Validator> validators, ValidationScope scope,
                              Class<? extends Validator> validatorClass) {
    try {
      Validator validator = validatorClass.getDeclaredConstructor().newInstance();
      validator.setAnnotation(annotation);
      validator.setAnnotatedElement(element);
      validator.setValidatedElementName(getAnnotatedElementName(element));
      validator.setValidationIndex(validationIndex);
      if (scope == null || scope.appliesTo(validator.getConfiguredScopes(null))) {
        validators.add(validator);
      }
    }
    catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException |
           InvocationTargetException ex) {
      throw new ValidationRuntimeException("could not create validator", ex);
    }
  }

  /**
   * Gets all validators for an annotated attribute.
   *
   * @param element the {@link Field} or {@link Method} to inspect
   * @param scope optional validation scope, null = all
   * @param validationIndexHolder the holder of the next validation index
   * @return the list of field validators
   */
  protected List<Validator> getElementValidators(AnnotatedElement element, ValidationScope scope, Holder<Integer> validationIndexHolder) {
    List<Validator> validators = new ArrayList<>();
    for (Annotation annotation: element.getAnnotations()) {
      extractValidators(annotation, element, validators, scope, validationIndexHolder);
    }
    return validators;
  }


  private void logTest(Validator validator, ValidationContext validationContext) {
    LOGGER.log(testLogLevel, "applying " + validator + " to " + validationContext);
  }

}
