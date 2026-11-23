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

import org.tentackle.common.Service;
import org.tentackle.misc.Secret;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The default keystore password provider.
 * <p>
 * Keeps the registered passwords in memory as {@link Secret}s, i.e. encrypted, if a
 * {@link org.tentackle.common.Cryptor} is configured. Keystores are identified by the normalized
 * absolute path of their file, so different spellings of the same path find the same password.
 *
 * @author harald
 */
@Service(KeyStorePasswordProvider.class)
public class DefaultKeyStorePasswordProvider implements KeyStorePasswordProvider {

  private final Map<String, Secret> passwords;    // normalized keystore path :: password


  /**
   * Creates the provider.
   */
  public DefaultKeyStorePasswordProvider() {
    passwords = new ConcurrentHashMap<>();
  }


  @Override
  public char[] getPassword(String keyStoreFile) {
    Secret password = passwords.get(normalize(keyStoreFile));
    return password == null ? null : password.get();
  }

  @Override
  public void setPassword(String keyStoreFile, char[] password) {
    String key = normalize(keyStoreFile);
    Secret old = password == null ? passwords.remove(key) : passwords.put(key, new Secret(password));
    if (old != null) {
      old.clear();
    }
  }

  @Override
  public void clear(String keyStoreFile) {
    setPassword(keyStoreFile, null);
  }

  /**
   * Normalizes the path of a keystore file.
   *
   * @param keyStoreFile the path
   * @return the normalized absolute path
   */
  protected String normalize(String keyStoreFile) {
    return Path.of(Objects.requireNonNull(keyStoreFile, "missing keystore file")).toAbsolutePath().normalize().toString();
  }

}
