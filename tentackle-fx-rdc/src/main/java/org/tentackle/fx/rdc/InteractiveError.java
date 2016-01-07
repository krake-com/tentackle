/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.fx.rdc;

import org.tentackle.fx.FxControl;
import org.tentackle.validate.ValidationResult;

/**
 * Description of an error.
 * <p>
 * {@link InteractiveError}s provide a link between a {@link ValidationResult} and a UI-control.
 *
 * @author harald
 */
public interface InteractiveError {

  /**
   * Returns whether this is just a warning.
   *
   * @return true if warning, false if error
   */
  boolean isWarning();

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
   * Gets the related FX control.
   *
   * @return the control, null if nothing related
   */
  FxControl getControl();

  /**
   * Shows the control to the user.<br>
   * The default implementation requests the focus.
   * <p>
   * Applications may override this method to switch tabs, point to rows in tables, etc...
   */
  void showControl();

  /**
   * Gets the optional validation result.
   *
   * @return the result, null if none
   */
  ValidationResult getValidationResult();

}
