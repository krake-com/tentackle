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


package org.tentackle.session;

import java.text.MessageFormat;
import java.util.Date;
import org.tentackle.misc.FormatHelper;


/**
 * Exception thrown if the user is already logged in.<br>
 * Usually for appservers that don't allow a user logged in more than once.
 *
 * @author harald
 */
public class AlreadyLoggedInException extends LoginFailedException {

  private static final long serialVersionUID = -3703843403789342728L;

  private final SessionInfo loginInfo;    // the application user info

  /**
   * Creates an {@code AlreadyLoggedInException}.
   *
   * @param session the session
   * @param loginInfo the application user info
   */
  public AlreadyLoggedInException(Session session, SessionInfo loginInfo) {
    super(session);
    this.loginInfo = loginInfo;
  }


  /**
   * Gets the application user info.
   *
   * @return the user info
   */
  public SessionInfo getLoginInfo() {
    return loginInfo;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden due to localized message.
   */
  @Override
  public String getMessage() {
    return MessageFormat.format(SessionCoreBundle.getString("YOU ARE ALREADY LOGGED IN AT {0} SINCE {1}"),
                                loginInfo.getHostInfo(),
                                FormatHelper.formatShortTimestamp(new Date(loginInfo.getSince())));
  }
}
