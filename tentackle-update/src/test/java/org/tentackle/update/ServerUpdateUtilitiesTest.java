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

package org.tentackle.update;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TransportManager;
import org.tentackle.trip.TripRuntimeException;

import java.net.URI;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests for {@link ServerUpdateUtilities}, which publishes the {@link UpdateService} on a
 * TRIP transport.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class ServerUpdateUtilitiesTest {

  private static final String UPDATE_URL = "http://downloads.example.org/myapp";

  private final ServerUpdateUtilities utilities = new ServerUpdateUtilities();

  @AfterMethod
  public void tearDown() {
    TransportManager.getInstance().closeAllTransports();
  }


  @Test
  public void providesASingleton() {
    assertNotNull(ServerUpdateUtilities.getInstance());
    assertSame(ServerUpdateUtilities.getInstance(), ServerUpdateUtilities.getInstance());
  }

  /**
   * The implementation class is named in the server's configuration, so it is instantiated
   * reflectively through its mandatory {@code String} constructor taking the download base URL.
   */
  @Test
  public void createsTheServiceImplementationFromItsUrlConstructor() {
    UpdateService service = utilities.createUpdateServiceImpl(TestUpdateServiceImpl.class, UPDATE_URL);
    assertEquals(service.getClass(), TestUpdateServiceImpl.class);
    assertEquals(((TestUpdateServiceImpl) service).getUpdateURL(), UPDATE_URL + "/");
  }

  @Test
  public void reportsAMissingUrlConstructor() {
    TentackleRuntimeException ex = expectThrows(
      TentackleRuntimeException.class, () -> utilities.createUpdateServiceImpl(WithoutUrlConstructor.class, UPDATE_URL));
    assertTrue(ex.getMessage().contains(WithoutUrlConstructor.class.getName()), ex.getMessage());
  }

  @Test
  public void reportsAFailingConstructor() {
    TentackleRuntimeException ex = expectThrows(
      TentackleRuntimeException.class, () -> utilities.createUpdateServiceImpl(FailingConstructor.class, UPDATE_URL));
    assertTrue(ex.getMessage().contains(FailingConstructor.class.getName()), ex.getMessage());
  }

  @Test
  public void reportsAnAbstractImplementationClass() {
    expectThrows(TentackleRuntimeException.class,
                 () -> utilities.createUpdateServiceImpl(AbstractUpdateServiceImpl.class, UPDATE_URL));
  }


  /**
   * The service is registered under the <em>path</em> of the service URL, which is what the
   * client looks up.
   */
  @Test
  public void registersTheServiceUnderTheUriPath() throws Exception {
    URI uri = URI.create("trip://localhost:" + UpdateTestSupport.freeTcpPort() + "/Update");
    utilities.startUpdateService(uri.toString(), UPDATE_URL, TestUpdateServiceImpl.class);

    Transport transport = TransportManager.getInstance().requestTransport(uri);
    assertTrue(transport.isAccepting());
    assertNotNull(transport.getRegistry().lookup("/Update"));
  }

  /**
   * A server publishing several services on the same endpoint must not try to accept twice.
   */
  @Test
  public void reusesAnAlreadyAcceptingTransport() throws Exception {
    int port = UpdateTestSupport.freeTcpPort();
    utilities.startUpdateService("trip://localhost:" + port + "/Update", UPDATE_URL, TestUpdateServiceImpl.class);
    utilities.startUpdateService("trip://localhost:" + port + "/Update2", UPDATE_URL, TestUpdateServiceImpl.class);

    Transport transport = TransportManager.getInstance().requestTransport(URI.create("trip://localhost:" + port));
    assertNotNull(transport.getRegistry().lookup("/Update"));
    assertNotNull(transport.getRegistry().lookup("/Update2"));
  }

  @Test
  public void reportsAnUnusableServiceUrl() {
    TripRuntimeException ex = expectThrows(
      TripRuntimeException.class,
      () -> utilities.startUpdateService("nosuchprotocol://localhost:33001/Update", UPDATE_URL,
                                         TestUpdateServiceImpl.class));
    assertTrue(ex.getMessage().startsWith("cannot start update service"), ex.getMessage());
  }


  /** An implementation without the mandatory {@code String} constructor. */
  public static class WithoutUrlConstructor implements UpdateService {
    @Override
    public UpdateInfo getUpdateInfo(ClientInfo info) {
      return null;
    }
  }

  /** An implementation whose constructor fails. */
  public static class FailingConstructor implements UpdateService {
    /**
     * Creates the service - or rather, does not.
     *
     * @param updateURL the download base URL
     */
    public FailingConstructor(String updateURL) {
      throw new IllegalStateException("no downloads configured for " + updateURL);
    }
    @Override
    public UpdateInfo getUpdateInfo(ClientInfo info) {
      return null;
    }
  }

}
