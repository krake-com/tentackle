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

package org.tentackle.persist.trip;

import org.tentackle.dbms.trip.RemoteDbDelegate;
import org.tentackle.pdo.AdminExtension.SessionData;
import org.tentackle.pdo.DomainContext;

import java.util.List;

/**
 * Remote delegate for the {@link org.tentackle.persist.AdminExtensionAdapter}.
 *
 *  @author harald
 */
public interface AdminExtensionAdapterRemoteDelegate extends RemoteDbDelegate {

  /**
   * Gets the session data of all sessions currently logged into the middle tier.
   *
   * @param context the domain context
   * @return the session data
   */
  List<SessionData> getSessions(DomainContext context);

  /**
   * Submits a logout request to the modification tracker sessions of a given user.
   *
   * @param context the domain context
   * @param userId the user id
   * @param sessionGroupId the optional session group, 0 = all
   * @param applicationName the optional application name, null = all
   * @param applicationId the optional application ID, 0 = all
   * @return true if at least one logout request was submitted, false if no such user session
   */
  boolean logout(DomainContext context, long userId, long sessionGroupId, String applicationName, long applicationId);

  /**
   * Kills all sessions for a given user id.
   *
   * @param context the domain context
   * @param userId the user id
   * @param sessionGroupId the optional session group, 0 = all
   * @param applicationName the optional application name, null = all
   * @param applicationId the optional application ID, 0 = all
   * @return the number of sessions killed
   */
  int kill(DomainContext context, long userId, long sessionGroupId, String applicationName, long applicationId);

}
