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

package org.tentackle.trip;

import org.tentackle.misc.Poolable;

/**
 * An outgoing connection from a client to a server.
 */
public interface Connection extends Poolable<Connection> {

  /**
   * Gets the transport this connection belongs to.
   *
   * @return the transport, never null
   */
  Transport getTransport();

  /**
   * Gets the stream associated with this connection.
   *
   * @return the stream to read/write data, never null
   */
  TripStream getStream();

  /**
   * Flushes the output stream.<br>
   * Invoked when a call is serialized completely and should be sent to the server.
   */
  void flush();

}
