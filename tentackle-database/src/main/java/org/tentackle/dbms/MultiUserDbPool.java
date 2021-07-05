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

import org.tentackle.session.MultiUserSessionPool;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;

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
  private final long maxIdleMinutes;
  private final long maxUsageMinutes;

  private Map<SessionInfo, MultiPoolImpl> poolMap;              // maintained pools per session info (user), null if shut down
  private final AtomicInteger size = new AtomicInteger();       // total number of sessions
  private final AtomicInteger count = new AtomicInteger();      // session pool ID

  /**
   * Creates a multi user session pool.
   *
   * @param name the name of the pool, null if "default-multi"
   * @param conMgr the connection manager for local sessions, null if remote
   * @param sessionGroupId the session group ID to use for all sessions
   * @param maxSize the maximum number of sessions per session info, 0 = unlimited
   * @param maxIdleMinutes the idle timeout in minutes to close unused sessions per session info, 0 = unlimited
   * @param maxUsageMinutes the max. used time in minutes per session info, 0 = unlimited
   */
  public MultiUserDbPool(String name, ConnectionManager conMgr, int sessionGroupId, int maxSize, long maxIdleMinutes, long maxUsageMinutes) {
    this.name = Objects.requireNonNullElse(name, "default-multi");
    this.conMgr = conMgr;
    this.sessionGroupId = sessionGroupId;
    this.maxSize = maxSize;
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
  public String getName() {
    return name;
  }

  @Override
  public int getMaxSize() {
    return maxSize;
  }

  @Override
  public int getSize() {
    return size.get();
  }

  @Override
  public synchronized void shutdown() {
    assertNotShutdown();
    for (DbPool pool : poolMap.values()) {
      pool.shutdown();
    }
    poolMap = null;
  }

  @Override
  public synchronized boolean isShutdown() {
    return poolMap == null;
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
  private DbPool getPool(SessionInfo sessionInfo) {
    assertNotShutdown();
    MultiPoolImpl pool = poolMap.get(sessionInfo);
    if (pool == null) {
      pool = new MultiPoolImpl(name + "(" + count.incrementAndGet() + ")", conMgr, sessionInfo.clone(), sessionGroupId);
      poolMap.put(sessionInfo, pool);
      determineSize();
    }
    pool.nextSessionInfo = sessionInfo;
    return pool;
  }

  /**
   * Re-calculates the total number of sessions of all sub pools.
   * <p>
   * Notice: the method is invoked synchronized for <code>this</code>.{@link MultiUserSessionPool}!
   */
  private void determineSize() {
    int sz = 0;
    for (DbPool pool : poolMap.values()) {
      sz += pool.getSize();
    }
    size.set(sz);
  }


  /**
   * DbPool with total session count updates.
   */
  private class MultiPoolImpl extends DbPool {

    private SessionInfo nextSessionInfo;

    public MultiPoolImpl(String name, ConnectionManager conMgr, SessionInfo sessionInfo, int sessionGroupId) {
      // iniSize 0  -> make sure that createDbInstances uses nextSessionInfo
      // minSize -1 -> shutdown sub-pool when all sessions closed
      // maxSize 0  -> unlimited per sessioninfo (but limited in total for all sub-pools)
      super(name, conMgr, sessionInfo, sessionGroupId, 0, 1, -1, 0, maxIdleMinutes, maxUsageMinutes);
    }

    @Override
    protected SessionInfo createSessionInfo(int slotNumber) {
      return nextSessionInfo;
    }

    @Override
    protected int createDbInstances(int num) {
      if (maxSize > 0) {
        int sz = size.get();
        if (sz + num > maxSize) {
          num = maxSize - sz;
          if (num <= 0) {
            PersistenceException pex = new PersistenceException("cannot create more session instances, max. poolsize " + maxSize + " reached");
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
  }

}
