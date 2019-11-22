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
import org.tentackle.fx.Fx;
import org.tentackle.fx.rdc.app.DesktopApplication;
import org.tentackle.fx.rdc.app.LoginFailedHandler;
import org.tentackle.log.Logger;
import org.tentackle.misc.BlockingHolder;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.VersionIncompatibleException;
import org.tentackle.update.ClientInfo;
import org.tentackle.update.ClientUpdateUtilities;
import org.tentackle.update.UpdateInfo;
import org.tentackle.update.UpdateService;

import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

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

      if (ClientUpdateUtilities.getInstance().isApplicationUpdatable()) {
        UpdateService service = createUpdateService();
        if (service != null) {
          try {
            ClientInfo clientInfo = new ClientInfo(getApplication().getName(), getApplication().getVersion());
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
                  update(updateInfo);
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
        LOGGER.info("client not running within an updatable directory");
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
   * @return the directory to download and unzip the files for update
   */
  protected File createUpdateDirectory() {
    return new File("update");
  }

  /**
   * Runs the update.
   *
   * @param updateInfo the update info
   */
  protected void update(UpdateInfo updateInfo) {
    File updateDir = createUpdateDirectory();
    LOGGER.info("using update directory {0}", updateDir);

    File zipFile = ClientUpdateUtilities.getInstance().dowloadZip(updateInfo, updateDir,
                   progress -> getApplication().showApplicationStatus(UpdateFxRdcBundle.getString("DOWNLOADING"), progress));

    LOGGER.info("download successful: {0}", zipFile);

    try {
      FileHelper.unzip(zipFile, updateDir, file -> {
        getApplication().showApplicationStatus(MessageFormat.format(UpdateFxRdcBundle.getString("INSTALLING_{0}"), file), 1.0, 20);
        LOGGER.info("{0} unzipped", file);
      });
      zipFile.delete();

      File updateScript = new File(new File(updateDir, "bin"), updateInfo.getUpdateExecutor());
      if (!updateScript.exists()) {
        throw new IOException("no such update script: " + updateScript);
      }
      updateScript.setExecutable(true);
      // windows needs the pid to wait for this client to finish before overriding its files (not necessary for *nixes)
      long pid = ProcessHandle.current().pid();
      ProcessBuilder builder = new ProcessBuilder(updateScript.getPath(), Long.toString(pid));
      Process process = builder.start();
      LOGGER.info("executing {0} {1} (pid = {2})", updateScript, pid, process.pid());
    }
    catch (IOException iox) {
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
