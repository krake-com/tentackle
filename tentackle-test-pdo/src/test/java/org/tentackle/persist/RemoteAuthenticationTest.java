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

package org.tentackle.persist;

import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.dbms.Db;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.testng.AbstractPdoTest;
import org.tentackle.pdo.testng.ServerProcessWrapper;
import org.tentackle.persist.server.TestAuthenticationProvider;
import org.tentackle.persist.server.TestServer;
import org.tentackle.session.LoginFailedException;
import org.tentackle.session.SessionInfo;
import org.tentackle.session.auth.TokenCredentials;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;

/**
 * Tests logging in with a token instead of a username and password.
 * <p>
 * Covers the whole path: the credentials travel to the server via TRIP, the server hands them to
 * the {@link org.tentackle.session.auth.AuthenticationManager}, the discovered
 * {@link TestAuthenticationProvider} verifies them, and the proven identity comes back to the client.
 *
 * @author harald
 */
public class RemoteAuthenticationTest {

  /**
   * Opens a remote session presenting a token.
   *
   * @param token the token
   * @return the session
   */
  private Db login(char[] token) {
    SessionInfo sessionInfo = Pdo.createSessionInfo("client");
    sessionInfo.setCredentials(new TokenCredentials(token));
    return (Db) Pdo.createSession(sessionInfo);
  }


  @Test(groups = "remote1", dependsOnGroups = "local")
  public void testTokenAuthentication() throws Exception {
    Reporter.log("RemoteAuthenticationTest.testTokenAuthentication <br>", true);

    ServerProcessWrapper server = new ServerProcessWrapper(AbstractPdoTest.runClass(TestServer.class));
    try {
      server.awaitReady(30000);

      // a valid token identifies the user, even though the client never knew who it was
      Db session = login(TestAuthenticationProvider.VALID_TOKEN);
      try {
        SessionInfo serverInfo = session.getRemoteSession().getClientSessionInfo();
        assertEquals(serverInfo.getUserId(), TestAuthenticationProvider.USER_ID);
        assertEquals(serverInfo.getUserClassId(), TestAuthenticationProvider.USER_CLASS_ID);
        assertEquals(serverInfo.getUserName(), TestAuthenticationProvider.USER_NAME,
                     "the server must report the name it authenticated");
        assertNull(serverInfo.getPassword(), "token credentials must not provide a password");
      }
      finally {
        session.close();
      }

      // an unknown token is refused by the provider
      assertThrows(LoginFailedException.class, () -> login("bogus-token".toCharArray()));
    }
    finally {
      server.stopAndWait();
    }
  }

}
