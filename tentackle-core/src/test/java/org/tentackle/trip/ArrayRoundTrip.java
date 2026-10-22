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

package org.tentackle.trip;

import org.tentackle.io.MutableByteArrayInputStream;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.testng.Assert.assertEquals;

/**
 * Serializes a predefined array and reads it back.
 */
final class ArrayRoundTrip {

  private static final UUID NIL_UUID = new UUID(0, 0);

  /** written behind the array to verify that the reader consumed exactly the array. */
  private static final int MARKER = 0x7A11E;

  /**
   * Serializes the array, deserializes it again and verifies the result.<br>
   * A marker value is sent behind the array to verify that the reader stays in sync with the writer.
   *
   * @param data the array
   * @return the number of bytes on the wire
   * @param <T> the array type
   */
  static <T> int encodeAndVerify(T data) {
    TripFactory factory = TripFactory.getInstance();
    @SuppressWarnings("unchecked")
    TripType<T> type = (TripType<T>) factory.getType(data.getClass());

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    MutableByteArrayInputStream bis = new MutableByteArrayInputStream();
    Serializer serializer = factory.createSerializer(bos);
    Deserializer deserializer = factory.createDeserializer(bis);
    TripStream outStream = factory.createStream(factory.createDictionary(NIL_UUID, true), serializer, deserializer);
    TripStream inStream = factory.createStream(factory.createDictionary(NIL_UUID, false), serializer, deserializer);

    TripType<Integer> markerType = factory.getType(Integer.class);

    type.write(data, outStream);
    serializer.flush();
    int size = bos.size();
    markerType.write(MARKER, outStream);
    serializer.flush();
    byte[] encoded = bos.toByteArray();

    bis.setBuffer(encoded);
    deserializer.setInputStream(bis);
    @SuppressWarnings("unchecked")
    T decoded = ((PredefinedArray<T>) type).readWithType(inStream, deserializer.deserializeOpCode());
    assertEquals(decoded, data);
    assertEquals(markerType.read(inStream, deserializer.deserializeOpCode()), Integer.valueOf(MARKER),
                 "reader out of sync after " + data.getClass().getSimpleName());

    return size;
  }

  private ArrayRoundTrip() {
  }

}
