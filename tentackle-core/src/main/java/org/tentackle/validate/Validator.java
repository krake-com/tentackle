/**
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


package org.tentackle.validate;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.List;

/**
 * The validator.
 * <p>
 * IMPORTANT: validators are cached and created only once per class and annotation.
 * Therefore, validators MUST NOT maintain any state beyond their initial configuration!
 * State values are passed via {@link ValidationContext}.
 *
 * @author harald
 */
public interface Validator {

  // ----------------------- annotation parameters -------------------------

  /**
   * Gets the message text set by the annotation's message()-parameter.
   *
   * @return the message, null if use message from validator
   */
  String getMessage();

  /**
   * Gets the optional error code.<br>
   * The error code is used to identify the error by a unique
   * code instead of some message string.
   *
   * @return the error code
   */
  String getErrorCode();

  /**
   * Gets the severity set by the annotation's severity()-parameter.
   * @return the severity
   */
  Class<? extends ValidationSeverity>[] getSeverity();

  /**
   * Gets the priority set by the annotation's priority()-parameter.
   * @return the priority
   */
  int getPriority();

  /**
   * Gets the validation scopes set by the annotation's scopes()-parameter
   * @return
   */
  Class<? extends ValidationScope>[] getScope();

  /**
   * Determines whether the validation process stops if this validator fails.
   *
   * @return true if stop, false if continue with other validators
   */
  boolean getStopIfFailed();




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
   * @return the element, null if dynamic validator
   */
  AnnotatedElement getAnnotatedElement();


  /**
   * Sets the name of the validated element.
   *
   * @param name
   */
  void setValidatedElementName(String name);

  /**
   * Gets the name of the validated element.
   * @return
   */
  String getValidatedElementName();


  /**
   * Sets the index of this validator for a {@link RepeatableValidator}.
   *
   * @param index the index starting at 0, -1 if single validator
   */
  void setValidationIndex(int index);

  /**
   * Sets the index of this validator for a {@link RepeatableValidator}.
   *
   * @return the index in a {@link RepeatableValidator}, -1 if single validator
   */
  int getValidationIndex();


  // ----------------------- runtime validation -------------------------

  /**
   * Gets the configured scopes for this annotation.
   * <p>
   * If the annotation has the {@link DefaultScope} configured
   * the rules defined in {@link ScopeConfigurator} apply.
   * Otherwise the annotations parameter will be returned.
   *
   * @param validationContext the validation context
   * @return the configured scopes
   */
  Class<? extends ValidationScope>[] getConfiguredScopes(ValidationContext validationContext);


  /**
   * Checks whether the validator applies at all.
   *
   * @param validationContext the validation context
   * @return true if it applies
   */
  boolean isConditionValid(ValidationContext validationContext);


  /**
   * Performs the validation.
   *
   * @param validationContext the validation context
   * @return the validation results, null or empty if valid and no messages
   */
  List<? extends ValidationResult> validate(ValidationContext validationContext);

}
