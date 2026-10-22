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

import org.tentackle.trip.Transport;
import org.tentackle.trip.impl.TripError;
import org.tentackle.trip.remote.RemoteInvocationHandler;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Client-side invocation handler for {@link RemoteDbDelegate}s.<br>
 * Translates a missing delegate on the server, which means that the remote session was closed,
 * into a {@link RemoteSessionClosedException}.
 *
 * @see DbTripFactory
 */
public class DbRemoteInvocationHandler extends RemoteInvocationHandler {

  /**
   * Creates a remote invocation handler.
   *
   * @param remoteId  the ID of the remote object
   * @param transport the transport to use for remote calls
   */
  public DbRemoteInvocationHandler(UUID remoteId, Transport transport) {
    super(remoteId, transport);
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      return super.invoke(proxy, method, args);
    }
    catch (TripError te) {
      if (te.isNoSuchDelegate()) {
        // the only possible reason is that the remote session was closed
        throw new RemoteSessionClosedException(te.getMessage());
      }
      throw te;
    }
  }
}
