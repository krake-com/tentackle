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

import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

@SuppressWarnings("missing-explicit-ctor")
public class SerializationTest {

  private final ByteArrayOutputStream bos = new ByteArrayOutputStream();
  private final ByteArrayInputStream bis = new ByteArrayInputStream(new byte[0]);
  private final Serializer serializer = TripFactory.getInstance().createSerializer(bos);
  private final Deserializer deserializer = TripFactory.getInstance().createDeserializer(bis);

  @Test
  void testBytes() {
    byte[] outBytes = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
    serializer.serialize(outBytes, 0, outBytes.length);
    serializer.flush();
    deserializer.setInputStream(new ByteArrayInputStream(bos.toByteArray()));
    bos.reset();
    byte[] inBytes = new byte[outBytes.length];
    deserializer.deserialize(inBytes, 0, inBytes.length);
    assertEquals(inBytes, outBytes);
  }

  @Test
  void testBoolean () {
    tunnel(true, serializer::serializeBoolean, deserializer::deserializeBoolean);
    tunnel(false, serializer::serializeBoolean, deserializer::deserializeBoolean);
  }

  @Test
  void testByte() {
    tunnel((byte) 123, serializer::serializeByte, deserializer::deserializeByte);
    tunnel((byte) 2, serializer::serializeByte, deserializer::deserializeByte);
    tunnel((byte) -1, serializer::serializeByte, deserializer::deserializeByte);
    tunnel(Byte.MAX_VALUE, serializer::serializeByte, deserializer::deserializeByte);
    tunnel(Byte.MIN_VALUE, serializer::serializeByte, deserializer::deserializeByte);
  }

  @Test
  void testByteUnsigned() {
    tunnel(200, serializer::serializeUnsignedAsByte, deserializer::deserializeByteAsUnsigned);
    tunnelFailed(500, serializer::serializeUnsignedAsByte, deserializer::deserializeByteAsUnsigned);
    tunnelFailed(-4, serializer::serializeUnsignedAsByte, deserializer::deserializeByteAsUnsigned);
  }

  @Test
  void testChar() {
    tunnel('a', serializer::serializeChar, deserializer::deserializeChar);
    tunnel('Z', serializer::serializeChar, deserializer::deserializeChar);
    tunnel('ü', serializer::serializeChar, deserializer::deserializeChar);
    tunnel('Ä', serializer::serializeChar, deserializer::deserializeChar);
    tunnel('Ʊ', serializer::serializeChar, deserializer::deserializeChar);
    tunnel('ʩ', serializer::serializeChar, deserializer::deserializeChar);
  }

  @Test
  void testShort() {
    tunnel((short) 123, serializer::serializeShort, deserializer::deserializeShort);
    tunnel((short) -1, serializer::serializeShort, deserializer::deserializeShort);
    tunnel((short) 8000, serializer::serializeShort, deserializer::deserializeShort);
    tunnel((short) -8000, serializer::serializeShort, deserializer::deserializeShort);
    tunnel(Short.MAX_VALUE, serializer::serializeShort, deserializer::deserializeShort);
    tunnel(Short.MIN_VALUE, serializer::serializeShort, deserializer::deserializeShort);
  }

  @Test
  void testShortUnsigned() {
    tunnel(40000, serializer::serializeUnsignedAsShort, deserializer::deserializeShortAsUnsigned);
    tunnelFailed(80000, serializer::serializeUnsignedAsShort, deserializer::deserializeShortAsUnsigned);
    tunnelFailed(-20, serializer::serializeUnsignedAsShort, deserializer::deserializeShortAsUnsigned);
  }

  @Test
  void testInt() {
    tunnel(123, serializer::serializeInt, deserializer::deserializeInt);
    tunnel(-1, serializer::serializeInt, deserializer::deserializeInt);
    tunnel(8000000, serializer::serializeInt, deserializer::deserializeInt);
    tunnel(-8000000, serializer::serializeInt, deserializer::deserializeInt);
    tunnel(Integer.MAX_VALUE, serializer::serializeInt, deserializer::deserializeInt);
    tunnel(Integer.MIN_VALUE, serializer::serializeInt, deserializer::deserializeInt);
  }

  @Test
  void testInt3() {
    tunnel(123, serializer::serializeInt3, deserializer::deserializeInt3);
    tunnel(-1, serializer::serializeInt3, deserializer::deserializeInt3);
    tunnel(8000000, serializer::serializeInt3, deserializer::deserializeInt3);
    tunnel(-8000000, serializer::serializeInt3, deserializer::deserializeInt3);
    tunnel(TripConstants.INT3_MAX, serializer::serializeInt3, deserializer::deserializeInt3);
    tunnel(TripConstants.INT3_MIN, serializer::serializeInt3, deserializer::deserializeInt3);
  }

  @Test
  void testInt3Unsigned() {
    tunnel(123, serializer::serializeInt3, deserializer::deserializeInt3AsUnsigned);
    tunnel(8000000, serializer::serializeInt3, deserializer::deserializeInt3AsUnsigned);
    tunnel(TripConstants.UNSIGNED_INT3_MAX, serializer::serializeInt3, deserializer::deserializeInt3AsUnsigned);
    tunnelFailed(-20, serializer::serializeInt3, deserializer::deserializeInt3AsUnsigned);
  }

  @Test
  void testLong() {
    tunnel(123L, serializer::serializeLong, deserializer::deserializeLong);
    tunnel(-123L, serializer::serializeLong, deserializer::deserializeLong);
    tunnel(800000000L, serializer::serializeLong, deserializer::deserializeLong);
    tunnel(-800000000L, serializer::serializeLong, deserializer::deserializeLong);
    tunnel(Long.MAX_VALUE, serializer::serializeLong, deserializer::deserializeLong);
    tunnel(Long.MIN_VALUE, serializer::serializeLong, deserializer::deserializeLong);
  }

  @Test
  void testLong5() {
    tunnel(123L, serializer::serializeLong5, deserializer::deserializeLong5);
    tunnel(-123L, serializer::serializeLong5, deserializer::deserializeLong5);
    tunnel(800000000L, serializer::serializeLong5, deserializer::deserializeLong5);
    tunnel(-800000000L, serializer::serializeLong5, deserializer::deserializeLong5);
    tunnel(TripConstants.LONG5_MAX, serializer::serializeLong5, deserializer::deserializeLong5);
    tunnel(TripConstants.LONG5_MIN, serializer::serializeLong5, deserializer::deserializeLong5);
  }

  @Test
  void testLong6() {
    tunnel(123L, serializer::serializeLong6, deserializer::deserializeLong6);
    tunnel(-123L, serializer::serializeLong6, deserializer::deserializeLong6);
    tunnel(TripConstants.LONG5_MAX + 1, serializer::serializeLong6, deserializer::deserializeLong6);
    tunnel(TripConstants.LONG5_MIN - 1, serializer::serializeLong6, deserializer::deserializeLong6);
    tunnel(TripConstants.LONG6_MAX, serializer::serializeLong6, deserializer::deserializeLong6);
    tunnel(TripConstants.LONG6_MIN, serializer::serializeLong6, deserializer::deserializeLong6);
  }

  @Test
  void testLong7() {
    tunnel(123L, serializer::serializeLong7, deserializer::deserializeLong7);
    tunnel(-123L, serializer::serializeLong7, deserializer::deserializeLong7);
    tunnel(TripConstants.LONG6_MAX + 1, serializer::serializeLong7, deserializer::deserializeLong7);
    tunnel(TripConstants.LONG6_MIN - 1, serializer::serializeLong7, deserializer::deserializeLong7);
    tunnel(TripConstants.LONG7_MAX, serializer::serializeLong7, deserializer::deserializeLong7);
    tunnel(TripConstants.LONG7_MIN, serializer::serializeLong7, deserializer::deserializeLong7);
  }

  @Test
  void testFloat() {
    tunnel(123.456f, serializer::serializeFloat, deserializer::deserializeFloat);
    tunnel(-123.456f, serializer::serializeFloat, deserializer::deserializeFloat);
    tunnel(0f, serializer::serializeFloat, deserializer::deserializeFloat);
    tunnel(Float.MAX_VALUE, serializer::serializeFloat, deserializer::deserializeFloat);
    tunnel(Float.MIN_VALUE, serializer::serializeFloat, deserializer::deserializeFloat);
    tunnel(Float.NaN, serializer::serializeFloat, deserializer::deserializeFloat);
    tunnel(Float.POSITIVE_INFINITY, serializer::serializeFloat, deserializer::deserializeFloat);
    tunnel(Float.NEGATIVE_INFINITY, serializer::serializeFloat, deserializer::deserializeFloat);
    tunnel(Float.MIN_NORMAL, serializer::serializeFloat, deserializer::deserializeFloat);
  }

  @Test
  void testDouble() {
    tunnel(1234567890.0123456789d, serializer::serializeDouble, deserializer::deserializeDouble);
    tunnel(-1234567890.0123456789d, serializer::serializeDouble, deserializer::deserializeDouble);
    tunnel(0d, serializer::serializeDouble, deserializer::deserializeDouble);
    tunnel(Double.MAX_VALUE, serializer::serializeDouble, deserializer::deserializeDouble);
    tunnel(Double.MIN_VALUE, serializer::serializeDouble, deserializer::deserializeDouble);
    tunnel(Double.NaN, serializer::serializeDouble, deserializer::deserializeDouble);
    tunnel(Double.POSITIVE_INFINITY, serializer::serializeDouble, deserializer::deserializeDouble);
    tunnel(Double.NEGATIVE_INFINITY, serializer::serializeDouble, deserializer::deserializeDouble);
    tunnel(Double.MIN_NORMAL, serializer::serializeDouble, deserializer::deserializeDouble);
  }


  /**
   * A broken connection must be reported as a closed stream, no matter whether the peer
   * shut it down cleanly or it failed with an I/O error. Callers such as the
   * {@code RemoteInvocationHandler} and the session's keep-alive task rely on that
   * distinction to tell a dead connection from a serialization problem.
   */
  @Test
  void reportsBrokenConnectionsAsClosedStream() {
    deserializer.setInputStream(new ByteArrayInputStream(new byte[0]));
    TripStreamClosedException eof = expectThrows(TripStreamClosedException.class, deserializer::deserializeInt);
    assertTrue(eof.getMessage().contains("end of input stream"), eof.getMessage());

    IOException failure = new IOException("Connection reset");
    deserializer.setInputStream(new InputStream() {
      @Override
      public int read() throws IOException {
        throw failure;
      }
      @Override
      public int read(byte[] b, int off, int len) throws IOException {
        throw failure;
      }
    });
    TripStreamClosedException reset = expectThrows(TripStreamClosedException.class, deserializer::deserializeInt);
    assertSame(reset.getCause(), failure);
  }

  /**
   * The write side reports a broken connection the same way.
   */
  @Test
  void reportsBrokenOutputAsClosedStream() {
    IOException failure = new IOException("Broken pipe");
    Serializer failing = TripFactory.getInstance().createSerializer(new OutputStream() {
      @Override
      public void write(int b) throws IOException {
        throw failure;
      }
      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        throw failure;
      }
    });
    failing.serializeInt(4711);
    TripStreamClosedException broken = expectThrows(TripStreamClosedException.class, failing::flush);
    assertSame(broken.getCause(), failure);
  }


  private <T> void tunnel(T value, Consumer<T> serialize, Supplier<T> deserialize) {
    assertEquals(tunnelValue(value, serialize, deserialize), value);
  }

  private <T> void tunnelFailed(T value, Consumer<T> serialize, Supplier<T> deserialize) {
    assertNotEquals(tunnelValue(value, serialize, deserialize), value);
  }

  private <T> T tunnelValue(T value, Consumer<T> serialize, Supplier<T> deserialize) {
    serialize.accept(value);
    serializer.flush();
    deserializer.setInputStream(new ByteArrayInputStream(bos.toByteArray()));
    bos.reset();
    return deserialize.get();
  }

}
