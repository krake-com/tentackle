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


package org.tentackle.validate;

import java.util.List;
import org.tentackle.log.Loggable;


/**
 * Exception thrown if a validation fails.
 * <p>
 * A runtime exception that holds the validation results.
 *
 * @author harald
 */
public class ValidationFailedException extends RuntimeException implements Loggable {

  private static final long serialVersionUID = 922168908361481367L;

  private final List<ValidationResult> results;   // the validation results


  /**
   * No-args constructor for deserialization.
   */
  public ValidationFailedException() {
    super();
    results = null;
  }

  /**
   * Creates a validation exception.
   *
   * @param results the validation results
   */
  public ValidationFailedException(List<ValidationResult> results) {
    super(ValidationUtilities.getInstance().resultsToMessage(results));
    this.results = results;
  }

  /**
   * Creates a validation exception.
   *
   * @param message the message
   */
  public ValidationFailedException(String message) {
    super(message);
    this.results = null;
  }

  /**
   * Creates a validation exception.
   *
   * @param message the message
   * @param results the validation results
   */
  public ValidationFailedException(String message, List<ValidationResult> results) {
    super(message);
    this.results = results;
  }

  /**
   * Creates a validation exception.
   *
   * @param cause the cause
   * @param results the validation results
   */
  public ValidationFailedException(Throwable cause, List<ValidationResult> results) {
    super(cause);
    this.results = results;
  }

  /**
   * Creates a validation exception.
   *
   * @param message the message
   * @param cause the cause
   * @param results the validation results
   */
  public ValidationFailedException(String message, Throwable cause, List<ValidationResult> results) {
    super(message, cause);
    this.results = results;
  }


  /**
   * Gets the validation results.
   *
   * @return the list of results
   */
  public List<ValidationResult> getResults() {
    return results;
  }


  /**
   * Gets the validation results concatenated to a single message string.
   *
   * @return the results as string, never null
   */
  public String resultsToMessage() {
    return ValidationUtilities.getInstance().resultsToMessage(results);
  }

  @Override
  public boolean withStacktrace() {
    return false;   // log only the message
  }

  /**
   * {@inheritDoc}
   * <p>
   * Also contains the validation results.
   */
  @Override
  public String toString() {
    if (results != null && !results.isEmpty()) {
      return super.toString() + "\n" +
             ValidationUtilities.getInstance().resultsToString(results);
    }
    return super.toString();
  }

}
