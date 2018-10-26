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


package org.tentackle.session;

import org.tentackle.common.ServiceFactory;


interface ModificationTrackerHolder {
  ModificationTracker INSTANCE = ServiceFactory.createService(ModificationTracker.class);
}


/**
 * Tracks global PDO changes.
 * <p>
 * The pro tracker maintains its own session.
 * The application can request an exclusive use of this session for a
 * (short) period of time and release it afterwards.
 *
 * @author harald
 */
public interface ModificationTracker extends SessionTaskDispatcher, ExclusiveSessionProvider {

  /**
   * The tracker singleton.
   *
   * @return the singleton
   */
  static ModificationTracker getInstance() {
    return ModificationTrackerHolder.INSTANCE;
  }


  /**
   * Adds a modification listener.
   *
   * @param listener the listener to add
   */
  void addModificationListener(ModificationListener listener);

  /**
   * Removes a modification listener.
   *
   * @param listener to remove
   * @return true if removed
   */
  boolean removeModificationListener(ModificationListener listener);

  /**
   * Counts the modification for a given name.<br>
   * Used to trigger modification events.
   *
   * @param session the session persisting the modification, null if threadlocal session
   * @param name a unique name to track modifications
   * @return the modification serial
   */
  long countModification(Session session, String name);

  /**
   * Registers a pure runnable to be executed if the pdo tracker is terminated
   * due to severe errors.
   *
   * @param runnable the runnable
   */
  void addShutdownRunnable(Runnable runnable);

  /**
   * Unregisters a shutdown runnable.
   *
   * @param runnable the runnable
   * @return true if runnable removed
   */
  boolean removeShutdownRunnable(Runnable runnable);

  /**
   * Gets the serial for a given class.
   *
   * @param clazz the tracked class
   * @return the table serial
   */
  long getSerial(Class<?> clazz);

  /**
   * Gets the serial for a given modification name.
   *
   * @param name the modification name
   * @return the table serial
   */
  long getSerial(String name);

  /**
   * Invalidates the tracker.<br>
   * Forces the tracking info to be rebuilt.
   */
  void invalidate();

}
