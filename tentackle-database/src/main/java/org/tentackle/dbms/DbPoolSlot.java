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

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;
import org.tentackle.misc.AbstractPoolSlot;
import org.tentackle.misc.Pool;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;

/**
 * Slot of a {@link DbPool} holding a pooled session.<br>
 * Closed sessions are removed from the pool.
 * Remote sessions not belonging to a session group are kept alive while idle.
 */
public class DbPoolSlot extends AbstractPoolSlot<Session> {

  private static final Logger LOGGER = Logger.get(DbPoolSlot.class);


  /**
   * Creates a slot.
   *
   * @param pool the pool
   * @param slotNumber the slot number within the pool (== pool-ID - 1)
   * @param session the managed session
   */
  public DbPoolSlot(Pool<Session> pool, int slotNumber, Session session) {
    super(pool, slotNumber, session);
  }


  @Override
  public boolean isObsolete() {
    Session session = getPoolable();
    if (session != null) {   // not lent
      if (!session.isOpen()) {
        LOGGER.warning("session {0} closed unexpectedly -> removed from pool {1}", session, getPool());
        return true;
      }
      if (session.isRemote() && session.getSessionGroupId() == 0) {
        // remote connections must be kept alive in order not to be closed by remote server.
        // But only if they do not belong to the session group! It is assumed that the root session
        // of the session group is kept alive by the application (usually by the modification tracker).
        try {
          session.setAlive(true);
        }
        catch (RuntimeException ex) {
          LOGGER.severe("remote keep alive failed", ex);
          return true;
        }
      }
    }
    return false;
  }


  @Override
  protected TentackleRuntimeException createException(String msg) {
    return new PersistenceException(getReferencedPoolable(), msg);
  }

}
