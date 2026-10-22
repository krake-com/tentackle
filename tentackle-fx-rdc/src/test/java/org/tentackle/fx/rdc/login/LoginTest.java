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

package org.tentackle.fx.rdc.login;

import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for the progress-dependent visibility rules of the {@link Login} controller.
 * <p>
 * The controller is created without loading its FXML, so no JavaFX toolkit is needed.
 */
@SuppressWarnings("missing-explicit-ctor")
public class LoginTest {

  private final Login login = new Login();

  @Test
  public void testLoginPaneVisibility() {
    assertTrue(login.determineLoginPaneVisibility(0.0), "visible before login");
    assertTrue(login.determineLoginPaneVisibility(0.5), "visible while logging in");
    assertTrue(login.determineLoginPaneVisibility(-1.0), "visible with infinite progress");
    assertFalse(login.determineLoginPaneVisibility(1.0), "hidden when done");
  }

  @Test
  public void testProgressBarVisibility() {
    assertFalse(login.determineProgressBarVisibility(0.0), "no progress -> hidden");
    assertTrue(login.determineProgressBarVisibility(0.05), "visible while logging in");
    assertTrue(login.determineProgressBarVisibility(0.99), "visible while logging in");
    assertFalse(login.determineProgressBarVisibility(1.0), "hidden when done");
    assertFalse(login.determineProgressBarVisibility(-1.0), "hidden with infinite progress");
  }

}
