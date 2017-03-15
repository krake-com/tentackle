/*
 * Tentackle - http://www.tentackle.org
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

package org.tentackle.persist.prefs;

import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoTracker;
import org.tentackle.pdo.Session;
import org.tentackle.prefs.PersistedPreferences;

/**
 * Tests for persisted preferences running in parallel in another JVM.<br>
 * Invoked from MultiJVMPreferencesTest.
 *
 * @author harald
 */
public class AddPrefsInOtherJVM {

  /**
   * The logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(AddPrefsInOtherJVM.class);

  // not annotated with @Test because invoked from shell!
  private static void addNode() throws Exception {
    Session session = Pdo.createSession();
    session.open();
    session.makeCurrent();

    PdoTracker tracker = PdoTracker.getInstance();
    tracker.setSession(session.clone());
    tracker.start();

    PersistedPreferences prefs = PersistedPreferences.systemNodeForPackage(AddPrefsInOtherJVM.class).node("other");
    prefs.put(MultiJVMPreferencesTest.KEY_4, MultiJVMPreferencesTest.VALUE_4);
    prefs.flush();
    tracker.terminate();
    session.close();
  }

  public static void main(String[] args) {
    try {
      addNode();
    }
    catch (Exception ex) {
      System.out.println("execution failed:");
      ex.printStackTrace();
      System.exit(1);
    }
  }
}
