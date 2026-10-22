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

package org.tentackle.common;

import java.io.InputStream;
import java.io.OutputStream;


interface ObjectSerializerHolder {
  ObjectSerializer INSTANCE = ServiceFactory.createService(ObjectSerializer.class, DefaultObjectSerializer.class);
}


/**
 * Generic object serializer.
 * <p>
 * The default implementation {@link DefaultObjectSerializer} uses the Java serialization mechanism,
 * but it can be replaced by a custom implementation which is used
 * by generic serializers such as {@link TBinary}. If tentackle-core is present, the
 * TRIP-based {@code org.tentackle.trip.impl.TripObjectSerializer} replaces the default.
 */
public interface ObjectSerializer {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static ObjectSerializer getInstance() {
    return ObjectSerializerHolder.INSTANCE;
  }


  /**
   * Serializes the given object to an output stream.<br>
   * The stream is closed afterward.
   *
   * @param object the object to serialize
   * @param out the output stream to write to
   * @throws TentackleRuntimeException if serialization failed
   */
  void serialize(Object object, OutputStream out);

  /**
   * Deserializes an object from an input stream.<br>
   * The stream is closed afterward.
   *
   * @param in the input stream to read from
   * @return the deserialized object
   * @throws TentackleRuntimeException if deserialization failed
   */
  Object deserialize(InputStream in);

  /**
   * Creates a deep copy of the given object.
   * <p>
   * Not to be mixed up with {@code org.tentackle.misc.Snapshotable#copy()} which returns
   * a semantically equivalent copy, which is not necessarily a deep copy.
   * For example, PDOs copied via snapshots are all <em>new</em> with an id and serial of 0.
   *
   * @param object the object to copy
   * @return the copy
   * @param <T> the object type
   * @throws TentackleRuntimeException if the object could not be copied
   */
  <T> T copy(T object);

}
