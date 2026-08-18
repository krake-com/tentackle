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

package org.tentackle.dbms.trip;

import org.tentackle.log.Logger.Level;
import org.tentackle.session.SessionInfo;
import org.tentackle.trip.Remote;

/**
 * Application server session.<br>
 * The session will create all other delegates for the client.
 *
 * @author harald
 */
public interface RemoteDbSession extends Remote {

  /**
   * Gets the client session info.
   * <p>
   * The server may set certain values in the session info that may be
   * of interest to the client application, for example, the userId.
   *
   * @return the session info
   */
  SessionInfo getClientSessionInfo();

  /**
   * Closes a session.
   */
  void close();

  /**
   * Sends text-based logging info to the TRIP-Server.
   *
   * @param name the logger name, null if predefined
   * @param level the logging level
   * @param message the message
   */
  void log(String name, Level level, String message);

  /**
   * Logs the TRIP-statistics.
   *
   * @param level the logging level
   * @param clear true if clear statistics after dump
   */
  void logStatistics(Level level, boolean clear);

  /**
   * Gets the delegate for the remote session.
   *
   * @return the delegate
   */
  DbRemoteDelegate getRemoteDelegate();

  /**
   * Gets the remote delegate for a given class name.
   *
   * @param <T> the delegate type
   * @param classname is the name to get the delegates for
   * @return the delegate
   */
  <T extends Remote> T getRemoteDelegate(String classname);

  /**
   * Gets the remote delegates for the given class names in a single roundtrip.<br>
   * This is the bulk variant of {@link #getRemoteDelegate(String)}, used to preload the delegates
   * of classes annotated with {@link PreloadRemoteDelegate}.
   * <p>
   * Class names the server cannot create a delegate for are answered with null instead of
   * an exception, since preloading must never break the session.
   *
   * @param classnames the class names to get the delegates for
   * @return the delegates in the order of the given class names, null for each unresolvable name
   */
  Remote[] getRemoteDelegates(String[] classnames);

}
