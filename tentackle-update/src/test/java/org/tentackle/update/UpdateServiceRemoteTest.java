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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.common.FileHelper;
import org.tentackle.trip.TransportManager;
import org.tentackle.trip.TripRuntimeException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * End-to-end test of a complete update round trip within one JVM:
 * the server publishes the {@link UpdateService} on a TRIP transport, the client looks it up,
 * asks for an update and downloads the offered ZIP from the (separate) HTTP endpoint.
 * <p>
 * A TRIP transport is either accepting or connecting, never both, and the
 * {@link TransportManager} keys transports by scheme, host and port. Addressing the server by
 * {@code 127.0.0.1} instead of {@code localhost} therefore yields a second, client-side transport talking
 * to the server over a real socket - the same setup as two separate JVMs, only closer.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class UpdateServiceRemoteTest {

  private static final String BASE_PATH = "/downloads/";
  private static final String ZIP_PATH = BASE_PATH + TestUpdateServiceImpl.ARTIFACT_NAME
                                         + '-' + TestUpdateServiceImpl.VERSION + "-linux-amd64.zip";
  private static final String CHECKSUM_PATH = ZIP_PATH.substring(0, ZIP_PATH.length() - 4) + ".sha256";

  private static final ClientInfo CLIENT =
    new ClientInfo("myapp", "1.0.0", "linux", "amd64", InstallationType.JLINK);

  private TestHttpServer downloadServer;
  private Path tempDir;
  private byte[] zipContent;
  private String checksum;
  private String serviceUrl;
  private String clientServiceUrl;

  @BeforeMethod
  public void setUp() throws Exception {
    tempDir = Files.createTempDirectory("tentackle-update-remote-test");
    zipContent = new byte[16_384];
    for (int i = 0; i < zipContent.length; i++) {
      zipContent[i] = (byte) (i * 31);
    }
    checksum = UpdateTestSupport.sha256(zipContent);
    downloadServer = new TestHttpServer()
      .serve(ZIP_PATH, zipContent)
      .serve(CHECKSUM_PATH, checksum + "  update.zip\n");

    int port = UpdateTestSupport.freeTcpPort();
    serviceUrl = "trip://localhost:" + port + "/Update";
    clientServiceUrl = "trip://127.0.0.1:" + port + "/Update";

    ServerUpdateUtilities.getInstance().startUpdateService(
      serviceUrl, downloadServer.getBaseUrl() + BASE_PATH, TestUpdateServiceImpl.class);
  }

  @AfterMethod
  public void tearDown() throws Exception {
    TransportManager.getInstance().closeAllTransports();
    downloadServer.close();
    FileHelper.removeDirectory(tempDir.toString());
  }


  /**
   * The whole point of the module: ask the server what to install, then install exactly that.
   */
  @Test
  public void answersAnUpdateRequestOverTrip() {
    UpdateService service = ClientUpdateUtilities.getInstance().getUpdateService(clientServiceUrl);
    UpdateInfo info = service.getUpdateInfo(CLIENT);

    assertEquals(info.version(), TestUpdateServiceImpl.VERSION);
    assertEquals(info.urlStr(), downloadServer.getBaseUrl() + ZIP_PATH);
    assertEquals(info.checksum(), checksum);
    assertEquals(info.size(), zipContent.length);
    assertEquals(info.updateExecutor(), "update.sh");

    // the client is about to restart, so the transports are torn down gracefully
    assertTrue(info.isCloseAllTransportsRequested());
  }

  /**
   * The download URL is the server's decision and points at an HTTP endpoint of its choosing -
   * here a completely separate server, as a CDN or a local mirror would be in production.
   */
  @Test
  public void downloadsTheOfferedZipFromTheAdvertisedUrl() throws Exception {
    UpdateService service = ClientUpdateUtilities.getInstance().getUpdateService(clientServiceUrl);
    UpdateInfo info = service.getUpdateInfo(CLIENT);

    File updateDir = tempDir.resolve("update").toFile();
    File zipFile = ClientUpdateUtilities.getInstance().downloadZip(info, updateDir, null);

    assertEquals(Files.readAllBytes(zipFile.toPath()), zipContent);
    assertEquals(downloadServer.getRequestCount(ZIP_PATH), 2);    // the HEAD of the server, the GET of the client
  }

  /**
   * A jpackage client is offered a different artifact, built from the same convention.
   */
  @Test
  public void offersTheJPackageArtifactToAJPackageClient() {
    downloadServer.serve(BASE_PATH + TestUpdateServiceImpl.ARTIFACT_NAME + '-' + TestUpdateServiceImpl.VERSION
                         + "-mac-aarch64-pkg.zip", zipContent)
                  .serve(BASE_PATH + TestUpdateServiceImpl.ARTIFACT_NAME + '-' + TestUpdateServiceImpl.VERSION
                         + "-mac-aarch64-pkg.sha256", checksum + "  update.zip\n");

    UpdateService service = ClientUpdateUtilities.getInstance().getUpdateService(clientServiceUrl);
    UpdateInfo info = service.getUpdateInfo(
      new ClientInfo("myapp", "1.0.0", "mac", "aarch64", InstallationType.JPACKAGE));

    assertTrue(info.urlStr().endsWith("-mac-aarch64-pkg.zip"), info.urlStr());
    assertEquals(info.updateExecutor(), "update.sh");
  }

  @Test
  public void reportsAnUnknownServiceName() {
    TripRuntimeException ex = expectThrows(TripRuntimeException.class,
                 () -> ClientUpdateUtilities.getInstance().getUpdateService(
                   clientServiceUrl.replace("/Update", "/NoSuchService")));
    assertTrue(ex.getMessage().contains("remote object not found"), ex.getMessage());
  }

}
