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
import org.tentackle.session.Session;
import org.tentackle.session.SessionPool;
import org.tentackle.session.SessionPoolFactory;

/**
 * SessionPool factory implementation.
 */
@Service(SessionPoolFactory.class)
public class DbPoolFactory implements SessionPoolFactory {

  @Override
  public SessionPool create(String name, Session session,
                            int iniSize, int incSize, int minSize, int maxSize, long maxIdleMinutes, long maxUsageMinutes) {

    Db db = (Db) session;
    return new DbPool(name, db.getConnectionManager(), db.getSessionInfo(), db.getSessionGroupId(),
                      iniSize, incSize, minSize, maxSize, maxIdleMinutes, maxUsageMinutes);
  }

}
