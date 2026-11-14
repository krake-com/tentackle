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

import org.tentackle.log.Logger;
import org.tentackle.trip.PreAuthenticationPolicy;
import org.tentackle.trip.Registry;
import org.tentackle.trip.RejectionDelay;
import org.tentackle.trip.Remote;
import org.tentackle.trip.RemoteDelegate;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TripConnectionRejectedException;
import org.tentackle.trip.TripNoSuchDelegateException;
import org.tentackle.trip.TripPolicyViolationException;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripStreamClosedException;
import org.tentackle.trip.impl.DefaultOpCode;
import org.tentackle.trip.remote.IdentifiedRemoteCall;
import org.tentackle.trip.remote.IdentifiedRemoteResponse;
import org.tentackle.trip.remote.InitialRemoteCall;
import org.tentackle.trip.remote.InitialRemoteResponse;
import org.tentackle.trip.remote.RemoteCall;
import org.tentackle.trip.remote.RemoteResponse;

import java.util.UUID;

/**
 * Handles a remote call.<br>
 * Runs in a regular thread.
 * <p>
 * If the invoked method {@link RejectionDelay#defer(long) deferred} its response, the handler does not send it.
 * The {@link ConnectionHandler} waits for the delay in its virtual thread and then
 * {@link #sendDeferredResponse() sends} it.
 *
 * @see ConnectionHandler
 */
public class CallHandler implements Runnable {

  private static final Logger LOGGER = Logger.get(CallHandler.class);

  private final Transport transport;
  private final TripStream stream;
  private final TransportClientInfo clientInfo;
  private final RemoteCall remoteCall;
  private RejectionDelay.Deferral deferral;     // the deferral of the response, null if not deferred
  private Runnable deferredWriter;              // writes the deferred response
  private boolean discarded;                    // true if the response is not sent at all

  /**
   * Creates a new handler.
   *
   * @param transport the transport associated with this handler
   * @param stream the stream associated with this handler
   * @param clientInfo the client connection information
   * @param remoteCall the remote call
   */
  public CallHandler(Transport transport, TripStream stream, TransportClientInfo clientInfo, RemoteCall remoteCall) {
    this.transport = transport;
    this.stream = stream;
    this.clientInfo = clientInfo;
    this.remoteCall = remoteCall;
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
   * Gets the stream associated with this handler.
   *
   * @return the stream
   */
  public TripStream getStream() {
    return stream;
  }

  /**
   * Gets the client connection info.
   *
   * @return the client info
   */
  public TransportClientInfo getClientInfo() {
    return clientInfo;
  }

  /**
   * Gets the remote call.
   *
   * @return the remote call
   */
  public RemoteCall getRemoteCall() {
    return remoteCall;
  }

  @Override
  public void run() {
    boolean deferred = false;
    try {
      LOGGER.fine("{0} {1}", clientInfo, remoteCall);
      TransportClientInfo.set(clientInfo);
      prepareResponse();
      RemoteDelegate<Remote> delegate = transport.getRegistry().lookup(remoteCall.remoteId());
      if (delegate == null || !isDelegateAllowed() || !isOwnedByCaller(delegate)) {
        // don't tell whether it exists
        throw new TripNoSuchDelegateException(remoteCall.remoteId());
      }
      Runnable writer;
      RejectionDelay.Deferral pending = RejectionDelay.Deferral.begin();
      try {
        if (remoteCall instanceof IdentifiedRemoteCall identifiedRemoteCall) {
          IdentifiedRemoteResponse response = delegate.invoke(identifiedRemoteCall);
          checkLookup(response, response.result());
          writer = () -> stream.getType(IdentifiedRemoteResponse.class).write(response, stream);
        }
        else if (remoteCall instanceof InitialRemoteCall initialRemoteCall) {
          InitialRemoteResponse response = delegate.invoke(initialRemoteCall);
          checkLookup(response, response.result());
          writer = () -> stream.getType(InitialRemoteResponse.class).write(response, stream);
        }
        else {
          throw new TripRuntimeException("unexpected remote call type: " + remoteCall.getClass());
        }
      }
      finally {
        pending.end();
      }
      if (pending.isRequested()) {
        // answered late, but the delay must not block this platform thread:
        // the ConnectionHandler waits in its virtual thread and then invokes sendDeferredResponse()
        deferral = pending;
        deferredWriter = writer;
        deferred = true;
      }
      else {
        writer.run();
      }
    }
    catch (TripStreamClosedException ce) {
      LOGGER.warning("client closed connection while processing {0} ({1})", remoteCall, ce.getMessage());
      throw ce;   // will be caught via ExecutionException in ConnectionHandler
    }
    catch (TripConnectionRejectedException rx) {
      throw rx;   // not answered: the ConnectionHandler closes the connection after the rejection delay
    }
    catch (TripNoSuchDelegateException nd) {
      if (isRestricted()) {
        // A client that is not authenticated knows the public entry points only.
        // Anything else is probing: not answered, the ConnectionHandler logs it once and closes the connection
        // after the rejection delay.
        if (isErrorSuppressed()) {
          discarded = true;
        }
        else {
          stream.sendError(DefaultOpCode.NO_SUCH_DELEGATE, nd);
        }
        throw new TripPolicyViolationException("not allowed before authentication: invocation of remote object " +
                                               TransportClientInfo.redact(remoteCall.remoteId()));
      }
      stream.sendError(DefaultOpCode.NO_SUCH_DELEGATE, nd);
      LOGGER.warning(nd.getMessage());
    }
    catch (Throwable t) {
      // serializing the response failed
      if (isErrorSuppressed()) {
        // not answered: the error message would tell the client that it talks to a TRIP server.
        // Don't flush what was serialized so far either.
        discarded = true;
        throw t;
      }
      LOGGER.severe("failure processing " + remoteCall, t);
      stream.sendError(DefaultOpCode.SERVER_SERIALIZATION_ERROR, t);
    }
    finally {
      TransportClientInfo.set(null);
      if (!deferred && !discarded) {
        finalizeResponse();
      }
    }
  }

  /**
   * Gets the deferral of the response.
   *
   * @return the deferral if the response was not sent yet, null if sent or no call processed yet
   * @see RejectionDelay#defer(long)
   */
  public RejectionDelay.Deferral getDeferral() {
    return deferral;
  }

  /**
   * Sends the deferred response.<br>
   * Invoked by the {@link ConnectionHandler} in its virtual thread after it waited for the {@link #getDeferral() deferral}.
   *
   * @throws TripStreamClosedException if the client closed the connection
   */
  public void sendDeferredResponse() {
    Runnable writer = deferredWriter;
    if (writer == null) {
      throw new TripRuntimeException("no deferred response for " + remoteCall);
    }
    deferral = null;
    deferredWriter = null;
    try {
      TransportClientInfo.set(clientInfo);
      writer.run();
    }
    catch (TripStreamClosedException ce) {
      LOGGER.warning("client closed connection while processing {0} ({1})", remoteCall, ce.getMessage());
      throw ce;
    }
    catch (Throwable t) {
      // serializing the response failed
      if (isErrorSuppressed()) {
        discarded = true;
        throw t;    // not answered, see run()
      }
      LOGGER.severe("failure processing " + remoteCall, t);
      stream.sendError(DefaultOpCode.SERVER_SERIALIZATION_ERROR, t);
    }
    finally {
      TransportClientInfo.set(null);
      if (!discarded) {
        finalizeResponse();
      }
    }
  }

  /**
   * Rejects the lookup of an unknown name by a client that is not authenticated yet.
   * <p>
   * The registry exports only {@link org.tentackle.trip.RemoteLookup#lookup(String)}, so a successful
   * call of the registry with a {@code null} result means that the name is unknown. Such a client is
   * guessing the service name, or misconfigured: it gets no answer, and the connection is closed after the
   * {@link org.tentackle.trip.RejectionDelay}. Authenticated clients get the {@code null} result.
   *
   * @param response the response of the call
   * @param result the result of the call
   * @throws TripConnectionRejectedException if rejected
   */
  protected void checkLookup(RemoteResponse response, Object result) {
    if (result == null && response.wasSuccessFul() && isRejectingUnknownNames() &&
        Registry.REGISTRY_ID.equals(remoteCall.remoteId()) && !clientInfo.isAuthenticated()) {
      // don't tell the name: it was chosen by the client
      throw new TripConnectionRejectedException("lookup of an unknown remote object name");
    }
  }

  /**
   * Returns whether the lookup of an unknown name by an unauthenticated client is rejected.
   *
   * @return true if rejected (default), false to answer with {@code null}
   * @see #checkLookup(RemoteResponse, Object)
   */
  protected boolean isRejectingUnknownNames() {
    return true;
  }

  /**
   * Checks whether the client may invoke the remote object of the call.<br>
   * As long as the client is not authenticated, the {@link PreAuthenticationPolicy} of the
   * transport, if any, decides.
   *
   * @return true if allowed
   */
  protected boolean isDelegateAllowed() {
    PreAuthenticationPolicy policy = transport.getPreAuthenticationPolicy();
    if (policy == null || clientInfo.isAuthenticated() ||
        policy.isDelegateAllowed(transport.getRegistry(), remoteCall.remoteId())) {
      return true;
    }
    if (policy.isEnforcing()) {
      return false;   // logged by the caller
    }
    policy.violation("invocation of a remote object not registered by name");
    return true;
  }

  /**
   * Checks whether the calling client owns the remote object of the call.
   * <p>
   * An unnamed remote object is private to the client transport that created it: only that client
   * may invoke it. Public remote objects (registered by name, the registry itself, or registered
   * without a client call in progress) have no owner and are invocable by everyone.
   * A client invoking a remote object owned by another client is answered with
   * {@code "no such delegate"}, as if it did not exist, but the attempt is logged.
   *
   * @return true if the delegate is public or owned by the calling client
   */
  protected boolean isOwnedByCaller(RemoteDelegate<Remote> delegate) {
    UUID ownerId = delegate.getOwnerId();
    if (ownerId == null || ownerId.equals(clientInfo.clientId())) {
      return true;
    }
    LOGGER.warning("client {0} attempted to invoke foreign remote object {1}",
                   TransportClientInfo.redact(clientInfo.clientId()),
                   TransportClientInfo.redact(remoteCall.remoteId()));
    return false;
  }

  /**
   * Returns whether the client is restricted by an enforcing {@link PreAuthenticationPolicy}.
   *
   * @return true if not authenticated yet and the policy is enforcing
   */
  protected boolean isRestricted() {
    PreAuthenticationPolicy policy = transport.getPreAuthenticationPolicy();
    return policy != null && policy.isEnforcing() && !clientInfo.isAuthenticated();
  }

  /**
   * Returns whether errors are not answered.<br>
   * As long as the client is {@link #isRestricted() restricted}, a failed call is not answered at all:
   * the error message would tell a prober that it talks to a TRIP server. The connection handler closes
   * the connection after the {@link RejectionDelay}.
   *
   * @return true if restricted (default)
   */
  protected boolean isErrorSuppressed() {
    return isRestricted();
  }

  /**
   * Prepares the response.
   */
  protected void prepareResponse() {
    stream.clearModifiable();
  }

  /**
   * Finalizes the response.
   */
  protected void finalizeResponse() {
    stream.getSerializer().flush();
    stream.clear();
  }

}
