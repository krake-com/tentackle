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

package org.tentackle.trip;

import org.tentackle.trip.remote.RemoteCall;
import org.tentackle.trip.transport.TransportClientInfo;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * The transport layer.
 * <p>
 * Transports are used to send and receive serialized objects. Each transport is uniquely identified
 * by its id (UUID). Multiple transports can point to the same remote endpoint URI. A server accepts connections
 * on his transport from different clients, while each client has its own transport per server endpoint.
 * For outgoing connections, the transport provides a dictionary and a connection pool that is dynamically sized
 * and contains at least one connection.
 * For incoming connections, the transport provides a separate dictionary per client transport ID
 * so that all connections from the same client use the same dictionary.
 * <p>
 * Clients use the method {@link #getConnection()} to get a connection from the pool. When the
 * connection is no longer needed, the method {@link #putConnection(Connection)} must be invoked.
 * This typically happens when the response from a remote call was received.
 * <p>
 * Servers use the method {@link #accept()} to accept connections from clients.
 * For each accepted connection a handler (i.e., the consumer of the connection) is invoked
 * on a separate virtual thread. The virtual threads receive remote calls and dispatch them to
 * threads from a regular thread pool (not virtual threads). Each call is processed
 * by such a thread, which sends the result back to the client and returns to the pool.
 * <p>
 * Creating a transport object does not start any threads, nor does it connect to the remote endpoint.
 * This will happen when the first connection is requested, either by {@link #getConnection()} (client side) or
 * {@link #accept()} (server side).
 * <p>
 * It is not allowed to use the same transport for both server and client at the same time.
 */
public interface Transport {

  /**
   * Gets the URI of this transport.
   *
   * @return the transport URI
   */
  URI getUri();

  /**
   * Gets the transport key.<br>
   * Although transports are created using a URI, they are unique
   * by their TransportKey since the URI-path is maintained by the transport's registry.
   *
   * @return the transport key
   */
  TransportKey getTransportKey();

  /**
   * Gets the unique ID of this transport.
   *
   * @return the transport ID
   */
  UUID getId();

  /**
   * Gets a free connection from the pool.
   *
   * @return the connection
   */
  Connection getConnection();

  /**
   * Returns a connection to the pool.
   *
   * @param connection the connection
   */
  void putConnection(Connection connection);

  /**
   * Accepts connections from clients.
   */
  void accept();

  /**
   * Returns true if this transport is accepting connections.
   *
   * @return true if accepting, false otherwise
   */
  boolean isAccepting();

  /**
   * Gets the dictionary for client transport.
   *
   * @return the dictionary
   */
  Dictionary getClientDictionary();

  /**
   * Gets the dictionary for the server transport per client.
   * <p>
   * A dictionary lives as long as at least one connection of its client transport is open and is
   * garbage collected afterward. Since a client keeps its own dictionary for the lifetime of its
   * transport, a new dictionary may only be created for a client that does not hold any entry yet.
   * A client whose dictionary is populated while this side has none would send ordinals this side
   * never learned, so it is rejected and must establish a new transport. This also covers a client
   * outliving a restart of the server.
   *
   * @param id the client's transport UUID
   * @param clientDictionaryEmpty true if the client's dictionary is still empty, as told by the
   *        connection handshake
   * @return the dictionary; null if the client transport is stale and must be re-established
   */
  Dictionary getServerDictionary(UUID id, boolean clientDictionaryEmpty);

  /**
   * Gets the server's registry.
   *
   * @return the registry
   */
  Registry getRegistry();

  /**
   * Gets the client's remote lookup for the remote registry.
   *
   * @return the remote lookup
   */
  RemoteLookup getRemoteLookup();

  /**
   * Looks up a remote object by its name.
   *
   * @param clazz the interface of the remote object
   * @param name the symbolic name of the remote object
   * @return the remote object, never null
   * @param <T> the type of the remote object
   */
  <T> T lookup(Class<T> clazz, String name);

  /**
   * Gets the virtual thread pool for reading the calls from incoming connections.
   *
   * @return the thread pool
   */
  ExecutorService getConnectionThreadPool();

  /**
   * Creates a connection handler for the given streams.
   *
   * @param connectionName the name of the connection
   * @param inputStream the input stream
   * @param outputStream the output stream
   * @return the connection handler
   */
  Runnable createConnectionHandler(String connectionName, InputStream inputStream, OutputStream outputStream);

  /**
   * Gets the server's call handling thread pool.
   *
   * @return the thread pool
   */
  ExecutorService getCallThreadPool();

  /**
   * Creates a call handler for the given remote call.
   *
   * @param stream the trip stream
   * @param clientInfo the client connection info
   * @param remoteCall the remote call
   * @return the call handler
   */
  Runnable createCallHandler(TripStream stream, TransportClientInfo clientInfo, RemoteCall remoteCall);

  /**
   * Closes this transport.
   */
  void close();

  /**
   * Returns true if this transport is closed.
   *
   * @return true if closed, false otherwise
   */
  boolean isClosed();

}
