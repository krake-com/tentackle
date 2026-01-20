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
import org.tentackle.trip.Connection;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripStreamClosedException;
import org.tentackle.trip.TripType;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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

  private record MethodKey(String methodName, Class<?>[] parameterTypes) {}

  private final Map<MethodKey, Integer> methodTypeMap;    // method+parameterTypes -> methodIndex

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
    try {
      TripStream stream = connection.getStream();
      stream.clear();
      MethodKey key = new MethodKey(method.getName(), method.getParameterTypes());
      Integer methodIndex = methodTypeMap.get(key);
      if (methodIndex == null) {
        InitialRemoteCall initialCall = new InitialRemoteCall(remoteId, method.getName(), method.getParameterTypes(), args);
        call = initialCall;
        initialCallType.write(initialCall, stream);
      }
      else {
        IdentifiedRemoteCall identifiedCall = new IdentifiedRemoteCall(remoteId, methodIndex, args);
        call = identifiedCall;
        identifiedCallType.write(identifiedCall, stream);
      }

      connection.flush();

      if (methodIndex == null) {
        InitialRemoteResponse initialResponse = initialResponseType.read(stream, stream.getDeserializer().deserializeOpCode());
        if (initialResponse == null) {
          throw new TripRuntimeException("initial response <null> received from server");
        }
        response = initialResponse;
        methodIndex = initialResponse.methodIndex();
        if (methodIndex >= 0) {
          methodTypeMap.put(key, methodIndex);
        }
        if (initialResponse.wasSuccessFul()) {
          return processResult(initialResponse.result());
        }
        else {
          throw initialResponse.throwable();
        }
      }
      else {
        IdentifiedRemoteResponse identifiedResponse = identifiedRemoteType.read(stream, stream.getDeserializer().deserializeOpCode());
        if (identifiedResponse == null) {
          throw new TripRuntimeException("identified response <null> received from server");
        }
        response = identifiedResponse;
        if (identifiedResponse.wasSuccessFul()) {
          return processResult(identifiedResponse.result());
        }
        else {
          throw identifiedResponse.throwable();
        }
      }
    }
    catch (TripStreamClosedException tc) {
      String msg = MessageFormat.format("connection closed by server {0}", transport);
      LOGGER.fine(msg, tc);
      throw new TripStreamClosedException(msg);
    }
    catch (Throwable t) {
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
      transport.putConnection(connection);
    }
  }


  /**
   * Processes the result of a remote call.<br>
   * If the result is a {@link DelegateDescriptor}, it is replaced with a proxy for the
   * remote object.
   *
   * @param result the result of the remote call
   * @return the processed result
   */
  protected Object processResult(Object result) {
    if (result instanceof DelegateDescriptor(UUID id, Class<?>[] remoteInterfaces)) {
      return TripFactory.getInstance().createRemoteProxy(id, transport, remoteInterfaces);
    }
    return result;
  }

}
