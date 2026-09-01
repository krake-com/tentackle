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

import java.io.Serial;

/**
 * BindingListeners may throw a BindingVetoException to
 * inhibit the transfer of data between the view and the model,
 * respectively vice versa.
 *
 * @author harald
 */
public class BindingVetoException extends Exception {

  @Serial
  private static final long serialVersionUID = 1L;


  private final boolean rethrowEnabled;       // true if rethrowEnabled as Binding(Runtime)Exception
  private final boolean resyncRequested;      // true if model/view should be resync'd


  /**
   * Constructs a <code>BindingVetoException</code> with <code>null</code> as its detail
   * message, no resync and no rethrow requested.
   */
  public BindingVetoException() {
    super();
    this.resyncRequested = false;
    this.rethrowEnabled = false;
  }

  /**
   * Constructs a <code>BindingVetoException</code> with optional resync of model or view.
   *
   * @param message a descriptive message
   * @param resyncRequested true if resync model/view
   */
  public BindingVetoException(String message, boolean resyncRequested) {
    super(message);
    this.resyncRequested = resyncRequested;
    this.rethrowEnabled = false;
  }

  /**
   * Constructs a <code>BindingVetoException</code> with optional rethrowEnabled as a binding runtime exception.
   *
   * @param message a descriptive message
   * @param rethrowEnabled true to rethrowEnabled as Binding(Runtime)Exception, false to process silently
   */
  public BindingVetoException(boolean rethrowEnabled, String message) {
    super(message);
    this.resyncRequested = false;
    this.rethrowEnabled = rethrowEnabled;
  }

  /**
   * Constructs a <code>BindingVetoException</code>.
   *
   * @param message a descriptive message
   */
  public BindingVetoException(String message) {
    super(message);
    this.resyncRequested = false;
    this.rethrowEnabled = false;
  }


  /**
   * Returns whether resync is requested.
   *
   * @return true if model or view should be synchronized, false if not (default)
   */
  public boolean isResyncRequested() {
    return resyncRequested;
  }

  /**
   * Returns whether the veto exception should be rethrown as a binding runtime exception after being processed.
   *
   * @return true if re-throw, false if process silently
   */
  public boolean isRethrowEnabled() {
    return rethrowEnabled;
  }
}
