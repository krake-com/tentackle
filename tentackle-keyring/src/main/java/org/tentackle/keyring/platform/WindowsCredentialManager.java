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
import org.tentackle.keyring.platform.ffm.Advapi32;
import org.tentackle.log.Logger;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A keyring backed by the Windows Credential Manager.
 * <p>
 * The secrets are generic credentials of the user and show up in the Control Panel's Credential Manager
 * under "Windows Credentials". The target name is {@code account@service}, with {@code %}, {@code @} and
 * {@code #} percent-encoded, so that different ids never map to the same target, and no target collides with
 * the name of a chunk. Target names are case-insensitive, though: ids that differ only in case share a credential.
 * <p>
 * A credential holds at most {@value Advapi32#CRED_MAX_CREDENTIAL_BLOB_SIZE} bytes, which some tokens exceed.
 * The first byte of the blob is therefore a format tag:
 * <ul>
 *   <li>{@value #FORMAT_INLINE}: the secret follows,</li>
 *   <li>{@value #FORMAT_CHUNKED}: a 4-byte big-endian chunk count {@code n} and a generation {@code g} of 0 or 1
 *   follow, and the secret is spread over the credentials {@code target#g.1} to {@code target#g.n}.</li>
 * </ul>
 * A chunked secret is written to the generation its predecessor does not use, and the credential referring to
 * the chunks is written last. Replacing it switches to the new secret in one step, and only then are the old
 * chunks removed. A store that fails midway therefore leaves the previous secret intact, and a load never
 * assembles a secret from parts of two.
 */
public class WindowsCredentialManager extends AbstractKeyring {

  /**
   * The name of this keyring.
   */
  public static final String NAME = "windows";

  /**
   * The format tag of a secret held in the credential itself.
   */
  public static final byte FORMAT_INLINE = 0;

  /**
   * The format tag of a secret spread over chunks.
   */
  public static final byte FORMAT_CHUNKED = 1;

  private static final int MAX_INLINE_SIZE = Advapi32.CRED_MAX_CREDENTIAL_BLOB_SIZE - 1;
  private static final int CHUNK_SIZE = Advapi32.CRED_MAX_CREDENTIAL_BLOB_SIZE;
  private static final int CHUNKED_SIZE = 6;    // tag, count, generation

  /**
   * The chunks of a stored secret.
   *
   * @param count the number of chunks, 0 if inline or not stored
   * @param generation the generation of the chunks, 0 or 1
   */
  private record Chunks(int count, int generation) {
    private static final Chunks NONE = new Chunks(0, 0);
  }

  /**
   * Access to the generic credentials.
   * <p>
   * Implemented by {@link Advapi32} and replaceable for tests.
   */
  protected interface CredentialAccess {

    /**
     * Writes a credential.
     *
     * @param targetName the target name
     * @param userName the username
     * @param comment the comment
     * @param blob the blob, at most {@value Advapi32#CRED_MAX_CREDENTIAL_BLOB_SIZE} bytes
     */
    void write(String targetName, String userName, String comment, byte[] blob);

    /**
     * Reads a credential's blob.
     *
     * @param targetName the target name
     * @return the blob, null if not found
     */
    byte[] read(String targetName);

    /**
     * Deletes a credential.
     *
     * @param targetName the target name
     * @return true if deleted, false if not found
     */
    boolean delete(String targetName);
  }


  private static final Logger LOGGER = Logger.get(WindowsCredentialManager.class);

  private final CredentialAccess advapi32;

  /**
   * Creates the keyring.
   *
   * @throws KeyringException if not running on Windows
   */
  public WindowsCredentialManager() {
    Advapi32 binding = new Advapi32();
    this.advapi32 = new CredentialAccess() {
      @Override
      public void write(String targetName, String userName, String comment, byte[] blob) {
        binding.write(targetName, userName, comment, blob);
      }

      @Override
      public byte[] read(String targetName) {
        return binding.read(targetName);
      }

      @Override
      public boolean delete(String targetName) {
        return binding.delete(targetName);
      }
    };
  }

  /**
   * Creates the keyring with a custom credential access.
   *
   * @param credentialAccess the credential access
   */
  protected WindowsCredentialManager(CredentialAccess credentialAccess) {
    this.advapi32 = credentialAccess;
  }

  @Override
  public String getName() {
    return NAME;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Reads a credential that does not exist, which fails if the logon session has no credential store,
   * as is the case for services and network logons.
   */
  @Override
  public boolean isAvailable() {
    try {
      advapi32.read(getTargetName(new SecretId(PROBE_SERVICE, PROBE_ACCOUNT)));
      return true;
    }
    catch (KeyringException kex) {
      LOGGER.info("credential manager not usable: {0}", kex.getMessage());
      return false;
    }
  }

  @Override
  protected void doStore(SecretId id, String label, char[] secret) {
    String target = getTargetName(id);
    Chunks oldChunks = getChunks(target);
    Chunks newChunks = Chunks.NONE;
    byte[] bytes = SecretsHelper.encode(secret);
    byte[] blob = null;
    try {
      if (bytes.length <= MAX_INLINE_SIZE) {
        blob = new byte[bytes.length + 1];
        blob[0] = FORMAT_INLINE;
        System.arraycopy(bytes, 0, blob, 1, bytes.length);
      }
      else {
        // the generation not in use by the current secret, which therefore stays intact until the switch below
        int count = (bytes.length + CHUNK_SIZE - 1) / CHUNK_SIZE;
        newChunks = new Chunks(count, oldChunks.count() > 0 ? 1 - oldChunks.generation() : 0);
        for (int i = 1; i <= count; i++) {
          byte[] chunk = Arrays.copyOfRange(bytes, (i - 1) * CHUNK_SIZE, Math.min(bytes.length, i * CHUNK_SIZE));
          try {
            advapi32.write(getChunkName(target, newChunks.generation(), i), id.account(),
                           label + " (part " + i + " of " + count + ")", chunk);
          }
          finally {
            SecretsHelper.wipe(chunk);
          }
        }
        blob = ByteBuffer.allocate(CHUNKED_SIZE).put(FORMAT_CHUNKED).putInt(count)
                         .put((byte) newChunks.generation()).array();
      }
      advapi32.write(target, id.account(), label, blob);    // the switch
    }
    finally {
      SecretsHelper.wipe(bytes);
      SecretsHelper.wipe(blob);
    }
    removeStaleChunks(target, newChunks, oldChunks);
  }

  @Override
  protected char[] doLoad(SecretId id) {
    String target = getTargetName(id);
    byte[] blob = advapi32.read(target);
    if (blob == null) {
      return null;
    }
    byte[] bytes = null;
    try {
      Chunks chunks = getChunks(target, blob);
      if (chunks.count() == 0) {
        bytes = Arrays.copyOfRange(blob, 1, blob.length);
      }
      else {
        byte[][] parts = new byte[chunks.count()][];
        try {
          int length = 0;
          for (int i = 0; i < parts.length; i++) {
            parts[i] = advapi32.read(getChunkName(target, chunks.generation(), i + 1));
            if (parts[i] == null) {
              throw new KeyringException("part " + (i + 1) + " of " + parts.length + " of " + id + " is missing");
            }
            length += parts[i].length;
          }
          bytes = new byte[length];
          int offset = 0;
          for (byte[] part : parts) {
            System.arraycopy(part, 0, bytes, offset, part.length);
            offset += part.length;
          }
        }
        finally {
          for (byte[] part : parts) {
            SecretsHelper.wipe(part);
          }
        }
      }
      return SecretsHelper.decode(bytes);
    }
    finally {
      SecretsHelper.wipe(blob);
      SecretsHelper.wipe(bytes);
    }
  }

  @Override
  protected boolean doRemove(SecretId id) {
    String target = getTargetName(id);
    Chunks chunks = getChunks(target);
    boolean removed = advapi32.delete(target);    // the credential first: without it the chunks are unreachable
    removeStaleChunks(target, Chunks.NONE, chunks);
    return removed;
  }


  /**
   * Gets the target name of a secret.
   *
   * @param id the secret's id
   * @return the target name
   */
  protected String getTargetName(SecretId id) {
    return escape(id.account()) + "@" + escape(id.service());
  }


  private static String getChunkName(String target, int generation, int chunk) {
    return target + "#" + generation + "." + chunk;
  }

  private static String escape(String str) {
    return str.replace("%", "%25").replace("@", "%40").replace("#", "%23");
  }

  /**
   * Gets the chunks of the stored secret.
   *
   * @param target the target name
   * @return the chunks, {@link Chunks#NONE} if inline, not stored or of an unknown format
   */
  private Chunks getChunks(String target) {
    byte[] blob = advapi32.read(target);
    try {
      return blob == null ? Chunks.NONE : getChunks(target, blob);
    }
    catch (KeyringException kex) {
      return Chunks.NONE;   // replaced or removed anyway
    }
    finally {
      SecretsHelper.wipe(blob);
    }
  }

  /**
   * Gets the chunks from a credential's blob.
   *
   * @param target the target name
   * @param blob the blob
   * @return the chunks, {@link Chunks#NONE} if inline
   * @throws KeyringException if the blob has an unknown format
   */
  private Chunks getChunks(String target, byte[] blob) {
    if (blob.length >= 1 && blob[0] == FORMAT_INLINE) {
      return Chunks.NONE;
    }
    if (blob.length == CHUNKED_SIZE && blob[0] == FORMAT_CHUNKED) {
      int count = ByteBuffer.wrap(blob, 1, 4).getInt();
      int generation = blob[5];
      if (count > 0 && (generation == 0 || generation == 1)) {
        return new Chunks(count, generation);
      }
    }
    throw new KeyringException("credential " + target + " has an unknown format");
  }

  /**
   * Removes the chunks no longer referred to.
   * <p>
   * Removes the chunks of the previous secret, and the chunks left behind by a store that failed midway.
   * The latter are found by removing chunks until the first one is missing. Failures are only logged,
   * since stale chunks are unreachable and do no harm.
   *
   * @param target the target name
   * @param current the chunks of the current secret, {@link Chunks#NONE} if inline or removed
   * @param previous the chunks of the previous secret, {@link Chunks#NONE} if inline or not stored
   */
  private void removeStaleChunks(String target, Chunks current, Chunks previous) {
    try {
      for (int generation = 0; generation <= 1; generation++) {
        int from = current.count() > 0 && current.generation() == generation ? current.count() + 1 : 1;
        int to = previous.count() > 0 && previous.generation() == generation ? previous.count() : 0;
        for (int i = from; advapi32.delete(getChunkName(target, generation, i)) || i < to; i++) {
          // up to the last chunk of the previous secret at least, then until the first one missing
        }
      }
    }
    catch (KeyringException kex) {
      LOGGER.warning("removing stale chunks of " + target + " failed", kex);
    }
  }

}
