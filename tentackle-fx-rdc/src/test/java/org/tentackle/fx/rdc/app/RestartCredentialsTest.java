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

package org.tentackle.fx.rdc.app;

import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.common.Cryptor;
import org.tentackle.common.EncryptedProperties;
import org.tentackle.session.DefaultSessionInfo;
import org.tentackle.session.SessionInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the hand-off of the credentials to a restarted client.
 */
@SuppressWarnings("missing-explicit-ctor")
public class RestartCredentialsTest {

  private final Cryptor cryptor = new Cryptor("restart-credentials-salt", "restart-credentials-passphrase");
  private final List<Path> created = new ArrayList<>();
  private String applicationName;

  @BeforeMethod
  public void setUp() {
    // unique per test, so that concurrent builds don't see each other's files
    applicationName = "RestartTest" + System.nanoTime();
  }

  @AfterMethod
  public void tearDown() throws IOException {
    for (Path path : created.reversed()) {
      Files.deleteIfExists(path);
    }
  }


  @Test
  public void handsTheCredentialsOverAndRemovesTheFile() throws IOException {
    Path file = write("scott", "tiger".toCharArray());

    String content = Files.readString(file, StandardCharsets.US_ASCII);
    assertFalse(content.contains("scott"), content);
    assertFalse(content.contains("tiger"), content);
    assertTrue(file.getFileName().toString().startsWith(applicationName.toLowerCase()), file.toString());

    SessionInfo sessionInfo = sessionInfo();
    assertTrue(RestartCredentials.read(applicationName, file.toString(), cryptor, sessionInfo));
    assertEquals(sessionInfo.getUserName(), "scott");
    assertEquals(new String(sessionInfo.getPassword()), "tiger");
    assertFalse(Files.exists(file), "credentials file must be removed after reading");
  }

  @Test
  public void handsTheUsernameOverWithoutAPassword() throws IOException {
    Path file = write("scott", null);
    SessionInfo sessionInfo = sessionInfo();
    assertTrue(RestartCredentials.read(applicationName, file.toString(), cryptor, sessionInfo));
    assertEquals(sessionInfo.getUserName(), "scott");
    assertNull(sessionInfo.getPassword());
  }

  @Test
  public void createsAFileOnlyTheOwnerCanAccess() throws IOException {
    if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
      throw new SkipException("POSIX file permissions not supported");
    }
    Path file = write("scott", "tiger".toCharArray());
    assertEquals(Files.getPosixFilePermissions(file),
                 EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
  }

  /**
   * The file is deleted after reading, so the path from the environment must not point anywhere else.
   */
  @Test
  public void neitherReadsNorDeletesAFileOutsideTheTemporaryDirectory() throws IOException {
    Path dir = track(Files.createTempDirectory(RestartCredentials.getDirectory(), "restart-test"));
    Path file = track(Files.copy(write("scott", "tiger".toCharArray()), dir.resolve(validFileName())));
    assertRefused(file);
  }

  @Test
  public void neitherReadsNorDeletesAFileOfAnotherName() throws IOException {
    Path original = write("scott", "tiger".toCharArray());
    assertRefused(track(Files.copy(original, RestartCredentials.getDirectory().resolve("other" + validFileName()))));
    assertRefused(track(Files.copy(original, RestartCredentials.getDirectory().resolve(applicationName + ".txt"))));
    SessionInfo sessionInfo = sessionInfo();
    assertFalse(RestartCredentials.read(applicationName, "relative.cred", cryptor, sessionInfo));
    assertFalse(RestartCredentials.read(applicationName, null, cryptor, sessionInfo));
    assertTrue(Files.exists(original));
  }

  @Test
  public void neitherFollowsNorDeletesASymbolicLink() throws IOException {
    Path target = write("scott", "tiger".toCharArray());
    Path link = RestartCredentials.getDirectory().resolve(validFileName());
    try {
      track(Files.createSymbolicLink(link, target));
    }
    catch (IOException | UnsupportedOperationException ex) {
      throw new SkipException("symbolic links not supported", ex);
    }
    assertRefused(link);
    assertTrue(Files.exists(target));
  }

  @Test
  public void ignoresAndRemovesAStaleFile() throws IOException {
    Path file = write("scott", "tiger".toCharArray());
    makeStale(file);
    SessionInfo sessionInfo = sessionInfo();
    assertFalse(RestartCredentials.read(applicationName, file.toString(), cryptor, sessionInfo));
    assertNull(sessionInfo.getPassword());
    assertFalse(Files.exists(file));
  }

  /**
   * Restarts that never happen leave files behind, which the next start cleans up.
   */
  @Test
  public void discardsTheUnreadFileAndStaleFiles() throws IOException {
    Path unread = write("scott", "tiger".toCharArray());
    Path stale = write("scott", "tiger".toCharArray());
    makeStale(stale);
    Path fresh = write("scott", "tiger".toCharArray());   // a restart of another installation in progress

    RestartCredentials.discard(applicationName, unread.toString());

    assertFalse(Files.exists(unread));
    assertFalse(Files.exists(stale));
    assertTrue(Files.exists(fresh));
  }

  @Test
  public void namesTheEnvironmentVariableAfterTheApplication() {
    assertEquals(RestartCredentials.getEnvironmentName("MyApp"), "MYAPP_CREDENTIALS");
  }


  private Path write(String userName, char[] password) throws IOException {
    return track(RestartCredentials.write(applicationName, cryptor, userName, password));
  }

  private Path track(Path path) {
    created.add(path);
    return path;
  }

  private String validFileName() {
    return applicationName.toLowerCase() + "-credentials-4711.cred";
  }

  private void assertRefused(Path file) {
    SessionInfo sessionInfo = sessionInfo();
    assertFalse(RestartCredentials.read(applicationName, file.toString(), cryptor, sessionInfo), file.toString());
    assertNull(sessionInfo.getPassword());
    assertTrue(Files.exists(file, LinkOption.NOFOLLOW_LINKS), "must not be deleted: " + file);
  }

  private static void makeStale(Path file) throws IOException {
    Files.setLastModifiedTime(file, FileTime.from(
        Instant.now().minus(RestartCredentials.MAX_AGE).minusSeconds(60)));
  }

  private static SessionInfo sessionInfo() {
    return new DefaultSessionInfo(new EncryptedProperties());
  }

}
