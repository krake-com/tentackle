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

package org.tentackle.fx.rdc.update;

import org.tentackle.common.Settings;
import org.tentackle.common.StringHelper;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.fx.FxController;
import org.tentackle.fx.rdc.app.DesktopApplication;
import org.tentackle.fx.rdc.app.LoginFailedHandler;
import org.tentackle.log.Logger;
import org.tentackle.session.SessionInfo;
import org.tentackle.update.ClientUpdateUtilities;
import org.tentackle.update.InstallationType;

import javafx.scene.Parent;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/**
 * Desktop application with auto update facility.
 * <p>
 * Installs the {@link LoginFailedWithUpdateHandler}, which updates the client if the login fails
 * because the client is outdated. Furthermore, when started from an updatable jlink or jpackage installation,
 * only one instance of the application may run within that installation, since the update replaces the image in place.
 * For non-updatable launches (plain classpath or module path), the application behaves like a {@link DesktopApplication}.
 *
 * @param <C> the main controller type
 */
public abstract class UpdatableDesktopApplication<C extends FxController> extends DesktopApplication<C> {

  private static final Logger LOGGER = Logger.get(UpdatableDesktopApplication.class);

  private FileChannel channel;    // reference must be kept as long as the application is running!


  /**
   * Creates an updatable FX desktop application.
   *
   * @param name the application name, null for default name
   * @param version the application version, null for default version
   */
  public UpdatableDesktopApplication(String name, String version) {
    super(name, version);
  }

  @Override
  public LoginFailedHandler createLoginFailedHandler(Parent view, SessionInfo sessionInfo) {
    return new LoginFailedWithUpdateHandler(this, view, sessionInfo);
  }

  @Override
  protected void initialize() {
    assertNotAlreadyRunning();
    super.initialize();
  }


  /**
   * Checks whether another updatable application is already running within the same installation.<br>
   * Throws {@link TentackleRuntimeException} if so.
   * <p>
   * The check is based on an exclusive lock on a lockfile, which receives the PID of the running instance.
   * The lockfile is kept open until the application terminates and is never deleted:
   * only the lock counts, so a lockfile left behind by a crashed instance does no harm.
   * Does nothing if the application does not run from an updatable installation.
   *
   * @see #createLockFilePath(InstallationType)
   */
  protected void assertNotAlreadyRunning() {
    InstallationType installationType = ClientUpdateUtilities.getInstance().determineInstallationType();
    if (installationType != null) {   // updatable jlink- or jpackage installation
      LOGGER.info("application " + getName() + " started from updatable " + installationType + " installation");
      Path lockPath = createLockFilePath(installationType);
      try {
        boolean created;    // true if lockfile is created, false if it already exists
        try {
          Files.createFile(lockPath);
          created = true;
        }
        catch (FileAlreadyExistsException ex) {
          created = false;
        }

        channel = FileChannel.open(lockPath, StandardOpenOption.WRITE);

        if (channel.tryLock() == null) {
          // another instance is holding the lock
          String message = created ? "creating exclusive lock on " + lockPath + " failed"
                                   : "another " + getName() + " with PID " + readPidFromLockFile(lockPath) + " is still running";
          channel.close();
          channel = null;
          throw new TentackleRuntimeException(message);
        }

        // we are the only instance: write the PID to the lockfile.
        // truncate first: a shorter PID must not leave digits of a previous instance behind
        channel.truncate(0);
        channel.write(ByteBuffer.wrap(Long.toString(ProcessHandle.current().pid()).getBytes(Settings.getEncodingCharset())));
        // keep it open and referenced!
      }
      catch (IOException iox) {
        throw new TentackleRuntimeException("could not access lockfile " + lockPath, iox);
      }
    }
  }

  /**
   * Reads the PID of the application instance holding the lock.
   *
   * @param lockPath the path of the lockfile
   * @return the PID, "unknown" if the lockfile does not (yet) contain one
   * @throws IOException if reading the lockfile failed
   */
  protected String readPidFromLockFile(Path lockPath) throws IOException {
    try (FileChannel readChannel = FileChannel.open(lockPath, StandardOpenOption.READ)) {
      ByteBuffer buff = ByteBuffer.allocate(64);
      int count = readChannel.read(buff);
      if (count <= 0) {
        // the other instance creates and locks the lockfile before writing its PID: it may still be empty
        return "unknown";
      }
      return new String(buff.array(), 0, count, Settings.getEncodingCharset()).trim();
    }
  }

  /**
   * Creates the path of the lockfile.<br>
   * The lockfile must live within the installation, not in the current working directory,
   * since jpackage launchers don't necessarily start the application in its installation directory.
   *
   * @param installationType the installation type
   * @return the path of the lockfile
   */
  protected Path createLockFilePath(InstallationType installationType) {
    if (installationType == InstallationType.JPACKAGE) {
      File rootDir = ClientUpdateUtilities.getInstance().determineJPackageRoot();   // cannot be null since installationType is already known
      return new File(rootDir, getLockFileName()).toPath();
    }
    // else JLink: execution dir is always the jlink image's root
    return Paths.get(getLockFileName());
  }

  /**
   * Gets the name of the lockfile.<br>
   * The default is the application name reduced to ASCII letters and digits, in lower case, plus {@code .lck}.
   *
   * @return the lockfile name
   */
  protected String getLockFileName() {
    return StringHelper.toAsciiLetterOrDigit(getName()).toLowerCase(Locale.ROOT) + ".lck";
  }

}
