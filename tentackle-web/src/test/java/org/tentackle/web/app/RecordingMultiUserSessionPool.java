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

import org.tentackle.session.MultiUserSessionPool;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link MultiUserSessionPool} handing out one prepared session and recording which session
 * info it was asked for.
 * <p>
 * This is the pool a <em>proxy</em> web node owns: unlike a {@code SessionPool} it is keyed by
 * user, because each upstream session stays bound to one identity. Like the real
 * {@code MultiUserDbPool} it is not itself the session's {@code Pool} - the session belongs to an
 * inner per-user pool, which is what {@code WebApplication.putSession} returns it to.
 */
public class RecordingMultiUserSessionPool implements MultiUserSessionPool {

  private final Session session;
  private final List<SessionInfo> requestedFor = new ArrayList<>();
  private boolean shutdown;

  /**
   * Creates the pool.
   *
   * @param session the session to hand out
   */
  public RecordingMultiUserSessionPool(Session session) {
    this.session = session;
  }

  /**
   * Gets the session infos this pool was asked for.
   *
   * @return the session infos, in call order
   */
  public List<SessionInfo> getRequestedFor() {
    return requestedFor;
  }

  @Override
  public Session get(SessionInfo sessionInfo) {
    requestedFor.add(sessionInfo);
    return session;
  }

  @Override
  public void put(Session session) {
    throw new AssertionError("a proxy node must return the session to its own pool, not here");
  }

  @Override
  public void close(SessionInfo sessionInfo) {
    // nothing to close
  }

  @Override
  public String getName() {
    return "recording-remote-pool";
  }

  @Override
  public int getMaximumSize() {
    return 1;
  }

  @Override
  public int getMaximumTotalSize() {
    return 1;
  }

  @Override
  public int getSize() {
    return 1;
  }

  @Override
  public void shutdown() {
    shutdown = true;
  }

  @Override
  public boolean isShutdown() {
    return shutdown;
  }

}
