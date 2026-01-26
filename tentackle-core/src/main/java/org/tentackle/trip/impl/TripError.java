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

import org.tentackle.trip.OpCode;
import org.tentackle.trip.TripRuntimeException;

import java.io.Serial;

/**
 * Special kind of {@link TripRuntimeException} used in conjunction with
 * {@link OpCodeCategory#ERROR}.
 * <p>
 * There are 4 cases:
 * <ol>
 *   <li>Serialization error when sending a {@link org.tentackle.trip.remote.RemoteCall}:
 *   since we don't know how far we got, the client appends a {@link DefaultOpCode#CLIENT_SERIALIZATION_ERROR}
 *   followed by the stacktrace as a string and re-throws the original exception.
 *   When the message is deserialized by the server and this code is seen,
 *   the client's stacktrace and is thrown as a {@code TripError}.</li>
 *
 *   <li>Deserialization error when receiving a {@link org.tentackle.trip.remote.RemoteCall}:
 *   the server sends back a {@link DefaultOpCode#SERVER_DESERIALIZATION_ERROR} followed by the stacktrace
 *   and closes the connection, since there is no way to continue with the remaining bytes in the input stream.
 *   When the client receives this, the server's stacktrace is thrown as a {@code TripError}
 *   and the client closes as well.</li>
 *
 *   <li>Serialization error when sending a {@link org.tentackle.trip.remote.RemoteResponse}:
 *   The server appends a {@link DefaultOpCode#SERVER_SERIALIZATION_ERROR} followed by the stacktrace.
 *   When received by the client, the server's stacktrace is thrown as a {@code TripError}.</li>
 *
 *   <li>Deserialization error when receiving a {@link org.tentackle.trip.remote.RemoteResponse}:
 *   The client sends a {@link DefaultOpCode#CLIENT_DESERIALIZATION_ERROR}}, closes the connection,
 *   and throws the original exception. If the server receives this, it will close the connection as well.
 *   </li>
 * </ol>
 *
 */
public class TripError extends TripRuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  private final OpCode opCode;

  /**
   * Constructs a new trip error.
   *
   * @param opCode the opcode that caused this error
   * @param message the detail message
   */
  public TripError(OpCode opCode, String message) {
    super(opCode + ": " + message);
    this.opCode = opCode;
  }

  /**
   * Gets the opcode that caused this error.
   *
   * @return the opcode
   */
  public OpCode getOpCode() {
    return opCode;
  }

}
