/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.dbms;

import org.tentackle.common.ExceptionHelper;
import org.tentackle.common.Service;
import org.tentackle.dbms.trip.DbModificationTrackerRemoteDelegate;
import org.tentackle.io.ReconnectedException;
import org.tentackle.log.Logger;
import org.tentackle.log.Logger.Level;
import org.tentackle.misc.IdSerialNameTuple;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.session.AbstractSessionTask;
import org.tentackle.session.DefaultSessionTaskDispatcher;
import org.tentackle.session.MasterSerial;
import org.tentackle.session.MasterSerialEvent;
import org.tentackle.session.MasterSerialEventHandlerFactory;
import org.tentackle.session.ModificationEvent;
import org.tentackle.session.ModificationEventDetail;
import org.tentackle.session.ModificationListener;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionClosedException;
import org.tentackle.session.SessionUtilities;
import org.tentackle.task.Task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * The modification tracker for the tentackle persistence layer.
 * <p>
 * The tracker runs as a thread that periodically polls the modification table (see {@link DbModification})
 * and notifies the registered {@link ModificationListener}s about changed tables.
 * Modification counts are collected in {@link ModificationTally}s and persisted by the tracker
 * thread, or immediately in {@link #isLocalClientMode() local client mode}.
 * In remote clients, the tracker polls the server's tracker via a {@link DbModificationTrackerRemoteDelegate}.
 *
 * @author harald
 */
@Service(ModificationTracker.class)
public class DbModificationTracker extends DefaultSessionTaskDispatcher implements ModificationTracker {

  private static final Logger LOGGER = Logger.get(DbModificationTracker.class);

  private static final long DEFAULT_POLLING_INTERVAL = 2000;        // 2s

  private static final AtomicInteger LISTENER_KEY_INSTANCE_COUNTER = new AtomicInteger();


  /**
   * The listener entries.<br>
   * Sorted according to priority and registration order.
   */
  private static class ListenerEntry implements Comparable<ListenerEntry> {

    /** the modification listener. */
    private final ModificationListener listener;

    /** the table entry. */
    private final TableEntry tableEntry;

    /** the priority. */
    private final int priority;

    /** the instance number representing the registration order. */
    private final int instanceNumber;

    /** the time window in milliseconds. */
    private final long timeFrame;

    /**
     * Creates a key with a given priority.
     *
     * @param listener the modification listener
     * @param tableEntry the table entry
     */
    private ListenerEntry(ModificationListener listener, TableEntry tableEntry) {
      this.listener = listener;
      this.tableEntry = tableEntry;
      if (listener.getTimeFrame() > 0) {
        this.timeFrame = listener.getTimeFrame();
        this.priority = 0;
      }
      else {
        this.timeFrame = 0;
        this.priority = listener.getPriority();
      }
      this.instanceNumber = LISTENER_KEY_INSTANCE_COUNTER.incrementAndGet();
    }

    /**
     * Returns whether this is the master-listener.
     *
     * @return true if master listener
     */
    private boolean isMaster() {
      return tableEntry.isMaster();
    }

    /**
     * Creates a modification detail.
     *
     * @param serial the serial
     * @return the detail
     */
    private ModificationEventDetail createDetail(long serial) {
      return new ModificationEventDetail(tableEntry.tableName, serial);
    }

    @Override
    public int hashCode() {
      int hash = 7;
      hash = 41 * hash + this.instanceNumber;
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      return this.instanceNumber == ((ListenerEntry) obj).instanceNumber;
    }

    @Override
    public int compareTo(ListenerEntry obj) {
      int rv = Integer.compare(priority, obj.priority);
      if (rv == 0) {
        rv = Integer.compare(instanceNumber, obj.instanceNumber);
      }
      return rv;
    }

    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder();
      buf.append("order=");
      buf.append(instanceNumber);
      if (timeFrame != 0) {
        buf.append(" window=");
        buf.append(timeFrame);
      }
      else  {
        buf.append(" prio=");
        buf.append(priority);
      }
      return buf.toString();
    }
  }


  /**
   * Entry for each table (or class) to watch for modifications.
   */
  private static class TableEntry {

    private final String tableName;                     // the table name
    private volatile long id;                           // unique id
    private volatile long serial;                       // last serial

    /**
     * Creates a modification entry.<br>
     * For internal use only!
     *
     * @param tableName the table name of the class, null if master-serial
     * @param id the table name id, 0 for master serial
     * @param serial the last serial seen
     */
    private TableEntry(String tableName, long id, long serial) {
      this.tableName = tableName;
      this.id = id;
      this.serial = serial;
    }

    /**
     * Returns whether this is the master entry.
     *
     * @return true if master entry
     */
    private boolean isMaster() {
      return id == 0;
    }

    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder();
      if (tableName != null) {
        buf.append("tableName='");
        buf.append(tableName);
        buf.append("', id=");
        buf.append(id);
      }
      else  {
        buf.append("master");
      }
      buf.append(", serial=");
      buf.append(serial);
      return buf.toString();
    }

  }


  /**
   * A delayed event.<br>
   * There is only one event per listener.
   * The natural ordering is by invocation time.
   */
  private static class DelayedEvent implements Comparable<DelayedEvent> {

    private final ModificationListener listener;
    private final Map<String,ModificationEventDetail> details;
    private final long invocationTime;

    /**
     * Creates a delayed event.
     *
     * @param invocationTime the invocation time in epochal ms
     * @param listener the listener
     */
    private DelayedEvent(long invocationTime, ModificationListener listener) {
      this.invocationTime = invocationTime;
      this.listener = listener;
      this.details = new HashMap<>();
    }

    /**
     * Adds modification details.
     *
     * @param details named event details
     */
    private void addDetails(Collection<ModificationEventDetail> details) {
      for (ModificationEventDetail detail: details) {
        this.details.put(detail.getName(), detail);
      }
    }

    /**
     * Gets the modification details.
     *
     * @return the details
     */
    private Collection<ModificationEventDetail> getDetails() {
      return details.values();
    }

    @Override
    public int hashCode() {
      int hash = 7;
      hash = 59 * hash + Objects.hashCode(this.listener);
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      DelayedEvent other = (DelayedEvent) obj;
      return Objects.equals(this.listener, other.listener);
    }

    @Override
    public int compareTo(DelayedEvent o) {
      return Long.compare(invocationTime, o.invocationTime);
    }

  }

  private volatile int remoteDelegateId;                            // the delegate ID
  private final List<Runnable> shutdownRunnables;                   // runnables to be run once when the thread is stopped
  private final Object shutdownRunnablesLock;                       // guards shutdownRunnables
  private final Object configurationLock;                           // serializes the configuration tasks
  private final Map<String, ModificationTally> countersByName;      // map of modification counters (tableName:counter)
  private final Map<Long, ModificationTally> countersById;          // map of modification counters (id:counter)
  private final Set<ModificationTally> retiredCounters;             // replaced counters with pending counts left
  private final Set<ListenerEntry> listenerEntries;                 // modification listeners, sorted by priority
  private final Map<String, TableEntry> tableEntriesByName;         // the table entries mapped by (table)name
  private final Map<Long, TableEntry> tableEntriesById;             // the table entries mapped by ID
  private volatile TableEntry masterEntry;                          // master serial entry
  private final Random timeRandom;                                  // execution time randomizer
  private final Map<ModificationListener,DelayedEvent> delayedListeners;   // map of delayed listeners (tracker thread only)
  private final Set<DelayedEvent> delayedEvents;                    // delayed events (tracker thread only)
  private long delayedSleepInterval;                                // != 0 if this is the next max. sleep interval
  private DbModification masterModification;                        // po to access the master serial
  private volatile boolean localClientMode;                         // true if the local session and not a server
  private long lastPollTime;                                        // last polling time
  private Map<String, IdSerialTuple> remoteSerials;                 // initial id/serial/name tuples of the remote server's tracker (null if local)
  private boolean remoteConfigPending;                              // true if remote session configuration pending

  /**
   * Creates a tracker to be configured.
   *
   * @param name the thread's name
   */
  public DbModificationTracker(String name) {
    super(name);

    /*
     * This is not a daemon thread!
     * As long as this thread is running, the JVM will not terminate.
     */
    setDaemon(false);
    setPriority(MAX_PRIORITY);    // runs periodically as reliable as possible

    setSessionClosedOnTermination(true);
    setSleepInterval(DEFAULT_POLLING_INTERVAL);

    countersByName = new ConcurrentHashMap<>();
    countersById = new ConcurrentHashMap<>();
    retiredCounters = ConcurrentHashMap.newKeySet();
    listenerEntries = new ConcurrentSkipListSet<>();    // sorted like a TreeSet, but iterable while being modified
    delayedListeners = new HashMap<>();
    delayedEvents = new TreeSet<>();
    tableEntriesByName = new ConcurrentHashMap<>();
    tableEntriesById = new ConcurrentHashMap<>();
    timeRandom = new Random();
    shutdownRunnables = new ArrayList<>();
    shutdownRunnablesLock = new Object();
    configurationLock = new Object();
  }

  /**
   * Creates a tracker with a default name to be configured.
   */
  public DbModificationTracker() {
    this("DB Modification Tracker");
  }


  @Override
  public Session requestSession() {
    lock(Thread.currentThread());
    return getSession();
  }

  @Override
  public boolean releaseSession(Session session) {
    return unlock(Thread.currentThread());
  }

  @Override
  public Db getSession() {
    Db db = (Db) super.getSession();
    if (db == null) {
      PersistenceException ex = new PersistenceException("no session configured for " + this);
      ex.setTemporary(true);    // try again later when everything is up and running
      throw ex;
    }
    return db;
  }

  @Override
  public void setSession(Session session) {
    super.setSession(session);
    if (session.isRemote()) {
      remoteConfigPending = true;
    }
    // clear counters
    retireCounters();
    // check if the modification table is initialized
    try {
      masterModification = new DbModification((Db) session);
      selectMasterSerial();
    }
    catch (PersistenceException ex) {
      DbModification.initializeModificationTable(getSession());
    }
  }

  /**
   * Adds a shutdown runnable.<br>
   * The runnables will be executed on termination of this tracker.
   *
   * @param runnable the runnable
   */
  @Override
  public void addShutdownRunnable(Runnable runnable) {
    synchronized (shutdownRunnablesLock) {
      shutdownRunnables.add(runnable);
    }
  }

  /**
   * Removes a shutdown runnable.
   * <p>
   * Notice that the first occurrence of the runnable is removed.
   *
   * @param runnable the runnable
   * @return true if runnable removed, false if no such runnable registered
   */
  @Override
  public boolean removeShutdownRunnable(Runnable runnable) {
    synchronized (shutdownRunnablesLock) {
      return shutdownRunnables.remove(runnable);
    }
  }

  /**
   * Adds a modification listener.
   *
   * @param listener the listener to add
   */
  @Override
  public void addModificationListener(ModificationListener listener) {
    // notice that the listener must refer to the configured entry, not to one configured concurrently
    if (listener.getNames() == null || listener.getNames().length == 0) {
      // unnamed master listener
      TableEntry entry = masterEntry;
      if (entry == null) {
        entry = configureMaster();
      }
      listenerEntries.add(new ListenerEntry(listener, entry));
    }
    else  {
      // named listener(s)
      for (String name: listener.getNames()) {
        TableEntry entry = tableEntriesByName.get(name);
        if (entry == null) {
          entry = configureName(name);
        }
        listenerEntries.add(new ListenerEntry(listener, entry));
      }
    }
  }

  /**
   * Removes a modification listener.
   *
   * @param listener the listener to remove
   * @return true if listener removed
   */
  @Override
  public boolean removeModificationListener(ModificationListener listener) {
    boolean removed = false;
    Iterator<ListenerEntry> iter = listenerEntries.iterator();
    while (iter.hasNext()) {
      ListenerEntry listenerEntry = iter.next();
      if (listener == listenerEntry.listener) {   // == is okay
        iter.remove();
        removed = true;
      }
    }
    return removed;
  }

  /**
   * Returns whether the tracker runs in local client mode.<br>
   * In this mode, modifications will be persisted immediately.
   * This is necessary for applications without a middle-tier server,
   * for example, desktop standalone applications directly talking to the database.
   * <p>
   * In multi-tier applications, however, modifications will be persisted delayed
   * by the middle-tier (connected to the database) when no transactions are running
   * anymore that refer to the modified tables.
   * <p>
   * Important: in local client mode, the thread-local session must be valid,
   * because the session used to persist the modification count must be the
   * same as the current transaction, if any.
   *
   * @return true if in local client mode, false if the remote client or server
   */
  @Override
  public boolean isLocalClientMode() {
    return localClientMode;
  }

  /**
   * Sets local client mode.
   *
   * @param localClientMode true if in local client mode, false if the remote client or server
   * @see #isLocalClientMode()
   */
  public void setLocalClientMode(boolean localClientMode) {
    if (this.localClientMode != localClientMode) {
      this.localClientMode = localClientMode;
      synchronized (configurationLock) {    // must not run concurrently to configureCounter
        retireCounters();
      }
      LOGGER.info(getName() + " running in {0} mode", localClientMode ? "local-client" : "optimized");
    }
  }

  /**
   * Gets the current master serial.<br>
   * Used in remote connections.
   *
   * @return the current master serial
   */
  public long getMasterSerial() {
    TableEntry entry = masterEntry;
    if (entry == null) {
      entry = configureMaster();
    }
    return entry.serial;
  }

  @Override
  public IdSerialTuple getIdSerialForName(String name) {
    if (name == null) {
      throw new PersistenceException("tableName must be given");
    }
    TableEntry entry = tableEntriesByName.get(name);
    if (entry == null) {
      entry = configureName(name);
    }
    return new IdSerialTuple(entry.id, entry.serial);
  }

  /**
   * Sets the row id and serial for a given name.<br>
   * The method is provided for cache preloading only
   * and avoids a roundtrip.
   * <p>
   * Important: the serial will always be applied, even if the table entry already exists!
   *
   * @param name the name to set
   * @param idSerial the id/serial pair to set
   */
  public void setIdSerialForName(String name, IdSerialTuple idSerial) {
    // putIfAbsent: a concurrent configuration task must not install a second entry for the same name
    TableEntry newEntry = new TableEntry(name, idSerial.getId(), idSerial.getSerial());
    TableEntry entry = tableEntriesByName.putIfAbsent(name, newEntry);
    if (entry == null) {
      tableEntriesById.put(idSerial.getId(), newEntry);
    }
    else {
      entry.serial = idSerial.getSerial();
    }
  }

  @Override
  public IdSerialTuple getIdSerialForClass(Class<?> clazz) {
    String name = SessionUtilities.getInstance().getTableName(clazz.getName());
    if (name == null) {
      // not annotated with @TableName: try to instantiate and get from AbstractDbObject
      AbstractDbObject<?> obj = (AbstractDbObject<?>) DbUtilities.getInstance().createObject((Class<?>) clazz);
      if (obj != null) {
        name = obj.getTableName();
      }
    }
    return getIdSerialForName(name);
  }

  /**
   * Gets the id/serial tuples of all monitored tables.<br>
   * Used to serve remote clients only.
   *
   * @return the tuples
   */
  public List<IdSerialTuple> getAllIdSerials() {
    List<IdSerialTuple> tuples = new ArrayList<>();
    for (TableEntry entry: tableEntriesByName.values()) {
      tuples.add(new IdSerialTuple(entry.id, entry.serial));
    }
    return tuples;
  }

  /**
   * Gets all id/serial/name tuples from the table entries.<br>
   * Used in remote connections.
   *
   * @return the tuples
   */
  public List<IdSerialNameTuple> getAllIdSerialNames() {
    List<IdSerialNameTuple> tuples = new ArrayList<>();
    for (TableEntry entry : tableEntriesByName.values()) {
      tuples.add(new IdSerialNameTuple(entry.id, entry.serial, entry.tableName));
    }
    return tuples;
  }

  /**
   * Counts the modification for a table.<br>
   * Used by the persistence layer to update the modification table.<br>
   * Never invoked for remote sessions!
   *
   * @param session the session persisting the modification, null if thread-local session
   * @param tableName the table name
   * @return the table serial assigned to the modification (the counter could be higher already)
   */
  @Override
  public long countModification(Session session, String tableName) {
    ModificationTally counter;
    long serial;
    do {
      counter = getCounter(tableName);
      // put pending count
      serial = counter.countPending(session);
    } while (serial == 0);   // retired concurrently: count by its successor
    if (localClientMode) {
      // immediate mode -> persist now
      counter.performPendingCount();
    }
    else if (!isAlive()) {
      // no poll() because not alive -> persist now, unless the tracker thread has been started meanwhile.
      // The tracker thread owns the session from then on, see start().
      synchronized (configurationLock) {
        if (!isAlive()) {
          counter.performPendingCount();
        }
      }
    }
    // else optimize: pending count is done in next poll()
    // Notice: the serial must be the one assigned by countPending, not the counter's latest serial,
    // which may include counts of concurrent modifications already, resulting in duplicate tableserials.
    return serial;
  }

  @Override
  public void addDeletion(Session session, String tableName, long id, long tableSerial) {
    if (!localClientMode && isAlive()) {
      getCounter(tableName).addDeletion(session, new IdSerialTuple(id, -tableSerial));
    }
  }

  /**
   * Adjusts the pending counts in all {@link ModificationTally}s.<br>
   * Eliminates cache invalidations if the whole pending count was due to the transaction rolled back.
   *
   * @param session the session with a rolled back transaction
   * @param txNumber the transaction number
   */
  @Override
  public void rollbackModification(Session session, long txNumber) {
    for (ModificationTally counter : countersByName.values()) {
      counter.rollbackPending(session, txNumber);
    }
    for (ModificationTally counter : retiredCounters) {
      counter.rollbackPending(session, txNumber);
    }
  }

  /**
   * Invalidates the modification table and re-initializes all entries and counters.
   */
  @Override
  public void invalidate() {
    // a configuration task: it replaces the counters and uses the tracker's session
    runConfigurationTask(new ConfigurationTask() {
      @Override
      protected void configure() {
        retireCounters();
        if (masterEntry != null) {
          masterEntry.serial = 1;
        }
        Db db = DbModificationTracker.this.getSession();
        if (!db.isRemote()) {
          DbModification.initializeModificationTable(db);
        }
        refreshTableEntries();
      }
    });
  }


  @Override
  protected void cleanup() {
    if (lastPollTime != 0) {
      long pollPending = System.currentTimeMillis() - lastPollTime;
      double pollFactor = (double) pollPending / (double) getSleepInterval();
      if (pollFactor > 2.0) {
        // longer running tasks? blocked connection? server closed a crashed session?
        LOGGER.warning("last poll " + pollPending + " ms ago, " +
                       String.format("%.2f", pollFactor) + " times the polling interval of " + getSleepInterval() + " ms)");
      }
      lastPollTime = 0;   // cleanup is invoked a second time from the main thread for sure
    }

    performPendingCounts();
    super.cleanup();
    invokeShutdownRunnables();
  }

  @Override
  @SuppressWarnings("unchecked")
  protected void lockInternal() {
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastPollTime > getSleepInterval() >> 1) {
      // don't poll too often, at most each half-sleep interval
      lastPollTime = currentTime;

      try {
        poll();
        invokeDelayedListeners();
      }
      catch (Throwable t) {
        if (!ExceptionHelper.handleException(
            true, t,
            new ExceptionHelper.Handler<>(ReconnectedException.class, rx -> {
              // reconnected: only log and start over.
              // requires Db.enableReconnection(true, >0) on the tracker's session
              LOGGER.warning("reconnection successful -> resuming normal operation ...", rx);
            }),
            new ExceptionHelper.Handler<>(SessionClosedException.class, sx -> {
              // if remote: the remote server has closed session (deadlock, timeout, whatever).
              // if local: the database server has closed the session.
              // reopen once and treat as if polling was ok.
              // if the session could not be re-opened: the process will terminate.
              if (isReOpenOnceEnabled()) {
                LOGGER.severe("session " + getSession() + " closed unexpectedly -> trying to re-open ...", sx);
                getSession().reOpen();
              }
              else {
                throw sx;
              }
            }))) {
          throw t;  // all other exceptions will be thrown as is
        }
      }
    }

    super.lockInternal();
  }

  /**
   * Returns whether the session should be re-opened (once) after a {@link SessionClosedException}.<br>
   * This is usually the case for non-grouped sessions, since grouping usually means
   * that the tracker's session is a lead session for applications such as desktop clients.
   * Those applications must be "killable" by closing the lead session.
   * <p>
   * Override this method if the application follows other rules.
   *
   * @return true if try to re-open, false to propagate exception as usual
   */
  protected boolean isReOpenOnceEnabled() {
    return getSession().getSessionGroupId() == 0;
  }

  @Override
  protected void unlockInternal(long sleepMs) {
    if (delayedSleepInterval > 0) {
      if (delayedSleepInterval < sleepMs) {
        sleepMs = delayedSleepInterval;
      }
      delayedSleepInterval = 0;
    }
    super.unlockInternal(sleepMs);
  }

  /**
   * Creates a modification event.
   *
   * @param details the event details, null or empty if master event
   * @return the event
   */
  protected ModificationEvent createModificationEvent(Collection<ModificationEventDetail> details) {
    return details == null || details.isEmpty() ?
            new ModificationEvent(getSession(), getMasterSerial()) :
            new ModificationEvent(getSession(), details);
  }

  /**
   * Extracts the master serial from the master serial object.<br>
   * Used in remote clients only.
   * <p>
   * If the master serial implements {@link MasterSerialEvent}, the corresponding
   * handler will be invoked (if any) before returning the serial.
   *
   * @param masterSerial the master serial object
   * @return the long serial
   */
  protected long extractMasterSerial(MasterSerial masterSerial) {
    if (masterSerial instanceof MasterSerialEvent masterSerialEvent) {
      if (masterSerialEvent instanceof Collection<?> events) {
        for (Object object : events) {
          if (object instanceof MasterSerialEvent event) {
            handleMasterSerialEvent(event);
          }
        }
      }
      else {
        handleMasterSerialEvent(masterSerialEvent);
      }
    }
    return masterSerial.serial();
  }

  /**
   * Handles a single master serial event.
   *
   * @param event the master serial event
   */
  @SuppressWarnings("unchecked")
  protected void handleMasterSerialEvent(MasterSerialEvent event) {
    // map to corresponding handler
    Consumer<MasterSerialEvent> eventHandler = (Consumer<MasterSerialEvent>) MasterSerialEventHandlerFactory.getInstance().getEventHandler(event.getClass());
    if (eventHandler != null) {
      eventHandler.accept(event);
    }
    else {
      // else: no handler registered -> ignored (this may be intended!)
      LOGGER.fine("no handler for {0}", () -> event.getClass().getName());
    }
  }

  /**
   * Returns the new master serial if modified.<br>
   * The method is provided to allow extensions, for example, listening for other events on other tables.
   *
   * @return the updated master serial, null if unchanged
   */
  protected Long getModifiedMasterSerial() {
    long serial = selectMasterSerial();
    if (serial != getMasterSerial()) {
      return serial;
    }
    return null;
  }

  /**
   * Reads the master-serial from the database.
   *
   * @return the master serial
   */
  protected long selectMasterSerial() {
    Db session = getSession();
    if (session.isRemote()) {
      return extractMasterSerial(getRemoteDelegate(session).selectMasterSerial());
    }
    else {
      return masterModification.selectMasterSerial();
    }
  }

  /**
   * Selects id/serial/name tuples from the remote server's tracker.<br>
   * The method is provided to reduce the number of roundtrips during startup of the client application.
   * For local sessions, the tuples are read from the modification table.
   *
   * @return the tuples
   */
  protected List<IdSerialNameTuple> selectAllIdSerialNames() {
    Db session = getSession();
    if (session.isRemote()) {
      return getRemoteDelegate(session).selectAllIdSerialNames();
    }
    else {
      List<IdSerialNameTuple> tuples = new ArrayList<>();
      for (DbModification dbMod : masterModification.selectAllObjects()) {
        tuples.add(new IdSerialNameTuple(dbMod.getId(), dbMod.getSerial(), dbMod.getTrackedName()));
      }
      return tuples;
    }
  }

  /**
   * Selects id/serial for all monitored tables.<br>
   * Override this method to listen on other modification tables as well (from other apps, for example),
   * see {@link #getModifiedMasterSerial()}.
   *
   * @return the tuples
   */
  protected List<IdSerialTuple> selectAllIdSerials() {
    Db session = getSession();
    if (session.isRemote()) {
      return getRemoteDelegate(session).selectAllIdSerials();
    }
    else {
      return masterModification.selectAllIdSerial();
    }
  }

  /**
   * Selects the pair of id/serial for a given table name.
   *
   * @param tableName the table to lookup
   * @return the id/serial pair for the table name, null if table not configured so far
   */
  protected IdSerialTuple selectIdSerialForName(String tableName) {
    Db session = getSession();
    if (session.isRemote()) {
      IdSerialTuple tuple = getRemoteIdSerial(tableName);   // try fast lookup first (to save a roundtrip)
      if (tuple == null) {
        // not in the initial map? -> get it from the remote server (and optionally create it)
        tuple = getRemoteDelegate(session).selectIdSerialForName(tableName);
      }
      return tuple;
    }
    else {
      return masterModification.selectIdSerial(tableName);
    }
  }

  /**
   * Gets the remote delegate ID.
   * <p>
   * Although the ModificationTracker is a singleton, the remote delegate is an instance variable.
   * If the application accidentally creates multiple instances of the ModificationTracker,
   * bypassing the ServiceFinder, each gets its own remote delegate.
   * So the old one is wasted, but the application does not fail with a ClassCastException if
   * the delegates are of different types.
   *
   * @return the delegate ID
   */
  protected int getRemoteDelegateId() {
    int id = remoteDelegateId;
    if (id == 0) {
      synchronized (this) {
        id = remoteDelegateId;
        if (id == 0) {
          id = prepareRemoteDelegate();
          remoteDelegateId = id;
        }
      }
    }
    return id;
  }

  /**
   * Prepares the remote delegate.
   * <p>
   * This method is called by {@link #getRemoteDelegateId()} if the delegate ID is not yet set.
   * It is overridable to allow clients to use a ModificationTracker
   * that is not known to the server.
   * <p>
   * Example:
   * <pre>
   *   &#64;Override
   *   protected int prepareRemoteDelegate() {
   *     // necessary since the server does not know RestModificationTracker.class
   *     return Db.prepareRemoteDelegate(getClass().getSuperclass());
   *   }
   * </pre>
   *
   * @return the delegate ID
   */
  protected int prepareRemoteDelegate() {
    return Db.prepareRemoteDelegate(getClass());
  }

  /**
   * Gets the remote delegate.
   *
   * @param session the session
   * @return the delegate
   */
  protected DbModificationTrackerRemoteDelegate getRemoteDelegate(Db session) {
    return (DbModificationTrackerRemoteDelegate) session.getRemoteDelegate(getRemoteDelegateId());
  }

  /**
   * Flushes all pending modification counts to the database.
   */
  private void performPendingCounts() {
    for (ModificationTally counter: countersByName.values()) {
      counter.performPendingCount();
    }
    for (Iterator<ModificationTally> iter = retiredCounters.iterator(); iter.hasNext(); ) {
      ModificationTally counter = iter.next();
      counter.performPendingCount();
      if (counter.isRetiredAndDone()) {
        iter.remove();
      }
    }
  }

  /**
   * Removes all counters.<br>
   * Other threads may still be counting on them, so they are retired rather than dropped:
   * the pending counts are performed until none are left, and new counts go to their successors.
   */
  private void retireCounters() {
    for (Iterator<ModificationTally> iter = countersByName.values().iterator(); iter.hasNext(); ) {
      ModificationTally counter = iter.next();
      retiredCounters.add(counter);   // before retiring: counts accepted until then must not get lost
      iter.remove();
      counter.retire();
    }
    countersById.clear();
  }

  /**
   * Checks for changes in the modification table.
   * <p>
   * Always invoked from within the tracker thread, so it's ok to use
   * the thread's session.
   */
  private void poll() {

    Db session = getSession();
    boolean isLocalSession = !session.isRemote();
    if (isLocalSession) {
      performPendingCounts();
    }
    else if (remoteConfigPending) {
      // do that only once!
      getRemoteDelegate(session).configureRemoteSession();
      remoteConfigPending = false;
    }

    Long serial = getModifiedMasterSerial();

    if (serial != null) {   // changed!

      remoteSerials = null;   // no more valid

      if (serial < masterEntry.serial) {
        // the new serial is less than the current serial: force complete re-initialization
        invalidate();
      }

      // read all serials and create modification events
      Map<ModificationListener,Set<ModificationEventDetail>> detailMap = new HashMap<>();

      for (IdSerialTuple idSer: selectAllIdSerials()) {

        if (isLocalSession && idSer.getId() > 0) {
          // update the last serial of counter (if configured)
          ModificationTally counter = countersById.get(idSer.getId());
          if (counter != null) {
            counter.setLastSerial(idSer.getSerial());
          }
        }

        TableEntry tableEntry = tableEntriesById.get(idSer.getId());     // serial changed for entry
        if (tableEntry != null) {                                        // no entry -> no listenerEntry
          for (ListenerEntry listenerEntry : listenerEntries) {
            if (listenerEntry.tableEntry.id == idSer.getId() && listenerEntry.tableEntry.serial != idSer.getSerial()) {
              // table entry for listener has changed
              LOGGER.fine("modification detected for {0}, new serial {1}", listenerEntry.tableEntry, idSer.getSerial());
              Set<ModificationEventDetail> details = detailMap.computeIfAbsent(listenerEntry.listener, _ -> new HashSet<>());
              // create a new one
              if (!listenerEntry.isMaster()) {
                if (isLocalSession) {
                  getCounter(listenerEntry.tableEntry.tableName);   // configure counter if not yet done
                }
                details.add(listenerEntry.createDetail(idSer.getSerial()));
              }
            }
          }
          tableEntry.serial = idSer.getSerial();   // remember serial, even if no listener entry
        }
      }

      masterEntry.serial = serial;

      // fire listeners
      for (Map.Entry<ModificationListener,Set<ModificationEventDetail>> entry: detailMap.entrySet()) {
        ModificationListener listener = entry.getKey();
        Set<ModificationEventDetail> details = entry.getValue();
        long timeFrame = listener.getTimeFrame();
        long timeDelay = listener.getTimeDelay();
        if (timeFrame > 0 || timeDelay > 0) {
          // delayed
          DelayedEvent event = delayedListeners.get(listener);
          if (event == null) {
            // new
            long epochalExecutionTime = System.currentTimeMillis();
            if (timeDelay > 0) {
              epochalExecutionTime += timeDelay;
            }
            if (timeFrame > 0) {
              epochalExecutionTime +=  (long) (timeRandom.nextDouble() * timeFrame);
            }
            event = new DelayedEvent(epochalExecutionTime, listener);
            event.addDetails(details);
            delayedListeners.put(listener, event);
            delayedEvents.add(event);
          }
          else  {
            // add the details (don't change the execution time)
            event.addDetails(details);
          }
        }
        else  {
          // invoke immediately
          listener.dataChanged(createModificationEvent(details));
        }
      }

    }
  }

  /**
   * Invokes the delayed modification listeners.
   */
  private void invokeDelayedListeners() {
    long currentTime = System.currentTimeMillis();
    delayedSleepInterval = getSleepInterval();
    Iterator<DelayedEvent> iter = delayedEvents.iterator();
    while (iter.hasNext()) {  // ordered by invocation time
      DelayedEvent delayedEvent = iter.next();
      long msLeft = delayedEvent.invocationTime - currentTime;
      if (msLeft <= 0) {
        // ready to run
        ModificationEvent event = createModificationEvent(delayedEvent.getDetails());
        delayedEvent.listener.dataChanged(event);
        iter.remove();
        delayedListeners.remove(delayedEvent.listener);
      }
      else if (msLeft < delayedSleepInterval) {
        delayedSleepInterval = msLeft;   // sleep until the first execution interval
      }
    }
  }

  /**
   * Gets the id/serial tuple from the cached remote initial values.<br>
   * Only used in remote connections.
   *
   * @param tableName the table to lookup
   * @return the id/serial tuple
   */
  private IdSerialTuple getRemoteIdSerial(String tableName) {
    if (remoteSerials == null) {
      Map<String, IdSerialTuple> remSers = new HashMap<>();
      for (IdSerialNameTuple tuple : selectAllIdSerialNames()) {
        remSers.put(tuple.getName(), tuple);
      }
      remoteSerials = remSers;
    }
    return remoteSerials.get(tableName);
  }

  /**
   * Refreshes the serials and ids for all monitored tables.
   */
  private void refreshTableEntries() {
    remoteSerials = null;
    boolean isLocalSession = !getSession().isRemote();
    List<TableEntry> rekeyedEntries = new ArrayList<>();
    for (TableEntry entry: tableEntriesByName.values()) {
      if (isLocalSession) {
        getCounter(entry.tableName);
      }
      IdSerialTuple idSer = selectIdSerialForName(entry.tableName);
      if (idSer == null && isLocalSession) {
        // deleted by the re-initialization: create it now to get its new id, like configureName does
        addToModificationTable(entry.tableName);
        idSer = selectIdSerialForName(entry.tableName);
      }
      if (idSer != null) {
        if (entry.id != idSer.getId()) {
          // the modification table was re-initialized: the entry got a new id
          tableEntriesById.remove(entry.id, entry);
          rekeyedEntries.add(entry);
        }
        entry.id = idSer.getId();
        entry.serial = idSer.getSerial();
      }
    }
    // not before all stale ids are removed: the new id of an entry may be the old id of another one
    for (TableEntry entry : rekeyedEntries) {
      tableEntriesById.put(entry.id, entry);
    }
  }

  /**
   * Gets the modification counter for a given table.<br>
   * If there is no such counter, it will be created.
   *
   * @param tableName the table name
   * @return the counter
   */
  private ModificationTally getCounter(String tableName) {
    ModificationTally counter = countersByName.get(tableName);
    if (counter == null) {
      counter = configureCounter(tableName);
    }
    return counter;
  }

  /**
   * Configures a counter.
   *
   * @param tableName the name
   * @return the counter
   */
  private ModificationTally configureCounter(String tableName) {
    Task task = new ConfigurationTask() {
      @Override
      protected void configure() {
        if (countersByName.get(tableName) == null) {   // another task may have configured it in the meantime
          ModificationTally counter = new ModificationTally(DbModificationTracker.this, tableName);
          IdSerialTuple idSer = selectIdSerialForName(tableName);
          long lastSerial = idSer == null ? 0 : idSer.getSerial();
          counter.setLastSerial(lastSerial);
          countersByName.put(tableName, counter);
          countersById.put(counter.getId(), counter);
        }
      }
    };
    runConfigurationTask(task);
    ModificationTally tally = countersByName.get(tableName);
    if (tally == null) {
      throw new PersistenceException("configuration of counter failed for " + tableName);
    }
    return tally;
  }

  /**
   * Adds a missing row for a name to the modification table.<br>
   * The counter was registered by its id before the row existed, i.e., with id 0,
   * so it is registered again by the id of the new row.
   *
   * @param name the name
   */
  private void addToModificationTable(String name) {
    ModificationTally counter = getCounter(name);
    counter.addToModificationTable();
    countersById.remove(0L, counter);
    countersById.put(counter.getId(), counter);
  }

  /**
   * Configures the master entry.
   *
   * @return the master entry, never null
   */
  private TableEntry configureMaster() {
    Task task = new ConfigurationTask() {
      @Override
      protected void configure() {
        if (masterEntry == null) {   // another task may have configured it in the meantime
          long serial = selectMasterSerial();
          masterEntry = new TableEntry(null, 0, serial);
        }
      }
    };

    runConfigurationTask(task);
    TableEntry entry = masterEntry;
    if (entry == null) {
      throw new PersistenceException("configuration of the master entry failed");
    }
    return entry;
  }

  /**
   * Configures a named entry.
   *
   * @param name the name
   * @return the table entry, never null
   */
  private TableEntry configureName(String name) {
    Task task = new ConfigurationTask() {
      @Override
      protected void configure() {
        if (tableEntriesByName.get(name) == null) {   // another task may have configured it in the meantime
          IdSerialTuple idSer = selectIdSerialForName(name);
          if (idSer == null) {
            // missing: create it
            addToModificationTable(name);
            idSer = selectIdSerialForName(name);
          }
          TableEntry entry = new TableEntry(name, idSer.getId(), idSer.getSerial());
          if (tableEntriesByName.putIfAbsent(name, entry) == null) {   // setIdSerialForName may have won the race
            tableEntriesById.put(idSer.getId(), entry);
          }
        }
      }
    };

    runConfigurationTask(task);
    TableEntry entry = tableEntriesByName.get(name);
    if (entry == null) {
      throw new PersistenceException("configuration of the table entry failed for " + name);
    }
    return entry;
  }

  /**
   * Runs a configuration task.
   * <p>
   * Configuration tasks lazily create the entries and counters. They must be serialized against
   * each other, since two threads requesting the same entry concurrently would otherwise create
   * it twice: the loser's instance is replaced in the maps but is still referenced by the
   * {@link ListenerEntry} created for it. Its serial would never be updated again by {@link #poll()},
   * so the listener would fire on every poll from then on.
   * <p>
   * Tasks are executed by the tracker thread, if it is running, or by the invoking thread otherwise.
   * The tracker thread owns the tracker's session, so it must not be started while another thread
   * executes a task: {@link #start()} acquires the configuration lock as well.
   * The tasks are serialized by the configuration lock, see {@link ConfigurationTask}.
   * A thread holding the lock already executes nested tasks itself: waiting for the tracker thread
   * would deadlock, since the tracker thread waits for the lock as well.
   *
   * @param task the configuration task
   */
  private void runConfigurationTask(Task task) {
    if (isTaskDispatcherThread() || Thread.holdsLock(configurationLock)) {
      task.run();               // already serialized or nested
      return;
    }
    synchronized (configurationLock) {
      if (!isAlive()) {
        task.run();             // no tracker thread to run it, and it cannot be started meanwhile
        return;
      }
    }
    addTaskAndWait(task);       // executed by the tracker thread
  }

  /**
   * Starts the tracker thread.<br>
   * Waits for configuration tasks executed by other threads, since the tracker thread
   * takes over the session they are using.
   */
  @Override
  public void start() {
    synchronized (configurationLock) {
      super.start();
    }
  }

  /**
   * Gets the lock serializing the use of the tracker's session by threads other than the tracker thread.<br>
   * The session is not thread-safe. While the tracker thread is not running, the configuration tasks
   * and the flushes of the pending counts (see {@link ModificationTally#performPendingCount()})
   * are executed by the invoking threads, all using the tracker's session.
   *
   * @return the lock
   */
  Object getSessionLock() {
    return configurationLock;
  }

  /**
   * A configuration task.<br>
   * Serialized by the configuration lock, no matter which thread executes it.
   */
  private abstract class ConfigurationTask extends AbstractSessionTask {

    @Override
    public void run() {
      synchronized (configurationLock) {
        configure();
      }
    }

    /**
     * Performs the configuration.
     */
    protected abstract void configure();
  }

  /**
   * Runs the shutdown runnables.
   */
  private void invokeShutdownRunnables() {
    /*
     * Run each runnable only once (in case of loops).
     * Notice that cleanup() may be invoked concurrently from the tracker thread and from the thread
     * terminating it, so taking the runnables over must be atomic. They are run without holding
     * the lock, since they are application code that may register or remove runnables itself.
     */
    List<Runnable> tempList;
    synchronized (shutdownRunnablesLock) {
      tempList = new ArrayList<>(shutdownRunnables);
      shutdownRunnables.clear();
    }
    for (Runnable r: tempList) {
      try {
        r.run();
      }
      catch (RuntimeException ex) {
        // log but don't stop the tracker
        LOGGER.logStacktrace(Level.SEVERE, ex);
      }
    }
  }

}
