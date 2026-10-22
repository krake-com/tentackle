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
import org.tentackle.common.InterruptedRuntimeException;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.common.Timestamp;
import org.tentackle.log.Logger;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A slot holding a poolable managed by a pool.<br>
 * While the poolable is lent, the slot holds only a weak reference to it,
 * so that lent but never returned poolables can be detected via {@link #isUnreferenced()}.
 *
 * @param <T> the type of poolable resources
 */
public abstract class AbstractPoolSlot<T extends Poolable<T>> implements PoolSlot<T> {

  private static final Logger LOGGER = Logger.get(AbstractPoolSlot.class);

  /**
   * Execute the possibly blocking closing operation in virtual threads.<br>
   * This is possible without pinning even with "synchronized" since Java 24.
   */
  private static final Executor THREAD_POOL = Executors.newThreadPerTaskExecutor(
    Thread.ofVirtual()
          .name("Pool Slot Closing VThread ", 1)
          .factory());


  private final Pool<T> pool;                   // the pool
  private final int slotNumber;                 // the slot number
  private T poolable;                           // the managed poolable, null if currently lent
  private final String slotStr;                 // the toString value of this slot
  private final WeakReference<T> poolableRef;   // weak reference to detect unreferenced lent poolable instances
  private long usedSince;                       // last lend time, 0 if in pool
  private String usingThreadStr;                // the lending thread string (not the reference!), null if in pool
  private String mdcStr;                        // mapped diagnostic context string
  private long unusedSince;                     // last return time, 0 if lent
  private long firstUse;                        // epochal time of first use


  /**
   * Creates a slot.
   *
   * @param pool the pool
   * @param slotNumber the slot number within the pool (== pool-ID - 1)
   * @param poolable the managed poolable
   * @throws NullPointerException if pool or poolable is null
   */
  public AbstractPoolSlot(Pool<T> pool, int slotNumber, T poolable) {
    this.pool = Objects.requireNonNull(pool);
    this.poolable = Objects.requireNonNull(poolable);
    this.slotNumber = slotNumber;

    slotStr = createSlotStr(pool, slotNumber, poolable);
    unusedSince = System.currentTimeMillis();
    poolableRef = new WeakReference<>(poolable);

    poolable.setPool(pool);
    poolable.setPoolId(0);
  }

  @Override
  public Pool<T> getPool() {
    return pool;
  }

  @Override
  public int getSlotNumber() {
    return slotNumber;
  }

  @Override
  public T getPoolable() {
    return poolable;
  }

  @Override
  public String toString() {
    return slotStr;
  }

  @Override
  public T getReferencedPoolable() {
    return poolableRef.get();
  }

  @Override
  public long getFirstUse() {
    return firstUse;
  }

  @Override
  public long getUsedSince() {
    return usedSince;
  }

  @Override
  public long getUnusedSince() {
    return unusedSince;
  }

  @Override
  public String getUsingThreadStr() {
    return usingThreadStr;
  }

  @Override
  public String getMdcStr() {
    return mdcStr;
  }

  @Override
  public void close(long timeoutMillis) {
    final T poolableToClose = poolableRef.get();
    // poolableRef.get() may be null because already garbage collected
    if (poolableToClose != null) {
      poolableToClose.setPool(null);
      poolableToClose.setPoolId(-1);
      if (poolableToClose.isOpen()) {
        CountDownLatch latch = timeoutMillis > 0 ? new CountDownLatch(1) : null;
        THREAD_POOL.execute(() -> {    // non-blocking!
          try {
            poolableToClose.close();
          }
          catch (Exception rex) {
            LOGGER.warning("closing " + slotStr + " failed", rex);
          }
          if (latch != null) {
            latch.countDown();
          }
        });
        if (latch != null) {
          try {
            if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
              LOGGER.warning("closing " + slotStr + " timed out");
            }
          }
          catch (InterruptedException ex) {
            throw new InterruptedRuntimeException(ex);
          }
        }
      }
    }
    // poolable may already be null because currently lent
    poolable = null;
  }

  @Override
  public void use(Thread usingThread) {
    if (poolable == null) {
      throw createException("unexpected loss of reference to " + slotStr +
                            " (last returned by " + usingThreadStr + " since " + new Timestamp(unusedSince) + ")");
    }
    usedSince = System.currentTimeMillis();
    if (firstUse == 0) {
      firstUse = usedSince;
    }
    unusedSince = 0;
    usingThreadStr = usingThread.toString();
    mdcStr = LOGGER.getMappedDiagnosticContext().toString();
    // keep only the weak reference
    poolable = null;
  }

  @Override
  public void unUse(T poolable) {
    // switch back to hard reference
    this.poolable = poolableRef.get();
    if (this.poolable == null) {
      throw createException("unexpected loss of reference to " + slotStr +
                            " (last use by " + usingThreadStr + " since " + new Timestamp(usedSince) + ")");
    }
    if (this.poolable != poolable) {
      this.poolable = null;
      throw createException("attempt to un-use " + poolable + " in wrong slot " + slotStr +
                            " (last use by " + usingThreadStr + " since " + new Timestamp(usedSince) + ")");
    }
    unusedSince = System.currentTimeMillis();
    usedSince = 0;
    usingThreadStr = null;
    mdcStr = null;
  }

  @Override
  public boolean isUnreferenced() {
    return poolableRef.get() == null;
  }

  @Override
  public long getIdleMinutes(long currentTimeMillis) {
    return firstUse == 0 || unusedSince == 0 ? 0 : (currentTimeMillis - unusedSince) / Constants.MINUTE_MS;
  }

  @Override
  public long getUsedMinutes(long currentTimeMillis) {
    return firstUse == 0 ? 0 : (currentTimeMillis - firstUse) / Constants.MINUTE_MS;
  }


  /**
   * Creates the string representation of this slot.
   *
   * @param pool the pool
   * @param slotNumber the slot number
   * @param poolable the poolable
   * @return the string representation
   */
  protected String createSlotStr(Pool<T> pool, int slotNumber, T poolable) {
    return pool + "#" + slotNumber + "|" + poolable;
  }

  /**
   * Creates the exception thrown by this slot.
   *
   * @param msg the message
   * @return the exception
   */
  protected abstract TentackleRuntimeException createException(String msg);

}
