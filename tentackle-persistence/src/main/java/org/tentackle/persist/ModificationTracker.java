/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.persist;

import java.rmi.RemoteException;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.tentackle.log.Logger;
import org.tentackle.log.Logger.Level;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.pdo.AbstractSessionTask;
import org.tentackle.pdo.DefaultSessionTaskDispatcher;
import org.tentackle.pdo.ExclusiveSessionProvider;
import org.tentackle.pdo.ModificationEvent;
import org.tentackle.pdo.ModificationEventDetail;
import org.tentackle.pdo.ModificationListener;
import org.tentackle.pdo.PersistenceException;
import org.tentackle.pdo.Session;
import org.tentackle.persist.rmi.ModificationTrackerRemoteDelegate;
import org.tentackle.task.Task;




/**
 * The modification tracker for the tentackle persistence layer.
 *
 * @author harald
 */
public class ModificationTracker extends DefaultSessionTaskDispatcher implements ExclusiveSessionProvider {

  /**
   * default polling interval in milliseconds.
   */
  public static long defaultPollingInterval = 2000;


  /**
   * the logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(ModificationTracker.class);



  /** listener key instance counter. */
  private static final AtomicInteger LISTENER_KEY_INSTANCE_COUNTER = new AtomicInteger();


  /**
   * Gets the remote delegate.
   *
   * @return the delegate
   */
  private static int getRemoteDelegateId() {
    if (delegateId == 0)  {
      delegateId = Db.prepareRemoteDelegate(ModificationTracker.class);
    }
    return delegateId;
  }

  private static int delegateId;


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
     * Returns whether this is the master listener.
     *
     * @return true if master listener
     */
    private boolean isMaster() {
      return tableEntry.isMaster();
    }

    /**
     * Creates a modification detail.
     *
     * @return the detail
     */
    private ModificationEventDetail createDetail() {
      return new ModificationEventDetail(tableEntry.tableName, tableEntry.serial);
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
      if (obj == null) {
        return Integer.MAX_VALUE;
      }
      int rv = priority - obj.priority;
      if (rv == 0) {
        rv = instanceNumber - obj.instanceNumber;
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
     * @param tableName the tablename of the class, null if master serial
     * @param id the tablename id, 0 if master serial
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
        buf.append("tablename='");
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




  private final List<Runnable> shutdownRunnables;                   // runnables to be run once when thread is stopped
  private final Map<String, ModificationTally> countersByName;      // map of modification counters (tablename:counter)
  private final Map<Long, ModificationTally> countersById;          // map of modification counters (id:counter)
  private final Set<ListenerEntry> listenerEntries;                 // modification listeners
  private final Map<String, TableEntry> tableEntriesByName;         // the table entries mapped by (table)name
  private final Map<Long, TableEntry> tableEntriesById;             // the table entries mapped by ID
  private volatile TableEntry masterEntry;                          // master serial entry
  private final Random timeRandom;                                  // execution time randomizer
  private final Map<ModificationListener,DelayedEvent> delayedListeners;   // map of delayed listeners
  private final Set<DelayedEvent> delayedEvents;                    // delayed events
  private long delayedSleepInterval;                                // != 0 if this is the next max. sleep interval
  private DbModification masterModification;                        // po to access the master serial


  /**
   * Creates a tracker to be configured.
   *
   * @param name the thread's name
   */
  public ModificationTracker(String name) {
    super(name);

    /**
     * This is not a daemon thread!
     * As long as this thread is running the JVM will not terminate.
     */
    setDaemon(false);

    setSleepInterval(defaultPollingInterval);

    countersByName = new ConcurrentHashMap<>();
    countersById = new HashMap<>();
    listenerEntries = new TreeSet<>();
    delayedListeners = new HashMap<>();
    delayedEvents = new TreeSet<>();
    tableEntriesByName = new ConcurrentHashMap<>();
    tableEntriesById = new ConcurrentHashMap<>();
    timeRandom = new Random();
    shutdownRunnables = new ArrayList<>();
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
    // check if modification table is initialized
    try {
      masterModification = new DbModification((Db) session);
      selectMasterSerial();
    }
    catch (PersistenceException ex) {
      DbModification.initializeModificationTable(getSession());
    }
  }



  @Override
  protected void cleanup() {
    super.cleanup();
    invokeShutdownRunnables();
  }

  /**
   * Adds a shutdown runnable.<br>
   * The runnables will be executed on termination of this tracker.
   *
   * @param runnable the runnable
   */
  public synchronized void addShutdownRunnable(Runnable runnable) {
    shutdownRunnables.add(runnable);
  }

  /**
   * Removes a shutdown runnable.
   * <p>
   * Notice that the first occurrence of the runnable is removed.
   *
   * @param runnable the runnable
   * @return true if runnable removed, false if no such runnable registered
   */
  public synchronized boolean removeShutdownRunnable(Runnable runnable) {
    return shutdownRunnables.remove(runnable);
  }


  /**
   * Adds a modification listener.
   *
   * @param listener the listener to add
   */
  public void addModificationListener(ModificationListener listener) {
    if (listener.getNames() == null || listener.getNames().length == 0) {
      // unnamed master listener
      if (masterEntry == null) {
        configureMaster();
      }
      synchronized(this) {
        listenerEntries.add(new ListenerEntry(listener, masterEntry));
      }
    }
    else  {
      // named listener(s)
      for (String name: listener.getNames()) {
        TableEntry entry = tableEntriesByName.get(name);
        if (entry == null) {
          configureName(name);
          entry = tableEntriesByName.get(name);
        }
        synchronized (this) {
          listenerEntries.add(new ListenerEntry(listener, entry));
        }
      }
    }
  }

  /**
   * Removes a modification listener.
   *
   * @param listener the listener to remove
   * @return true if listener removed
   */
  public synchronized boolean removeModificationListener(ModificationListener listener) {
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
   * Gets the pair of id/serial for a given tablename.<br>
   * Used in remote connections.
   *
   * @param tableName the table to lookup
   * @return the id/serial pair for the tablename, never null
   */
  public IdSerialTuple getIdSerialForName(String tableName) {
    TableEntry entry = tableEntriesByName.get(tableName);
    if (entry == null) {
      configureName(tableName);
      entry = tableEntriesByName.get(tableName);
    }
    return new IdSerialTuple(entry.id, entry.serial);
  }


  /**
   * Gets the serials of all monitored tables.<br>
   * Used in remote connections.
   *
   * @return the serials
   */
  public List<IdSerialTuple> getAllSerials() {
    List<IdSerialTuple> idSerList = new ArrayList<>();
    for (TableEntry entry: tableEntriesByName.values()) {
      idSerList.add(new IdSerialTuple(entry.id, entry.serial));
    }
    return idSerList;
  }


  /**
   * Counts the modification for a table.<br>
   * Used by the persistence layer to update the modification table.
   *
   * @param tableName the table name
   * @return the (guessed lowest) table serial (may be higher already)
   */
  public long countModification (String tableName) {
    long tableSerial = 0;
    if (isAlive()) {
      ModificationTally counter = getCounter(tableName);
      // put pending count
      counter.countPending();
      // return the guessed minimum table serial
      tableSerial = counter.getLatestSerial();
    }
    return tableSerial;
  }


  /**
   * Invalidates the modification table and re-initializes all entries and counters.
   */
  public void invalidate() {
    @SuppressWarnings("serial")
    Task task = new AbstractSessionTask() {
      @Override
      public void run() {
        countersByName.clear();
        countersById.clear();
        if (masterEntry != null) {
          masterEntry.serial = 1;
        }
        DbModification.initializeModificationTable(ModificationTracker.this.getSession());
        refreshTableEntries();
      }
    };

    if (isTaskDispatcherThread() || !isAlive()) {
      task.run();
    }
    else  {
      addTaskAndWait(task);
    }
  }


  @Override
  protected void lockInternal() {
    poll();
    invokeDelayedListeners();
    super.lockInternal();
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
   * Checks for changes in the modification table.
   * <p>
   * Always invoked from within the tracker thread, so it's ok to use
   * the thread's session.
   */
  private void poll() {

    // perform pending counts
    for (ModificationTally counter: countersByName.values()) {
      counter.performPendingCount();
    }

    long serial = selectMasterSerial();

    if (serial != getMasterSerial()) {

      if (serial < getMasterSerial()) {
        // new serial is less than current serial: force complete re-initialization
        invalidate();
      }

      // read all serials and create modification events
      Map<ModificationListener,Set<ModificationEventDetail>> detailMap = new HashMap<>();

      for (IdSerialTuple idSer: selectAllIdSerials()) {

        if (idSer.getId() > 0) {
          // update last serial of counter (if configured)
          ModificationTally counter = countersById.get(idSer.getId());
          if (counter != null) {
            counter.setLastSerial(idSer.getSerial());
          }
          // this updates also counters that don't have a listener
          // e.g. countModification invoked without a listener registered
        }

        TableEntry entry = tableEntriesById.get(idSer.getId());     // serial changed for entry
        if (entry != null) {                                        // no entry -> no listenerEntry
          for (ListenerEntry listenerEntry : listenerEntries) {
            if (listenerEntry.tableEntry.id == idSer.getId() && listenerEntry.tableEntry.serial != idSer.getSerial()) {
              // table entry for listener has changed
              LOGGER.fine("modification detected for {0}", listenerEntry.tableEntry);
              Set<ModificationEventDetail> details = detailMap.get(listenerEntry.listener);
              if (details == null) {
                // create a new one
                details = new HashSet<>();
                detailMap.put(listenerEntry.listener, details);
              }
              if (!listenerEntry.isMaster()) {
                getCounter(listenerEntry.tableEntry.tableName);   // configure counter if not yet done
                details.add(listenerEntry.createDetail());
              }
            }
          }
          entry.serial = idSer.getSerial();   // remember serial, even if no listener entry
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
      // else: more than mseconds left
    }
  }


  /**
   * Refreshes the serials and ids for all monitored tables.
   */
  private void refreshTableEntries() {
    for (TableEntry entry: tableEntriesByName.values()) {
      getCounter(entry.tableName);
      IdSerialTuple idSer = selectIdSerialForName(entry.tableName);
      if (idSer != null) {
        entry.id = idSer.getId();
        entry.serial = idSer.getSerial();
      }
    }
  }

  /**
   * Extracts the master serial from the master serial object.
   * <p>
   * Application can override this method to process the optional
   * application data.
   *
   * @param masterSerial the master serial object
   * @return the long serial
   */
  protected long extractMasterSerial(MasterSerial masterSerial) {
    return masterSerial.serial;
  }

  /**
   * Reads the master-serial from the database.
   * @return the master serial
   */
  private long selectMasterSerial() {
   if (getSession().isRemote())  {
      try {
        return extractMasterSerial(((ModificationTrackerRemoteDelegate) getSession().
                getRemoteDelegate(getRemoteDelegateId())).selectMasterSerial());
      }
      catch (RemoteException e) {
        throw PersistenceException.createFromRemoteException(getSession(), e);
      }
    }
    else  {
      return masterModification.selectMasterSerial();
    }
  }


  /**
   * Selects id/serial for all monitored tables.
   *
   * @return the tuples
   */
  private List<IdSerialTuple> selectAllIdSerials() {
    if (getSession().isRemote())  {
      try {
        return ((ModificationTrackerRemoteDelegate) getSession().getRemoteDelegate(getRemoteDelegateId())).selectAllSerials();
      }
      catch (RemoteException e) {
        throw PersistenceException.createFromRemoteException(getSession(), e);
      }
    }
    else  {
      return masterModification.selectAllIdSerial();
    }
  }


  /**
   * Selects the pair of id/serial for a given tablename.
   *
   * @param tableName the table to lookup
   * @return the id/serial pair for the tablename, null table not configured so far
   */
  private IdSerialTuple selectIdSerialForName(String tableName) {

    if (getSession().isRemote())  {
      try {
        return ((ModificationTrackerRemoteDelegate) getSession().getRemoteDelegate(getRemoteDelegateId())).selectIdSerialForName(tableName);
      }
      catch (RemoteException e) {
        throw PersistenceException.createFromRemoteException(getSession(), e);
      }
    }
    else  {
      return masterModification.selectIdSerial(tableName);
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
    @SuppressWarnings("serial")
    Task task = new AbstractSessionTask() {
      @Override
      public void run() {
        ModificationTally counter = new ModificationTally(ModificationTracker.this.getSession(), tableName);
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
    return countersByName.get(tableName);
  }



  /**
   * Configures the master entry.
   */
  private void configureMaster() {
    @SuppressWarnings("serial")
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
    @SuppressWarnings("serial")
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
    for (Runnable r: shutdownRunnables) {
      try {
        r.run();
      }
      catch (Exception ex) {
        // log but don't cause MD to stop
        LOGGER.logStacktrace(Level.SEVERE, ex);
      }
    }
  }

}
