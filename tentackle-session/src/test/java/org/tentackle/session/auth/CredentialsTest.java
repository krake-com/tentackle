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
package org.tentackle.session.auth;

import org.tentackle.session.DefaultSessionInfo;
import org.tentackle.session.SessionInfo;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests the credentials carried by a session info.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class CredentialsTest {

  @Test
  public void testPasswordIsClearedButNotShown() {
    PasswordCredentials credentials = new PasswordCredentials("hugo", "secret".toCharArray());
    Assert.assertTrue(credentials.isPasswordSet());
    Assert.assertFalse(credentials.toString().contains("secret"));

    credentials.clear();
    Assert.assertFalse(credentials.isPasswordSet());
    Assert.assertTrue(credentials.isPasswordCleared());
    Assert.assertNull(credentials.getPassword());
  }

  @Test
  public void testCloneCopiesTheSecretPhysically() {
    PasswordCredentials credentials = new PasswordCredentials("hugo", "secret".toCharArray());
    PasswordCredentials clone = credentials.clone();

    credentials.clear();
    Assert.assertNull(credentials.getPassword());
    Assert.assertEquals(clone.getPassword(), "secret".toCharArray());
  }

  @Test
  public void testTokenCredentialsClearBothTokens() {
    TokenCredentials credentials = new TokenCredentials("access".toCharArray());
    credentials.setRefreshToken("refresh".toCharArray());
    credentials.setIssuer("https://issuer.example.com");
    Assert.assertEquals(credentials.getScheme(), TokenCredentials.SCHEME);
    Assert.assertEquals(credentials.getTokenType(), TokenCredentials.DEFAULT_TOKEN_TYPE);
    Assert.assertFalse(credentials.toString().contains("access"));

    TokenCredentials clone = credentials.clone();
    credentials.clear();
    Assert.assertNull(credentials.getToken());
    Assert.assertNull(credentials.getRefreshToken());
    Assert.assertEquals(clone.getToken(), "access".toCharArray());
    Assert.assertEquals(clone.getRefreshToken(), "refresh".toCharArray());
  }

  @Test
  public void testTokenExpiry() {
    TokenCredentials credentials = new TokenCredentials("access".toCharArray());
    Assert.assertFalse(credentials.isExpired());    // unknown expiry never expires
    credentials.setExpiresAt(System.currentTimeMillis() - 1000);
    Assert.assertTrue(credentials.isExpired());
  }

  @Test
  public void testSessionInfoDefaultsToPasswordCredentials() {
    SessionInfo sessionInfo = new DefaultSessionInfo("hugo", "secret".toCharArray(), null);

    Assert.assertTrue(sessionInfo.getCredentials() instanceof PasswordCredentials);
    Assert.assertEquals(sessionInfo.getUserName(), "hugo");
    Assert.assertEquals(sessionInfo.getPassword(), "secret".toCharArray());
    Assert.assertEquals(((PasswordCredentials) sessionInfo.getCredentials()).getUserName(), "hugo");

    // the username is kept in sync with the credentials
    sessionInfo.setUserName("emil");
    Assert.assertEquals(((PasswordCredentials) sessionInfo.getCredentials()).getUserName(), "emil");

    sessionInfo.clearPassword();
    Assert.assertNull(sessionInfo.getPassword());
  }

  @Test
  public void testSessionInfoCarriesTokenCredentials() {
    SessionInfo sessionInfo = new DefaultSessionInfo("hugo", "secret".toCharArray(), null);
    sessionInfo.setCredentials(new TokenCredentials("access".toCharArray()));

    // there is no password anymore, but the session info still works
    Assert.assertNull(sessionInfo.getPassword());
    Assert.assertEquals(sessionInfo.getCredentials().getScheme(), TokenCredentials.SCHEME);
    Assert.assertFalse(sessionInfo.toString().contains("access"));

    SessionInfo clone = sessionInfo.clone();
    sessionInfo.getCredentials().clear();
    Assert.assertEquals(((TokenCredentials) clone.getCredentials()).getToken(), "access".toCharArray());

    // setting a password turns them back into password credentials
    sessionInfo.setPassword("secret".toCharArray());
    Assert.assertTrue(sessionInfo.getCredentials() instanceof PasswordCredentials);
    Assert.assertEquals(sessionInfo.getPassword(), "secret".toCharArray());
  }

  @Test
  public void testPasswordCredentialsProviderIsRegistered() {
    Assert.assertTrue(CredentialsProvider.getInstance((String) null) instanceof PasswordCredentialsProvider);
    Assert.assertTrue(CredentialsProvider.getMethods().contains(PasswordCredentials.SCHEME));
    Assert.assertFalse(CredentialsProvider.getInstance(PasswordCredentials.SCHEME).isInteractive());
  }

}
