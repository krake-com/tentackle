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
import org.tentackle.pdo.DomainContext;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;

/**
 * Base class for client applications.
 *
 * @author harald
 */
public abstract class AbstractClientApplication extends AbstractApplication {

  private static final Logger LOGGER = Logger.get(AbstractClientApplication.class);

  /**
   * Creates a client application.
   *
   * @param name the application's name
   * @param version the application version
   */
  public AbstractClientApplication(String name, String version) {
    super(name, version);
  }


  /**
   * Updates the session info after sucessful login.
   */
  protected void updateSessionInfoAfterLogin() {
    DomainContext context = getDomainContext();

    if (context != null) {
      Session session = context.getSession();
      SessionInfo sessionInfo = session.getSessionInfo();
      if (session.isRemote()) {
        SessionInfo remoteInfo = session.getRemoteSession().getClientSessionInfo();
        sessionInfo.setUserId(remoteInfo.getUserId());
        sessionInfo.setUserClassId(remoteInfo.getUserClassId());
      }
      else  {
        sessionInfo.setUserId(context.getSessionInfo().getUserId());
        sessionInfo.setUserClassId(context.getSessionInfo().getUserClassId());
      }
    }
  }

}
