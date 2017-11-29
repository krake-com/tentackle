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

import org.tentackle.common.StringHelper;
import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationRuntimeException;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.Validator;

/**
 * Base implementation for validation results.
 *
 * @author harald
 */
public abstract class AbstractValidationResult implements ValidationResult {

  private static final long serialVersionUID = 415899327881601354L;

  private String message;                                       // the validation failed message
  private String errorCode;                                     // error code
  private String validationPath;                                // the validation path
  private transient Object object;                              // the validated object
  private transient Object parentObject;                        // the validated parent object
  private Class<? extends ValidationScope>[] configuredScopes;  // the configured scopes
  private transient Validator validator;                        // the validator

  /**
   * Creates a validation result.
   *
   * @param validator the validator
   * @param validationContext the validation context
   * @param message the message
   */
  public AbstractValidationResult(Validator validator, ValidationContext validationContext, String message) {
    setValidator(validator, validationContext);
    this.message = message;
  }

  /**
   * Creates a validation result.
   *
   * @param validator the validator
   * @param validationContext the validation context
   * @param message the message
   * @param errorCode the error code (to override the code from the validator)
   */
  public AbstractValidationResult(Validator validator, ValidationContext validationContext, String message, String errorCode) {
    this(validator, validationContext, message);
    this.errorCode = errorCode;
  }

  /**
   * No args constructor to allow deserializtion.
   */
  public AbstractValidationResult() {
    // to allow deserialization
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

  /**
   * Sets validator and copies validation-specific parameters.
   * <p>
   * Note: validators exist only once per annotation!
   * @param validator the validator
   * @param validationContext the validation context
   */
  protected void setValidator(Validator validator, ValidationContext validationContext) {

    if (validator == null) {
      throw new IllegalArgumentException("validator must not be null");
    }
    this.validator    = validator;

    errorCode         = validator.getErrorCode();
    validationPath    = validationContext.getValidationPath();
    object            = validationContext.getObject();
    parentObject      = validationContext.getParentObject();
    configuredScopes  = validator.getConfiguredScopes(validationContext);
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
  public Object getObject() {
    return object;
  }

  @Override
  public Object getParentObject() {
    return parentObject;
  }

  @Override
  public Class<? extends ValidationScope>[] getConfiguredScopes() {
    return configuredScopes;
  }


  @Override
  public CollectionIndex[] getValidationCollectionIndexes() throws ValidationRuntimeException {

    int count = StringHelper.countOccurrences(validationPath, '[');

    CollectionIndex[] indexes = new CollectionIndex[count];
    int ndx = 0;

    for (int i=0; i < count; i++) {
      ndx = validationPath.indexOf('[', ndx + 1);
      if (ndx > 0) {
        CollectionIndex colIndex = new CollectionIndex();
        indexes[i] = colIndex;
        colIndex.pathPrefix = validationPath.substring(0, ndx);
        int ndx2 = validationPath.indexOf("].", ndx);
        if (ndx2 < 0) {
          throw new ValidationRuntimeException(
                  "no closing bracket after '" + colIndex.pathPrefix + "' in validation path '" + validationPath + "'");
        }
        colIndex.pathPostfix = validationPath.substring(ndx2 + 2);
        try {
          colIndex.index = Integer.parseInt(validationPath.substring(ndx + 1, ndx2));
          ndx = ndx2 + 2;
        }
        catch (RuntimeException ex) {
          throw new ValidationRuntimeException(
                  "cannot decode index in validation path '" + validationPath + "' after '" + colIndex.pathPrefix + "'");
        }
      }
      else  {
        throw new ValidationRuntimeException(
                "wrong number of indexes in '" + validationPath + "'. Expected=" + count + ", detected=" + (i+1));
      }
    }

    return indexes;
  }
}
