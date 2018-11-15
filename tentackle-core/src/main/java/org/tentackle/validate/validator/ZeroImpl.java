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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.tentackle.common.BMoney;
import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationSeverity;

/**
 * Implementation of the {@link Zero}-validator.
 *
 * @author harald
 */
public class ZeroImpl extends AbstractValidator<Zero> {


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

  @SuppressWarnings("unchecked")
  @Override
  public List<? extends ValidationResult> validate(ValidationContext validationContext) {
    Object value = annotation.value().isEmpty() ?
                        // use object's value
                        validationContext.getObject() :
                        // replace with compound value which should be a script
                        getValue(annotation.value(), validationContext);

    if (value == null) {
      return annotation.ignoreNulls() ? Collections.EMPTY_LIST :
          createFailedValidationResult("value is null", validationContext);
    }

    if (value instanceof Number) {

      final boolean zero;

      if (value instanceof BMoney) {
        zero = ((BMoney) value).isZero();
      }
      else if (value instanceof BigInteger) {
        zero = value.equals(BigInteger.ZERO);
      }
      else if (value instanceof BigDecimal) {
        zero = value.equals(BigDecimal.ZERO);
      }
      else if (value instanceof Double) {
        zero = ((Double) value) == 0.0d;
      }
      else if (value instanceof Float) {
        zero = ((Float) value) == 0.0f;
      }
      else {
        zero = ((Number) value).longValue() == 0;
      }

      return zero ? Collections.EMPTY_LIST : createFailedValidationResult("value is not zero", validationContext);
    }
    else  {
      return createFailedValidationResult("not a number", validationContext);
    }
  }

}
