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

import org.tentackle.log.Loggable;
import org.tentackle.reflect.CanonicalConstructor;
import org.tentackle.reflect.CanonicalGetter;
import org.tentackle.trip.TripIgnore;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Exception thrown if a validation fails logically.
 * <p>
 * A runtime exception that holds the validation results.
 * <p>
 * The purpose of this exception is two-fold:
 * <ol>
 *   <li>holds the final validation results</li>
 *   <li>holds the results of a component collected so far, when the validation process has been aborted,
 *   for example, due to the {@link org.tentackle.validate.validator.Fail}-validator</li>
 * </ol>
 *
 * A validating method should be implemented as follows:
 * <pre>
 *  public void validate(...) {
 *    List&lt;ValidationResult&gt; results = new ArrayList&lt;&gt;();
 *    try {
 *      // validate members and components
 *      results.addAll(ValidationUtilities.getInstance().validate(...));
 *      ...
 *    }
 *    catch (ValidationFailedException vfx) {
 *      // validation was aborted somewhere: add those results and return to caller
 *      vfx.reThrow(results);
 *    }
 *    return results;
 *  }
 * </pre>
 *
 * @author harald
 */
public class ValidationFailedException extends RuntimeException implements Loggable {

  @Serial
  private static final long serialVersionUID = 1L;

  @SuppressWarnings("serial")
  private final List<ValidationResult> results;   // the validation results
  private transient String resultsMessage;        // cached results message


  /**
   * Creates a validation exception.
   *
   * @param message the message
   * @param results the validation results
   */
  @CanonicalConstructor
  public ValidationFailedException(String message, List<ValidationResult> results) {
    super(message);
    this.results = results == null ? new ArrayList<>() : results;
  }

  /**
   * Creates a validation exception.
   *
   * @param results the validation results
   */
  public ValidationFailedException(List<ValidationResult> results) {
    this(ValidationUtilities.getInstance().resultsToMessage(results), results);
  }

  /**
   * Creates a validation exception.
   *
   * @param message the message
   */
  public ValidationFailedException(String message) {
    this(message, null);
  }


  @CanonicalGetter(ordinal = 0)
  @Override
  public String getMessage() {
    return super.getMessage();
  }

  @TripIgnore
  @Override
  public Throwable getCause() {
    return super.getCause();
  }

  /**
   * Gets the validation results.
   *
   * @return the list of results, never null
   */
  @CanonicalGetter(ordinal = 1)
  public List<ValidationResult> getResults() {
    return results;
  }


  /**
   * Gets the validation results concatenated to a single message string.
   *
   * @return the results as string, never null
   */
  public String resultsAsMessage() {
    if (resultsMessage == null) {
      resultsMessage = ValidationUtilities.getInstance().resultsToMessage(results);
    }
    return resultsMessage;
  }

  @Override
  public boolean withStacktrace() {
    return false;   // log only the message
  }


  /**
   * Rethrows this exception with the given validation results prepended.
   *
   * @param resultsSoFar the results collected so far
   * @throws ValidationFailedException always
   */
  public void reThrow(List<ValidationResult> resultsSoFar) {
    if (resultsSoFar != null && resultsSoFar != results && !resultsSoFar.isEmpty()) {
      results.addAll(0, resultsSoFar);
    }
    throw this;
  }

}
