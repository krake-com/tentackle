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

package org.tentackle.persist.prefs.rmi;

import java.rmi.RemoteException;
import org.tentackle.persist.prefs.DbPreferences;
import org.tentackle.persist.prefs.DbPreferencesOperation;
import org.tentackle.persist.rmi.AbstractDbOperationRemoteDelegateImpl;
import org.tentackle.persist.rmi.RemoteDbSessionImpl;

/**
 *
 * @author harald
 */
public class DbPreferencesOperationRemoteDelegateImpl
       extends AbstractDbOperationRemoteDelegateImpl<DbPreferencesOperation>
       implements DbPreferencesOperationRemoteDelegate {

  public DbPreferencesOperationRemoteDelegateImpl(RemoteDbSessionImpl serverSession,
                                                  Class<DbPreferencesOperation> clazz)
         throws RemoteException {

    super(serverSession, clazz);
  }

  @Override
  public DbPreferences getSystemRoot() throws RemoteException {
    try {
      return dbOperation.getSystemRoot();
    }
    catch (Exception e) {
      throw createException(e);
    }
  }

  @Override
  public DbPreferences getUserRoot(String user) throws RemoteException {
    try {
      return dbOperation.getUserRoot(user);
    }
    catch (Exception e) {
      throw createException(e);
    }
  }

  @Override
  public void flush(DbPreferences node) throws RemoteException {
    try {
      dbOperation.flush(node);
    }
    catch (Exception e) {
      throw createException(e);
    }
  }

}
