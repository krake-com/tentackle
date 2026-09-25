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

package org.tentackle.keyring.platform;

import org.tentackle.common.Service;
import org.tentackle.keyring.AbstractKeyring;
import org.tentackle.keyring.DefaultKeyringFactory;
import org.tentackle.keyring.Keyring;
import org.tentackle.keyring.KeyringException;
import org.tentackle.keyring.KeyringFactory;
import org.tentackle.log.Logger;

import java.util.Locale;

/**
 * The keyring factory for the credential store of the operating system.
 * <p>
 * Replaces the {@link DefaultKeyringFactory} of Tentackle core as soon as this module is part of the
 * application. Picks the keyring as follows:
 * <ol>
 *   <li>the keyring named by the system property {@value KeyringFactory#KEYRING_PROPERTY}, if set,</li>
 *   <li>the credential store of the operating system, if usable,</li>
 *   <li>the fallbacks of the {@link DefaultKeyringFactory}.</li>
 * </ol>
 * Falling back is logged as a warning, since the secrets are then either not protected or not remembered.
 */
@Service(KeyringFactory.class)
public class PlatformKeyringFactory extends DefaultKeyringFactory {

  private static final Logger LOGGER = Logger.get(PlatformKeyringFactory.class);

  /**
   * Creates the factory.
   */
  public PlatformKeyringFactory() {
    // see -Xlint:missing-explicit-ctor
  }

  /**
   * {@inheritDoc}
   * <p>
   * Returns the credential store of the operating system, if usable.
   */
  @Override
  protected Keyring selectBestKeyring() {
    String platformName = getPlatformKeyringName();
    try {
      AbstractKeyring platform = createKeyring(platformName);
      if (platform.isAvailable()) {
        return platform;
      }
    }
    catch (KeyringException | LinkageError ex) {
      LOGGER.info("keyring " + platformName + " not usable", ex);
    }
    LOGGER.warning("the credential store of the OS is not usable, falling back");
    return super.selectBestKeyring();
  }

  /**
   * {@inheritDoc}
   * <p>
   * Adds the keyrings of the operating systems.
   */
  @Override
  protected AbstractKeyring createKeyring(String name) {
    return switch (name) {
      case MacKeychain.NAME -> new MacKeychain();
      case WindowsCredentialManager.NAME -> new WindowsCredentialManager();
      case LibSecretKeyring.NAME -> new LibSecretKeyring();
      default -> super.createKeyring(name);
    };
  }

  /**
   * Gets the name of the keyring for the current operating system.
   *
   * @return the keyring name
   */
  protected String getPlatformKeyringName() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.startsWith("mac")) {
      return MacKeychain.NAME;
    }
    if (os.startsWith("windows")) {
      return WindowsCredentialManager.NAME;
    }
    return LibSecretKeyring.NAME;
  }

}
