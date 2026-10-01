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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;

/**
 * Datatype for storing an object in a database.<br>
 * Uses Java Object Serialization (JOS) under the hood.
 * <p>
 * It uses the {@code XXXBinaryStream}-methods of {@link java.sql.ResultSet} to read and write the data avoiding
 * the transactional limits of blobs, because blobs usually are only valid within a transaction.
 * {@code Binary} instead provides a blob-like interface while remaining valid outside transactions.
 * <p>
 * Furthermore, binaries can be frozen, i.e., made immutable.
 * <p>
 * Keep in mind that the wrapped object is serialized when the Binary is created or setObject is invoked.
 * Any further changes to the object are not reflected in the internal byte array.
 *
 * @param <T> the serializable type
 */
public final class Binary<T extends Serializable> extends AbstractBinary<T> implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;


  /**
   * Creates a {@code Binary} out of an InputStream.
   * Used to read a {@code Binary} from the database.
   * Notice that {@code stream.available()} cannot be used according to the {@link InputStream} contract.
   * The stream is closed after creating the {@code Binary}.
   *
   * @param stream the InputStream (associated with the database) to read from, may be null
   * @param bufSize the initial buffer size, values below 128 select the default size of 1024
   * @param frozen true if {@code Binary} is frozen
   *
   * @return the new {@code Binary} or null if the stream is null or empty.
   * @throws java.io.IOException if reading from the input stream failed
   * @param <T> the serializable type
   */
  public static <T extends Serializable> Binary<T> create(InputStream stream, int bufSize, boolean frozen) throws IOException {
    if (stream == null) {
      return null;
    }
    Binary<T> binary = new Binary<>(stream, bufSize, frozen);
    return binary.length == 0 ? null : binary;
  }


  /**
   * Creates a Binary from a {@code Serializable}.
   *
   * @param object the {@code Serializable} to be stored in the database
   *
   * @throws IOException if serialization failed
   */
  public Binary(T object) throws IOException {
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
  public Binary(InputStream stream, int bufSize, boolean frozen) throws IOException {
    super(stream, bufSize, frozen);
  }

  /**
   * Creates an empty binary.
   */
  public Binary() {}


  /**
   * {@inheritDoc}
   * <p>
   * Cloned binaries are always not frozen.
   */
  @Override
  public Binary<T> clone() {
    return (Binary<T>) super.clone();
  }

  /**
   * Retrieves the object encapsulated by the {@code Binary}.<br>
   * The object is deserialized on first invocation and cached afterward.
   *
   * @return the deserialized object (retrieved from the db), or null if NULL-value
   *
   * @throws java.io.IOException if reading the object failed
   * @throws ClassNotFoundException if the class of a serialized object cannot be found.
   */
  @SuppressWarnings("unchecked")
  @Override
  public T getObject() throws IOException, ClassNotFoundException {
    if (object == null && data != null && length > 0) {
      try (ObjectInputStream is = new ObjectInputStream(getInputStream())) {
        object = (T) is.readObject();
      }
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
      data   = null;
      length = 0;
    }
    else  {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      try (ObjectOutputStream os = new ObjectOutputStream(bos)) {
        os.writeObject(object);
        os.flush();
        data   = bos.toByteArray();
        length = bos.size();
      }
    }
  }

}
