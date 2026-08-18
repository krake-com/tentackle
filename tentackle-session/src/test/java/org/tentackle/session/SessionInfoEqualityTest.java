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
package org.tentackle.session;

import org.tentackle.session.auth.TokenCredentials;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests the equality contract session infos are used as a pool key by.
 * <p>
 * A {@link MultiUserSessionPool} maps a user to its sessions, looks that mapping up again for a
 * session handed back to it -- whose info is a {@link SessionInfo#clone() clone} -- and lets
 * {@link MultiUserSessionPool#close(SessionInfo)} address a user by a session info created just for
 * that purpose. All three only work while equality is decided by the username alone.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class SessionInfoEqualityTest {

  @Test
  public void testCloneEqualsItsOriginal() {
    SessionInfo sessionInfo = new DefaultSessionInfo("hugo", "secret".toCharArray(), null);
    sessionInfo.setSessionName("original");
    SessionInfo clone = sessionInfo.clone();

    // the pool finds a user's pool again for a session it handed out
    Assert.assertEquals(clone, sessionInfo, "a clone must stay equal to its original");
    Assert.assertEquals(clone.hashCode(), sessionInfo.hashCode());
  }


  @Test
  public void testAnAddressingInfoFindsTheUser() {
    Map<SessionInfo, String> poolMap = new HashMap<>();
    SessionInfo hugo = new DefaultSessionInfo("hugo", "secret".toCharArray(), null);
    hugo.setApplicationName("app");
    poolMap.put(hugo, "hugo's pool");

    // this is how MultiUserSessionPool.close(SessionInfo) addresses a user
    SessionInfo addressing = new DefaultSessionInfo();
    addressing.setUserName("hugo");

    Assert.assertEquals(poolMap.get(addressing), "hugo's pool");
  }


  @Test
  public void testCredentialsAreNotCompared() {
    SessionInfo password = new DefaultSessionInfo("hugo", "secret".toCharArray(), null);
    SessionInfo other = new DefaultSessionInfo("hugo", "different".toCharArray(), null);
    SessionInfo token = new DefaultSessionInfo("hugo", null, null);
    token.setCredentials(new TokenCredentials("access".toCharArray()));

    /*
     * Credentials have no value equality and are copied by clone(), so comparing them would make a
     * clone differ from its original and break every pool lookup.
     */
    Assert.assertEquals(other, password);
    Assert.assertEquals(token, password);
    Assert.assertEquals(token.hashCode(), password.hashCode());
  }


  @Test
  public void testDifferentUsersDiffer() {
    SessionInfo hugo = new DefaultSessionInfo("hugo", "secret".toCharArray(), null);
    SessionInfo emil = new DefaultSessionInfo("emil", "secret".toCharArray(), null);

    Assert.assertNotEquals(emil, hugo);
  }


  @Test
  public void testAnonymousInfosShareOneUser() {
    Assert.assertEquals(new DefaultSessionInfo(), new DefaultSessionInfo());
    Assert.assertEquals(new DefaultSessionInfo().hashCode(), new DefaultSessionInfo().hashCode());
    Assert.assertNotEquals(new DefaultSessionInfo("hugo", null, null), new DefaultSessionInfo());
  }


  @Test
  public void testForeignObjectsAreNotEqual() {
    SessionInfo sessionInfo = new DefaultSessionInfo("hugo", null, null);

    Assert.assertNotEquals(sessionInfo, null);
    Assert.assertNotEquals(sessionInfo, "hugo");
  }

}
