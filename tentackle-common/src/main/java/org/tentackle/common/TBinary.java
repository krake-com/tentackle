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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.io.Serializable;

/**
 * Datatype for storing an object in a database.<br>
 * Same as {@link Binary}, but uses the application-specific {@link ObjectSerializer} implementation.
 * If {@code tentackle-core} is present, as in any multi-tier Tentackle application,
 * {@code org.tentackle.trip.impl.TripObjectSerializer} is used. Otherwise, the {@link DefaultObjectSerializer}
 * applies, which requires the objects to be {@link Serializable}.
 * <p>
 * With TRIP, the objects are not required to implement {@link Serializable}.
 *
 * @param <T> the object type
 * @see Binary
 */
public final class TBinary<T> extends AbstractBinary<T> implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;


  /**
   * Creates a {@code TBinary} out of an InputStream.
   * Used to read a {@code TBinary} from the database.
   * The stream is closed after creating the {@code TBinary}.
   *
   * @param stream the InputStream (associated with the database) to read from, may be null
   * @param bufSize the initial buffer size, values below 128 select the default size of 1024
   * @param frozen true if {@code TBinary} is frozen
   *
   * @return the new {@code TBinary} or null if the stream is null
   * @throws IOException if reading from the input stream failed
   * @param <T> the object type
   */
  public static <T> TBinary<T> create(InputStream stream, int bufSize, boolean frozen) throws IOException {
    if (stream == null) {
      return null;
    }
    return new TBinary<>(stream, bufSize, frozen);
  }


  /**
   * Creates a {@code TBinary} from an object.
   *
   * @param object the object to be stored in the database
   *
   * @throws IOException if serialization failed
   */
  public TBinary(T object) throws IOException {
    setObject(object);
  }

  /**
   * Creates a binary from an {@link InputStream}.<br>
   * Used to read a binary from the database.
   * The stream is closed after creating the binary.
   *
   * @param stream the InputStream (associated with the database) to read from, never null (see {@code create})
   * @param bufSize the initial buffer size, values below 128 select the default size of 1024
   * @param frozen true if binary is frozen
   *
   * @throws IOException if reading from the input stream failed
   */
  public TBinary(InputStream stream, int bufSize, boolean frozen) throws IOException {
    super(stream, bufSize, frozen);
  }

  /**
   * Creates an empty binary.
   */
  public TBinary() {}


  /**
   * {@inheritDoc}
   * <p>
   * Cloned binaries are always not frozen.
   */
  @Override
  public TBinary<T> clone() {
    return (TBinary<T>) super.clone();
  }

  /**
   * Retrieves the object encapsulated by the {@code TBinary}.<br>
   * The object is deserialized on first invocation and cached afterward.
   *
   * @return the deserialized object (retrieved from the db), or null if NULL-value
   *
   * @throws IOException if reading the object failed
   * @throws ClassNotFoundException if the class of a serialized object cannot be found.
   * @throws TentackleRuntimeException if the {@link ObjectSerializer} failed
   */
  @SuppressWarnings("unchecked")
  @Override
  public T getObject() throws IOException, ClassNotFoundException {
    if (object == null && data != null && length > 0) {
      object = (T) ObjectSerializer.getInstance().deserialize(getInputStream());
    }
    return object;
  }

  /**
   * Sets the object.<br>
   * The object is serialized immediately.
   *
   * @param object the object, null to clear the binary
   * @throws IOException if the object could not be serialized
   * @throws TentackleRuntimeException if the binary is frozen
   */
  @Override
  public void setObject(T object) throws IOException {
    assertNotFrozen();
    this.object = object;
    hash = null;
    if (object == null) {
      data = null;
      length = 0;
    }
    else {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ObjectSerializer.getInstance().serialize(object, bos);
      data = bos.toByteArray();
      length = bos.size();
    }
  }

}
