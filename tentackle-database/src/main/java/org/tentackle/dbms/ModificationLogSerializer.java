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
 * <p>
 * If a serializer is registered for a backend via {@link ModificationLogFactory#setSerializer}, the state of an
 * inserted or updated object is stored along with its modlog, so that the receiver does not have to load
 * the object from the database.
 * <p>
 * Notice that the serializer, like the modlogs themselves, is only one of the fundamental building blocks to
 * implement asynchronous coupling. The coupling itself is out of Tentackle's scope, since there are too many
 * different scenarios to cover them with a single generic solution. Reading the modlogs, transferring them,
 * and applying them on the receiving side, which includes invoking {@link #deserialize} and {@link #reset},
 * must be implemented at the application level.
 */
public interface ModificationLogSerializer {

  /**
   * Resets the serializer.
   * <p>
   * If the serializer uses TRIP, it maintains a {@link org.tentackle.trip.Dictionary} that spans
   * several modlogs, i.e., a type definition is only sent along with the first serialized object of that type.
   * The serializing side must reset its dictionary after a certain number of serialized objects or
   * when some time has passed and write a {@link DbModificationType#RESET} modlog before the next serialized
   * object. Since {@link #serialize} gets the session from the object, it writes the RESET modlog itself.
   * <p>
   * The deserializing side, usually a replication processing the modlogs in order, invokes this method
   * when it encounters a {@link DbModificationType#RESET} modlog. Its dictionary is then in sync again with
   * the one used to serialize the following modlogs.
   */
  void reset();

  /**
   * Invoked when a transaction is rolled back in which this serializer registered itself as the
   * transaction property {@code ModificationLogSerializer.class}.
   * <p>
   * A rolled-back transaction discards the modlogs written within it. If those carried
   * dictionary entries, the serializer must not refer to them anymore and has to reset
   * before serializing the next object.
   *
   * @param session the session
   * @see Db#setTransactionProperty(Object, Object)
   */
  void rollback(Db session);

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
