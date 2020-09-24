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

import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationRuntimeException;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationSeverity;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of the {@link LessOrEqual}-validator.
 *
 * @author harald
 */
public class LessOrEqualImpl extends AbstractValidator<LessOrEqual> {

  private LessOrEqual annotation;

  @Override
  public void setAnnotation(Annotation annotation) {
    this.annotation = (LessOrEqual) annotation;
  }

  @Override
  public LessOrEqual getAnnotation() {
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
  public Class<? extends ValidationSeverity>[] getSeverity() {
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

  @Override
  public List<? extends ValidationResult> validate(ValidationContext validationContext) {

    final boolean failed;
    
    Comparable<Object> object = assertComparable(validationContext.getObject());
    Comparable<Object> value = assertComparable(getValue(validationContext));

    if (object == null) {
      // object is always logically less or equal than value whether ignoreNulls or not
      failed = false;
    }
    else {
      if (value == null) {
        // object != null is always greater, except if ignore nulls
        failed = !annotation.ignoreNulls();
      }
      else {
        try {
          failed = object.compareTo(value) > 0;
        }
        catch (RuntimeException rex) {
          throw new ValidationRuntimeException("comparison failed", rex);
        }
      }
    }

    return failed ? createFailedValidationResult(object + " > " + getValue(), validationContext) : Collections.emptyList();
  }

}
