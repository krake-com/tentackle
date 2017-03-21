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


package org.tentackle.swing.rdc;

import org.tentackle.session.SessionInfo;


/**
 * A generic login dialog.
 *
 * @author harald
 */
public interface LoginDialog {

  /**
   * Displays the dialog and prompts for username and password.<br>
   *
   * If the method is invoked from within the GUI thread, the
   * dialog is modal and will dispose after login data entered.
   * Otherwise the dialog remains open and showStatus() can be
   * used to further update the login process.
   *
   * @return  null if user aborts
   */
  SessionInfo getSessionInfo();


  /**
   * Shows a message status.
   *
   * @param status the message text
   */
  void showStatus(String status);


  /**
   * Shows or hides this login dialog.
   *
   * @param visible true if visible, else invisible
   */
  void setVisible(boolean visible);


  /**
   * Checks if this Window is showing on screen.
   *
   * @return true if showing
   */
  boolean isShowing();


  /**
   * Releases all of the native screen resources used by this
   * <code>LoginDialog</code>, its subcomponents, and all of its owned
   * children.
   */
  void dispose();

}
