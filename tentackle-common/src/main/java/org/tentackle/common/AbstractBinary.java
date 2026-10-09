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
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

/**
 * Base implementation for storing objects in a database.
 * <p>
 * The object is serialized avoiding the transactional limits of blobs, because blobs usually are
 * only valid within a transaction. Instead, it provides a blob-like interface while remaining
 * valid outside transactions.
 * <p>
 * Furthermore, binaries can be frozen, i.e., made immutable.
 * <p>
 * Keep in mind that the wrapped object is serialized when the binary is created by the result set
 * or setObject is invoked. Any further changes to the object are not reflected in the internal byte array.
 * <p>
 * See {@link TBinary} to serialize objects that don't need to implement {@link Serializable}.
 *
 * @param <T> the object type
 */
public abstract sealed class AbstractBinary<T> implements Serializable, Cloneable, Freezable
                permits Binary, TBinary {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Initial default buffer size when reading from the database.
   */
  private static final int INITIAL_SIZE = 1024;

  /**
   * The minimum buffer size.
   */
  private static final int MIN_SIZE = 128;


  /** the byte array of the serialized object. */
  protected byte[] data;

  /** the number of valid bytes in {@link #data}. */
  protected int length;

  /** the lazy hash code. */
  protected Integer hash;

  /** true if the binary cannot be modified anymore. */
  protected boolean frozen;

  /** the cached object. */
  protected transient T object;


  /**
   * Creates a binary from an object.
   *
   * @param object the object to be stored in the database
   *
   * @throws IOException if serialization failed
   */
  public AbstractBinary(T object) throws IOException {
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
  public AbstractBinary(InputStream stream, int bufSize, boolean frozen) throws IOException {
    try (stream) {
      if (bufSize < MIN_SIZE) {
        bufSize = INITIAL_SIZE;
      }
      data = new byte[bufSize];
      int count;
      while ((count = stream.read(data, length, bufSize - length)) >= 0) {
        length += count;
        if (length == bufSize) {
          byte[] oldData = data;
          bufSize <<= 1;    // double the buffer size
          data = new byte[bufSize];
          System.arraycopy(oldData, 0, data, 0, length);
        }
      }
      if (frozen) {
        freeze();
      }
    }
  }

  /**
   * Creates an empty binary.
   */
  public AbstractBinary() {}


  @Override
  public void freeze() {
    frozen = true;
  }

  @Override
  public boolean isFrozen() {
    return frozen;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Cloned binaries are always not frozen.
   */
  @Override
  @SuppressWarnings("unchecked")
  public AbstractBinary<T> clone() {
    AbstractBinary<T> binary;
    try {
      binary = (AbstractBinary<T>) super.clone();
    }
    catch (CloneNotSupportedException ex) {
      throw new TentackleRuntimeException(ex);    // this shouldn't happen, since we are Cloneable
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
    AbstractBinary<?> binary = (AbstractBinary<?>) o;
    if (length != binary.length) return false;
    // only the first 'length' bytes are valid: the backing array may be larger (e.g., read from a stream)
    return length == 0 || Arrays.equals(data, 0, length, binary.data, 0, length);
  }

  @Override
  public int hashCode() {
    if (hash == null) {
      // hash only the valid bytes, so that binaries with equal content but differently sized
      // backing arrays are consistent with equals()
      int h = 1;
      for (int i = 0; i < length; i++) {
        h = 31 * h + data[i];
      }
      hash = h;
    }
    return hash;
  }

  /**
   * Gets the length in bytes of this binary.<br>
   * This is either the number of bytes read from the stream (if created from an {@link InputStream})
   * or the length of the serialized object (if created from an object or {@link #setObject} was invoked).
   *
   * @return the length of the {@code Binary} in bytes
   */
  public int getLength()  {
    return length;
  }

  /**
   * Returns whether this binary is empty.<br>
   * A binary is empty if it holds no object, i.e. its length is 0.
   * Notice that an empty binary is stored as an empty value, not as SQL NULL.
   *
   * @return true if empty
   */
  public boolean isEmpty() {
    return length == 0;
  }

  /**
   * Gets the internal byte array of this binary.<br>
   * Only provided for serialization purposes.
   * Don't mess around with it! Notice that the array is not a copy, i.e., changing it
   * also changes a frozen binary.
   * <p>
   * The array may be longer than {@link #getLength()}, if the binary was read from a stream.
   * Only the first {@code getLength()} bytes are valid.
   *
   * @return the internal byte array, null if the binary is empty
   */
  public byte[] getData() {
    return data;
  }

  /**
   * Gets the stream to store the binary in the database.<br>
   * The stream provides the {@link #getLength()} valid bytes.
   *
   * @return the stream associated with the database
   */
  public InputStream getInputStream() {
    return new ByteArrayInputStream(data == null ? new byte[0] : data, 0, length);
  }

  /**
   * Retrieves the object encapsulated by the binary.<br>
   * The object is deserialized on first invocation and cached afterward.
   *
   * @return the deserialized object (retrieved from the db), or null if NULL-value
   *
   * @throws IOException if reading the object failed
   * @throws ClassNotFoundException if the class of a serialized object cannot be found.
   */
  public abstract T getObject() throws IOException, ClassNotFoundException;

  /**
   * Sets the object.<br>
   * The object is serialized immediately.
   *
   * @param object the object, null to clear the binary
   * @throws IOException if the object could not be serialized
   * @throws TentackleRuntimeException if the binary is frozen
   */
  public abstract void setObject(T object) throws IOException;


  /**
   * Asserts that the binary is not frozen.
   *
   * @throws TentackleRuntimeException if frozen
   */
  protected void assertNotFrozen() {
    if (frozen) {
      throw new TentackleRuntimeException("binary is frozen");
    }
  }

}
