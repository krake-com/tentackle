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

package org.tentackle.trip.remote;

import org.tentackle.log.Logger;
import org.tentackle.trip.ClientTransportCloseRequest;
import org.tentackle.trip.Connection;
import org.tentackle.trip.Remote;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TransportManager;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripStreamClosedException;
import org.tentackle.trip.TripType;
import org.tentackle.trip.impl.DefaultOpCode;
import org.tentackle.trip.impl.TripError;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * The invocation handler for remote interfaces.
 * <p>
 * Runs in a client-side thread. Since the invocation handler is not thread-safe,
 * the application must prevent concurrent invocations of the same remote object.
 * For Tentackle sessions, this is always guaranteed because each thread has its
 * own session and each session maps to connections pooled by the same transport.
 */
public class RemoteInvocationHandler implements InvocationHandler {

  private static final Logger LOGGER = Logger.get(RemoteInvocationHandler.class);

  private final UUID remoteId;
  private final Transport transport;
  private final TripType<InitialRemoteCall> initialCallType;
  private final TripType<InitialRemoteResponse> initialResponseType;
  private final TripType<IdentifiedRemoteCall> identifiedCallType;
  private final TripType<IdentifiedRemoteResponse> identifiedRemoteType;
  private final Map<Method, Integer> methodTypeMap;    // method -> methodIndex

  /**
   * Creates a remote invocation handler.
   *
   * @param remoteId the ID of the remote object
   * @param transport the transport to use for remote calls
   */
  public RemoteInvocationHandler(UUID remoteId, Transport transport) {
    this.remoteId = remoteId;
    this.transport = Objects.requireNonNull(transport);
    TripFactory factory = TripFactory.getInstance();
    initialCallType = factory.getType(InitialRemoteCall.class);
    initialResponseType = factory.getType(InitialRemoteResponse.class);
    identifiedCallType = factory.getType(IdentifiedRemoteCall.class);
    identifiedRemoteType = factory.getType(IdentifiedRemoteResponse.class);
    methodTypeMap = new HashMap<>();
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    Connection connection = transport.getConnection();
    RemoteCall call = null;
    RemoteResponse response = null;
    TripStream stream = connection.getStream();
    try {
      stream.clear();
      Integer methodIndex = methodTypeMap.get(method);

      // send call
      try {
        if (methodIndex == null) {
          InitialRemoteCall initialCall = new InitialRemoteCall(remoteId, method.getName(), method.getParameterTypes(),
                                                                args == null || args.length == 0 ? null : args);
          call = initialCall;
          initialCallType.write(initialCall, stream);
        }
        else {
          IdentifiedRemoteCall identifiedCall = new IdentifiedRemoteCall(remoteId, methodIndex,
                                                                         args == null || args.length == 0 ? null : args);
          call = identifiedCall;
          identifiedCallType.write(identifiedCall, stream);
        }
      }
      catch (Throwable t) {
        stream.sendError(DefaultOpCode.CLIENT_SERIALIZATION_ERROR, t);
        connection.flush();
        // the server will log that, and we don't need to wait for the answer
        throw t;
      }

      connection.flush();

      // receive response
      if (methodIndex == null) {
        InitialRemoteResponse initialResponse = initialResponseType.read(stream, stream.receiveOpCode());
        if (initialResponse == null) {
          throw new TripRuntimeException("initial response <null> received from server");
        }
        response = initialResponse;
        methodIndex = initialResponse.methodIndex();
        if (methodIndex >= 0) {
          methodTypeMap.put(method, methodIndex);
        }
        if (initialResponse.wasSuccessFul()) {
          return processResult(initialResponse.result());
        }
        else {
          throw new ExecutionException(initialResponse.throwable());    // caught below
        }
      }
      else {
        IdentifiedRemoteResponse identifiedResponse = identifiedRemoteType.read(stream, stream.receiveOpCode());
        if (identifiedResponse == null) {
          throw new TripRuntimeException("identified response <null> received from server");
        }
        response = identifiedResponse;
        if (identifiedResponse.wasSuccessFul()) {
          return processResult(identifiedResponse.result());
        }
        else {
          throw new ExecutionException(identifiedResponse.throwable());   // caught below
        }
      }
    }
    catch (ExecutionException ex) {
      // exception during execution of the remote method at the server side
      throw ex.getCause();
    }
    catch (TripError te) {
      if (te.getOpCode() == DefaultOpCode.SERVER_DESERIALIZATION_ERROR) {
        // we don't know what's still in the server's input stream
        closeConnection(connection);
      } // else: we received a serialization error from the server: we can keep the connection open
      throw te;
    }
    catch (TripStreamClosedException tc) {
      String msg = MessageFormat.format("connection closed by server {0}", transport);
      LOGGER.fine(msg, tc);
      closeConnection(connection);
      throw new TripStreamClosedException(msg, tc);
    }
    catch (Throwable t) {
      try {
        stream.sendError(DefaultOpCode.CLIENT_DESERIALIZATION_ERROR, t);
      }
      catch (RuntimeException sx) {
        // the connection is already broken -> there is nothing left to tell the server.
        // Must not prevent closing the connection below, or it would be handed back to the pool.
        t.addSuppressed(sx);
      }
      closeConnection(connection);   // since we don't know what's still in the input stream, discard it
      StringBuilder buf = new StringBuilder("error invoking remote method ").append(method);
      if (call != null) {
        buf.append('\n').append(call);
      }
      if (response != null) {
        buf.append('\n').append(response);
      }
      LOGGER.severe(buf.toString(), t);
      throw t;
    }
    finally {
      if (!transport.isClosed()) {
        transport.putConnection(connection);  // closed connections will be removed from the pool
      }
    }
  }


  /**
   * Processes the result of a remote call.<br>
   * If the result is a {@link DelegateDescriptor}, it is replaced with a proxy for the
   * remote object. An array of descriptors is replaced with an array of proxies,
   * which allows retrieving several remote objects in a single roundtrip.
   *
   * @param result the result of the remote call
   * @return the processed result
   */
  protected Object processResult(Object result) {
    if (result instanceof DelegateDescriptor(UUID id, Class<?>[] remoteInterfaces)) {
      return TripFactory.getInstance().createRemoteProxy(id, transport, remoteInterfaces);
    }
    if (result instanceof DelegateDescriptor[] descriptors) {
      Remote[] remotes = new Remote[descriptors.length];
      for (int i = 0; i < descriptors.length; i++) {
        if (descriptors[i] != null) {
          remotes[i] = TripFactory.getInstance().createRemoteProxy(
                  descriptors[i].remoteId(), transport, descriptors[i].remoteInterfaces());
        }
        // else: no delegate for that object
      }
      return remotes;
    }
    if (result instanceof ClientTransportCloseRequest closeRequest) {
      if (closeRequest.isCloseAllTransportsRequested()) {
        LOGGER.info("close all transports requested");
        TransportManager.getInstance().closeAllTransports();
      }
      else if (closeRequest.isTransportReleaseRequested()) {
        LOGGER.info("transport release requested");
        TransportManager.getInstance().releaseTransport(transport);
      }
    }
    return result;
  }


  private void closeConnection(Connection connection) {
    if (connection.isOpen()) {
      try {
        connection.close();
      }
      catch (RuntimeException cx) {
        LOGGER.warning("error closing connection", cx);
      }
    }
  }

}
