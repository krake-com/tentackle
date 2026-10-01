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
import java.util.concurrent.locks.ReentrantLock;

/**
 * Base class of the keyrings.
 * <p>
 * Checks the arguments and serializes all accesses, so that the implementations need to care about
 * neither. Serializing is cheap here, since a credential store is accessed rarely, and it allows
 * implementations to update several native entries as one.
 */
public abstract class AbstractKeyring implements Keyring {

  /**
   * The service of the secret the native keyrings look up to probe their credential store.
   * <p>
   * The secret never exists, so looking it up neither finds anything nor prompts the user.
   */
  protected static final String PROBE_SERVICE = "org.tentackle.keyring";

  /**
   * The account of the probe secret.
   */
  protected static final String PROBE_ACCOUNT = "availability-probe";

  private final ReentrantLock lock = new ReentrantLock();

  /**
   * Creates a keyring.
   */
  public AbstractKeyring() {
    // see -Xlint:missing-explicit-ctor
  }

  /**
   * Returns whether this keyring is usable.
   * <p>
   * Invoked once by the {@link DefaultKeyringFactory} before the keyring is handed out. The native
   * keyrings probe their credential store here, since a library that loads does not mean there is a
   * store to talk to.
   *
   * @return true if usable
   */
  public boolean isAvailable() {
    return true;
  }

  @Override
  public boolean isSecure() {
    return true;
  }

  @Override
  public boolean isPersistent() {
    return true;
  }

  @Override
  public void store(SecretId id, String label, char[] secret) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(secret, "secret");
    if (secret.length == 0) {
      throw new IllegalArgumentException("secret must not be empty");
    }
    for (char c : secret) {
      if (c == '\0') {
        // some native APIs take NUL-terminated strings
        throw new IllegalArgumentException("secret must not contain NUL characters");
      }
    }
    if (label == null || label.isBlank()) {
      label = defaultLabel(id);
    }
    lock.lock();
    try {
      doStore(id, label, secret);
    }
    catch (KeyringException kex) {
      throw kex;
    }
    catch (RuntimeException rex) {
      throw new KeyringException("storing " + id + " in " + getName() + " failed", rex);
    }
    finally {
      lock.unlock();
    }
  }

  @Override
  public char[] load(SecretId id) {
    Objects.requireNonNull(id, "id");
    lock.lock();
    try {
      return doLoad(id);
    }
    catch (KeyringException kex) {
      throw kex;
    }
    catch (RuntimeException rex) {
      throw new KeyringException("loading " + id + " from " + getName() + " failed", rex);
    }
    finally {
      lock.unlock();
    }
  }

  @Override
  public boolean remove(SecretId id) {
    Objects.requireNonNull(id, "id");
    lock.lock();
    try {
      return doRemove(id);
    }
    catch (KeyringException kex) {
      throw kex;
    }
    catch (RuntimeException rex) {
      throw new KeyringException("removing " + id + " from " + getName() + " failed", rex);
    }
    finally {
      lock.unlock();
    }
  }

  @Override
  public String toString() {
    return getName();
  }


  /**
   * Stores a secret.
   *
   * @param id the secret's id
   * @param label the label, never null
   * @param secret the secret, neither empty nor containing NUL characters, must not be modified
   */
  protected abstract void doStore(SecretId id, String label, char[] secret);

  /**
   * Loads a secret.
   *
   * @param id the secret's id
   * @return the secret, null if not found
   */
  protected abstract char[] doLoad(SecretId id);

  /**
   * Removes a secret.
   *
   * @param id the secret's id
   * @return true if removed, false if not found
   */
  protected abstract boolean doRemove(SecretId id);

  /**
   * Derives a label from a secret id.
   *
   * @param id the secret's id
   * @return the label
   */
  protected String defaultLabel(SecretId id) {
    return id.service() + " (" + id.account() + ")";
  }

}
