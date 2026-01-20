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
import tech.kwik.core.log.SysOutLogger;
import tech.kwik.core.server.ServerConnectionConfig;
import tech.kwik.core.server.ServerConnector;

import org.tentackle.io.UriHelper;
import org.tentackle.misc.Pool;
import org.tentackle.trip.Connection;
import org.tentackle.trip.Dictionary;
import org.tentackle.trip.TransportService;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.transport.AbstractTransport;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
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

    // TODO: make kwik using Tentackle logger
    Logger log = new SysOutLogger();
    log.timeFormat(Logger.TimeFormat.Long);
    log.logWarning(true);
    log.logInfo(true);

    Map<String, String> queryParameters = UriHelper.getQueryParameters(getUri().getQuery(), String::valueOf);
    String alpnId = queryParameters.getOrDefault("alpnid", getUri().getScheme());
    String keystoreType = queryParameters.get("kstype");    // keystore instance name
    String keystorePassword = queryParameters.getOrDefault("kspass", System.getProperty("javax.net.ssl.keyStorePassword"));
    String keystoreFile = queryParameters.getOrDefault("ksfile", System.getProperty("javax.net.ssl.keyStore"));
    String certificateAlias = queryParameters.get("ksalias");
    if (keystorePassword == null || keystoreFile == null || certificateAlias == null) {
      throw new TripRuntimeException("keystore-password, keystore-file and certificate-alias must be specified");
    }

    ServerConnectionConfig serverConnectionConfig =
      ServerConnectionConfig.builder()
                            .maxOpenPeerInitiatedBidirectionalStreams(getUriPars().getOrDefault("maxstreams", 12))
                            .build();

    try {
      KeyStore keyStore = KeyStore.getInstance(keystoreType == null ? KeyStore.getDefaultType() : keystoreType);
      keyStore.load(new FileInputStream(keystoreFile), keystorePassword.toCharArray());
      ServerConnector.Builder builder =
        ServerConnector.builder()
                       .withPort(getUri().getPort())
                       .withConfiguration(serverConnectionConfig)
                       .withKeyStore(keyStore, certificateAlias, keystorePassword.toCharArray())
                       .withLogger(log);
      ServerConnector serverConnector = builder.build();
      serverConnector.registerApplicationProtocol(alpnId, new QuicProtocolConnectionFactory(this));
      serverConnector.start();
      accepting = true;
    }
    catch (CertificateException | KeyStoreException | IOException | NoSuchAlgorithmException e) {
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
