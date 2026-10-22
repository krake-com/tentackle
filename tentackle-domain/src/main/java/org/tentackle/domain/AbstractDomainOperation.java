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

package org.tentackle.domain;

import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainOperation;
import org.tentackle.pdo.Operation;
import org.tentackle.pdo.PersistenceDelegate;
import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.session.Session;
import org.tentackle.trip.TripReferencable;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Base domain implementation of an {@link Operation}.
 * <p>
 * Every domain delegate of an operation extends this class. It holds a reference to the operation proxy it is
 * a delegate for and forwards the domain context and session to the persistence delegate, so the domain
 * logic never accesses the database directly. All business logic lives in the concrete subclasses.
 * <p>
 * Subclasses must provide two public constructors: one with the operation as its only argument, used when the
 * factory creates the operation and its delegates in one go, and a no-args constructor, used when the domain
 * delegate is created on its own and linked to the operation later via {@link #setOperation(Operation)}.
 * Both are enforced by the annotation processor for {@code @DomainOperationService}.
 *
 * @param <T> the operation type (interface)
 * @param <D> the domain implementation type
 * @author harald
 */
@TripReferencable(TripReferencable.NEVER)    // only proxies are referenced, not implementations
public abstract class AbstractDomainOperation<T extends Operation<T>, D extends AbstractDomainOperation<T,D>>
       implements DomainOperation<T>, EffectiveClassProvider<T>, Serializable {

  @Serial
  private static final long serialVersionUID = 1L;


  /**
   * The operation proxy this is a delegate for.
   */
  private T operation;


  /**
   * Creates the domain delegate for the given operation.
   *
   * @param operation the operation proxy this is a delegate for
   */
  public AbstractDomainOperation(T operation) {
    this.operation = operation;
  }


  /**
   * Creates the domain delegate without an operation.<br>
   * The operation must be linked later via {@link #setOperation(Operation)}.
   */
  public AbstractDomainOperation() {
  }


  @Override
  public PersistenceDelegate<T> getPersistenceDelegate() {
    return operation.getPersistenceDelegate();
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
   * Sets the operation this is a delegate for.<br>
   * Invoked by {@link DefaultDomainDelegateLinker}.
   *
   * @param operation the operation proxy
   */
  public void setOperation(T operation) {
    this.operation = operation;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Forwarded to the persistence delegate.
   */
  @Override
  public DomainContext getDomainContext() {
    return operation.getPersistenceDelegate().getDomainContext();
  }

  /**
   * {@inheritDoc}
   * <p>
   * Forwarded to the persistence delegate.
   */
  @Override
  public Session getSession() {
    return operation.getPersistenceDelegate().getSession();
  }

  @Override
  public Class<T> getEffectiveClass() {
    return operation.getEffectiveClass();
  }

  @Override
  public List<Class<? super T>> getEffectiveSuperClasses() {
    return operation.getEffectiveSuperClasses();
  }
}
