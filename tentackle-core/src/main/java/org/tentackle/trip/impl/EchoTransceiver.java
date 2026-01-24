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

package org.tentackle.trip.impl;

import org.tentackle.common.Cryptor;
import org.tentackle.io.CompressedInputStream;
import org.tentackle.io.CompressedOutputStream;
import org.tentackle.io.MutableByteArrayInputStream;
import org.tentackle.trip.Deserializer;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripStream;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Echoing transmitter/receiver for low-level serializing/deserializing w/o a transport layer.
 */
public class EchoTransceiver {

  private static final UUID NIL_UUID = new UUID(0, 0);

  private final ByteArrayOutputStream bos;
  private final MutableByteArrayInputStream bis;
  private final Serializer serializer;
  private final TripStream outStream;
  private final TripStream inStream;
  private final Cryptor cryptor;

  private TripStream stream;
  private int size;


  /**
   * Creates a new EchoTransceiver.<br>
   * The transceiver is initially in transmit-mode.
   *
   * @param serializerDictionary the dictionary to use for serialization
   * @param deserializerDictionary the dictionary to use for deserialization
   * @param compressed true if compression should be used (for testing purposes only), false otherwise
   * @param cryptor optional cryptor (for testing purposes only), null if none
   */
  public EchoTransceiver(Dictionary serializerDictionary, Dictionary deserializerDictionary,
                         boolean compressed, Cryptor cryptor) {
    bos = new ByteArrayOutputStream();
    bis = new MutableByteArrayInputStream();
    serializer = TripFactory.getInstance().createSerializer(compressed ? createCompressedOutputStream(bos) : bos);
    Deserializer deserializer = TripFactory.getInstance().createDeserializer(compressed ? new CompressedInputStream(bis) : bis);
    outStream = TripFactory.getInstance().createStream(serializerDictionary, serializer, deserializer);
    inStream = TripFactory.getInstance().createStream(deserializerDictionary, serializer, deserializer);
    this.cryptor = cryptor;
    reset();
  }

  /**
   * Creates a new EchoTransceiver.<br>
   * The transceiver is initially in transmit-mode.
   *
   * @param compressed true if compression should be used, false otherwise
   * @param cryptor optional cryptor (for testing purposes only), null if none
   */
  public EchoTransceiver(boolean compressed, Cryptor cryptor) {
    // sender = server avoids temporary dictionary entries
    this(TripFactory.getInstance().createDictionary(NIL_UUID, true),
         TripFactory.getInstance().createDictionary(NIL_UUID, false),
         compressed, cryptor);
  }

  /**
   * Creates a new EchoTransceiver.<br>
   * The transceiver is initially in transmit-mode.
   */
  public EchoTransceiver() {
    this(false, null);
  }


  /**
   * Gets the TRIP stream.<br>
   * Returns the output stream in transmit-mode, the input stream in receive-mode.
   *
   * @return the stream
   */
  public TripStream getStream() {
    return stream;
  }

  /**
   * Echoes the transmission and switches from transmit- to receive-mode.
   */
  public void echo() {
    serializer.flush();
    byte[] data = bos.toByteArray();
    if (cryptor != null) {
      data = cryptor.encrypt(data);
      size = data.length;
      data = cryptor.decrypt(data);
    }
    else {
      size = data.length;
    }
    bis.setBuffer(data);
    stream = inStream;
  }

  /**
   * Gets the number of bytes serialized.<br>
   * The method must be called after {@link #echo()}.
   *
   * @return the number of bytes serialized
   */
  public int getSize() {
    return size;
  }

  /**
   * Resets to transmit-mode and clears the output buffer.<br>
   * The object references are <em>not</em> cleared in the streams.
   */
  public void reset() {
    stream = outStream;
    bos.reset();
  }

  /**
   * Clears the object references in the streams.
   */
  public void clear() {
    inStream.clear();
    outStream.clear();
  }

  /**
   * Clears the object references in the streams, keeping the immutable objects (deduplicatables).
   */
  public void clearModifiable() {
    inStream.clearModifiable();
    outStream.clearModifiable();
  }


  /**
   * Creates the compressed output stream.
   *
   * @param bos the output stream to wrap
   * @return the compressed output stream
   */
  protected CompressedOutputStream createCompressedOutputStream(OutputStream bos) {
    return new CompressedOutputStream(bos);
  }

}
