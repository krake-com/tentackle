/*
 * Tentackle - a framework for java desktop applications
 * http://www.tentackle.org
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

package org.tentackle.tools.fixprefs;

import org.tentackle.app.ConsoleApplication;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.prefs.DbPreferencesFactory;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * A tool to fix the persisted preferences stored in a database.
 *
 * @author harald
 */
public class FixPreferences extends ConsoleApplication {

  /**
   * The logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(FixPreferences.class);


  public FixPreferences() {
    super("Fix Preferences");
  }

  @Override
  public <U extends PersistentDomainObject<U>> U getUser(DomainContext context, long userId) {
    return null;    // not used
  }


  private void run() {
    Db db = (Db) getSession();
    db.assertNotRemote();   // must be a direct JDBC link
    db = db.clone();
    try {
      DbPreferencesFactory.getInstance().checkAllPreferences(db);
    }
    finally {
      db.close();
    }
  }


  public static void main(String[] args) {
    FixPreferences app = new FixPreferences();
    app.start(args);
    try {
      app.run();
    }
    catch (RuntimeException rex) {
      LOGGER.severe("FAILED!", rex);
    }
    finally {
      app.stop();
    }
  }

}
