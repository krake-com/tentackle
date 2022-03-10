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

package org.tentackle.security.pdo;

import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationSeverity;
import org.tentackle.validate.ValidationUtilities;
import org.tentackle.validate.validator.AbstractObjectValidator;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

/**
 * Security validator implementation.
 *
 * @author harald
 */
public class SecurityValidatorImpl extends AbstractObjectValidator<SecurityValidator> {

  private SecurityValidator annotation;

  @Override
  @SuppressWarnings("unchecked")
  public void setAnnotation(Annotation annotation) {
    this.annotation = (SecurityValidator) annotation;
  }

  @Override
  public SecurityValidator getAnnotation() {
    return annotation;
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
  public List<? extends ValidationResult> validate(ValidationContext validationContext) {
    List<ValidationResult> results = null;
    Object object = validationContext.getObject();
    if (object instanceof Security security) {
      results = new ArrayList<>();
      /*
       * was via scripting formerly -> replaced by object validator to avoid requirement for scripting at all.
       * objectClassName: @NotNull(condition="{object.objectClassId==0}", message="{ @('missing object classname') }")
       * objectClassId: @NotNull(condition="{object.objectClassName==null}", message="{ @('missing object class-ID') }")
       * permissions: @NotNull(message="{ @('missing permissions') }")
       */
      if (security.getObjectClassId() == 0) {
        if (security.getObjectClassName() == null) {
          results.add(ValidationUtilities.getInstance().createValidationResult(
              this, validationContext, ValidationBundle.getString("missing object classname")));
        }
      }
      else if (security.getObjectClassName() != null) {
        results.add(ValidationUtilities.getInstance().createValidationResult(
            this, validationContext, ValidationBundle.getString("object classname must be null")));
      }
      if (security.getPermissions() == null) {
        results.add(ValidationUtilities.getInstance().createValidationResult(
            this, validationContext, ValidationBundle.getString("missing permissions")));
      }
    }
    return results;
  }

}
