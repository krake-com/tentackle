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
import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationSeverity;
import org.tentackle.validate.ValidatorCompoundValueFactory;

import java.lang.annotation.Annotation;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of the {@link Pattern}-validator.
 *
 * @author harald
 */
public final class PatternImpl extends AbstractValidator<Pattern> {

  private Pattern annotation;                         // the annotation
  private volatile CompoundValue patternParameter;    // the regular expression
  private volatile java.util.regex.Pattern regex;     // the compiled pattern

  /**
   * Creates a {@link Pattern}-validator.
   */
  public PatternImpl() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void setAnnotation(Annotation annotation) {
    this.annotation = (Pattern) annotation;
  }

  @Override
  public Pattern getAnnotation() {
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
    Object object = validationContext.getObject();

    if (object == null) {
      return annotation.ignoreNulls() ? Collections.emptyList() :
             createValidationResultList("object is null", validationContext);
    }
    java.util.regex.Pattern pattern = getPattern(validationContext);
    return pattern.matcher(object.toString()).matches() ? Collections.emptyList() :
           createValidationResultList(MessageFormat.format(ValidatorBundle.getString("{0} does not match {1}"), validationContext, pattern.pattern()), validationContext);
  }



  /**
   * Gets the current pattern.
   *
   * @param validationContext the validation context
   * @return the pattern
   */
  public java.util.regex.Pattern getPattern(ValidationContext validationContext) {
    CompoundValue localPar = patternParameter;
    if (localPar == null) {
      synchronized (this) {
        localPar = patternParameter;
        if (localPar == null) {
          // determine the parameter once
          localPar = patternParameter = ValidatorCompoundValueFactory.getInstance().createValueParameter(getValue(), String.class);
        }
      }
    }

    java.util.regex.Pattern localRegex = regex;
    if (localRegex == null || localPar.getType() != CompoundValue.Type.CONSTANT) {
      String pattern = (String) localPar.getValue(validationContext.getParentObject(), createScriptVariables(localPar, validationContext));
      localRegex = regex = java.util.regex.Pattern.compile(pattern, annotation.flag());
    }
    return localRegex;
  }

}
