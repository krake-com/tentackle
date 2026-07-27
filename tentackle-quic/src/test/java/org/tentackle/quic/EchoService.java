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

package org.tentackle.quic;

import org.tentackle.trip.Remote;

/**
 * A minimal remote service used to exercise real remote invocations over QUIC.
 */
public interface EchoService extends Remote {

  /**
   * Gets the name of this service.
   *
   * @return the name
   */
  String getName();

  /**
   * Echoes the given payload back to the caller.<br>
   * The very same instance is returned, so TRIP sends back a reference rather than the object.
   *
   * @param payload the payload
   * @return the payload
   */
  Payload echo(Payload payload);

  /**
   * Returns a payload derived from the given one, with {@code "-copy"} appended to its text.<br>
   * Unlike {@link #echo(Payload)}, the result is neither the same nor an equal object, so TRIP
   * can neither send back a reference nor deduplicate it: the payload really travels back.
   *
   * @param payload the payload
   * @return the derived payload
   */
  Payload duplicate(Payload payload);

  /**
   * Adds two numbers.
   *
   * @param a the first summand
   * @param b the second summand
   * @return the sum
   */
  int add(int a, int b);

  /**
   * Throws an {@link IllegalStateException} with the given message.
   *
   * @param message the message of the exception
   */
  void fail(String message);

}
