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

import org.tentackle.trip.Transport;
import org.tentackle.trip.TripFactory;

import org.testng.Reporter;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertTrue;

/**
 * Runs this module's transport against the KWIK-based one of {@code tentackle-quic}.
 * <p>
 * Two implementations of the same protocol testing only against themselves prove nothing about the
 * protocol: a shared misreading of RFC-9000 passes every such test. Here a Cloudflare quiche client talks
 * to a KWIK server and the other way round, so the handshake, the transport parameters, the stream
 * framing and the flow control all have to be right by the specification rather than by agreement.
 * <p>
 * The two transports register different URI schemes on purpose, so both are resolvable at once. Since the
 * ALPN protocol defaults to the scheme, the {@code alpnid} parameter is what makes the two ends agree.
 * <p>
 * Nothing of {@code tentackle-quic} is imported: it is reached exclusively through the transport SPI,
 * which is also what keeps this module free of a compile-time dependency on it.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class QuicheKwikInteropTest {

  /**
   * The scheme of the KWIK transport, hardcoded rather than imported for the reason above.
   */
  private static final String KWIK_SCHEME = "tripq";

  /**
   * The ALPN protocol both ends are told to use.
   */
  private static final String ALPN = "trip-interop";


  private Path keyStore;

  @BeforeClass
  public void setUp() throws Exception {
    keyStore = QuicheTestSupport.createKeyStore();
    try {
      TripFactory.getInstance().createTransport(URI.create(KWIK_SCHEME + "://localhost:1")).close();
    }
    catch (RuntimeException e) {
      throw new SkipException("the KWIK transport (" + KWIK_SCHEME + ") is not on the module path: " +
                              e.getMessage());
    }
  }

  @AfterClass
  public void tearDown() throws Exception {
    if (keyStore != null) {
      Files.deleteIfExists(keyStore);
    }
  }


  @Test
  public void testQuicheClientAgainstKwikServer() throws Exception {
    Reporter.log("a Cloudflare quiche client invokes methods on a KWIK server", true);
    exchange(KWIK_SCHEME, QuicheTripTransport.PROTOCOL);
  }

  @Test
  public void testKwikClientAgainstQuicheServer() throws Exception {
    Reporter.log("a KWIK client invokes methods on a Cloudflare quiche server", true);
    exchange(QuicheTripTransport.PROTOCOL, KWIK_SCHEME);
  }


  /**
   * Starts a server of one implementation and calls it with a client of the other.
   *
   * @param serverScheme the scheme of the server transport
   * @param clientScheme the scheme of the client transport
   * @throws Exception if the endpoints could not be set up
   */
  private void exchange(String serverScheme, String clientScheme) throws Exception {
    int port = QuicheTestSupport.freeUdpPort();
    EchoServiceImpl service = new EchoServiceImpl("interop");

    Transport server = TripFactory.getInstance().createTransport(URI.create(
      serverScheme + "://localhost:" + port + "?alpnid=" + ALPN + "&" +
      QuicheTestSupport.keyStoreParameters(keyStore)));
    try {
      server.accept();
      assertTrue(server.isAccepting());
      server.getRegistry().register(service, "echo");

      Transport client = TripFactory.getInstance().createTransport(URI.create(
        clientScheme + "://localhost:" + port + "?alpnid=" + ALPN + "&idle=1&connecttimeout=60"));
      try {
        EchoService remote = client.lookup(EchoService.class, "echo");
        assertEquals(remote.getName(), "interop");
        assertEquals(remote.add(17, 25), 42);

        // 64 kB is larger than a single packet and larger than the initial congestion window, so this
        // exercises the stream framing and the flow control rather than just the handshake
        Payload payload = Payload.of("interop", 64 * 1024);
        Payload duplicate = remote.duplicate(payload);
        assertNotSame(duplicate, payload);
        assertEquals(duplicate.text(), "interop-copy");
        assertEquals(duplicate.blob(), payload.blob());
        assertEquals(duplicate.tags(), payload.tags());

        assertEquals(service.getInvocationCount(), 3);
      }
      finally {
        client.close();
      }
    }
    finally {
      server.close();
    }
  }

}
