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

package org.tentackle.fx.rdc.update;

import javafx.scene.Parent;

import org.tentackle.fx.FxController;
import org.tentackle.fx.rdc.app.DesktopApplication;
import org.tentackle.fx.rdc.app.LoginFailedHandler;
import org.tentackle.session.SessionInfo;

/**
 * Desktop application with auto update facility.
 *
 * @param <C> the main controller type
 */
public abstract class UpdatableDesktopApplication<C extends FxController> extends DesktopApplication<C> {

  /**
   * Creates an FX desktop application.
   *
   * @param name the application name
   * @param version the application version
   */
  public UpdatableDesktopApplication(String name, String version) {
    super(name, version);
  }

  @Override
  public LoginFailedHandler createLoginFailedHandler(Parent view, SessionInfo sessionInfo) {
    return new LoginFailedWithUpdateHandler(this, view, sessionInfo);
  }
}
