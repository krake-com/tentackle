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

package org.tentackle.dbms.prefs.rmi;

import java.rmi.RemoteException;
import org.tentackle.dbms.prefs.DbPreferences;
import org.tentackle.dbms.prefs.DbPreferencesFactory;
import org.tentackle.dbms.prefs.DbPreferencesOperation;
import org.tentackle.dbms.prefs.DbPreferencesOperation.RefreshInfo;
import org.tentackle.dbms.rmi.AbstractDbOperationRemoteDelegateImpl;
import org.tentackle.dbms.rmi.RemoteDbSessionImpl;

/**
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
  public DbPreferences loadRootTree(String user, boolean forced) throws RemoteException {
    try {
      return dbOperation.loadRootTree(user, forced);
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public RefreshInfo loadRefreshInfo(String user, long serial) throws RemoteException {
    try {
      DbPreferences node = DbPreferencesFactory.getInstance().getRootTree(getSession(), user);
      return dbOperation.loadRefreshInfo(node, serial);
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

  @Override
  public boolean flush(DbPreferences node, boolean sync) throws RemoteException {
    try {
      return dbOperation.flush(node, sync, true);
    }
    catch (RuntimeException e) {
      throw createException(e);
    }
  }

}
