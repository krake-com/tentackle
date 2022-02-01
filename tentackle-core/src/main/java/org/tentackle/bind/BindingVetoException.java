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


package org.tentackle.bind;

/**
 * BindingListeners may throw a BindingVetoException to
 * inhibit the transfer of data between the view and the model,
 * respectively vice versa.
 *
 * @author harald
 */
public class BindingVetoException extends Exception {

  private static final long serialVersionUID = 1L;


  private final boolean resyncRequested;     // true if model/view should be resync'd


  /**
   * Constructs a <code>BindingVetoException</code> with a
   * detailed message.<br>
   * If the message is not null or empty the binder
   * will log the message (no further action).
   *
   * @param message a descriptive message
   * @param resyncRequested true if resync model/view
   */
  public BindingVetoException(String message, boolean resyncRequested) {
    super(message);
    this.resyncRequested = resyncRequested;
  }


  /**
   * Constructs a <code>BindingVetoException</code> with a
   * detailed message.<br>
   * If the message is not null or empty the binder
   * will log the message (no further action).<br>
   * The model/view will not be resync'd.
   * This is up to the application.
   *
   * @param message a descriptive message
   */
  public BindingVetoException(String message) {
    this(message, false);
  }


  /**
   * Returns whether resync is requested.
   *
   * @return true if model or view should be synchronized, false if not (default)
   */
  public boolean isResyncRequested() {
    return resyncRequested;
  }

}
