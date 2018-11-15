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

import java.util.Collections;
import java.util.List;
import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationRuntimeException;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationSeverity;

/**
 * Implementation of the {@link Less}-validator.
 *
 * @author harald
 */
public class LessImpl extends AbstractValidator<Less> {

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

    String valueStr = annotation.value();
    Comparable<Object> object = assertComparable(validationContext.getObject());
    Comparable<Object> value = assertComparable(getValue(valueStr, validationContext));

    if (value == null) {
      // both null isn't less, except if nulls should be ignored
      failed = !annotation.ignoreNulls() && object == null;
    }
    else {
      if (object == null) {
        // object is always logically less than value whether ignoreNulls or not
        failed = false;
      }
      else {
        try {
          failed = object.compareTo(value) >= 0;
        }
        catch (RuntimeException rex) {
          throw new ValidationRuntimeException("comparison failed", rex);
        }
      }
    }

    return failed ? createFailedValidationResult(object + " >= " + valueStr, validationContext) : Collections.EMPTY_LIST;
  }

}
