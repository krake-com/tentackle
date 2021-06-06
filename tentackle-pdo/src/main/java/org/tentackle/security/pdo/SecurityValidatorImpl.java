/*
 * Copyright (C) 2015 by:
 *
 * Hydro Aluminium Rolled Products GmbH
 * Koblenzer Strasse 122
 * D-41468 Neuss
 * Germany
 * Tel. +49 2131 382 0
 * http://www.hydro.com/Deutschland
 *
 * All Rights Reserved.
 */

package org.tentackle.security.pdo;

import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.ValidationSeverity;
import org.tentackle.validate.validator.AbstractObjectValidator;
import org.tentackle.validate.validator.FailedValidationResult;

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
  public List<? extends ValidationResult> validate(ValidationContext validationContext) {
    List<ValidationResult> results = null;
    Object object = validationContext.getObject();
    if (object instanceof Security) {
      Security security = (Security) object;
      results = new ArrayList<>();
      /**
       * // was via scripting formerly -> replaced by object validator to avoid requirement for scripting at all
       * objectClassName: @NotNull(condition="{object.objectClassId==0}", message="{ @('missing object classname') }")
       * objectClassId: @NotNull(condition="{object.objectClassName==null}", message="{ @('missing object class-ID') }")
       * permissions: @NotNull(message="{ @('missing permissions') }")
       */
      if (security.getObjectClassId() == 0) {
        if (security.getObjectClassName() == null) {
          results.add(new FailedValidationResult(this, validationContext,
                                                 ValidationBundle.getString("missing object classname")));
        }
      }
      else if (security.getObjectClassName() != null) {
        results.add(new FailedValidationResult(this, validationContext,
                                               ValidationBundle.getString("object classname must be null")));
      }
      if (security.getPermissions() == null) {
        results.add(new FailedValidationResult(this, validationContext,
                                               ValidationBundle.getString("missing permissions")));
      }
    }
    return results;
  }

}
