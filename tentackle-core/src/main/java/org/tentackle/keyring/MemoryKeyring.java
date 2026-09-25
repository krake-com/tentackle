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

import java.util.HashMap;
import java.util.Map;

/**
 * A keyring kept in memory.
 * <p>
 * The last resort if no other keyring is usable, and useful for tests. The secrets never leave the
 * process but are lost when it terminates.
 */
public class MemoryKeyring extends AbstractKeyring {

  /**
   * The name of this keyring.
   */
  public static final String NAME = "memory";

  private final Map<SecretId, char[]> secrets = new HashMap<>();

  /**
   * Creates an empty memory keyring.
   */
  public MemoryKeyring() {
    // see -Xlint:missing-explicit-ctor
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean isPersistent() {
    return false;
  }

  @Override
  protected void doStore(SecretId id, String label, char[] secret) {
    Secrets.wipe(secrets.put(id, secret.clone()));
  }

  @Override
  protected char[] doLoad(SecretId id) {
    char[] secret = secrets.get(id);
    return secret == null ? null : secret.clone();
  }

  @Override
  protected boolean doRemove(SecretId id) {
    char[] secret = secrets.remove(id);
    Secrets.wipe(secret);
    return secret != null;
  }

}
