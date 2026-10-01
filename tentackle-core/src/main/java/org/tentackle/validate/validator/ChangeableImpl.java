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

import org.tentackle.validate.ChangeableBindingEvaluator;
import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationSeverity;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of the {@link Changeable}-validator.
 *
 * @author harald
 */
public final class ChangeableImpl extends AbstractValidator<Changeable> implements ChangeableBindingEvaluator {

  private Changeable annotation;

  /**
   * Creates a {@link Changeable}-validator.
   */
  public ChangeableImpl() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void setAnnotation(Annotation annotation) {
    this.annotation = (Changeable) annotation;
  }

  @Override
  public Changeable getAnnotation() {
    return annotation;
  }

  @Override
  public String getValue() {
    return annotation.value();
  }

  @Override
  public String getMessage() {
    return annotation.message();
  }

  @Override
  public String getErrorCode() {
    return annotation.error();
  }

  @Override
  public Class<? extends ValidationSeverity> getSeverity() {
    return annotation.severity();
  }

  @Override
  public int getPriority() {
    return annotation.priority();
  }

  @Override
  public Class<? extends ValidationScope>[] getScope() {
    return annotation.scope();
  }

  @Override
  public String getCondition() {
    return annotation.condition();
  }

  /**
   * {@inheritDoc}
   * <p>
   * If the annotation has a condition set, the changeable property is dynamic.
   */
  @Override
  public boolean isChangeableDynamic() {
    return !annotation.condition().isEmpty();
  }

  /**
   * {@inheritDoc}
   * <p>
   * The element is changeable if the value is empty or evaluates to {@code true}, and the condition,
   * if any, is met.
   *
   * @return true if changeable
   */
  @Override
  public boolean isChangeable(ValidationContext validationContext) {
    return (getValue().isEmpty() || getValue(Boolean.class, validationContext)) &&
           isConditionValid(validationContext);
  }

  @Override
  public List<? extends ValidationResult> validate(ValidationContext validationContext) {
    return Collections.emptyList();    // validation is always ok
  }

}
