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
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.task.AbstractTask;


interface ModificationTrackerHolder {
  ModificationTracker INSTANCE = ServiceFactory.createService(ModificationTracker.class);
}


/**
 * Tracks global changes.
 * <p>
 * The tracker is a {@link SessionTaskDispatcher} that maintains its own session and periodically
 * polls for modifications, notifying the registered {@link ModificationListener}s.
 * Remote clients poll the server's tracker instead of the database, which may also push
 * {@link MasterSerialEvent}s along.
 * The application can request an exclusive use of the tracker's session for a
 * (short) period of time and release it afterward.
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


  // some convenience methods to reduce the amount of code for common use cases

  /**
   * Submits a task invoking given runnable.
   *
   * @param runnable the runnable to be executed once
   */
  static void submit(Runnable runnable) {
    getInstance().addTask(new AbstractTask() {
      @Override
      public void run() {
        runnable.run();
      }
    });
  }

  /**
   * Submits a session task invoking the given runnable within a transaction of the tracker's session.
   *
   * @param txName the transaction name, null for default <code>"&lt;ModificationTracker&gt;"</code>
   * @param runnable the runnable to be executed once
   */
  static void transaction(String txName, Runnable runnable) {
    getInstance().addTask(new AbstractSessionTask() {
      @Override
      public void run() {
        getSession().transaction(txName == null ? "<ModificationTracker>" : txName, () -> {
          runnable.run();
          return null;
        });
      }
    });
  }

  /**
   * Submits a session task invoking the given runnable within a transaction of the tracker's session.
   *
   * @param runnable the runnable to be executed once
   */
  static void transaction(Runnable runnable) {
    transaction(null, runnable);
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
   * @param session the session persisting the modification, null if thread-local session (never remote)
   * @param name a unique name to track modifications
   * @return the modification serial assigned to this modification, unique per name (the counter could be higher already)
   */
  long countModification(Session session, String name);

  /**
   * Adds a deletion to the previously counted modifications.
   *
   * @param session the session persisting the modification, null if thread-local session (never remote)
   * @param name a unique name to track modifications
   * @param id the ID of the object that was deleted
   * @param tableSerial the modification serial obtained from {@link #countModification(Session, String)}
   */
  void addDeletion(Session session, String name, long id, long tableSerial);

  /**
   * Invoked whenever a transaction is rolled back.<br>
   * Allows the tracker to adjust the pending counts.
   *
   * @param session the transaction's session (never remote)
   * @param txNumber the transaction number
   */
  void rollbackModification(Session session, long txNumber);

  /**
   * Registers a runnable to be executed once when the tracker thread terminates.<br>
   * This applies to an orderly termination as well as to a termination due to severe errors.
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
   * Gets the pair of id/serial for a given class.<br>
   * The modification name is determined from the class's table name.
   *
   * @param clazz the tracked class
   * @return the id/serial pair, never null
   */
  IdSerialTuple getIdSerialForClass(Class<?> clazz);

  /**
   * Gets the pair of id/serial for a given modification name.<br>
   * Used in remote connections.
   *
   * @param name the name to look up
   * @return the id/serial pair for the name, never null
   */
  IdSerialTuple getIdSerialForName(String name);

  /**
   * Invalidates the tracker.<br>
   * Forces the tracking info to be rebuilt.
   */
  void invalidate();

  /**
   * Returns whether the tracker runs in local client mode.<br>
   * In this mode, there is no middle-tier server and the clients
   * are directly connected to the database.<br>
   * Although this mode isn't recommended, it is an option for simple desktop applications
   * with only very few parallel users.
   *
   * @return true if in local client mode, false for regular mode
   */
  boolean isLocalClientMode();

}
