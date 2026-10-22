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

package org.tentackle.trip.remote;

import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.trip.Remote;
import org.tentackle.trip.TripDeduplicatable;
import org.tentackle.trip.TripReferencable;

import java.util.UUID;

/**
 * Delegate descriptor returned if the return value of a remote call is
 * itself a remote object.
 *
 * @param remoteId the ID of the remote object
 * @param remoteInterfaces the remote interfaces implemented by the remote object
 */
@TripReferencable(TripReferencable.NEVER)
@TripDeduplicatable(TripDeduplicatable.NEVER)
public record DelegateDescriptor(UUID remoteId, Class<?>[] remoteInterfaces) {

  /**
   * Creates a descriptor from the remote object.<br>
   * The remote interfaces are the interfaces extending {@link Remote} implemented by the object's class
   * and its superclasses.
   *
   * @param remoteId the ID of the remote object
   * @param remoteObject the remote object
   */
  public DelegateDescriptor(UUID remoteId, Object remoteObject) {
    this(remoteId, ReflectionHelper.findAllInterfaces(remoteObject.getClass(), Remote.class).toArray(new Class<?>[0]));
  }

}
