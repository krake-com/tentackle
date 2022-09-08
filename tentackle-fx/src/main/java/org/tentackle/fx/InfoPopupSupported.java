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

/**
 * Controls that support info popups must implement this interface.
 *
 * @author harald
 */
public interface InfoPopupSupported {

  /**
   * Gets the info message.
   *
   * @return the message, null if no info
   */
  String getInfo();

  /**
   * Sets the info message.<br>
   * Notice that an empty string will set the style but does not show any popup.
   *
   * @param info the info message, null to clear
   */
  void setInfo(String info);

  /**
   * Shows the info popup.<br>
   * Requires the info message set.
   */
  void showInfoPopup();

  /**
   * Hides the info popup if shown.
   */
  void hideInfoPopup();

}
