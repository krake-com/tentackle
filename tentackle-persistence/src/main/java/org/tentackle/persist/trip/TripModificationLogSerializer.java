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

package org.tentackle.persist.trip;

import org.tentackle.dbms.Db;
import org.tentackle.dbms.DbModificationType;
import org.tentackle.dbms.ModificationLog;
import org.tentackle.dbms.ModificationLogFactory;
import org.tentackle.dbms.ModificationLogSerializer;
import org.tentackle.log.Logger;
import org.tentackle.misc.SerialNumbered;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.SessionDependable;
import org.tentackle.trip.Deserializer;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.DictionaryEntry;
import org.tentackle.trip.DictionaryEntryComponent;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.UUID;

/**
 * A {@link ModificationLogSerializer} based on TRIP.
 * <p>
 * Serializes the PDOs without their related objects, except the embedded ones (see {@link PdoComponentTripFilter}).
 * The receiver gets the PDO with the relations not loaded, so they are loaded lazily on first access.
 * <p>
 * To keep the serialized data compact, the TRIP {@link Dictionary} spans several modlogs: a type definition is only
 * written along with the first object of that type. The dictionary is reset after a configurable number of
 * serialized objects or when a configurable time span has elapsed, whichever comes first.
 * A reset writes a {@link DbModificationType#RESET} modlog <em>before</em> the modlog of the next serialized object.
 * The very first object serialized by a new instance is preceded by a RESET modlog as well, since the receiver
 * may still hold the dictionary of a previous run.
 * <p>
 * The serializer is registered for a physical backend via
 * {@link ModificationLogFactory#setSerializer(int, ModificationLogSerializer)}.
 * The receiving side, usually a replication, uses a separate instance of this class and must:
 * <ul>
 *   <li>process the modlogs in the order of their ids</li>
 *   <li>invoke {@link #reset()} when it encounters a {@link DbModificationType#RESET} modlog</li>
 *   <li>set the session of the deserialized object</li>
 * </ul>
 * <p>
 * If a transaction is rolled back after it wrote a modlog carrying new dictionary entries, those entries are lost
 * for the receiver. Therefore, the serializer resets before serializing the next object (see {@link #rollback(Db)}).
 * <p>
 * <strong>Important:</strong> since the dictionary is shared among all sessions of a backend, the modlogs of
 * concurrent transactions must not overlap. Otherwise, a modlog referring to a dictionary entry may be
 * committed before the modlog defining it. This is guaranteed if the modlog-writing transactions are serialized,
 * for example, by a transaction-based {@link org.tentackle.dbms.IdSource}. If the receiver encounters an unknown
 * dictionary ordinal, it waits for the entry until the dictionary's timeout elapses.
 */
public class TripModificationLogSerializer implements ModificationLogSerializer {

  /** the default maximum number of serialized objects until the dictionary is reset. */
  public static final int DEFAULT_MAX_OBJECTS = 10_000;

  /** the default maximum time span until the dictionary is reset. */
  public static final Duration DEFAULT_MAX_AGE = Duration.ofHours(1);


  private static final Logger LOGGER = Logger.get(TripModificationLogSerializer.class);

  private static final UUID NIL_UUID = new UUID(0, 0);


  private final TripFactory factory;
  private final int maxObjects;
  private final Duration maxAge;
  private final long maxAgeMillis;
  private final Serializer dummySerializer;
  private final Deserializer dummyDeserializer;

  private CountingDictionary writerDictionary;    // the dictionary used to serialize
  private Dictionary readerDictionary;            // the dictionary used to deserialize
  private int count;                              // number of serialized objects since last reset
  private long resetTime;                         // time of the last reset in epochal milliseconds
  private boolean stale;                          // true if RESET modlog must be written before next serialization


  /**
   * Creates a serializer.
   *
   * @param maxObjects the maximum number of serialized objects until the dictionary is reset, &le; 0 if unlimited
   * @param maxAge the maximum time span until the dictionary is reset, null or not positive if unlimited
   */
  public TripModificationLogSerializer(int maxObjects, Duration maxAge) {
    this.maxObjects = maxObjects;
    this.maxAge = maxAge;
    maxAgeMillis = maxAge == null || maxAge.isNegative() || maxAge.isZero() ? 0 : maxAge.toMillis();
    factory = TripFactory.getInstance();
    dummySerializer = factory.createSerializer(OutputStream.nullOutputStream());
    dummyDeserializer = factory.createDeserializer(new ByteArrayInputStream(new byte[0]));
    reset();
    stale = true;   // the receiver may hold the dictionary of a previous run
  }

  /**
   * Creates a serializer with the default limits.
   *
   * @see #DEFAULT_MAX_OBJECTS
   * @see #DEFAULT_MAX_AGE
   */
  public TripModificationLogSerializer() {
    this(DEFAULT_MAX_OBJECTS, DEFAULT_MAX_AGE);
  }


  /**
   * Gets the maximum number of serialized objects until the dictionary is reset.
   *
   * @return the maximum number of objects, &le; 0 if unlimited
   */
  public int getMaxObjects() {
    return maxObjects;
  }

  /**
   * Gets the maximum time span until the dictionary is reset.
   *
   * @return the maximum time span, null or not positive if unlimited
   */
  public Duration getMaxAge() {
    return maxAge;
  }

  /**
   * Returns whether the next serialization will reset the dictionary first.
   *
   * @return true if a reset is pending
   */
  public synchronized boolean isResetPending() {
    return stale ||
           maxObjects > 0 && count >= maxObjects ||
           maxAgeMillis > 0 && currentTimeMillis() - resetTime >= maxAgeMillis;
  }

  @Override
  public synchronized void reset() {
    writerDictionary = new CountingDictionary(factory.createDictionary(NIL_UUID, true));
    readerDictionary = factory.createDictionary(NIL_UUID, false);
    count = 0;
    resetTime = currentTimeMillis();
    stale = false;
  }

  @Override
  public synchronized void rollback(Db session) {
    LOGGER.fine("{0}: transaction rolled back -> reset pending", session);
    stale = true;
  }

  @Override
  public synchronized <T extends SessionDependable & SerialNumbered> byte[] serialize(T object) {
    Db db = (Db) object.getSession();
    boolean registerRollback = false;

    if (isResetPending()) {
      LOGGER.fine("{0}: resetting dictionary after {1} objects", db, count);
      writeResetModificationLog(db);
      reset();
      registerRollback = true;    // a lost RESET modlog would be fatal as well
    }

    int entryCount = writerDictionary.getAddedEntryCount();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Serializer serializer = factory.createSerializer(out);
    TripStream stream = factory.createStream(writerDictionary, serializer, dummyDeserializer);
    stream.setFilter(PdoComponentTripFilter.getInstance());
    @SuppressWarnings("unchecked")
    TripType<T> type = factory.getType((Class<T>) object.getClass());
    type.write(object, stream);
    serializer.flush();
    count++;

    if ((registerRollback || writerDictionary.getAddedEntryCount() != entryCount) && db.isTxRunning()) {
      db.setTransactionProperty(ModificationLogSerializer.class, this);
    }

    return out.toByteArray();
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized <T extends SessionDependable & SerialNumbered> T deserialize(byte[] data) {
    Deserializer deserializer = factory.createDeserializer(new ByteArrayInputStream(data));
    TripStream stream = factory.createStream(readerDictionary, dummySerializer, deserializer);
    OpCode opCode = deserializer.deserializeOpCode();
    TripType<?> type = stream.deriveType(opCode);
    if (type.isDictionaryBased()) {
      opCode = deserializer.deserializeOpCode();
    }
    Object object = type.read(stream, opCode);
    if (object instanceof SessionDependable && object instanceof SerialNumbered) {
      return (T) object;
    }
    throw new PersistenceException("deserialized object is not a session dependable serial numbered object: " +
                                   (object == null ? "null" : object.getClass().getName()));
  }


  /**
   * Writes the {@link DbModificationType#RESET} modlog.
   *
   * @param db the session
   * @return the saved modlog
   */
  protected ModificationLog writeResetModificationLog(Db db) {
    ModificationLog log = ModificationLogFactory.getInstance().createModificationLog(db, DbModificationType.RESET);
    log.saveObject();
    return log;
  }

  /**
   * Gets the current time.<br>
   * Overridable for testing purposes.
   *
   * @return the current time in epochal milliseconds
   */
  protected long currentTimeMillis() {
    return System.currentTimeMillis();
  }


  /**
   * Dictionary counting the added entries.<br>
   * Used to detect whether a serialized object carries new dictionary entries.
   */
  private static class CountingDictionary implements Dictionary {

    private final Dictionary dictionary;
    private int addedEntryCount;

    private CountingDictionary(Dictionary dictionary) {
      this.dictionary = dictionary;
    }

    private int getAddedEntryCount() {
      return addedEntryCount;
    }

    @Override
    public UUID getId() {
      return dictionary.getId();
    }

    @Override
    public boolean isServer() {
      return dictionary.isServer();
    }

    @Override
    public boolean isEmpty() {
      return dictionary.isEmpty();
    }

    @Override
    public DictionaryEntry createEntry(Class<?> clazz) {
      return dictionary.createEntry(clazz);
    }

    @Override
    public DictionaryEntry createTemporaryEntry(Class<?> clazz) {
      return dictionary.createTemporaryEntry(clazz);
    }

    @Override
    public DictionaryEntry createEntry(int ordinal, String className, boolean isProxy, DictionaryEntryComponent[] components) {
      return dictionary.createEntry(ordinal, className, isProxy, components);
    }

    @Override
    public DictionaryEntryComponent createComponent(String componentName, TripType<?> type) {
      return dictionary.createComponent(componentName, type);
    }

    @Override
    public void addEntry(DictionaryEntry entry) {
      dictionary.addEntry(entry);
      addedEntryCount++;    // invoked within synchronized serialize()
    }

    @Override
    public DictionaryEntry getEntry(int ordinal) {
      return dictionary.getEntry(ordinal);
    }

    @Override
    public DictionaryEntry getEntry(String className) {
      return dictionary.getEntry(className);
    }

    @Override
    public String toString() {
      return dictionary.toString();
    }
  }

}
