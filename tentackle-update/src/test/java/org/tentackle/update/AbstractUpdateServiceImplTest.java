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

import org.tentackle.common.TentackleRuntimeException;

import java.net.URL;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests for {@link AbstractUpdateServiceImpl}, the server-side implementation deriving everything
 * a client needs from the artifact naming convention:
 * <pre>
 *   &lt;updateURL&gt;/&lt;artifact&gt;-&lt;version&gt;-&lt;platform&gt;-&lt;architecture&gt;[-pkg].zip
 * </pre>
 * plus a sibling {@code .sha256} file.
 * <p>
 * The checksum and the download size are fetched over real HTTP from a {@link TestHttpServer},
 * so the download, the {@code HEAD} request and the metadata caching are all covered.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class AbstractUpdateServiceImplTest {

  private static final String BASE_PATH = "/downloads/";
  private static final String ZIP_PATH = BASE_PATH + TestUpdateServiceImpl.ARTIFACT_NAME
                                         + '-' + TestUpdateServiceImpl.VERSION + "-linux-amd64.zip";
  private static final String CHECKSUM_PATH = ZIP_PATH.substring(0, ZIP_PATH.length() - 4) + ".sha256";

  private static final ClientInfo LINUX_JLINK =
    new ClientInfo("myapp", "1.0.0", "linux", "amd64", InstallationType.JLINK);

  private TestHttpServer server;
  private byte[] zipContent;
  private String checksum;

  @BeforeMethod
  public void setUp() throws Exception {
    zipContent = new byte[3072];
    for (int i = 0; i < zipContent.length; i++) {
      zipContent[i] = (byte) i;
    }
    checksum = UpdateTestSupport.sha256(zipContent);
    server = new TestHttpServer()
      .serve(ZIP_PATH, zipContent)
      .serve(CHECKSUM_PATH, checksum + "  " + ZIP_PATH.substring(BASE_PATH.length()) + "\n");
  }

  @AfterMethod
  public void tearDown() throws Exception {
    server.close();
  }


  /**
   * The base URL is concatenated with the artifact name, so a missing slash would silently
   * produce a wrong URL.
   */
  @Test
  public void normalizesTheUpdateUrlToATrailingSlash() {
    assertEquals(new TestUpdateServiceImpl("http://host/downloads").getUpdateURL(), "http://host/downloads/");
    assertEquals(new TestUpdateServiceImpl("http://host/downloads/").getUpdateURL(), "http://host/downloads/");

    AbstractUpdateServiceImpl service = new TestUpdateServiceImpl("http://host/downloads/");
    service.setUpdateURL("http://mirror/other");
    assertEquals(service.getUpdateURL(), "http://mirror/other/");
  }

  @Test
  public void buildsTheZipAndChecksumUrlsFromTheNamingConvention() {
    AbstractUpdateServiceImpl service = new TestUpdateServiceImpl("http://host/downloads");
    assertEquals(service.createZipURL(LINUX_JLINK).toExternalForm(),
                 "http://host/downloads/myapp-jlink-client-2.1.0-linux-amd64.zip");
    assertEquals(service.createChecksumURL(LINUX_JLINK).toExternalForm(),
                 "http://host/downloads/myapp-jlink-client-2.1.0-linux-amd64.sha256");
  }

  /**
   * jpackage images have a different directory layout, hence a separate artifact.
   */
  @Test
  public void marksJPackageArtifactsWithAPkgSuffix() {
    AbstractUpdateServiceImpl service = new TestUpdateServiceImpl("http://host/downloads");
    ClientInfo macJPackage = new ClientInfo("myapp", "1.0.0", "mac", "aarch64", InstallationType.JPACKAGE);
    assertEquals(service.createBaseUrlName(macJPackage),
                 "http://host/downloads/myapp-jlink-client-2.1.0-mac-aarch64-pkg");
    assertEquals(service.createZipURL(macJPackage).toExternalForm(),
                 "http://host/downloads/myapp-jlink-client-2.1.0-mac-aarch64-pkg.zip");
  }

  /**
   * A client whose installation type is unknown is treated like a jlink image.
   */
  @Test
  public void treatsAnUnknownInstallationTypeAsJlink() {
    AbstractUpdateServiceImpl service = new TestUpdateServiceImpl("http://host/downloads");
    assertEquals(service.createBaseUrlName(new ClientInfo("myapp", "1.0.0", "linux", "amd64", null)),
                 "http://host/downloads/myapp-jlink-client-2.1.0-linux-amd64");
  }

  @Test
  public void picksThePlatformSpecificUpdateExecutor() {
    AbstractUpdateServiceImpl service = new TestUpdateServiceImpl("http://host/downloads");
    assertEquals(service.createUpdateExecutor(
      new ClientInfo("myapp", "1.0.0", "windows", "amd64", InstallationType.JPACKAGE)), "update.cmd");
    assertEquals(service.createUpdateExecutor(
      new ClientInfo("myapp", "1.0.0", "mac", "aarch64", InstallationType.JPACKAGE)), "update.sh");
    assertEquals(service.createUpdateExecutor(
      new ClientInfo("myapp", "1.0.0", "linux", "amd64", InstallationType.JLINK)), "update.sh");
  }

  @Test
  public void rejectsAnUnusableUpdateUrl() {
    AbstractUpdateServiceImpl service = new TestUpdateServiceImpl("not a url");
    expectThrows(TentackleRuntimeException.class, () -> service.createZipURL(LINUX_JLINK));
    expectThrows(TentackleRuntimeException.class, () -> service.createChecksumURL(LINUX_JLINK));
  }


  /**
   * The {@code .sha256} file holds the hash followed by the file name, as produced by
   * {@code sha256sum}; only the first token is the hash.
   */
  @Test
  public void readsTheHashFromTheChecksumFile() throws Exception {
    AbstractUpdateServiceImpl service = new TestUpdateServiceImpl(server.getBaseUrl() + BASE_PATH);
    assertEquals(service.determineChecksumHash(server.url(CHECKSUM_PATH)), checksum);
  }

  /**
   * Every client asks for the same artifact, so the metadata must be fetched once only.
   */
  @Test
  public void cachesTheChecksumPerUrl() throws Exception {
    AbstractUpdateServiceImpl service = new TestUpdateServiceImpl(server.getBaseUrl() + BASE_PATH);
    URL checksumURL = server.url(CHECKSUM_PATH);
    assertEquals(service.determineChecksumHash(checksumURL), checksum);
    assertEquals(server.getRequestCount(CHECKSUM_PATH), 1);

    server.serve(CHECKSUM_PATH, "0000000000000000000000000000000000000000000000000000000000000000  x.zip");
    assertEquals(service.determineChecksumHash(checksumURL), checksum);   // still the cached one
    assertEquals(server.getRequestCount(CHECKSUM_PATH), 1);               // and not re-fetched
  }

  /**
   * The size drives the client's progress bar and is taken from the {@code content-length} of a
   * {@code HEAD} request - the ZIP itself is never pulled through the server.
   */
  @Test
  public void readsTheDownloadSizeFromTheContentLength() throws Exception {
    AbstractUpdateServiceImpl service = new TestUpdateServiceImpl(server.getBaseUrl() + BASE_PATH);
    assertEquals(service.determineDownloadSize(server.url(ZIP_PATH)), zipContent.length);
  }

  @Test
  public void cachesTheDownloadSizePerUrl() throws Exception {
    AbstractUpdateServiceImpl service = new TestUpdateServiceImpl(server.getBaseUrl() + BASE_PATH);
    URL zipURL = server.url(ZIP_PATH);
    assertEquals(service.determineDownloadSize(zipURL), zipContent.length);
    assertEquals(server.getRequestCount(ZIP_PATH), 1);

    server.serve(ZIP_PATH, new byte[1]);
    assertEquals(service.determineDownloadSize(zipURL), zipContent.length);
    assertEquals(server.getRequestCount(ZIP_PATH), 1);
  }

  /**
   * A CDN may answer without a {@code content-length}; the client then shows an indeterminate
   * progress bar instead of failing the update.
   */
  @Test
  public void reportsAnUnknownDownloadSizeAsMinusOne() throws Exception {
    server.withoutContentLength(ZIP_PATH);
    AbstractUpdateServiceImpl service = new TestUpdateServiceImpl(server.getBaseUrl() + BASE_PATH);
    assertEquals(service.determineDownloadSize(server.url(ZIP_PATH)), -1L);
  }


  @Test
  public void assemblesTheUpdateInfoFromTheServedArtifact() {
    AbstractUpdateServiceImpl service = new TestUpdateServiceImpl(server.getBaseUrl() + BASE_PATH);
    UpdateInfo info = service.getUpdateInfo(LINUX_JLINK);

    assertEquals(info.version(), TestUpdateServiceImpl.VERSION);
    assertEquals(info.urlStr(), server.getBaseUrl() + ZIP_PATH);
    assertEquals(info.checksum(), checksum);
    assertEquals(info.size(), zipContent.length);
    assertEquals(info.updateExecutor(), "update.sh");
  }

  /**
   * The version offered is always the server's own - the client's version is only ever compared
   * against it, never used to build the URL.
   */
  @Test
  public void alwaysOffersTheServersOwnVersion() {
    AbstractUpdateServiceImpl service = new TestUpdateServiceImpl(server.getBaseUrl() + BASE_PATH);
    ClientInfo ancient = new ClientInfo("myapp", "0.0.1-SNAPSHOT", "linux", "amd64", InstallationType.JLINK);
    assertEquals(service.getUpdateInfo(ancient).version(), TestUpdateServiceImpl.VERSION);
  }

  /**
   * A missing artifact must not leave the client guessing.
   */
  @Test
  public void failsWhenTheArtifactIsNotPublished() {
    AbstractUpdateServiceImpl service = new TestUpdateServiceImpl(server.getBaseUrl() + "/nowhere/");
    RuntimeException ex = expectThrows(RuntimeException.class, () -> service.getUpdateInfo(LINUX_JLINK));
    assertEquals(ex.getMessage(), "cannot create update info");
    assertNotNull(ex.getCause());
  }

  /**
   * Every step is a {@code protected} method, so an application can deviate from the convention
   * (per-customer artifacts, a different executor, extra fields) without reimplementing the flow.
   */
  @Test
  public void letsSubclassesCustomizeEveryStep() {
    AbstractUpdateServiceImpl service = new TestUpdateServiceImpl(server.getBaseUrl() + "/elsewhere/") {
      @Override
      protected String createBaseUrlName(ClientInfo clientInfo) {
        return server.getBaseUrl() + ZIP_PATH.substring(0, ZIP_PATH.length() - 4);
      }
      @Override
      protected String createUpdateExecutor(ClientInfo clientInfo) {
        return "install-" + clientInfo.application() + ".sh";
      }
      @Override
      protected UpdateInfo createUpdateInfo(String version, URL zipURL, String checksumHash,
                                            long size, String updateExecutor) {
        return super.createUpdateInfo(version + "-custom", zipURL, checksumHash, size, updateExecutor);
      }
    };

    UpdateInfo info = service.getUpdateInfo(LINUX_JLINK);
    assertEquals(info.version(), TestUpdateServiceImpl.VERSION + "-custom");
    assertEquals(info.urlStr(), server.getBaseUrl() + ZIP_PATH);
    assertEquals(info.checksum(), checksum);
    assertEquals(info.updateExecutor(), "install-myapp.sh");
    assertTrue(info.isCloseAllTransportsRequested());
  }

}
