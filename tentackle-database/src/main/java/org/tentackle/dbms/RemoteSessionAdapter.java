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

package org.tentackle.dbms;

import org.tentackle.dbms.rmi.RemoteDbSession;
import org.tentackle.log.Logger;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.RemoteSession;
import org.tentackle.session.SessionInfo;

import java.rmi.RemoteException;

/**
 * Adapter for a remote session.<br>
 * Hides the RMI stuff.
 *
 * @author harald
 */
public class RemoteSessionAdapter implements RemoteSession {

  private final RemoteDbSession rs;

  /**
   * Creates the remote session adapter.
   *
   * @param rs the remote session object
   */
  public RemoteSessionAdapter(RemoteDbSession rs) {
    this.rs = rs;
  }


  /**
   * Gets the remote db session object.
   *
   * @return the remote session object
   */
  public RemoteDbSession getRemoteDbSession() {
    return rs;
  }


  @Override
  public SessionInfo getClientSessionInfo() {
    try {
      return rs.getClientSessionInfo();
    }
    catch (RemoteException rex) {
      throw PersistenceException.createFromRemoteException(this, rex);
    }
  }


  @Override
  public void log(Logger.Level level, String message) {
    try {
      rs.log(null, level, message);
    }
    catch (RemoteException rex) {
      throw PersistenceException.createFromRemoteException(this, rex);
    }
  }

  @Override
  public void log(String name, Logger.Level level, String message) {
    try {
      rs.log(name, level, message);
    }
    catch (RemoteException rex) {
      throw PersistenceException.createFromRemoteException(this, rex);
    }
  }

  @Override
  public void logStatistics(Logger.Level level, boolean clear) {
    try {
      rs.logStatistics(level, clear);
    }
    catch (RemoteException rex) {
      throw PersistenceException.createFromRemoteException(this, rex);
    }
  }

}
