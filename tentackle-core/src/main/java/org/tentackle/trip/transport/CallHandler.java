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
import org.tentackle.trip.Remote;
import org.tentackle.trip.RemoteDelegate;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TripNoSuchDelegateException;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripStreamClosedException;
import org.tentackle.trip.impl.DefaultOpCode;
import org.tentackle.trip.remote.IdentifiedRemoteCall;
import org.tentackle.trip.remote.IdentifiedRemoteResponse;
import org.tentackle.trip.remote.InitialRemoteCall;
import org.tentackle.trip.remote.InitialRemoteResponse;
import org.tentackle.trip.remote.RemoteCall;

/**
 * Handles a remote call.<br>
 * Runs in a regular thread.
 *
 * @see ConnectionHandler
 */
public class CallHandler implements Runnable {

  private static final Logger LOGGER = Logger.get(CallHandler.class);

  private final Transport transport;
  private final TripStream stream;
  private final TransportClientInfo clientInfo;
  private final RemoteCall remoteCall;

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
    try {
      LOGGER.fine("{0} {1}", clientInfo, remoteCall);
      TransportClientInfo.set(clientInfo);
      prepareResponse();
      RemoteDelegate<Remote> delegate = transport.getRegistry().lookup(remoteCall.remoteId());
      if (delegate == null) {
        throw new TripNoSuchDelegateException(remoteCall.remoteId());
      }
      if (remoteCall instanceof IdentifiedRemoteCall identifiedRemoteCall) {
        IdentifiedRemoteResponse response = delegate.invoke(identifiedRemoteCall);
        stream.getType(IdentifiedRemoteResponse.class).write(response, stream);
      }
      else if (remoteCall instanceof InitialRemoteCall initialRemoteCall) {
        InitialRemoteResponse response = delegate.invoke(initialRemoteCall);
        stream.getType(InitialRemoteResponse.class).write(response, stream);
      }
      else {
        throw new TripRuntimeException("unexpected remote call type: " + remoteCall.getClass());
      }
    }
    catch (TripStreamClosedException ce) {
      LOGGER.warning("client closed connection while processing {0} ({1})", remoteCall, ce.getMessage());
      throw ce;   // will be caught via ExecutionException in ConnectionHandler
    }
    catch (TripNoSuchDelegateException nd) {
      LOGGER.warning(nd.getMessage());
      stream.sendError(DefaultOpCode.NO_SUCH_DELEGATE, nd);
    }
    catch (Throwable t) {
      // serializing the response failed
      LOGGER.severe("failure processing " + remoteCall, t);
      stream.sendError(DefaultOpCode.SERVER_SERIALIZATION_ERROR, t);
    }
    finally {
      TransportClientInfo.set(null);
      finalizeResponse();
    }
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
