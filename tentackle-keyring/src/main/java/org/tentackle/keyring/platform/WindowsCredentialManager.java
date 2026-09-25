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
 * {@code #} percent-encoded, so that different ids never map to the same target.
 * <p>
 * A credential holds at most {@value Advapi32#CRED_MAX_CREDENTIAL_BLOB_SIZE} bytes, which some tokens exceed.
 * The first byte of the blob is therefore a format tag:
 * <ul>
 *   <li>{@value #FORMAT_INLINE}: the secret follows,</li>
 *   <li>{@value #FORMAT_CHUNKED}: a 4-byte big-endian chunk count follows, and the secret is spread over the
 *   credentials {@code target#1} to {@code target#n}.</li>
 * </ul>
 * The chunks are written before the credential referring to them, so a failure leaves the previous secret
 * intact, or at least never one assembled from parts of two.
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
    int oldChunks = getChunkCount(target);
    byte[] bytes = SecretsHelper.encode(secret);
    byte[] blob = null;
    int newChunks = 0;
    try {
      if (bytes.length <= MAX_INLINE_SIZE) {
        blob = new byte[bytes.length + 1];
        blob[0] = FORMAT_INLINE;
        System.arraycopy(bytes, 0, blob, 1, bytes.length);
      }
      else {
        newChunks = (bytes.length + CHUNK_SIZE - 1) / CHUNK_SIZE;
        for (int i = 0; i < newChunks; i++) {
          byte[] chunk = Arrays.copyOfRange(bytes, i * CHUNK_SIZE, Math.min(bytes.length, (i + 1) * CHUNK_SIZE));
          try {
            advapi32.write(getChunkName(target, i + 1), id.account(),
                           label + " (part " + (i + 1) + " of " + newChunks + ")", chunk);
          }
          finally {
            SecretsHelper.wipe(chunk);
          }
        }
        blob = ByteBuffer.allocate(5).put(FORMAT_CHUNKED).putInt(newChunks).array();
      }
      advapi32.write(target, id.account(), label, blob);
    }
    finally {
      SecretsHelper.wipe(bytes);
      SecretsHelper.wipe(blob);
    }
    removeChunks(target, newChunks + 1, oldChunks);
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
      int chunks = getChunkCount(target, blob);
      if (chunks == 0) {
        bytes = Arrays.copyOfRange(blob, 1, blob.length);
      }
      else {
        byte[][] parts = new byte[chunks][];
        try {
          int length = 0;
          for (int i = 0; i < chunks; i++) {
            parts[i] = advapi32.read(getChunkName(target, i + 1));
            if (parts[i] == null) {
              throw new KeyringException("part " + (i + 1) + " of " + chunks + " of " + id + " is missing");
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
    int chunks = getChunkCount(target);
    boolean removed = advapi32.delete(target);    // the credential first: without it the chunks are unreachable
    removeChunks(target, 1, chunks);
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


  private static String getChunkName(String target, int chunk) {
    return target + "#" + chunk;
  }

  private static String escape(String str) {
    return str.replace("%", "%25").replace("@", "%40").replace("#", "%23");
  }

  /**
   * Gets the number of chunks of the stored secret.
   *
   * @param target the target name
   * @return the number of chunks, 0 if none or not stored
   */
  private int getChunkCount(String target) {
    byte[] blob = advapi32.read(target);
    try {
      return blob == null ? 0 : getChunkCount(target, blob);
    }
    catch (KeyringException kex) {
      return 0;   // replaced or removed anyway
    }
    finally {
      SecretsHelper.wipe(blob);
    }
  }

  /**
   * Gets the number of chunks from a credential's blob.
   *
   * @param target the target name
   * @param blob the blob
   * @return the number of chunks, 0 if inline
   */
  private int getChunkCount(String target, byte[] blob) {
    if (blob.length >= 1 && blob[0] == FORMAT_INLINE) {
      return 0;
    }
    if (blob.length == 5 && blob[0] == FORMAT_CHUNKED) {
      int chunks = ByteBuffer.wrap(blob, 1, 4).getInt();
      if (chunks > 0) {
        return chunks;
      }
    }
    throw new KeyringException("credential " + target + " has an unknown format");
  }

  /**
   * Removes chunks.
   *
   * @param target the target name
   * @param from the first chunk
   * @param to the last chunk
   */
  private void removeChunks(String target, int from, int to) {
    for (int i = from; i <= to; i++) {
      advapi32.delete(getChunkName(target, i));
    }
  }

}
