/*
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

package org.tentackle.persist.rmi;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.tentackle.common.Compare;
import org.tentackle.common.Timestamp;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.pdo.AdminExtension.SessionData;
import org.tentackle.pdo.DomainContext;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.rmi.RemoteDbSessionImpl;
import org.tentackle.dbms.rmi.RemoteDelegateImpl;
import org.tentackle.persist.AdminExtensionAdapter;
import org.tentackle.security.SecurityFactory;
import org.tentackle.security.SecurityResult;
import org.tentackle.session.SessionInfo;

/**
 * Admin extension implementation.
 *
 * @author harald
 */
public class AdminExtensionAdapterRemoteDelegateImpl extends RemoteDelegateImpl<AdminExtensionAdapter>
       implements AdminExtensionAdapterRemoteDelegate {

  /**
   * the logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(AdminExtensionAdapterRemoteDelegateImpl.class);

  /**
   * Creates the admin extension implementation.
   *
   * @param session the server session
   * @throws RemoteException if failed
   */
  public AdminExtensionAdapterRemoteDelegateImpl(RemoteDbSessionImpl session, Class<AdminExtensionAdapter> clazz)
         throws RemoteException {

    super(session, clazz);
  }

  @Override
  public List<SessionData> getSessions(DomainContext context) throws RemoteException {
    try {
      context.setSession(getSession());
      checkPermission(context);
      List<SessionData> sessions = new ArrayList<>();
      for (RemoteDbSessionImpl remoteSession: RemoteDbSessionImpl.getOpenSessions()) {
        sessions.add(new SessionDataImpl(remoteSession));
      }
      Collections.sort(sessions, (SessionData o1, SessionData o2) -> {
        int rv = Compare.compare(o1.getUserName(), o2.getUserName());
        if (rv == 0) {
          rv = Long.compare(o1.getSessionNumber(), o2.getSessionNumber());
        }
        return rv;
      });
      return sessions;
    }
    catch (RuntimeException ex) {
      throw createException(ex);
    }
  }

  @Override
  public int kill(DomainContext context, long userId, long sessionGroupId, String applicationName, long applicationId)
         throws RemoteException {
    LOGGER.warning("killing session(s) for userId {0}, groupId {1}, application {2}, applicationId {3}",
                   userId, sessionGroupId, applicationName, applicationId);
    try {
      context.setSession(getSession());
      checkPermission(context);
      int count = 0;
      for (RemoteDbSessionImpl openSession : RemoteDbSessionImpl.getOpenSessions()) {
        if (openSession.getClientSessionInfo().getUserId() == userId &&
            (sessionGroupId == 0 || openSession.getSession().getSessionGroupId() == sessionGroupId) &&
            (applicationName == null ||
            Objects.equals(openSession.getClientSessionInfo().getApplicationName(), applicationName)) &&
            (applicationId == 0 || openSession.getClientSessionInfo().getApplicationId() == applicationId)) {

          Db db = openSession.getSession();
          if (db != null) {
            // switch owner to prevent any further use and allow close by me
            db.setOwnerThread(Thread.currentThread());
          }
          openSession.close();
          count++;
        }
      }
      return count;
    }
    catch (RuntimeException ex) {
      throw createException(ex);
    }
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
  private static class SessionDataImpl implements SessionData, Serializable {

    private static final long serialVersionUID = 1L;

    private static final NumberFormat OFFSET_FMT = new DecimalFormat("+#0.#");

    private final long userId;
    private final String userName;
    private final Timestamp since;
    private final String applicationName;
    private final long applicationId;
    private final String clientVersionInfo;
    private final String locale;
    private final String vmInfo;
    private final String osInfo;
    private final String hostInfo;
    private final String timeZone;
    private final String clientHost;
    private final long sessionNumber;
    private final long sessionGroup;
    private final String options;

    private SessionDataImpl(RemoteDbSessionImpl remoteSession) {
      SessionInfo info = remoteSession.getClientSessionInfo();
      userId = info.getUserId();
      userName = info.getUserName();
      since = new Timestamp(info.getSince());
      applicationName = info.getApplicationName();
      applicationId = info.getApplicationId();
      clientVersionInfo = info.getClientVersionInfo() == null ? "?" : info.getClientVersionInfo().toString();
      locale = info.getLocale() == null ? "?" : info.getLocale().toString();
      vmInfo = info.getVmInfo();
      osInfo = info.getOsInfo();
      hostInfo = info.getHostInfo();
      double tzOffsetH = info.getTimeZone().getOffset(System.currentTimeMillis()) / 3600000.0;
      synchronized(OFFSET_FMT) {
        timeZone = info.getTimeZone() == null ?
                          "?" : (info.getTimeZone().getID() + " (" + OFFSET_FMT.format(tzOffsetH) + "h)");
      }
      clientHost = remoteSession.getClientHostString();
      sessionNumber = remoteSession.getSessionNumber();
      sessionGroup = remoteSession.getSession().getSessionGroupId();
      options = remoteSession.getOptions();
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
    public String getClientVersionInfo() {
      return clientVersionInfo;
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
  }

}
