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

import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.keyring.AbstractKeyring;
import org.tentackle.keyring.Keyring;
import org.tentackle.keyring.KeyringException;
import org.tentackle.keyring.SecretId;
import org.tentackle.keyring.platform.ffm.Advapi32;

import java.util.Map;
import java.util.TreeMap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link WindowsCredentialManager} against a fake credential store.
 * <p>
 * The fake enforces the blob size limit, so the chunking can be tested on any platform.
 */
@SuppressWarnings("missing-explicit-ctor")
public class WindowsChunkingTest extends AbstractKeyringContractTest {

  private final Map<String, byte[]> credentials = new TreeMap<>();
  private int writesUntilFailure = -1;    // < 0: never fail

  @Override
  protected Keyring createKeyring() {
    return new WindowsCredentialManager(new WindowsCredentialManager.CredentialAccess() {
      @Override
      public void write(String targetName, String userName, String comment, byte[] blob) {
        if (writesUntilFailure >= 0 && writesUntilFailure-- == 0) {
          throw new KeyringException("simulated failure");
        }
        if (blob.length > Advapi32.CRED_MAX_CREDENTIAL_BLOB_SIZE) {
          throw new KeyringException("blob too large");
        }
        credentials.put(targetName, blob.clone());
      }

      @Override
      public byte[] read(String targetName) {
        byte[] blob = credentials.get(targetName);
        return blob == null ? null : blob.clone();
      }

      @Override
      public boolean delete(String targetName) {
        return credentials.remove(targetName) != null;
      }
    });
  }

  @Test
  public void testAvailable() {
    Reporter.log("a credential that is not found does not make the store unavailable", true);
    assertTrue(((AbstractKeyring) keyring).isAvailable());
  }

  @Test
  public void testChunksAreCleanedUp() {
    Reporter.log("growing and shrinking a secret leaves no stale chunks", true);
    SecretId id = id("chunks");
    keyring.store(id, null, secret(3 * Advapi32.CRED_MAX_CREDENTIAL_BLOB_SIZE));
    assertEquals(credentials.size(), 4);
    keyring.store(id, null, secret(Advapi32.CRED_MAX_CREDENTIAL_BLOB_SIZE + 1));
    assertEquals(credentials.size(), 3);
    keyring.store(id, null, secret(Advapi32.CRED_MAX_CREDENTIAL_BLOB_SIZE - 1));    // largest inline
    assertEquals(credentials.size(), 1);
    keyring.remove(id);
    assertTrue(credentials.isEmpty());
  }

  @Test(expectedExceptions = KeyringException.class)
  public void testMissingChunk() {
    Reporter.log("a missing chunk is reported, not silently truncated", true);
    SecretId id = id("chunks");
    keyring.store(id, null, secret(2 * Advapi32.CRED_MAX_CREDENTIAL_BLOB_SIZE));
    credentials.remove(credentials.keySet().stream().filter(k -> k.endsWith("#0.2")).findFirst().orElseThrow());
    keyring.load(id);
  }

  @Test
  public void testFailedStoreKeepsPreviousSecret() {
    Reporter.log("a store failing midway leaves the previous secret intact", true);
    SecretId id = id("chunks");
    char[] first = secret(3 * Advapi32.CRED_MAX_CREDENTIAL_BLOB_SIZE);
    keyring.store(id, null, first);
    char[] second = secret(3 * Advapi32.CRED_MAX_CREDENTIAL_BLOB_SIZE);
    second[0] = 'X';
    second[second.length - 1] = 'X';
    writesUntilFailure = 2;     // the third chunk fails
    assertThrows(KeyringException.class, () -> keyring.store(id, null, second));
    assertEquals(keyring.load(id), first);
    // the next store overwrites the leftovers and removes all stale chunks
    keyring.store(id, null, secret(Advapi32.CRED_MAX_CREDENTIAL_BLOB_SIZE + 1));
    assertEquals(credentials.size(), 3);
    keyring.store(id, null, second);
    assertEquals(keyring.load(id), second);
    assertEquals(credentials.size(), 4);
  }

  @Test
  public void testFailedStoreLeftoversAreRemoved() {
    Reporter.log("chunks left behind by a failed store are removed by the next one", true);
    SecretId id = id("chunks");
    writesUntilFailure = 2;     // two chunks are written, the third fails
    assertThrows(KeyringException.class, () -> keyring.store(id, null, secret(3 * Advapi32.CRED_MAX_CREDENTIAL_BLOB_SIZE)));
    assertEquals(credentials.size(), 2);
    keyring.store(id, null, "small".toCharArray());
    assertEquals(credentials.size(), 1);
    assertEquals(keyring.load(id), "small".toCharArray());
  }

}
