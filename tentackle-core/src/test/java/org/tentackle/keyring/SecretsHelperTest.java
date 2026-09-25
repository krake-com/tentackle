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

import org.testng.Reporter;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;

import static org.testng.Assert.assertEquals;

/**
 * Tests the {@link SecretsHelper} codec.
 */
public class SecretsHelperTest {

  @Test
  public void testRoundTrip() {
    Reporter.log("secrets are encoded to UTF-8 and back without loss", true);
    String text = "pässwörd-€-🔑";    // incl. a surrogate pair
    byte[] bytes = SecretsHelper.encode(text.toCharArray());
    assertEquals(bytes, text.getBytes(StandardCharsets.UTF_8));
    assertEquals(new String(SecretsHelper.decode(bytes)), text);
  }

  @Test(expectedExceptions = KeyringException.class)
  public void testLoneSurrogate() {
    Reporter.log("a lone surrogate is no valid secret", true);
    SecretsHelper.encode(new char[] {'a', '\uD83D' });
  }

  @Test(expectedExceptions = KeyringException.class)
  public void testMalformedUtf8() {
    Reporter.log("a stored secret that is not UTF-8 is reported", true);
    SecretsHelper.decode(new byte[] {(byte) 0xC3, (byte) 0x28 });
  }

  @Test
  public void testWipe() {
    char[] chars = "secret".toCharArray();
    byte[] bytes = "secret".getBytes(StandardCharsets.UTF_8);
    SecretsHelper.wipe(chars);
    SecretsHelper.wipe(bytes);
    SecretsHelper.wipe((char[]) null);
    assertEquals(chars, new char[6]);
    assertEquals(bytes, new byte[6]);
  }

}
