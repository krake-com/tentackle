/*
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


package org.tentackle.validate.validator;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.tentackle.validate.MandatoryBindingEvaluator;
import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationRuntimeException;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationSeverity;

/**
 * Implementation of the {@link NotEmpty}-validator.
 *
 * @author harald
 */
public class NotEmptyImpl extends AbstractValidator<Size> implements MandatoryBindingEvaluator {

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

  /**
   * {@inheritDoc}
   * <p>
   * If the annotation has a condition set, the mandatory property is dynamic.
   */
  @Override
  public boolean isMandatoryDynamic() {
    return !annotation.condition().isEmpty();
  }

  @Override
  public boolean isMandatory(ValidationContext validationContext) {
    return isConditionValid(validationContext) && validate(validationContext) != null;
  }

  @Override
  public List<? extends ValidationResult> validate(ValidationContext validationContext) {
    Object object = validationContext.getObject();

    if (object == null) {
      return createFailedValidationResult("object is null", validationContext);
    }

    boolean empty;
    if (object instanceof String) {
      empty = ((String) object).isEmpty();
    }
    else if (object instanceof Collection) {
      empty = ((Collection) object).isEmpty();
    }
    else if (object instanceof Map) {
      empty = ((Map) object).isEmpty();
    }
    else if (object instanceof Array) {
      empty = Array.getLength(object) == 0;
    }
    else  {
      throw new ValidationRuntimeException("unsupported type: " + object.getClass().getName());
    }

    if (empty) {
      return createFailedValidationResult("object is empty", validationContext);
    }
    return null;
  }

}
