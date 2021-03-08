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

import javafx.scene.Parent;

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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Desktop application with auto update facility.
 *
 * @param <C> the main controller type
 */
public abstract class UpdatableDesktopApplication<C extends FxController> extends DesktopApplication<C> {

  private static final Logger LOGGER = Logger.get(UpdatableDesktopApplication.class);

  private FileChannel channel;    // reference must be kept as long as the application is running!


  /**
   * Creates an FX desktop application.
   *
   * @param name the application name
   * @param version the application version
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
   * Checks whether another updatable application is already running in the current working directory.<br>
   * Throws {@link TentackleRuntimeException} if so.
   */
  protected void assertNotAlreadyRunning() {
    InstallationType installationType = ClientUpdateUtilities.getInstance().determineInstallationType();
    if (installationType != null) {   // updatable jlink- or jpackage installation
      LOGGER.info("application " + getName() + " started from updatable " + installationType + " installation");
      Path lockPath = Paths.get(getLockFileName());
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

        if (created) {
          if (channel.tryLock() == null) {
            throw new TentackleRuntimeException("creating exclusive lock on " + lockPath + " failed");
          }
        }
        else {
          if (channel.tryLock() == null) {
            channel.close();
            channel = FileChannel.open(lockPath, StandardOpenOption.READ);
            ByteBuffer buff = ByteBuffer.allocate(64);
            int count = channel.read(buff);
            buff.limit(count);
            String pid = new String(buff.array(), Settings.getEncodingCharset());
            throw new TentackleRuntimeException("another " + getName() + " with PID " + pid + " is still running");
          }
        }

        // we are the only instance: write the PID to the lockfile
        channel.write(ByteBuffer.wrap(Long.toString(ProcessHandle.current().pid()).getBytes(Settings.getEncodingCharset())));
        // keep it open and referenced!
      }
      catch (IOException iox) {
        throw new TentackleRuntimeException("could not access lockfile " + lockPath, iox);
      }
    }
  }

  /**
   * Gets the name of the lockfile.
   *
   * @return the lockfile name
   */
  protected String getLockFileName() {
    return StringHelper.toAsciiLetterOrDigit(getName()).toLowerCase() + ".lck";
  }

}
