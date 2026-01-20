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

package org.tentackle.persist.trip;

import org.tentackle.dbms.trip.AbstractDbOperationRemoteDelegateImpl;
import org.tentackle.dbms.trip.RemoteDbSessionImpl;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Operation;
import org.tentackle.pdo.Pdo;
import org.tentackle.persist.AbstractPersistentOperation;

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
   * The PDO interface class.
   */
  protected final Class<T> opClass;

  /**
   * Operation with a default domain context associated to the delegate's session.
   */
  protected T operation;


  /**
   * Creates a delegate on the serverSession socket.
   *
   * @param serverSession the TRIP serverSession
   * @param clazz the class of the persistence implementation
   * @param opClass the operation class
   */
  public AbstractPersistentOperationRemoteDelegateImpl(RemoteDbSessionImpl serverSession, Class<P> clazz, Class<T> opClass) {
    super(serverSession, clazz);
    this.opClass = opClass;
  }


  /**
   * Instantiates a singe object and connect it to the database context.
   * The object is used for all methods not returning a new object and thus
   * minimizes object construction.
   */
  @Override
  @SuppressWarnings("unchecked")
  public void initialize() {
    /*
     * Try to set a default context.
     * This will fail in apps that extend the domain context, but is useful for other apps.
     * We ignore any thrown exception safely.
     */
    try {
      operation = newInstance(Pdo.createDomainContext(getSession()));
    }
    catch (RuntimeException e) {
      /*
       * Application requires an extended domain context.
       * Use the local operation object as it is for simple methods.
       * In all other cases, the application must pass the context via the remote method
       * and set the context explicitly.
       */
      operation = newInstance(null);    // create without domain context
    }
    dbOperation = (P) operation.getPersistenceDelegate();
  }


  /**
   * Sets the domain context in the default operation object.
   *
   * @param context the domain context
   */
  protected void setDomainContext(DomainContext context) {
    // set the session in the context first as this will be copied to the object below
    context.setSession(getSession());   // session in context is transient, so we must set it here
    dbOperation.setDomainContext(context);
  }


  /**
   * Creates a new instance of the required class in the given context
   * for methods that return a new operation.
   *
   * @param context the domain context
   * @return the created operation
   */
  protected T newInstance(DomainContext context) {
    if (context != null) {
      context.setSession(getSession());
    }
    return context == null ? Pdo.createOperation(opClass, getSession()) : Pdo.createOperation(opClass, context);
  }

}
