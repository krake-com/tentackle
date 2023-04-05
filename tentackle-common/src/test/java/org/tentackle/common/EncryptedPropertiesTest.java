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
 * Tests for en- and decryption of properties.
 */
@SuppressWarnings("missing-explicit-ctor")
public class EncryptedPropertiesTest {

  private static final String MSG = "a hollow voice says plugh";
  private static final char[] MSGARR = MSG.toCharArray();
  private static final String STARTSWITHTILDE = "~kjshdfkshfskdf";

  private static final String KEY_ENCRYPTED = "encrypted";
  private static final String KEY_UNENCRYPTED = "unencrypted";
  private static final String KEY_STARTSWITHTILDE = "startswithtilde";


  @Test
  public void doProps() {
    Cryptor cryptor = Cryptor.getInstance();
    String base64 = cryptor.encrypt64(MSG);

    EncryptedProperties properties = new EncryptedProperties();
    properties.setEncryptedProperty(KEY_ENCRYPTED, MSG);
    properties.setProperty(KEY_UNENCRYPTED, MSG);
    properties.setProperty(KEY_STARTSWITHTILDE, "\\" + STARTSWITHTILDE);

    Assert.assertEquals(properties.getProperty(KEY_ENCRYPTED), MSG);
    Assert.assertEquals(properties.getPropertyAsChars(KEY_ENCRYPTED), MSGARR);
    Assert.assertEquals(properties.getPropertyBlunt(KEY_ENCRYPTED), "~" + base64);
    Assert.assertEquals(properties.getProperty(KEY_UNENCRYPTED), MSG);
    Assert.assertEquals(properties.getPropertyAsChars(KEY_UNENCRYPTED), MSGARR);
    Assert.assertEquals(properties.getProperty(KEY_STARTSWITHTILDE), STARTSWITHTILDE);
    Assert.assertEquals(properties.getPropertyBlunt(KEY_STARTSWITHTILDE), "\\" + STARTSWITHTILDE);

    Assert.assertNull(properties.getProperty("bird"));
    Assert.assertNull(properties.getPropertyAsChars("troll"));
  }

}
