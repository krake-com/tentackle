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
import org.tentackle.common.EncryptedProperties;
import org.tentackle.log.Logger;
import org.tentackle.misc.PropertiesUtilities;
import org.tentackle.session.LoginFailedException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.auth.Credentials;
import org.tentackle.session.auth.CredentialsPrompt;
import org.tentackle.session.auth.CredentialsProvider;
import org.tentackle.session.auth.DefaultCredentialsPrompt;

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
   * @param name the application name, null for default name
   * @param version the application version, null for default version
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
      EncryptedProperties props = PropertiesUtilities.getInstance().create();
      props.setProperty(Constants.BACKEND_URL, url);
      props.setProperty(Constants.BACKEND_USER, user);
      if (password != null) {
        props.setEncryptedProperty(Constants.BACKEND_PASSWORD, password);
      }
      sessionInfo.setProperties(props);
    }
    sessionInfo.applyProperties();
    applyProperties(sessionInfo.getProperties());
  }


  /**
   * Connects to the database backend or application server.
   */
  protected void login() {
    String username = getPropertyIgnoreCase(Constants.BACKEND_USER);
    char[] password = getPropertyAsCharsIgnoreCase(Constants.BACKEND_PASSWORD);
    String sessionPropsName = getCommandLine().getOptionValue(Constants.BACKEND_PROPS);

    SessionInfo sessionInfo = createSessionInfo(username, password, sessionPropsName);
    configureSessionInfo(sessionInfo);
    obtainCredentials(sessionInfo);
    setSessionInfo(sessionInfo);
    Session session = createSession(sessionInfo);
    session.makeCurrent();

    setDomainContext(createDomainContext(session));

    updateSessionInfoAfterLogin();
  }


  /**
   * Obtains the credentials to log in with.
   * <p>
   * Determines the {@link CredentialsProvider} from the {@value Constants#BACKEND_AUTHENTICATION}
   * property and lets it create the credentials. For the default username/password method there is
   * nothing to obtain, since the session info carries them already.
   *
   * @param sessionInfo the session info
   */
  protected void obtainCredentials(SessionInfo sessionInfo) {
    CredentialsProvider credentialsProvider = CredentialsProvider.getInstance(sessionInfo);
    Credentials credentials = credentialsProvider.createCredentials(sessionInfo, createCredentialsPrompt());
    if (credentials == null) {
      throw new LoginFailedException("no credentials obtained");
    }
    sessionInfo.setCredentials(credentials);
  }

  /**
   * Creates the prompt an interactive {@link CredentialsProvider} may use.
   *
   * @return the prompt, never null
   */
  protected CredentialsPrompt createCredentialsPrompt() {
    return new DefaultCredentialsPrompt();
  }

}
