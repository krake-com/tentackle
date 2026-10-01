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

import org.tentackle.validate.ValidationContext;
import org.tentackle.validate.Validator;

import java.io.Serial;

/**
 * Simple informational result.<br>
 * Created by the {@link MinorSeverity}. Provides a message, but does not fail.
 *
 * @author harald
 */
public class InfoValidationResult extends FailedValidationResult {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates an informational validation result.
   *
   * @param validator the validator
   * @param validationContext the validation context
   * @param message the message
   */
  public InfoValidationResult(Validator validator, ValidationContext validationContext, String message) {
    super(validator, validationContext, message);
  }

  /**
   * For TRIP.
   */
  protected InfoValidationResult() {}


  @Override
  public boolean hasFailed() {
    return false;
  }

}
