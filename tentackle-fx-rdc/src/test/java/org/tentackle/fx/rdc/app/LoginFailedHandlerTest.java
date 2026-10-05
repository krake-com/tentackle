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

package org.tentackle.fx.rdc.app;

import org.testng.annotations.Test;

import org.tentackle.common.Constants;
import org.tentackle.common.EncryptedProperties;
import org.tentackle.fx.FxController;
import org.tentackle.pdo.DomainContext;
import org.tentackle.misc.Identifiable;
import org.tentackle.session.AlreadyLoggedInException;
import org.tentackle.session.DefaultSessionInfo;
import org.tentackle.session.LoginFailedException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Unit tests for {@link LoginFailedHandler}.
 * <p>
 * Covers the paths that only report a status to the application: the first two refused
 * logins, an already logged-in user and the renewal of credentials. The final refusal and
 * unexpected errors show a dialog on the FX thread and are not covered, since no JavaFX
 * toolkit is started.
 */
@SuppressWarnings("missing-explicit-ctor")
public class LoginFailedHandlerTest {

  /**
   * Records the status messages instead of showing them.
   */
  private static class StatusRecorder extends DesktopApplication<FxController> {

    private final List<String> messages = new ArrayList<>();

    StatusRecorder() {
      super("test", "1.0");
    }

    @Override
    public Class<? extends FxController> getMainControllerClass() {
      return FxController.class;
    }

    @Override
    public <U extends Identifiable> U getUser(DomainContext context, long userId) {
      return null;
    }

    @Override
    public void showApplicationStatus(String msg, double progress) {
      messages.add(msg);
    }
  }


  private static SessionInfo sessionInfo(String authenticationMethod) {
    EncryptedProperties properties = new EncryptedProperties();
    if (authenticationMethod != null) {
      properties.setProperty(Constants.BACKEND_AUTHENTICATION, authenticationMethod);
    }
    return new DefaultSessionInfo(properties);
  }


  @Test
  public void testFirstTwoRefusalsAnnounceRetries() {
    StatusRecorder application = new StatusRecorder();
    LoginFailedHandler handler = new LoginFailedHandler(application, null, sessionInfo(null));

    assertNull(handler.handle(new LoginFailedException((Session) null, "refused")));
    assertNull(handler.handle(new LoginFailedException((Session) null, "refused")));
    assertEquals(application.messages, List.of(AppFxRdcBundle.getString("LOGIN FAILED! (2 MORE RETRIES)"),
                                               AppFxRdcBundle.getString("LOGIN FAILED! (LAST RETRY)")));
  }

  @Test
  public void testAlreadyLoggedInOnlyShowsStatus() {
    StatusRecorder application = new StatusRecorder();
    SessionInfo info = sessionInfo(null);
    LoginFailedHandler handler = new LoginFailedHandler(application, null, info);

    AlreadyLoggedInException ex = new AlreadyLoggedInException(info);
    assertNull(handler.handle(ex));
    assertEquals(application.messages, List.of(ex.getLocalizedMessage()));

    // does not count as a refused login
    handler.handle(new LoginFailedException((Session) null, "refused"));
    assertEquals(application.messages.getLast(), AppFxRdcBundle.getString("LOGIN FAILED! (2 MORE RETRIES)"));
  }

  @Test
  public void testPasswordCredentialsCannotBeRenewed() {
    StatusRecorder application = new StatusRecorder();
    LoginFailedHandler handler = new LoginFailedHandler(application, null, sessionInfo(null));
    assertNull(handler.renewCredentials(), "password credentials cannot be refreshed");
    assertEquals(application.messages, List.of(), "nothing renewed -> no status");
  }

  @Test
  public void testUnknownAuthenticationMethodCannotBeRenewed() {
    StatusRecorder application = new StatusRecorder();
    LoginFailedHandler handler = new LoginFailedHandler(application, null, sessionInfo("no-such-method"));
    assertNull(handler.renewCredentials(), "a missing credentials provider must not escape the handler");
    assertNull(handler.renewCredentials(), "renewal is tried only once");
    assertEquals(application.messages, List.of());
  }

}
