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
import org.tentackle.pdo.DomainObject;
import org.tentackle.pdo.PersistenceDelegate;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.session.Session;

/**
 * A mocked domain object.
 *
 * @param <T> the PDO class
 * @param <D> the domain object class
 * @author harald
 */
public class MockDomainObject<T extends PersistentDomainObject<T>, D extends MockDomainObject<T,D>>
       implements DomainObject<T> {

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
  public T pdo() {
    return pdo;
  }

  @Override
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
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public Object getUniqueDomainKey() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void setUniqueDomainKey(Object domainKey) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public T findByUniqueDomainKey(Object domainKey) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public DomainObject<T> cloneDomainObject() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public DomainContext getDomainContext() {
    return pdo.getDomainContext();
  }

  @Override
  public String getSingular() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public String getPlural() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public PersistenceDelegate<T> getPersistenceDelegate() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public Session getSession() {
    return pdo.getSession();
  }

}
