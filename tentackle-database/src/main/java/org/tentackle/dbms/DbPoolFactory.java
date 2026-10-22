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

import org.tentackle.common.Service;
import org.tentackle.session.MultiUserSessionPool;
import org.tentackle.session.Session;
import org.tentackle.session.SessionPool;
import org.tentackle.session.SessionPoolFactory;

/**
 * SessionPool factory implementation.<br>
 * Creates {@link DbPool}s and {@link MultiUserDbPool}s.
 */
@Service(SessionPoolFactory.class)
public class DbPoolFactory implements SessionPoolFactory {

  /**
   * Creates the session pool factory.
   */
  public DbPoolFactory() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public SessionPool create(String name, Session session,
                            int initialSize, int incrementSize, int minimumSize, int maximumSize,
                            long idleMinutes, long usageMinutes) {
    Db db = (Db) session;
    return new DbPool(name, db.getConnectionManager(), db.getSessionInfo(), db.getSessionGroupId(),
                      initialSize, incrementSize, minimumSize, maximumSize, idleMinutes, usageMinutes);
  }

  @Override
  public MultiUserSessionPool create(String name, Session session, int maximumSize, int maximumTotalSize,
                                     long idleMinutes, long usageMinutes) {
    Db db = (Db) session;
    return new MultiUserDbPool(name, db.getConnectionManager(), db.getSessionGroupId(), maximumSize,
                               maximumTotalSize, idleMinutes, usageMinutes);
  }

}
