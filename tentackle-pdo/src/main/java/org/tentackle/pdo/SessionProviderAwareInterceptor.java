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

import org.tentackle.reflect.AbstractInterceptor;
import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionProvider;

/**
 * Base implementation for interceptors on {@link SessionProvider}s.
 */
public abstract class SessionProviderAwareInterceptor extends AbstractInterceptor {

  /**
   * Parent constructor.
   */
  public SessionProviderAwareInterceptor() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Returns the session from the given proxy.<br>
   * Throws {@link PersistenceException} if the proxy is not a {@link SessionProvider} or the session is null.
   *
   * @param orgProxy the proxy object
   * @return the session, never null
   */
  protected Session getSession(Object orgProxy) {
    if (orgProxy instanceof SessionProvider sessionProvider) {
      Session session = sessionProvider.getSession();
      if (session == null) {
        throw new PersistenceException("no session provided by " + sessionProvider);
      }
      return session;
    }
    throw new PersistenceException(EffectiveClassProvider.getEffectiveClass(orgProxy) + " is not a SessionProvider");
  }

}
