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

import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Restricts what a server accepts from a client that is not authenticated yet.
 * <p>
 * Server-only TLS does not authenticate the client: anyone with a route to the server can talk
 * TRIP to it, even without a login. Until the application marks the client transport as
 * authenticated (see {@link org.tentackle.trip.transport.TransportClientInfo#authenticate()}),
 * a server with a policy installed (see {@link Transport#setPreAuthenticationPolicy(PreAuthenticationPolicy)}):
 * <ul>
 *   <li>resolves only the class names on the allowlist, checked <em>before</em> any class is loaded,</li>
 *   <li>limits the size of a message and the number of elements of arrays, collections and maps,</li>
 *   <li>does not wait for dictionary entries the client never sent,</li>
 *   <li>serves only the registry and the remote objects registered by name,</li>
 *   <li>limits the number of unauthenticated connections and the time until their first message.</li>
 * </ul>
 * Once authenticated, none of these restrictions applies anymore and the policy costs nothing.
 * <p>
 * The connection limits apply to all transports sharing the same policy instance.
 */
public interface PreAuthenticationPolicy {

  /**
   * How violations are handled.
   */
  enum Mode {

    /** Violations are refused: the connection is closed. */
    ENFORCE,

    /** Violations are logged only, to find out what an application really needs before enforcing. */
    AUDIT

  }

  /**
   * The admission of a connection that is not authenticated yet.
   */
  interface Admission {

    /**
     * Notifies that a complete message was received.<br>
     * Ends the deadline for the first message.
     */
    void messageReceived();

    /**
     * Releases the admission.<br>
     * Invoked when the connection is closed or the client is authenticated.
     */
    void release();

  }


  /**
   * Gets the mode.
   *
   * @return the mode
   */
  Mode getMode();

  /**
   * Returns whether violations are refused.
   *
   * @return true if {@link Mode#ENFORCE}, false if only logged
   */
  boolean isEnforcing();

  /**
   * Handles a violation.<br>
   * Throws a {@link TripPolicyViolationException} if enforcing, otherwise logs the violation.
   *
   * @param message the violation
   * @throws TripPolicyViolationException if enforcing
   */
  void violation(String message);

  /**
   * Returns whether an unauthenticated client may send an object of the given class.
   * <p>
   * The name is checked as received, i.e. before it is resolved to a class.
   *
   * @param className the class name as sent over the wire
   * @return true if allowed
   * @see org.tentackle.reflect.ReflectionHelper#getSerializableClassName(Class)
   */
  boolean isClassAllowed(String className);

  /**
   * Returns whether an unauthenticated client may invoke the given remote object.
   *
   * @param registry the registry of the transport
   * @param remoteId the ID of the remote object
   * @return true if allowed
   */
  boolean isDelegateAllowed(Registry registry, UUID remoteId);

  /**
   * Gets the maximum number of bytes of a single message.
   *
   * @return the maximum size, 0 if unlimited
   */
  int getMaxMessageSize();

  /**
   * Gets the maximum number of elements of an array, collection or map.
   *
   * @return the maximum number of elements, 0 if unlimited
   */
  int getMaxElements();

  /**
   * Gets the time to wait for a dictionary entry referenced by an ordinal not received yet.
   *
   * @return the wait time in milliseconds
   */
  long getDictionaryWaitTime();

  /**
   * Admits a new connection.<br>
   * Invoked before anything is read from the connection.
   *
   * @param host the client's host, null if unknown
   * @param closer closes the connection, invoked to evict it or when its first message is overdue
   * @param authenticated tells whether the client got authenticated meanwhile
   * @return the admission, null if the connection is refused
   */
  Admission admit(String host, Runnable closer, BooleanSupplier authenticated);

}
