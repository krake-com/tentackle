/**
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


package org.tentackle.dbms.rmi;

import org.tentackle.dbms.DbModificationTracker;
import org.tentackle.dbms.MasterSerial;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.session.ModificationTracker;

import java.rmi.RemoteException;
import java.util.List;

/**
 * Remote delegate implementation for {@link DbModificationTracker}.
 * @param <T> the modification thread class
 * @author  harald
 */
public class DbModificationTrackerRemoteDelegateImpl<T extends DbModificationTracker>
             extends RemoteDelegateImpl<T>
             implements DbModificationTrackerRemoteDelegate {


  public DbModificationTrackerRemoteDelegateImpl(RemoteDbSessionImpl session, Class<T> clazz) throws RemoteException {
    super(session, clazz);
  }

  /**
   * Creates the master serial object from a long serial.
   * <p>
   * Application can override this method to return some application-
   * specific status back to the client.
   *
   * @param serial the long master serial
   * @return the master serial object
   * @see DbModificationTracker#extractMasterSerial(MasterSerial)
   */
  protected MasterSerial createMasterSerial(long serial) {
    MasterSerial ms = new MasterSerial();
    ms.serial = serial;
    return ms;
  }

  @Override
  public MasterSerial selectMasterSerial() throws RemoteException {
    try {
      return createMasterSerial(((DbModificationTracker) ModificationTracker.getInstance()).getMasterSerial());
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public IdSerialTuple selectIdSerialForName(String tableName) throws RemoteException {
    try {
      return ((DbModificationTracker) ModificationTracker.getInstance()).getIdSerialForName(tableName);
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

  @Override
  public List<IdSerialTuple> selectAllSerials() throws RemoteException {
    try {
      return ((DbModificationTracker) ModificationTracker.getInstance()).getAllSerials();
    }
    catch (Exception ex)  {
      throw createException(ex);
    }
  }

}
