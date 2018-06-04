/*
 * Tentackle - http://www.tentackle.org.
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
 *
 */

package org.tentackle.pdo;

import java.io.Serializable;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;


/**
 * Dummy persistent delegate if operation provides only the domain part.
 *
 * @author harald
 * @param <T> the operation type
 */
@SuppressWarnings("deprecation")
public class DummyPersistentOperation<T extends Operation<T>> implements PersistentOperation<T>, Cloneable, Serializable {

  private static final long serialVersionUID = 1L;

  private DomainContext context;                  // application domain context
  private transient Session session;              // the session
  private transient boolean contextImmutable;     // true if domain context cannot be changed anymore
  private transient boolean sessionImmutable;     // true if session is immutable
  private T operation;                            // the operation proxy instance this is a delegate for

  /**
   * Creates an operation object.
   *
   * @param operation the operation object this is a delegate for
   * @param context the database context
   */
  public DummyPersistentOperation(T operation, DomainContext context) {
    this.operation = operation;
    this.context = context;
  }

  /**
   * Creates an operation object without a domain context
   * for a given connection.<p>
   * Note: the application must set the context.
   *
   * @param operation the operation object this is a delegate for
   * @param session the session (must be an instance of {@link Session}).
   */
  public DummyPersistentOperation(T operation, Session session) {
    this.operation = operation;
  }

  /**
   * Creates an operation object without a database context.<p>
   * Note: the application must set the context.
   *
   * @param operation the persistent operation object this is a delegate for
   */
  public DummyPersistentOperation(T operation) {
    this.operation = operation;
  }

  /**
   * Creates an operation object without a database context.
   */
  public DummyPersistentOperation() {
  }


  @Override
  public DomainDelegate<T> getDomainDelegate() {
    return operation.getDomainDelegate();
  }


  @Override
  @SuppressWarnings("unchecked")
  public DummyPersistentOperation<T> clone() {
    try {
      DummyPersistentOperation<T> obj = (DummyPersistentOperation<T>) super.clone();
      obj.setOperation(null);
      return obj;
    }
    catch (CloneNotSupportedException ex) {
      throw new InternalError();    // should never happen
    }
  }

  @Override
  public PersistentOperation<T> clonePersistentOperation() {
    return clone();
  }


  @Override
  public void setSession(Session session)  {
    if (context != null)  {
      context.setSession(session);
    }
    this.session = session;
  }

  @Override
  public Session getSession() {
    if (context != null) {
      return context.getSession();
    }
    return session;
  }

  @Override
  public void setDomainContext(DomainContext context)  {
    if (this.context != context) {
      if (context == null) {
        throw new IllegalArgumentException("domain context cannot be cleared to null");
      }
      assertDomainContextMutable();
      this.context = context;
      determineContextId();
    }
  }

  /**
   * Gets the database context.
   *
   * @return the domain context
   */
  @Override
  public DomainContext getDomainContext() {
    return context;
  }


  @Override
  public PersistentOperation<T> getDelegate() {
    return this;
  }


  @Override
  public PersistentOperation<T> newInstance() {
    try {
      @SuppressWarnings("unchecked")
      T obj = (T) getClass().newInstance();
      obj.setSession(getSession());
      return obj;
    }
    catch (InstantiationException | IllegalAccessException e) {
      throw new PersistenceException(getSession(), "creating new object failed", e);
    }
  }

  @Override
  public T operation() {
    return operation;
  }

  @Override
  public T me() {
    return operation;
  }

  @Override
  public T getOperation() {
    return operation;
  }

  @Override
  public void setOperation(T po) {
    this.operation = po;
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation does nothing (object living in a context
   * not depending on another object).
   */
  @Override
  public void determineContextId() {
  }

  @Override
  public long getContextId() {
    return -1;
  }

  @Override
  public DomainContext getBaseContext()  {
    return getDomainContext();
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation just returns a new {@link DomainContext}.<br>
   */
  @Override
  public DomainContext createValidContext() {
    Session s = Session.getCurrentSession();
    if (s != null) {
      s = null;   // use the thread-local session for the new context
    }
    else  {
      s = getSession();
    }
    return Pdo.createDomainContext(s);
  }


  /**
   * Returns whether the domain context is immutable.
   *
   * @return true if context cannot be changed
   */
  @Override
  public boolean isDomainContextImmutable() {
    return contextImmutable;
  }

  /**
   * Sets the immutable flag of the domain context.
   *
   * @param contextImmutable true if context cannot be changed
   */
  @Override
  public void setDomainContextImmutable(boolean contextImmutable) {
    this.contextImmutable = contextImmutable;
  }

  @Override
  public void setSessionImmutable(boolean sessionImmutable) {
    this.sessionImmutable = sessionImmutable;
  }

  @Override
  public boolean isSessionImmutable() {
    if (context != null) {
      return context.isSessionImmutable();
    }
    return sessionImmutable;
  }

  /**
   * Asserts that the domain context is mutable.
   */
  protected void assertDomainContextMutable() {
    if (isDomainContextImmutable()) {
      throw new PersistenceException(getSession(), "domain context is immutable");
    }
  }

}
