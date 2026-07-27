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


package org.tentackle.pdo.mock;

import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainOperation;
import org.tentackle.pdo.Operation;
import org.tentackle.pdo.PersistenceDelegate;
import org.tentackle.session.Session;

import java.io.Serial;

/**
 * A mocked domain object.
 *
 * @param <T> the PDO class
 * @param <D> the domain object class
 * @author harald
 */
public class MockDomainOperation<T extends Operation<T>, D extends MockDomainOperation<T,D>>
       implements DomainOperation<T> {

  /**
   * Exception message if the method is not implemented.
   */
  public static final String UNSUPPORTED = "not implemented";

  @Serial
  private static final long serialVersionUID = 1L;


  private T operation;   // the operation instance this is a delegate for


  /**
   * Creates an operation domain object.
   *
   * @param operation the operation object this is a delegate for
   */
  public MockDomainOperation(T operation) {
    this.operation = operation;
  }

  /**
   * Creates an application domain object.
   */
  public MockDomainOperation() {
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

  @Override
  public DomainContext getDomainContext() {
    return operation.getDomainContext();
  }

  @Override
  public Session getSession() {
    return operation.getSession();
  }

  @Override
  public PersistenceDelegate<T> getPersistenceDelegate() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

}
