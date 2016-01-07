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

// Created on December 11, 2004, 6:31 PM

package org.tentackle.swing.rdc;

import org.tentackle.log.Logger.Level;
import org.tentackle.swing.FormComponent;
import org.tentackle.validate.ValidationResult;



/**
 * Default implementation for an interactive error.
 *
 * @author harald
 */
public class DefaultInteractiveError implements InteractiveError {

  private Level level;                          // error level
  private String text;                          // error text
  private String errorCode;                     // optional error code (from TT2 validation)
  private ValidationResult validationResult;    // the optional validation result
  private Object uiComponent;                   // optional GUI component that causes the error, null if none

  /**
   * Creates an error.
   *
   * @param text the error text
   * @param level the severity (usually {@link Level#WARNING} or {@link Level#SEVERE})
   * @param errorCode the optional error code
   * @param uiComponent the optional UI component related to the error, null if none
   * @param validationResult the optional validation result
   */
  public DefaultInteractiveError(String text, Level level, String errorCode, ValidationResult validationResult, Object uiComponent)  {
    setText(text);
    setLevel(level);
    setErrorCode(errorCode);
    setValidationResult(validationResult);
    setUiComponent(uiComponent);
  }

  /**
   * Creates an error from another error.<br>
   * Useful to override methods in anonymous inner classes.
   *
   * @param error the error
   */
  public DefaultInteractiveError(InteractiveError error) {
    this(error.getText(), error.getLevel(), error.getErrorCode(), error.getValidationResult(), error.getUiComponent());
  }

  /**
   * Creates an empty interactive error.<br>
   */
  public DefaultInteractiveError() {
  }

  @Override
  public String toString()  {
    return getText();
  }

  @Override
  public Level getLevel() {
    return level;
  }

  @Override
  public void setLevel(Level level) {
    this.level = level;
  }

  @Override
  public String getText() {
    return text;
  }

  @Override
  public void setText(String text) {
    this.text = text;
  }

  @Override
  public String getErrorCode() {
    return errorCode;
  }

  @Override
  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  @Override
  public FormComponent getFormComponent() {
    return uiComponent instanceof FormComponent ? (FormComponent)uiComponent : null;
  }

  @Override
  public Object getUiComponent() {
    return uiComponent;
  }

  @Override
  public void setUiComponent(Object uiComponent) {
    this.uiComponent = uiComponent;
  }

  @Override
  public void showComponent() {
    FormComponent comp = getFormComponent();
    if (comp != null) {
      // jump to erroneous component
      comp.requestFocusLater();
    }
  }

  @Override
  public ValidationResult getValidationResult() {
    return validationResult;
  }

  @Override
  public void setValidationResult(ValidationResult validationResult) {
    this.validationResult = validationResult;
    text = validationResult.getMessage();
    errorCode = validationResult.getErrorCode();
    level = validationResult.hasFailed() ? Level.SEVERE : Level.WARNING;
  }

}
