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

import org.tentackle.prefs.PersistedPreferences;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.Session;
import org.tentackle.session.SessionFactory;

/**
 * Tests for persisted preferences running in parallel in another JVM.<br>
 * Invoked from MultiJVMPreferencesTest.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class AddPrefsInOtherJVM {

  // not annotated with @Test because invoked from shell!
  private static void addNode() throws Exception {
    Session session = SessionFactory.getInstance().create();
    try {
      session.makeCurrent();

      ModificationTracker tracker = ModificationTracker.getInstance();
      tracker.setSession(session.clone(null));
      tracker.setSleepInterval(500);
      tracker.start();

      PersistedPreferences prefs = PersistedPreferences.systemNodeForPackage(AddPrefsInOtherJVM.class).node("other");
      prefs.put(MultiJVMPreferencesTest.KEY_4, MultiJVMPreferencesTest.VALUE_4);
      prefs.flush();

      Thread.sleep(1000);

      tracker.terminate();
    }
    finally {
      session.close();
    }
  }

  static void main(String[] args) {
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
