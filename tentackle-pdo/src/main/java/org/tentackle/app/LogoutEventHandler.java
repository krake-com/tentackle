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

package org.tentackle.app;

import org.tentackle.log.Logger;
import org.tentackle.session.MasterSerialEventService;
import org.tentackle.session.ModificationTracker;

import java.util.function.Consumer;

/**
 * Default handler for a {@link LogoutEvent}.<br>
 * Simply terminates the modification tracker, which will in turn terminate the application.
 * Needs to be replaced, if application does not terminate this way.
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
    // terminating the ModificationTracker terminates the whole application (by default)
    ModificationTracker.getInstance().terminate();
  }

}
