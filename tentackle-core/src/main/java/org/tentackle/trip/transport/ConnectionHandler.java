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
import org.tentackle.trip.DictionaryEntry;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.PredefinedArrayType;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TripConstants;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripStreamClosedException;
import org.tentackle.trip.TripType;
import org.tentackle.trip.impl.DefaultOpCode;
import org.tentackle.trip.remote.IdentifiedRemoteResponse;
import org.tentackle.trip.remote.InitialRemoteCall;
import org.tentackle.trip.remote.InitialRemoteResponse;
import org.tentackle.trip.remote.RemoteCall;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

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

  private final TripType<DictionaryEntry> dictionaryType = TripFactory.getInstance().getType(DictionaryEntry.class);

  private final Transport transport;
  private final String connectionName;
  private final InputStream inputStream;
  private final OutputStream outputStream;

  /**
   * Creates a new handler.
   *
   * @param transport the transport associated with this accept-loop
   * @param connectionName the name of the connection
   * @param inputStream the input stream associated with this connection
   * @param outputStream the output stream associated with this connection
   */
  public ConnectionHandler(Transport transport, String connectionName, InputStream inputStream, OutputStream outputStream) {
    this.transport = Objects.requireNonNull(transport);
    this.connectionName = Objects.requireNonNull(connectionName);
    this.inputStream = Objects.requireNonNull(inputStream);
    this.outputStream = Objects.requireNonNull(outputStream);
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
   * <p>
   * <ul>
   *   <li>reads the connection request: the TRIP magic followed by the UUID of the client's transport</li>
   *   <li>the UUID is used to determine the dictionary and a TRIP stream is created</li>
   *   <li>reads the remote calls from the stream and dispatches them to the call handler thread pool.</li>
   * </ul>
   */
  @Override
  public void run() {
    try {
      readMagic();
    }
    catch (TripRuntimeException e) {
      if (ExceptionHelper.extractException(IOException.class, true, e) != null) {
        // SSL handshake exception, peer closed, etc... -> just log the messages, no stacktrace
        LOGGER.warning("connection {0} aborted: {1}",
                       new TransportClientInfo(transport, connectionName),
                       ExceptionHelper.concatenateMessages(e));
        return;
      }
      throw e;
    }

    UUID id = readUUID();
    TripStream stream = createStream(id);
    TransportClientInfo transportClientInfo = new TransportClientInfo(transport, connectionName, id);
    LOGGER.info("connection " + transportClientInfo + " opened");

    while (isConnectionAlive()) {
      TripType<?> tripType = null;
      try {
        stream.clear();
        Deserializer deserializer = stream.getDeserializer();
        tripType = deriveType(stream, deserializer.deserializeOpCode());
        if (tripType.isDictionaryBased()) {   // all remote calls are dictionary-based
          Object call = tripType.read(stream, deserializer.deserializeOpCode());
          if (call instanceof RemoteCall remoteCall) {
            // dispatch to call handler ...
            transport.getCallThreadPool().submit(transport.createCallHandler(stream, transportClientInfo, remoteCall))
                     // ... and wait for the call to finish _before_ clearing the stream.
                     // The submitted call handler runs asynchronously in a regular thread
                     // of a thread pool. Blocking virtual threads is much cheaper
                     // than blocking regular threads.
                     .get();
          }
          else {
            throw new TripRuntimeException("not a remote call: " + call.getClass());
          }
        }
        else {
          throw new TripRuntimeException("unexpected type received: " + tripType);
        }
      }
      catch (TripStreamClosedException tc) {
        LOGGER.fine("connection closed by client", tc);
        break;
      }
      catch (Throwable t) {
        // some protocol error (deserializing the request failed)
        // If the error stems from the client, the stream is usually corrupted and must be closed.
        // We can try to send back some error message, but the client is usually no more listening.
        // The stacktrace may also show a lot of follow-up errors that can be misleading,
        // but one never knows...
        if (tripType == null) {
          LOGGER.severe("cannot derive remote call type", t);
        }
        else {
          LOGGER.severe("error receiving remote call " + tripType, t);
          if (RemoteCall.class.isAssignableFrom(tripType.getObjectClass())) {
            try {
              stream.clear();
              if (InitialRemoteCall.class.isAssignableFrom(tripType.getObjectClass())) {
                stream.getType(InitialRemoteResponse.class).write(new InitialRemoteResponse(-1, null, t), stream);
              }
              else {
                stream.getType(IdentifiedRemoteResponse.class).write(new IdentifiedRemoteResponse(null, t), stream);
              }
              stream.getSerializer().flush();
            }
            catch (RuntimeException rx) {
              // sending back failed: log the error and terminate the connection
              LOGGER.severe("cannot send error response", rx);
            }
          }
        }
        break;
      }
    }
    LOGGER.info("connection " + transportClientInfo + " closed");
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
   * @return the trip stream
   */
  protected TripStream createStream(UUID id) {
    TripFactory factory = TripFactory.getInstance();
    Serializer serializer = factory.createSerializer(outputStream);
    Deserializer deserializer = factory.createDeserializer(inputStream);
    return factory.createStream(transport.getServerDictionary(id), serializer, deserializer);
  }


  private void readMagic() {
    try {
      byte[] magic = inputStream.readNBytes(2);
      if (magic.length != 2) {
        throw new TripStreamClosedException("end of stream while reading magic");
      }
      if (magic[0] != TripConstants.MAGIC[0] || magic[1] != TripConstants.MAGIC[1]) {
        throw new TripRuntimeException("invalid magic: " + HexFormat.ofDelimiter(", ").withPrefix("0x").formatHex(magic));
      }
    }
    catch (IOException e) {
      throw new TripRuntimeException("reading magic failed", e);
    }
  }

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

  private TripType<?> deriveType(TripStream stream, OpCode opCode) {
    TripType<?> type;
    if (opCode.isFixedType()) {
      type = opCode.getFixedType();
    }
    else {
      Deserializer deserializer = stream.getDeserializer();
      if (opCode.isPredefinedArray()) {
        char arrayCode = deserializer.deserializeChar();
        PredefinedArrayType arrayType = PredefinedArrayType.valueOf(arrayCode);
        if (arrayType == null) {
          throw new TripRuntimeException("'" + arrayCode + "' is not a predefined array subtype");
        }
        type = opCode.getPredefinedArrayType(arrayType);
      }
      else {
        assertDictionaryBased(opCode);
        opCode = deserializer.deserializeOpCode();  // skip code OBJECT, RECORD, etc...
        type = receiveDictionary(stream, opCode).getType();
      }
    }
    return type;
  }

  private void assertDictionaryBased(OpCode opCode) {
    if (!opCode.isDictionaryBased()) {
      // usually a trip type implementation error
      throw new TripRuntimeException("OpCode " + opCode + " is not dictionary-based");
    }
  }

  private DictionaryEntry receiveDictionary(TripStream stream, OpCode opCode) {
    DictionaryEntry entry;
    if (opCode == DefaultOpCode.DICTIONARY) {
      entry = dictionaryType.read(stream, opCode);
      stream.getDictionary().addEntry(entry);
    }
    else {
      // must be the ordinal
      int ordinal = stream.readOrdinal(opCode);
      entry = stream.getDictionaryEntry(ordinal);
    }
    return entry;
  }

}
