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


package org.tentackle.bind;

import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationUtilities;

import java.io.Serial;
import java.util.EventObject;
import java.util.List;

/**
 * A validation event.<br>
 * Fired after a binding has been validated.
 *
 * @author harald
 */
public class ValidationEvent extends EventObject {

  @Serial
  private static final long serialVersionUID = 1L;

  @SuppressWarnings("serial")
  private final List<ValidationResult> results;


  /**
   * Creates a validation event.
   *
   * @param source the binding source
   * @param results the validation results
   */
  public ValidationEvent(Binding source, List<ValidationResult> results) {
    super(source);
    this.results = results;
  }


  @Override
  public Binding getSource() {
    return (Binding) source;
  }

  /**
   * Gets the validation results.
   *
   * @return the results, empty if no errors.
   */
  public List<ValidationResult> getValidationResults() {
    return results;
  }

  @Override
  public String toString() {
    return ValidationUtilities.getInstance().resultsToString(results);
  }

}
