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

// Created on November 22, 2003, 12:53 PM

package org.tentackle.dbms.rmi;

import java.rmi.Remote;

/**
 * Tag interface for Remote delegates provided by getRemoteDelegate() in Db.<br>
 * The implementation of a RemoteDelegate must provide a constructor as follows:
 * <pre>
 * public RemoteDelegateImpl(RemoteDbSessionImpl session, Class clazz) throws RemoteException {
 *
 * }
 * </pre>
 *
 * while session is the remote DB-session and clazz is the Class the delegate implements
 * the remote methods.
 *
 * @author harald
 */
public interface RemoteDelegate extends Remote {

}
