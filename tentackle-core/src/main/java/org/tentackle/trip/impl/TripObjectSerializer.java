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

import org.tentackle.common.ObjectSerializer;
import org.tentackle.common.Service;
import org.tentackle.trip.Deserializer;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.PredefinedArray;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripConstants;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Object serializer using the TRIP serialization format.<br>
 * The serialized data is self-contained, i.e. it includes the dictionary entries.
 * Replaces the default Java serialization of {@link ObjectSerializer}.
 */
@Service(ObjectSerializer.class)
public class TripObjectSerializer implements ObjectSerializer {

  private final TripFactory factory;
  private final UUID nilUuid;
  // the dictionaries can be re-used by the copy()-method to increase performance!
  private final Dictionary serializerDictionary;
  private final Dictionary deserializerDictionary;
  private final Serializer dummySerializer;
  private final Deserializer dummyDeserializer;

  /**
   * Creates the TRIP serializer.
   */
  public TripObjectSerializer() {
    factory = TripFactory.getInstance();
    nilUuid = new UUID(0, 0);
    serializerDictionary = factory.createDictionary(nilUuid, true);
    deserializerDictionary = factory.createDictionary(nilUuid, false);
    dummySerializer = factory.createSerializer(new ByteArrayOutputStream() {
      @Override
      public synchronized void write(int b) {
        noWriteAllowed();
      }

      @Override
      public synchronized void write(byte[] b, int off, int len) {
        noWriteAllowed();
      }

      @Override
      public void writeBytes(byte[] b) {
        noWriteAllowed();
      }

      @Override
      public void write(byte[] b) {
        noWriteAllowed();
      }
    });
    dummyDeserializer = factory.createDeserializer(new ByteArrayInputStream(new byte[0]));
  }

  /**
   * Serialize the given object to an output stream.
   * <p>
   * Prepends {@link TripConstants#MAGIC} to the serialized data.
   *
   * @param object the object to serialize
   * @param out the output stream to write to
   */
  @Override
  public void serialize(Object object, OutputStream out) {
    // we cannot reuse the dictionary since the created data must be self-contained
    Dictionary dictionary = factory.createDictionary(nilUuid, true);
    Serializer serializer = factory.createSerializer(out);
    serializer.serialize(TripConstants.MAGIC);
    TripStream stream = TripFactory.getInstance().createStream(dictionary, serializer, dummyDeserializer);
    if (object == null) {
      serializer.serializeOpCode(DefaultOpCode.NULL);
    }
    else {
      TripType<Object> type = factory.getType(object.getClass());
      type.write(object, stream);
    }
    serializer.flush();
  }

  /**
   * Deserialize an object from an input stream.
   * <p>
   * Verifies that the data begins with {@link TripConstants#MAGIC}.
   *
   * @param in the input stream to read from
   * @return the deserialized object
   */
  @Override
  public Object deserialize(InputStream in) {
    // we cannot reuse the dictionary since the data is self-contained
    Dictionary dictionary = factory.createDictionary(nilUuid, false);
    Deserializer deserializer = factory.createDeserializer(in);
    byte[] magic = new byte[2];
    deserializer.deserialize(magic, 0, 2);
    if (magic[0] != TripConstants.MAGIC[0] || magic[1] != TripConstants.MAGIC[1]) {
      throw new TripRuntimeException("invalid magic: " + HexFormat.ofDelimiter(", ").withPrefix("0x").formatHex(magic));
    }
    TripStream stream = TripFactory.getInstance().createStream(dictionary, dummySerializer, deserializer);
    OpCode opCode = deserializer.deserializeOpCode();
    if (opCode == DefaultOpCode.NULL) {
      return null;
    }
    TripType<?> type = stream.deriveType(opCode);
    if (type.isDictionaryBased()) {
      opCode = deserializer.deserializeOpCode();
    }
    return type.read(stream, opCode);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T copy(T object) {
    if (object != null) {
      // we need new streams due to thread-safety, but we can reuse the dictionaries
      EchoTransceiver transceiver = new EchoTransceiver(serializerDictionary, deserializerDictionary, false, null);
      TripStream stream = transceiver.getStream();
      TripType<T> type = factory.getType((Class<T>) object.getClass());
      type.write(object, stream);
      transceiver.echo();
      stream = transceiver.getStream();   // switched!
      OpCode opCode = stream.getDeserializer().deserializeOpCode();
      if (type instanceof PredefinedArray<?> arrayType) {
        return (T) arrayType.readWithType(stream, opCode);
      }
      return type.read(stream, opCode);
    }
    return null;
  }


  private void noWriteAllowed() {
    // cannot happen, but one never knows (avoids potential memleak)
    throw new TripRuntimeException("illegal write to dummy output stream");
  }

}
