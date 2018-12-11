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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;


/**
 * Datatype for storing arbitrary (binary) data in a database.
 * <p>
 * It uses the XXXBinaryStream-methods to read and write the data avoiding
 * the transactional limits of blobs, because blobs usually are only valid within a transaction.
 * {@code Binary} instead provides a blob-like interface while remaining valid outside transactions.
 * <p>
 * Furthermore, binaries can be frozen, i.e. made immutable.
 * <p>
 * Keep in mind that the wrapped object is serialized when the Binary is created or setObject is invoked.
 * Any subsequent changes to the object are not reflected in the internal byte array.
 *
 * @param <T> the serializable type
 */

public class Binary<T extends Serializable> implements Serializable, Cloneable, Freezable {

  private static final long serialVersionUID = 5853122168291704220L;

  /**
   * initial default buffersize when reading from the database.
   */
  private static final int INITIAL_SIZE = 1024;


  private byte[] data;                  // byte array of arbitrary data or serialized object
  private int length;                   // length of data/object
  private Integer hash;                 // the lazy hash code
  private boolean frozen;               // true if binary cannot be modified anymore
  private transient T object;           // cached object


  /**
   * Creates a Binary from a {@code Serializable}.
   *
   * @param object the {@code Serializable} to be stored in the database
   *
   * @throws IOException if some serialization error.
   */
  public Binary(T object) throws IOException {
    setObject(object);
  }

  /**
   * Creates an empty binary.
   */
  public Binary() {
  }


  @Override
  public void freeze() {
    frozen = true;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Cloned binaries are always not frozen.
   */
  @Override
  @SuppressWarnings("unchecked")
  public Binary<T> clone() {
    Binary<T> binary;
    try {
      binary = (Binary<T>) super.clone();
    }
    catch (CloneNotSupportedException ex) {
      // this shouldn't happen, since we are Cloneable
      throw new InternalError();
    }
    if (binary.data != null) {
      binary.data = new byte[length];
      System.arraycopy(data, 0, binary.data, 0, length);
    }
    binary.frozen = false;
    // hash remains the same
    return binary;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Binary<?> binary = (Binary<?>) o;
    return Arrays.equals(data, binary.data);
  }

  @Override
  public int hashCode() {
    if (hash == null) {
      hash = Arrays.hashCode(data);
    }
    return hash;
  }

  /**
   * Creates a Binary out of an InputStream.
   * Used to read a {@code Binary} from the database.
   * Notice that stream.available() cannot be used according to sun's spec.
   * The stream is closed after creating the Binary.
   *
   * @param stream the InputStream (associated to the database) to read from, may be null
   * @param bufSize the initial buffer size, 0 = default size (bufSize)
   * @param frozen true if binary is frozen
   *
   * @return the new Binary or null if stream is null or empty.
   * @throws java.io.IOException if reading from the input stream failed
   * @param <T> the serializable type
   */
  public static <T extends Serializable> Binary<T> createBinary(InputStream stream, int bufSize, boolean frozen) throws IOException {
    if (stream == null) {
      return null;
    }
    try (InputStream s = stream) {
      Binary<T> b = new Binary<>();
      if (bufSize <= 0) {
        bufSize = Binary.INITIAL_SIZE;
      }
      b.data = new byte[bufSize];
      int count;
      while ((count = s.read(b.data, b.length, bufSize - b.length)) >= 0) {
        b.length += count;
        if (b.length == bufSize) {
          byte[] oldData = b.data;
          bufSize <<= 1;    // double the buffer size
          b.data = new byte[bufSize];
          System.arraycopy(oldData, 0, b.data, 0, b.length);
        }
      }
      if (frozen) {
        b.freeze();
      }
      return b.length == 0 ? null : b;
    }
  }


  /**
   * Gets the length in bytes of this {@code Binary}.
   * The length of a {@code Binary} is either the length of a byte-array (if {@code Binary(byte[])} used)
   * or the length of the serialized object (if {@code Binary(Object)} used)
   *
   * @return the length of the {@code Binary} in bytes
   */
  public int getLength()  {
    return length;
  }

  /**
   * Gets the stream to store the {@code Binary} in the database.
   *
   * @return the stream associated to the database.
   */
  public InputStream getInputStream() {
    return new ByteArrayInputStream(data);
  }



  /**
   * Retrieves the object encapsulated by the {@code Binary}.
   *
   * @return the de-serialized object (retrieved from the db), or null if NULL-value
   *
   * @throws java.io.IOException if reading the object failed
   * @throws ClassNotFoundException if class of a serialized object cannot be found.
   */
  @SuppressWarnings("unchecked")
  public T getObject() throws IOException, ClassNotFoundException {
    if (object == null && data != null) {
      try (ObjectInputStream is = new ObjectInputStream(getInputStream())) {
        object = (T) is.readObject();
      }
    }
    return object;
  }


  /**
   * Sets the serializable.
   *
   * @param object the serializable object.
   * @throws IOException if object could not be serialized
   */
  public void setObject(T object) throws IOException {
    assertNotfrozen();
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


  private void assertNotfrozen() {
    if (frozen) {
      throw new TentackleRuntimeException("binary is frozen");
    }
  }

}
