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
    private long id;                                    // unique id
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


  private static volatile int remoteDelegateId;   // the remote delegate ID

  /**
   * Gets the remote delegate.
   *
   * @return the delegate
   */
  private static int getRemoteDelegateId() {
    int id = remoteDelegateId;
    if (id == 0)  {
      synchronized (DbModificationTracker.class) {
        id = remoteDelegateId;
        if (id == 0)  {   // still unprepared: another thread may have won the race
          id = Db.prepareRemoteDelegate(DbModificationTracker.class);
          remoteDelegateId = id;
        }
      }
    }
    return id;
  }


  private final List<Runnable> shutdownRunnables;                   // runnables to be run once when the thread is stopped
  private final Object shutdownRunnablesLock;                       // guards shutdownRunnables
  private final Map<String, ModificationTally> countersByName;      // map of modification counters (tableName:counter)
  private final Map<Long, ModificationTally> countersById;          // map of modification counters (id:counter)
  private final Set<ListenerEntry> listenerEntries;                 // modification listeners, sorted by priority
  private final Map<String, TableEntry> tableEntriesByName;         // the table entries mapped by (table)name
  private final Map<Long, TableEntry> tableEntriesById;             // the table entries mapped by ID
  private volatile TableEntry masterEntry;                          // master serial entry
  private final Random timeRandom;                                  // execution time randomizer
  private final Map<ModificationListener,DelayedEvent> delayedListeners;   // map of delayed listeners (tracker thread only)
  private final Set<DelayedEvent> delayedEvents;                    // delayed events (tracker thread only)
  private long delayedSleepInterval;                                // != 0 if this is the next max. sleep interval
  private DbModification masterModification;                        // po to access the master serial
  private boolean localClientMode;                                  // true if the local session and not a server
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
    listenerEntries = new ConcurrentSkipListSet<>();    // sorted like a TreeSet, but iterable while being modified
    delayedListeners = new HashMap<>();
    delayedEvents = new TreeSet<>();
    tableEntriesByName = new ConcurrentHashMap<>();
    tableEntriesById = new ConcurrentHashMap<>();
    timeRandom = new Random();
    shutdownRunnables = new ArrayList<>();
    shutdownRunnablesLock = new Object();
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
      throw new PersistenceException("no session configured for " + this);
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
    countersById.clear();
    countersByName.clear();
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
    if (listener.getNames() == null || listener.getNames().length == 0) {
      // unnamed master listener
      if (masterEntry == null) {
        configureMaster();
      }
      listenerEntries.add(new ListenerEntry(listener, masterEntry));
    }
    else  {
      // named listener(s)
      for (String name: listener.getNames()) {
        TableEntry entry = tableEntriesByName.get(name);
        if (entry == null) {
          configureName(name);
          entry = tableEntriesByName.get(name);
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
      countersByName.clear();
      countersById.clear();
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
    if (masterEntry == null) {
      configureMaster();
    }
    return masterEntry.serial;
  }

  /**
   * Gets the pair of id/serial for a given table name.<br>
   * Used in remote connections.
   *
   * @param tableName the table to lookup
   * @return the id/serial pair for the table name, never null
   */
  public IdSerialTuple getIdSerialForName(String tableName) {
    if (tableName == null) {
      throw new PersistenceException("tableName must be given");
    }
    TableEntry entry = tableEntriesByName.get(tableName);
    if (entry == null) {
      configureName(tableName);
      entry = tableEntriesByName.get(tableName);
    }
    return new IdSerialTuple(entry.id, entry.serial);
  }

  @Override
  public long getSerial(Class<?> clazz) {
    String name = SessionUtilities.getInstance().getTableName(clazz.getName());
    if (name == null) {
      // not annotated with @TableName: try to instantiate and get from AbstractDbObject
      AbstractDbObject<?> obj = (AbstractDbObject<?>) DbUtilities.getInstance().createObject((Class<?>) clazz);
      if (obj != null) {
        name = obj.getTableName();
      }
    }
    return getSerial(name);
  }

  @Override
  public long getSerial(String name) {
    return getIdSerialForName(name).getSerial();
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
   * @return the table serial (guessed lowest, could be higher already)
   */
  @Override
  public long countModification(Session session, String tableName) {
    ModificationTally counter = getCounter(tableName);
    // put pending count
    counter.countPending(session);
    if (localClientMode || !isAlive()) {
      // immediate mode or no poll() because not alive -> persist now
      counter.performPendingCount();
    }
    // else optimize:
    // return the guessed minimum table serial (pending count is done in next poll())
    return counter.getLatestSerial();
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
  }

  /**
   * Invalidates the modification table and re-initializes all entries and counters.
   */
  @Override
  public void invalidate() {
    AbstractSessionTask task = new AbstractSessionTask() {
      @Override
      public void run() {
        countersByName.clear();
        countersById.clear();
        if (masterEntry != null) {
          masterEntry.serial = 1;
        }
        Session session = getSession();
        if (session instanceof Db db && !db.isRemote()) {
          DbModification.initializeModificationTable(db);
        }
        refreshTableEntries();
      }
    };

    if (isTaskDispatcherThread() || !isAlive()) {
      task.setSession(getSession());
      task.run();
    }
    else  {
      addTaskAndWait(task);
    }
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
   * handler will be invoked (if any), before returning the serial.
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
   * @return the master serial
   */
  protected long selectMasterSerial() {
    Db session = getSession();
    if (session.isRemote()) {
      return extractMasterSerial(((DbModificationTrackerRemoteDelegate)
                                    session.getRemoteDelegate(getRemoteDelegateId())).selectMasterSerial());
    }
    else {
      return masterModification.selectMasterSerial();
    }
  }

  /**
   * Selects id/serial/name tuples from the remote server's tracker.<br>
   * The method is provided to reduce the number of roundtrips during startup of the client application.
   * <p>
   * Throws a {@link PersistenceException} if invoked on a local session.
   *
   * @return the tuples
   */
  protected List<IdSerialNameTuple> selectAllIdSerialNames() {
    Db session = getSession();
    if (session.isRemote()) {
      return ((DbModificationTrackerRemoteDelegate) session.getRemoteDelegate(getRemoteDelegateId())).selectAllIdSerialNames();
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
      return ((DbModificationTrackerRemoteDelegate) session.getRemoteDelegate(getRemoteDelegateId())).selectAllIdSerials();
    }
    else {
      return masterModification.selectAllIdSerial();
    }
  }

  /**
   * Selects the pair of id/serial for a given table name.
   *
   * @param tableName the table to lookup
   * @return the id/serial pair for the table name, null table not configured so far
   */
  protected IdSerialTuple selectIdSerialForName(String tableName) {
    Db session = getSession();
    if (session.isRemote()) {
      IdSerialTuple tuple = getRemoteIdSerial(tableName);   // try fast lookup first (to save a roundtrip)
      if (tuple == null) {
        // not in the initial map? -> get it from the remote server (and optionally create it)
        tuple = ((DbModificationTrackerRemoteDelegate) session.getRemoteDelegate(getRemoteDelegateId())).selectIdSerialForName(tableName);
      }
      return tuple;
    }
    else {
      return masterModification.selectIdSerial(tableName);
    }
  }

  /**
   * Flushes all pending modification counts to the database.
   */
  private void performPendingCounts() {
    for (ModificationTally counter: countersByName.values()) {
      counter.performPendingCount();
    }
  }

  /**
   * Checks for changes in the modification table.
   * <p>
   * Always invoked from within the tracker thread, so it's ok to use
   * the thread's session.
   */
  private void poll() {

    boolean isLocalSession = !getSession().isRemote();
    if (isLocalSession) {
      performPendingCounts();
    }
    else if (remoteConfigPending) {
      // do that only once!
      ((DbModificationTrackerRemoteDelegate) getSession().getRemoteDelegate(getRemoteDelegateId())).configureRemoteSession();
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
    for (TableEntry entry: tableEntriesByName.values()) {
      if (!getSession().isRemote()) {
        getCounter(entry.tableName);
      }
      IdSerialTuple idSer = selectIdSerialForName(entry.tableName);
      if (idSer != null) {
        entry.id = idSer.getId();
        entry.serial = idSer.getSerial();
      }
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
    Task task = new AbstractSessionTask() {
      @Override
      public void run() {
        ModificationTally counter = new ModificationTally(DbModificationTracker.this, tableName);
        IdSerialTuple idSer = selectIdSerialForName(tableName);
        long lastSerial = idSer == null ? 0 : idSer.getSerial();
        counter.setLastSerial(lastSerial);
        countersByName.put(tableName, counter);
        countersById.put(counter.getId(), counter);
      }
    };
    if (isTaskDispatcherThread() || !isAlive()) {
      task.run();
    }
    else  {
      addTaskAndWait(task);
    }
    ModificationTally tally = countersByName.get(tableName);
    if (tally == null) {
      throw new PersistenceException("configuration of counter failed for " + tableName);
    }
    return tally;
  }

  /**
   * Configures the master entry.
   */
  private void configureMaster() {
    Task task = new AbstractSessionTask() {
      @Override
      public void run() {
        long serial = selectMasterSerial();
        masterEntry = new TableEntry(null, 0, serial);
      }
    };

    if (isTaskDispatcherThread() || !isAlive()) {
      task.run();
    }
    else  {
      addTaskAndWait(task);
    }
  }

  /**
   * Configures a named entry.
   *
   * @param name the name
   */
  private void configureName(String name) {
    Task task = new AbstractSessionTask() {
      @Override
      public void run() {
        IdSerialTuple idSer = selectIdSerialForName(name);
        if (idSer == null) {
          // missing: create it
          getCounter(name).addToModificationTable();
          idSer = selectIdSerialForName(name);
        }
        TableEntry entry = new TableEntry(name, idSer.getId(), idSer.getSerial());
        tableEntriesByName.put(name, entry);
        tableEntriesById.put(idSer.getId(), entry);
      }
    };

    if (isTaskDispatcherThread() || !isAlive()) {
      task.run();
    }
    else  {
      addTaskAndWait(task);
    }
  }

  /**
   * runs the shutdown runnables
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
        // log but don't cause MD to stop
        LOGGER.logStacktrace(Level.SEVERE, ex);
      }
    }
  }

}
