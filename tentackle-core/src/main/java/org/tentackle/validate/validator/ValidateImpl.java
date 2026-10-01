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

import org.tentackle.validate.Validateable;
import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationRuntimeException;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationSeverity;
import org.tentackle.validate.ValidationUtilities;
import org.tentackle.validate.severity.DefaultSeverity;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;

/**
 * Implementation of the {@link Validate}-validator.
 *
 * @author harald
 */
public final class ValidateImpl extends AbstractValidator<Validate> {

  private Validate annotation;

  /**
   * Creates a {@link Validate}-validator.
   */
  public ValidateImpl() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void setAnnotation(Annotation annotation) {
    this.annotation = (Validate) annotation;
  }

  @Override
  public Validate getAnnotation() {
    return annotation;
  }

  @Override
  public String getValue() {
    return "";
  }

  @Override
  public String getMessage() {
    return "";
  }

  @Override
  public String getErrorCode() {
    return "";
  }

  @Override
  public Class<? extends ValidationSeverity> getSeverity() {
    return DefaultSeverity.class;   // never used anyway since this validator does not create its own validation result
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
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public List<? extends ValidationResult> validate(ValidationContext validationContext) {
    List<? extends ValidationResult> results;
    Object object = validationContext.getObject();
    if (object instanceof Validateable validateable) {
      // redirect to the object in case the validate-method is overridden (see Validateable)
      results = validateable.validate(validationContext.getValidationPath(), validationContext.getEffectiveScope());
    }
    else if (object instanceof Collection) {
      results = ValidationUtilities.getInstance().validateCollection((Collection) object, validationContext.getValidationPath(),
                                                                     validationContext.getEffectiveScope());
    }
    else if (object == null) {
      results = List.of();
    }
    else {
      throw new ValidationRuntimeException(validationContext.getValidationPath() + " is not validateable");
    }
    return results;
  }

}
