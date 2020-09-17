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

package org.tentackle.common;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for en- and decryption.
 */
public class CryptorTest {

  private static final String MSG = "a hollow voice says plugh";


  @Test
  public void doCrypt() {
    Cryptor cryptor = new Cryptor("cage", "wave rod");
    String base64 = cryptor.encrypt64(MSG);
    Assert.assertEquals(cryptor.decrypt64(base64), MSG);

    char[] msg = MSG.toCharArray();
    String arr64 = cryptor.encrypt64(msg);
    cryptor = new Cryptor("cage", "wave rod");
    Assert.assertEquals(cryptor.decrypt64(arr64), MSG);

    Assert.assertEquals(cryptor.decrypt64ToChars(arr64), MSG.toCharArray());
  }
}
