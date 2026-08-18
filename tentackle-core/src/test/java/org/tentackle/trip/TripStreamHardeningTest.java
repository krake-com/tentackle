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

import org.tentackle.trip.impl.DefaultOpCode;
import org.tentackle.trip.impl.EchoTransceiver;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
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

}
