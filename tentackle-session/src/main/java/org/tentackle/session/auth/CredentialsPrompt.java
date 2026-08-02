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

package org.tentackle.session.auth;

import java.net.URI;

/**
 * The user interaction a {@link CredentialsProvider} may need while obtaining credentials.
 * <p>
 * It keeps providers free of any dependency on a UI toolkit: an interactive single sign-on
 * provider asks the prompt to {@link #browse(URI)} the authorization endpoint and reports its
 * progress, while the desktop client, a console application, or a test supply the implementation
 * that actually does it.
 * <p>
 * All methods are invoked from the thread running the login, which is never the UI thread.
 *
 * @author harald
 */
public interface CredentialsPrompt {

  /**
   * Opens the given URI in the user's browser.
   *
   * @param uri the URI, usually the identity provider's authorization endpoint
   */
  void browse(URI uri);

  /**
   * Shows a message to the user.
   *
   * @param message the message, null to clear
   */
  void showMessage(String message);

  /**
   * Reports the progress of an ongoing interaction.
   *
   * @param progress &le; 0 not begun yet, &ge; 1 completed
   */
  void setProgress(double progress);

  /**
   * Asks the user for a confidential input, such as a device code or a one-time password.
   *
   * @param label describes what is asked for
   * @return the input, null if the user refused
   */
  char[] promptSecret(String label);

  /**
   * Returns whether the user canceled the login.<br>
   * Long-running interactions should poll this and give up when it turns true.
   *
   * @return true if canceled
   */
  boolean isCanceled();

}
