/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.fx.rdc.update;

import javafx.application.Platform;
import javafx.scene.Parent;

import org.tentackle.common.ExceptionHelper;
import org.tentackle.common.FileHelper;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.fx.Fx;
import org.tentackle.fx.rdc.app.DesktopApplication;
import org.tentackle.fx.rdc.app.LoginFailedHandler;
import org.tentackle.log.Logger;
import org.tentackle.misc.BlockingHolder;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.VersionIncompatibleException;
import org.tentackle.update.ClientInfo;
import org.tentackle.update.ClientUpdateUtilities;
import org.tentackle.update.InstallationType;
import org.tentackle.update.UpdateInfo;
import org.tentackle.update.UpdateService;

import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Optional;

/**
 * To handle login failed exceptions.
 *
 * @author harald
 */
public class LoginFailedWithUpdateHandler extends LoginFailedHandler {

  private static final Logger LOGGER = Logger.get(LoginFailedWithUpdateHandler.class);

  /**
   * Creates a login failed handler.
   *
   * @param application the application
   * @param view the view
   * @param sessionInfo the session info
   */
  public LoginFailedWithUpdateHandler(DesktopApplication<?> application, Parent view, SessionInfo sessionInfo) {
    super(application, view, sessionInfo);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Runnable handle(Exception ex) {
    if (ex instanceof VersionIncompatibleException ||
        // or SSL certificate changed due to new server version
        ExceptionHelper.extractException(true, ex, SSLHandshakeException.class) != null) {

      InstallationType installationType = ClientUpdateUtilities.getInstance().determineInstallationType();
      if (installationType != null) {
        UpdateService service = createUpdateService();
        if (service != null) {
          try {
            ClientInfo clientInfo = new ClientInfo(getApplication().getName(), getApplication().getVersion(), installationType);
            UpdateInfo updateInfo = service.getInfo(clientInfo);
            LOGGER.info("application update {0} -> {1}", clientInfo, updateInfo);
            if (!updateInfo.getVersion().equals(clientInfo.getVersion())) {
              return () -> {
                BlockingHolder<Boolean> holder = new BlockingHolder<>();
                Platform.runLater(() -> Fx.question(MessageFormat.format(UpdateFxRdcBundle.getString("{0}_{1}_OUTDATED_INSTALL_{2}"),
                                                                         clientInfo.getApplication(), clientInfo.getVersion(), updateInfo.getVersion()),
                                                    false,
                                                    holder::accept));
                Boolean answer = holder.get();
                if (answer) {
                  update(updateInfo, installationType);
                }
                else {
                  terminate(null, null);
                }
              };
            }
            // else: version is okay, but TLS handshake didn't work
          }
          catch (Exception rx) {
            LOGGER.warning("getting update info failed", rx);
          }
        }
        else {
          LOGGER.info("no update service configured");
        }
      }
      else {
        LOGGER.info("client not running within an updatable directory", ex);
        terminate(MessageFormat.format(UpdateFxRdcBundle.getString(
            "{0}_{1}_OUTDATED_CALL_ADMIN"), getApplication().getName(), getApplication().getVersion()), null);
        return null;
      }
    }

    return super.handle(ex);
  }

  /**
   * Creates the remote update service.
   *
   * @return the service object, null if no service configured
   */
  protected UpdateService createUpdateService() {
    String serviceUrl = getApplication().getProperty("updateService");
    return serviceUrl == null ? null : ClientUpdateUtilities.getInstance().getUpdateService(serviceUrl, null);
  }

  /**
   * Creates the update directory.
   *
   * @param installationType the installation type
   * @return the directory to download and unzip the files for update
   */
  protected File createUpdateDirectory(InstallationType installationType) {
    if (installationType == InstallationType.JPACKAGE) {
      // install in package directory
      File rootDir = ClientUpdateUtilities.getInstance().determineJPackageRoot();   // cannot be null since installationType is already known
      return new File(rootDir, "update");
    }
    // else JLink: execution dir is always the jlink images' root
    return new File("update");
  }

  /**
   * Runs the update.
   *
   * @param updateInfo the update info
   * @param installationType the installation type
   */
  protected void update(UpdateInfo updateInfo, InstallationType installationType) {
    File updateDir = createUpdateDirectory(installationType);
    LOGGER.info("using update directory {0}", updateDir);

    File zipFile = ClientUpdateUtilities.getInstance().dowloadZip(updateInfo, updateDir,
                   progress -> getApplication().showApplicationStatus(UpdateFxRdcBundle.getString("DOWNLOADING"), progress));

    LOGGER.info("download successful: {0}", zipFile);

    try {
      String updatePath = updateDir.getCanonicalPath();
      FileHelper.unzip(zipFile, updateDir, file -> {
        String fileName;    // shortened name
        try {
          fileName = file.getCanonicalPath();
          if (fileName.startsWith(updatePath)) {
            fileName = fileName.substring(updatePath.length() + 1);   // +1 due to File.separator
          }
        }
        catch (IOException iox) {
          fileName = file.getPath();    // better than nothing...
        }
        getApplication().showApplicationStatus(MessageFormat.format(UpdateFxRdcBundle.getString("INSTALLING_{0}"), fileName), 1.0, 20);
        LOGGER.info("{0} unzipped", file);
      });
      zipFile.delete();

      File updateScript = new File(new File(updateDir, "bin"), updateInfo.getUpdateExecutor());
      if (!updateScript.exists()) {
        throw new IOException("no such update script: " + updateScript);
      }
      updateScript.setExecutable(true);
      // windows needs the pid to wait for this client to finish before overriding its files (not necessary for *nixes)
      long pid = ProcessHandle.current().pid();       // 1st arg is the PID of the executing process
      ProcessBuilder builder;
      if (installationType == InstallationType.JPACKAGE) {
        Optional<String> javaCommand = ProcessHandle.current().info().command();   // 2nd is the path to the executable to be restarted
        if (javaCommand.isPresent()) {
          builder = new ProcessBuilder(updateScript.getPath(), Long.toString(pid), javaCommand.get());
        }
        else {
          throw new TentackleRuntimeException("cannot determine running java command");
        }
      }
      else {    // JLink
        builder = new ProcessBuilder(updateScript.getPath(), Long.toString(pid));
      }
      Process process = builder.start();
      LOGGER.info("executing {0} {1} (pid = {2})", updateScript, pid, process.pid());
    }
    catch (RuntimeException | IOException iox) {
      terminate(UpdateFxRdcBundle.getString("UPDATE_FAILED"), iox);
    }

    getApplication().showApplicationStatus(UpdateFxRdcBundle.getString("UPDATE_COMPLETE"), 1.0);
    try {
      Thread.sleep(1000);   // time to read...
    }
    catch (InterruptedException ix) {
      // we terminate anyway...
    }

    System.exit(0);   // terminate in any case...
  }


  private void terminate(String msg, Throwable t) {
    Platform.runLater(() -> {
      if (msg != null) {
        if (t != null) {
          LOGGER.severe(msg, t);
          Fx.error(msg, t);
        }
        else {
          LOGGER.info(msg);
          Fx.info(msg);
        }
      }
      // application terminates when the last stage closes
      getView().getScene().getWindow().hide();
    });
  }

}
