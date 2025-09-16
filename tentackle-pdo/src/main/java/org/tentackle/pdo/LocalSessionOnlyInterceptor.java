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

import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;

import java.lang.reflect.Method;

/**
 * Implementation for the {@link LocalSessionOnly} interception.
 *
 * @author harald
 */
public final class LocalSessionOnlyInterceptor extends SessionProviderAwareInterceptor {

  /**
   * Creates a {@link LocalSessionOnly} interceptor.
   */
  public LocalSessionOnlyInterceptor() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public Object proceed(Object proxy, Method method, Object[] args,
                        Object orgProxy, Method orgMethod, Object[] orgArgs) throws Throwable {

    Session session = getSession(orgProxy);
    if (session.isRemote()) {
      throw new PersistenceException(session, "not a local session");
    }
    return method.invoke(proxy, args);
  }

}
