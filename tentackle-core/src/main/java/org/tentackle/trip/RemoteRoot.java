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
 * The root of a remote object tree.<br>
 * The delegates of remote objects are automatically unregistered if their parent delegates are unregistered.
 * The roots of such delegate trees, however, are automatically unregistered only after all connections
 * were closed for the client transport ID, which in turn closes the corresponding registry.<br>
 * If such objects need to be unregistered programmatically, they must implement this interface
 * and invoke {@link #unregisterFromRegistry()} when they are no longer needed.
 */
public interface RemoteRoot {

  /**
   * Sets the registry and the delegate ID this object belongs to.
   *
   * @param registry the registry
   * @param id the delegate ID
   */
  void setRegistryCoordinates(Registry registry, UUID id);

  /**
   * Unregisters this target object's delegate from the registry.
   */
  void unregisterFromRegistry();

}
