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

import org.tentackle.common.ServiceFactory;


interface KeyringFactoryHolder {
  // no provider is the normal case without tentackle-keyring: don't log the fallback
  KeyringFactory INSTANCE = ServiceFactory.createService(KeyringFactory.class, DefaultKeyringFactory.class, false);
}


/**
 * Factory providing the {@link Keyring}.
 * <p>
 * Without a {@code @Service(KeyringFactory.class)}, the {@link DefaultKeyringFactory} is used, which keeps the
 * secrets in the Java preferences or in memory. The module {@code tentackle-keyring} provides a factory for the
 * credential store of the operating system. Applications may replace the factory as well, for example, to
 * refuse the insecure fallback or to plug in a keyring of their own.
 *
 * @author harald
 */
public interface KeyringFactory {

  /**
   * The system property to select the keyring by its {@link Keyring#getName() name}.
   * <p>
   * Forcing a keyring that is not usable on the current platform is an error.
   */
  String KEYRING_PROPERTY = "tentackle.keyring";


  /**
   * Gets the singleton.
   *
   * @return the singleton
   */
  static KeyringFactory getInstance() {
    return KeyringFactoryHolder.INSTANCE;
  }


  /**
   * Gets the keyring.
   * <p>
   * The keyring is determined once, and then the same instance is returned on every invocation.
   *
   * @return the keyring, never null
   * @throws KeyringException if a keyring was forced via {@value #KEYRING_PROPERTY} that is not usable
   */
  Keyring getKeyring();

}
