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

package org.tentackle.trip.impl;

import org.tentackle.trip.Transport;
import org.tentackle.trip.TransportKey;
import org.tentackle.trip.TransportManager;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of a {@link TransportManager}.<br>
 * Transports are leased: each request increments a lease counter, each release decrements it.
 * The transport is closed when the last lease is released.<br>
 * Transports are unique by scheme, host, and port. Requesting a transport for an endpoint already in use
 * with different query parameters fails with a {@link TripRuntimeException}.
 */
public class DefaultTransportManager implements TransportManager {

  private final Map<TransportKey, TransportLease> transports;

  /**
   * Creates a new transport manager.
   */
  public DefaultTransportManager() {
    transports = new HashMap<>();
  }

  @Override
  public synchronized Transport requestTransport(URI uri) {
    TransportKey key = new TransportKey(uri);
    TransportLease lease = transports.get(key);
    if (lease == null) {
      lease = new TransportLease(TripFactory.getInstance().createTransport(uri));
      transports.put(key, lease);
    }
    else if (!lease.getTransport().getTransportKey().isSameQuery(key)) {
      // key.toString() does not reveal the query parameters
      throw new TripRuntimeException("transport " + key + " already in use with different query parameters");
    }
    return lease.request();
  }

  @Override
  public synchronized void releaseTransport(Transport transport) {
    TransportLease lease = transports.get(transport.getTransportKey());
    if (lease != null) {
      if (transport != lease.getTransport()) { // must be the same instance
        throw new TripRuntimeException("transport " + transport + " not maintained by this manager");
      }
      lease.release();
    }
    else {
      throw new TripRuntimeException("transport " + transport + " not requested before");
    }
  }

  @Override
  public synchronized void closeAllTransports() {
    transports.values().forEach(lease -> lease.getTransport().close());
    transports.clear();
  }


  private class TransportLease {

    private final Transport transport;
    private int leaseCount;

    private TransportLease(Transport transport) {
      this.transport = transport;
    }

    private Transport getTransport() {
      return transport;
    }

    private Transport request() {
      leaseCount++;
      return transport;
    }

    private void release() {
      if (--leaseCount <= 0) {
        transports.remove(transport.getTransportKey());
        transport.close();
      }
    }
  }

}
