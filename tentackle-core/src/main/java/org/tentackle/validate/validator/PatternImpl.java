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


package org.tentackle.validate.validator;

import java.util.List;
import org.tentackle.misc.CompoundValue;
import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationRuntimeException;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationSeverity;

/**
 * Implementation of the {@link Pattern}-validator.
 *
 * @author harald
 */
public class PatternImpl extends AbstractValidator<Pattern> {


  protected volatile CompoundValue patternParameter;   // the regular expression
  protected volatile java.util.regex.Pattern regex;    // the compiled pattern



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
  public boolean getStopIfFailed() {
    return annotation.stop();
  }


  @Override
  public List<? extends ValidationResult> validate(ValidationContext validationContext) {
    Object object = validationContext.getObject();
    if (object == null) {
      return null;
    }
    if (object instanceof String) {
      String value = annotation.value();
      if (getPattern(validationContext).matcher((String) object).matches()) {
        return null;    // okay
      }
      return createFailedValidationResult("'" + object + "' does not match '" + value + "'", validationContext);
    }
    else  {
      throw new ValidationRuntimeException("not a String");
    }
  }



  /**
   * Gets the current pattern.
   *
   * @param validationContext the validation context
   * @return the pattern
   */
  protected java.util.regex.Pattern getPattern(ValidationContext validationContext) {
    CompoundValue localPar = patternParameter;
    if (localPar == null) {
      synchronized (this) {
        localPar = patternParameter;
        if (localPar == null) {
          // determine the parameter once
          localPar = patternParameter = createCompoundValue(annotation.value(), String.class, VALIDATION_CONVERTER_MAP::get);
        }
      }
    }

    java.util.regex.Pattern localRegex = regex;
    if (localRegex == null || localPar.getType() != CompoundValue.Type.CONSTANT) {
      String pattern = (String) localPar.getValue(validationContext.getParentObject(), createScriptVariables(validationContext));
      localRegex = regex = java.util.regex.Pattern.compile(pattern, annotation.flag());
    }
    return localRegex;
  }

}
