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

package org.tentackle.io;

import org.tentackle.common.ServiceFactory;

/**
 * Holder for the keystore password provider singleton.
 */
interface KeyStorePasswordProviderHolder {
  KeyStorePasswordProvider INSTANCE = ServiceFactory.createService(KeyStorePasswordProvider.class,
                                                                   DefaultKeyStorePasswordProvider.class);
}


/**
 * Provides the passwords of keystores whose password is not configured.
 * <p>
 * A client that authenticates by a certificate keeps its private key in a keystore. Configuring the
 * keystore's password in the transport URI or a system property ({@code kspass}, see {@link SslParameters})
 * would leave the key unprotected, since anybody who can read the configuration can use the key.
 * Instead, the password can be left out and entered by the user before the connection is established,
 * for example by a credentials provider at login. The password is registered here and picked up by the
 * {@link SslParameters} when the keystore is loaded. It never leaves the client.
 * <p>
 * Configured passwords always take precedence. With nothing registered, the provider returns null and the
 * keystore is loaded without a password, exactly as before.
 * <p>
 * Replaceable via {@link org.tentackle.common.Service &#64;Service}, for example to read the password from
 * the operating system's keyring.
 *
 * @author harald
 */
public interface KeyStorePasswordProvider {

  /**
   * Gets the keystore password provider singleton.
   *
   * @return the provider, never null
   */
  static KeyStorePasswordProvider getInstance() {
    return KeyStorePasswordProviderHolder.INSTANCE;
  }

  /**
   * Gets the password of a keystore.
   *
   * @param keyStoreFile the path of the keystore file
   * @return a copy of the password the caller should wipe once done, null if none is registered
   */
  char[] getPassword(String keyStoreFile);

  /**
   * Registers the password of a keystore.
   *
   * @param keyStoreFile the path of the keystore file
   * @param password the password, copied, null to remove it
   */
  void setPassword(String keyStoreFile, char[] password);

  /**
   * Removes and wipes the password of a keystore.
   *
   * @param keyStoreFile the path of the keystore file
   */
  void clear(String keyStoreFile);

}
