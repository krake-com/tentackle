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

package org.tentackle.app;

import org.tentackle.common.Constants;
import org.tentackle.common.StringHelper;
import org.tentackle.log.Logger;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;

import java.util.Properties;

/**
 * Abstract class to handle the application's lifecycle for a console application (no gui).
 * Tentackle applications should extend this class and invoke {@link #start}.
 * To shut down gracefully, application should invoke {@link #stop}.
 *
 * @author harald
 */
public abstract class ConsoleApplication extends AbstractClientApplication {

  private static final Logger LOGGER = Logger.get(ConsoleApplication.class);


  /**
   * Creates the console application.
   *
   * @param name the application's name
   * @param version the application version
   */
  public ConsoleApplication(String name, String version) {
    super(name, version);
  }


  @Override
  protected void startup() {
    LOGGER.fine("register application");
    // make sure that only one application is running at a time
    register();

    LOGGER.fine("initialize application");
    // initialize environment
    initialize();

    LOGGER.fine("login to backend");
    // connect to database/application server
    login();

    LOGGER.fine("configure application");
    // configure the application
    configure();

    LOGGER.fine("finish startup");
    // finish startup
    finishStartup();
  }


  /**
   * Configures the session info.
   *
   * @param sessionInfo the session info
   */
  protected void configureSessionInfo(SessionInfo sessionInfo) {
    String url = getCommandLine().getOptionValue(Constants.BACKEND_URL);
    String user = getCommandLine().getOptionValue(Constants.BACKEND_USER);
    String password = getCommandLine().getOptionValue(Constants.BACKEND_PASSWORD);
    if (url != null && user != null) {
      // don't use a properties file: get from command line (password is optional)
      Properties props = new Properties();
      props.setProperty(Constants.BACKEND_URL, url);
      props.setProperty(Constants.BACKEND_USER, user);
      props.setProperty(Constants.BACKEND_PASSWORD, password);
      sessionInfo.setProperties(props);
    }
    sessionInfo.applyProperties();
    if (sessionInfo.getApplicationName() == null) {
      sessionInfo.setApplicationName(getClass().getSimpleName());
    }
    applyProperties(sessionInfo.getProperties());
  }


  /**
   * Connects to the database backend or application server.
   */
  protected void login() {
    String username = getProperty(Constants.BACKEND_USER);
    char[] password = StringHelper.toCharArray(getProperty(Constants.BACKEND_PASSWORD));
    String sessionPropsName = getCommandLine().getOptionValue(Constants.BACKEND_PROPS);

    SessionInfo sessionInfo = createSessionInfo(username, password, sessionPropsName);
    configureSessionInfo(sessionInfo);
    setSessionInfo(sessionInfo);
    Session session = createSession(sessionInfo);
    session.makeCurrent();

    setDomainContext(createDomainContext(session));

    updateSessionInfoAfterLogin();
  }

}
