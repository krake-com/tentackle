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

package org.tentackle.trip.transport;

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.io.UriHelper;
import org.tentackle.misc.AbstractPool;
import org.tentackle.trip.Connection;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TripRuntimeException;

import java.util.Map;
import java.util.Objects;

/**
 * Base class for connection pools.<br>
 * The pooling parameters are optionally defined in the URI query string:
 * <ul>
 *   <li>{@code initial}: the initial pool size (default 1)</li>
 *   <li>{@code increment}: the number of connections to add if all are in use (default 1)</li>
 *   <li>{@code minimum}: the minimum pool size (default 1)</li>
 *   <li>{@code maximum}: the maximum pool size (default 0, i.e. unlimited)</li>
 *   <li>{@code idle}: the idle timeout in minutes (default 10)</li>
 *   <li>{@code usage}: the maximum usage time in minutes (default 0, i.e. unlimited)</li>
 * </ul>
 *
 * @see org.tentackle.misc.AbstractPool
 */
public abstract class AbstractConnectionPool extends AbstractPool<Connection> {

  private final Transport transport;
  private final Dictionary dictionary;

  /**
   * Creates a connection pool.
   *
   * @param transport     the transport associated with this pool
   * @param dictionary    the dictionary associated with this pool
   */
  public AbstractConnectionPool(Transport transport, Dictionary dictionary) {
    Map<String, Integer> queryMap = UriHelper.getQueryParameters(transport.getUri().getQuery(), Integer::parseInt);
    int initialSize = queryMap.getOrDefault("initial", 1);
    int incrementSize = queryMap.getOrDefault("increment", 1);
    int minimumSize = queryMap.getOrDefault("minimum", 1);
    int maximumSize = queryMap.getOrDefault("maximum", 0);
    int idleMinutes = queryMap.getOrDefault("idle", 10);
    int usageMinutes = queryMap.getOrDefault("usage", 0);
    super(transport.toString(), initialSize, incrementSize, minimumSize, maximumSize, idleMinutes, usageMinutes);
    this.transport = Objects.requireNonNull(transport);
    this.dictionary = Objects.requireNonNull(dictionary);

  }

  /**
   * Gets the transport associated with this pool.
   *
   * @return the transport
   */
  public Transport getTransport() {
    return transport;
  }

  /**
   * Gets the dictionary associated with this pool.
   *
   * @return the dictionary
   */
  public Dictionary getDictionary() {
    return dictionary;
  }

  @Override
  protected TentackleRuntimeException createException(String msg) {
    return new TripRuntimeException(msg);
  }

  @Override
  protected TentackleRuntimeException createException(Connection connection, String msg) {
    return new TripRuntimeException(connection + ": " + msg);
  }

}
