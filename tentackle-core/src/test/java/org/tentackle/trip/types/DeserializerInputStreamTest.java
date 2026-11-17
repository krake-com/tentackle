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

package org.tentackle.trip.types;

import org.testng.annotations.Test;

import org.tentackle.trip.OpCode;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripPolicyViolationException;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripStreamClosedException;
import org.tentackle.trip.TripType;
import org.tentackle.trip.impl.DefaultOpCode;
import org.tentackle.trip.impl.EchoTransceiver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Values whose length is announced ahead of their bytes must not be allocated before the bytes arrive.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DeserializerInputStreamTest {

  @Test
  void testReadBytes() {
    byte[] data = new byte[100_000];
    new Random(4711).nextBytes(data);
    EchoTransceiver transceiver = new EchoTransceiver();
    transceiver.getStream().getSerializer().serialize(data, 0, data.length);
    transceiver.getStream().getSerializer().serializeInt(4711);
    transceiver.echo();
    TripStream in = transceiver.getStream();
    assertEquals(DeserializerInputStream.readBytes(in.getDeserializer(), 60_000), java.util.Arrays.copyOf(data, 60_000));
    DeserializerInputStream rest = new DeserializerInputStream(in.getDeserializer(), 40_000);
    assertEquals(rest.read(), data[60_000] & 0xff);
    rest.drain();
    assertEquals(rest.read(), -1);
    assertEquals(in.getDeserializer().deserializeInt(), 4711);    // still in sync
  }

  @Test(timeOut = 30000)
  void testForgedBigDecimalLength() {
    EchoTransceiver transceiver = new EchoTransceiver();
    TripStream out = transceiver.getStream();
    out.getSerializer().serializeOpCode(DefaultOpCode.BIGDECIMAL);
    out.getSerializer().serializeChar('B');
    out.getSerializer().serializeBoolean(false);
    out.getSerializer().serializeInt(Integer.MAX_VALUE);
    out.getSerializer().serialize(new byte[100], 0, 100);
    transceiver.echo();
    TripStream in = transceiver.getStream();
    TripType<BigDecimal> type = TripFactory.getInstance().getType(BigDecimal.class);
    OpCode opCode = in.getDeserializer().deserializeOpCode();
    expectThrows(TripStreamClosedException.class, () -> type.read(in, opCode));
  }

  @Test
  void testLargeBigDecimal() {
    BigDecimal value = new BigDecimal(BigInteger.ONE.shiftLeft(100_000).add(BigInteger.TEN), 42);
    EchoTransceiver transceiver = new EchoTransceiver();
    TripType<BigDecimal> type = TripFactory.getInstance().getType(BigDecimal.class);
    type.write(value, transceiver.getStream());
    transceiver.echo();
    TripStream in = transceiver.getStream();
    assertEquals(type.read(in, in.getDeserializer().deserializeOpCode()), value);
  }

  @Test(timeOut = 30000)
  @SuppressWarnings({ "rawtypes", "unchecked" })
  void testForgedEmptyEnumMapSize() {
    EchoTransceiver transceiver = new EchoTransceiver();
    TripStream out = transceiver.getStream();
    TripFactory.getInstance().getType(Integer.class).write(0, out);    // number of entries
    out.getSerializer().serializeBoolean(false);                     // empty map, sent via JOS
    out.getSerializer().serializeInt(Integer.MAX_VALUE);
    out.getSerializer().serialize(new byte[100], 0, 100);
    transceiver.echo();
    TripStream in = transceiver.getStream();
    EnumMapTripType type = new EnumMapTripType(EnumMap.class);
    OpCode opCode = in.receiveOpCode();
    // not a JOS stream at all: refused right away instead of allocating 2 GiB first
    expectThrows(TripRuntimeException.class, () -> type.readHeader(in, opCode));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testEmptyEnumMap() {
    EnumMap<TimeUnit, String> map = new EnumMap<>(TimeUnit.class);
    EchoTransceiver transceiver = new EchoTransceiver();
    TripStream out = transceiver.getStream();
    TripType<EnumMap<TimeUnit, String>> type = out.getType((Class<EnumMap<TimeUnit, String>>) map.getClass());
    type.write(map, out);
    TripFactory.getInstance().getType(String.class).write("trailer", out);
    transceiver.echo();
    TripStream in = transceiver.getStream();
    EnumMap<TimeUnit, String> result = type.read(in, in.getDeserializer().deserializeOpCode());
    assertEquals(result, map);
    result.put(TimeUnit.SECONDS, "s");    // knows its key type
    assertEquals(TripFactory.getInstance().getType(String.class).read(in, in.receiveOpCode()), "trailer");
  }

  /**
   * Sends the header of an empty enum map with the given object serialized via JOS instead of the map.
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  private TripPolicyViolationException readForgedEmptyEnumMap(Object object) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (ObjectOutputStream os = new ObjectOutputStream(bos)) {
      os.writeObject(object);
    }
    EchoTransceiver transceiver = new EchoTransceiver();
    TripStream out = transceiver.getStream();
    TripFactory.getInstance().getType(Integer.class).write(0, out);
    out.getSerializer().serializeBoolean(false);
    out.getSerializer().serializeInt(bos.size());
    out.getSerializer().serialize(bos.toByteArray(), 0, bos.size());
    transceiver.echo();
    TripStream in = transceiver.getStream();
    EnumMapTripType type = new EnumMapTripType(EnumMap.class);
    OpCode opCode = in.receiveOpCode();
    return expectThrows(TripPolicyViolationException.class, () -> type.readHeader(in, opCode));
  }

  @Test
  void testForeignObjectInEmptyEnumMapIsRefused() throws IOException {
    TripPolicyViolationException ex = readForgedEmptyEnumMap(new ArrayList<>(List.of("x")));
    assertTrue(ex.getMessage().contains("deserialization filter"), ex.getMessage());
    // hidden within an enum map: the values are refused as well
    EnumMap<TimeUnit, Object> map = new EnumMap<>(TimeUnit.class);
    map.put(TimeUnit.SECONDS, new ArrayList<>(List.of("x")));
    ex = readForgedEmptyEnumMap(map);
    assertTrue(ex.getMessage().contains("deserialization filter"), ex.getMessage());
  }

  @Test
  void testNonEmptyEnumMapIsRefused() throws IOException {
    EnumMap<TimeUnit, String> map = new EnumMap<>(TimeUnit.class);
    map.put(TimeUnit.SECONDS, "s");
    TripPolicyViolationException ex = readForgedEmptyEnumMap(map);
    assertTrue(ex.getMessage().contains("empty enum map expected"), ex.getMessage());
  }

}
