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

import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.common.FileHelper;
import org.tentackle.common.TentackleRuntimeException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests for {@link ClientUpdateUtilities} - detecting an updatable image and downloading
 * and verifying the ZIP the server pointed the client at.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class ClientUpdateUtilitiesTest {

  /** The sub-directories every jlink image consists of, plus the image root itself. */
  private static final String[] JLINK_SUBDIRS = { "bin", "conf", "include", "legal", "lib" };

  private static final String ZIP_PATH = "/downloads/myapp-2.1.0-linux-amd64.zip";

  private final ClientUpdateUtilities utilities = new ClientUpdateUtilities();

  private Path tempDir;
  private TestHttpServer server;
  private byte[] zipContent;
  private String checksum;

  @BeforeMethod
  public void setUp() throws Exception {
    tempDir = Files.createTempDirectory("tentackle-update-test");
    zipContent = new byte[40_000];    // large enough for the download to take several reads
    for (int i = 0; i < zipContent.length; i++) {
      zipContent[i] = (byte) i;
    }
    checksum = UpdateTestSupport.sha256(zipContent);
    server = new TestHttpServer().serve(ZIP_PATH, zipContent);
  }

  @AfterMethod
  public void tearDown() throws Exception {
    server.close();
    if (Files.exists(tempDir)) {
      FileHelper.removeDirectory(tempDir.toString());
    }
  }


  @Test
  public void providesASingleton() {
    assertNotNull(ClientUpdateUtilities.getInstance());
    assertSame(ClientUpdateUtilities.getInstance(), ClientUpdateUtilities.getInstance());
  }

  @Test
  public void usesSha256ToVerifyDownloads() throws Exception {
    assertEquals(utilities.getMessageDigest().getAlgorithm(), "SHA-256");
  }


  @Test
  public void recognizesACompleteJlinkImage() throws Exception {
    assertTrue(utilities.isUpdatableJlinkDirectory(createJlinkImage().toFile()));
  }

  /**
   * A partial image is not safe to overwrite in place - anything but the complete layout counts
   * as "cannot self-update".
   */
  @Test
  public void rejectsAnIncompleteJlinkImage() throws Exception {
    for (String missing : JLINK_SUBDIRS) {
      Path image = createJlinkImage();
      FileHelper.removeDirectory(image.resolve(missing).toString());
      assertFalse(utilities.isUpdatableJlinkDirectory(image.toFile()),
                  "must not accept an image without '" + missing + "'");
    }
  }

  @Test
  public void rejectsAnythingButADirectory() throws Exception {
    assertFalse(utilities.isUpdatableJlinkDirectory(tempDir.resolve("does-not-exist").toFile()));
    Path file = Files.writeString(tempDir.resolve("a-file"), "not a directory");
    assertFalse(utilities.isUpdatableJlinkDirectory(file.toFile()));
  }

  /**
   * The image must be writable, otherwise the update script cannot replace it.
   */
  @Test
  public void rejectsAReadOnlyJlinkImage() throws Exception {
    Path image = createJlinkImage();
    File lib = image.resolve("lib").toFile();
    if (!lib.setWritable(false) || Files.isWritable(lib.toPath())) {
      throw new SkipException("cannot make a directory read-only here (running as root?)");
    }
    try {
      assertFalse(utilities.isUpdatableJlinkDirectory(image.toFile()));
    }
    finally {
      lib.setWritable(true);
    }
  }

  /**
   * A jlink image takes precedence: it is the plain, per-user layout, whereas the jpackage probing
   * has to guess the package root from the running executable.
   */
  @Test
  public void prefersJlinkOverJPackage() {
    assertEquals(installationType(true, true), InstallationType.JLINK);
    assertEquals(installationType(true, false), InstallationType.JLINK);
    assertEquals(installationType(false, true), InstallationType.JPACKAGE);
    assertNull(installationType(false, false));
  }

  /**
   * A build started from a plain JDK - like this test run - is not a self-updatable installation.
   */
  @Test
  public void doesNotMistakeAPlainJvmForAnUpdatableInstallation() {
    assertNull(utilities.determineInstallationType());
    assertFalse(utilities.isUpdateableJlinkApplication());
    assertFalse(utilities.isUpdatableJPackageApplication());
    assertNull(utilities.determineJPackageRoot());
  }


  @Test
  public void downloadsAndVerifiesTheZip() throws Exception {
    File updateDir = tempDir.resolve("update").toFile();
    List<Double> progress = new ArrayList<>();

    File zipFile = utilities.downloadZip(updateInfo(checksum), updateDir, progress::add);

    assertTrue(updateDir.isDirectory());
    assertEquals(zipFile.getParentFile(), updateDir);
    assertEquals(zipFile.getName(), "myapp-2.1.0-linux-amd64.zip");    // taken from the URL path
    assertEquals(Files.readAllBytes(zipFile.toPath()), zipContent);

    assertFalse(progress.isEmpty());
    assertEquals(progress.getLast().doubleValue(), 1.0);
    double previous = 0.0;
    for (double value : progress) {
      assertTrue(value > previous && value <= 1.0, "unexpected progress " + value + " in " + progress);
      previous = value;
    }
  }

  @Test
  public void downloadsWithoutAProgressConsumer() throws Exception {
    File updateDir = tempDir.resolve("update").toFile();
    assertEquals(Files.readAllBytes(utilities.downloadZip(updateInfo(checksum), updateDir, null).toPath()),
                 zipContent);
  }

  /**
   * The download is plain HTTP, so the checksum is the only thing standing between the user and a
   * tampered or truncated image.
   */
  @Test
  public void refusesAZipWithAWrongChecksum() {
    File updateDir = tempDir.resolve("update").toFile();
    String wrongChecksum = checksum.replace(checksum.charAt(0), checksum.charAt(0) == 'a' ? 'b' : 'a');
    TentackleRuntimeException ex = expectThrows(
      TentackleRuntimeException.class, () -> utilities.downloadZip(updateInfo(wrongChecksum), updateDir, null));
    assertTrue(ex.getMessage().startsWith("checksum failed!"), ex.getMessage());
    assertTrue(ex.getMessage().contains(wrongChecksum));
  }

  /**
   * The hash is hex, so its case carries no information: {@code sha256sum} writes it lowercase,
   * while {@code certutil -hashfile} on Windows writes it uppercase. Rejecting the update because
   * of the case would look exactly like a tampered download to the user.
   */
  @Test
  public void acceptsAnUppercaseChecksum() throws Exception {
    File updateDir = tempDir.resolve("update").toFile();
    File zipFile = utilities.downloadZip(updateInfo(checksum.toUpperCase(Locale.ROOT)), updateDir, null);
    assertEquals(Files.readAllBytes(zipFile.toPath()), zipContent);
  }

  /**
   * The checksum is a token of a text file, so it may well carry trailing whitespace.
   */
  @Test
  public void acceptsAChecksumWithSurroundingWhitespace() throws Exception {
    File updateDir = tempDir.resolve("update").toFile();
    File zipFile = utilities.downloadZip(updateInfo("  " + checksum + "\n"), updateDir, null);
    assertEquals(Files.readAllBytes(zipFile.toPath()), zipContent);
  }

  /**
   * A digest with a leading zero byte is one hex character short and must be padded to the
   * digest's own length - not to the length of whatever the server happened to send.
   */
  @Test
  public void verifiesAChecksumWithALeadingZero() throws Exception {
    byte[] content = contentHashingToALeadingZero();
    server.serve(ZIP_PATH, content);
    String expectedChecksum = UpdateTestSupport.sha256(content);
    assertTrue(expectedChecksum.startsWith("0"), expectedChecksum);
    assertEquals(expectedChecksum.length(), UpdateTestSupport.SHA256_HEX_LENGTH);

    File updateDir = tempDir.resolve("update").toFile();
    UpdateInfo info = new UpdateInfo("2.1.0", server.getBaseUrl() + ZIP_PATH, expectedChecksum,
                                     content.length, "update.sh");
    assertEquals(Files.readAllBytes(utilities.downloadZip(info, updateDir, null).toPath()), content);
  }

  /**
   * Without a checksum there is nothing to verify, so the download must be refused
   * rather than accepted unverified.
   */
  @Test
  public void refusesADownloadWithoutAChecksum() {
    File updateDir = tempDir.resolve("update").toFile();
    for (String noChecksum : new String[] { null, "", "   " }) {
      TentackleRuntimeException ex = expectThrows(
        TentackleRuntimeException.class, () -> utilities.downloadZip(updateInfo(noChecksum), updateDir, null));
      assertTrue(ex.getMessage().startsWith("no checksum provided"), ex.getMessage());
    }
  }

  @Test
  public void reportsAnUnreachableDownload() throws Exception {
    File updateDir = tempDir.resolve("update").toFile();
    UpdateInfo info = new UpdateInfo("2.1.0", server.getBaseUrl() + "/no/such.zip", checksum,
                                     zipContent.length, "update.sh");
    TentackleRuntimeException ex = expectThrows(
      TentackleRuntimeException.class, () -> utilities.downloadZip(info, updateDir, null));
    assertEquals(ex.getMessage(), "download failed");
  }

  /**
   * The update directory must be empty before unzipping, so a leftover from a previous, aborted
   * attempt is wiped and re-created.
   */
  @Test
  public void recreatesALeftoverUpdateDirectory() throws Exception {
    File updateDir = tempDir.resolve("update").toFile();
    assertTrue(updateDir.mkdir());
    Files.writeString(updateDir.toPath().resolve("leftover.txt"), "from an aborted attempt");

    File zipFile = utilities.downloadZip(updateInfo(checksum), updateDir, null);

    assertEquals(Files.readAllBytes(zipFile.toPath()), zipContent);
    assertFalse(new File(updateDir, "leftover.txt").exists());
    assertEquals(updateDir.list().length, 1);
  }

  /**
   * A jpackage client puts the update directory inside the package root rather than into the
   * working directory, so wiping a leftover must not depend on where the directory sits.
   */
  @Test
  public void recreatesALeftoverUpdateDirectoryOutsideTheWorkingDirectory() throws Exception {
    File updateDir = Files.createDirectories(tempDir.resolve("package-root/app/update")).toFile();
    Files.writeString(updateDir.toPath().resolve("leftover.txt"), "from an aborted attempt");

    File zipFile = utilities.downloadZip(updateInfo(checksum), updateDir, null);

    assertEquals(Files.readAllBytes(zipFile.toPath()), zipContent);
    assertFalse(new File(updateDir, "leftover.txt").exists());
    assertEquals(updateDir.list().length, 1);
  }


  /**
   * Searches content whose SHA-256 starts with a zero byte.<br>
   * One in 256 candidates qualifies, so this returns almost immediately.
   *
   * @return the content
   */
  private byte[] contentHashingToALeadingZero() {
    for (int candidate = 0; candidate < 100_000; candidate++) {
      byte[] content = ("update-" + candidate).getBytes(StandardCharsets.UTF_8);
      if (UpdateTestSupport.sha256(content).startsWith("0")) {
        return content;
      }
    }
    throw new AssertionError("no content with a leading zero in its hash found");
  }

  /**
   * Creates a complete, writable jlink image layout.
   *
   * @return the image root
   * @throws IOException if the directories could not be created
   */
  private Path createJlinkImage() throws IOException {
    Path image = Files.createTempDirectory(tempDir, "jlink-image");
    for (String subdir : JLINK_SUBDIRS) {
      Files.createDirectory(image.resolve(subdir));
    }
    return image;
  }

  /**
   * Determines the installation type for the given probing results.
   *
   * @param jlink whether the jlink probing succeeds
   * @param jpackage whether the jpackage probing succeeds
   * @return the installation type, null if neither
   */
  private InstallationType installationType(boolean jlink, boolean jpackage) {
    return new ClientUpdateUtilities() {
      @Override
      public boolean isUpdateableJlinkApplication() {
        return jlink;
      }
      @Override
      public boolean isUpdatableJPackageApplication() {
        return jpackage;
      }
    }.determineInstallationType();
  }

  /**
   * Builds the update info pointing at the ZIP served by the test server.
   *
   * @param expectedChecksum the checksum the client will verify against
   * @return the update info
   */
  private UpdateInfo updateInfo(String expectedChecksum) {
    return new UpdateInfo("2.1.0", server.getBaseUrl() + ZIP_PATH, expectedChecksum,
                          zipContent.length, "update.sh");
  }

}
