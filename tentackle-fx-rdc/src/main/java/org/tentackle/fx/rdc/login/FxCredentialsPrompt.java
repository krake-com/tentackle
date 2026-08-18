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

package org.tentackle.fx.rdc.login;

import org.tentackle.fx.FxFactory;
import org.tentackle.fx.FxFxBundle;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.NotificationBuilder;
import org.tentackle.fx.rdc.RdcRuntimeException;
import org.tentackle.log.Logger;
import org.tentackle.misc.Holder;
import org.tentackle.session.auth.CredentialsPrompt;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.PasswordField;
import javafx.stage.Popup;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * The credentials prompt of the desktop client.
 * <p>
 * Drives the login view on behalf of an interactive
 * {@link org.tentackle.session.auth.CredentialsProvider} — which runs on a background thread —
 * and opens the system browser for a single sign-on redirect.
 *
 * @author harald
 */
public class FxCredentialsPrompt implements CredentialsPrompt {

  private static final Logger LOGGER = Logger.get(FxCredentialsPrompt.class);

  private final LoginController controller;   // the login view to drive
  private volatile boolean canceled;          // true if the user canceled the login


  /**
   * Creates a prompt for a login view.
   *
   * @param controller the login controller
   */
  public FxCredentialsPrompt(LoginController controller) {
    this.controller = controller;
  }


  /**
   * Cancels the ongoing interaction.<br>
   * Invoked when the user closes the login view.
   */
  public void cancel() {
    canceled = true;
  }

  @Override
  public boolean isCanceled() {
    return canceled;
  }

  @Override
  public void browse(URI uri) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
      try {
        Desktop.getDesktop().browse(uri);
        return;
      }
      catch (IOException | RuntimeException ex) {
        LOGGER.warning("could not open the browser", ex);
      }
    }
    // no browser: at least tell the user where to go
    LOGGER.info("please open the following URL to authenticate: {0}", uri);
    showMessage(uri.toString());
  }

  @Override
  public void showMessage(String message) {
    Platform.runLater(() -> controller.setMessage(message));
  }

  @Override
  public void setProgress(double progress) {
    Platform.runLater(() -> controller.setProgress(progress));
  }

  @Override
  public char[] promptSecret(String label) {
    if (Platform.isFxApplicationThread()) {
      // We would post the dialog to the FX thread and then block that very thread waiting for it:
      // the dialog would never be shown and the application would hang forever.
      // Fail loudly instead, as FxUtilities.runAndWait does.
      throw new RdcRuntimeException("calling thread is the FX application thread");
    }
    // we are on a background thread: let the FX thread do the dialog and wait for the answer
    CompletableFuture<char[]> answer = new CompletableFuture<>();
    Platform.runLater(() -> {
      try {
        showSecretDialog(label, answer::complete);
      }
      catch (RuntimeException rx) {
        answer.completeExceptionally(rx);
      }
    });
    try {
      return answer.get();
    }
    catch (InterruptedException ix) {
      Thread.currentThread().interrupt();
      return null;
    }
    catch (ExecutionException ex) {
      LOGGER.severe("could not prompt for " + label, ex.getCause());
      return null;
    }
  }


  /**
   * Shows the dialog asking for a confidential input.<br>
   * Runs within the FX application thread and returns immediately: since the notification popup
   * must not block the event queue (see {@link javafx.scene.control.Dialog#showAndWait()}),
   * the input is provided via a {@link Consumer} as soon as the popup is closed.
   *
   * @param label describes what is asked for
   * @param secret receives the input, null if the user refused
   */
  protected void showSecretDialog(String label, Consumer<char[]> secret) {
    Holder<char[]> result = new Holder<>();    // ESC or hiding w/o pressing OK -> refused
    PasswordField passwordField = new PasswordField();
    Popup popup = FxFactory.getInstance().createNotificationPopup();
    Parent notification = FxFactory.getInstance().createNotificationBuilder()
                                   .type(NotificationBuilder.Type.QUESTION)
                                   .text(label)
                                   .content(passwordField)
                                   .button(FxFxBundle.getString("CANCEL"), null, false, null)
                                   .button(FxFxBundle.getString("OK"), null, true,
                                           () -> result.accept(passwordField.getText().toCharArray()))
                                   .hide(popup::hide)
                                   .build();
    // trigger the secret consumer when the popup is closed
    FxUtilities.getInstance().showNotification(controller.getView(), popup, notification,
                                               () -> secret.accept(result.get()));
    Platform.runLater(passwordField::requestFocus);
  }

}
