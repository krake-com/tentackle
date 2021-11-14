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

package org.tentackle.validate;

/**
 * A severity associated with a failed validation.<br>
 * Applications may define their own severities or replace the default ones in order to create
 * application-specific validation results.
 * <p>
 * IMPORTANT: a severity is a singleton and must not maintain any state!
 *
 * @author harald
 */
public interface ValidationSeverity {

  /**
   * Creates the validation result.
   *
   * @param validator the validator instance
   * @param validationContext the validation context
   * @param message the diagnostic message
   * @return the created validation result
   */
  ValidationResult createValidationResult(Validator validator, ValidationContext validationContext, String message);

}
