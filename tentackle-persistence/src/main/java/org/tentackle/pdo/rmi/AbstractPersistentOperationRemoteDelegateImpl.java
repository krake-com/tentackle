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


package org.tentackle.pdo.rmi;

import java.rmi.RemoteException;
import org.tentackle.pdo.AbstractPersistentOperation;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Operation;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistenceException;
import org.tentackle.persist.rmi.AbstractDbOperationRemoteDelegateImpl;
import org.tentackle.persist.rmi.RemoteDbSessionImpl;



/**
 * Implementation of the remote delegate for {@link AbstractPersistentOperation}.<br>
 *
 * @param <T> the {@code AbstractPersistentOperation} class
 * @param <P> the database operation class
 * @author harald
 */
public class AbstractPersistentOperationRemoteDelegateImpl<T extends Operation<T>, P extends AbstractPersistentOperation<T,P>>
       extends AbstractDbOperationRemoteDelegateImpl<P>
       implements AbstractPersistentOperationRemoteDelegate<T,P> {


  /**
   * Creates a delegate on the serverSession socket.
   *
   * @param serverSession the RMI serverSession
   * @param clazz the subclass of DbObject
   * @throws java.rmi.RemoteException
   */
  public AbstractPersistentOperationRemoteDelegateImpl(RemoteDbSessionImpl serverSession, Class<P> clazz) throws RemoteException {
    super(serverSession, clazz);
  }


  /**
   * Instantiates a singe object and connect it to the database context.
   * The object is used for all methods not returning a new object and thus
   * minimizes object construction.
   */
  @Override
  public void initialize() {
    /**
     * Try to set a default contextDb.
     * This will fail in apps that extend the contextdb, but is useful for other apps.
     */
    try {
      dbOperation = newInstance(Pdo.createDomainContext(getSession()));
    }
    catch (Exception e) {
      /**
       * Application requires an extended contextdb.
       * Try construction with a session only.
       */
      dbOperation = newInstance();
    }
  }


  /**
   * Sets the contextdb in the default object.
   *
   * @param context the domain context
   */
  protected void setContextDb(DomainContext context) {
    // set the db in the contextDb first as this will be copied to the object below
    context.setSession(getSession());   // db in contextDb is transient, so we must set it here
    dbOperation.setDomainContext(context);
  }


  /**
   * Creates a new instance of the required class in the given context
   * for methods that return a new object.
   *
   * @param context the domain context
   * @return the created object
   * @throws java.rmi.RemoteException
   */
  protected P newInstance(DomainContext context) {
    try {
      context.setSession(getSession());
      return getServicedClass().getConstructor(DomainContext.class).newInstance(context);
    }
    catch (Exception e) {
      throw new PersistenceException("creating object for class '" + getServicedClass() + "' in failed", e);
    }
  }

}
