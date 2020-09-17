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

// Created on November 14, 2003, 2:35 PM

package org.tentackle.dbms.rmi;

import org.tentackle.log.Logger.Level;
import org.tentackle.session.SessionInfo;

import java.rmi.Remote;
import java.rmi.RemoteException;



/**
 * Application server session.<br>
 * The session will create all other delegates for the client.
 *
 * @author harald
 */
public interface RemoteDbSession extends Remote {

  /**
   * Gets the client user info.
   * <p>
   * The server may set certain values in the session info that may be
   * of interest by the client application, for example the userId.
   *
   * @return the session info
   * @throws RemoteException if failed
   */
  SessionInfo getClientSessionInfo() throws RemoteException;


  /**
   * Closes a session.
   *
   * @throws RemoteException if failed
   */
  void close() throws RemoteException;


  /**
   * Sends text based logging infos to the RMI-Server.
   *
   * @param level the logging level
   * @param message the message
   * @throws RemoteException if failed
   */
  void log(Level level, String message) throws RemoteException;


  /**
   * Logs the statistics.<br>
   *
   * @param level the logging level
   * @param clear true if clear statistics after dump
   * @throws RemoteException if logging failed
   * @see RemoteDelegateInvocationHandler#collectStatistics
   */
  void logStatistics(Level level, boolean clear) throws RemoteException;


  /**
   * Gets the delegate for the remote db-connection.
   *
   * @return the delegate
   * @throws RemoteException if failed
   */
  DbRemoteDelegate getDbRemoteDelegate() throws RemoteException;

  /**
   * Gets the delegate for a given classname.<br>
   * Per class the rmi-clients request a remote access there must be
   * a RemoteDelegate. The delegate is determined from the classname of the
   * client class as follows:<br>
   * <code>&lt;package&gt;.rmi.&lt;last-element-of-classname&gt;.class</code> on the client-side and<br>
   * <code>&lt;package&gt;.rmi.&lt;last-element-of-classname&gt;Impl.class</code> on the server-side.<br>
   *
   * @param <T> the delegate type
   * @param classname is the name of class
   * @return the delegate
   * @throws RemoteException if failed
   */
  <T extends RemoteDelegate> T getRemoteDelegate(String classname) throws RemoteException;

}
