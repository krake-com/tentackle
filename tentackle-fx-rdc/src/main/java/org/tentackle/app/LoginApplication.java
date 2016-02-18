/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.app;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javax.jnlp.BasicService;
import javax.jnlp.ServiceManager;
import javax.jnlp.UnavailableServiceException;
import org.tentackle.common.Constants;
import org.tentackle.fx.Fx;
import org.tentackle.fx.rdc.login.Login;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.ApplicationException;
import org.tentackle.misc.StringHelper;
import org.tentackle.pdo.SessionInfo;
import org.tentackle.reflect.ReflectionHelper;

/**
 * The doLogin FX application.
 * <p>
 * Displays the doLogin view and spawns a doLogin handler.
 *
 * @author harald
 */
public class LoginApplication extends FxApplication {

  /**
   * the logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(LoginApplication.class);



  private Login controller;       // the doLogin controller


  @Override
  public void startApplication(Stage stage) throws Exception {
    controller = Fx.load(Login.class);
    controller.setSessionInfo(createSessionInfo());
    Scene scene = new Scene(controller.getView());
    stage.setScene(scene);
    stage.initStyle(StageStyle.TRANSPARENT);
    stage.show();
  }


  @Override
  public void showApplicationStatus(String msg, double progress) {
    if (controller.getView().isVisible()) {
      controller.setMessage(msg);
      controller.setProgress(progress);
    }
    else {
      LOGGER.info(msg);
    }
  }


  /**
   * Creates the session info to be passed to the doLogin controller.
   *
   * @return the session info
   * @throws ApplicationException if session info could not be created
   */
  public SessionInfo createSessionInfo() throws ApplicationException {
    DesktopApplication<?> application = (DesktopApplication) AbstractApplication.getRunningApplication();
    String username = application.getCommandLine().getOptionValue(Constants.BACKEND_USER);
    char[] password = StringHelper.toCharArray(application.getCommandLine().getOptionValue(Constants.BACKEND_PASSWORD));
    String sessionPropsName = application.getCommandLine().getOptionValue(Constants.BACKEND_PROPS);

    SessionInfo sessionInfo = application.createSessionInfo(username, password, sessionPropsName);

    // provide user and password from backend properties, if given
    Properties props = sessionInfo.getProperties();
    if (username == null) {   // if not overridden by command line
      sessionInfo.setUserName(props.getProperty(Constants.BACKEND_USER, sessionInfo.getUserName()));
    }
    if (password == null) {   // if not overridden by command line
      sessionInfo.setPassword(StringHelper.toCharArray(
              props.getProperty(Constants.BACKEND_PASSWORD, sessionInfo.getPasswordAsString())));
    }

    if (application.isDeployedByJNLP() && sessionPropsName != null) {
      // load the db properties relative to the JNLP codebase
      InputStream is = null;
      try {
        URL dbURL;
        if (sessionPropsName.indexOf("://") >= 0) {
          dbURL = new URL(sessionPropsName);
        }
        else  {
          URL codeBase = ((BasicService) ServiceManager.lookup(BasicService.class.getName())).getCodeBase();
          dbURL = new URL(codeBase + "/" + sessionPropsName);
        }
        Properties sessionProps = new Properties();
        is = dbURL.openStream();
        sessionProps.load(is);
        sessionInfo.setProperties(sessionProps);
      }
      catch (MalformedURLException ex) {
        throw new ApplicationException("malformed URL for db properties", ex);
      }
      catch (IOException ex) {
        throw new ApplicationException(MessageFormat.format("loading db properties {0} failed", sessionPropsName), ex);
      }
      catch (RuntimeException | UnavailableServiceException ex) {
        throw new ApplicationException("cannot determine JNLP codebase", ex);
      }
      finally {
        if (is != null) {
          try {
            is.close();
          }
          catch (IOException ex) {}
        }
      }
    }

    if (sessionInfo.getApplicationName() == null) {
      sessionInfo.setApplicationName(ReflectionHelper.getClassBaseName(application.getClass()));
    }

    // sets the locale to be used for this user
    sessionInfo.setLocale(Locale.getDefault());

    return sessionInfo;
  }

}
