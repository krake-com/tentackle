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

package org.tentackle.validate.severity;

import org.tentackle.common.StringHelper;
import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationRuntimeException;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.Validator;

import java.io.Serial;
import java.util.Objects;

/**
 * Base implementation for validation results.
 *
 * @author harald
 */
public abstract class AbstractValidationResult implements ValidationResult {

  @Serial
  private static final long serialVersionUID = 1L;

  private final String message;                                       // the validation failed message
  private final String errorCode;                                     // error code
  private final String validationPath;                                // the validation path
  private final Class<? extends ValidationScope>[] configuredScopes;  // the configured scopes

  // transient because those objects are not serializable!
  private final transient ValidationContext validationContext;        // the validation context
  private final transient Validator validator;                        // the validator


  /**
   * Creates a validation result.
   *
   * @param validator the validator
   * @param validationContext the validation context
   * @param message the message
   * @param errorCode the error code, null to take it from the validator (default)
   * @throws NullPointerException if validator or validationContext is null
   */
  public AbstractValidationResult(Validator validator, ValidationContext validationContext, String message, String errorCode) {
    this.validator = Objects.requireNonNull(validator, "validator");
    this.validationContext = Objects.requireNonNull(validationContext, "validationContext");
    this.message = message;
    this.errorCode = errorCode != null ? errorCode : validator.getErrorCode();
    // extract early in case the result is passed back via TRIP to the client
    configuredScopes = validator.getConfiguredScopes(validationContext);
    validationPath = validationContext.getValidationPath();
  }

  /**
   * Creates a validation result.
   *
   * @param validator the validator
   * @param validationContext the validation context
   * @param message the message
   */
  public AbstractValidationResult(Validator validator, ValidationContext validationContext, String message) {
    this(validator, validationContext, message, null);
  }

  /**
   * Creates a validation result from its serialized values.<br>
   * The transient validator and validation context are null.<br>
   * Subclasses must provide a {@link org.tentackle.reflect.CanonicalConstructor}
   * for TRIP that delegates to this constructor.
   *
   * @param message the message
   * @param errorCode the error code
   * @param validationPath the validation path
   * @param configuredScopes the configured scopes
   */
  protected AbstractValidationResult(String message, String errorCode, String validationPath,
                                     Class<? extends ValidationScope>[] configuredScopes) {
    this.validator = null;
    this.validationContext = null;
    this.message = message;
    this.errorCode = errorCode;
    this.configuredScopes = configuredScopes;
    this.validationPath = validationPath;
  }


  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    if (validator != null) {
      buf.append('[').append(validator).append("] ");
    }
    buf.append(validationPath).append(":");
    if (hasMessage()) {
      buf.append(' ').append(getMessage());
    }
    if (hasFailed()) {
      buf.append(" (FAILED)");
    }
    else {
      buf.append(" (PASSED)");
    }
    return buf.toString();
  }

  @Override
  public String getMessage() {
    return message;
  }

  @Override
  public Validator getValidator() {
    return validator;
  }

  @Override
  public boolean hasMessage() {
    return message != null && !message.isEmpty();
  }

  @Override
  public String getErrorCode() {
    return errorCode;
  }

  @Override
  public String getValidationPath() {
    return validationPath;
  }

  @Override
  public ValidationContext getValidationContext() {
    return validationContext;
  }

  @Override
  public Class<? extends ValidationScope>[] getConfiguredScopes() {
    return configuredScopes;
  }

  @Override
  public CollectionIndex[] getValidationCollectionIndexes() {

    int count = StringHelper.countOccurrences(validationPath, '[');

    CollectionIndex[] indexes = new CollectionIndex[count];
    int ndx = 0;

    for (int i=0; i < count; i++) {
      ndx = validationPath.indexOf('[', ndx + 1);
      if (ndx > 0) {
        String pathPrefix = validationPath.substring(0, ndx);
        int ndx2 = validationPath.indexOf(']', ndx);
        if (ndx2 < 0) {
          throw new ValidationRuntimeException(
                  "no closing bracket after '" + pathPrefix + "' in validation path '" + validationPath + "'");
        }
        // the path may end with an index, for example, if an object validator failed for a collection element
        int postfixStart = ndx2 + 1;
        if (postfixStart < validationPath.length() && validationPath.charAt(postfixStart) == '.') {
          postfixStart++;
        }
        String pathPostfix = validationPath.substring(postfixStart);
        int index;
        try {
          index = Integer.parseInt(validationPath.substring(ndx + 1, ndx2));
          ndx = ndx2;
        }
        catch (RuntimeException ex) {
          throw new ValidationRuntimeException(
                  "cannot decode index in validation path '" + validationPath + "' after '" + pathPrefix + "'");
        }
        indexes[i] = new CollectionIndex(index, pathPrefix, pathPostfix);
      }
      else  {
        throw new ValidationRuntimeException(
                "wrong number of indexes in '" + validationPath + "'. Expected=" + count + ", detected=" + (i+1));
      }
    }

    return indexes;
  }

}
