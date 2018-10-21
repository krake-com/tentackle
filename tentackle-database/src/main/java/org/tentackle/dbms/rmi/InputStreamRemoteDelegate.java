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

package org.tentackle.dbms.rmi;

import java.rmi.RemoteException;

/**
 *
 * @author harald
 */
public interface InputStreamRemoteDelegate extends RemoteDelegate {

  /**
   * Reads bytes from a stream.<br>
   * If there is no more data available the operation will wait
   * until data is available again.
   *
   * @return the byte array, null if end of stream
   * @throws RemoteException if reading failed
   */
  byte[] read() throws RemoteException;

}
