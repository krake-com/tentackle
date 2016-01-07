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

// Created on November 1, 2006, 4:31 PM

package org.tentackle.misc;

/**
 * Some platform helper methods.
 *
 * @author harald
 */
public class Toolkit {

  /** set to false in order to disable beeps **/
  public static boolean beepEnabled = true;   
  
  /**
   * Beep.
   * <p>
   * On some platforms the {@link java.awt.Toolkit#beep()} does not work.
   * For this reason an additional Ctrl-G will be echoed
   * to the standard output (works on most platforms as long
   * as the application is started from a shell).
   *
   * Furthermore, the beep can be turned off by the application.
   */
  public static void beep() {
    if (beepEnabled)  {
      java.awt.Toolkit.getDefaultToolkit().beep();
      System.out.print('\007');    // system bell
      System.out.flush();
    }
  }
  
}
