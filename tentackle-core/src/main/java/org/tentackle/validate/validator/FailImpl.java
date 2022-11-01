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
import org.tentackle.validate.ValidationFailedException;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationSeverity;
import org.tentackle.validate.ValidationUtilities;
import org.tentackle.validate.severity.DefaultSeverity;

import java.lang.annotation.Annotation;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of the {@link Fail}-validator.
 *
 * @author harald
 */
public final class FailImpl extends AbstractValidator<Fail> {

  private Fail annotation;

  /**
   * Creates a {@link Fail}-validator.
   */
  public FailImpl() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void setAnnotation(Annotation annotation) {
    this.annotation = (Fail) annotation;
  }

  @Override
  public Fail getAnnotation() {
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
  public List<? extends ValidationResult> validate(ValidationContext validationContext) {
    if (switch (annotation.value()) {
      case FAILED -> ValidationUtilities.getInstance().hasFailed(validationContext.getValidationResults());
      case ANY -> !validationContext.getValidationResults().isEmpty();
      case IGNORED -> true;
    }) {
      // message is not added to the results, just informational for the stacktrace (thus not translated)
      throw new ValidationFailedException(MessageFormat.format("validation aborted at {0}", validationContext),
                                          validationContext.getValidationResults());
    }
    return Collections.emptyList();
  }

}
