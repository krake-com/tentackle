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

package org.tentackle.dbms.prefs;

import org.tentackle.prefs.PersistedPreferences;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.Session;
import org.tentackle.session.SessionFactory;
import org.tentackle.session.SessionInfoFactory;

/**
 * Tests for persisted preferences running in parallel in another JVM.<br>
 * Invoked from MultiJVMPreferencesTest.
 *
 * @author harald
 */
public class RemovePrefsInOtherJVM {

  // not annotated with @Test because invoked from shell!
  private static void removeNode() throws Exception {
    Session session = SessionFactory.getInstance().create(SessionInfoFactory.getInstance().create());
    session.open();
    session.makeCurrent();

    ModificationTracker tracker = ModificationTracker.getInstance();
    tracker.setSession(session.clone());
    tracker.start();

    PersistedPreferences prefs = PersistedPreferences.systemNodeForPackage(RemovePrefsInOtherJVM.class).node("other");
    prefs.removeNode();
    PersistedPreferences.systemRoot().flush();
    tracker.terminate();
    session.close();
  }

  public static void main(String[] args) {
    try {
      removeNode();
    }
    catch (Exception ex) {
      System.err.println("execution failed" + ex);
      System.exit(1);
    }
  }
}
