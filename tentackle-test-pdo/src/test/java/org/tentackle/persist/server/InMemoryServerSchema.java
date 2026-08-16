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

package org.tentackle.persist.server;

import org.tentackle.dbms.Db;
import org.tentackle.log.Logger;
import org.tentackle.model.ModelException;
import org.tentackle.pdo.test.DbTestUtilities;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;

/**
 * Creates the database schema of a test server running in its own JVM.
 * <p>
 * The remote tests fork a server process and talk to it over TRIP; the database lives entirely
 * inside that process. Since the tests run against an in-memory database, the forked JVM gets a
 * fresh and empty one and has to build the schema itself &mdash; there is no
 * {@code AbstractPdoTest} in the server to do it, which is what
 * {@link org.tentackle.pdo.testng.AbstractPdoTest} does for the test JVM.
 *
 * @author harald
 */
public final class InMemoryServerSchema {

  private static final Logger LOGGER = Logger.get(InMemoryServerSchema.class);

  private InMemoryServerSchema() {
    // static use only
  }

  /**
   * Creates the tables if the server's database is in-memory.<br>
   * Does nothing for a persistent database, which is prepared out-of-band.
   *
   * @param session the server's session
   */
  public static void createTablesIfInMemory(Session session) {
    if (session instanceof Db db && db.getBackend().isDatabaseInMemory(session.getUrl())) {
      try {
        DbTestUtilities.getInstance().runScript(db, DbTestUtilities.getInstance().createPopulateScript(db));
        LOGGER.info("in-memory database populated");
      }
      catch (ModelException mex) {
        throw new PersistenceException("populating the in-memory database failed", mex);
      }
    }
  }

}
