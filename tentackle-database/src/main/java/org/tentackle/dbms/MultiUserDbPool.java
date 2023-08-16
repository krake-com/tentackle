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
import org.tentackle.session.MultiUserSessionPool;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionPool;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of a {@link MultiUserSessionPool}.<br>
 * The pool is actually a pool of {@link DbPool}s, one for each {@link SessionInfo}.
 */
public class MultiUserDbPool implements MultiUserSessionPool {

  private final String name;
  private final ConnectionManager conMgr;
  private final int sessionGroupId;
  private final int maxSize;
  private final int maxTotalSize;
  private final long maxIdleMinutes;
  private final long maxUsageMinutes;

  private Map<SessionInfo, MultiPoolImpl> poolMap;              // maintained pools per session info (user), null if shut down
  private final AtomicInteger size = new AtomicInteger();       // total number of sessions
  private final AtomicInteger count = new AtomicInteger();      // session pool ID

  /**
   * Creates a multi-user session pool.
   *
   * @param name the name of the pool, null if "default-multi"
   * @param conMgr the connection manager for local sessions, null if remote
   * @param sessionGroupId the session group ID to use for all sessions
   * @param maxSize the maximum number of sessions per session info, 0 = unlimited
   * @param maxTotalSize the maximum total number of sessions, 0 = unlimited
   * @param maxIdleMinutes the idle timeout in minutes to close unused sessions per session info, 0 = unlimited
   * @param maxUsageMinutes the max. used time in minutes per session info, 0 = unlimited
   */
  public MultiUserDbPool(String name, ConnectionManager conMgr, int sessionGroupId, int maxSize, int maxTotalSize, long maxIdleMinutes, long maxUsageMinutes) {
    this.name = Objects.requireNonNullElse(name, "default-multi");
    this.conMgr = conMgr;
    this.sessionGroupId = sessionGroupId;
    this.maxSize = maxSize;
    this.maxTotalSize = maxTotalSize;
    this.maxIdleMinutes = maxIdleMinutes;
    this.maxUsageMinutes = maxUsageMinutes;

    poolMap = new HashMap<>();
  }


  @Override
  public synchronized Session get(SessionInfo sessionInfo) {
    return getPool(sessionInfo).getSession();
  }

  @Override
  public synchronized void put(Session session) {
    getPool(session.getSessionInfo()).putSession(session);
  }

  @Override
  public synchronized void close(SessionInfo sessionInfo) {
    SessionPool pool = poolMap.get(sessionInfo);
    if (pool != null) {
      pool.shutdown();
    }
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public int getMaxSize() {
    return maxSize;
  }

  @Override
  public int getMaxTotalSize() {
    return maxTotalSize;
  }

  @Override
  public int getSize() {
    return size.get();
  }

  @Override
  public synchronized void shutdown() {
    assertNotShutdown();
    for (SessionPool pool : poolMap.values()) {
      pool.shutdown();
    }
    poolMap = null;
  }

  @Override
  public synchronized boolean isShutdown() {
    return poolMap == null;
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
   * This method is provided to override the default in {@link DbPool}, which is used a private subclass
   * in {@code MultiUserDbPool}.
   *
   * @param pooledDb the pooled session
   * @return true if it can be removed
   */
  protected boolean isPooledDbRemovable(PooledDb pooledDb) {
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
   * Gets the pool for given session info.<br>
   * The pool will be created if missing.
   * <p>
   * Notice: the method is invoked synchronized for <code>this</code>.{@link MultiUserSessionPool}!
   *
   * @param sessionInfo the session info
   * @return the pool, never null
   */
  private SessionPool getPool(SessionInfo sessionInfo) {
    assertNotShutdown();
    MultiPoolImpl pool = poolMap.get(sessionInfo);
    if (pool == null) {
      pool = new MultiPoolImpl(sessionInfo.getUserName() + "@" + name + "(" + count.incrementAndGet() + ")",
                               conMgr, sessionInfo.clone(), sessionGroupId);
      poolMap.put(sessionInfo, pool);
      determineSize();
    }
    pool.setNextSessionName(sessionInfo.getSessionName(), determineSessionCloned(sessionInfo, pool));
    return pool;
  }

  /**
   * Re-calculates the total number of sessions of all sub pools.
   * <p>
   * Notice: the method is invoked synchronized for <code>this</code>.{@link MultiUserSessionPool}!
   */
  private void determineSize() {
    int sz = 0;
    for (SessionPool pool : poolMap.values()) {
      sz += pool.getSize();
    }
    size.set(sz);
  }


  /**
   * Internal {@link SessionPool} implementation.
   */
  private class MultiPoolImpl extends DbPool {

    /**
     * If the pool needs to create a new session, its name is copied from the original session-info
     * passed to {@link #get(SessionInfo)}. As a result, the session names are the ones when the
     * sessions were created, but not when a session is re-used by the pool. This is perfectly
     * ok for fixes sessions such as MT and FX. For pooled sessions, we're providing an internal
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
     * This is just cosmetics, but avoids confused users (see comment above).
     */
    private final Map<String, Integer> nameCounterMap = new HashMap<>();

    private MultiPoolImpl(String name, ConnectionManager conMgr, SessionInfo sessionInfo, int sessionGroupId) {
      // iniSize 0  -> make sure that createSessionInfo uses nextSessionName
      // minSize -1 -> shutdown sub-pool when all sessions closed
      // maxSize 0  -> unlimited in super class, but limited in createDbInstances below!
      super(name, conMgr, sessionInfo, sessionGroupId, 0, 1, -1, 0, maxIdleMinutes, maxUsageMinutes);
    }

    @Override
    public boolean isPooledDbRemovable(PooledDb pooledDb) {
      return MultiUserDbPool.this.isPooledDbRemovable(pooledDb) && super.isPooledDbRemovable(pooledDb);
    }

    @Override
    public void shutdown() {
      int sz = getSize();
      SessionInfo sessionInfo = getSessionInfo();
      super.shutdown();
      size.addAndGet(-sz);
      synchronized (MultiUserDbPool.this) {
        poolMap.remove(sessionInfo);
      }
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
    protected int createDbInstances(int num) {
      if (maxTotalSize > 0) {
        int sz = size.get();
        if (sz + num > maxTotalSize) {
          num = maxTotalSize - sz;
          if (num <= 0) {
            PersistenceException pex = new PersistenceException("cannot create more session instances, max. total pool size " + maxTotalSize + " reached");
            pex.setTemporary(true);
            throw pex;
          }
        }
      }
      if (maxSize > 0) {
        int sz = getSize();
        if (sz + num > maxSize) {
          num = maxSize - sz;
          if (num <= 0) {
            PersistenceException pex = new PersistenceException("cannot create more session instances, max. pool size per session " + maxSize + " reached");
            pex.setTemporary(true);
            throw pex;
          }
        }
      }
      int created = super.createDbInstances(num);
      size.addAndGet(created);
      return created;
    }

    @Override
    protected void removeDbIndex(int index) {
      super.removeDbIndex(index);
      size.decrementAndGet();
    }

    private void setNextSessionName(String nextSessionName, boolean nextSessionCloned) {
      if (nextSessionName != null) {
        int ndx = nextSessionName.lastIndexOf('#');
        if (ndx >= 0 && ndx < nextSessionName.length() - 1) {
          String slotStr = nextSessionName.substring(ndx + 1);
          if (StringHelper.isAllDigits(slotStr)) {  // pooled name!
            nextSessionName = nextSessionName.substring(0, ndx);    // cut original slot number
            nameCounterMap.putIfAbsent(nextSessionName, 0);
          }
        }
      }
      this.nextSessionName = nextSessionName;
      this.nextSessionCloned = nextSessionCloned;
    }

  }

}
