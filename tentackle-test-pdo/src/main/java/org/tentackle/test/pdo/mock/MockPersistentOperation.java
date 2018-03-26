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


package org.tentackle.test.pdo.mock;

import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainDelegate;
import org.tentackle.pdo.Operation;
import org.tentackle.pdo.PersistentOperation;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;

/**
 * A mocked persistence object.
 *
 * @param <T> the pdo type
 * @author harald
 */
public class MockPersistentOperation<T extends Operation<T>>
       implements PersistentOperation<T> {


  private DomainContext context;                  // application database context
  private T operation;                            // the operation instance this is a delegate for
  private transient Session session;              // the session
  private transient boolean sessionImmutable;     // true if session is immutable


  /**
   * Creates an operation object.
   *
   * @param operation the operation object this is a delegate for
   * @param context the database context
   */
  public MockPersistentOperation(T operation, DomainContext context) {
    this.operation = operation;
    this.context = context;
    this.session = context == null ? null : context.getSession();
  }


  /**
   * Creates an operation object without a domain context
   * for a given connection.<p>
   * Note: the application must set the context.
   *
   * @param operation the operation object this is a delegate for
   * @param session the session (must be an instance of {@link Session}).
   */
  public MockPersistentOperation(T operation, Session session) {
    this.operation = operation;
    this.session  = session;
  }

  /**
   * Creates an operation object without a database context.<p>
   * Note: the application must set the context.
   *
   * @param operation the operation object this is a delegate for
   */
  public MockPersistentOperation(T operation) {
    this.operation = operation;
  }


  /**
   * Creates an operation object without a database context.
   */
  public MockPersistentOperation() {
  }


  @Override
  public void setSessionImmutable(boolean sessionImmutable) {
    this.sessionImmutable = sessionImmutable;
  }

  @Override
  public boolean isSessionImmutable() {
    return sessionImmutable;
  }

  @Override
  public void setSession(Session session) {
    if (isSessionImmutable() && this.session != session) {
      throw new PersistenceException(this.session, "illegal attempt to change the immutable session of " + this +
                                              " from " + this.session + " to " + session);
    }
    this.session = session;
  }

  @Override
  public Session getSession() {
    return session;
  }


  @Override
  public DomainContext getDomainContext() {
    return context;
  }

  @Override
  public void setDomainContext(DomainContext context) {
    this.context = context;
  }

  @Override
  public void determineContextId() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public long getContextId() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public DomainContext getBaseContext() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public DomainContext createValidContext() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean isDomainContextImmutable() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void setDomainContextImmutable(boolean contextImmutable) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public PersistentOperation<T> getDelegate() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public PersistentOperation<T> newInstance() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public T getOperation() {
    return operation;
  }

  @Override
  @SuppressWarnings("deprecation")
  public T operation() {
    return operation;
  }

  @Override
  public T me() {
    return operation;
  }

  @Override
  public void setOperation(T operation) {
    this.operation = operation;
  }

  @Override
  public PersistentOperation<T> clonePersistentOperation() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public DomainDelegate<T> getDomainDelegate() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

}
