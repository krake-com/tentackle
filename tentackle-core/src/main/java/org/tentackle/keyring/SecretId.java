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

package org.tentackle.keyring;

import java.util.Objects;

/**
 * The identity of a secret in a {@link Keyring}.
 * <p>
 * A secret is identified by a service and an account, which is how every credential store of the
 * operating systems organizes its entries. The service names what the secret is for, usually the
 * application and the purpose, such as {@code "com.example.myapp/google"}. The account names whose
 * secret it is within that service, such as the user or an OAuth client id.
 *
 * @param service the service, never blank
 * @param account the account, never blank
 */
public record SecretId(String service, String account) {

  /**
   * Creates a secret id.
   *
   * @param service the service, never blank
   * @param account the account, never blank
   * @throws IllegalArgumentException if the service or account is blank or contains a NUL character
   */
  public SecretId {
    check("service", service);
    check("account", account);
  }

  @Override
  public String toString() {
    return account + "@" + service;
  }


  /**
   * Checks a component of the id.
   *
   * @param name the component's name
   * @param value the value to check
   */
  private static void check(String name, String value) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    if (value.indexOf('\0') >= 0) {
      // all native APIs take NUL-terminated strings
      throw new IllegalArgumentException(name + " must not contain NUL characters");
    }
  }

}
