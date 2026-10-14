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

package org.tentackle.dbms.trip;

import org.tentackle.session.SessionInfo;
import org.tentackle.trip.Registry;
import org.tentackle.trip.Transport;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;

/**
 * Stubs to create {@link RemoteDbSessionImpl}s without a TRIP-server or client.
 *
 * @author harald
 */
final class RemoteSessionTestSupport {

  /**
   * Creates a connection that does not create sessions by itself.
   *
   * @return the connection
   */
  static RemoteDbConnectionImpl createConnection() {
    return new RemoteDbConnectionImpl(null, createTransport()) {
      @Override
      public RemoteDbSession createSession(SessionInfo clientInfo) {
        throw new UnsupportedOperationException();
      }
    };
  }

  /**
   * Creates a transport whose registry knows no delegates.
   *
   * @return the transport
   */
  static Transport createTransport() {
    Registry registry = createStub(Registry.class);
    return (Transport) Proxy.newProxyInstance(Transport.class.getClassLoader(), new Class<?>[] { Transport.class },
                                               (_, method, _) -> method.getName().equals("getRegistry") ?
                                                                 registry : defaultValue(method.getReturnType()));
  }

  @SuppressWarnings("unchecked")
  private static <T> T createStub(Class<T> clazz) {
    return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] { clazz },
                                      (_, method, _) -> defaultValue(method.getReturnType()));
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive() || type == void.class) {
      return null;
    }
    // the zero value of the primitive type, i.e. the default of a one-element array
    return Array.get(Array.newInstance(type, 1), 0);
  }


  private RemoteSessionTestSupport() {
    // no instances
  }

}
