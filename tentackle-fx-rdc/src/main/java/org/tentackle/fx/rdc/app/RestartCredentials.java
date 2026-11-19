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

import org.tentackle.common.Cryptor;
import org.tentackle.common.StringHelper;
import org.tentackle.log.Logger;
import org.tentackle.session.SessionInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Hands the credentials of a client over to its restarted successor, for example after an update.
 * <p>
 * The username and password are encrypted with the {@link Cryptor} and written to a temporary file that only
 * the current user can access. Only the path of that file is passed to the restarted client, in the environment
 * variable {@code <APP>_CREDENTIALS}, where {@code <APP>} is the application name reduced to ASCII letters and
 * digits, in uppercase. The restarted client reads the file and deletes it.
 * <p>
 * The credentials themselves never appear in an environment. That matters because a process cannot remove a
 * variable from its own environment: the restarted client would keep it for its whole lifetime and pass it on to
 * every process it starts, such as a browser or a viewer. The {@code Cryptor}'s key ships with the client, so
 * its encryption alone does not protect the password from whoever gets hold of the variable.
 * <p>
 * A file that is not picked up within {@link #MAX_AGE}, because the client was not restarted, is ignored and
 * removed by the next start of the application (see {@link #discard(String)}).
 */
public final class RestartCredentials {

  /**
   * Suffix of the environment variable holding the path of the credentials file.
   */
  public static final String ENVIRONMENT_SUFFIX = "_CREDENTIALS";

  /**
   * Maximum age of a credentials file.<br>
   * Older files are ignored and removed.
   */
  public static final Duration MAX_AGE = Duration.ofMinutes(5);

  private static final Logger LOGGER = Logger.get(RestartCredentials.class);

  private static final String FILE_INFIX = "-credentials-";
  private static final String FILE_SUFFIX = ".cred";
  private static final int MAX_FILE_SIZE = 8192;


  /**
   * Gets the name of the environment variable holding the path of the credentials file.
   *
   * @param applicationName the application name
   * @return the variable name, {@code <APP>_CREDENTIALS}
   */
  public static String getEnvironmentName(String applicationName) {
    return (toAsciiName(applicationName) + ENVIRONMENT_SUFFIX).toUpperCase(Locale.ROOT);
  }

  /**
   * Writes the credentials to a new file only the current user can access.
   *
   * @param applicationName the application name
   * @param cryptor the cryptor to encrypt the credentials
   * @param userName the username
   * @param password the password, null if none
   * @return the path of the file, to be passed in the environment variable {@link #getEnvironmentName(String)}
   * @throws IOException if the file could not be created or written
   */
  public static Path write(String applicationName, Cryptor cryptor, String userName, char[] password) throws IOException {
    Path file = createPrivateFile(getFilePrefix(applicationName));
    try {
      StringBuilder buf = new StringBuilder(cryptor.encrypt64(userName)).append('\n');
      if (password != null) {
        buf.append(cryptor.encrypt64(password)).append('\n');
      }
      Files.writeString(file, buf, StandardCharsets.US_ASCII,
                        StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS);
      return file;
    }
    catch (IOException | RuntimeException ex) {
      delete(file);
      throw ex;
    }
  }

  /**
   * Reads the credentials passed by the previous instance of the application and deletes the file.<br>
   * The path is taken from the environment variable {@link #getEnvironmentName(String)}.
   * Files outside the temporary directory, not named like a credentials file, or older than
   * {@link #MAX_AGE} are ignored.
   *
   * @param applicationName the application name
   * @param cryptor the cryptor to decrypt the credentials
   * @param sessionInfo the session info to receive the username and password
   * @return true if the credentials were applied to the session info
   */
  public static boolean read(String applicationName, Cryptor cryptor, SessionInfo sessionInfo) {
    return read(applicationName, System.getenv(getEnvironmentName(applicationName)), cryptor, sessionInfo);
  }

  /**
   * Removes the credentials file passed by the previous instance, if it was not read, and any stale
   * credentials files of the application left behind by restarts that never happened.
   *
   * @param applicationName the application name
   */
  public static void discard(String applicationName) {
    discard(applicationName, System.getenv(getEnvironmentName(applicationName)));
  }

  /**
   * Deletes a credentials file.
   *
   * @param file the file, null if none
   */
  public static void delete(Path file) {
    if (file != null) {
      try {
        Files.deleteIfExists(file);
      }
      catch (IOException | RuntimeException ex) {
        LOGGER.warning("could not delete credentials file " + file, ex);
      }
    }
  }


  /**
   * Reads the credentials and deletes the file.
   *
   * @param applicationName the application name
   * @param pathName the path of the credentials file, null if none
   * @param cryptor the cryptor to decrypt the credentials
   * @param sessionInfo the session info to receive the username and password
   * @return true if the credentials were applied to the session info
   */
  static boolean read(String applicationName, String pathName, Cryptor cryptor, SessionInfo sessionInfo) {
    Path file = resolve(applicationName, pathName);
    if (file != null) {
      try {
        if (isStale(file)) {
          LOGGER.warning("ignoring stale credentials file {0}", file);
        }
        else if (Files.size(file) > MAX_FILE_SIZE) {
          LOGGER.warning("ignoring oversized credentials file {0}", file);
        }
        else {
          List<String> lines = Files.readAllLines(file, StandardCharsets.US_ASCII);
          if (!lines.isEmpty() && !lines.getFirst().isBlank()) {
            sessionInfo.setUserName(cryptor.decrypt64(lines.getFirst()));
            if (lines.size() > 1 && !lines.get(1).isBlank()) {
              sessionInfo.setPassword(cryptor.decrypt64ToChars(lines.get(1)));
            }
            return true;
          }
        }
      }
      catch (IOException | RuntimeException ex) {
        LOGGER.warning("reading the credentials file " + file + " failed", ex);
      }
      finally {
        delete(file);
      }
    }
    return false;
  }

  /**
   * Removes the given credentials file, if it was not read, and the stale files of the application.
   *
   * @param applicationName the application name
   * @param pathName the path of the credentials file, null if none
   */
  static void discard(String applicationName, String pathName) {
    delete(resolve(applicationName, pathName));
    Path dir = getDirectory();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, getFilePrefix(applicationName) + "*" + FILE_SUFFIX)) {
      for (Path file : stream) {
        if (isCredentialsFileName(applicationName, file.getFileName().toString()) &&
            Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && isStale(file)) {
          LOGGER.info("removing stale credentials file {0}", file);
          delete(file);
        }
      }
    }
    catch (IOException | RuntimeException ex) {
      LOGGER.warning("cleaning up stale credentials files in " + dir + " failed", ex);
    }
  }

  /**
   * Gets the directory holding the credentials files.
   *
   * @return the temporary directory
   */
  static Path getDirectory() {
    return Path.of(System.getProperty("java.io.tmpdir"));
  }


  /**
   * Validates the path of a credentials file.<br>
   * Since the file is deleted after reading, it must be a regular file in the temporary directory
   * named like a credentials file of the application.
   *
   * @param applicationName the application name
   * @param pathName the path, null if none
   * @return the path, null if none, already removed or invalid
   */
  private static Path resolve(String applicationName, String pathName) {
    if (pathName == null || pathName.isBlank()) {
      return null;
    }
    try {
      Path file = Path.of(pathName);
      if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
        return null;    // already read or removed
      }
      Path parent = file.getParent();
      if (file.isAbsolute() && parent != null && Files.isSameFile(parent, getDirectory()) &&
          isCredentialsFileName(applicationName, file.getFileName().toString()) &&
          Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
        return file;
      }
    }
    catch (IOException | RuntimeException ex) {
      // invalid path: logged below
    }
    LOGGER.warning("ignoring invalid credentials file {0}", pathName);
    return null;
  }

  private static boolean isCredentialsFileName(String applicationName, String fileName) {
    return Pattern.matches(Pattern.quote(getFilePrefix(applicationName)) + "[0-9]+" + Pattern.quote(FILE_SUFFIX), fileName);
  }

  private static boolean isStale(Path file) throws IOException {
    Instant modified = Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toInstant();
    return modified.plus(MAX_AGE).isBefore(Instant.now());
  }

  private static String getFilePrefix(String applicationName) {
    return toAsciiName(applicationName).toLowerCase(Locale.ROOT) + FILE_INFIX;
  }

  private static String toAsciiName(String applicationName) {
    return StringHelper.toAsciiLetterOrDigit(applicationName);
  }

  /**
   * Creates a new temporary file only the current user can access.
   *
   * @param prefix the file name prefix
   * @return the file
   * @throws IOException if the file could not be created or protected
   */
  private static Path createPrivateFile(String prefix) throws IOException {
    Path dir = getDirectory();
    if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
      return Files.createTempFile(dir, prefix, FILE_SUFFIX, PosixFilePermissions.asFileAttribute(
          EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
    }

    Path file = Files.createTempFile(dir, prefix, FILE_SUFFIX);
    try {
      AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
      if (view != null) {   // Windows: replace the inherited entries by one for the owner
        view.setAcl(List.of(AclEntry.newBuilder()
            .setType(AclEntryType.ALLOW)
            .setPrincipal(view.getOwner())
            .setPermissions(EnumSet.of(AclEntryPermission.READ_DATA, AclEntryPermission.WRITE_DATA,
                                       AclEntryPermission.APPEND_DATA, AclEntryPermission.READ_ATTRIBUTES,
                                       AclEntryPermission.WRITE_ATTRIBUTES, AclEntryPermission.READ_NAMED_ATTRS,
                                       AclEntryPermission.WRITE_NAMED_ATTRS, AclEntryPermission.READ_ACL,
                                       AclEntryPermission.WRITE_ACL, AclEntryPermission.DELETE,
                                       AclEntryPermission.SYNCHRONIZE))
            .build()));
      }
      return file;
    }
    catch (IOException | RuntimeException ex) {
      delete(file);
      throw ex;
    }
  }


  private RestartCredentials() {
    // static utility class
  }

}
