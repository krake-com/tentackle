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

package org.tentackle.sql;

import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.dbms.Db;
import org.tentackle.pdo.Pdo;
import org.tentackle.session.PersistenceException;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Simple connection test to in-memory H2-database.
 */
public class H2ConnectionTest {

  @Test(groups = "local")
  public void openSession() {
    Reporter.log("H2ConnectionTest.openSession <br>", true);

    try {
      Db session = (Db) Pdo.createSession(Pdo.createSessionInfo("h2"));
      assertTrue(session.getBackend().isDatabaseInMemory(session.getUrl()));
      long txVoucher = session.begin("test"); // forces attach
      Reporter.log(session + " <br>", true);
      session.rollback(txVoucher);
      session.close();
    }
    catch (PersistenceException ex) {
      fail("cannot connect to in-memory H2 database", ex);
    }
  }

}
