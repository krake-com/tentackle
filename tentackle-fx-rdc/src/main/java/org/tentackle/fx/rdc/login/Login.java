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

import org.tentackle.app.Application;
import org.tentackle.common.Constants;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxComboBox;
import org.tentackle.fx.component.FxLabel;
import org.tentackle.fx.component.FxPasswordField;
import org.tentackle.fx.component.FxTextField;
import org.tentackle.fx.component.Note;
import org.tentackle.fx.rdc.app.DesktopApplication;
import org.tentackle.log.Logger;
import org.tentackle.session.BackendConfiguration;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.auth.Credentials;
import org.tentackle.session.auth.CredentialsProvider;

import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.WindowEvent;
import java.util.ResourceBundle;

/**
 * Login controller.<br>
 * Collects the credentials according to the configured authentication method: the username and password,
 * or, for an interactive (single sign-on) method, a sign-in button that runs the {@link CredentialsProvider}
 * in the background. Optionally, the user can select and edit the backend to connect to.
 *
 * @author harald
 */
@FxControllerService(binding = FxControllerService.BINDING.NO)
public class Login extends AbstractFxController implements LoginController {

  private static final Logger LOGGER = Logger.get(Login.class);

  private SessionInfo sessionInfo;            // the session info
  private boolean autoLogin;                  // true if login immediately
  private boolean backendFromSystemPrefs;     // if select backend from system preferences
  private char[] password;                    // password buffer
  private TimeoutThread timeoutThread;        // != null if timeout thread started
  private boolean backendsEditable;           // true if the user can edit the backends configuration
  private int inactivityTimeout;              // inactivity timeout in seconds (0 if turned off)
  private long lastActivity;                  // last key press epochal time
  private CredentialsProvider credentialsProvider;    // the configured authentication method
  private FxCredentialsPrompt credentialsPrompt;      // != null while an interactive login is running

  @FXML
  private ResourceBundle resources;           // localization bundle

  @FXML
  private GridPane loginPane;

  @FXML
  private FxLabel backendLabel;
  @FXML
  private FxComboBox<BackendConfiguration> backendField;
  @FXML
  private FxButton backendButton;

  @FXML
  private FxLabel usernameLabel;
  @FXML
  private FxTextField usernameField;

  @FXML
  private FxLabel passwordLabel;
  @FXML
  private HBox passwordBox;
  @FXML
  private FxPasswordField passwordField;

  @FXML
  private Label messageLabel;

  @FXML
  private ProgressBar progressBar;

  @FXML
  private FxButton loginButton;

  @FXML
  private FxButton ssoButton;

  @FXML
  private FxButton cancelButton;

  @FXML
  private FxButton revealButton;


  /**
   * Creates the login controller.
   */
  public Login() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @FXML
  private void initialize() {
    progressBar.setVisible(false);
    setCapsLockMessage(isCapsLock());
    cancelButton.setOnAction(_ -> cancel());
    cancelButton.setGraphic(Fx.createGraphic("cancel"));
    loginButton.setOnAction(_ -> login());
    loginButton.setGraphic(Fx.createGraphic("login"));
    ssoButton.setOnAction(_ -> login());
    ssoButton.setGraphic(Fx.createGraphic("login"));
    setSingleSignOn(false);   // until the session info tells us the authentication method
    usernameField.setAutoSelect(true);
    passwordField.setOnAction(_ -> login());
    passwordField.setAutoSelect(true);
    // password needs listeners since it wouldn't work in unbound mode with character buffers
    passwordField.addViewToModelListener(() -> password = passwordField.getPassword());   // this clears the password in the component!
    passwordField.addModelToViewListener(() -> passwordField.setPassword(password));
    backendButton.setGraphic(Fx.createGraphic("edit"));
    backendButton.setOnAction(_ -> Backends.editBackends(getView(), sessionInfo.getApplicationName(), backendFromSystemPrefs, this::loadBackendConfigurations));
    backendField.setOnAction(_ -> presetUserAndPassword(backendField.getValue()));
    revealButton.setGraphic(Fx.createGraphic("view"));
    revealButton.setTooltip(new Tooltip(resources.getString("reveal password")));
    revealButton.setOnAction(_ -> {
      // show some feedback (because nothing changed visually, except perhaps the save button)
      Note infoNote = Fx.create(Note.class);
      infoNote.setType(Note.Type.STICKY);
      infoNote.setPosition(Note.Position.BOTTOM);
      infoNote.setText(passwordField.getText() == null ? resources.getString("empty password") : passwordField.getText());
      infoNote.setFadeEffect(500, 1000, 1000);
      infoNote.show(passwordField);
    });
  }


  /**
   * Presets the username and password fields from the backend configuration.<br>
   * Applies only to remote backends, since the user and password of a JDBC or JNDI backend
   * are used to connect to the database.
   *
   * @param backendConfiguration the backend config, null if none
   */
  public void presetUserAndPassword(BackendConfiguration backendConfiguration) {
    if (isSingleSignOn()) {
      return;   // there are no user- and password fields to preset
    }
    if (backendConfiguration != null && backendConfiguration.getUrl() != null &&
        !backendConfiguration.getUrl().startsWith(Constants.BACKEND_JDBC_URL_INTRO) &&
        !backendConfiguration.getUrl().startsWith(Constants.BACKEND_JNDI_URL_INTRO) &&
        backendConfiguration.getUser() != null) {
      // copy the user and password from the backend config
      // (for jdbc/jndi the user/pw is used to connect to the database only)
      setUsername(backendConfiguration.getUser());
      if (backendConfiguration.getPassword() != null) {
        setPassword(backendConfiguration.getPassword().toCharArray());
      }
      else {
        setPassword(null);
      }
    }
  }

  /**
   * Sets the username in the username field.
   *
   * @param username the username
   */
  public void setUsername(String username) {
    usernameField.setText(username);
  }

  /**
   * Sets the password in the password field.
   *
   * @param password the password
   */
  public void setPassword(char[] password) {
    this.password = password;
    passwordField.setPassword(password);
  }


  /**
   * Gets the inactivity timeout.
   *
   * @return the timeout in seconds, 0 if disabled (default)
   */
  public int getInactivityTimeout() {
    return inactivityTimeout;
  }

  /**
   * Sets the inactivity timeout.<br>
   * If there is no keyboard input, the application will terminate.
   * This feature is only available for simple login dialogs with the backend configuration disabled.
   *
   * @param inactivityTimeout the timeout in seconds, 0 to disable
   */
  public void setInactivityTimeout(int inactivityTimeout) {
    this.inactivityTimeout = inactivityTimeout;
  }


  @Override
  public void configure() {
    getView().addEventFilter(KeyEvent.KEY_RELEASED, event -> {
      lastActivity = System.currentTimeMillis();
      if (event.getCode() == KeyCode.ESCAPE) {
        cancel();
      }
      if (event.getCode() == KeyCode.CAPS) {
        Platform.runLater(() -> setCapsLockMessage(isCapsLock()));
      }
    });

    getView().sceneProperty().addListener(
        (_, _, newValue) -> {
          if (newValue != null) {
            newValue.windowProperty().addListener(
                (_, _, newValue1) -> {
                  if (newValue1 != null) {
                    newValue1.addEventHandler(WindowEvent.WINDOW_SHOWN, _ -> {
                      if (timeoutThread == null) {
                        timeoutThread = new TimeoutThread();
                        timeoutThread.start();
                      }
                    });
                    newValue1.addEventHandler(WindowEvent.WINDOW_HIDDEN, _ -> {
                      if (timeoutThread != null) {
                        timeoutThread.interrupt();
                      }
                    });
                  }
                });
          }
        });
  }

  /**
   * Returns whether the configured authentication method logs in interactively,
   * i.e., without the username and password fields.
   *
   * @return true if single sign-on
   */
  public boolean isSingleSignOn() {
    return credentialsProvider != null && credentialsProvider.isInteractive();
  }

  /**
   * Switches the view between the username/password fields and the single sign-on button.
   *
   * @param singleSignOn true for single sign-on
   */
  protected void setSingleSignOn(boolean singleSignOn) {
    setVisibleAndManaged(usernameLabel, !singleSignOn);
    setVisibleAndManaged(usernameField, !singleSignOn);
    setVisibleAndManaged(passwordLabel, !singleSignOn);
    setVisibleAndManaged(passwordBox, !singleSignOn);
    setVisibleAndManaged(loginButton, !singleSignOn);
    setVisibleAndManaged(ssoButton, singleSignOn);
  }

  /**
   * Shows or hides a node without leaving a gap in the layout.
   *
   * @param node the node
   * @param visible true to show
   */
  private void setVisibleAndManaged(Node node, boolean visible) {
    node.setVisible(visible);
    node.setManaged(visible);
  }

  /**
   * Sets the initial focus.
   */
  @Override
  public void requestInitialFocus() {
    Platform.runLater(isSingleSignOn() ? ssoButton::requestFocus : usernameField::requestFocus);
  }

  /**
   * Sets the session info.
   *
   * @param sessionInfo the session info
   */
  @Override
  public void setSessionInfo(SessionInfo sessionInfo) {
    this.sessionInfo = sessionInfo;
    credentialsProvider = CredentialsProvider.getInstance(sessionInfo);
    setSingleSignOn(credentialsProvider.isInteractive());
    if (!isSingleSignOn()) {
      setUsername(sessionInfo.getUserName());
      setPassword(sessionInfo.getPassword());
    }
    String backendInfo = sessionInfo.getProperties().getPropertyIgnoreCase(Constants.BACKEND_INFO);
    backendsEditable = false;
    if (Constants.BACKEND_INFO_USER.equals(backendInfo)) {
      backendsEditable = true;
      backendFromSystemPrefs = false;
    }
    else if (Constants.BACKEND_INFO_SYSTEM.equals(backendInfo)) {
      backendsEditable = true;
      backendFromSystemPrefs = true;
    }
    backendLabel.setVisible(backendsEditable);
    backendLabel.setManaged(backendsEditable);
    backendField.setVisible(backendsEditable);
    backendField.setManaged(backendsEditable);
    backendButton.setVisible(backendsEditable);
    backendButton.setManaged(backendsEditable);
    if (backendsEditable) {
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
          if (backendConfiguration.getUser() != null &&
              !backendConfiguration.getUrl().startsWith(Constants.BACKEND_JNDI_URL_INTRO) &&
              !backendConfiguration.getUrl().startsWith(Constants.BACKEND_JDBC_URL_INTRO)) {
            // preset user/pw if fixed backend config is remote and user set
            usernameField.setText(backendConfiguration.getUser());
            if (backendConfiguration.getPassword() != null) {
              passwordField.setPassword(backendConfiguration.getPassword().toCharArray());
            }
            else {
              passwordField.setPassword(null);
            }
          }
        }
        else {
          LOGGER.warning("no such backend configuration: " + configName);
        }
      }
    }
  }

  /**
   * Gets the session info.
   *
   * @return the session info
   */
  public SessionInfo getSessionInfo() {
    return sessionInfo;
  }

  /**
   * Logs in automatically.
   */
  @Override
  public void autoLogin() {
    autoLogin = true;
    Platform.runLater(() -> {
      usernameField.setEditable(false);
      passwordField.setEditable(false);
      login();
    });
  }

  /**
   * Sets the progress.
   *
   * @param progress &le; 0 login not begun yet, &ge; 1 for completed
   */
  @Override
  public void setProgress(double progress) {
    if (progress <= 0.0 && !autoLogin) {
      progressBar.setProgress(0.0);
      loginButton.setDisable(false);
      ssoButton.setDisable(false);
      cancelButton.setDisable(false);
      usernameField.setChangeable(true);
      passwordField.setChangeable(true);
      revealButton.setDisable(false);
    }
    else {
      progressBar.setProgress(progress);
      loginButton.setDisable(true);
      ssoButton.setDisable(true);
      cancelButton.setDisable(!isSingleSignOn());   // sso: allow to abort the browser round-trip
      usernameField.setChangeable(false);
      passwordField.setChangeable(false);
      revealButton.setDisable(true);
    }
    loginPane.setVisible(determineLoginPaneVisibility(progress));
    progressBar.setVisible(determineProgressBarVisibility(progress));
  }

  /**
   * Determines whether the login pane should be visible, according to the progress.
   *
   * @param progress &le; 0 login not begun yet, &ge; 1 for completed
   * @return true if the login pane should be visible
   */
  protected boolean determineLoginPaneVisibility(double progress) {
    return progress < 1.0;
  }

  /**
   * Determines whether the progress bar should be visible, according to the progress.
   *
   * @param progress &le; 0 login not begun yet, &ge; 1 for completed
   * @return true if the progress bar should be visible
   */
  protected boolean determineProgressBarVisibility(double progress) {
    return progress > 0.0 && progress < 1.0;
  }

  /**
   * Sets the message field.
   *
   * @param message the message
   */
  @Override
  public void setMessage(String message) {
    messageLabel.setText(message);
  }


  /**
   * Returns whether login is in progress.
   *
   * @return true if currently logging in
   */
  private boolean isLoginInProgress() {
    return progressBar.getProgress() > 0.0;
  }

  /**
   * Performs the login.
   */
  @Override
  public void login() {
    if (progressBar.getProgress() <= 0.0) {
      BackendConfiguration backendConfiguration = backendField.getValue();
      if (backendConfiguration != null) {
        String backendInfo = sessionInfo.getProperties().getPropertyIgnoreCase(Constants.BACKEND_INFO) + ":" + backendConfiguration;
        sessionInfo.getProperties().setProperty(Constants.BACKEND_INFO, backendInfo);
      }
      setProgress(0.01);    // login is in progress...
      if (isSingleSignOn()) {
        obtainCredentials();
      }
      else {
        sessionInfo.setUserName(usernameField.getText());
        sessionInfo.setPassword(password);
        ((DesktopApplication<?>) Application.getInstance()).login(getView(), sessionInfo);
      }
    }
    else {
      LOGGER.warning("login already in progress...");
    }
  }

  /**
   * Obtains the credentials from an interactive credentials provider and logs in.
   * <p>
   * The provider runs on a background thread, since it usually waits for the user to
   * authenticate at the identity provider in a browser.
   */
  private void obtainCredentials() {
    credentialsPrompt = new FxCredentialsPrompt(this);
    setMessage(resources.getString("signing in..."));
    Service<Credentials> service = new Service<>() {
      @Override
      protected Task<Credentials> createTask() {
        return new Task<>() {
          @Override
          protected Credentials call() {
            return credentialsProvider.createCredentials(sessionInfo, credentialsPrompt);
          }
        };
      }
    };
    service.setOnSucceeded(_ -> {
      credentialsPrompt = null;
      Credentials credentials = service.getValue();
      if (credentials == null) {
        // the user canceled or the identity provider refused: let them try again
        setProgress(0.0);
        setMessage(resources.getString("sign in canceled"));
      }
      else {
        sessionInfo.setCredentials(credentials);
        ((DesktopApplication<?>) Application.getInstance()).login(getView(), sessionInfo);
      }
    });
    service.setOnFailed(_ -> {
      credentialsPrompt = null;
      LOGGER.severe("could not obtain the credentials", service.getException());
      setProgress(0.0);
      setMessage(resources.getString("sign in failed"));
    });
    service.start();
  }

  /**
   * Cancels the login and terminates the application.
   */
  private void cancel() {
    if (credentialsPrompt != null) {
      credentialsPrompt.cancel();   // stop waiting for the identity provider
    }
    Fx.getStage(getView()).hide();
  }

  /**
   * Sets the caps lock message.
   *
   * @param capsLock true if caps lock is on
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
   * Returns whether caps lock is active.
   *
   * @return true if CAPS LOCK on
   */
  private boolean isCapsLock() {
    return Platform.isFxApplicationThread() && Platform.isKeyLocked(KeyCode.CAPS).orElse(false);
  }

  /**
   * Loads the backend configurations.
   */
  private void loadBackendConfigurations() {
    backendField.getItems().setAll(BackendConfiguration.getBackendConfigurations(sessionInfo.getApplicationName(), backendFromSystemPrefs).values());
    BackendConfiguration defaultBackend = BackendConfiguration.getDefault(sessionInfo.getApplicationName(), backendFromSystemPrefs);
    if (defaultBackend != null) {
      backendField.setValue(defaultBackend);
      Platform.runLater(() -> backendField.fireEvent(new ActionEvent()));
    }
  }


  /**
   * Timeout thread.<br>
   * Closes the login dialog if no user activity for a given time.
   */
  private class TimeoutThread extends Thread {

    public TimeoutThread() {
      super("login timeout");
    }

    @Override
    public void run() {
      LOGGER.fine("{0} started", this);
      lastActivity = System.currentTimeMillis();
      while (!interrupted()) {
        try {
          sleep(1000);
          Platform.runLater(() -> {
            long now = System.currentTimeMillis();
            long inactiveSeconds = (now - lastActivity) / 1000;
            if (autoLogin || backendsEditable || isLoginInProgress()) {
              lastActivity = now;
            }
            else {
              if (inactivityTimeout > 0 && inactiveSeconds > inactivityTimeout) {
                LOGGER.info("login timed out after {0} seconds -> closed", inactiveSeconds);
                cancel();
              }
            }
          });
        }
        catch (InterruptedException e) {
          break;
        }
      }
      LOGGER.fine("{0} terminated", this);
    }
  }

}
