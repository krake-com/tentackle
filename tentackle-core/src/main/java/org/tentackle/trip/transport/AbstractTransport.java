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

import org.tentackle.io.UriHelper;
import org.tentackle.log.Logger;
import org.tentackle.misc.Pool;
import org.tentackle.trip.Connection;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.Registry;
import org.tentackle.trip.Remote;
import org.tentackle.trip.RemoteLookup;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TransportKey;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.remote.RemoteCall;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base class for transport implementations.
 */
public abstract class AbstractTransport implements Transport {

  private static final Logger LOGGER = Logger.get(AbstractTransport.class);

  private final URI uri;                              // URI of the transport
  private final TransportKey transportKey;            // the transport key
  private final String name;                          // the key's string value
  private final UUID id;                              // UUID of the transport
  private final Map<String, Integer> uriPars;         // the URI query parameters
  private ThreadGroup serverThreadGroup;              // call handling thread group [server]
  private ExecutorService callThreadPool;             // call handling thread pool [server]
  private ExecutorService connectionThreadPool;       // incoming connection virtual thread pool [server]
  private Pool<Connection> clientConnectionPool;      // outgoing connection pool [client]
  private Dictionary dictionary;                      // single dictionary for client transports
  private Registry registry;                          // the server's registry
  private RemoteLookup remoteLookup;                  // the cached remote lookup
  private boolean closed;                             // true if the transport is closed (cannot be used anymore)

  /**
   * A server-side map with weak UUIDs mapped to weak references to dictionaries.
   * This is necessary to GC the server-side dictionaries when the last
   * connection sharing the dictionary (mapped by its UUID, which is
   * the same reference as the UUID of the client's transport) is closed.
   * <p>
   * Notice: the ModificationTracker automatically reconnects its {@code Session} if the
   * TRIP-connection is closed due to an error (for example, a timeout or some serialization error).
   * If this was the only connection, the dictionary is removed from this map, and a
   * new dictionary is created when the session is re-established.<br>
   * If TRIP is used by other applications, some similar reconnection mechanism must be implemented as well.<br>
   * See also {@code ConnectionHandler:239 → "no such dictionary … (stale client transport?)"}.
   */
  private WeakHashMap<UUID, WeakReference<Dictionary>> dictionariesById;

  /** thread counter for call handlers. */
  private final AtomicLong nextCallHandlerId = new AtomicLong(0);

  /**
   * Creates a new transport.
   *
   * @param uri the URI of the transport
   */
  public AbstractTransport(URI uri) {
    this.uri = Objects.requireNonNull(uri);
    transportKey = new TransportKey(uri);
    name = transportKey.toString();
    id = UUID.randomUUID();
    uriPars = UriHelper.getQueryParameters(uri.getQuery(), Integer::parseInt);
  }

  @Override
  public URI getUri() {
    return uri;
  }

  @Override
  public TransportKey getTransportKey() {
    return transportKey;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public synchronized Connection getConnection() {
    assertNotClosed();
    if (callThreadPool != null) {
      throw new TripRuntimeException("transport is in server mode");
    }
    if (clientConnectionPool == null) {
      dictionary = TripFactory.getInstance().createDictionary(id,false);
      clientConnectionPool = createClientConnectionPool();
    }
    return clientConnectionPool.get();
  }

  @Override
  public synchronized void putConnection(Connection connection) {
    if (clientConnectionPool == null) {
      throw new TripRuntimeException("transport is not in client mode");
    }
    clientConnectionPool.put(connection);
  }

  @Override
  public synchronized void accept() {
    assertNotClosed();
    if (clientConnectionPool != null) {
      throw new TripRuntimeException("transport is in client mode");
    }
    if (serverThreadGroup != null) {
      throw new TripRuntimeException("transport is already accepting connections");
    }
    serverThreadGroup = createThreadGroup();
    callThreadPool = createCallThreadPool();
    connectionThreadPool = createConnectionThreadPool();
    dictionariesById = new WeakHashMap<>();
    registry = createRegistry();
  }

  @Override
  public Dictionary getClientDictionary() {
    return dictionary;
  }

  @Override
  public synchronized Dictionary getServerDictionary(UUID id) {
    // Since the dictionary keeps a strong reference to the transport's ID,
    // all the incoming connection's UUID are mapped to the same reference.
    // When the last connection sharing the dictionary is closed, the dictionary
    // and the UUID in the weak map are both garbage collected.
    return dictionariesById.computeIfAbsent(id, uuid -> new WeakReference<>(
      TripFactory.getInstance().createDictionary(uuid, true))).get();
  }

  @Override
  public Registry getRegistry() {
    return registry;
  }

  @Override
  public RemoteLookup getRemoteLookup() {
    if (remoteLookup == null) {
      remoteLookup = TripFactory.getInstance().createRemoteProxy(Registry.REGISTRY_ID, this, RemoteLookup.class);
    }
    return remoteLookup;
  }

  @Override
  public <T> T lookup(Class<T> clazz, String name) {
    if (!clazz.isInterface() || !Remote.class.isAssignableFrom(clazz)) {
      throw new TripRuntimeException("not a remote interface: " + clazz);
    }
    UUID id = getRemoteLookup().lookup(name);
    if (id != null) {
      return TripFactory.getInstance().createRemoteProxy(id, this, clazz);
    }
    throw new TripRuntimeException("remote object not found: " + name);
  }

  @Override
  public ExecutorService getConnectionThreadPool() {
    return connectionThreadPool;
  }

  @Override
  public Runnable createConnectionHandler(String connectionName, InputStream inputStream, OutputStream outputStream) {
    return new ConnectionHandler(this, connectionName, inputStream, outputStream);
  }

  @Override
  public ExecutorService getCallThreadPool() {
    return callThreadPool;
  }

  @Override
  public Runnable createCallHandler(TripStream stream, TransportClientInfo clientInfo, RemoteCall remoteCall) {
    return new CallHandler(this, stream, clientInfo, remoteCall);
  }

  @Override
  public synchronized void close() {
    LOGGER.info("closing transport " + this);
    closed = true;
    if (clientConnectionPool != null) {
      clientConnectionPool.shutdown();
      clientConnectionPool = null;
    }
    else {
      if (callThreadPool != null) {
        callThreadPool.shutdownNow();
        callThreadPool = null;
      }
      if (connectionThreadPool != null) {
        connectionThreadPool.shutdownNow();
        connectionThreadPool = null;
      }
    }
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  @Override
  public String toString() {
    return name;
  }

  /**
   * Gets the URI query parameters.
   *
   * @return a map of key-value pairs
   */
  public Map<String, Integer> getUriPars() {
    return uriPars;
  }

  /**
   * Creates the server's registry.
   *
   * @return the registry
   */
  protected Registry createRegistry() {
    return TripFactory.getInstance().createRegistry();
  }

  /**
   * Creates the connection pool for outgoing connections.
   *
   * @return the connection pool
   */
  protected abstract Pool<Connection> createClientConnectionPool();

  /**
   * Creates a new server-side thread.
   *
   * @param runnable the runnable
   * @return the thread
   */
  protected Thread createServerThread(Runnable runnable) {
    Thread t = new Thread(serverThreadGroup, runnable,
                          uri.getScheme() + "://" +
                          uri.getHost() + ":" + uri.getPort() +
                          "[" + nextCallHandlerId.incrementAndGet() + "]");
    t.setDaemon(true);
    return t;
  }

  /**
   * Creates the thread pool for call handling.
   *
   * @return the thread pool (regular threads, not virtual threads)
   */
  private ExecutorService createCallThreadPool() {
    return new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                                  uriPars.getOrDefault("idle", 5), TimeUnit.MINUTES,
                                  new SynchronousQueue<>(),
                                  this::createServerThread);
  }

  /**
   * Creates the thread group for server-side TRIP handling.
   *
   * @return the thread group
   */
  private ThreadGroup createThreadGroup() {
    return new ThreadGroup(Thread.currentThread().getThreadGroup(), "TRIP server thread group " + uri.getScheme());
  }

  /**
   * Creates the virtual thread pool for reading the calls from incoming connections.
   *
   * @return the thread pool
   */
  private ExecutorService createConnectionThreadPool() {
    return Executors.newThreadPerTaskExecutor(
      Thread.ofVirtual()
            .name("TRIP connection " + uri.getScheme() + " VThread ", 1)
            .factory());
  }

  /**
   * Asserts that the transport is not closed.<br>
   * A closed transport cannot be used anymore.
   */
  private void assertNotClosed() {
    if (closed) {
      throw new TripRuntimeException("transport " + this + " is closed");
    }
  }

}
