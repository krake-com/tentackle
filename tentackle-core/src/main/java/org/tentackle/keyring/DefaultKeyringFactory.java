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

import org.tentackle.log.Logger;

/**
 * The default keyring factory.
 * <p>
 * Used if no {@code @Service(KeyringFactory.class)} is found, i.e., the application runs without the credential
 * store of the operating system. It picks the keyring as follows:
 * <ol>
 *   <li>the keyring named by the system property {@value KeyringFactory#KEYRING_PROPERTY}, if set,</li>
 *   <li>the {@link PreferencesKeyring}, if the application provides a cryptor,</li>
 *   <li>the {@link MemoryKeyring}.</li>
 * </ol>
 * The module {@code tentackle-keyring} provides a factory that puts the credential store of the operating
 * system in front of them. Adding it to the application's dependencies is all it takes.
 */
public class DefaultKeyringFactory implements KeyringFactory {

  private static final Logger LOGGER = Logger.get(DefaultKeyringFactory.class);

  private volatile Keyring keyring;

  /**
   * Creates the factory.
   */
  public DefaultKeyringFactory() {
    // see -Xlint:missing-explicit-ctor
  }

  @Override
  public Keyring getKeyring() {
    Keyring result = keyring;
    if (result == null) {
      synchronized (this) {
        result = keyring;
        if (result == null) {
          result = keyring = selectKeyring();
          LOGGER.info("using keyring {0}", result.getName());
        }
      }
    }
    return result;
  }


  /**
   * Selects the keyring.
   * <p>
   * Returns the keyring forced via {@value KeyringFactory#KEYRING_PROPERTY}, or the
   * {@link #selectBestKeyring() best keyring} available.
   *
   * @return the keyring
   * @throws KeyringException if the forced keyring does not exist or is not available
   */
  protected Keyring selectKeyring() {
    String name = System.getProperty(KEYRING_PROPERTY);
    if (name != null && !name.isBlank()) {
      AbstractKeyring forced = createKeyring(name.trim());
      if (!forced.isAvailable()) {
        throw new KeyringException("keyring '" + forced.getName() + "' is not available");
      }
      return forced;
    }
    return selectBestKeyring();
  }

  /**
   * Selects the best keyring available.
   * <p>
   * Overridden by factories providing more secure keyrings, which fall back to this implementation
   * if theirs is not usable.
   *
   * @return the keyring
   */
  protected Keyring selectBestKeyring() {
    AbstractKeyring preferences = createKeyring(PreferencesKeyring.NAME);
    if (preferences.isAvailable()) {
      LOGGER.info("secrets are kept in the preferences, encrypted with the cryptor");
      return preferences;
    }
    LOGGER.warning("no cryptor available: secrets are not remembered beyond the lifetime of the application");
    return createKeyring(MemoryKeyring.NAME);
  }

  /**
   * Creates a keyring by its name.
   * <p>
   * Overridden by factories providing more keyrings.
   *
   * @param name the keyring's name
   * @return the keyring
   * @throws KeyringException if there is no such keyring, or it cannot be created on this platform
   */
  protected AbstractKeyring createKeyring(String name) {
    return switch (name) {
      case PreferencesKeyring.NAME -> new PreferencesKeyring();
      case MemoryKeyring.NAME -> new MemoryKeyring();
      default -> throw new KeyringException("no such keyring '" + name + "'");
    };
  }

}
