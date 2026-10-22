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

import org.tentackle.common.Cryptor;
import org.tentackle.io.DecryptingInputStream;
import org.tentackle.io.EncryptingOutputStream;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TransportService;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;

/**
 * Transport implementation for encrypted TCP.<br>
 * Uses a {@link org.tentackle.common.Cryptor} for symmetric encryption.
 * This kind of encryption is less secure than SSL since
 * the key is pre-shared between the client and the server.
 * However, it does not need a certificate and is sometimes just "good enough",
 * for example, pragmatically used by the auto update service (see {@code org.tentackle.update}).
 */
@TransportService(EncryptedTransport.PROTOCOL)
public class EncryptedTransport extends TcpTransport {

  /**
   * The protocol name for encrypted TCP.
   */
  public static final String PROTOCOL = "tripe";

  /**
   * Creates a new transport.
   *
   * @param uri the URI of the transport
   */
  public EncryptedTransport(URI uri) {
    super(uri);
  }

  @Override
  public TripStream createStream(Dictionary dictionary, Socket socket) {
    TripFactory factory = TripFactory.getInstance();
    try {
      return factory.createStream(dictionary,
                                  factory.createSerializer(createEncryptingOutputStream(socket.getOutputStream())),
                                  factory.createDeserializer(createDecryptingInputStream(socket.getInputStream())));
    }
    catch (IOException e) {
      throw new TripRuntimeException("cannot create encrypted trip stream", e);
    }
  }

  @Override
  public Runnable createConnectionHandler(String connectionName, InputStream inputStream, OutputStream outputStream) {
    return super.createConnectionHandler(connectionName,
                                         createDecryptingInputStream(inputStream),
                                         createEncryptingOutputStream(outputStream));
  }


  /**
   * Creates the encrypting output stream.
   *
   * @param outputStream the output stream from the socket
   * @return the encrypting output stream
   */
  protected OutputStream createEncryptingOutputStream(OutputStream outputStream) {
    return new EncryptingOutputStream(outputStream, getCryptor());
  }

  /**
   * Creates the decrypting input stream.
   *
   * @param inputStream the encrypted input stream from the socket
   * @return the decrypting input stream
   */
  protected InputStream createDecryptingInputStream(InputStream inputStream) {
    return new DecryptingInputStream(inputStream, getCryptor());
  }


  /**
   * Gets the cryptor instance.<br>
   * Defaults to the application's {@link Cryptor}.
   *
   * @return the cryptor
   */
  protected Cryptor getCryptor() {
    return Cryptor.getInstance();
  }

}
