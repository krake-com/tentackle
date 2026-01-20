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

import org.tentackle.common.Service;
import org.tentackle.session.Session;

/**
 * Default domain context factory.
 *
 * @author harald
 */
@Service(DomainContextFactory.class)
public class DefaultDomainContextFactory implements DomainContextFactory {

  /**
   * Creates the domain context factory.
   */
  public DefaultDomainContextFactory() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public DomainContext create() {
    return new DefaultDomainContext();
  }

  @Override
  public DomainContext create(Session session) {
    return new DefaultDomainContext(session);
  }

  @Override
  public DomainContext create(Session session, boolean sessionImmutable) {
    return new DefaultDomainContext(session, sessionImmutable);
  }

  @Override
  public <T extends PersistentDomainObject<T>> DomainContext create(T contextPdo) {
    return contextPdo.getDomainContext().clone();
  }

}
