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

import org.tentackle.keyring.AbstractKeyring;
import org.tentackle.keyring.KeyringException;
import org.tentackle.keyring.SecretId;
import org.tentackle.keyring.SecretsHelper;
import org.tentackle.keyring.platform.ffm.LibSecret;
import org.tentackle.log.Logger;

/**
 * A keyring backed by the freedesktop.org Secret Service via {@code libsecret}.
 * <p>
 * Used on Linux and the other Unixes. It needs a Secret Service provider on the session bus, such as
 * GNOME Keyring, KWallet, or KeePassXC. On a headless machine, over SSH without a session, or in a
 * container, there usually is none, and the keyring is not {@link #isAvailable() available}.
 * <p>
 * The secrets go to the default collection, "login" on most desktops, which the user's login unlocks.
 * They show up in Seahorse ("Passwords and Keys") or KWalletManager with their label.
 */
public class LibSecretKeyring extends AbstractKeyring {

  /**
   * The name of this keyring.
   */
  public static final String NAME = "libsecret";

  private static final Logger LOGGER = Logger.get(LibSecretKeyring.class);

  private final LibSecret libSecret;

  /**
   * Creates the keyring.
   *
   * @throws KeyringException if {@code libsecret} is not installed
   */
  public LibSecretKeyring() {
    libSecret = new LibSecret();
  }

  @Override
  public String getName() {
    return NAME;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Looks up a secret that does not exist, which fails if there is no Secret Service on the session bus.
   * Since nothing matches, no locked collection is unlocked, and the user is not prompted.
   */
  @Override
  public boolean isAvailable() {
    try {
      libSecret.lookup(PROBE_SERVICE, PROBE_ACCOUNT);
      return true;
    }
    catch (KeyringException kex) {
      LOGGER.info("no Secret Service: {0}", kex.getMessage());
      return false;
    }
  }

  @Override
  protected void doStore(SecretId id, String label, char[] secret) {
    byte[] bytes = SecretsHelper.encode(secret);
    try {
      libSecret.store(id.service(), id.account(), label, bytes);
    }
    finally {
      SecretsHelper.wipe(bytes);
    }
  }

  @Override
  protected char[] doLoad(SecretId id) {
    byte[] bytes = libSecret.lookup(id.service(), id.account());
    try {
      return bytes == null ? null : SecretsHelper.decode(bytes);
    }
    finally {
      SecretsHelper.wipe(bytes);
    }
  }

  @Override
  protected boolean doRemove(SecretId id) {
    return libSecret.clear(id.service(), id.account());
  }

}
