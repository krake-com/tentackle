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
import org.tentackle.pdo.DomainObject;
import org.tentackle.pdo.PersistenceDelegate;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.session.Session;

import java.io.Serial;

/**
 * A mocked domain object.
 *
 * @param <T> the PDO class
 * @param <D> the domain object class
 * @author harald
 */
public class MockDomainObject<T extends PersistentDomainObject<T>, D extends MockDomainObject<T,D>>
       implements DomainObject<T> {

  /**
   * Exception message if the method is not implemented.
   */
  public static final String UNSUPPORTED = "not implemented";

  @Serial
  private static final long serialVersionUID = 1L;


  private T pdo;   // the pdo instance this is a delegate for


  /**
   * Creates an application domain object.
   *
   * @param pdo the persistent domain object this is a delegate for
   */
  public MockDomainObject(T pdo) {
    this.pdo = pdo;
  }


  /**
   * Creates an application domain object.
   */
  public MockDomainObject() {
  }


  @Override
  public T getPdo() {
    return pdo;
  }

  @Override
  public T me() {
    return pdo;
  }

  /**
   * Sets the PDO.<br>
   *
   * @param pdo the pdo
   */
  public void setPdo(T pdo) {
    this.pdo = pdo;
  }

  @Override
  public String toGenericString() {
    return super.toString();
  }

  @Override
  public boolean isUniqueDomainKeyProvided() {
    return false;
  }

  @Override
  public Class<?> getUniqueDomainKeyType() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public Object getUniqueDomainKey() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public void setUniqueDomainKey(Object domainKey) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public T findByUniqueDomainKey(Object domainKey) {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public DomainContext getDomainContext() {
    return pdo.getDomainContext();
  }

  @Override
  public String getSingular() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public String getPlural() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public PersistenceDelegate<T> getPersistenceDelegate() {
    throw new UnsupportedOperationException(UNSUPPORTED);
  }

  @Override
  public Session getSession() {
    return pdo.getSession();
  }

}
