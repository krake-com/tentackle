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

package org.tentackle.dbms;

import org.tentackle.misc.SerialNumbered;
import org.tentackle.session.SessionDependable;

/**
 * Serializer for the {@link ModificationLog}'s {@code objectData} field.
 */
public interface ModificationLogSerializer {

  /**
   * Resets the serializer.
   * <p>
   * If the serializer uses TRIP, it must reset its internal {@link org.tentackle.trip.Dictionary}
   * after a certain number of serialized objects or when some time has passed
   * and write a {@link DbModificationType#RESET} modlog. This allows the
   * replication to detect that the dictionary was reset and to pick up the dictionary contents
   * from the modlogs before the processing can continue.
   */
  void reset();

  /**
   * Serializes an object.
   *
   * @param object the object to serialize
   * @return the serialized object
   */
  <T extends SessionDependable & SerialNumbered> byte[] serialize(T object);

  /**
   * Deserializes an object.
   *
   * @param data the serialized object
   * @return the deserialized object
   */
  <T extends SessionDependable & SerialNumbered> T deserialize(byte[] data);

}
