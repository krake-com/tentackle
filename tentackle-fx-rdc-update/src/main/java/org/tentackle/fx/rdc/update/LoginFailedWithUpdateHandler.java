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

import org.tentackle.common.Constants;
import org.tentackle.common.Cryptor;
import org.tentackle.common.ExceptionHelper;
import org.tentackle.common.FileHelper;
import org.tentackle.common.StringHelper;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.FxFxBundle;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.NotificationBuilder;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.rdc.app.DesktopApplication;
import org.tentackle.fx.rdc.app.FxApplication;
import org.tentackle.fx.rdc.app.LoginFailedHandler;
import org.tentackle.log.Logger;
import org.tentackle.misc.BlockingHolder;
import org.tentackle.misc.Holder;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.VersionIncompatibleException;
import org.tentackle.update.ClientInfo;
import org.tentackle.update.ClientUpdateUtilities;
import org.tentackle.update.InstallationType;
import org.tentackle.update.UpdateInfo;
import org.tentackle.update.UpdateService;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.stage.Popup;
import javafx.util.Duration;
import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Login failed handler that updates an outdated client.
 * <p>
 * A {@link VersionIncompatibleException} or an {@link SSLHandshakeException} (a new server version may come
 * with a new certificate) is taken as a hint that the client is outdated. If the client runs from an updatable
 * jlink or jpackage image and an {@code updateService} property is configured, the remote {@link UpdateService}
 * is asked for a newer version, which is then downloaded and installed, and the application restarted.
 * If the client is not updatable, the user is told to contact the administrator.
 * All other failures are passed on to the standard {@link LoginFailedHandler}.
 *
 * @author harald
 */
public class LoginFailedWithUpdateHandler extends LoginFailedHandler {

  /**
   * Exit value if the application terminated because it is outdated and cannot update itself, or the update failed.<br>
   * Differs from {@link LoginFailedHandler#EXIT_LOGIN_FAILED} used by the standard handler for other login failures.
   * If the user declines the update, the application terminates without a stop reason.
   */
  public static final int EXIT_VALUE_OUTDATED = 97;

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

  /**
   * {@inheritDoc}
   * <p>
   * Returns the update runnable if a newer version is available.
   * Like the handler itself, the runnable is invoked from within the login service's background thread.
   */
  @Override
  @SuppressWarnings("unchecked")
  public Runnable handle(Exception ex) {
    if (ex instanceof VersionIncompatibleException ||
        // or SSL certificate changed due to a new server version
        ExceptionHelper.extractException(true, ex, SSLHandshakeException.class) != null) {

      InstallationType installationType = ClientUpdateUtilities.getInstance().determineInstallationType();
      if (installationType != null) {
        UpdateService service = createUpdateService();
        if (service != null) {
          try {
            ClientInfo clientInfo = new ClientInfo(getApplication().getName(), getApplication().getVersion(), installationType);
            UpdateInfo updateInfo = service.getUpdateInfo(clientInfo);
            LOGGER.info("application update {0} -> {1}", clientInfo, updateInfo);
            if (!updateInfo.version().equals(clientInfo.version())) {
              return createUpdateRunnable(clientInfo, updateInfo, installationType);
            }
            // else: the version is okay, but TLS handshake didn't work -> proceed with standard impl below
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
        /*
         * The client cannot update itself, and the user is told to call the administrator.
         * However, an SSL handshake error is not necessarily an outdated client: it may as well be
         * an expired or untrusted certificate, an intercepting proxy or a protocol mismatch.
         * Since the standard handler is not reached, log the cause and provide it as the stop reason,
         * or else it would be lost completely.
         */
        LOGGER.severe("client not running within an updatable directory", ex);
        setStopReason(ex);
        terminate(MessageFormat.format(UpdateFxRdcBundle.getString(
            "{0}_{1}_OUTDATED_CALL_ADMIN"), getApplication().getName(), getApplication().getVersion()), null);
        return null;
      }
    }

    return super.handle(ex);
  }


  /**
   * Creates a runnable that will be invoked if a newer version exists.<br>
   * The default implementation asks the user whether to update or not and invokes
   * {@link #update(UpdateInfo, InstallationType)} or {@link #terminate(String, Throwable)}, respectively.
   * The runnable blocks until the user has answered the dialog shown on the FX thread.<br>
   * Applications running in unattended mode (presentation mode, dashboards, etc...)
   * may override this method and update without asking the user.
   *
   * @param clientInfo the client info of the currently running application
   * @param updateInfo the update info of the new application
   * @param installationType the installation type
   * @return the runnable, never null
   */
  protected Runnable createUpdateRunnable(ClientInfo clientInfo, UpdateInfo updateInfo, InstallationType installationType) {
    return () -> {
      BlockingHolder<Boolean> answer = new BlockingHolder<>();    // wait for result!
      Platform.runLater(() -> {
        try {
          showUpdateDialog(
            getView(), MessageFormat.format(UpdateFxRdcBundle.getString("{0}_{1}_OUTDATED_INSTALL_{2}"),
                                            clientInfo.application(), clientInfo.version(), updateInfo.version()),
            answer);
        }
        catch (RuntimeException rx) {
          // the dialog could not be shown (window already gone, for example):
          // answer NO instead of leaving the invoking thread blocked in get() forever
          LOGGER.severe("showing the update dialog failed", rx);
          answer.accept(Boolean.FALSE);
        }
      });
      if (answer.get()) {
        update(updateInfo, installationType);
      }
      else {
        terminate(null, null);
      }
    };
  }

  /**
   * Shows an update dialog to ask the user whether to update or not.<br>
   * Invoked from within the FX thread.
   * Closing the notification without pressing a button (ESC, for example) is taken as NO.
   * <p>
   * Implementations must either invoke the answer consumer or throw, since the invoking thread
   * is blocked until the answer is provided (see {@link #createUpdateRunnable}).
   *
   * @param owner the dialog owner
   * @param message the message
   * @param answer the result consumer
   */
  protected void showUpdateDialog(Object owner, String message, Consumer<Boolean> answer) {
    Holder<Boolean> result = new Holder<>(Boolean.FALSE);   // ESC or hiding w/o pressing a button -> NO
    FxButton goButton = Fx.create(Button.class);
    goButton.setText(FxFxBundle.getString("YES"));
    Timeline countdown = createCountdownTimeline(goButton);
    Popup popup = FxFactory.getInstance().createNotificationPopup();
    Parent notification = FxFactory.getInstance().createNotificationBuilder()
                                   .type(NotificationBuilder.Type.QUESTION)
                                   .text(message)
                                   .button(FxFxBundle.getString("NO"), null, false, null)
                                   .button(goButton, true, () -> result.accept(Boolean.TRUE))
                                   .hide(popup::hide)
                                   .build();
    FxUtilities.getInstance().showNotification(owner, popup, notification, () -> {
      if (countdown != null) {
        countdown.stop();
      }
      answer.accept(result.get());
    });
    if (countdown != null) {
      countdown.play();
    }
  }

  /**
   * Creates a countdown timeline for the go-button to start the update automatically after a few seconds.<br>
   * The default implementation replaces the button text every second by 3, 2, 1, and 0 seconds
   * and clicks the button at 0, i.e., about four seconds after the dialog was shown.<br>
   * The update should not start immediately, since the user must be able to cancel.
   * Furthermore, if the update keeps failing for whatever reason, the user can break the installation loop.
   * Return null to require an explicit confirmation.
   *
   * @param goButton the go-button
   * @return the timeline, null if no countdown (no automatic update start)
   */
  protected Timeline createCountdownTimeline(FxButton goButton) {
    Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), _ -> {
      String text = goButton.getText();
      char c = text.charAt(0);
      int value;
      if (Character.isDigit(c)) {
        value = c - '0' - 1;
      }
      else {
        value = 3;
      }
      goButton.setText(value + " s");
      if (value < 1) {
        goButton.doClick();
      }
    }));
    timeline.setCycleCount(4);
    return timeline;
  }

  /**
   * Creates the remote update service.<br>
   * The service URL is taken from the application property {@code updateService}.
   *
   * @return the service object, null if no service configured
   */
  protected UpdateService createUpdateService() {
    String updateService = getApplication().getPropertyIgnoreCase("updateService");
    return updateService == null ? null : ClientUpdateUtilities.getInstance().getUpdateService(updateService);
  }

  /**
   * Determines the update directory.<br>
   * The default is {@code update} below the package root for jpackage and below the image root for jlink.
   * The directory itself is (re)created empty by {@link ClientUpdateUtilities#downloadZip}.
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
    // else JLink: execution dir is always the jlink image's root
    return new File("update");
  }

  /**
   * Runs the update.<br>
   * Downloads and unzips the new version and launches the update script, which replaces the
   * running image after this application has terminated.
   * The script gets the PID of this process and, for jpackage, the path of the launcher to restart.
   * On success, the application exits via {@link System#exit(int)}.
   * If anything goes wrong, the update is aborted via {@link #terminate(String, Throwable)}.
   *
   * @param updateInfo the update info
   * @param installationType the installation type
   */
  protected void update(UpdateInfo updateInfo, InstallationType installationType) {
    File updateDir = createUpdateDirectory(installationType);
    LOGGER.info("using update directory: {0}", updateDir);

    try {
      // the download is part of the update: a failure must be reported like any other
      File zipFile = ClientUpdateUtilities.getInstance().downloadZip(updateInfo, updateDir,
                     progress -> getApplication().showApplicationStatus(UpdateFxRdcBundle.getString("DOWNLOADING"), progress));

      LOGGER.info("download successful: {0}", zipFile);

      String updatePath = updateDir.getCanonicalPath();
      FileHelper.unzip(zipFile, updateDir, file -> {
        String fileName;    // shortened name
        try {
          fileName = file.getCanonicalPath();
          // the length check covers zip entries such as "./", which resolve to the update directory itself
          if (fileName.length() > updatePath.length() && fileName.startsWith(updatePath)) {
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

      File updateScript = new File(new File(updateDir, "bin"), updateInfo.updateExecutor());
      if (!updateScript.exists()) {
        throw new IOException("no such update script: " + updateScript);
      }
      updateScript.setExecutable(true);
      // windows needs the pid to wait for this client to finish before overriding its files (not necessary for *nixes)
      long pid = ProcessHandle.current().pid();       // 1st arg is the PID of the executing process
      ProcessBuilder builder;
      if (installationType == InstallationType.JPACKAGE) {
        Optional<String> launcher = ProcessHandle.current().info().command();   // 2nd is the path to the launcher to be restarted
        if (launcher.isPresent()) {
          builder = new ProcessBuilder(updateScript.getPath(), Long.toString(pid), launcher.get());
        }
        else {
          throw new TentackleRuntimeException("cannot determine the running launcher executable");
        }
      }
      else {    // JLink
        builder = new ProcessBuilder(updateScript.getPath(), Long.toString(pid));
      }
      passSessionInfoViaEnvironment(builder.environment());
      Process process = builder.start();
      LOGGER.info("executing {0} {1} (pid = {2})", updateScript, pid, process.pid());
    }
    catch (RuntimeException | IOException iox) {
      terminate(UpdateFxRdcBundle.getString("UPDATE_FAILED"), iox);
      return;   // don't tell the user that the update succeeded and don't kill the error dialog by exiting below
    }

    getApplication().showApplicationStatus(UpdateFxRdcBundle.getString("UPDATE_COMPLETE"), 1.0);
    try {
      Thread.sleep(1000);   // time to read...
    }
    catch (InterruptedException ix) {
      // we terminate anyway...
    }

    System.exit(0);   // terminate to let the update script replace the running image
  }

  /**
   * Passes the current username and password encrypted to the process environment of the updated application.<br>
   * Requires a {@link Cryptor}. The username is passed if known, the password in addition if the credentials
   * are password-based. The variables are named {@code <APP>_USER} and {@code <APP>_PASSWORD}, where
   * {@code <APP>} is the application name reduced to ASCII letters and digits, all in uppercase.
   * See {@code LoginApplication#updateSessionInfoFromEnvironment(SessionInfo)}.
   *
   * @param environment the process environment
   */
  protected void passSessionInfoViaEnvironment(Map<String, String> environment) {
    Cryptor cryptor = Cryptor.getInstance();
    if (cryptor != null) {
      SessionInfo sessionInfo = getSessionInfo();
      String userName = sessionInfo.getUserName();
      if (userName != null) {
        String envPrefix = StringHelper.toAsciiLetterOrDigit(getApplication().getName());
        environment.put((envPrefix + "_" + Constants.BACKEND_USER).toUpperCase(Locale.ROOT), cryptor.encrypt64(userName));
        char[] password = sessionInfo.getPassword();
        if (password != null) {
          environment.put((envPrefix + "_" + Constants.BACKEND_PASSWORD).toUpperCase(Locale.ROOT), cryptor.encrypt64(password));
        }
      }
    }
  }

  /**
   * Records why the application is going to terminate.<br>
   * The exit value and the stacktrace are picked up when the last window is closed.
   *
   * @param t the throwable that made the application terminate, null if none
   * @see #EXIT_VALUE_OUTDATED
   */
  protected void setStopReason(Throwable t) {
    FxApplication fxApplication = getApplication().getFxApplication();
    if (t != null && fxApplication != null) {
      fxApplication.setStopReason(EXIT_VALUE_OUTDATED, t);
    }
  }

  /**
   * Terminates the application.<br>
   * Shows the message, if any, as an error (with throwable) or info dialog and closes the login window
   * when the dialog is closed. The application terminates when its last window is closed.
   *
   * @param msg the message shown to the user, null to close the window without a message
   * @param t the throwable recorded as the stop reason, null if none
   * @see #setStopReason(Throwable)
   */
  protected void terminate(String msg, Throwable t) {
    setStopReason(t);   // before showing the dialog: the reason must survive a failing dialog
    Platform.runLater(() -> {
      if (msg != null) {
        if (t != null) {
          LOGGER.severe(msg, t);
          Fx.error(getView(), msg, t, this::hide);
        }
        else {
          LOGGER.info(msg);
          Fx.info(getView(), msg, this::hide);
        }
      }
      else {
        hide();
      }
    });
  }

  private void hide() {
    // application terminates when the last stage closes
    getView().getScene().getWindow().hide();
  }

}
