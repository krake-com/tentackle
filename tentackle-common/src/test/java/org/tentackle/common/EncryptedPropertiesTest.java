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

import org.testng.annotations.Test;

import java.util.Enumeration;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;

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
  private static final String KEY_STARTSWITHTILDE_CASE = "startsWithTilde";

  private static final String KEY_PREFIX = "tentackle.";
  private static final String NOT_IN_SPACE = "notInSpace";


  @Test
  public void doProps() {
    runIt(new EncryptedProperties());
  }

  @Test
  public void withPrefix() {
    EncryptedProperties properties = new EncryptedProperties();
    properties.setKeyPrefix(KEY_PREFIX);
    runIt(properties);

    properties.put(NOT_IN_SPACE, "100");
    Set<String> strings = properties.stringPropertyNames();
    assertEquals(strings.size(), 3);
    assertTrue(strings.contains(KEY_ENCRYPTED));
    assertTrue(strings.contains(KEY_UNENCRYPTED));
    assertTrue(strings.contains(KEY_STARTSWITHTILDE));

    Enumeration<?> enumeration = properties.propertyNames();
    int count = 0;
    while(enumeration.hasMoreElements()) {
      String key = (String) enumeration.nextElement();
      count++;
      assertTrue(key.startsWith(KEY_PREFIX) || NOT_IN_SPACE.equals(key));
    }
    assertEquals(count, 4);
  }

  private void runIt(EncryptedProperties properties) {
    Cryptor cryptor = Cryptor.getInstance();
    String base64 = cryptor.encrypt64(MSG);

    properties.setEncryptedProperty(KEY_ENCRYPTED, MSG);
    properties.setProperty(KEY_UNENCRYPTED, MSG);
    properties.setProperty(KEY_STARTSWITHTILDE, "\\" + STARTSWITHTILDE);

    assertEquals(properties.getProperty(KEY_ENCRYPTED), MSG);
    assertEquals(properties.getPropertyAsChars(KEY_ENCRYPTED), MSGARR);
    assertEquals(properties.getPropertyBlunt(KEY_ENCRYPTED), "~" + base64);
    assertEquals(properties.getProperty(KEY_UNENCRYPTED), MSG);
    assertEquals(properties.getPropertyAsChars(KEY_UNENCRYPTED), MSGARR);
    assertEquals(properties.getProperty(KEY_STARTSWITHTILDE), STARTSWITHTILDE);
    assertEquals(properties.getPropertyBlunt(KEY_STARTSWITHTILDE), "\\" + STARTSWITHTILDE);

    assertNull(properties.getProperty("bird"));
    assertNull(properties.getPropertyAsChars("troll"));

    assertEquals(properties.getPropertyIgnoreCase(KEY_STARTSWITHTILDE_CASE), STARTSWITHTILDE);
  }

  @Test
  public void encryptedCharsWithPrefix() {
    EncryptedProperties properties = new EncryptedProperties();
    properties.setKeyPrefix(KEY_PREFIX);
    properties.setEncryptedProperty(KEY_ENCRYPTED, MSG.toCharArray());
    // the prefix must be applied only once
    assertNotNull(properties.get(KEY_PREFIX + KEY_ENCRYPTED));
    assertNull(properties.get(KEY_PREFIX + KEY_PREFIX + KEY_ENCRYPTED));
    assertEquals(properties.getProperty(KEY_ENCRYPTED), MSG);
    assertEquals(properties.getPropertyIgnoreCase(KEY_ENCRYPTED.toUpperCase()), MSG);
  }

  @Test
  public void cloneDoesNotShareKeyCaseMap() {
    EncryptedProperties properties = new EncryptedProperties();
    properties.setProperty(KEY_UNENCRYPTED, MSG);
    assertEquals(properties.getPropertyIgnoreCase(KEY_UNENCRYPTED.toUpperCase()), MSG);   // creates the case map

    EncryptedProperties clone = properties.clone();
    clone.setProperty("cloneOnly", "x");
    assertEquals(clone.getPropertyIgnoreCase("CLONEONLY"), "x");
    assertNull(properties.getPropertyIgnoreCase("CLONEONLY"));
    assertEquals(properties.getKeyIgnoreCase("CLONEONLY"), "CLONEONLY");
  }

  @Test
  public void getPropertyAsCharsWithDefaults() {
    EncryptedProperties properties = new EncryptedProperties();
    char[] defaults = "dflt".toCharArray();
    assertSame(properties.getPropertyAsChars("missing", defaults), defaults);
  }

  @Test
  public void escapedTildeInFile() throws java.io.IOException {
    EncryptedProperties properties = new EncryptedProperties();
    properties.load(new java.io.StringReader("doubled=\\\\~plain\nsingle=\\~plain\n"));
    // the doubled backslash survives Properties.load and escapes the tilde
    assertEquals(properties.getProperty("doubled"), "~plain");
    // a single backslash is dropped by Properties.load, hence the value is considered encrypted
    assertEquals(properties.getPropertyBlunt("single"), "~plain");
  }

}
