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

import org.tentackle.misc.CompoundValue;
import org.tentackle.script.ScriptVariable;
import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationSeverity;

import java.util.Collections;
import java.util.List;

/**
 * Implementation of the {@link Pattern}-validator.
 *
 * @author harald
 */
public class PatternImpl extends AbstractValidator<Pattern> {


  protected CompoundValue patternParameter;   // the regular expression
  protected java.util.regex.Pattern regex;    // the compiled pattern



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
    Object object = validationContext.getObject();

    if (object == null) {
      return annotation.ignoreNulls() ? Collections.EMPTY_LIST :
          createFailedValidationResult("object is null", validationContext);
    }
    java.util.regex.Pattern pattern = getPattern(validationContext);
    return pattern.matcher(object.toString()).matches() ? Collections.EMPTY_LIST :
        createFailedValidationResult("'" + object + "' does not match '" + pattern.pattern() + "'", validationContext);
  }



  /**
   * Gets the current pattern.
   *
   * @param validationContext the validation context
   * @return the pattern
   */
  protected java.util.regex.Pattern getPattern(ValidationContext validationContext) {
    if (patternParameter == null) {
      // determine the parameter once
      patternParameter = createCompoundValue(annotation.value(), String.class);
    }
    if (regex == null || patternParameter.getType() != CompoundValue.Type.CONSTANT) {
      Object parentObject = validationContext.getParentObject();
      Object object = validationContext.getObject();
      ScriptVariable[] variables = new ScriptVariable[]{
        new ScriptVariable("value", object)
      };
      String pattern = (String) patternParameter.getValue(parentObject, variables);
      regex = java.util.regex.Pattern.compile(pattern, annotation.flag());
    }
    return regex;
  }

}
