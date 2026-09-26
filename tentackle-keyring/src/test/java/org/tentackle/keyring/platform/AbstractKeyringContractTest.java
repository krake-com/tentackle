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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.tentackle.keyring.Keyring;
import org.tentackle.keyring.SecretId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * The contract every {@link Keyring} must fulfill.
 * <p>
 * Each test uses its own ids and removes them afterward, so it can run against a real credential store.
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public abstract class AbstractKeyringContractTest {

  private final String service = "org.tentackle.keyring.test/" + UUID.randomUUID();
  private final List<SecretId> ids = new ArrayList<>();

  /** the keyring under test */
  protected Keyring keyring;

  /**
   * Creates the keyring to test.
   *
   * @return the keyring
   */
  protected abstract Keyring createKeyring();

  @BeforeClass
  public void setUpKeyring() {
    keyring = createKeyring();
  }

  @AfterMethod(alwaysRun = true)
  public void removeSecrets() {
    for (SecretId id : ids) {
      keyring.remove(id);
    }
    ids.clear();
  }

  /**
   * Creates an id which is removed after the test.
   *
   * @param account the account
   * @return the id
   */
  protected SecretId id(String account) {
    return id(service, account);
  }

  /**
   * Creates an id which is removed after the test.
   *
   * @param service the service
   * @param account the account
   * @return the id
   */
  protected SecretId id(String service, String account) {
    SecretId id = new SecretId(service, account);
    ids.add(id);
    return id;
  }

  /**
   * Creates a secret of the given length.
   *
   * @param length the length
   * @return the secret
   */
  protected static char[] secret(int length) {
    char[] secret = new char[length];
    for (int i = 0; i < length; i++) {
      secret[i] = (char) ('a' + i % 26);
    }
    return secret;
  }

  @Test
  public void testStoreLoadRemove() {
    Reporter.log(keyring.getName() + ": a secret is stored, loaded and removed", true);
    SecretId id = id("user");
    assertNull(keyring.load(id));
    char[] secret = "eyJhbGciOiJSUzI1NiJ9.payload.signature".toCharArray();
    keyring.store(id, "Tentackle keyring test", secret);
    assertEquals(new String(secret), "eyJhbGciOiJSUzI1NiJ9.payload.signature", "argument must not be wiped");
    assertEquals(keyring.load(id), secret);
    assertTrue(keyring.remove(id));
    assertNull(keyring.load(id));
    assertFalse(keyring.remove(id));
  }

  @Test
  public void testReplace() {
    Reporter.log(keyring.getName() + ": storing again replaces the secret", true);
    SecretId id = id("user");
    keyring.store(id, null, "first".toCharArray());
    keyring.store(id, null, "second".toCharArray());
    assertEquals(keyring.load(id), "second".toCharArray());
  }

  @Test
  public void testLoadReturnsCopy() {
    Reporter.log(keyring.getName() + ": wiping a loaded secret does not affect the stored one", true);
    SecretId id = id("user");
    keyring.store(id, null, "secret".toCharArray());
    Arrays.fill(keyring.load(id), '\0');
    assertEquals(keyring.load(id), "secret".toCharArray());
  }

  @Test
  public void testIdsAreDistinct() {
    Reporter.log(keyring.getName() + ": ids differing in service or account don't collide", true);
    SecretId id1 = id(service + "/a@b#1", "c");
    SecretId id2 = id(service + "/a", "b#1@c");
    SecretId id3 = id(service + "/a@b#1", "d");
    keyring.store(id1, null, "one".toCharArray());
    keyring.store(id2, null, "two".toCharArray());
    keyring.store(id3, null, "three".toCharArray());
    assertEquals(keyring.load(id1), "one".toCharArray());
    assertEquals(keyring.load(id2), "two".toCharArray());
    assertEquals(keyring.load(id3), "three".toCharArray());
    assertTrue(keyring.remove(id2));
    assertEquals(keyring.load(id1), "one".toCharArray());
    assertEquals(keyring.load(id3), "three".toCharArray());
  }

  @Test
  public void testUnicode() {
    Reporter.log(keyring.getName() + ": non-ASCII secrets and ids survive", true);
    SecretId id = id(service + "/ümlaut", "jörg@example.com");
    char[] secret = "pässwörd-€-🔑".toCharArray();
    keyring.store(id, "Tentackle Schlüssel", secret);
    assertEquals(keyring.load(id), secret);
  }

  @Test
  public void testLargeSecret() {
    Reporter.log(keyring.getName() + ": a secret larger than a Windows credential blob survives", true);
    SecretId id = id("large");
    char[] secret = secret(5000);
    keyring.store(id, null, secret);
    assertEquals(keyring.load(id), secret);
    // shrink and grow again
    keyring.store(id, null, "small".toCharArray());
    assertEquals(keyring.load(id), "small".toCharArray());
    secret = secret(3000);
    keyring.store(id, null, secret);
    assertEquals(keyring.load(id), secret);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testEmptySecret() {
    keyring.store(id("user"), null, new char[0]);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNulInSecret() {
    keyring.store(id("user"), null, "a\0b".toCharArray());
  }

}
