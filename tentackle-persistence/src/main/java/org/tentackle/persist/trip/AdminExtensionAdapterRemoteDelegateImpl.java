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

import org.tentackle.app.LogoutEvent;
import org.tentackle.common.Compare;
import org.tentackle.common.Constants;
import org.tentackle.common.Timestamp;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.trip.RemoteDbDelegateImpl;
import org.tentackle.dbms.trip.RemoteDbSessionImpl;
import org.tentackle.log.Logger;
import org.tentackle.pdo.AdminExtension.SessionData;
import org.tentackle.pdo.DomainContext;
import org.tentackle.persist.AdminExtensionAdapter;
import org.tentackle.security.SecurityException;
import org.tentackle.security.SecurityFactory;
import org.tentackle.security.SecurityResult;
import org.tentackle.session.SessionInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Admin extension implementation.
 *
 * @author harald
 */
public class AdminExtensionAdapterRemoteDelegateImpl extends RemoteDbDelegateImpl<AdminExtensionAdapter>
       implements AdminExtensionAdapterRemoteDelegate {

  private static final Logger LOGGER = Logger.get(AdminExtensionAdapterRemoteDelegateImpl.class);

  /**
   * Creates the admin extension implementation.
   *
   * @param session the server session
   */
  public AdminExtensionAdapterRemoteDelegateImpl(RemoteDbSessionImpl session, Class<AdminExtensionAdapter> clazz) {
    super(session, clazz);
  }

  @Override
  public List<SessionData> getSessions(DomainContext context) {
    context.setSession(getSession());
    checkPermission(context);
    List<SessionData> sessions = new ArrayList<>();
    for (RemoteDbSessionImpl remoteSession : RemoteDbSessionImpl.getOpenSessions()) {
      sessions.add(new SessionDataImpl(remoteSession));
    }
    sessions.sort((SessionData o1, SessionData o2) -> {
      int rv = Compare.compare(o1.getUserName(), o2.getUserName());
      if (rv == 0) {
        rv = Long.compare(o1.getSessionNumber(), o2.getSessionNumber());
      }
      return rv;
    });
    return sessions;
  }

  @Override
  public int kill(DomainContext context, long userId, long sessionGroupId,
                  String applicationName, long applicationId) {
    return kill(context, userId, sessionGroupId, applicationName, applicationId, true);
  }

  @Override
  public boolean logout(DomainContext context, long userId, long sessionGroupId,
                        String applicationName, long applicationId) {
    return kill(context, userId, sessionGroupId, applicationName, applicationId, false) == 1;
  }


  protected int kill(DomainContext context, long userId, long sessionGroupId,
                     String applicationName, long applicationId, boolean kill) {

    LOGGER.warning("{0} for userId {1}, groupId {2}, application {3}, applicationId {4}",
                   kill ? "killing session(s)" : "submitting logout request",
                   userId, sessionGroupId, applicationName, applicationId);
    context.setSession(getSession());
    checkPermission(context);
    int count = 0;
    for (RemoteDbSessionImpl openSession : RemoteDbSessionImpl.getOpenSessions()) {
      if (openSession.getClientSessionInfo().getUserId() == userId &&
          (kill || openSession.isModificationTrackerSession()) &&
          (sessionGroupId == 0 || openSession.getSession().getExportedSessionGroupId() == sessionGroupId) &&
          (applicationName == null ||
           Objects.equals(openSession.getClientSessionInfo().getApplicationName(), applicationName)) &&
          (applicationId == 0 || openSession.getClientSessionInfo().getApplicationId() == applicationId)) {

        Db db = openSession.getSession();
        if (kill) {
          if (db != null) {
            // switch owner to prevent any further use and allow close by me
            db.setOwnerThread(Thread.currentThread());
          }
          openSession.close();
        }
        else {
          openSession.addMasterSerialEvent(new LogoutEvent());
        }
        count++;
      }
    }
    return count;
  }


  /**
   * Checks the permissions before executing the method.
   *
   * @param context the invoking user's domain context
   */
  private void checkPermission(DomainContext context) {
    SecurityFactory sf = SecurityFactory.getInstance();
    org.tentackle.security.SecurityManager sm = sf.getSecurityManager();
    SecurityResult sr = sm.evaluate(context, sf.getExecutePermission(), getServicedClass());
    if (!sr.isAccepted()) {
      throw new SecurityException(sr.explain("no execute permission for " + getServicedClass().getName()));
    }
  }


  /**
   * DTO to transfer the session data.
   */
  private static class SessionDataImpl implements SessionData {

    private long userId;
    private String userName;
    private Timestamp since;
    private String applicationName;
    private long applicationId;
    private String sessionName;
    private String clientVersion;
    private String locale;
    private String vmInfo;
    private String osInfo;
    private String hostInfo;
    private String timeZone;
    private String clientHost;
    private long sessionNumber;
    private long sessionGroup;
    private String options;
    private boolean cloned;

    private SessionDataImpl(RemoteDbSessionImpl remoteSession) {
      SessionInfo info = remoteSession.getClientSessionInfo();
      userId = info.getUserId();
      userName = info.getUserName();
      since = new Timestamp(info.getSince());
      applicationName = info.getApplicationName();
      applicationId = info.getApplicationId();
      sessionName = info.getSessionName();
      clientVersion = info.getClientVersion() == null ? "?" : info.getClientVersion();
      locale = info.getLocale() == null ? "?" : info.getLocale().toString();
      vmInfo = info.getVmInfo();
      osInfo = info.getOsInfo();
      hostInfo = info.getHostInfo();
      if (info.getTimeZone() != null) {
        int offMin = info.getTimeZone().getOffset(System.currentTimeMillis()) / (int) Constants.MINUTE_MS;
        timeZone = info.getTimeZone().getID() + " (" + String.format("%+d:%02d", offMin / 60, offMin % 60) + ")";
      }
      else {
        timeZone = null;
      }
      clientHost = remoteSession.getClientHostString();
      sessionNumber = remoteSession.getSessionNumber();
      sessionGroup = remoteSession.getSession().getExportedSessionGroupId();
      options = remoteSession.getOptions();
      cloned = remoteSession.getClientSessionInfo().isCloned();
    }

    private SessionDataImpl() {
      // for TRIP
    }

    @Override
    public long getUserId() {
      return userId;
    }

    @Override
    public String getUserName() {
      return userName;
    }

    @Override
    public Timestamp getSince() {
      return since;
    }

    @Override
    public String getApplicationName() {
      return applicationName;
    }

    @Override
    public long getApplicationId() {
      return applicationId;
    }

    @Override
    public String getSessionName() {
      return sessionName;
    }

    @Override
    public String getClientVersion() {
      return clientVersion;
    }

    @Override
    public String getLocale() {
      return locale;
    }

    @Override
    public String getVmInfo() {
      return vmInfo;
    }

    @Override
    public String getOsInfo() {
      return osInfo;
    }

    @Override
    public String getHostInfo() {
      return hostInfo;
    }

    @Override
    public String getTimeZone() {
      return timeZone;
    }

    @Override
    public String getClientHost() {
      return clientHost;
    }

    @Override
    public long getSessionNumber() {
      return sessionNumber;
    }

    @Override
    public long getSessionGroup() {
      return sessionGroup;
    }

    @Override
    public String getOptions() {
      return options;
    }

    @Override
    public boolean isCloned() {
      return cloned;
    }
  }

}
