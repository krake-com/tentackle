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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

/**
 * Tests the {@link SecretId}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class SecretIdTest {

  @Test
  public void testValid() {
    Reporter.log("a secret id is a value object", true);
    SecretId id = new SecretId("com.example.myapp/google", "client-4711");
    assertEquals(id, new SecretId("com.example.myapp/google", "client-4711"));
    assertNotEquals(id, new SecretId("com.example.myapp/google", "client-4712"));
    assertEquals(id.toString(), "client-4711@com.example.myapp/google");
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void testNullService() {
    new SecretId(null, "account");
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testBlankAccount() {
    new SecretId("service", " ");
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testNulCharacter() {
    new SecretId("serv\0ice", "account");
  }

}
