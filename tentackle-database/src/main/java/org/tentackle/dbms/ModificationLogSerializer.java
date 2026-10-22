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
 * and applying them on the receiving side, which includes invoking {@link #deserialize}, must be implemented
 * at the application level.
 */
public interface ModificationLogSerializer {

  /**
   * Resets the serializer.
   * <p>
   * If the serializer uses TRIP, it maintains a {@link org.tentackle.trip.Dictionary} that spans
   * several modlogs, i.e., a type definition is only sent along with the first serialized object of that type.
   * The serializing side resets its dictionary after a certain number of serialized objects or
   * when some time has passed and writes a {@link DbModificationType#RESET} modlog before the next serialized
   * object. Since {@link #serialize} gets the session from the object, it writes the RESET modlog itself.
   * <p>
   * Whether the deserializing side must reset when it encounters a {@link DbModificationType#RESET} modlog
   * depends on the implementation. If the modlog-writing transactions overlap, the modlogs serialized before
   * the RESET may be committed after it, so the deserializer must not simply discard its dictionary.
   */
  void reset();

  /**
   * Invoked when a transaction is rolled back, or rolled back to a savepoint, in which this serializer
   * registered itself as the transaction property {@code ModificationLogSerializer.class}.
   * <p>
   * A rollback discards the modlogs written within the transaction or after the savepoint. If those carried
   * dictionary entries, the serializer must not refer to them anymore.
   *
   * @param session the session
   * @see Db#setTransactionProperty(Object, Object)
   */
  void rollback(Db session);

  /**
   * Invoked after a transaction has been committed physically, in which this serializer
   * registered itself as the transaction property {@code ModificationLogSerializer.class}.
   * <p>
   * From now on, the modlogs written within the transaction are visible to the deserializing side.
   * Hence, the dictionary entries they carry may be referred to by other transactions.
   *
   * @param session the session
   * @see Db#setTransactionProperty(Object, Object)
   */
  void commit(Db session);

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
