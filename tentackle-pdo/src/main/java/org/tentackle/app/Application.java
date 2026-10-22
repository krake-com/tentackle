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

package org.tentackle.app;

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.misc.CommandLine;
import org.tentackle.misc.Identifiable;
import org.tentackle.misc.VolatileHolder;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionProvider;

import java.util.Properties;

interface ApplicationHolder {
  VolatileHolder<Application> HOLDER = new VolatileHolder<>();
}

/**
 * Tentackle application.
 */
public interface Application extends SessionProvider, DomainContextProvider {

  /**
   * Gets the application instance currently running.
   *
   * @return the application singleton, null if no application running yet
   */
  static Application getInstance() {
    // no synchronized necessary due to VolatileHolder
    return ApplicationHolder.HOLDER.get();
  }

  /**
   * Registers this application.<br>
   * Makes sure that only one application is running at a time.
   * <p>
   * Throws TentackleRuntimeException if another application is already running.
   */
  default void register() {
    synchronized(ApplicationHolder.class) {
      Application running = getInstance();
      if (running != null && running != this) {
        throw new TentackleRuntimeException("application '" + running + "' already registered");
      }
      ApplicationHolder.HOLDER.accept(this);
    }
  }

  /**
   * Unregisters this application.
   * <p>
   * Throws TentackleRuntimeException if no application is running at all or another application is still running.
   */
  default void unregister() {
    synchronized(ApplicationHolder.class) {
      Application running = getInstance();
      if (running != this) {
        if (running == null) {
          throw new TentackleRuntimeException("no application registered at all");
        }
        throw new TentackleRuntimeException("application '" + this + "' cannot unregister application '" + running + "'");
      }
      ApplicationHolder.HOLDER.accept(null);
    }
  }



  /**
   * Gets the application name.
   *
   * @return the name
   */
  String getName();

  /**
   * Gets the application version.
   *
   * @return the version
   */
  String getVersion();

  /**
   * Gets the creation time in epoch milliseconds.
   *
   * @return the creation time of this application
   */
  long getCreationTime();

  /**
   * Gets the command line.
   *
   * @return the commandline, null if not started
   */
  CommandLine getCommandLine();

  /**
   * Gets the session info.
   *
   * @return the session info
   */
  SessionInfo getSessionInfo();

  /**
   * Gets an application property.
   *
   * @param key the property's name
   * @return the value of the key, null if no such property, the empty string if no value for this property
   */
  String getProperty(String key);

  /**
   * Gets an application property case-insensitive.
   *
   * @param key the property's name
   * @return the value of the key, null if no such property, the empty string if no value for this property
   */
  String getPropertyIgnoreCase(String key);

  /**
   * Gets an application property as a character array.<br>
   * Useful for passwords and alike.
   *
   * @param key the property's name
   * @return the value of the key, null if no such property, the empty array if no value for this property
   */
  char[] getPropertyAsChars(String key);

  /**
   * Gets an application property as a character array, case-insensitive.<br>
   * Useful for passwords and alike.
   *
   * @param key the property's name
   * @return the value of the key, null if no such property, the empty array if no value for this property
   */
  char[] getPropertyAsCharsIgnoreCase(String key);

  /**
   * Applies further application properties.<br>
   * If the given properties are different from the application properties, they will be copied to
   * the application properties.
   *
   * @param properties the properties, null if none
   */
  void applyProperties(Properties properties);

  /**
   * Gets the identifiable corresponding to the ID of a user.
   *
   * @param <U> the user type
   * @param context the domain context
   * @param userId the user id
   * @return the user object, null if unknown
   */
  <U extends Identifiable> U getUser(DomainContext context, long userId);

  /**
   * Returns whether the running application is a server.
   *
   * @return true if server, false if not a server or no application running at all
   */
  boolean isServer();

  /**
   * Returns whether the running application is interactive.
   *
   * @return true if interaction with user, false if server, daemon or whatever
   */
  boolean isInteractive();

  /**
   * Starts the application.
   *
   * @param args the arguments (usually from commandline), null or empty if none
   */
  void start(String[] args);

  /**
   * Starts the application without further arguments.
   */
  default void start() {
    start(null);
  }

  /**
   * Terminates the application with optional error handling.
   *
   * @param exitValue the stop value for System.exit()
   * @param exitThrowable an exception causing the termination, null if none
   */
  void stop(int exitValue, Throwable exitThrowable);

  /**
   * Gracefully terminates the application.
   */
  default void stop() {
    stop(0, null);
  }

}
