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

import org.tentackle.dbms.AbstractDbOperation;
import org.tentackle.dbms.Db;
import org.tentackle.session.PersistenceException;

import java.lang.reflect.InvocationTargetException;

/**
 * Base class for the remote delegate of AbstractDbOperation.<br>
 * All other subclasses of AbstractDbOperation should extend AbstractDbOperationRemoteDelegateImpl to reflect
 * the class hierarchy below AbstractDbOperation.
 *
 * @param <P> the database operation class
 * @author harald
 */
public class AbstractDbOperationRemoteDelegateImpl<P extends AbstractDbOperation<P>>
       extends RemoteDbDelegateImpl<P>
       implements AbstractDbOperationRemoteDelegate<P> {


  /**
   * operation object associated to the delegate's session.
   */
  protected P dbOperation;


  /**
   * Creates a delegate on the serverSession socket.
   *
   * @param serverSession is the TRIP server session
   * @param clazz is the subclass of AbstractDbOperation
   */
  public AbstractDbOperationRemoteDelegateImpl(RemoteDbSessionImpl serverSession, Class<P> clazz) {
    super(serverSession, clazz);
  }


  /**
   * Instantiates a singe object and connect it to the db.
   * <p>
   * The object is used for all methods not returning a new object and thus
   * minimizes object construction.
   */
  @Override
  public void initialize() {
    dbOperation = newInstance();
  }


  /**
   * Creates a new instance of the required class
   * for methods that return a new object.
   * @return the new object
   */
  protected P newInstance() {
    try {
      return getServicedClass().getDeclaredConstructor(Db.class).newInstance(getSession());
    }
    catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException |
           InvocationTargetException e) {
      throw new PersistenceException(getSession(), "creating object for class '" + getServicedClass() + "' failed", e);
    }
  }

}
