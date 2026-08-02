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

import org.tentackle.log.Logger;

import java.io.Console;
import java.net.URI;

/**
 * The credentials prompt for applications without a graphical user interface.
 * <p>
 * There is no browser to launch, so the URI to visit is printed, and the user is expected to open
 * it. This is what a console application, a service, or a test gets by default; the desktop client
 * provides a prompt that drives the login view instead.
 *
 * @author harald
 */
public class DefaultCredentialsPrompt implements CredentialsPrompt {

  private static final Logger LOGGER = Logger.get(DefaultCredentialsPrompt.class);

  /**
   * Creates the prompt.
   */
  public DefaultCredentialsPrompt() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void browse(URI uri) {
    LOGGER.info("please open the following URL to authenticate: {0}", uri);
  }

  @Override
  public void showMessage(String message) {
    if (message != null) {
      LOGGER.info(message);
    }
  }

  @Override
  public void setProgress(double progress) {
    // nothing to show
  }

  @Override
  public char[] promptSecret(String label) {
    Console console = System.console();
    return console == null ? null : console.readPassword("%s: ", label);
  }

  @Override
  public boolean isCanceled() {
    return false;
  }

}
