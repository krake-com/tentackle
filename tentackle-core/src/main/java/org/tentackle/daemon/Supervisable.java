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


package org.tentackle.daemon;

/**
 * A thread being supervised by a {@link DaemonSupervisor}.
 *
 * @author harald
 */
public interface Supervisable extends Killable {

  /**
   * The name of the supervisable.
   *
   * @return the name, usually the thread's name
   */
  String getName();

  /**
   * Returns whether this supervisable is dead and needs to be killed.
   *
   * @return true if dead, false if ok and running
   */
  boolean isDead();

  /**
   * Forces the supervisable to be dead or alive.
   *
   * @param dead true if dead, else force alive
   */
  void setDead(boolean dead);

  /**
   * Returns the epochal time when this supervisable was started.
   *
   * @return the start time, 0 if not started yet
   */
  long startedAt();

  /**
   * Returns the epochal time when this supervisable has terminated.
   *
   * @return the termination time, 0 if not terminated yet
   */
  long terminatedAt();

  /**
   * Gets the termination cause.<br>
   * May be a stracktrace or whatever or null.
   *
   * @return the termination cause
   */
  Object getTerminationCause();

}
