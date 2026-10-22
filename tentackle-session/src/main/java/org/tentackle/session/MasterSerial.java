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
 * Wraps the master serial used by the {@link ModificationTracker}.<br>
 * The tracker of a remote client periodically requests the master serial from the server.
 * Instead of a plain {@code long}, the server returns an object implementing <code>MasterSerial</code>:
 * either a simple record holding the serial, or a {@link MasterSerialEvent}
 * carrying optional, application-specific payload as well.
 */
public interface MasterSerial {

  /**
   * Gets the serial value.
   *
   * @return the serial
   */
  long serial();

}
