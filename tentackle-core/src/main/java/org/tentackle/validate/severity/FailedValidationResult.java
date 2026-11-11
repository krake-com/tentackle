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

import org.tentackle.reflect.CanonicalConstructor;
import org.tentackle.reflect.CanonicalName;
import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.Validator;

import java.io.Serial;
import java.util.Objects;

/**
 * Simple failed result.<br>
 * Created by the {@link MajorSeverity}. Requires a message.
 *
 * @author harald
 */
public class FailedValidationResult extends AbstractValidationResult {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates a failed validation result.
   *
   * @param validator the validator
   * @param validationContext the validation context
   * @param message the message
   * @throws NullPointerException if the message is null
   * @throws IllegalArgumentException if the message is empty
   */
  public FailedValidationResult(Validator validator, ValidationContext validationContext, String message) {
    super(validator, validationContext, message);
    if (Objects.requireNonNull(message, "message").isEmpty()) {
      throw new IllegalArgumentException("empty message");
    }
  }

  /**
   * Creates a validation result from its serialized values.<br>
   * Used by TRIP to avoid mutating final fields (JEP-500).
   *
   * @param message the message
   * @param errorCode the error code
   * @param validationPath the validation path
   * @param configuredScopes the configured scopes
   */
  @CanonicalConstructor
  protected FailedValidationResult(@CanonicalName("message") String message,
                                   @CanonicalName("errorCode") String errorCode,
                                   @CanonicalName("validationPath") String validationPath,
                                   @CanonicalName("configuredScopes") Class<? extends ValidationScope>[] configuredScopes) {
    super(message, errorCode, validationPath, configuredScopes);
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
