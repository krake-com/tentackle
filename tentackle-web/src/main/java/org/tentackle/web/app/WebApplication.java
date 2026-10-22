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

package org.tentackle.web.app;

import org.tentackle.dbms.Db;
import org.tentackle.persist.app.AbstractServerApplication;
import org.tentackle.session.MultiUserSessionPool;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionPool;

import java.util.Collection;

/**
 * The Tentackle side of a bridge to a web framework.
 * <p>
 * A web application is an {@link AbstractServerApplication}: it logs into its backend (a database or an
 * upstream Tentackle server) and owns a session pool, so it is a regular node of a multi-tier cascade.
 * Unlike a {@code ServerApplication}, however, it opens no TRIP endpoint, i.e., it is a leaf toward HTTP.
 * <p>
 * On top of that, it maps the web framework's session keys to the users' {@link SessionInfo}s and lends
 * a pooled persistence session per request:
 * <ol>
 *   <li>{@link #addWebSession} after authentication,</li>
 *   <li>{@link #getSession} at the beginning of each request,</li>
 *   <li>{@link #putSession} after the response has been sent (in a finally-block),</li>
 *   <li>{@link #removeWebSession} on logout or when the web session expires.</li>
 * </ol>
 * The recommended boot order is to start the Tentackle application first and launch the web framework
 * from within {@link #finishStartup()}.
 *
 * @author harald
 * @param <T> the web session key type, must provide proper {@code equals} and {@code hashCode}
 */
public abstract class WebApplication<T> extends AbstractServerApplication {

  /**
   * The web session info cache.
   * <p>
   * Created along with the application, not in {@link #startup}, so that the cache is never null:
   * a web container may still be draining requests while the application is already cleaning up.
   * Only the cache's cleanup thread follows the application's lifecycle.
   */
  private final WebSessionKeyCache<T> sessionInfoCache;


  /**
   * Creates an instance of a web application.
   *
   * @param name the application name, null for default name
   * @param version the application version, null for the default version
   */
  public WebApplication(String name, String version) {
    super(name, version);
    sessionInfoCache = new WebSessionKeyCache<>();
  }


  /**
   * Adds a mapping between a web session key and a user's session info.<br>
   * This is usually done in the login controller.
   * If a session with that key already exists, the session info
   * will be replaced.
   *
   * @param sessionKey the unique web session key
   * @param sessionInfo the session info
   */
  public void addWebSession(T sessionKey, SessionInfo sessionInfo) {
    sessionInfoCache.addSessionInfo(sessionKey, sessionInfo);
  }


  /**
   * Removes a mapping between a web session key and user session info.<br>
   * This is usually done in the logout controller.
   * If there is no such session, the method will do nothing.
   *
   * @param sessionKey the (unique) web session key
   */
  public void removeWebSession(T sessionKey) {
    sessionInfoCache.removeSessionInfo(sessionKey);
  }


  /**
   * Gets the web session keys for user session info.
   * <p>
   * The session infos are compared by {@code SessionInfo.equals}, i.e., by username for the default
   * implementation. Hence, any session info describing the user finds all web sessions of that user.
   *
   * @param sessionInfo the user session info
   * @return the web session keys, never null, independent of the cache
   */
  public Collection<T> getSessionKeys(SessionInfo sessionInfo) {
    return sessionInfoCache.getSessionKeys(sessionInfo);
  }


  /**
   * Gets a persistence session from the session pool by a web session key.
   * <p>
   * The session is taken from the local session pool if the application is connected to a database,
   * or from the remote session pool if it is a client of another server. The user's session info is
   * attached to it, so that it executes as the user that has been authenticated by the web framework.
   * The session must be given back via {@link #putSession} when the request is finished.
   *
   * @param sessionKey the web session key
   * @return the session, null if the key is unknown, its session info has been reclaimed by the garbage
   * collector or there is no session pool (startup not finished yet or application already cleaned up)
   */
  public Session getSession(T sessionKey) {
    SessionInfo sessionInfo = sessionInfoCache.getSessionInfo(sessionKey);
    if (sessionInfo != null) {
      // not cleared so far: we can use it
      SessionPool sessionPool = getSessionPool();
      Db session = null;
      if (sessionPool != null) {
        session = (Db) sessionPool.get();
      }
      else {
        MultiUserSessionPool remoteSessionPool = getRemoteSessionPool();
        if (remoteSessionPool != null) {
          session = (Db) remoteSessionPool.get(sessionInfo);
        }
      }
      if (session != null) {
        session.setSessionInfo(sessionInfo); // attach session info
        return session;
      }
    }
    return null;
  }


  /**
   * Releases a persistence session.<br>
   * Should be invoked after the response has been sent to the web client.
   * <p>
   * If the application is connected to a database, the user's session info is detached from the session,
   * since pooled sessions are shared among all users. Sessions of the remote session pool remain bound to
   * their user. In both cases, the session is returned to its own pool (see {@link Session#getPool()}).
   *
   * @param session the persistence session to release
   */
  public void putSession(Session session) {
    if (getSessionPool() != null) {   // only if not a remote server!
      /*
       * dereference session info (possible target for the GC).
       * If (due to tight memory or timeout) the session info gets garbage-collected,
       *  the user must log in again.
       */
      ((Db) session).setSessionInfo(null);
    }
    session.getPool().put(session);    // always pooled, works also for MultiUserDbPool
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to start the cleanup thread of the web session key cache.
   */
  @Override
  protected void finishStartup() {
    super.finishStartup();

    // start the session cache
    sessionInfoCache.startup(300000);    // 5 minutes
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to terminate the cleanup thread of the web session key cache, if started.
   */
  @Override
  protected void cleanup() {
    try {
      /*
       * Only terminate what has been started: if the startup failed before finishStartup(),
       * the cleanup thread never ran and terminate() would throw, aborting the cleanup below.
       */
      if (sessionInfoCache.isRunning()) {
        sessionInfoCache.terminate();
      }
    }
    finally {
      // whatever happened to the cleanup thread, the sessions, pools and drivers must be released
      super.cleanup();
    }
  }

}
