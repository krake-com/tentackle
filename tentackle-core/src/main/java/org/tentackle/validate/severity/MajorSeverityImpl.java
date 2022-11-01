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
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationSeverityService;
import org.tentackle.validate.Validator;

/**
 * Implements the major severity.<br>
 * Creates a {@link FailedValidationResult}.
 */
@ValidationSeverityService(MajorSeverity.class)
public class MajorSeverityImpl implements MajorSeverity {

  /**
   * Creates the major severity.
   */
  public MajorSeverityImpl() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public ValidationResult createValidationResult(Validator validator, ValidationContext validationContext, String message) {
    return new FailedValidationResult(validator, validationContext, message);
  }
}
