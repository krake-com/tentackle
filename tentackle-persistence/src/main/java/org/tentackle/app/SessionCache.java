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


package org.tentackle.app;

import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import org.tentackle.misc.ApplicationException;
import org.tentackle.pdo.SessionInfo;

/**
 * A session cache for to map user infos to sessions.
 * <p>
 * Primarily used in web-applications where short-lived roundtrips
 * must be associated to long-lived authenticated sessions.
 *
 * @author harald
 */
public class SessionCache {

  // the user info map, key is the session-key
  private final ConcurrentHashMap<Object,SoftReference<SessionInfo>> userInfoMap;
  private CleanupThread cleanupThread;



  // thread to clean up soft references
  private class CleanupThread extends Thread {

    private final long ms;
    private boolean stop;

    CleanupThread(long ms) {
      this.ms = ms;
      setDaemon(true);
    }

    void requestToStop()  {
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
   * Creates a session cache.
   */
  public SessionCache() {
    userInfoMap = new ConcurrentHashMap<>();
  }



  /**
   * Starts the session cache.<p>
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
   * @param userInfo the user info
   */
  public void addSession(Object sessionKey, SessionInfo userInfo) {
    userInfoMap.put(sessionKey, new SoftReference<>(userInfo));
  }


  /**
   * Removes a mapping between a session and a user info.<br>
   * This is usually done in the logout controller.
   * If there is no such session, the method will do nothing.
   *
   * @param sessionKey the (unique) session key
   */
  public void removeSession(Object sessionKey) {
    userInfoMap.remove(sessionKey);
  }



  /**
   * Gets the session.
   *
   * @param sessionKey the session key
   * @return the session, null if no such session
   */
  public SessionInfo getSession(Object sessionKey) {
    SoftReference<SessionInfo> userInfoRef = userInfoMap.get(sessionKey);
    if (userInfoRef != null) {
      SessionInfo userInfo = userInfoRef.get();
      if (userInfo != null) {
        // not cleared so far: we can use it
        return userInfo;
      }
    }
    return null;
  }

}
