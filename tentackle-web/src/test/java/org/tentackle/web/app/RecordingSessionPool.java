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

package org.tentackle.web.app;

import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.SessionPool;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link SessionPool} handing out one prepared session and recording every borrow and return.
 * <p>
 * Stands in for a {@code DbPool} without its connection manager, timeout threads and cloning
 * logic - none of which {@code WebApplication} knows or cares about. It doubles as the session's
 * own {@code Pool} (what {@code Session.getPool()} returns), which is exactly how a real
 * {@code DbPool} wires itself into the sessions it hands out.
 */
public class RecordingSessionPool implements SessionPool {

  private final Session session;
  private final List<Session> borrowed = new ArrayList<>();
  private final List<Session> returned = new ArrayList<>();
  private boolean shutdown;

  /**
   * Creates the pool.
   *
   * @param session the session to hand out
   */
  public RecordingSessionPool(Session session) {
    this.session = session;
    session.setPool(this);
  }

  /**
   * Gets the sessions handed out so far.
   *
   * @return the borrowed sessions
   */
  public List<Session> getBorrowed() {
    return borrowed;
  }

  /**
   * Gets the sessions returned so far.
   *
   * @return the returned sessions
   */
  public List<Session> getReturned() {
    return returned;
  }

  @Override
  public Session get() {
    borrowed.add(session);
    return session;
  }

  @Override
  public void put(Session poolable) {
    returned.add(poolable);
  }

  @Override
  public SessionInfo getSessionInfo() {
    return session.getSessionInfo();
  }

  @Override
  public int getSessionGroupId() {
    return 0;
  }

  @Override
  public String getName() {
    return "recording-pool";
  }

  @Override
  public int getInitialSize() {
    return 1;
  }

  @Override
  public int getIncrementSize() {
    return 1;
  }

  @Override
  public int getMinimumSize() {
    return 1;
  }

  @Override
  public int getMaximumSize() {
    return 1;
  }

  @Override
  public int getSize() {
    return 1;
  }

  @Override
  public long getIdleMinutes() {
    return 0;
  }

  @Override
  public long getUsageMinutes() {
    return 0;
  }

  @Override
  public long getShutdownTimeout() {
    return 0;
  }

  @Override
  public void shutdown() {
    shutdown = true;
  }

  @Override
  public boolean isShutdown() {
    return shutdown;
  }

  @Override
  public void monitor() {
    // nothing to monitor
  }

}
