/*
 * Tentackle - https://tentackle.org.
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

import org.tentackle.reflect.DummyDelegate;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;


/**
 * Dummy persistent delegate if operation provides only the domain part.<br>
 * The {@link DefaultOperationFactory} uses it as the default persistence implementation,
 * so that operations without a persistence part still carry their domain context and session.
 *
 * @author harald
 * @param <T> the operation type
 */
public class DummyPersistentOperation<T extends Operation<T>>
       implements PersistentOperation<T>, OperationMethodCacheProvider<T>, DummyDelegate, Serializable {

  @Serial
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
   * @param context the domain context
   */
  public DummyPersistentOperation(T operation, DomainContext context) {
    this.operation = operation;
    this.context = context;
  }

  /**
   * Creates an operation object without a domain context
   * for a given session.<p>
   * Note: the application must set the context.
   *
   * @param operation the operation object this is a delegate for
   * @param session the session
   */
  public DummyPersistentOperation(T operation, Session session) {
    this.operation = operation;
    this.session = session;
  }

  /**
   * Creates an operation object without a domain context.<p>
   * Note: the application must set the context.
   *
   * @param operation the persistent operation object this is a delegate for
   */
  public DummyPersistentOperation(T operation) {
    this.operation = operation;
  }

  /**
   * Creates an operation object without a domain context.
   */
  public DummyPersistentOperation() {
  }


  @Override
  public DomainDelegate<T> getDomainDelegate() {
    return operation.getDomainDelegate();
  }

  @Override
  public void setSession(Session session)  {
    if (context != null)  {
      context.setSession(session);    // the context checks its own immutability
    }
    else if (sessionImmutable && this.session != session) {
      throw new PersistenceException(this.session, "illegal attempt to change the immutable session of " + this +
                                                   " from " + this.session + " to " + session);
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
      assertDomainContextMutable();
      this.context = Objects.requireNonNull(context, "context");
      determineContextId();
    }
  }

  @Override
  public DomainContext getDomainContext() {
    return context;
  }


  @Override
  public T me() {
    return operation;
  }

  @Override
  public T getOperation() {
    return operation;
  }

  /**
   * Sets the operation.<br>
   *
   * @param operation the operation
   */
  public void setOperation(T operation) {
    this.operation = operation;
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
   * The default implementation just returns a new {@link DomainContext}.
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

  /**
   * {@inheritDoc}
   * <p>
   * Not used by the {@link OperationInvocationHandler}, since it uses the global cache for dummy delegates
   * (see {@link org.tentackle.reflect.Mixin#isDummy()}).
   */
  @Override
  public OperationMethodCache<T> getOperationMethodCache() {
    return OperationMethodCache.getCache(operation.getEffectiveClass());
  }
}
