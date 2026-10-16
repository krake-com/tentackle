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
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
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
 * A reset starts a new <em>epoch</em> and writes a {@link DbModificationType#RESET} modlog. The id of that modlog
 * becomes the epoch's number, which is stored in front of each serialized object.
 * The very first object serialized by a new instance starts a new epoch as well.
 * <p>
 * The modlog-writing transactions may overlap, i.e., they need not be serialized. Since the receiver
 * only sees committed modlogs, in the order of their commits, the dictionary is <em>commit-gated</em>:
 * <ul>
 *   <li>The entries a transaction adds remain private to that transaction until it commits
 *   (see {@link #commit(Db)}). Another transaction needing the same type meanwhile defines it again under
 *   its own ordinal.</li>
 *   <li>If the transaction is rolled back, or rolled back to a savepoint, its private entries are discarded
 *   (see {@link #rollback(Db)}). No other transaction ever referred to them.</li>
 *   <li>Objects serialized outside a transaction carry their new entries inline and never share them.</li>
 * </ul>
 * As a consequence, a serialized object refers only to entries defined earlier in its own transaction or
 * to entries whose transaction had committed before the object was serialized. The modlogs defining them
 * have a lower id, since the factory reserves the id while holding this serializer's monitor
 * (see {@link org.tentackle.dbms.DefaultModificationLogFactory}), and they are visible to every reader
 * that sees the referring modlog.
 * <p>
 * The serializer is registered for a physical backend via
 * {@link ModificationLogFactory#setSerializer(int, ModificationLogSerializer)}.
 * The receiving side, usually a replication, may use a separate instance of this class and must:
 * <ul>
 *   <li>process the visible, unprocessed modlogs in the order of their ids</li>
 *   <li>set the session of the deserialized object</li>
 * </ul>
 * The receiver keeps one dictionary per epoch, so that the modlogs of a transaction that started in an earlier
 * epoch, but committed after a later one, are still decoded correctly. It must <em>not</em> invoke {@link #reset()}
 * when it encounters a {@link DbModificationType#RESET} modlog, since this would discard the dictionaries of the
 * earlier epochs. The number of dictionaries kept is limited, see {@link #setMaxEpochs(int)}.
 * <p>
 * A receiver that is restarted, or meets an epoch it already discarded, lacks the entries of the modlogs it
 * processed before. Deserializing then fails immediately with a {@link RebuildEpochException}. The receiver
 * rebuilds the dictionary of that epoch (see {@link RebuildEpochException#getEpoch()} or {@link #getEpoch(byte[])})
 * via {@link #rebuildEpoch(long, Iterable)} from the processed modlogs with an id greater than or equal to the epoch,
 * and retries.
 */
public class TripModificationLogSerializer implements ModificationLogSerializer {

  /** the default maximum number of serialized objects until the dictionary is reset. */
  public static final int DEFAULT_MAX_OBJECTS = 10_000;

  /** the default maximum time span until the dictionary is reset. */
  public static final Duration DEFAULT_MAX_AGE = Duration.ofHours(1);

  /** the default maximum number of epochs kept by the receiver. */
  public static final int DEFAULT_MAX_EPOCHS = 16;


  private static final Logger LOGGER = Logger.get(TripModificationLogSerializer.class);

  private static final UUID NIL_UUID = new UUID(0, 0);


  /**
   * Gets the epoch of serialized data.
   *
   * @param data the data created by {@link #serialize}
   * @return the epoch, i.e., the id of the RESET modlog that started it
   */
  public static long getEpoch(byte[] data) {
    return TripFactory.getInstance().createDeserializer(new ByteArrayInputStream(data)).deserializeLong();
  }


  private final TripFactory factory;
  private final int maxObjects;
  private final Duration maxAge;
  private final long maxAgeMillis;
  private final Serializer dummySerializer;
  private final Deserializer dummyDeserializer;
  private final Dictionary entryFactory;          // creates the reader's entries and components
  private final Object pendingKey = new Object(); // transaction property key for the pending entries

  // writer
  private Dictionary writerDictionary;            // the committed entries of the current epoch
  private long epoch;                             // the current epoch, 0 if a new one must be started
  private int count;                              // number of serialized objects since last reset
  private long resetTime;                         // time of the last reset in epochal milliseconds

  // reader
  private final NavigableMap<Long, ReaderDictionary> readerDictionaries = new TreeMap<>();
  private int maxEpochs = DEFAULT_MAX_EPOCHS;
  private long discardedEpoch;                    // the highest epoch discarded by the reader


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
    entryFactory = factory.createDictionary(NIL_UUID, false);
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
   * Gets the maximum number of epochs whose dictionaries are kept by the receiver.
   *
   * @return the maximum number of epochs
   */
  public synchronized int getMaxEpochs() {
    return maxEpochs;
  }

  /**
   * Sets the maximum number of epochs whose dictionaries are kept by the receiver.
   * <p>
   * A transaction that spans more resets than that at the writer's side cannot be decoded without
   * {@link #rebuildEpoch(long, Iterable) rebuilding} the dictionary. Since a dictionary holds only one entry
   * per serialized type, the default of {@value #DEFAULT_MAX_EPOCHS} is generous.
   *
   * @param maxEpochs the maximum number of epochs, at least 1
   */
  public synchronized void setMaxEpochs(int maxEpochs) {
    if (maxEpochs < 1) {
      throw new IllegalArgumentException("at least one epoch must be kept");
    }
    this.maxEpochs = maxEpochs;
    discardOldEpochs();
  }

  /**
   * Gets the current epoch of the writer.
   *
   * @return the epoch, 0 if none started yet
   */
  public synchronized long getCurrentEpoch() {
    return epoch;
  }

  /**
   * Returns whether the next serialization will start a new epoch first.
   *
   * @return true if a reset is pending
   */
  public synchronized boolean isResetPending() {
    return epoch == 0 ||
           maxObjects > 0 && count >= maxObjects ||
           maxAgeMillis > 0 && currentTimeMillis() - resetTime >= maxAgeMillis;
  }

  /**
   * {@inheritDoc}
   * <p>
   * The writer starts a new epoch with the next serialized object. The reader forgets the dictionaries
   * of all epochs.
   */
  @Override
  public synchronized void reset() {
    epoch = 0;
    writerDictionary = null;
    readerDictionaries.clear();
    discardedEpoch = 0;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Promotes the entries added by the committed transaction to the shared dictionary, unless a new epoch
   * has started in the meantime.
   */
  @Override
  public synchronized void commit(Db session) {
    if (session.getTransactionProperty(pendingKey) instanceof PendingEntries pending && !pending.isEmpty()) {
      if (pending.epoch == epoch) {
        for (DictionaryEntry entry : pending.byOrdinal.values()) {
          if (writerDictionary.getEntry(entry.getClassName()) == null) {   // else: another transaction was first
            writerDictionary.addEntry(entry);
          }
        }
      }
      pending.clear();
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Discards the entries added by the transaction. They are never shared with other transactions, so no
   * reset is necessary.
   */
  @Override
  public synchronized void rollback(Db session) {
    if (session.getTransactionProperty(pendingKey) instanceof PendingEntries pending && !pending.isEmpty()) {
      LOGGER.fine("{0}: transaction rolled back -> {1} pending entries discarded", session, pending.byOrdinal.size());
      pending.clear();
    }
  }

  @Override
  public synchronized <T extends SessionDependable & SerialNumbered> byte[] serialize(T object) {
    Db db = (Db) object.getSession();

    if (isResetPending()) {
      LOGGER.fine("{0}: resetting dictionary after {1} objects", db, count);
      startEpoch(db);
    }

    PendingEntries pending = null;
    boolean txRunning = db.isTxRunning();
    if (txRunning && db.getTransactionProperty(pendingKey) instanceof PendingEntries txPending) {
      pending = txPending;
      if (pending.epoch != epoch) {
        pending.clear();    // entries of an earlier epoch
        pending.epoch = epoch;
      }
    }
    boolean registered = pending != null;
    if (pending == null) {
      pending = new PendingEntries(epoch);
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Serializer serializer = factory.createSerializer(out);
    serializer.serializeLong(epoch);
    TripStream stream = factory.createStream(new TransactionDictionary(writerDictionary, pending), serializer, dummyDeserializer);
    stream.setFilter(PdoComponentTripFilter.getInstance());
    @SuppressWarnings("unchecked")
    TripType<T> type = factory.getType((Class<T>) object.getClass());
    type.write(object, stream);
    serializer.flush();
    count++;

    if (txRunning && !registered && !pending.isEmpty()) {
      // get notified about commit and rollback
      db.setTransactionProperty(pendingKey, pending);
      db.setTransactionProperty(ModificationLogSerializer.class, this);
    }

    return out.toByteArray();
  }

  @Override
  public synchronized <T extends SessionDependable & SerialNumbered> T deserialize(byte[] data) {
    Deserializer deserializer = factory.createDeserializer(new ByteArrayInputStream(data));
    long dataEpoch = deserializer.deserializeLong();
    ReaderDictionary dictionary = readerDictionaries.get(dataEpoch);
    if (dictionary == null) {
      if (dataEpoch <= discardedEpoch) {
        throw new RebuildEpochException(dataEpoch,
                                        "dictionary of epoch " + dataEpoch + " already discarded -> rebuild required");
      }
      dictionary = new ReaderDictionary(dataEpoch);
      readerDictionaries.put(dataEpoch, dictionary);
      discardOldEpochs();
    }
    return read(deserializer, dictionary);
  }

  /**
   * Rebuilds the receiver's dictionary of an epoch.
   * <p>
   * Necessary if the receiver was restarted, or the dictionary of the epoch was discarded, and {@link #deserialize}
   * failed with a {@link RebuildEpochException}. The data must contain the serialized objects of the epoch the receiver
   * has processed so far, in the order of their modlog ids, i.e., the object data of the processed modlogs with an
   * id &ge; epoch. Data of other epochs is skipped.
   *
   * @param epoch the epoch
   * @param data the serialized objects
   * @return the number of objects of the given epoch
   */
  public synchronized int rebuildEpoch(long epoch, Iterable<byte[]> data) {
    ReaderDictionary dictionary = new ReaderDictionary(epoch);
    readerDictionaries.put(epoch, dictionary);
    int num = 0;
    for (byte[] bytes : data) {
      Deserializer deserializer = factory.createDeserializer(new ByteArrayInputStream(bytes));
      if (deserializer.deserializeLong() == epoch) {
        read(deserializer, dictionary);
        num++;
      }
    }
    LOGGER.info("dictionary of epoch {0} rebuilt from {1} objects", epoch, num);
    return num;
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
   * Starts a new epoch.
   *
   * @param db the session
   */
  private void startEpoch(Db db) {
    // a rolled back RESET modlog is harmless: the epoch is just a number stored with each object
    epoch = writeResetModificationLog(db).getId();
    writerDictionary = factory.createDictionary(NIL_UUID, true);
    count = 0;
    resetTime = currentTimeMillis();
  }

  /**
   * Reads an object.
   *
   * @param deserializer the deserializer positioned behind the epoch
   * @param dictionary the dictionary of the epoch
   * @return the object
   * @param <T> the object type
   */
  @SuppressWarnings("unchecked")
  private <T extends SessionDependable & SerialNumbered> T read(Deserializer deserializer, ReaderDictionary dictionary) {
    TripStream stream = factory.createStream(dictionary, dummySerializer, deserializer);
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
   * Discards the dictionaries of the oldest epochs exceeding {@link #maxEpochs}.
   */
  private void discardOldEpochs() {
    while (readerDictionaries.size() > maxEpochs) {
      long discarded = readerDictionaries.pollFirstEntry().getKey();
      discardedEpoch = Math.max(discardedEpoch, discarded);
      LOGGER.fine("dictionary of epoch {0} discarded", discarded);
    }
  }


  /**
   * The entries added by a transaction, not yet visible to other transactions.
   */
  private static class PendingEntries {

    private final Map<Integer, DictionaryEntry> byOrdinal = new HashMap<>();
    private final Map<String, DictionaryEntry> byName = new HashMap<>();
    private long epoch;

    private PendingEntries(long epoch) {
      this.epoch = epoch;
    }

    private boolean isEmpty() {
      return byOrdinal.isEmpty();
    }

    private void add(DictionaryEntry entry) {
      byOrdinal.put(entry.getOrdinal(), entry);
      byName.put(entry.getClassName(), entry);
    }

    private void clear() {
      byOrdinal.clear();
      byName.clear();
    }
  }


  /**
   * The writer's view of the dictionary for a transaction.<br>
   * Sees the committed entries plus the pending ones of the transaction. New entries are added to the pending ones.
   * The ordinals are unique within the epoch, since they are all created by the committed dictionary.
   */
  private record TransactionDictionary(Dictionary committed, PendingEntries pending) implements Dictionary {

    @Override
    public UUID getId() {
      return committed.getId();
    }

    @Override
    public boolean isServer() {
      return committed.isServer();
    }

    @Override
    public boolean isEmpty() {
      return committed.isEmpty() && pending.isEmpty();
    }

    @Override
    public DictionaryEntry createEntry(Class<?> clazz) {
      return committed.createEntry(clazz);
    }

    @Override
    public DictionaryEntry createTemporaryEntry(Class<?> clazz) {
      return committed.createTemporaryEntry(clazz);
    }

    @Override
    public DictionaryEntry createEntry(int ordinal, String className, boolean isProxy, DictionaryEntryComponent[] components) {
      return committed.createEntry(ordinal, className, isProxy, components);
    }

    @Override
    public DictionaryEntryComponent createComponent(String componentName, TripType<?> type) {
      return committed.createComponent(componentName, type);
    }

    @Override
    public void addEntry(DictionaryEntry entry) {
      pending.add(entry);
    }

    @Override
    public DictionaryEntry getEntry(int ordinal) {
      DictionaryEntry entry = pending.byOrdinal.get(ordinal);
      return entry != null ? entry : committed.getEntry(ordinal);
    }

    @Override
    public DictionaryEntry getEntry(String className) {
      DictionaryEntry entry = pending.byName.get(className);
      return entry != null ? entry : committed.getEntry(className);
    }

    @Override
    public String toString() {
      return committed.toString();
    }
  }


  /**
   * The receiver's dictionary of an epoch.
   * <p>
   * Unlike the dictionary of a TRIP connection, it never waits for a missing entry: the modlog defining it is always
   * processed before the modlogs referring to it. Furthermore, concurrent transactions may define the same class under
   * different ordinals.
   */
  private class ReaderDictionary implements Dictionary {

    private final long epoch;
    private final Map<Integer, DictionaryEntry> byOrdinal = new HashMap<>();
    private final Map<String, DictionaryEntry> byName = new HashMap<>();

    private ReaderDictionary(long epoch) {
      this.epoch = epoch;
    }

    @Override
    public UUID getId() {
      return NIL_UUID;
    }

    @Override
    public boolean isServer() {
      return false;
    }

    @Override
    public boolean isEmpty() {
      return byOrdinal.isEmpty();
    }

    @Override
    public DictionaryEntry createEntry(Class<?> clazz) {
      throw new TripRuntimeException("receiver's dictionary cannot create entries");
    }

    @Override
    public DictionaryEntry createTemporaryEntry(Class<?> clazz) {
      throw new TripRuntimeException("receiver's dictionary cannot create entries");
    }

    @Override
    public DictionaryEntry createEntry(int ordinal, String className, boolean isProxy, DictionaryEntryComponent[] components) {
      return entryFactory.createEntry(ordinal, className, isProxy, components);
    }

    @Override
    public DictionaryEntryComponent createComponent(String componentName, TripType<?> type) {
      return entryFactory.createComponent(componentName, type);
    }

    @Override
    public void addEntry(DictionaryEntry entry) {
      DictionaryEntry oldEntry = byOrdinal.putIfAbsent(entry.getOrdinal(), entry);
      if (oldEntry != null && !oldEntry.getClassName().equals(entry.getClassName())) {
        // the same object processed twice is ok, but not another class
        throw new TripRuntimeException("duplicate ordinal in epoch " + epoch + ": " + entry + ", existing: " + oldEntry);
      }
      byName.put(entry.getClassName(), entry);
    }

    @Override
    public DictionaryEntry getEntry(int ordinal) {
      DictionaryEntry entry = byOrdinal.get(ordinal);
      if (entry == null) {
        throw new RebuildEpochException(epoch, "unknown ordinal " + ordinal + " in epoch " + epoch +
                                               " -> rebuild required (receiver restarted or modlogs skipped?)");
      }
      return entry;
    }

    @Override
    public DictionaryEntry getEntry(String className) {
      return byName.get(className);
    }

    @Override
    public String toString() {
      return "epoch " + epoch;
    }
  }

}
