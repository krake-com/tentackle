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

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import org.tentackle.app.AbstractApplication;
import org.tentackle.common.Constants;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxComboBox;
import org.tentackle.fx.component.FxLabel;
import org.tentackle.fx.component.FxPasswordField;
import org.tentackle.fx.component.FxTextField;
import org.tentackle.fx.rdc.app.DesktopApplication;
import org.tentackle.fx.rdc.app.LoginApplication;
import org.tentackle.log.Logger;
import org.tentackle.session.BackendConfiguration;
import org.tentackle.session.SessionInfo;

import java.util.ResourceBundle;

/**
 * Login controller.
 *
 * @author harald
 */
@FxControllerService(binding = FxControllerService.BINDING.NO)
public class Login extends AbstractFxController {

  private static final Logger LOGGER = Logger.get(Login.class);

  private SessionInfo sessionInfo;            // the session info
  private boolean autoLogin;                  // true if login immediately
  private boolean backendFromSystemPrefs;     // if select backend from system preferences

  @FXML
  private ResourceBundle resources;           // localization bundle

  @FXML
  private FxLabel backendLabel;
  @FXML
  private FxComboBox<BackendConfiguration> backendField;
  @FXML
  private FxButton backendButton;

  @FXML
  private FxTextField usernameField;

  @FXML
  private FxPasswordField passwordField;

  @FXML
  private Label messageLabel;

  @FXML
  private ProgressBar progressBar;

  @FXML
  private FxButton loginButton;

  @FXML
  private FxButton cancelButton;


  @FXML
  private void initialize() {
    progressBar.setVisible(false);
    setCapsLockMessage(isCapsLock());
    cancelButton.setOnAction(e -> cancel());
    cancelButton.setGraphic(Fx.createImageView("close"));
    loginButton.setOnAction(e -> login());
    loginButton.setGraphic(Fx.createImageView("ok"));
    usernameField.setAutoSelect(true);
    passwordField.setOnAction(e -> login());
    passwordField.setAutoSelect(true);
    backendButton.setGraphic(Fx.createImageView("edit"));
    backendButton.setOnAction(e -> {
      Backends.editBackends(sessionInfo.getApplicationName(), backendFromSystemPrefs);
      loadBackendConfigurations();
    });

    backendField.setOnAction(event -> {
      BackendConfiguration backendConfiguration = backendField.getValue();
      if (backendConfiguration != null && backendConfiguration.getUrl() != null &&
          backendConfiguration.getUrl().startsWith(Constants.BACKEND_RMI_URL_INTRO) &&
          backendConfiguration.getUser() != null) {
        // copy the user and password from the backend config
        // (for jdbc/jndi the user/pw is used to connect to the database only)
        usernameField.setText(backendConfiguration.getUser());
        passwordField.setText(backendConfiguration.getPassword());
      }
    });
  }

  @Override
  public void configure() {
    getView().addEventFilter(KeyEvent.KEY_RELEASED, (KeyEvent event) -> {
      if (event.getCode() == KeyCode.ESCAPE) {
        cancel();
      }
      if (event.getCode() == KeyCode.CAPS) {
        Platform.runLater(() -> setCapsLockMessage(isCapsLock()));
      }
    });
  }

  /**
   * Sets the initial focus.
   */
  public void requestInitialFocus() {
    Platform.runLater(usernameField::requestFocus);
  }

  /**
   * Sets the session info.
   *
   * @param sessionInfo the session info
   */
  public void setSessionInfo(SessionInfo sessionInfo) {
    this.sessionInfo = sessionInfo;
    usernameField.setText(sessionInfo.getUserName());
    passwordField.setText(sessionInfo.getPasswordAsString());
    String backendInfo = sessionInfo.getProperties().getProperty(Constants.BACKEND_INFO);
    boolean backendVisible = false;
    if (Constants.BACKEND_INFO_USER.equals(backendInfo)) {
      backendVisible = true;
      backendFromSystemPrefs = false;
    }
    else if (Constants.BACKEND_INFO_SYSTEM.equals(backendInfo)) {
      backendVisible = true;
      backendFromSystemPrefs = true;
    }
    backendLabel.setVisible(backendVisible);
    backendLabel.setManaged(backendVisible);
    backendField.setVisible(backendVisible);
    backendField.setManaged(backendVisible);
    backendButton.setVisible(backendVisible);
    backendButton.setManaged(backendVisible);
    if (backendVisible) {
      loadBackendConfigurations();
    }
    else if (backendInfo != null) {
      int ndx = backendInfo.indexOf(':');
      if (ndx >= 0) {
        String configName = backendInfo.substring(ndx + 1);
        boolean systemPrefs = backendInfo.substring(0, ndx).equals(Constants.BACKEND_INFO_SYSTEM);
        BackendConfiguration backendConfiguration = BackendConfiguration.getBackendConfigurations(
            sessionInfo.getApplicationName(), systemPrefs).get(configName);
        if (backendConfiguration != null) {
          if (backendConfiguration.getUrl().startsWith(Constants.BACKEND_RMI_URL_INTRO) &&
              backendConfiguration.getUser() != null) {
            // preset user/pw if fixed backend config is remote and user set
            usernameField.setText(backendConfiguration.getUser());
            passwordField.setText(backendConfiguration.getPassword());
          }
        }
        else {
          LOGGER.warning("no such backend configuration: " + configName);
        }
      }
    }
  }


  /**
   * Logs in automatically.
   *
   * @see LoginApplication#isAutoLogin(org.tentackle.session.SessionInfo)
   */
  public void autoLogin() {
    autoLogin = true;
    Platform.runLater(() -> {
      usernameField.setEditable(false);
      passwordField.setEditable(false);
      login();
    });
  }


  private void login() {
    if (progressBar.getProgress() <= 0.0) {
      sessionInfo.setUserName(usernameField.getText());
      String pwd = passwordField.getText();
      sessionInfo.setPassword(pwd == null ? null : pwd.toCharArray());
      BackendConfiguration backendConfiguration = backendField.getValue();
      if (backendConfiguration != null) {
        String backendInfo = sessionInfo.getProperties().getProperty(Constants.BACKEND_INFO) + ":" + backendConfiguration;
        sessionInfo.getProperties().setProperty(Constants.BACKEND_INFO, backendInfo);
      }
      setProgress(0.01);
      ((DesktopApplication) AbstractApplication.getRunningApplication()).login(getView(), sessionInfo);
    }
    else {
      LOGGER.warning("login already in progress...");
    }
  }

  private void cancel() {
    Fx.getStage(getView()).hide();
  }


  /**
   * Sets the progress.
   *
   * @param value &le; 0 to hide progress bar, else up to 1 for 100%
   */
  public void setProgress(double value) {
    if (value <= 0.0 && !autoLogin) {
      progressBar.setVisible(false);
      progressBar.setProgress(0.0);
      loginButton.setDisable(false);
      cancelButton.setDisable(false);
      usernameField.setChangeable(true);
      passwordField.setChangeable(true);
    }
    else {
      progressBar.setVisible(true);
      progressBar.setProgress(value);
      loginButton.setDisable(true);
      cancelButton.setDisable(true);
      usernameField.setChangeable(false);
      passwordField.setChangeable(false);
    }
  }

  /**
   * Sets the message field.
   *
   * @param message the message
   */
  public void setMessage(String message) {
    messageLabel.setText(message);
  }

  /**
   * Sets the caps-lock message.
   *
   * @param capsLock the caps lock message
   */
  private void setCapsLockMessage(boolean capsLock) {
    if (capsLock) {
      setMessage(resources.getString("CAPS LOCK!"));
    }
    else {
      setMessage(null);
    }
  }

  /**
   * Returns whether CAPS-LOCK is active.
   *
   * @return true if CAPS LOCK on
   */
  private boolean isCapsLock() {
    // @todo: this starts the XAWT-Thread! There is no other way currently in Java FX 8.
    // as soon as FX provides its own implementation, use that!
    return java.awt.Toolkit.getDefaultToolkit().getLockingKeyState(java.awt.event.KeyEvent.VK_CAPS_LOCK);
  }

  private void loadBackendConfigurations() {
    backendField.getItems().clear();
    backendField.getItems().addAll(BackendConfiguration.getBackendConfigurations(sessionInfo.getApplicationName(), backendFromSystemPrefs).values());
  }
}
