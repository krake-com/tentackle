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

/**
 * Registry for remote delegates.<br>
 * All remote delegates must be registered at the registry before their target object can be used
 * as a remote object.
 * <p>
 * There are two types of registrations:
 * <ul>
 *   <li>{@code named}: The remote delegate is registered along with a symbolic name. Those objects can
 *   be located without knowing their unique ID.</li>
 *   <li>{@code unnamed}: The remote delegate is registered without a symbolic name and can be located
 *   only by its unique ID.</li>
 * </ul>
 * As long as a remote delegate is registered, it cannot be garbage-collected.
 * Objects must be unregistered before they can be garbage-collected.
 * <p>
 * The ID of the registry itself is {@link #REGISTRY_ID}.
 */
public interface Registry extends Remote, RemoteLookup {

  /** The ID of the registry. */
  UUID REGISTRY_ID = new UUID(0, 0);


  /**
   * Registers a remote object with a symbolic name.
   *
   * @param <T> the remote type
   * @param remoteObject the remote object to register
   * @param name the symbolic name of the remote delegate
   * @return the remote delegate
   */
  <T extends Remote> RemoteDelegate<T> register(T remoteObject, String name);

  /**
   * Registers a remote object w/o a symbolic name.
   *
   * @param <T> the remote type
   * @param remoteObject the remote object to register
   * @return the remote delegate
   */
  <T extends Remote> RemoteDelegate<T> register(T remoteObject);

  /**
   * Unregisters a remote delegate by its unique ID.
   *
   * @param id the unique ID of the remote delegate to unregister
   * @return the remote delegate if found, null otherwise
   * @param <T> the type of the target object
   */
  <T extends Remote> RemoteDelegate<T> unregister(UUID id);

  /**
   * Unregisters a remote delegate by its symbolic name.
   *
   * @param name the symbolic name of the remote delegate to unregister
   * @return the remote delegate if found, null otherwise
   * @param <T> the type of the target object
   */
  <T extends Remote> RemoteDelegate<T> unregister(String name);

  /**
   * Looks up a remote delegate by its unique ID.
   *
   * @param id the unique ID of the remote delegate to look up
   * @return the remote delegate if found, null otherwise
   * @param <T> the type of the target object
   */
  <T extends Remote> RemoteDelegate<T> lookup(UUID id);

  /**
   * Looks up the unique ID of a remote object.<br>
   * Locates the remote delegate of a remote object and returns its unique ID.
   *
   * @param remoteObject the target object of the remote delegate to look up
   * @return the unique ID of the remote delegate if found, null otherwise
   */
  UUID lookup(Remote remoteObject);

}
