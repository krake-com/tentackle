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

package org.tentackle.domain;

import java.io.Serializable;
import java.util.List;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainOperation;
import org.tentackle.pdo.Operation;
import org.tentackle.pdo.PersistenceDelegate;
import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.session.Session;

/**
 * The abstract domain operation.
 *
 * @author harald
 * @param <T> the operation type
 * @param <D> the domain implementation type
 */
public abstract class AbstractDomainOperation<T extends Operation<T>, D extends AbstractDomainOperation<T,D>>
       implements DomainOperation<T>, EffectiveClassProvider<T>, Cloneable, Serializable {

  private static final long serialVersionUID = 1L;


  private T operation;   // the operation instance this is a delegate for


  /**
   * Creates an operation domain object.
   *
   * @param operation the persistent domain object this is a delegate for
   */
  public AbstractDomainOperation(T operation) {
    this.operation = operation;
  }


  /**
   * Creates an operation domain object.
   */
  public AbstractDomainOperation() {
  }

  @Override
  @SuppressWarnings("unchecked")
  public DomainOperation<T> clone() {
    DomainOperation<T> obj;
    try {
      obj = (DomainOperation<T>) super.clone();
    }
    catch (CloneNotSupportedException ex) {
      throw new InternalError();    // should never happen
    }
    obj.setOperation(null);
    return obj;
  }

  @Override
  public DomainOperation<T> cloneDomainOperation() {
    return clone();
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

  @Override
  public void setOperation(T operation) {
    this.operation = operation;
  }

  @Override
  public DomainContext getDomainContext() {
    return operation.getPersistenceDelegate().getDomainContext();
  }

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
