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
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripConstants;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripFilter;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripStreamClosedException;
import org.tentackle.trip.TripType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

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

  /**
   * Upper bound of the per-stream dictionary entry caches.<br>
   * The caches are indexed by an ordinal read from the stream, so their size must not be driven by
   * it. Way beyond the number of classes any application transfers, so the cache never overflows
   * in practice. Ordinals above are still valid, they are just looked up in the dictionary itself.
   */
  private static final int MAX_CACHED_DICTIONARY_ENTRIES = 1 << 16;

  /**
   * Upper bound for the reference id of a single message.<br>
   * The reference array is indexed by an id read from the stream and, unlike the dictionary
   * caches, it cannot fall back to a lookup elsewhere. The bound is far above the number of
   * objects a message realistically contains, but keeps a corrupted id from allocating
   * unbounded memory.
   */
  private static final int MAX_REFERENCES = 1 << 24;

  private final Dictionary dictionary;
  private final Serializer serializer;
  private final Deserializer deserializer;
  private final HashMap<String, Class<?>> classMap = new HashMap<>();
  private IdentityHashMap<Object, Reference<?>> identityMap = new IdentityHashMap<>();
  private HashMap<Object, Reference<?>> deDupMap = new HashMap<>();
  private Reference<?>[] references;
  private int nextReferenceId;

  // Since dictionary entries and trip types are only created and never modified, we can cache them.
  // Streams are always bound to the same thread (either virtual or regular), so we
  // don't need to synchronize, gaining some speed, especially with a large number of
  // concurrent threads.
  private DictionaryEntry[] dictionaryEntries;
  private DictionaryEntry[] temporaryDictionaryEntries;
  private final HashMap<String, DictionaryEntry> dictionaryEntriesByName;
  private final IdentityHashMap<Class<?>, DictionaryEntry> dictionaryEntriesByClass;
  private final HashMap<Class<?>, TripType<?>> tripTypes;
  private final TripFactory factory;
  private final TripType<DictionaryEntry> dictTripType;

  /**
   * The resolver passed to {@link #tripTypes}.<br>
   * Held in a field because {@code factory::getType} captures the factory, so writing it inline
   * would allocate a new function object on every {@link #getType} call.
   */
  private final Function<Class<?>, TripType<?>> typeResolver;

  private TripFilter filter;


  /**
   * Creates a new stream.
   *
   * @param dictionary the dictionary to use
   * @param serializer the serializer to use
   * @param deserializer the deserializer to use
   */
  public DefaultTripStream(Dictionary dictionary, Serializer serializer, Deserializer deserializer) {
    this.dictionary = Objects.requireNonNull(dictionary, "dictionary missing");
    this.serializer = Objects.requireNonNull(serializer, "serializer missing");
    this.deserializer = Objects.requireNonNull(deserializer, "deserializer missing");
    dictionaryEntriesByName = new HashMap<>();
    dictionaryEntriesByClass = new IdentityHashMap<>();
    tripTypes = new HashMap<>();
    factory = TripFactory.getInstance();
    dictTripType = factory.getType(DictionaryEntry.class);
    typeResolver = factory::getType;
    clear();
  }

  @Override
  public Dictionary getDictionary() {
    return dictionary;
  }

  @Override
  public DictionaryEntry getDictionaryEntry(int ordinal) {
    if (ordinal >= 0) {
      if (ordinal >= MAX_CACHED_DICTIONARY_ENTRIES) {
        // Do not size the cache from an ordinal read off the stream: a corrupted one would
        // allocate gigabytes. Beyond the cache the dictionary is asked every time, which is
        // where an unknown ordinal is rejected anyway.
        return dictionary.getEntry(ordinal);
      }
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
      // -ordinal would overflow for Integer.MIN_VALUE, mapping it to a positive index again
      int index = ordinal == Integer.MIN_VALUE ? MAX_CACHED_DICTIONARY_ENTRIES : -ordinal - 1;
      if (index >= MAX_CACHED_DICTIONARY_ENTRIES) {
        return dictionary.getEntry(ordinal);    // see above
      }
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
    // Keyed by the class first: this runs for every object, enum value and collection written,
    // and again on the read side for every one read as a component. Going through
    // getSerializableClassName() and a string-hashed lookup for each of them is pure overhead,
    // since the class already identifies the entry.
    DictionaryEntry dictionaryEntry = dictionaryEntriesByClass.get(clazz);
    if (dictionaryEntry != null) {
      return dictionaryEntry;
    }
    String className = ReflectionHelper.getSerializableClassName(clazz);
    dictionaryEntry = dictionaryEntriesByName.get(className);
    if (dictionaryEntry == null) {
      dictionaryEntry = dictionary.getEntry(className);
    }
    if (dictionaryEntry != null && !dictionaryEntry.isClientOnly()) {
      // since server entries replace client-only entries, we cannot cache client-only entries
      dictionaryEntriesByName.put(className, dictionaryEntry);
      dictionaryEntriesByClass.put(clazz, dictionaryEntry);
    }
    return dictionaryEntry;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> TripType<T> getType(Class<? extends T> clazz) {
    return (TripType<T>) tripTypes.computeIfAbsent(clazz, typeResolver);
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
    // Deliberately not computeIfAbsent: the mapping function would capture this and allocate on
    // every call, and IdentityHashMap does not override it anyway, so its default already probes
    // twice. This is the single hottest lookup on the write path.
    if (deduplicate) {
      Reference<?> reference = deDupMap.get(object);
      if (reference == null) {
        reference = createAndAddReference(object);
        deDupMap.put(object, reference);
      }
      return (Reference<T>) reference;
    }
    Reference<?> reference = identityMap.get(object);
    if (reference == null) {
      reference = createAndAddReference(object);
      identityMap.put(object, reference);
    }
    return (Reference<T>) reference;
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
  public void setFilter(TripFilter filter) {
    this.filter = filter;
  }

  @Override
  public TripFilter getFilter() {
    return filter;
  }

  @Override
  public void writeComponentOrdinal(int ordinal) {
    if (ordinal >= Short.MIN_VALUE && ordinal <= Short.MAX_VALUE) {
      if (ordinal >= Byte.MIN_VALUE && ordinal <= Byte.MAX_VALUE) {
        serializer.serializeOpCode(DefaultOpCode.COMPONENT_ORDINAL1);
        serializer.serializeByte((byte) ordinal);
      }
      else {
        serializer.serializeOpCode(DefaultOpCode.COMPONENT_ORDINAL2);
        serializer.serializeShort((short) ordinal);
      }
    }
    else {
      if (ordinal >= TripConstants.INT3_MIN && ordinal <= TripConstants.INT3_MAX) {
        serializer.serializeOpCode(DefaultOpCode.COMPONENT_ORDINAL3);
        serializer.serializeInt3(ordinal);
      }
      else {
        serializer.serializeOpCode(DefaultOpCode.COMPONENT_ORDINAL);
        serializer.serializeInt(ordinal);
      }
    }
  }

  @Override
  public int readComponentOrdinal(OpCode opCode) {
    // Narrowed to DefaultOpCode before switching: with the OpCode interface as the selector
    // type, javac compiles the switch to an invokedynamic typeSwitch, whose generated body is
    // a linear chain of comparisons instead of a jump table. An opcode of another
    // implementation is not one of ours and takes the same path as an unknown one.
    if (opCode instanceof DefaultOpCode code) {
      return switch (code) {
        case COMPONENT_ORDINAL -> deserializer.deserializeInt();
        case COMPONENT_ORDINAL3 -> deserializer.deserializeInt3();
        case COMPONENT_ORDINAL2 -> deserializer.deserializeShort();
        case COMPONENT_ORDINAL1 -> deserializer.deserializeByte();
        default -> throw new TripRuntimeException("not a component ordinal opCode: " + opCode);
      };
    }
    throw new TripRuntimeException("not a component ordinal opCode: " + opCode);
  }

  @Override
  public int writeNextComponentOrdinal(int lastOrdinal, TripComponent<?, ?> component) {
    int ordinal = component.getOrdinal();
    int diff = ordinal - lastOrdinal;
    if (diff > 1) {
      if (diff < 17) {
        getSerializer().serializeUnsignedAsByte(DefaultOpCode.COMPONENT_ORDINAL_SKIP1.getCode() + diff - 2);
      }
      else {
        writeComponentOrdinal(ordinal);
      }
    }
    else if (diff < 1 ||
             lastOrdinal < 0 && (component.getType().isDictionaryBased() || !component.getType().isFinal())) {
      // Always write the ordinal if we go back in sequence or for the first dictionary-based component.
      // The first ordinal must be written, because in case we need to send a directory entry
      // for the component, we wouldn't be able to determine whether it's for the parent
      // or the component.
      writeComponentOrdinal(ordinal);
    }
    // write nothing, if advance to the next ordinal
    return ordinal;
  }

  @Override
  public int readNextComponentOrdinal(int lastOrdinal, OpCode opCode) {
    int code = opCode.getCode();
    if (code >= DefaultOpCode.COMPONENT_ORDINAL_SKIP1.getCode() &&
        code <= DefaultOpCode.COMPONENT_ORDINAL_SKIP15.getCode()) {
      return lastOrdinal + code - DefaultOpCode.COMPONENT_ORDINAL_SKIP1.getCode() + 2;
    }
    return readComponentOrdinal(opCode);
  }

  @Override
  public void writeDictionaryOrdinal(int ordinal) {
    if (ordinal >= Short.MIN_VALUE && ordinal <= Short.MAX_VALUE) {
      if (ordinal >= Byte.MIN_VALUE && ordinal <= Byte.MAX_VALUE) {
        serializer.serializeOpCode(DefaultOpCode.DICTIONARY_ORDINAL1);
        serializer.serializeByte((byte) ordinal);
      }
      else {
        serializer.serializeOpCode(DefaultOpCode.DICTIONARY_ORDINAL2);
        serializer.serializeShort((short) ordinal);
      }
    }
    else {
      if (ordinal >= TripConstants.INT3_MIN && ordinal <= TripConstants.INT3_MAX) {
        serializer.serializeOpCode(DefaultOpCode.DICTIONARY_ORDINAL3);
        serializer.serializeInt3(ordinal);
      }
      else {
        serializer.serializeOpCode(DefaultOpCode.DICTIONARY_ORDINAL);
        serializer.serializeInt(ordinal);
      }
    }
  }

  @Override
  public int readDictionaryOrdinal(OpCode opCode) {
    // Narrowed to DefaultOpCode before switching: with the OpCode interface as the selector
    // type, javac compiles the switch to an invokedynamic typeSwitch, whose generated body is
    // a linear chain of comparisons instead of a jump table. An opcode of another
    // implementation is not one of ours and takes the same path as an unknown one.
    if (opCode instanceof DefaultOpCode code) {
      return switch (code) {
        case DICTIONARY_ORDINAL -> deserializer.deserializeInt();
        case DICTIONARY_ORDINAL3 -> deserializer.deserializeInt3();
        case DICTIONARY_ORDINAL2 -> deserializer.deserializeShort();
        case DICTIONARY_ORDINAL1 -> deserializer.deserializeByte();
        default -> throw new TripRuntimeException("not a dictionary ordinal opCode: " + opCode);
      };
    }
    throw new TripRuntimeException("not a dictionary ordinal opCode: " + opCode);
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
          writeDictionaryOrdinal(dictionaryEntry.getOrdinal());
        }
      }
    }
    else {
      writeDictionaryOrdinal(dictionaryEntry.getOrdinal());
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
      int ordinal = readDictionaryOrdinal(opCode);
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
    if (referenceId >= MAX_REFERENCES) {
      // The array is indexed by a reference id read from the stream, so a corrupted one would
      // allocate gigabytes before the first byte of the object arrives.
      throw new TripRuntimeException("reference id " + referenceId + " exceeds the maximum of " + MAX_REFERENCES);
    }
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
    // the bound also stops the doubling from overflowing to a negative size and looping forever
    while (newSize <= ordinal && newSize < MAX_CACHED_DICTIONARY_ENTRIES) {
      newSize <<= 1;
    }
    return newSize;
  }

}
