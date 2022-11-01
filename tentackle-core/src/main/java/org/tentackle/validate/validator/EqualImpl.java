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
 * Implementation of the {@link Equal}-validator.
 *
 * @author harald
 */
public final class EqualImpl extends AbstractValidator<Equal> {

  private Equal annotation;

  /**
   * Creates a {@link Equal}-validator.
   */
  public EqualImpl() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void setAnnotation(Annotation annotation) {
    this.annotation = (Equal) annotation;
  }

  @Override
  public Equal getAnnotation() {
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

  @Override
  public List<? extends ValidationResult> validate(ValidationContext validationContext) {

    final boolean failed;

    Object object = validationContext.getObject();
    Object value = getValue(validationContext);

    if (value == null) {
      failed = !annotation.ignoreNulls() && object != null;
    }
    else {
      if (object == null) {
        failed = !annotation.ignoreNulls();
      }
      else {
        try {
          failed = !object.equals(value);
        }
        catch (RuntimeException rex) {
          throw new ValidationRuntimeException("equals() failed", rex);
        }
      }
    }

    return failed ? createValidationResultList(object + " != " + getValue(), validationContext) : Collections.emptyList();
  }

}
