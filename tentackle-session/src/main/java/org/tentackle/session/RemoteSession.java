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

package org.tentackle.session;

import org.tentackle.log.Logger;

/**
 * The remote session.<br>
 * Provides access to the remote (server-side) client session.
 *
 * @author harald
 */
public interface RemoteSession {

  /**
   * Gets the client user info.
   * <p>
   * The server may set certain values in the session info that may be
   * of interest by the client application, for example the userId.
   *
   * @return the session info
   */
  SessionInfo getClientSessionInfo();


  /**
   * Logs at the server-side.
   *
   * @param level the log level
   * @param message the log message
   */
  void log(Logger.Level level, String message);


  /**
   * Logs the statistics at the server-side.<br>
   *
   * @param level the logging level
   * @param clear true if clear statistics after dump
   */
  void logStatistics(Logger.Level level, boolean clear);

}
