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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.List;

/**
 * A validator.
 * <p>
 * IMPORTANT: validators are cached and created only once per annotated element and annotation.
 * Therefore, validators MUST NOT maintain any state beyond their initial configuration!
 * State values are passed via {@link ValidationContext}.
 *
 * @author harald
 */
public interface Validator {

  // ----------------------- annotation parameters -------------------------

  /**
   * Gets the annotation's value.
   *
   * @return the value, never null
   */
  String getValue();

  /**
   * Gets the message text set by the annotation's message()-parameter.
   *
   * @return the message text, never null, empty if message provided by the validate method
   * @see #validate(ValidationContext)
   */
  String getMessage();

  /**
   * Gets the condition.
   *
   * @return the condition, never null, empty if none
   */
  String getCondition();

  /**
   * Gets the optional error code.<br>
   * The error code is used to identify the error by a unique
   * code instead of some message string.
   *
   * @return the error code, empty if none
   */
  String getErrorCode();

  /**
   * Gets the severity set by the annotation's severity()-parameter.
   *
   * @return the severity
   */
  Class<? extends ValidationSeverity> getSeverity();

  /**
   * Gets the priority set by the annotation's priority()-parameter.<br>
   * Highest priority will be invoked first.
   * <p>
   * By default, validators are invoked in the order they appear in the source file,
   * if no priority is explicitly set.
   *
   * @return the priority, 0 is lowest (default)
   */
  int getPriority();

  /**
   * Gets the validation scopes set by the annotation's scopes()-parameter.
   *
   * @return the validation scopes
   */
  Class<? extends ValidationScope>[] getScope();



  // ----------------------- configuration -------------------------


  /**
   * Sets the annotation of the validator.
   *
   * @param annotation the annotation
   */
  void setAnnotation(Annotation annotation);

  /**
   * Gets the annotation of this validator.
   *
   * @return the annotation
   */
  Annotation getAnnotation();


  /**
   * Sets the annotated element.<p>
   * The method is only invoked if this is a fixed validator, i.e.
   * created by an annotation.
   *
   * @param element the element
   */
  void setAnnotatedElement(AnnotatedElement element);

  /**
   * Gets the annotated element.
   *
   * @return the element, null if dynamic validator
   */
  AnnotatedElement getAnnotatedElement();


  /**
   * Sets the name of the validated element.
   *
   * @param name the element name
   */
  void setValidatedElementName(String name);

  /**
   * Gets the name of the validated element.
   *
   * @return the element name
   */
  String getValidatedElementName();


  /**
   * Sets the invocation index of this validator.
   *
   * @param index the index starting at 0
   */
  void setValidationIndex(int index);

  /**
   * Gets the invocation index of this validator.
   *
   * @return the index
   */
  int getValidationIndex();


  /**
   * Validates the validator's configuration.<br>
   * Checks the references and parses the scripts, if any.
   *
   * @throws ValidationRuntimeException if the configuration is invalid
   */
  void validate();


  // ----------------------- runtime validation -------------------------

  /**
   * Gets the configured scopes for this annotation.
   * <p>
   * If the annotation has the {@link DefaultScope} configured, i.e. no other scopes mentioned in the annotation,
   * the rules defined in the context's parent object apply, if the parent is a {@link ScopeConfigurator}.
   * Otherwise, the annotation's parameter will be returned unchanged.
   *
   * @param validationContext the validation context (can be null)
   * @return the configured scopes
   */
  Class<? extends ValidationScope>[] getConfiguredScopes(ValidationContext validationContext);


  /**
   * Checks whether the validator applies at all.
   *
   * @param validationContext the validation context
   * @return true if it applies or no condition (empty)
   */
  boolean isConditionValid(ValidationContext validationContext);


  /**
   * Performs the validation.
   *
   * @param validationContext the validation context
   * @return immutable list of validation results, empty if valid and no messages
   */
  List<? extends ValidationResult> validate(ValidationContext validationContext);

}
