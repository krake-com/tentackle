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

import org.testng.annotations.Test;

import org.tentackle.misc.IdSerialTuple;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * Tracked names are stored literally, never interpreted as SQL code.
 * <p>
 * Remote clients may request arbitrary names via the modification tracker,
 * which creates the missing rows in the modification table.
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class DbModificationTrackedNameTest extends AbstractDbTest {

  public DbModificationTrackedNameTest() {
    super(TransactionType.NONE);
  }


  @Test
  public void testQuotedNameStoredLiterally() {
    assertStoredLiterally("x'||'y", "xy");
  }

  @Test
  public void testSubqueryNotExecuted() {
    assertStoredLiterally("z'||(SELECT MAX(id) FROM modification)||'", null);
  }

  @Test
  public void testApostrophe() {
    assertStoredLiterally("it's", null);
  }


  private void assertStoredLiterally(String name, String injectedName) {
    Db db = getSession();
    DbModificationTracker tracker = new DbModificationTracker("tracked name test");   // not started: runs its tasks in this thread
    tracker.setSession(db.clone(null));
    try {
      IdSerialTuple idSerial = tracker.getIdSerialForName(name);
      assertNotNull(idSerial);
      IdSerialTuple stored = new DbModification(db).selectIdSerial(name);
      assertNotNull(stored, "name not stored literally: " + name);
      assertEquals(stored.getId(), idSerial.getId());
      if (injectedName != null) {
        assertNull(new DbModification(db).selectIdSerial(injectedName), "name interpreted as SQL: " + name);
      }
    }
    finally {
      tracker.terminate();
    }
  }

}
