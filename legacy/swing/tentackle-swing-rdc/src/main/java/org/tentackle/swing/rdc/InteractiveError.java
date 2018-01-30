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
 * Description of an error.
 * <p>
 * {@link InteractiveError}s provide a semantic link between the
 * data- and the presentation-layer. Usually, the data is verified
 * at the data-layer and then decorated at the presentation-layer
 * by examining the error (e.g. assign a GUI-component).<br>
 * The {@link TooltipAndErrorPanel} is able to deal with a list
 * of InteractiveErrors.
 *
 * @author harald
 */
public interface InteractiveError {

  /**
   * Gets the severity level.
   *
   * @return one of {@link Level#WARNING} or {@link Level#SEVERE}
   */
  Level getLevel();

  /**
   * Gets the error message.
   *
   * @return the error text
   */
  String getText();

  /**
   * Gets the optional error code.
   *
   * @return the error code
   */
  String getErrorCode();

  /**
   * Gets the related swing GUI-component.
   *
   * @return the component, null if none or guiComponent is not a FormComponent.
   */
  FormComponent getFormComponent();

  /**
   * Gets the related GUI-component.
   * Can be anything, depending on the presentation layer.
   *
   * @return the component, null if none
   */
  Object getUiComponent();

  /**
   * Shows the component to the user.<br>
   * The default implementation invokes {@link FormComponent#requestFocusLater()}.
   * <p>
   * Applications may override this method to switch tabs, point to rows in tables, etc...
   */
  void showComponent();

  /**
   * Gets the optional validation result.
   *
   * @return the result, null if none
   */
  ValidationResult getValidationResult();

  /**
   * Sets the optional error code.
   *
   * @param errorCode the error code.
   */
  void setErrorCode(String errorCode);

  /**
   * Sets the severity level.
   *
   * @param level usually one of {@link Level#WARNING} or {@link Level#SEVERE}
   */
  void setLevel(Level level);

  /**
   * Sets the error message.
   *
   * @param text the error text
   */
  void setText(String text);

  /**
   * Sets the related GUI-component.
   * Can be anything, depending on the presentation layer.
   *
   * @param uiComponent  the component, null if none
   */
  void setUiComponent(Object uiComponent);

  /**
   * Sets the optional validation result.
   * @param validationResult the result
   */
  void setValidationResult(ValidationResult validationResult);

}
