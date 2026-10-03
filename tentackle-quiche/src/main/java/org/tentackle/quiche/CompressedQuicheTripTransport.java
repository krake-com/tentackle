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

package org.tentackle.quiche;

import org.tentackle.io.CompressedInputStream;
import org.tentackle.quiche.engine.QuicheStream;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TransportService;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.transport.FastCompressedOutputStream;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

/**
 * Transport implementation for QUIC over quiche with compression.
 */
@TransportService(CompressedQuicheTripTransport.PROTOCOL)
public class CompressedQuicheTripTransport extends QuicheTripTransport {

  /**
   * The protocol name for QUIC over quiche with compression.
   */
  public static final String PROTOCOL = "tripcj";


  /**
   * Creates a new compressed QUIC transport.
   *
   * @param uri the URI of the transport
   */
  public CompressedQuicheTripTransport(URI uri) {
    super(uri);
  }

  @Override
  public Runnable createConnectionHandler(String connectionName, InputStream inputStream, OutputStream outputStream) {
    return super.createConnectionHandler(connectionName,
                                         createCompressedInputStream(inputStream),
                                         createCompressedOutputStream(outputStream));
  }

  @Override
  TripStream createStream(Dictionary dictionary, QuicheStream quicStream) {
    TripFactory factory = TripFactory.getInstance();
    return factory.createStream(dictionary,
                                factory.createSerializer(createCompressedOutputStream(quicStream.getOutputStream())),
                                factory.createDeserializer(createCompressedInputStream(quicStream.getInputStream())));
  }


  /**
   * Creates the compressed output stream.
   *
   * @param outputStream the uncompressed output stream of the QUIC stream
   * @return the compressed output stream
   */
  protected OutputStream createCompressedOutputStream(OutputStream outputStream) {
    return new FastCompressedOutputStream(outputStream,
                                          getUriPars().getOrDefault("mincompress", 1024));
  }

  /**
   * Creates the compressed input stream.
   *
   * @param inputStream the uncompressed input stream of the QUIC stream
   * @return the compressed input stream
   */
  protected InputStream createCompressedInputStream(InputStream inputStream) {
    return new CompressedInputStream(inputStream);
  }

}
