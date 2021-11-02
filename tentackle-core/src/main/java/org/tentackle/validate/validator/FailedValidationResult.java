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
import org.tentackle.validate.Validator;

import java.util.Objects;

/**
 * Simple failed result.
 *
 * @author harald
 */
public class FailedValidationResult extends AbstractValidationResult {

  private static final long serialVersionUID = 7944890568191272731L;

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
   * @param errorCode the error code, null to take it from the validator (default)
   */
  public FailedValidationResult(Validator validator, ValidationContext validationContext, String message, String errorCode) {
    super(validator, validationContext, message, errorCode);
    checkArgs();
  }

  /**
   * Checks the arguments.
   * <p>
   * Throws IllegalArgumentException if message is empty.
   */
  protected void checkArgs() {
    String message = getMessage();
    if (Objects.requireNonNull(message, "message").isEmpty()) {
      throw new IllegalArgumentException("empty message");
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
