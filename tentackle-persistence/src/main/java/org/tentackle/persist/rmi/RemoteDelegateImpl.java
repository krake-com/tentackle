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


package org.tentackle.persist.rmi;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import org.tentackle.persist.Db;



/**
 * All remote delegates must extend this class
 *
 * @param <T> the class handled by this delegate
 * @author  harald
 */
public abstract class RemoteDelegateImpl<T> implements RemoteDelegate {

  /** the server session. */
  private final RemoteDbSessionImpl serverSession;

  /** class handled by this delegate. */
  private final Class<T> servicedClass;


  /**
   * Creates a delegate.
   * <p>
   * Note that we don't extend {@link UnicastRemoteObject} to allow
   * adding interceptors via dynamic proxies. Therefore, the object
   * must be explicitly exported by
   * {@link RemoteDbSessionImpl#exportRemoteDelegate(org.tentackle.persist.rmi.RemoteDelegate)}.
   *
   * @param serverSession the RMI serverSession
   * @param servicedClass the class the delegate provides service for
   * @throws RemoteException if creating the delegate failed
   */
  public RemoteDelegateImpl(RemoteDbSessionImpl serverSession, Class<T> servicedClass)
         throws RemoteException {

    this.serverSession = serverSession;
    this.servicedClass = servicedClass;
  }


  /**
   * Gets the server session.
   *
   * @return the session
   */
  public RemoteDbSessionImpl getServerSession() {
    return serverSession;
  }


  /**
   * Gets the db (which is the db of the serverSession).
   * @return the db
   */
  public Db getSession() {
    return serverSession.getSession();
  }


  /**
   * Gets the serviced class.
   *
   * @return the class
   */
  @Override
  public Class<T> getServicedClass() {
    return servicedClass;
  }


  /**
   * Does any initialization after construction.
   * <p>
   * The default implementation does nothing.
   */
  public void initialize() {
    // default does nothing
  }


  /**
   * Creates a remote exception.
   *
   * @param t the cause
   * @return the exception
   */
  public RemoteException createException(Throwable t) {
    RemoteException rex = new RemoteException(toString(), t);
    rex.setStackTrace(t.getStackTrace());
    return rex;
  }

  @Override
  public String toString() {
    return "servicedClass=" + servicedClass.getName() + ", session=" + getServerSession();
  }

}
