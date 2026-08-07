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

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.session.SessionInfo;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cache mapping user session infos to the web container's session keys.
 *
 * @author harald
 * @param <T> the session key type
 */
public class WebSessionKeyCache<T> {

  // the session info map, key is the session-key
  private final ConcurrentHashMap<T, SoftReference<SessionInfo>> sessionInfoMap;
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
          sessionInfoMap.values().removeIf(ref -> ref.get() == null);
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
    sessionInfoMap = new ConcurrentHashMap<>();
  }



  /**
   * Starts the session key cache.<p>
   * This will start a thread to clean up crashed sessions.
   * <p>
   * Throws TentackleRuntimeException if the cleanup thread is already running.
   *
   * @param cleanupInterval interval in [ms] to run cleanup
   */
  public void startup(long cleanupInterval) {
    if (cleanupInterval <= 0) {
      throw new IllegalArgumentException("cleanupInterval must be > 0");
    }
    if (cleanupThread != null && cleanupThread.isAlive()) {
      throw new TentackleRuntimeException("session cache cleanup thread already running");
    }
    // start the session pool cleanup thread
    cleanupThread = new CleanupThread(cleanupInterval);    // 5 minutes
    cleanupThread.start();
  }


  /**
   * Returns whether the session key cache has been started.<br>
   * Only a started cache can be {@link #terminate}d.
   *
   * @return true if {@link #startup} has been invoked and {@link #terminate} not yet
   */
  public boolean isRunning() {
    return cleanupThread != null;
  }


  /**
   * Terminates the session cache.
   * <p>
   * Throws TentackleRuntimeException if the cleanup thread is not running at all.
   */
  public void terminate() {
    if (cleanupThread != null) {
      boolean isAlive = cleanupThread.isAlive();
      if (isAlive) {
        cleanupThread.requestToStop();
        try {
          cleanupThread.join();
        }
        catch (InterruptedException ex) {
          // we're already terminating...
        }
      }
      cleanupThread = null;
      if (!isAlive) {
        throw new TentackleRuntimeException("session cache cleanup thread died");
      }
    }
    else  {
      throw new TentackleRuntimeException("session cache cleanup thread hasn't been started");
    }
  }


  /**
   * Adds a mapping between a session and a session info.<br>
   * This is usually done in the login controller.
   * If a session with that key already exists, the session info
   * will be replaced.
   *
   * @param sessionKey the (unique) session key
   * @param sessionInfo the user's session info
   */
  public void addSessionInfo(T sessionKey, SessionInfo sessionInfo) {
    sessionInfoMap.put(sessionKey, new SoftReference<>(sessionInfo));
  }


  /**
   * Removes a mapping between a session and a session info.<br>
   * This is usually done in the logout controller.
   * If there is no such session, the method will do nothing.
   *
   * @param sessionKey the (unique) session key
   */
  public void removeSessionInfo(T sessionKey) {
    sessionInfoMap.remove(sessionKey);
  }


  /**
   * Gets the session info.
   *
   * @param sessionKey the session key
   * @return the session info, null if no such session info
   */
  public SessionInfo getSessionInfo(T sessionKey) {
    SoftReference<SessionInfo> sessionInfoRef = sessionInfoMap.get(sessionKey);
    if (sessionInfoRef != null) {
      SessionInfo sessionInfo = sessionInfoRef.get();
      if (sessionInfo != null) {
        // not cleared so far: we can use it
        return sessionInfo;
      }
      // drop the dead entry, but only if a concurrent login didn't replace it meanwhile
      sessionInfoMap.remove(sessionKey, sessionInfoRef);
    }
    return null;
  }


  /**
   * Gets the session keys for a user session info.
   *
   * @param sessionInfo the session info
   * @return the session keys, never null
   */
  public Collection<T> getSessionKeys(SessionInfo sessionInfo) {
    Collection<T> sessionKeys = new ArrayList<>();
    for (Map.Entry<T, SoftReference<SessionInfo>> entry: sessionInfoMap.entrySet()) {
      SessionInfo info = entry.getValue().get();
      if (info == null) {
        // drop the dead entry, but only if a concurrent login didn't replace it meanwhile
        sessionInfoMap.remove(entry.getKey(), entry.getValue());
      }
      else if (info.equals(sessionInfo)) {
        sessionKeys.add(entry.getKey());
      }
    }
    return sessionKeys;
  }

}
