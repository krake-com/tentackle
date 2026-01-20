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

import org.tentackle.trip.remote.IdentifiedRemoteCall;
import org.tentackle.trip.remote.IdentifiedRemoteResponse;
import org.tentackle.trip.remote.InitialRemoteCall;
import org.tentackle.trip.remote.InitialRemoteResponse;

import java.util.UUID;

/**
 * A remote delegate.<br>
 * Receives calls from the remote side, invokes the methods on the target object and returns the results.
 *
 * @param <T> the type of the target object
 */
public interface RemoteDelegate<T extends Remote> {

  /**
   * Gets the registry.
   *
   * @return the registry
   */
  Registry getRegistry();

  /**
   * Gets the ID assigned to this delegate by the registry.
   *
   * @return the remote id
   */
  UUID getRemoteId();

  /**
   * Gets the target object.
   *
   * @return the target object
   */
  T getTarget();

  /**
   * Invokes the method on the target object.
   *
   * @param call the remote call
   * @return the result
   */
  InitialRemoteResponse invoke(InitialRemoteCall call);

  /**
   * Invokes the method on the target object.
   *
   * @param call the indexed remote call
   * @return the indexed result
   */
  IdentifiedRemoteResponse invoke(IdentifiedRemoteCall call);

  /**
   * Invoked when this delegate is unregistered from the registry.<br>
   * This will recursively unregister all delegates that depend on this one,
   * i.e., that were registered by this delegate.
   */
  void unregistered();

}
