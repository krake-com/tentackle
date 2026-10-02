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

package org.tentackle.dbms.trip;

import org.tentackle.dbms.DbModificationTracker;
import org.tentackle.misc.IdSerialNameTuple;
import org.tentackle.misc.IdSerialTuple;
import org.tentackle.session.MasterSerial;
import org.tentackle.session.MasterSerialEvent;
import org.tentackle.session.MasterSerialListEvent;
import org.tentackle.session.ModificationTracker;

import java.util.List;

/**
 * Remote delegate implementation for {@link DbModificationTracker}.
 *
 * @param <T> the modification tracker class
 * @author  harald
 */
public class DbModificationTrackerRemoteDelegateImpl<T extends DbModificationTracker>
             extends RemoteDbDelegateImpl<T>
             implements DbModificationTrackerRemoteDelegate {

  public DbModificationTrackerRemoteDelegateImpl(RemoteDbSessionImpl session, Class<T> clazz) {
    super(session, clazz);
  }


  @Override
  public void configureRemoteSession() {
    getServerSession().setModificationTrackerSession(true);
  }

  @Override
  public MasterSerial selectMasterSerial() {
    return createMasterSerial(((DbModificationTracker) ModificationTracker.getInstance()).getMasterSerial());
  }

  @Override
  public IdSerialTuple selectIdSerialForName(String tableName) {
    return ((DbModificationTracker) ModificationTracker.getInstance()).getIdSerialForName(tableName);
  }

  @Override
  public List<IdSerialTuple> selectAllIdSerials() {
    return ((DbModificationTracker) ModificationTracker.getInstance()).getAllIdSerials();
  }

  @Override
  public List<IdSerialNameTuple> selectAllIdSerialNames() {
    return ((DbModificationTracker) ModificationTracker.getInstance()).getAllIdSerialNames();
  }


  /**
   * Creates the master serial object from a long serial.
   * <p>
   * If {@link MasterSerialEvent}s are registered for this remote session,
   * the next event will be polled from the queue, its serial set, and that event returned.
   *
   * @param serial the long master serial
   * @return the master serial object
   */
  protected MasterSerial createMasterSerial(long serial) {
    MasterSerialEvent masterSerialEvent = getServerSession().pollMasterSerialEvent();
    if (masterSerialEvent != null) {
      masterSerialEvent.setSerial(serial);
      MasterSerialEvent nextEvent = getServerSession().pollMasterSerialEvent();
      if (nextEvent != null) {
        MasterSerialListEvent listEvent = new MasterSerialListEvent();
        listEvent.setSerial(serial);
        listEvent.add(masterSerialEvent);
        while (nextEvent != null) {
          nextEvent.setSerial(serial);
          listEvent.add(nextEvent);
          nextEvent = getServerSession().pollMasterSerialEvent();
        }
        // could be reduced to one element due to MasterSerialEvent.isOverridingOlderEvents(), see MasterSerialListEvent.add()
        masterSerialEvent = listEvent.size() == 1 ? listEvent.get(0) : listEvent;
      }
      return masterSerialEvent;
    }
    return new DefaultMasterSerial(serial);
  }

}
