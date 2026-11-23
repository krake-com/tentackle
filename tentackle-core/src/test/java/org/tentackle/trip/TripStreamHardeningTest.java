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

import org.tentackle.common.AbstractBinary;
import org.tentackle.common.TBinary;
import org.tentackle.trip.impl.DefaultOpCode;
import org.tentackle.trip.impl.EchoTransceiver;
import org.tentackle.trip.impl.TestJOSClass;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Ordinals, reference ids and sizes are read from the stream. A corrupted one must end up as a
 * {@link TripRuntimeException} closing the connection, and never hang the reading thread or
 * allocate memory the stream could not possibly fill.
 */
@SuppressWarnings("missing-explicit-ctor")
public class TripStreamHardeningTest {

  /**
   * A dictionary that answers every ordinal without ever waiting, so that a lookup which is
   * <em>not</em> served from the stream's cache can be observed.
   */
  private static class CountingDictionary implements Dictionary {

    private final Dictionary delegate = TripFactory.getInstance().createDictionary(UUID.randomUUID(), true);
    private final List<Integer> requestedOrdinals = new ArrayList<>();

    @Override
    public DictionaryEntry getEntry(int ordinal) {
      requestedOrdinals.add(ordinal);
      return null;    // never waits, unlike the default implementation
    }

    @Override
    public DictionaryEntry getEntry(int ordinal, long maxWaitTime) {
      return null;
    }

    @Override
    public boolean isAuthenticated() {
      return false;
    }

    @Override
    public void setAuthenticated() {

    }

    @Override public UUID getId() { return delegate.getId(); }
    @Override public boolean isServer() { return delegate.isServer(); }
    @Override public boolean isEmpty() { return delegate.isEmpty(); }
    @Override public DictionaryEntry createEntry(Class<?> clazz) { return delegate.createEntry(clazz); }
    @Override public DictionaryEntry createTemporaryEntry(Class<?> clazz) { return delegate.createTemporaryEntry(clazz); }
    @Override public DictionaryEntry createEntry(int ordinal, String className, boolean isProxy,
                                                 DictionaryEntryComponent[] components) {
      return delegate.createEntry(ordinal, className, isProxy, components);
    }
    @Override public DictionaryEntryComponent createComponent(String componentName, TripType<?> type) {
      return delegate.createComponent(componentName, type);
    }
    @Override public void addEntry(DictionaryEntry entry) { delegate.addEntry(entry); }
    @Override public DictionaryEntry getEntry(String className) { return delegate.getEntry(className); }
  }

  private TripStream createStream(Dictionary dictionary) {
    EchoTransceiver transceiver = new EchoTransceiver();
    return TripFactory.getInstance().createStream(dictionary,
                                                  transceiver.getStream().getSerializer(),
                                                  transceiver.getStream().getDeserializer());
  }

  /**
   * The per-stream caches are indexed by the dictionary ordinal, so they must not be sized by it:
   * doubling the array towards a huge ordinal used to overflow to a negative size and spin forever.
   */
  @Test(timeOut = 30000)
  void doesNotSizeTheDictionaryCacheFromTheOrdinal() {
    CountingDictionary dictionary = new CountingDictionary();
    TripStream stream = createStream(dictionary);

    for (int ordinal : new int[] { Integer.MAX_VALUE, Integer.MAX_VALUE - 1, 1 << 30,
                                   Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -(1 << 30) }) {
      stream.getDictionaryEntry(ordinal);
    }
    // all of them went to the dictionary instead of growing the cache
    assertEquals(dictionary.requestedOrdinals.size(), 6);

    // ordinals in range are still cached, i.e. asked only once
    dictionary.requestedOrdinals.clear();
    stream.getDictionaryEntry(7);
    stream.getDictionaryEntry(7);
    assertEquals(dictionary.requestedOrdinals, List.of(7, 7),
                 "a null entry is not cached, but it must not spin either");
  }

  /**
   * The reference array is indexed by a reference id read from the stream and cannot fall back to
   * a lookup elsewhere, so an id beyond any plausible message must be rejected.
   */
  @Test(timeOut = 30000)
  void rejectsReferenceIdsBeyondThePlausibleMaximum() {
    TripStream stream = createStream(TripFactory.getInstance().createDictionary(UUID.randomUUID(), true));
    Object object = new Object();

    TripRuntimeException tx = expectThrows(TripRuntimeException.class,
                                           () -> stream.addReference(new Reference<>(Integer.MAX_VALUE), object, false));
    assertTrue(tx.getMessage().contains("exceeds the maximum"), tx.getMessage());

    // a plausible id is accepted and resolves back to its object
    Reference<Object> reference = new Reference<>(4711);
    stream.addReference(reference, object, false);
    assertSame(stream.getReference(Object.class, 4711).getObject(), object);
  }

  /**
   * Only the 4-byte form of a reference can carry a negative id, and only from a corrupted stream.
   */
  @Test(timeOut = 30000)
  void rejectsNegativeReferenceIdsFromTheStream() {
    EchoTransceiver transceiver = new EchoTransceiver();
    TripStream stream = transceiver.getStream();
    stream.getSerializer().serializeOpCode(DefaultOpCode.REFERENCE);
    stream.getSerializer().serializeInt(-1);
    transceiver.echo();

    TripStream inStream = transceiver.getStream();
    OpCode opCode = inStream.getDeserializer().deserializeOpCode();
    TripRuntimeException tx = expectThrows(TripRuntimeException.class, () -> Reference.read(inStream, opCode));
    assertTrue(tx.getMessage().contains("negative reference id"), tx.getMessage());
  }

  /**
   * The length of a binary is read ahead of its data. It must not be allocated before the data arrives:
   * the stream ends long before 2 GiB, and nothing is held while waiting for the rest.
   */
  @Test(timeOut = 30000)
  @SuppressWarnings("rawtypes")
  void doesNotAllocateTheAnnouncedLengthOfABinary() {
    EchoTransceiver transceiver = new EchoTransceiver();
    TripStream stream = transceiver.getStream();
    stream.getSerializer().serializeOpCode(DefaultOpCode.BINARY);
    stream.getSerializer().serializeByte((byte) 'T');
    stream.getSerializer().serializeInt(Integer.MAX_VALUE);
    stream.getSerializer().serializeBoolean(false);
    stream.getSerializer().serialize(new byte[100], 0, 100);
    transceiver.echo();

    TripStream inStream = transceiver.getStream();
    TripType<AbstractBinary> type = inStream.getType(TBinary.class);
    OpCode opCode = inStream.getDeserializer().deserializeOpCode();
    expectThrows(TripStreamClosedException.class, () -> type.read(inStream, opCode));
  }

  /**
   * Same for an object serialized via Java Object Serialization.
   */
  @Test(timeOut = 30000)
  void doesNotAllocateTheAnnouncedSizeOfAJosObject() {
    EchoTransceiver transceiver = new EchoTransceiver();
    TripStream stream = transceiver.getStream();
    stream.getSerializer().serializeOpCode(DefaultOpCode.JOS);
    stream.getReference(new Object(), false).send(stream);
    stream.getType(Class.class).write(TestJOSClass.class, stream);
    stream.getSerializer().serializeInt(Integer.MAX_VALUE);
    stream.getSerializer().serialize(new byte[100], 0, 100);
    transceiver.echo();

    TripStream inStream = transceiver.getStream();
    TripType<TestJOSClass> type = inStream.getType(TestJOSClass.class);
    OpCode opCode = inStream.getDeserializer().deserializeOpCode();
    TripRuntimeException tx = expectThrows(TripRuntimeException.class, () -> type.read(inStream, opCode));
    assertFalse(tx instanceof TripPolicyViolationException);
  }

  private static final AtomicInteger GADGET_READ = new AtomicInteger();

  /**
   * Stands in for a deserialization gadget: Serializable, but not serialized via JOS by TRIP.
   */
  public static class JosGadget implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      in.defaultReadObject();
      GADGET_READ.incrementAndGet();
    }
  }

  /**
   * Writes a JOS object the way {@code JOSTripType} does, but with an arbitrary announced class.
   *
   * @param announcedClass the class sent via TRIP, null to send none
   * @param object the object sent via Java Object Serialization
   * @return the stream to read from, positioned at the JOS opcode
   */
  private TripStream writeJos(Class<?> announcedClass, Object object) throws IOException {
    EchoTransceiver transceiver = new EchoTransceiver();
    TripStream stream = transceiver.getStream();
    stream.getSerializer().serializeOpCode(DefaultOpCode.JOS);
    stream.getReference(object, false).send(stream);
    stream.getType(Class.class).write(announcedClass, stream);
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (ObjectOutputStream os = new ObjectOutputStream(bos)) {
      os.writeObject(object);
    }
    stream.getSerializer().serializeInt(bos.size());
    stream.getSerializer().serialize(bos.toByteArray(), 0, bos.size());
    transceiver.echo();
    return transceiver.getStream();
  }

  private Object readJos(TripStream inStream) {
    TripType<TestJOSClass> type = inStream.getType(TestJOSClass.class);
    OpCode opCode = inStream.getDeserializer().deserializeOpCode();
    return type.read(inStream, opCode);
  }

  /**
   * The class of a JOS object is announced via TRIP ahead of the Java-serialized form.
   * A class the receiver doesn't serialize via JOS itself is refused before the JOS stream is read.
   */
  @Test(timeOut = 30000)
  void refusesJosObjectsOfClassesNotSerializedViaJos() throws IOException {
    TripStream inStream = writeJos(JosGadget.class, new JosGadget());
    int readBefore = GADGET_READ.get();
    TripPolicyViolationException tx = expectThrows(TripPolicyViolationException.class, () -> readJos(inStream));
    assertTrue(tx.getMessage().contains("not serialized via JOS"), tx.getMessage());
    assertEquals(GADGET_READ.get(), readBefore, "gadget deserialized");
  }

  /**
   * The root object of the Java-serialized form must be of the announced class.
   * Anything else is refused before it is instantiated.
   */
  @Test(timeOut = 30000)
  void refusesJosObjectsNotOfTheAnnouncedClass() throws IOException {
    TripStream inStream = writeJos(TestJOSClass.class, new JosGadget());
    int readBefore = GADGET_READ.get();
    TripPolicyViolationException tx = expectThrows(TripPolicyViolationException.class, () -> readJos(inStream));
    assertTrue(tx.getMessage().contains(JosGadget.class.getName()), tx.getMessage());
    assertEquals(GADGET_READ.get(), readBefore, "gadget deserialized");

    // the announced class itself is still accepted
    TestJOSClass obj = new TestJOSClass();
    obj.name = "accepted";
    assertEquals(readJos(writeJos(TestJOSClass.class, obj)), obj);
  }

  /**
   * A JOS object without a class is refused.
   */
  @Test(timeOut = 30000)
  void refusesJosObjectsWithoutClass() throws IOException {
    TripStream inStream = writeJos(null, new TestJOSClass());
    expectThrows(TripPolicyViolationException.class, () -> readJos(inStream));
  }

  /**
   * Binaries larger than the initial buffer grow while their data arrives, and what follows them is read in sync.
   */
  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void readsLargeBinariesInChunks() throws IOException {
    byte[] data = new byte[300_000];
    new Random(4711).nextBytes(data);
    for (int length : new int[] { 0, 1, 65535, 65536, 65537, 300_000 }) {
      byte[] bytes = Arrays.copyOf(data, length);
      TBinary<?> binary = TBinary.create(new ByteArrayInputStream(bytes), 0, true);
      EchoTransceiver transceiver = new EchoTransceiver();
      TripStream stream = transceiver.getStream();
      List<Object> message = new ArrayList<>(List.of(binary, new TestJOSClass(), "trailer"));
      TripType<List<Object>> type = (TripType) stream.getType(ArrayList.class);
      type.write(message, stream);
      transceiver.echo();
      TripStream inStream = transceiver.getStream();
      List<Object> result = type.read(inStream, inStream.getDeserializer().deserializeOpCode());
      TBinary<?> resultBinary = (TBinary<?>) result.get(0);
      assertEquals(resultBinary.getLength(), length);
      assertEquals(Arrays.copyOf(resultBinary.getData(), length), bytes, "length " + length);
      assertTrue(resultBinary.isFrozen());
      assertEquals(result.get(1), message.get(1));
      assertEquals(result.get(2), "trailer");
    }
  }

}
