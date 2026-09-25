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

import org.tentackle.common.Cryptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * A keyring kept in the Java user preferences.
 * <p>
 * The default keyring, and the fallback if the operating system's credential store is not usable.
 * The secrets are encrypted with the application's {@link Cryptor}, which keeps them from being picked up
 * by simply looking at the preferences. That is obfuscation, not protection: whoever has the preferences and the application,
 * which carries the {@link Cryptor}'s key, has the secrets. Hence, this keyring is not {@link #isSecure() secure}.
 * <p>
 * The keyring is only available if the application provides a cryptor.
 * <p>
 * The secrets are kept in the preferences node {@value #NODE}, each under a key derived from the hash of its
 * id: the preferences limit the length of a key, but not the length of a service or account.
 * The Java preferences are local to the machine and user, unlike Tentackle's database-backed
 * {@code org.tentackle.prefs}, which roam with the user.
 */
public class PreferencesKeyring extends AbstractKeyring {

  /**
   * The name of this keyring.
   */
  public static final String NAME = "preferences";

  /**
   * The preferences node holding the secrets.
   */
  public static final String NODE = "/org/tentackle/keyring";

  /**
   * Creates a preferences keyring.
   */
  public PreferencesKeyring() {
    // see -Xlint:missing-explicit-ctor
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean isSecure() {
    return false;
  }

  @Override
  public boolean isAvailable() {
    return getCryptor() != null;
  }

  @Override
  protected void doStore(SecretId id, String label, char[] secret) {
    String encrypted = requireCryptor().encrypt64(secret.clone());   // encrypt wipes its argument
    if (encrypted.length() > Preferences.MAX_VALUE_LENGTH) {
      throw new KeyringException("secret " + id + " is too large for the preferences");
    }
    Preferences node = getNode();
    node.put(getKey(id), encrypted);
    flush(node);
  }

  @Override
  protected char[] doLoad(SecretId id) {
    String encrypted = getNode().get(getKey(id), null);
    return encrypted == null ? null : requireCryptor().decrypt64ToChars(encrypted);
  }

  @Override
  protected boolean doRemove(SecretId id) {
    Preferences node = getNode();
    String key = getKey(id);
    if (node.get(key, null) == null) {
      return false;
    }
    node.remove(key);
    flush(node);
    return true;
  }


  /**
   * Gets the preferences node holding the secrets.
   *
   * @return the node
   */
  protected Preferences getNode() {
    return Preferences.userRoot().node(NODE);
  }

  /**
   * Gets the preferences key of a secret.
   *
   * @param id the secret's id
   * @return the key
   */
  protected String getKey(SecretId id) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(id.service().getBytes(StandardCharsets.UTF_8));
      digest.update((byte) 0);    // cannot occur in either part, see SecretId
      digest.update(id.account().getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
    }
    catch (NoSuchAlgorithmException ex) {
      throw new KeyringException("SHA-256 not supported", ex);    // every JDK must support it
    }
  }


  /**
   * Gets the cryptor.
   *
   * @return the cryptor, null if none
   */
  protected Cryptor getCryptor() {
    return Cryptor.getInstance();
  }


  private Cryptor requireCryptor() {
    Cryptor cryptor = getCryptor();
    if (cryptor == null) {
      throw new KeyringException("the preferences keyring needs a cryptor");
    }
    return cryptor;
  }

  private void flush(Preferences node) {
    try {
      node.flush();
    }
    catch (BackingStoreException ex) {
      throw new KeyringException("flushing the preferences failed", ex);
    }
  }

}
