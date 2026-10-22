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

package org.tentackle.dbms;

import org.tentackle.common.StringHelper;
import org.tentackle.misc.PoolSlot;
import org.tentackle.session.MultiUserSessionPool;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionPool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of a {@link MultiUserSessionPool}.<br>
 * The pool is actually a pool of {@link DbPool}s, one for each {@link SessionInfo}.
 */
public class MultiUserDbPool implements MultiUserSessionPool {

  private final String name;
  private final ConnectionManager conMgr;
  private final int sessionGroupId;
  private final int maximumSize;
  private final int maximumTotalSize;
  private final long idleMinutes;
  private final long usageMinutes;

  private final Map<SessionInfo, MultiPoolImpl> poolMap;        // maintained pools per session info (user)
  private volatile boolean shutdown;                            // true if this pool has been shut down
  private final AtomicInteger size = new AtomicInteger();       // total number of sessions
  private final AtomicInteger count = new AtomicInteger();      // session pool ID

  /**
   * Creates a multi-user session pool.
   *
   * @param name the name of the pool, null if "default-multi"
   * @param conMgr the connection manager for local sessions, null if remote
   * @param sessionGroupId the session group ID to use for all sessions
   * @param maximumSize the maximum number of sessions per session info, 0 = unlimited
   * @param maximumTotalSize the maximum total number of sessions, 0 = unlimited
   * @param idleMinutes the idle timeout in minutes to close unused sessions per session info, 0 = unlimited
   * @param usageMinutes the maximum used time in minutes per session info, 0 = unlimited
   */
  public MultiUserDbPool(String name, ConnectionManager conMgr, int sessionGroupId, int maximumSize, int maximumTotalSize, long idleMinutes, long usageMinutes) {
    this.name = Objects.requireNonNullElse(name, "default-multi");
    this.conMgr = conMgr;
    this.sessionGroupId = sessionGroupId;
    this.maximumSize = maximumSize;
    this.maximumTotalSize = maximumTotalSize;
    this.idleMinutes = idleMinutes;
    this.usageMinutes = usageMinutes;

    poolMap = new ConcurrentHashMap<>();
  }


  @Override
  public Session get(SessionInfo sessionInfo) {
    for (;;) {
      MultiPoolImpl pool;
      synchronized (this) {
        pool = getPool(sessionInfo);
      }
      // Not within the monitor of this pool: creating a session may take a while, and other users must not wait.
      // Within the monitor of the sub-pool instead (the one of AbstractPool#get), since the next session name
      // must be applied to the session created by get().
      synchronized (pool) {
        pool.setNextSessionName(sessionInfo.getSessionName(), determineSessionCloned(sessionInfo, pool));
        try {
          return pool.get();
        }
        catch (RuntimeException rx) {
          if (!pool.isShutdown()) {
            throw rx;
          }
          // shut down concurrently by the timeout thread, since it was empty -> retry with a new sub-pool
        }
      }
    }
  }

  @Override
  public void put(Session session) {
    if (session.getPool() instanceof MultiPoolImpl pool && pool.getOwner() == this) {
      // return to the sub-pool that lent the session, even if it has been replaced in the meantime
      assertNotShutdown();
      pool.put(session);    // not within the monitor of this pool: may roll back a pending transaction
    }
    else {
      SessionPool pool;
      synchronized (this) {
        pool = getPool(session.getSessionInfo());
      }
      pool.put(session);    // not lent by this pool -> fails in DbPool.put
    }
  }

  @Override
  public synchronized void close(SessionInfo sessionInfo) {
    MultiPoolImpl pool = poolMap.get(sessionInfo);
    if (pool != null) {
      shutdownSubPool(pool);
    }
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public int getMaximumSize() {
    return maximumSize;
  }

  @Override
  public int getMaximumTotalSize() {
    return maximumTotalSize;
  }

  @Override
  public int getSize() {
    return size.get();
  }

  @Override
  public synchronized void shutdown() {
    assertNotShutdown();
    shutdown = true;
    RuntimeException failure = null;
    // iterate a copy: the sub-pools remove themselves from the map while being shut down
    for (MultiPoolImpl pool : new ArrayList<>(poolMap.values())) {
      try {
        shutdownSubPool(pool);
      }
      catch (RuntimeException rx) {
        // shut down the remaining sub-pools anyway, otherwise their sessions would never be closed
        if (failure == null) {
          failure = rx;
        }
        else {
          failure.addSuppressed(rx);
        }
      }
    }
    poolMap.clear();
    if (failure != null) {
      throw failure;
    }
  }

  @Override
  public boolean isShutdown() {
    return shutdown;
  }


  /**
   * Determines whether the next session to open should be cloned or not.
   *
   * @param sessionInfo the session info requested
   * @param pool the session pool for the given session info
   * @return true to be cloned
   */
  protected boolean determineSessionCloned(SessionInfo sessionInfo, DbPool pool) {
    return sessionInfo.isCloned();
  }

  /**
   * Determines whether the given pooled session can be removed from the pool due to timeout.<br>
   * This method is provided to override the default in {@link DbPool}, which is used as a private subclass
   * in {@code MultiUserDbPool}.
   *
   * @param slot the pool's session slot
   * @return true if it can be removed
   */
  protected boolean isSlotRemovable(PoolSlot<Session> slot) {
    return true;
  }


  /**
   * Asserts that this pool wasn't shutdown.
   */
  private void assertNotShutdown() {
    if (isShutdown()) {
      throw new PersistenceException("multi user session pool '" + this + "' already shutdown");
    }
  }

  /**
   * Shuts down a sub-pool.<br>
   * The pool's timeout thread shuts down an empty sub-pool on its own (see {@link MultiPoolImpl}),
   * which may happen concurrently. Since the sub-pool is removed from the pool map only after it has been shut down,
   * it may still be found in the map. This is not an error.
   *
   * @param pool the sub-pool
   */
  private void shutdownSubPool(MultiPoolImpl pool) {
    try {
      pool.shutdown();
    }
    catch (RuntimeException rx) {
      if (!pool.isShutdown()) {
        throw rx;
      }
      // else: shut down concurrently by the timeout thread
    }
  }

  /**
   * Gets the pool for given session info.<br>
   * The pool will be created if missing.
   * <p>
   * Notice: the method is invoked synchronized for <code>this</code>.{@link MultiUserSessionPool}!
   *
   * @param sessionInfo the session info
   * @return the pool, never null
   */
  private MultiPoolImpl getPool(SessionInfo sessionInfo) {
    assertNotShutdown();
    MultiPoolImpl pool = poolMap.get(sessionInfo);
    if (pool == null || pool.isShutdown()) {
      // missing or shut down concurrently by the pool's timeout thread (minimum size < 0) -> replace it
      pool = new MultiPoolImpl(sessionInfo.getUserName() + "@" + name + "(" + count.incrementAndGet() + ")",
                               conMgr, sessionInfo.clone(), sessionGroupId);
      poolMap.put(sessionInfo, pool);
    }
    return pool;
  }


  /**
   * Internal {@link SessionPool} implementation.
   */
  private class MultiPoolImpl extends DbPool {

    /**
     * If the pool needs to create a new session, its name is copied from the original session-info
     * passed to {@link #get(SessionInfo)}. As a result, the session names are the ones when the
     * sessions were created, but not when a session is re-used by the pool. This is perfectly
     * ok for fixed sessions such as MT and FX. For pooled sessions, we're providing an internal
     * counter "faking" a slot number. As a result, the initial session names look exactly the same
     * as the ones of the client (remote) server.
     */
    private String nextSessionName;

    /**
     * Indicates whether the next session to open should be marked as cloned.
     */
    private boolean nextSessionCloned;

    /**
     * Counter per pooled session name.<br>
     * This is just cosmetics but avoids confused users (see comment above).
     */
    private final Map<String, Integer> nameCounterMap = new HashMap<>();

    /**
     * Number of sessions of this sub-pool counted in the total size of the multi-user pool.<br>
     * Guarded by the monitor of this sub-pool.
     */
    private int contributed;

    private MultiPoolImpl(String name, ConnectionManager conMgr, SessionInfo sessionInfo, int sessionGroupId) {
      // iniSize 0 -> make sure that createSessionInfo uses nextSessionName
      // minSize -1 -> shutdown sub-pool when all sessions closed
      // maxSize 0 -> unlimited in super class, but limited in createSlots below!
      super(name, conMgr, sessionInfo, sessionGroupId, 0, 1, -1, 0, idleMinutes, usageMinutes);
    }

    private MultiUserDbPool getOwner() {
      return MultiUserDbPool.this;
    }

    @Override
    public boolean isSlotRemovable(PoolSlot<Session> poolSlot) {
      return MultiUserDbPool.this.isSlotRemovable(poolSlot) && super.isSlotRemovable(poolSlot);
    }

    @Override
    public void shutdown() {
      SessionInfo sessionInfo = getSessionInfo();
      super.shutdown();
      int sz;
      synchronized (this) {
        // no more sessions created or removed once shut down
        sz = contributed;
        contributed = 0;
      }
      size.addAndGet(-sz);
      // notice that the map is thread-safe: acquiring the monitor of MultiUserDbPool.this here
      // would invert the locking order (MultiUserDbPool -> sub-pool) and could deadlock
      poolMap.remove(sessionInfo, this);    // only if not already replaced by a new sub-pool, see getPool
    }

    @Override
    protected SessionInfo createSessionInfo(int slotNumber) {
      SessionInfo clonedInfo = getSessionInfo().clone();

      if (nextSessionName != null) {
        Integer count = nameCounterMap.get(nextSessionName);
        if (count != null) {
          // pooled name: use pool name's own slot number
          String pooledName = nextSessionName + "#" + count;
          nameCounterMap.put(nextSessionName, count + 1);
          nextSessionName = pooledName;
        }
        clonedInfo.setSessionName(nextSessionName);
      } // else: no original session name -> cloned session infos are unnamed by default

      if (!nextSessionCloned) {
        clonedInfo.clearCloned();   // main session -> check for already logged in to another server
      }
      return clonedInfo;
    }

    @Override
    protected int createSlots(int num) {
      if (maximumSize > 0) {
        int sz = getSize();
        if (sz + num > maximumSize) {
          num = maximumSize - sz;
          if (num <= 0) {
            PersistenceException pex = new PersistenceException("cannot create more session instances, max. pool size per session " + maximumSize + " reached");
            pex.setTemporary(true);
            throw pex;
          }
        }
      }
      int reserved = 0;
      if (maximumTotalSize > 0 && num > 0) {
        // reserve atomically: the sub-pools create their sessions concurrently
        for (;;) {
          int sz = size.get();
          int n = Math.min(num, maximumTotalSize - sz);
          if (n <= 0) {
            PersistenceException pex = new PersistenceException("cannot create more session instances, max. total pool size " + maximumTotalSize + " reached");
            pex.setTemporary(true);
            throw pex;
          }
          if (size.compareAndSet(sz, sz + n)) {
            reserved = n;
            num = n;
            break;
          }
        }
      }
      int created = 0;
      try {
        created = super.createSlots(num);
      }
      finally {
        size.addAndGet(created - reserved);   // release the reservation, count the created sessions
        contributed += created;
      }
      return created;
    }

    @Override
    protected void removeSlotIndex(int index) {
      super.removeSlotIndex(index);
      contributed--;
      size.decrementAndGet();
    }


    private void setNextSessionName(String nextSessionName, boolean nextSessionCloned) {
      if (nextSessionName != null) {
        int ndx = nextSessionName.lastIndexOf('#');
        if (ndx >= 0 && ndx < nextSessionName.length() - 1) {
          String slotStr = nextSessionName.substring(ndx + 1);
          if (StringHelper.isAllDigits(slotStr)) {  // pooled name!
            nextSessionName = nextSessionName.substring(0, ndx);    // cut the original slot number
            nameCounterMap.putIfAbsent(nextSessionName, 0);
          }
        }
      }
      this.nextSessionName = nextSessionName;
      this.nextSessionCloned = nextSessionCloned;
    }
  }

}
