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

package org.tentackle.web.app;

import java.util.Collection;
import org.tentackle.app.AbstractApplication;
import org.tentackle.dbms.Db;
import org.tentackle.persist.app.AbstractServerApplication;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;

/**
 * Web Server Application.
 * <p>
 * Web applications usually run in a container such as glassfish.
 *
 * @author harald
 * @param <T> the web session key type
 */
public abstract class WebApplication<T> extends AbstractServerApplication {

  /**
   * Gets the current web application instance.<p>
   *
   * This is just a convenience method to {@link AbstractApplication#getRunningApplication()}.
   *
   * @param <T> the web session key type
   * @return the application, null if not started yet
   */
  @SuppressWarnings("unchecked")
  public static <T> WebApplication<T> getWebApplication() {
    return (WebApplication<T>) getRunningApplication();
  }


  private WebSessionKeyCache<T> sessionCache;   // the web session cache


  /**
   * Creates an instance of a web application.
   * @param name the application name
   */
  public WebApplication(String name) {
    super(name);
  }


  /**
   * Adds a mapping between a web session key and a user session info.<br>
   * This is usually done in the login controller.
   * If a session with that key already exists, the user info
   * will be replaced.
   *
   * @param sessionKey the (unique) web session key
   * @param userInfo the user info
   */
  public void addWebSession(T sessionKey, SessionInfo userInfo) {
    sessionCache.addSession(sessionKey, userInfo);
  }


  /**
   * Removes a mapping between a web session key and a user session info.<br>
   * This is usually done in the logout controller.
   * If there is no such session, the method will do nothing.
   *
   * @param sessionKey the (unique) web session key
   */
  public void removeWebSession(T sessionKey) {
    sessionCache.removeSession(sessionKey);
  }


  /**
   * Gets the web session keys for a user session info.<br>
   *
   * @param sessionInfo the user session info
   * @return the web session keys, never null
   */
  public Collection<T> getSessionKeys(SessionInfo sessionInfo) {
    return sessionCache.getSessionKeys(sessionInfo);
  }


  /**
   * Gets a persistence session from the session pool by a web session key.<br>
   *
   * @param sessionKey the web session key
   * @return the attached session or null if no such session
   */
  public Session getSession(T sessionKey) {
    SessionInfo sessionInfo = sessionCache.getSession(sessionKey);
    if (sessionInfo != null) {
      // not cleared so far: we can use it
      Db db = (Db) getSessionPool().getSession();
      db.setSessionInfo(sessionInfo); // attach userinfo
      return db;
    }
    else  {
      return null;
    }
  }


  /**
   * Release a persistence session.<br>
   * Should be invoked after sending/rendering the response to the web browser.
   *
   * @param session the persistence session to release
   */
  public void putSession(Session session) {
    /**
     * dereference user info (possible target for the GC).
     * If (due to tight memory or timeout) the userinfo gets garbage
     * collected the user must login again.
     */
    ((Db) session).setSessionInfo(null);
    getSessionPool().putSession(session);
  }


  @Override
  protected void startup() {
    sessionCache = new WebSessionKeyCache<>();
    super.startup();
  }


  @Override
  protected void finishStartup() {
    super.finishStartup();

    // start the session cache
    sessionCache.startup(300000);    // 5 minutes
  }


  @Override
  protected void cleanup() {
    if (sessionCache != null) {
      sessionCache.terminate();
      sessionCache = null;
    }
    super.cleanup();
  }

}
