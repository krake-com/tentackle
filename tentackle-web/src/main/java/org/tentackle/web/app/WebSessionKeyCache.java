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


package org.tentackle.web.app;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.tentackle.misc.ApplicationException;
import org.tentackle.session.SessionInfo;

/**
 * A cache mapping user session infos to the web container's session keys.
 *
 * @author harald
 * @param <T> the session key type
 */
public class WebSessionKeyCache<T> {

  // the session info map, key is the session-key
  private final ConcurrentHashMap<T, SoftReference<SessionInfo>> userInfoMap;
  private CleanupThread cleanupThread;



  // thread to clean up soft references
  private class CleanupThread extends Thread {

    private final long ms;
    private boolean stop;

    private CleanupThread(long ms) {
      this.ms = ms;
      setDaemon(true);
    }

    private void requestToStop()  {
      stop = true;
      interrupt();
    }

    @Override
    public void run() {
      while (!interrupted() && !stop) {
        try {
          sleep(ms);
          for (Iterator<SoftReference<SessionInfo>> iter = userInfoMap.values().iterator(); iter.hasNext(); ) {
            SoftReference<SessionInfo> ref = iter.next();
            if (ref.get() == null) {
              iter.remove();
            }
          }
        }
        catch (InterruptedException ex) {
          if (stop) {
            break;
          }
          // continue
        }
      }
    }
  }




  /**
   * Creates a session key cache.
   */
  public WebSessionKeyCache() {
    userInfoMap = new ConcurrentHashMap<>();
  }



  /**
   * Starts the session key cache.<p>
   * This will start a thread to cleanup crashed sessions.
   *
   * @param cleanupInterval interval in [ms] to run cleanup
   * @throws ApplicationException if the cleanup thread is already running
   */
  public void startup(long cleanupInterval) throws ApplicationException {
    if (cleanupInterval <= 0) {
      throw new IllegalArgumentException("cleanupInterval must be > 0");
    }
    if (cleanupThread != null && cleanupThread.isAlive()) {
      throw new ApplicationException("session cache cleanup thread already running");
    }
    // start the session pool cleanup thread
    cleanupThread = new CleanupThread(cleanupInterval);    // 5 minutes
    cleanupThread.start();
  }


  /**
   * Terminates the session cache.<p>
   *
   * @throws ApplicationException if the cleanup thread is not running at all
   */
  public void terminate() throws ApplicationException {
    if (cleanupThread != null) {
      boolean isAlive = cleanupThread.isAlive();
      if (isAlive) {
        cleanupThread.requestToStop();
        try {
          cleanupThread.join();
        }
        catch (InterruptedException ex) {
          // nothing we can do...
        }
      }
      cleanupThread = null;
      if (!isAlive) {
        throw new ApplicationException("session cache cleanup thread died");
      }
    }
    else  {
      throw new ApplicationException("session cache cleanup thread hasn't been started");
    }
  }


  /**
   * Adds a mapping between a session and a user info.<br>
   * This is usually done in the login controller.
   * If a session with that key already exists, the user info
   * will be replaced.
   *
   * @param sessionKey the (unique) session key
   * @param sessionInfo the user's session info
   */
  public void addSession(T sessionKey, SessionInfo sessionInfo) {
    userInfoMap.put(sessionKey, new SoftReference<>(sessionInfo));
  }


  /**
   * Removes a mapping between a session and a user info.<br>
   * This is usually done in the logout controller.
   * If there is no such session, the method will do nothing.
   *
   * @param sessionKey the (unique) session key
   */
  public void removeSession(T sessionKey) {
    userInfoMap.remove(sessionKey);
  }


  /**
   * Gets the session.
   *
   * @param sessionKey the session key
   * @return the session, null if no such session
   */
  public SessionInfo getSession(T sessionKey) {
    SoftReference<SessionInfo> sessionInfoRef = userInfoMap.get(sessionKey);
    if (sessionInfoRef != null) {
      SessionInfo userInfo = sessionInfoRef.get();
      if (userInfo != null) {
        // not cleared so far: we can use it
        return userInfo;
      }
    }
    return null;
  }


  /**
   * Gets the session keys for a user session info.
   *
   * @param sessionInfo the user info
   * @return the session keys, never null
   */
  public Collection<T> getSessionKeys(SessionInfo sessionInfo) {
    Collection<T> sessionKeys = new ArrayList<>();
    for (Iterator<Map.Entry<T, SoftReference<SessionInfo>>> iter = userInfoMap.entrySet().iterator(); iter.hasNext();) {
      Map.Entry<T,SoftReference<SessionInfo>> entry = iter.next();
      SessionInfo info = entry.getValue().get();
      if (info == null) {
        iter.remove();
      }
      else if (info.equals(sessionInfo)) {
        sessionKeys.add(entry.getKey());
      }
    }
    return sessionKeys;
  }

}
