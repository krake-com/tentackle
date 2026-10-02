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

package org.tentackle.pdo;

import org.tentackle.bind.Bindable;
import org.tentackle.common.Timestamp;

import java.util.List;

/**
 * Remote session extension providing admin services for the middle tier.
 *
 * @author harald
 */
public interface AdminExtension extends DomainContextDependable {

  /**
   * Provides information about a logged-in client session.
   */
  interface SessionData {

    /**
     * Gets the user id.
     *
     * @return the object ID of the current user
     */
    @Bindable
    long getUserId();

    /**
     * Gets the username.
     *
     * @return the username
     */
    @Bindable
    String getUserName();

    /**
     * Gets the time since when the user is logged in.
     *
     * @return logged in since, null if not logged in
     */
    @Bindable
    Timestamp getSince();

    /**
     * Gets the name of the application.
     *
     * @return the name, null if none
     */
    @Bindable
    String getApplicationName();

    /**
     * Returns the application id.
     *
     * @return the id, 0 if none
     */
    @Bindable
    long getApplicationId();

    /**
     * Gets the optional session name.
     *
     * @return the session name, null if none
     */
    @Bindable
    String getSessionName();

    /**
     * Gets the client version.
     *
     * @return the client version
     */
    @Bindable
    String getClientVersion();

    /**
     * Gets the session's locale.
     *
     * @return the locale as string, never null
     */
    @Bindable
    String getLocale();

    /**
     * Gets the info string describing the JVM.
     *
     * @return the jvm info
     */
    @Bindable
    String getVmInfo();

    /**
     * Gets the operating system info.
     *
     * @return the OS info
     */
    @Bindable
    String getOsInfo();

    /**
     * Gets the host info.
     *
     * @return the hostname or similar info
     */
    @Bindable
    String getHostInfo();

    /**
     * Gets the timezone.
     *
     * @return the timezone as string
     */
    @Bindable
    String getTimeZone();

    /**
     * Gets the client host string determined by the middle tier.
     *
     * @return usually the IP-address
     */
    @Bindable
    String getClientHost();

    /**
     * Gets the session number.
     *
     * @return the session number
     */
    @Bindable
    long getSessionNumber();

    /**
     * Gets the session group.
     *
     * @return the group, 0 if ungrouped
     */
    @Bindable
    long getSessionGroup();

    /**
     * Gets the session options.
     *
     * @return the options
     */
    @Bindable
    String getOptions();

    /**
     * Gets the clone flag of the session.
     *
     * @return true if session is cloned
     */
    @Bindable
    boolean isCloned();

  }


  /**
   * Gets the session data of all sessions currently logged into the middle tier.
   *
   * @return the session data
   */
  List<SessionData> getSessions();

  /**
   * Submits a logout request to the modification tracker session of a given user.
   *
   * @param userId the user id
   * @param sessionGroupId the optional session group, 0 = all
   * @param applicationName the optional application name, null = all
   * @param applicationId the optional application ID, 0 = all
   * @return true if request submitted, false if no such user session
   */
  boolean logout(long userId, long sessionGroupId, String applicationName,  long applicationId);

  /**
   * Kills all sessions of a given user.
   *
   * @param userId the user id
   * @param sessionGroupId the optional session group, 0 = all
   * @param applicationName the optional application name, null = all
   * @param applicationId the optional application ID, 0 = all
   * @return the number of sessions killed
   */
  int kill(long userId, long sessionGroupId, String applicationName,  long applicationId);

}
