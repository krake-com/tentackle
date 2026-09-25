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
import org.tentackle.keyring.Secrets;
import org.tentackle.keyring.platform.ffm.MacSecurity;
import org.tentackle.log.Logger;

/**
 * A keyring backed by the macOS keychain.
 * <p>
 * The secrets are generic passwords in the user's login keychain and show up in Keychain Access with
 * their label. The keychain grants access to the application that created an item without asking.
 * Any other application, and the same application after its binary changed, must be allowed by the user,
 * unless the binary is signed with the same identity. An update of an unsigned application therefore
 * makes macOS ask the user once per secret.
 */
public class MacKeychain extends AbstractKeyring {

  /**
   * The name of this keyring.
   */
  public static final String NAME = "macos";

  private static final Logger LOGGER = Logger.get(MacKeychain.class);

  private final MacSecurity security;

  /**
   * Creates the keyring.
   *
   * @throws KeyringException if not running on macOS
   */
  public MacKeychain() {
    security = new MacSecurity();
  }

  @Override
  public String getName() {
    return NAME;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Looks up a secret that does not exist, which fails if there is no keychain to talk to, for example,
   * in an SSH session while the login keychain is locked. Since nothing matches, the user is not prompted.
   */
  @Override
  public boolean isAvailable() {
    try {
      security.copy(PROBE_SERVICE, PROBE_ACCOUNT);
      return true;
    }
    catch (KeyringException kex) {
      LOGGER.info("keychain not usable: {0}", kex.getMessage());
      return false;
    }
  }

  @Override
  protected void doStore(SecretId id, String label, char[] secret) {
    byte[] bytes = Secrets.encode(secret);
    try {
      // try update first: in the common case of renewing a token, the item exists
      if (security.update(id.service(), id.account(), label, bytes) == MacSecurity.ERR_SEC_ITEM_NOT_FOUND &&
          security.add(id.service(), id.account(), label, bytes) == MacSecurity.ERR_SEC_DUPLICATE_ITEM) {
        // someone else added it in between
        security.update(id.service(), id.account(), label, bytes);
      }
    }
    finally {
      Secrets.wipe(bytes);
    }
  }

  @Override
  protected char[] doLoad(SecretId id) {
    byte[] bytes = security.copy(id.service(), id.account());
    try {
      return bytes == null ? null : Secrets.decode(bytes);
    }
    finally {
      Secrets.wipe(bytes);
    }
  }

  @Override
  protected boolean doRemove(SecretId id) {
    return security.delete(id.service(), id.account());
  }

}
