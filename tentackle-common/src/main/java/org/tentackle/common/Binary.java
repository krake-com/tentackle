/**
 * Tentackle - http://www.tentackle.org
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




/**
 * Datatype for storing arbitrary (binary) data in a database.
 * <p>
 * Its primary use is to store {@code Serializable}s but it can handle arbitrary {@code byte}-data as well.
 * It uses the XXXBinaryStream-methods to read and write the data avoiding
 * the transactional limits of blobs, because blobs usually are only valid within a transaction.
 * {@code Binary} instead provides a blob-like interface while remaining valid outside transactions.
 *
 * @param <T> the java class handled by this Binary
 */

public class Binary<T> implements Serializable, Cloneable {

  private static final long serialVersionUID = 5853122168291704220L;

  /**
   * initial default buffersize when reading from the database.
   */
  public static int bufSize = 1024;


  private byte[] data;                   // byte array of arbitrary data or serialized object
  private int length;                    // length of data/object

  private transient T object;            // cached object


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
   * Creates a Binary out of an array of arbitrary bytes.

   * @param data the byte array to be stored in the database.
   */
  public Binary(byte[] data) {
    setData(data);
  }


  /**
   * Creates an empty binary
   */
  public Binary() {
  }


  /**
   * Clones the binary
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
    return binary;
  }



  /**
   * Creates a Binary out of an InputStream.
   * Used to read a {@code Binary} from the database.
   * Notice that stream.available() cannot be used according to sun's spec.
   * The stream is closed after creating the Binary.
   *
   * @param stream the InputStream (associated to the database) to read from, may be null
   * @param bufSize the initial buffer size, 0 = default size (bufSize)
   *
   * @return the new Binary or null if stream is null or empty.
   * @throws java.io.IOException if reading from the input stream failed
   */
  public static Binary<?> createBinary(InputStream stream, int bufSize) throws IOException {
    if (stream == null) {
      return null;
    }
    Binary<?> b = new Binary<>();
    if (bufSize <= 0) {
      bufSize = Binary.bufSize;
    }
    b.data = new byte[bufSize];
    int count;
    while ((count = stream.read(b.data, b.length, bufSize - b.length)) >= 0) {
      b.length += count;
      if (b.length == bufSize) {
        byte[] oldData = b.data;
        bufSize <<= 1;    // double the buffer size
        b.data = new byte[bufSize];
        System.arraycopy(oldData, 0, b.data, 0, b.length);
      }
    }
    stream.close();
    return b.length == 0 ? null : b;
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
      // try to treat data as a serialized object
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
   */
  private void setObject(T object) throws IOException {
    this.object = object;
    if (object == null) {
      data   = null;
      length = 0;
    }
    else  {
      ObjectOutputStream os = null;
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      try {
        os = new ObjectOutputStream(bos);
        os.writeObject(object);
        os.flush();
        data   = bos.toByteArray();
        length = bos.size();
      }
      finally {
        if (os != null) {
          os.close();
        }
      }
    }
  }


  /**
   * Retrieves the @{code Binary} data as a byte[]-array.
   *
   * @return the byte[] representation of the @{code Binary}, null if none (or NULL-value in db).
   */
  public byte[] getData() {
    return data;
  }


  /**
   * Sets the binary data.
   *
   * @param data the byte[]-array data.
   */
  public void setData(byte[] data) {
    this.data = data;
    object = null;
    if (data == null) {
      length = 0;
    }
    else  {
      length = data.length;
    }
  }

}
