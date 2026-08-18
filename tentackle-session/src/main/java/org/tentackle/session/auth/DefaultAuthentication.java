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

import org.tentackle.trip.TripReferencable;

import java.io.Serial;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The default implementation of an {@link Authentication}.
 *
 * @author harald
 */
@TripReferencable(TripReferencable.NEVER)
public class DefaultAuthentication implements Authentication {

  @Serial
  private static final long serialVersionUID = 1L;

  private String name;                          // the principal's name
  private Credentials credentials;              // the verified credentials
  @SuppressWarnings("serial")
  private Map<String, Object> attributes;       // the principal's attributes
  @SuppressWarnings("serial")
  private Set<String> roles;                    // the external roles
  private String providerName;                  // the authenticating provider
  private long expiresAt;                       // expiry in epochal millis, 0 if none
  private long userId;                          // the user's object id
  private int userClassId;                      // the user's class id


  /**
   * Creates an authentication.
   *
   * @param name the principal's name, null if unknown
   * @param credentials the verified credentials
   */
  public DefaultAuthentication(String name, Credentials credentials) {
    this.name = name;
    this.credentials = credentials;
    this.attributes = new LinkedHashMap<>();
    this.roles = new LinkedHashSet<>();
  }

  /**
   * Creates an empty authentication.<br>
   * Required by TRIP, should an application send an authentication to a client.
   */
  public DefaultAuthentication() {
    this(null, null);
  }


  @Override
  public String getName() {
    return name;
  }

  @Override
  public Credentials getCredentials() {
    return credentials;
  }

  @Override
  public String getProviderName() {
    return providerName;
  }

  /**
   * Sets the name of the provider that authenticated the credentials.
   *
   * @param providerName the provider name
   */
  public void setProviderName(String providerName) {
    this.providerName = providerName;
  }

  @Override
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  @Override
  public Set<String> getRoles() {
    return roles;
  }

  @Override
  public long getExpiresAt() {
    return expiresAt;
  }

  /**
   * Sets the epochal time this authentication expires.
   *
   * @param expiresAt the expiry in epochal millis, 0 if it does not expire
   */
  public void setExpiresAt(long expiresAt) {
    this.expiresAt = expiresAt;
  }

  @Override
  public long getUserId() {
    return userId;
  }

  @Override
  public void setUserId(long userId) {
    this.userId = userId;
  }

  @Override
  public int getUserClassId() {
    return userClassId;
  }

  @Override
  public void setUserClassId(int userClassId) {
    this.userClassId = userClassId;
  }

  @Override
  public String toString() {
    return name + '[' + userId + "]/" + (credentials == null ? "?" : credentials.getScheme()) +
           (providerName == null ? "" : "@" + providerName);
  }

}
