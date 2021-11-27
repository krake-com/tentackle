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


package org.tentackle.pdo;

import org.tentackle.common.ServiceFactory;
import org.tentackle.session.Session;


interface DomainContextFactoryHolder {
  DomainContextFactory INSTANCE = ServiceFactory.createService(
              DomainContextFactory.class, DefaultDomainContextFactory.class);
}


/**
 * Factory for {@link DomainContext}.
 *
 * @author harald
 */
public interface DomainContextFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static DomainContextFactory getInstance() {
    return DomainContextFactoryHolder.INSTANCE;
  }


  /**
   * Creates a mutable domain context.
   *
   * @param session the session, null if thread-local
   * @return the domain context
   * @see Session#makeCurrent()
   */
  DomainContext create(Session session);

  /**
   * Creates a domain context.
   *
   * @param session the session, null if thread-local
   * @param sessionImmutable true if session cannot be changed anymore
   * @return the domain context
   * @see Session#makeCurrent()
   */
  DomainContext create(Session session, boolean sessionImmutable);

  /**
   * Creates a domain context for a given context root PDO.
   * <p>
   * This method must be overridden by an application-specific {@link DomainContextFactory}!
   * The default implementation just returns a clone of the given PDO's domain context.
   *
   * @param contextPdo the PDO spawning a domain context space in a multi-tenant system
   * @param <T> the PDO type
   * @return the domain context
   */
  <T extends PersistentDomainObject<T>> DomainContext create(T contextPdo);

}
