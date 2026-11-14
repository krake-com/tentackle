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
 * The key of the application's {@link org.tentackle.common.Cryptor} is pre-shared between the client and the server.
 * Each connection derives keys of its own from it and random salts of both peers, and every packet is encrypted
 * and authenticated with AES-GCM (see {@link EncryptedChannel}).
 * This kind of encryption is less secure than SSL, since anybody holding the client holds the key as well.
 * However, it does not need a certificate and is sometimes just "good enough",
 * for example, pragmatically used by the auto update service (see {@code org.tentackle.update}).
 * <p>
 * The server also accepts clients of the previous version (AES/ECB), unless the URI query parameter
 * {@code legacy} is {@code 0}.
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
      EncryptedChannel channel = createChannel(socket.getInputStream(), socket.getOutputStream(), false);
      return factory.createStream(dictionary,
                                  factory.createSerializer(createEncryptingOutputStream(channel)),
                                  factory.createDeserializer(createDecryptingInputStream(channel)));
    }
    catch (IOException e) {
      throw new TripRuntimeException("cannot create encrypted trip stream", e);
    }
  }

  @Override
  public Runnable createConnectionHandler(String connectionName, InputStream inputStream, OutputStream outputStream) {
    EncryptedChannel channel = createChannel(inputStream, outputStream, true);
    return super.createConnectionHandler(connectionName,
                                         createDecryptingInputStream(channel),
                                         createEncryptingOutputStream(channel));
  }


  /**
   * Creates the encrypted channel of a connection.<br>
   * The server accepts clients of the previous version (AES/ECB) unless the URI query parameter
   * {@code legacy} is {@code 0}.
   *
   * @param inputStream the input stream from the socket
   * @param outputStream the output stream from the socket
   * @param server true if server side
   * @return the channel
   */
  protected EncryptedChannel createChannel(InputStream inputStream, OutputStream outputStream, boolean server) {
    return new EncryptedChannel(getCryptor(), inputStream, outputStream, server,
                                getUriPars().getOrDefault("legacy", 1) != 0);
  }

  /**
   * Creates the encrypting output stream.
   *
   * @param channel the encrypted channel
   * @return the encrypting output stream
   */
  protected OutputStream createEncryptingOutputStream(EncryptedChannel channel) {
    return channel.getOutputStream();
  }

  /**
   * Creates the decrypting input stream.
   *
   * @param channel the encrypted channel
   * @return the decrypting input stream
   */
  protected InputStream createDecryptingInputStream(EncryptedChannel channel) {
    return channel.getInputStream();
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
