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

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.misc.Secret;
import org.tentackle.trip.TripReferencable;

import java.io.Serial;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Token-based credentials, such as an OAuth2 access token or an OIDC ID token.
 * <p>
 * Tentackle does not interpret the token: it is the job of an {@link AuthenticationProvider}
 * to verify it (signature, issuer, audience, expiry) and to derive the principal from it.
 * The framework merely transports it to the server and hands it to the provider chain.
 * <p>
 * The optional refresh token is meant for the <em>client</em> side only
 * ({@link CredentialsProvider#refresh(Credentials)}); a server-side provider never needs it.
 *
 * @author harald
 */
@TripReferencable(TripReferencable.NEVER)
public class TokenCredentials implements Credentials, Cloneable {

  /** the scheme name of token credentials. */
  public static final String SCHEME = "bearer";

  /** the default token type. */
  public static final String DEFAULT_TOKEN_TYPE = "Bearer";

  @Serial
  private static final long serialVersionUID = 1L;

  private String tokenType;                 // the token type, e.g. "Bearer"
  private Secret token;                     // the token itself
  private Secret refreshToken;              // the optional refresh token (client side only)
  private String issuer;                    // the optional issuer that minted the token
  private long expiresAt;                   // epochal millis the token expires, 0 if unknown
  @SuppressWarnings("serial")
  private Map<String, String> parameters;   // optional additional parameters


  /**
   * Creates token credentials.
   *
   * @param tokenType the token type, null for {@value #DEFAULT_TOKEN_TYPE}
   * @param token the token, null if none
   */
  public TokenCredentials(String tokenType, char[] token) {
    this.tokenType = tokenType == null ? DEFAULT_TOKEN_TYPE : tokenType;
    this.token = new Secret(token);
    this.refreshToken = new Secret();
    this.parameters = new LinkedHashMap<>();
  }

  /**
   * Creates bearer token credentials.
   *
   * @param token the token, null if none
   */
  public TokenCredentials(char[] token) {
    this(null, token);
  }

  /**
   * Creates empty bearer token credentials.<br>
   * Required by TRIP to deserialize the credentials on the server.
   */
  public TokenCredentials() {
    this(null, null);
  }


  @Override
  public String getScheme() {
    return SCHEME;
  }

  /**
   * Gets the token type.
   *
   * @return the token type, never null
   */
  public String getTokenType() {
    return tokenType;
  }

  /**
   * Sets the token type.
   *
   * @param tokenType the token type, null for {@value #DEFAULT_TOKEN_TYPE}
   */
  public void setTokenType(String tokenType) {
    this.tokenType = tokenType == null ? DEFAULT_TOKEN_TYPE : tokenType;
  }

  /**
   * Gets the token.
   *
   * @return the token, null if none or cleared
   */
  public char[] getToken() {
    return token.get();
  }

  /**
   * Sets the token.
   *
   * @param token the token, null if none
   */
  public void setToken(char[] token) {
    this.token.set(token);
  }

  /**
   * Gets the refresh token.
   *
   * @return the refresh token, null if none or cleared
   */
  public char[] getRefreshToken() {
    return refreshToken.get();
  }

  /**
   * Sets the refresh token.
   *
   * @param refreshToken the refresh token, null if none
   */
  public void setRefreshToken(char[] refreshToken) {
    this.refreshToken.set(refreshToken);
  }

  /**
   * Gets the issuer that minted the token.
   *
   * @return the issuer, null if unknown
   */
  public String getIssuer() {
    return issuer;
  }

  /**
   * Sets the issuer that minted the token.
   *
   * @param issuer the issuer, null if unknown
   */
  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  /**
   * Gets the epochal time the token expires.
   * <p>
   * This is only a hint provided by the client: the authoritative expiry is the one
   * the {@link AuthenticationProvider} reads from the verified token.
   *
   * @return the expiry in epochal millis, 0 if unknown
   */
  public long getExpiresAt() {
    return expiresAt;
  }

  /**
   * Sets the epochal time the token expires.
   *
   * @param expiresAt the expiry in epochal millis, 0 if unknown
   */
  public void setExpiresAt(long expiresAt) {
    this.expiresAt = expiresAt;
  }

  /**
   * Returns whether the token is expired according to {@link #getExpiresAt()}.
   *
   * @return true if the expiry is known and in the past
   */
  public boolean isExpired() {
    return expiresAt != 0 && expiresAt <= System.currentTimeMillis();
  }

  /**
   * Gets the additional parameters.<br>
   * Useful to pass along non-confidential extras, such as the tenant or the requested scope.
   *
   * @return the parameters, never null
   */
  public Map<String, String> getParameters() {
    return parameters;
  }

  @Override
  public void clear() {
    token.clear();
    refreshToken.clear();
  }

  @Override
  public TokenCredentials clone() {
    TokenCredentials credentials;
    try {
      credentials = (TokenCredentials) super.clone();
    }
    catch (CloneNotSupportedException ex) {
      throw new TentackleRuntimeException(ex);    // this shouldn't happen, since we are Cloneable
    }
    credentials.token = token.clone();
    credentials.refreshToken = refreshToken.clone();
    credentials.parameters = new LinkedHashMap<>(parameters);
    return credentials;
  }

  @Override
  public String toString() {
    return tokenType + ' ' + token + (issuer == null ? "" : "@" + issuer);
  }

}
