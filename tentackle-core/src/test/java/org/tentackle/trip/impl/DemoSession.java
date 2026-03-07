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

package org.tentackle.trip.impl;

import org.tentackle.trip.Remote;

/**
 * Fake session.
 */
public interface DemoSession extends Remote {

  /**
   * Gets the logged-in user.
   *
   * @return the username
   */
  String getUsername();

  /**
   * Echoes the object.<br>
   * Emulates persist() and alike.
   * To test reference optimization (for example,
   * Strings are not sent back, but only their reference).
   *
   * @param object the object to echo
   * @return the echoed object (same instance!)
   * @param <T> the type of the object to echo
   */
  <T> T echo(T object);

}
