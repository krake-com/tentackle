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

import org.tentackle.io.CompressedInputStream;
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
 * Transport implementation for compressed SSL over TCP.<br>
 * Packets smaller than the URI query parameter {@code mincompress} (default 1024) are not compressed.
 */
@TransportService(CompressedSslTransport.PROTOCOL)
public class CompressedSslTransport extends SslTransport {

  /**
   * The protocol name for compressed SSL over TCP.
   */
  public static final String PROTOCOL = "tripcs";

  /**
   * Creates a new compressed SSL transport.
   *
   * @param uri the URI of the transport
   */
  public CompressedSslTransport(URI uri) {
    super(uri);
  }


  @Override
  public TripStream createStream(Dictionary dictionary, Socket socket) {
    TripFactory factory = TripFactory.getInstance();
    try {
      return factory.createStream(dictionary,
                                  factory.createSerializer(createCompressedOutputStream(socket.getOutputStream())),
                                  factory.createDeserializer(createCompressedInputStream(socket.getInputStream())));
    }
    catch (IOException e) {
      throw new TripRuntimeException("cannot create compressed SSL trip stream", e);
    }
  }

  @Override
  public Runnable createConnectionHandler(String connectionName, InputStream inputStream, OutputStream outputStream) {
    return super.createConnectionHandler(connectionName,
                                         createCompressedInputStream(inputStream),
                                         createCompressedOutputStream(outputStream));
  }


  /**
   * Creates the compressed output stream.
   *
   * @param outputStream the uncompressed output stream from the socket
   * @return the compressed output stream
   */
  protected OutputStream createCompressedOutputStream(OutputStream outputStream) {
    return new FastCompressedOutputStream(outputStream,
                                          getUriPars().getOrDefault("mincompress", 1024));
  }

  /**
   * Creates the compressed input stream.
   *
   * @param inputStream the uncompressed input stream from the socket
   * @return the compressed input stream
   */
  protected InputStream createCompressedInputStream(InputStream inputStream) {
    return new CompressedInputStream(inputStream);
  }

}
