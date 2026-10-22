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

package org.tentackle.dbms.prefs;

import org.testng.annotations.Test;

import org.tentackle.dbms.AbstractDbTest;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * Tests the serial of the refresh info sent to remote clients.
 * <p>
 * The serial must be the one of the tree sent, even if the tree was not loaded into the server's repository
 * before. Otherwise, the client would remember a serial that never matches, and reload the whole tree on
 * every refresh.
 *
 * @author harald
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class PreferencesRefreshSerialTest extends AbstractDbTest {

  @Test
  public void testRefreshSerialMatchesTree() {
    DbPreferencesFactory factory = DbPreferencesFactory.getInstance();
    DbPreferences root = factory.getSystemRoot();
    DbPreferencesOperation operation = new DbPreferencesOperation(getSession());
    long clientSerial = factory.getSerial(root.getUser()) + 1000;   // any outdated serial of the remote client

    factory.invalidate();   // the tree is not loaded into the repository anymore

    DbPreferencesOperation.RefreshInfo info = operation.loadRefreshInfo(root, clientSerial);
    assertNotNull(info, "outdated client tree not refreshed");
    assertNotEquals(info.serial(), 0L, "serial of an unloaded tree sent");
    assertEquals(info.serial(), factory.getSerial(root.getUser()), "serial does not match the tree sent");

    // the client is up to date now
    assertNull(operation.loadRefreshInfo(root, info.serial()), "refreshed again although up to date");
  }

}
