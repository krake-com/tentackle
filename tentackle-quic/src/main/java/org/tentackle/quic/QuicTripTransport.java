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

package org.tentackle.quic;

import tech.kwik.core.QuicStream;
import tech.kwik.core.log.Logger;
import tech.kwik.core.server.ServerConnectionConfig;
import tech.kwik.core.server.ServerConnector;

import org.tentackle.io.SslParameters;
import org.tentackle.io.UriHelper;
import org.tentackle.misc.Pool;
import org.tentackle.trip.Connection;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TransportService;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.transport.AbstractTransport;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.util.Map;

/**
 * Transport implementation for QUIC.
 */
@TransportService(QuicTripTransport.PROTOCOL)
public class QuicTripTransport extends AbstractTransport {

  /**
   * The protocol name for QUIC.
   */
  public static final String PROTOCOL = "tripq";


  private boolean accepting;

  /**
   * Creates a new QUIC transport.
   *
   * @param uri the URI of the transport
   */
  public QuicTripTransport(URI uri) {
    super(uri);
  }


  @Override
  public synchronized void accept() {
    super.accept();

    Logger log = new QuicLogger();
    log.timeFormat(Logger.TimeFormat.Long);
    log.logWarning(true);
    log.logInfo(true);

    URI uri = getUri();

    Map<String, String> queryParameters = UriHelper.getQueryParameters(uri.getQuery(), String::valueOf);
    String alpnId = queryParameters.getOrDefault("alpnid", uri.getScheme());

    // the keystore, certificate alias and password are resolved consistently with the TCP transports
    SslParameters sslParameters = new SslParameters(uri);
    KeyStore keyStore = sslParameters.getKeyStore();
    String certificateAlias = sslParameters.getCertificateAlias();
    if (keyStore == null || certificateAlias == null) {
      throw new TripRuntimeException(
        "keystore-file (ksfile) and certificate-alias (ksalias, or a single-entry keystore) must be specified");
    }

    ServerConnectionConfig serverConnectionConfig =
      ServerConnectionConfig.builder()
                            .maxOpenPeerInitiatedBidirectionalStreams(getUriPars().getOrDefault("maxstreams", 128))
                            .build();

    try {
      ServerConnector.Builder builder =
        ServerConnector.builder()
                       .withConfiguration(serverConnectionConfig)
                       .withKeyStore(keyStore, certificateAlias, sslParameters.getKeyPassword())
                       .withLogger(log);
      String host = uri.getHost();
      int port = uri.getPort();
      if (port < 0 || port > 0xffff) {
        throw new TripRuntimeException("invalid port number " + port + " in URI " + uri);
      }
      if ("0".equals(host)) { // use 0.0.0.0 to bind on all IPV4 interfaces, 0 binds also IPV6
        builder.withPort(port);
      }
      else if (host != null) {
        builder.withSocket(new DatagramSocket(port, InetAddress.getByName(host)));
      }
      else {
        throw new TripRuntimeException("missing host in URI " + uri);
      }
      ServerConnector serverConnector = builder.build();
      serverConnector.registerApplicationProtocol(alpnId, new QuicProtocolConnectionFactory(this));
      serverConnector.start();
      accepting = true;
    }
    catch (CertificateException | IOException e) {
      throw new TripRuntimeException("starting QUIC service failed", e);
    }
  }

  @Override
  public boolean isAccepting() {
    return accepting;
  }

  /**
   * Creates a trip stream for the given QUIC stream.
   *
   * @param dictionary the dictionary to use for serialization/deserialization
   * @param quicStream the QUIC stream
   * @return the trip stream
   */
  public TripStream createStream(Dictionary dictionary, QuicStream quicStream) {
    TripFactory factory = TripFactory.getInstance();
    return factory.createStream(dictionary,
                                factory.createSerializer(quicStream.getOutputStream()),
                                factory.createDeserializer(quicStream.getInputStream()));
  }


  @Override
  protected Pool<Connection> createClientConnectionPool() {
    return new QuicTripConnectionPool(this, getClientDictionary());
  }

}
