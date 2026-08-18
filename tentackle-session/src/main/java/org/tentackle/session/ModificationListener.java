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


/**
 * Listener for modifications.
 * <p>
 * Modifications are named. The names usually correspond to a
 * database tablename, but may be any application-specific name as well.
 *
 * @author harald
 */
public interface ModificationListener {

  /**
   * Gets the names to listen to.
   *
   * @return the modification names, null if listen for any change
   */
  String[] getNames();

  /**
   * Gets the execution priority.<br>
   * Determines the order of execution. Lower priority comes first.
   *
   * @return the execution priority
   */
  int getPriority();

  /**
   * Gets the optional execution time frame in milliseconds.<br>
   * If given, the listener will be executed at a random time within the time frame.
   * The priority will have no effect in such a case.<br>
   * The same listener is only fired once even if the corresponding event occurs
   * again before the listener gets invoked.
   *
   * @return the execution time window
   */
  long getTimeFrame();

  /**
   * Gets the optional delay to fire the event.<br>
   * Allows collecting events for multiple names within an interval.<br>
   * If both timeFrame and timeDelay are given, the delay is treated as an
   * offset to the frame.
   *
   * @return the delay in milliseconds, 0 if no delay
   */
  long getTimeDelay();

  /**
   * Invoked whenever an instance of the given class has changed.
   *
   * @param ev the modification event
   */
   void dataChanged(ModificationEvent ev);

}
