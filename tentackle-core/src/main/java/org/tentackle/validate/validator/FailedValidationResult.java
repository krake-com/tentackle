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

import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.Validator;

/**
 * Simple failed result.
 *
 * @author harald
 */
public class FailedValidationResult extends AbstractValidationResult {

  private static final long serialVersionUID = 1L;

  private static final String DEFAULT_MESSAGE = "unspecified";   // only for deserialization

  /**
   * Creates a failed validation result.
   *
   * @param validator the validator
   * @param validationContext the validation context
   * @param message the message
   */
  public FailedValidationResult(Validator validator, ValidationContext validationContext, String message) {
    super(validator, validationContext, message);
    checkArgs();
  }

  /**
   * Creates a failed validation result.
   *
   * @param validator the validator
   * @param validationContext the validation context
   * @param message the message
   * @param errorCode the error code (to override the code from the validator)
   */
  public FailedValidationResult(Validator validator, ValidationContext validationContext, String message, String errorCode) {
    super(validator, validationContext, message, errorCode);
    checkArgs();
  }

  /**
   * No args constructor to allow deserializtion.
   */
  public FailedValidationResult() {
    this(null, null, DEFAULT_MESSAGE, null);
  }

  /**
   * Checks the arguments.
   *
   * @throws IllegalArgumentException if message is empty
   */
  protected void checkArgs() throws IllegalArgumentException {
    String message = getMessage();
    if (message == null || message.length() == 0) {
      throw new IllegalArgumentException("message must not be empty for a failed validation result");
    }
  }

  @Override
  public boolean hasFailed() {
    return true;
  }

  @Override
  public boolean hasMessage() {
    return true;
  }

}
