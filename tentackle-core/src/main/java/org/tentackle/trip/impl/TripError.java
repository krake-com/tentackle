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

import org.tentackle.reflect.CanonicalConstructor;
import org.tentackle.reflect.CanonicalGetter;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.TripIgnore;
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
 *   the client's stacktrace is thrown as a {@code TripError}.</li>
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
 *   The client sends a {@link DefaultOpCode#CLIENT_DESERIALIZATION_ERROR}, closes the connection,
 *   and throws the original exception. If the server receives this, it will close the connection as well.
 *   </li>
 * </ol>
 *
 * This control-flow reduces the number of follow-up exceptions just bloating the logs,
 * allows to keep the connections open after serialization errors, and closes the
 * connections after deserialization errors.
 */
public class TripError extends TripRuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  private final OpCode opCode;
  private final String rawMessage;   // the message as passed to the constructor, without the opcode prefix

  /**
   * Constructs a new trip error.
   *
   * @param opCode the opcode that caused this error
   * @param message the detail message
   */
  @CanonicalConstructor
  public TripError(OpCode opCode, String message) {
    super(opCode + ": " + message);
    this.opCode = opCode;
    this.rawMessage = message;
  }


  /**
   * Gets the opcode that caused this error.
   *
   * @return the opcode
   */
  @CanonicalGetter(ordinal = 0)
  public OpCode getOpCode() {
    return opCode;
  }

  /**
   * Gets the raw, unprefixed message passed to the constructor.<br>
   * Used by TRIP to reconstruct this error via the canonical constructor: {@link #getMessage()}
   * already carries the opcode prefix, so feeding it back into the constructor would prefix it twice.
   *
   * @return the raw message
   */
  @CanonicalGetter(ordinal = 1)
  public String getRawMessage() {
    return rawMessage;
  }

  @TripIgnore
  @Override
  public String getMessage() {
    return super.getMessage();
  }

  @TripIgnore
  @Override
  public Throwable getCause() {
    return super.getCause();
  }

  /**
   * Returns whether the reason is no such delegate.
   *
   * @return true if the reason is no such delegate, false otherwise
   */
  public boolean isNoSuchDelegate() {
    return opCode == DefaultOpCode.NO_SUCH_DELEGATE;
  }

}
