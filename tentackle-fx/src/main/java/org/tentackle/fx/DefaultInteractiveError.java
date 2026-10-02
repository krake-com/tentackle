/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.fx;

import javafx.scene.Node;

import org.tentackle.validate.ValidationResult;

/**
 * Default implementation for an interactive error.
 *
 * @author harald
 */
public class DefaultInteractiveError implements InteractiveError {

  private final boolean warning;                      // true if just a warning
  private final String text;                          // error text
  private final String errorCode;                     // optional error code
  private final ValidationResult validationResult;    // the validation result
  private final FxControl control;                    // the ui control, null if none

  /**
   * Creates an interactive error.
   *
   * @param warning true if just a warning, false if error
   * @param text the error text
   * @param errorCode the optional error code
   * @param validationResult the validation result
   * @param control the optional UI component related to the error, null if none
   */
  public DefaultInteractiveError(boolean warning, String text, String errorCode, ValidationResult validationResult, FxControl control)  {
    this.warning = warning;
    this.text = text;
    this.errorCode = errorCode;
    this.validationResult = validationResult;
    this.control = control;
  }

  /**
   * Creates an interactive error.
   *
   * @param validationResult the validation result
   * @param control the optional UI component related to the error, null if none
   */
  public DefaultInteractiveError(ValidationResult validationResult, FxControl control) {
    this(!validationResult.hasFailed(), validationResult.getMessage(), validationResult.getErrorCode(), validationResult, control);
  }

  /**
   * Creates an error from another error.<br>
   * Useful to override methods in anonymous inner classes.
   *
   * @param error the error
   */
  public DefaultInteractiveError(InteractiveError error) {
    this(error.isWarning(), error.getText(), error.getErrorCode(), error.getValidationResult(), error.getControl());
  }


  @Override
  public String toString()  {
    return getText();
  }

  @Override
  public boolean isWarning() {
    return warning;
  }

  @Override
  public String getText() {
    return text;
  }

  @Override
  public String getErrorCode() {
    return errorCode;
  }

  @Override
  public FxControl getControl() {
    return control;
  }

  @Override
  public void showControl() {
    if (control instanceof Node) {
      ((Node) control).requestFocus();
    }
  }

  @Override
  public ValidationResult getValidationResult() {
    return validationResult;
  }

}
