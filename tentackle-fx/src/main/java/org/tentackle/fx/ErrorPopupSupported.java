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

package org.tentackle.fx;

/**
 * Controls that support error popups must implement this interface.
 *
 * @author harald
 */
public interface ErrorPopupSupported {

  /**
   * Gets the error message.
   *
   * @return the message, null if no error
   */
  String getError();

  /**
   * Sets the error message.<br>
   * Notice that an empty string will set the style but dows not show any popup.
   *
   * @param error the error message, null to clear
   */
  void setError(String error);

  /**
   * Shows the error popup.<br>
   * Requires the error message set.
   */
  void showErrorPopup();

  /**
   * Hides the error popup if shown.
   */
  void hideErrorPopup();

}
