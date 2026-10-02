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

package org.tentackle.fx.rdc.app;

import org.tentackle.app.LogoutEvent;
import org.tentackle.fx.Fx;
import org.tentackle.log.Logger;
import org.tentackle.session.MasterSerialEventService;

import javafx.application.Platform;
import java.util.function.Consumer;

/**
 * Handler for a {@link LogoutEvent} in a desktop application.<br>
 * Replaces the default logout handler and invokes {@code Fx.terminate()} from within the FX thread.<br>
 * The logout event is sent by the server, for example, if an administrator logs out the user's session.
 */
@MasterSerialEventService(LogoutEvent.class)
public class LogoutEventHandler implements Consumer<LogoutEvent> {

  private static final Logger LOGGER = Logger.get(LogoutEventHandler.class);

  /**
   * Creates the logout event handler.
   */
  public LogoutEventHandler() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void accept(LogoutEvent logoutEvent) {
    LOGGER.warning("logout requested by remote server");
    Platform.runLater(Fx::terminate);
  }

}
