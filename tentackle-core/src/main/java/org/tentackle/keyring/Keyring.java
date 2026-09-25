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

/**
 * A store for secrets, such as passwords or tokens, kept on the local machine.
 * <p>
 * Tentackle core provides two keyrings that work everywhere: the {@link PreferencesKeyring}, which keeps the
 * secrets encrypted with the application's {@link org.tentackle.common.Cryptor} in the Java user preferences,
 * and the {@link MemoryKeyring}, which forgets them when the application terminates. Neither protects a secret
 * the way the operating system can, and they say so via {@link #isSecure()} and {@link #isPersistent()}.
 * <p>
 * Adding the module {@code tentackle-keyring} to the application puts the credential store of the operating
 * system in front of them:
 * <ul>
 *   <li>macOS: the login keychain,</li>
 *   <li>Windows: the Credential Manager,</li>
 *   <li>Linux and other Unixes: the Secret Service (GNOME Keyring, KWallet, KeePassXC) via libsecret.</li>
 * </ul>
 * Applications use the same API either way.
 * <p>
 * Secrets are passed as {@code char[]}, never as {@code String}, so they can be wiped from memory
 * as soon as they are no longer needed. The keyring never keeps a reference to an array passed in and
 * always hands out a new array, which the caller should wipe after use.
 * <p>
 * Implementations are threadsafe.
 *
 * @author harald
 */
public interface Keyring {

  /**
   * Gets the keyring.
   *
   * @return the keyring, never null
   * @see KeyringFactory#getKeyring()
   */
  static Keyring getInstance() {
    return KeyringFactory.getInstance().getKeyring();
  }


  /**
   * Gets the name of this keyring.
   * <p>
   * The name identifies the backend, such as {@code "macos"}, {@code "windows"}, {@code "libsecret"},
   * {@code "preferences"} or {@code "memory"}. It is also the value of the system property
   * {@value KeyringFactory#KEYRING_PROPERTY} that forces a particular backend.
   *
   * @return the name
   */
  String getName();

  /**
   * Returns whether the secrets are protected by the operating system.
   * <p>
   * The credential stores of the operating systems are secure: the secrets are bound to the logged-in
   * user and encrypted with a key the user's login unlocks. The fallbacks are not.
   *
   * @return true if secure, false if the secrets are merely obfuscated or kept in memory
   */
  boolean isSecure();

  /**
   * Returns whether the secrets survive the application.
   * <p>
   * Only the keyring kept in memory, which is the last resort if nothing else is usable, is not persistent.
   *
   * @return true if persistent, false if the secrets are lost when the JVM terminates
   */
  boolean isPersistent();

  /**
   * Stores a secret.<br>
   * An existing secret with the same id is replaced.
   *
   * @param id the secret's id
   * @param label the label shown to the user by the OS tools, such as Keychain Access or Seahorse,
   *              null to derive it from the id
   * @param secret the secret; must not be empty or contain NUL characters
   * @throws KeyringException if the secret could not be stored
   */
  void store(SecretId id, String label, char[] secret);

  /**
   * Loads a secret.
   * <p>
   * Depending on the platform, this may ask the user for permission, for example, if the application's
   * binary has changed since the secret was stored.
   *
   * @param id the secret's id
   * @return the secret, null if there is no such secret
   * @throws KeyringException if the credential store could not be accessed, or the user denied access
   */
  char[] load(SecretId id);

  /**
   * Removes a secret.
   *
   * @param id the secret's id
   * @return true if removed, false if there was no such secret
   * @throws KeyringException if the credential store could not be accessed
   */
  boolean remove(SecretId id);

}
