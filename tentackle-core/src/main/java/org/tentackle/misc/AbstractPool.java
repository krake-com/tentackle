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

package org.tentackle.misc;

import org.tentackle.common.Constants;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.common.Timestamp;
import org.tentackle.log.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Base implementation of a pool managing {@link Poolable} resources.<br>
 * It allows min/max sizes, fixed increments, and timeouts for unused and long-used instances.
 * <p>
 * The resources are created lazily on the first {@link #get()}, which also registers the pool
 * with the {@link PoolTimeoutThread}.
 *
 * @param <T> the type of poolable resources
 */
public abstract class AbstractPool<T extends Poolable<T>> implements Pool<T> {

  private static final Logger LOGGER = Logger.get(AbstractPool.class);

  /** default timeout per resource to shut down. */
  private static final long SHUTDOWN_TIMEOUT_MILLIS = Constants.SECOND_MS;

  private final String name;                    // the pool's name
  private final int initialSize;                // initial size of the pool
  private int incrementSize;                    // increment size
  private int minimumSize;                      // min pool size
  private int maximumSize;                      // max pool size
  private long idleMinutes;                     // idle timeout in [minutes]
  private long usageMinutes;                    // usage timeout in [minutes]

  private long shutdownTimeout;                 // shutdown timeout in milliseconds
  private boolean initialized;                  // true if the pool is initialized
  private PoolSlot<T>[] slots;                  // the pooled resource slots
  private int[] freeList;                       // indexes of free slots in 'slots'
  private int freeCount;                        // number of indexes in 'freeList'
  private int[] unusedList;                     // indexes of unused slots in 'slots'
  private int unusedCount;                      // number of indexes in 'unusedList'


  /**
   * Creates a pool.
   *
   * @param name the name of the pool
   * @param initialSize the initial pool size
   * @param incrementSize the number of resource instances to enlarge the pool if all in use
   * @param minimumSize the minimum number of resource instances to keep in the pool, &lt; 0 if auto shutdown pool if all resources timed out
   * @param maximumSize the maximum number of resource instances, 0 = unlimited
   * @param idleMinutes the idle timeout in minutes to close unused resource instances, 0 = unlimited
   * @param usageMinutes the max. used time in minutes, 0 = unlimited
   * @throws IllegalArgumentException if the size parameters are inconsistent
   * @throws NullPointerException if name is null
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public AbstractPool(String name, int initialSize, int incrementSize, int minimumSize, int maximumSize, long idleMinutes, long usageMinutes) {

    if (maximumSize > 0 && (maximumSize < initialSize || maximumSize < minimumSize) ||
        incrementSize < 1 ||
        initialSize < 0) {
      throw new IllegalArgumentException("illegal size parameters");
    }

    this.name = Objects.requireNonNull(name, "name");
    this.initialSize = initialSize;
    this.incrementSize = incrementSize;
    this.minimumSize = minimumSize;
    this.maximumSize = maximumSize;
    this.idleMinutes = idleMinutes;
    this.usageMinutes = usageMinutes;

    shutdownTimeout = SHUTDOWN_TIMEOUT_MILLIS;
    slots = new PoolSlot[initialSize];
    freeList = new int[initialSize];
    unusedList = new int[initialSize];
    for (int i = 0; i < initialSize; i++) {
      slots[i] = null;
      freeList[freeCount++] = i;
      unusedList[i] = -1;
    }

    // the slots are created when get() is invoked for the first time
  }


  @Override
  public String toString() {
    return name;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public int getInitialSize() {
    return initialSize;
  }

  @Override
  public synchronized int getIncrementSize() {
    return incrementSize;
  }

  /**
   * Sets the minimum increment to enlarge the pool.
   *
   * @param incrementSize the minimum increment (at least 1)
   * @throws IllegalArgumentException if incrementSize &lt; 1
   */
  public synchronized void setIncrementSize(int incrementSize) {
    if (incrementSize < 1) {
      throw new IllegalArgumentException("increment size must be at least 1");
    }
    this.incrementSize = incrementSize;
  }

  @Override
  public synchronized int getMinimumSize() {
    return minimumSize;
  }

  /**
   * Sets the minimum size.<br>
   * Setting the minimum size to a value &lt; 0 will shut down the pool
   * as soon as all resources are closed (usually due to time out of unused resources).
   *
   * @param minimumSize the minimum size
   */
  public synchronized void setMinimumSize(int minimumSize) {
    this.minimumSize = minimumSize;
  }

  @Override
  public synchronized int getMaximumSize() {
    return maximumSize;
  }

  /**
   * Sets the maximum size.
   *
   * @param maximumSize the maximum size, 0 = unlimited
   * @throws IllegalArgumentException if maximumSize is lower than the minimum size
   */
  public synchronized void setMaximumSize(int maximumSize) {
    if (maximumSize > 0 && maximumSize < minimumSize) {
      throw new IllegalArgumentException("maximum size must not be lower than minsize=" + minimumSize);
    }
    this.maximumSize = maximumSize;
  }

  @Override
  public synchronized long getIdleMinutes() {
    return idleMinutes;
  }

  /**
   * Sets the idle minutes.<br>
   * Resources are closed if unused for given minutes.
   *
   * @param idleMinutes the idle timeout, 0 if unlimited
   */
  public synchronized void setIdleMinutes(long idleMinutes) {
    this.idleMinutes = idleMinutes;
  }

  @Override
  public synchronized long getUsageMinutes() {
    return usageMinutes;
  }

  /**
   * Sets the maximum usage minutes.<br>
   * Resources are closed when the given time has elapsed since their first use,
   * regardless of whether they were idle or not.
   *
   * @param usageMinutes the usage timeout, 0 if unlimited
   */
  public synchronized void setUsageMinutes(long usageMinutes) {
    this.usageMinutes = usageMinutes;
  }

  @Override
  public long getShutdownTimeout() {
    return shutdownTimeout;
  }

  /**
   * Sets the timeout to wait for closing resources on shutdown.<br>
   * The timeout applies to each resource individually.
   *
   * @param shutdownTimeout the timeout in milliseconds, &le; 0 for asynchronous non-blocking operation
   */
  public void setShutdownTimeout(long shutdownTimeout) {
    this.shutdownTimeout = shutdownTimeout;
  }

  @Override
  public void shutdown() {
    PoolSlot<T>[] slotsToClose;
    long timeout;
    synchronized (this) {
      assertNotShutdown();
      LOGGER.info("shutting down pool {0}", this);
      slotsToClose = slots;
      timeout = shutdownTimeout;
      // mark the pool shut down before closing: get() and put() must not hand out resources anymore
      slots = null;
      freeList = null;
      unusedList = null;
    }
    /*
     * Close the resources without holding the pool's monitor.
     * close() waits for the closing operation of each resource, and closing a resource may
     * well need the pool again. Holding the monitor here would block that until the timeout
     * struck, leaving the resource unclosed - and would stall the shared PoolTimeoutThread,
     * which invokes this via monitor() and services all other pools as well.
     */
    for (PoolSlot<T> slot: slotsToClose) {
      if (slot != null) {
        slot.close(timeout);
      }
    }
  }

  @Override
  public synchronized boolean isShutdown() {
    return slots == null;
  }

  @Override
  public synchronized int getSize() {
    return slots == null ? 0 : slots.length - freeCount;
  }

  @Override
  public synchronized T get() {
    assertNotShutdown();
    if (!initialized) {
      // the pool is empty at the beginning: create instances
      createSlots(initialSize);
      initialized = true;
      PoolTimeoutThread.register(this);   // start monitoring by timeout thread
    }
    // A resource may die while sitting unused in the pool, without the pool being notified:
    // the peer closed it, a protocol-level idle timeout struck, the network went away, ...
    // Such a resource must be dropped and replaced instead of being handed out.
    // Each loop either returns a resource or removes a stale one, so the number of
    // stale resources found is limited by the current pool size.
    int staleLimit = slots.length + 1;
    for (int stale = 0; ; stale++) {
      if (unusedCount == 0) {
        createSlots(incrementSize); // enlarge the pool (will throw Exception if the pool is exhausted)
      }
      int poolId = unusedList[--unusedCount];
      PoolSlot<T> poolSlot = slots[poolId];
      if (poolSlot == null) {
        throw createException("pool " + this + ": poolable with ID=" + poolId + " cleared unexpectedly");
      }
      T poolable = poolSlot.getPoolable();
      if (poolable == null) {
        throw createException("pool " + this + ": poolable with ID=" + poolId + " still in use unexpectedly");
      }
      if (!poolable.isOpen()) {
        if (stale >= staleLimit) {
          // a freshly created resource must be open: give up rather than loop forever
          throw createException(poolable, "pool " + this + ": no usable resource after " + stale + " attempts");
        }
        LOGGER.info("pool {0}: unused poolable {1} is not open anymore -> removed from pool", this, poolSlot);
        removePoolable(poolable, poolId);
        continue;   // try the next unused resource, create a new one if there is none left
      }
      poolSlot.use(Thread.currentThread());
      poolable.setPoolId(poolId + 1);   // starting at 1
      LOGGER.fine("{0}: poolable {1} assigned to pool id {2}", this, poolable, poolId);
      return poolable;
    }
  }

  @Override
  public synchronized void put(T poolable) {
    if (poolable.getPool() != this) {
      throw createException(poolable, "resource is not managed by pool " + this);
    }
    if (isShutdown()) {
      if (poolable.isOpen()) {
        LOGGER.warning("pool " + this + " already shut down -> closing resource " + poolable);
        poolable.close();
      }
    }
    else {
      int poolId = poolable.getPoolId();
      // 0 = already returned to pool, -1 removed from pool, else not returned yet
      if (poolId < -1 || poolId > slots.length) {
        throw createException(poolable, "pool " + this + ": resource has invalid pool ID " + poolId);
      }
      if (poolable.isOpen()) {
        unusePoolable(poolable);
      }
      else {
        if (poolId > 0) { // if not returned to pool yet
          removePoolable(poolable, poolId - 1);
          LOGGER.info("pool {0}: returned closed resource {1} removed from pool", this, poolable);
        }
        // else: not an error to return a closed resource more than once
      }
    }
  }

  @Override
  public void monitor() {
    boolean shutdownPool;

    synchronized (this) {
      if (isShutdown()) {
        return;   // shut down concurrently: nothing left to monitor
      }
      shutdownPool = monitorSlots();
    }

    if (shutdownPool && !isShutdown()) {
      // outside the monitor: shutdown() waits for the resources to be closed
      shutdown();
    }
  }

  /**
   * Logs a summary of the used slots per thread.<br>
   * Provided primarily for debugging purposes and in case of resource leaks.
   *
   * @param level the log level to use
   */
  public void logSlotCountPerThread(Logger.Level level) {
    StringBuilder buf = new StringBuilder();
    buf.append("pool ").append(this).append(", used slots:");
    for (Map.Entry<String, Integer> entry : getSlotCountPerThread()) {
      buf.append('\n').append(entry.getKey()).append(": ").append(entry.getValue());
    }
    LOGGER.log(level, buf.toString(), (Throwable) null);
  }

  /**
   * Gets the number of used slots per thread.<br>
   * Provided primarily for debugging purposes and in case of resource leaks.
   *
   * @return the sorted list of the number of used slots per thread, highest slot count first,
   *         empty if no slot is in use or the pool is shut down
   */
  public List<Map.Entry<String, Integer>> getSlotCountPerThread() {
    Map<String, Integer> slotCounts = new HashMap<>();  // slots used per thread
    synchronized (this) {
      if (slots != null) {    // null if shut down
        for (PoolSlot<T> slot : slots) {
          String threadName = slot == null ? null : slot.getUsingThreadStr();
          if (threadName != null) {   // null if in pool
            slotCounts.merge(threadName, 1, Integer::sum);
          }
        }
      }
    }
    // sort by count (highest first)
    List<Map.Entry<String, Integer>> sorted = new ArrayList<>(slotCounts.entrySet());
    sorted.sort((o1, o2) -> Integer.compare(o2.getValue(), o1.getValue()));
    return sorted;
  }


  /**
   * Determines whether the given slot can be removed from the pool.<br>
   * By default, a slot can be removed if its resource is not in use.
   *
   * @param slot the slot with its resource
   * @return true if it can be removed
   */
  protected boolean isSlotRemovable(PoolSlot<T> slot) {
    return slot.getPoolable() != null;   // must not be lent!
  }

  /**
   * Creates new slots of resources.<br>
   * The number of created slots is at least 1, but may be less than requested if the maximum
   * pool size is reached.
   *
   * @param num the number of slots to add to the pool
   * @return the number of slots created
   * @throws TentackleRuntimeException if the pool is exhausted and the maximum pool size is reached
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  protected int createSlots(int num) {
    if (num > freeCount) {
      // enlarge arrays
      int nSize = slots.length + num - freeCount;
      if (maximumSize > 0 && nSize > maximumSize) {
        nSize = maximumSize;
      }
      if (nSize <= slots.length) {
        TentackleRuntimeException pex = createException("cannot create more resource instances, max. pool size " + maximumSize + " reached");
        pex.setTemporary(true);   // may go away if just a misconfiguration, hence temporary
        logSlotCountPerThread(Logger.Level.WARNING);   // could be a sign of a resource leak as well, hence log it
        throw pex;
      }
      PoolSlot<T>[] nPool  = new PoolSlot[nSize];
      int[] nFreeList   = new int[nSize];
      int[] nUnusedList = new int[nSize];
      System.arraycopy(slots, 0, nPool, 0, slots.length);
      System.arraycopy(freeList, 0, nFreeList, 0, slots.length);
      System.arraycopy(unusedList, 0, nUnusedList, 0, slots.length);
      for (int i = slots.length; i < nSize; i++) {
        nPool[i] = null;
        nFreeList[freeCount++] = i;
        nUnusedList[i] = -1;
      }
      slots = nPool;
      freeList = nFreeList;
      unusedList = nUnusedList;
    }

    // freeCount is at least 1: get from freelist
    int created = 0;
    while (num > 0 && freeCount > 0) {
      int slotNumber = freeList[freeCount - 1];  // no --freeCount because new PooledResource() may throw exceptions
      slots[slotNumber] = createSlot(slotNumber);
      created++;
      freeCount--;
      unusedList[unusedCount++] = slotNumber;
      num--;
    }

    // now we have at least 1 unused resource instances
    return created;
  }

  /**
   * Brings the pool back to its minimum size.
   * <p>
   * Invoked by {@link #monitor()} with the pool's monitor held, i.e., usually from the
   * {@link PoolTimeoutThread} all pools share. The default implementation creates the missing slots.
   * A pool whose resources cannot be created without blocking for long &mdash; because they depend on
   * something that is gone and has to be re-established first &mdash; should override this and leave the
   * pool below its minimum size instead: {@link #get()} creates the resources as soon as they are needed.
   *
   * @param num the number of slots missing
   */
  protected void restoreMinimumSize(int num) {
    createSlots(num);
  }

  /**
   * Closes a poolable and removes it from the pool.<br>
   * Since the closing operation is executed asynchronously,
   * there is no guarantee that the resource is really closed.
   * But more importantly, this does not block the caller.
   *
   * @param poolable the poolable to remove, null to remove whatever the slot holds
   * @param slotIndex the slot index
   * @throws TentackleRuntimeException if the poolable does not match the one in the slot
   */
  protected void removePoolable(T poolable, int slotIndex) {
    PoolSlot<T> slot = slots[slotIndex];
    T poolableRef = slot.getReferencedPoolable();    // poolableRef may be null because currently lent
    if (poolableRef != null) {
      if (poolableRef != poolable && poolable != null) {
        throw createException(poolable + " to remove does not match " + poolableRef + " in pool '" + this + "' at index " + slotIndex);
      }
      slot.close(0);   // this is a non-blocking call!
    }
    // remove from the pool immediately, even if the closing operation is still running
    removeSlotIndex(slotIndex);
  }

  /**
   * Removes the slot index from the pool.
   *
   * @param slotIndex the pool's slot index
   */
  protected void removeSlotIndex(int slotIndex) {
    slots[slotIndex] = null;
    freeList[freeCount++] = slotIndex;  // add to freelist

    // check if the index was in the unused list.
    // If so, remove it.
    for (int i=0; i < unusedCount; i++) {
      if (unusedList[i] == slotIndex) {
        // found:
        System.arraycopy(unusedList, i + 1, unusedList, i, unusedCount - (i + 1));
        unusedCount--;
        break;
      }
    }
  }


  /**
   * Closes timed out resources and re-establishes the minimum pool size.<br>
   * Must be invoked with the pool's monitor held.
   *
   * @return true if the pool should be shut down
   */
  private boolean monitorSlots() {
    long currentMillis = System.currentTimeMillis();

    int i = 0;    // start with the oldest unused
    while (i < unusedCount) {
      int index = unusedList[i];
      PoolSlot<T> slot = slots[index];
      if (isSlotRemovable(slot)) {
        boolean idleTimedOut = false;
        if (idleMinutes > 0 && getSize() > minimumSize) {
          long idle = slot.getIdleMinutes(currentMillis);
          idleTimedOut = idle >= idleMinutes;
          if (idleTimedOut) {
            LOGGER.info("{0} idle for {1} (max={2}) -> closed", slot, idle, idleMinutes);
          }
        }

        boolean usageTimedOut = false;
        if (usageMinutes > 0) {
          long used = slot.getUsedMinutes(currentMillis);
          usageTimedOut = used >= usageMinutes;
          if (usageTimedOut) {
            LOGGER.info("{0} used for {1} (max={2}) -> closed", slot, used, usageMinutes);
          }
        }

        if (idleTimedOut || usageTimedOut || slot.isObsolete()) {
          // removePoolable drops index i from the unused list and shifts the remaining
          // entries left, so the next slot moves into i: don't advance i here
          removePoolable(slot.getPoolable(), index);
        }
        else {
          i++;
        }
      }
      else {
        // the slot must be kept (isSlotRemovable may be overridden to veto removal):
        // advance, otherwise this loop would never terminate and block the pool forever
        i++;
      }
    }

    // remove unreferenced resources from the pool
    i = 0;
    for (PoolSlot<T> slot : slots) {
      if (slot != null && slot.isUnreferenced()) {
        LOGGER.warning("unreferenced " + slot +
                       " last used by " + slot.getUsingThreadStr() +
                       " in MDC{" + slot.getMdcStr() +
                       "} since " + new Timestamp(slot.getUsedSince()) +
                       " -> removed from pool");
        removeSlotIndex(i);
        // the cleaner already closed or will close the resource physically
      }
      i++;
    }

    // check if we need to bring up some resources to keep the minimum size
    int size = getSize();
    if (minimumSize > 0 && size < minimumSize) {
      restoreMinimumSize(minimumSize - size);
      return false;
    }
    // minimum size < 0, and all resources closed -> shut down pool
    return size == 0 && minimumSize < 0;
  }

  /**
   * Marks a resource as unused and returned to the pool.
   *
   * @param poolable the resource
   */
  protected void unusePoolable(T poolable) {
    int poolId = poolable.getPoolId();
    if (poolId > 0) {
      LOGGER.fine("{0}: poolable {1} returned to pool, id {2}", this, poolable, poolId);
      poolId--;
      slots[poolId].unUse(poolable);
      unusedList[unusedCount++] = poolId;
      poolable.setPoolId(0);            // returned to the pool
    }
  }


  /**
   * Creates a new slot.
   *
   * @param slotNumber the slot number, starting at 0
   * @return the slot
   */
  protected abstract PoolSlot<T> createSlot(int slotNumber);

  /**
   * Creates an exception.
   *
   * @param msg the message
   * @return the exception
   */
  protected abstract TentackleRuntimeException createException(String msg);

  /**
   * Creates an exception.
   *
   * @param resource the resource instance that caused the exception
   * @param msg the message
   * @return the exception
   */
  protected abstract TentackleRuntimeException createException(T resource, String msg);


  /**
   * Asserts that this pool wasn't shut down.
   */
  private void assertNotShutdown() {
    if (isShutdown()) {
      throw createException("pool '" + this + "' already shut down");
    }
  }

}
