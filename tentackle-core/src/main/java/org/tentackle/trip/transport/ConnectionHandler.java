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

import org.tentackle.common.ExceptionHelper;
import org.tentackle.log.Logger;
import org.tentackle.trip.Deserializer;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.PreAuthenticationPolicy;
import org.tentackle.trip.RejectionDelay;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TripConnectionRejectedException;
import org.tentackle.trip.TripConstants;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripPolicyViolationException;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripStreamClosedException;
import org.tentackle.trip.TripType;
import org.tentackle.trip.impl.DefaultOpCode;
import org.tentackle.trip.impl.TripError;
import org.tentackle.trip.remote.RemoteCall;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Handler for a new incoming connection.
 * <p>
 * Each handler runs in a virtual thread, so that the deserialization of remote calls,
 * which takes time depending on the incoming data rate, does not block regular threads.
 * When a call is deserialized, it is dispatched to a regular thread from the call thread pool.
 * This combination of virtual and regular threads reduces the total number of threads,
 * since we don't need a regular thread per incoming connection anymore, but only that many
 * threads that are necessary to handle the remote calls concurrently.
 *
 * @see CallHandler
 */
public class ConnectionHandler implements Runnable {

  private static final Logger LOGGER = Logger.get(ConnectionHandler.class);

  private final Transport transport;
  private final String connectionName;
  private final InputStream inputStream;
  private final OutputStream outputStream;
  private volatile TransportClientInfo clientInfo;
  private volatile boolean evicted;

  /**
   * Creates a new handler.
   *
   * @param transport the transport associated with this handler
   * @param connectionName the name of the connection
   * @param inputStream the input stream associated with this connection
   * @param outputStream the output stream associated with this connection
   */
  public ConnectionHandler(Transport transport, String connectionName, InputStream inputStream, OutputStream outputStream) {
    this.transport = Objects.requireNonNull(transport, "missing transport");
    this.connectionName = Objects.requireNonNull(connectionName, "missing connection name");
    this.inputStream = Objects.requireNonNull(inputStream, "missing input stream");
    this.outputStream = Objects.requireNonNull(outputStream, "missing output stream");
  }

  /**
   * Gets the transport associated with this handler.
   *
   * @return the transport
   */
  public Transport getTransport() {
    return transport;
  }

  /**
   * Gets the name of the connection.
   *
   * @return the name
   */
  public String getConnectionName() {
    return connectionName;
  }

  /**
   * Gets the input stream associated with this connection.
   *
   * @return the input stream
   */
  public InputStream getInputStream() {
    return inputStream;
  }

  /**
   * Gets the output stream associated with this connection.
   *
   * @return the output stream
   */
  public OutputStream getOutputStream() {
    return outputStream;
  }

  /**
   * Handles the new connection.
   * <ul>
   *   <li>reads the connection request: the TRIP magic followed by the UUID of the client's transport
   *   and whether the client's dictionary is still empty. An invalid magic closes the connection
   *   after the {@link RejectionDelay}.</li>
   *   <li>the UUID is used to determine the dictionary and a TRIP stream is created</li>
   *   <li>reads the remote calls from the stream and dispatches them to the call handler thread pool.</li>
   * </ul>
   */
  @Override
  public void run() {
    PreAuthenticationPolicy policy = transport.getPreAuthenticationPolicy();
    PreAuthenticationPolicy.Admission admission = null;
    if (policy != null) {
      admission = policy.admit(TransportClientInfo.getHost(connectionName), this::evict, this::isAuthenticated);
      if (admission == null) {
        LOGGER.warning("connection {0} refused: too many unauthenticated connections", connectionName);
        return;
      }
    }
    try {
      handleConnection(policy, admission);
    }
    finally {
      if (admission != null) {
        admission.release();
      }
    }
  }


  /**
   * Returns whether the client of this connection is authenticated.
   *
   * @return true if authenticated, false if not or the handshake is not complete yet
   */
  protected boolean isAuthenticated() {
    TransportClientInfo info = clientInfo;
    return info != null && info.isAuthenticated();
  }

  /**
   * Closes the connection on behalf of the pre-authentication policy.<br>
   * Unblocks the thread reading from the connection.
   */
  protected void evict() {
    evicted = true;
    try {
      inputStream.close();
    }
    catch (IOException | RuntimeException ex) {
      LOGGER.fine("closing input stream of {0} failed: {1}", connectionName, ex.getMessage());
    }
    try {
      outputStream.close();
    }
    catch (IOException | RuntimeException ex) {
      LOGGER.fine("closing output stream of {0} failed: {1}", connectionName, ex.getMessage());
    }
  }

  /**
   * Checks whether the connection is still alive.
   *
   * @return true if the connection is still alive, false to terminate the handler
   */
  protected boolean isConnectionAlive() {
    return !transport.isClosed();
  }

  /**
   * Creates a new stream for the given UUID.
   *
   * @param id the UUID of the client's transport
   * @param clientDictionaryEmpty true if the client's dictionary is still empty
   * @return the trip stream
   */
  protected TripStream createStream(UUID id, boolean clientDictionaryEmpty) {
    if (transport.isClosed()) {   // don't use isConnectionAlive since this would block in LoopbackTransport (see override)
      throw new TripStreamClosedException("transport " + transport + " already closed");
    }
    Dictionary serverDictionary = transport.getServerDictionary(id, clientDictionaryEmpty);
    if (serverDictionary != null) {
      TripFactory factory = TripFactory.getInstance();
      Serializer serializer = factory.createSerializer(outputStream);
      Deserializer deserializer = factory.createDeserializer(inputStream);
      return factory.createStream(serverDictionary, serializer, deserializer);
    }
    else {
      throw new TripStreamClosedException("no such dictionary " + TransportClientInfo.redact(id) + " in transport " + transport +
                                          " (stale client transport?)");
    }
  }


  /**
   * Handles the connection.
   *
   * @param policy the pre-authentication policy, null if none
   * @param admission the admission by the policy, null if none
   */
  private void handleConnection(PreAuthenticationPolicy policy, PreAuthenticationPolicy.Admission admission) {
    UUID id;
    TripStream stream;
    try {
      long started = System.nanoTime();
      int versionSkew = readMagic();
      if (versionSkew < 0) {
        // Not a TRIP client, or someone guessing the magic: close as late as a failed login,
        // so that the response time tells nothing. Don't log what was received: anybody can send garbage.
        LOGGER.warning("connection {0} sent an invalid magic -> closed",
                       new TransportClientInfo(transport, null, connectionName, null));
        RejectionDelay.await(started);
        return;
      }
      if (versionSkew != 0) {
        // For an auto-updatable client, this will happen only once per client with the update URL endpoint.
        // The client then updates itself and automatically uses the latest protocol.
        LOGGER.warning("client connection {0} uses an outdated version of TRIP, version skew = {1}",
                       new TransportClientInfo(transport, null, connectionName, null), versionSkew);
      }
      id = readUUID();
      boolean clientDictionaryEmpty = true;
      if (versionSkew == 0) {
        clientDictionaryEmpty = readDictionaryFlag();
      }
      stream = createStream(id, clientDictionaryEmpty);
    }
    catch (TripRuntimeException e) {
      // SSL handshake exception, peer closed, stale client transport, etc...
      // -> just log the messages, no stacktrace: anybody can open a connection and send garbage
      LOGGER.warning("connection {0} aborted: {1}",
                     new TransportClientInfo(transport, null, connectionName, null),
                     ExceptionHelper.concatenateMessages(e));
      return;
    }
    clientInfo = new TransportClientInfo(transport, stream.getDictionary(), connectionName, id);
    int messageCount = 0;   // number of messages received before authentication
    boolean closedByClient = false;
    LOGGER.info("connection " + clientInfo + " opened");

    while (isConnectionAlive()) {
      TripType<?> tripType = null;
      Object call = null;
      long callReceived = 0;    // System.nanoTime() when the call was received
      try {
        if (admission != null && clientInfo.isAuthenticated()) {
          // the client logged in (on this or another connection): no restrictions anymore
          admission.release();
          admission = null;
        }
        stream.setReadPolicy(admission != null ? policy : null);
        stream.clear();
        tripType = stream.deriveType();
        if (tripType.isDictionaryBased()) {   // all remote calls are dictionary-based
          call = tripType.read(stream, stream.receiveOpCode());
          callReceived = System.nanoTime();
          if (admission != null) {
            checkMessageSize(policy, stream);
            checkMessageCount(policy, ++messageCount);
            admission.messageReceived();
          }
          if (call instanceof RemoteCall remoteCall) {
            try {
              Runnable callHandler = transport.createCallHandler(stream, clientInfo, remoteCall);
              // dispatch to call handler ...
              transport.getCallThreadPool().submit(callHandler)
                       // ... and wait for the call to finish _before_ clearing the stream.
                       // The submitted call handler runs asynchronously in a regular thread
                       // of a thread pool. Blocking virtual threads is much cheaper
                       // than blocking regular threads.
                       .get();
              if (callHandler instanceof CallHandler handler && handler.getDeferral() != null) {
                // answered late, e.g. a failed login: wait here, not in the regular thread of the call handler.
                // The call is still in progress for the admission until the response is sent.
                handler.getDeferral().await();
                handler.sendDeferredResponse();
              }
            }
            finally {
              if (admission != null) {
                admission.callCompleted();
              }
            }
          }
          else {
            throw new TripRuntimeException("not a remote call: " + call.getClass());
          }
        }
        else {
          throw new TripRuntimeException("unexpected type received: " + tripType);
        }
      }
      catch (TripError e) {
        if (admission != null) {
          // The client failed to serialize its call. Not expected before the login, and the text is the client's:
          // don't log it, and don't allow the client to repeat it.
          if (policy.isEnforcing()) {
            LOGGER.warning("unauthenticated connection {0} sent an error ({1} characters) -> closed",
                           clientInfo, e.getMessage() == null ? 0 : e.getMessage().length());
            break;
          }
          policy.violation("error sent by the client");
        }
        LOGGER.severe("serialization error received from client", e);
        // ignore the call and continue reading from the stream
      }
      catch (TripStreamClosedException ce) {
        closedByClient = !evicted;
        break;
      }
      catch (ExecutionException xe) {
        if (xe.getCause() instanceof TripStreamClosedException ce) {
          LOGGER.warning("client closed connection while sending response for {0} ({1})", call, ce.getMessage());
          break;
        }
        if (xe.getCause() instanceof TripConnectionRejectedException rx) {
          // the call handler did not answer: close the connection as late as a failed login.
          // The delay runs in this virtual thread, not in the platform thread of the call handler.
          LOGGER.warning("unauthenticated connection {0} rejected: {1}", clientInfo, rx.getMessage());
          RejectionDelay.await(callReceived);
          break;
        }
        if (xe.getCause() instanceof TripPolicyViolationException pv) {
          // the call handler already answered the call
          LOGGER.warning("unauthenticated connection {0} violates the pre-authentication policy: {1}",
                         clientInfo, pv.getMessage());
          break;
        }
        try {
          stream.clearModifiable();
          stream.sendError(DefaultOpCode.SERVER_SERIALIZATION_ERROR, xe.getCause());
        }
        catch (TripStreamClosedException ce) {
          closedByClient = true;
          break;
        }
      }
      catch (Throwable t) {
        // deserializing the remote call failed
        TripPolicyViolationException pv = ExceptionHelper.extractException(TripPolicyViolationException.class, true, t);
        if (pv != null) {
          // no stacktrace: the client could flood the log otherwise
          LOGGER.warning("unauthenticated connection {0} violates the pre-authentication policy: {1}",
                         clientInfo, pv.getMessage());
          stream.clearModifiable();
          stream.sendError(DefaultOpCode.SERVER_DESERIALIZATION_ERROR, pv);
          break;
        }
        if (tripType == null) {
          LOGGER.severe("cannot derive remote call type", t);
        }
        else {
          LOGGER.severe("failed to deserialize remote call " + tripType, t);
        }
        stream.clearModifiable();
        stream.sendError(DefaultOpCode.SERVER_DESERIALIZATION_ERROR, t);
        break;    // close the connection since we don't know what's still in the stream
      }
    }
    LOGGER.info("connection {0} closed{1}", clientInfo,
                closedByClient ? " by client" : (evicted ? " by the pre-authentication policy" : ""));
  }

  /**
   * Logs a message exceeding the size limit of a policy that only audits.<br>
   * An enforcing policy already stopped reading it.
   *
   * @param policy the policy
   * @param stream the stream
   */
  private void checkMessageSize(PreAuthenticationPolicy policy, TripStream stream) {
    int maxMessageSize = policy.getMaxMessageSize();
    if (!policy.isEnforcing() && maxMessageSize > 0) {
      long size = stream.getDeserializer().getBytesRead();
      if (size > maxMessageSize) {
        policy.violation("message exceeds the maximum size of " + maxMessageSize + " bytes");
      }
    }
  }

  /**
   * Checks the number of messages received before authentication.
   *
   * @param policy the policy
   * @param messageCount the number of messages received so far, including the current one
   */
  private void checkMessageCount(PreAuthenticationPolicy policy, int messageCount) {
    int maxMessages = policy.getMaxMessages();
    if (maxMessages > 0 && messageCount > maxMessages) {
      policy.violation("more than " + maxMessages + " messages before authentication");
    }
  }

  /**
   * Reads the magic number and derives the version-skew.
   *
   * @return the version-skew, 0 if the client is using the current version, -1 if the magic is invalid
   */
  private int readMagic() {
    try {
      byte[] magic = inputStream.readNBytes(2);
      if (magic.length != 2) {
        throw new TripStreamClosedException("end of stream while reading magic");
      }
      if (magic[0] == TripConstants.MAGIC[0] && magic[1] == TripConstants.MAGIC[1]) {
        return 0;   // current version
      }
      else if (magic[0] == TripConstants.MAGIC_1[0] && magic[1] == TripConstants.MAGIC_1[1]) {
        return 1;  // one version behind w/o the dictionary flag
      }
      return -1;
    }
    catch (IOException e) {
      throw new TripRuntimeException("reading magic failed", e);
    }
  }

  /**
   * Reads the dictionary flag.
   *
   * @return true if the client's dictionary is empty, false otherwise
   */
  private boolean readDictionaryFlag() {
    try {
      int flag = inputStream.read();
      if (flag == -1) {
        throw new TripStreamClosedException("end of stream while reading the dictionary flag");
      }
      return flag != 0;
    }
    catch (IOException e) {
      throw new TripRuntimeException("reading the dictionary flag failed", e);
    }
  }

  /**
   * Reads the UUID of the client's transport.
   *
   * @return the UUID of the client's transport
   */
  private UUID readUUID() {
    try {
      byte[] uuidBytes = inputStream.readNBytes(16);
      if (uuidBytes.length != 16) {
        throw new TripStreamClosedException("end of stream while reading UUID");
      }
      ByteBuffer bb = ByteBuffer.wrap(uuidBytes);
      long firstLong = bb.getLong();
      long secondLong = bb.getLong();
      return new UUID(firstLong, secondLong);
    }
    catch (IOException e) {
      throw new TripRuntimeException("reading UUID failed", e);
    }
  }

}
