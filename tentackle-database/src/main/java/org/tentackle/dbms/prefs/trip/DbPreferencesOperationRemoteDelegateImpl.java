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

package org.tentackle.dbms.prefs.trip;

import org.tentackle.dbms.prefs.DbPreferences;
import org.tentackle.dbms.prefs.DbPreferencesFactory;
import org.tentackle.dbms.prefs.DbPreferencesOperation;
import org.tentackle.dbms.prefs.DbPreferencesOperation.RefreshInfo;
import org.tentackle.dbms.trip.AbstractDbOperationRemoteDelegateImpl;
import org.tentackle.dbms.trip.RemoteDbSessionImpl;

/**
 * Server-side implementation of the {@link DbPreferencesOperationRemoteDelegate}.
 *
 * @author harald
 */
public class DbPreferencesOperationRemoteDelegateImpl
       extends AbstractDbOperationRemoteDelegateImpl<DbPreferencesOperation>
       implements DbPreferencesOperationRemoteDelegate {

  public DbPreferencesOperationRemoteDelegateImpl(RemoteDbSessionImpl serverSession,
                                                  Class<DbPreferencesOperation> clazz) {
    super(serverSession, clazz);
  }

  @Override
  public DbPreferences loadRootTree(String user, boolean forced) {
    return dbOperation.loadRootTree(user, forced);
  }

  @Override
  public RefreshInfo loadRefreshInfo(String user, long serial) {
    DbPreferences node = DbPreferencesFactory.getInstance().getRootTree(getSession(), user);
    return dbOperation.loadRefreshInfo(node, serial);
  }

  @Override
  public boolean flush(DbPreferences node, boolean sync) {
    return dbOperation.flush(node, sync, true);
  }

}
