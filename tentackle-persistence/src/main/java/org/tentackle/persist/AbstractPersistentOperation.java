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

package org.tentackle.persist;

import org.tentackle.dbms.AbstractDbOperation;
import org.tentackle.dbms.Db;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainDelegate;
import org.tentackle.pdo.Operation;
import org.tentackle.pdo.OperationMethodCache;
import org.tentackle.pdo.OperationMethodCacheProvider;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistenceDelegate;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.PersistentOperation;
import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * A {@code AbstractPersistentOperation} provides methods that are not part of {@link PersistentOperation}s
 * and is associated to a {@link DomainContext}. Complex transactions are usually
 * {@code AbstractPersistentOperation}s. {@code AbstractPersistentOperation}s are remote capable.
 *
 * @param <T> the PO class (interface)
 * @param <P> the operation implementation class
 * @author harald
 */
public abstract class AbstractPersistentOperation<T extends Operation<T>, P extends AbstractPersistentOperation<T,P>>
       extends AbstractDbOperation<P>
       implements PersistenceDelegate<T>, PersistentOperation<T>, EffectiveClassProvider<T>, OperationMethodCacheProvider<T> {

  @Serial
  private static final long serialVersionUID = 1L;

  private DomainContext context;                  // application domain context
  private transient boolean contextImmutable;     // true if domain context cannot be changed anymore
  private T operation;                            // the operation proxy instance this is a delegate for


  /**
   * Creates an operation object.
   *
   * @param operation the operation object this is a delegate for
   * @param context the database context
   */
  public AbstractPersistentOperation(T operation, DomainContext context) {
    this.operation = operation;
    setDomainContext(context);
  }

  /**
   * Creates an operation object without a domain context for a given session.<br>
   * Notice: the application must set the domain context.
   *
   * @param operation the operation object this is a delegate for
   * @param session the session (must be an instance of {@link Db}).
   */
  public AbstractPersistentOperation(T operation, Session session) {
    super((Db) session);
    this.operation = operation;
  }

  /**
   * Creates an operation object without a database context.<p>
   * Note: the application must set the context.
   *
   * @param operation the persistent operation object this is a delegate for
   */
  public AbstractPersistentOperation(T operation) {
    super();
    this.operation = operation;
  }

  /**
   * Creates an operation object without a database context.
   */
  public AbstractPersistentOperation() {
    super();
  }


  @Override
  public DomainDelegate<T> getDomainDelegate() {
    return operation.getDomainDelegate();
  }


  @Override
  public Class<T> getEffectiveClass() {
    return operation.getEffectiveClass();
  }

  @Override
  public List<Class<? super T>> getEffectiveSuperClasses() {
    return operation.getEffectiveSuperClasses();
  }

  /**
   * {@inheritDoc}
   * <p>
   * In fact, {@link PersistentOperationClassVariables} is an extension of
   * {@link org.tentackle.dbms.DbOperationClassVariables}.
   * So this fulfills the requirements for AbstractDbObject
   */
  @Override
  public PersistentOperationClassVariables<T,P> getClassVariables() {
    throw new PersistenceException("classvariables undefined for " + getClass());
  }

  @Override
  public OperationMethodCache<T> getOperationMethodCache() {
    return getClassVariables().methodCache;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Overridden to set the Db in DomainContext as well.
   */
  @Override
  public void setSession(Session session)  {
    if (context != null)  {
      context.setSession(session);
    }
    super.setSession(session);
  }


  /**
   * Sets the database context.
   *
   * @param context the domain context
   */
  @Override
  public void setDomainContext(DomainContext context)  {
    if (this.context != context) {
      assertDomainContextMutable();
      this.context = Objects.requireNonNull(context, "context");
      setSessionHolder(context);
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

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation returns -1.
   */
  @Override
  public long getContextId() {
    return -1;
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation returns the PDO's DomainContext.
   */
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
    Session session = Session.getCurrentSession();
    if (session != null) {
      session = null;   // use the thread-local session for the new context
    }
    else  {
      session = getSession();
    }
    return Pdo.createDomainContext(session);
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


  /**
   * Configures a within an operation remotely retrieved PDO.
   *
   * @param context the local domain context
   * @param pdo the PDO
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public <X extends PersistentDomainObject<X>> void configureRemoteObject(DomainContext context, X pdo) {
    ((AbstractPersistentObject) pdo.getPersistenceDelegate()).configureRemoteObject(context, pdo);
  }

  /**
   * Configures a within an operation remotely retrieved collection of PDOs.
   *
   * @param context the local domain context
   * @param pdos the PDOs to configure
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public <X extends PersistentDomainObject<X>> void configureRemoteObjects(DomainContext context, Collection<X> pdos) {
    if (pdos != null && !pdos.isEmpty()) {
      X pdo = pdos.iterator().next();
      ((AbstractPersistentObject) pdo.getPersistenceDelegate()).configureRemoteObjects(context, pdos);
    }
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
