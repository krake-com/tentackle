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

import org.tentackle.common.ExceptionHelper;
import org.tentackle.log.Logger;
import org.tentackle.misc.Holder;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.trip.Deserializer;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.DictionaryEntry;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.PredefinedArrayType;
import org.tentackle.trip.Reference;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripConstants;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripStreamClosedException;
import org.tentackle.trip.TripType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Default implementation of a {@link TripStream}.
 * <p>
 * As it doesn't make sense that multiple threads read from or write to the same stream concurrently,
 * streams are not thread-safe and attaching streams to threads must be synchronized externally.
 */
public class DefaultTripStream implements TripStream {

  private static final Logger LOGGER = Logger.get(DefaultTripStream.class);

  private static final int INITIAL_REF_ARRAY_SIZE = 256;
  private static final int INITIAL_DICTIONARY_ARRAY_SIZE = 64;

  private final Dictionary dictionary;
  private final Serializer serializer;
  private final Deserializer deserializer;
  private final Map<String, Class<?>> classMap = new HashMap<>();
  private Map<Object, Reference<?>> identityMap = new IdentityHashMap<>();
  private Map<Object, Reference<?>> deDupMap = new HashMap<>();
  private Reference<?>[] references;
  private int nextReferenceId;

  // Since dictionary entries and trip types are only created and never modified, we can cache them.
  // Streams are always bound to the same thread (either virtual or regular), so we
  // don't need to synchronize, gaining some speed, especially with a large number of
  // concurrent threads.
  private DictionaryEntry[] dictionaryEntries;
  private DictionaryEntry[] temporaryDictionaryEntries;
  private final Map<String, DictionaryEntry> dictionaryEntriesByName;
  private final Map<Class<?>, TripType<?>> tripTypes;
  private final TripFactory factory;
  private final TripType<DictionaryEntry> dictTripType;


  /**
   * Creates a new stream.
   *
   * @param dictionary the dictionary to use
   * @param serializer the serializer to use
   * @param deserializer the deserializer to use
   */
  public DefaultTripStream(Dictionary dictionary, Serializer serializer, Deserializer deserializer) {
    this.dictionary = dictionary;
    this.serializer = serializer;
    this.deserializer = deserializer;
    dictionaryEntriesByName = new HashMap<>();
    tripTypes = new HashMap<>();
    factory = TripFactory.getInstance();
    dictTripType = factory.getType(DictionaryEntry.class);
    clear();
  }

  @Override
  public Dictionary getDictionary() {
    return dictionary;
  }

  @Override
  public DictionaryEntry getDictionaryEntry(int ordinal) {
    if (ordinal >= 0) {
      if (dictionaryEntries == null) {
        dictionaryEntries = new DictionaryEntry[alignDictionarySize(INITIAL_DICTIONARY_ARRAY_SIZE, ordinal)];
      }
      else {
        if (ordinal >= dictionaryEntries.length) {
          dictionaryEntries = Arrays.copyOf(dictionaryEntries,
                                            alignDictionarySize(dictionaryEntries.length, ordinal));
        }
      }
      DictionaryEntry entry = dictionaryEntries[ordinal];
      if (entry == null) {
        entry = dictionary.getEntry(ordinal);
        dictionaryEntries[ordinal] = entry;
      }
      return entry;
    }
    else {
      int index = -ordinal - 1;
      if (temporaryDictionaryEntries == null) {
        temporaryDictionaryEntries = new DictionaryEntry[alignDictionarySize(INITIAL_DICTIONARY_ARRAY_SIZE, index)];
      }
      else if (index >= temporaryDictionaryEntries.length) {
        temporaryDictionaryEntries = Arrays.copyOf(temporaryDictionaryEntries,
                                                   alignDictionarySize(temporaryDictionaryEntries.length, index));
      }
      DictionaryEntry entry = temporaryDictionaryEntries[index];
      if (entry == null) {
        entry = dictionary.getEntry(ordinal);
        temporaryDictionaryEntries[index] = entry;
      }
      // when server entries replace client-only entries, client-entries become obsolete
      // and their index will never be used again. However, for performance reasons, we
      // don't scan for outdated entries and simply keep them in the array.
      return entry;
    }
  }

  @Override
  public DictionaryEntry getDictionaryEntry(Class<?> clazz) {
    String className = ReflectionHelper.getSerializableClassName(clazz);
    DictionaryEntry dictionaryEntry = dictionaryEntriesByName.get(className);
    if (dictionaryEntry == null) {
      dictionaryEntry = dictionary.getEntry(className);
      if (dictionaryEntry != null && !dictionaryEntry.isClientOnly()) {
        // since server entries replace client-only entries, we cannot cache client-only entries
        dictionaryEntriesByName.put(className, dictionaryEntry);
      }
    }
    return dictionaryEntry;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> TripType<T> getType(Class<? extends T> clazz) {
    return (TripType<T>) tripTypes.computeIfAbsent(clazz, factory::getType);
  }

  @Override
  public Serializer getSerializer() {
    return serializer;
  }

  @Override
  public Deserializer getDeserializer() {
    return deserializer;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> Reference<T> getReference(T object, boolean deduplicate) {
    return deduplicate ?
      (Reference<T>) deDupMap.computeIfAbsent(object, this::createAndAddReference) :
      (Reference<T>) identityMap.computeIfAbsent(object, this::createAndAddReference);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> Reference<T> getReference(Class<T> clazz, int referenceId) {
    if (referenceId < 0 || referenceId >= references.length) {
      throw new TripRuntimeException("reference id " + referenceId + " out of range for " + clazz);
    }
    Reference<T> reference = (Reference<T>) references[referenceId];
    if (reference == null) {
      throw new TripRuntimeException("no such reference id " + referenceId + " for " + clazz);
    }
    return reference;
  }

  @Override
  public <T> void addReference(Reference<T> reference, T object, boolean deduplicate) {
    reference.setObject(object);
    if (addReference(reference)) {
      if (deduplicate) {
        deDupMap.put(object, reference);
      }
      else {
        identityMap.put(object, reference);
      }
    }
  }

  @Override
  public Class<?> getClass(String className) {
    return classMap.computeIfAbsent(className, fqcn -> {
      try {
        return ReflectionHelper.loadClass(fqcn);
      }
      catch (ClassNotFoundException e) {
        throw new TripRuntimeException("cannot load class " + fqcn, e);
      }
    });
  }

  @Override
  public void clear() {
    // creating new maps is faster than clear() for large maps
    identityMap = new IdentityHashMap<>();
    deDupMap = new HashMap<>();
    initializeReferences();
  }

  @Override
  public void clearModifiable() {
    // remove all references not in deDupMap
    identityMap.clear();
    Arrays.fill(references, null);
    deDupMap.values().forEach(reference -> {
      references[reference.getReferenceId()] = reference;
      reference.markReceived();
    });
  }

  @Override
  public void writeOrdinal(int ordinal) {
    if (ordinal >= Short.MIN_VALUE && ordinal <= Short.MAX_VALUE) {
      if (ordinal >= Byte.MIN_VALUE && ordinal <= Byte.MAX_VALUE) {
        serializer.serializeOpCode(DefaultOpCode.ORDINAL1);
        serializer.serializeByte((byte) ordinal);
      }
      else {
        serializer.serializeOpCode(DefaultOpCode.ORDINAL2);
        serializer.serializeShort((short) ordinal);
      }
    }
    else {
      if (ordinal >= TripConstants.INT3_MIN && ordinal <= TripConstants.INT3_MAX) {
        serializer.serializeOpCode(DefaultOpCode.ORDINAL3);
        serializer.serializeInt3(ordinal);
      }
      else {
        serializer.serializeOpCode(DefaultOpCode.ORDINAL);
        serializer.serializeInt(ordinal);
      }
    }
  }

  @Override
  public int readOrdinal(OpCode opCode) {
    return switch (opCode) {
      case DefaultOpCode.ORDINAL -> deserializer.deserializeInt();
      case DefaultOpCode.ORDINAL3 -> deserializer.deserializeInt3();
      case DefaultOpCode.ORDINAL2 -> deserializer.deserializeShort();
      case DefaultOpCode.ORDINAL1 -> deserializer.deserializeByte();
      default -> throw new TripRuntimeException("not an ordinal opCode: " + opCode);
    };
  }

  @Override
  public void writeNextOrdinal(int lastOrdinal, int ordinal) {
    int diff = ordinal - lastOrdinal;
    if (diff > 1) {
      if (diff < 17) {
        getSerializer().serializeUnsignedAsByte(DefaultOpCode.ORDINAL_SKIP1.toExternal() + diff - 2);
      }
      else {
        writeOrdinal(ordinal);
      }
    }
    else if (diff < 1) {
      writeOrdinal(ordinal);
    }
    // write nothing, if advance to the next ordinal
  }

  @Override
  public int readNextOrdinal(int lastOrdinal, OpCode opCode) {
    int code = opCode.getCode();
    if (code >= DefaultOpCode.ORDINAL_SKIP1.getCode() && code <= DefaultOpCode.ORDINAL_SKIP15.getCode()) {
      return lastOrdinal + code - DefaultOpCode.ORDINAL_SKIP1.getCode() + 2;
    }
    return readOrdinal(opCode);
  }

  @Override
  public void sendDictionary(Class<?> clazz) {
    DictionaryEntry dictionaryEntry = getDictionaryEntry(clazz);
    if (dictionaryEntry == null || dictionary.isServer() && dictionaryEntry.isClientOnly()) {
      synchronized (dictionary) {   // the dictionary is shared!
        dictionaryEntry = getDictionaryEntry(clazz);
        if (dictionaryEntry == null || dictionary.isServer() && dictionaryEntry.isClientOnly()) {
          // never talked about this class before, or client-only -> send the class definition
          dictionaryEntry = dictionary.isServer() ?
                              dictionary.createEntry(clazz) :
                              dictionary.createTemporaryEntry(clazz);
          dictTripType.write(dictionaryEntry, this);
          dictionary.addEntry(dictionaryEntry);
        }
        else {
          writeOrdinal(dictionaryEntry.getOrdinal());
        }
      }
    }
    else {
      writeOrdinal(dictionaryEntry.getOrdinal());
    }
  }

  @Override
  public DictionaryEntry receiveDictionary(OpCode opCode) {
    DictionaryEntry entry;
    if (opCode == DefaultOpCode.DICTIONARY) {
      entry = dictTripType.read(this, opCode);
      getDictionary().addEntry(entry);
    }
    else {
      // must be the ordinal
      int ordinal = readOrdinal(opCode);
      entry = getDictionaryEntry(ordinal);
    }
    return entry;
  }

  @Override
  public DictionaryEntry receiveDictionary() {
    return receiveDictionary(receiveOpCode());
  }

  @Override
  public TripType<?> deriveType(OpCode opCode) {
    TripType<?> type;
    if (opCode.isFixedType()) {
      type = opCode.getFixedType();
    }
    else {
      Deserializer deserializer = getDeserializer();
      if (opCode.isPredefinedArray()) {
        char arrayCode = deserializer.deserializeChar();
        PredefinedArrayType arrayType = PredefinedArrayType.valueOf(arrayCode);
        if (arrayType == null) {
          throw new TripRuntimeException("'" + arrayCode + "' is not a predefined array subtype");
        }
        type = opCode.getPredefinedArrayType(arrayType);
      }
      else {
        if (!opCode.isDictionaryBased()) {
          // usually a trip type implementation error
          throw new TripRuntimeException("OpCode " + opCode + " is not dictionary-based");
        }
        // skip code OBJECT, RECORD, etc...
        type = receiveDictionary().getType();
      }
    }
    return type;
  }

  @Override
  public TripType<?> deriveType() {
    return deriveType(receiveOpCode());
  }

  @Override
  public OpCode receiveOpCode() {
    OpCode opCode = deserializer.deserializeOpCode();
    if (opCode.isError()) {
      // read the stacktrace string (see CallHandler)
      String stackTrace;
      try {
        stackTrace = TripFactory.getInstance().getType(String.class)
                                .read(this, deserializer.deserializeOpCode());
      }
      catch (Throwable t) {
        // connection closed before we could read the stacktrace?
        // no linger-option set? (implementation-dependent)
        stackTrace = ExceptionHelper.concatenateMessages(t);  // take our stack trace instead
        // this will close the stream
        TripStreamClosedException cx = new TripStreamClosedException(opCode + ": " + stackTrace, t);
        LOGGER.severe("remote stacktrace incomplete", cx);
        throw cx;
      }
      throw new TripError(opCode, stackTrace);
    }
    return opCode;
  }

  @Override
  public void sendError(OpCode errorOpCode, Throwable t) {
    if (errorOpCode.isError()) {
      serializer.serializeOpCode(errorOpCode);
      TripFactory.getInstance().getType(String.class).write(ExceptionHelper.concatenateMessages(t), this);
      serializer.flush();
    }
    else {
      // what??
      LOGGER.severe("not an error opCode: {0}", errorOpCode);
    }
  }

  /**
   * Gets the number of object references per class.<br>
   * Provided for debugging purposes only!
   *
   * @return the map of class to reference count
   */
  public Map<Class<?>, Holder<Integer>> getClassCount() {
    HashMap<Class<?>, Holder<Integer>> map = new HashMap<>();
    for (int i = 0; i < nextReferenceId; i++) {
      Reference<?> reference = references[i];
      if (reference != null) {
        Holder<Integer> integerHolder = map.computeIfAbsent(reference.getObject().getClass(), _ -> new Holder<>(0));
        integerHolder.accept(integerHolder.get() + 1);
      }
    }
    return map;
  }


  private <T> Reference<T> createAndAddReference(T object) {
    if (nextReferenceId == Integer.MAX_VALUE) {
      throw new TripRuntimeException("too many references");
    }
    Reference<T> reference = new Reference<>(nextReferenceId++);
    reference.setObject(object);
    addReference(reference);
    return reference;
  }

  private boolean addReference(Reference<?> reference) {
    int referenceId = reference.getReferenceId();
    if (referenceId >= references.length) {
      references = Arrays.copyOf(references, Math.max(references.length << 1, referenceId + INITIAL_REF_ARRAY_SIZE / 2));
    }
    Reference<?> existing = references[referenceId];
    if (existing != null) {
      if (existing.getObject() != reference.getObject()) {
        throw new TripRuntimeException("duplicate reference id: " + existing);
      }
      if (existing.getReferenceId() != referenceId) {
        throw new TripRuntimeException("reference map became corrupted");
      }
      return false;
    }
    references[referenceId] = reference;
    if (referenceId >= nextReferenceId) {
      nextReferenceId = referenceId + 1;
    }
    return true;
  }

  private void initializeReferences() {
    references = new Reference<?>[INITIAL_REF_ARRAY_SIZE];
    nextReferenceId = 0;
  }

  private int alignDictionarySize(int currentSize, int ordinal) {
    int newSize = currentSize;
    while (newSize <= ordinal) {
      newSize <<= 1;
    }
    return newSize;
  }

}
